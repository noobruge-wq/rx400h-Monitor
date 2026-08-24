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

D-047 adds a presentation-only CRT Green skin over the unchanged D-041 structure. It may change palette, static frames, button skin and bounded low-cost screen treatment, but not geometry contracts, fields, controls, signal semantics or core behavior. The pre-integration prototype passed local test/lint/assemble and exact API 26 GUI checks at target-car, portrait, landscape and 4:3 sizes. The fixed skin is now integrated in the local V0.3.3 candidate; it is not a generalized theme system.

Scheduler reconstruction in progress (D-046, superseding D-040 semantics): E1 showed cadence drift, batch-blind deadlines, duplicated miss/skip meaning, excessive header churn and a transport wait budget that cannot satisfy the frozen demand. V0.3.2 keeps the seven requests and periods but moves to absolute releases, single-item header-aware replanning, conserved per-request outcomes, prompt-delimited runtime transactions and fail-closed capacity admission.

### Previous installed/evidence candidate — V0.3.2

V0.3.2/v24 reconstructs the scheduler and its normal runtime transport boundary under D-046. The scheduler profile becomes `v030_capacity_002`; the protocol profile, decoder, whitelist and target periods remain unchanged. HA/HCI is used only as clean-room feasibility evidence: it proves a strict serial six-command core loop around 159 ms and disproves treating Probe-era fixed per-command waits as protocol requirements, but it does not provide source code or trusted per-command p95 costs. Local gates and exact-commit GitHub Actions run `31635798035` pass. Two hash-audited API 27 paired-OBD same-period sessions now pass normal connection/LIVE/End/archive integrity with every scheduled completion on time; their admission state remains `UNKNOWN` until D-051 turns the recorded distributions into a versioned reproducible trusted model. Forced interrupted recovery and the End/onDestroy/finalize race matrix remain open.

### Current local implementation candidate — V0.3.3/v25

V0.3.3 is the bounded D-048 correction candidate, not a new major milestone. It adds an explicit `RECOVERING` phase and bounded metadata startup fast path (D-049), one exactly-once logical terminalization owner shared by End/onDestroy/finalize (D-050), direction-aware API 27 model `api27_sp7731e_obdlink_v030_capacity_002_p95_v1` at the unchanged periods (D-051), observational-only wall-clock adjustment evidence (D-052), and the accepted fixed CRT Green integration (D-047). D-051 uses steady header costs `7E0 → 7E2 = 65 ms/4500` and `7E2 → 7E0 = 116 ms/4498`; observed `NONE → 7E0 = 154 ms/2` and explicit engineering `NONE → 7E2 = 154 ms/0` cold bounds remain untrusted and both targets are required fail-closed. The exact empirical population is `8998 steady + 2 observed cold = 9000 total`. Only the pinned API 27 sprd/sp7731e target and a matching `OBDLink MX+` family name may select it; other contexts remain `UNKNOWN`. Its frozen 60-second replay is `ADMITTED` at projected utilization `0.906533` with zero misses/rejections. The 2026-08-18 dirty-CRT short runs remain holdout/regression evidence rather than sole training input.

The local source, Gradle identity and workflow artifact name are now V0.3.3/v25. Exact-clean implementation commit `c9ad397` has a locally verified APK (SHA-256 `2d75bd7d1bc6be8a923495e901c05ce904d58966f8e10ff3e12b2e980a33d0a1`, embedded `GIT_DIRTY=false`), 98/98 direct-JUnit results, lint/assemble/manifest/signature gates and API 26 CRT smoke at 1280x720, 360x800 and 800x360. Exact 800x600 recapture is host-blocked and is not claimed. The installed/vehicle-evidence candidate remains V0.3.2/v24. V0.3.3 does not change the protocol/decoder/SignalStore contract or any request period/phase/deadline and does not authorize the rate ladder. Promotion still requires remote exact-commit CI and API 27 connection/LIVE/End/public-save/forced-recovery evidence on that V0.3.3/v25 artifact. No push, PR, release publication or vehicle action is implied.

### Previous installable candidate — V0.3.1

V0.3.1 (D-042…D-044) combines D-041 with the user-approved three-button session flow and resilient logging. The dashboard controls become `设备` / `开始` / `结束`; one typed session owner performs connect → validate → LIVE → stop → save. Logs receive periodic durable checkpoints, interrupted-session recovery, human-readable end-time archive names and automatic publication to user-visible Downloads. This deliberately pulls a small, fixed subset of V0.4/V0.5 lifecycle/durability work forward because reliable evidence must precede the V0.3 rate ladder. It does not claim Activity-independent continuous background monitoring, complete durability closure or V0.3 performance closure.

The V0.3.1 local checks remain useful regression evidence, but it was never promoted: exact-commit GitHub publication, API 27 and paired-OBD connection→LIVE→End/public-save/recovery smoke are still pending. V0.3.2 must repeat those gates and then complete a same-period E1 before any rate-ladder step.

### Carried from V0.2.0 (real-vehicle-only)

- Continue natural Idle Check observations to strengthen eligibility equivalence.
- Longer-session memory/CPU trend on the target head unit via replay soak and natural driving.

### Goal

Determine the practical high-value sampling frontier of RX400h + OBDLink.

### Required outcomes

- Multi-source cross-check: Hybrid Assistant APK + Dr Prius XAPK + HCI + E1 + current source are complementary; no single source blocks development (D-031).
- Epoch-anchored, capacity-aware single-transaction scheduler with replanning after each header/request transaction.
- Independent fast/medium/slow request periods.
- Conserved per-request outcomes, deterministic capacity shedding and no catch-up request avalanche.
- Real acquisition Hz and signal publish Hz metrics.
- Controlled step tests through the HA operating region and, where stable, above it.
- Track queue/setup/service/interval/lateness distributions; on-time, late, capacity-rejected, expired, transport-unavailable and session-ended outcomes; NO DATA, TIMEOUT, BUS/ISO-TP errors, CPU and GC.
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
