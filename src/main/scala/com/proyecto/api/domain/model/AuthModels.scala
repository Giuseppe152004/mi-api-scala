package com.proyecto.api.domain.model

enum DocumentType:
  case DNI, CE

case class DocumentNumber(value: String)

// Cambiamos Password por requestIp
case class UserCredentials(
    documentType: DocumentType,
    documentNumber: DocumentNumber,
    requestIp: String
)

case class AuthToken(value: String)

case class User(
    documentType: DocumentType,
    documentNumber: DocumentNumber,
    nombres: String,
    apellidos: String,
    observaciones: Option[String]
)