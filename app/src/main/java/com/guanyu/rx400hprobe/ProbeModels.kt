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
    val normalizedHex: String,
    val latencyMs: Long,
    val status: TransactionStatus,
    val promptSeen: Boolean,
    val responsePendingSeen: Boolean = false,
    val firstByteLatencyMs: Long? = null,
    val promptLatencyMs: Long? = null,
    val quietWindowMs: Long = 0
)

enum class SignalStatus {
    IDLE,
    SEARCHING_PROTOCOL,
    WAITING_RESPONSE,
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
    var rawResponse: String? = null
)

data class BaselineData(
    val rpm: SignalValue<Double> = SignalValue(),
    val speedKph: SignalValue<Double> = SignalValue(),
    val coolantC: SignalValue<Double> = SignalValue(),
    val adapterVoltageV: SignalValue<Double> = SignalValue()
)

data class HybridData(
    val socPct: SignalValue<Double> = SignalValue(),
    val hvVoltageV: SignalValue<Double> = SignalValue(),
    val hvCurrentA: SignalValue<Double> = SignalValue(),
    val hvPowerKw: SignalValue<Double> = SignalValue(),
    val batteryTempsC: SignalValue<List<Double>> = SignalValue(),
    val batteryTempMinC: SignalValue<Double> = SignalValue(),
    val batteryTempMaxC: SignalValue<Double> = SignalValue(),
    val batteryTempAvgC: SignalValue<Double> = SignalValue(),
    val mg1Rpm: SignalValue<Double> = SignalValue(),
    val mg2Rpm: SignalValue<Double> = SignalValue(),
    val mg1TorqueNm: SignalValue<Double> = SignalValue(),
    val mg2TorqueNm: SignalValue<Double> = SignalValue(),
    val rearMgRpm: SignalValue<Double> = SignalValue(),
    val rearMgTorqueNm: SignalValue<Double> = SignalValue(),
    val injectionUl: SignalValue<Double> = SignalValue(),
    val iceTorqueRaw: SignalValue<Double> = SignalValue()
)

data class CanFrame(val canId: String, val bytes: List<Int>)

data class IsoTpMessage(
    val canId: String,
    val payload: List<Int>,
    val declaredLength: Int = payload.size,
    val complete: Boolean = true
) {
    val payloadHex: String
        get() = payload.joinToString("") { "%02X".format(it) }
}

data class StandardDecoded(
    val engineLoadPct: Double? = null,
    val coolantC: Double? = null,
    val rpm: Double? = null,
    val speedKph: Double? = null,
    val timingDeg: Double? = null,
    val mafGps: Double? = null,
    val runTimeS: Double? = null,
    val ambientC: Double? = null
)

data class ToyotaC3Decoded(
    val mg2Rpm: Double,
    val mg2TorqueNm: Double,
    val mg1Rpm: Double,
    val mg1TorqueNm: Double,
    val icePowerKw: Double,
    val socPct: Double,
    val auxiliaryTempsC: List<Double>,
    val hvVoltageV: Double,
    val hvCurrentA: Double,
    val hvPowerKw: Double,
    val brakeRegenTorqueRaw: Double,
    val brakeMasterTorqueRaw: Double,
    val rawDataHex: String
)

data class ToyotaC4Decoded(
    val rearMgRpm: Double,
    val rearMgTorqueNm: Double,
    val secondaryRatioPct: Double,
    val brakeRegenAccumRaw: Double,
    val rawDataHex: String
)

data class ToyotaCfDecoded(
    val batteryTempsC: List<Double>,
    val batteryTempMinC: Double,
    val batteryTempMaxC: Double,
    val batteryTempAvgC: Double,
    val scalarTemp3C: Double,
    val scalarTemp4C: Double,
    val statusByte: Int,
    val rawDataHex: String
)

data class ToyotaCdF3Decoded(
    val iceTorqueRaw: Double,
    val injectionUl: Double,
    val rawDataHex: String
)

data class ProtocolAttempt(
    val requestedCode: String,
    val label: String,
    var resolvedCode: String? = null,
    var description: String? = null,
    var valid0100: Int = 0,
    var total0100: Int = 0,
    var valid010C: Int = 0,
    var total010C: Int = 0,
    var valid0105: Int = 0,
    var total0105: Int = 0,
    var valid010D: Int = 0,
    var total010D: Int = 0,
    val ecuIds: MutableSet<String> = linkedSetOf(),
    var validFrames: Int = 0,
    var noData: Int = 0,
    var busErrors: Int = 0,
    var totalLatencyMs: Long = 0,
    var transactions: Int = 0
) {
    val score: Int
        get() = valid0100 * 30 + valid010C * 10 + valid0105 * 10 + valid010D * 10 +
            ecuIds.size * 5 - noData * 3 - busErrors * 30
    val averageLatencyMs: Double
        get() = if (transactions == 0) 0.0 else totalLatencyMs.toDouble() / transactions
}
