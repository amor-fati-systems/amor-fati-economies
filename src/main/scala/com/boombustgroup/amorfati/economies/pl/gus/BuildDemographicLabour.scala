package com.boombustgroup.amorfati.economies.pl.gus

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** Allocates national BAEL sex-age margins to private persons. */
object BuildDemographicLabour:
  private val Header = "sex\tage_band\tstatus\tcount"
  private val Bands = Vector("0-14", "15-17", "18-19", "20-24", "25-29", "30-34", "35-39", "40-44", "45-49", "50-54", "55-59", "60-64", "65-74", "75-89", "90+")

  def main(args: Array[String]): Unit =
    args match
      case Array(personsPath, baelPath, output) =>
        val persons = readPersons(personsPath)
        val bael    = readBael(baelPath)
        val rows = bael.flatMap { case (sex, sourceBand, status, target) =>
          val targetBands = if sourceBand == "65-89-source" then Vector("65-74", "75-89") else Vector(sourceBand)
          val available = targetBands.map(band => (band, persons.getOrElse((sex, band), 0L)))
          val allocations = allocate(target, available)
          allocations.map { case (band, count) => (sex, band, status, count) }
        }.groupMapReduce(row => (row._1, row._2, row._3))(_._4)(_ + _).toVector
          .map { case ((sex, band, status), count) => (sex, band, status, count) }
          .filter(_._4 > 0L).sortBy { case (sex, band, status, _) => (sex, Bands.indexOf(band), status) }
        Files.createDirectories(Path.of(output).getParent)
        Files.writeString(Path.of(output), (Header +: rows.map { case (sex, band, status, count) => s"$sex\t$band\t$status\t$count" }).mkString("\n") + "\n", UTF_8)
      case _ =>
        Console.err.println("Usage: BuildDemographicLabour <persons.tsv> <bael.tsv> <output.tsv>")
        sys.exit(2)

  private def readPersons(path: String): Map[(String, String), Long] =
    Files.readAllLines(Path.of(path), UTF_8).asScala.drop(1).flatMap { line =>
      line.split('\t').toList match
        case _ :: sex :: band :: _ :: count :: Nil => count.toLongOption.map(value => (sex, band) -> value)
        case _ => None
    }.groupMapReduce(_._1)(_._2)(_ + _)

  private def readBael(path: String): Vector[(String, String, String, Long)] =
    Files.readAllLines(Path.of(path), UTF_8).asScala.drop(1).flatMap { line =>
      line.split('\t').toList match
        case sex :: band :: status :: count :: _ :: Nil => count.toLongOption.map(value => (sex, band, status, value))
        case _ => None
    }.toVector

  private def allocate(total: Long, available: Vector[(String, Long)]): Vector[(String, Long)] =
    val denominator = available.map(_._2).sum
    if denominator == 0L then available.map((band, _) => band -> 0L)
    else
      val exact = available.map { case (band, count) => band -> (BigDecimal(total) * BigDecimal(count) / BigDecimal(denominator)) }
      val floors = exact.map { case (band, value) => band -> value.setScale(0, BigDecimal.RoundingMode.FLOOR).toLong }
      val remainder = total - floors.map(_._2).sum
      val order = exact.zipWithIndex.sortBy { case ((_, value), index) => (-(value - value.setScale(0, BigDecimal.RoundingMode.FLOOR)).toDouble, index) }.map(_._2).take(remainder.toInt).toSet
      floors.zipWithIndex.map { case ((band, value), index) => band -> (value + (if order.contains(index) then 1L else 0L)) }
