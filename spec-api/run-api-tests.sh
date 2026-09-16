#!/usr/bin/env bash
set -euo pipefail

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

# No default: a missing APIURL must never silently target a remote host.
: "${APIURL:?set APIURL to the API base, e.g. http://localhost:8080}"
USERNAME=${USERNAME:-u$(date +%s)}
EMAIL=${EMAIL:-$USERNAME@mail.com}
PASSWORD=${PASSWORD:-password}
REPORT=${REPORT:-newman-report.json}
NEWMAN_VERSION=6.2.2

npx --yes "newman@${NEWMAN_VERSION}" run "$SCRIPTDIR/Conduit.postman_collection.json" \
  --delay-request 500 \
  --reporters cli,json \
  --reporter-json-export "$REPORT" \
  --global-var "APIURL=$APIURL" \
  --global-var "USERNAME=$USERNAME" \
  --global-var "EMAIL=$EMAIL" \
  --global-var "PASSWORD=$PASSWORD"
