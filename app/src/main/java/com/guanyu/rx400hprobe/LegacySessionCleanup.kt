package com.guanyu.rx400hprobe

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/** Narrow legacy migration, not a recorder or recovery engine. Ambiguity means KEEP. */
internal object LegacySessionCleanup {
    private val noFollow = arrayOf(LinkOption.NOFOLLOW_LINKS)
    @Synchronized fun clean(appFiles: File) {
        val base = appFiles.canonicalFile
        val root = File(base, "probe_sessions").absoluteFile
        if (!root.isDirectory || root != root.canonicalFile) return
        root.listFiles()?.forEach { session ->
            runCatching {
                val target = session.toPath()
                if (!session.name.startsWith("RX400h_") ||
                    !Files.isDirectory(target, *noFollow) || session != session.canonicalFile) return@runCatching
                val children = session.listFiles() ?: return@runCatching
                // Finished/partially published archives, unknown files, links and nested trees are ambiguous.
                if (children.any { !Files.isRegularFile(it.toPath(), *noFollow) ||
                        it.name !in LEGACY_WORK_FILES }) return@runCatching
                val metadata = File(session, "session.json")
                if (!metadata.isFile || metadata.length() !in 1L..65_536L) return@runCatching
                val json = JSONObject(metadata.readText())
                if (!positivelyUnsaved(
                        session.name, json.optString("session_id"),
                        json.optString("application_id"), json.optString("status"),
                        json.has("archive_name") && json.isNull("archive_name"),
                        json.has("ended_at") && json.isNull("ended_at"),
                        json.has("evidence_complete") && !json.optBoolean("evidence_complete", true)
                    )) return@runCatching
                // No ZIP is deleted by this migration. Legacy named/partial ZIPs may mean saving started.
                if (File(root, session.name + ".zip").exists()) return@runCatching
                OwnedSessionFiles.deleteSession(root, session)
            } // A failed cleanup can never prevent real-time acquisition.
        }
    }

    internal fun positivelyUnsaved(folder: String, id: String, applicationId: String,
        status: String, archiveExplicitlyNull: Boolean, endExplicitlyNull: Boolean,
        explicitlyIncomplete: Boolean): Boolean =
        folder == id && (applicationId == "com.guanyu.rx400hprobe.debug" ||
            applicationId == "com.guanyu.rx400hprobe") && status == "active" &&
            archiveExplicitlyNull && endExplicitlyNull && explicitlyIncomplete

    // Publication/finalization markers and manifest are intentionally NOT accepted.
    private val LEGACY_WORK_FILES = setOf("session.json", "raw_io.jsonl", "decoded.jsonl",
        "frames.csv", "events.csv", "errors.log", "connection.log", "performance.csv",
        "scheduler_events.jsonl", "scheduler_request_stats.csv", "checkpoint.json",
        "device.json", "adapter.json")
}
