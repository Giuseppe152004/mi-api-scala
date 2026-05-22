package com.proyecto.api.infrastructure.adapter.in.http.capacitacion

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import io.circe.generic.auto.*
import cats.effect.IO
import com.proyecto.api.application.port.in.CapacitacionUseCase
import com.proyecto.api.domain.model.*
import com.proyecto.api.domain.error.CapacitacionError
import com.proyecto.api.infrastructure.adapter.in.http.{ErrorResponse, CapacitacionHttpErrorMapper}
import com.proyecto.api.application.port.out.{ApiKeyValidator, TokenService}

// DTOs para solicitudes y respuestas
case class AsistenciaRequest(postulanteId: Int, diaCapacitacion: Int, asistio: Boolean)

case class DocumentDto(tipo: String, numero: String)

case class AsistenciaDetalleResponse(dia: Int, estado: String, origen: String)

case class ResumenResponse(
    postulanteId: Int,
    nombreCompleto: String,
    documento: DocumentDto,
    asistenciasRegistradas: Int,
    montoBonoAcumulado: Double,
    estadoScorecard: Option[String],
    validacionOperativa: Option[Boolean],
    corteOperativo: Option[Int],
    fechaPagoEstimada: Option[String],
    estadoGeneral: String,
    historialAsistencias: List[AsistenciaDetalleResponse]
)

case class PostulanteBasicoResponse(
    id: Int,
    nombreCompleto: String,
    puesto: String,
    estado: String
)

case class HistorialAsistenciaResponse(dia: Int, asistio: Boolean)

case class PostulanteDetalleResponse(
    postulanteId: Int,
    nombres: String,
    apellidos: String,
    tipoDocumento: String,
    numeroDocumento: String,
    idPortfolio: Option[Int],
    idCampaign: Option[Int],
    estadoScorecard: String,
    historialAsistencias: List[HistorialAsistenciaResponse]
)

