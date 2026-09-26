# D-063 lean runtime continuation

Local completion checkpoint: `D063_LOCAL_REVIEW_20260917.md`. Six batches implemented/locally checked, with standard Gradle host-worker failure and actual R8 LIVE/MX+ wire/long-soak limits expressly open. Keep exact source/APKs and R8 mapping in the D063 delivery; do not equate packaging or steady-search microbenchmarks with car5Hz.

Authorized 2026-09-17 after explicit scope confirmation: continue source simplification AND A+B/runtime/MX+ optimization, not immediate car-test handoff. Exact control is D062 source ZIP/APKs; all147 manifest entries matched before changes. Branch v0.3.0, HEAD8f1a3b9 + preserved dirty D055-D062. Local mirror fetched, no merge/push.

## Implementation and gates

1. Simplify live log plumbing: pass existing scheduler snapshots directly rather than expand/reassemble dozens of fields; reuse the three fixed-shape high-rate JSON objects under the existing logger lock. Preserve every field/value, explicit null, timestamp site, ordering, counters, checkpoint and failure behavior. Verify alternating records/session values on Android, not only a mocked JVM JSON implementation.
2. Reduce scheduler work without changing decisions: reuse pending-list storage; sort deadline order once per search rather than at every search node; avoid allocating the HA single-index result. Compare full traces against the exact D062 scheduler with virtual clocks and failure/transport scenarios.
3. Eliminate unused wrappers/duplicated small paths when consumer checks prove safe. Keep the selected strategies, cancellation/ownership, saved evidence and authored pixels.
   Remove the DashboardUi forwarding-only wrapper: Activity presentation binds the existing PixelDashboardView directly, still passing DashboardSnapshot/status/control contracts. No drawing or Vehicle Core moves into Activity; migrate the debug preview through the same constructor.
4. Enable the existing Android shrinker for benchmark only as a separate experiment. It should remove unreachable debug-only renderer helpers and unused library code from the car package. Debug remains a full local-validation control. No dependency/download is required; prove installed main/settings/native page and Android file behavior before acceptance. Do not count shrinker effects as source-line reduction or CPU gains.
5. MX+ official documentation review and bounded test-only wire fixtures. No new transport commands, firmware changes, active car experiment, whitelist/decoder/period change or silent strategy downgrade. Unsupported adapter formats remain a documented gate.
6. Recompile, direct unit plus standard Gradle attempt, lint/build, exact-source/artifact hashes, local Android JSON/file/pixel/GUI tests; controlled microbenchmarks or emulator comparisons only with stated scope. Write actual results and remaining limits; do not claim 5Hz or80%car equivalence.

## Boundaries

No user layout/font/effects changes, sampled-log reduction, new unbounded queue/framework, source evidence deletion, physical install, commit/push or remote release. Original D062 deliverables remain untouched and recover deleted code. If preserving logger evidence or scheduler decisions fails, stop that sub-change and retain the last proven local candidate.
