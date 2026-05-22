package com.proyecto.api.application.service

import cats.Monad
import cats.data.EitherT
import cats.syntax.all.*
import com.proyecto.api.application.port.in.CapacitacionUseCase
import com.proyecto.api.application.port.out.CapacitacionRepository
import com.proyecto.api.domain.model.*
import com.proyecto.api.domain.error.CapacitacionError

class CapacitacionService[F[_]: Monad](
    repository: CapacitacionRepository[F]
) extends CapacitacionUseCase[F]:

  override def registrarAsistencia(postulanteId: Int, diaCapacitacion: Int, asistio: Boolean): F[Either[CapacitacionError, Unit]] =
    val process: EitherT[F, CapacitacionError, Unit] = for
      // 1. Verificar si existe el postulante
      exists <- EitherT.liftF(repository.existsPostulante(postulanteId))
      _      <- EitherT.cond[F](exists, (), CapacitacionError.PostulanteNotFound())

      // 2. Validar que el día esté entre 1 y 7
      _      <- EitherT.cond[F](diaCapacitacion >= 1 && diaCapacitacion <= 7, (), CapacitacionError.InvalidDiaCapacitacion())

      // 3. Registrar asistencia (UPSERT - idempotente)
      _      <- EitherT.liftF(repository.registrarAsistencia(postulanteId, diaCapacitacion, asistio))
    yield ()

    process.value

  override def obtenerResumen(postulanteId: Int): F[Either[CapacitacionError, PostulanteResumen]] =
    repository.getResumen(postulanteId).map {
      case Some(resumen) => Right(resumen)
      case None => Left(CapacitacionError.PostulanteNotFound())
    }

  override def listarPostulantes(): F[List[PostulanteBasico]] =
    repository.listarPostulantes()

  override def listarMisPostulantes(idUser: Int): F[List[PostulanteDetalle]] =
    repository.listarPostulantesPorUsuario(idUser)
