package com.proyecto.api.domain.error

enum AuthError extends Exception:
  case InvalidCredentials(message: String = "Credenciales inválidas") extends AuthError
  case UserNotFound(message: String = "Usuario no encontrado") extends AuthError
  case InvalidToken(message: String = "Token inválido o expirado") extends AuthError
  case InvalidApiKey(message: String = "API Key inválida") extends AuthError
  case AccessDenied(message: String = "Acceso denegado") extends AuthError
