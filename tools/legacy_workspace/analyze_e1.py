import csv
import json
import math
import statistics
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path

ROOT = Path(r"C:\Users\冠儒\Documents\Codex\2026-08-12\files-mentioned-by-the-user-rx400h\work\v030_scheduler_baseline_3af54a30")

CORE = [
    ("7E0", "01040C0D0E10 2", 800),
    ("7E0", "21CDF3 3", 1000),
    ("7E0", "01050607 1", 3000),
    ("7E2", "21C3 6", 800),
    ("7E2", "21C4 5", 1500),
    ("7E2", "21CF 4", 5000),
    (None, "ATRV", 3000),
]


def q(values, p):
    xs = sorted(values)
    if not xs:
        return math.nan
    pos = (len(xs) - 1) * p
    lo, hi = math.floor(pos), math.ceil(pos)
    if lo == hi:
        return xs[lo]
    return xs[lo] + (xs[hi] - xs[lo]) * (pos - lo)


def iso(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


with (ROOT / "performance.csv").open(newline="", encoding="utf-8") as f:
    perf = list(csv.DictReader(f))
for row in perf:
    for k in row:
        if k != "timestamp_iso":
            row[k] = float(row[k])

print("PERFORMANCE")
print("rows", len(perf))
print("first", perf[0]["timestamp_iso"], "last", perf[-1]["timestamp_iso"])
print("wall_span_s", (iso(perf[-1]["timestamp_iso"]) - iso(perf[0]["timestamp_iso"])).total_seconds())
for key in ["request_hz", "publish_hz", "latency_p50_ms", "latency_p95_ms", "latency_p99_ms"]:
    vals = [r[key] for r in perf[1:]]  # discard startup denominator artefact
    print(key, "min", min(vals), "median", statistics.median(vals), "p95", q(vals, .95), "max", max(vals))
for key in ["cycle_ms","render_ms","logger_write_ms","cpu_delta_ms"]:
    vals=[r[key] for r in perf[1:]]
    print(key,"median",statistics.median(vals),"p95",q(vals,.95),"p99",q(vals,.99),"max",max(vals))

for key in ["deadline_misses", "skipped_overdue"]:
    vals = [int(r[key]) for r in perf]
    diffs = [b-a for a,b in zip(vals, vals[1:])]
    print(key, "first", vals[0], "last", vals[-1], "sum_delta", sum(diffs),
          "diff_counts", dict(sorted(Counter(diffs).items())), "max_step", max(diffs),
          "zero_steps", sum(d == 0 for d in diffs))

misses = [int(r["deadline_misses"]) for r in perf]
skips = [int(r["skipped_overdue"]) for r in perf]
print("counter_equal_every_row", all(a == b for a,b in zip(misses, skips)))
print("miss_rate_perf_span_per_s", (misses[-1]-misses[0]) / ((iso(perf[-1]["timestamp_iso"]) - iso(perf[0]["timestamp_iso"])).total_seconds()))
x = [(iso(r["timestamp_iso"])-iso(perf[0]["timestamp_iso"])).total_seconds() for r in perf]
y = misses
xbar, ybar = statistics.mean(x), statistics.mean(y)
slope = sum((a-xbar)*(b-ybar) for a,b in zip(x,y))/sum((a-xbar)**2 for a in x)
intercept = ybar-slope*xbar
ss_res = sum((b-(intercept+slope*a))**2 for a,b in zip(x,y))
ss_tot = sum((b-ybar)**2 for b in y)
print("miss_linear_fit_slope_per_s",slope,"intercept",intercept,"r2",1-ss_res/ss_tot,
      "max_abs_residual",max(abs(b-(intercept+slope*a)) for a,b in zip(x,y)))

raw = []
with (ROOT / "raw_io.jsonl").open(encoding="utf-8") as f:
    for line in f:
        raw.append(json.loads(line))

print("\nRAW")
print("rows", len(raw), "status", dict(Counter(r["status"] for r in raw)))
print("first", raw[0]["wall_time_iso"], "last", raw[-1]["wall_time_iso"],
      "wall_span_s", (iso(raw[-1]["wall_time_iso"]) - iso(raw[0]["wall_time_iso"])).total_seconds(),
      "mono_span_s", (raw[-1]["monotonic_ns"] - raw[0]["monotonic_ns"]) / 1e9)

by_key = defaultdict(list)
for r in raw:
    by_key[(r.get("tx_header"), r["command_sent"])].append(r)

for header, cmd, period in CORE:
    rows = by_key[(header, cmd)]
    ts = [r["monotonic_ns"] / 1e6 for r in rows]
    intervals = [b-a for a,b in zip(ts, ts[1:])]
    lat = [r["latency_ms"] for r in rows]
    statuses = Counter(r["status"] for r in rows)
    print("CORE", header or "NONE", cmd, "period", period, "n", len(rows), "status", dict(statuses),
          "lat_med", q(lat,.5), "lat_p95", q(lat,.95), "lat_max", max(lat),
          "int_min", min(intervals), "int_med", q(intervals,.5), "int_p95", q(intervals,.95), "int_max", max(intervals),
          "within_period_pct", 100*sum(v <= period for v in intervals)/len(intervals),
          "within_1.5period_pct", 100*sum(v <= 1.5*period for v in intervals)/len(intervals),
          "effective_hz", 1000/q(intervals,.5))

for header_cmd in [(None,"ATSH7E0"),(None,"ATSH7E2")]:
    rows = by_key[header_cmd]
    lat = [r["latency_ms"] for r in rows]
    print("HEADER", header_cmd[1], "n", len(rows), "lat_med", q(lat,.5), "lat_p95", q(lat,.95), "lat_max", max(lat))

print("command_counts")
for (header, cmd), rows in sorted(by_key.items(), key=lambda kv: (-len(kv[1]), str(kv[0]))):
    print(header or "NONE", cmd, len(rows), dict(Counter(r["status"] for r in rows)))

# Consecutive live records and time consumed between log-record timestamps.
live_start = iso("2026-08-10T20:29:10.063957Z")
live_stop = iso("2026-08-10T21:16:40.451182Z")
live = [r for r in raw if live_start <= iso(r["wall_time_iso"]) <= live_stop]
print("live_rows", len(live), "live_core", sum((r.get("tx_header"),r["command_sent"]) in {(h,c) for h,c,_ in CORE} for r in live),
      "live_headers", sum(r["command_sent"].startswith("ATSH") for r in live))

core_keys = {(h,c) for h,c,_ in CORE}
def category(r):
    if r["command_sent"].startswith("ATSH"):
        return "header"
    if (r.get("tx_header"), r["command_sent"]) in core_keys:
        return "scheduled"
    return "other"

cat_count = Counter(category(r) for r in live)
cat_latency = Counter()
cat_predrain = Counter()
cat_spacing = Counter()
for r in live:
    cat_latency[category(r)] += r["latency_ms"]
    cat_predrain[category(r)] += r["pre_drain_ms"]
for a,b in zip(live,live[1:]):
    cat_spacing[category(b)] += (b["monotonic_ns"]-a["monotonic_ns"])/1e6
print("live_category_count", dict(cat_count))
print("live_category_raw_latency_s", {k:round(v/1000,3) for k,v in cat_latency.items()})
print("live_category_predrain_s", {k:round(v/1000,3) for k,v in cat_predrain.items()})
print("live_category_arrival_spacing_s", {k:round(v/1000,3) for k,v in cat_spacing.items()},
      "spacing_share", {k:round(100*v/sum(cat_spacing.values()),2) for k,v in cat_spacing.items()})
live_duration = (live_stop-live_start).total_seconds()
print("live_duration_s",live_duration,"scheduled_hz",cat_count['scheduled']/live_duration,
      "header_hz",cat_count['header']/live_duration,"all_tx_hz",len(live)/live_duration,
      "header_tx_pct",100*cat_count['header']/len(live))
for header, cmd, period in CORE:
    rows = [r for r in live if (r.get("tx_header"),r["command_sent"]) == (header,cmd)]
    ts = [r["monotonic_ns"]/1e6 for r in rows]
    ints = [b-a for a,b in zip(ts,ts[1:])]
    avg_hz = len(rows)/live_duration
    target_hz = 1000/period
    print("LIVE_CORE",header or "NONE",cmd,"n",len(rows),"avg_interval_ms",live_duration*1000/len(rows),
          "median_interval_ms",q(ints,.5),"p95_interval_ms",q(ints,.95),
          "avg_hz",avg_hz,"target_hz",target_hz,"target_pct",100*avg_hz/target_hz,
          "median_multiple",q(ints,.5)/period)

nominal_hz = sum(1000/p for _,_,p in CORE)
nominal_seconds_per_second_raw_plus_fixed = 0.0
nominal_seconds_per_second_raw_only = 0.0
for header,cmd,period in CORE:
    vals=[r["latency_ms"] for r in live if (r.get("tx_header"),r["command_sent"])==(header,cmd)]
    meanlat=statistics.mean(vals)
    nominal_seconds_per_second_raw_only += meanlat/period
    nominal_seconds_per_second_raw_plus_fixed += (meanlat+200)/period
print("nominal_hz",nominal_hz,"nominal_raw_latency_s_per_s",nominal_seconds_per_second_raw_only,
      "nominal_raw_plus_120gap_80drain_s_per_s",nominal_seconds_per_second_raw_plus_fixed)

# Approximate occupied time by the inter-record spacing; report per next command category.
spacings = defaultdict(list)
for a,b in zip(live,live[1:]):
    dt = (b["monotonic_ns"]-a["monotonic_ns"])/1e6
    key=(b.get("tx_header"),b["command_sent"])
    spacings[key].append(dt)
for header,cmd,period in CORE:
    vals=spacings[(header,cmd)]
    print("ARRIVAL_SPACING",header or "NONE",cmd,"n",len(vals),"med",q(vals,.5),"p95",q(vals,.95),"max",max(vals))
for key in [(None,"ATSH7E0"),(None,"ATSH7E2")]:
    vals=spacings[key]
    print("ARRIVAL_SPACING",key[1],"n",len(vals),"med",q(vals,.5),"p95",q(vals,.95),"max",max(vals))

# Count status per command for last 20 seconds.
tail = [r for r in raw if (iso(raw[-1]["wall_time_iso"])-iso(r["wall_time_iso"])).total_seconds() <= 20]
print("tail20_status_by_command")
for key, rows in sorted(defaultdict(list, {k:[r for r in tail if (r.get('tx_header'),r['command_sent'])==k] for k in set((r.get('tx_header'),r['command_sent']) for r in tail)}).items(), key=lambda kv:str(kv[0])):
    print(key, len(rows), dict(Counter(r["status"] for r in rows)))

nodata = [r for r in raw if r["status"] == "NO_DATA"]
if nodata:
    print("nodata_first", nodata[0]["wall_time_iso"], "nodata_last", nodata[-1]["wall_time_iso"],
          "span_s", (iso(nodata[-1]["wall_time_iso"])-iso(nodata[0]["wall_time_iso"])).total_seconds(),
          "before_live_stop_s", (live_stop-iso(nodata[0]["wall_time_iso"])).total_seconds())
