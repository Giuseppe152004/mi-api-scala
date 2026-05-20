package com.proyecto.api.infrastructure.adapter.in.http

import sttp.model.StatusCode
import com.proyecto.api.domain.error.AuthError

object HttpErrorMapper:
  /**
   * Transforma un error de dominio puro (AuthError) a un código de estado HTTP 
   * y un ErrorResponse estandarizado para Tapir.
   */
  def mapToHttp(error: AuthError): (StatusCode, ErrorResponse) = error match
    case e: AuthError.InvalidCredentials => 
      (StatusCode.Unauthorized, ErrorResponse("UNAUTHORIZED", e.message))
    case e: AuthError.UserNotFound => 
      (StatusCode.NotFound, ErrorResponse("NOT_FOUND", e.message))
    case e: AuthError.InvalidToken => 
      (StatusCode.Unauthorized, ErrorResponse("INVALID_TOKEN", e.message))
    case e: AuthError.InvalidApiKey => 
      (StatusCode.Unauthorized, ErrorResponse("INVALID_API_KEY", e.message))
    case e: AuthError.AccessDenied => 
      (StatusCode.Forbidden, ErrorResponse("FORBIDDEN", e.message))
