import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._

import scala.language.postfixOps
import scalariform.formatter.preferences._

organization := "io.vamp"

name := "vamp-lifter"

version := VersionHelper.versionByTag

scalaVersion := "2.12.8"

scalariformSettings ++ Seq(ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(RewriteArrowSymbols, true))

// Libraries
val akka = "com.typesafe.akka" %% "akka-stream" % "2.5.23" ::
  "com.typesafe.akka" %% "akka-actor" % "2.5.23" ::
  "com.typesafe.akka" %% "akka-http" % "10.1.8" ::
  "com.typesafe.akka" %% "akka-parsing" % "10.1.8" ::
  ("com.typesafe.akka" %% "akka-slf4j" % "2.5.23" exclude("org.slf4j", "slf4j-api")) :: Nil

val bouncyCastle =
  "org.bouncycastle" % "bcprov-jdk15on" % "1.61" ::
  "org.bouncycastle" % "bcprov-ext-jdk15on" % "1.61" ::
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.61" :: Nil

val json4s = "org.json4s" %% "json4s-native" % "3.5.0" ::
  "org.json4s" %% "json4s-core" % "3.5.0" ::
  "org.json4s" %% "json4s-ext" % "3.5.0" :: Nil

val logging = "org.slf4j" % "slf4j-api" % "1.7.21" ::
  "ch.qos.logback" % "logback-classic" % "1.2.0" ::
  ("com.typesafe.scala-logging" %% "scala-logging" % "3.5.0" exclude("org.slf4j", "slf4j-api")) :: Nil

val sql = Seq(
  "org.postgresql" % "postgresql" % "42.2.5",
  "mysql" % "mysql-connector-java" % "8.0.16",
  "com.microsoft.sqlserver" % "mssql-jdbc" % "7.2.2.jre8" excludeAll ExclusionRule(organization = "org.bouncycastle"),
  "org.xerial" % "sqlite-jdbc" % "3.19.3"
) ++ bouncyCastle

val fp = "org.typelevel" %% "cats" % "0.9.0" ::
  "com.chuusai" %% "shapeless" % "2.3.2" :: Nil

val config = "com.typesafe" % "config" % "1.3.1" :: Nil

val vamp = "io.vamp" %% "vamp-bootstrap" % "katana" :: Nil
lazy val root = project.in(sbt.file(".")).settings(packAutoSettings).settings(
  libraryDependencies ++= vamp ++ akka ++ json4s ++ logging ++ sql ++ fp ++ config
)

scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++ Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")
