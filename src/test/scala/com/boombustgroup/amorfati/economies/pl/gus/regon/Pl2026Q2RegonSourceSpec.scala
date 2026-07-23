package com.boombustgroup.amorfati.economies.pl.gus.regon

import com.boombustgroup.amorfati.economies.pl.gus.regon.Pl2026Q2RegonSource.{InspectionContract, InspectionError}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.util.Using

class Pl2026Q2RegonSourceSpec extends AnyFlatSpec with Matchers:

  private def withTemporaryFile[A](f: Path => A): A =
    val path = Files.createTempFile("pl-2026-q2-regon-", ".xlsx")
    try f(path)
    finally Files.deleteIfExists(path)

  private def writeWorkbook(path: Path, sheetName: String, lastRowIndex: Int): Unit =
    Using.resource(new XSSFWorkbook): workbook =>
      val sheet = workbook.createSheet(sheetName)
      (0 to 19).foreach: rowIndex =>
        sheet.createRow(rowIndex).createCell(0).setCellValue(s"row-$rowIndex")
      if lastRowIndex > 19 then sheet.createRow(lastRowIndex)
      Using.resource(Files.newOutputStream(path))(workbook.write)

  private def sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).iterator.map(byte => f"${byte & 0xff}%02x").mkString

  private def fixtureContract(
      path: Path,
      tabl6Name: String = Pl2026Q2RegonSource.Tabl6Name,
      expectedLastRowIndex: Int = Pl2026Q2RegonSource.ExpectedTabl6LastRowIndex,
  ): InspectionContract =
    InspectionContract(sha256(path), tabl6Name, expectedLastRowIndex)

  "Pl2026Q2RegonSource.inspect" should "reject a source with a mismatched digest before parsing it" in
    withTemporaryFile: source =>
      Files.writeString(source, "untrusted source bytes")

      Pl2026Q2RegonSource.inspect(source) shouldBe Left(
        InspectionError.DigestMismatch(Pl2026Q2RegonSource.ExpectedSha256, sha256(source)),
      )

  it should "report a declared workbook sheet that is absent" in
    withTemporaryFile: source =>
      writeWorkbook(source, "Other table", 19)

      Pl2026Q2RegonSource.inspect(source, fixtureContract(source, expectedLastRowIndex = 19)) shouldBe Left(
        InspectionError.MissingSheet(Pl2026Q2RegonSource.Tabl6Name),
      )

  it should "map invalid workbook bytes to a read failure" in
    withTemporaryFile: source =>
      Files.writeString(source, "not an XLSX workbook")

      Pl2026Q2RegonSource.inspect(source, fixtureContract(source)) match
        case Left(InspectionError.ReadFailure(detail)) => detail should not be empty
        case other                                     => fail(s"expected invalid workbook read failure, got: $other")

  it should "preserve the header and initial data-row evidence from a valid fixture" in
    withTemporaryFile: source =>
      writeWorkbook(source, Pl2026Q2RegonSource.Tabl6Name, Pl2026Q2RegonSource.ExpectedTabl6LastRowIndex)

      Pl2026Q2RegonSource.inspect(source, fixtureContract(source)) match
        case Right(evidence) =>
          evidence.sheetName shouldBe Pl2026Q2RegonSource.Tabl6Name
          evidence.lastRowIndex shouldBe Pl2026Q2RegonSource.ExpectedTabl6LastRowIndex
          evidence.headerRows.size shouldBe 4
          evidence.headerRows.head.head shouldBe "row-0"
          evidence.firstDataRows.size shouldBe 16
          evidence.firstDataRows.head.head shouldBe "row-4"
        case other           => fail(s"expected valid fixture evidence, got: $other")

  it should "reject a workbook whose final row index differs from its declared contract" in
    withTemporaryFile: source =>
      writeWorkbook(source, Pl2026Q2RegonSource.Tabl6Name, 20)

      Pl2026Q2RegonSource.inspect(source, fixtureContract(source, expectedLastRowIndex = 19)) shouldBe Left(
        InspectionError.UnexpectedLastRowIndex(19, 20),
      )
