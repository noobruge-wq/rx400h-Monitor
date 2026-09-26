# RX400h Monitor — CHANGELOG

This changelog records engineering baselines, not every chat turn. Major-version work must update this file **before** code changes and again when the milestone is closed.

---

## [Unreleased]

- 2026-09-26 archival only: user pauses project and authorizes GitHub source backup before replacing computer. Preserve D064 product/build files unchanged; add restore instructions, protect local caches/credentials from staging, archive external browser editors and engineering scripts. No new app behavior, APK publication, main-branch overwrite or new real-car acceptance claim.

- D-064 (local completion 2026-09-23): V0.3.5/v29 pure real-time instrument, HA-order334ms core target; product logs/performance/recovery/export/steady strategy removed. Automatic one-shot connection, side-car reconnect, background acquisition and immediate Back exit implemented; device selection moved into settings, old three controls removed, wheel rotation corrected. Authored assets unchanged; only positively unsaved app-private sessions eligible for cleanup. Main9044→3525lines (−61%); benchmark6378581bytes SHA `3a4ed745…76496f5`. Two92-test direct runs,41143historicreplies,10Androidchecks,157pixelframes,two builds/lints/signatures and no-adapterGUI pass. Standard Gradle host worker failure and actual Bluetooth/music/sleep/first-frame limitations remain explicit. Report `D064_LOCAL_REVIEW_20260923.md`; no push/publication/physical install.

- D-063 (local candidate 2026-09-17): remove forwarding-only DashboardUi/unused logger alias; reuse fixed-shape JSON under existing lock, forward typed stats, avoid scheduler transient lists/HA array and repeated recursive sorts; benchmark R8 removes unreachable code. Main−126lines this batch, D061 cumulative−16.3%; test APK6,446,609bytes/cumulative−22.2%. 150directJUnit/41,143recordedreplies/48,000schedulerdecisions/8Androidfile-JSON/146pixeloracle/bothlint-build-signatures-nativeGUI pass. StandardGradle worker remains host failure; steady-search host micro29–35% is not whole-app/car/HA speedup. MX+ guards test-only; no new wire modes, request/period/formula/UI/save policy, push or car action. Full results/limits: `D063_LOCAL_REVIEW_20260917.md`.

- D-062 (local candidate 2026-09-17, authorized 2026-09-16): strengthened behavior-preserving cleanup removes dormant UI/recovery code, unused fields/interfaces, AndroidX Core dependency and benchmark debug fixtures; shared payload/hex, cached percentiles, foreground-only coalesced dashboard publication, value-based formatting and shared reusable power math. Main Kotlin−1,631lines/15.1%, test APK−13.9%;144 direct JUnit,41,143recorded replies,148oracle frames,7Android file faults,both lint/build/signature/GUI pass. Standard Gradle host worker failure remains explicit; UI-only CPU9.96→9.88% is essentially flat, not real-car speedup. Requests/periods/formulas/Idle/assets/logging/save policies preserved. No push/car install; report `D062_LOCAL_REVIEW_20260917.md`.

- D-061 (local candidate 2026-09-12, authorized 2026-09-11): exact sharp/halo damage + static-prefix cache, pixel no-op animation updates, shared CAN parsing and bounded arrival-driven serial Bluetooth reads. Independent full-raster oracle;165directJUnit with13,465recordedtransactions;debug/benchmarklint/build/GUI/file-fault passes. StandardGradle worker host failure is not relabeled passed. Same-debug UI-only CPU14.55%→10.07/10.12% (about31% relative), fixed-memory cache tradeoff documented. Add non-debuggable diagnostic test build and six low-frequency renderer CSV fields. STPX/STBC are test-only, not enabled; no periods/formulas/Idle/session/art changes, real5Hz claim, firmware update or push. Report `D061_LOCAL_REVIEW_20260912.md`; prior D060/evidence preserved.

- D-060 (local implementation/validation 2026-09-08): steady + default HA-replica strategies with serial200ms core targets, confirmed automatic End/save/new-session handoff, bounded private unsaved discard with receipts, Idle hysteresis/source coherence, explicit stale/failed publication and distinct dashboard snapshot Hz. Noto Chinese/settings pixel geometry, authored power bounds, stationary wheel40%, new-session self-test and exact cached software composition fixes. 140 direct JUnit,21 Python,7 real Android file-fault tests, lint/assemble/v2 signature pass. Same-content static/dynamic pixels exact; controlled UI CPU about22% lower on4core/1core emulator profiles. Final APK `db6b33fa…811297`, no release/push. Full LIVE Bluetooth handoff, latest raw Idle replay and80% whole-device calibration remain open. See `D060_LOCAL_REVIEW_20260908.md`; earlier UI-only freeze entries are historical.

