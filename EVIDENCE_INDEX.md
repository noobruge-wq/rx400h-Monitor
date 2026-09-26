# RX400h Monitor — EVIDENCE_INDEX

## D-064 pure realtime candidate — 2026-09-23

- Scope/review: `D064_REALTIME_CLOSURE.md`, `D064_LOCAL_REVIEW_20260923.md`.
- Artifacts/source/D063diff/manifest/R8mapping: `../../deliverables/D064-realtime-local-20260923/`; benchmarkSHA `3a4ed745b18eb19843b04e3d038047d383301182161d41b67f835ac8376496f5`, debugSHA `748151c02125f928c4010c120275ca3321fa2fc0ce6c7a57effaeeec567ae902`.
- Evidence: `../../outputs/d064-realtime-20260923/`: `build-final.log`, `junit-final-sep14.log` and `junit-final-sep09.log`92each/41,143replies; `native-smoke-final/`10checks; `pixel-final/`40samples/157frames; `gui-debug-final/` and `gui-benchmark-final/` matching8stableimages; signature and manifest files.
- `gradle-unit.log` is host-worker startup failure, NOT a pass. Immediate screenshot captures system transition, NOT actual-first-frame proof. Real LIVE/Bluetooth/music/sleep not checked by this emulator. Earlier failed/partial tooling outputs retained as history; only final named results apply.

## D-063 local lean/runtime candidate — 2026-09-17

- Review/scope: `D063_LOCAL_REVIEW_20260917.md`, `D063_LEAN_RUNTIME_PLAN.md`, `D063_MXPLUS_RESEARCH.md`.
- Artifacts/source/D062diff/exactmanifest/R8mapping: `../../deliverables/D063-lean-runtime-local-20260917/`; benchmarkSHA `bcf479211dc29f01d45ff17ec6efabd0f1bb165d3e80a914791c4e4d5f3a507d`, debugSHA `10da73a8b7353d93419436dd0f9f42a0b70650e36ce98cb0372718ba4c0b70f5`.
- Evidence root `../../outputs/d063-lean-runtime-20260917/`: `build-final-02.log`; `junit-final-02-sep14.log` and `junit-final-02-historical.log`150each with27,678+13,465replies/48,000schedulersteps; `file-json-smoke-final/`8/8; `pixel-oracle-final/`146frames; `native-gui-debug-final/` and `native-gui-benchmark/` six identical screenshots; `strategy-gui-debug/` same-production pre-fixture-strengtheningdebug persistence; final signature files.
- `gradle-unit-attempt.log`: known host worker class startup failure, not product-test pass. `junit-final-02-*.log` contains raw microbenchmark rounds; restricted to steady-search work, no new whole-app/car speed claim.
- Benchmark excludes debug fixtures: Android JSON/file suite ran debug; exact shrunk benchmark ran main/device/settings/resume, not actual LIVE/end/export. Original D062/evidence kept.

## D-062 local lean candidate — 2026-09-17

- Review/gates/limitations: `D062_LOCAL_REVIEW_20260917.md`; approved scope `D062_LEAN_PLAN.md`.
- Source/APKs/manifest/D061 diff: `../../deliverables/D062-lean-local-20260917/`.
- Evidence: `../../outputs/d062-lean-20260916/`: `build-final-03.log`, final JUnit logs for27,678+13,465transactions, `pixel-oracle/`148frames, `lifecycle/`7/7, `gui-debug/`, `native-gui-debug/`, `native-gui-benchmark/`, `native-gui-d061-control/`, `runtime-summary.json` and exact-APK replay directories/signature/manifest logs.
- Standard Gradle worker failure in `gradle-tests.log` is not a product test pass. Main source−15.1%, car-test APK−13.9%, UI-only CPU essentially flat9.96→9.88%. No real-car5Hz or calibrated80% emulator claim.

This is a compact evidence locator. Original evidence files must not be edited in place.

## D-061 A+B local candidate — 2026-09-12

- Scope/results/open gates: `D061_AB_PLAN.md`, `D061_LOCAL_REVIEW_20260912.md`.
- Candidates/source/manifest/D060-only delta: `../../deliverables/D061-AB-local-20260912/`; benchmark SHA256 `ea2525dc148dfc5247bc3f1ed7c623cfd17e6213a421330d1838c820522d8835`, debug `56779960efde0668f0aaf33d366cb229133c48ac3e6dac6b69a7ccec23cf4db7`.
- Evidence root `../../outputs/d061-ab-20260911/`: `pre-change/`, `build-final.log`, `junit-final.log`165pass, `standard-test-01.log`hostworkerfailure, `signature-*.log`, `python-tests.log`, `summary-tests.log`. Preserve earlier failed build/tool-environment outputs separately.
- `parser-corpus.tsv`:13,465transactions derived from unchanged September9ZIP; original archive SHA `b8e7ea50…996e3`, corpusSHA `50691468…bb68`; generator/test source checked in local snapshot, corpus not embedded in source.
- `pixel-debug-final`148frames / `pixel-benchmark-final`146frames independent full-sharp+halo oracle. `gui-debug-final` / `gui-benchmark-final` authored1280×720/actualView1280×672 settings+persistence; benchmark preview excludes two unsynchronized wheels, other screenshots compare full frame. `smoke-debug-final` / `smoke-benchmark-final` isolatedAndroidfilefault7/7each.
- `perf-old-debug-1`, `perf-new-debug-1`, `perf-new-debug-2`, `perf-new-benchmark-1`: exact installed SHA/runId/config/rawlogs; `runtime-summary.json` final weighted metrics. All are UI-only, same1core/actual1.5GB/SwiftShader, not MX+ throughput or80% car-fit. Synthetic frame/publish counts are not ECU Hz.

## D-060 local closure checkpoint — 2026-09-08

