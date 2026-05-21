package com.proyecto.api.infrastructure.adapter.out.db

import cats.effect.{IO, Resource}
import java.sql.{Connection, DriverManager}

class PostgresTransactor:
  private val url = "jdbc:postgresql://142.44.158.217:5432/ecosystem_dev?currentSchema=training_dev"
  private val user = "api_training_user"
  private val password = "Pepeluchoelquetequieremucho"

  // Carga explícita del driver JDBC de PostgreSQL
  Class.forName("org.postgresql.Driver")

  def connection: Resource[IO, Connection] =
    Resource.make(IO.blocking(DriverManager.getConnection(url, user, password)))(conn => IO.blocking(conn.close()))
