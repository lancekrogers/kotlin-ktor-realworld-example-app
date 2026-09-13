# Build recipes — containerized Gradle, no local JDK required
root := justfile_directory()
gradle_version := "8.14"
default_jdk := env_var_or_default("JDK", "17")
cache_volume := "ktor-gradle-cache"

# Show available build commands
[private]
default:
    @just --list --unsorted --justfile {{source_file()}}

# Run an arbitrary Gradle invocation in the container (e.g. just build gradle "tasks --all")
[no-cd]
gradle args:
    @docker run --rm -v {{root}}:/app -w /app -v {{cache_volume}}:/home/gradle/.gradle \
        gradle:{{gradle_version}}-jdk{{default_jdk}} gradle --no-daemon {{args}}

# Compile main sources only (fastest feedback)
[no-cd]
compile:
    @just build gradle compileKotlin

# Full clean build including tests
[no-cd]
all:
    @just build gradle "clean build"

# Build without running tests
[no-cd]
quick:
    @just build gradle "build -x test"

# Produce the runnable distribution under build/install/api
[no-cd]
assemble:
    @just build gradle installDist

# Build against a specific JDK — the exercise requires both 17 and 21
[no-cd]
jdk version:
    #!/usr/bin/env bash
    set -euo pipefail
    echo "--- building on JDK {{version}} ---"
    docker run --rm -v {{root}}:/app -w /app -v {{cache_volume}}:/home/gradle/.gradle \
        gradle:{{gradle_version}}-jdk{{version}} gradle --no-daemon clean build

# Build on every JDK the CI matrix must cover
[no-cd]
matrix:
    #!/usr/bin/env bash
    set -euo pipefail
    for v in 17 21; do just build jdk "$v"; done
    echo "matrix OK: JDK 17 and 21"

# Regenerate the Gradle wrapper and re-pin its distribution checksum.
# The checksum matters: without it the wrapper accepts whatever the URL returns.
[doc('Regenerate the Gradle wrapper and re-pin its distribution checksum')]
[no-cd]
wrapper version=gradle_version:
    #!/usr/bin/env bash
    set -euo pipefail
    docker run --rm -v {{root}}:/app -w /app -v {{cache_volume}}:/home/gradle/.gradle \
        gradle:{{version}}-jdk{{default_jdk}} \
        gradle wrapper --gradle-version {{version}} --distribution-type bin --no-daemon -q
    sum=$(docker run --rm curlimages/curl:latest -sfL \
        "https://services.gradle.org/distributions/gradle-{{version}}-bin.zip.sha256")
    props={{root}}/gradle/wrapper/gradle-wrapper.properties
    grep -v '^distributionSha256Sum=' "$props" > "$props.tmp" && mv "$props.tmp" "$props"
    printf 'distributionSha256Sum=%s\n' "$sum" >> "$props"
    echo "wrapper regenerated at {{version}}, checksum pinned"

# Remove build output
[no-cd]
clean:
    @just build gradle clean
