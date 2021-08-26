/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("unused")

package org.jetbrains.kotlin.fir.pipeline

import com.intellij.lang.LighterASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.builder.AstToFirConverter
import org.jetbrains.kotlin.fir.builder.PsiHandlingMode
import org.jetbrains.kotlin.fir.builder.RawFirBuilder
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.lightTree.LightTree2Fir
import org.jetbrains.kotlin.fir.lightTree.converter.DeclarationsConverter
import org.jetbrains.kotlin.fir.resolve.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File
import java.net.URI

fun FirSession.buildFirViaLightTree(files: Collection<File>): List<FirFile> {
    val firProvider = (firProvider as FirProviderImpl)
    val builder = LightTree2Fir(this, firProvider.kotlinScopeProvider)
    return files.map {
        builder.buildFirFile(it).also { firFile ->
            firProvider.recordFile(firFile)
        }
    }
}

fun FirSession.buildFirFromKtFiles(
    ktFiles: Collection<KtFile>,
    continueOnSyntaxErrors: Boolean = true, // TODO: false by default
    syntaxErrorReporter: ((FirSourceElement) -> Unit)? = null
): List<FirFile> {
    val firProvider = (firProvider as FirProviderImpl)
    val builder = RawFirBuilder(this, firProvider.kotlinScopeProvider, PsiHandlingMode.COMPILER)
    return ktFiles.mapNotNull {
        val hasErrors = if (syntaxErrorReporter != null) {
            var errorsFound = false
            it.acceptChildren(object : KtTreeVisitorVoid() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    syntaxErrorReporter(element.toFirPsiSourceElement(FirRealSourceElementKind))
                    errorsFound = true
                }
            })
            errorsFound
        } else false
        if (hasErrors && !continueOnSyntaxErrors) null
        else {
            builder.buildFirFile(it).also { firFile ->
                firProvider.recordFile(firFile)
            }
        }
    }
}

//class PsiToFirConverter(
//    session: FirSession,
//    syntaxErrorReporter: ((FirSourceElement) -> Unit)? = null
//) : AstToFirConverter<PsiElement> {
//
//    private val firProvider = session.firProvider as FirProviderImpl
//    private val builder =
//        RawFirBuilder(session, firProvider.kotlinScopeProvider, PsiHandlingMode.COMPILER)
//
//    override fun convert(node: PsiElement, source: URI): FirFile {
//        node as KtFile
//        return builder.buildFirFile(node).also { firFile ->
//            firProvider.recordFile(firFile)
//        }
//    }
//
//}

class LightTreeToFirConverter(
    val session: FirSession,
    private val scopeProvider: FirScopeProvider,
    private val stubMode: Boolean = false,
    private val syntaxErrorReporter: ((FirSourceElement) -> Unit)? = null
) : AstToFirConverter<FlyweightCapableTreeStructure<LighterASTNode>> {

    override fun convert(node: FlyweightCapableTreeStructure<LighterASTNode>, source: URI): FirFile {
        return DeclarationsConverter(session, scopeProvider, stubMode, node, syntaxErrorReporter = syntaxErrorReporter)
            .convertFile(node.root, File(source).name)
    }
}
