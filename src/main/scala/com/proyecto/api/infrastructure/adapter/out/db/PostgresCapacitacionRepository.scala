package com.proyecto.api.infrastructure.adapter.out.db

import cats.effect.IO
import com.proyecto.api.application.port.out.CapacitacionRepository
import com.proyecto.api.domain.model.*
import io.circe.parser.parse

class PostgresCapacitacionRepository(transactor: PostgresTransactor) extends CapacitacionRepository[IO]:

  override def existsPostulante(id: Int): IO[Boolean] =
    transactor.connection.use { conn =>
      IO.blocking {
        val stmt = conn.prepareStatement("SELECT 1 FROM training_dev.postulants WHERE id_postulant = ?")
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
        val stmt = conn.prepareStatement("SELECT document_type, document_number, first_name || ' ' || last_name AS nombre FROM training_dev.postulants WHERE id_postulant = ?")
        try
          stmt.setInt(1, id)
          val rs = stmt.executeQuery()
          try
            if (rs.next()) {
              val docType = if (rs.getString("document_type") == "DNI") DocumentType.DNI else DocumentType.CE
              val docNum = DocumentNumber(rs.getString("document_number"))
              val nombre = rs.getString("nombre")
              Some((docType, docNum, nombre))
            } else {
              None
            }
          finally rs.close()
        finally stmt.close()
      }
    }

  override def registrarAsistencia(postulanteId: Int, diaCapacitacion: Int, asistio: Boolean): IO[Unit] =
    transactor.connection.use { conn =>
      IO.blocking {
        val sql = """
          INSERT INTO training_dev.daily_control (id_postulant, training_day, attendance, attendance_date)
          VALUES (?, ?, ?, CURRENT_DATE)
          ON CONFLICT (id_postulant, training_day) 
          DO UPDATE SET 
              attendance = EXCLUDED.attendance,
              attendance_date = CURRENT_DATE;
        """
        val stmt = conn.prepareStatement(sql)
        try
          stmt.setInt(1, postulanteId)
          stmt.setInt(2, diaCapacitacion)
          stmt.setBoolean(3, asistio)
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
          FROM training_dev.daily_control
          WHERE id_postulant = ? AND attendance = true;
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
              p.id_postulant AS "postulanteId",
              CONCAT(p.first_name, ' ', p.last_name) AS "nombreCompleto",
              p.document_type AS "tipoDocumento",
              p.document_number AS "numeroDocumento",
              
              -- Cuenta total de días asistidos marcados con attendance=true
              COUNT(CASE WHEN dc.attendance = true THEN 1 END)::INT AS "asistenciasRegistradas",
              
              -- Multiplica por S/ 15 únicamente si el día de capacitación está entre el 1 y el 7
              SUM(CASE WHEN dc.training_day BETWEEN 1 AND 7 AND dc.attendance = true THEN 15.00 ELSE 0.00 END)::DOUBLE PRECISION AS "montoBonoAcumulado",
              
              -- Si aún no se evalúa el scorecard, por defecto se asume 'EN_CAPACITACION'
              COALESCE(ps.code, 'EN_CAPACITACION') AS "estadoScorecard",
              p.operational_validation AS "validacionOperativa",
              
              -- Regla de Cortes: Días 1-15 (Corte 1) | Días 16-31 (Corte 2)
              CASE 
                  WHEN EXTRACT(DAY FROM p.start_date) BETWEEN 1 AND 15 THEN 1
                  ELSE 2
              END::INT AS "corteOperativo",
              
              -- Fecha de Pago: Corte 1 paga el 18 del mes siguiente. Corte 2 paga el 5 del subsiguiente.
              CASE 
                  WHEN EXTRACT(DAY FROM p.start_date) BETWEEN 1 AND 15 
                      THEN (p.start_date + INTERVAL '1 month')::DATE - EXTRACT(DAY FROM p.start_date + INTERVAL '1 month')::INT + 18
                  ELSE 
                      (p.start_date + INTERVAL '2 month')::DATE - EXTRACT(DAY FROM p.start_date + INTERVAL '2 month')::INT + 5
              END AS "fechaPagoEstimada"

          FROM training_dev.postulants p
          LEFT JOIN training_dev.postulant_statuses ps ON p.id_state = ps.id_state
          LEFT JOIN training_dev.daily_control dc ON p.id_postulant = dc.id_postulant
          WHERE p.id_postulant = ?
          GROUP BY p.id_postulant, ps.code, p.first_name, p.last_name, p.document_type, p.document_number, p.operational_validation, p.start_date;
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

              val valOperativa = rs.getBoolean("validacionOperativa")
              val date = rs.getDate("fechaPagoEstimada")
              val localDate = if (date != null) Some(date.toLocalDate) else None

              // Segunda consulta: Historial detallado de asistencias día a día
              val sqlDetalle = """
                SELECT 
                    dc.training_day AS dia,
                    CASE WHEN dc.attendance THEN 'A' ELSE 'F' END AS estado,
                    CASE 
                        WHEN dc.training_day BETWEEN 1 AND 2 THEN 'Manual'
                        ELSE 'Nexus'
                    END AS origen
                FROM training_dev.daily_control dc
                WHERE dc.id_postulant = ?
                ORDER BY dc.training_day;
              """
              val stmtDetalle = conn.prepareStatement(sqlDetalle)
              val historial = try
                stmtDetalle.setInt(1, postulanteId)
                val rsDetalle = stmtDetalle.executeQuery()
                try
                  val buffer = scala.collection.mutable.ListBuffer[AsistenciaDetalle]()
                  while (rsDetalle.next()) {
                    buffer += AsistenciaDetalle(
                      dia = rsDetalle.getInt("dia"),
                      estado = rsDetalle.getString("estado"),
                      origen = rsDetalle.getString("origen")
                    )
                  }
                  buffer.toList
                finally rsDetalle.close()
              finally stmtDetalle.close()

              Some(PostulanteResumen(
                postulanteId = rs.getInt("postulanteId"),
                nombreCompleto = rs.getString("nombreCompleto"),
                tipoDocumento = docType,
                numeroDocumento = docNum,
                asistenciasRegistradas = rs.getInt("asistenciasRegistradas"),
                montoBonoAcumulado = rs.getDouble("montoBonoAcumulado"),
                estadoScorecard = Some(score),
                validacionOperativa = Some(valOperativa),
                corteOperativo = Some(rs.getInt("corteOperativo")),
                fechaPagoEstimada = localDate,
                estadoGeneral = estadoGeneral,
                historialAsistencias = historial
              ))
            } else {
              None
            }
          finally rs.close()
        finally stmt.close()
      }
    }

  override def listarPostulantes(): IO[List[PostulanteBasico]] =
    transactor.connection.use { conn =>
      IO.blocking {
        val sql = """
          SELECT 
              p.id_postulant AS id,
              CONCAT(p.first_name, ' ', p.last_name) AS nombre_completo,
              COALESCE(p.position, 'Sin asignar') AS puesto,
              COALESCE(ps.code, 'EN_CAPACITACION') AS estado
          FROM training_dev.postulants p
          LEFT JOIN training_dev.postulant_statuses ps ON p.id_state = ps.id_state
          ORDER BY p.id_postulant;
        """
        val stmt = conn.prepareStatement(sql)
        try
          val rs = stmt.executeQuery()
          try
            val buffer = scala.collection.mutable.ListBuffer[PostulanteBasico]()
            while (rs.next()) {
              val estado = rs.getString("estado") match {
                case "ALTA" => PostulanteEstado.ALTA
                case "NO_APTO" => PostulanteEstado.NO_APTO
                case _ => PostulanteEstado.EN_CAPACITACION
              }
              buffer += PostulanteBasico(
                id = rs.getInt("id"),
                nombreCompleto = rs.getString("nombre_completo"),
                puesto = rs.getString("puesto"),
                estado = estado
              )
            }
            buffer.toList
          finally rs.close()
        finally stmt.close()
      }
    }

  override def listarPostulantesPorUsuario(idUser: Int): IO[List[PostulanteDetalle]] =
    transactor.connection.use { conn =>
      IO.blocking {
        val sql = """
          SELECT 
              p.id_postulant AS "postulanteId",
              p.first_name AS "nombres",
              p.last_name AS "apellidos",
              p.document_type AS "tipoDocumento",
              p.document_number AS "numeroDocumento",
              p.id_portfolio AS "idPortfolio",
              p.id_campaign AS "idCampaign",
              COALESCE(ps.code, 'EN_CAPACITACION') AS "estadoScorecard",
              (
                  SELECT COALESCE(json_agg(json_build_object(
                      'dia', sub_dc.training_day,
                      'asistio', sub_dc.attendance
                  ) ORDER BY sub_dc.training_day), '[]'::json)
                  FROM training_dev.daily_control sub_dc
                  WHERE sub_dc.id_postulant = p.id_postulant
              ) AS "historialAsistencias"
          FROM training_dev.postulants p
          LEFT JOIN training_dev.postulant_statuses ps ON p.id_state = ps.id_state
          WHERE p.id_user = ? 
          ORDER BY p.last_name ASC;
        """
        val stmt = conn.prepareStatement(sql)
        try
          stmt.setInt(1, idUser)
          val rs = stmt.executeQuery()
          try
            val buffer = scala.collection.mutable.ListBuffer[PostulanteDetalle]()
            while (rs.next()) {
              // Parsear el JSON del json_agg con Circe
              val jsonStr = rs.getString("historialAsistencias")
              val historial = parse(jsonStr).toOption
                .flatMap(_.asArray)
                .map(_.toList.flatMap { json =>
                  for
                    dia     <- json.hcursor.downField("dia").as[Int].toOption
                    asistio <- json.hcursor.downField("asistio").as[Boolean].toOption
                  yield HistorialAsistencia(dia, asistio)
                })
                .getOrElse(List.empty)

              val idPortfolio = rs.getInt("idPortfolio")
              val idCampaign = rs.getInt("idCampaign")

              buffer += PostulanteDetalle(
                postulanteId = rs.getInt("postulanteId"),
                nombres = rs.getString("nombres"),
                apellidos = rs.getString("apellidos"),
                tipoDocumento = rs.getString("tipoDocumento"),
                numeroDocumento = rs.getString("numeroDocumento"),
                idPortfolio = if (rs.wasNull()) None else Some(idPortfolio),
                idCampaign = if (rs.wasNull()) None else Some(idCampaign),
                estadoScorecard = rs.getString("estadoScorecard"),
                historialAsistencias = historial
              )
            }
            buffer.toList
          finally rs.close()
        finally stmt.close()
      }
    }
