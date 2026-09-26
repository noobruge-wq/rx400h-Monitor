package com.guanyu.rx400hprobe

/** Fixed request ownership, used only to publish an explicit failed acquisition.
 * The existing SignalStore quality semantics and decoder formulas stay unchanged.
 */
internal class RequestSignalBindings(store: SignalStore) {
    private val core = listOf(store.baseline.rpm, store.baseline.speedKph)
    private val coolant = listOf(store.baseline.coolantC)
    private val torque = listOf(store.hybrid.iceTorqueNm)
    private val c3 = listOf(store.hybrid.socPct, store.hybrid.hvPowerKw)
    private val c4 = listOf(store.hybrid.warmupActive)
    private val temperature = listOf(store.hybrid.batteryTempMinC,
        store.hybrid.batteryTempMaxC, store.hybrid.batteryTempAvgC)
    private val voltage = listOf(store.baseline.adapterVoltageV)

    fun forRequest(id: String): List<SignalValue<*>> = when (id) {
        "std_core" -> core
        "coolant" -> coolant
        "cd_f3" -> torque
        "c3" -> c3
        "c4" -> c4
        "cf" -> temperature
        "atrv" -> voltage
        else -> error("Unknown request: $id")
    }
}
