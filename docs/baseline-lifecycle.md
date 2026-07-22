# Baseline Lifecycle

A reference economy is an immutable, named collection of data-only components.
It is not a scenario, a notebook, a source download, or a set of Scala
defaults.

## Records

Every empirical component follows this chain:

1. A **source record** identifies an acquired external artifact by provider,
   location, SHA-256, observation period, release date, and access date.
2. A **recipe** records the baseline-specific choices that turn one or more
   sources into one named component. It states the source dimensions retained,
   residual treatment, forbidden inferences, and target output path.
3. A **component artifact** is a deterministic, data-only output. It carries
   its own manifest, provenance, and reconciliation evidence, and must pass
   the corresponding `amor-fati` contract loader.
4. A **baseline record** composes explicit component versions. It declares
   whether the collection is executable; it must not imply that missing
   components have been inferred.

## Component Status

`baseline.yaml` records one of these statuses for each component:

| Status | Meaning |
| --- | --- |
| `planned` | The component boundary is known but no source is pinned. |
| `source_pinned` | The source is identified and integrity-pinned, but no accepted artifact exists. |
| `artifact_validated` | A generated artifact passes the owned core contract and its declared reconciliation checks. |
| `runtime_validated` | The validated artifact has also passed the declared model-initialization and runtime evidence gate. |

A baseline remains `assembly` until all of its required components have the
status required by its declared executable contract. Only then may it be marked
`executable`. The current `PL-2026-Q2-v1` record is deliberately not
executable.

## Versioning

Baseline IDs have the form `<ISO-3166-1-alpha-2>-<YYYY>-<Qn>-v<n>`, for
example `PL-2026-Q2-v1`.

Once published, a baseline directory is immutable. A correction, source
revision, revised bridge, or improved calibration produces a new version such
as `PL-2026-Q2-v2`. Scenarios belong in `amor-fati-research` and reference an
unchanged baseline ID plus artifact hashes.
