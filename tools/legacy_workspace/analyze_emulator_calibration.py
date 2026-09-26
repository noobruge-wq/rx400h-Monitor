#!/usr/bin/env python3
"""Build a reproducible target-head-unit performance calibration dataset.

The tool reads RX400h evidence ZIPs directly.  It never modifies the archives.
Only sessions captured on the API 27 sp7731e target head unit are admitted to
the calibration dataset.
"""

from __future__ import annotations

import argparse
import bisect
import csv
import glob
import hashlib
import json
import math
import statistics
import zipfile
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable


TARGET_MODEL = "sp7731e_1h10_native"


@dataclass(frozen=True)
class Evidence:
    path: Path
    session: dict[str, Any]
    device: dict[str, Any]
    manifest: dict[str, Any]
    performance: list[dict[str, str]]
    frames: list[dict[str, str]]

    @property
    def session_id(self) -> str:
        return str(self.session.get("session_id", ""))

    @property
    def version(self) -> str:
        return str(self.session.get("app_version", "unknown"))

    @property
    def version_code(self) -> str:
        value = self.manifest.get("version_code", self.session.get("version_code", ""))
        return "" if value is None else str(value)

    @property
    def apk_sha256(self) -> str:
        return str(self.manifest.get("apk_sha256", self.session.get("apk_sha256", "")))

    @property
    def build_id(self) -> str:
        sha = self.apk_sha256
        if sha:
            return f"v{self.version_code or '?'}-{sha[:10]}"
        return f"v{self.version_code or '?'}-legacy-{self.version}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--workspace", type=Path, required=True)
    parser.add_argument("--downloads", type=Path, required=True)
    parser.add_argument("--legacy", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def csv_entry(archive: zipfile.ZipFile, name: str) -> list[dict[str, str]]:
    candidates = [entry for entry in archive.infolist() if Path(entry.filename).name == name]
    if not candidates:
        return []
    text = archive.read(candidates[0]).decode("utf-8-sig")
    return list(csv.DictReader(text.splitlines()))


def json_entry(archive: zipfile.ZipFile, name: str) -> dict[str, Any]:
    candidates = [entry for entry in archive.infolist() if Path(entry.filename).name == name]
    if not candidates:
        return {}
    return json.loads(archive.read(candidates[0]).decode("utf-8-sig"))


def read_evidence(path: Path) -> Evidence | None:
    try:
        with zipfile.ZipFile(path) as archive:
            session = json_entry(archive, "session.json")
            if not session:
                return None
            return Evidence(
                path=path,
                session=session,
                device=json_entry(archive, "device.json"),
                manifest=json_entry(archive, "manifest.json"),
                performance=csv_entry(archive, "performance.csv"),
                frames=csv_entry(archive, "frames.csv"),
            )
    except (OSError, zipfile.BadZipFile, UnicodeDecodeError, json.JSONDecodeError):
        return None


def discover(args: argparse.Namespace) -> list[Path]:
    patterns = [
        "RX400h Monitor log*.zip",
        "RX400h Monitor research logs*.zip",
    ]
    paths: list[Path] = []
    for pattern in patterns:
        paths.extend(Path(value) for value in glob.glob(str(args.downloads / pattern)))
    uploaded = args.workspace / "work" / "e1-upload" / "RX400h-upload.zip"
    if uploaded.exists():
        paths.append(uploaded)
    if args.legacy and args.legacy.exists():
        paths.append(args.legacy)
    return sorted(set(path.resolve() for path in paths))


def parse_iso_ms(value: str) -> int | None:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=timezone.utc)
        return int(parsed.timestamp() * 1000)
    except (TypeError, ValueError):
        return None


def number(row: dict[str, str], key: str) -> float | None:
    value = row.get(key, "").strip()
    if not value:
        return None
    try:
        parsed = float(value)
    except ValueError:
        return None
    return parsed if math.isfinite(parsed) else None


def prior_frame(
    frame_times: list[int],
    frames: list[dict[str, str]],
    timestamp_ms: int,
) -> dict[str, str] | None:
    if not frame_times:
        return None
    best = bisect.bisect_right(frame_times, timestamp_ms) - 1
    if best < 0 or timestamp_ms - frame_times[best] > 2_500:
        return None
    return frames[best]