- Review and remaining gates: `D060_LOCAL_REVIEW_20260908.md`; exact nine-group ledger: `D060_REQUIREMENTS_CLOSURE.md`.
- Candidate APK/source hash inventory: `../../deliverables/D060-local-20260908/`; APK SHA-256 `db6b33fa2038ae6f9711fc87e0d83907d99fb09d42d1d3b8f156f51965811297`. Includes untracked source/assets in manifest, not just tracked diff.
- Build/140 direct JUnit/21 Python/signature logs: `../../outputs/d060-validation-20260907/` (`*-final.log`). Standard Gradle worker failure is not relabeled as a pass.
- Final APK's Android isolated file-fault7/7: `lifecycle-final/`; true close-fault reproduction: `lifecycle-close-red-corrected/` by UUID; initial `lifecycle-close-red/` is a test setup failure, not product root cause.
- Final settings/1280x720 PNG and persistence: `gui-final/`; whole720p dynamic exact composition100frames: `pixel-oracle-final/`.
- Same-APK full-software/cached stress5 controls: `stress5-tiles-*`(4core/2GB) and `stress5-final-1core-*`(1core/actual1.5GB). Current delivery repeat: `stress5-delivery-1core-01/`. `runtime-summary.json` retains all runs, including earlier nonfresh-process controls which are excluded from the main comparison.
- Approx22% UI-only CPU reduction and pixel parity do not certify real OBD5Hz, full LIVE handoff or80% calibrated vehicle equivalence. Original host logs/layout/vehicle art untouched.

## D-058 actual runtime and visual hold — 2026-09-06

- Latest report: `../../outputs/d058-runtime-20260906/D058_RUNTIME_REPORT.md`.
- Six completed controls (3 stress5, 1 each parked/wheel/recorded), exact installed identity, screenshots/logcat/gfxinfo/meminfo: `../../outputs/d058-runtime-20260906/`; all 3,868 inputs accounted for, no skips.
- Per-window and per-scenario statistics with raw log SHA: `summary.json`; new offline-analysis regression log `analysis-tests.log` (10/10).
- GUI settings/Idle/self-test evidence: `gui-01/`; no-wheel settings restoration pixels match exactly.
- Exact D-057 same-emulator static control: `d057-same-avd-preview/metadata.json`. Full R1 visual equivalence NOT PASSED: `gui-01/same-avd-pixel-checks.json` shows halo differences; old-versus-old historical/current non-wheel pixels match.
- Runtime confirmed only on local API 26 / x86_64 / SwiftShader; not target API 27, dynamic D-057 A/B or weak-car fit. No source/APK change or promotion. Earlier no-install/no-GUI statements below describe 2026-09-05.

## D-058 local dynamic validation follow-up — 2026-09-05

- Review/run instructions: `D058_DYNAMIC_VALIDATION.md`.
- Build/direct-JUnit/Python/preflight logs: `../../outputs/d058-dynamic-validation/`.
- Corrected exact-APK/monotonic/scenario analysis: `../../outputs/emulator-calibration-v2/REAL_CAR_BASELINE.md` and `inventory.json` (21 sessions, 7 exact APKs + 1 legacy group).
- Recorded display input: `app/src/debug/assets/renderer_replay/recorded.csv`, SHA-256 `214b1f95151ebc17ea285945fe866721d5a8e5f52dee175ce07e28bb7f97cca9`, with source provenance in `recorded.json`.
- Debug validation APK: `../../deliverables/RX400h-Monitor-v0.3.5-v28-D058-dynamic-validation-debug.apk`, SHA-256 `b388efed7e3a09153d8b2c2428d130ea07b963e0e14d01ced65b0193220e68b7`.
- 120/120 direct JUnit, 11/11 Python checks, lint 0 errors/9 warnings and assemble/signature pass. No installation, GUI, frame-rate or dynamic CPU pass; acceleration preflight remains blocked. This is not a promoted baseline.

---

## A. Valid source/build baselines

### V0.1.8 source

```text
RX400hProtocolProbe_v0.1.8_source.zip
SHA-256: 174d5de7e8a295860f5e5577ab4d2e48e247ab674f4fbbc0b88a97d52a3d7cef
```

Role: first successful evidence-driven Toyota runtime baseline.

### V0.1.10 cleanup candidate source

```text
RX400hProtocolProbe_v0.1.10_cleanup_candidate_source.zip
SHA-256: e938169f03a38f4ef855ad72c71176094a7379002350e792378e2c4b9170ce2c
```

Role: cleanup candidate built strictly from V0.1.8.

### V0.1.10 GitHub fixed-signing package

```text
RX400hProtocolProbe_v0.1.10_cleanup_GitHub_AutoBuild_SIGNED.zip
SHA-256: 7d7975a6d24dfbe8e6e1c797db1879dd3d2c6cd8292216b044c27fc0366236b7
```

Role: phone-first/GitHub Actions package using the fixed debug-signing workflow.

### V0.2.0 Reactive Core source

```text
Baseline: V0.2.0 (closed 2026-08-09), tag v0.2.0
Closure commit: 8a7aef44f891985fcc41b0bf4a36ca7fba1e40ef
GitHub Actions run: 31299528505 — success (decoder 002 final)
Artifact: RX400hProtocolProbe-v0.2.0-reactive-debug-signed
APK SHA-256: 207f28409c89ad7b8650f1aaf92426be52c77abe14bf2a9d816d2772e119f7be
Unit tests: 17 passed
Source snapshot: RX400hProtocolProbe_v0.2.0_source.zip
SHA-256: f431dd0107b9c6bb807f37bf6c71e1d9d52ebc1261995659baaefad4af35c5f2
```

Role: V0.2.0 Reactive Core implementation baseline (SignalStore, change-driven UI, consumer audit, performance observability, scheduler table). Idle Check has natural E1 + deterministic replay support; further natural observations are tracked in V0.3.0.

### V0.2.1 UI adjustment

```text
Commit: 4872045177ec7cb91416a5bb007eb6952dd52290 (main)
GitHub Actions run: 31308752139 — success
Artifact: RX400hProtocolProbe-v0.2.1-ui-debug-signed
APK SHA-256: 701ecb4e00706aba468940d47ff3145d6914fd5ce81e2655fb8f7c1e8b3859ca
Unit tests: 17 passed
```

Role: head-unit UI adjustment after V0.2.0 closure — taller top bar, centered and enlarged control buttons. No protocol or scheduler changes.

### V0.3.0 UI header candidate (branch v0.3.0)

