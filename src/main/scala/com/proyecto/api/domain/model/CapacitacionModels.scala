package com.proyecto.api.domain.model

import java.time.LocalDate

enum AsistenciaCodigo:
  case A, F

enum PostulanteEstado:
  case EN_CAPACITACION, ALTA, NO_APTO

case class BonoCapacitacion(
    postulanteId: Int,
    diasAsistidos: Int,
    montoAcumulado: Double,
    corteOperativo: Int,
    fechaPagoEstimada: LocalDate
)

/** DTO básico para el listado de postulantes en la pantalla principal */
case class PostulanteBasico(
    id: Int,
    nombreCompleto: String,
    puesto: String,
    estado: PostulanteEstado
)

/** Detalle de asistencia por día para la Hoja de Asistencia del Curso */
case class AsistenciaDetalle(
    dia: Int,
    estado: String,
    origen: String
)

case class PostulanteResumen(
    postulanteId: Int,
    nombreCompleto: String,
    tipoDocumento: DocumentType,
    numeroDocumento: DocumentNumber,
    asistenciasRegistradas: Int,
    montoBonoAcumulado: Double,
    estadoScorecard: Option[String],
    validacionOperativa: Option[Boolean],
    corteOperativo: Option[Int],
    fechaPagoEstimada: Option[LocalDate],
    estadoGeneral: PostulanteEstado,
    historialAsistencias: List[AsistenciaDetalle]
)

/** Historial de asistencia por día para el endpoint mis-postulantes */
case class HistorialAsistencia(
    dia: Int,
    asistio: Boolean
)

/** Detalle completo de un postulante asignado al capacitador */
case class PostulanteDetalle(
    postulanteId: Int,
    nombres: String,
    apellidos: String,
    tipoDocumento: String,
    numeroDocumento: String,
    idPortfolio: Option[Int],
    idCampaign: Option[Int],
    estadoScorecard: String,
    historialAsistencias: List[HistorialAsistencia]
)
