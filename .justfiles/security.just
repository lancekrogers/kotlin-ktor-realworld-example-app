# Security recipes — the audit's findings as repeatable checks
#
# Turns workflow/explore/ktor-realworld-repo-review/security/ into something CI
# can run, so a fixed finding cannot quietly regress.
root := justfile_directory()
container_name := "ktor-realworld-dev"
host_port := env_var_or_default("HOST_PORT", "18080")

# Show available security commands
[private]
default:
    @just --list --unsorted --justfile {{source_file()}}

# Run every static check (no containers required)
[no-cd]
audit:
    #!/usr/bin/env bash
    set -euo pipefail
    just security supply-chain
    just security secrets
    just security wrapper
    echo "static audit: PASSED"

# Assert the build resolves only from trusted, pinned sources.
# jcenter() is decommissioned, mavenLocal() shadows Central, and a dynamic
# version on a plugin that runs at configuration time is a supply-chain hole.
[doc('Assert the build resolves only from trusted, pinned sources')]
[no-cd]
supply-chain:
    #!/usr/bin/env bash
    set -euo pipefail
    fail=0
    cd {{root}}
    if grep -n 'jcenter()' build.gradle 2>/dev/null; then
        echo "  FAIL  jcenter() is decommissioned; remove it"; fail=1
    else echo "  ok    no jcenter()"; fi

    if grep -n 'mavenLocal()' build.gradle 2>/dev/null; then
        echo "  FAIL  mavenLocal() shadows Maven Central"; fail=1
    else echo "  ok    no mavenLocal()"; fi

    if grep -nE '^[a-z_]+_version=.*\+\s*$' gradle.properties 2>/dev/null; then
        echo "  FAIL  dynamic version pin found"; fail=1
    else echo "  ok    all versions pinned"; fi

    if grep -q 'distributionSha256Sum=' gradle/wrapper/gradle-wrapper.properties; then
        echo "  ok    Gradle distribution checksum pinned"
    else
        echo "  FAIL  gradle-wrapper.properties has no distributionSha256Sum"; fail=1
    fi
    exit $fail

# Look for credentials committed to the tree.
# The original code shipped its JWT signing key as a string literal, which made
# every account forgeable; this guards against that class of regression.
[doc('Look for credentials committed to the tree')]
[no-cd]
secrets:
    #!/usr/bin/env bash
    set -euo pipefail
    cd {{root}}
    hits=$(grep -rnE 'HMAC256\("|password\s*=\s*"[^"$][^"]{7,}"|secret\s*=\s*"[^"$][^"]{7,}"|api[_-]?key\s*=\s*"' \
        src/main --include='*.kt' 2>/dev/null || true)
    if [ -n "$hits" ]; then
        echo "  FAIL  possible hardcoded credential:"; echo "$hits" | sed 's/^/        /'; exit 1
    fi
    echo "  ok    no hardcoded credentials in src/main"

# Inspect the Gradle wrapper JAR: it must contain only stock Gradle classes and
# reference no download host of its own. A swapped wrapper JAR is the classic
# way to get code execution into a build, so this is worth asserting.
[doc('Verify the Gradle wrapper JAR is stock and hardcodes no URLs')]
[no-cd]
wrapper:
    #!/usr/bin/env bash
    set -euo pipefail
    cd {{root}}
    jar=gradle/wrapper/gradle-wrapper.jar

    # Stock wrappers ship only org/gradle/** classes plus META-INF metadata.
    # Anything else -- a script, a nested archive, another vendor's package -- is suspect.
    unexpected=$(unzip -Z1 "$jar" | grep -vE '^(org/gradle/.*\.class|META-INF/(MANIFEST\.MF|LICENSE)|.*/)$' || true)
    if [ -n "$unexpected" ]; then
        echo "  FAIL  unexpected entries in wrapper jar:"; echo "$unexpected" | sed 's/^/        /'; exit 1
    fi
    echo "  ok    wrapper jar contains only stock Gradle classes"

    # The distribution URL must come from gradle-wrapper.properties, not be baked
    # into the jar. gradle.org/release-checksums is quoted in Gradle's own
    # checksum-mismatch message and is the one expected literal.
    urls=$(unzip -p "$jar" 'org/gradle/*' 2>/dev/null \
        | LC_ALL=C tr -c '[:print:]' '\n' \
        | grep -aoE 'https?://[^ ]{6,}' | sort -u \
        | grep -v '^https://gradle.org/release-checksums/$' || true)
    if [ -n "$urls" ]; then
        echo "  FAIL  wrapper jar references unexpected URLs:"; echo "$urls" | sed 's/^/        /'; exit 1
    fi
    echo "  ok    wrapper jar hardcodes no download URLs"
    echo "  ok    sha256 $(shasum -a 256 "$jar" | cut -d' ' -f1)"

# Checks that need the app running: listening surface and token forgery.
# Expects a container already up (just docker up).
[doc('Runtime checks: listening surface and old-key token forgery')]
[no-cd]
runtime:
    #!/usr/bin/env bash
    set -euo pipefail
    fail=0
    if ! docker ps --format '{{ '{{' }}.Names{{ '}}' }}' | grep -q '^{{container_name}}$'; then
        echo "  container not running; starting it"
        just docker up
        trap 'just docker down' EXIT
    fi

    ports=$(docker exec {{container_name}} sh -c 'cat /proc/net/tcp /proc/net/tcp6 2>/dev/null' \
        | python3 -c "
    import sys
    p = set()
    for line in sys.stdin:
        f = line.split()
        if len(f) > 3 and f[3] == '0A':
            try:
                p.add(int(f[1].split(':')[1], 16))
            except ValueError:
                pass
    print(','.join(str(x) for x in sorted(p)))
    ")
    if [ "$ports" = "8080" ]; then
        echo "  ok    only port 8080 listening"
    else
        echo "  FAIL  unexpected listening ports: $ports (H2 5435 was removed)"; fail=1
    fi

    forged=$(python3 - <<'PY'
    import hmac, hashlib, base64, json, time
    b = lambda x: base64.urlsafe_b64encode(x).rstrip(b'=').decode()
    h = b(json.dumps({"typ": "JWT", "alg": "HS256"}, separators=(',', ':')).encode())
    p = b(json.dumps({"sub": "Authentication", "aud": "ktor-audience", "iss": "ktor-realworld",
                      "exp": int(time.time()) + 3600, "iat": int(time.time()),
                      "email": "jake@jake.jake"}, separators=(',', ':')).encode())
    sig = b(hmac.new(b"something-very-secret-here", f"{h}.{p}".encode(), hashlib.sha256).digest())
    print(f"{h}.{p}.{sig}")
    PY
    )
    code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:{{host_port}}/user" \
        -H "Authorization: Token $forged")
    if [ "$code" = "401" ]; then
        echo "  ok    token forged with the old committed key rejected (401)"
    else
        echo "  FAIL  forged token accepted (HTTP $code) — signing key may be back in the source"; fail=1
    fi
    exit $fail

# Everything: static checks plus the runtime checks
[no-cd]
all:
    #!/usr/bin/env bash
    set -euo pipefail
    just security audit
    just security runtime
    echo "security: PASSED"
