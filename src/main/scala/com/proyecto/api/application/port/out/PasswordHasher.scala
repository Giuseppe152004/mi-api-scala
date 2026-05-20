package com.proyecto.api.application.port.out

import com.proyecto.api.domain.model.Password

/**
 * Puerto de Salida para delegar la lógica de hashing y verificación de contraseñas
 * (ej. BCrypt, Argon2) fuera del core de la aplicación.
 */
trait PasswordHasher[F[_]]:
  def hash(password: Password): F[String]
  def verify(password: Password, hashed: String): F[Boolean]
