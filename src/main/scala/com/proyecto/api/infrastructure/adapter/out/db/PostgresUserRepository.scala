package com.proyecto.api.infrastructure.adapter.out.db

import cats.effect.IO
import com.proyecto.api.application.port.out.UserRepository
import com.proyecto.api.domain.model.*

class PostgresUserRepository(transactor: PostgresTransactor) extends UserRepository[IO]:

  override def findUser(docType: DocumentType, docNum: DocumentNumber, requestIp: String): IO[Option[User]] =
    transactor.connection.use { conn =>
      IO.blocking {
        val sql = """
          UPDATE training_dev.postulantes 
          SET ultimo_log = 'IP: ' || ? || ' | UA: Desktop App | Fecha: ' || NOW()
          WHERE tipo_documento = ? 
            AND numero_documento = ?
          RETURNING id, nombres, apellidos, observaciones;
        """
        val stmt = conn.prepareStatement(sql)
        try
          stmt.setString(1, requestIp) // Usamos la IP real enviada por el frontend
          stmt.setString(2, docType.toString)
          stmt.setString(3, docNum.value)
          
          val rs = stmt.executeQuery()
          try
            if (rs.next()) {
              // Si el DNI existe en la tabla, el RETURNING devuelve datos y entra aquí.
              val nombres       = rs.getString("nombres")
              val apellidos     = rs.getString("apellidos")
              val observaciones = Option(rs.getString("observaciones"))
              Some(User(docType, docNum, nombres, apellidos, observaciones))
            } else {
              // Si el DNI no existe en la tabla, devuelve None (rechazado).
              None
            }
          finally rs.close()
        finally stmt.close()
      }
    }