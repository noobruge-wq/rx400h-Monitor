# RX400h Monitor — FULL PROJECT CONTEXT SNAPSHOT (Codex Migration)

**Date:** 2026-08-08<br>
**Purpose:** one-file technical digest of the long ChatGPT project history.<br>
**Authority:** if this file conflicts with newer canonical repository documents or newer verified evidence, the newer repository state wins.

**Update 2026-08-09:** V0.2.0 Reactive Core is closed (2026-08-09); `PROJECT_STATE.md`, `DECISIONS.md`, `ROADMAP.md` and `CHANGELOG.md` are authoritative for current status.

## A. Project identity and engineering philosophy

RX400h Monitor is a Lexus RX400h-only native Android monitor. It is intentionally specialized.

Primary product reasons:
- useful dynamic data should refresh much faster than the current Probe-derived ~0.35 Hz frame behavior;
- code should be lighter and less redundant than generic multi-vehicle tools;
- weak Cortex-A55/~1GB-class targets matter;
- UI/skins can evolve later without disturbing vehicle truth;
- protocol claims require evidence;
- real-vehicle testing cost must be minimized.

The project does **not** aim to become:
- generic Toyota diagnostic framework;
- arbitrary scan/injection platform;
- Hybrid Assistant clone;
- “feature-complete reproduction” of every HA state.

## B. Evidence hierarchy

```text
E1 real-vehicle raw evidence
E2 current source
E3 APK direct literal evidence
E4 static callgraph/bytecode inference
E5 official/quasi-official docs
H  hypothesis
R  rejected
```

No plausible-looking value is promoted merely because it looks physically reasonable.

## C. Protocol facts

Physical OBD:
```text
ISO 15765-4 CAN
11-bit
500 kbit/s
ATSP6
```

Confirmed paths:
```text
7E0 -> 7E8
7E2 -> 7EA
```

CAN ID alone is not sufficient to invent ECU functional names.

Current runtime whitelist:
```text
7E0:
  01040C0D0E10 2
  01050607 1
  21CDF3 3

7E2:
  21C3 6
  21C4 5
  21CF 4

adapter:
  ATRV
```

ELM trailing numbers (`... 2`, `... 3`, `... 6`, etc.) are adapter response-count limits, not CAN payload.

Do not reintroduce:
```text
22xxxx
2C
10 02
10 03
7E1/7E3/7E4 scan
arbitrary command input
```

Historical `22 10 01/02/18 14` got `7F 22 11`; this proves service 0x22 was rejected in that context only.

`10 02` = Programming Session.
`10 03` = Extended Diagnostic Session.
No evidence justifies switching the successful path to either.

HCI showed HA's `10/31/2C/F301/F302` under Header 700 was a failed compatibility/discovery branch and not required for normal RX400h data.

## D. HCI performance evidence

HCI/RFCOMM capture recovered 8611 complete ELM transactions.

Main core:
```text
ATSH7E0
01040C0D0E10 2
21CDF3 3
ATSH7E2
21C3 6
21C4 5
```

Median bare cycle:
```text
~0.159 s
~6.28 Hz
```

Effective HA core including optional work:
```text
~5.5 Hz
```

Low frequency:
```text
01050607 1  ~3.106 s
21CF 4      ~5.205 s
```

This is a feasibility reference, not ECU maximum. V0.3.0 should deliberately investigate the practical frontier above HA if latency/error/CPU remain healthy.

## E. Decoder and signal semantics

### Standard block
`01040C0D0E10 2` includes:
- engine load;
- RPM;
- speed;
- ignition timing;
- MAF.

Only RPM/speed are final display requirements. Other typed fields must justify a consumer.

`01050607 1`: coolant required.

### 21CDF3 / 61CD
After `61 CD`, known:
```text
ICE Torque = (d[3] - 128) * 2 Nm
Injection  = u16be(d[12:14]) / 32 µL
```

Injection is real but **must not** be used as a combustion boolean.

Derived:
```text
ICE mechanical kW =
torqueNm * 2π * rpm / 60 / 1000
```

Old C3 `u16(...)/100 = icePowerKw` semantic was wrong and was removed.

### 21C3 / 61C3
Current product requires:
- SOC;
- HV voltage/current inputs;
- HV battery power.

Power sign:
- positive = battery discharge;
- negative = charge/regeneration.

Other motor/PSD fields can be removed from product typed runtime when no consumer remains.

### 21C4 / 61C4
```text
Warmup Active = (d[1] & 0x01) != 0
```

Needed for Idle Check.

### 21CF / 61CF
Eight battery temperatures:
```text
T = (u16be(raw) - 32768) / 100 °C
```

Independent Dr Prius cross-check matched this formula.

### ATRV
Adapter/DLC supply voltage, label `12V OBD`. Do not call it proven battery-terminal voltage.

## F. Engine/HSD and Idle Check

