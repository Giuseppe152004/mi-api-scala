package com.proyecto.api.application.port.in

import com.proyecto.api.domain.model.{AuthToken, UserCredentials}
import com.proyecto.api.domain.error.AuthError

/**
 * Puerto de Entrada (Use Case) para el proceso de Login.
 * Utilizamos F[_] para mantener la abstracción del efecto (ej. IO de cats-effect).
 */
trait LoginUseCase[F[_]]:
  def login(credentials: UserCredentials): F[Either[AuthError, AuthToken]]
