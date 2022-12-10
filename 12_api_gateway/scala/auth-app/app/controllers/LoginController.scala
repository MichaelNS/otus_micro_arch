package controllers

import models.db.Tables
import play.api.Logger
import play.api.data.Form
import play.api.libs.json._
import play.api.mvc._
import services.db.DBService
import utils.db.SmPostgresDriver.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class LoginController @Inject()(userAction: UserInfoAction,
                                sessionGenerator: SessionGenerator,
                                cc: ControllerComponents,
                                val database: DBService
                               )(implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  val logger: Logger = play.api.Logger(getClass)

  def login: Action[AnyContent] = userAction.async { implicit request: UserRequest[AnyContent] =>
    logger.info("login - start")
    val successFunc = { userLogin: UserLogin =>
      val userInfo = UserInfo(userLogin.username)
      logger.info(s"login userInfo - ${userInfo}")
      database.runAsync(Tables.AuthUser.filter(fld => fld.login === userLogin.username && fld.password === userLogin.password).map(fld => (fld.id, fld.login, fld.email, fld.lastName, fld.firstName)).result)
        .map { rowSeq =>
          logger.info(s"login DB res - ${rowSeq}")
          if (rowSeq.nonEmpty) {
            val cookieRes =
              sessionGenerator.createSession(userInfo).map {
                case (sessionId, encryptedCookie) =>
                  val session = request.session + (SESSION_ID -> sessionId)

                  logger.info("login - redirect")

                  // TODO disable Redirect. nginx "401 Authorization Required"
                  // Redirect(routes.HomeController.index)
                  Ok(Json.obj("status" -> "ok"))
                    .withSession(session)
                    .withCookies(encryptedCookie)
                    .withCookies(Cookie("X-User", userLogin.username))
                // TODO del ?
                // .withHeaders(("X-User", userLogin.username))
              }

            // TODO rewrite
            import scala.concurrent.duration._
            Await.result(cookieRes, 3.seconds)
          } else {
            logger.info("login - user ${userInfo.username} not found")
            Unauthorized(s"user ${userInfo.username} not found")
          }
        }
    }

    val errorFunc = { badForm: Form[UserLogin] =>
      logger.info("login errorFunc")
      Future.successful {
        BadRequest(views.html.index(badForm)).flashing(FLASH_ERROR -> "Could not login!")
      }
    }

    form.bindFromRequest().fold(errorFunc, successFunc)
  }

}
