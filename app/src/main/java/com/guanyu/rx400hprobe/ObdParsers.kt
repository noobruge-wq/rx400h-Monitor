package com.guanyu.rx400hprobe

object ObdParsers {
    private fun bytesAfterPrefix(hex: String, prefix: String): List<Int>? {
        val clean = hex.uppercase()
        val index = clean.indexOf(prefix)
        if (index < 0) return null
        val tail = clean.substring(index + prefix.length)
        if (tail.length < 2) return emptyList()
        return tail.chunked(2).filter { it.length == 2 }.mapNotNull { it.toIntOrNull(16) }
    }

    fun rpm(hex: String): Double? {
        val b = bytesAfterPrefix(hex, "410C") ?: return null
        if (b.size < 2) return null
        return ((b[0] * 256) + b[1]) / 4.0
    }

    fun speed(hex: String): Double? {
        val b = bytesAfterPrefix(hex, "410D") ?: return null
        return b.firstOrNull()?.toDouble()
    }

    fun coolant(hex: String): Double? {
        val b = bytesAfterPrefix(hex, "4105") ?: return null
        return b.firstOrNull()?.minus(40)?.toDouble()
    }

    fun adapterVoltage(lines: List<String>): Double? {
        val text = lines.joinToString(" ")
        return Regex("([0-9]+(?:\\.[0-9]+)?)\\s*V", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    fun f302Temps(hex: String): TempCandidate? {
        val b = bytesAfterPrefix(hex, "62F302") ?: return null
        if (b.size < 7) return null
        val values = b.take(6).map { it - 40.0 }
        return TempCandidate(values, b[6] - 40.0)
    }
}
