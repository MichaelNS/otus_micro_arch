package controllers

import models.AuthUser
import models.db.Tables
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import play.api.{Configuration, Logger}
import services.db.DBService
import utils.db.SmPostgresDriver.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class UserController @Inject()(implicit assetsFinder: AssetsFinder, config: Configuration, val database: DBService)
  extends InjectedController {

  val logger: Logger = play.api.Logger(getClass)

  // https://www.playframework.com/documentation/2.8.x/ScalaJsonHttp
  case class User(login: String, password: String, email: String, first_name: String, last_name: String)

  implicit val userReads: Reads[User] = (
    (JsPath \ "login").read[String] and
      (JsPath \ "password").read[String] and
      (JsPath \ "email").read[String] and
      (JsPath \ "first_name").read[String] and
      (JsPath \ "last_name").read[String]
    ) (User.apply _)

  // This helper parses and validates JSON using the implicit `placeReads`
  // above, returning errors if the parsed json fails validation.
  def validateJson[A: Reads]: BodyParser[A] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  def register: Action[User] = Action.async(validateJson[User]) { request =>
    // `request.body` contains a fully validated `User` instance.
    val user = request.body
    val row = Tables.AuthUserRow(-1, user.login, Option(user.password), user.email, user.last_name)
    database.runAsync((Tables.AuthUser returning Tables.AuthUser.map(_.id)) += AuthUser.apply(row).data.toRow)
      .map { rowSeq =>
        logger.info(rowSeq.toString)
        Ok(Json.obj("id" -> rowSeq, "status" -> "created"))
      }
      .recover { case ex: Throwable =>
        logger.error(s"register user to DB error - login/email already exists : ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
        BadRequest("login/email already exists")
      }
  }

  def check: Action[AnyContent] = Action.async { implicit request =>
    logger.info("check - start")
    logger.info("request.headers = " + request.headers)
    logger.info("request.cookies = " + request.cookies)
    logger.info("request.flash = " + request.flash)

    val userInfo = request.cookies.get("userInfo")
    val XUser = request.cookies.get("X-User")
    logger.info(s"userInfo = $userInfo")
    logger.info(s"XUser = $XUser")

    if (userInfo.isDefined) {
      logger.info("userInfo isDefined")
      logger.info("userInfo.Cookie = " + userInfo.get)
      logger.info("userInfo.name = " + userInfo.get.name)
      logger.info("userInfo.value = " + userInfo.get.value)
      Future.successful(Ok("User authenticated"))
    } else {
      logger.info("userInfo NOT isDefined")
      Future.successful(Unauthorized)
    }
  }

}