def interval_scenario(times: list[int], frames: list[dict[str, str]], start: int, end: int) -> str:
    """Conservative interval label; never borrow a future frame for a boundary."""
    if end <= start:
        return "unknown"
    first = prior_frame(times, frames, start)
    last = prior_frame(times, frames, end)
    if first is None or last is None:
        return "unknown"
    lo, hi = bisect.bisect_right(times, start), bisect.bisect_right(times, end)
    checkpoints = [start, *times[lo:hi], end]
    if any(b - a > 2_500 for a, b in zip(checkpoints, checkpoints[1:])):
        return "unknown"
    states = {scenario(frame) for frame in [first, *frames[lo:hi], last]}
    if "unknown" in states:
        return "unknown"
    return states.pop() if len(states) == 1 else "mixed"


def deduplicate(found: list[Evidence]) -> tuple[list[Evidence], list[str]]:
    groups: dict[str, list[Evidence]] = defaultdict(list)
    for evidence in found:
        groups[evidence.session_id].append(evidence)
    admitted, conflicts = [], []
    for session_id, group in sorted(groups.items()):
        signatures = {
            json.dumps([e.version, e.apk_sha256, e.device, e.performance, e.frames], sort_keys=True)
            for e in group
        }
        if not session_id or len(signatures) != 1:
            conflicts.append(session_id or "<missing session ID>")
        else:
            admitted.append(group[0])
    return admitted, conflicts


def scenario(frame: dict[str, str] | None) -> str:
    if frame is None:
        return "unknown"
    speed = number(frame, "speed_kph")
    rpm = number(frame, "rpm")
    ice_power = number(frame, "ice_power_kw")
    if speed is not None and speed > 1.0:
        return "moving"
    if speed is not None and speed <= 1.0:
        rpm_off = rpm is not None and rpm <= 50.0
        power_off = ice_power is not None and abs(ice_power) <= 0.05
        if rpm_off and power_off:
            return "stopped_ice_off"
        return "stopped_other"
    return "unknown"


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    weight = position - lower
    return ordered[lower] * (1.0 - weight) + ordered[upper] * weight


