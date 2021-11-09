/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.jdk7.test

import java.io.IOException
import java.nio.file.*
import kotlin.io.path.*
import kotlin.jdk7.test.PathTreeWalkTest.Companion.createTestFiles
import kotlin.jdk7.test.PathTreeWalkTest.Companion.testVisitedFiles
import kotlin.test.*

class CopyDeleteRecursivelyTest : AbstractPathTest() {
    @Test
    fun deleteRecursively() {
        val dir = createTempDirectory()
        val subDir = dir.resolve("subdir").createDirectory()
        dir.resolve("test1.txt").createFile()
        subDir.resolve("test2.txt").createFile()

        assertTrue(dir.deleteRecursively())
        assertFalse(dir.exists())
        assertFalse(subDir.exists())
        assertTrue(dir.deleteRecursively()) // possible to delete recursively a non-existing directory
    }

    @Test
    fun deleteRecursivelyRestrictedRead() {
        val basedir = createTestFiles().cleanupRecursively()
        val restricted1 = basedir.resolve("1").toFile()
        val restricted2 = basedir.resolve("7.txt").toFile()
        try {
            if (restricted1.setReadable(false) && restricted2.setReadable(false)) {
                assertFalse(basedir.deleteRecursively(), "Expected incomplete recursive deletion.")
                assertTrue(restricted1.exists())
                assertFalse(restricted2.exists()) // restricted read allows removal of file

                restricted1.setReadable(true)
                testVisitedFiles(listOf("", "1", "1/2", "1/3", "1/3/4.txt", "1/3/5.txt"), basedir.walkTopDown(), basedir)
                assertTrue(basedir.deleteRecursively())
            }
        } finally {
            restricted1.setReadable(true)
            restricted2.setReadable(true)
        }
    }

    @Test
    fun deleteRecursivelyRestrictedWrite() {
        val basedir = createTestFiles().cleanupRecursively()
        val restricted = basedir.resolve("1").toFile()
        try {
            if (restricted.setWritable(false)) {
                assertFailsWith<AccessDeniedException> {
                    basedir.deleteRecursively()
                }
                assertTrue(restricted.exists())

                restricted.setWritable(true)
                assertTrue(basedir.deleteRecursively())
            }
        } finally {
            restricted.setWritable(true)
        }
    }

    @Test
    fun deleteRecursivelyFollowSymlinks() {
        val dir1 = createTempDirectory()
        val dir2 = createTempDirectory().cleanup()
        val dir2File = dir2.resolve("file.txt").createFile()
        dir1.resolve("link").createSymbolicLinkPointingTo(dir2)

        assertTrue(dir1.deleteRecursively(followLinks = true))
        assertFalse(dir1.exists())
        assertTrue(dir2.exists())
        assertFalse(dir2File.exists())
    }

    @Test
    fun deleteRecursivelyNoFollowSymlinks() {
        val dir1 = createTempDirectory()
        val dir2 = createTempDirectory().cleanupRecursively()
        val dir2File = dir2.resolve("file.txt").createFile()
        dir1.resolve("link").createSymbolicLinkPointingTo(dir2)

        assertTrue(dir1.deleteRecursively(followLinks = false))
        assertFalse(dir1.exists())
        assertTrue(dir2.exists())
        assertTrue(dir2File.exists())
    }

    private fun compareFiles(src: Path, dst: Path, message: String? = null) {
        assertTrue(dst.exists())
        assertEquals(src.isRegularFile(), dst.isRegularFile(), message)
        assertEquals(src.isDirectory(), dst.isDirectory(), message)
        if (dst.isRegularFile()) {
            assertTrue(src.readBytes().contentEquals(dst.readBytes()), message)
        }
    }

    private fun compareDirectories(src: Path, dst: Path) {
        for (srcFile in src.walkTopDown()) {
            val dstFile = dst.resolve(srcFile.relativeTo(src))
            compareFiles(srcFile, dstFile)
        }
        for (dstFile in dst.walkTopDown()) {
            val srcFile = src.resolve(dstFile.relativeTo(dst))
            compareFiles(dstFile, srcFile)
        }
    }

    @Test
    fun copyRecursively() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTempDirectory().cleanupRecursively()

        src.resolve("1/3/4.txt").writeText("hello")
        src.resolve("7.txt").writeText("wazzup")

        assertTrue(src.copyRecursively(dst))
        compareDirectories(src, dst)

        assertFailsWith(FileAlreadyExistsException::class) {
            src.copyRecursively(dst)
        }

