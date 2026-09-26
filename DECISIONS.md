# RX400h Monitor — DECISIONS

## D-064 — Pure real-time instrument closure (2026-09-23)

Local result: `D064_LOCAL_REVIEW_20260923.md`; V0.3.5/v29 benchmark SHA `3a4ed745…76496f5`, main3,525lines, two92-test runs/41,143replies/10Android checks/157pixel frames/build/lint/signatures/no-adapterGUI pass. Standard Gradle host-worker failure and real Bluetooth/music/sleep remain unproven. Only local artifacts, no promotion/push. Deleted D063 modules recoverable from preserved source ZIP.

Accepted by user after cross-checking chat requirements. Full scope: `D064_REALTIME_CLOSURE.md`. Retain HA sequence only with 3Hz core target and 3s coolant/ATRV,5s battery temperature. Eliminate runtime evidence/performance producers and consumers, save/recovery/strategy switching. Automatic one-shot connection, manual side-car reconnect, background acquisition, immediate Back cancellation and scoped connection ownership replace log-owned lifecycle. Persistent simultaneous loss of engine and hybrid valid vehicle data for 10s stops acquisition; ATRV/ELM OK never resets this timer. Use monotonic time. Unknown is not zero or proof of ignition off. Old app-private cleanup deletes only positively unsaved sessions, preserves published/ambiguous data and preferences, never blocks startup; no new discard logs. Preserve authored UI except old buttons removal/settings device entry/status meanings/wheel direction. No protocol/formula changes or unverified MX+ commands. Tests/artifact provenance remain local engineering tools, not product telemetry. No commit/push/install on physical car/publication.

## D-063 — Lean runtime continuation (2026-09-17)

Local completion: `D063_LOCAL_REVIEW_20260917.md`. Main9044lines; R8 benchmark6446609bytes; exact D062 preserved. Correctness, independent pixel, Android file/JSON and native GUI gates pass; standard Gradle host worker failure and shrunk-package actual LIVE/end/export remain open. R8 mapping is part of the artifact identity and must be retained. Host steady-search microbenchmark is not a HA/car CPU claim. Enhanced MX+ transport remains test-only, no unverified capability activation.

The user explicitly requested both further simplification and runtime/adapter optimization and confirmed local implementation/testing. Keep D062 frozen as the exact comparison, not the old Git HEAD. Prefer removing duplicated forwarding and repeated hot-loop work over new architecture. Fixed JSON record reuse is serialized by the existing logger monitor, must overwrite every nullable/value field, and must preserve emitted content; no async logging queue is added. Scheduler order/deadlines/accounting require differential tests. Benchmark-only R8 is a measured packaging experiment, not evidence of source-line or CPU improvement; retain an unshrunk debug control. MX+ enhanced commands stay test-only pending verified firmware/format evidence; no new ECU payload, period/formula/UI/failure-policy change or physical action.

## D-062 — Behavior-preserving lean-code cleanup (2026-09-16)

Completion 2026-09-17: `D062_LOCAL_REVIEW_20260917.md`. Additional authorized strengthening removes unused duplicate fields/state/interfaces and the sole AndroidX Core dependency; native insets are version-guarded for minAPI26. User-authored assets remain byte-identical. Main-source reduction15.1%, benchmark APK13.9%; same-emulator UI CPU essentially flat, so no performance escalation claimed. Direct-unit/recorded-parser/oracle/file-fault/GUI/lint/build/signature gates recorded; standard Gradle worker host failure and real-car limits kept explicit.

User approved the first batch of the self-audit. Delete unreferenced old responsive/CRT UI and recovery-only call trees superseded by D060 discard. Retain all shared active-save/export/identity validation, ownership, prompt/cancel and freshness boundaries. Reuse already assembled standard payload evidence; keep raw/log bytes and decoder results unchanged. Percentiles use a fixed scratch array invalidated on add/clear, so multiple reads of the same window do not allocate/sort again. UI publication is one pending main-thread callback per actual source/status/Idle update plus the existing freshness heartbeat, not duplicate calls after a header change. Display formatting compares values/freshness, while sample versions and timestamps retain their source meaning. Power self-test math must be shared allocation-free by renderer and tests. Debug fixture activities/assets remain available in debug only; benchmark stays non-debuggable and signed with the same test key/package, suffix D062-lean. Metrics remain available for comparison.

No logging reduction, durability-policy change, transport experiment, formula/period/Idle threshold change, visual/frame-rate change, physical-device install, commit/push or release promotion. Verify unchanged parser evidence, scheduler outcomes, pixel/math/freshness/session behavior, compile/unit/lint/build, and exact local emulator checks where available. D061 source/artifacts and user evidence remain untouched. Removing dormant code is not evidence of CPU improvement.

## D-061 — Local A+B CPU/transport prototype (2026-09-11)

