# RX400h Monitor — DECISIONS

This file is the durable decision log. New architectural or protocol decisions must be added here before implementation.

Format:

```text
D-XXX — Title
Status: Accepted / Superseded / Rejected / Experimental
Decision
Reason
Consequences
Evidence / trigger
```

---

## D-001 — RX400h-only product scope

**Status:** Accepted

**Decision:** Build a dedicated Lexus RX400h monitor, not a generic Toyota diagnostic platform.

**Reason:** Generic abstraction adds code, dependencies, allocations, testing scope and UI complexity without serving the current product.

**Consequences:** Fixed request tables, typed models and RX400h-specific decoders are preferred to generalized plugin/vehicle-definition frameworks.

---

## D-002 — Native Kotlin + Android View

**Status:** Accepted

**Decision:** Use native Android/Kotlin and lightweight Views for the core product.

**Reason:** Weak head-unit target, low CPU/RAM goals, offline operation, and a small fixed UI.

**Consequences:** Avoid Compose, WebView and large chart/UI frameworks unless later evidence shows a compelling benefit.

---

## D-003 — Evidence-first protocol changes

**Status:** Accepted

**Decision:** Vehicle requests and field semantics require evidence. No blind Header/DID/session expansion.

**Consequences:** Runtime is whitelist-only. Unknown semantics remain Unknown/Unavailable. Raw evidence is preserved.

---

## D-004 — V0.1.9 is permanently void as a code baseline

**Status:** Accepted

**Decision:** All subsequent product work derives from V0.1.8/V0.1.10 valid history, never from V0.1.9.

**Reason:** Later evidence invalidated important V0.1.9 scheduling choices.

---

## D-005 — Current runtime request whitelist

**Status:** Accepted

**Decision:** Preserve the current request set until new direct evidence justifies a change.

```text
7E0: 01040C0D0E10 2
7E0: 01050607 1
7E0: 21CDF3 3
7E2: 21C3 6
7E2: 21C4 5
7E2: 21CF 4
ATRV
```

Do not reintroduce `22xxxx`, `2C`, `10 02`, `10 03`, 7E1/7E3/7E4 scanning or arbitrary input without a new explicit evidence decision.

---

## D-006 — Raw evidence is authoritative; typed Runtime is minimal

**Status:** Accepted

**Decision:** `raw_io` (or future equivalent) preserves vehicle evidence. Typed Runtime fields exist only when consumed by product display, derived calculations, required state, connection/health logic or validation.

**Consequences:** Consumer audit may remove typed fields even when the bytes remain present in a polled block.

---

## D-007 — Three-domain primary UI contract

**Status:** Accepted

**Decision:** Default product UI is organized as:

```text
BATTERY
  SOC (A)
  AVG TEMP (A)
  MAX / MIN (B)

VEHICLE STATUS
  SPEED (A)
  COOLANT (A)
  12V OBD (A)

POWER
  ICE POWER (A)
  ENGINE RPM (A)
    IDLE CHECK (B, conditional)
  HV BATTERY POWER (A)
```

**Consequences:** No duplicate HV-power display. MG1/MG2/MGR are not required on the default dashboard merely because they are decodable.

---

## D-008 — Idle Check is the only HSD state intended for the default UI

**Status:** Accepted

**Decision:** Do not display S0/S1/S2/S3/S4 names. Show `IDLE CHECK` only when the runtime is truly in Idle Check.

**Reason:** Other warmup stages are not useful enough for the product dashboard.

**Consequences:** V0.2.0 may minimize internal HSD state to an equivalent IdleCheck eligibility machine only if replay proves equivalence.

---

## D-009 — Acquisition, signal publication and animation frame rate are independent

**Status:** Accepted

**Decision:** Separate:

```text
vehicle acquisition rate
signal change rate
UI publish rate
renderer/animation FPS
```

**Consequences:** Unchanged 12V/temperature values do not repaint unnecessarily. RPM/power may update several times per second. Future skins may render at 30 fps using interpolation without fabricating logged vehicle values.

---

## D-010 — High refresh rate is a first-class project goal

**Status:** Accepted

**Decision:** The project will explore the practical RX400h + OBDLink performance frontier, not simply settle at ~2 Hz.

**Reason:** HCI proves HA operates around 5.5 Hz effective core rate and ~6.28 Hz raw core-loop median; this is an observed operating point, not a proven ECU maximum.

**Consequences:** V0.3.0 will use staged frequency tests and latency/error/CPU knee-point analysis, including exploration above HA where safe and useful.

---

## D-011 — Deadline scheduler instead of whole-frame fixed cadence

**Status:** Accepted

**Decision:** Requests are independently scheduled by target period, priority, deadline and lateness.

**Consequences:** Fast signals can outpace slow signals; overdue low-priority work must not create catch-up request storms.

---

## D-012 — Lean Core over generalized architecture

**Status:** Accepted

**Decision:** Prefer fixed typed structures, fixed request tables and simple single-writer state over generic registries, plugin systems, `Map<String, Any>` stores or unnecessary middleware.

