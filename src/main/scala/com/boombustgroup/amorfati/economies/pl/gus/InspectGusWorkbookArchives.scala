package com.boombustgroup.amorfati.economies.pl.gus

import java.io.InputStream
import java.nio.file.{Files, Path}
import java.util.Locale
import java.util.zip.ZipFile

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.{DataFormatter, WorkbookFactory}
import org.apache.poi.xssf.eventusermodel.{XSSFReader, XSSFSheetXMLHandler}
import org.apache.poi.xssf.usermodel.XSSFComment
import org.xml.sax.{InputSource, SAXException}
import org.xml.sax.helpers.XMLReaderFactory

import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Prints a bounded structural view of public GUS workbook archives without
  * copying their raw contents into the repository.
  *
  * This is an acquisition aid: recipes select source tables from the inspected
  * workbook structure, while production transformations must still verify the
  * source record digest before reading an archive.
  */
object InspectGusWorkbookArchives:
  private val MaxCellsPerRow = 16
  private val MaxLegacyWorkbookBytes = 256L * 1024L * 1024L

  def main(args: Array[String]): Unit =
    val options = Options.parse(args.toList)
    if options.archives.isEmpty then
      Console.err.println(
        "Usage: InspectGusWorkbookArchives [--entry=<substring>] [--sheet=<substring>] <archive.zip|workbook.xls[x]> [source ...]",
      )
      sys.exit(2)

    options.archives.foreach(path => inspectPath(Path.of(path), options))

  private def inspectPath(path: Path, options: Options): Unit =
    val fileName = path.getFileName.toString
    if fileName.toLowerCase(Locale.ROOT).endsWith(".zip") then inspectArchive(path, options)
    else if fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx") then inspectXlsx(path, options)
    else if isWorkbook(fileName) then
      Using.resource(Files.newInputStream(path))(input => withCappedTemporaryWorkbook(fileName, input)(temporary => inspectWorkbook(path.toString, temporary, options)))
    else throw IllegalArgumentException(s"unsupported source file: $path")

  private def inspectArchive(archive: Path, options: Options): Unit =
    val zip = new ZipFile(archive.toFile)
    try
      println(s"archive\t$archive")
      zip
        .entries()
        .asScala
        .filter(entry => !entry.isDirectory && isWorkbook(entry.getName))
        .filter(entry => options.matchesEntry(entry.getName))
        .foreach: entry =>
          val input = zip.getInputStream(entry)
          try withCappedTemporaryWorkbook(entry.getName, input)(temporary => inspectWorkbook(entry.getName, temporary, options))
          finally input.close()
    finally zip.close()

  private def inspectWorkbook(name: String, path: Path, options: Options): Unit =
    val workbook = WorkbookFactory.create(path.toFile)
    try
      println(s"workbook\t$name")
      (0 until workbook.getNumberOfSheets).foreach { sheetIndex =>
        val sheet = workbook.getSheetAt(sheetIndex)
        if options.matchesSheet(sheet.getSheetName) then
          println(s"sheet\t${sheet.getSheetName}")
          val formatter = new DataFormatter(Locale.ROOT)
          sheet
            .iterator()
            .asScala
            .filter(row => row.getLastCellNum > 0)
            .take(options.maxRowsPerSheet)
            .foreach { row =>
              val cells = row
                .cellIterator()
                .asScala
                .take(MaxCellsPerRow)
                .map(cell => formatter.formatCellValue(cell).trim)
                .mkString(" | ")
              println(s"row\t${row.getRowNum + 1}\t$cells")
            }
      }
    finally workbook.close()

  private def withCappedTemporaryWorkbook[A](name: String, input: InputStream)(f: Path => A): A =
    val suffix    = if name.toLowerCase(Locale.ROOT).endsWith(".xls") then ".xls" else ".workbook"
    val temporary = Files.createTempFile("amor-fati-inspect-", suffix)
    try
      Using.resource(Files.newOutputStream(temporary)): output =>
        val buffer = new Array[Byte](64 * 1024)
        var total  = 0L
        var read   = input.read(buffer)
        while read >= 0 do
          total += read
          if total > MaxLegacyWorkbookBytes then
            throw IllegalArgumentException(s"workbook exceeds $MaxLegacyWorkbookBytes bytes: $name")
          output.write(buffer, 0, read)
          read = input.read(buffer)
      f(temporary)
    finally Files.deleteIfExists(temporary)

  private def inspectXlsx(path: Path, options: Options): Unit =
    Using.resource(OPCPackage.open(path.toFile)): packageFile =>
      val reader    = new XSSFReader(packageFile)
      val iterator  = reader.getSheetsData.asInstanceOf[XSSFReader.SheetIterator]
      val formatter = new DataFormatter(Locale.ROOT)
      println(s"workbook\t$path")
      while iterator.hasNext do
        val input     = iterator.next()
        val sheetName = iterator.getSheetName
        try
          if options.matchesSheet(sheetName) then
            println(s"sheet\t$sheetName")
            printXlsxRows(input, reader, formatter, options.maxRowsPerSheet)
        finally input.close()

  private def printXlsxRows(input: InputStream, reader: XSSFReader, formatter: DataFormatter, maxRows: Int): Unit =
    val handler = new XSSFSheetXMLHandler.SheetContentsHandler:
      private var values        = Vector.empty[String]
      private var emittedRows   = 0

      override def startRow(rowNum: Int): Unit = values = Vector.empty

      override def cell(cellReference: String, formattedValue: String, comment: XSSFComment): Unit =
        if values.size < MaxCellsPerRow then values :+= formattedValue.trim

      override def endRow(rowNum: Int): Unit =
        if values.nonEmpty then
          println(s"row\t${rowNum + 1}\t${values.mkString(" | ")}")
          emittedRows += 1
          if emittedRows >= maxRows then throw StopInspection

      override def headerFooter(text: String, isHeader: Boolean, tagName: String): Unit = ()

    val parser = XMLReaderFactory.createXMLReader()
    parser.setContentHandler(new XSSFSheetXMLHandler(reader.getStylesTable, null, reader.getSharedStringsTable, handler, formatter, false))
    try parser.parse(new InputSource(input))
    catch case StopInspection => ()

  private def isWorkbook(name: String): Boolean =
    val lower = name.toLowerCase(Locale.ROOT)
    lower.endsWith(".xls") || lower.endsWith(".xlsx")

  private object StopInspection extends SAXException

  private final case class Options(
      archives: List[String],
      entrySubstring: Option[String],
      sheetSubstring: Option[String],
      maxRowsPerSheet: Int,
  ):
    def matchesEntry(entryName: String): Boolean =
      entrySubstring.forall(entryName.toLowerCase(Locale.ROOT).contains)

    def matchesSheet(sheetName: String): Boolean =
      sheetSubstring.forall(sheetName.toLowerCase(Locale.ROOT).contains)

  private object Options:
    def parse(args: List[String]): Options =
      val (optionArgs, archives) = args.partition(_.startsWith("--"))
      val entrySubstring         = optionArgs.collectFirst {
        case value if value.startsWith("--entry=") => value.stripPrefix("--entry=").toLowerCase(Locale.ROOT)
      }
      val sheetSubstring         = optionArgs.collectFirst {
        case value if value.startsWith("--sheet=") => value.stripPrefix("--sheet=").toLowerCase(Locale.ROOT)
      }
      val maxRowsPerSheet        = optionArgs
        .collectFirst {
          case value if value.startsWith("--max-rows=") => value.stripPrefix("--max-rows=")
        }
        .flatMap(_.toIntOption)
        .filter(_ > 0)
        .getOrElse(12)

      Options(archives, entrySubstring, sheetSubstring, maxRowsPerSheet)
