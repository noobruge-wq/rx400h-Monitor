package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProbeLoggerLifecyclePolicyTest {
    @Test
    fun startFailedSkeletonFillsOnlyMissingNonFinalizerEvidenceFiles() {
        val existing = setOf("raw_io.jsonl", "connection.log")
        val placeholders = ProbeLoggerLifecyclePolicy.startFailedPlaceholderFiles(existing)

        assertFalse("raw_io.jsonl" in placeholders)
        assertFalse("connection.log" in placeholders)
        assertFalse("session.json" in placeholders)
        assertFalse("request_stats.csv" in placeholders)
        assertTrue("device.json" in placeholders)
        assertTrue("events.csv" in placeholders)
        assertTrue("scheduler_events.jsonl" in placeholders)
        assertTrue("scheduler_request_stats.csv" in placeholders)

        val complete = ProbeLoggerLifecyclePolicy.requiredEvidenceFiles +
            ProbeLoggerLifecyclePolicy.capacitySchedulerEvidenceFiles
        assertEquals(
            emptySet<String>(),
            ProbeLoggerLifecyclePolicy.startFailedPlaceholderFiles(complete)
        )
    }

    @Test
    fun finalizeRequestCannotBeWrittenAfterTerminalEvents() {
        assertTrue(
            ProbeLoggerLifecyclePolicy.mayWriteFinalizeRequest(
                writable = true,
                terminalEventsLogged = false,
                finalizeRequestLogged = false
            )
        )
        assertFalse(
            ProbeLoggerLifecyclePolicy.mayWriteFinalizeRequest(
                writable = true,
                terminalEventsLogged = true,
                finalizeRequestLogged = false
            )
        )
        assertFalse(
            ProbeLoggerLifecyclePolicy.mayWriteFinalizeRequest(
                writable = true,
                terminalEventsLogged = false,
                finalizeRequestLogged = true
            )
        )
    }

    @Test
    fun recoveryEnumerationFailureIsNotTreatedAsAnEmptyCleanRoot() {
        assertEquals(emptyList<File>(), ProbeLoggerLifecyclePolicy.recoveryEntries(emptyArray()))
        assertThrows(IllegalStateException::class.java) {
            ProbeLoggerLifecyclePolicy.recoveryEntries(null)
        }
    }
}
