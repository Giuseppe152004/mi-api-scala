package com.proyecto.api.application.port.in

import com.proyecto.api.domain.model.*
import com.proyecto.api.domain.error.CapacitacionError

trait CapacitacionUseCase[F[_]]:
  def registrarAsistenciaManual(postulanteId: Int, dia: Int, codigo: AsistenciaCodigo): F[Either[CapacitacionError, Unit]]
  def registrarAsistenciaAutomatica(postulanteId: Int, dia: Int): F[Either[CapacitacionError, Unit]]
  def registrarScorecard(postulanteId: Int, scorecard: Scorecard): F[Either[CapacitacionError, Unit]]
  def obtenerResumen(postulanteId: Int): F[Either[CapacitacionError, PostulanteResumen]]
