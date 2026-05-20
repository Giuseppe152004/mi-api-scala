package com.proyecto.api.domain.model

import java.time.LocalDate

enum AsistenciaCodigo:
  case A, F

enum AsistenciaRegistroTipo:
  case Manual, Automatico

enum ScorecardEstado:
  case APTO, NO_APTO

enum PostulanteEstado:
  case EN_CAPACITACION, ALTA, NO_APTO

case class Asistencia(
    postulanteId: Int,
    diaCapacitacion: Int,
    codigoAsistencia: AsistenciaCodigo,
    tipoRegistro: AsistenciaRegistroTipo,
    registradoPor: String
)

case class Scorecard(
    postulanteId: Int,
    estadoScorecard: ScorecardEstado,
    validacionOperativa: Boolean,
    observaciones: String
)

case class BonoCapacitacion(
    postulanteId: Int,
    diasAsistidos: Int,
    montoAcumulado: Double,
    corteOperativo: Int,
    fechaPagoEstimada: LocalDate
)

case class PostulanteResumen(
    postulanteId: Int,
    nombreCompleto: String,
    tipoDocumento: DocumentType,
    numeroDocumento: DocumentNumber,
    asistenciasRegistradas: Int,
    montoBonoAcumulado: Double,
    estadoScorecard: Option[ScorecardEstado],
    validacionOperativa: Option[Boolean],
    corteOperativo: Option[Int],
    fechaPagoEstimada: Option[LocalDate],
    estadoGeneral: PostulanteEstado
)
