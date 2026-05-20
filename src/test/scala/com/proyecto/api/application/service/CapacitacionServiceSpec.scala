package com.proyecto.api.application.service

import cats.effect.IO
import munit.CatsEffectSuite
import java.time.LocalDate
import com.proyecto.api.application.port.out.CapacitacionRepository
import com.proyecto.api.domain.model.*
import com.proyecto.api.domain.error.CapacitacionError

class CapacitacionServiceSpec extends CatsEffectSuite {

  class MockCapacitacionRepository extends CapacitacionRepository[IO] {
    var asistencias = List.empty[Asistencia]
    var scorecard: Option[Scorecard] = None
    var bono: Option[BonoCapacitacion] = None
    var estadoPostulante = PostulanteEstado.EN_CAPACITACION

    def existsPostulante(id: Int): IO[Boolean] = 
      IO.pure(id == 145)

    def getPostulanteDocument(id: Int): IO[Option[(DocumentType, DocumentNumber, String)]] = 
      IO.pure(Some((DocumentType.DNI, DocumentNumber("12345678"), "Juan Pérez")))

    def registrarAsistencia(asistencia: Asistencia): IO[Unit] = IO.delay {
      asistencias = asistencias :+ asistencia
    }

    def hasAsistencia(postulanteId: Int, dia: Int): IO[Boolean] = 
      IO.pure(asistencias.exists(a => a.postulanteId == postulanteId && a.diaCapacitacion == dia))

    def registrarScorecard(sc: Scorecard): IO[Unit] = IO.delay {
      scorecard = Some(sc)
    }

    def actualizarEstadoPostulante(postulanteId: Int, estado: PostulanteEstado): IO[Unit] = IO.delay {
      estadoPostulante = estado
    }

    def countAsistenciasValidas(postulanteId: Int): IO[Int] = 
      IO.pure(asistencias.count(a => a.postulanteId == postulanteId && a.codigoAsistencia == AsistenciaCodigo.A))

    def registrarBono(b: BonoCapacitacion): IO[Unit] = IO.delay {
      bono = Some(b)
    }

    def getResumen(postulanteId: Int): IO[Option[PostulanteResumen]] = 
      IO.pure(Some(PostulanteResumen(
        postulanteId = postulanteId,
        nombreCompleto = "Juan Pérez",
        tipoDocumento = DocumentType.DNI,
        numeroDocumento = DocumentNumber("12345678"),
        asistenciasRegistradas = asistencias.count(_.codigoAsistencia == AsistenciaCodigo.A),
        montoBonoAcumulado = bono.map(_.montoAcumulado).getOrElse(0.0),
        estadoScorecard = scorecard.map(_.estadoScorecard),
        validacionOperativa = scorecard.map(_.validacionOperativa),
        corteOperativo = bono.map(_.corteOperativo),
        fechaPagoEstimada = bono.map(_.fechaPagoEstimada),
        estadoGeneral = estadoPostulante
      )))
  }

  val postulanteId = 145

  test("Registro exitoso de asistencia manual (Día 1)") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    service.registrarAsistenciaManual(postulanteId, 1, AsistenciaCodigo.A).map { result =>
      assertEquals(result, Right(()))
      assertEquals(repo.asistencias.size, 1)
      assertEquals(repo.asistencias.head.diaCapacitacion, 1)
      assertEquals(repo.asistencias.head.codigoAsistencia, AsistenciaCodigo.A)
      assertEquals(repo.asistencias.head.tipoRegistro, AsistenciaRegistroTipo.Manual)
    }
  }

  test("Fallo de asistencia manual para Día 3") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    service.registrarAsistenciaManual(postulanteId, 3, AsistenciaCodigo.A).map { result =>
      assertEquals(result, Left(CapacitacionError.InvalidDiaManual()))
    }
  }

  test("Registro exitoso de asistencia automática (Día 3)") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    service.registrarAsistenciaAutomatica(postulanteId, 3).map { result =>
      assertEquals(result, Right(()))
      assertEquals(repo.asistencias.size, 1)
      assertEquals(repo.asistencias.head.diaCapacitacion, 3)
      assertEquals(repo.asistencias.head.codigoAsistencia, AsistenciaCodigo.A)
      assertEquals(repo.asistencias.head.tipoRegistro, AsistenciaRegistroTipo.Automatico)
    }
  }

  test("Fallo de asistencia automática para Día 2") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    service.registrarAsistenciaAutomatica(postulanteId, 2).map { result =>
      assertEquals(result, Left(CapacitacionError.InvalidDiaAutomatico()))
    }
  }

  test("Registro exitoso de Scorecard APTO y cálculo de bono") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    // Pre-cargar 5 asistencias válidas
    val setup = for {
      _ <- service.registrarAsistenciaManual(postulanteId, 1, AsistenciaCodigo.A)
      _ <- service.registrarAsistenciaManual(postulanteId, 2, AsistenciaCodigo.A)
      _ <- service.registrarAsistenciaAutomatica(postulanteId, 3)
      _ <- service.registrarAsistenciaAutomatica(postulanteId, 4)
      _ <- service.registrarAsistenciaAutomatica(postulanteId, 5)
    } yield ()

    setup.flatMap { _ =>
      val scorecard = Scorecard(postulanteId, ScorecardEstado.APTO, validacionOperativa = true, "Aprobado")
      service.registrarScorecard(postulanteId, scorecard).map { result =>
        assertEquals(result, Right(()))
        assertEquals(repo.estadoPostulante, PostulanteEstado.ALTA)
        assert(repo.bono.isDefined)
        assertEquals(repo.bono.get.diasAsistidos, 5)
        assertEquals(repo.bono.get.montoAcumulado, 75.00) // 5 * 15
        
        // Validar lógica de corte basada en día actual
        val hoy = LocalDate.now()
        if (hoy.getDayOfMonth <= 15) {
          assertEquals(repo.bono.get.corteOperativo, 1)
          assertEquals(repo.bono.get.fechaPagoEstimada, LocalDate.of(hoy.getYear, hoy.getMonthValue, 18))
        } else {
          assertEquals(repo.bono.get.corteOperativo, 2)
          val proximoMes = hoy.plusMonths(1)
          assertEquals(repo.bono.get.fechaPagoEstimada, LocalDate.of(proximoMes.getYear, proximoMes.getMonthValue, 5))
        }
      }
    }
  }
}
