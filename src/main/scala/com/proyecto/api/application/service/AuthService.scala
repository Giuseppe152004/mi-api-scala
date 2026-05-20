package com.proyecto.api.application.service

import cats.Monad
import cats.data.EitherT
import cats.syntax.all.*
import com.proyecto.api.application.port.in.LoginUseCase
import com.proyecto.api.application.port.out.{PasswordHasher, TokenService, UserRepository}
import com.proyecto.api.domain.error.AuthError
import com.proyecto.api.domain.model.{AuthToken, UserCredentials}

/**
 * Implementación del caso de uso de Login.
 * Utiliza Cats (Monad y EitherT) para un control de flujo elegante y funcional.
 */
class AuthService[F[_]: Monad](
    userRepository: UserRepository[F],
    passwordHasher: PasswordHasher[F],
    tokenService: TokenService[F]
) extends LoginUseCase[F]:

  override def login(credentials: UserCredentials): F[Either[AuthError, AuthToken]] =
    val process: EitherT[F, AuthError, AuthToken] = for
      // 1. Buscar usuario por tipo y número de documento
      userOpt <- EitherT.liftF(userRepository.findUser(credentials.documentType, credentials.documentNumber))
      user    <- EitherT.fromOption[F](userOpt, AuthError.UserNotFound(): AuthError)
      
      // 2. Verificar que la contraseña coincida con el hash almacenado
      isValid <- EitherT.liftF(passwordHasher.verify(credentials.password, user.hashedPassword))
      _       <- EitherT.cond[F](isValid, (), AuthError.InvalidCredentials(): AuthError)
      
      // 3. Generar el Bearer Token para la sesión
      token   <- EitherT.liftF(tokenService.generateToken(user.documentType, user.documentNumber))
    yield token

    // Convertimos el EitherT[F, AuthError, AuthToken] de vuelta a F[Either[AuthError, AuthToken]]
    process.value
