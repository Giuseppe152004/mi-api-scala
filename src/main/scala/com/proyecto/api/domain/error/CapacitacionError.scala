package com.proyecto.api.domain.error

enum CapacitacionError extends Exception:
  case PostulanteNotFound(message: String = "Postulante no encontrado") extends CapacitacionError
  case InvalidDiaCapacitacion(message: String = "El día de capacitación debe ser entre 1 y 7") extends CapacitacionError
  case DatabaseError(message: String) extends CapacitacionError