**Reason:** N is tiny and fixed; simpler code can be faster, smaller, easier to audit and easier to prove correct.

**Consequences:** Optimize allocations/GC and hot-path string parsing where metrics justify it. Avoid object-heavy reactive stacks merely for architectural fashion.

---

## D-013 — Skin/animation is outside the vehicle core

**Status:** Accepted

**Decision:** Core 1.0 exposes a stable presentation contract. Skin/animation is future optional product work.

**Consequences:** A skin may choose CRT/modern/aviation/animation/blur presentation, but may not access Bluetooth directly, send requests, change signal semantics or become a source of logged vehicle truth.

---

## D-014 — No expensive dedicated long-drive release gate

**Status:** Accepted

**Decision:** Do not require special long-distance trips solely for project validation.

**Reason:** Real-vehicle test cost is disproportionate and most long-run software failures can be tested without consuming fuel/travel cost.

**Consequences:** Use normal daily driving for real ECU/Bluetooth evidence, and replay/virtual-clock/fault-injection soak for memory, logger, scheduler, stale/deadline and UI stress.

---

## D-015 — Performance observability must precede aggressive optimization

**Status:** Accepted

**Decision:** Build PSS/heap/CPU/GC/request/render/logger/health telemetry before pushing the final scheduler frontier.

**Consequences:** Frequency and micro-optimization decisions are data-driven; the monitoring system itself must use bounded memory and low overhead.

---

## D-016 — Long-lived memory must be bounded

**Status:** Accepted

**Decision:** Runtime memory use should be effectively independent of session duration.

**Consequences:** Historical data streams to disk; in-memory history uses fixed windows/ring buffers; no unbounded lists/StringBuilders/event collections.

---

## D-017 — Documentation is the primary project memory

**Status:** Accepted — 2026-08-08

**Decision:** Every development session updates baseline documents before code. Every major milestone closes with a new project baseline.

Required durable documents:

- `PROJECT_STATE.md`
- `CHANGELOG.md`
- `DECISIONS.md`
- `ROADMAP.md`
- `DEVELOPMENT_PROTOCOL.md`
- `EVIDENCE_INDEX.md`

**Reason:** Project continuity must not depend on a single long AI conversation.

**Consequences:** A future chat should resume from these files + latest source in minutes. Chat history is supplementary evidence, not the canonical state store.


---

## D-018 — Codex handoff becomes a first-class repository contract

**Status:** Accepted — 2026-08-08

**Decision:** Add `AGENTS.md` + `CODEX_HANDOFF.md` so Codex can recover project rules and state directly from the repository/migration package.

**Reason:** ChatGPT and Codex conversation histories are not a reliable shared project database.

**Consequences:** New Codex sessions must first read the handoff/baseline and provide a recovery report before source changes.

---

## D-019 — GitHub writes use local Git/gh, not ChatGPT connector writes

**Status:** Accepted — 2026-08-08

**Decision:** Treat local `git` + GitHub CLI/browser OAuth as the canonical write path for Codex.

**Reason:** Connected GitHub reading works, while direct ChatGPT contents write returned GitHub 403 `Resource not accessible by integration`.

**Consequences:** Do not store PATs in the repository. Migration automation may initiate `gh auth login --web`, but first-time authorization remains a user-consent boundary.

---

## D-020 — Migration package must be self-contained enough for offline recovery

**Status:** Accepted — 2026-08-08

**Decision:** The full Codex transfer package contains canonical state documents, current source snapshot, key E1 logs, HCI evidence, reconstruction/reference documents, historical valid/void source archives, UI references, access/bootstrap scripts and integrity manifests.

**Reason:** Repository access or conversation availability should not be a single point of failure.

**Consequences:** Large binary evidence is archival/supporting material; Codex should read the compact canonical documents first and open large artifacts only when needed.

---

## D-021 — V0.2.0 typed SignalStore and stable presentation contract

**Status:** Accepted — 2026-08-08

**Decision:** V0.2.0 introduces a lightweight typed `SignalStore` where each signal carries value, source timestamp, update timestamp, age, quality, version and source, using monotonic time for scheduling/freshness/state timers. A separate presentation contract carries only what the renderer needs.

**Reason:** Probe-era code mixes acquisition, state, logging and rendering in `MainActivity`; V0.2.0 must make the presentation layer replaceable without changing vehicle truth.

**Consequences:** Decoder/SignalStore semantics are the frozen boundary for renderer replacement; UI/skin code never writes vehicle truth or raw logs.

---

## D-022 — Change-driven UI publication

**Status:** Accepted — 2026-08-08

**Decision:** The dashboard no longer repaints all fields on a fixed 500 ms timer. Updates are published on signal change; the renderer compares per-field versions and updates only changed Views.

**Reason:** Unchanged low-frequency values must not trigger string formatting or setText work; this is the main UI cost in V0.1.10.

