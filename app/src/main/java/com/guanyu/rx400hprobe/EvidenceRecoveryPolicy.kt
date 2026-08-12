package com.guanyu.rx400hprobe

/** Pure recovery rules shared by Android I/O code and deterministic JVM tests. */
internal object EvidenceRecoveryPolicy {
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
}
