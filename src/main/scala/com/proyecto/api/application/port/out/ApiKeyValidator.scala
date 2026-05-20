package com.proyecto.api.application.port.out

/**
 * Puerto de Salida para validar las API Keys.
 */
trait ApiKeyValidator[F[_]]:
  def isValid(apiKey: String): F[Boolean]
