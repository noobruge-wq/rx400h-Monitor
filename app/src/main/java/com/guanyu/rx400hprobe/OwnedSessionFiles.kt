package com.guanyu.rx400hprobe

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException

/** Deletes only one explicitly owned direct child; never follows links. */
internal object OwnedSessionFiles {
    fun deleteSession(root: File, session: File) {
        val rootPath = root.toPath().toRealPath()
        val target = session.toPath().toAbsolutePath().normalize()
        require(target.parent == rootPath && target.fileName.toString().startsWith("RX400h_"))
        require(!Files.isSymbolicLink(target) && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS))
        require(target.toRealPath() == target)
        Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                check(file.normalize().startsWith(target))
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                check(dir.normalize().startsWith(target))
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
