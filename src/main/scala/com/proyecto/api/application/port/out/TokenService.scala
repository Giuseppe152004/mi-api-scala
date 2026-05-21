package com.proyecto.api.application.port.out

import com.proyecto.api.domain.model.{AuthToken, DocumentNumber, DocumentType, User}
import com.proyecto.api.domain.error.AuthError

/**
 * Puerto de Salida para la generación y validación de tokens.
 */
trait TokenService[F[_]]:
  def generateToken(user: User): F[AuthToken]
  def validateToken(token: String): F[Either[AuthError, (DocumentType, DocumentNumber)]]

