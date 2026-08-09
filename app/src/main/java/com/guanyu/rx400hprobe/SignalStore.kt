package com.guanyu.rx400hprobe

import android.os.SystemClock

/**
 * V0.2.0 typed single-writer signal store.
 *
 * All runtime vehicle signals live here. UI, logging and derived state read
 * from this store; only the acquisition/decoder path writes to it.
 */
class SignalStore(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {
    val baseline = BaselineData()
    val hybrid = HybridData()

    fun <T> update(signal: SignalValue<T>, value: T?, command: String, result: CommandResult) {
        signal.source = command
        if (value != null) {
            val now = clock()
            signal.value = value
            signal.updatedAtElapsedMs = now
            signal.sourceTimestampElapsedMs = now
            signal.version++
            signal.status = SignalStatus.VALID
        } else {
            signal.status = resultToSignalStatus(result)
        }
    }

    fun <T> setDerived(signal: SignalValue<T>, value: T?, source: String) {
        signal.source = source
        val now = clock()
        val targetStatus = if (value != null) SignalStatus.VALID else SignalStatus.STALE
        if (signal.value == value && signal.status == targetStatus) {
            signal.updatedAtElapsedMs = now
            signal.sourceTimestampElapsedMs = now
            return
        }
        signal.value = value
        signal.updatedAtElapsedMs = now
        signal.sourceTimestampElapsedMs = now
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
        listOf(
            baseline.rpm, baseline.speedKph, baseline.coolantC, baseline.adapterVoltageV,
            hybrid.socPct, hybrid.hvVoltageV, hybrid.hvCurrentA, hybrid.hvPowerKw,
            hybrid.iceTorqueNm, hybrid.warmupActive, hybrid.idleCheckActive
        ).forEach { markStale(it, now, 5000L) }
        listOf(hybrid.batteryTempsC, hybrid.batteryTempMinC, hybrid.batteryTempMaxC, hybrid.batteryTempAvgC)
            .forEach { markStale(it, now, 12_000L) }
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
