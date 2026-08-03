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
    IDLE, SEARCHING_PROTOCOL, WAITING_RESPONSE, VALID, NO_DATA,
    INTERRUPTED, TIMEOUT, DECODE_ERROR, STALE
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

data class TempCandidate(val values: List<Double>, val room: Double?) {
    val max: Double? get() = values.maxOrNull()
    val min: Double? get() = values.minOrNull()
    val average: Double? get() = values.takeIf { it.isNotEmpty() }?.average()
    val hottestThreeAverage: Double? get() = values.sortedDescending().take(3).takeIf { it.isNotEmpty() }?.average()
    val delta: Double? get() = if (max != null && min != null) max!! - min!! else null
}

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
    val score: Int get() =
        valid0100 * 30 + valid010C * 10 + valid0105 * 10 + valid010D * 10 +
            ecuIds.size * 5 - noData * 3 - busErrors * 30
    val averageLatencyMs: Double get() = if (transactions == 0) 0.0 else totalLatencyMs.toDouble() / transactions
}
