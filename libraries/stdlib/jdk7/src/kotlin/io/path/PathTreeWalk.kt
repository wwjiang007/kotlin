/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:JvmMultifileClass
@file:JvmName("PathsKt")

package kotlin.io.path

import java.io.IOException
import java.nio.file.LinkOption
import java.nio.file.Path


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
public class PathTreeWalk private constructor(
    private val start: Path,
    private val options: Array<out PathWalkOption>,
    private val onEnter: ((Path) -> Boolean)?,
    private val onLeave: ((Path) -> Unit)?,
    private val onFail: ((f: Path, e: IOException) -> Unit)?,
    private val maxDepth: Int = Int.MAX_VALUE
) : Sequence<Path> {

    internal constructor(start: Path, options: Array<out PathWalkOption>) : this(
        start,
        options,
        onEnter = null,
        onLeave = null,
        onFail = null
    )

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
                } else if (state.size >= maxDepth) {
                    return if (excludeDirectories) gotoNext() else path
                } else {
                    // Proceed to a sub-directory
                    state.add(DirectoryState(path))
                    return gotoNext()
                }
            }
        }

        /** Visiting in bottom-up order */
        private inner class DirectoryState(rootDir: Path) : WalkState(rootDir) {

            private var invokeOnEnter = true
            private var visitRootBefore = options.contains(PathWalkOption.INCLUDE_DIRECTORIES_BEFORE)
            private var visitRootAfter = options.contains(PathWalkOption.INCLUDE_DIRECTORIES_AFTER)

            private var fileList: List<Path>? = null

            private var fileIndex = 0

            private var failed = false

            override fun step(): Path? {
                if (invokeOnEnter) {
                    invokeOnEnter = false

                    if (onEnter?.invoke(root) == false) {
                        // skip this directory
                        return null
                    }
                }
                if (visitRootBefore) {
                    visitRootBefore = false
                    // visit the root dir before entries
                    return root
                }
                if (!failed && fileList == null) {
                    try {
                        fileList = root.listDirectoryEntries()
                    } catch (e: IOException) { // NotDirectoryException is also an IOException
                        onFail?.invoke(root, e)
                        failed = true
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
                onLeave?.invoke(root)
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
            if (onEnter?.invoke(path) == false) {
                // skip this directory
                return gotoNext()
            }
            if (depth < maxDepth) {
                var failed = false
                try {
                    val entries = path.listDirectoryEntries()
                    queue.addAll(entries)
                } catch (e: IOException) { // NotDirectoryException is also an IOException
                    onFail?.invoke(path, e)
                    failed = true
                }
                if (failed) return gotoNext()
            }
            return if (excludeDirectories) gotoNext() else path
        }
    }

    /**
     * Sets a predicate [function], that is called on any entered directory before its files are visited
     * and before it is visited itself.
     *
     * If the [function] returns `false` the directory is not entered and neither it nor its files are visited.
     *
     * When a directory is entered, all its files are retrieved eagerly.
     * Thus any changes in the directory won't affect the list of visited immediate children.
     */
    public fun onEnter(function: (Path) -> Boolean): PathTreeWalk {
        return PathTreeWalk(start, options, onEnter = function, onLeave, onFail, maxDepth)
    }

    /**
     * Sets a callback [function], that is called on any left directory after its files are visited and after it is visited itself.
     */
    public fun onLeave(function: (Path) -> Unit): PathTreeWalk {
        return PathTreeWalk(start, options, onEnter, onLeave = function, onFail, maxDepth)
    }

    /**
     * Set a callback [function], that is called on a directory when it's impossible to get its file list.
     *
     * The provided [function] is called after [onEnter] callback function,
     * and [onLeave] callback function is called after the specified [function] if the latter doesn't throw.
     */
    public fun onFail(function: (Path, IOException) -> Unit): PathTreeWalk {
        return PathTreeWalk(start, options, onEnter, onLeave, onFail = function, maxDepth)
    }

    /**
     * Sets the maximum [depth] of a directory tree to traverse. By default there is no limit.
     *
     * The value must be positive and [Int.MAX_VALUE] is used to specify an unlimited depth.
     *
     * With a value of 1, walker visits only the origin directory and all its immediate children,
     * with a value of 2 also grandchildren, etc.
     */
    public fun maxDepth(depth: Int): PathTreeWalk {
        if (depth <= 0)
            throw IllegalArgumentException("depth must be positive, but was $depth.")
        return PathTreeWalk(start, options, onEnter, onLeave, onFail, maxDepth = depth)
    }
}

/**
 * Gets a sequence for visiting this directory and all its content.
 *
 * By default only files are visited, in depth-first search order, and symbolic links are not followed.
 * The combination of [options] (see [PathWalkOption]) overrides the default behavior.
 */
public fun Path.walk(vararg options: PathWalkOption): PathTreeWalk =
    PathTreeWalk(this, options)
