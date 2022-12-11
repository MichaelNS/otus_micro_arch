package models

import models.db.Tables

case class Entity[T](id: Int, data: T)

case class AuthUser(login: String, password: Option[String] = None, email: String, firstName: String, lastName: String) {
  def toRow: Tables.AuthUserRow = {
    Tables.AuthUserRow(
      id = -1,
      login = login,
      password = password,
      email = email,
      firstName = firstName,
      lastName = lastName
    )
  }
}

object AuthUser {
  def apply(row: Tables.AuthUserRow): Entity[AuthUser] = {
    Entity(
      id = row.id,
      data = AuthUser(
        login = row.login,
        password = row.password,
        email = row.email,
        firstName = row.firstName,
        lastName = row.lastName
      )
    )
  }
}
