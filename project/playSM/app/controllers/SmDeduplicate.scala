package controllers

import models.db.Tables
import play.api.Logger
import play.api.mvc._
import ru.ns.model.OsConf
import ru.ns.tools.FileUtils
import services.db.DBService
import utils.db.SmPostgresDriver.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Дедубликация файлов
  * 19-10-2021 создать тесты
  * в [[views.html.sm_device_sha256]] добавить на форму общее кол-во файлов и кол-во дубликатов
  *
  * sm_device_sha256.scala
  * @param assetsFinder assetsFinder
  * @param database     database
  */
@Singleton
class SmDeduplicate @Inject()(implicit assetsFinder: AssetsFinder, val database: DBService)
  extends InjectedController {

  val logger: Logger = play.api.Logger(getClass)

  def deleteFilesIfExist(deviceUid: String, fParent: String): Action[AnyContent] = Action.async {
    val res = deleteFilesIfExistEx(deviceUid, fParent)

    res.map(ww => ww.map(ss => println(ss)))

    //      Ok(s"${rowSeq._1}    ${rowSeq._2}")
    Future.successful(Ok(s""))
  }

  /**
    * Call from [[views.html.sm_device_sha256]]
    * @param deviceUid deviceUid
    * @param fParent fParent
    * @return
    */
  def deleteFilesIfExistEx(deviceUid: String, fParent: String): Future[Future[(Int, Int)]] = {
    debugParam

    val composedAction = getFilesForDelete(deviceUid, fParent)

    val resDb = for {
      // TODO use runStream
      composedAction <- database.runAsync(composedAction)
      mount <- FileUtils.getDeviceInfo(deviceUid)
    } yield (composedAction, mount)
    resDb.map { composedAction =>
      // TODO if (device.isDefined) {
      val resDelFile = composedAction._1._3.map { cFc => delFile(composedAction._2.head.mountpoint + OsConf.fsSeparator + cFc._1 + cFc._2, cFc._3) }
      //      val resDelFile = composedAction._1._3.map { cFc => delFileStub(composedAction._2.head.mountpoint + OsConf.fsSeparator + cFc._1 + cFc._2, cFc._3) }

      // https://loftinspace.com.au/blog/working-with-scala-futures.html
      val result = Future.sequence(resDelFile.map { case (a, b) => b.map(bb => (a, bb)) }).map { resDelete =>
        (
          composedAction._1._1,
          composedAction._1._2
        )
      }.recover { case ex: Throwable =>
        logger.error(s"deleteFilesIfExist error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
        throw ex
      }

      result
    }.recover { case ex: Throwable =>
      logger.error(s"deleteFilesIfExist error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
      throw ex
    }
  }

  def delFile(fPath: String, id: String): (Boolean, Future[Int]) = {
    val dbRes = database.runAsync(Tables.SmFileCard.filter(_.id === id).delete)
    dbRes.onComplete {
      case Success(_) =>
      case Failure(ex) => logger.error(s"delFromDb -> Delete from DB error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
    }

    (FileUtils.deleteFile(fPath),
      dbRes
    )
  }

  def delFileStub(fPath: String, id: String): (Boolean, Future[Int]) = {
    debugParam
    (true,
      Future.successful(1)
    )
  }

  def getFilesForDelete(deviceUid: String, fParent: String): DBIOAction[(Int, Int, Seq[(String, String, String)]), NoStream, Effect.Read] = {
    val baseQry = qryFilesForDelete(deviceUid, fParent)

    val filterAll = qryFindAllFilesInParent(deviceUid, fParent)

    val cnt = baseQry.length
    val filterQry = baseQry.sortBy(r => (r._1, r._2))

    for {cnt <- cnt.result
         filterQry <- filterQry.result
         filterAll <- filterAll.result
         } yield (filterAll, cnt, filterQry)
  }

  def qryFindAllFilesInParent(deviceUid: String, fParent: String): Rep[Int] = {
    Tables.SmFileCard.filter(a => a.sha256.nonEmpty && a.deviceUid === deviceUid && a.fParent === fParent).length
  }

  def qryFilesForDelete(deviceUid: String, fParent: String): Query[(Rep[String], Rep[String], Rep[String]), (String, String, String), Seq] = {
    for {
      a <- Tables.SmFileCard if a.sha256.nonEmpty && a.deviceUid === deviceUid && a.fParent === fParent && Tables.SmFileCard
        .filter(b => b.deviceUid === a.deviceUid && b.sha256 === a.sha256 && b.fName === a.fName && b.fParent =!= a.fParent)
        //        .filter(b => b.deviceUid === deviceUid && b.sha256 === a.sha256 && b.fName === a.fName && b.fParent =!= a.fParent)
        .filterNot(b => b.fParent endsWith "_files")
        .map(p => p.fName)
        .exists
    } yield (a.fParent, a.fName, a.id)
  }
}
