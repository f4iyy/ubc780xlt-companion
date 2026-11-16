#!/usr/bin/env python3
"""Print a Markdown summary of the test or coverage results (used for the GitHub job summary).

Usage: summary.py tests | coverage
"""
import glob
import sys
import xml.etree.ElementTree as ET


def tests() -> None:
    total = failures = errors = skipped = 0
    for path in glob.glob("target/surefire-reports/TEST-*.xml") + glob.glob("target/failsafe-reports/TEST-*.xml"):
        root = ET.parse(path).getroot()
        total += int(root.get("tests", 0))
        failures += int(root.get("failures", 0))
        errors += int(root.get("errors", 0))
        skipped += int(root.get("skipped", 0))
    print("| Tests | Failures | Errors | Skipped |")
    print("|------:|---------:|-------:|--------:|")
    print(f"| {total} | {failures} | {errors} | {skipped} |")


def coverage() -> None:
    root = ET.parse("target/reports/coverage/jacoco.xml").getroot()
    print("| Counter | Covered | Missed | Coverage |")
    print("|---------|--------:|-------:|---------:|")
    for counter in root.findall("counter"):
        covered, missed = int(counter.get("covered")), int(counter.get("missed"))
        total = covered + missed
        pct = 100.0 * covered / total if total else 100.0
        print(f"| {counter.get('type')} | {covered} | {missed} | {pct:.1f} % |")


if __name__ == "__main__":
    {"tests": tests, "coverage": coverage}[sys.argv[1]]()
