# RX400h Monitor — ROADMAP to Core 1.0

## 2026-09-26：按用户要求暂时结束

当前D064停止主动推进；先完成源码远端归档和换电脑备份提示，入口 `PROJECT_ARCHIVE_20260926.md`。真实蓝牙/音乐/唤醒限制保持未验证，不为收束而虚假关闭。再次开发须由用户发起，不恢复已取消的5Hz/日志/稳健策略。

## Active closure — D-064 / 2026-09-23

Local implementation and available local checks completed; see `D064_LOCAL_REVIEW_20260923.md`. Exact V0.3.5/v29 source/APKs are packaged locally. Next is optional natural-use acceptance of actual adapter lifecycle and music coexistence. No product telemetry to export, no restoration of cancelled modules, no claim that emulator proves hardware sleep or full connection-cancellation coverage.

Implement `D064_REALTIME_CLOSURE.md` before another release candidate. User cancels speed-frontier/steady strategy and product logging; only real-time instruments/local settings remain. 3Hz target is not a guarantee of actual Bluetooth throughput. Old 5Hz, recovery, exported-log and strategy-switch gates no longer apply. Local correctness, lifecycle/GUI, build/signature and lean consumer audit remain required; hardware music coexistence/real sleep cannot be proven by emulator. Torque boundary meaning remains unresolved, not silently corrected.

## Local lean/runtime checkpoint — 2026-09-17 / D-063

Authorized continuation completed locally, not a car/release promotion. Report `D063_LOCAL_REVIEW_20260917.md`, package `../../deliverables/D063-lean-runtime-local-20260917/`. Additional wrapper/log/scheduler simplification and benchmark shrinker verified; main9,044lines, benchmark6,446,609bytes. Correctness/local GUI gates pass with standardGradle host failure separate. Steady-search microbenchmark is not HA/whole-app/car improvement. Next safe work remains bounded consumer/performance review and verified MX+ capability/format research before any wire activation; no obligatory special drive. Full R8 LIVE save/export, actual acquisition5Hz/CPU/soak and previous wheel/torque gates are open. Earlier next-step paragraphs are historical; the user chose continued local optimization before immediate car handoff.

## Local cleanup checkpoint — 2026-09-17 / D-062

Completed the approved behavior-preserving cleanup locally; `D062_LOCAL_REVIEW_20260917.md` and `../../deliverables/D062-lean-local-20260917/`. Main source−15.1%, car-test APK−13.9%, local correctness gates pass with standard Gradle worker limitation separately recorded. UI-only CPU effectively flat, not new real-car evidence. Next safe step is natural-use exact-artifact comparison, not adding infrastructure or removing useful logging blindly. Preserve two strategies and authored UI. 5Hz, torque correctness, MX+ batching and LIVE end-to-end vehicle gates remain open.

## Local candidate checkpoint — 2026-09-12 / D-061

A+B authorized: first same-input exact-pixel CPU reduction and bounded transport/parse work; then measured MX+ wire-mode experiments only when capability/response-format gates are satisfied. `D061_AB_PLAN.md` controls scope. Keep two user strategy choices and the 5Hz measured goal. No 80% emulator equivalence, GPU rewrite, logging architecture migration or real-vehicle promotion implied.

Implemented/local evidence: `D061_LOCAL_REVIEW_20260912.md`. Same-debug UI control repeats show about31% CPU reduction with independent sharp+halo parity; bounded blocking input and parser reuse tested, 13,465 recorded transactions consistent. Non-debuggable test candidate retains diagnostics. Next gate is actual MX+ receive/cancel/reconnect behavior and real request frequency; STPX/STBC support/format gates remain open, no enabled batches. Wheel direction, torque boundary, latest Idle replay, live handoff and long-run memory are separate work; don't mark V0.3.5 or real5Hz complete.

## Current local implementation checkpoint — 2026-09-08 / D-060

See `D060_REQUIREMENTS_CLOSURE.md` and `D060_LOCAL_REVIEW_20260908.md`. The nine-group implementation is now in the local dirty tree: two strategies only, default HA; automatic confirmed session handoff; bounded private unsaved discard; Idle/source freshness/publication; exact authored pixel UI. Local build, unit, file-fault and same-content renderer gates pass. Next unresolved gates: LIVE transport handoff end-to-end, latest raw-transaction Idle replay, matched multi-version whole-workload emulator calibration/holdout and longer memory validation. 1-core/actual1.5GB coverage is not an80%-equivalent car. Do not add self-developed/four-mode settings, request scans or demand a special drive to fill a local-validation gap. No release/push/car promotion.