        dst.resolve("1/3/4.txt").writeText("hi")
        assertTrue(src.copyRecursively(dst, overwrite = true))
        compareDirectories(src, dst)
    }

    @Test
    fun copyRecursivelyNonExistentSource() {
        val src = createTempDirectory().also { it.deleteExisting() }
        val dst = createTempDirectory()

        assertFailsWith(NoSuchFileException::class) {
            src.copyRecursively(dst)
        }

        dst.deleteExisting()
        assertFailsWith(NoSuchFileException::class) {
            src.copyRecursively(dst)
        }

        assertTrue(src.copyRecursively(dst) { _, _ -> OnErrorAction.SKIP })
        assertFalse(src.copyRecursively(dst) { _, _ -> OnErrorAction.TERMINATE })
    }

    @Test
    fun copyRecursivelyNonExistentDestination() {
        val src = createTempDirectory().cleanupRecursively()
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        assertFalse(dst.exists())
        assertTrue(src.copyRecursively(dst))
        assertTrue(dst.exists())
    }

    @Test
    fun copyRecursivelyNonExistentDestinationParent() {
        val src = createTempDirectory().cleanupRecursively()
        val dst = createTempDirectory().cleanupRecursively().resolve("parent/dst")

        assertFalse(dst.parent.exists())

        assertFailsWith<NoSuchFileException> { src.copyRecursively(dst) }
        assertFailsWith<NoSuchFileException> { src.copyRecursively(dst) { _, _ -> OnErrorAction.SKIP } }
        assertFailsWith<NoSuchFileException> { src.copyRecursively(dst) { _, _ -> OnErrorAction.TERMINATE } }

        assertFalse(dst.parent.exists())
    }

    @Test
    fun copyRecursivelyWithConflicts() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTestFiles().cleanupRecursively()

        dst.resolve("8").deleteRecursively()
        assertFailsWith(java.nio.file.FileAlreadyExistsException::class) {
            src.copyRecursively(dst)
        }

        dst.resolve("8").deleteRecursively()
        val existingNames = hashSetOf<String>()
        assertTrue(src.copyRecursively(dst) { path: Path, e: IOException ->
            assertTrue(e is java.nio.file.FileAlreadyExistsException)
            existingNames.add(path.relativeToOrSelf(dst).invariantSeparatorsPathString)
            OnErrorAction.SKIP
        })
        assertEquals(setOf("1/3/4.txt", "1/3/5.txt", "7.txt"), existingNames)

        dst.resolve("8").deleteRecursively()
        assertFalse(src.copyRecursively(dst) { _, _ -> OnErrorAction.TERMINATE })
    }

    @Test
    fun copyRecursivelyRestrictedRead() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTempDirectory().cleanupRecursively()

        val restricted = src.resolve("1/3").toFile()
        if (restricted.setReadable(false)) {
            try {
                var caught = false
                assertTrue(src.copyRecursively(dst) { path: Path, e: IOException ->
                    assertTrue(e is java.nio.file.AccessDeniedException)
                    assertEquals(restricted, path.toFile())
                    caught = true
                    OnErrorAction.SKIP
                })
                assertTrue(caught)
                compareDirectories(src, dst)
            } finally {
                restricted.setReadable(true)
            }
        } else {
            System.err.println("cannot restrict access")
        }
    }

    @Test
    fun copyRecursivelyRestrictedWrite() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTestFiles().cleanupRecursively()

        src.resolve("1/3/4.txt").writeText("hello")
        src.resolve("7.txt").writeText("wazzup")

        val restricted = dst.resolve("1/3").toFile()
        if (restricted.setWritable(false)) {
            try {
                val accessDeniedNames = hashSetOf<String>()
                assertTrue(src.copyRecursively(dst, overwrite = true) { path: Path, e: IOException ->
                    assertTrue(path.invariantSeparatorsPathString.startsWith(restricted.invariantSeparatorsPath), path.toString())
                    assertTrue(e is java.nio.file.AccessDeniedException)
                    assertTrue(
                        accessDeniedNames.add(path.relativeToOrSelf(dst).invariantSeparatorsPathString)
                    )
                    OnErrorAction.SKIP
                })

                assertEquals(setOf("1/3/4.txt", "1/3/5.txt"), accessDeniedNames)
                assertNotEquals(src.resolve("1/3/4.txt").readText(), dst.resolve("1/3/4.txt").readText())
            } finally {
                restricted.setWritable(true)
            }

            src.resolve("1/3").deleteRecursively()
            dst.resolve("1/3").deleteRecursively()
            compareDirectories(src, dst)
        } else {
            System.err.println("cannot restrict access")
        }
    }

    @Test
    fun copyRecursivelyWithOverwrite() {
        val src = createTempDirectory().cleanupRecursively()
        val dst = createTempDirectory().cleanupRecursively()

        val srcFile = src.resolve("test")
        val dstFile = dst.resolve("test")
        srcFile.writeText("text1")

        src.copyRecursively(dst)

        srcFile.writeText("text1 modified")
        src.copyRecursively(dst, overwrite = true)
        compareDirectories(src, dst)

        dstFile.deleteExisting()
        dstFile.createDirectory()
        dstFile.resolve("subFile").writeText("subfile")
        src.copyRecursively(dst, overwrite = true)
        compareDirectories(src, dst)

        srcFile.deleteExisting()
        srcFile.createDirectory()
        srcFile.resolve("subFile").writeText("text2")
        src.copyRecursively(dst, overwrite = true)
        compareDirectories(src, dst)
    }
}
