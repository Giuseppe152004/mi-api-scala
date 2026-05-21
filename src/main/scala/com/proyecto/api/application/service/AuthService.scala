package com.proyecto.api.application.service

import cats.Monad
import cats.data.EitherT
import cats.syntax.all.*
import com.proyecto.api.application.port.in.LoginUseCase
import com.proyecto.api.application.port.out.{TokenService, UserRepository}
import com.proyecto.api.domain.error.AuthError
import com.proyecto.api.domain.model.{AuthToken, UserCredentials}

/**
 * Implementación del caso de uso de Login.
 * Utiliza Cats (Monad y EitherT) para un control de flujo elegante y funcional.
 */
class AuthService[F[_]: Monad](
    userRepository: UserRepository[F],
    tokenService: TokenService[F]
) extends LoginUseCase[F]:

  override def login(credentials: UserCredentials): F[Either[AuthError, AuthToken]] =
    val process: EitherT[F, AuthError, AuthToken] = for
      // 1. Busca el DNI y registra su IP. Si no existe, corta el proceso con UserNotFound (404)
      userOpt <- EitherT.liftF(userRepository.findUser(credentials.documentType, credentials.documentNumber, credentials.requestIp))
      user    <- EitherT.fromOption[F](userOpt, AuthError.UserNotFound(): AuthError)
      
      // 2. Como el usuario es válido, se genera su Token con todos sus datos
      token   <- EitherT.liftF(tokenService.generateToken(user))
    yield token

    process.value