```text
Commit: 8cd2e00f39ac0862467b32a42d1eeaf6f6ee2c9a (v0.3.0)
GitHub Actions run: 31314095899 — success
Artifact: RX400hProtocolProbe-v0.3.0-ui-header-debug-signed
APK SHA-256: f94ae4aafb45e57bb254074541ebe44c37d934fc4de7dce327db3a7c218ba08c
Unit tests: 17 passed
```

Role: first V0.3.0 development build — wide-screen header becomes a single row (title / buttons / status), narrow layouts keep the two-row fallback (D-033). Layout-only; no protocol or scheduler changes.

### V0.3.0 header v2 candidate (branch v0.3.0)

```text
Commit: e9479babbe2cd67711692c9970b7de116aad22cc (v0.3.0)
GitHub Actions run: 31315386621 — success
Artifact: RX400hProtocolProbe-v0.3.0-header-v2-debug-signed
APK SHA-256: 27b013c68cc822c59d3f0ca5f320c7150a4c9ea5a71947fe60880ca21427f1d6
Unit tests: 17 passed
```

Role: header v2 + Chinese display contract (D-034) — two-line fixed-size title/status text, centered buttons that do not squeeze the text zones, uniform value typography except battery MAX/MIN, POWER order 混动功率/引擎功率/转速 and permanent gray 怠速检查. Layout-only; no protocol or scheduler changes.

### V0.3.0 header v3 candidate (branch v0.3.0)

```text
Commit: 6a18f18217f5ff3995b6fbb51b86a50c124c0ef5 (v0.3.0)
GitHub Actions run: 31316526865 — success
Artifact: RX400hProtocolProbe-v0.3.0-header-v3-debug-signed
APK SHA-256: 399d268c3781324373b8392258c8393304c9d27ef12408cefa6879fef5d483c2
Unit tests: 17 passed
```

Role: header v3 (D-035) — text row first (two-line title + widened multi-line status) above a full-width button row; buttons narrower/taller and squeezed by text; Chinese centered domain titles (能量域/车辆域/动力域), label/value lines and doubled fonts except battery MAX/MIN and the permanent gray 怠速检查. Layout-only; no protocol or scheduler changes.

### V0.3.0 header v4 fix candidate (branch v0.3.0)

```text
Commit: 0f791110808dd62eb8b9553b95a4d0e8ee2aa79f (v0.3.0)
GitHub Actions run: 31318586138 — success
Artifact: RX400hProtocolProbe-v0.3.0-header-v4-debug-signed
APK SHA-256: 347841bdbd38c37bf2fb92e7823841016a19bcf17db02da095b878edf444d5dc
Unit tests: 17 passed
```

Role: fixes domain card child ordering — each label is immediately followed by its value line (电量/`--.-%`, 温度/`--.-°C`, 最高 最低/`--.-°C--.-°C`), matching the user's example. Layout-only.

### V0.3.0 header v5 candidate (branch v0.3.0)

```text
Commit: e971ded91823754750d1d74a716cc21a287ace53 (v0.3.0)
GitHub Actions run: 31318832012 — success
Artifact: RX400hProtocolProbe-v0.3.0-header-v5-debug-signed
APK SHA-256: 4dd73e132d543351f8cd8891055e6810096f30088fe85ee12dfdece2d0e8c349
Unit tests: 17 passed
```

Role: header v5 (D-036) — buttons return inside the header row on wide screens (title / buttons / widened multi-line status), removing the separate button row so vertical space is not wasted; narrow screens keep the scrollable button row. Layout-only.

### V0.3.0 header v6 candidate (branch v0.3.0)

```text
Commit: 5cc642679f6896cdea1a9a1d73703a2c1daa4072 (v0.3.0)
GitHub Actions run: 31319195137 — success
Artifact: RX400hProtocolProbe-v0.3.0-header-v6-debug-signed
APK SHA-256: f192a05600d00d455460f41261d0791eefe5657e07f48ddb7331a5cee0f6455d
Unit tests: 17 passed
```

Role: screen-proportional typography and controls (D-037) — fonts and button/header metrics scale with the screen's short side (reference 720dp, floor 0.5); factor is 1.0 on the target head unit. Layout-only.

### V0.3.0 header v7 candidate (branch v0.3.0)

```text
Commit: 4df826cbae7de5c803372c22af6bb11d0d5c8ae1 (v0.3.0)
GitHub Actions run: 31319464972 — success
Artifact: RX400hProtocolProbe-v0.3.0-header-v7-debug-signed
APK SHA-256: bb2b5fdc0034f626d647e25ed6a0b217efb63b446c42351b81cdb7cd6f940cf5
Unit tests: 17 passed
```

Role: all layout elements scale with the same screen proportion (D-038) — root/card/button paddings, margins, separator, corner radius, header/button geometry, with a 1px floor; narrow fallbacks and ellipsis keep text inside the displayable area. Layout-only.

### V0.3.0 responsive UI candidate (branch v0.3.0)

```text
Commit: 1cce7304921e21e0ecbdab43f852877676b88b2d (v0.3.0)
GitHub Actions run: 31320031870 — success
Artifact: RX400hProtocolProbe-v0.3.0-responsive-debug-signed
APK SHA-256: 6d4873e02466d1fb492abf0c95b9cea53ffe3dd85f4401784025da9e203dff2f
Unit tests: 19 passed (incl. ResponsiveLayoutTest)
```

Role: size-independent responsive/adaptive UI (D-039) — window-size-driven dynamic card columns (240dp min, max 3), width-based header mode, vertical scroll instead of text shrinking, live resize re-layout by bucket. Layout-only; visual pass at multiple window sizes is pending user device testing.

### V0.3.0 scheduler core candidate (branch v0.3.0)

```text
Commit: fd3024011fd2d6566d42093a68cd3f9e9ffbe5d3 (v0.3.0)
GitHub Actions run: 31320500825 — success
Artifact: RX400hProtocolProbe-v0.3.0-scheduler-debug-signed
APK SHA-256: 8cde4227cdc1fd45efa7f133c152b1664678bd95f9b714f023ca28ab194019fe
Unit tests: 26 passed (incl. DeadlineSchedulerTest + LatencyWindow)
```

