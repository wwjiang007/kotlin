/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.jdk7.test

import java.io.IOException
import java.nio.file.*
import kotlin.io.path.*
import kotlin.jdk7.test.PathTreeWalkTest.Companion.createTestFiles
import kotlin.jdk7.test.PathTreeWalkTest.Companion.referenceFilenames
import kotlin.jdk7.test.PathTreeWalkTest.Companion.testVisitedFiles
import kotlin.jdk7.test.PathTreeWalkTest.Companion.tryCreateSymbolicLinkTo
import kotlin.test.*

class CopyDeleteRecursivelyTest : AbstractPathTest() {

    @Test
    fun deleteFile() {
        val file = createTempFile()

        assertTrue(file.exists())
        file.deleteRecursively()
        assertFalse(file.exists())

        file.createFile().writeText("non-empty file")

        assertTrue(file.exists())
        file.deleteRecursively()
        assertFalse(file.exists())
    }

    @Test
    fun deleteDirectory() {
        val dir = createTestFiles()

        assertTrue(dir.exists())
        dir.deleteRecursively()
        assertFalse(dir.exists())
        dir.deleteRecursively() // successfully deletes recursively a non-existent directory
    }

    private fun Path.walkTopDown() = walk(PathWalkOption.INCLUDE_DIRECTORIES_BEFORE)

    @Test
    fun deleteRestrictedRead() {
        val basedir = createTestFiles().cleanupRecursively()
        val restricted1 = basedir.resolve("1").toFile()
        val restricted2 = basedir.resolve("7.txt").toFile()
        try {
            if (restricted1.setReadable(false) && restricted2.setReadable(false)) {
                assertFails("Expected incomplete recursive deletion.") { basedir.deleteRecursively() }
                assertTrue(restricted1.exists())
                assertFalse(restricted2.exists()) // restricted read allows removal of file

                restricted1.setReadable(true)
                testVisitedFiles(listOf("", "1", "1/2", "1/3", "1/3/4.txt", "1/3/5.txt"), basedir.walkTopDown(), basedir)
                basedir.deleteRecursively()
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
                basedir.deleteRecursively()
            }
        } finally {
            restricted.setWritable(true)
        }
    }

    @Test
    fun deleteBaseSymlinkToFile() {
        for (followLinks in listOf(true, false)) {
            val file = createTempFile().cleanup()
            val link = createTempDirectory().cleanupRecursively().resolve("link").tryCreateSymbolicLinkTo(file) ?: return

            link.deleteRecursively(followLinks)
            assertFalse(link.exists(LinkOption.NOFOLLOW_LINKS))
            assertTrue(file.exists())
        }
    }

    @Test
    fun deleteBaseSymlinkToDirectoryFollow() {
        val dir = createTestFiles().cleanupRecursively()
        val link = createTempDirectory().cleanupRecursively().resolve("link").tryCreateSymbolicLinkTo(dir) ?: return

        link.deleteRecursively(followLinks = true)
        assertFalse(link.exists(LinkOption.NOFOLLOW_LINKS))
        assertEquals(dir, dir.walkTopDown().single())
    }

    @Test
    fun deleteBaseSymlinkToDirectoryNoFollow() {
        val dir = createTestFiles().cleanupRecursively()
        val link = createTempDirectory().cleanupRecursively().resolve("link").tryCreateSymbolicLinkTo(dir) ?: return

        link.deleteRecursively(followLinks = false)
        assertFalse(link.exists(LinkOption.NOFOLLOW_LINKS))
        testVisitedFiles(listOf("") + referenceFilenames, dir.walkTopDown(), dir)
    }

    @Test
    fun deleteFollowSymlinks() {
        val dir1 = createTestFiles().cleanupRecursively()
        val dir2 = createTestFiles().cleanupRecursively().also { it.resolve("8/link").tryCreateSymbolicLinkTo(dir1) ?: return }

        dir2.deleteRecursively(followLinks = true)
        assertFalse(dir2.exists())
        assertEquals(dir1, dir1.walkTopDown().single())
    }

    @Test
    fun deleteNoFollowSymlinks() {
        val dir1 = createTestFiles().cleanupRecursively()
        val dir2 = createTestFiles().cleanupRecursively().also { it.resolve("8/link").tryCreateSymbolicLinkTo(dir1) ?: return }

        dir2.deleteRecursively(followLinks = false)
        assertFalse(dir2.exists())
        testVisitedFiles(listOf("") + referenceFilenames, dir1.walkTopDown(), dir1)
    }