User explicitly confirmed implementation and local verification after the A+B research. A keeps exact pixels: damage applies to sharp raster and halo, cached static content respects authored z-order, animation changes are quantized to the actual rendered geometry. The reference/oracle must independently rebuild the entire sharp page; comparing two compositions of the same damaged sharp buffer is insufficient. B uses one bounded blocking input pump per connection and one command owner, close-before-drain cancellation, monotonic receive timing and one parsed CAN representation shared with decoding. No unbounded queue, blind serial command parallelism, new ECU service, formula/Idle/period change, or silent strategy downgrade.

MX+ experimental encodings may contain only the existing whitelist payloads, preserve reply identity/count semantics and report actual wire commands. STBC is not certified for observed STN2256 v5.6.24; STPX response-count/segmentation parity also needs evidence. Implement/test pure encoders and batch boundary/failure logic locally but leave unverified wire modes out of the default runtime. A test build is not real-adapter throughput proof. If a wire-mode gate cannot be closed locally, disclose it as open, never ship an enabled guess. Retain D-060 saved archives/source/artifact and fixed test signing; no firmware update, car operation, commit/push/PR.

2026-09-12 implementation outcome: wire encoders/parsers exist only inside JVM research tests; runtime remains legacy ELM framing, not a hidden alternate strategy. Benchmark build is non-debuggable/unminified with fixed debug applicationId/signing and the same opt-in diagnostics. Add six renderer-window columns at the end of performance.csv; timestamp identifies repeated last-known windows, null means unavailable, no GPU-time claim. Completed local evidence/CPU controls and limitations are in `D061_LOCAL_REVIEW_20260912.md`. This decision does not change request periods, formulas, Idle, authored art, or session discard semantics.

## D-060 — Authorized local closure of nine audited chat requirements (2026-09-07)

User confirmed the scope in `D060_REQUIREMENTS_CLOSURE.md`. This supersedes D-055 recovery/retention only for unsaved old sessions, and supersedes earlier UI-only freezes for the explicitly listed Idle, publication, strategy and lifecycle work. Keep saved archives and all original host evidence. Implement steady and HA replica only, default replica, with confirmed automatic End/save/new-session strategy switches; no automatic downgrade. Record failed-save discard in the following session without retaining old raw contents. New Idle rules use the last accepted D-059 plan, not a mixture of earlier threshold proposals. Exact authored layout remains authoritative, with corrections limited to identified fidelity gaps. Each batch needs tests and evidence; 5Hz/80% device-equivalence are measured goals, not guaranteed claims. No Git commit/push/PR/release or real-vehicle interaction is authorized.

2026-09-08 outcome: retain original software pixel composition, cache it in fixed offscreen damage tiles, and always present the full bitmap. Direct HWUI's residual halo differences are not silently accepted. Old-buffer flush success is no longer a precondition for deliberately discarding a failed session; descriptors are explicitly closed and errors remain reported. Dynamic Chinese uses a locally generated OFL Noto derivative; no runtime font/tool download. API26 single-core test requested1GB but runtime clamps1536MB, so use actual configuration and do not call it a fitted target car. Verification/remaining gates are in `D060_LOCAL_REVIEW_20260908.md`.


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

---

## D-047 — CRT Green remains a bounded presentation skin over D-041

**Status:** Integrated and exact-clean locally at `c9ad397`; remote/target promotion pending — 2026-08-25

**Decision:** Restyle the existing D-041 dashboard as one fixed CRT Green prototype without introducing a generalized theme system. Keep the stable Android View tree and all responsive geometry policies. Use a near-black surface, a small monochrome phosphor-green palette, thin rectangular frames with restrained corner accents, state-aware terminal-style buttons, subtle static scanlines and a low-radius text glow. Effects must be preallocated or static, must not schedule animation frames, and must not require Compose, WebView, runtime blur, a shader framework or a new dependency.

The renderer continues to consume only `DashboardSnapshot`, `DashboardStatus` and `MonitorControlState`. The exact controls, fields, units, order, freshness indication and active-only Idle Check behavior do not change. Existing warning semantics may retain one restrained amber state; presentation styling must not create new vehicle or safety meaning.

**Reason:** The user supplied a concrete monochrome CRT reference and requested a low-cost presentation prototype. The current `DashboardUi` / presentation contract already permits replacing visual treatment without changing vehicle truth. Keeping this as a bounded skin proves that separation and avoids coupling a visual experiment to the open scheduler and lifecycle work.

**Consequences:** Production core, protocol, decoder, SignalStore, logger and session ownership were untouched while the prototype was evaluated. The completed local test/lint/assemble/signature and responsive GUI evidence is sufficient to integrate this fixed skin into the next V0.3.3 candidate without creating a theme system. Readability overrides scanline/glow strength. The V0.3.3 integration may advance build identity only under D-048 and authorizes neither Git publication nor a final multi-theme architecture.

---

## D-048 — V0.3.3/v25 is a bounded reliability, admission-data and CRT integration candidate

**Status:** Implemented and exact-clean locally at `c9ad397`; promotion pending — 2026-08-25

**Decision:** Advance the next installable candidate inside the still-open V0.3.0 Scheduler / Refresh Frontier milestone to `versionName = 0.3.3`, `versionCode = 25`. V0.3.3 combines only these already-scoped items:

