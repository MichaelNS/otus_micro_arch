package controllers

import models.db.Tables
import play.api.libs.json._
import play.api.mvc._
import play.api.{Configuration, Logger}
import services.db.DBService
import utils.db.SmPostgresDriver.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


@Singleton
class HomeController @Inject()(implicit assetsFinder: AssetsFinder, config: Configuration, val database: DBService)
  extends InjectedController {

  val logger: Logger = play.api.Logger(getClass)

  def index: Action[AnyContent] = Action.async {
    Future.successful(Ok("READY"))
  }

  def health: Action[AnyContent] = Action.async {
    logger.info("health")
    Future.successful(Ok(Json.obj("status" -> "OK")))
  }

  def pg: Action[AnyContent] = Action.async {
    database.runAsync(
      Tables.AuthUser
        .map(fld => (fld.id, fld.login, fld.lastName, fld.firstName))
        .result
    ).map { rowSeq =>
      logger.info(rowSeq.toString())

      Ok(rowSeq.toString())
    }
  }
}
