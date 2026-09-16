#!/usr/bin/env python3
"""Compare a newman JSON report against the expected-failures manifest.

Exit 0 only when the failing requests are exactly the ones listed. An unexpected
failure is a regression; an unexpected pass means the manifest is stale.
"""
import json
import sys
from pathlib import Path


def ordered_request_names(collection):
    names = []

    def walk(items, folder=None):
        for it in items:
            if "item" in it:
                walk(it["item"], folder or it["name"])
            else:
                names.append(f"{folder} / {it['name']}" if folder else it["name"])

    walk(collection["item"])
    return names


def execution_failed(ex):
    if ex.get("requestError"):
        return True
    if any(a.get("error") for a in ex.get("assertions", [])):
        return True
    if any(s.get("error") for s in ex.get("testScript", [])):
        return True
    return False


def failed_requests(report, ordered_names):
    failed = set()
    for name, ex in zip(ordered_names, report["run"]["executions"]):
        if execution_failed(ex):
            failed.add(name)
    return failed


def manifest(path):
    lines = Path(path).read_text().splitlines()
    return {l.strip() for l in lines if l.strip() and not l.lstrip().startswith("#")}


def main(report_path, manifest_path, collection_path):
    collection = json.loads(Path(collection_path).read_text())
    ordered_names = ordered_request_names(collection)
    failed = failed_requests(json.loads(Path(report_path).read_text()), ordered_names)
    expected = manifest(manifest_path)
    sections = (
        ("UNEXPECTED FAILURES (regressions)", sorted(failed - expected)),
        ("UNEXPECTED PASSES (update the manifest)", sorted(expected - failed)),
    )
    for title, items in sections:
        if items:
            print(title)
            print("\n".join(f"  {i}" for i in items))
    print(f"{len(failed)} failed, {len(expected)} expected")
    return 1 if any(items for _, items in sections) else 0


if __name__ == "__main__":
    collection = sys.argv[3] if len(sys.argv) > 3 else str(Path(__file__).with_name("Conduit.postman_collection.json"))
    sys.exit(main(sys.argv[1], sys.argv[2], collection))