**Consequences:** Fast signals may publish several times per second; unchanged values do not repaint; the 500 ms stale-refresh timer may remain only for stale marking and status line refresh.

---

## D-023 — Three-domain product UI replaces Probe-era dashboard cards

**Status:** Accepted — 2026-08-08

**Decision:** Default UI is BATTERY (SOC, AVG temp, MAX/MIN), VEHICLE STATUS (speed, coolant, 12V OBD) and POWER (ICE power, RPM, conditional IDLE CHECK, HV power). WARMUP text, ENGINE STATE text and MG1/MG2/MGR power lines are removed from the default dashboard.

**Reason:** The documented product contract has no consumer for those display items; MG1/MG2/MGR must not enter the default UI merely because they are decodable.

**Consequences:** Raw evidence remains in `raw_io.jsonl`; removed display items can return only through a new evidence/decision.

---

## D-024 — Minimal IdleCheckEligibilityState (experimental until replay-validated)

**Status:** Experimental → partially validated by natural E1 — 2026-08-09

**Decision:** V0.2.0 implements a minimal eligibility state: warmup active, 900 < RPM < 1100, ICE mechanical power ~0 kW (tolerance 0.05 kW for floating-point safety), speed <= 55 km/h, stable for ~1 s. State transitions are written to the session log. `IDLE CHECK` is displayed only while the state is active; otherwise the position is blank.

**Reason:** Current V0.1.10 code contains no S0–S4 state machine, so there is no full reference implementation to delete; the recovered candidate conditions are the best available evidence.

**Consequences:** This state remains experimental until deterministic replay against E1 logs and more natural real-vehicle Idle Check observations confirm equivalence. The `RX400h_20260808_234255` phone session already captured a natural activation (4 frames at RPM 901.5–903, speed 9–13 km/h, ICE power 0 kW, warmup true), matching the candidate conditions. Insufficient evidence means the field stays blank rather than guessing.

---

## D-025 — V0.2.0 consumer audit removes no-consumer typed fields

**Status:** Accepted — 2026-08-08

**Decision:** Remove from typed Runtime/decoded summaries: engine load, MAF, ignition timing, injection, MG1/MG2/MGR, rear MG and brake candidates, unless a consumer is identified during the audit. Raw responses stay in `raw_io.jsonl`. The unreferenced probe profile JSON assets containing banned 22/2C requests are deleted from the repo; they remain recoverable from git history and the migration package.

**Reason:** Lean Core requires every typed field to have an explicit consumer; logger-only fields are not product consumers.

**Consequences:** `frames.csv` schema is updated in the same version; historical evidence archives remain unchanged and can be re-parsed later.

---

## D-026 — Performance observability baseline in V0.2.0

**Status:** Accepted — 2026-08-08

**Decision:** Add a constant-space streaming `performance.csv` (or equivalent) with PSS, Java heap, process CPU time delta, GC/allocation counters where available, scheduler cycle duration, UI render duration and logger/request latency indicators, sampled about every 5 s.

**Reason:** V0.3.0 frequency work must be data-driven; V0.2.0 must establish the baseline before any rate increase.

**Consequences:** Telemetry itself is bounded and low-overhead; no session-duration-proportional RAM growth.

---

## D-027 — Scheduler interface prepared without changing rates

**Status:** Accepted — 2026-08-08

**Decision:** Introduce a fixed request table describing header/command/target period/priority/timeouts so V0.3.0 can implement deadline scheduling; V0.2.0 keeps current polling periods unchanged.

**Reason:** Uncontrolled frequency increase before observability exists is explicitly forbidden by the project.

**Consequences:** The live loop behavior stays equivalent to V0.1.10 in this release; only structure changes.

---

## D-028 — Unit test gate in CI

**Status:** Accepted — 2026-08-08

**Decision:** Add parser/SignalStore/Idle Check unit tests and run `:app:testDebugUnitTest` before `:app:assembleDebug` in GitHub Actions.

**Reason:** Most regressions should be testable without the vehicle; CI must catch parser/state regressions early.

**Consequences:** JUnit becomes a test-only dependency; runtime dependencies remain unchanged.

---

## D-029 — Standard-block decoder must skip unknown PIDs, not abort

**Status:** Accepted — 2026-08-08

**Decision:** `decodeStandard` keeps the full PID size table for the standard OBD block so PIDs with no typed consumer (e.g. `04`, `0E`, `10`) are skipped, and parsing continues to later PIDs such as `0C` (RPM) and `0D` (speed).

**Reason:** V0.2.0 consumer audit removed the typed fields but also removed their sizes from the parser, causing the loop to break on the first skipped PID and lose RPM/speed/ICE power in the `RX400h_20260808_043828` real-vehicle session.

**Consequences:** Decoder version bumped to `rx400h-reactive-20260808-002`; regression test added.

---

## D-030 — V0.2.0 closure and audit results

**Status:** Accepted — 2026-08-09

