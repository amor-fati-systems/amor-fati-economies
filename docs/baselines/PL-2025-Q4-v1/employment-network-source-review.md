# PL-2025-Q4-v1 Employment-Network Source Review

Status: reviewed, not yet an employment-network component

Date: 2026-07-24

## Decision

`PL-2025-Q4-v1` may ultimately contain a **synthetic** employment network. It
cannot recover actual Polish employment contracts, actual worker-employer
pairs, or an observed firm-level headcount from the public sources reviewed
here.

The first defensible relation has three separate meanings:

```text
synthetic employed resident
  -> synthetic workplace cell (work voivodeship x PKD 2007 section)
  -> synthetic enterprise in that cell
```

The first arrow assigns a modelled primary work location; the second allocates
that job to a modelled production unit. Neither arrow is an observation of a
real person's employer. The resulting edge must always be labelled a
`synthetic assignment` in its manifest and research results.

This is sufficient for an agent-based economy: firms need workers, persons need
a workplace geography and a primary employer, and the model needs aggregate
closure. It is not a claim that public data disclose the underlying bilateral
relationships.

## Pinned Evidence

| Need | Pinned source | Evidence role | What it supports | What it does not support |
| --- | --- | --- | --- | --- |
| Synthetic enterprise structure | [GUS REGON, 2025-12-31](../../../sources/PL/GUS/REGON/2025-12-31/source.yaml) | Structural prior | Registered-entity composition by the publication's geography, PKD 2007, and expected-worker bands. | Realised jobs, FTE, capacity, or a worker's employer. |
| Residence-to-work pattern | [GUS NSP 2021 commuting matrix](../../../sources/PL/GUS/nsp-2021/commuting-matrix/source.yaml) | Structural prior | Historical gmina origin-to-work-location flows within the source's published scope. | A Q4 2025 flow, a complete employed-person universe, or firm identity. |
| Employed-resident margin | [GUS BAEL Q4 2025](../../../sources/PL/GUS/bael/2025-Q4/source.yaml) | Hard-control candidate | National employed-person margins in BAEL's declared dwelling-resident universe. | A work location, job count, FTE, establishment, or employer. |

The raw source files are integrity-pinned by their source records and remain
outside Git.

Publication pages: [REGON Q4 2025](https://stat.gov.pl/obszary-tematyczne/podmioty-gospodarcze-wyniki-finansowe/zmiany-strukturalne-grup-podmiotow/kwartalna-informacja-o-podmiotach-gospodarki-narodowej-w-rejestrze-regon-rok-2025%2C7%2C15.html) and [NSP 2021 commuting matrix](https://stat.gov.pl/spisy-powszechne/nsp-2021/nsp-2021-wyniki-ostateczne/macierz-przeplywow-ludnosci-zwiazanych-z-zatrudnieniem-nsp-2021%2C9%2C2.html).

## What Can Be Built Without Guessing

The data establish the following narrow, auditable proposition:

1. A Q4 2025 population compiler can identify a synthetic number of employed
   residents within the declared BAEL universe.
2. A 2021 commuting matrix can supply a historical structural prior for the
   distribution of eligible residence-to-work-location flows.
3. Q4 2025 REGON can supply the structural distribution used to create
   synthetic enterprises.

They do **not** establish how an individual worker maps to a particular real
enterprise. Consequently, the compilation task is an aggregate-constrained
matching problem, not record linkage and not an attempt to reconstruct private
employment contracts.

The initial design deliberately does not require a separate `Establishment`
entity. A workplace cell is a work-voivodeship and PKD 2007-section aggregate;
synthetic firms created in that cell act as modelled production units. This
means that a first artifact does not represent multi-establishment legal
enterprises. Introducing that extra entity is justified only when a pinned
source and a model requirement need it.

## Required Empirical Bridge

The pinned evidence is not yet enough to create a Q4 2025 network. One current
workplace-side source must first be qualified and pinned.

The BDL catalogue identifies relevant candidates, but they are not source
records and must not yet be used by a recipe:

| Candidate BDL subject | Published concept | Possible role | Qualification still required |
| --- | --- | --- | --- |
| `P2834` | Workers by actual place of work, PKD section/group, and sex | Workplace-side demand margin | Exact variable, period, territorial resolution, raw response, statistical universe, and digest. |
| `P4458` | Workers by place of residence and PKD section/division, monthly | Residence-side validation or bridge | Exact variable and whether its universe can bridge to BAEL. |
| `P4457` | Workers by PKD section, sex, and main-work seat, monthly | Diagnostic only unless its seat concept is bridged to actual workplace | Exact variable, geography, and an explicit seat-to-workplace limitation. |

`P4457` is not interchangeable with an actual work-location series. Likewise,
the commuting matrix cannot supply the missing current workplace total. The
recipe must preserve all three concepts rather than force them to agree.

## Constraint Classification

The prospective compiler has four types of input:

| Input | Classification | Rule |
| --- | --- | --- |
| BAEL employed residents | Current person-side control candidate | Reconcile only within its published universe and tolerance. |
| Qualified BDL actual-workplace series | Current workplace-side control candidate | Reconcile only after a documented universe and unit bridge. |
| NSP 2021 commuting matrix | Historical structural prior | Fit and report separately; never call it a Q4 2025 control. |
| REGON expected-worker bands | Enterprise structural prior | Use to distribute synthetic firms; never treat a band as realised capacity. |

Any residual between BAEL people, a workplace-side administrative series, and
REGON must be reported by source concept. It must not be hidden by changing
person weights, inventing jobs, or treating declared expected-worker bands as
headcounts.

## Acceptance Gate Before Implementation

No matching code, runtime entity, or population-control table is authorised by
this review alone. The employment-network component may become `source_pinned`
only after its recipe identifies and qualifies all of the following:

1. A digest-pinned, Q4 2025-or-earlier workplace-side BDL (or equivalent)
   extraction with exact variable identifiers, units, geography, and universe.
2. A declared bridge between the workplace series and the BAEL employed-person
   universe, including its residual category and reconciliation tolerance.
3. The precise published scope of the NSP commuting matrix, including treatment
   of same-gmina and out-of-scope employment.
4. A declared geographic resolution for the first target. The initial target
   may aggregate the historical gmina matrix to voivodeships; it must not claim
   an unobserved finer Q4 flow.
5. A deterministic assignment procedure and evidence report that distinguish
   current controls, historical-prior fit, and synthetic edge counts.
6. A statement that `worker -> firm` is a synthetic model edge and that the
   first artifact represents each synthetic firm as one production unit, not a
   complete legal-enterprise establishment network.

Until then, `PL-2025-Q4-v1` has a feasible research direction but no
employment-network component. Its population component and its enterprise
component remain independently qualified.

## Non-Goals

This review does not choose a matching algorithm, acquire BDL extracts, write
Scala, change the model ontology, or produce a compiled baseline. It only
states the empirical boundary that a later implementation must respect.
