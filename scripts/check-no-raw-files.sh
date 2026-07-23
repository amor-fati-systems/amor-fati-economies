#!/usr/bin/env bash
set -euo pipefail

tracked_raw="$(git ls-files -- raw/)"

if [[ -n "$tracked_raw" ]]; then
  printf '%s\n' "Raw source data must not be tracked under raw/." >&2
  printf '%s\n' "Move the file to approved external storage and retain only its provenance record." >&2
  printf '%s\n' "Tracked raw paths:" >&2
  printf '%s\n' "$tracked_raw" >&2
  exit 1
fi
