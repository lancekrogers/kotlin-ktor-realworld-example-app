# Test recipes
root := justfile_directory()
gradle_version := "8.14"
default_jdk := env_var_or_default("JDK", "17")
cache_volume := "ktor-gradle-cache"

# Show available test commands
[private]
default:
    @just --list --unsorted --justfile {{source_file()}}

# Run the whole suite
[no-cd]
all:
    @just build gradle test

# Run one test class (e.g. just test only UserControllerTest)
[no-cd]
only pattern:
    @just build gradle "test --tests '*{{pattern}}*'"

# Run the suite with full output
[no-cd]
verbose:
    @just build gradle "test --info"

# Run the suite on a specific JDK
[no-cd]
jdk version:
    @docker run --rm -v {{root}}:/app -w /app -v {{cache_volume}}:/home/gradle/.gradle \
        gradle:{{gradle_version}}-jdk{{version}} gradle --no-daemon test

# Summarize the last run: what ran, passed, failed, and what is skipped.
#
# This exists because a green build here is misleading. Most test classes are
# @Ignore'd at class level, so `gradle test` can report success having executed
# almost nothing. This recipe makes that visible instead of hiding it.
[doc('Show what actually ran vs. what is skipped')]
[no-cd]
census:
    #!/usr/bin/env bash
    set -euo pipefail
    dir={{root}}/build/test-results/test
    if [ ! -d "$dir" ]; then
        echo "No results yet — run 'just test all' first." >&2
        exit 1
    fi
    python3 - "$dir" <<'PY'
    import glob, sys, xml.etree.ElementTree as ET
    total = failed = skipped = 0
    rows = []
    for p in sorted(glob.glob(sys.argv[1] + '/*.xml')):
        r = ET.parse(p).getroot()
        name = r.get('name', '').split('.')[-1]
        t = int(r.get('tests', 0))
        f = int(r.get('failures', 0)) + int(r.get('errors', 0))
        s = int(r.get('skipped', 0))
        total += t; failed += f; skipped += s
        rows.append((name, t - s, (t - s) - f, f, s))
    for name, ran, passed, f, s in rows:
        flag = '  <-- entire class disabled' if ran == 0 and s else ''
        print(f"  {name:26} ran={ran:<3} passed={passed:<3} failed={f:<3} skipped={s}{flag}")
    print(f"\n  TOTAL ran={total - skipped} passed={total - skipped - failed} "
          f"failed={failed} skipped={skipped}")
    if skipped:
        print(f"\n  WARNING: {skipped} test(s) skipped. A green build does not mean "
              f"the application works.")
    PY

# Open the HTML test report
[no-cd]
report:
    @echo "file://{{root}}/build/reports/tests/test/index.html"
