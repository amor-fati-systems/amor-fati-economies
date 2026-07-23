# Data Governance

`amor-fati-economies` contains reproducible evidence records and reviewed,
data-only artifacts. It is not an archive of everything downloaded while
building a reference economy.

## Source Records

Every external input has a tracked source record under `sources/`. At minimum,
it names the provider, direct location, SHA-256 of the acquired bytes,
observation period, release date, access date, and raw-artifact policy. This
identifies the exact input without committing the raw artifact.

The `raw_artifact` object records two independent facts:

- `tracked_in_repository` says whether the acquired upstream bytes are present
  in Git. Raw downloads must be `false` in this public repository.
- `redistribution_status` records the review status of permission to rehost
  those upstream bytes. `not_assessed` means no such determination has been
  made; it does not imply permission or prohibition.

Each deterministic transformation must verify the declared source digest before
reading it. A changed upstream file is a new source artifact and requires a
new source record or an explicit reviewed update.

## Repository Content

The public repository may track small, redistributable, human-inspectable
derived artifacts such as normalized TSV controls, manifests, provenance, and
reconciliation reports. It must not track:

- raw downloads unless their redistribution terms have been reviewed and
  explicitly allow it;
- restricted, proprietary, or personally identifiable data;
- researcher notebooks, runs, or scenario outputs;
- silently derived aggregates with no declared source and recipe.

Licensed or restricted inputs belong in an access-controlled repository or
storage system. Their public counterpart may retain only non-sensitive
provenance and the conditions required to reproduce the permitted artifact.

## Reproducibility

The model core validates artifact contracts; this repository owns source
provenance and the transformation that produced them. Artifact manifests must
record their source identity and digest. A baseline correction is published as
a new version rather than an edit to an existing baseline directory.
