package com.proyecto.api.application.port.out

import com.proyecto.api.domain.model.{DocumentNumber, DocumentType, User}

/**
 * Puerto de Salida para interactuar con la persistencia de usuarios.
 */
trait UserRepository[F[_]]:
  def findUser(docType: DocumentType, docNum: DocumentNumber): F[Option[User]]
