# D-061 A+B local prototype

Authorized 2026-09-11. Base branch v0.3.0 / 8f1a3b9fb49359484c283ea6c3eaf69c631b4b45 + existing D-060 dirty source. Reference delivered APK db6b33fa2038ae6f9711fc87e0d83907d99fb09d42d1d3b8f156f51965811297. Origin is a local mirror, fetched without merging; GitHub writes/CI are not part of this task. V0.1.9 remains VOID; direct HWUI halo was rejected for pixel mismatch.

## Batches

1. Freeze pre-change source/artifact identity; preserve user's layout, images and prior edits.
2. A: independent full-raster oracle; sharp and composition damage; actual-pixel animation suppression; cached static draw instructions/regions without rearranging z-order. Preserve physical Chinese and 720p CRT effects. No whole-GPU rewrite or low-resolution policy change.
3. A/B: parse CAN lines once with bounded primitive scanning; reuse parsed frames in status and decoder; preserve formulas and incomplete-CF policy. Compare recorded/malformed inputs against the old parser.
4. B: one blocking input pump per socket, fixed ring/bulk buffer, timed wait, close/unblock, missing-prompt/overflow fail closed, no command concurrency. Test fragmented/burst replies, EOF, late prompt, cancellation and reconnect isolation.
5. B research fixtures: whitelist-only STPX encoder and small batched-command parser. Unverified firmware/counter/segmentation behavior cannot become a default enabled runtime path. No STCSEGR, periodic transmit or firmware upgrade in this version.
6. Unit + standard test attempt + lint + build/signature; API26 emulator independent pixel oracle, settings and synthetic 5Hz display replay if available. Same-source non-debuggable performance build is a separate control, not a silent product behavior change. Real Bluetooth/ECU 5Hz remains a vehicle gate.

## Frozen

Seven commands: 7E0/7E8: 01040C0D0E10 2, 01050607 1, 21CDF3 3; 7E2/7EA: 21C3 6, 21C4 5, 21CF 4; ATRV. No other ECU payloads. Two strategies (steady/HA replica default), existing periods, state/Idle/source freshness, all decoded formulas, authored layout/fonts/effects and single-session ownership unchanged. Wheel direction is a separately reported defect, not silently folded into a pixel-equivalent performance comparison.

## Stop/rollback gates

Pixel mismatch, stale pixels, missed source invalidation, changed decoder output, unbounded memory, inability to cancel blocked input, data crossing connections, batch error counted as a completed command, or unsupported adapter command silently enabled. Preserve failed test outputs. Do not overwrite old artifacts, delete user logs, push, install to a physical device, or upgrade firmware.

## Pending evidence

At plan creation no tests had run. Final checkpoint 2026-09-12: implementation/local package complete; see `D061_LOCAL_REVIEW_20260912.md` for exact source/artifacts,165directJUnit+13,465recordedtransactions,bothlint/builds,148/146independentoracleframes,bothGUI+7filefaults,andfourcontrolledperformancecaptures. Same-debug UI CPU reduction about31%; non-debuggable candidate9.07% of a guest core. StandardGradle worker failure is recorded separately, not passed. STPX/STBC remain test-only, not enabled production encoders. No real-adapter/cancel/throughput,80%carfit,longsoak or5Hz gate is closed. Original D060/logs retained; no commit/push/firmware/car action.
