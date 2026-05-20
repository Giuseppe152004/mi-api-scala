package com.proyecto.api.domain.model

enum DocumentType:
  case DNI, CE

// Value objects para mayor tipado y seguridad
case class DocumentNumber(value: String)
case class Password(value: String)

case class UserCredentials(
    documentType: DocumentType,
    documentNumber: DocumentNumber,
    password: Password
)

case class AuthToken(value: String)

case class User(
    documentType: DocumentType,
    documentNumber: DocumentNumber,
    hashedPassword: String
)