- D-058 runtime follow-up (2026-09-06): after user reboot, WHPX/API 26 emulator works. Six unchanged-R1 UI-only runs complete with 3,868/3,868 input delivery, no skips; stress5 CPU 11.23/11.25/11.54%, recorded moving-display 4.54% versus parked-display 0.42%. Settings/Idle/self-test samples pass, but same-emulator exact D-057 preview reveals halo-pixel non-equivalence, so promotion remains on hold. No APK rebuild/product/debug Kotlin or asset change. New offline summary tests 10/10; not car/OBD performance or an 80% calibrated emulator. Report: `../../outputs/d058-runtime-20260906/D058_RUNTIME_REPORT.md`. Older blocked paragraphs below are historical checkpoints.

- D-058 validation follow-up (2026-09-05): added bounded debug-only dynamic display-frame replay / synthetic 5 Hz UI stress, parked/wheel controls and capture tooling; corrected host calibration statistics to monotonic time, interval-quality flags and exact-APK comparisons. 120/120 direct JUnit and 11/11 Python tests pass; lint 0 errors/9 warnings, assemble and fixed v2 signature pass. No product rendering/core behavior change. Emulator remains blocked, so no dynamic CPU/FPS/GUI improvement is claimed. Validation-only APK `b388efed…e68b7`; see `D058_DYNAMIC_VALIDATION.md`.

### V0.3.5/v28 — exact user-authored pixel dashboard (authorized 2026-09-01)

- D-058-R1 (local source/build complete 2026-09-05) replaces D-057's dynamic full-page software halo cache after exact target evidence showed `89.10%` moving CPU versus old-v28 approximately `72%` (parked/ICE-off improved to `17.17%`). R1 retains the sharp page, draws the same halo/page/wheel/scanline order on the View canvas, suppresses hidden wheel-only scheduling while settings is open and adds bounded debug-only logcat counters without changing evidence schemas. Production/test compilation, 112/112 direct JUnit, lint (0 errors / 9 warnings), assemble, package identity and fixed v2 signature pass. The 2,656,023-byte APK has SHA-256 `33021cb603fb760e92fd190cedffcf633ef1ef51541da5360dfcae3f3ee7fafa` and is copied to `../../deliverables/RX400h-Monitor-v0.3.5-v28-D058-R1-renderer-debug.apk`. The host emulator currently lacks its hypervisor driver and software fallback did not reach ADB, so GUI and target CPU gates remain open. Layout/assets/CRT settings/animation timing, core, scheduler, periods, protocol, decoder, SignalStore, logger/session and Idle Check remain frozen.
- D-057 (completed locally 2026-09-02) is a presentation-only renderer cost correction: cache the unchanged full page, draw the 10 fps wheel as a separate layer, replace per-frame scanline loops with one bounded repeating bitmap pattern, and suppress no-op SOC/HV-power/wheel transitions. Exact coordinates, CRT settings and animation meaning remain unchanged. On the same API 26/1280×720 AVD, the 12-second steady preview improves from 103/103 janky frames at a 40 ms median and 9 slow bitmap uploads to 59/104 at a 20 ms median and zero slow uploads; app CPU samples average about 3.5% versus the original 4–12% range. Production/test compilation, 111/111 direct JUnit, lint (0 errors / 9 warnings), assemble, package identity and fixed v2 signature pass. The 2,656,023-byte dirty-worktree APK has SHA-256 `b8cfe5d5497e13eb30a8eb4d02695ae2eed9de50a91bac265cd74af2cc28bbab` and is copied to `../../deliverables/RX400h-Monitor-v0.3.5-v28-D057-renderer-debug.apk`. Its later exact target run keeps parked CPU at `17.17%` but regresses moving CPU to `89.10%`, so it is not promoted; scheduler/core/session behavior is untouched.
- D-056 makes `RX400h-UI-layout-v035-pixel-v2 (3).json` the exact presentation source instead of normalizing the user's coordinates or redrawing the supplied vehicle art.
- Render 640×360 logical pixels with nearest-neighbour 2× output for the 1280×720 target; fixed Chinese uses Noto Sans SC physical-pixel sprites and dynamic values use the supplied bitmap glyphs.
- Add the confirmed SOC/wheel/HV-power animation and startup self-test semantics, including one post-test power cursor only; preserve the 10%/100% Idle Check indicator.
- Add a matching local-only settings overlay for scanline opacity/width/gap and bounded halo intensity/radius. Defaults are the final JSON values.
- Preserve D-055 failed-evidence recovery behavior. Protocol, decoder, SignalStore, scheduler/request periods, logger evidence and Idle Check eligibility remain unchanged.
- Local verification completed 2026-09-01: production/test compilation, 111/111 direct JUnit, `lintDebug` (0 errors / 9 non-blocking warnings), `assembleDebug`, version/resource/hash inspection and fixed-v2 signature verification pass. The 2,656,023-byte dirty-worktree APK has SHA-256 `020a0d9fb05e718e0eef6e100edbdd306dcba244f7b4017b67e2967f048d30f6`, embeds base `8f1a3b9` with `GIT_DIRTY=true`, and is copied to `../../deliverables/RX400h-Monitor-v0.3.5-v28-pixel-dashboard-debug.apk`. The API 26 GUI retry cleared the old lock and reproduced the pre-boot failure with fresh userdata, a clean isolated 1280×720 AVD, software acceleration and valid hardware acceleration; the remaining blocker is this host's Emulator/QEMU/WHPX runtime, so no GUI pass is claimed. Target GUI/OBD/recovery smoke remains manual. Full review: `CODE_REVIEW_V0.3.5_V28.md`; retry evidence: `../../outputs/v28-gui-retry/README.md`.

