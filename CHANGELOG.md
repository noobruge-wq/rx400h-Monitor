# RX400h Monitor — CHANGELOG

This changelog records engineering baselines, not every chat turn. Major-version work must update this file **before** code changes and again when the milestone is closed.

---

## [Unreleased]

No unreleased changes yet.

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

- Unit tests: 15 passed (`:app:testDebugUnitTest`), including parsers, SignalStore and Idle Check state.
- GitHub Actions run `31194615369` (HEAD `a0ee1a9`) completed **success**; `:app:assembleDebug` signed APK verified with the fixed project debug key.
- Artifact: `RX400hProtocolProbe-v0.2.0-reactive-debug-signed`, APK SHA-256 `f1c87bda96d1b4238488627300c40343768e809d251fbf154d41fd846960aa3e`.
- `versionCode = 10`, `versionName = 0.2.0`.
- Idle Check eligibility has a natural E1 capture plus a deterministic replay test; further natural observations are tracked in V0.3.0.

### Regression fix & real-vehicle validation

- `01040C0D0E10 2` decoder no longer stops at PID `04` (engine load) before reaching PID `0C` (RPM) and `0D` (speed). Restores speed, RPM and derived ICE power after the regression observed in `RX400h_20260808_043828`.
- Regression test added for the standard-block skip behavior.
- `RX400h_20260808_234255` (Samsung SM-F946B, Android 16): decoder `002`, ~7.7 min, 1287 tx, 167 frames, 0 errors, all signals present. Captured the first natural real-vehicle Idle Check activation (4 frames at RPM 901.5–903, speed 9–13 km/h, ICE power 0 kW, warmup true).
- `RX400h_20260809_045711` (Spreadtrum sp7731e head unit, Android 8.1): decoder `002`, ~19.8 min, 3404 tx, 450 frames, 0 errors, all signals present. Weak-hardware stability confirmed with low PSS/heap and ~10% one-core CPU.
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
