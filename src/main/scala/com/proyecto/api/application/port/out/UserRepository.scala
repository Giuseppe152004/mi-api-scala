package com.proyecto.api.application.port.out

import com.proyecto.api.domain.model.{DocumentNumber, DocumentType, User}

trait UserRepository[F[_]]:
  def findUser(docType: DocumentType, docNum: DocumentNumber, requestIp: String): F[Option[User]]