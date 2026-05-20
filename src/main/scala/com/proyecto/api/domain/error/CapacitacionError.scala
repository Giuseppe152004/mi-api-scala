package com.proyecto.api.domain.error

enum CapacitacionError extends Exception:
  case PostulanteNotFound(message: String = "Postulante no encontrado") extends CapacitacionError
  case InvalidDiaManual(message: String = "El día de capacitación debe ser 1 o 2 para registro manual") extends CapacitacionError
  case InvalidDiaAutomatico(message: String = "El día de capacitación debe ser entre 3 y 7 para registro automático") extends CapacitacionError
  case AsistenciaDuplicada(message: String = "La asistencia para este día ya ha sido registrada") extends CapacitacionError
  case DatabaseError(message: String) extends CapacitacionError