## Historical local validation checkpoint — 2026-09-06

D-058 local runtime now works. Parked/wheel/recorded captures and three stress5 repeats completed with no input skips; settings restore and Idle/self-test samples are verified. Same-AVD exact old APK control exposes a remaining R1 halo-pixel difference, so full visual equivalence is NOT passed. Next: localize/fix that presentation difference in a separately scoped source change, then perform trusted same-input old-renderer dynamic A/B and weak-device calibration before deciding R2/target sampling. Do not demand another car run to fill this local gap or claim 80% calibration/OBD 5 Hz. Detailed results: `../../outputs/d058-runtime-20260906/D058_RUNTIME_REPORT.md`. No core/scheduler change in this measurement follow-up.

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

D-053 adds the V0.3.4/v26 presentation correction after target-head-unit feedback. Exact-clean local implementation/artifact `b60619d` and source/test/lint/assemble/signature checks are complete: it prioritizes the API 27 1280x720 natural first frame, removes the debug-only forced-layout evidence bias, and raises daylight CRT visibility with static scanlines, a brighter green hierarchy and selective bold primary text. The user subsequently accepted the real target first frame, notification-shade geometry stability and CRT visibility; only remote CI remains open. Phone-portrait optimization is deferred. Vehicle-core, scheduler, request-period and signal contracts remain frozen.

D-054 authorizes V0.3.5/v27 as the current local presentation candidate. It translates the user's editable 1280x720 layout and three reference pictures into native, signal-driven BAT/VEH/PWR schematics without shipping the pictures or changing acquisition: SOC/whole-pack temperature, direction-neutral speed motion, explicit coolant/12 V indicators and a truthful bidirectional HV-power scale. A low-brightness inactive/unknown Idle Check frame becomes 100% bright only when the existing core state is active. Interpolation is capped at 20 fps and stops when detached/background/stale/stationary; no new dependency or core change is included.

D-055 is a user-authorized V0.3.5 lifecycle correction: a failed startup recovery, archive build, public copy or receipt write remains a visible, retryable evidence obligation but cannot permanently block live monitoring. Once prior I/O and vehicle/session ownership are safely released, `SAVE_FAILED` enables device selection and Start while End continues to retry the old evidence. A new session uses a new directory and never deletes or marks the old run successful; recovery remains prohibited during LIVE. Protocol, scheduler, periods, decoding and evidence-integrity rules do not change.

D-056 advances the local V0.3.5 candidate to v28 and supersedes v27's approximate/native-redraw presentation. The final user-authored pixel JSON is now exact: no coordinate normalization, a 640×360 logical raster is scaled nearest-neighbour to 1280×720, fixed Chinese uses pre-rasterized Noto Sans SC physical-pixel sprites, and the supplied top/side vehicle masks plus independent wheel layer ship directly. Signal-driven SOC, wheel and power animations follow the confirmed self-test/cursor rules. A same-style settings overlay locally persists scanline and bounded halo parameters. D-055 remains intact; core, scheduler, periods, protocol and decoder remain frozen.

The first UI candidates (D-033…D-039) established Chinese domain cards and initial window-width reflow, but retained short-side proportional scaling and a permanent inactive Idle Check label. D-041 supersedes that layout model with component-bounded, actual-window native reflow, wrapped/reachable controls, capped ultra-wide cards, height-aware whole-page scrolling and the frozen POWER/active-only Idle Check contract. Implementation, local tests/lint/signature verification and an API 37 continuous-resize matrix are complete; GitHub Actions candidate publication remains pending.

D-047 adds a presentation-only CRT Green skin over the unchanged D-041 structure. It may change palette, static frames, button skin and bounded low-cost screen treatment, but not geometry contracts, fields, controls, signal semantics or core behavior. The pre-integration prototype passed local test/lint/assemble and exact API 26 GUI checks at target-car, portrait, landscape and 4:3 sizes. The fixed skin is now integrated in the local V0.3.3 candidate; it is not a generalized theme system.

