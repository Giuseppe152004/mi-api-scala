name := "mi-api-scala"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.3.7" // Actualizado a 3.3.7 LTS para total compatibilidad con Metals

// Recomendado para evitar problemas de hilos con cats-effect dentro de sbt
Compile / run / fork := true

val tapirVersion = "1.9.8"
val http4sVersion = "0.23.25"
val circeVersion = "0.14.6"

// Dependencias Core e Infraestructura HTTP
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "3.5.2",
  "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
  "com.softwaremill.sttp.apispec" %% "openapi-circe" % "0.7.3",
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "org.postgresql" % "postgresql" % "42.7.2",
  "com.auth0" % "java-jwt" % "4.4.0",
  // Dependencias para Testing
  "org.scalameta" %% "munit" % "1.0.0-M10" % Test,
  "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
)