Role: V0.3.0 scheduler phase first candidate (D-040) — `DeadlineScheduler` (independent periods, priority/header order, deadline skip/backpressure), `LatencyWindow` P50/P95/P99, scheduler columns in `performance.csv`, `SCHEDULER_PROFILE = v030_deadline_001`. Rates still at V0.2.0 periods; real-vehicle ladder tests pending.

### V0.3.0 bottom-align candidate (branch v0.3.0)

```text
Commit: 14125dd723570f2be0302074798a72d9050eaf79 (v0.3.0)
GitHub Actions run: 31320956460 — success
Artifact: RX400hProtocolProbe-v0.3.0-bottom-align-debug-signed
APK SHA-256: 2b8c022c1af8b13ec8c17ce990991e0bfacb17f6db6ab672fd30b4fa28509fbb
Unit tests: 26 passed
```

Role: equal-height cards per row (each fills the tallest card) with a bottom-anchored, viewport-filling data area so all domain frames share one bottom edge aligned to the screen bottom. UI-only.

### V0.3.0 fluid responsive UI local pre-CI candidate (branch v0.3.0)

```text
Base HEAD: f1f7b2d3ab3d70fec519fc0ba4745f2981ac93e9 (v0.3.0)
Commit: pending — intentional uncommitted worktree
GitHub Actions run: pending
Candidate artifact: RX400hProtocolProbe-v0.3.0-fluid-ui-debug-signed
Local APK: app/build/outputs/apk/debug/app-debug.apk
Local APK SHA-256: 427a1c0e1950b4154a613e1a7173f9c3c8b5ea372460affec3444dd6863d0364
Signature: APK Signature Scheme v2; fixed certificate SHA-256 77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
Unit tests: 38 passed (14 ResponsiveLayoutTest)
Lint: 0 errors / 10 non-blocking warnings
```

Role: D-041 local candidate — stable native View tree, exact policy/runtime row geometry, inset-safe whole-page scrolling, component-bounded card/control contracts, safe header hysteresis, and restored POWER / active-only Idle Check contract. API 37 View testing covered phone portrait/landscape, 4:3, 16:9, 16:10, tablet, split/freeform-sized, ultra-wide, extreme-wide/short, 200dp narrow with font scale 2.0, and bidirectional threshold sweeps without Activity replacement or crash. This record is local evidence only; do not promote it to a remote build baseline until a commit and GitHub Actions signed artifact exist.

### V0.3.1 resilient controls/logs local candidate

```text
Branch: v0.3.0
App version: 0.3.1
versionCode: 23
Candidate artifact: RX400hProtocolProbe-v0.3.1-resilient-logs-debug-signed
Commit: pending
GitHub Actions run: pending
Local APK size: 2,503,690 bytes
Local APK SHA-256: bd3b252e09f193f3754e8f7d0597ef72841ad8e964e431ba3fd85690d0ae14a3
Signature: APK Signature Scheme v2; fixed certificate SHA-256 77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
Unit tests: 62 passed / 0 failed / 0 skipped
Lint: 0 errors / 9 non-blocking warnings
Manifest identity: applicationId com.guanyu.rx400hprobe.debug; versionName 0.3.1-debug; versionCode 23; minSdk 26; targetSdk 35
```

Role: local, dirty-worktree evidence for D-041 responsive UI plus D-043 three-button, single-owner session flow and D-044 durable checkpoints/interrupted-session recovery/public archive publication. 62 JVM tests and compile/lint/assemble/signature checks passed using JDK 17 and checksum-verified Gradle 8.9. Build provenance records base commit `f1f7b2d3ab3d70fec519fc0ba4745f2981ac93e9` and `git_dirty=true`. API 26 (Android 8.0) emulator installation, cold launch, three-control states, DevicePicker empty state, portrait/landscape reflow, scroll reachability and same-Activity rotation were exercised without fatal crash. The emulator had no paired Bluetooth OBD adapter, so it did not validate the real connect→LIVE path, legacy public-Downloads grant/deny, completed archive publication or forced-interruption recovery. Do not treat this hash as a remote baseline: exact-commit GitHub Actions, API 27 and paired-device save/recovery smoke are still pending.

### V0.3.2 capacity-aware scheduler local candidate

```text
Branch: v0.3.0
Base commit: e58d9f9dd60197882a3d41f5d78b304a52f663f7
Implementation commit: 8e55c6afae20ca64b9ea9bba5861bc85d8017c62
Remote branch: origin/v0.3.0
App version: 0.3.2
versionCode: 24
Scheduler profile: v030_capacity_002
Candidate artifact: RX400hProtocolProbe-v0.3.2-capacity-scheduler-debug-signed
GitHub Actions run: 31635798035 — success
GitHub Actions URL: https://github.com/noobruge-wq/rx400h-Monitor/actions/runs/31635798035
CI APK size: 2,554,982 bytes
CI APK SHA-256: 841b1a4adb9f9e4a1834d2830dd6e94754a54cbcc3b2b3209023061da1969e9b
Local APK size: 2,557,574 bytes
Local APK SHA-256: a8bc90fb35a2c0f8e1c41b517b9016ef42444f1102d15e2ab2518de7343bb347
Signature: APK Signature Scheme v2; certificate SHA-256 77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
Unit tests: 72 passed / 0 failed / 0 errors / 0 skipped
Lint: 0 errors / 9 non-blocking warnings
Manifest identity: applicationId com.guanyu.rx400hprobe.debug; versionName 0.3.2-debug; versionCode 24; minSdk 26; targetSdk 35
```

Role: D-046 exact-source build evidence. GitHub Actions rebuilt clean commit `8e55c6a`, passed the configured unit/lint/assemble gates, verified embedded commit/clean provenance, verified the fixed v2 signing certificate and uploaded the named artifact. The scheduler covers epoch-anchored releases, one-transaction header/request replanning, conserved per-request terminal outcomes, transport-down accounting, fail-closed admission, mutually exclusive legacy deadline/skip compatibility counters, streaming evidence and prompt-loss/I/O-failure reconnect enforcement. The seven requests, headers, commands, decoder and target periods are unchanged. Later API 27 paired-OBD sessions listed below validate normal connection → LIVE → End/public-save and same-period behavior. Forced interrupted recovery remained open for the V0.3.2 artifact; D-051 trusted-model integration is implemented only in the later V0.3.3 source candidate.

