package com.guanyu.rx400hprobe

import kotlin.math.PI
import kotlin.math.sin

/** Preallocated debug inputs only. This is not a SignalStore or OBD simulator. */
internal data class DashboardReplaySample(val atMs: Long, val snapshot: DashboardSnapshot)

internal class DashboardReplay(val samples: List<DashboardReplaySample>) {
    init {
        require(samples.size in 2..1801)
        require(samples.first().atMs == 0L)
        require(samples.last().atMs <= 1_800_000L)
        require(samples.zipWithNext().all { (a, b) -> b.atMs > a.atMs })
    }

    /** Latest due input, never a future sample or an unbounded catch-up loop. */
    fun indexAt(elapsedMs: Long): Int {
        var low = 0
        var high = samples.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (samples[mid].atMs <= elapsedMs) {
                result = mid
                low = mid + 1
            } else high = mid - 1
        }
        return result
    }

    companion object {
        const val HEADER = "at_ms,speed_kph,soc_pct,battery_temp_min_c,battery_temp_max_c," +
            "battery_temp_avg_c,hv_power_kw,rpm,coolant_c,adapter_12v_v,ice_power_kw,idle_check_active"

        fun recorded(lines: Sequence<String>): DashboardReplay {
            val iterator = lines.iterator()
            require(iterator.hasNext() && iterator.next() == HEADER)
            val samples = ArrayList<DashboardReplaySample>()
            while (iterator.hasNext()) {
                require(samples.size < 1801)
                val fields = iterator.next().split(',')
                require(fields.size == 12)
                require(fields[11] in listOf("", "true", "false"))
                fun value(index: Int): Double? = fields[index].takeIf { it.isNotEmpty() }?.let {
                    it.toDouble().also { number -> require(number.isFinite()) }
                }
                val version = samples.size.toLong() + 1L
                samples.add(DashboardReplaySample(fields[0].toLong(), snapshot(
                    version, version, value(1), value(2), value(3), value(4), value(5),
                    value(6), value(7), value(8), value(9), value(10), fields[11] == "true"
                )))
            }
            return DashboardReplay(samples)
        }

        /** 120 s at 5 snapshot updates/s, distinct from actual acquisition Hz. */
        fun synthetic(mode: String): DashboardReplay {
            require(mode in listOf("stress5", "wheel", "parked"))
            return DashboardReplay(List(601) { index ->
                val dynamic = mode == "stress5"
                val seconds = index / 5.0
                val phase = seconds * 2 * PI / 10
                val version = index.toLong() + 1
                val slowVersion = if (dynamic) index / 25L + 1 else 1L
                DashboardReplaySample(index * 200L, snapshot(
                    version, slowVersion,
                    speed = when (mode) { "parked" -> 0.0; "wheel" -> 42.0; else -> 50 + 45 * sin(phase / 2) },
                    soc = if (dynamic) 55 + 20 * sin(phase / 6) else 61.5,
                    tempMin = 27.0, tempMax = 33.0,
                    tempAvg = if (dynamic) 29.0 + (index / 25 % 10) * 0.1 else 29.8,
                    power = if (dynamic) 35 * sin(phase) else 0.0,
                    rpm = if (dynamic) 1500 + 900 * sin(phase / 2) else 0.0,
                    coolant = 86.0, voltage = 13.8,
                    ice = if (dynamic) 15 + 12 * sin(phase / 2) else 0.0,
                    idle = false
                ))
            })
        }

        private fun snapshot(
            version: Long, slowVersion: Long, speed: Double?, soc: Double?,
            tempMin: Double?, tempMax: Double?, tempAvg: Double?, power: Double?,
            rpm: Double?, coolant: Double?, voltage: Double?, ice: Double?, idle: Boolean
        ) = DashboardSnapshot(
            speedKph = speed, speedFresh = speed != null, speedVersion = version,
            socPct = soc, socFresh = soc != null, socVersion = version,
            batteryTempMinC = tempMin, batteryTempMaxC = tempMax, batteryTempAvgC = tempAvg,
            batteryTempFresh = tempMin != null && tempMax != null && tempAvg != null,
            batteryTempVersion = slowVersion,
            hvPowerKw = power, hvPowerFresh = power != null, hvPowerVersion = version,
            rpm = rpm, rpmFresh = rpm != null, rpmVersion = version,
            coolantC = coolant, coolantFresh = coolant != null, coolantVersion = slowVersion,
            adapterVoltageV = voltage, adapterVoltageFresh = voltage != null, adapterVoltageVersion = slowVersion,
            icePowerKw = ice, icePowerFresh = ice != null, icePowerVersion = version,
            idleCheckActive = idle, idleCheckVersion = slowVersion
        )
    }
}
