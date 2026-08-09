# RX400h Monitor — PROJECT_STATE

**Baseline date:** 2026-08-09<br>
**Current engineering baseline:** V0.2.0 — Reactive Core (closed 2026-08-09)<br>
**Historical validated engineering baseline:** V0.1.10 cleanup / real-vehicle validated branch<br>
**Next major milestone:** V0.3.0 — High-Performance Scheduler / Refresh Frontier
**Repository:** `noobruge-wq/rx400h-Monitor`

> This file is the primary handoff document. Future development must update this document before code changes. A new AI/developer should be able to resume the project from this file + `DECISIONS.md` + `ROADMAP.md` + latest source without relying on chat history.

---

## 1. Product definition

RX400h Monitor is a dedicated native Android monitor for the Lexus RX400h.

### In scope

- RX400h-only monitoring.
- Offline operation.
- Low CPU / low RAM / weak Android head-unit support.
- Native Kotlin + Android View.
- High-value hybrid/vehicle signals only.
- High effective sampling rate for dynamic signals where the vehicle/adapter path supports it.
- Long-running runtime with robust reconnect, evidence logging and deterministic replay.
- A stable presentation contract that can later support optional skins/animation without changing the vehicle core.

### Explicitly out of scope for Core 1.0

- Generic Toyota diagnostic platform.
- Arbitrary CAN / PID / DID injection.
- Blind Header/DID scanning.
- Hybrid Assistant clone.
- Good/Bad Brake scoring, Pulse/Glide/Sweet Spot, reporter expansion unless later explicitly re-scoped.
- Heavy UI frameworks, WebView or generalized plugin architecture.

---

## 2. Evidence discipline

Evidence ranking:

- **E1:** real-vehicle raw evidence.
- **E2:** current source.
- **E3:** APK direct literal evidence.
- **E4:** static callgraph / bytecode inference.
- **E5:** official or quasi-official documentation.
- **H:** hypothesis.
- **R:** rejected.

Rules:

1. Protocol changes must be evidence-driven.
2. Do not promote plausible values to confirmed semantics without evidence.
3. Missing/uncertain conditions must become `Unknown` / `Unavailable`, not guessed defaults.
4. Runtime requests remain whitelist-only.
5. Raw evidence is preserved so removed typed fields can be re-derived later if needed.

---

## 3. Version baseline and invalid branches

### Valid history

- **V0.1.8** — first successful evidence-driven RX400h request set and strong real-vehicle baseline.
- **V0.1.10** — cleanup branch created strictly from V0.1.8; removes Probe-era UI/dead code and retains the validated request set.

### Invalid history

- **V0.1.9 is VOID.**
- Never use V0.1.9 as a code baseline.
- It incorrectly downsampled `21CDF3` and `21C4`; later HCI/state evidence showed those blocks are dynamic/core inputs.
- Individual ideas may be reimplemented independently after review, but code must derive from the current valid baseline.

---

## 4. Frozen runtime request set at the current baseline

### 7E0 → 7E8

```text
01040C0D0E10 2
01050607 1
21CDF3 3
```

### 7E2 → 7EA

```text
21C3 6
21C4 5
21CF 4
```

### Adapter

```text
ATRV
```

### Do not reintroduce without new direct evidence

```text
22xxxx
2C
10 02
10 03
7E1 / 7E3 / 7E4 scanning
arbitrary command input
```

Important protocol facts:

- Vehicle physical OBD protocol: ISO 15765-4 CAN, 11-bit, 500 kbit/s (`ATSP6`).
- `7E0 -> 7E8`, `7E2 -> 7EA` are confirmed paths; CAN IDs alone must not be over-labelled with guessed ECU names.
- ELM suffixes such as `21C3 6` / `21CF 4` represent adapter response-count limits, not CAN payload bytes.
- ELM ASCII commands are visible in Bluetooth HCI/RFCOMM captures, not in a passive CAN listener as literal text.

---

## 5. Product signal contract currently desired

### BATTERY

- **SOC** — A-level.
- **HV battery average temperature** — A-level primary temperature.
- **HV battery max/min temperature** — B-level secondary values.

### VEHICLE STATUS

- **Speed** — A-level.
- **Coolant temperature** — A-level.
- **12V OBD** — A-level. This is adapter/DLC supply voltage; do not claim battery-terminal voltage without new evidence.

### POWER

- **ICE mechanical power** — A-level.
- **Engine RPM** — A-level.
- **Idle Check** — B-level text under RPM, visible only when the runtime determines the vehicle is actually in Idle Check. Otherwise show nothing.
- **HV battery power** — A-level.

