package controllers

import models.db.Tables
import play.api.Configuration
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}
import services.db.DBService
import utils.db.SmPostgresDriver.api._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by ns on 02.03.2017.
 */
@Singleton
class SmReport @Inject()(cc: MessagesControllerComponents, config: Configuration, val database: DBService)
  extends MessagesAbstractController(cc)
    with play.api.i18n.I18nSupport {

  def listFilesWithoutSha256ByDevice(device: String): Action[AnyContent] = Action.async { request =>
    val maxRows = 200
    val baseQry = Tables.SmFileCard
      .filter(fc => fc.deviceUid === device && fc.sha256.isEmpty && fc.fSize > 0L)
      .sortBy(_.fLastModifiedDate.desc)
      .map(fld => (fld.fParent, fld.fName, fld.fLastModifiedDate))

    val composedAction = for {cnt <- baseQry.length.result
                              qry <- baseQry.take(maxRows).result} yield (cnt, qry)

    database.runAsync(composedAction).map { rowSeq =>
      Ok(views.html.filecards(Some(rowSeq._1), Some(maxRows), rowSeq._2)(request)
      )
    }
  }

  /**
   * https://stackoverflow.com/questions/32262353/how-to-do-a-correlated-subquery-in-slick
   * for {
   * a <- A if !B.filter(b => b.fieldK === a.fieldA).exists
   * } yield (a.fieldA)
   *
   * @param device device
   * @return
   */
  def checkBackUp(device: String): Action[AnyContent] = Action.async { request =>
    val backUpVolumes = config.get[Seq[String]]("BackUp.volumes")
    val maxRows: Long = config.get[Long]("BackUp.maxResult")

    val baseQry = for {
      a <- Tables.SmFileCard
      if a.sha256.nonEmpty && a.deviceUid === device && !Tables.SmFileCard
        .filter(b => b.sha256.nonEmpty && b.sha256 === a.sha256 && b.deviceUid =!= device && b.deviceUid.inSet(backUpVolumes))
        .filterNot(b => b.fParent endsWith "_files")
        .map(p => p.fName)
        .exists
    } yield (a.fParent, a.fName, a.fLastModifiedDate)

    val cnt = baseQry.length
    val filtQry = baseQry
      .sortBy(r => (r._1, r._2))
      .take(maxRows)

    val composedAction = for {cnt <- cnt.result
                              filtQry <- filtQry.result} yield (cnt, filtQry)

    database.runAsync(composedAction).map { rowSeq =>
      Ok(views.html.sm_chk_device_backup(rowSeq._1, maxRows, rowSeq._2)(request))
    }
  }

  def checkBackAllFiles: Action[AnyContent] = Action.async { request =>
    val cntFiles: Int = config.get[Int]("BackUp.allFiles.cntFiles")
    val maxRows: Int = config.get[Int]("BackUp.allFiles.maxRows")
    val device_Unreliable: String = config.get[Seq[String]]("BackUp.allFiles.device_Unreliable").toSet.mkString("'", "', '", "'")
    val device_NotView: String = config.get[Seq[String]]("BackUp.allFiles.device_NotView").toSet.mkString("'", "', '", "'")

    debug(device_Unreliable)
    debug(device_NotView)

    val qry = sql"""
       SELECT
         sha256,
         f_name,
         category_type,
         description,
         device_uid
       FROM (
              SELECT
                card.sha256,
                card.f_name,
                category_rule.category_type,
                category_rule.description,
                (SELECT device_uid
                 FROM sm_file_card sq
                 WHERE sq.sha256 = card.sha256
                 AND   sq.device_uid NOT IN (#$device_Unreliable)
                 LIMIT 1) AS device_uid
              FROM "sm_file_card" card
                JOIN sm_category_fc category ON category.f_name = card.f_name and category.sha256 = card.sha256
                JOIN sm_category_rule category_rule ON category_rule.id = category.id
              WHERE category_rule.category_type IS NOT NULL
              GROUP BY card.sha256,
                       card.f_name,
                       category_rule.category_type,
                       category_rule.description
              HAVING COUNT(1) < #$cntFiles
            ) AS res
       WHERE device_uid NOT IN (#$device_NotView)
       LIMIT #$maxRows
      """
      .as[(String, String, String, String, String)]
    database.runAsync(qry).map { rowSeq =>
      Ok(views.html.sm_chk_all_backup(rowSeq, device_Unreliable, device_NotView, cntFiles, rowSeq.length, maxRows)(request))
    }

  }

  def checkBackFilesLastYear: Action[AnyContent] = Action.async { request =>
    val cntFiles: Int = 2
    val maxRows: Int = 200
    // TODO delete
    val device_Unreliable: String = "".toSet.mkString("'", "', '", "'")
    val device_NotView: String = "".toSet.mkString("'", "', '", "'")

    debug(device_Unreliable)
    debug(device_NotView)

    val qry = sql"""
        SELECT card.sha256,
               card.f_name,
               array_agg(sd.label_v),
               array_agg(to_char(card.f_last_modified_date, 'YYYY-MM-DD HH:MM:SS')) as last_modified_date
        FROM sm_file_card card
                 inner join sm_device sd on card.device_uid = sd.uid
        WHERE card.f_last_modified_date >= date_trunc('month', card.f_last_modified_date) - INTERVAL '1 year'
          and sd.label_v NOT IN (#$device_NotView)
          and sd.reliable
        GROUP BY card.sha256,
                 card.f_name
--                  , last_modified_date
        HAVING COUNT(1) < #$cntFiles

        union all
        SELECT card.sha256,
               card.f_name,
               array_agg(sd.label_v),
               array_agg(to_char(card.f_last_modified_date, 'YYYY-MM-DD HH:MM:SS')) as last_modified_date
        FROM sm_file_card card
                 inner join sm_device sd on card.device_uid = sd.uid
        WHERE card.f_last_modified_date >= date_trunc('month', card.f_last_modified_date) - INTERVAL '1 year'
          and sd.label_v NOT IN (#$device_NotView)
          and not sd.reliable
          and not exists(select 1
                         from sm_file_card
                                  inner join sm_device s on s.uid = sm_file_card.device_uid
                         where sm_file_card.sha256 = card.sha256
                           and s.reliable)
        GROUP BY card.sha256,
                 card.f_name
--                  , last_modified_date
        HAVING COUNT(1) < #$cntFiles
        order by last_modified_date desc
        LIMIT #$maxRows
          """
      .as[(String, String, String, String)]
    database.runAsync(qry).map { rowSeq =>
      Ok(views.html.sm_chk_backup_last_year(rowSeq, device_Unreliable, device_NotView, cntFiles, rowSeq.length, maxRows)(request))
    }
  }

  /**
   * check duplicates SmFileCard
   * Call from [[views.html.smd_index]]
   *
   * @param device device uid
   * @return [[views.html.f_duplicates]]
   */
  def checkDuplicates(device: String): Action[AnyContent] = Action.async { request =>
    val res = checkDuplicatesEx(device, fParent = None, fExtension = None)
    res._1.map { rowSeq =>
      Ok(views.html.f_duplicates(device, res._2, rowSeq)(request))
    }
  }

  def checkDuplicatesEx(device: String, fParent: Option[String], fExtension: Option[String]): (Future[Seq[(Option[String], String, Option[Long], Int)]], Long) = {
    val maxFileSize: Long = config.underlying.getBytes("checkDuplicates.maxFileSize")

    val qry = (for {
      uRow <- Tables.SmFileCard if uRow.deviceUid === device && uRow.fSize > 0L && uRow.fSize > maxFileSize && uRow.sha256.nonEmpty
    } yield uRow)
      .filterOpt(fParent)(_.fParent startsWith _)
      .filterOpt(fExtension)(_.fExtension === _)
      .groupBy(uRow =>
        (uRow.sha256, uRow.fName, uRow.fSize))
      .map({
        case ((uRow, fName, fSize), cnt) =>
          (uRow, fName, fSize, cnt.map(_.sha256).length)
      })
      .filter(cnt => cnt._4 > 1)
      .sortBy(r => (r._4.desc, r._3.desc))

    (database.runAsync(qry.result), maxFileSize)
  }

}
