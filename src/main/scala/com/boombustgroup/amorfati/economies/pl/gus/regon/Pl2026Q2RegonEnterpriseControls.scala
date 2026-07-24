package com.boombustgroup.amorfati.economies.pl.gus.regon

import com.boombustgroup.amorfati.economies.pl.gus.regon.Pl2026Q2RegonSource.{InspectionContract, InspectionError}
import org.apache.poi.ss.usermodel.{DataFormatter, Row, WorkbookFactory}

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Locale
import scala.util.{Try, Using}

/** Deterministic normalization of the pinned Q2 2026 REGON `Tabl 6` workbook
  * into the source-native enterprise-control bundle consumed by Amor Fati.
  *
  * It preserves registry entity counts by registered seat, PKD 2007 section,
  * and declared expected-worker band. It does not construct runtime firms,
  * infer realised employment, or map PKD 2007 to a production ontology.
  */
object Pl2026Q2RegonEnterpriseControls:

  val BaselineId: String = "PL-2026-Q2-v1"

  private val SchemaVersion = 2

  private val SourceProvider          = "GUS"
  private val SourceObservationPeriod = "2026-06-30"
  private val SourceRelease           = "2026-07-16"
  private val SourceAccessedAt        = "2026-07-21"

  private val RegionClassificationId      = "PL-TERYT-VOIVODESHIP"
  private val RegionClassificationVersion = "2026-06-30"
  private val PkdClassificationId         = "PKD-2007-SECTION"
  private val PkdClassificationVersion    = "2007"
  private val BandClassificationId        = "REGON-EXPECTED-WORKERS-BAND"
  private val BandClassificationVersion   = "2026-Q2"

  private val WorkerBands = Vector("0-9", "10-49", "50-249", "250=>")
  private val Sections    = ('A' to 'U').map(_.toString).toVector

  private val RegionCodes = Vector(
    "DOLNOŚLĄSKIE"        -> "02",
    "KUJAWSKO-POMORSKIE"  -> "04",
    "LUBELSKIE"           -> "06",
    "LUBUSKIE"            -> "08",
    "ŁÓDZKIE"             -> "10",
    "MAŁOPOLSKIE"         -> "12",
    "MAZOWIECKIE"         -> "14",
    "OPOLSKIE"            -> "16",
    "PODKARPACKIE"        -> "18",
    "PODLASKIE"           -> "20",
    "POMORSKIE"           -> "22",
    "ŚLĄSKIE"             -> "24",
    "ŚWIĘTOKRZYSKIE"      -> "26",
    "WARMIŃSKO-MAZURSKIE" -> "28",
    "WIELKOPOLSKIE"       -> "30",
    "ZACHODNIOPOMORSKIE"  -> "32",
  )

  private val RegionCodeBySourceLabel = RegionCodes.toMap

  private val ManifestFile         = "manifest.tsv"
  private val RegionsFile          = "registered-seat-regions.tsv"
  private val Pkd2007SectionsFile  = "pkd2007-sections.tsv"
  private val ExpectedWorkersFile  = "expected-workers-bands.tsv"
  private val EnterpriseStrataFile = "enterprise-strata.tsv"
  private val SourceResidualsFile  = "source-residuals.tsv"
  private val SourceTotalsFile     = "source-totals.tsv"

  private val ComponentFiles = Vector(
    RegionsFile,
    Pkd2007SectionsFile,
    ExpectedWorkersFile,
    EnterpriseStrataFile,
    SourceResidualsFile,
    SourceTotalsFile,
  )

  final case class Stratum(registeredSeatRegion: String, pkd2007Section: String, expectedWorkersBand: String, count: Long)

  final case class Residual(
      sourceResidualCategory: String,
      registeredSeatRegion: Option[String],
      pkd2007Section: Option[String],
      expectedWorkersBand: String,
      count: Long,
  )

  final case class SourceTotal(expectedWorkersBand: String, count: Long)

  final case class Candidate(
      enterpriseStrata: Vector[Stratum],
      sourceResiduals: Vector[Residual],
      sourceTotals: Vector[SourceTotal],
  )

  final case class Written(root: Path, digest: String)

  enum ExtractionError:
    case Source(error: InspectionError)
    case UnexpectedLastRowIndex(expected: Int, actual: Int)
    case DuplicateNationalTotal(rowIndex: Int)
    case DuplicateResidualMargin(label: String, rowIndex: Int)
    case MissingNationalTotal
    case MissingResidualMargin(label: String)
    case UnknownRegionLabel(rowIndex: Int, label: String)
    case MissingRegionContext(rowIndex: Int, section: String)
    case InvalidCount(rowIndex: Int, column: String, value: String)
    case InvalidNationalTotal(rowIndex: Int, expected: Long, actual: Long)
    case InvalidResidualIntersection(band: String, total: Long, knownKnown: Long, missingRegisteredSeat: Long, missingPkd: Long)
    case DuplicateRow(table: String, key: String)
    case MissingSourceTotal(band: String)
    case FailedReconciliation(band: String, expected: Long, actual: Long)
    case ExistingOutput(root: Path)
    case WriteFailure(root: Path, detail: String)

  private enum RegionContext:
    case Unset
    case Ordinary(code: String)

  private final case class ParseState(
      regionContext: RegionContext,
      sourceTotals: Option[Vector[SourceTotal]],
      missingRegisteredSeatMargins: Option[Vector[SourceTotal]],
      missingPkdMargins: Option[Vector[SourceTotal]],
      enterpriseStrata: Vector[Stratum],
      sourceResiduals: Vector[Residual],
  )

  /** Parse the production source using the pinned source contract. */
  def extract(source: Path): Either[ExtractionError, Candidate] =
    for
      sourceBytes <- Pl2026Q2RegonSource.readVerifiedBytes(source).left.map(ExtractionError.Source.apply)
      candidate   <- parseVerifiedBytes(sourceBytes, Pl2026Q2RegonSource.ExpectedTabl6LastRowIndex, Pl2026Q2RegonSource.Tabl6Name)
    yield candidate

  /** Fixture entry point. Production callers always use [[extract]]. */
  private[regon] def extract(source: Path, contract: InspectionContract): Either[ExtractionError, Candidate] =
    for
      sourceBytes <- Pl2026Q2RegonSource.readVerifiedBytes(source, contract).left.map(ExtractionError.Source.apply)
      candidate   <- parseVerifiedBytes(sourceBytes, contract.expectedLastRowIndex, contract.tabl6Name)
    yield candidate

  /** Write a complete bundle to a previously absent root directory. The
    * component manifest contains a digest computed from the exact normalized
    * TSV bytes written beside it.
    */
  def write(candidate: Candidate, root: Path): Either[ExtractionError, Written] =
    if Files.exists(root) then Left(ExtractionError.ExistingOutput(root))
    else
      val rendered = render(candidate)
      Try {
        Files.createDirectories(root)
        rendered.files.foreach: (name, content) =>
          Files.writeString(root.resolve(name), content, UTF_8)
        Written(root, rendered.digest)
      }.toEither.left.map(error => ExtractionError.WriteFailure(root, messageOf(error)))

  private final case class Rendered(files: Vector[(String, String)], digest: String)

  private def parseVerifiedBytes(sourceBytes: Array[Byte], expectedLastRowIndex: Int, sheetName: String): Either[ExtractionError, Candidate] =
    Using
      .Manager { use =>
        val input    = use(new ByteArrayInputStream(sourceBytes))
        val workbook = use(WorkbookFactory.create(input))
        Option(workbook.getSheet(sheetName))
          .toRight(ExtractionError.Source(InspectionError.MissingSheet(sheetName)))
          .flatMap: sheet =>
            val lastRowIndex = sheet.getLastRowNum
            if lastRowIndex != expectedLastRowIndex then Left(ExtractionError.UnexpectedLastRowIndex(expectedLastRowIndex, lastRowIndex))
            else
              val formatter = new DataFormatter(Locale.ROOT)
              (4 to lastRowIndex)
                .foldLeft[Either[ExtractionError, ParseState]](Right(ParseState(RegionContext.Unset, None, None, None, Vector.empty, Vector.empty))):
                  (state, rowIndex) => state.flatMap(current => consumeRow(current, rowIndex, rowValues(sheet.getRow(rowIndex), formatter)))
                .flatMap(toCandidate)
      }
      .toEither
      .left
      .map(error => ExtractionError.Source(InspectionError.ReadFailure(messageOf(error))))
      .flatMap(identity)

  private def consumeRow(state: ParseState, rowIndex: Int, values: Vector[String]): Either[ExtractionError, ParseState] =
    val region  = normalized(values(0))
    val section = normalized(values(1))

    region match
      case ""                 => consumeNestedRow(state, rowIndex, section, values)
      case "OGÓŁEM"           =>
        readNationalTotals(rowIndex, values).flatMap: totals =>
          if state.sourceTotals.nonEmpty then Left(ExtractionError.DuplicateNationalTotal(rowIndex))
          else Right(state.copy(sourceTotals = Some(totals)))
      case "BRAK WOJEWÓDZTWA" =>
        recordResidualMargin(
          state,
          rowIndex,
          "Brak województwa",
          values,
          _.missingRegisteredSeatMargins,
          (current, totals) => current.copy(missingRegisteredSeatMargins = Some(totals)),
        )
      case "BRAK PKD"         =>
        recordResidualMargin(state, rowIndex, "Brak PKD", values, _.missingPkdMargins, (current, totals) => current.copy(missingPkdMargins = Some(totals)))
      case label              =>
        RegionCodeBySourceLabel
          .get(label)
          .map(code => Right(state.copy(regionContext = RegionContext.Ordinary(code))))
          .getOrElse(
            Left(ExtractionError.UnknownRegionLabel(rowIndex, values(0))),
          )

  private def consumeNestedRow(
      state: ParseState,
      rowIndex: Int,
      section: String,
      values: Vector[String],
  ): Either[ExtractionError, ParseState] =
    if Sections.contains(section) then
      state.regionContext match
        case RegionContext.Ordinary(region) => appendStrata(state, rowIndex, region, section, values)
        case RegionContext.Unset            => Left(ExtractionError.MissingRegionContext(rowIndex, section))
    else if section == "BRAK PKD" then
      state.regionContext match
        case RegionContext.Ordinary(region) => appendResidual(state, rowIndex, "missing_pkd", Some(region), None, values)
        case RegionContext.Unset            => Left(ExtractionError.MissingRegionContext(rowIndex, section))
    else Right(state)

  private def readNationalTotals(rowIndex: Int, values: Vector[String]): Either[ExtractionError, Vector[SourceTotal]] =
    for
      total <- parseCount(rowIndex, "Ogółem", values(4))
      bands <- parseBandCounts(rowIndex, values)
      actual = bands.map(_._2).sum
      _     <- Either.cond(actual == total, (), ExtractionError.InvalidNationalTotal(rowIndex, total, actual))
    yield bands.map { case (band, count) => SourceTotal(band, count) }

  private def recordResidualMargin(
      state: ParseState,
      rowIndex: Int,
      label: String,
      values: Vector[String],
      existing: ParseState => Option[Vector[SourceTotal]],
      update: (ParseState, Vector[SourceTotal]) => ParseState,
  ): Either[ExtractionError, ParseState] =
    readNationalTotals(rowIndex, values).flatMap: totals =>
      if existing(state).nonEmpty then Left(ExtractionError.DuplicateResidualMargin(label, rowIndex))
      else Right(update(state, totals))

  private def appendStrata(
      state: ParseState,
      rowIndex: Int,
      region: String,
      section: String,
      values: Vector[String],
  ): Either[ExtractionError, ParseState] =
    parseBandCounts(rowIndex, values).map: counts =>
      state.copy(enterpriseStrata = state.enterpriseStrata ++ counts.map { case (band, count) => Stratum(region, section, band, count) })

  private def appendResidual(
      state: ParseState,
      rowIndex: Int,
      category: String,
      region: Option[String],
      section: Option[String],
      values: Vector[String],
  ): Either[ExtractionError, ParseState] =
    parseBandCounts(rowIndex, values).map: counts =>
      state.copy(
        sourceResiduals = state.sourceResiduals ++ counts.map { case (band, count) =>
          Residual(category, region, section, band, count)
        },
      )

  private def parseBandCounts(rowIndex: Int, values: Vector[String]): Either[ExtractionError, Vector[(String, Long)]] =
    WorkerBands.zipWithIndex.foldLeft[Either[ExtractionError, Vector[(String, Long)]]](Right(Vector.empty)):
      case (counts, (band, index)) =>
        for
          accumulated <- counts
          count       <- parseCount(rowIndex, band, values(index + 5))
        yield accumulated :+ (band -> count)

  private def parseCount(rowIndex: Int, column: String, value: String): Either[ExtractionError, Long] =
    val normalizedValue = value.replace('\u00a0', ' ').replace(" ", "").trim
    if normalizedValue == "-" then Right(0L)
    else normalizedValue.toLongOption.filter(_ >= 0L).toRight(ExtractionError.InvalidCount(rowIndex, column, value))

  private def toCandidate(state: ParseState): Either[ExtractionError, Candidate] =
    for
      totals                       <- state.sourceTotals.toRight(ExtractionError.MissingNationalTotal)
      missingRegisteredSeatMargins <- state.missingRegisteredSeatMargins.toRight(ExtractionError.MissingResidualMargin("Brak województwa"))
      missingPkdMargins            <- state.missingPkdMargins.toRight(ExtractionError.MissingResidualMargin("Brak PKD"))
      partitionedResiduals         <- partitionResidualMargins(state.enterpriseStrata, totals, missingRegisteredSeatMargins, missingPkdMargins)
      residuals                     = state.sourceResiduals ++ partitionedResiduals
      _                            <- unique("enterprise-strata", state.enterpriseStrata)(row => s"${row.registeredSeatRegion}|${row.pkd2007Section}|${row.expectedWorkersBand}")
      _                            <- unique("source-residuals", residuals)(row =>
        s"${row.sourceResidualCategory}|${row.registeredSeatRegion.getOrElse("")}|${row.pkd2007Section.getOrElse("")}|${row.expectedWorkersBand}",
      )
      _                            <- unique("source-totals", totals)(_.expectedWorkersBand)
      _                            <- WorkerBands.foldLeft[Either[ExtractionError, Unit]](Right(())): (result, band) =>
        result.flatMap: _ =>
          totals
            .find(_.expectedWorkersBand == band)
            .toRight(ExtractionError.MissingSourceTotal(band))
            .flatMap: total =>
              val actual = state.enterpriseStrata.iterator.filter(_.expectedWorkersBand == band).map(_.count).sum +
                residuals.iterator.filter(_.expectedWorkersBand == band).map(_.count).sum
              Either.cond(actual == total.count, (), ExtractionError.FailedReconciliation(band, total.count, actual))
    yield Candidate(
      enterpriseStrata = state.enterpriseStrata.sortBy(row => (row.registeredSeatRegion, row.pkd2007Section, WorkerBands.indexOf(row.expectedWorkersBand))),
      sourceResiduals = residuals.sortBy(row =>
        (row.sourceResidualCategory, row.registeredSeatRegion.getOrElse(""), row.pkd2007Section.getOrElse(""), WorkerBands.indexOf(row.expectedWorkersBand)),
      ),
      sourceTotals = totals.sortBy(row => WorkerBands.indexOf(row.expectedWorkersBand)),
    )

  private def partitionResidualMargins(
      enterpriseStrata: Vector[Stratum],
      sourceTotals: Vector[SourceTotal],
      missingRegisteredSeatMargins: Vector[SourceTotal],
      missingPkdMargins: Vector[SourceTotal],
  ): Either[ExtractionError, Vector[Residual]] =
    WorkerBands.foldLeft[Either[ExtractionError, Vector[Residual]]](Right(Vector.empty)): (result, band) =>
      result.flatMap: accumulated =>
        for
          total                 <- countFor(sourceTotals, band, "source total")
          missingRegisteredSeat <- countFor(missingRegisteredSeatMargins, band, "Brak województwa")
          missingPkd            <- countFor(missingPkdMargins, band, "Brak PKD")
          knownKnown             = enterpriseStrata.iterator.filter(_.expectedWorkersBand == band).map(_.count).sum
          missingBoth            = knownKnown + missingRegisteredSeat + missingPkd - total
          _                     <- Either.cond(
            missingBoth >= 0L && missingBoth <= missingRegisteredSeat && missingBoth <= missingPkd,
            (),
            ExtractionError.InvalidResidualIntersection(band, total, knownKnown, missingRegisteredSeat, missingPkd),
          )
        yield accumulated ++ Vector(
          Residual("missing_registered_seat_only", None, None, band, missingRegisteredSeat - missingBoth),
          Residual("missing_pkd_only", None, None, band, missingPkd - missingBoth),
          Residual("missing_registered_seat_and_pkd", None, None, band, missingBoth),
        )

  private def countFor(totals: Vector[SourceTotal], band: String, label: String): Either[ExtractionError, Long] =
    totals.find(_.expectedWorkersBand == band).map(_.count).toRight(ExtractionError.MissingResidualMargin(s"$label:$band"))

  private def unique[A](table: String, rows: Vector[A])(key: A => String): Either[ExtractionError, Unit] =
    rows.groupBy(key).collectFirst { case (duplicateKey, values) if values.size > 1 => ExtractionError.DuplicateRow(table, duplicateKey) }.toLeft(())

  private def render(candidate: Candidate): Rendered =
    val componentFiles = Vector(
      RegionsFile          -> table("code", RegionCodes.map(_._2)),
      Pkd2007SectionsFile  -> table("code", Sections),
      ExpectedWorkersFile  -> table("code", WorkerBands),
      EnterpriseStrataFile -> table(
        "registered_seat_region\tpkd2007_section\texpected_workers_band\tcount",
        candidate.enterpriseStrata.map(row => s"${row.registeredSeatRegion}\t${row.pkd2007Section}\t${row.expectedWorkersBand}\t${row.count}"),
      ),
      SourceResidualsFile  -> table(
        "source_residual_category\tregistered_seat_region\tpkd2007_section\texpected_workers_band\tcount",
        candidate.sourceResiduals.map: row =>
          s"${row.sourceResidualCategory}\t${row.registeredSeatRegion.getOrElse("")}\t${row.pkd2007Section.getOrElse("")}\t${row.expectedWorkersBand}\t${row.count}",
      ),
      SourceTotalsFile     -> table(
        "expected_workers_band\tcount",
        candidate.sourceTotals.map(row => s"${row.expectedWorkersBand}\t${row.count}"),
      ),
    )
    val digest         = componentDigest(componentFiles.toMap)
    val manifest       = table(
      "schema_version\tbaseline_id\tenterprise_controls_digest\tregistered_seat_region_classification_id\tregistered_seat_region_classification_version\tpkd2007_section_classification_id\tpkd2007_section_classification_version\texpected_workers_band_classification_id\texpected_workers_band_classification_version\tsource_provider\tsource_location\tsource_sha256\tsource_observation_period\tsource_release\tsource_accessed_at",
      Vector(
        s"$SchemaVersion\t$BaselineId\t$digest\t$RegionClassificationId\t$RegionClassificationVersion\t$PkdClassificationId\t$PkdClassificationVersion\t$BandClassificationId\t$BandClassificationVersion\t$SourceProvider\t${Pl2026Q2RegonSource.SourceLocation}\t${Pl2026Q2RegonSource.ExpectedSha256}\t$SourceObservationPeriod\t$SourceRelease\t$SourceAccessedAt",
      ),
    )
    Rendered((ManifestFile -> manifest) +: componentFiles, digest)

  private def componentDigest(componentFiles: Map[String, String]): String =
    val manifestFields = Vector(
      "enterprise-control-bundle-v2",
      s"schema_version=$SchemaVersion",
      s"baseline_id=$BaselineId",
      s"registered_seat_region_classification=$RegionClassificationId@$RegionClassificationVersion",
      s"pkd2007_section_classification=$PkdClassificationId@$PkdClassificationVersion",
      s"expected_workers_band_classification=$BandClassificationId@$BandClassificationVersion",
      s"source_provider=$SourceProvider",
      s"source_location=${Pl2026Q2RegonSource.SourceLocation}",
      s"source_sha256=${Pl2026Q2RegonSource.ExpectedSha256}",
      s"source_observation_period=$SourceObservationPeriod",
      s"source_release=$SourceRelease",
      s"source_accessed_at=$SourceAccessedAt",
    )
    val fileFields     = ComponentFiles.sorted.map(file => s"$file=${sha256Hex(componentFiles(file).getBytes(UTF_8))}")
    sha256Hex((manifestFields ++ fileFields).mkString("\n").concat("\n").getBytes(UTF_8))

  private def table(header: String, rows: Vector[String]): String = (header +: rows).mkString("\n").concat("\n")

  private def rowValues(row: Row | Null, formatter: DataFormatter): Vector[String] =
    Option(row).fold(Vector.fill(9)("")): current =>
      (0 to 8).toVector.map: columnIndex =>
        Option(current.getCell(columnIndex)).fold("")(cell => formatter.formatCellValue(cell).trim)

  private def normalized(value: String): String = value.replace('\u00a0', ' ').trim.toUpperCase(Locale.ROOT)

  private def sha256Hex(bytes: Array[Byte]): String =
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(bytes)
    digest.digest().iterator.map(byte => f"${byte & 0xff}%02x").mkString

  private def messageOf(error: Throwable): String = Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)

@main def writePl2026Q2RegonEnterpriseControls(source: String, outputRoot: String): Unit =
  val sourcePath = Path.of(source)
  val root       = Path.of(outputRoot)
  Pl2026Q2RegonEnterpriseControls.extract(sourcePath).flatMap(candidate => Pl2026Q2RegonEnterpriseControls.write(candidate, root)) match
    case Left(error)   =>
      Console.err.println(error)
      sys.exit(1)
    case Right(bundle) =>
      println(s"bundle_root=${bundle.root}")
      println(s"enterprise_controls_digest=${bundle.digest}")
