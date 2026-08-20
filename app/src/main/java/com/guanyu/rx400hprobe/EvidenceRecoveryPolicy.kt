package com.guanyu.rx400hprobe

/** Pure recovery rules shared by Android I/O code and deterministic JVM tests. */
internal object EvidenceRecoveryPolicy {
    private val PUBLISHED_TERMINAL_STATUSES = setOf("completed", "interrupted", "start_failed")
    private val derivedBaseNames = setOf(
        "manifest.json",
        "manifest.pre_recovery.json",
        "session.pre_recovery.json",
        "recovery.json",
        "recovery_failure.txt",
        "public_export.json"
    )
    private val derivedFileNames = derivedBaseNames + derivedBaseNames.flatMap { base ->
        listOf("$base.new", "$base.bak")
    } + setOf("session.json.new", "session.json.bak")

    fun acquisitionFileNames(actualNames: Collection<String>): Set<String> =
        actualNames.filterTo(linkedSetOf()) { it !in derivedFileNames }

    fun sourceFileName(manifestEntryName: String, preservedSessionAvailable: Boolean): String =
        if (manifestEntryName == "session.json" && preservedSessionAvailable) {
            "session.pre_recovery.json"
        } else {
            manifestEntryName
        }

    fun identityMatches(
        expectedSessionId: String,
        sessionId: String?,
        manifestSessionId: String?
    ): Boolean = sessionId == expectedSessionId &&
        (manifestSessionId == null || manifestSessionId == sessionId)

    /**
     * Cheap startup classification for an archive that was already fully
     * validated and published. The atomic receipt is the prior validation
     * commit; startup deliberately does not re-hash or inflate the ZIP here.
     * Any uncertainty falls back to the existing deep validation path.
     */
    fun isPublishedTerminalFastPath(
        expectedSessionId: String,
        sessionId: String?,
        status: String?,
        sessionArchiveName: String?,
        receiptSessionId: String?,
        receiptArchiveName: String?,
        receiptArchiveSize: Long?,
        receiptArchiveSha256: String?,
        actualArchiveName: String?,
        actualArchiveSize: Long?,
        actualArchiveExists: Boolean
    ): Boolean {
        if (sessionId != expectedSessionId || status !in PUBLISHED_TERMINAL_STATUSES) return false
        if (!actualArchiveExists || actualArchiveSize == null || actualArchiveSize < 0L) return false
        if (sessionArchiveName.isNullOrBlank() || actualArchiveName != sessionArchiveName) return false
        if (receiptSessionId != expectedSessionId || receiptArchiveName != actualArchiveName) return false
        if (receiptArchiveSize != actualArchiveSize) return false
        return receiptArchiveSha256
            ?.lowercase()
            ?.matches(Regex("[0-9a-f]{64}")) == true
    }
}