There is no intentional duplicate battery-power display across sections.

---

## 6. Current signal semantics

### Standard OBD block `01040C0D0E10 2`

Confirmed useful contents include RPM, speed, engine load, ignition timing and MAF. Product Runtime should retain only consumers needed by Core 1.0. Load/MAF are currently candidates for typed-model removal unless a consumer exists.

### Standard OBD block `01050607 1`

Coolant is required. Other returned standard fields need no typed Runtime representation unless consumed.

### `21CDF3 / 61CD`

- ICE torque: `(d[3] - 128) * 2 Nm`.
- ICE mechanical power:
  `ICE Torque * 2π * RPM / 60 / 1000` kW.
- Injection is a real HA field but is not a valid combustion boolean and is not required by the current product contract.

### `21C3 / 61C3`

Required consumers include SOC and HV electrical power inputs. The block also contains other hybrid/motor fields, but unused typed fields should be removed from Runtime after consumer audit while preserving raw evidence.

### `21C4 / 61C4`

- Direct warmup flag: `(d[1] & 0x01) != 0`.
- Currently required for accurate Idle Check logic.

### `21CF / 61CF`

- Eight battery temperature channels are decoded.
- Product display uses AVG as A-level; MAX/MIN as B-level.

### `ATRV`

- OBD adapter/DLC supply voltage.
- UI label should remain `12V OBD` (or equivalent precision-preserving wording).

---

## 7. Idle Check policy

The product does **not** display S0 / Stable S0 / Unstable S0 / S1a / End S1a / S1b / S2 / S3 / S4.

Only `IDLE CHECK` is user-visible, under RPM, and only while the runtime state is actually Idle Check.

Current recovered HA conditions include:

```text
Warmup Active == true
900 < RPM < 1100
ICE Power == 0.0 kW
speed <= 55 km/h
stable for ~1 second
```

However, the full HSD state/history currently contributes to eligibility. Therefore:

- Do not blindly replace the state machine with a one-line predicate.
- V0.2.0 should test whether a smaller `IdleCheckEligibilityState` can reproduce the full reference model for every replay fixture.
- If equivalent, remove the full S0–S4 product-state implementation.
- If not equivalent, keep the minimum internal state required but do not expose it in the UI.

Idle Check runtime evidence remains less complete than its static/replay reconstruction; future natural observations may strengthen validation but should not require special driving solely for the project.

---

## 8. HCI performance evidence

Hybrid Assistant RX400h capture showed a fast serial loop containing:

```text
ATSH7E0
01040C0D0E10 2
21CDF3 3
ATSH7E2
21C3 6
21C4 5
```

Observed:

- Median raw core-loop period ~0.159 s (~6.28 loops/s).
- With lower-frequency requests interleaved, effective core data is ~5.5 Hz.
- `01050607 1`: ~3.1 s median period.
- `21CF 4`: ~5.2 s median period.

Interpretation:

- ~5.5 Hz is an observed HA operating point, **not a proven RX400h ECU maximum**.
- V0.3.0 is expected to explore above the HA operating point under controlled step tests.
- Final fast-signal rate should be selected from latency/error/CPU knee-point evidence, not from a preset 2 Hz or 5.5 Hz ceiling.

---

## 9. V0.1.10 cleanup status

Structural cleanup already performed includes:

- Removed runtime raw HEX dashboard path.
- Removed obsolete manual link/HA validation controls.
- Removed old manual READY/ENGINE_STARTED/etc event controls.
- Removed `ProtocolAttempt`, `bestProtocol`, `protocol_matrix` paths.
- Removed duplicate `SignalValue.rawResponse` cache.
- Removed incorrect C3 ICE-power semantic.
- Added typed CDF3 ICE torque and C4 warmup flag.
- Improved Response Pending recognition to require a true structured `7F <service> 78` negative response.
- Fixed valid AT text response classification.
- Added FileProvider internal-files fallback support.
- Replaced shared-log adapter MAC with SHA-256 identifier.
- Extracted dashboard construction/presentation into `DashboardUi.kt`.

Known remaining architectural debt:

- Activity still owns too much lifecycle state.
- No final ForegroundService runtime yet.
- Scheduler is still the old slow V0.1.8-style timing and is the major UX/performance bottleneck.
- Log rolling / disk guard / orphan-session recovery are not final.
- Performance observability is designed but not yet implemented in the current baseline.

---

