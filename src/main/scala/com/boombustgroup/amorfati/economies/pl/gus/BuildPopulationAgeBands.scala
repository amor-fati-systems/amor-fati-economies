package com.boombustgroup.amorfati.economies.pl.gus

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** Aggregates the single-age population intermediate into the declared v1 age
  * bands. Collective-residence subtraction is intentionally a later stage; this
  * output remains the gross population-balance intermediate.
  */
object BuildPopulationAgeBands:
  private val Header = "region\tsex\tage_band\tcount"

  private val Bands = Vector(
    "0-14"  -> (0, 14),
    "15-17" -> (15, 17),
    "18-19" -> (18, 19),
    "20-24" -> (20, 24),
    "25-29" -> (25, 29),
    "30-34" -> (30, 34),
    "35-39" -> (35, 39),
    "40-44" -> (40, 44),
    "45-49" -> (45, 49),
    "50-54" -> (50, 54),
    "55-59" -> (55, 59),
    "60-64" -> (60, 64),
    "65-74" -> (65, 74),
    "75-89" -> (75, 89),
    "90+"   -> (90, 200),
  )

  def main(args: Array[String]): Unit =
    args match
      case Array(input, output) =>
        val grouped    = Files.readAllLines(Path.of(input), UTF_8).asScala.drop(1).flatMap(parse).groupMapReduce(row => (row._1, row._2, row._3))(_._4)(_ + _)
        val rows       = grouped.toVector.sortBy { case ((region, sex, band), _) => (region, sex, Bands.indexWhere(_._1 == band)) }.map {
          case ((region, sex, band), count) => s"$region\t$sex\t$band\t$count"
        }
        val outputPath = Path.of(output)
        Option(outputPath.getParent).foreach(parent => Files.createDirectories(parent))
        Files.writeString(outputPath, (Header +: rows).mkString("\n") + "\n", UTF_8)
      case _                    =>
        Console.err.println("Usage: BuildPopulationAgeBands <single-age.tsv> <output.tsv>")
        sys.exit(2)

  private def parse(line: String): Option[(String, String, String, Long)] =
    line.split('\t').toList match
      case region :: sex :: age :: count :: Nil =>
        for
          ageValue <- if age.matches("90\\+.*") then Some(90) else age.toIntOption
          band     <- Bands.collectFirst { case (label, (min, max)) if ageValue >= min && ageValue <= max => label }
          value    <- count.toLongOption
        yield (region, sex, band, value)
      case _                                    => None
