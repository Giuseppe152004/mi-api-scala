package com.proyecto.api.application.port.out

import com.proyecto.api.domain.model.*

trait CapacitacionRepository[F[_]]:
  def existsPostulante(id: Int): F[Boolean]
  def getPostulanteDocument(id: Int): F[Option[(DocumentType, DocumentNumber, String)]]
  def registrarAsistencia(asistencia: Asistencia): F[Unit]
  def hasAsistencia(postulanteId: Int, dia: Int): F[Boolean]
  def registrarScorecard(scorecard: Scorecard): F[Unit]
  def actualizarEstadoPostulante(postulanteId: Int, estado: PostulanteEstado): F[Unit]
  def countAsistenciasValidas(postulanteId: Int): F[Int]
  def registrarBono(bono: BonoCapacitacion): F[Unit]
  def getResumen(postulanteId: Int): F[Option[PostulanteResumen]]
