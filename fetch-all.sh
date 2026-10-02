#!/bin/sh

# Regenerates every checked-in resource the fetch-all-*.py scripts own, in one go.
# Each script resolves its own output path, so this only has to invoke them; it runs
# them from the repo root regardless of the caller's working directory.
#
# set -e makes any script's non-zero exit abort the run, which the pre-push hook relies
# on: fetch-all-kotlin-modules.py exits 2 when a module still needs a human-written
# mapping, and fetch-all-ide-kotlin-versions.py exits 1 without an authenticated gh.

set -e

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

python3 "$ROOT/plugins/fetch-all-kotlin-versions.py"
python3 "$ROOT/plugins/fetch-all-ide-versions.py"
python3 "$ROOT/plugins/fetch-all-kotlin-modules.py"
python3 "$ROOT/version-resolution/fetch-all-ide-kotlin-versions.py"