1. the startup-recovery fast path and explicit `RECOVERING` state in D-049;
2. one exactly-once logical terminalization owner for End/onDestroy/finalize in D-050;
3. a versioned trusted API 27 p95 scheduler-cost model at the unchanged request periods in D-051;
4. observational wall-clock-adjustment evidence in D-052; and
5. integration of the accepted fixed CRT Green presentation from D-047.

At this decision's acceptance, source and APK remained V0.3.2/v24 until implementation could change and verify build identity. Implementation subsequently advanced exact-clean local source/APK identity to V0.3.3/v25 at `c9ad397`; that exact artifact was later installed and produced the two hash-audited 2026-08-26 normal-flow sessions. This decision does not itself promote V0.3.3, close V0.3.0, authorize a rate ladder, or claim a new engineering baseline; forced interrupted recovery remains open.

**Reason:** The API 27 same-period sessions show that the reconstructed scheduler can run the frozen demand cleanly, while startup recovery still performs work that is too broad for every launch, `SAVING` does not honestly describe recovery, and End/Activity destruction can compete around finalization. The accepted CRT prototype is ready to ship with those bounded corrections. A new app version is required so evidence cannot be confused with V0.3.2/v24.

**Consequences:** The frozen vehicle contract is unchanged: no new command/header, no protocol-profile or decoder-formula change, no `SignalStore` semantic change, and no request-table period/phase/deadline change. The exact periods remain `std_core=800 ms`, `cd_f3=1000 ms`, `coolant=3000 ms`, `c3=800 ms`, `c4=1500 ms`, `cf=5000 ms`, `atrv=3000 ms`. This decision originally authorized local implementation and verification only. Later explicit user continuation authorized the resulting local implementation/evidence commits, but did not authorize push, PR, release publication or vehicle action.

---

## D-049 — Startup recovery has an explicit phase and a metadata fast path

**Status:** Exact-clean local implementation complete; target-device validation pending — 2026-08-25

**Decision:** Add `RECOVERING` as a first-class monitor/session phase, distinct from normal-session `SAVING`. On cold start the UI enters `RECOVERING`, reports that prior logs are being checked, and keeps all three controls unavailable until classification completes. Recovery work remains off the UI thread and remains serialized against any live/finalizing logger owner.

The startup classifier first reads only bounded atomic metadata. A directory in a stable terminal status (`completed`, `interrupted` or `start_failed`), with the expected immutable archive matching identity/name/recorded size and a parseable bound publication receipt, may take the already-published fast path without recomputing the archive SHA-256 or opening every ZIP entry at every launch. This does not upgrade interrupted/start-failed evidence to complete; it only recognizes that its immutable archive and public receipt are no longer pending work. Missing/invalid metadata, non-terminal status, missing/size-mismatched archive, absent/invalid receipt or any recovery/publication-pending condition enters the existing slow path, which retains full ZIP/manifest/size/SHA/session validation before recovery or publication. The fast path makes no new post-publication bit-rot claim; it reuses the integrity result already committed by finalization/publication and must never discard or rewrite acquisition evidence.

**Reason:** A growing history of already completed/published sessions must not make each app launch hash every old ZIP or present recovery as ordinary saving. Recovery correctness still requires deep validation for actual candidates, but healthy terminal history is not a recovery candidate.

**Consequences:** No database, unbounded index, service or new dependency is implied; prefer the existing atomic session/receipt records. Tests must prove that healthy completed history avoids deep hashing, suspicious/pending sessions still take the validating path, Activity replacement cannot race the recovery worker, and the phase returns deterministically to `IDLE` or the existing save-failed/retry state.

---

## D-050 — End, onDestroy and finalize share one exactly-once logical terminalization owner

**Status:** Exact-clean local implementation complete; forced-recovery validation pending — 2026-08-25

**Decision:** One session-scoped atomic terminalization claim owns stop reason, transport close, final event sequence, durable checkpoint, archive finalization and automatic publication. User End and `onDestroy` submit intent to that same owner; neither may independently close/reclassify/finalize the logger after the claim is held. A previously latched user End remains `USER_END` if Activity destruction follows it. Activity destruction without a prior normal End is an interruption reason, but, while the process is alive, it still runs through the same idempotent terminalization pipeline instead of bypassing finalization and separately invoking logger shutdown.

Exactly once means one logical terminal transition and at most one promoted internal archive/publication receipt per session in one process. The atomic `finalize_intent.json` is the canonical logical terminal claim. Normal in-process completion also writes one `SESSION_END` row. If the process dies after the intent is durable but before that event batch is durable, recovery preserves the claimed kind/reason, records that the event was not durable, and keeps `evidence_complete=false`; it does not invent a back-dated `SESSION_END`. Filesystem operations remain retry-idempotent so process death at any boundary is completed by next-launch recovery. This does not upgrade arbitrary SAF providers to a cross-process exactly-once guarantee; D-044's documented external-write-to-receipt best-effort window remains.

**Reason:** The current Activity finally block may return when destruction is observed while `onDestroy` separately asks the logger to shut down. That conservative behavior preserves evidence but can turn a user-ended run into next-launch interrupted recovery and makes competing completion paths difficult to reason about.

