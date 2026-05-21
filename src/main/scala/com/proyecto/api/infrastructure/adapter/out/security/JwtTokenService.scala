package com.proyecto.api.infrastructure.adapter.out.security

import cats.effect.IO
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.proyecto.api.application.port.out.TokenService
import com.proyecto.api.domain.model.{AuthToken, DocumentNumber, DocumentType, User}
import com.proyecto.api.domain.error.AuthError

/**
 * Implementación real del puerto TokenService usando JWT (JSON Web Token)
 * con firma HMAC-SHA256 (HS256) mediante la librería java-jwt de Auth0.
 *
 * Genera tokens con claims estándar:
 * - sub: número de documento del usuario
 * - docType: tipo de documento (DNI/CE)
 * - iat: fecha de emisión
 * - exp: fecha de expiración (8 horas)
 */
class JwtTokenService(secret: String) extends TokenService[IO]:

  private val algorithm = Algorithm.HMAC256(secret)
  private val verifier  = JWT.require(algorithm).build()
  private val EXPIRATION_HOURS = 8L

  override def generateToken(user: User): IO[AuthToken] =
    IO.blocking {
      val now = Instant.now()
      val token = JWT.create()
        .withSubject(user.documentNumber.value)
        .withClaim("docType", user.documentType.toString)
        .withClaim("name", s"${user.nombres} ${user.apellidos}")
        .withClaim("nombres", user.nombres)
        .withClaim("apellidos", user.apellidos)
        .withClaim("observaciones", user.observaciones.getOrElse(""))
        .withIssuedAt(now)
        .withExpiresAt(now.plus(EXPIRATION_HOURS, ChronoUnit.HOURS))
        .sign(algorithm)
      AuthToken(token)
    }

  override def validateToken(token: String): IO[Either[AuthError, (DocumentType, DocumentNumber)]] =
    IO.blocking {
      try
        val decoded = verifier.verify(token)
        val docNum  = decoded.getSubject
        val docType = decoded.getClaim("docType").asString() match
          case "DNI" => DocumentType.DNI
          case "CE"  => DocumentType.CE
          case _     => throw new JWTVerificationException("Tipo de documento inválido en el token")

        Right((docType, DocumentNumber(docNum)))
      catch
        case _: JWTVerificationException =>
          Left(AuthError.InvalidToken())
    }
