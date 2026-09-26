"""Offline analysis of bounded UI-only replay captures; never alters raw evidence.

Process windows use the fixture's monotonic timing, exclude any overlap with
the first 10 seconds or the post-input settle. Renderer windows lack elapsedMs:
their alignment explicitly uses log timestamps and is rejected on clock reversal.
No car CPU calibration, isolated GPU timing, or OBD-rate inference is made.
"""
import argparse
from bisect import bisect_right
import csv
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re


LINE = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)\s+(\d+)\s+\d+\s+\w\s+(RX400hReplay|RX400hRenderer): (.*)$")
PAIR = re.compile(r"(\w+)=([^ ]+)")


def fields(text):
    result = {}
    for key, value in PAIR.findall(text):
        try:
            result[key] = float(value) if "." in value else int(value)
        except ValueError:
            result[key] = value
    return result


def steady(rows, duration_ms, warmup_ms=10000):
    return [r for r in rows if r["reason"] == "window"
            and r["elapsedMs"] - r["windowMs"] >= warmup_ms
            and r["elapsedMs"] <= duration_ms]


def weighted(rows, key, weight):
    total = sum(r[weight] for r in rows)
    return sum(r[key] * r[weight] for r in rows) / total if total else None


def recorded_state(frame):
    speed, rpm = frame["speed_kph"], frame["rpm"]
    if speed == "":
        return "unknown"
    if abs(float(speed)) > 1:
        return "moving_display"
    if rpm == "":
        return "unknown"
    return "parked_ice_off_display" if abs(float(rpm)) <= 1 else "parked_ice_on_display"


def interval_state(trace, start, end):
    """Held displayed inputs on [start,end), never future frames; gap<=2.5s."""
    times = [int(frame["at_ms"]) for frame in trace]
    index = bisect_right(times, start) - 1
    if index < 0:
        return "unknown"
    states = set()
    while index < len(trace) and times[index] < end:
        held_end = min(end, times[index + 1] if index + 1 < len(trace) else end)
        if held_end - times[index] > 2500:
            return "unknown_or_source_gap"
        states.add(recorded_state(trace[index]))
        index += 1
    if "unknown" in states or not states:
        return "unknown"
    return next(iter(states)) if len(states) == 1 else "mixed_display_states"


def scenario_summary(data, trace):
    grouped = {}
    for row in steady(data["process_windows"], data["duration_ms"]):
        name = interval_state(trace, row["elapsedMs"] - row["windowMs"], row["elapsedMs"])
        grouped.setdefault(name, []).append(row)
    return {name: {"windows": len(rows), "window_ms": sum(r["windowMs"] for r in rows),
                   "cpu_pct_weighted": weighted(rows, "processCpuPct", "windowMs"),
                   "window_frame_hz": sum(r["windowFrames"] for r in rows) * 1000 / sum(r["windowMs"] for r in rows)}
            for name, rows in grouped.items()}


