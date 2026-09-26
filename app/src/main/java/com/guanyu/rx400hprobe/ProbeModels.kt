package com.guanyu.rx400hprobe

enum class TransactionStatus {
    OK,
    IN_PROGRESS,
    NO_DATA,
    INTERRUPTED,
    TIMEOUT,
    COMMAND_ERROR,
    BUS_ERROR,
    NEGATIVE_RESPONSE,
    RESPONSE_PENDING,
    UNKNOWN
}

data class CommandResult(
    val command: String,
    val rawLines: List<String>,
    val status: TransactionStatus,
    val promptSeen: Boolean,
    val canFrames: List<CanFrame>? = null
)

enum class SignalStatus {
    IDLE,
    VALID,
    NO_DATA,
    INTERRUPTED,
    TIMEOUT,
    DECODE_ERROR,
    STALE
}

data class SignalValue<T>(
    var value: T? = null,
    var status: SignalStatus = SignalStatus.IDLE,
    var source: String? = null,
    var updatedAtElapsedMs: Long? = null,
    var version: Long = 0L
) {
    fun ageMs(nowMs: Long): Long? = updatedAtElapsedMs?.let { nowMs - it }
}

data class BaselineData(
    val rpm: SignalValue<Double> = SignalValue(),
    val speedKph: SignalValue<Double> = SignalValue(),
    val coolantC: SignalValue<Double> = SignalValue(),
    val adapterVoltageV: SignalValue<Double> = SignalValue()
)

data class HybridData(
    val socPct: SignalValue<Double> = SignalValue(),
    val hvPowerKw: SignalValue<Double> = SignalValue(),
    val batteryTempMinC: SignalValue<Double> = SignalValue(),
    val batteryTempMaxC: SignalValue<Double> = SignalValue(),
    val batteryTempAvgC: SignalValue<Double> = SignalValue(),
    val iceTorqueNm: SignalValue<Double> = SignalValue(),
    val warmupActive: SignalValue<Boolean> = SignalValue(),
    val idleCheckActive: SignalValue<Boolean> = SignalValue()
)

data class CanFrame(val canId: String, val bytes: List<Int>)

data class IsoTpMessage(
    val canId: String,
    val payload: List<Int>,
    val declaredLength: Int = payload.size,
    val complete: Boolean = true
) {
    val payloadHex: String
        get() = buildString(payload.size * 2) {
            val digits = "0123456789ABCDEF"
            for (byte in payload) {
                append(digits[(byte ushr 4) and 15])
                append(digits[byte and 15])
            }
        }
}

data class StandardDecoded(
    val coolantC: Double? = null,
    val rpm: Double? = null,
    val speedKph: Double? = null
)

data class ToyotaC3Decoded(
    val socPct: Double,
    val hvVoltageV: Double,
    val hvCurrentA: Double,
    val hvPowerKw: Double
)

data class ToyotaC4Decoded(
    val warmupActive: Boolean
)

data class ToyotaCfDecoded(
    val batteryTempsC: List<Double>,
    val batteryTempMinC: Double,
    val batteryTempMaxC: Double,
    val batteryTempAvgC: Double
)

data class ToyotaCdF3Decoded(
    val iceTorqueNm: Double
)
