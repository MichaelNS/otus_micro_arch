package controllers

import models.rest.FileCardSt
import play.api._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._
import services.db.DBService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class FileCardController @Inject()(cc: MessagesControllerComponents, val database: DBService, ws: WSClient)
  extends MessagesAbstractController(cc) {

  val logger: Logger = play.api.Logger(getClass)

  val smSyncDeviceStream = new SmSyncDeviceStream(cc, database, ws)

  case class FileCard(deviceUid: String,
                      impPath: String,
                      hSmBoFileCard: Seq[FileCardSt]
                     )

  implicit val fileCardStReads: Reads[FileCardSt] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "deviceUid").read[String] and
      (JsPath \ "fParent").read[String] and
      (JsPath \ "fName").read[String] and
      (JsPath \ "fExtension").readNullable[String] and
      (JsPath \ "fCreationDate").read[java.time.LocalDateTime] and
      (JsPath \ "fLastModifiedDate").read[java.time.LocalDateTime] and
      (JsPath \ "fSize").readNullable[Long] and
      (JsPath \ "fMimeTypeJava").readNullable[String] and
      (JsPath \ "fNameLc").read[String]
    ) (FileCardSt.apply _)

  implicit val fileCardReads: Reads[FileCard] = (
    (JsPath \ "deviceUid").read[String] and
      (JsPath \ "impPath").read[String] and
      (JsPath \ "hSmBoFileCard").read[Seq[FileCardSt]]
    ) (FileCard.apply _)

  def validateJson[A: Reads]: BodyParser[A] = parse.json.validate(
    _.validate[A].asEither.left.map(e => BadRequest(JsError.toJson(e)))
  )

  // https://books.underscore.io/essential-slick/essential-slick-3.html
  // 3.1.7 More Control over Inserts
  def create: Action[FileCard] = Action.async(validateJson[FileCard]) { request =>
    debugParam
    val fileCard = request.body

    debug(fileCard)

    smSyncDeviceStream.mergePath2Db(fileCard.deviceUid, fileCard.impPath, fileCard.hSmBoFileCard)

    Future.successful(Ok("created"))
  }
}