## 10. Real-vehicle baseline summary

The strongest V0.1.10 run available in project history is:

```text
RX400h_20260807_120303.zip
SHA-256: e0f4756d1ec8fb712bfdb12d766c984209bbed385f7950e5fa728714ed987f0b
```

Recorded summary:

- ~22.3 min LIVE.
- 3656 transactions.
- 479 frames.
- 0 logged errors.
- `logger_degraded = false`.
- `evidence_complete = true`.
- All manifest hashes matched.
- Terminal `NO DATA` events occurred only after deliberate vehicle shutdown; normal active segment had no vehicle-request failure.
- Frame interval remained ~2.8 s, confirming that V0.1.10 cleanup intentionally did not change scheduler timing.

This proves cleanup/protocol/logger regression safety, not final performance.

---

## 11. Lean Core policy

The project deliberately prefers a small RX400h-specific implementation over a generic vehicle framework.

Hot-path rules:

- Avoid regex/split/substrings where a fixed parser can do the job.
- Avoid dynamic `Map<String, Any>` signal stores.
- Avoid generalized plugin/decoder registries.
- Prefer fixed typed structures and fixed request tables.
- Avoid unnecessary allocations and temporary collections.
- Keep long history on disk, not RAM.
- Prefer bounded ring buffers where in-memory history is necessary.
- Keep third-party Runtime dependencies near zero unless a library clearly reduces risk more than it adds CPU/RAM/package cost.
- UI/Skin code must never directly access Bluetooth, send requests, or redefine decoder semantics.

The optimization target is not merely APK size; it is lower CPU, lower PSS, lower allocation/GC pressure, simpler auditability and higher useful signal rate.

---

## 12. Reactive / presentation architecture target

Target separation:

```text
Transport / ELM
    ↓
Deadline Scheduler
    ↓
Decoder
    ↓
Signal Store
    ↓
Derived Physics / minimal state
    ↓
Presentation Contract
    ↓
Renderer / Skin / Animation
```

Important invariants:

- Acquisition rate != signal-change rate != UI publish rate != animation FPS.
- Signal changes are published asynchronously/event-driven.
- Unchanged low-frequency values do not trigger needless UI work.
- Fast signals (RPM, HV power, ICE power, speed) may publish several times per second.
- Future 30 fps animation may interpolate visual state, but interpolated values never become vehicle truth or logger raw data.

---

## 13. Test-cost policy

Do not require expensive dedicated long-distance drives as a release gate.

Use:

1. Normal daily driving to accumulate real Bluetooth/ECU evidence.
2. Replay-loop and accelerated/virtual-clock soak for memory, logger, state, stale/deadline and UI stress.
3. Fault injection while stationary where possible (Bluetooth off/on, OBDLink disconnect/reconnect, vehicle OFF/READY, process restart, low-storage simulation).

Only questions that genuinely require the vehicle should consume real-vehicle test time.

---

## 14. Next milestone — V0.2.0 Reactive Core

**Status: closed — 2026-08-09.** Unit tests (17), the CI-signed APK build, and phone + head-unit real-vehicle sessions pass. The first natural Idle Check capture is recorded. V0.1.10 remains the historical real-vehicle-validated baseline; V0.2.0 is the current engineering baseline.

V0.2.0 target:

- Formal typed `SignalStore`.
- Monotonic clock semantics.
- Signal value + timestamp + age + quality + version + source.
- Change-driven UI updates / dirty-mask or equally lightweight mechanism.
- Performance observability baseline (`performance.csv` or equivalent).
- PSS / Java heap / native heap / CPU / GC / render latency / request latency / logger latency / session size / health counters.
- Consumer audit and removal of unused typed Runtime fields.
- Current three-domain UI semantics.
- B-level Idle Check under RPM only when actually active.
- Scheduler interface prepared for deadline/priority operation, but no uncontrolled frequency increase until instrumentation exists.

**Exit condition:** presentation can be replaced without changing Decoder/SignalStore semantics, and Runtime typed fields all have an explicit consumer.

---

## 15. Handoff instructions for a new conversation/developer

Read in this order:

1. `PROJECT_STATE.md`
2. `DECISIONS.md`
3. `ROADMAP.md`
4. `CHANGELOG.md`
5. `DEVELOPMENT_PROTOCOL.md`
6. `EVIDENCE_INDEX.md`
7. Latest source tree

Then verify:

