package com.proyecto.api.application.service

import cats.Monad
import cats.data.EitherT
import cats.syntax.all.*
import java.time.LocalDate
import com.proyecto.api.application.port.in.CapacitacionUseCase
import com.proyecto.api.application.port.out.CapacitacionRepository
import com.proyecto.api.domain.model.*
import com.proyecto.api.domain.error.CapacitacionError

class CapacitacionService[F[_]: Monad](
    repository: CapacitacionRepository[F]
) extends CapacitacionUseCase[F]:

  override def registrarAsistenciaManual(postulanteId: Int, dia: Int, codigo: AsistenciaCodigo): F[Either[CapacitacionError, Unit]] =
    val process: EitherT[F, CapacitacionError, Unit] = for
      // 1. Verificar si existe el postulante
      exists <- EitherT.liftF(repository.existsPostulante(postulanteId))
      _      <- EitherT.cond[F](exists, (), CapacitacionError.PostulanteNotFound())

      // 2. Validar que el día sea manual (1 o 2)
      _      <- EitherT.cond[F](dia == 1 || dia == 2, (), CapacitacionError.InvalidDiaManual())

      // 3. Validar duplicados
      hasAst <- EitherT.liftF(repository.hasAsistencia(postulanteId, dia))
      _      <- EitherT.cond[F](!hasAst, (), CapacitacionError.AsistenciaDuplicada())

      // 4. Registrar asistencia
      asistencia = Asistencia(postulanteId, dia, codigo, AsistenciaRegistroTipo.Manual, "CAPACITADOR")
      _      <- EitherT.liftF(repository.registrarAsistencia(asistencia))
    yield ()

    process.value

  override def registrarAsistenciaAutomatica(postulanteId: Int, dia: Int): F[Either[CapacitacionError, Unit]] =
    val process: EitherT[F, CapacitacionError, Unit] = for
      // 1. Verificar si existe
      exists <- EitherT.liftF(repository.existsPostulante(postulanteId))
      _      <- EitherT.cond[F](exists, (), CapacitacionError.PostulanteNotFound())

      // 2. Validar que el día sea automático (3 a 7)
      _      <- EitherT.cond[F](dia >= 3 && dia <= 7, (), CapacitacionError.InvalidDiaAutomatico())

      // 3. Validar duplicados (idempotente)
      hasAst <- EitherT.liftF(repository.hasAsistencia(postulanteId, dia))
      _      <- if (hasAst) EitherT.pure[F, CapacitacionError](())
                else
                  val asistencia = Asistencia(postulanteId, dia, AsistenciaCodigo.A, AsistenciaRegistroTipo.Automatico, "NEXUS_SYSTEM")
                  EitherT.liftF(repository.registrarAsistencia(asistencia))
    yield ()

    process.value

  override def registrarScorecard(postulanteId: Int, scorecard: Scorecard): F[Either[CapacitacionError, Unit]] =
    val process: EitherT[F, CapacitacionError, Unit] = for
      // 1. Verificar si existe el postulante
      exists <- EitherT.liftF(repository.existsPostulante(postulanteId))
      _      <- EitherT.cond[F](exists, (), CapacitacionError.PostulanteNotFound())

      // 2. Registrar Scorecard
      _      <- EitherT.liftF(repository.registrarScorecard(scorecard))

      // 3. Actualizar estado del postulante
      nuevoEstado = if (scorecard.estadoScorecard == ScorecardEstado.APTO && scorecard.validacionOperativa) PostulanteEstado.ALTA
                    else PostulanteEstado.NO_APTO
      _      <- EitherT.liftF(repository.actualizarEstadoPostulante(postulanteId, nuevoEstado))

      // 4. Calcular bono y cortes
      asistenciasValidas <- EitherT.liftF(repository.countAsistenciasValidas(postulanteId))
      montoCalculado = asistenciasValidas * 15.00

      // Calcular fecha de pago y corte basado en la fecha del sistema (01-15 -> Corte 1, 16-31 -> Corte 2)
      hoy = LocalDate.now()
      diaDelMes = hoy.getDayOfMonth
      (corte, fechaPago) = if (diaDelMes <= 15)
                             (1, LocalDate.of(hoy.getYear, hoy.getMonthValue, 18))
                           else
                             val proximoMes = hoy.plusMonths(1)
                             (2, LocalDate.of(proximoMes.getYear, proximoMes.getMonthValue, 5))

      bono = BonoCapacitacion(postulanteId, asistenciasValidas, montoCalculado, corte, fechaPago)
      _      <- EitherT.liftF(repository.registrarBono(bono))
    yield ()

    process.value

  override def obtenerResumen(postulanteId: Int): F[Either[CapacitacionError, PostulanteResumen]] =
    repository.getResumen(postulanteId).map {
      case Some(resumen) => Right(resumen)
      case None => Left(CapacitacionError.PostulanteNotFound())
    }
