# RX400h Monitor — ROADMAP to Core 1.0

## Versioning rule

Major engineering milestones use `0.x.0`.

- `0.x.1`, `0.x.2`, etc. are corrections inside the same milestone.
- If a milestone's exit criteria are already met by earlier work, **skip directly to the next major milestone**.
- Version numbers describe engineering maturity, not the number of coding iterations.

---

## V0.1.10 — Validated cleanup baseline

**Status:** Historical real-vehicle-validated transition baseline; superseded as the engineering baseline by V0.2.0.

### Goal

Prove that Probe-era cleanup does not break the validated RX400h request/logging path.

### Exit criteria

- V0.1.8 request whitelist preserved.
- Obsolete Probe UI/dead code removed.
- Logger/evidence regression clean on real vehicle.
- No fast-scheduler change mixed into cleanup.

### Next

V0.2.0.

---

## V0.2.0 — Reactive Core

**Status:** Complete — 2026-08-09 (unit tests + CI signed build + phone/head-unit real-vehicle sessions passed; first natural Idle Check capture recorded). Next: V0.3.0.

### Goal

Define how vehicle truth exists inside the product.

### Required outcomes

- Lightweight typed `SignalStore`.
- Monotonic time.
- Per-signal value/timestamp/age/quality/version/source.
- Change-driven publication; no periodic full dashboard repaint as the primary mechanism.
- Consumer audit and removal of unused typed Runtime fields.
- Current three-domain dashboard semantics.
- `IDLE CHECK` conditional B-level under RPM only.
- Performance/health observability baseline.
- Bounded-memory primitives.
- Scheduler interface prepared for independent deadlines/priorities.

### Exit criterion

A renderer can be replaced without modifying decoder/signal semantics, and every typed Runtime field has a documented consumer.

---

## V0.3.0 — High-Performance Scheduler / Refresh Frontier

**Status:** In development — started 2026-08-10 (branch `v0.3.0`).

The first UI candidates (D-033…D-039) established Chinese domain cards and initial window-width reflow, but retained short-side proportional scaling and a permanent inactive Idle Check label. D-041 supersedes that layout model with component-bounded, actual-window native reflow, wrapped/reachable controls, capped ultra-wide cards, height-aware whole-page scrolling and the frozen POWER/active-only Idle Check contract. Implementation, local tests/lint/signature verification and an API 37 continuous-resize matrix are complete; GitHub Actions candidate publication remains pending.

Scheduler phase in progress (D-040): deadline/priority scheduler core with backpressure is implemented; rates still remain at V0.2.0 periods pending staged frequency tests.

### Current installable candidate — V0.3.1

V0.3.1 (D-042…D-044) combines D-041 with the user-approved three-button session flow and resilient logging. The dashboard controls become `设备` / `开始` / `结束`; one typed session owner performs connect → validate → LIVE → stop → save. Logs receive periodic durable checkpoints, interrupted-session recovery, human-readable end-time archive names and automatic publication to user-visible Downloads. This deliberately pulls a small, fixed subset of V0.4/V0.5 lifecycle/durability work forward because reliable evidence must precede the V0.3 rate ladder. It does not claim Activity-independent continuous background monitoring, complete durability closure or V0.3 performance closure.

Immediate V0.3.1 gate: local implementation, 62 deterministic tests, lint/build and fixed-signature verification are complete. API 26 installation, cold launch, three-control states, DevicePicker and portrait/landscape resize/scroll smoke are also complete. Remaining gates are an exact-commit GitHub artifact, API 27 and paired-OBD connection→LIVE→End plus public-save/interruption-recovery smoke. Only then resume transport wait/rate tuning.

### Carried from V0.2.0 (real-vehicle-only)

- Continue natural Idle Check observations to strengthen eligibility equivalence.
- Longer-session memory/CPU trend on the target head unit via replay soak and natural driving.

### Goal

Determine the practical high-value sampling frontier of RX400h + OBDLink.

### Required outcomes

- Multi-source cross-check: Hybrid Assistant APK + Dr Prius XAPK + HCI + E1 + current source are complementary; no single source blocks development (D-031).
- Deadline/priority scheduler.
- Independent fast/medium/slow request periods.
- Backpressure and skip-overdue policy; no catch-up request avalanche.
- Real acquisition Hz and signal publish Hz metrics.
- Controlled step tests through the HA operating region and, where stable, above it.
- Track median/P95/P99 latency, deadline misses, NO DATA, TIMEOUT, BUS/ISO-TP errors, CPU and GC.
- Hot-path allocation/parser review.

### Important target philosophy

- HA ~5.5 Hz effective core rate is a known stable reference point, not a ceiling.
- Explore higher rate until latency/error/CPU curves show a practical knee or extra sampling has no useful product benefit.
- Fast signals may have different rates; there is no requirement for one global “frame rate.”

### Exit criterion

Fast-signal rates are selected from measured stability/performance evidence and can run without degrading low-priority tasks or weak-device headroom.