Scheduler reconstruction in progress (D-046, superseding D-040 semantics): E1 showed cadence drift, batch-blind deadlines, duplicated miss/skip meaning, excessive header churn and a transport wait budget that cannot satisfy the frozen demand. V0.3.2 keeps the seven requests and periods but moves to absolute releases, single-item header-aware replanning, conserved per-request outcomes, prompt-delimited runtime transactions and fail-closed capacity admission.

### Previous scheduler evidence candidate — V0.3.2

V0.3.2/v24 reconstructs the scheduler and its normal runtime transport boundary under D-046. The scheduler profile becomes `v030_capacity_002`; the protocol profile, decoder, whitelist and target periods remain unchanged. HA/HCI is used only as clean-room feasibility evidence: it proves a strict serial six-command core loop around 159 ms and disproves treating Probe-era fixed per-command waits as protocol requirements, but it does not provide source code or trusted per-command p95 costs. Local gates and exact-commit GitHub Actions run `31635798035` pass. Two hash-audited API 27 paired-OBD same-period sessions now pass normal connection/LIVE/End/archive integrity with every scheduled completion on time; their admission state remains `UNKNOWN` until D-051 turns the recorded distributions into a versioned reproducible trusted model. Forced interrupted recovery and the End/onDestroy/finalize race matrix remain open.

### Current installed/vehicle-evidence candidate — V0.3.3/v25

V0.3.3 is the bounded D-048 correction candidate, not a new major milestone. It adds an explicit `RECOVERING` phase and bounded metadata startup fast path (D-049), one exactly-once logical terminalization owner shared by End/onDestroy/finalize (D-050), direction-aware API 27 model `api27_sp7731e_obdlink_v030_capacity_002_p95_v1` at the unchanged periods (D-051), observational-only wall-clock adjustment evidence (D-052), and the accepted fixed CRT Green integration (D-047). D-051 uses steady header costs `7E0 → 7E2 = 65 ms/4500` and `7E2 → 7E0 = 116 ms/4498`; observed `NONE → 7E0 = 154 ms/2` and explicit engineering `NONE → 7E2 = 154 ms/0` cold bounds remain untrusted and both targets are required fail-closed. The exact empirical population is `8998 steady + 2 observed cold = 9000 total`. Only the pinned API 27 sprd/sp7731e target and a matching `OBDLink MX+` family name may select it; other contexts remain `UNKNOWN`. Its frozen 60-second replay is `ADMITTED` at projected utilization `0.906533` with zero misses/rejections. The 2026-08-18 dirty-CRT short runs remain holdout/regression evidence rather than sole training input.

Exact-clean implementation commit `c9ad397` has a locally verified APK (SHA-256 `2d75bd7d1bc6be8a923495e901c05ce904d58966f8e10ff3e12b2e980a33d0a1`, embedded `GIT_DIRTY=false`), 98/98 direct-JUnit results, lint/assemble/manifest/signature gates and API 26 CRT smoke at 1280x720, 360x800 and 800x360. Exact 800x600 recapture is host-blocked and is not claimed. That exact APK was installed on the API 27 target; the two 2026-08-26 completed `USER_END` archives (ZIP SHA-256 `c917e183…5201` and `5364e69e…ecda`) provide normal start/LIVE/End/public-save evidence, including one pristine `4006/4006` on-time run. V0.3.3 does not change the protocol/decoder/SignalStore contract or any request period/phase/deadline and does not authorize the rate ladder. Promotion still requires remote exact-commit CI and forced interrupted recovery evidence. No push, PR or release publication is implied.

### Current local implementation candidate — V0.3.4/v26

V0.3.4 is the completed presentation-only D-053 correction. It adds the complete real-window layout identity and a first-attach stabilizer capped at two suppressed pre-draw retries, removes the debug preview's private three-pass workaround, brightens the phosphor palette, bolds primary values/titles/buttons and makes static scanlines visible without animation or draw-time allocation. Exact-clean commit `b60619d` produced the 2,591,078-byte APK with SHA-256 `fa88064be3450ccb8765217ed0f12e97e01793c66434b07b0901777b4072002b` and embedded `GIT_DIRTY=false`; production/test compilation, 99/99 direct JUnit, lint 0 errors / 9 existing warnings, assemble and v2 signature checks pass. The user accepted the actual API 27/1280x720 first-frame/daylight gate; remote CI remains pending. No core/scheduler/protocol/decoder/SignalStore/logger/session behavior changed.

