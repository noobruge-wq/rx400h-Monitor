package com.guanyu.rx400hprobe

/** Pure classification shared by the real transport and fault fixtures. */
internal object ElmResponseParser {
    private val textCommands = setOf("ATI", "STI", "AT@1", "ATDP", "ATDPN")
    private val voltage = Regex(".*\\d+\\.?\\d*V.*")

    fun service(command: String): Int? {
        if (command.startsWith("AT") || command.startsWith("ST")) return null
        return command.take(2).toIntOrNull(16)
    }

    fun pending(frames: List<CanFrame>, service: Int?): Boolean =
        service != null && frames.any { matches(it.bytes, 0x7F, service, 0x78) }

    fun status(command: String, lines: List<String>, frames: List<CanFrame>, hasHex: Boolean,
               prompt: Boolean): TransactionStatus {
        if (!prompt) return TransactionStatus.TIMEOUT
        val service = service(command)
        if (service != null && frames.any { matches(it.bytes, (service + 0x40) and 255) }) return TransactionStatus.OK
        if (pending(frames, service)) return TransactionStatus.RESPONSE_PENDING
        if (service != null && frames.any { matches(it.bytes, 0x7F, service) }) return TransactionStatus.NEGATIVE_RESPONSE
        val joined = lines.joinToString(" ").uppercase()
        return when {
            joined.contains("CAN ERROR") || joined.contains("BUS ERROR") || joined.contains("BUFFER FULL") -> TransactionStatus.BUS_ERROR
            joined.contains("STOPPED") -> TransactionStatus.INTERRUPTED
            joined.contains("NO DATA") || joined.contains("UNABLE TO CONNECT") -> TransactionStatus.NO_DATA
            joined.contains('?') -> TransactionStatus.COMMAND_ERROR
            joined.contains("SEARCHING") && !hasHex -> TransactionStatus.IN_PROGRESS
            command in textCommands && joined.isNotBlank() -> TransactionStatus.OK
            hasHex || joined.contains("OK") || joined.contains("ELM") || voltage.matches(joined) -> TransactionStatus.OK
            else -> TransactionStatus.UNKNOWN
        }
    }

    private fun matches(bytes: List<Int>, first: Int, second: Int? = null, third: Int? = null): Boolean {
        if (bytes.isEmpty()) return false
        val offset = when (bytes[0] ushr 4) {
            0 -> { if (bytes.size < 1 + (bytes[0] and 15)) return false; 1 }
            1 -> { if (bytes.size < 3) return false; 2 }
            else -> return false
        }
        val available = if (offset == 1) bytes[0] and 15 else bytes.size - 2
        return available >= 1 && bytes[offset] == first &&
            (second == null || available >= 2 && bytes[offset + 1] == second) &&
            (third == null || available >= 3 && bytes[offset + 2] == third)
    }
}
