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
import org.jetbrains.kotlin.fir.builder.*
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.fir.lightTree.converter.DeclarationsConverter
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.fir.toFirPsiSourceElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File
import java.net.URI

enum class SyntaxErrorCheckingMode {
    SKIP_CHECK,
    CONVERT_ON_ERRORS,
    EMPTY_RESULT_ON_ERROR
}

class PsiToFirConverter(
    session: FirSession,
    scopeProvider: FirScopeProvider,
    private val errorCheckingMode: SyntaxErrorCheckingMode = SyntaxErrorCheckingMode.EMPTY_RESULT_ON_ERROR,
    bodyBuildingMode: BodyBuildingMode = BodyBuildingMode.NORMAL
): AstToFirConverter<KtFile> {
    private val builder = RawFirBuilder(session, scopeProvider, PsiHandlingMode.COMPILER, bodyBuildingMode)

    override fun convert(node: KtFile, source: URI): FirFileWithSyntaxErrors {
        val errors = mutableListOf<FirSyntaxError>()
        if (errorCheckingMode != SyntaxErrorCheckingMode.SKIP_CHECK) {
            node.acceptChildren(object : KtTreeVisitorVoid() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    errors.add(FirSyntaxError(element.toFirPsiSourceElement(FirRealSourceElementKind), element.errorDescription))
                }
            })
        }
        val firFile =
            if (errors.isNotEmpty() && errorCheckingMode != SyntaxErrorCheckingMode.CONVERT_ON_ERRORS) buildFile {}
            else builder.buildFirFile(node)

        return FirFileWithSyntaxErrors(firFile, errors)
    }
}

fun PsiToFirConverter.convert(files: Collection<KtFile>): List<FirFileWithSyntaxErrors> = files.map {
    convert(it, URI(it.virtualFile.path))
}

class LightTreeToFirConverter(
    private val session: FirSession,
    private val scopeProvider: FirScopeProvider,
    private val stubMode: Boolean = false,
    private val errorCheckingMode: SyntaxErrorCheckingMode = SyntaxErrorCheckingMode.EMPTY_RESULT_ON_ERROR
) : AstToFirConverter<FlyweightCapableTreeStructure<LighterASTNode>> {

    override fun convert(node: FlyweightCapableTreeStructure<LighterASTNode>, source: URI): FirFileWithSyntaxErrors {
        val errors = mutableListOf<FirSyntaxError>()
        val converter = DeclarationsConverter(
            session, scopeProvider, stubMode,
            node,
            syntaxErrorReporter = if (errorCheckingMode != SyntaxErrorCheckingMode.SKIP_CHECK) { it: FirSourceElement ->
                errors.add(FirSyntaxError(it, ""))
            }
            else null
        )
        val conversionResult = converter.convertFile(node.root, File(source.path).name)
        val firFile =
            if (errors.isNotEmpty() && errorCheckingMode != SyntaxErrorCheckingMode.CONVERT_ON_ERRORS) buildFile {}
            else conversionResult

        return FirFileWithSyntaxErrors(firFile, errors)
    }
}

