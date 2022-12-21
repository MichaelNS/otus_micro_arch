import play.api.Logger
import slick.jdbc.GetResult

import java.time.LocalDateTime

package object controllers {

//  implicit val getDateTimeResult: AnyRef with GetResult[DateTime] = GetResult(r => new DateTime(r.nextTimestamp()))

  implicit val getDateTimeResult: AnyRef with GetResult[LocalDateTime] =
    GetResult { r =>
      val nextTimestamp = r.nextTimestamp()
      if (nextTimestamp == null) {
        LocalDateTime.MIN
      } else {
        LocalDateTime.of(nextTimestamp.toLocalDateTime.toLocalDate, nextTimestamp.toLocalDateTime.toLocalTime)
      }
    }

  val logger: Logger = play.api.Logger(getClass)

  def debugParam(implicit line: sourcecode.Line, enclosing: sourcecode.Enclosing, args: sourcecode.Args): Unit = {
    logger.debug(s"debugParam ${enclosing.value} : ${line.value}  - "
      + args.value.map(_.map(a => a.source + s"=[${a.value}]").mkString("(", ", ", ")")).mkString("")
    )
  }

  def debug[V](value: sourcecode.Text[V])(implicit fullName: sourcecode.FullName): Unit = {
    logger.debug(s"${fullName.value} = ${value.source} : [${value.value}]")
  }

  def infoLog[V](value: sourcecode.Text[V])(implicit fullName: sourcecode.FullName): Unit = {
    logger.info(s"${fullName.value} = ${value.source} : [${value.value}]")
  }
}