---

## V0.4.0 — Persistent Runtime

### Goal

Make the vehicle session independent of Activity lifetime.

### Required outcomes

- ForegroundService or equivalent lifecycle-independent owner.
- Explicit runtime state machine, e.g.:
  `DISCONNECTED / CONNECTING / INITIALIZING / LIVE / DEGRADED / RECONNECTING / STOPPING`.
- Correct OBDLink reconnect/re-initialization semantics.
- Vehicle OFF vs Bluetooth failure distinction.
- Activity recreation/orientation/background does not destroy the vehicle session.

### Exit criterion

The UI can be destroyed/recreated while a valid runtime session remains correct and observable.

V0.3.1 introduces an Activity-owned typed session state to make the three controls race-safe, but this does not satisfy V0.4: the vehicle session still requires a lifecycle-independent owner before V0.4 closes.

---

## V0.5.0 — Durability / Recovery

### Goal

Make session duration and ordinary failures non-dangerous.

### Required outcomes

- Log rolling/segmentation.
- Disk-space guard.
- Crash/orphan session recovery.
- Logger degradation semantics.
- Kernel health/watchdog counters.
- Bounded buffers and no duration-proportional RAM growth.
- Fault injection for Bluetooth, adapter, vehicle OFF/READY, process restart and storage failure where practical.

### Validation philosophy

No dedicated expensive long-distance trip is required. Use daily driving + replay/virtual-clock soak + stationary fault injection.

### Exit criterion

Long-equivalent replay/fault tests and accumulated normal driving show no structural memory/logger/session failure.

V0.3.1 advances checkpointing and basic interrupted-session packaging/publication. Automatic MediaStore/legacy copies are hash-deduplicated; arbitrary SAF destinations are read-back verified but retain a documented external-write-to-receipt crash window. V0.5 still owns log rolling, disk guard, full fault matrix, SAF provisional-receipt recovery and long-equivalent durability closure.

---

## V0.6.0 — Deterministic Validation

### Goal

Make most regressions testable without the vehicle.

### Required outcomes

- `LiveSource` and `ReplaySource` feed the same core.
- Deterministic fixtures for normal and failure cases.
- Virtual monotonic clock.
- Replay-loop / accelerated soak support.
- Stable output contracts for key product signals and Idle Check.

### Exit criterion

Core changes can be regression-checked quickly from recorded evidence, including stale/time/fault behavior.

---

## V0.7.0 — Low-End Performance / Headroom

### Goal

Optimize the final core for low-frequency Cortex-A55-class hardware and ~1 GB RAM while retaining renderer headroom.

### Required outcomes

- Measure PSS, Java/native heap, CPU, GC/allocation pressure, request cost, logger cost and UI cost.
- Tune parser/buffer/allocation hot paths where metrics justify it.
- Keep Runtime dependencies minimal.
- Establish a performance budget that leaves meaningful CPU/GPU headroom for future optional skins/30 fps visual interpolation.

### Exit criterion

Core is demonstrably lightweight on target-class hardware or a representative constrained environment, with no known avoidable heavy paths.

---

## V0.8.0 — Product Core Freeze Candidate

### Goal

Perform the final product purge and freeze interfaces.

### Required outcomes

- Remove remaining Probe/research-only product code.
- Remove unused signals/states/presentation paths.
- Freeze:
  - protocol profile,
  - signal semantics,
  - freshness/quality semantics,
  - scheduler mechanism,
  - runtime state machine,
  - logger/replay schema,
  - Core → Presentation contract.
- Confirm Skin/Animation can evolve without vehicle-core changes.

### Exit criterion

A new UI/skin idea no longer requires modifications to the vehicle core.

---

## V0.9.0 — Daily-use Release Candidate

### Goal

Stop feature work and expose the freeze candidate to ordinary use.

### Allowed work

- Bug fixes.
- Performance fixes.
- Compatibility fixes.
- Tests/documentation.

### Not allowed

- Feature expansion.
- Protocol expansion without a new explicit Core version decision.

### Validation

Normal daily driving and low-cost fault/replay tests. No dedicated long-distance trip requirement.

### Exit criterion

No remaining structural core issue is found during a reasonable RC period; regressions pass; baseline docs are complete.

---

## V1.0.0 — Core Freeze

### Meaning

The RX400h vehicle core is finished as a stable product foundation.

Frozen areas:

- Bluetooth/ELM contract.
- RX400h protocol profile.
- Decoder semantics.
- Signal/freshness/quality semantics.
- Derived physics.
- Idle Check semantics.
- Scheduler mechanism.
- Runtime/reconnect semantics.
- Logger/replay contract.
- Health/performance contract.
- Core → Presentation API.

Not frozen:

- Layout.
- Fonts/colors.
- CRT or other skins.
- Animation/interpolation.
- Settings and other non-core product UX.

If a frozen semantic genuinely changes later, version it as Core 1.1/2.0 and revalidate the affected gates rather than silently changing 1.0 behavior.