def summarize(identity, text):
    parsed = []
    for line in text.splitlines():
        match = LINE.match(line)
        if match:
            stamp, pid, tag, message = match.groups()
            parsed.append((datetime.strptime("2026-" + stamp, "%Y-%m-%d %H:%M:%S.%f"), pid, tag, message, fields(message)))
    starts = [p for p in parsed if p[2] == "RX400hReplay" and p[3].startswith("start ")
              and p[4].get("runId") == identity["run_id"]]
    if len(starts) != 1:
        raise ValueError("Expected exactly one start with the capture run_id")
    start = starts[0]
    loaded = [p[4] for p in parsed if p[1] == start[1] and p[3].startswith("loaded ")]
    if len(loaded) != 1 or loaded[0]["mode"] != identity["mode"]:
        raise ValueError("Missing/ambiguous source duration or mode")
    duration = loaded[0]["durationMs"]
    planned = start[4]["plannedSnapshots"]
    rows = [p[4] for p in parsed if p[2] == "RX400hReplay" and p[1] == start[1]
            and p[4].get("runId") == identity["run_id"] and "reason" in p[4]]
    if not rows or rows[-1]["reason"] != "complete" or sum(r["reason"] == "complete" for r in rows) != 1:
        raise ValueError("Incomplete/aborted run; not a performance result")
    previous_elapsed, previous_published, previous_skipped = 0, 0, 0
    for row in rows:
        if row["mode"] != identity["mode"] or row["windowMs"] <= 0 or row["elapsedMs"] <= previous_elapsed:
            raise ValueError("Invalid mode or non-monotonic window")
        if row["published"] < previous_published or row["skipped"] < previous_skipped:
            raise ValueError("Counter reversal")
        if row["published"] + row["skipped"] != row["sourceIndex"] + 1:
            raise ValueError("Input accounting mismatch")
        previous_elapsed, previous_published, previous_skipped = row["elapsedMs"], row["published"], row["skipped"]
    complete = rows[-1]
    if complete["published"] + complete["skipped"] != planned:
        raise ValueError("Completion does not account for all planned inputs")
    clean = steady(rows, duration)
    if not clean:
        raise ValueError("No fully post-warmup, pre-settle windows")
    window_ms = sum(r["windowMs"] for r in clean)
    frame_count = sum(r["windowFrames"] for r in clean)
    clock_ok = all(a[0] <= b[0] for a, b in zip(parsed, parsed[1:]))
    renderer = []
    if clock_ok:
        for stamp, pid, tag, message, row in parsed:
            end_ms = (stamp - start[0]).total_seconds() * 1000
            if tag == "RX400hRenderer" and pid == start[1] and "windowMs" in row:
                if end_ms - row["windowMs"] >= 10000 and end_ms <= duration:
                    renderer.append(row)
    rebuilds = sum(r["rebuilds"] for r in renderer)
    renderer_ms = sum(r["windowMs"] for r in renderer)
    return {
        "mode": identity["mode"], "run_id": identity["run_id"], "pid": int(start[1]),
        "reference_renderer": identity.get("reference_renderer"),
        "fresh_process": identity.get("fresh_process"),
        "apk_sha256": identity["apk_sha256"].split()[0],
        "api": identity["api"], "screen": identity["screen"], "density": identity["density"],
        "preferences_raw": identity["effect_preferences"],
        "duration_ms": duration, "completed_elapsed_ms": complete["elapsedMs"],
        "planned": planned, "published": complete["published"], "skipped": complete["skipped"],
        "all_windows_late_max_ms": max(r["lateMaxMs"] for r in rows),
        "steady_windows": len(clean), "steady_ms": window_ms,
        "process_cpu_pct_weighted": weighted(clean, "processCpuPct", "windowMs"),
        "process_cpu_window_min_pct": min(r["processCpuPct"] for r in clean),
        "process_cpu_window_max_pct": max(r["processCpuPct"] for r in clean),
        "pss_min_kb": min(r["pssKb"] for r in clean), "pss_max_kb": max(r["pssKb"] for r in clean),
        "pss_first_kb": clean[0]["pssKb"], "pss_last_kb": clean[-1]["pssKb"],
        "pss_complete_kb": complete["pssKb"],
        "window_frame_hz": frame_count * 1000 / window_ms,
        "window_frames": frame_count,
        "frame_total_avg_us": weighted(clean, "totalDurationAvgUs", "windowFrames"),
        "frame_total_max_us": max(r["totalDurationMaxUs"] for r in clean),
        "frames_over_50ms": sum(r["over50Ms"] for r in clean),
        "frame_metric_drops": sum(r["frameMetricDrops"] for r in clean),
        "steady_late_max_ms": max(r["lateMaxMs"] for r in clean),
        "renderer_clock_alignment_valid": clock_ok,
        "renderer_windows": len(renderer), "renderer_window_ms": renderer_ms,
        "rebuilds": rebuilds,
        "rebuild_hz": rebuilds * 1000 / renderer_ms if renderer_ms else None,
        "rebuild_wall_avg_us": weighted(renderer, "wallAvgUs", "rebuilds"),
        "rebuild_thread_cpu_avg_us": weighted(renderer, "threadCpuAvgUs", "rebuilds"),
        "renderer_hardware_canvas_seen": any(r["hardwareCanvasSeen"] == "true" for r in renderer),
        "android_runtime_error_observed": bool(re.search(r"\bE AndroidRuntime\s*:", text)),
        "process_windows": rows,
        "notes": [
            "100% CPU approximately one guest core; whole debug process including measurement, not only renderer.",
            "Exclude windows overlapping 0-10s warmup or final input-to-completion settle; no prorating.",
            "FrameMetrics TOTAL_DURATION is not GPU-only time; frame Hz is not input/OBD Hz.",
            "Renderer alignment uses stable log time, unlike monotonic process windows; missing rows are unknown, not zero.",
            "Software SwiftShader host is not a calibrated car GPU; hardwareCanvasSeen describes Android View Canvas only.",
        ],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--recorded-trace", type=Path)
    args = parser.parse_args()
    trace = None
    if args.recorded_trace:
        trace_hash = hashlib.sha256(args.recorded_trace.read_bytes()).hexdigest()
        if trace_hash != "214b1f95151ebc17ea285945fe866721d5a8e5f52dee175ce07e28bb7f97cca9":
            raise ValueError("Recorded trace is not the validation APK's fixed source")
        with args.recorded_trace.open(encoding="utf-8", newline="") as source:
            trace = list(csv.DictReader(source))
    output = {"complete": [], "incomplete_or_invalid": []}
    for metadata in sorted(args.root.glob("*/identity.json")):
        identity = json.loads(metadata.read_text(encoding="utf-8"))
        if "mode" not in identity:  # Other independently identified fixture suites.
            continue
        raw = metadata.parent / "logcat.txt"
        try:
            data = summarize(identity, raw.read_text(encoding="utf-8"))
            data["capture"] = metadata.parent.name
            data["log_sha256"] = hashlib.sha256(raw.read_bytes()).hexdigest()
            if trace is not None and data["mode"] == "recorded":
                data["recorded_trace_sha256"] = trace_hash
                data["display_scenarios"] = scenario_summary(data, trace)
                data["notes"].append("Display scenarios classify held replay inputs, not vehicle ignition or original asynchronous acquisition.")
            output["complete"].append(data)
            print(f"{data['capture']}: CPU {data['process_cpu_pct_weighted']:.2f}% | frames {data['window_frame_hz']:.2f} Hz | "
                  f"inputs {data['published']}/{data['planned']} skipped {data['skipped']} | "
                  f"late {data['steady_late_max_ms']} ms | PSS {data['pss_min_kb']}-{data['pss_max_kb']} KB")
        except (ValueError, KeyError, OSError) as error:
            output["incomplete_or_invalid"].append({"capture": metadata.parent.name, "error": str(error)})
            print(f"{metadata.parent.name}: INCOMPLETE/INVALID {error}")
    args.output.write_text(json.dumps(output, indent=2, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