Instant flags reconstructed:
```text
EV          = RPM < 800
ICE Running = RPM >= 800
ICE Spinning = ICE Torque < 0 AND RPM > 0
ICE Off = EV OR ICE Spinning
```

Not mutually exclusive as raw flags.

If one label were needed:
```text
SPINNING priority if negative torque & rpm>0
else RUNNING if rpm>=800
else EV/OFF
```

Historical HSD stages:
```text
S0
Stable S0
Unstable S0
S1a
End S1a
S1b
S2
S3
S4
Idle Check
```

**Product decision:** never show S0-S4 on main UI.

Show only:
```text
IDLE CHECK
```
under RPM, B-level, only while actual state is Idle Check.

Recovered actual Idle Check candidate:
```text
Warmup Active true
900 < RPM < 1100
ICE Power == 0.0 kW
speed <= 55 km/h
stable ~1 s
```

Entering Idle Check still depends on HSD history (notably S3/S4). V0.2.0 must replay-test whether a smaller eligibility state is equivalent before deleting full internal history.

Do not copy HA `IDLECHECK marker` semantics: marker indicates need/wait for Idle Check, not necessarily current Idle Check.

## G. Final default UI

```text
BATTERY
  SOC                  A
  HV BATTERY AVG TEMP  A
  MAX / MIN            B

VEHICLE STATUS
  SPEED                A
  COOLANT              A
  12V OBD              A

POWER
  ICE MECH POWER       A
  ENGINE RPM           A
    IDLE CHECK          B conditional
  HV BATTERY POWER     A
```

Eight independent main physical signals:
- SOC;
- avg battery temp (+ max/min secondary);
- speed;
- coolant;
- 12V OBD;
- ICE mechanical power;
- RPM;
- HV battery power.

No raw HEX/CAN ID/request text in driving UI.

UI style preference:
- black background;
- cyan structure/labels;
- green large values;
- monospace / CRT / 1980s aviation feel;
- high readability;
- low decoration;
- no scanline/grid/car-art overhead by default.

Two visual references are included under `04_EVIDENCE/ui_reference/`.

## H. Current V0.1.10 status

V0.1.10 was built strictly from V0.1.8, not VOID V0.1.9.

Cleanup:
- raw HEX UI removed;
- manual link/HA validation controls removed;
- obsolete READY/ENGINE event buttons removed;
- ProtocolAttempt/bestProtocol/protocol_matrix removed;
- duplicate rawResponse cache removed;
- wrong C3 ICE power semantic removed;
- CDF3 typed ICE torque;
- C4 warmup bit;
- valid AT text response classification fixed;
- response pending requires structured `7F <requested service> 78`;
- FileProvider internal fallback fixed;
- adapter MAC replaced by SHA-256 identifier;
- DashboardUi extracted.

Current UI code is lightweight native Views. Existing inefficiency:
```text
Handler every 500ms
-> refreshStaleStates()
-> renderDashboard()
```
and render unconditionally formats/setTexts values. V0.2.0 should change this to event/dirty updates.

## I. Strongest E1 run

`RX400h_20260807_120303.zip`
```text
SHA e0f4756d1ec8fb712bfdb12d766c984209bbed385f7950e5fa728714ed987f0b
LIVE 22.294 min
3656 tx
479 frames
0 errors
logger clean
evidence complete
```

All 17 final NO_DATA occurred only after deliberate vehicle shutdown. ATRV still worked. This is strong evidence for distinguishing:
```text
vehicle ECU off
!=
Bluetooth/adapter failure
```

Frame median ~2.85s shows scheduler remained intentionally slow.

Ranges:
```text
RPM 0–2646
speed 0–67
coolant 28–87C
12V 11.6–13.9V
SOC 50.196–72.157%
HV 262–380V
HV current -68..+78A
HV power -23.436..+20.436kW
battery temperatures ~14–22C
ICE torque -256..254Nm
derived mech power -7.658..70.380kW
```

Known evidence debt: frames.csv can carry held last-known values after NO DATA without explicit freshness columns.

## J. Reactive architecture target

Do not build a generic event framework.

Preferred conceptual pipeline:
```text
OBD scheduler
→ decoder
→ single-writer SignalStore
→ derived/state
→ lightweight publication (dirty/version)
→ Presentation Contract
→ View/Skin
```

Separate:
```text
acquisition frequency
signal publication frequency
UI text update frequency
animation frame rate
```

Future renderer may do 30fps interpolation:
```text
rawValue = vehicle truth
displayValue = visual interpolation only
```

Never feed display interpolation into logs/state.

## K. Scheduling architecture

Future scheduler:
- fixed small request table;
- targetPeriod;
- priority;
- nextDeadline;
- maxLateness;
- backpressure;
- skip/coalesce obsolete low-priority work;
- no catch-up request avalanche.

N is tiny, so simple O(N) fixed-table scanning can be preferable to generic queues.

