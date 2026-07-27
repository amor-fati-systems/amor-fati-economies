# PL-2025-Q4-v1 population controls

This directory is the first reproducible population-control artifact assembled
from the pinned public extracts. `persons`, household tables, and the national
BAEL table are normalized inputs. `regional-labour.tsv` and `employment.tsv`
are explicitly modelled bridges: regional allocation uses private-person
margins, while employment is a diagonal residence/workplace allocation using
PKD section `A`. They are not observations of commuting, establishments, or
employer links and must remain marked as modeled until independently validated.

The artifact is ready for loader verification, but is not declared
`runtime_validated` by this repository alone.
