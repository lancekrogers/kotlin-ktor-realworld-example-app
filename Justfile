#!/usr/bin/env just --justfile
# kotlin-ktor-realworld — build and development tasks
#
# Every recipe runs inside a container. This repository arrived from a third
# party, so nothing here executes an unfamiliar toolchain against the host.
# Docker is the only prerequisite; no local JDK or Gradle is needed.

set dotenv-load := true

# Configuration
image_name := "ktor-realworld"
container_name := "ktor-realworld-dev"
host_port := env_var_or_default("HOST_PORT", "18080")

# Modules
[doc('Compile, assemble, and clean (containerized Gradle)')]
mod build '.justfiles/build.just'

[doc('Testing, including which tests are actually enabled')]
mod test '.justfiles/test.just'

[doc('Image lifecycle, compose, logs, and smoke checks')]
mod docker '.justfiles/docker.just'

[doc('Repeatable checks derived from the security audit')]
mod security '.justfiles/security.just'

[private]
default:
    #!/usr/bin/env bash
    echo "kotlin-ktor-realworld — RealWorld API on Kotlin + Ktor"
    echo "All recipes run in Docker; no local JDK required."
    echo ""
    just --list --unsorted

# Builds on both required JDKs, runs the suite, and re-checks the audit findings.
# Run this before pushing or submitting.
gate:
    #!/usr/bin/env bash
    set -euo pipefail
    echo "=== gate: supply-chain checks ==="
    just security supply-chain
    echo "=== gate: secret scan ==="
    just security secrets
    echo "=== gate: build on JDK 17 ==="
    just build jdk 17
    echo "=== gate: build on JDK 21 ==="
    just build jdk 21
    echo "=== gate: tests ==="
    just test all
    echo "=== gate: enabled-test census ==="
    just test census
    echo "=== gate: runtime checks ==="
    just security runtime
    echo "=== gate: PASSED ==="

# Remove build output, containers, and the Gradle cache volume
clean:
    #!/usr/bin/env bash
    set -euo pipefail
    just build clean
    just docker down
    docker volume rm -f ktor-gradle-cache >/dev/null 2>&1 || true
    echo "cleaned build output, containers, and cache volume"

# Show the resolved dependency tree
deps:
    @just build gradle "dependencies --configuration runtimeClasspath"

# Print the local URL the app serves on
url:
    @echo "http://localhost:{{host_port}}"
