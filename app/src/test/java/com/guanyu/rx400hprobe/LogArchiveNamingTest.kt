package com.guanyu.rx400hprobe

import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogArchiveNamingTest {

    private val endedAt = Instant.parse("2026-08-11T06:23:59Z")
    private val auckland = ZoneId.of("Pacific/Auckland")

    @Test
    fun completedArchiveUsesHumanReadableLocalEndTime() {
        assertEquals(
            "RX400h Monitor log 2026-08-11 18-23-59.zip",
            LogArchiveNaming.archiveName(endedAt, auckland, LogCompletionKind.COMPLETED)
        )
    }

    @Test
    fun interruptedAndStartFailedArchivesAreHonestAboutCompletion() {
        assertEquals(
            "RX400h Monitor log 2026-08-11 18-23-59 interrupted.zip",
            LogArchiveNaming.archiveName(endedAt, auckland, LogCompletionKind.INTERRUPTED)
        )
        assertEquals(
            "RX400h Monitor log 2026-08-11 18-23-59 start-failed.zip",
            LogArchiveNaming.archiveName(endedAt, auckland, LogCompletionKind.START_FAILED)
        )
    }

    @Test
    fun internalSessionIdIsUtcAndIncludesMilliseconds() {
        assertEquals("RX400h_20260811_062359_000", LogArchiveNaming.sessionId(endedAt))
    }

    @Test
    fun filenameAvoidsWindowsAndFatReservedSeparators() {
        val name = LogArchiveNaming.archiveName(endedAt, auckland, LogCompletionKind.COMPLETED)
        assertFalse(name.contains(':'))
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
    }

    @Test
    fun duplicateEndSecondGetsStableHumanSuffix() {
        val dir = Files.createTempDirectory("rx400h-naming-").toFile()
        try {
            val preferred = "RX400h Monitor log 2026-08-11 18-23-59.zip"
            assertTrue(File(dir, preferred).createNewFile())
            assertTrue(File(dir, "RX400h Monitor log 2026-08-11 18-23-59 (2).zip").createNewFile())
            assertEquals(
                "RX400h Monitor log 2026-08-11 18-23-59 (3).zip",
                LogArchiveNaming.uniqueFile(dir, preferred).name
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun recoveredMetadataCannotEscapeTheLogDirectory() {
        assertTrue(LogArchiveNaming.isSafeArchiveName("RX400h Monitor log 2026-08-11 18-23-59.zip"))
        assertFalse(LogArchiveNaming.isSafeArchiveName("../RX400h Monitor log 2026-08-11.zip"))
        assertFalse(LogArchiveNaming.isSafeArchiveName("C:\\RX400h Monitor log 2026-08-11.zip"))
        assertFalse(LogArchiveNaming.isSafeArchiveName("RX400h Monitor log 2026-08-11.zip/child"))
        assertFalse(LogArchiveNaming.isSafeArchiveName("RX400h Monitor log 2026-08-11\nforged.zip"))
        assertFalse(LogArchiveNaming.isSafeArchiveName("RX400h Monitor log ${"x".repeat(200)}.zip"))
        assertFalse(LogArchiveNaming.isSafeArchiveName("unrelated.zip"))
    }
}
