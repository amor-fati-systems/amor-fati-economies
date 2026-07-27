package com.boombustgroup.amorfati.economies.pl.gus

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Builds explicitly modelled regional labour and employment bridges from the
  * available margins.
  */
object BuildRegionalAndEmployment:
  private val Regions                 = Vector(
    "Dolnośląskie",
    "Kujawsko-Pomorskie",
    "Lubelskie",
    "Lubuskie",
    "Łódzkie",
    "Małopolskie",
    "Mazowieckie",
    "Opolskie",
    "Podkarpackie",
    "Podlaskie",
    "Pomorskie",
    "Śląskie",
    "Świętokrzyskie",
    "Warmińsko-Mazurskie",
    "Wielkopolskie",
    "Zachodniopomorskie",
  )
  def main(args: Array[String]): Unit = args match
    case Array(personsPath, demographicPath, regionalOut, employmentOut) =>
      val persons    = Files
        .readAllLines(Path.of(personsPath), UTF_8)
        .asScala
        .drop(1)
        .flatMap(_.split('\t').toList match
          case region :: _ :: band :: _ :: count :: Nil if band != "0-14" && band != "90+" => count.toLongOption.map(region -> _)
          case _                                                                           => None)
        .groupMapReduce(_._1)(_._2)(_ + _)
      val national   = Files
        .readAllLines(Path.of(demographicPath), UTF_8)
        .asScala
        .drop(1)
        .flatMap(_.split('\t').toList match
          case _ :: _ :: status :: count :: Nil => count.toLongOption.map(status -> _)
          case _                                => None)
        .groupMapReduce(_._1)(_._2)(_ + _)
      val rows       = national.toVector
        .flatMap { case (status, total) =>
          val denominator = persons.values.sum.toDouble
          persons.toVector.sortBy(_._1).map { case (region, base) => region -> status -> Math.round(total * base / denominator) }
        }
        .groupMapReduce(x => (x._1._1, x._1._2))(_._2)(_ + _)
        .toVector
        .sortBy(x => (Regions.indexOf(x._1._1), x._1._2))
      Files.writeString(Path.of(regionalOut), ("region\tstatus\tcount" +: rows.map { case ((r, s), c) => s"$r\t$s\t$c" }).mkString("\n") + "\n", UTF_8)
      val employed   = rows.collect { case ((r, "employed"), c) => (r, c) }
      val employment = employed.map { case (r, c) => s"$r\t$r\tA\t$c" }
      Files.writeString(Path.of(employmentOut), ("residence_region\tworkplace_region\tproduction_sector\tcount" +: employment).mkString("\n") + "\n", UTF_8)
    case _                                                               => sys.error("Usage: BuildRegionalAndEmployment <persons.tsv> <demographic-labour.tsv> <regional.tsv> <employment.tsv>")
