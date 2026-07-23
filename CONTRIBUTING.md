# Contributing to Amor Fati Economies

`amor-fati-economies` publishes versioned evidence about reference economies.
Its public contract is not only the shape of a baseline artifact, but also the
provenance, transformation, and reconciliation evidence that make the artifact
reviewable.

## Before You Start

Open an issue before substantial work on a country, source family,
classification, transformation recipe, baseline component, or shared data
contract. Small documentation corrections and narrowly scoped implementation
fixes may go directly to a pull request.

Keep a pull request focused on one empirical or transformation boundary. Do not
combine a source revision, a new baseline version, and unrelated refactoring.

## Data and Artifact Boundaries

Do not commit raw source files, credentials, personal data, restricted data, or
proprietary material. `raw/` is a local-only boundary, and CI rejects tracked
files beneath it. A public download is not by itself permission to redistribute
its bytes in this repository.

For every external input, record its provider, direct location, identity or
table, observation period, release and access dates, acquired-byte digest, and
review status in `sources/`. A deterministic recipe must declare its input,
classification choices, units, transformations, residual treatment, and
reconciliation checks. Do not commit derived controls whose provenance cannot
be reproduced from those records.

Validated baseline versions are historical records. Correct a source, recipe,
or accepted artifact by publishing a new baseline version rather than silently
rewriting the prior one.

## Development and Verification

The Nix shell is the supported development environment:

```bash
nix develop
sbt test scalafmtCheckAll
scripts/check-no-raw-files.sh
```

Run the focused inspector or transformation tests affected by your change. A
pull request that changes a source record, recipe, or artifact must state the
source used, the commands run, and the reconciliation or contract evidence
produced. Do not report an artifact as validated merely because it parses.

## Contributor License Agreement

This repository is licensed under `AGPL-3.0-only`. External contributions also
require acceptance of the [Amor Fati Contributor License Agreement](CLA.md).
Contributors retain ownership of their work while granting BoomBustGroup the
rights stated in that agreement, including the right to offer a contribution
under additional terms.

Accept the agreement through
[CLA Assistant](https://cla-assistant.io/amor-fati-systems/amor-fati-economies).
The pull request's `license/cla` check must pass before an external
contribution is merged. If a contribution is owned by an employer, university,
or other legal entity, the person accepting the agreement must be authorized to
bind it. Identify all third-party material and its license in the pull request.

## Pull Requests

A reviewable pull request:

- explains the empirical or technical boundary it changes;
- identifies affected source records, recipes, artifacts, and baseline versions;
- lists the verification commands and evidence;
- leaves raw inputs and unrelated generated files out of Git;
- passes formatting, tests, the raw-source boundary check, and the CLA check.
