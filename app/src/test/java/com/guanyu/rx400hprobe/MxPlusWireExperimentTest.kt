package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Test

/** Lab-only encoding/parser research: no production consumer or socket access.
 * Firmware support and physical-frame versus assembled-message count remain gates.
 */
class MxPlusWireExperimentTest {
    private fun stpx(request: ScheduledRequest, responseCount: Int): String {
        require(RequestTable.requests.any { it.id == request.id && it.command == request.command && it.header == request.header })
        require(request.header != null && responseCount in 1..6)
        return "STPX H:${request.header},D:${request.command.substringBefore(' ')},R:$responseCount"
    }

    private fun batch(requests: List<ScheduledRequest>): String {
        require(requests.size in 1..2)
        require(requests.all { it.header != null && it.header == requests.first().header })
        require(requests.all { candidate -> RequestTable.requests.any { it == candidate } })
        return "ATSH${requests.first().header}|" + requests.joinToString("|") { it.command }
    }

    // A received error is a reply, never a successful command/sample.
    private data class BatchReply(val replies: List<String>, val failedIndex: Int?, val notExecuted: Int)
    private fun parseBatch(text: String, commandCount: Int, outputFormat: Int = 0): BatchReply {
        // FRPM F sections 8.15/16: only verbose output has one segment per
        // executed command. Suppress/coalesce cannot be indexed this way.
        require(outputFormat == 0 && commandCount in 1..3 && text.length <= 16_384)
        require(text.trimEnd().endsWith('>') && text.count { it == '>' } == 1)
        val segments = text.trimEnd().dropLast(1).split('|').map { it.trim() }
        require(segments.all { it.isNotEmpty() } && segments.size <= commandCount)
        val terminal = setOf("?", "CAN ERROR", "BUS ERROR", "STOPPED", "BUFFER FULL")
        val failure = segments.indexOfFirst { it in terminal }
        require(failure < 0 || failure == segments.lastIndex)
        require(segments.size == commandCount || failure == segments.lastIndex && failure >= 0)
        for ((index, segment) in segments.withIndex()) {
            if (index == failure || segment == "OK" || segment == "NO DATA") continue
            val lines = segment.lines().map(String::trim).filter(String::isNotEmpty)
            require(lines.isNotEmpty() && ObdParsers.parseCanFrames(lines).size == lines.size) {
                "Unrecognized batch output is not an acquired sample"
            }
        }
        return BatchReply(segments, failure.takeIf { it >= 0 }, commandCount - segments.size)
    }

    @Test fun allVehicleEncodingsPreserveWhitelistPayloads() {
        for (request in RequestTable.requests.filter { it.header != null }) {
            assertEquals("STPX H:${request.header},D:${request.command.substringBefore(' ')},R:1", stpx(request, 1))
        }
    }
    @Test fun sameEcuPairIsSequentialNotANewCombinedVehicleRequest() {
        assertEquals("ATSH7E0|01040C0D0E10 2|21CDF3 3", batch(RequestTable.requests.take(2)))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownPayload() {
        stpx(RequestTable.requests.first().copy(command = "220001"), 1)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMixedTargets() {
        batch(listOf(RequestTable.requests[0], RequestTable.requests[3]))
    }
    @Test fun errorLeavesRemainingCommandsNotExecuted() {
        val reply = parseBatch("OK|?>", 3)
        assertEquals(1, reply.notExecuted)
        assertEquals(2, reply.replies.size)
        assertEquals(1, reply.failedIndex)
    }
    @Test fun noDataDoesNotAbortBatch() {
        assertEquals(0, parseBatch("OK|NO DATA|7E8101361CD00\r7E82100000000>", 3).notExecuted)
    }
    @Test(expected = IllegalArgumentException::class) fun missingPromptCannotAuthorizeNextBatch() {
        parseBatch("OK|7E803410D2A", 2)
    }
    @Test(expected = IllegalArgumentException::class) fun shortSuccessIsNotACompleteBatch() {
        parseBatch("OK>", 3)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsSuppressedOutputRatherThanGuessingIndices() {
        parseBatch("7E803410D2A>", 3, outputFormat = 1)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownOutputEvenWhenSegmentCountMatches() {
        parseBatch("OK|UNRECOGNIZED ERROR|7E803410D2A>", 3)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDataAfterAnError() {
        parseBatch("OK|?|7E803410D2A>", 3)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsMultiplePromptsAsCrossedTransactions() {
        parseBatch("OK>|7E803410D2A>", 2)
    }
}
