package com.guanyu.rx400hprobe

import android.os.SystemClock

/**
 * V0.2.0 typed single-writer signal store.
 *
 * All runtime vehicle signals live here. UI and derived state read
 * from this store; only the acquisition/decoder path writes to it.
 */
class SignalStore(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    val baseline = BaselineData()
    val hybrid = HybridData()
    private val fastSignals = arrayOf(
        baseline.rpm, baseline.speedKph, baseline.coolantC, baseline.adapterVoltageV,
        hybrid.socPct, hybrid.hvPowerKw,
        hybrid.iceTorqueNm, hybrid.warmupActive, hybrid.idleCheckActive
    )
    private val temperatureSignals = arrayOf(
        hybrid.batteryTempMinC, hybrid.batteryTempMaxC, hybrid.batteryTempAvgC
    )

    fun <T> update(signal: SignalValue<T>, value: T?, command: String, result: CommandResult) {
        signal.source = command
        if (value != null) {
            val now = clock()
            signal.value = value
            signal.updatedAtElapsedMs = now
            signal.version++
            signal.status = SignalStatus.VALID
        } else {
            val newStatus = resultToSignalStatus(result)
            if (signal.status != newStatus) {
                signal.status = newStatus
                signal.version++
            }
        }
    }

    fun <T> setDerived(signal: SignalValue<T>, value: T?, source: String) {
        signal.source = source
        val now = clock()
        val targetStatus = if (value != null) SignalStatus.VALID else SignalStatus.STALE
        if (signal.value == value && signal.status == targetStatus) {
            signal.updatedAtElapsedMs = now
            return
        }
        signal.value = value
        signal.updatedAtElapsedMs = now
        signal.version++
        signal.status = targetStatus
    }

    fun markDecodeFailure(signals: List<SignalValue<*>>, command: String, result: CommandResult) {
        signals.forEach { signal ->
            signal.source = command
            val newStatus = resultToSignalStatus(result)
            if (signal.status != newStatus) {
                signal.status = newStatus
                signal.version++
            }
        }
    }

    fun markStale(signal: SignalValue<*>, now: Long, thresholdMs: Long) {
        val age = signal.ageMs(now) ?: return
        if (signal.value != null && age > thresholdMs && signal.status != SignalStatus.STALE) {
            signal.status = SignalStatus.STALE
            signal.version++
        }
    }

    fun refreshStaleStates(now: Long) {
        for (signal in fastSignals) markStale(signal, now, 5000L)
        for (signal in temperatureSignals) markStale(signal, now, 12_000L)
    }

    fun clear() {
        clearSignal(baseline.rpm)
        clearSignal(baseline.speedKph)
        clearSignal(baseline.coolantC)
        clearSignal(baseline.adapterVoltageV)
        clearSignal(hybrid.socPct)
        clearSignal(hybrid.hvPowerKw)
        clearSignal(hybrid.batteryTempMinC)
        clearSignal(hybrid.batteryTempMaxC)
        clearSignal(hybrid.batteryTempAvgC)
        clearSignal(hybrid.iceTorqueNm)
        clearSignal(hybrid.warmupActive)
        clearSignal(hybrid.idleCheckActive)
    }

    private fun <T> clearSignal(signal: SignalValue<T>) {
        signal.value = null
        signal.status = SignalStatus.IDLE
        signal.source = null
        signal.updatedAtElapsedMs = null
        signal.version = 0L
    }

    companion object {
        fun resultToSignalStatus(result: CommandResult): SignalStatus = when (result.status) {
            TransactionStatus.NO_DATA -> SignalStatus.NO_DATA
            TransactionStatus.INTERRUPTED -> SignalStatus.INTERRUPTED
            TransactionStatus.TIMEOUT -> SignalStatus.TIMEOUT
            else -> SignalStatus.DECODE_ERROR
        }
    }
}
