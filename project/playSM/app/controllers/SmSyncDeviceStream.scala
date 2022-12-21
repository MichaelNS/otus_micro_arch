package controllers

import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import models.db.Tables
import models.{DeviceView, SmDevice}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.ws._
import play.api.mvc._
import play.api.{Configuration, Logger}
import ru.ns.model.{Device, FileCardSt, OsConf}
import ru.ns.tools.FileUtils
import services.db.DBService
import utils.db.SmPostgresDriver.api._

import java.nio.file.Paths
import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

/**
  * Created by ns on 12.03.2018
  */
@Singleton
class SmSyncDeviceStream @Inject()(cc: MessagesControllerComponents, config: Configuration, val database: DBService, ws: WSClient)
  extends MessagesAbstractController(cc) {

  val logger: Logger = play.api.Logger(getClass)

  implicit val system: ActorSystem = ActorSystem()

  val k8sFcUrl: String = config.get[String]("k8sFcUrl")

  var userInfo: Option[WSCookie] = None
  var XUser: Option[WSCookie] = None

  def importDevice: Action[AnyContent] = Action.async { request =>
    val devices = (for {
      dbDevices <- database.runAsync(Tables.SmDevice.sortBy(_.uid).map(_.uid).result)
      mountDevices <- FileUtils.getDevicesInfo()
    } yield (dbDevices, mountDevices))

    devices.filter { case (_, mountDevices) => mountDevices.nonEmpty }.map { case (dbDevices, deviceOpt) =>
      logger.debug(pprint.apply(dbDevices).toString())
      deviceOpt.foreach { device =>

        wsImportDevice(device, request)

        if (dbDevices.contains(device.uuid)) {
          logger.debug(s"Device [${device.toString}] already exists")
        } else {
          val cRow = Tables.SmDeviceRow(-1, device.uuid, device.name, device.label, None)

          database.runAsync((Tables.SmDevice returning Tables.SmDevice.map(_.id)) += SmDevice.apply(cRow).data.toRow)
            .map(ins => logger.debug(s"add device = $ins"))
            .recover { case ex: Throwable =>
              logger.error(s"importDevice DB ins error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
              throw ex
            }
        }
      }

      Redirect(routes.SmSyncDeviceStream.deviceImport)
    }.recover { case ex: Throwable =>
      logger.error(s"importDevice error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
      throw ex
    }
  }

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
    * Call from [[views.html.device_import]] and [[views.html.smd_index]]
    *
    * used [[SmSyncDeviceStream.syncPath]]
    *
    * @param deviceUid device Uid
    * @return Action.async
    */
  def syncDevice(deviceUid: String): Action[AnyContent] = Action.async {
    debugParam
    FileUtils.getDeviceInfo(deviceUid).map { device =>
      if (device.isDefined) {
        try {
          val impPath = config.get[Seq[String]]("paths2Scan.volumes." + deviceUid)
          debug(s"${impPath.length} : $impPath")
          logger.debug(pprint.apply(impPath.map(c => device.get.mountpoint + OsConf.fsSeparator + c)).toString())
          Source.fromIterator(() => impPath.iterator)
            .throttle(elements = 1, 10.millisecond, maximumBurst = 2, mode = ThrottleMode.Shaping)
            .mapAsync(1)(syncPath(_, deviceUid, device.get.mountpoint)
            ).runWith(Sink.ignore).onComplete {
            case Success(res) =>
              logger.info("done syncDevice, pathConfig " + res.toString + " " + impPath)

              //todo rest
              database.runAsync((for {uRow <- Tables.SmDevice if uRow.uid === deviceUid} yield uRow.pathScanDate)
                .update(Some(LocalDateTime.now())))
                .map(_ => logger.info(s"Sync complete for device $deviceUid"))

            case Failure(ex) =>
              logger.error(s"syncDevice error: ${ex.toString}")
              //todo rest
              database.runAsync((for {uRow <- Tables.SmDevice if uRow.uid === deviceUid} yield uRow.pathScanDate)
                .update(Some(LocalDateTime.now())))
                .map(_ => logger.info(s"Sync complete for device $deviceUid"))
          }
          Redirect(routes.SmSyncDeviceStream.deviceImport)
        } catch {
          case ex: com.typesafe.config.ConfigException =>
            logger.error(s"No config in [scanImport.conf] for device $deviceUid", ex)
            NotFound(ex.toString)
        }
      } else {
        Ok("device mountpoint is empty")
      }
    }
  }

  /**
    * sync path
    *
    * see [[SmSyncDeviceStream.syncDevice]]
    * used [[ru.ns.tools.FileUtils.getPathesRecursive]]
    * used [[SmSyncDeviceStream.mergePath2Db]]
    *
    * @param path2scan  paths to scan - (home/user/Documents)
    * @param deviceUid  deviceUid
    * @param mountPoint mountPoint
    * @return Future
    */
  def syncPath(path2scan: String, deviceUid: String, mountPoint: String): Future[Done] = {
    val funcName = "syncPath"
    val start = System.currentTimeMillis


    val resPath = Source.fromIterator(() => FileUtils.getPathesRecursive
    (path2scan, mountPoint, config.get[Seq[String]]("paths2Scan.exclusionPath")).iterator)
      .throttle(elements = 1, 10.millisecond, maximumBurst = 10, mode = ThrottleMode.Shaping)
      .mapAsync(1) { path =>
        mergePath2Db(deviceUid = deviceUid, mountPoint = mountPoint,
          path.fParent,
          config.get[Seq[String]]("paths2Scan.exclusionFile")
        )
      }
      .recover { case t: Throwable =>
        logger.error("Error retrieving output from flowA. Resuming without them.", t)
        None
      }
      .runWith(Sink.ignore)

    resPath.onComplete {
      case Success(res) => logger.info(s"$funcName " + res.toString + " " + path2scan + s" ${System.currentTimeMillis - start} ms")
      case Failure(ex) => logger.error(s"syncDevice error: ${ex.toString}")
    }

    resPath
  }

  def syncSingleNamePath(path2scan: String, deviceUid: String): Action[AnyContent] = Action {
    // TODO 05.08.2020 Проверить, что указанный каталог указан в конфигурации сканирования. Иначе можно в explorerDevice нажать синхронизацию на каталоге, который в конфиге не указан и таким образом в БД добавить лишние файлы
    FileUtils.getDeviceInfo(deviceUid).map { device =>
      if (device.isDefined) {
        syncPath(path2scan, deviceUid, device.get.mountpoint)
      }
    }
    Ok("Job run")
  }

  /**
    *
    * used [[ru.ns.tools.FileUtils.getFilesFromStore]]
    *
    * @param deviceUid      deviceUid
    * @param mountPoint     mountPoint
    * @param impPath        impPath
    * @param sExclusionFile list of ExclusionFile
    * @return
    */
  def mergePath2Db(deviceUid: String,
                   mountPoint: String,
                   impPath: String,
                   sExclusionFile: Seq[String]
                  ): Future[(Long, Long, Future[Int])] = {
    val funcName = "mergePath2Db"
    val start = System.currentTimeMillis
    val lstToIns = ArrayBuffer[Tables.SmFileCard#TableElementType]()
    val hSmBoFileCard = FileUtils.getFilesFromStore(impPath, deviceUid, mountPoint, sExclusionFile)

    wsImportFileCard(deviceUid, impPath, hSmBoFileCard)

    val hInMap = database.runAsync(Tables.SmFileCard
      .filter(_.deviceUid === deviceUid).filter(_.fParent === impPath)
      .map(fld => (fld.id, fld.fLastModifiedDate)).result)
      .map { dbGet =>
        val hInMap: Map[String, Seq[(String, LocalDateTime)]] = dbGet.groupBy(_._1)
        //        logger.debug(s"$funcName -> path = [$impPath]  hSmBoFileCard.size = [${hSmBoFileCard.size}]  rowSeq.size = [${dbGet.size}]   get 2 lists time: ${System.currentTimeMillis - start} ms")
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
        //        case Failure(ex) => logger.error(s"$funcName 1 error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
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

  def deleteNonExistsFpathInDb(deviceUid: String): Action[AnyContent] = Action {
    debugParam
    FileUtils.getDevicesInfo(deviceUid).map { devices =>
      debug(devices)
      val device = devices.find(_.uuid == deviceUid)
      debug(device)
      if (device.isDefined) {
        database.runAsync(Tables.SmFileCard
          .filter(_.deviceUid === deviceUid)
          .map(fld => fld.fParent)
          .distinct.result)
          .map { dbGet =>
            dbGet.foreach { cPath =>
              if (!Paths.get(device.get.mountpoint + OsConf.fsSeparator + cPath).toFile.exists) {
                logger.debug(s"deleteNonExistsFpathInDb ${device.get.mountpoint + OsConf.fsSeparator + cPath}")
                val insRes = database.runAsync(Tables.SmFileCard.filter(_.deviceUid === deviceUid).filter(_.fParent === cPath).delete)
                insRes onComplete {
                  case Success(suc) => logger.debug(s"deleted [$suc] row , fParent = [$cPath]")
                  case Failure(ex) => logger.error(s"delFromDb -> Delete from DB error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
                }
              }
            }
          }
        logger.info("DONE deleteNonExistsFpathInDb")
      }
    }
    Ok("Job run")
  }

  def wsLogin(): Future[Unit] = {
    //    implicit request =>
    val requestCheck: WSRequest = ws.url(config.get[String]("k8sCheckUrl"))
    //    if (XUser.isDefined) requestCheck.withCookies(XUser.get)
    if (userInfo.isDefined) requestCheck.withCookies(userInfo.get)
    logger.debug(s"wsLogin userInfo $userInfo   XUser $XUser")
    debug(requestCheck)
    val futureResponseCheck: Future[WSResponse] = requestCheck.get()
    //    futureResponseCheck.onComplete {
    //      case Success(res) =>
    //        logger.debug(s"REST check Success - $res")
    //      //        logger.debug(s"REST check Success 2 cookies - ${res.cookies}   data = $k8sUser")
    //      //        logger.debug(s"REST check Success 2 headers - ${res.headers}   data = $k8sUser")
    //      case Failure(ex) => logger.error(s"REST check Failure : ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
    //    }
    futureResponseCheck.map { res =>
      logger.debug(s"REST check Success res = $res   res.body = ${res.body}")
      if (res.status == 401) { // Unauthorized
        val data = Json.obj("username" -> config.get[String]("k8sUser"), "password" -> config.get[String]("k8sPassword"))
        logger.debug("wsLogin start rest")
        // TODO 2 conf
        val request: WSRequest = ws.url(config.get[String]("k8sLoginUrl"))
        val futureResponse: Future[WSResponse] = request.post(data)
        futureResponse.onComplete {
          case Success(res) =>
            logger.debug(s"REST login Success - $res")
          //              logger.debug(s"REST login Success 2 cookies - ${res.cookies}   data = $k8sUser")
          //              logger.debug(s"REST login Success 2 headers - ${res.headers}   data = $k8sUser")
          case Failure(ex) => logger.error(s"REST login Failure : ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
        }
        futureResponse.map { res2 =>
          logger.debug(s"REST login Success - $res2")
          if (res2.status == 200) {
            val userInfoO: Option[WSCookie] = res2.cookie("userInfo")
            val XUserO: Option[WSCookie] = res2.cookie("X-User")

            if (userInfoO.isDefined) {
              userInfo = userInfoO
            }
            if (XUserO.isDefined) {
              XUser = XUserO
            }
          }
        }
      }
    }
  }

  def wsImportDevice(device: Device, request: MessagesRequest[AnyContent]): Status = {
    //    logger.debug(s"wsImportDevice start request - $request")
    //    logger.debug(s"wsImportDevice start request - ${request.session}")
    //    logger.debug(s"wsImportDevice start request - ${request.cookies}")

    // TODO rewrite
    Await.result(wsLogin(), 50.seconds)

    wsLogin().map { resLogin =>
      logger.debug(s"resLogin = $resLogin")

      val data = Json.obj("name" -> device.name, "label" -> device.label, "uuid" -> device.uuid)
      logger.debug(s"wsImportDevice start rest device - $device")
      val url = k8sFcUrl + "/create"
      logger.debug(s"wsImportDevice userInfo $userInfo   XUser $XUser")
      val request: WSRequest = ws.url(url).withCookies(userInfo.get, XUser.get)

      debug(request)

      val futureResponse: Future[WSResponse] = request.post(data)
      futureResponse.onComplete {
        case Success(res) => logger.debug(s"REST wsImportDevice Success - $res   data = $data")
        case Failure(ex) => logger.error(s"REST wsImportDevice Failure : ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
      }
    }
    Ok
  }

  case class FileCard(deviceUid: String,
                      impPath: String,
                      hSmBoFileCard: ArrayBuffer[FileCardSt]
                     )

  implicit val fileCardStReads: Writes[FileCardSt] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "deviceUid").write[String] and
      (JsPath \ "fParent").write[String] and
      (JsPath \ "fName").write[String] and
      (JsPath \ "fExtension").writeNullable[String] and
      (JsPath \ "fCreationDate").write[java.time.LocalDateTime] and
      (JsPath \ "fLastModifiedDate").write[java.time.LocalDateTime] and
      (JsPath \ "fSize").writeNullable[Long] and
      (JsPath \ "fMimeTypeJava").writeNullable[String] and
      (JsPath \ "fNameLc").write[String]
    ) (unlift(FileCardSt.unapply))

  implicit val fileCardReads: Writes[FileCard] = (
    (JsPath \ "deviceUid").write[String] and
      (JsPath \ "impPath").write[String] and
      (JsPath \ "hSmBoFileCard").write[ArrayBuffer[FileCardSt]]
    ) (unlift(FileCard.unapply))

  def wsImportFileCard(deviceUid: String,
                       impPath: String,
                       hSmBoFileCard: ArrayBuffer[FileCardSt]): Unit = {

    // TODO rewrite
    Await.result(wsLogin(), 50.seconds)

    wsLogin().map { resLogin =>
      logger.debug(s"resLogin = $resLogin")

      val fileCard = FileCard(
        deviceUid,
        impPath,
        hSmBoFileCard
      )

      val json = Json.toJson(fileCard)

      logger.debug("wsImportFileCard start rest")
      // TODO 2 conf
      val url = k8sFcUrl + "/createFc"
      logger.debug(s"wsImportFileCard userInfo $userInfo   XUser $XUser")
      val request: WSRequest = ws.url(url).withCookies(userInfo.get, XUser.get)

      val futureResponse: Future[WSResponse] = request.post(json)
      futureResponse.onComplete {
        case Success(res) => logger.debug(s"REST Success - $res   json = $json")
        case Failure(ex) => logger.error(s"REST Failure : ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
      }
    }
  }

}
