package com.boombustgroup.amorfati.economies.pl.gus

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.xssf.eventusermodel.{XSSFReader, XSSFSheetXMLHandler}
import org.apache.poi.xssf.usermodel.XSSFComment
import org.xml.sax.InputSource
import org.xml.sax.helpers.XMLReaderFactory

import scala.collection.mutable
import scala.util.Using

/** Event-streams NSP 2021 household-composition rows to voivodeship totals. */
object NormalizeHouseholdComposition:
  private val Header  = "region\tcomposition\thousehold_count"
  private val Regions = Map(
    "02" -> "Dolnośląskie",
    "04" -> "Kujawsko-pomorskie",
    "06" -> "Lubelskie",
    "08" -> "Lubuskie",
    "10" -> "Łódzkie",
    "12" -> "Małopolskie",
    "14" -> "Mazowieckie",
    "16" -> "Opolskie",
    "18" -> "Podkarpackie",
    "20" -> "Podlaskie",
    "22" -> "Pomorskie",
    "24" -> "Śląskie",
    "26" -> "Świętokrzyskie",
    "28" -> "Warmińsko-mazurskie",
    "30" -> "Wielkopolskie",
    "32" -> "Zachodniopomorskie",
  )

  def main(args: Array[String]): Unit =
    args match
      case Array(input, output) =>
        val totals = mutable.Map.empty[(String, String), Long].withDefaultValue(0L)
        Using.resource(OPCPackage.open(Path.of(input).toFile)): pkg =>
          val reader = new XSSFReader(pkg)
          val sheets = reader.getSheetsData.asInstanceOf[XSSFReader.SheetIterator]
          while sheets.hasNext do
            val stream = sheets.next()
            try if sheets.getSheetName == "Dane - rejony statystyczne" then parse(stream, reader, totals)
            finally stream.close()
        val lines  = totals.toVector.sortBy { case ((region, composition), _) => (region, composition) }.map { case ((region, composition), count) =>
          s"$region\t$composition\t$count"
        }
        Option(Path.of(output).getParent).foreach(parent => Files.createDirectories(parent))
        Files.writeString(Path.of(output), (Header +: lines).mkString("\n") + "\n", UTF_8)
      case _                    =>
        Console.err.println("Usage: NormalizeHouseholdComposition <household-composition.xlsx> <output.tsv>")
        sys.exit(2)

  private def parse(input: java.io.InputStream, reader: XSSFReader, totals: mutable.Map[(String, String), Long]): Unit =
    val handler = new XSSFSheetXMLHandler.SheetContentsHandler:
      private var values                                                                = Vector.empty[String]
      override def startRow(rowNum: Int): Unit                                          = values = Vector.empty
      override def cell(reference: String, value: String, comment: XSSFComment): Unit   = values :+= value.trim
      override def endRow(rowNum: Int): Unit                                            =
        if rowNum > 0 && values.size >= 5 then
          for
            region <- Regions.get(values(0).take(2))
            count  <- values(4).replace(" ", "").toLongOption
          do totals.update((region, values(3)), totals((region, values(3))) + count)
      override def headerFooter(text: String, isHeader: Boolean, tagName: String): Unit = ()
    val parser  = XMLReaderFactory.createXMLReader()
    parser.setContentHandler(
      new XSSFSheetXMLHandler(reader.getStylesTable, null, reader.getSharedStringsTable, handler, new org.apache.poi.ss.usermodel.DataFormatter(), false),
    )
    parser.parse(new InputSource(input))
