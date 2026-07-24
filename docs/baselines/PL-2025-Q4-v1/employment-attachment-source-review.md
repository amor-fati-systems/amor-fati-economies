# PL-2025-Q4-v1 Employment-Attachment Source Review

Status: reviewed, not yet an employment-attachment component

Date: 2026-07-24

## Decision

`PL-2025-Q4-v1` may contain **synthetic main-employment attachments**. It
cannot recover actual Polish employment contracts, actual worker-employer
pairs, actual work locations, or an observed firm-level headcount from the
public sources reviewed here.

The first defensible `v1` relation is:

```text
synthetic employed resident
  -> synthetic workplace
       -> synthetic enterprise
```

Each synthetic workplace has a PKD 2007 section and a modelled location. In
`v1`, the modelled location is initialised from the published seat of the
entity of the main job. This is a provenance rule, not a claim that the seat is
the observed actual workplace. Each `v1` workplace belongs to one synthetic
enterprise; each synthetic enterprise has one workplace.

Neither edge is an observation of a real person's employer. The attachment
must be labelled a `synthetic assignment` in its manifest and research
results. This is sufficient for the first agent-based economy: firms need
workers, persons need a primary workplace, and the model needs aggregate
closure. It is not a claim that public data disclose the underlying bilateral
relationships.

## Pinned Evidence

| Need | Pinned source | Evidence role | What it supports | What it does not support |
| --- | --- | --- | --- | --- |
| Synthetic workplace allocation | [GUS employment, 2025-12-31](../../../sources/PL/GUS/employment/2025-12-31/source.yaml) | Current structural prior | Published weights by PKD 2007, sex, and the seat of the entity of the main job. | An observed actual workplace, employer identity, contract, FTE, or equality with BAEL without a bridge. |
| Synthetic enterprise structure | [GUS REGON, 2025-12-31](../../../sources/PL/GUS/REGON/2025-12-31/source.yaml) | Structural prior | Registered-entity composition by the publication's geography, PKD 2007, and expected-worker bands. | Realised jobs, FTE, capacity, or a worker's employer. |
| Historical commuting evidence | [GUS NSP 2021 commuting matrix](../../../sources/PL/GUS/nsp-2021/commuting-matrix/source.yaml) | Later validation-only prior | Historical cross-gmina commuting of workers covered by its published scope. | A Q4 2025 flow, a complete employed-person universe, firm identity, or a `v1` constraint. |
| Employed-resident margin | [GUS BAEL Q4 2025](../../../sources/PL/GUS/bael/2025-Q4/source.yaml) | Hard-control candidate | National employed-person margins in BAEL's declared dwelling-resident universe. | A work location, job count, FTE, establishment, or employer. |

The raw source files are integrity-pinned by their source records and remain
outside Git.

Publication pages: [employment in December 2025](https://stat.gov.pl/obszary-tematyczne/rynek-pracy/pracujacy-zatrudnieni-wynagrodzenia-koszty-pracy/pracujacy-w-gospodarce-narodowej-w-polsce-w-grudniu-2025-r-%2C27%2C37.html), [REGON Q4 2025](https://stat.gov.pl/obszary-tematyczne/podmioty-gospodarcze-wyniki-finansowe/zmiany-strukturalne-grup-podmiotow/kwartalna-informacja-o-podmiotach-gospodarki-narodowej-w-rejestrze-regon-rok-2025%2C7%2C15.html), and [NSP 2021 commuting matrix](https://stat.gov.pl/spisy-powszechne/nsp-2021/nsp-2021-wyniki-ostateczne/macierz-przeplywow-ludnosci-zwiazanych-z-zatrudnieniem-nsp-2021%2C9%2C2.html).

## What Can Be Built Without Guessing

The data establish the following narrow, auditable proposition:

1. A Q4 2025 population compiler can identify a synthetic number of employed
   residents within the declared BAEL universe.
2. A current GUS employment extract can supply published PKD and entity-seat
   weights for allocating those residents to synthetic main jobs.
3. Q4 2025 REGON can supply the structural distribution used to create
   synthetic enterprises.

They do **not** establish how an individual worker maps to a particular real
enterprise. Consequently, the compilation task is an aggregate-constrained
matching problem, not record linkage and not an attempt to reconstruct private
employment contracts.

The initial design deliberately does not require a separate `Establishment`
entity. `Workplace` is a model entity: it is the locus to which the person is
assigned and where the synthetic enterprise produces. In `v1`, each synthetic
enterprise has exactly one workplace. Its location is initialised from the
published entity seat and is explicitly marked as a proxy in baseline
provenance. This means that a first artifact does not represent observed actual
workplaces or multi-establishment legal enterprises. Introducing those
capabilities is justified only when a pinned source and a model requirement
need them.

## Required Bridge

The pinned evidence defines the `v1` target, but does not yet compile it. The
recipe must bridge the BAEL person universe and the GUS main-employment
universe without claiming that their totals are identical.

The BDL catalogue has also been reviewed for actual-workplace data:

| BDL subject | Published concept | Review result |
| --- | --- | --- |
| `P2834` | Workers by actual place of work, PKD section/group, and sex | Ends in 2021; unsuitable as a Q4 2025 `v1` control. |
| `P4458` | Workers by place of residence and PKD section/division, monthly | Possible diagnostic source; not required for the first attachment recipe. |
| `P4457` | Workers by PKD section, sex, and main-job entity seat, monthly | Its published concept is represented directly by the pinned December 2025 GUS extract. It is not an actual-workplace source. |

The NSP 2021 commuting matrix also cannot turn an entity-seat initialisation
into a current actual-workplace observation. It remains outside `v1` matching.

## Constraint Classification

The prospective compiler has four types of input:

| Input | Classification | Rule |
| --- | --- | --- |
| BAEL employed residents | Current person-side control candidate | Reconcile only within its published universe and tolerance. |
| GUS employment by entity seat | Current synthetic-workplace allocation prior | Use as declared allocation weights after its bridge to BAEL; do not call the seat an observed workplace. |
| NSP 2021 commuting matrix | Later validation-only prior | Do not use in `v1` matching or call it a Q4 2025 control. |
| REGON expected-worker bands | Enterprise structural prior | Use to distribute synthetic firms; never treat a band as realised capacity. |

Any residual between BAEL people, GUS main-employment counts, and REGON must be
reported by source concept. It must not be hidden by changing person weights,
inventing jobs, or treating declared expected-worker bands as headcounts.

## Acceptance Gate Before Implementation

No matching code, runtime entity, or population-control table is authorised by
this review alone. The employment-attachment component may become
`source_pinned` only after its recipe identifies and qualifies all of the
following:

1. A declared bridge between the GUS main-employment and BAEL employed-person
   universes, including residual categories and reconciliation tolerances.
2. A declared treatment for employees, self-employed persons, and helping
   family members.
3. A deterministic assignment procedure and evidence report that distinguish
   current person controls, current allocation-prior fit, and synthetic edge
   counts.
4. A statement that `Person -> Workplace -> Firm` is a synthetic model path,
   that every `v1` workplace location is initialised from an entity-seat proxy,
   and that each synthetic firm is one production unit rather than a complete
   legal-enterprise establishment network.

Until then, `PL-2025-Q4-v1` has a defined `v1` target but no compiled
employment-attachment component. Its population component and enterprise
component remain independently qualified.

## Non-Goals

This review does not choose a matching algorithm, acquire BDL extracts, write
Scala, change the model ontology, or produce a compiled baseline. It only
states the empirical boundary that a later implementation must respect.