### V0.3.5/v27 — signal-driven native CRT schematics (authorized 2026-08-30)

- D-054 translates the user's `RX400h-UI-layout-v035.json` into the target API 27/1280x720 landscape composition while retaining the responsive/scroll fallback. Phone-portrait optimization remains deferred.
- Recreate the three embedded picture references as lightweight native drawing rather than APK image assets: BAT uses real SOC and whole-pack temperature summary, VEH uses direction-neutral wheel motion from fresh speed plus explicit coolant/12 V/engine indicators, and PWR maps fresh HV battery power to a `-50 … +50 kW` charge/discharge scale. Unknown/stale values do not become fake zero and unsupported gear/MG/wheel/battery-module state is never inferred.
- Keep numeric values authoritative. Short visual interpolation is capped at 20 fps and runs only while the view is attached/visible and a real signal transition or movement requires it; geometry/drawing objects are cached and there is no full-screen animation, PNG/frame sequence, Compose, WebView, blur/shader framework or new dependency.
- Replace the old invisible inactive Idle Check presentation with the user's low-brightness framed state; actual active state switches to 100% phosphor brightness without blinking or pulsing. `IdleCheckState` thresholds/timing and `SignalStore` semantics remain unchanged.
- Normalize the user-edited approximate placement into exact target geometry: common card baselines, equal gutters, centered domain titles/metrics/schematics, evenly spaced controls and a right-aligned status block. The JSON remains a composition guide rather than a source of accidental offsets.
- D-055 prevents failed save/startup recovery from permanently locking out live monitoring. Failed evidence remains intact and retryable; once the failed worker, writers, socket and lease are safely released, `SAVE_FAILED` permits device selection and Start as well as End-to-retry. Starting detaches only in-memory ownership and never deletes or claims success for the old session; recovery never runs concurrently with LIVE.
- Frozen outside the D-055 exception: protocol, decoder, `SignalStore`, scheduler/request periods, transport transactions, evidence content/integrity and single-session ownership. Local implementation/build is authorized; push, PR, install and vehicle action are not.
- Local verification completed 2026-08-31: production/test compilation, `lintDebug` (0 errors / 9 existing warnings), `assembleDebug`, fixed-v2 signature verification and 113/113 direct JUnit tests all pass. The dirty-worktree APK is 2,612,662 bytes with SHA-256 `0b803f3cf3759d96e350e6ea4f18146ae263c1250cfa557780d8011a84bd533e`; it embeds base commit `8f1a3b9fb49359484c283ea6c3eaf69c631b4b45` and `GIT_DIRTY=true`. A detailed review is in `CODE_REVIEW_V0.3.5.md`; target API 27 GUI, paired-OBD and forced recovery smoke remain manual gates.

### V0.3.4/v26 — target-head-unit first-frame and daylight CRT correction (local implementation 2026-08-27)