**Consequences:** Tests must race repeated End, End followed by onDestroy, onDestroy during connect/LIVE/finalize/publish, Activity replacement and retry after process-death checkpoints. They must assert one canonical terminal claim, exactly one `SESSION_END` on the uninterrupted path, no post-terminal writes, one internal archive identity, content-hash-deduplicated public output, and honest downgraded recovery when the process dies before the event batch or terminal receipt.

---

## D-051 — Trusted scheduler costs are versioned API 27 evidence, not period tuning

**Status:** Exact-clean local artifact complete; remote CI and target-device holdout pending — 2026-08-25

**Decision:** Replace the zero-sample diagnostic seed only with a named, versioned cost-model record derived offline from hash-pinned, provenance-complete, same-period API 27 E1 sessions on the target Spreadtrum head unit and OBDLink adapter. The primary training provenance is the two clean exact-commit 2026-08-15 long runs:

```text
RX400h Monitor log 2026-08-15 14-15-58.zip
SHA-256 0dc6b6a71f40365b18febe6a815eeb13f2a6bb32ee0a4dcb6237091421a245f7

RX400h Monitor log 2026-08-15 17-50-18.zip
SHA-256 d304e9dfabb1f1d4a28eda9b9c0fc674c04276ce119e8ea5a94e8cdf783432d7

git commit 8e55c6afae20ca64b9ea9bba5861bc85d8017c62
exact CI APK SHA-256 841b1a4adb9f9e4a1834d2830dd6e94754a54cbcc3b2b3209023061da1969e9b
device API 27
```

The conservative request-service p95 inputs and sample counts are frozen for the first model as:

```text
std_core  141 ms / 5631 samples
cd_f3     139 ms / 4503 samples
coolant   139 ms / 1501 samples
c3        162 ms / 5630 samples
c4        169 ms / 3000 samples
cf        151 ms /  900 samples
atrv       81 ms / 1498 samples
```

The first model is direction-aware rather than one scalar. Its steady header costs are `7E0 → 7E2 = 65 ms / 4500 samples` and `7E2 → 7E0 = 116 ms / 4498 samples`. Cold start is represented separately: `NONE → 7E0 = 154 ms / 2 samples` is the observed maximum, while `NONE → 7E2 = 154 ms / 0 direct samples` is an explicit conservative engineering bound for reconnect planning, not empirical p95 evidence. Both cold entries remain statistically untrusted and do not satisfy or block the minimum-sample trust gate for periodic work. Missing either required cold target makes the model incomplete/`UNKNOWN`; there is no silent cross-target fallback. The exact empirical population remains `8998 steady transitions + 2 observed cold starts = 9000 total`; the cold maximum must never be charged to every steady switch.

The model ID is `api27_sp7731e_obdlink_v030_capacity_002_p95_v1`. Its applicability is fail-closed: only API 27, manufacturer `sprd`, model `sp7731e_1h10_native`, device `sp7731e_1h10` and a case-sensitive `OBDLink MX+` family adapter name select the trusted model. Any mismatch or unavailable adapter name selects the existing untrusted diagnostic seed and therefore returns admission `UNKNOWN`. With the frozen seven-request table, the deterministic 60-second production-policy replay returns `ADMITTED`, projected utilization `0.906533`, zero projected deadline misses and zero capacity rejections. This admission result does not authorize a polling-period increase or rate-ladder step.

The record must bind model ID, all source ZIP SHA-256 values, app/APK/commit identity, device/API/adapter applicability, extraction method and quantile rule, per-request values/sample counts, both steady header directions, the separate cold-start limitation and any explicit conservative margin. Runtime observations may be logged for later models but may not silently mutate the trusted model or polling periods.

The two 2026-08-18 dirty-CRT API 27 short sessions (`c3cdba1706a3bd610d106403ed505c4479562ad174b15ee5ceccf38dc5ac24c9` and `a806771c8fb6d09aadc6db102716e58a3429c0ca75ea133fe4bba6385118c8b7`) are holdout/regression evidence. They may validate the frozen model against the integrated presentation candidate, but they are not the sole or primary training provenance.

Admission remains fail-closed under D-046: incomplete or hardware-inapplicable costs remain `UNKNOWN`; only the complete versioned model passing the deterministic 60-second production-policy replay with zero projected misses, zero capacity rejections and positive headroom may report `ADMITTED`. The V0.3.3 model uses the already frozen request table and does not change periods, phases, deadlines, priority, whitelist or prompt-delimited transport semantics.

**Reason:** The API 27 sessions provide the missing real weak-device service/header distributions, but a bare set of constants without source hashes, samples and a quantile method is not reproducible trusted evidence. Admission data and rate selection are separate decisions.

**Consequences:** Unit tests must pin the model ID/inputs, reject missing/untrusted samples, recompute admission deterministically and prove the seven periods are byte-for-byte unchanged. Any later cost update requires a new model version and evidence record; any later period change still requires a separate rate-ladder decision and real-vehicle gate.

---

## D-052 — Wall-clock adjustment is observational; monotonic truth is unchanged

