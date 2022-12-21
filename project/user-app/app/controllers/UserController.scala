package controllers

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


  def me: Action[AnyContent] = Action.async { implicit request =>
    logger.info("request.headers = " + request.headers)
    logger.info("request.cookies = " + request.cookies)
    logger.info("request.flash = " + request.flash)

    val userInfo = request.cookies.get("userInfo")
    val XUser = request.cookies.get("X-User")
    logger.info("userInfo = " + userInfo)
    logger.info("XUser = " + XUser)

    if (XUser.isDefined) {
      logger.info("XUser isDefined")
      logger.info("XUser.Cookie = " + XUser.get)
      logger.info("XUser.value = " + XUser.get.value)

      database.runAsync(
        Tables.AuthUser
          .filter(_.login === XUser.get.value)
          .map(fld => (fld.login, fld.lastName, fld.firstName))
          .result
      ).map { rowSeq =>
        logger.info(rowSeq.toString())

        if (rowSeq.nonEmpty) {
          // TODO return full user JSON
          Ok(Json.obj("first_name" -> rowSeq.head._3))
        }
        else {
          logger.info(s"me - user ${XUser.get.value} not found")
          NotFound(s"user ${XUser.get.value} not found")
        }
      }
    } else
      Future.successful(Unauthorized)
  }

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

  def edit: Action[User] = Action.async(validateJson[User]) { request =>
    logger.info("edit")

    // `request.body` contains a fully validated `User` instance.
    val user = request.body
    val cookies = request.cookies

    val XUser = cookies.get("X-User")
    logger.info("XUser = " + XUser)
    logger.info("XUser.get.value = " + XUser.get.value)
    logger.info("user.login = " + user.login)

    if (XUser.isDefined && XUser.get.value == user.login) {
      database.runAsync(
        (for {
          uRow <- Tables.AuthUser if uRow.login === user.login
        } yield (uRow.password, uRow.email, uRow.firstName, uRow.lastName))
          .update((Option(user.password), user.email, user.first_name, user.last_name))
      ).map { _ =>
        Ok("ok")
        //        Ok("updated")
      }.recover {
        case ex: Throwable =>
          logger.error(s"update user to DB error : ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
          BadRequest("update user to DB error")
      }
    } else
      Future.successful(Unauthorized)
  }

}
