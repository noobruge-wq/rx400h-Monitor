# RX400h Monitor — CHANGELOG

This changelog records engineering baselines, not every chat turn. Major-version work must update this file **before** code changes and again when the milestone is closed.

---

## [Unreleased]

### V0.3.0 preparation

- Registered Hybrid Assistant APK (`563a8b08…`) and Dr Prius XAPK (`7246dab1…`) as hash-verified multi-source cross-check material for V0.3.0 (D-031).
- Re-verified user-provided `dumpstate.zip` (`1d657dc0…`) and its embedded HCI btsnoop files (`205d6dd0…`, `c9c080e8…`) as V0.3.0 cross-check material.
- Recorded D-032: HA/DP are reference-only; no wholesale copying; keep the implementation minimal until new requirements are added.

### V0.3.0 — High-Performance Scheduler / Refresh Frontier (development started 2026-08-10)

- Header v3 (D-035, supersedes D-033): text row first — two-line title left, widened status column right (device name + 蓝牙/协议/数据 lines that may stack vertically or use short words) — then a full-width button row; buttons are narrower and taller and are squeezed by text, never the reverse. Insufficient horizontal width falls back to a stacked/scrollable arrangement.
- Domain cards use Chinese centered titles at the top (能量域 / 车辆域 / 动力域); each value has its own centered label line and value line (e.g. 电量 / `--.-%`).
- All three-domain text is doubled (labels/values 40sp, titles 28sp) except battery MAX/MIN (26sp, dim) and the permanent gray 怠速检查, which turns the active green only while Idle Check is active.
- `versionCode = 14`, `versionName = 0.3.0` on branch `v0.3.0` (candidate artifact `RX400hProtocolProbe-v0.3.0-header-v3-debug-signed`).
- Fix (candidate v4): label/value pairs now alternate line by line (电量 / `--.-%`, 温度 / `--.-°C`, 最高 最低 / `--.-°C--.-°C`, …) instead of all labels stacking above all values. `versionCode = 15`, candidate artifact `RX400hProtocolProbe-v0.3.0-header-v4-debug-signed`.
- Header v5 (D-036): buttons move back inside the header row on wide screens (between the two-line title and the widened multi-line status column), removing the separate button row so no vertical space is wasted; narrow screens keep the scrollable button row below. `versionCode = 16`, candidate artifact `RX400hProtocolProbe-v0.3.0-header-v5-debug-signed`.
- Screen-proportional typography (D-037): all dashboard fonts and button/header metrics scale with the screen's short side (reference 720dp; factor = min(width,height)/720, floor 0.5). On the target head unit the factor is 1.0 so v5 proportions are unchanged; other screens scale automatically. `versionCode = 17`, candidate artifact `RX400hProtocolProbe-v0.3.0-header-v6-debug-signed`.
- All elements auto-adapt (D-038): the screen-proportional factor now applies to every layout metric (root/card/button paddings, margins, separator, corner radius, header and button geometry) with a 1px floor; combined with narrow-screen fallbacks and single-line ellipsis, no text is placed outside the displayable area. `versionCode = 18`, candidate artifact `RX400hProtocolProbe-v0.3.0-header-v7-debug-signed`.
- Layout-only entry: no protocol, scheduler, signal or presentation-contract changes.

### Verification (2026-08-10)

- Header v1 (HEAD `8cd2e00`): GitHub Actions run `31314095899` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-ui-header-debug-signed`, APK SHA-256 `f94ae4aafb45e57bb254074541ebe44c37d934fc4de7dce327db3a7c218ba08c`.
- Header v2 + Chinese display (HEAD `e9479ba`): GitHub Actions run `31315386621` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v2-debug-signed`, APK SHA-256 `27b013c68cc822c59d3f0ca5f320c7150a4c9ea5a71947fe60880ca21427f1d6`.
- Header v3 + Chinese domain cards (HEAD `6a18f18`): GitHub Actions run `31316526865` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v3-debug-signed`, APK SHA-256 `399d268c3781324373b8392258c8393304c9d27ef12408cefa6879fef5d483c2`.
- Header v4 label/value ordering fix (HEAD `0f79111`): GitHub Actions run `31318586138` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v4-debug-signed`, APK SHA-256 `347841bdbd38c37bf2fb92e7823841016a19bcf17db02da095b878edf444d5dc`.
- Header v5 buttons-in-header (HEAD `e971ded`): GitHub Actions run `31318832012` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v5-debug-signed`, APK SHA-256 `4dd73e132d543351f8cd8891055e6810096f30088fe85ee12dfdece2d0e8c349`.
- Screen-proportional typography (HEAD `5cc6426`): GitHub Actions run `31319195137` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v6-debug-signed`, APK SHA-256 `f192a05600d00d455460f41261d0791eefe5657e07f48ddb7331a5cee0f6455d`.
- All APKs passed `apksigner verify` with the fixed project debug key (`CN=RX400h Protocol Probe Debug, O=Guanyu, C=NZ`).
- Layout-only items; real-vehicle re-validation is not required.

