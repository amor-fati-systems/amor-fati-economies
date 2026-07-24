# PL-2025-Q4-v1 Population Source Review

Status: reviewed, not yet a population-control component

Date: 2026-07-24

## Decision

`PL-2025-Q4-v1` can support a first **demographic-household-labour**
population. It cannot honestly claim a fully observed joint population of
persons, households, workplaces, and firms.

The first compiler must therefore use three distinct kinds of evidence:

1. Q4 2025 controls that it reproduces within their declared tolerances.
2. NSP 2021 structural priors that determine how to create a plausible joint
   household microstructure within those controls.
3. Explicitly unresolved relations that are neither inferred nor presented as
   empirical facts.

This is a scope decision, not an implementation waiver. The component remains
`planned` until the recipe, transformations, and resulting controls have been
reviewed and accepted.

## Pinned Evidence

| Need | Pinned source | Evidence role | What it supports | What it does not support |
| --- | --- | --- | --- | --- |
| Person totals | [GUS population balance](../../../sources/PL/GUS/population/2025-12-31/source.yaml) | Hard-control candidate | Voivodeship, sex, and age totals on 2025-12-31. | Residence type, households, or labour status. |
| Labour status | [GUS BAEL Q4 2025](../../../sources/PL/GUS/bael/2025-Q4/source.yaml) | Hard-control candidate | National margins in the release's dwelling-resident universe and published age bands. | Complete voivodeship-by-sex-by-age status cells, workplace geography, or employers. |
| Household size | [NSP 2021 household size](../../../sources/PL/GUS/nsp-2021/household-size/source.yaml) | Structural prior | Household-size distribution after aggregation to voivodeships. | A Q4 2025 household count. |
| Household composition | [NSP 2021 household composition](../../../sources/PL/GUS/nsp-2021/household-composition/source.yaml) | Structural prior | Family-composition distribution for private households. | Member role by age or a Q4 2025 observation. |
| Family role | [NSP 2021 family type](../../../sources/PL/GUS/nsp-2021/family-type/source.yaml) | Structural prior | Couple and lone-parent allocation inside generated household structures. | Household totals, including multi-family households. |
| Children in families | [NSP 2021 family children](../../../sources/PL/GUS/nsp-2021/family-children/source.yaml) | Structural prior | Conditional allocation of children. | A current person-level age distribution. |

The raw files are identified by the source records and are deliberately absent
from Git.

Publication pages: [population balance](https://stat.gov.pl/obszary-tematyczne/ludnosc/ludnosc/ludnosc-stan-i-struktura-oraz-ruch-naturalny-w-przekroju-terytorialnym-w-2025-r-stan-w-dniu-31-grudnia%2C6%2C40.html), [BAEL Q4 2025](https://stat.gov.pl/obszary-tematyczne/rynek-pracy/pracujacy-bezrobotni-bierni-zawodowo-wg-bael/aktywnosc-ekonomiczna-ludnosci-polski-4-kwartal-2025-r-%2C4%2C61.html), [NSP 2021 household and family tables](https://stat.gov.pl/spisy-powszechne/nsp-2021/nsp-2021-wyniki-ostateczne/gospodarstwa-domowe-oraz-rodziny-wedlug-rejonow-statystycznych-i-obwodow-spisowych%2C13%2C1.html), and [BDL collective-accommodation series](https://bdl.stat.gov.pl/bdl/dane/podgrup/wymiary/31/640/4360).

## Statistical Universes

The sources do not describe one identical population.

```text
GUS population balance, 2025-12-31
  -> total population by territory, sex, and age

BAEL Q4 2025
  -> residents in dwellings in the published BAEL age universe
  -> excludes a direct labour observation for collective accommodation

NSP 2021 household and family tables
  -> private-household and family structure at the Census reference date
```

The compiled artifact must retain these source concepts in its manifest. In
particular, it must not relabel the population-balance source as `usual
residents` merely because BAEL uses a residence-based dwelling universe.

## Feasible First Population

The following target is feasible once the control recipe is implemented:

```text
GUS Q4 2025 person totals
  + NSP 2021 household and family priors
  + BAEL Q4 2025 national labour-status margins
  = synthetic persons, private households, memberships, and labour status
```

The compiler can use the Census priors to construct joint household membership,
but those generated joint cells remain modelled structure. They are not Q4 2025
hard controls. It must report their fit to the prior separately from fit to
current controls.

The population balance supplies age at a finer level than the published BAEL
labour tables. BAEL margins therefore remain at their published age bands until
a separately pinned and reviewed bridge supports finer cells. Splitting an
observed BAEL band by population shares would be a model assumption, not a
measurement.

## Explicit Gaps

| Relation or component | Review result | Required treatment in the first target |
| --- | --- | --- |
| Private versus collective residents in Q4 2025 | No matching Q4 split has been pinned. BDL exposes NSP 2021 collective-accommodation data, but the exact API extraction and its scope still require a source record. | Keep collective residents as a separate required subcomponent. Do not assign all residents to private households. |
| Regional BAEL labour margin | The pinned public Q4 release does not establish the required full 16-voivodeship joint margin. | Do not make it a hard control. A later regional bridge must identify its distinct universe and tolerance. |
| Household member role by age | The reviewed public Census tables are marginal household and family tables, not a member-level microdata extract. | Generate this relation from the structural priors; record it as prior fit or acquire an approved microdata source later. |
| Residence-to-workplace commuting | No current origin-destination source is pinned. | Do not create a workplace-voivodeship hard control or call a historical commuting matrix a Q4 observation. |
| Person-to-firm employment | Neither BAEL nor the reviewed Census tables identify employers. | No person-to-firm edge in this target. Employment remains a person-level labour state or a later explicit matching model. |
| Firm stock and job capacity | No Q4 2025 enterprise component is pinned. | Keep enterprise compilation separate from the first population artifact. |

## Consequences For The Population Contract

The first accepted artifact may contain these source-backed controls:

1. `persons`: total residents by voivodeship, sex, and a declared aggregation
   of the population-balance age cells.
2. `demographic-labour`: national BAEL labour-status margins in exactly the
   publication's available age bands and statistical universe.
3. `households` and `household-membership`: generated output constrained by
   current person totals and Census structural priors, with separate evidence
   identifying the prior rather than presenting the cells as observed Q4 data.

It must not yet publish the following as hard empirical controls:

- a Q4 2025 private/collective population split;
- regional BAEL status totals;
- residence-to-workplace employment flows;
- person-to-firm assignments; or
- employment reconciled to firm job capacity.

This is sufficient to ground the population, household, and labour-state parts
of the model ontology. It is intentionally insufficient to claim a compiled
Polish labour network or a firm-linked economy.

## Gate Before Compiler Work

Before a candidate population artifact is generated, the recipe must:

1. pin the exact BDL or equivalent public extraction for collective
   accommodation and state how its 2021 structural vintage is bridged to the
   Q4 2025 total population;
2. declare the population-balance age aggregation and BAEL age-band mapping;
3. state the private-household total that the generated memberships reconcile
   to, without silently absorbing collective residents;
4. define source rounding and BAEL sampling tolerances separately from
   deterministic transform tolerances; and
5. declare person-level employment as unlinked to firms until a separate
   evidence-backed employment and enterprise component exists.

Only then can a recipe advance the component from `planned` to `source_pinned`.

## Non-Goals

This review does not select a household-synthesis algorithm, create source
transforms, generate a control bundle, or change the Amor Fati runtime. Those
are subsequent implementation decisions. Its purpose is to prevent those
steps from encoding unavailable empirical relations as facts.
