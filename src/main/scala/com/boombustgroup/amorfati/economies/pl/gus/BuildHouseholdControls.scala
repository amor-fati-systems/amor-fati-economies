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
      case Array(sizePath, compositionPath, personsPath, householdsPath, membershipPath) =>
        val sizes = read(sizePath).map { case (region, key, count) => (region, key, count) }
        val targetPersons = readPersons(personsPath)
        val compositionShares = read(compositionPath).groupBy(_._1).view.mapValues { rows =>
          val totals = rows.groupMapReduce(row => normalizeComposition(row._2))(_._3)(_ + _)
          val total  = totals.values.sum.toDouble
          totals.toVector.map { case (label, count) => label -> count.toDouble / total }
        }.toMap
        val rawRows = sizes.flatMap { case (region, sizeLabel, total) =>
          val size = if sizeLabel == "5 i więcej" then 5 else sizeLabel.toInt
          val shares = compositionShares(region)
          allocate(total, shares.toVector.sortBy(_._1)).map { case (composition, count) => (region, size, composition, count) }
        }
        val householdRows = rebase(rawRows, targetPersons)
        val membership = householdRows.groupBy(_._3).toVector.flatMap { case (composition, rows) =>
          val households = rows.map(_._4).sum
          val sizeTotal  = rows.map { case (_, size, _, count) => size.toLong * count }.sum
          roleAllocation(composition, households, sizeTotal)
        }
        write(householdsPath, Header, householdRows.sortBy(row => (row._1, row._2, Compositions.indexOf(row._3))).map { case (region, size, composition, count) => s"$region\t$size\t$composition\t$count" })
        write(membershipPath, MembershipHeader, membership.map { case (composition, role, age, count) => s"$composition\t$role\t$age\t$count" })
      case _ =>
        Console.err.println("Usage: BuildHouseholdControls <size.tsv> <composition.tsv> <persons.tsv> <households.tsv> <membership.tsv>")
        sys.exit(2)

  private def read(path: String): Vector[(String, String, Long)] =
    Files.readAllLines(Path.of(path), UTF_8).asScala.drop(1).flatMap { line =>
      line.split('\t').toList match
        case region :: key :: count :: Nil => count.toLongOption.map(value => (region, key, value))
        case _ => None
    }.toVector

  private def readPersons(path: String): Map[String, Long] =
    Files.readAllLines(Path.of(path), UTF_8).asScala.drop(1).flatMap { line =>
      line.split('\t').toList match
        case region :: _ :: _ :: _ :: count :: Nil => count.toLongOption.map(region -> _)
        case _ => None
    }.groupMapReduce(_._1)(_._2)(_ + _)

  private def rebase(rows: Vector[(String, Int, String, Long)], targetPersons: Map[String, Long]): Vector[(String, Int, String, Long)] =
    rows.groupBy(_._1).toVector.flatMap { case (region, regionalRows) =>
      val target   = targetPersons(region)
      val capacity = regionalRows.map { case (_, size, _, count) => size.toLong * count }.sum
      val factor   = BigDecimal(target) / BigDecimal(capacity)
      val exact    = regionalRows.map { case (r, size, composition, count) => (r, size, composition, BigDecimal(count) * factor) }
      val floors   = exact.map { case (r, size, composition, value) => (r, size, composition, value.setScale(0, BigDecimal.RoundingMode.FLOOR).toLong) }
      val remainder = target - floors.map { case (_, size, _, count) => size.toLong * count }.sum
      val order = exact.zipWithIndex.sortBy { case ((_, size, _, value), index) => (-(size.toDouble * (value - value.setScale(0, BigDecimal.RoundingMode.FLOOR)).toDouble), index) }.map(_._2)
      val selected = order.iterator.scanLeft(remainder) { (left, index) => if left >= exact(index)._2 then left - exact(index)._2 else left }.zip(order).collect { case (before, index) if before > remainder - exact(index)._2 => index }.toSet
      val adjusted = floors.zipWithIndex.map { case ((r, size, composition, count), index) => (r, size, composition, count + (if selected.contains(index) then 1L else 0L)) }
      val residual = target - adjusted.map { case (_, size, _, count) => size.toLong * count }.sum
      adjusted.map {
        case (r, size, composition, count) if size == 1 && composition == "one_person" => (r, size, composition, count + residual)
        case row => row
      }.toVector
    }

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
