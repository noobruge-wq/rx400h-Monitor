package com.guanyu.rx400hprobe

import android.Manifest
import android.annotation.TargetApi
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

internal data class PendingLogArchive(
    val sessionDir: File,
    val zipFile: File,
    val displayName: String
)

internal data class PublicLogResult(
    val success: Boolean,
    val displayName: String,
    val location: String? = null,
    val uri: Uri? = null,
    val error: String? = null,
    val needsUserDestination: Boolean = false
)

/** Publishes a finalized immutable ZIP outside Android/data, never on the poll hot path. */
internal class PublicLogExporter(private val context: Context) {
    fun publish(archive: PendingLogArchive): PublicLogResult = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) publishWithMediaStore(archive)
        else publishLegacy(archive)
    } catch (e: Exception) {
        PublicLogResult(
            success = false,
            displayName = archive.displayName,
            error = "${e::class.java.simpleName}: ${e.message}",
            needsUserDestination = true
        )
    }

    fun publishToUri(archive: PendingLogArchive, destination: Uri): PublicLogResult = try {
        val resolver = context.contentResolver
        val sourceFingerprint = fingerprint(FileInputStream(archive.zipFile))
        val copied = resolver.openOutputStream(destination, "wt")?.use { output ->
            FileInputStream(archive.zipFile).use { input ->
                input.copyTo(output).also { output.flush() }
            }
        } ?: error("Selected output stream unavailable")
        if (copied != sourceFingerprint.sizeBytes) {
            error("Selected archive length mismatch: $copied/${sourceFingerprint.sizeBytes}")
        }
        val destinationFingerprint = try {
            resolver.openInputStream(destination)?.let(::fingerprint)
                ?: error("Selected destination cannot be read back for verification")
        } catch (e: Exception) {
            throw IllegalStateException(
                "Selected destination cannot be read back for verification: ${e.message}",
                e
            )
        }
        if (destinationFingerprint.sizeBytes != sourceFingerprint.sizeBytes) {
            error(
                "Selected destination length verification failed: " +
                    "${destinationFingerprint.sizeBytes}/${sourceFingerprint.sizeBytes}"
            )
        }
        if (destinationFingerprint.sha256 != sourceFingerprint.sha256) {
            error("Selected destination checksum verification failed")
        }
        val actualName = runCatching {
            resolver.query(
                destination,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: archive.displayName
        PublicLogResult(
            success = true,
            displayName = actualName,
            location = destination.toString(),
            uri = destination
        )
    } catch (e: Exception) {
        PublicLogResult(
            success = false,
            displayName = archive.displayName,
            error = "${e::class.java.simpleName}: ${e.message}",
            needsUserDestination = true
        )
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun publishWithMediaStore(archive: PendingLogArchive): PublicLogResult {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/RX400h Monitor/"
        findExistingMediaStoreArchive(archive, relativePath)?.let { return it }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, archive.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                relativePath
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore did not create the archive")
        try {
            val copied = resolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(archive.zipFile).use { input -> input.copyTo(output) }
            } ?: error("MediaStore output stream unavailable")
            if (copied != archive.zipFile.length()) {
                error("Public archive length mismatch: $copied/${archive.zipFile.length()}")
            }
            val sourceHash = sha256(FileInputStream(archive.zipFile))
            val publicHash = resolver.openInputStream(uri)?.use { sha256(it) }
                ?: error("MediaStore archive cannot be verified")
            if (publicHash != sourceHash) error("MediaStore archive checksum mismatch")
            val promoted = resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            if (promoted != 1) error("MediaStore did not publish the archive")
            val actualName = mediaStoreDisplayName(uri, archive.displayName)
            return PublicLogResult(
                success = true,
                displayName = actualName,
                location = "Download/RX400h Monitor/$actualName",
                uri = uri
            )
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun findExistingMediaStoreArchive(
        archive: PendingLogArchive,
        relativePath: String
    ): PublicLogResult? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val sourceHash = sha256(FileInputStream(archive.zipFile))
        resolver.query(
            collection,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.IS_PENDING
            ),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(relativePath),
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val pendingColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
                val displayName = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val pending = cursor.getInt(pendingColumn) != 0
                val sameContent = size == archive.zipFile.length() && runCatching {
                    resolver.openInputStream(uri)?.use { sha256(it) } == sourceHash
                }.getOrDefault(false)
                if (sameContent) {
                    if (pending) {
                        val promoted = resolver.update(
                            uri,
                            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                            null,
                            null
                        )
                        if (promoted != 1) error("MediaStore could not recover pending archive")
                    }
                    val actualName = mediaStoreDisplayName(uri, archive.displayName)
                    return PublicLogResult(
                        success = true,
                        displayName = actualName,
                        location = "Download/RX400h Monitor/$actualName",
                        uri = uri
                    )
                }
                if (pending && displayName == archive.displayName) resolver.delete(uri, null, null)
            }
        }
        return null
    }

    private fun mediaStoreDisplayName(uri: Uri, fallback: String): String =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                } else {
                    null
                }
            }
        }.getOrNull().takeUnless { it.isNullOrBlank() } ?: fallback

    @Suppress("DEPRECATION")
    private fun publishLegacy(archive: PendingLogArchive): PublicLogResult {
        if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return PublicLogResult(
                success = false,
                displayName = archive.displayName,
                error = "Public Downloads permission not granted",
                needsUserDestination = true
            )
        }
        val parent = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "RX400h Monitor"
        )
        if (!parent.exists() && !parent.mkdirs()) error("Cannot create ${parent.absolutePath}")
        val sourceHash = sha256(FileInputStream(archive.zipFile))
        val existing = parent.listFiles()?.firstOrNull { candidate ->
            candidate.isFile &&
                LogArchiveNaming.isSafeArchiveName(candidate.name) &&
                candidate.length() == archive.zipFile.length() &&
                runCatching { sha256(FileInputStream(candidate)) == sourceHash }.getOrDefault(false)
        }
        if (existing != null) {
            return PublicLogResult(
                success = true,
                displayName = existing.name,
                location = existing.absolutePath,
                uri = Uri.fromFile(existing)
            )
        }
        val target = LogArchiveNaming.uniqueFile(parent, archive.displayName)
        val temp = File(parent, ".${target.name}.tmp")
        if (temp.exists() && !temp.delete()) error("Cannot replace ${temp.absolutePath}")
        val copied = FileInputStream(archive.zipFile).use { input ->
            FileOutputStream(temp).use { output ->
                val count = input.copyTo(output)
                output.flush()
                output.fd.sync()
                count
            }
        }
        if (copied != archive.zipFile.length() || temp.length() != archive.zipFile.length()) {
            temp.delete()
            error("Public archive length mismatch")
        }
        if (sha256(FileInputStream(temp)) != sourceHash) {
            temp.delete()
            error("Public archive checksum mismatch")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Cannot promote public archive")
        }
        return PublicLogResult(
            success = true,
            displayName = target.name,
            location = target.absolutePath,
            uri = Uri.fromFile(target)
        )
    }

    private data class Fingerprint(val sizeBytes: Long, val sha256: String)

    private fun fingerprint(input: InputStream): Fingerprint = input.use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var sizeBytes = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) {
                break
            }
            sizeBytes += read
            digest.update(buffer, 0, read)
        }
        Fingerprint(
            sizeBytes = sizeBytes,
            sha256 = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        )
    }

    private fun sha256(input: InputStream): String = fingerprint(input).sha256
}