- User-authorized presentation-only correction D-053 targets the actual API 27 Spreadtrum head unit at 1280x720 (approximately 1280x672 app content after the navigation bar). Phone-portrait optimization is deferred; the existing responsive fallback remains but is not a release gate for this correction.
- Fix the production cold-start measurement path so native auto-size text, custom ViewGroup geometry and inset-safe viewport state settle before the first visible frame. One attach-scoped stabilizer may suppress at most two pre-draws, then removes itself and always releases the third draw; it is not an unbounded layout loop or steady-state listener. Real viewport width/height, density and font scale are now part of layout invalidation.
- Remove the debug preview's three-pass recursive force-layout workaround because it made prior screenshots represent only the post-refresh steady state and masked the production first-frame overlap reported across V0.3.1-V0.3.3.
- Increase daylight legibility with a brighter phosphor-green hierarchy, selectively bold primary values/titles/buttons and visibly stronger but still static, allocation-free scanlines. No animation, shader, blur, new UI framework or dependency is permitted.
- Frozen: three controls/domains, every signal binding and unit, active-only Idle Check, protocol, decoder, SignalStore, scheduler/request periods, transport, logger and session semantics. No push, install or vehicle action is included in this local implementation authorization.
- Local source verification passes production/test compilation, 99/99 direct JUnit tests and `lintDebug` with 0 errors / 9 existing warnings. Exact-clean implementation commit `b60619d5c1f4e011508b3cf74de6fee7422ec720` was then rebuilt as V0.3.4-debug/v26 with embedded `GIT_DIRTY=false`; the 2,591,078-byte fixed-v2-certificate APK has SHA-256 `fa88064be3450ccb8765217ed0f12e97e01793c66434b07b0901777b4072002b`. The standard Gradle test worker retains the known Windows/Unicode-path `GradleWorkerMain`/closed-pipe launch failure before test execution. No local-emulator GUI evidence is claimed: no API 27 image is installed, and both hardware-accelerated and software API 26 emulator boots stop before ADB on this host. The user later installed the candidate and accepted the actual target's natural first frame, notification-shade geometry stability, scanlines, brightness and bold text; the remaining daylight limit is accepted as panel-bound. No push or release publication was performed.

- Adopt the project-specific `Chat → Work → Codex` routing model (D-045). Add standalone `CHAT_ROLE.md` and `WORK_ROLE.md` cards with frozen RX400h boundaries, task routing, `TASK_PACKET`, `CODEX_ESCALATION_PACKET`, build/ADB guidance and evidence-return formats. Codex becomes the senior architecture/troubleshooting escalation path; routine implementation and machine operations default to Work.

### V0.3.3/v25 — bounded recovery/lifecycle/admission-data correction + CRT integration (local implementation 2026-08-21)

