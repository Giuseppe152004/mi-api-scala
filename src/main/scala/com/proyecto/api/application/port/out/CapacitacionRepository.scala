package com.proyecto.api.application.port.out

import com.proyecto.api.domain.model.*

trait CapacitacionRepository[F[_]]:
  def existsPostulante(id: Int): F[Boolean]
  def getPostulanteDocument(id: Int): F[Option[(DocumentType, DocumentNumber, String)]]
  def registrarAsistencia(postulanteId: Int, diaCapacitacion: Int, asistio: Boolean): F[Unit]
  def countAsistenciasValidas(postulanteId: Int): F[Int]
  def registrarBono(bono: BonoCapacitacion): F[Unit]
  def getResumen(postulanteId: Int): F[Option[PostulanteResumen]]
  def listarPostulantes(): F[List[PostulanteBasico]]
  def listarPostulantesPorUsuario(idUser: Int): F[List[PostulanteDetalle]]