**Decision:** V0.2.0 closes as the current engineering baseline. Closure audits completed: dead-code (removed unused `SignalStore.revision`), consumer traceability, dependency, duplicate-state/cache, and hot-path allocation review (optimization deferred to V0.3.0 with measurements). Real-vehicle-only items move to V0.3.0.

**Reason:** Exit gate is met, CI signed build passes, and two natural real-vehicle sessions (phone + target head unit) ran with 0 errors and all signals present; first natural Idle Check activation was captured.

**Consequences:** V0.2.0 is tagged `v0.2.0`; V0.1.10 becomes the historical real-vehicle-validated baseline; V0.3.0 owns further Idle Check observations, long-session memory trends and high-refresh ladder testing.

---

## D-031 — V0.3.0 multi-source cross-check policy

**Status:** Accepted — 2026-08-09

**Decision:** V0.3.0 development uses Hybrid Assistant APK, Dr Prius XAPK, Bluetooth HCI/RFCOMM capture, E1 real-vehicle logs and current source as complementary evidence. No single source blocks development; conflicting sources are resolved through `DECISIONS.md` entries rather than silent preference.

**Reason:** Avoids development blockage from a single reverse-engineering source and prevents unverified semantics from entering the runtime model.

**Consequences:** HA APK (`563a8b08…`) and Dr Prius XAPK (`7246dab1…`) hashes are re-verified and recorded as V0.3.0 cross-check material; any new protocol/field claim must cite at least one independent corroborating source or be marked hypothesis.

---

## D-032 — HA/DP are reference-only; keep the implementation minimal

**Status:** Accepted — 2026-08-09

**Decision:** Hybrid Assistant and Dr Prius code/APK/resources are reference evidence only. Do not copy their implementation wholesale. Before new development requirements are added, keep the RX400h Monitor implementation as simple and efficient as possible.

**Reason:** The project exists to build a lighter, RX400h-specific monitor, not a HA clone or a generalized platform. Copying third-party structure would reintroduce the complexity, dependencies and UI weight the project intentionally avoids.

**Consequences:** New code is added only when a concrete consumer/requirement exists; reference apps are used for interoperability facts and cross-checking, not as an implementation template. Lean Core gates (dead-code/consumer/allocation/dependency/duplicate-state audits) remain mandatory.

---

## D-033 — V0.3.0 header UI: single-row wide layout

**Status:** Superseded by D-035 — 2026-08-10

**Decision:** On wide screens the dashboard header is one row: title left, four control buttons centered, status right, per the user's head-unit mockup. Narrow screens keep the existing two-row fallback (title+status row, button row below) so the buttons cannot overflow.

**Reason:** The target head-unit screen sits far from the driver; the user requested larger fonts and buttons plus the four buttons moved to the middle of the top bar. The mockup places the buttons inside the top bar, not on a separate row.

**Consequences:** Layout-only change on branch `v0.3.0`; no protocol, scheduler, signal or presentation-contract changes. Narrow layouts retain the horizontal-scroll button row as a fallback.

---

## D-034 — V0.3.0 header v2 and Chinese display contract

**Status:** Partially superseded by D-041 — 2026-08-10

**Decision:** The header uses two-line fixed-size text on both sides (title: `RX400h` / `MONITOR`; status: device name on line 1, 蓝牙连接/协议/测试数据 state line on line 2), buttons centered between them; text always reserves its space and buttons shrink only if the header would overflow. All three dashboard domains use the same value font size and color except battery MAX/MIN, which stays B-level small text. All labels/statuses are Chinese except card titles and number units. POWER order is 混动功率 → 引擎功率 → 转速 → 怠速检查; 怠速检查 is permanent near-background gray and turns the active value color only while Idle Check is active.

**Reason:** The user's text specification overrides the earlier vision-model description of the mockup. The goal is readable fixed-size text on the distant head-unit screen, with touch targets that never crowd the text zones.

**Consequences:** Layout/presentation-only change on branch `v0.3.0`; no protocol, scheduler, signal or presentation-contract changes.

---

## D-035 — V0.3.0 header v3: text-first layout, Chinese domain cards, doubled fonts

**Status:** Partially superseded by D-041 — 2026-08-10

**Decision:** Header layout is text-first: line 1/2 are the two-line title (`RX400h` / `MONITOR`) on the left and a widened status column on the right (device name line + 蓝牙/协议/数据 lines, allowed to stack vertically or use short words); the four buttons sit in their own full-width row below and are narrower and taller. Buttons are squeezed by text, never the reverse; when horizontal width is insufficient the layout changes (stacked text or scrollable button row). Domain cards get Chinese centered titles at the top of each frame (能量域 / 车辆域 / 动力域), every value has a centered label line followed by a centered value line, and all three-domain text is doubled from the previous build (labels/values 40sp, titles 28sp) except battery MAX/MIN (26sp, dim) and the permanent gray 怠速检查, which turns the active value color only during Idle Check.

**Reason:** User's v3 text spec overrides the earlier mockup interpretation; buttons were still too wide and the previous single-line status squeezed the text zones. The distant head-unit screen needs the largest readable text and priority to text over buttons.

