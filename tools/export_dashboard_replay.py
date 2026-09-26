"""Extract recorded *display frames*, not acquisition events, for debug UI replay.

No decoder, freshness inference, protocol replay or interpolated fake samples.
Only whitelisted numeric display fields leave the original read-only ZIP.
"""
import argparse
import csv
import hashlib
import io
import json
import math
from pathlib import Path
import zipfile

FIELDS = ("speed_kph", "soc_pct", "battery_temp_min_c", "battery_temp_max_c",
          "battery_temp_avg_c", "hv_power_kw", "rpm", "coolant_c", "adapter_12v_v",
          "ice_power_kw", "idle_check_active")


def extract(data):
    rows = list(csv.DictReader(data.decode("utf-8-sig").splitlines()))
    if not 2 <= len(rows) <= 1801:
        raise ValueError("Replay requires 2..1801 display frames")
    origin = int(rows[0]["timestamp_ms"])
    output = io.StringIO(newline="")
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(("at_ms", *FIELDS))
    last = -1
    for row in rows:
        at = int(row["timestamp_ms"]) - origin
        if not last < at <= 1_800_000:
            raise ValueError("Non-monotonic/oversized display trace: do not silently repair clocks")
        last = at
        values = []
        for field in FIELDS:
            value = row.get(field, "")
            if field == "idle_check_active":
                if value not in ("", "true", "false"):
                    raise ValueError("Invalid Idle display state")
            elif value and not math.isfinite(float(value)):
                raise ValueError("Non-finite display value")
            values.append(value)
        writer.writerow((at, *values))
    return output.getvalue().encode(), len(rows), last


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with zipfile.ZipFile(args.source) as archive:
        entries = {Path(entry.filename).name: entry for entry in archive.infolist()}
        frames = archive.read(entries["frames.csv"])
        manifest = json.loads(archive.read(entries["manifest.json"]))
    trace, count, duration = extract(frames)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "recorded.csv").write_bytes(trace)
    provenance = {
        "schema": 1, "source_file": args.source.name,
        "source_zip_sha256": hashlib.sha256(args.source.read_bytes()).hexdigest(),
        "source_apk_sha256": manifest.get("apk_sha256"),
        "frames_sha256": hashlib.sha256(frames).hexdigest(),
        "trace_sha256": hashlib.sha256(trace).hexdigest(),
        "samples": count, "duration_ms": duration,
        "limitations": "Recorded display frames (~1Hz), wall-time offsets; no freshness, no original asynchronous publish timing. Non-empty values are treated as fresh for UI workload only. Not an OBD or 5Hz acquisition replay."
    }
    (args.output / "recorded.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(provenance, ensure_ascii=False))


if __name__ == "__main__":
    main()
