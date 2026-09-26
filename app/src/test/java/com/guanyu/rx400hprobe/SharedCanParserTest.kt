package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import java.io.File
import java.util.Base64
import org.junit.Assume.assumeTrue

class SharedCanParserTest {
    private fun oldParser(lines: List<String>): List<CanFrame> = lines.mapNotNull { line ->
        val compact = line.replace(Regex("\\s+"), "").uppercase()
        val match = Regex("^([0-9A-F]{3})([0-9A-F]{2,})$").matchEntire(compact) ?: return@mapNotNull null
        val body = match.groupValues[2]
        if (body.length % 2 != 0) return@mapNotNull null
        val bytes = body.chunked(2).mapNotNull { it.toIntOrNull(16) }
        if (bytes.size * 2 != body.length) null else CanFrame(match.groupValues[1], bytes)
    }

    @Test fun parserMatchesOldForGeneratedAndMalformedLines() {
        val random = Random(61)
        val alphabet = "0123456789abcdefABCDEF \t\r\n?>xyz:\u0000\u000b\u000c"
        repeat(5000) {
            val text = buildString { repeat(random.nextInt(90)) { append(alphabet[random.nextInt(alphabet.length)]) } }
            assertEquals(text, oldParser(listOf(text)), ObdParsers.parseCanFrames(listOf(text)))
        }
        repeat(2000) {
            val text = "%03X".format(random.nextInt(4096)) + (0..random.nextInt(20)).joinToString(" ") { "%02x".format(random.nextInt(256)) }
            assertEquals(oldParser(listOf(text)), ObdParsers.parseCanFrames(listOf(text)))
        }
    }

    @Test fun cachedFramesAreUsedWithoutReparsing() {
        val frames = ObdParsers.parseCanFrames(listOf("7E803410D2A"))
        assertEquals(42.0, ObdParsers.decodeStandard(emptyList(), parsedFrames = frames)!!.speedKph!!, 0.0)
    }

    @Test fun assembledStandardMessageSuppliesBothValuesAndExactEvidence() {
        val frames = ObdParsers.parseCanFrames(listOf("7E806410C0FA00D2A"))
        val message = ObdParsers.isoTpMessage(emptyList(), "7E8", parsedFrames = frames)!!
        val decoded = ObdParsers.decodeStandardPayload(message.payload)!!
        assertEquals(1000.0, decoded.rpm!!, 0.0)
        assertEquals(42.0, decoded.speedKph!!, 0.0)
        assertEquals("410C0FA00D2A", message.payloadHex)
        assertNull(ObdParsers.decodeStandardPayload(emptyList()))
        assertNull(ObdParsers.decodeStandardPayload(listOf(0x7f, 1, 0x12)))
        val bytes = (0..255).toList()
        assertEquals(bytes.joinToString("") { "%02X".format(it) }, IsoTpMessage("7E8", bytes).payloadHex)
    }

    @Test fun optionalRecordedCorpusMatchesOldFramesAndRecordedStatuses() {
        val path = System.getenv("RX400H_PARSER_CORPUS")
        assumeTrue("External vehicle corpus supplied by capture tooling", path != null)
        var transactions = 0
        File(path!!).forEachLine { row ->
            val columns = row.split('\t')
            val command = columns[0]
            val lines = columns.drop(4).map { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }
            val frames = ObdParsers.parseCanFrames(lines)
            assertEquals("transaction $transactions", oldParser(lines), frames)
            assertEquals("status $transactions $command", TransactionStatus.valueOf(columns[2]),
                ElmResponseParser.status(command, lines, frames, columns[3].isNotBlank(), columns[1].toBoolean()))
            // Shared-frame and normal entry points must give the same typed values.
            when (command.substringBefore(' ')) {
                "01040C0D0E10", "01050607" -> {
                    val message = ObdParsers.isoTpMessage(emptyList(), "7E8", parsedFrames = frames)
                    assertEquals(ObdParsers.decodeStandard(lines), message?.let { ObdParsers.decodeStandardPayload(it.payload) })
                    assertEquals(message?.payload?.joinToString("") { "%02X".format(it) }, message?.payloadHex)
                }
                "21C3" -> assertEquals(ObdParsers.decode21C3(lines), ObdParsers.decode21C3(emptyList(), parsedFrames = frames))
                "21C4" -> assertEquals(ObdParsers.decode21C4(lines), ObdParsers.decode21C4(emptyList(), parsedFrames = frames))
                "21CF" -> assertEquals(ObdParsers.decode21CF(lines), ObdParsers.decode21CF(emptyList(), parsedFrames = frames))
                "21CDF3" -> assertEquals(ObdParsers.decode21CdF3(lines), ObdParsers.decode21CdF3(emptyList(), parsedFrames = frames))
            }
            transactions++
        }
        assertTrue("Corpus is empty", transactions > 0)
        println("D061 recorded corpus: $transactions transactions; frame/status/cached-output parity PASS")
    }

    @Test fun statusPreservesPromptAndNegativeResponseRules() {
        fun status(line: String, prompt: Boolean = true): TransactionStatus {
            val lines = listOf(line)
            return ElmResponseParser.status("21C3 6", lines, ObdParsers.parseCanFrames(lines), false, prompt)
        }
        assertEquals(TransactionStatus.RESPONSE_PENDING, status("7EA037F2178"))
        assertEquals(TransactionStatus.NEGATIVE_RESPONSE, status("7EA037F2112"))
        assertEquals(TransactionStatus.OK, status("7EA102761C300000000"))
        assertEquals(TransactionStatus.TIMEOUT, status("7EA102761C300000000", false))
        assertEquals(TransactionStatus.NO_DATA, status("NO DATA"))
        assertEquals(TransactionStatus.COMMAND_ERROR, status("?"))
        assertEquals(TransactionStatus.UNKNOWN, status("7EA0061000000000000"))
    }
}