Initial engineering target classes (not frozen constants):
```text
FAST: 01040C0D0E10, 21CDF3, 21C3
MEDIUM: 21C4
SLOW: 01050607, 21CF, ATRV
```

Exact periods must be evidence-tuned.

## L. Temporal coherence

At higher rates, ICE power combines RPM and torque from different transactions. Core must track timestamp/source age/skew.

Do not silently compute with temporally incompatible inputs. If max skew/freshness is violated:
```text
DEGRADED / STALE / Unavailable
```

## M. Long-running kernel gaps

Formal runtime state:
```text
DISCONNECTED
CONNECTING
INITIALIZING
LIVE
DEGRADED
RECONNECTING
STOPPING
```

Reconnect must rebuild ELM/protocol/header state deterministically.

ForegroundService belongs before hard Core freeze.

Logger/durability:
- rolling/segments;
- disk threshold;
- crash/orphan recovery;
- bounded ring buffers;
- fallback logger degradation;
- manifest hashes;
- no unbounded in-RAM histories.

Kernel Health should track:
- last valid ECU response age;
- scheduler progress age;
- logger last successful write;
- stale signals;
- reconnect count;
- stalled transaction state.

## N. Performance observability

V0.2.0 should introduce low-overhead telemetry:
- PSS;
- Java heap;
- native heap;
- CPU delta;
- GC where reliably available;
- UI render duration;
- scheduler cycle/deadline stats;
- request latency;
- logger latency;
- session/log bytes;
- stale counters;
- signal acquisition Hz;
- signal publish Hz;
- kernel health.

Prefer streaming `performance.csv` every ~5s and constant-space rolling stats.

Danger sign:
```text
memory monotonically increasing with uptime
```
not merely one high initial sample.

## O. Test policy

Do not spend money only to make a long Session.

Software-only longevity:
- loop 22min E1 fixture for hours;
- millions of transactions;
- virtual clock;
- accelerated state/deadline/stale tests.

Real vehicle answers:
- request is accepted?
- actual OBDLink timing?
- frequency still stable?
- Bluetooth/ECU real behavior?
- real value semantics?

## P. Version roadmap

0.2 Reactive Core:
- typed signal store;
- monotonic time;
- reactive UI;
- consumer audit;
- observability;
- no uncontrolled frequency jump.

0.3 Scheduler:
- deadline/priority/backpressure;
- push fast signals toward practical frontier;
- cross-signal coherence.

0.4 Persistent Runtime:
- ForegroundService;
- connection state machine;
- reconnect.

0.5 Durability:
- logs/disk/orphan/health;
- no expensive long-drive gate.

0.6 Replay:
- LiveSource/ReplaySource;
- deterministic fixtures;
- virtual clock.

0.7 Low-end Performance:
- A55/~1GB measurements;
- tune allocations/GC;
- preserve renderer headroom.

0.8 Product Core freeze candidate:
- final dead-code purge;
- freeze protocols/signals/runtime/log/replay/presentation API.

0.9 RC:
- daily use/fault injection;
- no new features.

1.0:
- vehicle Core frozen;
- skins/settings may evolve later.

## Q. GitHub / signing continuity

Repository:
```text
noobruge-wq/rx400h-Monitor
```

Phone-first historical workflow:
```text
Termux -> git push -> GitHub Actions -> signed debug APK
```

Build:
```text
JDK 17
Gradle 8.9
:app:assembleDebug
apksigner verify
```

Keystore:
```text
.github/signing/rx400h-debug.keystore
SHA256 8e2ecdec24f9f628fd788cd4cb97e614172d3bb600e8d08d8fa8d4a87247bcb1
alias rx400hdebug
password android
```

Debug package:
```text
com.guanyu.rx400hprobe.debug
```

Do not rotate this test signing identity casually or installed APK update continuity breaks.

## R. Repository access migration

ChatGPT connector:
```text
READ = verified
WRITE = attempted, GitHub returned 403 Resource not accessible by integration
```

Codex should not depend on connector write.

Use:
```text
git + gh + browser OAuth
```

The scripts in `02_AUTOMATION` automate:
- install/check gh;
- browser auth initiation;
- git credential setup;
- clone/pull;
- remote validation;
- dry-run write validation;
- handoff docs installation;
- baseline manifest;
- optional commit/push.

First OAuth user confirmation remains manual by design.

## S. Key evidence included in this complete pack

- current V0.1.10 source snapshot;
- V0.1.8 source;
- VOID V0.1.9 source for historical comparison only;
- E1 runs including strongest V0.1.10 session;
- full dumpstate/HCI capture;
- HCI analysis;
- Hybrid Assistant APK used for interoperability research;
- Dr Prius XAPK cross-check;
- engine/HSD reconstruction;
- dashboard/energy-flow reconstruction;
- clean-room core reference docs/contract;
- audit/review documents;
- UI reference images.

Codex should not re-run reverse engineering merely because the artifacts exist. Use the canonical state first; inspect archived evidence only when a question requires it.