**Consequences:** Supersedes D-033's "buttons inside the header row" decision and refines D-034. Layout/presentation-only change on branch `v0.3.0`; no protocol, scheduler, signal or presentation-contract changes.

---

## D-036 — V0.3.0 header v5: buttons back inside the header row

**Status:** Accepted — 2026-08-10

**Decision:** On wide screens the four buttons return inside the header row, between the two-line title and the widened multi-line status column, so no separate button row wastes vertical space. Narrow screens keep the scrollable button row below. Buttons remain narrower/taller and text keeps priority over buttons.

**Reason:** User feedback after v3/v4: the separate button row wasted vertical space on the head unit.

**Consequences:** Replaces D-035's wide-screen button-row-below arrangement. Layout/presentation-only change on branch `v0.3.0`; no protocol, scheduler, signal or presentation-contract changes.

---

## D-037 — Screen-proportional typography and control metrics

**Status:** Superseded by D-041 — 2026-08-10

**Decision:** Dashboard fonts and button/header metrics scale with the screen's short side, normalized to the 720dp target head-unit reference: `factor = min(widthDp, heightDp) / 720`, clamped to ≥ 0.5. All dashboard text (titles, labels, values, status lines, buttons) and button min sizes, padding and header minimum height use this factor.

**Reason:** User feedback: fixed sp sizes do not adapt to different screens; the layout should occupy a consistent proportion of the screen regardless of device size/density.

**Consequences:** On the 720dp target the factor is exactly 1.0, so v5 proportions are unchanged; smaller screens shrink proportionally and larger screens grow. Layout-only change on branch `v0.3.0`; no protocol, scheduler, signal or presentation-contract changes.

---

## D-038 — All layout elements scale; no text outside the displayable area

**Status:** Superseded by D-041 — 2026-08-10

**Decision:** The screen-proportional factor applies to every layout metric, not only fonts: root/card/button paddings, margins, separator height, corner radius, header and button geometry all scale through the `dp` helper, which also enforces a 1px floor so strokes and padding never disappear. Combined with the narrow-screen fallbacks (scrollable button row) and single-line ellipsis on header text, no text is placed outside the displayable area.

**Reason:** User feedback after v6: fonts alone were not enough; every element must auto-adapt and no text may be clipped outside the visible area.

**Consequences:** On the 720dp target the factor remains 1.0, so v5/v6 proportions are unchanged. Layout-only change on branch `v0.3.0`; no protocol, scheduler, signal or presentation-contract changes.

---

## D-039 — Size-independent responsive/adaptive UI

**Status:** Superseded by D-041 — 2026-08-10

**Decision:** Replace fixed-aspect assumptions with a window-size-driven responsive layout. The actual app window (configuration `screenWidthDp`/`screenHeightDp`, plus the live laid-out root size) drives: font/control scale from the window short side (reference 720dp, floor 0.5), dynamic card column count (minimum card width 240dp, maximum 3 columns), and header mode (buttons inside the header only at ≥720dp width, otherwise a separate scrollable row). Cards reflow into computed rows; the data area always lives in a vertical ScrollView so reduced height scrolls instead of shrinking text. A coarse layout bucket (columns / header mode / font bucket) triggers a rebuild on window resize, with hysteresis to avoid flicker.

**Reason:** User requirement: the UI must be size-independent, reflow rather than scale, keep critical data readable, never clip or overlap, and shrink text only as the last resort.

**Consequences:** Layout is derived from the current window at build time and live-resize time; column/row math is unit-tested in `ResponsiveLayoutTest`. On the 720dp target the factor remains 1.0. Layout-only change on branch `v0.3.0`; no protocol, scheduler, signal or presentation-contract changes.

---

## D-040 — V0.3.0 deadline/priority scheduler with backpressure

**Status:** Superseded by D-046 after E1 review — 2026-08-12

**Decision:** Replace the fixed whole-frame cadence with a `DeadlineScheduler`. Each whitelist request keeps an independent target period, priority and deadline; due requests are selected every loop and ordered to minimize ELM header switches (7E0 group → 7E2 group → adapter). A request overdue past its deadline is skipped for that cycle and its next slot is re-based on now, so there is never a catch-up request avalanche. The scheduler tracks executions, deadline misses, skips and a bounded latency window (P50/P95/P99); the live loop samples request Hz, signal-publish Hz and NO DATA/TIMEOUT/BUS error counters into `performance.csv` every ~5 s. Fast/slow rates remain the V0.2.0 periods until staged frequency tests provide evidence.

**Reason:** ROADMAP V0.3.0 requires independent deadline/priority scheduling and backpressure (D-011/D-015) before any frequency increase; the old `next*` timer loop is a whole-frame cadence with no skip policy.

**Consequences:** Scheduler profile becomes `v030_deadline_001`; `performance.csv` gains scheduler columns; `DeadlineScheduler` and `LatencyWindow` are pure, unit-tested code. No request/whitelist changes; rates stay at V0.2.0 values until the staged ladder tests.