**Status:** Exact-clean local implementation complete; target-device clock-step observation pending — 2026-08-25

**Decision:** Detect a material divergence between wall-clock progression and elapsed/monotonic progression and emit a bounded `CLOCK_ADJUSTMENT` evidence event containing the observed delta/direction and before/after wall context. Do not reorder or rewrite prior records. Scheduler release/deadline/capacity logic, freshness, Idle Check timers, duration, checkpoint cadence, lifecycle ownership and recovery eligibility continue to use monotonic or already durable state and must not react to a wall-clock step.

Local archive naming and human-readable timestamps may reflect the wall clock observed at the terminal boundary, with existing collision-safe naming, but wall-clock movement cannot start/stop a session, trigger recovery, change completion status, alter a request period or become vehicle-OFF evidence.

**Reason:** Vehicle head units may correct time after boot, navigation/network synchronization or user action. A forward/backward wall step is valuable provenance, but treating it as control truth would corrupt deadlines, durations or session semantics.

**Consequences:** Virtual-clock tests must cover forward and backward wall steps while monotonic time advances normally, including steps around End/finalize. Scheduler outcomes and record order remain monotonic; only the additional observational event and subsequent wall timestamps may reflect the adjustment.

---

## D-053 — V0.3.4 fixes the natural first frame and tunes CRT visibility for the target head unit

**Status:** Exact-clean local implementation/artifact complete at `b60619d`; target first-frame/daylight gate accepted by user — 2026-08-30

**Decision:** Advance the next local installable candidate to `versionName = 0.3.4`, `versionCode = 26` for a presentation-only correction. The primary acceptance target is the actual Android 8.1/API 27 Spreadtrum head unit at a 1280x720 framebuffer (approximately 1280x672 application content with its navigation bar). Phone-portrait optimization is deferred; D-041 responsive behavior remains in source but is not a release gate for this correction.

The dashboard must not treat pre-attachment `displayMetrics` as a final window layout. Its invalidation identity includes the real laid-out width/height, density and font scale as well as safe insets. Native text auto-size and custom card/header measurement receive one attach-scoped stabilizer that may suppress at most two pre-draws before it removes itself and always releases the third draw. The debug screenshot Activity must use the same production path and must not recursively force three private extra layout passes.

CRT daylight tuning may brighten the monochrome green hierarchy, selectively apply a cached bold monospace typeface to primary values/titles/enabled controls, and strengthen the static scanline overlay. Scanlines remain preallocated, non-animated and free of draw-time allocation; their spacing should avoid one rigid short pixel period on the target panel. Glow remains small and sharp. No Compose, WebView, runtime blur, shader framework, animation clock, generalized theme system or new dependency is introduced.

**Reason:** The user repeatedly observed that labels overlap values on natural startup and become correct immediately after opening the notification shade or switching Activities. Source review found that V0.3.1 introduced pre-attachment auto-size/custom ViewGroup measurement and a window token that omitted the real viewport. V0.3.3's debug preview then recursively forced three layout passes before screenshots while production `MainActivity` did not, so earlier visual evidence proved only the refreshed steady state. The target car display also shows the original scanline alpha and green palette as effectively invisible or too dim in daylight.

**Consequences:** Natural cold-start first-frame geometry on API 27/1280x720 is the governing GUI gate: label/value sibling bounds must not overlap before any notification-shade, Activity or configuration refresh, and a later inset refresh must not change their settled geometry. Previous V0.3.3 preview screenshots are retained as post-settle styling evidence but are not valid first-frame evidence. The local source compiles, 99/99 direct JUnit tests pass, lint/assemble/signature checks pass, and static review confirms the pre-draw listener is bounded and the scanline path has no draw-time allocation. Exact-clean commit `b60619d5c1f4e011508b3cf74de6fee7422ec720` produced a 2,591,078-byte V0.3.4-debug/v26 APK with SHA-256 `fa88064be3450ccb8765217ed0f12e97e01793c66434b07b0901777b4072002b`, embedded `GIT_DIRTY=false` and the fixed v2 certificate. This host has no API 27 image and its API 26 emulator stops before ADB under both acceleration modes, so no local-emulator GUI pass is claimed. The user subsequently installed the candidate on the target and confirmed that the natural first frame no longer overlaps, notification-shade round-trip does not move the settled layout, and the brighter green/bold text/scanlines are visible; the panel's remaining daylight limitation is accepted as hardware-bound. Protocol, decoder, SignalStore, scheduler, request table/periods, transport, logger, session lifecycle, controls, fields, units and active-only Idle Check semantics remain unchanged. No push or release publication was performed.

---

## D-054 — V0.3.5 uses signal-driven native schematics and a two-level Idle Check indicator

**Status:** Authorized for local implementation — 2026-08-30

**Decision:** Advance the next local installable candidate to `versionName = 0.3.5`, `versionCode = 27`. Use the user's `RX400h-UI-layout-v035.json` as the target-head-unit composition contract and its three embedded PNGs only as shape/meaning references. The APK must not contain those PNGs. Recreate the three visual components with lightweight native Android `Canvas` drawing inside the existing presentation boundary:

