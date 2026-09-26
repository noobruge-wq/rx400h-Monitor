import unittest
from pathlib import Path
from analyze_emulator_calibration import Evidence, build_samples, deduplicate, interval_scenario, prior_frame


class CalibrationTests(unittest.TestCase):
    def evidence(self, perf, frames=(), **kwargs):
        return Evidence(Path("evidence.zip"), {"session_id": "s", "app_version": "0.3.5"}, {}, kwargs, perf, list(frames))

    def test_cpu_uses_monotonic_despite_wall_clock_jump(self):
        rows = [{"elapsed_ms": "5000", "cpu_delta_ms": "3000", "timestamp_iso": "2026-09-01T00:00:05Z"},
                {"elapsed_ms": "10000", "cpu_delta_ms": "2500", "timestamp_iso": "2026-09-01T01:00:10Z"}]
        result = build_samples([self.evidence(rows)])[0]
        self.assertEqual(50, result["process_cpu_pct"])
        self.assertIn("clock_alignment_uncertain", result["quality_flags"])

    def test_bad_cpu_row_does_not_merge_intervals(self):
        rows = [{"elapsed_ms": str(i * 5000), "cpu_delta_ms": cpu}
                for i, cpu in enumerate(["2000", "bad", "2500"], 1)]
        self.assertEqual(50, build_samples([self.evidence(rows)])[0]["process_cpu_pct"])

    def test_first_uptime_sample_is_never_used(self):
        rows = [{"elapsed_ms": str(i), "cpu_delta_ms": "1000"} for i in (98765000, 98770000)]
        result = build_samples([self.evidence(rows)])
        self.assertEqual(1, len(result))
        self.assertEqual(20, result[0]["process_cpu_pct"])

    def test_long_pause_is_flagged_not_silently_dropped(self):
        rows = [{"elapsed_ms": str(i), "cpu_delta_ms": "1000"} for i in (5000, 65000)]
        result = build_samples([self.evidence(rows)])[0]
        self.assertEqual(60000, result["interval_ms"])
        self.assertIn("irregular_interval", result["quality_flags"])

    def test_no_future_frame(self):
        self.assertIsNone(prior_frame([1001], [{}], 1000))
        self.assertIsNone(prior_frame([1000], [{}], 3501))

    def test_mixed_interval_not_labelled_by_its_last_frame(self):
        moving = {"speed_kph": "30"}
        parked = {"speed_kph": "0", "rpm": "0", "ice_power_kw": "0"}
        self.assertEqual("mixed", interval_scenario([0, 1000, 2000], [moving, moving, parked], 0, 2000))
        self.assertEqual("moving", interval_scenario([0, 1000, 2000], [moving] * 3, 0, 2000))
        self.assertEqual("unknown", interval_scenario([0, 5000], [moving] * 2, 0, 5000))

    def test_duplicate_conflicts_fail_closed(self):
        a, b = self.evidence([]), self.evidence([{"elapsed_ms": "1"}])
        self.assertEqual(1, len(deduplicate([a, a])[0]))
        self.assertEqual(([], ["s"]), deduplicate([a, b]))

    def test_elapsed_reset_is_not_a_negative_cpu_observation(self):
        rows = [{"elapsed_ms": str(i), "cpu_delta_ms": "1000"} for i in (5000, 0, 5000)]
        result = build_samples([self.evidence(rows)])
        self.assertEqual(1, len(result))
        self.assertEqual(20, result[0]["process_cpu_pct"])


if __name__ == "__main__":
    unittest.main()
