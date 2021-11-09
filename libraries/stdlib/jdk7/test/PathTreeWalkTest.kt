/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.jdk7.test

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class PathTreeWalkTest : AbstractPathTest() {

    companion object {
        val referenceFilenames = listOf("1", "1/2", "1/3", "1/3/4.txt", "1/3/5.txt", "6", "7.txt", "8", "8/9.txt")

        fun createTestFiles(): Path {
            val basedir = createTempDirectory()
            for (name in referenceFilenames) {
                val file = basedir.resolve(name)
                if (file.extension.isEmpty())
                    file.createDirectories()
                else
                    file.createFile()
            }
            return basedir
        }

        fun testVisitedFiles(expected: List<String>, walk: Sequence<Path>, basedir: Path) {
            val actual = walk.map { it.relativeToOrSelf(basedir).invariantSeparatorsPathString }
            assertEquals(expected.sorted(), actual.toList().sorted())
        }
    }

    @Test
    fun visitOnce() {
        val basedir = createTestFiles().cleanupRecursively()
        val expectedNames = listOf("") + referenceFilenames
        testVisitedFiles(expectedNames, basedir.walkTopDown(), basedir)
        testVisitedFiles(expectedNames, basedir.walkBottomUp(), basedir)
    }

    @Test
    fun singleFile() {
        val testFile = createTempFile().cleanup()
        val nonExistentFile = testFile.resolve("foo")

        assertEquals(testFile, testFile.walkTopDown().single(), "walk sequence of an existing file should contain only that file")
        assertEquals(testFile, testFile.walkTopDown().onEnter { false }.single(), "enter should not be called for single file")
        assertTrue(nonExistentFile.walkTopDown().none(), "should not walk a non-existent file")

        assertEquals(testFile, testFile.walkBottomUp().single(), "walk sequence of an existing file should contain only that file")
        assertEquals(testFile, testFile.walkBottomUp().onEnter { false }.single(), "enter should not be called for single file")
        assertTrue(nonExistentFile.walkBottomUp().none(), "should not walk a non-existent file")
    }

    @Test
    fun singleEmptyDirectory() {
        val testDir = createTempDirectory().cleanup()

        assertEquals(testDir, testDir.walkTopDown().single(), "walk sequence of an empty directory should contain only that directory")
        assertEquals(testDir, testDir.walkTopDown().onEnter { true }.single(), "onEnter should be called for an empty directory")
        assertTrue(testDir.walkTopDown().onEnter { false }.none(), "onEnter should be called for an empty directory")

        assertEquals(testDir, testDir.walkBottomUp().single(), "walk sequence of an empty directory should contain only that directory")
        assertEquals(testDir, testDir.walkBottomUp().onEnter { true }.single(), "onEnter should be called for an empty directory")
        assertTrue(testDir.walkBottomUp().onEnter { false }.none(), "onEnter should be called for an empty directory")
    }

    @Test
    fun enterLeaveVisitOrder() {
        val basedir = createTestFiles().cleanupRecursively()

        val namesWalkEnter = HashSet<String>()
        val namesWalkLeave = HashSet<String>()
        val namesWalkVisit = HashSet<String>()
        var direction = FileWalkDirection.TOP_DOWN

        fun enter(file: Path): Boolean {
            val name = file.relativeToOrSelf(basedir).invariantSeparatorsPathString
            assertTrue(file.isDirectory(), "$name is not directory, only directories should be entered")
            assertFalse(namesWalkLeave.contains(name), "$name is left before entrance")
            if (file.name == "3") return false // filter out 3
            assertTrue(namesWalkEnter.add(name), "$name is entered twice")
            return true
        }

        fun leave(file: Path) {
            val name = file.relativeToOrSelf(basedir).invariantSeparatorsPathString
            assertTrue(file.isDirectory(), "$name is not directory, only directories should be left")
            assertTrue(namesWalkLeave.add(name), "$name is left twice")
            assertTrue(namesWalkEnter.contains(name), "$name is left before entrance")
        }

        fun visit(file: Path) {
            val name = file.relativeToOrSelf(basedir).invariantSeparatorsPathString
            assertTrue(namesWalkVisit.add(name), "$name is visited twice")
            if (file.isDirectory()) {
                assertTrue(namesWalkEnter.contains(name), "$name is visited before entrance")
                assertFalse(namesWalkLeave.contains(name), "$name is visited after leaving")
            }

            if (file == basedir)
                return
            val parent = file.parent
            if (parent != null) {
                val parentName = parent.relativeToOrSelf(basedir).invariantSeparatorsPathString
                assertTrue(namesWalkEnter.contains(parentName), "$name is visited before entering its parent $parentName")
                assertFalse(namesWalkLeave.contains(parentName), "$name is visited after leaving its parent $parentName")

                when (direction) {
                    FileWalkDirection.TOP_DOWN ->
                        assertTrue(namesWalkVisit.contains(parentName), "$direction: $name is visited before its parent $parentName")
                    FileWalkDirection.BOTTOM_UP ->
                        assertFalse(namesWalkVisit.contains(parentName), "$direction: $parentName is visited before its child $name")
                }
            }
        }

        val expectedEnterNames = setOf("", "1", "1/2", "6", "8")
        val expectedVisitNames = setOf("", "1", "1/2", "6", "7.txt", "8", "8/9.txt")

        for (file in basedir.walkTopDown().onEnter(::enter).onLeave(::leave)) {
            visit(file)
        }
        assertEquals(expectedEnterNames, namesWalkEnter)
        assertEquals(expectedEnterNames, namesWalkLeave)
        assertEquals(expectedVisitNames, namesWalkVisit)

        namesWalkEnter.clear()
        namesWalkLeave.clear()
        namesWalkVisit.clear()
        direction = FileWalkDirection.BOTTOM_UP
        for (file in basedir.walkBottomUp().onEnter(::enter).onLeave(::leave)) {
            visit(file)
        }
        assertEquals(expectedEnterNames, namesWalkEnter)
        assertEquals(expectedEnterNames, namesWalkLeave)
        assertEquals(expectedVisitNames, namesWalkVisit)
    }

    @Test
    fun filterAndMap() {
        val basedir = createTestFiles().cleanupRecursively()
        val expectedNames = setOf("", "1", "1/2", "1/3", "6", "8")
        assertEquals(expectedNames, basedir.walkTopDown().filter { it.isDirectory() }.map {
            it.relativeToOrSelf(basedir).invariantSeparatorsPathString
        }.toHashSet())
    }

    @Test
    fun deleteChildrenOnEnter() {
        val expectedNames = listOf("", "1", "1/2", "1/3", "6", "8")

        fun enter(file: Path): Boolean {
            assertTrue(file.isDirectory())
            for (child in file.listDirectoryEntries()) {
                if (child.name.endsWith("txt"))
                    child.deleteExisting()
            }
            return true
        }

        val basedir1 = createTestFiles().cleanupRecursively()
        testVisitedFiles(expectedNames, basedir1.walkTopDown().onEnter(::enter), basedir1)
        val basedir2 = createTestFiles().cleanupRecursively()
        testVisitedFiles(expectedNames, basedir2.walkBottomUp().onEnter(::enter), basedir2)
    }

    @Test
    fun deleteChildrenOnVisitTopDown() {
        val basedir = createTestFiles().cleanupRecursively()
        val expected = listOf("", "1", "6", "7.txt", "8", "8/9.txt")
        val walk = basedir.walkTopDown().onEach { if (it.name == "1") it.deleteRecursively() }
        testVisitedFiles(expected, walk, basedir)
    }

    @Test
    fun deleteChildrenOnVisitBottomUp() {
        val basedir = createTestFiles().cleanupRecursively()
        val expected = listOf("") + referenceFilenames
        val walk = basedir.walkBottomUp().onEach { if (it.name == "1") it.deleteRecursively() }
        testVisitedFiles(expected, walk, basedir)
    }


    @Test
    fun addChildOnEnter() {
        val expectedNames = referenceFilenames + listOf("", "a.txt", "1/a.txt", "1/2/a.txt", "1/3/a.txt", "6/a.txt", "8/a.txt")

        fun enter(file: Path): Boolean {
            assertTrue(file.isDirectory())
            file.resolve("a.txt").createFile()
            return true
        }

        val basedir1 = createTestFiles().cleanupRecursively()
        testVisitedFiles(expectedNames, basedir1.walkTopDown().onEnter(::enter), basedir1)
        val basedir2 = createTestFiles().cleanupRecursively()
        testVisitedFiles(expectedNames, basedir2.walkBottomUp().onEnter(::enter), basedir2)
    }

    @Test
    fun addChildOnVisitTopDown() {
        val basedir = createTestFiles().cleanupRecursively()
        val expected = listOf("", "1/10.txt") + referenceFilenames
        val walk = basedir.walkTopDown().onEach { if (it.name == "1") it.resolve("10.txt").createFile() }
        testVisitedFiles(expected, walk, basedir)
    }

    @Test
    fun addChildOnVisitBottomUp() {
        val basedir = createTestFiles().cleanupRecursively()
        val expected = listOf("") + referenceFilenames
        val walk = basedir.walkBottomUp().onEach { if (it.name == "1") it.resolve("10.txt").createFile() }
        testVisitedFiles(expected, walk, basedir)
    }

    @Test
    fun filterOutDirectoryOnEnter() {
        val basedir = createTestFiles().cleanupRecursively()

        fun filter(path: Path) = path.name != "3"

        val expectedNames = listOf("", "1", "1/2", "6", "7.txt", "8", "8/9.txt")
        testVisitedFiles(expectedNames, basedir.walkTopDown().onEnter(::filter), basedir)
        testVisitedFiles(expectedNames, basedir.walkBottomUp().onEnter(::filter), basedir)

        assertEquals(emptyList(), basedir.walkTopDown().onEnter { false }.toList())
        assertEquals(emptyList(), basedir.walkBottomUp().onEnter { false }.toList())
    }

    @Test
    fun forEach() {
        val basedir = createTestFiles().cleanupRecursively()
        var i = 0
        basedir.walkTopDown().forEach { _ -> i++ }
        assertEquals(10, i);
        i = 0
        basedir.walkBottomUp().forEach { _ -> i++ }
        assertEquals(10, i);
    }

    @Test
    fun count() {
        val basedir = createTestFiles().cleanupRecursively()
        assertEquals(10, basedir.walkTopDown().count())
        assertEquals(10, basedir.walkBottomUp().count())
    }

    @Test
    fun reduce() {
        val basedir = createTestFiles().cleanupRecursively()
        val res = basedir.walkTopDown().reduce { a, b -> if (a.absolutePathString() > b.absolutePathString()) a else b }
        assertTrue(res.endsWith("9.txt"), "Expected end with 9.txt actual: ${res.name}")
    }

    @Test
    fun maxDepthAndOnFail() {
        val basedir = createTestFiles().cleanupRecursively()

        val visited = HashSet<String>()
        val dirs = HashSet<String>()
        val failed = HashSet<String>()
        val stack = ArrayList<Path>()

        fun enter(dir: Path): Boolean {
            stack.add(dir)
            assertTrue(
                dirs.add(dir.relativeToOrSelf(basedir).invariantSeparatorsPathString)
            )
            return true
        }

        fun leave(dir: Path) {
            assertEquals(stack.last(), dir)
            stack.removeAt(stack.lastIndex)
        }

        fun visit(file: Path) {
            if (file != stack.last()) {
                assertTrue(stack.last().listDirectoryEntries().contains(file), file.toString())
            }
            assertTrue(
                visited.add(file.relativeToOrSelf(basedir).invariantSeparatorsPathString)
            )
        }

        fun fail(dir: Path, @Suppress("UNUSED_PARAMETER") e: IOException) {
            assertEquals(stack.last(), dir)
            //stack.removeAt(stack.lastIndex) - don't remove from stack, onLeave will be called
            assertTrue(
                failed.add(dir.relativeToOrSelf(basedir).invariantSeparatorsPathString)
            )
        }

        for (direction in PathWalkDirection.values()) {
            fun clear() {
                listOf(visited, dirs, failed, stack).forEach(MutableCollection<*>::clear)
            }

            fun test(expectedStack: List<Path>, expectedFailed: Set<String>, expectedDirs: Set<String>, expectedVisited: Set<String>) {
                assertEquals(expectedStack, stack, direction.toString())
                assertEquals(expectedFailed, failed, direction.toString())
                assertEquals(expectedDirs, dirs, direction.toString())
                assertTrue(visited.containsAll(dirs), direction.toString())
                assertEquals(expectedVisited, visited - dirs, direction.toString())
            }

            val walk = basedir.walk(direction).onEnter(::enter).onLeave(::leave).onFail(::fail)

            clear()
            walk.forEach { visit(it) }
            test(
                expectedStack = emptyList(),
                expectedFailed = emptySet(),
                expectedDirs = setOf("", "1", "1/2", "1/3", "6", "8"),
                expectedVisited = setOf("1/3/4.txt", "1/3/5.txt", "7.txt", "8/9.txt")
            )

            //limit maxDepth
            clear()
            walk.maxDepth(1).forEach { visit(it) }
            test(
                expectedStack = emptyList(),
                expectedFailed = emptySet(),
                expectedDirs = setOf(""),
                expectedVisited = setOf("1", "6", "7.txt", "8")
            )

            //restrict access
            val restricted = basedir.resolve("1").toFile()
            if (restricted.setReadable(false)) {
                try {
                    clear()
                    walk.forEach { visit(it) }
                    test(
                        expectedStack = emptyList(),
                        expectedFailed = setOf("1"),
                        expectedDirs = setOf("", "1", "6", "8"),
                        expectedVisited = setOf("7.txt", "8/9.txt")
                    )
                } finally {
                    restricted.setReadable(true)
                }
            } else {
                System.err.println("cannot restrict access")
            }
        }
    }

    @Test
    fun backup() {
        var count = 0
        fun makeBackup(file: Path) {
            count++
            val bakFile = Path("$file.bak")
            file.copyTo(bakFile)
        }

        val basedir1 = createTestFiles().cleanupRecursively()
        basedir1.walkTopDown().forEach {
            if (it.isRegularFile()) {
                makeBackup(it)
            }
        }
        assertEquals(4, count)

        count = 0
        val basedir2 = createTestFiles().cleanupRecursively()
        basedir2.walkTopDown().forEach {
            if (it.isRegularFile()) {
                makeBackup(it)
            }
        }
        assertEquals(4, count)
    }

    @Test
    fun find() {
        val basedir = createTestFiles().cleanupRecursively()
        basedir.resolve("8/4.txt").createFile()
        val count = basedir.walkTopDown().count { it.name == "4.txt" }
        assertEquals(2, count)
    }

    @Test
    fun findGits() {
        val basedir = createTestFiles().cleanupRecursively()
        basedir.resolve("1/3/.git").createDirectory()
        basedir.resolve("1/2/.git").createDirectory()
        basedir.resolve("6/.git").createDirectory()
        val found = HashSet<String>()
        for (file in basedir.walkTopDown()) {
            if (file.name == ".git") {
                found.add(file.parent.relativeToOrSelf(basedir).invariantSeparatorsPathString)
            }
        }
        assertEquals(setOf("1/2", "1/3", "6"), found)
    }

    @Test
    fun links() {
        val basedir = createTestFiles().cleanupRecursively()
        val original = basedir.resolve("8/9.txt")
        try {
            basedir.resolve("1/3/link").createLinkPointingTo(original)
        } catch (e: Exception) {
            // the underlying OS may not support hard links or may require a privilege
            println("Creating a link failed with ${e.stackTraceToString()}")
            return
        }
        for (direction in PathWalkDirection.values()) {
            for (followLinks in listOf(true, false)) {
                val walk = basedir.walk(direction, followLinks)
                testVisitedFiles(referenceFilenames + listOf("", "1/3/link"), walk, basedir)
            }
        }
    }

    private fun Path.tryCreateSymbolicLinkTo(original: Path): Path? {
        return try {
            this.createSymbolicLinkPointingTo(original)
        } catch (e: Exception) {
            // the underlying OS may not support hard links or may require a privilege
            println("Creating a link failed with ${e.stackTraceToString()}")
            null
        }
    }

    @Test
    fun symlinkToFile() {
        val basedir = createTestFiles().cleanupRecursively()
        val original = basedir.resolve("8/9.txt")
        basedir.resolve("1/3/link").tryCreateSymbolicLinkTo(original) ?: return

        for (direction in PathWalkDirection.values()) {
            for (followLinks in listOf(true, false)) {
                val walk = basedir.walk(direction, followLinks)
                testVisitedFiles(referenceFilenames + listOf("", "1/3/link"), walk, basedir)
            }
        }

        original.deleteExisting()
        for (direction in PathWalkDirection.values()) {
            for (followLinks in listOf(true, false)) {
                val walk = basedir.walk(direction, followLinks)
                testVisitedFiles(referenceFilenames - listOf("8/9.txt") + listOf("", "1/3/link"), walk, basedir)
            }
        }
    }

    @Test
    fun symlinkToDirectory() {
        val basedir = createTestFiles().cleanupRecursively()
        val original = basedir.resolve("8")
        basedir.resolve("1/3/link").tryCreateSymbolicLinkTo(original) ?: return

        for (direction in PathWalkDirection.values()) {
            // follow links
            val walk = basedir.walk(direction, followLinks = true)
            // directory "8" contains "9.txt" file
            testVisitedFiles(referenceFilenames + listOf("", "1/3/link", "1/3/link/9.txt"), walk, basedir)

            // don't follow links
            val nofollowWalk = basedir.walk(direction, followLinks = false)
            testVisitedFiles(referenceFilenames + listOf("", "1/3/link"), nofollowWalk, basedir)
        }

        assertTrue(original.deleteRecursively())
        for (direction in PathWalkDirection.values()) {
            for (followLinks in listOf(true, false)) {
                val walk = basedir.walk(direction, followLinks)
                testVisitedFiles(referenceFilenames - listOf("8", "8/9.txt") + listOf("", "1/3/link"), walk, basedir)
            }
        }
    }

    @Test
    fun symlinkTwoPointingToEachOther() {
        val basedir = createTempDirectory().cleanupRecursively()
        val link1 = basedir.resolve("link1")
        val link2 = basedir.resolve("link2").createSymbolicLinkPointingTo(link1)
        link1.createSymbolicLinkPointingTo(link2)

        for (direction in PathWalkDirection.values()) {
            val walk = basedir.walk(direction, followLinks = true)

            testVisitedFiles(listOf("", "link1", "link2"), walk, basedir)
        }
    }

    @Test
    fun symlinkPointingToItself() {
        val basedir = createTempDirectory().cleanupRecursively()
        val link = basedir.resolve("link")
        link.createSymbolicLinkPointingTo(link)

        for (direction in PathWalkDirection.values()) {
            val walk = basedir.walk(direction, followLinks = true)

            testVisitedFiles(listOf("", "link"), walk, basedir)
        }
    }

    @Test
    fun symlinkToDirectoryMaxDepth() {
        val basedir = createTestFiles().cleanupRecursively()
        val original = basedir.resolve("8")
        original.resolve("10").createDirectory().resolve("11.txt").createFile()
        basedir.resolve("1/3/link").tryCreateSymbolicLinkTo(original) ?: return

        for (direction in PathWalkDirection.values()) {
            val walk = basedir.walk(direction, followLinks = true)

            val depth2ExpectedNames = listOf("", "1", "1/2", "1/3", "6", "7.txt", "8", "8/9.txt", "8/10") // link is not visited
            testVisitedFiles(depth2ExpectedNames, walk.maxDepth(2), basedir)

            val depth3ExpectedNames = depth2ExpectedNames +
                    listOf("1/3/4.txt", "1/3/5.txt", "8/10/11.txt", "1/3/link") // link is visited, but not followed
            testVisitedFiles(depth3ExpectedNames, walk.maxDepth(3), basedir)

            val depth4ExpectedNames = depth3ExpectedNames +
                    listOf("1/3/link/9.txt", "1/3/link/10") // visited once more through the symbolic link
            testVisitedFiles(depth4ExpectedNames, walk.maxDepth(4), basedir)

            val depth5ExpectedNames = depth4ExpectedNames +
                    listOf("1/3/link/10/11.txt")
            testVisitedFiles(depth5ExpectedNames, walk.maxDepth(5), basedir)
            testVisitedFiles(depth5ExpectedNames, walk, basedir) // no depth limit
        }
    }

    @Test
    fun symlinkToSymlink() {
        val basedir = createTestFiles().cleanupRecursively()
        val original = basedir.resolve("8")
        val link = basedir.resolve("1/3/link").tryCreateSymbolicLinkTo(original) ?: return
        basedir.resolve("1/linkToLink").tryCreateSymbolicLinkTo(link) ?: return

        for (direction in PathWalkDirection.values()) {
            val walk = basedir.walk(direction, followLinks = true)

            val depth2ExpectedNames = listOf("", "1", "1/2", "1/3", "1/linkToLink", "6", "7.txt", "8", "8/9.txt") // linkToLink is visited
            testVisitedFiles(depth2ExpectedNames, walk.maxDepth(2), basedir)

            val depth3ExpectedNames = depth2ExpectedNames +
                    listOf("1/3/4.txt", "1/3/5.txt", "1/3/link", "1/linkToLink/9.txt") // "9.txt" is visited once more through linkToLink
            testVisitedFiles(depth3ExpectedNames, walk.maxDepth(3), basedir)

            val depth4ExpectedNames = depth3ExpectedNames +
                    listOf("1/3/link/9.txt") // "9.txt" is visited once more through link
            testVisitedFiles(depth4ExpectedNames, walk.maxDepth(4), basedir)
            testVisitedFiles(depth4ExpectedNames, walk, basedir) // no depth limit
        }
    }

    @Test
    fun symlinkBasedir() {
        val basedir = createTestFiles().cleanupRecursively()
        val link = createTempDirectory().cleanupRecursively().resolve("link").tryCreateSymbolicLinkTo(basedir) ?: return

        for (direction in PathWalkDirection.values()) {
            val walk = link.walk(direction, followLinks = true)
            testVisitedFiles(referenceFilenames + listOf(""), walk, link)

            val nofollowWalk = link.walk(direction, followLinks = false)
            assertEquals(link, nofollowWalk.single())
        }

        assertTrue(basedir.deleteRecursively())
        for (direction in PathWalkDirection.values()) {
            val walk = link.walk(direction, followLinks = true)
            assertEquals(link, walk.single())

            val nofollowWalk = link.walk(direction, followLinks = false)
            assertEquals(link, nofollowWalk.single())
        }
    }

    @Test
    fun symlinkCyclic() {
        val basedir = createTestFiles().cleanupRecursively()
        val original = basedir.resolve("1")
        original.resolve("2/link").tryCreateSymbolicLinkTo(original) ?: return

        for (direction in PathWalkDirection.values()) {
            val walk = basedir.walk(direction, followLinks = true)

            val depth3ExpectedNames = referenceFilenames + listOf("", "1/2/link")
            testVisitedFiles(depth3ExpectedNames, walk.maxDepth(3), basedir)

            val depth4ExpectedNames = depth3ExpectedNames + listOf("1/2/link/2", "1/2/link/3")
            testVisitedFiles(depth4ExpectedNames, walk.maxDepth(4), basedir)

            val depth5ExpectedNames = depth4ExpectedNames + listOf("1/2/link/2/link", "1/2/link/3/4.txt", "1/2/link/3/5.txt")
            testVisitedFiles(depth5ExpectedNames, walk.maxDepth(5), basedir)

            val depth6ExpectedNames = depth5ExpectedNames + listOf("1/2/link/2/link/2", "1/2/link/2/link/3")
            testVisitedFiles(depth6ExpectedNames, walk.maxDepth(6), basedir)
        }
    }

    @Test
    fun symlinkCyclicWithTwo() {
        val basedir = createTestFiles().cleanupRecursively()
        val link1Parent = basedir.resolve("8")
        val link2Parent = basedir.resolve("1/2")
        link1Parent.resolve("linkTo2").tryCreateSymbolicLinkTo(link2Parent) ?: return
        link2Parent.resolve("linkTo8").tryCreateSymbolicLinkTo(link1Parent) ?: return

        for (direction in PathWalkDirection.values()) {
            val walk = basedir.walk(direction, followLinks = true)

            val depth2ExpectedNames = listOf("", "1", "1/2", "1/3", "6", "7.txt", "8", "8/9.txt", "8/linkTo2")
            testVisitedFiles(depth2ExpectedNames, walk.maxDepth(2), basedir)

            val depth3ExpectedNames = depth2ExpectedNames + listOf("1/2/linkTo8", "1/3/4.txt", "1/3/5.txt", "8/linkTo2/linkTo8")
            testVisitedFiles(depth3ExpectedNames, walk.maxDepth(3), basedir)

            val depth4ExpectedNames = depth3ExpectedNames +
                    listOf("1/2/linkTo8/9.txt", "1/2/linkTo8/linkTo2", "8/linkTo2/linkTo8/9.txt", "8/linkTo2/linkTo8/linkTo2")
            testVisitedFiles(depth4ExpectedNames, walk.maxDepth(4), basedir)

            val depth5ExpectedNames = depth4ExpectedNames + listOf("1/2/linkTo8/linkTo2/linkTo8", "8/linkTo2/linkTo8/linkTo2/linkTo8")
            testVisitedFiles(depth5ExpectedNames, walk.maxDepth(5), basedir)
        }
    }
}