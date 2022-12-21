package models

import models.db.Tables

case class Entity[T](id: Int, data: T)

case class EntitySmFc[T](id: String, data: T)

case class SmDevice(name: String, labelV: String, uid: String, pathScanDate: Option[java.time.LocalDateTime] = None) {
  def toRow: Tables.SmDeviceRow = {
    Tables.SmDeviceRow(
      id = -1,
      name = name,
      labelV = labelV,
      uid = uid,
      pathScanDate = pathScanDate
    )
  }
}

object SmDevice {
  def apply(row: Tables.SmDeviceRow): Entity[SmDevice] = {
    Entity(
      id = row.id,
      data = SmDevice(
        name = row.name,
        row.labelV,
        uid = row.uid,
        pathScanDate = row.pathScanDate
      )
    )
  }

}

case class SmFileCard(
                       id: String,
                       deviceUid: String,
                       fParent: String,
                       fName: String,
                       fExtension: Option[String] = None,
                       fCreationDate: java.time.LocalDateTime,
                       fLastModifiedDate: java.time.LocalDateTime,
                       fSize: Option[Long] = None,
                       fMimeTypeJava: Option[String] = None,
                       sha256: Option[String] = None,
                       fNameLc: String
                     ) {
  def toRow: _root_.models.db.Tables.SmFileCardRow = {
    Tables.SmFileCardRow(
      id = id,
      deviceUid = deviceUid,
      fParent = fParent,
      fName = fName,
      fExtension = fExtension,
      fCreationDate = fCreationDate,
      fLastModifiedDate = fLastModifiedDate,
      fSize = fSize,
      fMimeTypeJava = fMimeTypeJava,
      sha256 = sha256,
      fNameLc = fNameLc,
    )
  }
}

object SmFileCard {
  def apply(row: Tables.SmFileCardRow): EntitySmFc[SmFileCard] = {
    EntitySmFc(
      id = row.id,
      data = SmFileCard(
        id = row.id,
        deviceUid = row.deviceUid,
        fParent = row.fParent,
        fName = row.fName,
        fExtension = row.fExtension,
        fCreationDate = row.fCreationDate,
        fLastModifiedDate = row.fLastModifiedDate,
        fSize = row.fSize,
        fMimeTypeJava = row.fMimeTypeJava,
        sha256 = row.sha256,
        fNameLc = row.fNameLc
      )
    )
  }
}
