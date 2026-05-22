package com.proyecto.api.application.port.in

import com.proyecto.api.domain.model.*
import com.proyecto.api.domain.error.CapacitacionError

trait CapacitacionUseCase[F[_]]:
  def registrarAsistencia(postulanteId: Int, diaCapacitacion: Int, asistio: Boolean): F[Either[CapacitacionError, Unit]]
  def obtenerResumen(postulanteId: Int): F[Either[CapacitacionError, PostulanteResumen]]
  def listarPostulantes(): F[List[PostulanteBasico]]
  def listarMisPostulantes(idUser: Int): F[List[PostulanteDetalle]]
