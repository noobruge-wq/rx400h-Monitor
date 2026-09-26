import unittest
from export_dashboard_replay import extract


class ReplayExportTests(unittest.TestCase):
    def test_preserves_timing_and_does_not_fill_missing_values(self):
        data = b'timestamp_ms,speed_kph,soc_pct,idle_check_active\n10000,10,61,false\n11003,20,,true\n'
        trace, count, duration = extract(data)
        self.assertEqual(2, count)
        self.assertEqual(1003, duration)
        self.assertEqual('1003,20,,,,,,,,,,true', trace.decode().splitlines()[2])

    def test_clock_reversal_rejected_without_rewriting(self):
        with self.assertRaises(ValueError):
            extract(b'timestamp_ms\n1000\n999\n')

    def test_non_finite_value_rejected(self):
        with self.assertRaises(ValueError):
            extract(b'timestamp_ms,speed_kph\n0,10\n1000,NaN\n')


if __name__ == '__main__':
    unittest.main()
