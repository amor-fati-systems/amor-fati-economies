package com.boombustgroup.amorfati.economies.pl.gus

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** Applies the declared 2021-to-2025 collective-residence bridge and emits the
  * private-household person control table. All bridge outputs are modelled.
  */
object BuildPrivatePersons:
  private val Header = "region\tsex\tage_band\tresidence\tcount"
  private val JsonRow = "\"name\":\"([^\"]+)\".*?\"val\":(\\d+)".r
  private val Bands = Vector("0-14", "15-17", "18-19", "20-24", "25-29", "30-34", "35-39", "40-44", "45-49", "50-54", "55-59", "60-64", "65-74", "75-89", "90+")

  def main(args: Array[String]): Unit =
    args match
      case Array(persons, collective, denominator, output) =>
        val gross = readPersons(Path.of(persons))
        val c21   = readJson(Path.of(collective))
        val p21   = readJson(Path.of(denominator))
        val privateRows: Vector[(String, String, String, Long)] = gross.groupBy(_._1).toVector.flatMap { case (region, rows) =>
          val total25 = rows.map(_._4).sum
          val key = region.toUpperCase(java.util.Locale.ROOT)
          val collective2025: Long = (BigDecimal(c21(key)) * BigDecimal(total25) / BigDecimal(p21(key)))
            .setScale(0, BigDecimal.RoundingMode.HALF_UP).toLong.min(total25)
          val allocations: Vector[Long] = allocate(rows, collective2025)
          rows.zip(allocations).map { case ((_, sex, band, count), collectiveCount) =>
            (region, sex, band, count - collectiveCount)
          }
        }
        val lines = privateRows.filter(_._4 > 0L).sortBy((region, sex, band, _) => (region, sex, Bands.indexOf(band))).map {
          case (region, sex, band, count) => s"$region\t$sex\t$band\tprivate_household\t$count"
        }
        Files.createDirectories(Path.of(output).getParent)
        Files.writeString(Path.of(output), (Header +: lines).mkString("\n") + "\n", UTF_8)
      case _ =>
        Console.err.println("Usage: BuildPrivatePersons <age-bands.tsv> <collective.json> <population-2021.json> <persons.tsv>")
        sys.exit(2)

  private def readPersons(path: Path): Vector[(String, String, String, Long)] =
    Files.readAllLines(path, UTF_8).asScala.drop(1).flatMap { line =>
      line.split('\t').toList match
        case region :: sex :: band :: count :: Nil => count.toLongOption.map(value => (region, sex, band, value))
        case _ => None
    }.toVector

  private def readJson(path: Path): Map[String, Long] =
    JsonRow.findAllMatchIn(Files.readString(path, UTF_8)).map(m => m.group(1).toUpperCase(java.util.Locale.ROOT) -> m.group(2).toLong).toMap

  private def allocate(rows: Vector[(String, String, String, Long)], total: Long): Vector[Long] =
    val denominator = rows.map(_._4).sum
    val exact       = rows.map(row => BigDecimal(row._4) * BigDecimal(total) / BigDecimal(denominator))
    val floors: Vector[Long] = exact.map(_.setScale(0, BigDecimal.RoundingMode.FLOOR).toLong)
    val remainder   = total - floors.sum
    val order       = exact.zip(floors).zipWithIndex.sortBy { case ((value, _), index) => (-(value - value.setScale(0, BigDecimal.RoundingMode.FLOOR)).toDouble, index) }.map(_._2)
    floors.zipWithIndex.map { case (floor, index) => floor + (if order.take(remainder.toInt).contains(index) then 1L else 0L) }
