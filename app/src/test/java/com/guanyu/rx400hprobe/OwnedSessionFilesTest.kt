package com.guanyu.rx400hprobe

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OwnedSessionFilesTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun deletesOnlyNamedSessionAndPreservesSiblingEvidence() {
        val root = temp.newFolder("probe_sessions")
        val old = File(root, "RX400h_old").apply { mkdir() }
        File(old, "raw.log").writeText("test")
        val saved = File(root, "RX400h_saved").apply { mkdir() }
        val archive = File(root, "saved.zip").apply { writeText("preserve") }
        OwnedSessionFiles.deleteSession(root, old)
        assertFalse(old.exists())
        assertTrue(saved.exists())
        assertTrue(archive.exists())
    }
    @Test fun refusesRootUnrelatedFoldersAndOutsideTargets() {
        val root = temp.newFolder("probe_sessions")
        val outside = temp.newFolder("RX400h_outside")
        val unrelated = File(root, "downloads").apply { mkdir() }
        for (target in listOf(root, outside, unrelated)) {
            assertTrue(runCatching { OwnedSessionFiles.deleteSession(root, target) }.isFailure)
            assertTrue(target.exists())
        }
    }
}
