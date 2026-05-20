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
case class AsistenciaManualRequest(postulanteId: Int, diaCapacitacion: Int, codigoAsistencia: String)
case class AsistenciaAutomaticaRequest(postulanteId: Int, diaCapacitacion: Int)
case class ScorecardRequest(postulanteId: Int, estadoScorecard: String, validacionOperativa: Boolean, observaciones: String)

case class DocumentDto(tipo: String, numero: String)
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
    estadoGeneral: String
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

  // 2. Esquema de Seguridad con API Key (Para Nexus M2M)
  private val apiKeySecurity = baseEndpoint
    .securityIn(auth.apiKey(header[Option[String]]("X-API-Key")))
    .serverSecurityLogic {
      case Some(key) =>
        apiKeyValidator.isValid(key).map { isValid =>
          if (isValid) Right(())
          else Left((StatusCode.Unauthorized, ErrorResponse("INVALID_API_KEY", "API Key inválida")))
        }
      case None =>
        IO.pure(Left((StatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", "Se requiere X-API-Key"))))
    }

  // 3. Esquema de Seguridad Híbrido (API Key O Bearer Token)
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

  // Endpoint 1: Registro de Asistencia Manual (Días 1-2)
  val registrarAsistenciaManualEndpoint = bearerSecurity.post
    .in("api" / "v1" / "capacitacion" / "asistencia" / "manual")
    .in(jsonBody[AsistenciaManualRequest])
    .out(statusCode(StatusCode.Created))
    .summary("Registra asistencia de forma manual (Días 1-2) para un postulante")
    .serverLogic { _ => req =>
      val codigo = if (req.codigoAsistencia == "A") AsistenciaCodigo.A else AsistenciaCodigo.F
      useCase.registrarAsistenciaManual(req.postulanteId, req.diaCapacitacion, codigo).map {
        case Left(err) => Left(CapacitacionHttpErrorMapper.mapToHttp(err))
        case Right(_)  => Right(())
      }
    }

  // Endpoint 2: Registro de Asistencia Automática (Días 3-7)
  val registrarAsistenciaAutomaticaEndpoint = apiKeySecurity.post
    .in("api" / "v1" / "capacitacion" / "asistencia" / "automatico")
    .in(jsonBody[AsistenciaAutomaticaRequest])
    .out(statusCode(StatusCode.Ok))
    .summary("Registra asistencia de forma automática (Días 3-7) vía Nexus")
    .serverLogic { _ => req =>
      useCase.registrarAsistenciaAutomatica(req.postulanteId, req.diaCapacitacion).map {
        case Left(err) => Left(CapacitacionHttpErrorMapper.mapToHttp(err))
        case Right(_)  => Right(())
      }
    }

  // Endpoint 3: Registro de Scorecard
  val registrarScorecardEndpoint = bearerSecurity.post
    .in("api" / "v1" / "capacitacion" / "scorecard")
    .in(jsonBody[ScorecardRequest])
    .out(statusCode(StatusCode.Ok))
    .summary("Registra la evaluación final (scorecard) de capacitación")
    .serverLogic { _ => req =>
      val estado = if (req.estadoScorecard == "APTO") ScorecardEstado.APTO else ScorecardEstado.NO_APTO
      val scorecard = Scorecard(req.postulanteId, estado, req.validacionOperativa, req.observaciones)
      useCase.registrarScorecard(req.postulanteId, scorecard).map {
        case Left(err) => Left(CapacitacionHttpErrorMapper.mapToHttp(err))
        case Right(_)  => Right(())
      }
    }

  // Endpoint 4: Obtener Resumen Consolidado del Postulante
  val obtenerResumenEndpoint = hybridSecurity.get
    .in("api" / "v1" / "capacitacion" / "postulantes" / path[Int]("id") / "resumen")
    .out(jsonBody[ResumenResponse])
    .summary("Obtiene el resumen consolidado de la capacitación del postulante")
    .serverLogic { _ => id =>
      useCase.obtenerResumen(id).map {
        case Left(err) => Left(CapacitacionHttpErrorMapper.mapToHttp(err))
        case Right(r)  =>
          val docDto = DocumentDto(r.tipoDocumento.toString, r.numeroDocumento.value)
          val response = ResumenResponse(
            postulanteId = r.postulanteId,
            nombreCompleto = r.nombreCompleto,
            documento = docDto,
            asistenciasRegistradas = r.asistenciasRegistradas,
            montoBonoAcumulado = r.montoBonoAcumulado,
            estadoScorecard = r.estadoScorecard.map(_.toString),
            validacionOperativa = r.validacionOperativa,
            corteOperativo = r.corteOperativo,
            fechaPagoEstimada = r.fechaPagoEstimada.map(_.toString),
            estadoGeneral = r.estadoGeneral.toString
          )
          Right(response)
      }
    }

  // Exponer lista de endpoints
  val endpoints = List(
    registrarAsistenciaManualEndpoint,
    registrarAsistenciaAutomaticaEndpoint,
    registrarScorecardEndpoint,
    obtenerResumenEndpoint
  )