    @Test
    fun deleteSymlinkToSymlink() {
        val dir = createTestFiles()
        val link = createTempDirectory().resolve("link").tryCreateSymbolicLinkTo(dir) ?: return
        val linkToLink = createTempDirectory().resolve("linkToLink").tryCreateSymbolicLinkTo(link) ?: return

        linkToLink.deleteRecursively(followLinks = true)
        assertFalse(linkToLink.exists(LinkOption.NOFOLLOW_LINKS))
        assertTrue(link.exists(LinkOption.NOFOLLOW_LINKS)) // the mediator symlink is not deleted
        assertEquals(dir, dir.walkTopDown().single())
    }

    @Test
    fun deleteSymlinkCyclic() {
        val basedir = createTestFiles().cleanupRecursively()
        val original = basedir.resolve("1")
        original.resolve("2/link").tryCreateSymbolicLinkTo(original) ?: return

//        assertFailsWith<java.nio.file.FileSystemLoopException> {
//            basedir.deleteRecursively(followLinks = true)
//        }
//        // partial delete may have taken place

        basedir.deleteRecursively(followLinks = false)
        assertFalse(basedir.exists())
    }

    @Test
    fun deleteSymlinkCyclicWithTwo() {
        val basedir = createTestFiles().cleanupRecursively()
        val dir8 = basedir.resolve("8")
        val dir2 = basedir.resolve("1/2")
        dir8.resolve("linkTo2").tryCreateSymbolicLinkTo(dir2) ?: return
        dir2.resolve("linkTo8").tryCreateSymbolicLinkTo(dir8) ?: return

//        assertFailsWith<java.nio.file.FileSystemLoopException> {
//            basedir.deleteRecursively(followLinks = true)
//        }
//        // partial delete may have taken place

        basedir.deleteRecursively(followLinks = false)
        assertFalse(basedir.exists())
    }

    @Test
    fun deleteSymlinkPointingToItself() {
        val basedir = createTempDirectory().cleanupRecursively()
        val link = basedir.resolve("link")
        link.tryCreateSymbolicLinkTo(link) ?: return

        basedir.deleteRecursively(followLinks = true)
        assertFalse(basedir.exists())
    }

    @Test
    fun deleteSymlinkTwoPointingToEachOther() {
        val basedir = createTempDirectory().cleanupRecursively()
        val link1 = basedir.resolve("link1")
        val link2 = basedir.resolve("link2").tryCreateSymbolicLinkTo(link1) ?: return
        link1.tryCreateSymbolicLinkTo(link2) ?: return

        basedir.deleteRecursively(followLinks = true)
        assertFalse(basedir.exists())
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

        src.copyRecursively(dst)
        compareFiles(src, dst)

        dst.writeText("bye")
        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            src.copyRecursively(dst)
        }
        assertEquals("bye", dst.readText())

//        src.copyRecursively(dst, overwrite = true)
//        compareFiles(src, dst)
    }

    @Test
    fun copyFileToDirectory() {
        val src = createTempFile().cleanup().also { it.writeText("hello") }
        val dst = createTestFiles().cleanupRecursively()

        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            src.copyRecursively(dst)
        }
        assertTrue(dst.isDirectory())

//        src.copyRecursively(dst, overwrite = true)
//        compareFiles(src, dst)
    }

    @Test
    fun copyDirectoryToDirectory() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        src.copyRecursively(dst)
        compareDirectories(src, dst)

        src.resolve("1/3/4.txt").writeText("hello")
        dst.resolve("10").createDirectory()
        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            src.copyRecursively(dst)
        }
        assertTrue(dst.resolve("1/3/4.txt").readText().isEmpty())

