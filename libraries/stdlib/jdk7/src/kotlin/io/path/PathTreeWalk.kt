/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:JvmMultifileClass
@file:JvmName("PathsKt")

package kotlin.io.path

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributeView


/**
 * An enumeration to describe possible walk options.
 * The options can be combined to get the walk order and behavior needed.
 */
public enum class PathWalkOption {
    /** Depth-first search, directory is visited BEFORE its entries */
    INCLUDE_DIRECTORIES_BEFORE,
    /** Depth-first search, directory is visited AFTER its entries */
    INCLUDE_DIRECTORIES_AFTER,
    /** Breadth-first search, if combined with [INCLUDE_DIRECTORIES_BEFORE], directory and its siblings are visited BEFORE the directory entries */
    BFS,
    /** Symlinks are followed to the directories they point to */
    FOLLOW_LINKS
}

/**
 * This class is intended to implement different file traversal methods.
 * It allows to iterate through all files inside a given directory.
 * Iteration order of sibling files is unspecified.
 *
 * Use [Path.walk] extension function to instantiate a `PathTreeWalk` instance.
 *
 * If the file path given is just a file, walker iterates only it.
 * If the file path given does not exist, walker iterates nothing, i.e. it's equivalent to an empty sequence.
 */
private class PathTreeWalk(
    private val start: Path,
    private val options: Array<out PathWalkOption>
) : Sequence<Path> {

    init {
        val isBFS = options.contains(PathWalkOption.BFS)
        val isDirectoryAfter = options.contains(PathWalkOption.INCLUDE_DIRECTORIES_AFTER)
        require(!isBFS || !isDirectoryAfter) {
            "INCLUDE_DIRECTORIES_AFTER option is not applicable when BFS option is selected."
        }
    }

    private val linkOptions: Array<LinkOption>
        get() = LinkFollowing.toOptions(followLinks = options.contains(PathWalkOption.FOLLOW_LINKS))

    private val excludeDirectories: Boolean
        get() = !(options.contains(PathWalkOption.INCLUDE_DIRECTORIES_BEFORE) || options.contains(PathWalkOption.INCLUDE_DIRECTORIES_AFTER))

    private val isBFS: Boolean
        get() = options.contains(PathWalkOption.BFS)


    /** Returns an iterator walking through files. */
    override fun iterator(): Iterator<Path> = if (isBFS) BFSPathTreeWalkIterator() else DSFPathTreeWalkIterator()

    private inner class DSFPathTreeWalkIterator : AbstractIterator<Path>() {

        // Stack of directory states, beginning from the start directory
        private val state = ArrayList<WalkState>()

        init {
            when {
                start.isDirectory(*linkOptions) -> state.add(DirectoryState(start))
                start.exists(LinkOption.NOFOLLOW_LINKS) -> state.add(SingleFileState(start))
                else -> done()
            }
        }

        override fun computeNext() {
            val next = gotoNext()
            if (next != null)
                setNext(next)
            else
                done()
        }

        private tailrec fun gotoNext(): Path? {
            // Take next file from the top of the stack or return if there's nothing left
            val topState = state.lastOrNull() ?: return null
            val path = topState.step()
            if (path == null) {
                // There is nothing more on the top of the stack, go back
                state.removeLast()
                return gotoNext()
            } else {
                // Check that file/directory matches the filter
                if (path == topState.root || !path.isDirectory(*linkOptions)) {
                    // Proceed to a root directory or a simple file
                    return path
                } else {
                    // Proceed to a sub-directory
                    state.add(DirectoryState(path))
                    return gotoNext()
                }
            }
        }

        /** Visiting in bottom-up order */
        private inner class DirectoryState(rootDir: Path) : WalkState(rootDir) {

            private var visitRootBefore = options.contains(PathWalkOption.INCLUDE_DIRECTORIES_BEFORE)
            private var visitRootAfter = options.contains(PathWalkOption.INCLUDE_DIRECTORIES_AFTER)

            private var fileList: List<Path>? = null

            private var fileIndex = 0

            private var failed = false

            override fun step(): Path? {
                if (visitRootBefore) {
                    visitRootBefore = false
                    // visit the root dir before entries
                    return root
                }
                if (!failed && fileList == null) {
                    try {
                        // TODO: the path may have been deleted using deleteRecursively applied to the parent path
                        fileList = root.listDirectoryEntries()
                    } catch (e: IOException) { // NotDirectoryException is also an IOException
                        failed = true
                        throw e
                    }
                }
                if (fileList != null && fileIndex < fileList!!.size) {
                    // visit all entries
                    return fileList!![fileIndex++]
                }
                if (visitRootAfter) {
                    visitRootAfter = false
                    // visit the root dir after entries
                    return root
                }

                // That's all
                return null
            }
        }

        private inner class SingleFileState(rootFile: Path) : WalkState(rootFile) {
            private var visited: Boolean = false

            init {
                assert(rootFile.exists(LinkOption.NOFOLLOW_LINKS)) { "rootFile must exist." }
            }

            override fun step(): Path? {
                if (visited) return null
                visited = true
                return root
            }
        }

        /** Abstract class that encapsulates file visiting in some order, beginning from a given [root] */
        private abstract inner class WalkState(val root: Path) {
            /** Call of this function proceeds to a next file for visiting and returns it */
            public abstract fun step(): Path?
        }
    }

    private inner class BFSPathTreeWalkIterator : AbstractIterator<Path>() {
        // Queue of entries to be visited. Entries at current depth are divided from the next depth entries by a `null`.
        private val queue = ArrayDeque<Path?>()
        private var depth = 0

        init {
            queue.addLast(start)
            queue.addLast(null)
        }

        override fun computeNext() {
            val next = gotoNext()
            if (next != null)
                setNext(next)
            else
                done()
        }

        private tailrec fun gotoNext(): Path? {
            if (queue.isEmpty()) return null

            // TODO: the path may have been deleted using deleteRecursively applied to the parent path
            val path = queue.removeFirst()

            if (path == null) {
                if (queue.isNotEmpty()) {
                    // all entries in current depth were visited, seperated entries at the next depth from their children
                    queue.addLast(null)
                }
                depth += 1
                return gotoNext()
            }
            if (!path.isDirectory(*linkOptions)) {
                return path
            }
            val entries = path.listDirectoryEntries() // Don't catch IOExceptions
            queue.addAll(entries)
            return if (excludeDirectories) gotoNext() else path
        }
    }
}

