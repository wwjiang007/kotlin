/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.builder

import org.jetbrains.kotlin.fir.declarations.FirFile
import java.net.URI

interface AstToFirConverter<N> {
    fun convert(node: N, source: URI = URI("")): FirFile?
    fun convert(fileAST: SourceFileAST<N>): FirFile? = convert(fileAST.node, fileAST.source)
    fun convert(fileAsts: Collection<SourceFileAST<N>>): List<FirFile> = fileAsts.mapNotNull(this::convert)
}
