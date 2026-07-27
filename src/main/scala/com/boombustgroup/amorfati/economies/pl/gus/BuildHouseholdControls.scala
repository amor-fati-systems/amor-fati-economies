package com.boombustgroup.amorfati.economies.pl.gus

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** Combines independent NSP household-size and composition margins into a
  * deterministic modelled joint table, then emits membership positions.
  */
object BuildHouseholdControls:
  private val Compositions = Vector("one_person", "couple_without_dependent_children", "couple_with_dependent_children", "lone_parent", "other_multi_person")
  private val Header = "region\tsize\tcomposition\tcount"
  private val MembershipHeader = "composition\tmember_role\tage_band\tcount"

  def main(args: Array[String]): Unit =
    args match
      case Array(sizePath, compositionPath, householdsPath, membershipPath) =>
        val sizes = read(sizePath).map { case (region, key, count) => (region, key, count) }
        val compositionShares = read(compositionPath).groupBy(_._1).view.mapValues { rows =>
          val totals = rows.groupMapReduce(row => normalizeComposition(row._2))(_._3)(_ + _)
          val total  = totals.values.sum.toDouble
          totals.toVector.map { case (label, count) => label -> count.toDouble / total }
        }.toMap
        val householdRows = sizes.flatMap { case (region, sizeLabel, total) =>
          val size = if sizeLabel == "5 i więcej" then 5 else sizeLabel.toInt
          val shares = compositionShares(region)
          allocate(total, shares.toVector.sortBy(_._1)).map { case (composition, count) => (region, size, composition, count) }
        }
        val membership = householdRows.groupBy(_._3).toVector.flatMap { case (composition, rows) =>
          val households = rows.map(_._4).sum
          val sizeTotal  = rows.map { case (_, size, _, count) => size.toLong * count }.sum
          roleAllocation(composition, households, sizeTotal)
        }
        write(householdsPath, Header, householdRows.sortBy(row => (row._1, row._2, Compositions.indexOf(row._3))).map { case (region, size, composition, count) => s"$region\t$size\t$composition\t$count" })
        write(membershipPath, MembershipHeader, membership.map { case (composition, role, age, count) => s"$composition\t$role\t$age\t$count" })
      case _ =>
        Console.err.println("Usage: BuildHouseholdControls <size.tsv> <composition.tsv> <households.tsv> <membership.tsv>")
        sys.exit(2)

  private def read(path: String): Vector[(String, String, Long)] =
    Files.readAllLines(Path.of(path), UTF_8).asScala.drop(1).flatMap { line =>
      line.split('\t').toList match
        case region :: key :: count :: Nil => count.toLongOption.map(value => (region, key, value))
        case _ => None
    }.toVector

  private def normalizeComposition(value: String): String = value match
    case "jednorodzinne" => "couple_with_dependent_children"
    case "dwurodzinne" | "trzy i więcej rodzinne" => "other_multi_person"
    case "nierodzinne - jednoosobowe" => "one_person"
    case "nierodzinne - wieloosobowe" => "other_multi_person"
    case other => other

  private def allocate(total: Long, shares: Vector[(String, Double)]): Vector[(String, Long)] =
    val exact = shares.map { case (key, share) => key -> (share * total) }
    val floors = exact.map { case (key, value) => key -> value.toLong }
    val remainder = total - floors.map(_._2).sum
    val order = exact.zipWithIndex.sortBy { case ((_, value), index) => (-(value - value.toLong), index) }.map(_._2).take(remainder.toInt).toSet
    floors.zipWithIndex.map { case ((key, value), index) => key -> (value + (if order.contains(index) then 1L else 0L)) }

  private def roleAllocation(composition: String, households: Long, persons: Long): Vector[(String, String, String, Long)] =
    composition match
      case "one_person" => Vector((composition, "adult", "25-64", persons))
      case "couple_without_dependent_children" => Vector((composition, "adult", "25-64", persons))
      case "couple_with_dependent_children" =>
        val children = (persons - 2L * households).max(0L)
        Vector((composition, "adult", "25-64", persons - children), (composition, "dependent_child", "0-14", children))
      case "lone_parent" =>
        val children = (persons - households).max(0L)
        Vector((composition, "adult", "25-64", households), (composition, "dependent_child", "0-14", children))
      case _ => Vector((composition, "other_member", "25-64", persons))

  private def write(path: String, header: String, rows: Vector[String]): Unit =
    Files.createDirectories(Path.of(path).getParent)
    Files.writeString(Path.of(path), (header +: rows).mkString("\n") + "\n", UTF_8)