```text
Current baseline = V0.2.0 (closed 2026-08-09; historical validated baseline = V0.1.10)
V0.1.9 = VOID
Next major milestone = V0.3.0 High-Performance Scheduler
Protocol whitelist unchanged unless new evidence explicitly says otherwise
```

Do not reconstruct project state from old chat messages unless the baseline documents explicitly identify a missing evidence item.

---

## 16. Repository / GitHub build baseline

The project must remain recoverable without chat history, including its APK build and install-continuity workflow.

Current development build path:

```text
Termux git push
→ GitHub Actions (.github/workflows/build-apk.yml)
→ JDK 17 / Gradle 8.9
→ :app:assembleDebug
→ fixed project debug/test signing key
→ apksigner verify
→ versioned GitHub artifact
```

Fixed development signing is intentional so successive test APKs can overwrite the previous project build when package/version rules allow. The bundled key is a development/test key, not a production-store release credential.

Canonical detailed instructions, including Termux commands and complete baseline-document upload/verification, are in:

```text
GITHUB_BUILD_AND_BASELINE_WORKFLOW.md
```

A major version is not considered properly handed off if the source is recoverable but the build/signing/baseline-upload procedure is missing.


---

## 17. Codex migration / repository access state — 2026-08-08

The project is being migrated from a long ChatGPT conversation to Codex.

New canonical handoff files:

```text
AGENTS.md
CODEX_HANDOFF.md
REPO_ACCESS_AND_AUTH.md
```

Known repository state from the user's successful Git push:

```text
628da4b Add v0.1.10 project baseline
43a9e6b Update to v0.1.10 cleanup candidate
0121b66 Update to v0.1.8
```

Codex must fetch the current remote and treat a newer remote HEAD as authoritative.

### Access behavior

- ChatGPT's connected GitHub integration can read the repository.
- A direct contents write attempt returned GitHub 403 `Resource not accessible by integration`.
- Therefore normal project writes must use Codex/local `git`, not depend on ChatGPT connector writes.
- Preferred new-machine authentication is GitHub CLI browser OAuth + credential helper.
- The migration automation may launch browser authorization, but the first user consent cannot be silently bypassed.

### Migration acceptance

A new Codex session is considered successfully migrated when it can:
1. read the canonical baseline;
2. reconcile baseline vs current source;
3. fetch/pull;
4. pass `git push --dry-run` write verification;
5. inspect the signed-debug GitHub Actions workflow;
6. summarize V0.2.0 scope without chat history.

---

## 18. V0.2.0 closure — 2026-08-09

Closed as the current engineering baseline. Implementation is complete and verified by GitHub Actions (`8a7aef4`, run `31299528505`, success):

1. Docs-first migration closure: install Codex handoff documents and refresh baseline manifest.
2. Lightweight typed `SignalStore` with value/source timestamp/update timestamp/age/quality/version/source.
3. Presentation contract + change-driven UI publication (no periodic full-dashboard repaint as primary mechanism).
4. Three-domain UI: BATTERY / VEHICLE STATUS / POWER with conditional `IDLE CHECK`.
5. Minimal `IdleCheckEligibilityState`; transition results written to evidence logs for replay validation.
6. Consumer audit: remove no-consumer typed fields (load/MAF/timing, injection, MG1/MG2/MGR, rear MG, brake candidates) while preserving raw evidence.
7. Performance/health observability baseline (`performance.csv` or equivalent).
8. Scheduler request-table interface prepared for deadline/priority operation; polling rates deliberately unchanged.
9. Unit tests for parsers, SignalStore and Idle Check; GitHub Actions runs `testDebugUnitTest` before `assembleDebug`.

Real-vehicle validation status (decoder `002`):

- Phone session `RX400h_20260808_234255`: all signals present, 0 errors, first natural Idle Check activation captured (4 frames at RPM 901.5–903, speed 9–13 km/h, ICE power 0 kW, warmup true).
- Target head-unit session `RX400h_20260809_045711`: all signals present, 0 errors, ~19.8 min on Spreadtrum sp7731e with low memory footprint and ~10% one-core CPU.
- Final closure session `RX400h_20260809_091230`: all signals present, 0 errors, ~30.0 min on the target head unit with stable PSS/heap across the full session.
- Decoder regression fix (`0478756`) is included; CI signed build passed.

The V0.2.0 exit gate remains: renderer replacement requires no Decoder/SignalStore semantic changes, and every typed Runtime field has a documented consumer.

Remaining real-vehicle-only items are tracked in V0.3.0: more natural Idle Check observations, long-session memory trend on the target head unit, and high-refresh ladder testing.