---

## D-041 — Full size-independent responsive/adaptive UI reset

**Status:** Accepted — 2026-08-10

**Decision:** Reset the dashboard layout around the current app window's actual available width and height after system-bar, display-cutout and freeform-caption insets. The UI must reflow continuously and must not use a reference resolution, fixed aspect ratio or whole-screen proportional scale. A pure layout policy defines bounded component contracts (minimum/preferred/maximum card width, minimum card height, padding/gaps, bounded typography and minimum touch targets). The card grid computes the number of columns and centered row geometry from usable width; narrow windows reduce columns down to one, while ultra-wide windows cap card width instead of stretching content indefinitely. Header controls use inline, split and stacked/wrapped modes selected from the space actually available. Insufficient height is handled by whole-page vertical scrolling and compact spacing, never by unbounded font reduction. Live resize must update native view measurement/reflow without Activity/content-view rebuild storms.

The frozen product presentation contract is restored: POWER is ICE mechanical power → engine RPM → conditional `IDLE CHECK` → HV battery power. `IDLE CHECK` has no permanent inactive label and is visible only while the eligibility state is actually active. No S0–S4 or MG1/MG2/MGR values enter the default dashboard.

**Reason:** The earlier D-037/D-038 implementation scaled nearly every metric from the window short side, and D-039 added only coarse width buckets around that scale. That still behaves like a scaled reference canvas, produces oversized text on large windows, can force controls into horizontal scrolling, and does not provide component-level sizing contracts. The user requires responsive reflow across portrait, landscape, 4:3, 16:9, 16:10, ultra-wide, tablet, split/freeform and extreme window sizes, including all transition widths/heights.

**Consequences:** D-041 supersedes D-037, D-038 and D-039, supersedes the POWER-order/permanent-inactive-Idle-Check parts of D-034/D-035, refines D-036's header behavior, and replaces the prior bottom-anchored candidate with normal whole-page content flow. Critical labels and values wrap and expand their cards rather than ellipsizing; controls wrap into reachable rows; secondary status text may use end ellipsis but retains its full accessibility description. The implementation stays Kotlin + Android View with no new runtime dependency and no protocol/scheduler/signal changes. Pure JVM tests and production ViewGroups share the same exact row geometry and safe-height spacing policy. Tests must cover representative form factors, exact breakpoint neighbours, monotonic column reflow, card-width bounds, control reachability, bounded typography and resize stability. Visual/device testing remains useful but is not the sole acceptance evidence.

---

## D-042 — V0.3.x engineering milestone and V0.3.1 app version are distinct

**Status:** Milestone/app-version separation retained; V0.3.1/v23 mapping superseded by D-046 V0.3.2/v24 — 2026-08-12

**Decision:** Keep the V0.3.0 High-Performance Scheduler / Refresh Frontier engineering milestone open while advancing the installable app candidate to `versionName = 0.3.1`, `versionCode = 23`. D-041 responsive UI and the V0.3.1 control/logging work are delivered together in that app candidate on branch `v0.3.0`.

**Reason:** D-041 already produced a locally built `versionCode = 22` V0.3.0 APK, while the user has now explicitly authorized V0.3.1. Reusing code 22 or silently calling the performance milestone closed would make installation and evidence identity ambiguous.

**Consequences:** For the historical V0.3.1 candidate, Gradle, `BuildConfig`, logger metadata, workflow artifact names and canonical documents identified V0.3.1/v23. D-046 now advances those app/build identities to V0.3.2/v24 and changes only the scheduler semantic profile; decoder and protocol profile stay unchanged. The engineering milestone remains V0.3.0 until its scheduler-frontier exit gate is satisfied.

---

## D-043 — Three fixed controls and one owner for the complete session lifecycle

**Status:** Accepted — 2026-08-11

**Decision:** The dashboard exposes exactly three fixed controls: `设备`, `开始`, `结束`. `设备` retains the paired-device picker. `开始` atomically captures the selected device and starts one worker-owned sequence: create evidence session → connect Bluetooth → initialize/validate the adapter → configure the frozen runtime profile → enter LIVE. `结束` is an idempotent stop request; the same worker-owned sequence exits LIVE, closes the connection, finalizes and saves the logs. LIVE is not published before all required adapter/runtime commands succeed.

A small typed state (`IDLE`, permission wait, connecting/initializing, `LIVE`, stopping/saving and save-failed) is the only source for control availability. Device selection and duplicate Start are disabled whenever a session is owned; End can cancel permission/connection/initialization or stop LIVE, and is disabled while the exactly-once final save is running. There is no separate disconnect, stop-live or export button and no automatic share chooser.

**Reason:** Four separate actions require unnecessary driver decisions. The old independent `busy` / `liveMode` / connection / logger flags also allow device changes, repeated End/Start and finalization races during an active run.

