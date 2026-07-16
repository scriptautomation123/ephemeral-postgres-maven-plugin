#!/usr/bin/env bash
set -euo pipefail

mvn -Dopenshift.db.skip=false verify

# or skip provisioning/cleanup
# mvn -Dopenshift.db.skip=true verify