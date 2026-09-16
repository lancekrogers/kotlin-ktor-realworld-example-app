# Docker recipes — image lifecycle, compose, and smoke checks
root := justfile_directory()
image_name := "ktor-realworld"
container_name := "ktor-realworld-dev"
host_port := env_var_or_default("HOST_PORT", "18080")

# Show available docker commands
[private]
default:
    @just --list --unsorted --justfile {{source_file()}}

# Build the runtime image
[no-cd]
image tag="latest":
    @docker build -t {{image_name}}:{{tag}} {{root}}

# Start the app detached and wait until it actually answers
[no-cd]
up tag="latest":
    #!/usr/bin/env bash
    set -euo pipefail
    just docker image {{tag}}
    docker rm -f {{container_name}} >/dev/null 2>&1 || true
    docker run -d --name {{container_name}} -p {{host_port}}:8080 \
        -e JWT_SECRET="${JWT_SECRET:-}" {{image_name}}:{{tag}} >/dev/null
    for _ in $(seq 1 40); do
        if docker logs {{container_name}} 2>&1 | grep -q 'Responding at'; then
            echo "up at http://localhost:{{host_port}}"
            exit 0
        fi
        sleep 1
    done
    echo "app did not become ready; last output:" >&2
    docker logs {{container_name}} 2>&1 | tail -20 >&2
    exit 1

# Stop and remove the app container and any compose services
[no-cd]
down:
    #!/usr/bin/env bash
    set -euo pipefail
    docker rm -f {{container_name}} >/dev/null 2>&1 || true
    docker compose -f {{root}}/compose.yaml down --remove-orphans >/dev/null 2>&1 || true
    echo "stopped"

# Follow container logs
[no-cd]
logs:
    @docker logs -f {{container_name}}

# Shell into a throwaway Gradle container with the repo mounted
[no-cd]
shell:
    @docker run --rm -it -v {{root}}:/app -w /app \
        -v ktor-gradle-cache:/home/gradle/.gradle gradle:8.14-jdk17 bash

# Shell into the running app container
[no-cd]
exec:
    @docker exec -it {{container_name}} sh

# Show container status
[no-cd]
ps:
    @docker ps --filter "name={{container_name}}" --format 'table {{ '{{' }}.Names{{ '}}' }}\t{{ '{{' }}.Status{{ '}}' }}\t{{ '{{' }}.Ports{{ '}}' }}'

# Exercise the auth flow against a running container and assert the responses.
# Covers the behaviour the security work changed: registration succeeds, no
# password material comes back, and failures carry correct status codes.
[doc('Exercise the auth flow against a running container and assert responses')]
[no-cd]
smoke:
    #!/usr/bin/env bash
    set -euo pipefail
    B="http://localhost:{{host_port}}"
    fail=0
    check() { # label expected actual
        if [ "$2" = "$3" ]; then echo "  ok    $1 ($3)"; else echo "  FAIL  $1 (expected $2, got $3)"; fail=1; fi
    }
    email="smoke_$(date +%s)@valid_email.com"
    body="{\"user\":{\"email\":\"$email\",\"username\":\"smoke_$(date +%s)\",\"password\":\"smoke-password\"}}"

    code=$(curl -s -o /tmp/.smoke_reg -w '%{http_code}' -X POST "$B/users" \
        -H 'Content-Type: application/json' -d "$body")
    check "register" 200 "$code"

    if grep -qi password /tmp/.smoke_reg; then
        echo "  FAIL  register leaked password material"; fail=1
    else
        echo "  ok    register returned no password material"
    fi

    code=$(curl -s -o /tmp/.smoke_login -w '%{http_code}' -X POST "$B/users/login" \
        -H 'Content-Type: application/json' \
        -d "{\"user\":{\"email\":\"$email\",\"password\":\"smoke-password\"}}")
    check "login" 200 "$code"

    token=$(python3 -c 'import json;print(json.load(open("/tmp/.smoke_login"))["user"]["token"])')
    code=$(curl -s -o /dev/null -w '%{http_code}' "$B/user" -H "Authorization: Token $token")
    check "get current user" 200 "$code"

    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/users/login" \
        -H 'Content-Type: application/json' \
        -d "{\"user\":{\"email\":\"$email\",\"password\":\"wrong\"}}")
    check "wrong password -> 401" 401 "$code"

    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$B/users" \
        -H 'Content-Type: application/json' \
        -d '{"user":{"email":"x@y.com","username":"x","password":""}}')
    check "blank password -> 422" 422 "$code"

    code=$(curl -s -o /dev/null -w '%{http_code}' "$B/user")
    check "no token -> 401" 401 "$code"

    rm -f /tmp/.smoke_reg /tmp/.smoke_login
    [ "$fail" -eq 0 ] && echo "smoke: PASSED" || { echo "smoke: FAILED" >&2; exit 1; }

# Bring the app up, smoke it, and tear it back down
[no-cd]
verify:
    #!/usr/bin/env bash
    set -euo pipefail
    just docker up
    trap 'just docker down' EXIT
    just docker smoke

# Run the RealWorld Postman collection against the running app container
[no-cd]
spec:
    #!/usr/bin/env bash
    set -euo pipefail
    if ! docker inspect {{container_name}} >/dev/null 2>&1; then
        echo "start the app first: just docker up" >&2; exit 1
    fi
    docker run --rm --network container:{{container_name}} \
        -v {{root}}/spec-api:/spec -w /spec \
        -e APIURL=http://localhost:8080 -e REPORT=/spec/newman-report.json \
        node:22-bookworm-slim@sha256:83f487e0a63425e5b4d146fb5e5be574bcbe1b7b843d3ebafdd95eaf7767a7e5 ./run-api-tests.sh