def build_samples(evidences: Iterable[Evidence]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for evidence in evidences:
        frames = []
        for frame in evidence.frames:
            timestamp = number(frame, "timestamp_ms")
            if timestamp is not None:
                frames.append((int(timestamp), frame))
        frame_clock_reversed = any(b[0] < a[0] for a, b in zip(frames, frames[1:]))
        frames.sort(key=lambda item: item[0])
        frame_times = [item[0] for item in frames]
        frame_rows = [item[1] for item in frames]

        previous_timestamp: int | None = None
        previous_elapsed: float | None = None
        first_elapsed = number(evidence.performance[0], "elapsed_ms") if evidence.performance else None
        for index, performance in enumerate(evidence.performance):
            timestamp = parse_iso_ms(performance.get("timestamp_iso", ""))
            cpu_delta = number(performance, "cpu_delta_ms")
            elapsed = number(performance, "elapsed_ms")
            start_timestamp = previous_timestamp
            interval_ms = elapsed - previous_elapsed if elapsed is not None and previous_elapsed is not None else None
            # Advance on every row, even an invalid CPU row: cpu_delta belongs
            # only to the immediately preceding sampling interval.
            previous_timestamp = timestamp
            previous_elapsed = elapsed
            if interval_ms is None or interval_ms <= 0 or cpu_delta is None or cpu_delta < 0:
                continue
            drift = timestamp - start_timestamp - interval_ms if timestamp is not None and start_timestamp is not None else None
            flags = []
            if elapsed - (first_elapsed or 0) < 30_000:
                flags.append("warmup")
            if not 1_000 <= interval_ms <= 30_000:
                flags.append("irregular_interval")
            if drift is None or abs(drift) > 250 or frame_clock_reversed:
                flags.append("clock_alignment_uncertain")
            frame = prior_frame(frame_times, frame_rows, timestamp) if timestamp is not None else None
            state = interval_scenario(frame_times, frame_rows, start_timestamp, timestamp) if start_timestamp is not None and timestamp is not None else "unknown"
            if state in ("unknown", "mixed"):
                flags.append(state)
            rows.append(
                {
                    "version": evidence.version,
                    "version_code": evidence.version_code,
                    "build_id": evidence.build_id,
                    "apk_sha256": evidence.apk_sha256,
                    "session_id": evidence.session_id,
                    "source_file": evidence.path.name,
                    "sample_index": index,
                    "timestamp_ms": timestamp,
                    "elapsed_ms": elapsed,
                    "interval_ms": float(interval_ms),
                    "wall_clock_drift_ms": drift,
                    "cpu_delta_ms": cpu_delta,
                    "process_cpu_pct": cpu_delta / float(interval_ms) * 100.0,
                    "pss_kb": number(performance, "pss_kb"),
                    "request_hz": number(performance, "request_hz"),
                    "signal_update_hz": number(performance, "signal_update_hz"),
                    "speed_kph": number(frame or {}, "speed_kph"),
                    "rpm": number(frame or {}, "rpm"),
                    "ice_power_kw": number(frame or {}, "ice_power_kw"),
                    "end_frame_scenario": scenario(frame),
                    "scenario": state,
                    "quality_flags": "|".join(flags),
                    "clean_interval": not flags,
                }
            )
    return rows


def summarize(samples: list[dict[str, Any]], key_fields: tuple[str, ...]) -> list[dict[str, Any]]:
    groups: dict[tuple[Any, ...], list[dict[str, Any]]] = defaultdict(list)
    for sample in samples:
        key = tuple(sample[field] for field in key_fields)
        groups[key].append(sample)
    output = []
    for key, group in sorted(groups.items()):
        cpu_values = [float(row["process_cpu_pct"]) for row in group]
        intervals = [float(row["interval_ms"]) for row in group]
        cpu_deltas = [float(row["cpu_delta_ms"]) for row in group]
        pss_values = [float(row["pss_kb"]) for row in group if row["pss_kb"] is not None]
        request_values = [float(row["request_hz"]) for row in group if row["request_hz"] is not None]
        item = {field: value for field, value in zip(key_fields, key)}
        item.update(
            {
                "samples": len(group),
                "wall_seconds": sum(intervals) / 1000.0,
                "weighted_cpu_pct": sum(cpu_deltas) / sum(intervals) * 100.0,
                "sample_cpu_p50": percentile(cpu_values, 0.50),
                "sample_cpu_p90": percentile(cpu_values, 0.90),
                "pss_kb_median": statistics.median(pss_values) if pss_values else None,
                "pss_kb_max": max(pss_values) if pss_values else None,
                "request_hz_median": statistics.median(request_values) if request_values else None,
            }
        )
        output.append(item)
    return output


def write_csv(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        path.write_text("", encoding="utf-8")
        return
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def rounded(value: Any, digits: int = 2) -> str:
    if value is None:
        return "—"
    if isinstance(value, float):
        return f"{value:.{digits}f}"
    return str(value)


def main() -> None:
    args = parse_args()
    found = [evidence for path in discover(args) if (evidence := read_evidence(path))]

    unique, conflicts = deduplicate(found)

    target = [
        evidence
        for evidence in unique
        if evidence.device.get("model") == TARGET_MODEL
        and str(evidence.device.get("api_level")) == "27"
        and evidence.device.get("screen_width_px") == 1280
        and evidence.device.get("screen_height_px") == 720
        and evidence.session.get("status") == "completed"
        and evidence.performance
        and evidence.frames
    ]
    samples = build_samples(target)
    sessions = summarize(samples, ("version", "version_code", "build_id", "session_id"))
    builds = summarize(samples, ("version", "version_code", "build_id", "apk_sha256", "scenario"))
    clean_builds = summarize([r for r in samples if r["clean_interval"]], ("version", "build_id", "apk_sha256", "scenario"))
    versions = summarize(samples, ("version", "scenario"))

    args.output.mkdir(parents=True, exist_ok=True)
    write_csv(args.output / "target_samples.csv", samples)
    write_csv(args.output / "target_sessions.csv", sessions)
    write_csv(args.output / "target_build_scenarios.csv", builds)
    write_csv(args.output / "target_clean_build_scenarios.csv", clean_builds)
    write_csv(args.output / "target_version_scenarios.csv", versions)

    inventory = {
        "schema": 2,
        "target_model": TARGET_MODEL,
        "discovered_archives": len(found),
        "unique_sessions": len(unique),
        "conflicting_sessions_excluded": conflicts,
        "admitted_target_sessions": len(target),
        "admitted_versions": sorted({evidence.version for evidence in target}),
        "admitted_builds": sorted({evidence.build_id for evidence in target}),
        "exact_apk_build_count": len({e.apk_sha256 for e in target if e.apk_sha256}),
        "legacy_build_group_count": len({e.build_id for e in target if not e.apk_sha256}),
        "sample_count": len(samples),
        "wall_seconds": sum(float(row["interval_ms"]) for row in samples) / 1000.0,
        "sources": [{"session_id": e.session_id, "file": e.path.name,
                     "zip_sha256": hashlib.sha256(e.path.read_bytes()).hexdigest(),
                     "apk_sha256": e.apk_sha256} for e in target],
    }
    inventory["source_fingerprint"] = hashlib.sha256("\n".join(sorted(
        f"{s['session_id']}|{s['zip_sha256']}" for s in inventory["sources"]
    )).encode()).hexdigest()
    (args.output / "inventory.json").write_text(
        json.dumps(inventory, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    lines = [
        "# RX400h 目标车机性能校准基准",
        "",
        "本表由原始证据 ZIP 直接生成，重复副本按 `session_id` 去重。",
        "只纳入 `sp7731e_1h10_native / Android 8.1 API 27` 上完成且同时含有",
        "`performance.csv` 和 `frames.csv` 的会话。",
        "",
        f"- 有效会话：{len(target)}",
        f"- 版本：{len(inventory['admitted_versions'])}",
        f"- 可核实 APK：{inventory['exact_apk_build_count']}；缺少 APK 哈希的历史组：{inventory['legacy_build_group_count']}",
        f"- 性能样本：{len(samples)}",
        f"- 单调采样间隔合计：{inventory['wall_seconds'] / 60.0:.1f} 分钟（含有质量标记的区间）",
        "",
        "## 按确切 APK 与干净工况区间",
        "",
        "| 版本 / APK前缀 | 工况 | 样本 | 时间(s) | 加权CPU | CPU p50 | CPU p90 | PSS中位(MiB) | 请求Hz中位 |",
        "|---|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    labels = {
        "moving": "行驶",
        "stopped_ice_off": "停车/发动机停机",
        "stopped_other": "停车/发动机运行或状态不完整",
        "unknown": "无法分类",
    }
    for row in clean_builds:
        pss_mib = row["pss_kb_median"] / 1024.0 if row["pss_kb_median"] is not None else None
        lines.append(
            "| {version} | {scenario} | {samples} | {seconds} | {weighted}% | {p50}% | {p90}% | {pss} | {hz} |".format(
                version=row["version"] + " / " + row["build_id"],
                scenario=labels.get(row["scenario"], row["scenario"]),
                samples=row["samples"],
                seconds=rounded(row["wall_seconds"], 1),
                weighted=rounded(row["weighted_cpu_pct"]),
                p50=rounded(row["sample_cpu_p50"]),
                p90=rounded(row["sample_cpu_p90"]),
                pss=rounded(pss_mib),
                hz=rounded(row["request_hz_median"]),
            )
        )
    lines.extend(
        [
            "",
            "## 分类与计算口径",
            "",
            "- 单样本 CPU：`cpu_delta_ms / 相邻 elapsed_ms 差`；手机校时不改变分母，100% 约等于一个核心，允许超过100%。",
            "- 加权 CPU：同组 CPU 时间总和除以同组单调时间总和。CSV的 wall_seconds 列保留旧列名，其含义是单调时间。",
            "- 行驶：整个采样区间已观测车辆帧均为 `speed > 1 km/h`；这不是熄火判定。",
            "- 停车/发动机停机：`speed <= 1`、`rpm <= 50` 且 `abs(ICE power) <= 0.05 kW`。",
            "- 每个会话首个样本一律不算 CPU；缺失/重置间隔不拼接；后续长间隔保留但标记，避免静默删除暂停区间。",
            "- 只使用不晚于边界的车辆帧，最长2.5秒；区间混合工况单列，首个性能点后30秒标为预热。",
            "- 校时差超过250ms、帧时钟逆序、区间缺帧均不能进入上表；CPU本身仍保存在原始样本表。",
            "- 车辆帧约1Hz且无freshness字段，因此分类是观测代理，不证明完整实时状态；不同会话非控制实验。",
            "- 版本汇总CSV仅供清点，不用于优化结论；相同v28版本号下的APK必须分开。",
            "- 原日志 render_ms 不包含异步 View.onDraw，不能当完整绘制耗时；request_hz是所有请求总和，不是单信号Hz。",
            "- 同session但内容不一致的档案排除并列于inventory；ZIP SHA-256可追溯且不依赖本地路径。",
            "",
            "该基准用于校准模拟器的相对负载与版本排序，不用于声称模拟器复刻了展锐 SoC。",
            "",
        ]
    )
    (args.output / "REAL_CAR_BASELINE.md").write_text("\n".join(lines), encoding="utf-8")

    print(json.dumps(inventory, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
