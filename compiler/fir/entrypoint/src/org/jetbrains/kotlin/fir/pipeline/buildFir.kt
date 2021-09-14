/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.pipeline

import com.intellij.lang.LighterASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.fir.FirRealSourceElementKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.builder.AstToFirConverter
import org.jetbrains.kotlin.fir.builder.BodyBuildingMode
import org.jetbrains.kotlin.fir.builder.PsiHandlingMode
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.lightTree.converter.DeclarationsConverter
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.fir.toFirPsiSourceElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File
import java.net.URI

class PsiToFirConverter(
    private val session: FirSession,
    scopeProvider: FirScopeProvider,
    private val continueOnSyntaxErrors: Boolean = false,
    private val syntaxErrorReporter: ((FirSourceElement) -> Unit)? = null,
    bodyBuildingMode: BodyBuildingMode = BodyBuildingMode.NORMAL
) : AstToFirConverter<KtFile> {

    private val builder = RawFirBuilder(session, scopeProvider, PsiHandlingMode.COMPILER, bodyBuildingMode)

    override fun convert(node: KtFile, source: URI): FirFile? {
        var hasErrors = false
        syntaxErrorReporter?.let { syntaxErrorReporter ->
            node.acceptChildren(object : KtTreeVisitorVoid() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    syntaxErrorReporter(element.toFirPsiSourceElement(FirRealSourceElementKind))
                    hasErrors = true
                }
            })
        }
        return if (hasErrors && !continueOnSyntaxErrors) null
        else builder.buildFirFile(node)
    }
}

fun PsiToFirConverter.convert(files: Collection<KtFile>): List<FirFile> = files.mapNotNull {
    convert(it, URI(it.virtualFile.path))
}

class LightTreeToFirConverter(
    private val session: FirSession,
    private val scopeProvider: FirScopeProvider,
    private val stubMode: Boolean = false,
    private val syntaxErrorReporter: ((FirSourceElement) -> Unit)? = null
) : AstToFirConverter<FlyweightCapableTreeStructure<LighterASTNode>> {

    override fun convert(node: FlyweightCapableTreeStructure<LighterASTNode>, source: URI): FirFile {
        return DeclarationsConverter(session, scopeProvider, stubMode, node, syntaxErrorReporter = syntaxErrorReporter)
            .convertFile(node.root, File(source.path).name)
    }
}
