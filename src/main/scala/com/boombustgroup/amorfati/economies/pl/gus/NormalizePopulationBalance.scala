package com.boombustgroup.amorfati.economies.pl.gus

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import org.apache.poi.ss.usermodel.{DataFormatter, WorkbookFactory}

import scala.jdk.CollectionConverters.*
import scala.util.Using

/** Normalizes GUS tabela03.xls into one row per voivodeship, sex, and age
  * label. The source workbook is kept outside Git; this output is a
  * deterministic intermediate, not yet a population-control bundle.
  */
object NormalizePopulationBalance:
  private val Header = "region\tsex\tage_label\tcount"

  def main(args: Array[String]): Unit =
    args match
      case Array(input, output) => normalize(Path.of(input), Path.of(output))
      case _ =>
        Console.err.println("Usage: NormalizePopulationBalance <tabela03.xls> <output.tsv>")
        sys.exit(2)

  private def normalize(input: Path, output: Path): Unit =
    val formatter = new DataFormatter(java.util.Locale.ROOT)
    val rows      = Using.resource(WorkbookFactory.create(input.toFile)) { workbook =>
      (0 until workbook.getNumberOfSheets).iterator.flatMap { index =>
        val sheet  = workbook.getSheetAt(index)
        val region = sheet.getSheetName.trim
        sheet.iterator().asScala.drop(9).flatMap { row =>
          val cells = row.cellIterator().asScala.map(cell => formatter.formatCellValue(cell).trim).toVector
          if cells.size >= 4 then
            val label = cells.head
            for
              _     <- cells.lift(1).flatMap(parseCount)
              male  <- cells.lift(2).flatMap(parseCount)
              female <- cells.lift(3).flatMap(parseCount)
              if isAgeLabel(label)
            yield Vector(
              s"$region\tmale\t${escape(label)}\t$male",
              s"$region\tfemale\t${escape(label)}\t$female",
            )
          else Vector.empty
        }.flatten
      }.toVector
    }
    Files.createDirectories(output.getParent)
    Files.writeString(output, (Header +: rows).mkString("\n") + "\n", UTF_8)

  private def parseCount(value: String): Option[Long] =
    value.replace(" ", "").replace(" ", "").toLongOption

  private def isAgeLabel(value: String): Boolean =
    value.matches("\\d+") || value.matches("90\\+.*")

  private def escape(value: String): String = value.replace('\t', ' ')
