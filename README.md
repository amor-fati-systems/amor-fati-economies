# amor-fati-economies

Versioned reference economies, their source provenance, deterministic
transformations, and inspectable baseline artifacts for Amor Fati.

This repository owns facts about a reference economy. It does not own the
model runtime, accounting implementation, raw source files, notebooks, or
research-run outputs.

## Repository layout

```text
sources/     Source records: provider, URL, source hash, vintage, and terms.
recipes/     Baseline-scoped transformation decisions.
baselines/   Immutable, data-only components consumed by the model.
src/         Deterministic Scala extractors and artifact writers.
docs/        Lifecycle and data-governance rules.
```

The intended flow is:

```text
source record -> recipe + deterministic transformation -> validated component -> baseline record
```

The first workstream is the Polish `PL-2025-Q4-v1` reference economy. It
represents the observed Q4 2025 closing boundary and is the opening state for
simulation from January 2026. No empirical component is committed or
validated yet; its planned assembly status is recorded in
[`baselines/PL/PL-2025-Q4-v1/baseline.yaml`](baselines/PL/PL-2025-Q4-v1/baseline.yaml).
The [population source review](docs/baselines/PL-2025-Q4-v1/population-source-review.md)
records which public inputs are already pinned, what each can support, and the
remaining empirical boundaries before compiler work begins.

Read [the baseline lifecycle](docs/baseline-lifecycle.md) and [data governance](docs/data-governance.md)
before adding a country, source, or artifact.

## Boundaries

- `amor-fati` owns generic contracts, loaders, the runtime, and validation.
- This repository owns country evidence, classifications, explicit bridges,
  and data-only baseline components.
- `amor-fati-research` owns notebooks, scenarios, experiments, and results.
- Raw, restricted, licensed, or personally identifiable data must not enter
  this public repository.

An artifact correction produces a new baseline version. A scenario is not a
baseline and must reference a fixed baseline version rather than modifying it.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before proposing a source, recipe, or
artifact. External contributions require the [Amor Fati Contributor License
Agreement](CLA.md).
