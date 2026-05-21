package com.proyecto.api.infrastructure.adapter.in.http.auth

case class LoginRequest(documentType: String, documentNumber: String, requestIp: String)
case class LoginResponse(token: String)