1. The BAT schematic is a simplified top-view RX400h/chassis and central traction-battery pack. Real SOC controls segmented pack fill. Real battery minimum/average/maximum temperatures control only a whole-pack range and average marker; the UI must not invent per-module temperatures or a heat map. Real HV battery power may indicate charge/discharge direction without replacing the numeric value.
2. The VEH schematic is a simplified side-view RX400h. Fresh non-zero vehicle speed drives direction-neutral wheel-rim motion because the available signal does not encode forward/reverse direction. Coolant temperature and 12 V OBD voltage may drive explicit bounded gauges only. RPM/ICE mechanical power may light a clearly labelled engine region, but the renderer must not infer gear, steering, braking, wheel slip, pump flow or individual motor state.
3. The PWR schematic is a `-50 … 0 … +50 kW` bidirectional scale. Fresh HV battery power moves a clamped cursor and lights the segment from zero to that cursor; negative is charge/regen and positive is discharge/traction. A null or stale value is unknown and must dim/hide the cursor rather than pretending to be zero. The numeric value remains un-clamped.

The visual interpolation clock is not an acquisition clock. `DashboardSnapshot` remains the only vehicle-data input and is currently published about every 500 ms; short presentation interpolation may run at no more than 20 fps, only while a bounded transition or real signal-driven motion is active and the view is attached/visible. Background/detach/stale/zero conditions stop callbacks. Geometry, `Paint`, `Path`, `RectF` and any fixed animation buffers are cached outside `onDraw`; no full-screen animation, frame bitmap, dynamic noise, blur, shader framework, Compose, WebView or new dependency is allowed.

Replace the old active-only hidden `IDLE CHECK` presentation with the user-designed framed indicator at the bottom of the PWR domain. Inactive or presently unknown state shows the same frame/text at deliberately low brightness; actual active state switches the frame/text to 100% phosphor brightness. It does not pulse, blink or fade in a way that can hide the core state. This is a presentation-contract change only: `IdleCheckState`, its thresholds/timing and `SignalStore` semantics remain unchanged, and the dim state must not claim that every prerequisite is known false.

The primary layout gate is the API 27 target head unit at 1280x720 framebuffer / inset-safe landscape viewport. Preserve the stable responsive View tree, whole-page scroll fallback and all three controls, domains, numeric signals, units, freshness marks and accessibility descriptions; phone-portrait optimization remains deferred. Header/status/control business behavior is unchanged.

**Reason:** The user manually composed a readable V0.3.5 dashboard and clarified that the three imported pictures describe the desired vehicle/battery/power meaning, not shippable artwork. The product should make existing real signals spatially understandable without fabricating vehicle truth, increasing acquisition load or spending weak-head-unit GPU/CPU budget on decorative animation. The user also explicitly chose a continuously visible low-brightness Idle Check frame whose active state reaches full brightness.

**Consequences:** Work is limited to presentation/version/workflow/test/documentation files. Protocol, decoder, `SignalStore`, scheduler, request table and all seven periods, ELM/Bluetooth transport, logger, recovery and session ownership are frozen. Tests must cover mapping/clamping/unknown behavior, ticker gating, inactive/active Idle Check appearance contract and existing responsive geometry. Required local gates remain unit tests, `lintDebug`, `assembleDebug`, manifest/version/signature/provenance inspection and the best available 1280x720 GUI check. No push, PR, installation, vehicle action or promotion is authorized by this decision.

---

## D-055 — Failed evidence recovery must not lock out a new live session

**Status:** Local implementation/build verification complete; forced target recovery verification pending — 2026-09-01

**Decision:** Treat a failed startup recovery, final archive build, public copy or publication-receipt write as a retained evidence obligation, not as permanent ownership of the vehicle monitor. The verified or incomplete internal files remain untouched and retryable. After the failed worker has stopped, all writers/sockets are closed and the process session lease is released, the three-control UI must permit device selection and a new `开始` action while still exposing `结束` as the explicit retry action for the retained evidence.

Starting a new live session from `SAVE_FAILED` must atomically detach the failed session for later recovery, clear only in-memory retry ownership, and create a new session directory; it must never delete, overwrite, rename as successful or silently mark the old evidence complete. Recovery/finalization I/O must not run concurrently with LIVE. If the failed logger cannot be safely detached, Start stays rejected with a clear error instead of opening a second owner. The status remains visibly degraded until the user starts a new session or a retry succeeds.

**Reason:** On the target head unit an interrupted previous run can fail saving or recovery at the next launch. The current `SAVE_FAILED` policy enables only End, so repeated recovery failure traps the user in a save/retry/exit loop and blocks the product's primary purpose: displaying live vehicle data. Logging is development support and must fail visibly and durably, but it must not permanently disable monitoring.

**Consequences:** This is a narrow control/lifecycle exception to D-054's presentation-only boundary and refines D-043/D-044. It does not weaken single-session Bluetooth ownership or exactly-once terminal intent, and does not change protocol, decoder, `SignalStore`, scheduler, request periods, ELM transactions, log contents or recovery integrity rules. Tests must cover controls in `SAVE_FAILED`, Start from failed startup recovery, safe detachment of an in-memory finalization failure, preserved retry evidence, and refusal while a worker or lease is still owned. No automatic discard and no background recovery during LIVE are permitted.