- Implement D-048 inside the still-open V0.3.0 Scheduler / Refresh Frontier milestone. Gradle and workflow identity advanced to V0.3.3/v25; the exact local artifact was subsequently installed and tested on the API 27 target. This is normal-flow evidence, not a promotion or forced-recovery pass.
- D-049 adds an explicit `RECOVERING` phase and a bounded metadata startup fast path so healthy completed/published history does not require full ZIP hashing at every launch. Non-terminal, missing, inconsistent or publication-pending sessions retain full ZIP/manifest/size/SHA/session validation before recovery or retry.
- D-050 gives End, `onDestroy` and finalize one session-scoped exactly-once logical terminalization owner. A latched user End cannot be overwritten by later Activity destruction; archive/publication steps remain retry-idempotent across process death and next-launch recovery.
- D-051 replaces the zero-sample planning seed, only on its pinned hardware, with named model `api27_sp7731e_obdlink_v030_capacity_002_p95_v1`. Primary training provenance is the clean exact-commit 2026-08-15 long-run pair: ZIP SHA-256 `0dc6b6a71f40365b18febe6a815eeb13f2a6bb32ee0a4dcb6237091421a245f7` and `d304e9dfabb1f1d4a28eda9b9c0fc674c04276ce119e8ea5a94e8cdf783432d7`, commit `8e55c6a`, exact CI APK `841b1a4a…`. Frozen request-service p95/sample inputs are `std_core 141/5631`, `cd_f3 139/4503`, `coolant 139/1501`, `c3 162/5630`, `c4 169/3000`, `cf 151/900`, `atrv 81/1498` (milliseconds/samples). Header setup is direction-aware: steady `7E0 → 7E2 = 65 ms / 4500` and `7E2 → 7E0 = 116 ms / 4498`; observed cold `NONE → 7E0 = 154 ms / 2` and explicit unobserved `NONE → 7E2 = 154 ms / 0` are conservative, statistically untrusted reconnect bounds. Missing either cold target fails closed rather than silently borrowing another direction. The exact empirical population is `8998 steady + 2 observed cold = 9000 total`. A pure hardware selector permits this trusted model only for API 27, manufacturer `sprd`, model `sp7731e_1h10_native`, device `sp7731e_1h10` and a case-sensitive `OBDLink MX+` family name; all other contexts retain the untrusted diagnostic seed and admission `UNKNOWN`. The deterministic 60-second production-policy replay is `ADMITTED` at projected utilization `0.906533`, with zero projected deadline misses and zero capacity rejections. The two 2026-08-18 dirty-CRT short sessions remain holdout/regression evidence, not sole training provenance. All seven target periods, phases and deadlines remain unchanged; no rate-ladder step is included.
- D-052 records material forward/backward wall-clock adjustment as evidence only. Scheduling, freshness, Idle Check, duration, checkpoint, lifecycle and recovery truth remain monotonic and must not react to the wall step.
- Integrate the already verified fixed CRT Green skin from D-047 without changing D-041 geometry, the three controls/domains, signal bindings, active-only Idle Check, dependencies or presentation boundary.
- Frozen/prohibited: protocol whitelist/profile, request/header set, decoder/version/formulas, `SignalStore` semantics, derived physics, request periods/phases/deadlines, arbitrary commands, new runtime/UI framework and generalized theme architecture. No push, PR, release publication or vehicle action is included.
- Exact-clean local implementation commit `c9ad39759021fd8d4ca529b115ab7932aa2bf8d8` compiles production/test sources successfully. Direct JUnit execution of the freshly compiled suite passes 98/98 tests; `lintDebug` reports 0 errors / 9 existing warnings; `assembleDebug`, V0.3.3-debug/v25 manifest inspection and fixed-certificate APK Signature Scheme v2 verification pass. The 2,589,038-byte APK embeds `GIT_DIRTY=false` and has SHA-256 `2d75bd7d1bc6be8a923495e901c05ce904d58966f8e10ff3e12b2e980a33d0a1`. The standard Gradle test worker cannot start on this Windows/Unicode-path host (`GradleWorkerMain` classpath/pipe failure), so this is recorded separately from the passing test results. Exact-artifact API 26 GUI smoke is captured at 1280x720, 360x800 and 800x360 with scroll reachability and active-only Idle Check; the exact 800x600 recapture is host-blocked after emulator cold-boot/ADB failure and is not claimed.
- Two later hash-audited API 27 sessions bind exactly to `c9ad397` and that clean APK: `RX400h Monitor log 2026-08-26 09-10-02.zip` SHA-256 `c917e183eddb496e4ccdc5827e63583ea6dca59bc225176b066cd44a66af5201` completed `USER_END` with 1,725 transactions and scheduler conservation `1268 = 1209 on-time + 1 late + 57 capacity-rejected + 1 session-ended`; `RX400h Monitor log 2026-08-26 19-11-23.zip` SHA-256 `5364e69e85dae8d18358acb14f46e3544b96c73b3d82bba09112b628c1b0ecda` completed `USER_END` with 5,623 transactions and all 4,006 releases on time. Both have zero recorded errors, complete evidence and an external public-export receipt. Exact V0.3.3 target normal start/LIVE/End/public-save is therefore evidenced; forced interrupted recovery and remote exact-commit CI remain open.

### V0.3.x — CRT Green presentation prototype (started 2026-08-16)

- User-authorized presentation-only prototype D-047 restyles the existing responsive Android View dashboard as a restrained monochrome green CRT terminal: near-black surfaces, phosphor-green hierarchy, thin instrument frames, stateful terminal buttons, subtle static scanlines and a bounded low-radius glow.
- The stable View tree, actual-window responsive reflow, whole-page scrolling, three controls, dashboard fields and active-only Idle Check contract remain unchanged. No protocol, request table, scheduler, decoder, SignalStore, transport, logger, session-lifecycle or app-version change is authorized by this prototype.
- The implementation remains dependency-free and allocation-free in steady state: no Compose, WebView, runtime blur, animated shader, particle system or generalized theme framework. Local verification on the uncommitted prototype passed 72 JVM tests with 0 failures/errors/skips, lint with 0 errors / 9 existing warnings, debug assemble and fixed-certificate APK Signature Scheme v2 verification. Exact API 26 screenshots passed at 1280×720, 360×800, 800×360 and 800×600; portrait/short-landscape scroll reachability and active-only Idle Check were also exercised. D-047 now accepts this fixed skin for V0.3.3 integration; no Git publication was performed.

### V0.3.2 — capacity-aware scheduler reconstruction (started 2026-08-12)

