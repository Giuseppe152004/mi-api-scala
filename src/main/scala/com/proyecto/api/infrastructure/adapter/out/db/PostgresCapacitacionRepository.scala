package com.proyecto.api.infrastructure.adapter.out.db

import cats.effect.IO
import com.proyecto.api.application.port.out.CapacitacionRepository
import com.proyecto.api.domain.model.*

class PostgresCapacitacionRepository(transactor: PostgresTransactor) extends CapacitacionRepository[IO]:

  override def existsPostulante(id: Int): IO[Boolean] =
    transactor.connection.use { conn =>
      IO.blocking {
        val stmt = conn.prepareStatement("SELECT 1 FROM training_dev.postulantes WHERE id = ?")
        try
          stmt.setInt(1, id)
          val rs = stmt.executeQuery()
          try rs.next()
          finally rs.close()
        finally stmt.close()
      }
    }

  override def getPostulanteDocument(id: Int): IO[Option[(DocumentType, DocumentNumber, String)]] =
    transactor.connection.use { conn =>
      IO.blocking {
        val stmt = conn.prepareStatement("SELECT tipo_documento, numero_documento, nombres || ' ' || apellidos AS nombre FROM training_dev.postulantes WHERE id = ?")
        try
          stmt.setInt(1, id)
          val rs = stmt.executeQuery()
          try
            if (rs.next()) {
              val docType = if (rs.getString("tipo_documento") == "DNI") DocumentType.DNI else DocumentType.CE
              val docNum = DocumentNumber(rs.getString("numero_documento"))
              val nombre = rs.getString("nombre")
              Some((docType, docNum, nombre))
            } else {
              None
            }
          finally rs.close()
        finally stmt.close()
      }
    }

  override def registrarAsistencia(asistencia: Asistencia): IO[Unit] =
    transactor.connection.use { conn =>
      IO.blocking {
        // Consulta SQL para Asistencia Manual y Automática
        val sql = """
          INSERT INTO training_dev.control_diario (postulante_id, fecha_asistencia, dia_capacitacion, tipo_asistencia_id)
          VALUES (
              ?, 
              CURRENT_DATE, 
              ?, 
              (SELECT id FROM training_dev.tipos_asistencia WHERE codigo = ?)
          );
        """
        val stmt = conn.prepareStatement(sql)
        try
          stmt.setInt(1, asistencia.postulanteId)
          stmt.setInt(2, asistencia.diaCapacitacion)
          stmt.setString(3, asistencia.codigoAsistencia.toString)
          stmt.executeUpdate()
          ()
        finally stmt.close()
      }
    }

  override def hasAsistencia(postulanteId: Int, dia: Int): IO[Boolean] =
    transactor.connection.use { conn =>
      IO.blocking {
        val stmt = conn.prepareStatement("SELECT 1 FROM training_dev.control_diario WHERE postulante_id = ? AND dia_capacitacion = ?")
        try
          stmt.setInt(1, postulanteId)
          stmt.setInt(2, dia)
          val rs = stmt.executeQuery()
          try rs.next()
          finally rs.close()
        finally stmt.close()
      }
    }

  override def registrarScorecard(scorecard: Scorecard): IO[Unit] =
    transactor.connection.use { conn =>
      IO.blocking {
        // En el esquema del DBA, el Scorecard actualiza el estado, validación operativa y observaciones directamente en el postulante
        val sql = """
          UPDATE training_dev.postulantes
          SET 
              estado_postulante_id = (SELECT id FROM training_dev.estados_postulante WHERE codigo = ?),
              validacion_operativa = ?,
              observaciones = ?
          WHERE id = ?;
        """
        val stmt = conn.prepareStatement(sql)
        try
          stmt.setString(1, scorecard.estadoScorecard.toString)
          stmt.setBoolean(2, scorecard.validacionOperativa)
          stmt.setString(3, scorecard.observaciones)
          stmt.setInt(4, scorecard.postulanteId)
          stmt.executeUpdate()
          ()
        finally stmt.close()
      }
    }

  override def actualizarEstadoPostulante(postulanteId: Int, estado: PostulanteEstado): IO[Unit] =
    transactor.connection.use { conn =>
      IO.blocking {
        val sql = """
          UPDATE training_dev.postulantes
          SET estado_postulante_id = (SELECT id FROM training_dev.estados_postulante WHERE codigo = ?)
          WHERE id = ?;
        """
        val stmt = conn.prepareStatement(sql)
        try
          stmt.setString(1, estado.toString)
          stmt.setInt(2, postulanteId)
          stmt.executeUpdate()
          ()
        finally stmt.close()
      }
    }

  override def countAsistenciasValidas(postulanteId: Int): IO[Int] =
    transactor.connection.use { conn =>
      IO.blocking {
        val sql = """
          SELECT COUNT(*)::INT 
          FROM training_dev.control_diario cd
          JOIN training_dev.tipos_asistencia ta ON cd.tipo_asistencia_id = ta.id
          WHERE cd.postulante_id = ? AND ta.codigo = 'A';
        """
        val stmt = conn.prepareStatement(sql)
        try
          stmt.setInt(1, postulanteId)
          val rs = stmt.executeQuery()
          try
            if (rs.next()) rs.getInt(1) else 0
          finally rs.close()
        finally stmt.close()
      }
    }

  override def registrarBono(bono: BonoCapacitacion): IO[Unit] =
    // Dado que el cálculo del bono se realiza dinámicamente en el SELECT analítico provisto por el DBA,
    // no se requiere almacenar el bono por duplicado en una tabla física.
    IO.unit

  override def getResumen(postulanteId: Int): IO[Option[PostulanteResumen]] =
    transactor.connection.use { conn =>
      IO.blocking {
        // La consulta SQL analítica de alta velocidad proporcionada por el DBA
        val sql = """
          SELECT 
              p.id AS "postulanteId",
              CONCAT(p.nombres, ' ', p.apellidos) AS "nombreCompleto",
              p.tipo_documento AS "tipoDocumento",
              p.numero_documento AS "numeroDocumento",
              
              -- Cuenta total de días asistidos marcados con 'A' (incluye extras para estadística)
              COUNT(CASE WHEN ta.codigo = 'A' THEN 1 END)::INT AS "asistenciasRegistradas",
              
              -- Multiplica por S/ 15 únicamente si el día de capacitación está entre el 1 y el 7
              SUM(CASE WHEN cd.dia_capacitacion BETWEEN 1 AND 7 AND ta.codigo = 'A' THEN 15.00 ELSE 0.00 END)::DOUBLE PRECISION AS "montoBonoAcumulado",
              
              -- Si aún no se evalúa el scorecard, por defecto se asume 'EN_CAPACITACION'
              COALESCE(ep.codigo, 'EN_CAPACITACION') AS "estadoScorecard",
              p.validacion_operativa AS "validacionOperativa",
              
              -- Regla de Cortes: Días 1-15 (Corte 1) | Días 16-31 (Corte 2)
              CASE 
                  WHEN EXTRACT(DAY FROM p.fecha_inicio) BETWEEN 1 AND 15 THEN 1
                  ELSE 2
              END::INT AS "corteOperativo",
              
              -- Fecha de Pago: Corte 1 paga el 18 del mes siguiente. Corte 2 paga el 5 del subsiguiente.
              CASE 
                  WHEN EXTRACT(DAY FROM p.fecha_inicio) BETWEEN 1 AND 15 
                      THEN (p.fecha_inicio + INTERVAL '1 month')::DATE - EXTRACT(DAY FROM p.fecha_inicio + INTERVAL '1 month')::INT + 18
                  ELSE 
                      (p.fecha_inicio + INTERVAL '2 month')::DATE - EXTRACT(DAY FROM p.fecha_inicio + INTERVAL '2 month')::INT + 5
              END AS "fechaPagoEstimada"

          FROM training_dev.postulantes p
          LEFT JOIN training_dev.estados_postulante ep ON p.estado_postulante_id = ep.id
          LEFT JOIN training_dev.control_diario cd ON p.id = cd.postulante_id
          LEFT JOIN training_dev.tipos_asistencia ta ON cd.tipo_asistencia_id = ta.id
          WHERE p.id = ?
          GROUP BY p.id, ep.codigo, p.nombres, p.apellidos, p.tipo_documento, p.numero_documento, p.validacion_operativa, p.fecha_inicio;
        """
        val stmt = conn.prepareStatement(sql)
        try
          stmt.setInt(1, postulanteId)
          val rs = stmt.executeQuery()
          try
            if (rs.next()) {
              val docType = if (rs.getString("tipoDocumento") == "DNI") DocumentType.DNI else DocumentType.CE
              val docNum = DocumentNumber(rs.getString("numeroDocumento"))
              val score = rs.getString("estadoScorecard")
              
              val estadoGeneral = score match {
                case "ALTA" => PostulanteEstado.ALTA
                case "NO_APTO" => PostulanteEstado.NO_APTO
                case _ => PostulanteEstado.EN_CAPACITACION
              }

              val scorecardEstado = score match {
                case "APTO" => Some(ScorecardEstado.APTO)
                case "NO_APTO" => Some(ScorecardEstado.NO_APTO)
                case _ => None
              }

              val valOperativa = rs.getBoolean("validacionOperativa")
              val date = rs.getDate("fechaPagoEstimada")
              val localDate = if (date != null) Some(date.toLocalDate) else None

              Some(PostulanteResumen(
                postulanteId = rs.getInt("postulanteId"),
                nombreCompleto = rs.getString("nombreCompleto"),
                tipoDocumento = docType,
                numeroDocumento = docNum,
                asistenciasRegistradas = rs.getInt("asistenciasRegistradas"),
                montoBonoAcumulado = rs.getDouble("montoBonoAcumulado"),
                estadoScorecard = scorecardEstado,
                validacionOperativa = Some(valOperativa),
                corteOperativo = Some(rs.getInt("corteOperativo")),
                fechaPagoEstimada = localDate,
                estadoGeneral = estadoGeneral
              ))
            } else {
              None
            }
          finally rs.close()
        finally stmt.close()
      }
    }
