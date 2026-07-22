package com.boombustgroup.amorfati.economies.pl.gus.regon

import org.apache.poi.ss.usermodel.{DataFormatter, Row, WorkbookFactory}

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Locale
import scala.util.Using

/** Verified access to the first pinned REGON source artifact.
  *
  * The workbook remains a local acquisition input. This object only exposes
  * enough evidence to make the later normalization parser explicit and
  * reviewable; it neither initializes an Amor Fati model nor derives employment
  * from REGON worker bands.
  */
object Pl2026Q2RegonSource:

  val SourceId: String       = "PL-GUS-REGON-QUARTERLY-2026-06-30"
  val SourceLocation: String = "https://stat.gov.pl/download/gfx/portalinformacyjny/pl/defaultaktualnosci/5504/7/16/1/tablice_kwartalne_regon_20260630.xlsx"
  val ExpectedSha256: String = "858fd5715b1b4d64180d4bd15310a74b8b8d45d26ce3a79182e3dff7f8a6e6e9"
  val Tabl6Name: String      = "Tabl 6"

  final case class Evidence(
      source: Path,
      sha256: String,
      sheetName: String,
      lastRowIndex: Int,
      headerRows: Vector[Vector[String]],
      firstDataRows: Vector[Vector[String]],
  )

  enum InspectionError:
    case MissingSource(path: Path)
    case DigestMismatch(expected: String, actual: String)
    case MissingSheet(name: String)
    case ReadFailure(detail: String)

  /** Verify the raw artifact before inspecting `Tabl 6`. Rows are represented
    * as display values because GUS uses the visible dash as the missing/zero
    * marker; later parsing must classify that marker deliberately.
    */
  def inspect(source: Path): Either[InspectionError, Evidence] =
    if !Files.isRegularFile(source) then Left(InspectionError.MissingSource(source))
    else
      val actual = sha256(source)
      if actual != ExpectedSha256 then Left(InspectionError.DigestMismatch(ExpectedSha256, actual))
      else
        Using
          .Manager { use =>
            val input    = use(Files.newInputStream(source))
            val workbook = use(WorkbookFactory.create(input))
            Option(workbook.getSheet(Tabl6Name))
              .toRight(InspectionError.MissingSheet(Tabl6Name))
              .map: sheet =>
                val formatter = new DataFormatter(Locale.ROOT)
                Evidence(
                  source = source,
                  sha256 = actual,
                  sheetName = sheet.getSheetName,
                  lastRowIndex = sheet.getLastRowNum,
                  headerRows = (0 to 3).toVector.map(rowIndex => rowValues(sheet.getRow(rowIndex), formatter)),
                  firstDataRows = (4 to 19).toVector.map(rowIndex => rowValues(sheet.getRow(rowIndex), formatter)),
                )
          }
          .toEither
          .left
          .map(error => InspectionError.ReadFailure(messageOf(error)))
          .flatMap(identity)

  private def rowValues(row: Row | Null, formatter: DataFormatter): Vector[String] =
    Option(row).fold(Vector.fill(9)("")): current =>
      (0 to 8).toVector.map: columnIndex =>
        Option(current.getCell(columnIndex)).fold("")(cell => formatter.formatCellValue(cell).trim)

  private def sha256(path: Path): String =
    val digest = MessageDigest.getInstance("SHA-256")
    Using.resource(Files.newInputStream(path)): input =>
      val buffer = Array.ofDim[Byte](8192)
      Iterator
        .continually(input.read(buffer))
        .takeWhile(_ != -1)
        .foreach(read => digest.update(buffer, 0, read))
    digest.digest().iterator.map(byte => f"${byte & 0xff}%02x").mkString

  private def messageOf(error: Throwable): String =
    Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)

@main def inspectPl2026Q2Regon(source: String): Unit =
  Pl2026Q2RegonSource.inspect(Path.of(source)) match
    case Left(error)     =>
      Console.err.println(error)
      sys.exit(1)
    case Right(evidence) =>
      println(s"source=${evidence.source}")
      println(s"sha256=${evidence.sha256}")
      println(s"sheet=${evidence.sheetName}")
      println(s"last_row_index=${evidence.lastRowIndex}")
      println("header_rows=")
      evidence.headerRows.foreach(row => println(row.mkString("\\t")))
      println("first_data_rows=")
      evidence.firstDataRows.foreach(row => println(row.mkString("\\t")))
