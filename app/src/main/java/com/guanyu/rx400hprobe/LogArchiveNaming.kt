package com.guanyu.rx400hprobe

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal enum class LogCompletionKind {
    COMPLETED,
    INTERRUPTED,
    START_FAILED
}

internal object LogArchiveNaming {
    private val sessionStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
        .withZone(ZoneOffset.UTC)
    private val displayStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss")

    fun sessionId(startedAt: Instant): String = "RX400h_${sessionStamp.format(startedAt)}"

    fun archiveName(
        endedAt: Instant,
        zoneId: ZoneId,
        kind: LogCompletionKind
    ): String {
        val localTime = displayStamp.withZone(zoneId).format(endedAt)
        val suffix = when (kind) {
            LogCompletionKind.COMPLETED -> ""
            LogCompletionKind.INTERRUPTED -> " interrupted"
            LogCompletionKind.START_FAILED -> " start-failed"
        }
        return "RX400h Monitor log $localTime$suffix.zip"
    }

    fun uniqueFile(parent: File, preferredName: String): File {
        require(isSafeArchiveName(preferredName)) { "Unsafe archive name" }
        val preferred = File(parent, preferredName)
        if (!preferred.exists()) return preferred
        val extensionIndex = preferredName.lastIndexOf('.')
        val base = if (extensionIndex > 0) preferredName.substring(0, extensionIndex) else preferredName
        val extension = if (extensionIndex > 0) preferredName.substring(extensionIndex) else ""
        var index = 2
        while (true) {
            val candidate = File(parent, "$base ($index)$extension")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    fun isSafeArchiveName(name: String): Boolean =
        name.length in 1..180 &&
        File(name).name == name &&
            name.none { it.isISOControl() } &&
            name.all { it.isLetterOrDigit() || it in " ._()+-" } &&
            !name.contains(':') &&
            !name.contains('/') &&
            !name.contains('\\') &&
            name.endsWith(".zip", ignoreCase = true) &&
            (name.startsWith("RX400h Monitor log ") || name.startsWith("RX400h_"))
}
