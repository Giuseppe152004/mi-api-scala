package com.proyecto.api.application.port.out

import com.proyecto.api.domain.model.{DocumentNumber, DocumentType, AuthToken}
import com.proyecto.api.domain.error.AuthError

/**
 * Puerto de Salida para la generación y validación de tokens.
 */
trait TokenService[F[_]]:
  def generateToken(docType: DocumentType, docNum: DocumentNumber): F[AuthToken]
  def validateToken(token: String): F[Either[AuthError, (DocumentType, DocumentNumber)]]
