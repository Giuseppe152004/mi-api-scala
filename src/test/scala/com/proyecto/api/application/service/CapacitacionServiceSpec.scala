package com.proyecto.api.application.service

import cats.effect.IO
import munit.CatsEffectSuite
import java.time.LocalDate
import com.proyecto.api.application.port.out.CapacitacionRepository
import com.proyecto.api.domain.model.*
import com.proyecto.api.domain.error.CapacitacionError

class CapacitacionServiceSpec extends CatsEffectSuite {

  class MockCapacitacionRepository extends CapacitacionRepository[IO] {
    var asistencias = List.empty[(Int, Int, Boolean)] // (postulanteId, dia, asistio)

    def existsPostulante(id: Int): IO[Boolean] = 
      IO.pure(id == 145)

    def getPostulanteDocument(id: Int): IO[Option[(DocumentType, DocumentNumber, String)]] = 
      IO.pure(Some((DocumentType.DNI, DocumentNumber("12345678"), "Juan Pérez")))

    def registrarAsistencia(postulanteId: Int, diaCapacitacion: Int, asistio: Boolean): IO[Unit] = IO.delay {
      // Simula UPSERT: reemplaza si ya existe
      asistencias = asistencias.filterNot(a => a._1 == postulanteId && a._2 == diaCapacitacion)
      asistencias = asistencias :+ (postulanteId, diaCapacitacion, asistio)
    }

    def countAsistenciasValidas(postulanteId: Int): IO[Int] = 
      IO.pure(asistencias.count(a => a._1 == postulanteId && a._3))

    def registrarBono(b: BonoCapacitacion): IO[Unit] = IO.unit

    def getResumen(postulanteId: Int): IO[Option[PostulanteResumen]] = 
      IO.pure(Some(PostulanteResumen(
        postulanteId = postulanteId,
        nombreCompleto = "Juan Pérez",
        tipoDocumento = DocumentType.DNI,
        numeroDocumento = DocumentNumber("12345678"),
        asistenciasRegistradas = asistencias.count(a => a._1 == postulanteId && a._3),
        montoBonoAcumulado = 0.0,
        estadoScorecard = Some("EN_CAPACITACION"),
        validacionOperativa = None,
        corteOperativo = None,
        fechaPagoEstimada = None,
        estadoGeneral = PostulanteEstado.EN_CAPACITACION,
        historialAsistencias = asistencias.filter(_._1 == postulanteId).map(a =>
          AsistenciaDetalle(dia = a._2, estado = if (a._3) "A" else "F", origen = if (a._2 <= 2) "Manual" else "Nexus")
        )
      )))

    def listarPostulantes(): IO[List[PostulanteBasico]] =
      IO.pure(List(PostulanteBasico(145, "Juan Pérez", "Operador", PostulanteEstado.EN_CAPACITACION)))

    def listarPostulantesPorUsuario(idUser: Int): IO[List[PostulanteDetalle]] =
      IO.pure(List(PostulanteDetalle(
        postulanteId = 145,
        nombres = "Juan",
        apellidos = "Pérez",
        tipoDocumento = "DNI",
        numeroDocumento = "12345678",
        idPortfolio = Some(1),
        idCampaign = Some(10),
        estadoScorecard = "EN_CAPACITACION",
        historialAsistencias = List(HistorialAsistencia(1, true))
      )))
  }

  val postulanteId = 145

  test("Registro exitoso de asistencia (Día 1)") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    service.registrarAsistencia(postulanteId, 1, asistio = true).map { result =>
      assertEquals(result, Right(()))
      assertEquals(repo.asistencias.size, 1)
      assertEquals(repo.asistencias.head, (postulanteId, 1, true))
    }
  }

  test("Fallo de asistencia para Día 8 (fuera de rango)") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    service.registrarAsistencia(postulanteId, 8, asistio = true).map { result =>
      assertEquals(result, Left(CapacitacionError.InvalidDiaCapacitacion()))
    }
  }

  test("Fallo de asistencia para postulante inexistente") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    service.registrarAsistencia(999, 1, asistio = true).map { result =>
      assertEquals(result, Left(CapacitacionError.PostulanteNotFound()))
    }
  }

  test("UPSERT: actualizar asistencia existente") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    for {
      _ <- service.registrarAsistencia(postulanteId, 1, asistio = true)
      _ <- service.registrarAsistencia(postulanteId, 1, asistio = false)
    } yield {
      assertEquals(repo.asistencias.size, 1)
      assertEquals(repo.asistencias.head._3, false) // Actualizado a false
    }
  }

  test("Obtener resumen de postulante existente") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    service.obtenerResumen(postulanteId).map { result =>
      assert(result.isRight)
      assertEquals(result.toOption.get.nombreCompleto, "Juan Pérez")
    }
  }

  test("Listar mis postulantes devuelve datos correctos") {
    val repo = new MockCapacitacionRepository()
    val service = new CapacitacionService[IO](repo)

    service.listarMisPostulantes(1).map { lista =>
      assertEquals(lista.size, 1)
      assertEquals(lista.head.nombres, "Juan")
      assertEquals(lista.head.historialAsistencias.size, 1)
    }
  }
}
