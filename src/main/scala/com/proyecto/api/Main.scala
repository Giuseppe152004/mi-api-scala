package com.proyecto.api

import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import sttp.tapir.server.http4s.Http4sServerInterpreter
import com.proyecto.api.infrastructure.adapter.in.http.auth.AuthEndpoints
import com.proyecto.api.infrastructure.adapter.in.http.capacitacion.CapacitacionEndpoints
import com.proyecto.api.infrastructure.adapter.in.http.ScalarDocsRoutes
import com.proyecto.api.infrastructure.adapter.out.db.*
import com.proyecto.api.infrastructure.adapter.out.security.JwtTokenService
import com.proyecto.api.application.port.out.*
import com.proyecto.api.domain.model.*
import com.proyecto.api.domain.error.AuthError
import com.proyecto.api.application.service.{AuthService, CapacitacionService}

object Main extends IOApp.Simple:

  // 1. Servicios de utilidad y firma de Tokens
  val jwtSecret = sys.env.getOrElse("JWT_SECRET", "mi-api-scala-secreto-desarrollo-2026")
  val tokenService = new JwtTokenService(jwtSecret)

  val dummyApiKeyValidator = new ApiKeyValidator[IO]:
    def isValid(apiKey: String): IO[Boolean] = IO.pure(apiKey == "secret-api-key")

  // 2. Instanciar Transactor y Repositorios reales de PostgreSQL
  // Se eliminan por completo las simulaciones "dummy" para obligar al sistema a leer la BD
  val postgresTransactor = new PostgresTransactor()
  val realUserRepository = new PostgresUserRepository(postgresTransactor)
  val realCapacitacionRepository = new PostgresCapacitacionRepository(postgresTransactor)

  // 3. Instanciar Servicios (Capa de Aplicación acoplada a persistencia real)
  // AuthService ahora solo requiere el repositorio real y el generador de tokens JWT
  val authService = new AuthService[IO](realUserRepository, tokenService)
  val capacitacionService = new CapacitacionService[IO](realCapacitacionRepository)

  // 4. Instanciar Endpoints (Capa de Infraestructura HTTP)
  val authEndpoints = new AuthEndpoints(authService, dummyApiKeyValidator, tokenService)
  val capacitacionEndpoints = new CapacitacionEndpoints(capacitacionService, dummyApiKeyValidator, tokenService)

  // 5. Ensamblar rutas de la API (Tapir)
  val apiServerEndpoints = List(
    authEndpoints.loginEndpoint,
    authEndpoints.protectedHelloEndpoint
  ) ++ capacitacionEndpoints.endpoints

  val apiRoutes = Http4sServerInterpreter[IO]().toRoutes(apiServerEndpoints)

  // 6. Integrar Scalar UI para la documentación interactiva en /docs
  val docsRoutes = ScalarDocsRoutes.routes(
    apiServerEndpoints.map(_.endpoint),
    "API Autenticación Híbrida", "1.0"
  )

  val allRoutes = apiRoutes <+> docsRoutes

  // 7. Configurar Servidor Ember bajo un Resource seguro
  val serverResource = EmberServerBuilder
    .default[IO]
    .withHost(ipv4"0.0.0.0")
    .withPort(port"8081")
    .withHttpApp(allRoutes.orNotFound)
    .build

  def run: IO[Unit] = 
    serverResource.use { server =>
      IO.println(s"✅ Servidor levantado con éxito. Documentación Scalar en: http://localhost:${server.address.getPort}/docs") *>
      IO.never // Mantiene la ejecución del servidor activa de forma indefinida
    }