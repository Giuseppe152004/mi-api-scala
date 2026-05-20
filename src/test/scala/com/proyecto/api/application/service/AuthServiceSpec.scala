package com.proyecto.api.application.service

import cats.effect.IO
import munit.CatsEffectSuite
import com.proyecto.api.application.port.out.{PasswordHasher, TokenService, UserRepository}
import com.proyecto.api.domain.model.{DocumentType, DocumentNumber, Password, UserCredentials, User, AuthToken}
import com.proyecto.api.domain.error.AuthError

class AuthServiceSpec extends CatsEffectSuite {

  // Mocks manuales y simples (sin frameworks extra de mocking)
  class MockUserRepository(users: Map[(DocumentType, DocumentNumber), User]) extends UserRepository[IO] {
    def findUser(docType: DocumentType, docNum: DocumentNumber): IO[Option[User]] = 
      IO.pure(users.get((docType, docNum)))
  }

  class MockPasswordHasher extends PasswordHasher[IO] {
    def hash(password: Password): IO[String] = IO.pure(password.value.reverse)
    def verify(password: Password, hashed: String): IO[Boolean] = IO.pure(password.value.reverse == hashed)
  }

  class MockTokenService extends TokenService[IO] {
    def generateToken(docType: DocumentType, docNum: DocumentNumber): IO[AuthToken] = 
      IO.pure(AuthToken(s"fake-token-${docType}-${docNum.value}"))
    def validateToken(token: String): IO[Either[AuthError, (DocumentType, DocumentNumber)]] = 
      IO.pure(Left(AuthError.InvalidToken()))
  }

  // Escenario de prueba (fixtures)
  val testUserDni = User(DocumentType.DNI, DocumentNumber("12345678"), "drowssap") // "password" al revés
  val testUserCe = User(DocumentType.CE, DocumentNumber("000123456"), "terces")    // "secret" al revés
  
  val userRepo = new MockUserRepository(Map(
    (DocumentType.DNI, DocumentNumber("12345678")) -> testUserDni,
    (DocumentType.CE, DocumentNumber("000123456")) -> testUserCe
  ))
  
  val authService = new AuthService[IO](userRepo, new MockPasswordHasher(), new MockTokenService())

  test("Login exitoso con DNI debe generar un token válido") {
    val creds = UserCredentials(DocumentType.DNI, DocumentNumber("12345678"), Password("password"))
    authService.login(creds).map { result =>
      assertEquals(result, Right(AuthToken("fake-token-DNI-12345678")))
    }
  }

  test("Login exitoso con CE debe generar un token válido") {
    val creds = UserCredentials(DocumentType.CE, DocumentNumber("000123456"), Password("secret"))
    authService.login(creds).map { result =>
      assertEquals(result, Right(AuthToken("fake-token-CE-000123456")))
    }
  }

  test("Login debe fallar con UserNotFound si el documento no existe") {
    val creds = UserCredentials(DocumentType.DNI, DocumentNumber("00000000"), Password("password"))
    authService.login(creds).map { result =>
      assertEquals(result, Left(AuthError.UserNotFound()))
    }
  }

  test("Login debe fallar con InvalidCredentials si la contraseña es incorrecta") {
    val creds = UserCredentials(DocumentType.DNI, DocumentNumber("12345678"), Password("wrong_password"))
    authService.login(creds).map { result =>
      assertEquals(result, Left(AuthError.InvalidCredentials()))
    }
  }
}
