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
}
