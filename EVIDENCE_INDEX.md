# RX400h Monitor — EVIDENCE_INDEX

This is a compact evidence locator. Original evidence files must not be edited in place.

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
Commit: a0ee1a9bd333adf64cc7e7449fd148094e4ce33d (main)
GitHub Actions run: 31194615369 — success
Artifact: RX400hProtocolProbe-v0.2.0-reactive-debug-signed
APK SHA-256: f1c87bda96d1b4238488627300c40343768e809d251fbf154d41fd846960aa3e
Unit tests: 15 passed
```

Role: V0.2.0 Reactive Core implementation baseline (SignalStore, change-driven UI, consumer audit, performance observability, scheduler table). Idle Check state is experimental until replay/natural real-vehicle validation.

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
```

- Device: Samsung SM-F946B (Galaxy Z Fold5), Android 16 / API 36, portrait.
- App `0.2.0`, decoder `rx400h-reactive-20260808-002`; ~7.7 min LIVE; 1287 transactions; 167 frames; 0 errors; evidence complete.
- All signals present after the decoder fix, including speed, RPM and ICE power.
- First natural real-vehicle Idle Check capture: 4 consecutive frames active at RPM 901.5–903, speed 9–13 km/h, ICE power 0 kW, ICE torque 0 Nm, warmup true, coolant 74–75 °C; deactivated at RPM 889 (below 900) and warmup end. Direct E1 support for the minimal eligibility conditions.
- Frame interval median 2.82 s; request latency avg ~150–175 ms.

### V0.2.0 decoder-002 real-vehicle validation — target head unit

```text
RX400h_20260809_045711.zip
```

- Device: Spreadtrum sp7731e head unit, Android 8.1 / API 27, landscape 1280×720.
- App `0.2.0`, decoder `rx400h-reactive-20260808-002`; ~19.8 min LIVE; 3404 transactions; 450 frames; 0 errors; evidence complete.
- All signals present; warmup false for the whole session; no Idle Check trigger observed.
- Weak-hardware stability evidence: PSS ~27–30 MB, Java heap ~1.7–3.3 MB, CPU ~10% of one core, render avg ~1.3 ms, logger write avg ~1.4 ms (max 46 ms spike).
- Frame interval median 2.71 s; request latency avg ~133–157 ms.

---

## D. Bluetooth HCI / RFCOMM evidence

### Bugreport/dumpstate

```text
dumpstate(1).zip
SHA-256: 1d657dc06b631a94aca0aad0012140c03045a66982fa03307065ea9940dc4f3d
```

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

---

## F. Dr Prius cross-check

```text
Dr.+Prius+_+Dr.+Hybrid_7.01_APKPure.xapk
SHA-256: 7246dab1747cce740cfaa5ce71a897ac2075f64b7170029d4053b1f8af42e2b3
```

Role: independent cross-check for `21CF` temperature formula; do not add unrelated requests merely because they appear in another app.

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