- User-authorized D-046 supersedes D-040's scheduling semantics after the V0.3.0 E1 review: keep all seven requests and target periods, but replace completion-relative rebasing and whole-batch dispatch with epoch-anchored releases, explicit `release + period` deadlines and one-item replanning after every transaction.
- Replace the duplicate-by-construction global miss/skip interpretation with conserved per-request terminal outcomes: executed on time, executed late, capacity rejected, expired unexecuted, transport unavailable or session ended. Preserve legacy performance columns only as derived compatibility values and add auditable scheduler event/per-request streams.
- Model ELM header as measured state and minimize transitions only within deadline-feasible orders. `ATRV` remains header-neutral. Reconnect keeps the original release timeline instead of making all seven requests immediately due.
- Keep strict serial prompt-delimited transactions. Remove the unsupported fixed `120 ms` gap / `80 ms` pre-drain / `80 ms` post-prompt quiet waits from the normal scheduled hot path; initialization, identity and recovery retain conservative boundaries. HA/HCI evidence supplies the clean-room serial core order and median ~159 ms six-command working point, not source code or trusted per-command p95 values.
- Add fail-closed capacity assessment (`UNKNOWN`, `ADMITTED`, `OVERLOADED`) using complete trusted request/header p95 costs and a deterministic 60-second production-policy replay. Only `ADMITTED` can permit later rate-ladder work; initial seed estimates remain explicitly diagnostic until a same-period E1 validates them.
- Candidate identity: `versionName = 0.3.2`, `versionCode = 24`, scheduler profile `v030_capacity_002`, artifact `RX400hProtocolProbe-v0.3.2-capacity-scheduler-debug-signed`. Protocol profile, decoder, whitelist and periods remain unchanged. Local verification passed 72 JVM tests (0 failures/errors/skips), lint (0 errors / 9 warnings), assemble, manifest and fixed-key v2 signature checks. Exact clean implementation commit `8e55c6a` also passed GitHub Actions run `31635798035`; its APK SHA-256 is `841b1a4adb9f9e4a1834d2830dd6e94754a54cbcc3b2b3209023061da1969e9b`. API 27 paired-OBD normal connection/LIVE/End/archive and same-period E1 are now evidenced; forced interrupted recovery and trusted-model integration remain pending.
- API 27 same-period evidence now includes two hash-audited completed `USER_END` sessions: `11-52-40.zip` SHA-256 `c3cdba1706a3bd610d106403ed505c4479562ad174b15ee5ceccf38dc5ac24c9` (1,389 successful transactions; 979 on-time scheduler completions) and `20-29-22.zip` SHA-256 `a806771c8fb6d09aadc6db102716e58a3429c0ca75ea133fe4bba6385118c8b7` (3,044 successful transactions; 2,163 on-time scheduler completions). CRC/manifest/hash/count/tail/time-order checks pass with zero errors. They bind to APK SHA-256 `af4ce2ba7e9899a4d33bf304038cb4df13f7d4a9986fb65da5f1662f4ab5b51f`; its dirty state is presentation-only and audited core files match HEAD `3b48d2d`. Normal API 27 connection/LIVE/End/archive and same-period E1 are therefore evidenced; forced interrupted recovery and trusted-model integration remain pending.

### V0.3.1 — three-button session flow and resilient logs (development started 2026-08-11)

