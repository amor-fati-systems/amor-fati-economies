package com.boombustgroup.amorfati.economies.pl.gus.regon

import com.boombustgroup.amorfati.economies.pl.gus.regon.Pl2026Q2RegonEnterpriseControls.{Candidate, ExtractionError, Residual, SourceTotal, Stratum}
import com.boombustgroup.amorfati.economies.pl.gus.regon.Pl2026Q2RegonSource.InspectionContract
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.util.Using

class Pl2026Q2RegonEnterpriseControlsSpec extends AnyFlatSpec with Matchers:

  private val bands = Vector("0-9", "10-49", "50-249", "250=>")

  private def withTemporaryFile[A](f: Path => A): A =
    val path = Files.createTempFile("pl-2026-q2-regon-enterprise-controls-", ".xlsx")
    try f(path)
    finally Files.deleteIfExists(path)

  private def writeWorkbook(path: Path, nationalCounts: Vector[Long]): Unit =
    Using.resource(new XSSFWorkbook): workbook =>
      val sheet = workbook.createSheet(Pl2026Q2RegonSource.Tabl6Name)
      writeCounts(sheet.createRow(4), "OGÓŁEM", "", nationalCounts)
      writeCounts(sheet.createRow(5), "DOLNOŚLĄSKIE", "", Vector(0L, 0L, 0L, 0L))
      writeCounts(sheet.createRow(6), "", "A", Vector(7L, 4L, 2L, 0L))
      writeCounts(sheet.createRow(7), "Brak województwa", "", Vector(3L, 2L, 1L, 0L))
      writeCounts(sheet.createRow(8), "Brak PKD", "", Vector(5L, 1L, 1L, 0L))
      sheet.createRow(10)
      Using.resource(Files.newOutputStream(path))(workbook.write)

  private def writeCounts(row: org.apache.poi.ss.usermodel.Row, region: String, section: String, counts: Vector[Long]): Unit =
    row.createCell(0).setCellValue(region)
    row.createCell(1).setCellValue(section)
    row.createCell(4).setCellValue(counts.sum.toDouble)
    counts.zipWithIndex.foreach: (count, index) =>
      row.createCell(index + 5).setCellValue(count.toDouble)

  private def fixtureContract(path: Path): InspectionContract =
    InspectionContract(
      MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).iterator.map(byte => f"${byte & 0xff}%02x").mkString,
      Pl2026Q2RegonSource.Tabl6Name,
      10,
    )

  "Pl2026Q2RegonEnterpriseControls.extract" should "partition overlapping missing-region and missing-PKD margins without changing the national totals" in
    withTemporaryFile: source =>
      writeWorkbook(source, Vector(14L, 7L, 3L, 0L))

      Pl2026Q2RegonEnterpriseControls.extract(source, fixtureContract(source)) shouldBe Right(
        Candidate(
          enterpriseStrata = bands
            .zip(Vector(7L, 4L, 2L, 0L))
            .map: (band, count) =>
              Stratum("02", "A", band, count),
          sourceResiduals = Vector(
            Residual("missing_pkd_only", None, None, "0-9", 4L),
            Residual("missing_pkd_only", None, None, "10-49", 1L),
            Residual("missing_pkd_only", None, None, "50-249", 0L),
            Residual("missing_pkd_only", None, None, "250=>", 0L),
            Residual("missing_registered_seat_and_pkd", None, None, "0-9", 1L),
            Residual("missing_registered_seat_and_pkd", None, None, "10-49", 0L),
            Residual("missing_registered_seat_and_pkd", None, None, "50-249", 1L),
            Residual("missing_registered_seat_and_pkd", None, None, "250=>", 0L),
            Residual("missing_registered_seat_only", None, None, "0-9", 2L),
            Residual("missing_registered_seat_only", None, None, "10-49", 2L),
            Residual("missing_registered_seat_only", None, None, "50-249", 0L),
            Residual("missing_registered_seat_only", None, None, "250=>", 0L),
          ),
          sourceTotals = bands
            .zip(Vector(14L, 7L, 3L, 0L))
            .map: (band, count) =>
              SourceTotal(band, count),
        ),
      )

  it should "reject margins whose inferred overlap cannot be a non-negative partition" in
    withTemporaryFile: source =>
      writeWorkbook(source, Vector(16L, 7L, 3L, 0L))

      Pl2026Q2RegonEnterpriseControls.extract(source, fixtureContract(source)) shouldBe Left(
        ExtractionError.InvalidResidualIntersection("0-9", 16L, 7L, 3L, 5L),
      )