---

## D-056 — V0.3.5/v28 uses the user's final pixel layout without normalization

**Status:** Local implementation/build verification complete; target GUI/vehicle verification pending — 2026-09-01

**Decision:** Supersede only the presentation implementation of D-054 with the user's final `RX400h-UI-layout-v035-pixel-v2 (3).json` (SHA-256 `1f3914510943261f3c2d29f4d8eaf4abca7236b5676e0584948c2839b63518aa`) as the exact visual source of truth. Advance the local candidate to `versionCode = 28` while retaining `versionName = 0.3.5`. Every stored x/y/width/height, layer visibility, z order and alpha is intentional and must not be normalized or auto-aligned.

Render a fixed 640×360 logical bitmap, nearest-neighbour scale it to 1280×720, and center it on other viewports with black letterboxing. Logical bitmaps, numerical glyphs, vehicle art and animation frames remain grid-aligned. Fixed Chinese labels are pre-rasterized from Noto Sans SC at their declared physical-pixel sizes; changing values keep the supplied bitmap glyph sets. No source reference image is shipped.

Real signals drive only the declared presentation: nonlinear SOC fill (`0–30% → 0–15%`, `30–80% → 15–90%`, `80–100% → 90–100%`), direction-neutral wheel rotation from speed, and the symmetric `-50…0…50 kW` HV-power display. After Start and the first complete SOC/HV-power sample, SOC runs `0→100→actual` and power runs simultaneous `0→±50→actual-side/zero-other-side`; after self-test exactly one cursor remains (actual side, or center at zero). Idle Check is 10% while inactive/unknown and 100% while active.

Scanline opacity/width/gap and phosphor-halo intensity/radius are adjustable in a matching in-app CRT overlay and persist only in local preferences. Defaults come from the final JSON: `75% / 1 px / 1 px` and `100% / 8 px`. Scanlines are drawn last at physical-output resolution. The halo uses bounded cached bitmap draws rather than runtime blur or shaders. Wheel updates are capped at 10 fps; bounded SOC/power transitions stop on completion; stationary/background/detached views schedule no frames.

**Reason:** The earlier v27 implementation treated the user's exact edits as approximate guidance, redrew the vehicles abstractly and rearranged labels. The user explicitly rejected that interpretation and completed the pixel layout and vehicle art manually. Shipping those authored masks and coordinates is now the most faithful and lowest-risk presentation implementation.

**Consequences:** D-055 recovery behavior remains required and unchanged. Bluetooth/ELM, protocol, decoder, `SignalStore`, scheduler/request periods, logging evidence and Idle Check eligibility are untouched. Primary GUI acceptance is the target API 27/1280×720 head unit; phone portrait remains deferred. Local source/build work is authorized, but installation, push, PR and publication are not.

---

## D-057 — Steady wheel animation must not rebuild the complete pixel dashboard

**Status:** Local implementation/build/API 26 comparison complete; target-head-unit follow-up pending — 2026-09-02

**Decision:** Preserve D-056's exact coordinates, raster assets, CRT settings and animation semantics while separating the steady 10 fps wheel sprite from the full-page cached composition. Static and signal-driven page content, including its bounded eight-direction halo, is recomposed only when its visible result changes. A wheel step draws only the cached wheel frame and its local halo over that page. Final-pass scanlines retain the same physical 720p phase, width, gap and opacity but use one bounded repeating bitmap pattern rather than hundreds of per-frame line commands. Repeated signal versions whose displayed value and transition target are unchanged must not restart a 300 ms animation.

**Reason:** Five V0.3.5/v28 target sessions show roughly 68.6–71.7% of one CPU core versus about 16.6–18.6% for the earlier V0.3.3/v25 UI. Code review found that every wheel step marked the entire 1280×720 composition dirty, rebuilt all layers, then redrew eight full-screen halo copies and every scanline. The repaired API 26/1280×720 fixture independently records 103/103 janky steady frames with a 40 ms median under software graphics. Acquisition and logging are healthy; renderer work is consuming headroom intended for the later high-rate scheduler.

**Consequences:** This is presentation-only. It adds one bounded 1280×720 cached frame and a tiny scanline pattern, but no steady-state allocation, new dependency, blur, RenderEffect, general shader framework or changed frame-rate cap. The same API 26/1280×720 AVD reduces the 12-second steady preview from 103/103 janky frames at a 40 ms median and 9 slow bitmap uploads to 59/104 janky frames at a 20 ms median and zero slow bitmap uploads; sampled app CPU falls from the original 4–12% range to a 0–8% range averaging about 3.5%. Total PSS is 35,894 KB, accepting a small bounded memory increase for the full-frame cache. Complete-dashboard and settings-overlay screenshots retain the authored layout. An attempted dirty-rectangle wheel invalidation was rejected and removed because the API 26 buffer did not reliably preserve untouched regions. Production/test compilation, 111/111 direct JUnit, lint (0 errors / 9 warnings), assemble, package identity and fixed v2 signature pass. The 2,656,023-byte dirty-worktree APK has SHA-256 `b8cfe5d5497e13eb30a8eb4d02695ae2eed9de50a91bac265cd74af2cc28bbab`. Target-car CPU remains the governing follow-up. Scheduler, periods, protocol, decoder, `SignalStore`, logger/session behavior and recovery are frozen; no push, commit, PR or publication was performed.

