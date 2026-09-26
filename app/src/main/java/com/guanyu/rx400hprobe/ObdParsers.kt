package com.guanyu.rx400hprobe

object ObdParsers {
    const val DECODER_VERSION = "rx400h-reactive-20260808-002"

    private val voltagePattern = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*V", RegexOption.IGNORE_CASE)
    private val standardSizes = mapOf(0x04 to 1, 0x05 to 1, 0x06 to 1, 0x07 to 1,
        0x0C to 2, 0x0D to 1, 0x0E to 1, 0x10 to 2)
    private const val HEX = "0123456789ABCDEF"

    fun parseCanFrames(lines: List<String>): List<CanFrame> {
        val frames = ArrayList<CanFrame>(lines.size)
        for (line in lines) {
            var digits = 0
            var id = 0
            var high = -1
            var valid = true
            val bytes = ArrayList<Int>(8)
            for (ch in line) {
                if (ch == ' ' || ch in '\t'..'\r') continue // Same ASCII whitespace as the old regex.
                val value = when (ch) {
                    in '0'..'9' -> ch - '0'
                    in 'a'..'f' -> ch - 'a' + 10
                    in 'A'..'F' -> ch - 'A' + 10
                    else -> { valid = false; break }
                }
                if (digits < 3) id = (id shl 4) or value
                else if (high < 0) high = value
                else { bytes.add((high shl 4) or value); high = -1 }
                digits++
            }
            if (valid && digits >= 5 && high == -1) {
                val canId = "${HEX[(id ushr 8) and 15]}${HEX[(id ushr 4) and 15]}${HEX[id and 15]}"
                frames.add(CanFrame(canId, bytes))
            }
        }
        return frames
    }

    fun isoTpMessage(lines: List<String>, expectedCanId: String? = null, allowPartial: Boolean = false,
                     parsedFrames: List<CanFrame>? = null): IsoTpMessage? {
        val frames = parsedFrames ?: parseCanFrames(lines)
        val ids = if (expectedCanId == null) frames.map { it.canId }.distinct() else listOf(expectedCanId.uppercase())
        for (id in ids) {
            val sameId = frames.filter { it.canId == id && it.bytes.isNotEmpty() }
            if (sameId.isEmpty()) continue
            val first = sameId.first().bytes
            when (first[0] ushr 4) {
                0 -> {
                    val length = first[0] and 0x0F
                    if (first.size >= 1 + length) return IsoTpMessage(id, first.subList(1, 1 + length), length, true)
                }
                1 -> {
                    if (first.size < 2) continue
                    val length = ((first[0] and 0x0F) shl 8) or first[1]
                    val data = first.drop(2).toMutableList()
                    var expectedSequence = 1
                    for (frame in sameId.drop(1)) {
                        val bytes = frame.bytes
                        if (bytes.isEmpty() || bytes[0] ushr 4 != 2) continue
                        if ((bytes[0] and 0x0F) != (expectedSequence and 0x0F)) return null
                        expectedSequence++
                        data += bytes.drop(1)
                        if (data.size >= length) break
                    }
                    if (data.size >= length) return IsoTpMessage(id, data.take(length), length, true)
                    if (allowPartial && data.isNotEmpty()) return IsoTpMessage(id, data.toList(), length, false)
                }
            }
        }
        return null
    }

    fun decodeStandard(lines: List<String>, expectedCanId: String = "7E8", parsedFrames: List<CanFrame>? = null): StandardDecoded? {
        val payload = isoTpMessage(lines, expectedCanId, parsedFrames = parsedFrames)?.payload ?: return null
        return decodeStandardPayload(payload)
    }

    fun decodeStandardPayload(payload: List<Int>): StandardDecoded? {
        if (payload.firstOrNull() != 0x41) return null
        var i = 1
        var coolant: Double? = null
        var rpm: Double? = null
        var speed: Double? = null
        // Keep the full standard-block size table so unknown-to-UI PIDs are
        // skipped instead of aborting the parse before RPM/speed are reached.
        while (i < payload.size) {
            val pid = payload[i++]
            val size = standardSizes[pid] ?: break
            if (i + size > payload.size) break
            val values = payload.subList(i, i + size)
            i += size
            when (pid) {
                0x05 -> coolant = values[0] - 40.0
                0x0C -> rpm = u16(values, 0) / 4.0
                0x0D -> speed = values[0].toDouble()
            }
        }
        return StandardDecoded(coolant, rpm, speed)
    }

    fun decode21C3(lines: List<String>, parsedFrames: List<CanFrame>? = null): ToyotaC3Decoded? {
        val payload = isoTpMessage(lines, "7EA", parsedFrames = parsedFrames)?.payload ?: return null
        if (payload.size < 39 || payload[0] != 0x61 || payload[1] != 0xC3) return null
        val d = payload.drop(2)
        if (d.size < 33) return null
        val voltage = d[24] * 2.0
        val current = d[26] * 2.0 - 256.0
        return ToyotaC3Decoded(
            socPct = d[14] / 2.55,
            hvVoltageV = voltage,
            hvCurrentA = current,
            hvPowerKw = voltage * current / 1000.0
        )
    }

    fun decode21C4(lines: List<String>, parsedFrames: List<CanFrame>? = null): ToyotaC4Decoded? {
        val payload = isoTpMessage(lines, "7EA", parsedFrames = parsedFrames)?.payload ?: return null
        if (payload.size < 29 || payload[0] != 0x61 || payload[1] != 0xC4) return null
        val d = payload.drop(2)
        if (d.size < 25) return null
        return ToyotaC4Decoded(
            warmupActive = (d[1] and 0x01) != 0
        )
    }

    fun decode21CF(lines: List<String>, parsedFrames: List<CanFrame>? = null): ToyotaCfDecoded? {
        val payload = isoTpMessage(lines, "7EA", allowPartial = true, parsedFrames = parsedFrames)?.payload ?: return null
        if (payload.size < 27 || payload[0] != 0x61 || payload[1] != 0xCF) return null
        val d = payload.drop(2)
        if (d.size < 24) return null
        val temps = listOf(8, 10, 12, 14, 16, 18, 20, 22).map { index ->
            (u16(d, index) - 32768) / 100.0
        }
        return ToyotaCfDecoded(
            batteryTempsC = temps,
            batteryTempMinC = temps.minOrNull() ?: return null,
            batteryTempMaxC = temps.maxOrNull() ?: return null,
            batteryTempAvgC = temps.average()
        )
    }

    fun decode21CdF3(lines: List<String>, parsedFrames: List<CanFrame>? = null): ToyotaCdF3Decoded? {
        val payload = isoTpMessage(lines, "7E8", parsedFrames = parsedFrames)?.payload ?: return null
        if (payload.size < 19 || payload[0] != 0x61 || payload[1] != 0xCD) return null
        val d = payload.drop(2)
        if (d.size < 14 || d[11] != 0xF3) return null
        return ToyotaCdF3Decoded(
            iceTorqueNm = ((d[3] - 128) * 2).toDouble()
        )
    }

    fun adapterVoltage(lines: List<String>): Double? {
        val text = lines.joinToString(" ")
        return voltagePattern.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun u16(data: List<Int>, index: Int): Int = (data[index] shl 8) or data[index + 1]
}