### V0.3.3 recovery/cost/CRT exact-clean local candidate

```text
Branch: v0.3.0
Base commit before implementation: 3b48d2dd7ab5d85c99b94336320e19a1b4bd3787
Implementation commit: c9ad39759021fd8d4ca529b115ab7932aa2bf8d8
App version: 0.3.3
versionCode: 25
Protocol profile: rx400h_ha_hci_20260805_002 (unchanged)
Decoder profile: rx400h-reactive-20260808-002 (unchanged)
Scheduler profile: v030_capacity_002 (unchanged)
Local APK filename: RX400hProtocolProbe-v0.3.3-recovery-cost-crt-debug-signed.apk
Remote CI / push: not performed
Local APK size: 2,589,038 bytes
Local APK SHA-256: 2d75bd7d1bc6be8a923495e901c05ce904d58966f8e10ff3e12b2e980a33d0a1
Embedded provenance: GIT_COMMIT=c9ad39759021fd8d4ca529b115ab7932aa2bf8d8; GIT_DIRTY=false
Production/test compilation: pass
Compiled direct-JUnit result: 98 passed / 0 failed / 0 errors
Gradle test worker: host infrastructure failure before test execution (GradleWorkerMain / closed pipe)
Lint: 0 errors / 9 non-blocking warnings
assembleDebug: pass
Manifest identity: applicationId com.guanyu.rx400hprobe.debug; versionName 0.3.3-debug; versionCode 25; minSdk 26; targetSdk 35
Signature: APK Signature Scheme v2; certificate SHA-256 77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
Exact-artifact GUI smoke: debug-only DashboardPreviewActivity on API 26 at 1280x720, 360x800 and 800x360; portrait/landscape scroll reachability and active-only Idle Check captured; this is presentation evidence, not MainActivity/session-flow evidence
Exact-artifact 4:3 recapture: host-blocked after the API 26 cold-boot image stopped reaching ADB; no 4:3 result is claimed
```

Role: exact-clean local D-047…D-052 implementation evidence. It adds the explicit recovery phase/fast path, first-writer-wins terminal intent and idempotent recovery, pinned API 27 direction-aware cost model, observational clock-step logging and the fixed CRT Green presentation. Protocol, decoder, SignalStore, request whitelist and all seven periods/phases/deadlines remain unchanged. The APK is bound to clean implementation commit `c9ad397`; it is not a GitHub Actions artifact. It was subsequently installed on the target and produced the V0.3.3 normal-flow sessions listed below. The earlier D-047 prototype retains an API 26 800x600 visual record, but the exact V0.3.3 4:3 recapture is separately host-blocked and not claimed. Forced interrupted recovery remains pending.

### V0.3.4 target first-frame/daylight CRT local candidate

```text
Branch: v0.3.0
Base commit before implementation: 243001dad90fadf9cba7a00fffd6ac5069ada590
Implementation commit: b60619d5c1f4e011508b3cf74de6fee7422ec720
App version: 0.3.4
versionCode: 26
Protocol profile: rx400h_ha_hci_20260805_002 (unchanged)
Decoder profile: rx400h-reactive-20260808-002 (unchanged)
Scheduler profile: v030_capacity_002 (unchanged)
Local artifact: RX400hProtocolProbe-v0.3.4-b60619d-target-crt-ui-debug-signed.apk
APK size: 2,591,078 bytes
APK SHA-256: fa88064be3450ccb8765217ed0f12e97e01793c66434b07b0901777b4072002b
Embedded provenance: GIT_COMMIT=b60619d5c1f4e011508b3cf74de6fee7422ec720; GIT_DIRTY=false
Remote CI / push: not performed
Production/test compilation: pass
Compiled direct-JUnit result: 99 passed / 0 failed / 0 errors
Gradle test worker: known host infrastructure failure before test execution (GradleWorkerMain / closed pipe)
Lint: 0 errors / 9 non-blocking warnings
assembleDebug: pass
Manifest identity: applicationId com.guanyu.rx400hprobe.debug; versionName 0.3.4-debug; versionCode 26; minSdk 26; targetSdk 35
Signature: APK Signature Scheme v2; certificate SHA-256 77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
Local GUI: not claimed; API 27 image absent and API 26 emulator stops before ADB under hardware and software acceleration
Target API 27 1280x720 natural-first-frame gate: pending
```

Role: exact-clean local D-053 presentation implementation evidence. The complete real-window layout key covers width, height, safe insets, density and font scale; one attach-scoped listener suppresses at most two pre-draws before removing itself and always releasing the third draw; the debug preview's private three-pass workaround is removed. Static scanlines are strengthened, the green hierarchy is brighter and primary values/titles/buttons use a cached bold monospace. No core, scheduler, request period, protocol, decoder, SignalStore, logger or session-lifecycle source changed. The artifact is bound to clean implementation commit `b60619d`; it is not a GitHub Actions artifact and has not been installed. The actual API 27/1280x720 natural-first-frame gate remains open.

### V0.3.5/v27 signal-driven CRT + non-blocking recovery local candidate

```text
Branch: v0.3.0
Base HEAD: 8f1a3b9fb49359484c283ea6c3eaf69c631b4b45
App version: 0.3.5-debug
versionCode: 27
Local artifact: ../../deliverables/RX400h-Monitor-v0.3.5-v27-signal-ui-recovery-debug.apk
APK size: 2,612,662 bytes
APK SHA-256: 0b803f3cf3759d96e350e6ea4f18146ae263c1250cfa557780d8011a84bd533e
Embedded provenance: GIT_COMMIT=8f1a3b9fb49359484c283ea6c3eaf69c631b4b45; GIT_DIRTY=true
Production/test compilation: pass
Compiled direct-JUnit result: 113 passed / 0 failed / 0 errors
Gradle test worker: known Windows/Unicode-path launch failure before test execution
Lint: 0 errors / 9 non-blocking warnings
assembleDebug: pass
Manifest: applicationId com.guanyu.rx400hprobe.debug; minSdk 26; targetSdk 35
Signature: APK Signature Scheme v2; certificate SHA-256 77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
GUI / paired-OBD / forced-recovery smoke: pending manual target-device verification
Review: CODE_REVIEW_V0.3.5.md
```