---

## [0.2.1] — Head-unit UI adjustment — 2026-08-09

### Changed

- Top bar height doubled; title and status text enlarged.
- Four control buttons moved to the center of the header area and enlarged (taller and wider) for easier reach on the target head unit.
- `versionCode = 11`, `versionName = 0.2.1`.

### Verification

- GitHub Actions run `31308752139` (HEAD `4872045`) completed **success**; 17 unit tests passed.
- Artifact: `RX400hProtocolProbe-v0.2.1-ui-debug-signed`, APK SHA-256 `701ecb4e00706aba468940d47ff3145d6914fd5ce81e2655fb8f7c1e8b3859ca`.
- `versionCode = 11`, `versionName = 0.2.1`; signed with the fixed project debug key.

---

## [0.2.0] — Reactive Core — 2026-08-09


### Development infrastructure / Codex migration

- Added `AGENTS.md` as Codex repository-level operating instructions.
- Added `CODEX_HANDOFF.md` as a complete new-session recovery entry point.
- Added `REPO_ACCESS_AND_AUTH.md` and Windows/Unix bootstrap scripts for GitHub read/write setup.
- Formalized local `git` + GitHub CLI browser OAuth as the Codex write path.
- Added a self-contained migration package containing source/evidence/reference archives so chat history is no longer required.

### Implemented

- Introduced lightweight typed `SignalStore` with value/source timestamp/age/quality/version/source.
- Used monotonic time for scheduling, freshness, state timers and performance metrics.
- Replaced periodic full-dashboard repaint with change-driven publication.
- Added minimal `IdleCheckEligibilityState` with transition logging for replay validation.
- Added unit tests for parsers, SignalStore and Idle Check; run them in GitHub Actions before the APK build.
- Added performance/health telemetry suitable for A55 + 1 GB targets.
- Added consumer traceability and removed unused typed Runtime fields while preserving raw evidence.
- `frames.csv` schema updated in the same version: no-consumer columns removed, `idle_check_active` added; new `performance.csv` for observability.
- Removed unreferenced probe profile JSON assets containing banned `22xxxx`/`2C` commands (recoverable from git history).
- Established current product UI contract:
  - BATTERY: SOC, AVG temperature, MAX/MIN secondary.
  - VEHICLE STATUS: speed, coolant, 12V OBD.
  - POWER: ICE power, RPM, HV battery power.
  - `IDLE CHECK` appears below RPM only while truly active.
- Prepared scheduler API for deadline/priority operation without yet performing uncontrolled high-rate polling.
- Enforced Lean Core allocation/dependency rules.

### Verification (2026-08-08)

- Unit tests: 17 passed (`:app:testDebugUnitTest`), including parsers, SignalStore, Idle Check state and the natural E1 replay fixture.
- GitHub Actions run `31299528505` (HEAD `8a7aef4`) completed **success**; `:app:assembleDebug` signed APK verified with the fixed project debug key.
- Artifact: `RX400hProtocolProbe-v0.2.0-reactive-debug-signed`, APK SHA-256 `207f28409c89ad7b8650f1aaf92426be52c77abe14bf2a9d816d2772e119f7be`.
- `versionCode = 10`, `versionName = 0.2.0`.
- Idle Check eligibility has a natural E1 capture plus a deterministic replay test; further natural observations are tracked in V0.3.0.

### Regression fix & real-vehicle validation

- `01040C0D0E10 2` decoder no longer stops at PID `04` (engine load) before reaching PID `0C` (RPM) and `0D` (speed). Restores speed, RPM and derived ICE power after the regression observed in `RX400h_20260808_043828`.
- Regression test added for the standard-block skip behavior.
- `RX400h_20260808_234255` (Samsung SM-F946B, Android 16): decoder `002`, ~7.7 min, 1287 tx, 167 frames, 0 errors, all signals present. Captured the first natural real-vehicle Idle Check activation (4 frames at RPM 901.5–903, speed 9–13 km/h, ICE power 0 kW, warmup true).
- `RX400h_20260809_045711` (Spreadtrum sp7731e head unit, Android 8.1): decoder `002`, ~19.8 min, 3404 tx, 450 frames, 0 errors, all signals present. Weak-hardware stability confirmed with low PSS/heap and ~10% one-core CPU.
- `RX400h_20260809_091230` (target head unit, final closure run): decoder `002`, ~30.0 min, 5105 tx, 675 frames, 0 errors, all signals present; memory stable across the full session.
- Added deterministic Idle Check replay test from the phone E1 session (unit suite now 17 tests).

