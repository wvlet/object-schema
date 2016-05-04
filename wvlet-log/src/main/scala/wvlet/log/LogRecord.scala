package wvlet.log

import java.util.{logging => jl}

/**
  * Source code location where the log is
  *
  * @param path
  * @param fileName
  * @param line
  * @param col
  */
case class LogSource(path: String, fileName: String, line: Int, col: Int) {
  def fileBaseName = {
    val pos = fileName.lastIndexOf('.')
    if (pos == -1) {
      fileName
    }
    else {
      fileName.substring(0, pos)
    }
  }

  def fileLoc = s"${fileName}:${line}"
}

case class LogRecord(level: LogLevel,
                     source: LogSource,
                     message: String,
                     cause: Option[Throwable] = None)
  extends jl.LogRecord(level.jlLevel, message) {

  cause.foreach(setThrown(_))

  def leafLoggerName: String = {
    getLoggerName match {
      case null => ""
      case name =>
        val pos = name.lastIndexOf('.')
        if (pos == -1) {
          name
        }
        else {
          name.substring(pos + 1)
        }
    }
  }
}