Role: local D-054/D-055 implementation evidence. Manual JSON offsets are normalized into exact target-landscape geometry; the three picture references are recreated as signal-driven native Canvas schematics; Idle Check uses dim/full brightness levels while preserving core thresholds; `SAVE_FAILED` permits Device/Start/End after safe ownership release and retains old evidence. Protocol, decoder, SignalStore, scheduler, request periods and evidence-integrity rules remain unchanged. The worktree is intentionally dirty and this is not a remote CI artifact or promoted baseline.

### V0.3.5/v28 exact pixel dashboard local candidate

```text
Branch: v0.3.0
Base HEAD: 8f1a3b9fb49359484c283ea6c3eaf69c631b4b45
App version: 0.3.5-debug
versionCode: 28
Local artifact: ../../deliverables/RX400h-Monitor-v0.3.5-v28-pixel-dashboard-debug.apk
APK size: 2,656,023 bytes
APK SHA-256: 020a0d9fb05e718e0eef6e100edbdd306dcba244f7b4017b67e2967f048d30f6
Final layout JSON SHA-256: 1f3914510943261f3c2d29f4d8eaf4abca7236b5676e0584948c2839b63518aa
Embedded provenance: GIT_COMMIT=8f1a3b9fb49359484c283ea6c3eaf69c631b4b45; GIT_DIRTY=true
Production/test compilation: pass
Compiled direct-JUnit result: 111 passed / 0 failed / 0 errors
Gradle test worker: known Windows/Unicode-path launch failure before test execution
Lint: 0 errors / 9 non-blocking warnings
assembleDebug: pass
Manifest: applicationId com.guanyu.rx400hprobe.debug; minSdk 26; targetSdk 35
Signature: APK Signature Scheme v2; certificate SHA-256 77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192
API 26 GUI: 2026-09-01 retry cleared the stale lock, bypassed original userdata and reproduced the pre-boot failure on a clean isolated 1280x720 AVD with software and valid hardware acceleration; ADB remained absent/offline, local Emulator/QEMU/WHPX runtime is the remaining blocker, and no GUI pass is claimed (`../../outputs/v28-gui-retry/README.md`)
Target GUI / paired-OBD / forced-recovery smoke: pending manual verification
Review: CODE_REVIEW_V0.3.5_V28.md
```

Role: local D-055/D-056 implementation evidence. The final user JSON is byte-identical to the packaged source asset and supplies exact positions, masks and effect defaults. Android uses fixed nearest-neighbour 2× logical rendering, physical Noto labels, confirmed SOC/wheel/power animation semantics and local-only CRT settings. Core scheduler/request/protocol/decoder/SignalStore/Idle eligibility sources have no diff. This is not a remote CI artifact or promoted baseline.

---

## B. Void source

### V0.1.9

```text
SHA-256: 37470b85b90bc928361a3de177f827a9472851cbde7286b499161cedbee51765
STATUS: VOID
```

Do not use as a development baseline.

---

## C. Real-vehicle sessions

### V0.3.2 API 27 exact-commit long runs — D-051 primary training evidence

```text
RX400h Monitor log 2026-08-15 14-15-58.zip
SHA-256: 0dc6b6a71f40365b18febe6a815eeb13f2a6bb32ee0a4dcb6237091421a245f7
LIVE monotonic duration: 2346.753 s
Scheduler: 11813 releases = 11807 on-time + 1 late + 5 capacity-rejected

RX400h Monitor log 2026-08-15 17-50-18.zip
SHA-256: d304e9dfabb1f1d4a28eda9b9c0fc674c04276ce119e8ea5a94e8cdf783432d7
LIVE monotonic duration: 2158.960 s
Scheduler: 10869 releases = 10853 on-time + 2 late + 12 capacity-rejected + 2 session-ended

Source commit: 8e55c6afae20ca64b9ea9bba5861bc85d8017c62
Exact CI APK SHA-256: 841b1a4adb9f9e4a1834d2830dd6e94754a54cbcc3b2b3209023061da1969e9b
Device: API 27 sprd sp7731e_1h10_native / sp7731e_1h10; OBDLink MX+ 99905
```

Both ZIPs pass CRC, manifest file size/SHA, provenance, count, line-tail and monotonic scheduler conservation checks. The first contains one 168.604-second all-CAN `NO_DATA` window while ATRV/prompt/cadence continue; the user confirmed this coincided with vehicle shutdown, but the log alone labels it only as ECU/CAN unavailability. The second contains an approximately 54-second backward wall-clock correction; monotonic scheduling is continuous and unaffected. D-051 freezes request p95/sample values `141/5631, 139/4503, 139/1501, 162/5630, 169/3000, 151/900, 81/1498 ms`, steady headers `65/4500` and `116/4498 ms`, plus separately untrusted cold bounds.

### V0.3.2 API 27 dirty-CRT short runs — D-051 holdout/regression only

```text
RX400h Monitor log 2026-08-18 11-52-40.zip
SHA-256: c3cdba1706a3bd610d106403ed505c4479562ad174b15ee5ceccf38dc5ac24c9
LIVE monotonic duration: 193.893 s
Raw / scheduler completion: 1389 all OK / 979 all on-time

RX400h Monitor log 2026-08-18 20-29-22.zip
SHA-256: a806771c8fb6d09aadc6db102716e58a3429c0ca75ea133fe4bba6385118c8b7
LIVE monotonic duration: 429.495 s
Raw / scheduler completion: 3044 all OK / 2163 all on-time

Declared/local-matched APK SHA-256: af4ce2ba7e9899a4d33bf304038cb4df13f7d4a9986fb65da5f1662f4ab5b51f
```

Both are complete `USER_END` archives with full manifest/hash/count/tail checks and no recovery metadata. The longer run crosses a 12.852-second Activity stop/start while acquisition continues. Because the APK records a dirty presentation worktree, these sessions are useful independent holdout/regression evidence but are not the sole or primary trusted-cost training provenance.

### V0.3.3 API 27 exact-local normal-flow regressions — D-048

