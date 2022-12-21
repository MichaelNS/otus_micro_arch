package models.rest

case class FileCardSt(id: String,
                      deviceUid: String,
                      fParent: String,
                      fName: String,
                      fExtension: Option[String] = None,
                      fCreationDate: java.time.LocalDateTime,
                      fLastModifiedDate: java.time.LocalDateTime,
                      fSize: Option[Long] = None,
                      fMimeTypeJava: Option[String] = None,
                      fNameLc: String
                     )