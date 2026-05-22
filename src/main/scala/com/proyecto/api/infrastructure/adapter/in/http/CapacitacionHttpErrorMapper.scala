package com.proyecto.api.infrastructure.adapter.in.http

import sttp.model.StatusCode
import com.proyecto.api.domain.error.CapacitacionError

object CapacitacionHttpErrorMapper:
  /**
   * Mapea un error de dominio de Capacitación a su correspondiente código de estado HTTP 
   * y ErrorResponse estandarizado.
   */
  def mapToHttp(error: CapacitacionError): (StatusCode, ErrorResponse) = error match
    case e: CapacitacionError.PostulanteNotFound =>
      (StatusCode.NotFound, ErrorResponse("POSTULANTE_NOT_FOUND", e.message))
    case e: CapacitacionError.InvalidDiaCapacitacion =>
      (StatusCode.BadRequest, ErrorResponse("INVALID_DIA_CAPACITACION", e.message))
    case e: CapacitacionError.DatabaseError =>
      (StatusCode.InternalServerError, ErrorResponse("DATABASE_ERROR", e.message))
