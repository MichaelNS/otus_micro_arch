import java.io.{File, FileNotFoundException, IOException}
import java.nio.charset.StandardCharsets
import java.nio.file.FileStore
import java.nio.file.attribute.{FileAttributeView, FileStoreAttributeView}
import java.time.ZoneId
import java.{lang, util}

import com.google.common.hash.Hashing
import com.google.common.io.Files
import controllers.SmDeduplicate
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play.guice.GuiceOneServerPerTest
import org.scalatestplus.play.{HtmlUnitFactory, OneBrowserPerTest, PlaySpec, ServerProvider}
import play.api.Logger
import ru.ns.model._
import ru.ns.tools.FileUtils

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class SmDeduplicateSpec extends PlaySpec
  with MockFactory

  with OneBrowserPerTest
  with GuiceOneServerPerTest
  with HtmlUnitFactory
  with ServerProvider
  with BeforeAndAfter {
  private val logger: Logger = Logger(classOf[SmDeduplicateSpec])

  def delFile(fPath: String, id: String): (Boolean, Future[Int]) = {
    /*
    val dbRes = database.runAsync(Tables.SmFileCard.filter(_.id === id).delete)
    dbRes.onComplete {
      case Success(_) =>
      case Failure(ex) => logger.error(s"delFromDb -> Delete from DB error: ${ex.toString}\nStackTrace:\n ${ex.getStackTrace.mkString("\n")}")
    }
        (FileUtils.deleteFile(fPath)
          ,
          dbRes
        )

     */

    (FileUtils.deleteFile(fPath)
      ,
      //      database.runAsync(Tables.SmFileCard.filter(_.id === id).delete)
      //      if (fPath.contains("IMG_20160613_090002")) Future.failed(throw new Exception("qwe")) else Future.successful(1)
      if (fPath.contains("IMG_20160613_090002")) Future.failed(new Exception("qwe")) else Future.successful(1)
    )

  }



  "run del is OK" in {
    val controller = app.injector.instanceOf[SmDeduplicate]

    controller.deleteFilesIfExistEx("", "")

  }


  "getWinDevicesInfo is OK" in {
    val fs1: FileStore = new FileStore {
      override def getUsableSpace: Long = ???

      override def `type`(): String = ???

      override def getTotalSpace: Long = ???

      override def isReadOnly: Boolean = ???

      override def getAttribute(attribute: String): AnyRef = ???

      override def supportsFileAttributeView(`type`: Class[_ <: FileAttributeView]): Boolean = ???

      override def supportsFileAttributeView(name: String): Boolean = ???

      override def name(): String = "PLEX"

      def winLetter(): String = "(C:)"

      override def getUnallocatedSpace: Long = ???

      override def getFileStoreAttributeView[V <: FileStoreAttributeView](`type`: Class[V]): V = ???

      override def toString: String = s"${name()} ${winLetter()}"
    }

    val dev_1: Device = Device(name = "", label = "PLEX", uuid = "PLEX", mountpoint = "C:", fstype = "")
    val lstDevices = ArrayBuffer[Device](dev_1)

    val mockedUtils = mock[OsConfMethods]
    //    (mockedUtils.isWindows _).expects().returning(true)
    (mockedUtils.getMacWinDeviceRegexp _).expects().returning(OsConf.winDeviceRegExp)

    implicit val fileStores: lang.Iterable[FileStore] = util.Arrays.asList(fs1)
    implicit val osConfL: OsConfMethods = mockedUtils
    val res = FileUtils.getMacWinDevicesInfo
    res.size mustBe 1

    res mustBe lstDevices


  }

  "check crc file is equals" in {
    val fName = "./test/tmp/test1.txt"
    val hash = Files.asByteSource(new File(fName)).hash(Hashing.sha256)

    val crc = FileUtils.getGuavaSha256(fName)
    assert(crc != "")
    crc mustBe hash.toString.toUpperCase
  }

  "throw FileNotFoundException if file not exist" in {
    a[FileNotFoundException] should be thrownBy {
      FileUtils.getGuavaSha256("file_not_exist")
    }
  }

  "throw IOException with '' parameter FileName" in {
    a[IOException] should be thrownBy {
      FileUtils.getGuavaSha256("")
    }
  }

  "readDirRecursive return files" in {
    import java.nio.file.attribute.BasicFileAttributes
    import java.nio.file.{Files, Paths}

    val file_1 = Paths.get("./test/tmp/test1.txt")
    val attrs_1 = Files.readAttributes(file_1, classOf[BasicFileAttributes])

    file_1.toFile.exists mustBe true

    val fParent = "test" + OsConf.fsSeparator + "tmp"
    val deviceUid = "B6D40831D407F283"
    var mountPoint = Paths.get(fParent).toFile.getAbsolutePath.replace(fParent.replace(OsConf.fsSeparator, OsConf.getOsSeparator), "")

    mountPoint = mountPoint.substring(0, mountPoint.length() - 1)

    val path_1 = SmPath("test/tmp/")

    val fName1 = "test1.txt"
    val fc_1 = FileCardSt(
      id = Hashing.sha256().hashString(deviceUid + fParent + OsConf.fsSeparator + fName1, StandardCharsets.UTF_8).toString.toUpperCase,
      deviceUid = deviceUid,
      fParent = fParent + "/",
      fName = fName1,
      fExtension = Some("txt"),
      fCreationDate = java.time.LocalDateTime.ofInstant(attrs_1.creationTime.toInstant, ZoneId.systemDefault()),
      fLastModifiedDate = java.time.LocalDateTime.ofInstant(attrs_1.lastModifiedTime().toInstant, ZoneId.systemDefault()),
      fSize = Some(attrs_1.size),
      fMimeTypeJava = Some("text/plain"),
      fNameLc = fName1.toLowerCase
    )

    val file_2 = Paths.get("./test/tmp/test2.txt")
    val attrs_2 = Files.readAttributes(file_2, classOf[BasicFileAttributes])
    file_2.toFile.exists mustBe true
    val fName2 = "test2.txt"

    val fc_2 = FileCardSt(
      id = Hashing.sha256().hashString(deviceUid + fParent + OsConf.fsSeparator + fName2, StandardCharsets.UTF_8).toString.toUpperCase,
      deviceUid = deviceUid,
      fParent = fParent + "/",
      fName = fName2,
      fExtension = Some("txt"),
      fCreationDate = java.time.LocalDateTime.ofInstant(attrs_2.creationTime.toInstant, ZoneId.systemDefault()),
      fLastModifiedDate = java.time.LocalDateTime.ofInstant(attrs_2.lastModifiedTime().toInstant, ZoneId.systemDefault()),
      fSize = Some(attrs_2.size),
      fMimeTypeJava = Some("text/plain"),
      fNameLc = fName2.toLowerCase
    )

    val sExclusionDir: List[String] = List("")
    val sExclusionFile: List[String] = List("")

    val hSmBoSmPath: ArrayBuffer[SmPath] = FileUtils.getPathesRecursive(
      "test" + OsConf.fsSeparator + "tmp",
      mountPoint,
      sExclusionDir
    )
    val hSmPathTest = ArrayBuffer[SmPath](path_1)
    hSmBoSmPath.size mustBe 1
    hSmBoSmPath.head.toString must equal(hSmPathTest.head.toString)


    val hSmBoFileCardTest = ArrayBuffer[FileCardSt](fc_1, fc_2)
    val hSmBoFileCard: ArrayBuffer[FileCardSt] = FileUtils.getFilesFromStore(
      "test" + OsConf.fsSeparator + "tmp",
      deviceUid,
      mountPoint,
      sExclusionFile
    )
    hSmBoFileCard.size mustBe 2
    hSmBoFileCard.head.toString must equal(hSmBoFileCardTest.head.toString)
    hSmBoFileCard.take(2).toString must equal(hSmBoFileCardTest.take(2).toString)
    hSmBoFileCard mustBe hSmBoFileCardTest
  }

}
