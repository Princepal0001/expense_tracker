name := """expense-tracker"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  guice,
  javaJdbc,
  "com.mysql" % "mysql-connector-j" % "9.3.0",
  "org.mindrot" % "jbcrypt" % "0.4"
)