//        src.copyRecursively(dst, overwrite = true)
//        assertTrue(dst.resolve("10").exists())
//        compareDirectories(src, dst)
    }

    @Test
    fun copyDirectoryToFile() {
        val src = createTestFiles().cleanupRecursively()
        val dst = createTempFile().cleanupRecursively().also { it.writeText("hello") }

        assertFailsWith<java.nio.file.FileAlreadyExistsException> {
            src.copyRecursively(dst)
        }
        assertTrue(dst.isRegularFile())

//        src.copyRecursively(dst, overwrite = true)
//        compareDirectories(src, dst)
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

//        assertTrue(src.copyRecursively(dst) { _, _ -> OnErrorAction.SKIP })
//        assertFalse(src.copyRecursively(dst) { _, _ -> OnErrorAction.TERMINATE })
    }

    @Test
    fun copyNonExistentDestinationParent() {
        val src = createTempDirectory().cleanupRecursively()
        val dst = createTempDirectory().cleanupRecursively().resolve("parent/dst")

        assertFalse(dst.parent.exists())

        assertFailsWith<NoSuchFileException> { src.copyRecursively(dst) }
//        assertFailsWith<NoSuchFileException> { src.copyRecursively(dst) { _, _ -> OnErrorAction.SKIP } }
//        assertFailsWith<NoSuchFileException> { src.copyRecursively(dst) { _, _ -> OnErrorAction.TERMINATE } }

        assertFalse(dst.parent.exists())
    }
/*
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
*/
    @Test
    fun copyBaseSymlinkPointingToFileFollow() {
        val src = createTempFile().cleanup().also { it.writeText("hello") }
        val link = createTempDirectory().cleanupRecursively().resolve("link").tryCreateSymbolicLinkTo(src) ?: return
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        link.copyRecursively(dst, followLinks = true)
        compareFiles(src, dst)
    }

    @Test
    fun copyBaseSymlinkPointingToFileNoFollow() {
        val src = createTempFile().cleanup().also { it.writeText("hello") }
        val link = createTempDirectory().cleanupRecursively().resolve("link").tryCreateSymbolicLinkTo(src) ?: return
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        link.copyRecursively(dst, followLinks = false)
        compareFiles(link, dst)
    }

    @Test
    fun copyBaseSymlinkPointingToDirectoryFollow() {
        val src = createTestFiles().cleanupRecursively()
        val link = createTempDirectory().cleanupRecursively().resolve("link").tryCreateSymbolicLinkTo(src) ?: return
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        link.copyRecursively(dst, followLinks = true)
        compareDirectories(src, dst)
    }

    @Test
    fun copyBaseSymlinkPointingToDirectoryNoFollow() {
        val src = createTestFiles().cleanupRecursively()
        val link = createTempDirectory().cleanupRecursively().resolve("link").tryCreateSymbolicLinkTo(src) ?: return
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        link.copyRecursively(dst, followLinks = false)
        compareFiles(link, dst)
    }

    @Test
    fun copyFollowSymlinks() {
        val dir1 = createTestFiles().cleanupRecursively()
        val dir2 = createTestFiles().cleanupRecursively().also { it.resolve("8/link").tryCreateSymbolicLinkTo(dir1) ?: return }
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        dir2.copyRecursively(dst, followLinks = true)
        val dir2Content = listOf("", "8/link") + referenceFilenames
        val expectedDstContent = dir2Content + referenceFilenames.map { "8/link/$it" }
        testVisitedFiles(expectedDstContent, dst.walkTopDown(), dst)
    }

    @Test
    fun copyNoFollowSymlinks() {
        val dir1 = createTestFiles().cleanupRecursively()
        val dir2 = createTestFiles().cleanupRecursively().also { it.resolve("8/link").tryCreateSymbolicLinkTo(dir1) ?: return }
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        dir2.copyRecursively(dst, followLinks = false)
        testVisitedFiles(listOf("", "8/link") + referenceFilenames, dst.walkTopDown(), dst)
    }