- User-approved product contract (D-043): replace the four actions with exactly `设备` / `开始` / `结束`. Start connects, initializes and validates the selected OBD adapter before automatically entering LIVE; End requests one stop and the same session task closes and finalizes the run. There is no independent disconnect/stop/export action and no forced share chooser.
- Control safety is owned by one small typed session state. A single worker task owns connect → initialize → runtime configuration → LIVE → stop → close → save, preventing device changes, duplicate starts, finalization races and cross-session writes.
- Durable logging contract (D-044): keep streaming raw/decoded/frame/health files in the permission-free app working directory; use monotonic time-based flush/sync plus atomic metadata checkpoints; recover incomplete sessions at next launch without claiming complete evidence.
- Normal archives use the local end time, e.g. `RX400h Monitor log 2026-08-11 18-23-59.zip`; recovered archives are explicitly marked interrupted and use their last durable-record time. Final archives are automatically published to `Download/RX400h Monitor` through MediaStore (API 29+) or the narrow legacy Downloads permission path (API 26–28).
- Version mapping (D-042): app candidate `versionName = 0.3.1`, `versionCode = 23`; artifact `RX400hProtocolProbe-v0.3.1-resilient-logs-debug-signed`. The V0.3.0 scheduler/frontier engineering milestone remains open; protocol, decoder, scheduler profile, request set and target periods are unchanged.
- Add exact per-session build provenance so evidence records identify version name/code, commit/dirty state, build type and APK identity. Provenance capture now fails the build if Git cannot be executed or does not return a valid commit; CI asserts the embedded commit and clean-worktree flag before publishing an artifact.
- Local implementation completed: independent 2-second flush / 10-second durable-checkpoint task; atomic checkpoint reads; retry-idempotent interrupted-session recovery with pre-recovery metadata preservation and `AtomicFile` companion handling; completed-session file/hash/count/tail/session-identity downgrade checks; CRC/manifest/SHA/session-bound ZIP verification; hash-deduplicated automatic public publication; read-back-verified best-effort SAF publication; exact fail-closed build provenance; process-wide vehicle/session/publication ownership; honest `signal_update_hz` and logger checkpoint/fsync timing fields. Original vehicle request/decoder/scheduler profiles remain unchanged.
- Local verification completed before commit: 62 JVM tests passed, lint reported 0 errors / 9 non-blocking warnings, assemble succeeded, and APK Signature Scheme v2 verified with the fixed certificate. Local V0.3.1/v23 APK: 2,503,690 bytes; SHA-256 `bd3b252e09f193f3754e8f7d0597ef72841ad8e964e431ba3fd85690d0ae14a3`. That APK honestly records base commit `f1f7b2d3ab3d70fec519fc0ba4745f2981ac93e9` and `git_dirty=true`; the implementation was subsequently committed and pushed as `43a959b`. API 26 (Android 8.0) emulator installation, cold launch, three-control states and portrait/landscape reflow passed without a fatal crash; DevicePicker/scroll/rotation were also exercised on the same V0.3.1 UI candidate. Clean exact-commit GitHub artifact, API 27 and paired-OBD connection/public-save/interruption-recovery smoke remain pending; this is not yet a promoted baseline.

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
- Responsive/adaptive UI (D-039): layout is derived from the actual app window, not a fixed resolution or aspect ratio. Card column count is computed dynamically (240dp minimum card width, up to 3 columns) so cards reflow from one row to multiple rows as width shrinks; buttons live inside the header at ≥720dp width and move to their own scrollable row below; the data area is inside a vertical ScrollView so short windows scroll instead of shrinking text; live window resizes rebuild only when the layout bucket (columns/header mode/font bucket) changes. `versionCode = 19`, candidate artifact `RX400hProtocolProbe-v0.3.0-responsive-debug-signed`.
- Unit tests: added `ResponsiveLayoutTest` (column-count and row-plan coverage across phone/narrow/wide/ultra-wide widths); suite is now 19 tests.
- Layout-only entry: no protocol, scheduler, signal or presentation-contract changes.

### V0.3.0 scheduler phase — started 2026-08-10

- Added `DeadlineScheduler` core (D-040): independent periods, priority/header-group ordering, deadline-based skip/backpressure (no catch-up storms), executions/deadline-miss/skip counters.
- Added bounded `LatencyWindow` (P50/P95/P99) and scheduler metrics: request Hz, signal-publish Hz, NO DATA / TIMEOUT / BUS error counters appended to `performance.csv`.
- `ProbeLogger`: `APP_VERSION = 0.3.0`, `SCHEDULER_PROFILE = v030_deadline_001`, `performance.csv` schema extended.
- Live loop now executes due requests from the scheduler instead of the fixed `next*` timer cadence; per-request header switching is ordered to minimize `ATSH` changes.
- Request/whitelist unchanged; rates stay at V0.2.0 periods until staged frequency tests.
- `versionCode = 20`, candidate artifact `RX400hProtocolProbe-v0.3.0-scheduler-debug-signed`.
- UI alignment fix: cards in the same row are equal-height (each fills the tallest card) and the data area is bottom-anchored (`fillViewport` + bottom gravity), so the three domain frames share one bottom edge aligned to the screen bottom. `versionCode = 21`, candidate artifact `RX400hProtocolProbe-v0.3.0-bottom-align-debug-signed`.
- Full responsive UI reset implemented (D-041): short-side whole-screen scaling, fixed-width header assumptions, horizontal control scrolling and content-view rebuilds are replaced by component-bounded measurement from the actual inset-safe window. Cards have 240/320/480dp min/preferred/max width, 264dp minimum height, equal height within a row and centered capped rows; controls reflow through safe inline/split/stacked modes; short windows scroll the whole page. Critical text wraps without ellipsis, secondary status text has bounded ellipsis plus a full accessibility description, and touch targets never scale below their physical minimum. POWER is restored to ICE power → RPM → conditional Idle Check → HV battery power; inactive Idle Check is not visible but retains stable measurement space. `versionCode = 22`, candidate artifact `RX400hProtocolProbe-v0.3.0-fluid-ui-debug-signed`; no request, protocol, scheduler, signal or runtime dependency change.

