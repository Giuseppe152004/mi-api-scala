package com.proyecto.api.infrastructure.adapter.in.http.auth

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.model.StatusCode
import io.circe.generic.auto.*
import cats.effect.IO
import com.proyecto.api.application.port.in.LoginUseCase
// AQUÍ ESTÁ EL CAMBIO: Ya no importamos Password
import com.proyecto.api.domain.model.{DocumentType, DocumentNumber, UserCredentials}
import com.proyecto.api.domain.error.AuthError
import com.proyecto.api.infrastructure.adapter.in.http.{ErrorResponse, HttpErrorMapper}
import com.proyecto.api.application.port.out.{ApiKeyValidator, TokenService}

class AuthEndpoints(
    loginUseCase: LoginUseCase[IO],
    apiKeyValidator: ApiKeyValidator[IO],
    tokenService: TokenService[IO]
):

  // 1. Endpoint base con mapeo centralizado de errores
  private val baseEndpoint = endpoint
    .errorOut(statusCode.and(jsonBody[ErrorResponse]))

  // 2. Endpoint de Login (Público)
  val loginEndpoint = baseEndpoint.post
    .in("api" / "auth" / "login")
    .in(jsonBody[LoginRequest])
    .out(jsonBody[LoginResponse])
    .summary("Inicia sesión verificando si DNI/CE existe y obtiene Token")
    .serverLogic { req =>
      val docType = if (req.documentType == "DNI") DocumentType.DNI else DocumentType.CE
      val creds = UserCredentials(docType, DocumentNumber(req.documentNumber), req.requestIp)
      
      loginUseCase.login(creds).map {
        case Left(err) => Left(HttpErrorMapper.mapToHttp(err))
        case Right(token) => Right(LoginResponse(token.value))
      }
    }

  // 3. Interceptor de Autenticación Híbrida (API Key OR Bearer Token)
  // Usamos composición en Tapir para que Swagger UI muestre ambas opciones
  private val hybridAuthInput = auth.apiKey(header[Option[String]]("X-API-Key"))
    .and(auth.bearer[Option[String]]())

  val secureEndpoint = baseEndpoint
    .securityIn(hybridAuthInput)
    .serverSecurityLogic { case (apiKeyOpt, bearerOpt) =>
      // Decisión dinámica
      val authResult: IO[Either[AuthError, Unit]] = (apiKeyOpt, bearerOpt) match {
        case (Some(key), _) =>
          apiKeyValidator.isValid(key).map { isValid =>
            if (isValid) Right(()) else Left(AuthError.InvalidApiKey())
          }
        case (_, Some(token)) =>
          tokenService.validateToken(token).map {
            case Left(err) => Left(err)
            case Right(_) => Right(())
          }
        case _ =>
          IO.pure(Left(AuthError.AccessDenied("Se requiere X-API-Key o Bearer Token")))
      }

      authResult.map {
        case Left(err) => Left(HttpErrorMapper.mapToHttp(err))
        case Right(_) => Right(())
      }
    }

  // 4. Ejemplo de ruta protegida consumiendo el interceptor
  val protectedHelloEndpoint = secureEndpoint.get
    .in("api" / "hello")
    .out(stringBody)
    .summary("Prueba de endpoint protegido")
    .serverLogic { _ => _ => 
      IO.pure(Right("¡Hola! Tienes acceso al sistema porque tu autenticación fue exitosa."))
    }