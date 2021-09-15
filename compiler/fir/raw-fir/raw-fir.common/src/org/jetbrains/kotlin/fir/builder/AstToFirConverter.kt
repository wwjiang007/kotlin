/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.declarations.FirFile
import java.net.URI

class FirSyntaxError(
    val location: FirSourceElement,
    val message: String?
)

class FirFileWithSyntaxErrors(
    val firFile: FirFile,
    val syntaxErrors: Collection<FirSyntaxError>
)

fun interface AstToFirConverter<N> {
    fun convert(node: N, source: URI): FirFileWithSyntaxErrors
}

fun <N> AstToFirConverter<N>.convert(node: N): FirFileWithSyntaxErrors = convert(node, URI(""))

fun <N> AstToFirConverter<N>.convert(fileAST: SourceFileAST<N>): FirFileWithSyntaxErrors = convert(fileAST.node, fileAST.source)

fun <N> AstToFirConverter<N>.convert(fileAsts: Collection<SourceFileAST<N>>): List<FirFileWithSyntaxErrors> =
    fileAsts.map(this::convert)

fun <N> AstToFirConverter<N>.convertIgnoreErrors(node: N): FirFile = convert(node, URI("")).firFile

fun <N> AstToFirConverter<N>.convertIgnoreErrors(fileAST: SourceFileAST<N>): FirFile = convert(fileAST.node, fileAST.source).firFile

fun <N> AstToFirConverter<N>.convertIgnoreErrors(fileAsts: Collection<SourceFileAST<N>>): List<FirFile> =
    fileAsts.map { this.convert(it).firFile }