### Verification (2026-08-10)

- Full responsive UI reset local pre-CI candidate (commit pending): forced execution of `:app:testDebugUnitTest`, `:app:lintDebug` and `:app:assembleDebug` completed successfully; 38 unit tests passed (14 responsive-policy tests), lint reported 0 errors / 10 non-blocking warnings, and `apksigner verify` confirmed APK Signature Scheme v2 with the fixed project certificate. Local APK SHA-256: `427a1c0e1950b4154a613e1a7173f9c3c8b5ea372460affec3444dd6863d0364`. API 37 View-level testing at 160dpi covered 360×800, 800×360, 800×600 (4:3), 1280×720 (16:9), 1280×800 (16:10), 1024×768 tablet, 500×720 split, 2560×360 ultra-wide, 4096×1200 extreme-wide, 800×200 extreme-short, and 200×700 with font scale 2.0. Bidirectional sweeps across every structural threshold kept the same PID and Activity token; top/bottom scroll dumps retained all controls and frozen signals, inactive `IDLE CHECK` stayed absent, DevicePicker empty state remained inset-safe/scrollable, and the crash buffer was empty. GitHub Actions / uploaded artifact verification is still pending and this local hash is not yet a remote baseline.
- D-041 local audits passed: no unused responsive constants or private UI members, no duplicated row-width caches/reference-resolution fallbacks, no steady-state `DrawAllocation` lint finding, and no dependency/request/protocol/scheduler diff. Final independent spot-check found no remaining P1/P2 UI issue.
- Header v1 (HEAD `8cd2e00`): GitHub Actions run `31314095899` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-ui-header-debug-signed`, APK SHA-256 `f94ae4aafb45e57bb254074541ebe44c37d934fc4de7dce327db3a7c218ba08c`.
- Header v2 + Chinese display (HEAD `e9479ba`): GitHub Actions run `31315386621` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v2-debug-signed`, APK SHA-256 `27b013c68cc822c59d3f0ca5f320c7150a4c9ea5a71947fe60880ca21427f1d6`.
- Header v3 + Chinese domain cards (HEAD `6a18f18`): GitHub Actions run `31316526865` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v3-debug-signed`, APK SHA-256 `399d268c3781324373b8392258c8393304c9d27ef12408cefa6879fef5d483c2`.
- Header v4 label/value ordering fix (HEAD `0f79111`): GitHub Actions run `31318586138` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v4-debug-signed`, APK SHA-256 `347841bdbd38c37bf2fb92e7823841016a19bcf17db02da095b878edf444d5dc`.
- Header v5 buttons-in-header (HEAD `e971ded`): GitHub Actions run `31318832012` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v5-debug-signed`, APK SHA-256 `4dd73e132d543351f8cd8891055e6810096f30088fe85ee12dfdece2d0e8c349`.
- Screen-proportional typography (HEAD `5cc6426`): GitHub Actions run `31319195137` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v6-debug-signed`, APK SHA-256 `f192a05600d00d455460f41261d0791eefe5657e07f48ddb7331a5cee0f6455d`.
- All-elements proportional layout (HEAD `4df826c`): GitHub Actions run `31319464972` completed **success**; 17 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-header-v7-debug-signed`, APK SHA-256 `bb2b5fdc0034f626d647e25ed6a0b217efb63b446c42351b81cdb7cd6f940cf5`.
- Responsive/adaptive UI (HEAD `1cce730`): GitHub Actions run `31320031870` completed **success**; 19 unit tests passed (includes new `ResponsiveLayoutTest`); artifact `RX400hProtocolProbe-v0.3.0-responsive-debug-signed`, APK SHA-256 `6d4873e02466d1fb492abf0c95b9cea53ffe3dd85f4401784025da9e203dff2f`.
- Scheduler core (HEAD `fd30240`): GitHub Actions run `31320500825` completed **success**; 26 unit tests passed (includes new `DeadlineSchedulerTest` + `LatencyWindow`); artifact `RX400hProtocolProbe-v0.3.0-scheduler-debug-signed`, APK SHA-256 `8cde4227cdc1fd45efa7f133c152b1664678bd95f9b714f023ca28ab194019fe`.
- Bottom-aligned equal-height cards (HEAD `14125dd`): GitHub Actions run `31320956460` completed **success**; 26 unit tests passed; artifact `RX400hProtocolProbe-v0.3.0-bottom-align-debug-signed`, APK SHA-256 `2b8c022c1af8b13ec8c17ce990991e0bfacb17f6db6ab672fd30b4fa28509fbb`.
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
