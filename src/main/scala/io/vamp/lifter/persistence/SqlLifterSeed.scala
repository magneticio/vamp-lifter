package io.vamp.lifter.persistence

case class SqlLifterSeed(
  db:              String,
  user:            String,
  password:        String,
  createUrl:       String,
  vampDatabaseUrl: String)
