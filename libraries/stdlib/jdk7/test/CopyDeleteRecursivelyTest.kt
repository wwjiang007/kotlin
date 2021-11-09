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
    fun deleteFile() {
        val file = createTempFile()

        assertTrue(file.exists())
        assertTrue(file.deleteRecursively())
        assertFalse(file.exists())

        file.createFile().writeText("non-empty file")

        assertTrue(file.exists())
        assertTrue(file.deleteRecursively())
        assertFalse(file.exists())
    }

    @Test
    fun deleteDirectory() {
        val dir = createTestFiles()

        assertTrue(dir.exists())
        assertTrue(dir.deleteRecursively())
        assertFalse(dir.exists())
        assertTrue(dir.deleteRecursively()) // successfully deletes recursively a non-existent directory
    }

    @Test
    fun deleteRestrictedRead() {
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
    fun deleteRestrictedWrite() {
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
    fun deleteFollowSymlinks() {
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
    fun deleteNoFollowSymlinks() {
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
    }

    @Test
    fun copyFileToFile() {
        val src = createTempFile().cleanup().also { it.writeText("hello") }
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        assertTrue(src.copyRecursively(dst))
        compareFiles(src, dst)

        dst.writeText("bye")
        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            src.copyRecursively(dst)
        }
        assertEquals("bye", dst.readText())

        assertTrue(src.copyRecursively(dst, overwrite = true))
        compareFiles(src, dst)
    }

    @Test
    fun copyFileToDirectory() {
        val src = createTempFile().cleanup().also { it.writeText("hello") }
        val dst = createTestFiles().cleanupRecursively()

        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            src.copyRecursively(dst)
        }
        assertTrue(dst.isDirectory())

        assertTrue(src.copyRecursively(dst, overwrite = true))
        compareFiles(src, dst)
    }

    @Test
    fun copyDirectoryToDirectory() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        assertTrue(src.copyRecursively(dst))
        compareDirectories(src, dst)

        src.resolve("1/3/4.txt").writeText("hello")
        src.resolve("8/10").createDirectory()
        dst.resolve("10").createDirectory()
        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            src.copyRecursively(dst)
        }
        assertTrue(dst.resolve("1/3/4.txt").readText().isEmpty())
        assertFalse(dst.resolve("8/10").exists())

        assertTrue(src.copyRecursively(dst, overwrite = true))
        assertTrue(dst.resolve("10").exists())
        compareDirectories(src, dst)
    }

    @Test
    fun copyDirectoryToFile() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTempFile().cleanup().also { it.writeText("hello") }

        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            src.copyRecursively(dst)
        }
        assertTrue(dst.isRegularFile())

        assertTrue(src.copyRecursively(dst, overwrite = true))
        compareDirectories(src, dst)
    }

    @Test
    fun copyNonExistentSource() {
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
    fun copyNonExistentDestinationParent() {
        val src = createTempDirectory().cleanupRecursively()
        val dst = createTempDirectory().cleanupRecursively().resolve("parent/dst")

        assertFalse(dst.parent.exists())

        assertFailsWith<NoSuchFileException> { src.copyRecursively(dst) }
        assertFailsWith<NoSuchFileException> { src.copyRecursively(dst) { _, _ -> OnErrorAction.SKIP } }
        assertFailsWith<NoSuchFileException> { src.copyRecursively(dst) { _, _ -> OnErrorAction.TERMINATE } }

        assertFalse(dst.parent.exists())
    }

    @Test
    fun copyWithConflicts() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTestFiles().cleanupRecursively()

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
    fun copyRestrictedRead() {
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
    fun copyRestrictedWrite() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTestFiles().cleanupRecursively()

        src.resolve("1/3/4.txt").writeText("hello")
        src.resolve("7.txt").writeText("hi")

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
}
