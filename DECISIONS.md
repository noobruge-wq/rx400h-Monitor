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