```text
RX400h Monitor log 2026-08-26 09-10-02.zip
SHA-256: c917e183eddb496e4ccdc5827e63583ea6dca59bc225176b066cd44a66af5201
Session: RX400h_20260825_210526_915; completed USER_END; 1,725 transactions; zero recorded errors
Scheduler: 1,268 releases = 1,209 on-time + 1 late + 57 capacity-rejected + 1 session-ended

RX400h Monitor log 2026-08-26 19-11-23.zip
SHA-256: 5364e69e85dae8d18358acb14f46e3544b96c73b3d82bba09112b628c1b0ecda
Session: RX400h_20260826_065751_666; completed USER_END; 5,623 transactions; zero recorded errors
Scheduler: 4,006 releases = 4,006 on-time

Source commit: c9ad39759021fd8d4ca529b115ab7932aa2bf8d8
Exact local APK SHA-256: 2d75bd7d1bc6be8a923495e901c05ce904d58966f8e10ff3e12b2e980a33d0a1
Device: API 27 sprd sp7731e_1h10_native / sp7731e_1h10; OBDLink MX+ 99905
```

Both archives bind to the exact clean V0.3.3/v25 artifact, are `evidence_complete=true`, verify their internal manifests, and record an external public-export receipt. Together they evidence target normal start → LIVE → user End → public save. The first run includes bounded admission rejections and one 14 ms late completion; the second is a clean approximately 13-minute same-period run with every release on time. Neither archive is a forced process-death/interrupted-recovery test, so that gate remains open.

### V0.1.8 first successful run

```text
RX400h_20260805_163318.zip
SHA-256: 3509e072a2fcb8e1e88c456d202c144a1c07256e1f55020c7cebf9e4cbd9e160
```

Recorded characteristics:

- ~6m20s LIVE.
- 1145 raw transactions.
- 147 frame rows.
- 0 errors.
- logger clean/evidence complete.

### V0.1.8 second longer run

```text
RX400h_20260807_070701.zip
SHA-256: a0f9293fdcf2f870725f20333c19711ce73d7dd1288333d7b8966a1673ee9bd1
```

Recorded characteristics:

- ~10m35s.
- 1761 transactions.
- 230 frames.
- No wrong CAN ID / ISO-TP sequence / abnormal PCI / timeout / bus error in active operation.
- ~0.36 Hz frame rate due to app-side timing.

### V0.1.10 strongest cleanup regression run

```text
RX400h_20260807_120303.zip
SHA-256: e0f4756d1ec8fb712bfdb12d766c984209bbed385f7950e5fa728714ed987f0b
```

Recorded characteristics:

- ~22.3 min LIVE.
- 3656 transactions.
- 479 frames.
- 0 errors.
- logger clean/evidence complete.
- Terminal NO DATA only after deliberate vehicle shutdown.
- Cleanup changed semantics/UI but intentionally not scheduler timing.

### V0.2.0 first real-vehicle session (regression found)

```text
RX400h_20260808_043828.zip
```

Recorded characteristics:

- App `0.2.0`, decoder `rx400h-reactive-20260808-001`, scheduler `v020_reactive_core_candidate`.
- ~2 min LIVE, 332 transactions, 41 frames, 0 logged errors, evidence complete.
- Speed, RPM and derived ICE power were missing because `decodeStandard` aborted at PID `04` (engine load) before reaching PID `0C`/`0D`.
- Fix: decoder version `rx400h-reactive-20260808-002` restores full standard-block PID skipping; regression test added.

### V0.2.0 decoder-002 real-vehicle validation — phone

```text
RX400h_20260808_234255.zip
SHA-256: f46f716d9cefdcec58edd9f7afd91dc45d255fe29b37bab64b175173dc3d655a
```

- Device: Samsung SM-F946B (Galaxy Z Fold5), Android 16 / API 36, portrait.
- App `0.2.0`, decoder `rx400h-reactive-20260808-002`; ~7.7 min LIVE; 1287 transactions; 167 frames; 0 errors; evidence complete.
- All signals present after the decoder fix, including speed, RPM and ICE power.
- First natural real-vehicle Idle Check capture: 4 consecutive frames active at RPM 901.5–903, speed 9–13 km/h, ICE power 0 kW, ICE torque 0 Nm, warmup true, coolant 74–75 °C; deactivated at RPM 889 (below 900) and warmup end. Direct E1 support for the minimal eligibility conditions.
- Frame interval median 2.82 s; request latency avg ~150–175 ms.

### V0.2.0 decoder-002 real-vehicle validation — target head unit

```text
RX400h_20260809_045711.zip
SHA-256: e6b0478f85d4d5bb2f24408fcacf94c0144d7c221a16b65be21c4047d12406ec
```

- Device: Spreadtrum sp7731e head unit, Android 8.1 / API 27, landscape 1280×720.
- App `0.2.0`, decoder `rx400h-reactive-20260808-002`; ~19.8 min LIVE; 3404 transactions; 450 frames; 0 errors; evidence complete.
- All signals present; warmup false for the whole session; no Idle Check trigger observed.
- Weak-hardware stability evidence: PSS ~27–30 MB, Java heap ~1.7–3.3 MB, CPU ~10% of one core, render avg ~1.3 ms, logger write avg ~1.4 ms (max 46 ms spike).
- Frame interval median 2.71 s; request latency avg ~133–157 ms.

### V0.2.0 final closure validation — target head unit (30 min)

```text
RX400h_20260809_091230.zip
SHA-256: 55216740f81f00e5269ff5b612aa8e6f767cdd1face4caf468d9fa512182694b
```

- Device: Spreadtrum sp7731e head unit, Android 8.1 / API 27, landscape 1280×720.
- App `0.2.0`, decoder `rx400h-reactive-20260808-002`; ~30.0 min LIVE; 5105 transactions; 675 frames; 0 errors; evidence complete.
- All signals present: speed 0–100 km/h, RPM 0–3127, ICE power −7.3 to 83.2 kW, SOC 38–63%, HV power −27.1 to 29.7 kW, ICE torque −256 to 254 Nm.
- Memory stable across 30 minutes: PSS ~17.6–27.5 MB (mean 20.4), Java heap ~1.6–3.3 MB; no duration-proportional growth.
- CPU ~9.9% of one core; render avg 1.5 ms; logger write avg 1.1 ms (max 43 ms).
- Frame interval median 2.75 s (~0.375 Hz); request latency avg ~136–161 ms.

