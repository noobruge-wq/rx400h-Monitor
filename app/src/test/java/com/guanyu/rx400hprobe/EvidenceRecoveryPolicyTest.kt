package com.guanyu.rx400hprobe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceRecoveryPolicyTest {
    @Test
    fun recoveryDerivedFilesDoNotPolluteFrozenAcquisitionSet() {
        val acquisition = setOf("session.json", "raw_io.jsonl", "frames.csv")
        val afterFailedRecovery = acquisition + setOf(
            "manifest.json",
            "session.pre_recovery.json",
            "manifest.pre_recovery.json",
            "recovery.json",
            "recovery_failure.txt",
            "public_export.json"
        )

        assertEquals(acquisition, EvidenceRecoveryPolicy.acquisitionFileNames(afterFailedRecovery))
    }

    @Test
    fun interruptedFirstAtomicRecoveryWriteDoesNotPolluteAcquisitionSet() {
        val acquisition = setOf("session.json", "raw_io.jsonl")
        val withNewCompanions = acquisition + setOf(
            "session.pre_recovery.json.new",
            "manifest.pre_recovery.json.new",
            "recovery.json.new",
            "public_export.json.new"
        )

        assertEquals(acquisition, EvidenceRecoveryPolicy.acquisitionFileNames(withNewCompanions))
    }

    @Test
    fun interruptedReplacementAtomicWriteDoesNotPolluteAcquisitionSet() {
        val acquisition = setOf("session.json", "raw_io.jsonl")
        val withBackupCompanions = acquisition + setOf(
            "session.json.bak",
            "manifest.json.bak",
            "session.pre_recovery.json.bak",
            "recovery.json.bak"
        )

        assertEquals(acquisition, EvidenceRecoveryPolicy.acquisitionFileNames(withBackupCompanions))
    }

    @Test
    fun repeatedRecoveryValidatesTheFrozenSessionBytes() {
        assertEquals(
            "session.pre_recovery.json",
            EvidenceRecoveryPolicy.sourceFileName("session.json", preservedSessionAvailable = true)
        )
        assertEquals(
            "raw_io.jsonl",
            EvidenceRecoveryPolicy.sourceFileName("raw_io.jsonl", preservedSessionAvailable = true)
        )
    }

    @Test
    fun copiedOrRenamedDirectoryCannotRetainCompletedIdentity() {
        assertFalse(
            EvidenceRecoveryPolicy.identityMatches(
                expectedSessionId = "RX400h_20260812_010203_000",
                sessionId = "RX400h_20260811_010203_000",
                manifestSessionId = "RX400h_20260811_010203_000"
            )
        )
    }

    @Test
    fun manifestIdentityMustMatchSessionWhenPresent() {
        assertTrue(EvidenceRecoveryPolicy.identityMatches("session-a", "session-a", null))
        assertTrue(EvidenceRecoveryPolicy.identityMatches("session-a", "session-a", "session-a"))
        assertFalse(EvidenceRecoveryPolicy.identityMatches("session-a", "session-a", "session-b"))
    }

    @Test
    fun completedPublishedHistoryCanUseMetadataOnlyStartupFastPath() {
        assertTrue(
            EvidenceRecoveryPolicy.isPublishedTerminalFastPath(
                expectedSessionId = "session-a",
                sessionId = "session-a",
                status = "completed",
                sessionArchiveName = "RX400h Monitor log 2026-08-21 12-00-00.zip",
                receiptSessionId = "session-a",
                receiptArchiveName = "RX400h Monitor log 2026-08-21 12-00-00.zip",
                receiptArchiveSize = 1234L,
                receiptArchiveSha256 = "a".repeat(64),
                actualArchiveName = "RX400h Monitor log 2026-08-21 12-00-00.zip",
                actualArchiveSize = 1234L,
                actualArchiveExists = true
            )
        )
    }

    @Test
    fun alreadyPublishedInterruptedAndStartFailedHistoryAlsoUseFastPath() {
        listOf("interrupted", "start_failed").forEach { terminalStatus ->
            assertTrue(
                EvidenceRecoveryPolicy.isPublishedTerminalFastPath(
                    expectedSessionId = "session-a",
                    sessionId = "session-a",
                    status = terminalStatus,
                    sessionArchiveName = "archive.zip",
                    receiptSessionId = "session-a",
                    receiptArchiveName = "archive.zip",
                    receiptArchiveSize = 1234L,
                    receiptArchiveSha256 = "c".repeat(64),
                    actualArchiveName = "archive.zip",
                    actualArchiveSize = 1234L,
                    actualArchiveExists = true
                )
            )
        }
    }

    @Test
    fun uncertainPublicationMetadataAlwaysFallsBackToDeepValidation() {
        fun accepted(
            status: String = "completed",
            receiptSessionId: String = "session-a",
            receiptSize: Long = 1234L,
            receiptHash: String = "b".repeat(64),
            actualSize: Long = 1234L,
            exists: Boolean = true
        ) = EvidenceRecoveryPolicy.isPublishedTerminalFastPath(
            expectedSessionId = "session-a",
            sessionId = "session-a",
            status = status,
            sessionArchiveName = "archive.zip",
            receiptSessionId = receiptSessionId,
            receiptArchiveName = "archive.zip",
            receiptArchiveSize = receiptSize,
            receiptArchiveSha256 = receiptHash,
            actualArchiveName = "archive.zip",
            actualArchiveSize = actualSize,
            actualArchiveExists = exists
        )

        assertFalse(accepted(status = "active"))
        assertFalse(accepted(status = "finalizing"))
        assertFalse(accepted(status = "finalize_failed"))
        assertFalse(accepted(receiptSessionId = "session-b"))
        assertFalse(accepted(receiptSize = 999L))
        assertFalse(accepted(receiptHash = "not-a-hash"))
        assertFalse(accepted(actualSize = 999L))
        assertFalse(accepted(exists = false))
    }
}
