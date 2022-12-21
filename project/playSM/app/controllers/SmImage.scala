package controllers


import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ThrottleMode
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import models.db.Tables
import play.api.http.HttpEntity
import play.api.mvc._
import play.api.{Configuration, Logger}
import ru.ns.model.OsConf
import ru.ns.tools.SmImageUtil.getGroupDirName
import ru.ns.tools.{FileUtils, SmImageUtil}
import services.db.DBService
import slick.basic.DatabasePublisher
import slick.jdbc.{ResultSetConcurrency, ResultSetType}
import utils.db.SmPostgresDriver.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._


/**
  * Created by ns on 29.10.2019
  */
@Singleton
class SmImage @Inject()(config: Configuration, val database: DBService)
  extends InjectedController {

  val logger: Logger = play.api.Logger(getClass)
  val maxResult: Int = config.get[Int]("Images.maxResult")
  val pathCache: String = config.get[String]("Images.pathCache")

  implicit val system: ActorSystem = ActorSystem()

  type DbImageRes = (String, String, Option[String], Option[String])

  val fetchSize = 400

  def resizeImage(deviceUid: String): Action[AnyContent] = Action.async {
    debugParam

    FileUtils.getDeviceInfo(deviceUid) map { device =>
      if (device.isDefined) {
        val mountPoint: String = device.head.mountpoint
        val dbFcStream: Source[DbImageRes, NotUsed] = getStreamImageByDevice(deviceUid)
        dbFcStream
          .throttle(elements = fetchSize, 10.millisecond, maximumBurst = 1, mode = ThrottleMode.Shaping)
          .mapAsync(2)(writeImageResizeToDb(_, mountPoint))
          .runWith(Sink.ignore)
      }
    }

    Future.successful(Ok("run resizeImage"))
  }

  def getStreamImageByDevice(deviceUid: String): Source[DbImageRes, NotUsed] = {

    // TODO add group by (fcRow.fParent, fcRow.fName, fcRow.fExtension, fcRow.sha256)
    // TODO add job_resize
    val queryRes = (for {
      fcRow <- Tables.SmFileCard
      if fcRow.deviceUid === deviceUid && fcRow.fMimeTypeJava === "image/jpeg" && fcRow.sha256.nonEmpty && !Tables.SmImageResize
        .filter(imgRes => fcRow.sha256 === imgRes.sha256 && fcRow.fName === imgRes.fName)
        .map(p => p.fName)
        .exists
    }
    yield (fcRow.fParent, fcRow.fName, fcRow.fExtension, fcRow.sha256)
      ).result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = fetchSize)
      .transactionally

    val databasePublisher: DatabasePublisher[DbImageRes] = database runStream queryRes
    val akkaSourceFromSlick: Source[DbImageRes, NotUsed] = Source fromPublisher databasePublisher

    akkaSourceFromSlick
  }

  def writeImageResizeToDb(cFc: (String, String, Option[String], Option[String]), mountPoint: String): Future[Future[(String, String)]] = {
    SmImageUtil.saveImageResize(
      pathCache,
      mountPoint + OsConf.fsSeparator + cFc._1 + cFc._2,
      cFc._2,
      cFc._3.getOrElse(""),
      cFc._4.get).map { file_id =>
      if (file_id.isDefined) {
        val cRow = Tables.SmImageResizeRow(file_id.get, cFc._4.get, cFc._2)
        database.runAsync((Tables.SmImageResize returning Tables.SmImageResize.map(r => (r.sha256, r.fName))) += models.SmImageResize.apply(cRow).data.toRow)
      } else {
        Future.successful(("", ""))
      }
    }
  }

  def viewImages(lstDevice: Seq[String], fParent: String): Action[AnyContent] = Action.async {
    val qry = (Tables.SmDevice
      .filter(_.labelV inSet lstDevice)
      .join(Tables.SmFileCard) on (_.uid === _.deviceUid))
      .filter(_._2.fParent === fParent)
      .join(Tables.SmImageResize).on { case ((_, fc), img) => fc.sha256 === img.sha256 && fc.fName === img.fName }
      .map(fld => (fld._2.fileId, fld._1._2.sha256, fld._1._2.fName, fld._1._2.fExtension, fld._1._2.fMimeTypeJava))
      .take(maxResult).result

    val resDb = for {
      mount <- FileUtils.getDevicesInfo()
      composedAction <- database.runAsync(qry)
    } yield (composedAction, mount)
    resDb.map { tt =>
      val dbGet = tt._1
      val images: Seq[(String, Option[String], String, Option[String])] =
        dbGet.filter(rr => better.files.File(pathCache + OsConf.fsSeparator + getGroupDirName(rr._1) + OsConf.fsSeparator + rr._1).exists()).map { row =>
            (pathCache + OsConf.fsSeparator + getGroupDirName(row._1) + OsConf.fsSeparator + row._1,
              row._2, row._3, row._5
            )
        }
      debug(images)
      Ok(views.html.view_image(dbGet.size, maxResult, images)())
    }
  }

  def viewImage(fullPath: String, mimeType: Option[String]): Action[AnyContent] = Action.async {
    val file = better.files.File(fullPath)
    val path: java.nio.file.Path = file.path
    val source: Source[ByteString, _] = FileIO.fromPath(path)

    val contentLength = Some(file.size)

    Future.successful(
      Result(
        header = ResponseHeader(OK, Map.empty),
        body = HttpEntity.Streamed(source, contentLength, mimeType)
      )
    )
  }
}