**Consequences:** One session cannot mix devices or accept post-finalize writes. A process-wide vehicle-session lease is held from the worker-owned Start sequence until its ELM socket is closed, so an Activity replacement cannot start a second Bluetooth owner while the prior owner is still unwinding. Waiting for that lease is cancellable by End/onDestroy and cancellation returns to IDLE without opening a logger session. Connection setup receives a live cancellation predicate and rejects/closes a socket created after End/onDestroy. Logger shutdown is latched synchronously so a late worker cannot open a new session after Activity destruction. Start failures still preserve their partial connection evidence and are finalized as failed/interrupted rather than discarded. Reconnect always uses the immutable device captured for that session. This is control/lifecycle work only; the request whitelist and polling periods do not change.

---

## D-044 — Streaming checkpoints, interrupted-session recovery and human-readable public archives

**Status:** Accepted — 2026-08-11

**Decision:** Keep the app-specific external directory as the canonical live working area because it is available without broad storage permission on API 26+. Replace count-based incidental flushing with an independent monotonic checkpoint task: while the process is runnable, bulk streams target a 2-second flush interval and all fixed writers plus an atomic `session.json` checkpoint target a 10-second durable-sync interval. The task uses fixed delay, not catch-up scheduling; lifecycle/End/error boundaries force a durable checkpoint. Actual stream-write, checkpoint, fsync, maximum-checkpoint and checkpoint-lock-wait costs are exported for weak-hardware validation.

At next launch, fixed-root session directories left `active`, `finalizing` or otherwise incomplete are recovered offline. Original raw/decoded/frame/event/connection bytes are never rewritten; prior session/manifest metadata are copied to `*.pre_recovery.json` before derived recovery metadata is written. Recovery records the interruption and last durable record time, sets `evidence_complete=false`, produces a fresh manifest, and generates a ZIP idempotently. A `completed` session whose ZIP is missing may retain `evidence_complete=true` only when the preserved old session/manifest, required acquisition-file set, sizes, hashes, record counts, complete line tails and directory/session identity all still match; otherwise recovery records an integrity downgrade. A repeated recovery attempt uses the preserved metadata and ignores only the fixed recovery-derived files plus their known `AtomicFile` `.new`/`.bak` companions, so a recovery-time crash cannot pollute the frozen acquisition set. ZIP acceptance explicitly verifies every entry size/CRC plus the manifest-declared file set, sizes, SHA-256 values and session identity. Missing acquisition provenance remains unknown/incomplete and the recovery/manifest-generator build is recorded separately; it is never replaced by the current app's identity. Normal archives use the frozen local end time; recovered archives use the last durable-record time and original recorded time zone, and include `interrupted` in the display name. Normal example: `RX400h Monitor log 2026-08-11 18-23-59.zip`.

After normal finalization or recovery, the archive is automatically published to user-visible `Download/RX400h Monitor`: MediaStore on API 29+, and the legacy public Downloads directory on API 26–28 only when `WRITE_EXTERNAL_STORAGE` is granted. No `MANAGE_EXTERNAL_STORAGE` permission is allowed. If public publication cannot complete, the verified internal archive remains intact and can be retried; the UI must report that saving publicly failed rather than claiming success. Automatic MediaStore/legacy publication is content-hash deduplicated with a bound receipt. A user-selected arbitrary SAF provider is length/SHA-256 read-back verified, but its external-write-to-internal-receipt crash window is explicitly best-effort rather than an exactly-once guarantee.

**Reason:** Existing logs already stream to disk, but up to several seconds can remain in Java buffers, an interrupted session has no manifest/ZIP/recovery path, and app-specific `Android/data` is not reliably browseable on modern Android. The user has experienced interrupted runs that appeared to lose all evidence and requires file-manager-accessible, human-readable records.

**Consequences:** Final archive construction remains `.tmp` → fsync/hash/ZIP validation → atomic promotion; exact build provenance is checkpointed with the session. Git provenance capture is fail-closed: an unavailable/failing Git executable or an invalid commit ID fails the build instead of silently claiming `unknown` and clean; GitHub Actions additionally asserts that the embedded commit equals `GITHUB_SHA` and that the checkout is clean. Process-wide gates serialize Activity replacement against session recovery and serialize public hash-deduplication/copy/receipt commit, so two Activity instances cannot concurrently finalize or publish the same run. Public copies are content-hash verified and publication receipts remain outside the immutable evidence ZIP. Flush/sync frequency and contention—including acquisition-side logger-lock wait, full serialization cost and telemetry/PSS sampling cost—must be measured on the Android 8.1 weak head unit before V0.3.x performance closure. Public publication is off the acquisition hot path and never replaces the canonical working copy until success is confirmed.

---

## D-045 — Route routine delivery through Chat and Work; reserve Codex for senior escalation

**Status:** Accepted — 2026-08-12