---

## D. Bluetooth HCI / RFCOMM evidence

### Bugreport/dumpstate

```text
dumpstate(1).zip
SHA-256: 1d657dc06b631a94aca0aad0012140c03045a66982fa03307065ea9940dc4f3d
```

User-provided copy `dumpstate.zip` (2026-08-09) matches this hash; embedded `btsnoop_hci.log` / `btsnoop_hci.log.last` hashes were re-verified and are unchanged. Registered as V0.3.0 cross-check material.

### HCI snoop files

```text
btsnoop_hci.log.last
SHA-256: c9c080e84b7d4db88c9b6e57cd99e875e66a848a11806fb13623ebe9bf4f9770

btsnoop_hci.log
SHA-256: 205d6dd0efbc625551d65bc8ce423ca2ab7637c460ef16d7c3a0de0faf180875
```

Key recovered runtime loop:

```text
ATSH7E0
01040C0D0E10 2
21CDF3 3
ATSH7E2
21C3 6
21C4 5
```

Key timing evidence:

- Raw core loop median ~0.159 s (~6.28 Hz).
- Effective core data ~5.5 Hz with interleaved optional work.
- `01050607 1` ~3.106 s median period.
- `21CF 4` ~5.205 s median period.

Interpretation: HA rate is an observed stable reference point, not a proven ECU maximum.

---

## E. Hybrid Assistant APK

```text
Hybrid+Assistant_3.320.0_APKPure.apk
SHA-256: 563a8b0847e13533b7c2a6b602a649bd3303495b7e8b4259c52277f9289fae80
```

Role:

- Request-chain and field/static state reconstruction.
- ICE torque / warmup / HSD / presentation reference evidence.
- Must not be treated as permission to copy HA UI/code/resources; use interoperability facts and clean-room reconstruction only.
- V0.3.0 cross-check material; SHA-256 verified 2026-08-09 against the user-provided copy.

---

## F. Dr Prius cross-check

```text
Dr.+Prius+_+Dr.+Hybrid_7.01_APKPure.xapk
SHA-256: 7246dab1747cce740cfaa5ce71a897ac2075f64b7170029d4053b1f8af42e2b3
```

Role: independent cross-check for `21CF` temperature formula; do not add unrelated requests merely because they appear in another app.

### V0.3.0 multi-source cross-check policy

- Complementary sources: Hybrid Assistant APK, Dr Prius XAPK, Bluetooth HCI/RFCOMM capture, E1 real-vehicle logs and current source.
- No single source blocks development. When sources conflict, record the decision in `DECISIONS.md` and do not promote unverified semantics to the runtime model.
- Both APK/XAPK hashes were re-verified 2026-08-09 against the user-provided copies.

---

## G. Important reconstructed formulas / state evidence

### ICE mechanical power

```text
ICE torque = (d[3] - 128) * 2 Nm   from 61CD
ICE power kW = torqueNm * 2π * RPM / 60 / 1000
```

### Warmup bit

```text
Warmup Active = (d[1] & 0x01) != 0   from 61C4
```

### Idle Check recovered conditions

```text
Warmup Active == true
900 < RPM < 1100
ICE Power == 0.0 kW
speed <= 55 km/h
stable ~1 s
```

Runtime UI validation is less complete than the static/replay reconstruction; future natural evidence should be appended here when captured.

---

## H. Signing/build continuity

Fixed debug signing key identity recorded in project history:

```text
alias: rx400hdebug
package: com.guanyu.rx400hprobe.debug
```

Do not replace the signing identity casually or existing installations may no longer upgrade in place.

Sensitive secret material should not be copied into project-state documents beyond what is already intentionally public/test-only and required for reproducibility.


---

## I. Repository/Codex migration evidence

User-confirmed Git state during migration:

```text
628da4b (HEAD -> main, origin/main) Add v0.1.10 project baseline
43a9e6b Update to v0.1.10 cleanup candidate
0121b66 Update to v0.1.8
```

The user also confirmed:

```text
git push -> main updated successfully
git status -> working tree clean
```

Connected GitHub read access later successfully fetched:
- `PROJECT_STATE.md`
- `DECISIONS.md`
- `ROADMAP.md`
- `CHANGELOG.md`
- `DEVELOPMENT_PROTOCOL.md`
- `EVIDENCE_INDEX.md`
- `.github/workflows/build-apk.yml`
- `app/build.gradle.kts`

A direct attempt to create `AGENTS.md` through the ChatGPT GitHub contents integration returned:

```text
403 Resource not accessible by integration
```

This is why Codex write automation uses local Git/`gh` credentials instead of relying on connector write scopes.

---

## J. V0.3.5/v28 D-057 renderer comparison — 2026-09-02

Local artifact/evidence root:

```text
../../outputs/v28-gui-retry/isolated-avd-home/artifacts/
```

Key files:

```text
v28-debug.apk                 exact pre-D057 v28 artifact
v28-first.png                 pre-D057 first preview
v28-steady.png                pre-D057 steady preview
v28-d057-debug.apk            final D057 candidate
v28-d057-first.png            final first preview
v28-d057-steady.png           final steady preview
v28-d057-settings.png         final settings-overlay smoke
v28-d057-gfxinfo.txt          final 12-second graphics window
v28-d057-meminfo.txt          final memory snapshot
v28-d057-top.txt              final CPU samples
```

The exact pre-D057 fixture records 103/103 janky frames, 40 ms median and 9 slow bitmap uploads. The final candidate records 59/104, 20 ms median and zero slow bitmap uploads; its total PSS is 35,894 KB. Final APK SHA-256 is `b8cfe5d5497e13eb30a8eb4d02695ae2eed9de50a91bac265cd74af2cc28bbab`, package `com.guanyu.rx400hprobe.debug`, versionCode 28, versionName `0.3.5-debug`, fixed v2 certificate SHA-256 `77ba84b1f4f737a5d61b910bf4386df167548b9c6ce689ed25e994c37b2bc192`.
