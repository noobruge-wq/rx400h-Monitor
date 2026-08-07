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