**Decision:** Adopt a project-specific `Chat → Work → Codex` collaboration model. Chat owns requirement clarification, scope, priority, evidence classification and task packets. Work owns normal implementation, build, APK/adb/logcat/GUI operations and defined regression testing. Codex is the senior repository engineer for architecture, high-risk refactoring, difficult multi-file root causes, concurrency/lifecycle/state machines, performance core, CAN/ISO-TP/ELM327/Bluetooth, protocol parsing and sufficiently investigated problems that Work cannot solve reliably.

`CHAT_ROLE.md` and `WORK_ROLE.md` are standalone role entrypoints. Fast-changing version/commit/gate state remains canonical only in `PROJECT_STATE.md`; the cards point to it rather than becoming replacement state logs. `AGENTS.md` remains the Codex rule source.

**Reason:** Routine GUI, file, build, install, screenshot, log collection and clearly specified small changes do not require the same repository-level reasoning as core communication, architecture and concurrency work. Separating them reduces repeated investigation and unnecessary Codex use while preserving early escalation when blind Work trial-and-error would be more expensive or risky.

**Consequences:** Chat produces a project-specific `TASK_PACKET`; Work verifies remote/branch/HEAD before mutation and returns concrete commands, results, hashes and evidence. Work upgrades with the `CODEX_ESCALATION_PACKET` in `WORK_ROLE.md`. Codex begins from that local evidence, applies the smallest necessary core change, and returns a `WORK_FOLLOWUP` so Work can perform build/install/GUI regression. Frozen protocol/UI/evidence gates and explicit authorization for push/release/vehicle actions remain unchanged.

---

## D-046 — Reconstruct the scheduler around absolute releases, prompt boundaries and capacity evidence

**Status:** Accepted for implementation — 2026-08-12

**Decision:** Replace `v030_deadline_001` with a capacity-aware, single-dispatch scheduler. Each request keeps the frozen V0.2.0 target period, but releases are anchored to the LIVE epoch instead of `completion + period`. A release deadline is explicit (`release + period`). The scheduler retains at most one pending job per request, accounts for every coalesced release, replans after every header or request transaction, and treats the ELM header as state with a measurable transition cost. Feasible work is ordered to meet absolute deadlines first and minimize header changes second; priority is used only for deterministic overload shedding. Reconnect does not reset all requests to immediately due.

Each release has exactly one terminal class: executed on time, executed late, rejected for capacity, expired unexecuted, transport unavailable, or session ended. Per-request counters must conserve the release total. The legacy `deadline_misses` and `skipped_overdue` columns remain only as derived compatibility fields and no longer describe the same event twice. New scheduler event and per-request-stat streams record release/deadline times, queue wait, predicted and actual setup/service cost, lateness, header switches, admission state and terminal reason.

The transport remains strictly serial and never sends the next command before the prior ELM prompt boundary. The fixed Probe-era runtime waits (`minimumGap=120 ms`, `preDrain=80 ms`, `quiet=80 ms`) are not protocol facts: the hash-registered HA/HCI evidence records six serial ELM transactions in a median core loop of about 159 ms, which is incompatible with applying those fixed waits to every command. Normal scheduled requests therefore use the prompt as the synchronized transaction boundary and do not add fixed per-command gap/drain/quiet delays; conservative waits remain in initialization, identity and recovery paths. Any prompt loss still fails the transaction and drives the existing error/reconnect path.

Admission is fail-closed. A complete trusted p95 cost model is assessed over the 60-second hyperperiod using the production scheduling policy. Missing cost evidence returns `UNKNOWN`; impossible demand returns `OVERLOADED`; only a zero-miss simulation with positive headroom returns `ADMITTED`. `UNKNOWN` and `OVERLOADED` may run only as explicitly logged diagnostic best effort and cannot unlock the rate ladder.

**Reason:** The V0.3.0 E1 archive showed 4,248 scheduled executions in about 2,850 seconds (1.49/s) against 5.033/s frozen nominal demand, 2,270 header commands, and 2,856/2,856 legacy miss/skip counters. Source review proved those two counters were incremented in the same branch, long stalls counted only once per request scan, batch members were not rechecked after queueing, and both success and skip rebased cadence on the current/completion time. Even before header cost, the old fixed waits make the frozen demand physically infeasible. HA/HCI supplies a clean-room feasible serial working point and header-group sequence, but not HA scheduler source or per-command p95 values; it justifies removing the false wait assumption, not claiming capacity admission prematurely.

**Consequences:** The app candidate advances to `versionName = 0.3.2`, `versionCode = 24`, with scheduler profile `v030_capacity_002`; protocol profile, decoder, seven-request whitelist and target periods remain unchanged. D-040's implementation semantics are superseded, while its no-catch-up and bounded-memory goals remain. Deterministic tests must cover absolute cadence, long stalls, conservation, deadline boundaries, header-aware replanning, ATRV header neutrality, transport downtime, capacity admission and E1-like overload. The new candidate is not a promoted baseline until exact-commit CI, API 27, paired-OBD connection/LIVE/End/public-save/recovery smoke, and a same-period E1 rerun show auditable per-request behavior.
