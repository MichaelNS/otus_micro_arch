package controllers

import models.db.Tables
import play.api._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import services.db.DBService
import utils.db.SmPostgresDriver.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class DeviceController @Inject()(implicit assetsFinder: AssetsFinder, val database: DBService)
  extends InjectedController {

  val logger: Logger = play.api.Logger(getClass)

  case class Device(name: String,
                    label: String,
                    uuid: String)

  implicit val deviceReads: Reads[Device] = (
    (JsPath \ "name").read[String] and
      (JsPath \ "label").read[String] and
      (JsPath \ "uuid").read[String]
    ) (Device.apply _)

  def validateJson[A: Reads]: BodyParser[A] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  // https://books.underscore.io/essential-slick/essential-slick-3.html
  // 3.1.7 More Control over Inserts
  def create: Action[Device] = Action.async(validateJson[Device]) { request =>
    debugParam
    val device = request.body

    val data = Query((device.uuid, device.name, device.label))
    val exists = Tables.SmDevice.filter(m => m.uid === device.uuid).exists
    val selectExpression = data.filterNot(_ => exists)
    val forceAction = Tables.SmDevice.
      map(m => (m.uid, m.name, m.labelV)).
      forceInsertQuery(selectExpression)

    database.runAsync(forceAction).
      map { _ =>
        Ok("created")
      }.
      recover {
        case ex: Throwable =>
          logger.error(s"create Device DB error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
          BadRequest(s"create Device DB error ${ex.toString}")
      }
  }
}