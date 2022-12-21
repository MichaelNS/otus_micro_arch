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
class HealthController @Inject()(val database: DBService)
  extends InjectedController {

  val logger: Logger = play.api.Logger(getClass)

  def health: Action[AnyContent] = Action.async {
    logger.info("health")
    Future.successful(Ok(Json.obj("status" -> "OK")))
  }

}