/**
 * Gets a sequence for visiting this directory and all its content.
 *
 * By default only files are visited, in depth-first search order, and symbolic links are not followed.
 * The combination of [options] (see [PathWalkOption]) overrides the default behavior.
 */
public fun Path.walk(vararg options: PathWalkOption): Sequence<Path> = PathTreeWalk(this, options)

public fun Path.walk(visitor: PathTreeVisitor, followLinks: Boolean = false): Unit = visitor.walk(this, followLinks)


public class PathTreeVisitor private constructor(
    private val onFile: ((Path) -> Unit)?,
    private val onEnter: ((Path) -> Boolean)?,
    private val onLeave: ((Path) -> Unit)?,
    private val onFail: ((f: Path, e: IOException) -> Unit)?,
    private val maxDepth: Int = Int.MAX_VALUE
) {
    public constructor() : this(
        onFile = null,
        onEnter = null,
        onLeave = null,
        onFail = null
    )

    public fun onFile(function: (Path) -> Unit): PathTreeVisitor {
        return PathTreeVisitor(onFile = function, onEnter, onLeave, onFail, maxDepth)
    }

    public fun onEnterDirectory(function: (Path) -> Boolean): PathTreeVisitor {
        return PathTreeVisitor(onFile, onEnter = function, onLeave, onFail, maxDepth)
    }

    public fun onLeaveDirectory(function: (Path) -> Unit): PathTreeVisitor {
        return PathTreeVisitor(onFile, onEnter, onLeave = function, onFail, maxDepth)
    }

    public fun onFail(function: (Path, IOException) -> Unit): PathTreeVisitor {
        return PathTreeVisitor(onFile, onEnter, onLeave, onFail = function, maxDepth)
    }

    public fun maxDepth(depth: Int): PathTreeVisitor {
        if (depth <= 0)
            throw IllegalArgumentException("depth must be positive, but was $depth.")
        return PathTreeVisitor(onFile, onEnter, onLeave, onFail, maxDepth = depth)
    }

    internal fun walk(path: Path, followLinks: Boolean): Unit {
        val linkOptions = LinkFollowing.toOptions(followLinks)

        if (path.isDirectory(*linkOptions)) {
            if (onEnter?.invoke(path) == false) {
                return
            }
        } else {
            if (path.exists(LinkOption.NOFOLLOW_LINKS)) {
                onFile?.invoke(path)
            }
            return
        }

        try {
            // test start symlink to a directory
            // behavior not documented for symlinks
            Files.newDirectoryStream(path).use { directoryStream ->
                if (directoryStream is SecureDirectoryStream) {
                    directoryStream.walkEntries(linkOptions, 1)
                } else {
                    directoryStream.walkEntries(linkOptions, 1)
                }
            }
        } catch (e: IOException) {
            // should SecurityException be also caught and passed to onFail?
            onFail?.invoke(path, e)
        }

        onLeave?.invoke(path)

        /* catch (_: NotDirectoryException) {
            // test a file with limited read access
            // test a non-existent file
            // test a non-existent directory
            if (start.exists(LinkOption.NOFOLLOW_LINKS)) {
                onFile?.invoke(start)
            }
        }*/
    }

    // secure walk

    private fun SecureDirectoryStream<Path>.walkEntries(linkOptions: Array<LinkOption>, depth: Int) {
        for (entry in this) {
            if (isDirectory(entry, linkOptions)) {
                enterDirectory(entry, linkOptions, depth)
            } else {
                onFile?.invoke(entry)
            }
        }
    }

    private fun SecureDirectoryStream<Path>.enterDirectory(path: Path, linkOptions: Array<LinkOption>, depth: Int) {
        if (onEnter?.invoke(path) == false) {
            return
        }

        if (depth < maxDepth) {
            try {
                newDirectoryStream(path).use { it.walkEntries(linkOptions, depth + 1) }
            } catch (e: IOException) {
                // should SecurityException be also caught and passed to onFail?
                onFail?.invoke(path, e)
            }
        }

        onLeave?.invoke(path)
    }

    private fun SecureDirectoryStream<Path>.isDirectory(path: Path, linkOptions: Array<LinkOption>): Boolean {
        return getFileAttributeView(path, BasicFileAttributeView::class.java, *linkOptions).readAttributes().isDirectory
    }

    // insecure walk

    private fun DirectoryStream<Path>.walkEntries(linkOptions: Array<LinkOption>, depth: Int) {
        for (entry in this) {
            if (entry.isDirectory(*linkOptions)) {
                enterDirectory(entry, linkOptions, depth)
            } else {
                onFile?.invoke(entry)
            }
        }
    }

    private fun DirectoryStream<Path>.enterDirectory(path: Path, linkOptions: Array<LinkOption>, depth: Int) {
        if (onEnter?.invoke(path) == false) {
            return
        }

        if (depth < maxDepth) {
            try {
                Files.newDirectoryStream(path).use { it.walkEntries(linkOptions, depth + 1) }
            } catch (e: IOException) {
                // should SecurityException be also caught and passed to onFail?
                onFail?.invoke(path, e)
            }
        }

        onLeave?.invoke(path)
    }
}