**Target follow-up:** The exact D-057 APK was tested on the API 27 / 1280×720 head unit. Moving weighted process CPU is `89.10%` versus old-v28 approximately `72%`, while parked/ICE-off falls to `17.17%`; memory and transport remain healthy. D-057 is therefore not an acceptable high-rate-scheduler foundation. The likely regression is dynamic 20 fps invalidation repeatedly performing full-page software halo composition and texture upload; direct rebuild-rate/cost instrumentation is still absent.

---

## D-058 — Remove full-page software halo composition before attempting region caching

**Runtime evidence addendum (2026-09-06):** Authorized reboot/emulator recovery succeeded. Six local UI-only captures complete with no skipped inputs; no product/debug source changes were made. Settings/Idle/self-test sampling works, but same-emulator exact D-057 fixed-preview comparison exposes R1 halo-pixel differences outside moving wheels; old/current D-057 pixels match outside wheels. Do not silently waive frozen visual equivalence, call R1 promoted, infer target CPU from the local 11.23–11.54% stress5 range, or jump into R2/HA. Localize the compositing discrepancy and obtain same-input dynamic historical A/B before the next architecture decision. Raw evidence and limits: `../../outputs/d058-runtime-20260906/D058_RUNTIME_REPORT.md`.

**Local validation addendum (2026-09-05):** Fixed-value/wheel-only preview evidence is insufficient for a dynamic renderer decision. Add debug-only recorded display-frame replay and separately labelled synthetic 5 Hz changing snapshots, without changing vehicle truth or acquisition. Calibrate by exact APK and scenario using monotonic CPU intervals; flag wall-clock jumps and mixed intervals rather than silently treating them as clean moving/parked observations. Runtime measurements and visual equivalence remain unverified until the emulator can execute; host virtualization changes require separate authorization. No additional vehicle experiment is requested merely to compensate for this local test gap.

**Status:** R1 local source/build complete; GUI and target-car verification pending — 2026-09-05

**Decision:** First implement a minimal presentation-only R1: retain the sharp 1280×720 page bitmap and D-057 no-op suppression, but remove the second full-page cached bitmap and its Bitmap-backed eight-copy halo composition. Draw the eight-direction page halo, sharp page, independent wheel and repeating scanlines in the same output order on the View canvas. When the settings overlay hides the wheel, wheel-only animation must not keep scheduling frames. Debug-only bounded counters may measure rebuild wall/thread-CPU cost and trigger counts through low-frequency logcat; logger/evidence schemas remain unchanged.

If R1 preserves exact pixels but cannot reach the recommended target-car moving CPU gate (`<55%`, parked/ICE-off `<=20%`), R2 may divide dynamic content into fixed preallocated regions over a cached static page. R2 groups must be generated from the transitive overlap of rectangles expanded by the maximum halo radius—not merely the unexpanded content rectangles—so a later opaque background patch cannot erase a neighbouring halo. Each group retains existing z-order. Full-frame presentation remains mandatory; local dirty-rectangle invalidation is still prohibited.

**Reason:** D-057 proves stable-page caching works at rest but turns frequent dynamic updates into expensive CPU memory blending. R1 is the smallest reversible test of that root cause and preserves the current compositing order. Region caching offers more headroom but carries substantially higher seam/z-order/restore risk, so its complexity is justified only by R1 target evidence.

**Consequences:** D-058 changes only pixel-dashboard presentation code and its tests/docs. It cannot change coordinates, assets, CRT settings, signal semantics, frame-rate caps, scheduler/periods, protocol, decoder, `SignalStore`, logger/session/recovery or Idle Check eligibility. Capacity rejects must be compared as a rate per releases and collision window, not by raw count across unequal sessions. Idle Check hysteresis is a later independent decision; HA/5 Hz remains frozen until renderer moving CPU has adequate headroom.

**R1 local result:** `PixelDashboardView` removes the second full-page Bitmap/Canvas, moves unchanged halo composition to the View Canvas, adds bounded debug-only wall/thread-CPU counters and reports whether a hardware Canvas was observed. `PixelDashboardModel.animationFrameNeeded` makes settings/animation scheduling testable; its new regression test proves wheel-only frames stop behind settings while content transitions continue. Production/test compilation, 112/112 direct JUnit, lint (0 errors / 9 warnings), assemble, package/version and fixed-v2 signature pass. APK SHA-256 is `33021cb603fb760e92fd190cedffcf633ef1ef51541da5360dfcae3f3ee7fafa`. The host emulator currently lacks its hypervisor driver and software acceleration did not reach ADB, so no D-058 screenshot or performance claim is made; target verification controls promotion.
