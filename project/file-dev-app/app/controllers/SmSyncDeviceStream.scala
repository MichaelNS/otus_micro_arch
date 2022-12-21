package controllers

import akka.actor.ActorSystem
import models.DeviceView
import models.db.Tables
import models.rest.FileCardSt
import play.api.Logger
import play.api.libs.ws._
import play.api.mvc._
import services.db.DBService
import utils.db.SmPostgresDriver.api._

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
 * Created by ns on 12.03.2018
 */
@Singleton
class SmSyncDeviceStream @Inject()(cc: MessagesControllerComponents, val database: DBService, ws: WSClient)
  extends MessagesAbstractController(cc) {

  val logger: Logger = play.api.Logger(getClass)

  implicit val system: ActorSystem = ActorSystem()

  def deviceImport: Action[AnyContent] = Action.async { implicit request: MessagesRequest[AnyContent] =>
    database.runAsync(
      Tables.SmDevice
        .map(f => (f.name, f.labelV, f.uid, f.description, f.pathScanDate, f.visible, f.reliable))
        .sortBy(_._2)
        .result
    ).map { rowSeq =>
      val devices = ArrayBuffer[DeviceView]()
      rowSeq.foreach { p => devices += DeviceView(name = p._1, label = p._2, uid = p._3, description = p._4, syncDate = p._5, visible = p._6, reliable = p._7, withOutCrc = 0) }

      Ok(views.html.device_import(devices))
    }
  }


  /**
   *
   * @param deviceUid deviceUid
   * @param impPath   impPath
   * @return
   */
  def mergePath2Db(deviceUid: String,
                   impPath: String,
                   hSmBoFileCard: Seq[FileCardSt]
                  ): Future[(Long, Long, Future[Int])] = {
    val funcName = "mergePath2Db"
    val start = System.currentTimeMillis
    val lstToIns = ArrayBuffer[Tables.SmFileCard#TableElementType]()
    //    val hSmBoFileCard = FileUtils.getFilesFromStore(impPath, deviceUid, mountPoint, sExclusionFile)
    val hInMap = database.runAsync(Tables.SmFileCard
      .filter(_.deviceUid === deviceUid).filter(_.fParent === impPath)
      .map(fld => (fld.id, fld.fLastModifiedDate)).result)
      .map { dbGet =>
        val hInMap: Map[String, Seq[(String, LocalDateTime)]] = dbGet.groupBy(_._1)
        hInMap
      }
    val resMerge = hInMap.map { hInMap =>
      hSmBoFileCard.foreach { value => // add FC
        if (!hInMap.contains(value.id)) {
          lstToIns += Tables.SmFileCardRow(value.id, value.deviceUid, value.fParent, value.fName, value.fExtension,
            value.fCreationDate, value.fLastModifiedDate, value.fSize, value.fMimeTypeJava, None, value.fNameLc)
        } else { // upd FC
          if (hInMap(value.id).head._2 != value.fLastModifiedDate) {
            logger.info("1 UPD " + value.deviceUid + " " + value.fParent + " " + value.fName)
            database.runAsync(
              (for {uRow <- Tables.SmFileCard if uRow.id === value.id} yield (uRow.sha256, uRow.fCreationDate, uRow.fLastModifiedDate, uRow.fSize))
                .update((None, value.fCreationDate, value.fLastModifiedDate, value.fSize))
            ).map { _ => logger.debug(s"$funcName -> path = [$impPath]   Updated key ${value.id}") }
          }
        }
      }
      delFromDb(hInMap.keys.toSeq, hSmBoFileCard.map(p => p.id).toSeq, impPath)
      if (lstToIns.nonEmpty) logger.info(s"$funcName -> path = [$impPath]   lstToIns.size ${lstToIns.size}")

      (start, System.currentTimeMillis - start, database.runAsync((Tables.SmFileCard returning Tables.SmFileCard.map(_.id)) ++= lstToIns).map(_.size))
    }
    resMerge.onComplete {
      case Success(res) => res._3.onComplete {
        case Success(cntIns) => if (cntIns > 0) logger.info(s"$funcName inserted [$cntIns] rows   path = [$impPath]   " + s"Elapsed time: ${res._2} ms " + s"All time: ${System.currentTimeMillis - res._1} ms ") //cntIns
        case Failure(ex) => logger.error(s"$funcName 1 error: ${ex.toString}}")
      }
      case Failure(ex) => logger.error(s"$funcName 2 error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
    }
    resMerge
  }


  // todo change res delFromDb to future
  def delFromDb(idDb: Seq[String], idDevice: Seq[String], path: String): Unit = {
    val start = System.currentTimeMillis

    val delDiff = idDb diff idDevice
    if (delDiff.nonEmpty) {
      debug(delDiff)
    }

    val insRes = delDiff.map { key =>
      database.runAsync(Tables.SmFileCard.filter(_.id === key).delete)
    }
    val futureListOfTrys = Future.sequence(insRes)

    futureListOfTrys onComplete {
      case Success(suc) =>
        suc.foreach(qq => logger.debug(s"deleted [$qq] row "))

      case Failure(ex) => logger.error(s"delFromDb -> Delete from DB error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
    }

    logger.debug(s"delFromDb - is done, path = [$path] "
      + s"idDb.size = [${idDb.size}]  idDevice.size = [${idDevice.size}]   delDiff.size = ${delDiff.size}"
      + s"  Elapsed time: ${System.currentTimeMillis - start} ms")
  }
}