class CapacitacionEndpoints(
    useCase: CapacitacionUseCase[IO],
    apiKeyValidator: ApiKeyValidator[IO],
    tokenService: TokenService[IO]
):

  private val baseEndpoint = endpoint
    .errorOut(statusCode.and(jsonBody[ErrorResponse]))

  // 1. Esquema de Seguridad con Bearer Token
  private val bearerSecurity = baseEndpoint
    .securityIn(auth.bearer[Option[String]]())
    .serverSecurityLogic {
      case Some(token) =>
        tokenService.validateToken(token).map {
          case Left(err) => Left((StatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", err.getMessage)))
          case Right(_)  => Right(())
        }
      case None =>
        IO.pure(Left((StatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "Se requiere token Bearer"))))
    }

  // 2. Esquema de Seguridad Híbrido (API Key O Bearer Token)
  private val hybridSecurity = baseEndpoint
    .securityIn(auth.apiKey(header[Option[String]]("X-API-Key")).and(auth.bearer[Option[String]]()))
    .serverSecurityLogic { case (apiKeyOpt, bearerOpt) =>
      val authResult = (apiKeyOpt, bearerOpt) match {
        case (Some(key), _) =>
          apiKeyValidator.isValid(key).map(isValid => if (isValid) Right(()) else Left("API Key inválida"))
        case (_, Some(token)) =>
          tokenService.validateToken(token).map {
            case Left(err) => Left(err.getMessage)
            case Right(_)  => Right(())
          }
        case _ =>
          IO.pure(Left("Se requiere X-API-Key o Bearer Token"))
      }

      authResult.map {
        case Left(msg) => Left((StatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", msg)))
        case Right(_)  => Right(())
      }
    }

  // Endpoint 1: Registro de Asistencia Unificado (UPSERT)
  val registrarAsistenciaEndpoint = bearerSecurity.post
    .in("api" / "v1" / "capacitacion" / "asistencia")
    .in(jsonBody[AsistenciaRequest])
    .out(statusCode(StatusCode.Ok))
    .summary("Registra o actualiza asistencia de un postulante (UPSERT)")
    .serverLogic { _ => req =>
      useCase.registrarAsistencia(req.postulanteId, req.diaCapacitacion, req.asistio).map {
        case Left(err) => Left(CapacitacionHttpErrorMapper.mapToHttp(err))
        case Right(_)  => Right(())
      }
    }

  // Endpoint 2: Obtener Resumen Consolidado del Postulante
  val obtenerResumenEndpoint = hybridSecurity.get
    .in("api" / "v1" / "capacitacion" / "postulantes" / path[Int]("id") / "resumen")
    .out(jsonBody[ResumenResponse])
    .summary("Obtiene el resumen consolidado de la capacitación del postulante")
    .serverLogic { _ => id =>
      useCase.obtenerResumen(id).map {
        case Left(err) => Left(CapacitacionHttpErrorMapper.mapToHttp(err))
        case Right(r)  =>
          val docDto = DocumentDto(r.tipoDocumento.toString, r.numeroDocumento.value)
          val historial = r.historialAsistencias.map(a =>
            AsistenciaDetalleResponse(dia = a.dia, estado = a.estado, origen = a.origen)
          )
          val response = ResumenResponse(
            postulanteId = r.postulanteId,
            nombreCompleto = r.nombreCompleto,
            documento = docDto,
            asistenciasRegistradas = r.asistenciasRegistradas,
            montoBonoAcumulado = r.montoBonoAcumulado,
            estadoScorecard = r.estadoScorecard,
            validacionOperativa = r.validacionOperativa,
            corteOperativo = r.corteOperativo,
            fechaPagoEstimada = r.fechaPagoEstimada.map(_.toString),
            estadoGeneral = r.estadoGeneral.toString,
            historialAsistencias = historial
          )
          Right(response)
      }
    }

  // Endpoint 3: Listar Postulantes Básicos
  val listarPostulantesEndpoint = bearerSecurity.get
    .in("api" / "v1" / "capacitacion" / "postulantes")
    .out(jsonBody[List[PostulanteBasicoResponse]])
    .summary("Lista todos los postulantes con información básica para la pantalla principal")
    .serverLogic { _ => _ =>
      useCase.listarPostulantes().map { lista =>
        val response = lista.map(p =>
          PostulanteBasicoResponse(
            id = p.id,
            nombreCompleto = p.nombreCompleto,
            puesto = p.puesto,
            estado = p.estado.toString
          )
        )
        Right(response)
      }
    }

  // Endpoint 4: Mis Postulantes (por idUser del capacitador)
  val misPostulantesEndpoint = bearerSecurity.get
    .in("api" / "v1" / "capacitacion" / "mis-postulantes")
    .in(query[Int]("idUser"))
    .out(jsonBody[List[PostulanteDetalleResponse]])
    .summary("Lista los postulantes asignados al capacitador con historial de asistencias")
    .serverLogic { _ => idUser =>
      useCase.listarMisPostulantes(idUser).map { lista =>
        val response = lista.map(p =>
          PostulanteDetalleResponse(
            postulanteId = p.postulanteId,
            nombres = p.nombres,
            apellidos = p.apellidos,
            tipoDocumento = p.tipoDocumento,
            numeroDocumento = p.numeroDocumento,
            idPortfolio = p.idPortfolio,
            idCampaign = p.idCampaign,
            estadoScorecard = p.estadoScorecard,
            historialAsistencias = p.historialAsistencias.map(h =>
              HistorialAsistenciaResponse(dia = h.dia, asistio = h.asistio)
            )
          )
        )
        Right(response)
      }
    }

  // Exponer lista de endpoints
  val endpoints = List(
    registrarAsistenciaEndpoint,
    obtenerResumenEndpoint,
    listarPostulantesEndpoint,
    misPostulantesEndpoint
  )
