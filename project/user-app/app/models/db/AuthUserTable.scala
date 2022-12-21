package models.db

// AUTO-GENERATED Slick data model for table AuthUser
trait AuthUserTable {

  self: Tables =>

  import profile.api._
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** Entity class storing rows of table AuthUser
   *
   * @param id        Database column id SqlType(serial), AutoInc, PrimaryKey
   * @param login     Database column login SqlType(varchar)
   * @param password  Database column password SqlType(varchar), Default(None)
   * @param email     Database column email SqlType(varchar), Default()
   * @param firstName Database column first_name SqlType(varchar), Default()
   * @param lastName  Database column last_name SqlType(varchar), Default() */
  case class AuthUserRow(id: Int, login: String, password: Option[String] = None, email: String = "", firstName: String = "", lastName: String = "")

  /** GetResult implicit for fetching AuthUserRow objects using plain SQL queries */
  implicit def GetResultAuthUserRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[String]]): GR[AuthUserRow] = GR {
    prs =>
      import prs._
      AuthUserRow.tupled((<<[Int], <<[String], <<?[String], <<[String], <<[String], <<[String]))
  }

  /** Table description of table auth_user. Objects of this class serve as prototypes for rows in queries. */
  class AuthUser(_tableTag: Tag) extends profile.api.Table[AuthUserRow](_tableTag, "auth_user") {
    def * = (id, login, password, email, firstName, lastName) <> (AuthUserRow.tupled, AuthUserRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(login), password, Rep.Some(email), Rep.Some(firstName), Rep.Some(lastName))).shaped.<>({ r => import r._; _1.map(_ => AuthUserRow.tupled((_1.get, _2.get, _3, _4.get, _5.get, _6.get))) }, (_: Any) => throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column login SqlType(varchar) */
    val login: Rep[String] = column[String]("login")
    /** Database column password SqlType(varchar), Default(None) */
    val password: Rep[Option[String]] = column[Option[String]]("password", O.Default(None))
    /** Database column email SqlType(varchar), Default() */
    val email: Rep[String] = column[String]("email", O.Default(""))
    /** Database column first_name SqlType(varchar), Default() */
    val firstName: Rep[String] = column[String]("first_name", O.Default(""))
    /** Database column last_name SqlType(varchar), Default() */
    val lastName: Rep[String] = column[String]("last_name", O.Default(""))

    /** Uniqueness Index over (login) (database name auth_user_login_key) */
    val index1 = index("auth_user_login_key", login, unique = true)
  }

  /** Collection-like TableQuery object for table AuthUser */
  lazy val AuthUser = new TableQuery(tag => new AuthUser(tag))
}