### Closure audits (2026-08-09)

- Dead-code audit: removed unused `SignalStore.revision`; `SignalValue.ageMs` is now used by stale marking; no remaining probe-era UI/dead-code paths.
- Consumer audit: every typed Runtime field has an explicit consumer (UI, derived ICE power, Idle Check, or logger).
- Dependency audit: runtime dependency set is unchanged (`androidx.core:core-ktx` only); JUnit is test-only.
- Duplicate-state/cache audit: `SignalStore` is the single writer; no duplicate raw-response caches.
- Allocation/hot-path audit: no new hot-path allocations introduced; regex/split hot-path optimization is deferred to V0.3.0 with measurements.
- Real-vehicle-only items (more natural Idle Check observations, long-session memory trend on target hardware, high-refresh ladder tests) are moved to V0.3.0.

---

## [0.1.10] — Cleanup / Real-vehicle regression baseline — 2026-08-07/08

### Changed

- Created from V0.1.8, not V0.1.9.
- Separated dashboard construction/presentation into `DashboardUi.kt`.
- Removed raw HEX runtime display.
- Removed obsolete manual link/HA validation controls and old manual vehicle-state markers.
- Removed `ProtocolAttempt`, `bestProtocol`, `protocol_matrix` and unused parser helpers.
- Removed duplicate raw response storage from individual signal models; `raw_io.jsonl` remains authoritative evidence.
- Added typed CDF3 ICE torque and C4 warmup flag.
- ICE mechanical power uses torque × RPM formula only.
- Brake-related C3 values remain candidates only; not promoted to user-visible semantics.
- Valid AT text responses are classified correctly.
- Response Pending detection now requires structured `7F <requested service> 78` semantics.
- Added filesDir/FileProvider fallback consistency.
- Shared device metadata stores hashed adapter identifier instead of raw MAC.

### Preserved

Runtime request whitelist remains:

```text
7E0: 01040C0D0E10 2
7E0: 01050607 1
7E0: 21CDF3 3
7E2: 21C3 6
7E2: 21C4 5
7E2: 21CF 4
ATRV
```

### Real-vehicle regression

Strongest recorded V0.1.10 session:

```text
RX400h_20260807_120303.zip
SHA-256 e0f4756d1ec8fb712bfdb12d766c984209bbed385f7950e5fa728714ed987f0b
```

Summary recorded in project history:

- ~22.3 min LIVE.
- 3656 transactions / 479 frames.
- 0 errors.
- Logger clean and evidence complete.
- Terminal NO DATA only after deliberate vehicle shutdown.
- Scheduler intentionally remained slow (~2.8 s/frame class).

### Known debt left intentionally

- No final fast deadline scheduler.
- No ForegroundService lifecycle architecture.
- No final log rolling/crash/orphan recovery.
- No performance observability baseline yet.

---

## [0.1.9] — VOID / must not be used as a baseline

Status: **invalid branch**.

Reasons:

- Incorrectly downsampled `21CDF3` and `21C4` relative to later HCI/HSD evidence.
- Some contained ideas may be reimplemented independently, but V0.1.9 source must never become the base for subsequent versions.

---

## [0.1.8] — Evidence-driven Toyota runtime baseline — 2026-08-05

### Added/confirmed

- Successful RX400h Toyota request chain based on HCI evidence.
- Runtime whitelist that later became the V0.1.10 preserved set.
- Structured raw/decoded/frame/event/request-stat evidence package.
- Logger integrity improvements and evidence-complete semantics.
- Real-vehicle decoded SOC, HV V/A/power, battery temperatures and standard OBD values.

### Important evidence

Source ZIP SHA-256:

```text
174d5de7e8a295860f5e5577ab4d2e48e247ab674f4fbbc0b88a97d52a3d7cef
```

Representative real-vehicle runs:

```text
RX400h_20260805_163318.zip
SHA-256 3509e072a2fcb8e1e88c456d202c144a1c07256e1f55020c7cebf9e4cbd9e160

RX400h_20260807_070701.zip
SHA-256 a0f9293fdcf2f870725f20333c19711ce73d7dd1288333d7b8966a1673ee9bd1
```

---

## Historical pre-0.1.8 notes

Earlier versions were research probes used to establish CAN/ELM paths, logging discipline and failure modes. They are evidence history, not development baselines.

### Documentation / reproducibility amendment — 2026-08-08

- Added `GITHUB_BUILD_AND_BASELINE_WORKFLOW.md`.
- Documented phone-first Termux → GitHub Actions build workflow.
- Documented fixed development/test signing identity and signature verification requirement.
- Documented complete project-baseline overlay upload, SHA-256 manifest regeneration/verification, commit/push, and remote-state checks.
- Updated baseline handoff requirements so a new conversation can recover both project state and the build/upload process without chat history.
