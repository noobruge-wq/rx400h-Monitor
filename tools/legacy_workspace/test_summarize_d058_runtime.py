import unittest
from summarize_d058_runtime import fields, steady, weighted, summarize, interval_state


class SummaryTest(unittest.TestCase):
    def test_parse_types(self):
        self.assertEqual(fields("reason=window cpu=1.25 n=3 hw=true"),
                         {"reason": "window", "cpu": 1.25, "n": 3, "hw": "true"})

    def test_full_window_warmup_and_settle_exclusion(self):
        rows = [{"reason": "window", "elapsedMs": end, "windowMs": 5000}
                for end in (5000, 10000, 15000, 120001)]
        rows.append({"reason": "complete", "elapsedMs": 122000, "windowMs": 1000})
        self.assertEqual([r["elapsedMs"] for r in steady(rows, 120000)], [15000])

    def test_weight_not_arithmetic_mean(self):
        self.assertEqual(weighted([{"cpu": 10, "ms": 1}, {"cpu": 20, "ms": 3}], "cpu", "ms"), 17.5)
        self.assertIsNone(weighted([], "cpu", "ms"))

    def fixture(self, reason="complete", count=2):
        identity = {"run_id": "abc", "mode": "stress5", "api": "26", "screen": "1280x720", "density": "160",
                    "effect_preferences": "", "apk_sha256": "123 /base.apk"}
        head = "09-05 12:00:00.000 10 10 I RX400hReplay: "
        metrics = " mode=stress5 runId=abc windowMs=5000 processCpuPct=10 pssKb=30000 published=2 sourceIndex=1 skipped=0 lateMaxMs=2 windowFrames=10 totalDurationAvgUs=20000 totalDurationMaxUs=25000 over50Ms=0 frameMetricDrops=0"
        text = head + "loaded mode=stress5 durationMs=20000\n" + head + "start mode=stress5 runId=abc plannedSnapshots=2\n"
        text += head + "reason=window elapsedMs=15000" + metrics + "\n"
        text += head + f"reason={reason} elapsedMs=22000" + metrics.replace("published=2", f"published={count}") + "\n"
        return identity, text

    def test_complete_counts_and_metric_units(self):
        result = summarize(*self.fixture())
        self.assertEqual(result["published"], 2)
        self.assertEqual(result["window_frame_hz"], 2)
        self.assertEqual(result["frame_total_avg_us"], 20000)
        self.assertIsNone(result["rebuild_hz"])

    def test_abort_is_not_pass(self):
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            summarize(*self.fixture(reason="background_abort"))

    def test_inconsistent_input_counts_fail(self):
        with self.assertRaises(ValueError):
            summarize(*self.fixture(count=1))

    def test_foreign_run_is_not_accepted(self):
        identity, text = self.fixture()
        identity["run_id"] = "foreign"
        with self.assertRaises(ValueError):
            summarize(identity, text)

    def test_non_monotonic_window_fails(self):
        identity, text = self.fixture()
        with self.assertRaisesRegex(ValueError, "non-monotonic"):
            summarize(identity, text.replace("elapsedMs=22000", "elapsedMs=14000"))

    def test_scenario_never_uses_future(self):
        trace = [{"at_ms": "100", "speed_kph": "20", "rpm": "1200"},
                 {"at_ms": "1000", "speed_kph": "0", "rpm": "0"}]
        self.assertEqual(interval_state(trace, 0, 50), "unknown")
        self.assertEqual(interval_state(trace, 100, 999), "moving_display")
        self.assertEqual(interval_state(trace, 100, 1000), "moving_display")
        self.assertEqual(interval_state(trace, 100, 1001), "mixed_display_states")
        self.assertEqual(interval_state(trace, 1000, 1100), "parked_ice_off_display")

    def test_stale_source_and_unknown_not_stationary(self):
        self.assertEqual(interval_state([{"at_ms": "0", "speed_kph": "0", "rpm": "0"}], 2000, 2600),
                         "unknown_or_source_gap")
        self.assertEqual(interval_state([{"at_ms": "0", "speed_kph": "", "rpm": "0"}], 0, 500), "unknown")


if __name__ == "__main__":
    unittest.main()
