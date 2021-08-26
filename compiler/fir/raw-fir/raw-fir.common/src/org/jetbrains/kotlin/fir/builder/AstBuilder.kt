/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import java.io.File
import java.net.URI
import java.nio.file.Path

class SourceFileAST<N>(val node: N, val source: URI)

interface AstBuilder<T> {
    fun buildAST(code: String): T
    fun buildFileAST(uri: URI): SourceFileAST<T> = buildFileAST(FileUtil.loadFile(File(uri), CharsetToolkit.UTF8, true), uri)
    fun buildFileAST(code: String, uri: URI = URI("")): SourceFileAST<T> = SourceFileAST(buildAST(code), uri)
}

fun <N> AstBuilder<N>.buildFileAST(file: File): SourceFileAST<N> = buildFileAST(file.toURI())
fun <N> AstBuilder<N>.buildFileAST(path: Path): SourceFileAST<N> = buildFileAST(path.toUri())
