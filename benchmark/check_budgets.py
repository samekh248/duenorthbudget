#!/usr/bin/env python3
"""Fails when a benchmark result is over its budget in benchmark/budgets.json.

Reads every *benchmarkData.json the macrobenchmark run left under the given folder (default: benchmark/build),
prints each result, and exits 1 when any budget is broken or a budgeted benchmark did not run.
"""

import fnmatch
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent


def results(root):
    found = {}
    for path in sorted(root.rglob("*benchmarkData.json")):
        for bench in json.loads(path.read_text())["benchmarks"]:
            key = f"{bench['className'].rsplit('.', 1)[-1]}.{bench['name']}"
            values = {}
            for metric, stats in bench.get("metrics", {}).items():
                for stat in ("median", "minimum", "maximum"):
                    if stat in stats:
                        values[f"{metric}.{stat}"] = stats[stat]
            for metric, stats in bench.get("sampledMetrics", {}).items():
                for stat in ("P50", "P90", "P95", "P99"):
                    if stat in stats:
                        values[f"{metric}.{stat}"] = stats[stat]
            found[key] = values
    return found


def main():
    root = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "build"
    budgets = json.loads((HERE / "budgets.json").read_text())["budgets"]
    found = results(root)
    if not found:
        print(f"No benchmark results under {root}")
        return 1
    for key, values in found.items():
        print(key)
        for name, value in sorted(values.items()):
            print(f"    {name} = {value:.1f}")
    broken = []
    for pattern, limits in budgets.items():
        matched = [key for key in found if fnmatch.fnmatchcase(key, pattern)]
        if not matched:
            broken.append(f"{pattern}: no result (did the benchmark run?)")
        for key in matched:
            for stat, limit in limits.items():
                value = found[key].get(stat)
                if value is None:
                    broken.append(f"{key}: no {stat}")
                elif value > limit:
                    broken.append(f"{key}: {stat} {value:.1f} is over its budget of {limit}")
    if broken:
        print("\nOver budget:")
        for line in broken:
            print(f"  {line}")
        return 1
    print("\nEvery benchmark is within its budget.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
