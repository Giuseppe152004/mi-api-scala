package com.proyecto.api.infrastructure.adapter.out.db

import cats.effect.IO
import com.proyecto.api.application.port.out.UserRepository
import com.proyecto.api.domain.model.*

class PostgresUserRepository(transactor: PostgresTransactor) extends UserRepository[IO]:

  override def findUser(docType: DocumentType, docNum: DocumentNumber): IO[Option[User]] =
    transactor.connection.use { conn =>
      IO.blocking {
        val sql = """
          UPDATE training_dev.postulantes 
          SET ultimo_log = 'IP: ' || ? || ' | UA: ' || ? || ' | Fecha: ' || NOW()
          WHERE tipo_documento = ? 
            AND numero_documento = ?
          RETURNING id, nombres, apellidos;
        """
        val stmt = conn.prepareStatement(sql)
        try
          stmt.setString(1, "192.168.1.100")
          stmt.setString(2, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)...")
          stmt.setString(3, docType.toString)
          stmt.setString(4, docNum.value)
          
          val rs = stmt.executeQuery()
          try
            if (rs.next()) {
              // Si retorna fila, el login es exitoso. La contraseña es el mismo número de documento.
              Some(User(docType, docNum, docNum.value))
            } else {
              None
            }
          finally rs.close()
        finally stmt.close()
      }
    }