/*
    @Test
    fun copyOverwriteFollowSymlinks() {
        val dir1 = createTestFiles().cleanupRecursively()
        val dir2 = createTestFiles().cleanupRecursively().also { it.resolve("8/link").tryCreateSymbolicLinkTo(dir1) ?: return }

        val dir3 = createTempDirectory().cleanupRecursively().also { it.resolve("file.txt").createFile() }
        val dst = createTempDirectory().cleanupRecursively().also { it.resolve("1").tryCreateSymbolicLinkTo(dir3) ?: return }

        assertTrue(dir2.copyRecursively(dst, overwrite = true, followLinks = true))

        // the dir pointed from dst is not deleted
        testVisitedFiles(listOf("", "file.txt"), dir3.walkTopDown(), dir3)

        // content of the directory pointed from src is copied
        val dir2Content = listOf("", "8/link") + referenceFilenames
        val expectedDstContent = dir2Content + referenceFilenames.map { "8/link/$it" }
        testVisitedFiles(expectedDstContent, dst.walkTopDown(), dst)

        // symlink from dst is overwritten
        assertFalse(dst.resolve("1").isSymbolicLink())
    }

    @Test
    fun copyOverwriteNoFollowSymlinks() {
        val dir1 = createTestFiles().cleanupRecursively()
        val dir2 = createTestFiles().cleanupRecursively().also { it.resolve("8/link").tryCreateSymbolicLinkTo(dir1) ?: return }

        val dir3 = createTempDirectory().cleanupRecursively().also { it.resolve("file.txt").createFile() }
        val dst = createTempDirectory().cleanupRecursively().also { it.resolve("7.txt").tryCreateSymbolicLinkTo(dir3) ?: return }

        assertTrue(dir2.copyRecursively(dst, overwrite = true, followLinks = false))

        // the dir pointed from dst is not deleted
        testVisitedFiles(listOf("", "file.txt"), dir3.walkTopDown(), dir3)

        // content of the directory pointed from src is not copied
        testVisitedFiles(listOf("", "8/link") + referenceFilenames, dst.walkTopDown(), dst)

        // symlink from dst is overwritten
        assertFalse(dst.resolve("7.txt").isSymbolicLink())
    }
*/
    @Test
    fun copySymlinkToSymlink() {
        val src = createTestFiles()
        val link = createTempDirectory().resolve("link").tryCreateSymbolicLinkTo(src) ?: return
        val linkToLink = createTempDirectory().resolve("linkToLink").tryCreateSymbolicLinkTo(link) ?: return
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        linkToLink.copyRecursively(dst, followLinks = true)
        testVisitedFiles(listOf("") + referenceFilenames, dst.walkTopDown(), dst)
    }

//    @Test
//    fun copySymlinkCyclic() {
//        val src = createTestFiles().cleanupRecursively()
//        val original = src.resolve("1")
//        original.resolve("2/link").tryCreateSymbolicLinkTo(original) ?: return
//        val dst = createTempDirectory().cleanupRecursively().resolve("dst")
//
//        assertFailsWith<java.nio.file.FileSystemLoopException> {
//            src.copyRecursively(dst, followLinks = true)
//        }
//        // partial copy
//        val pathToLink = listOf(dst.resolve("1"), dst.resolve("1/link"))
//        assertTrue(dst.walkTopDown().toList().containsAll(pathToLink))
//    }

//    @Test
//    fun copySymlinkCyclicWithTwo() {
//        val src = createTestFiles().cleanupRecursively()
//        val dir8 = src.resolve("8")
//        val dir2 = src.resolve("1/2")
//        dir8.resolve("linkTo2").tryCreateSymbolicLinkTo(dir2) ?: return
//        dir2.resolve("linkTo8").tryCreateSymbolicLinkTo(dir8) ?: return
//        val dst = createTempDirectory().cleanupRecursively().resolve("dst")
//
//        assertFailsWith<java.nio.file.FileSystemLoopException> {
//            src.copyRecursively(dst, followLinks = true)
//        }
//        // partial copy
//        assertTrue(dst.exists())
//    }

    @Test
    fun copySymlinkPointingToItself() {
        val src = createTempDirectory().cleanupRecursively()
        val link = src.resolve("link")
        link.tryCreateSymbolicLinkTo(link) ?: return
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        assertFailsWith<java.nio.file.FileSystemException> {
            // throws with message "Too many levels of symbolic links"
            src.copyRecursively(dst, followLinks = true)
        }
    }

    @Test
    fun copySymlinkTwoPointingToEachOther() {
        val src = createTempDirectory().cleanupRecursively()
        val link1 = src.resolve("link1")
        val link2 = src.resolve("link2").tryCreateSymbolicLinkTo(link1) ?: return
        link1.tryCreateSymbolicLinkTo(link2) ?: return
        val dst = createTempDirectory().cleanupRecursively().resolve("dst")

        assertFailsWith<java.nio.file.FileSystemException> {
            // throws with message "Too many levels of symbolic links"
            src.copyRecursively(dst, followLinks = true)
        }
    }
}