### Previous local implementation candidate — V0.3.5/v27

V0.3.5 combines the D-054 presentation candidate with the narrow D-055 recovery-control correction. The user's browser-edited 1280x720 composition guides the target layout, but its approximate manual offsets are normalized into exact common baselines, equal gutters and centered groups. Three imported pictures are reference-only and are recreated as native `Canvas` schematics driven by existing `DashboardSnapshot` values. BAT visualizes SOC and whole-pack temperature range, VEH visualizes direction-neutral speed motion plus explicit coolant/12 V/engine state, and PWR visualizes fresh HV battery power on a truthful bidirectional scale. Numeric data remains authoritative and stale/null never becomes fake zero. Idle Check changes from hidden-when-inactive to a low-brightness framed inactive/unknown state and reaches full brightness only when the unchanged core state is active. Failed evidence remains intact and retryable without locking out a later live session once ownership is safely released. Local implementation and verification are complete: 113/113 direct JUnit, compile, lint (0 errors / 9 warnings), assemble and fixed-v2 signature checks pass. The dirty-worktree APK is 2,612,662 bytes with SHA-256 `0b803f3cf3759d96e350e6ea4f18146ae263c1250cfa557780d8011a84bd533e`; full review is in `CODE_REVIEW_V0.3.5.md`. Target API 27 GUI/paired-OBD/forced-recovery smoke remains open; no push/install/vehicle action is included.

### Current authorized implementation candidate — V0.3.5/v28

V0.3.5/v28 replaces only v27's rejected approximate presentation under D-056. `RX400h-UI-layout-v035-pixel-v2 (3).json` is the exact layer/coordinate/alpha contract, including the user's final side/top vehicle art and independent wheel sprite. The app renders a 640×360 logical canvas at nearest-neighbour 2× scale, overlays fixed Noto Sans SC physical-pixel labels and applies locally adjustable bounded halo plus final-pass scanlines. SOC, wheel and power visuals use existing `DashboardSnapshot` values and the confirmed self-test/cursor behavior; Idle Check remains 10%/100%. D-055 remains unchanged. Local compile, 111/111 direct JUnit, lint (0 errors / 9 warnings), assemble, manifest/resource/hash and fixed-v2 signature checks pass. The 2,656,023-byte dirty-worktree APK has SHA-256 `020a0d9fb05e718e0eef6e100edbdd306dcba244f7b4017b67e2967f048d30f6`; full review is `CODE_REVIEW_V0.3.5_V28.md`. The 2026-09-01 API 26 retry cleared the stale lock, bypassed original userdata and created an isolated 1280×720 AVD, but both software and valid hardware acceleration stopped before Android boot; this host's Emulator/QEMU/WHPX runtime remains the blocker and no GUI pass is claimed. Target GUI/paired-OBD/forced-recovery smoke remains open. No APK install, push or publication is authorized.

D-057 is locally complete as presentation evidence but failed the target moving-performance gate. Its exact APK records `89.10%` weighted process CPU while moving versus old-v28 approximately `72%`, despite improving parked/ICE-off CPU to `17.17%`; transport, evidence integrity and memory remain healthy. The second full-page software composition is therefore not a foundation for HA/5 Hz.

D-058-R1 is locally implemented and built: it removes the second 1280×720 software halo cache, renders the unchanged eight-direction halo on the View canvas, suppresses wheel-only scheduling while settings hides the wheel and retains D-057's no-op/scanline improvements. Production/test compilation, 112/112 direct JUnit, lint and assemble/signature pass; APK SHA-256 is `33021cb6…e7fafa`. Exact pixels and all vehicle/core contracts remain frozen. The host emulator currently lacks its hypervisor driver and software fallback did not reach ADB, so visual/performance promotion remains open. Only if the target run preserves visuals but moving CPU remains above the recommended `<55%` gate should R2 introduce static-page plus preallocated dynamic regions grouped by halo-expanded transitive overlap. Idle Check hysteresis and HA scheduling remain separate later work.

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
