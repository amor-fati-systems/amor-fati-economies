package com.boombustgroup.amorfati.economies.pl.gus

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import org.apache.poi.ss.usermodel.{DataFormatter, WorkbookFactory}

import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Normalizes BAEL tab.1.3 sex-by-age labour margins from thousands to persons. */
object NormalizeBaelLabour:
  private val Header = "sex\tage_band\tstatus\tpersons\tsource_unit"
  private val Ages = Map("15-17 lat" -> "15-17", "18-19" -> "18-19", "20-24" -> "20-24", "25-29" -> "25-29", "30-34" -> "30-34", "35-39" -> "35-39", "40-44" -> "40-44", "45-49" -> "45-49", "50-54" -> "50-54", "55-59" -> "55-59", "60-64" -> "60-64", "65-89 lat" -> "65-89-source")

  def main(args: Array[String]): Unit =
    args match
      case Array(input, output) =>
        val formatter = new DataFormatter(java.util.Locale.ROOT)
        val rows = Using.resource(WorkbookFactory.create(Path.of(input).toFile)) { workbook =>
          val sheet = workbook.getSheet("tab.1.3")
          var sex = "total"
          sheet.iterator().asScala.flatMap { row =>
            val values = row.cellIterator().asScala.map(cell => formatter.formatCellValue(cell).trim).toVector
            val labelIndex = values.indexWhere(value => Ages.contains(value) || value == "Mężczyźni" || value == "Kobiety")
            val label      = if labelIndex >= 0 then values(labelIndex) else ""
            if label == "Mężczyźni" then sex = "male"
            if label == "Kobiety" then sex = "female"
            Ages.get(label).toVector.flatMap { band =>
              if sex == "total" then Vector.empty
              else
                Vector(
                  (sex, band, "employed", values.lift(labelIndex + 3).flatMap(parseThousands)),
                  (sex, band, "unemployed", values.lift(labelIndex + 6).flatMap(parseThousands)),
                  (sex, band, "inactive", values.lift(labelIndex + 7).flatMap(parseThousands)),
                ).collect { case (s, b, status, Some(value)) => s"$s\t$b\t$status\t${value * 1000L}\tthousands" }
            }
          }.toVector
        }
        Files.createDirectories(Path.of(output).getParent)
        Files.writeString(Path.of(output), (Header +: rows).mkString("\n") + "\n", UTF_8)
      case _ =>
        Console.err.println("Usage: NormalizeBaelLabour <bael.xls> <output.tsv>")
        sys.exit(2)

  private def parseThousands(value: String): Option[Long] =
    value.replace(" ", "").replace(",", ".").toDoubleOption.map(value => math.round(value))
