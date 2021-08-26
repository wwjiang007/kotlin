/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.lightTree

import com.intellij.lang.LighterASTNode
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.fir.builder.AstBuilder
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.parsing.KotlinLightParser
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

class LightTreeAstBuilder : AstBuilder<FlyweightCapableTreeStructure<LighterASTNode>> {

    companion object {
        private val parserDefinition = KotlinParserDefinition()
        private fun makeLexer() = KotlinLexer()

        fun buildLightTreeBlockExpression(code: String): FlyweightCapableTreeStructure<LighterASTNode> {
            val builder = PsiBuilderFactoryImpl().createBuilder(parserDefinition, makeLexer(), code)
            //KotlinParser.parseBlockExpression(builder)
            KotlinLightParser.parseBlockExpression(builder)
            return builder.lightTree
        }

        fun buildLightTreeLambdaExpression(code: String): FlyweightCapableTreeStructure<LighterASTNode> {
            val builder = PsiBuilderFactoryImpl().createBuilder(parserDefinition, makeLexer(), code)
            //KotlinParser.parseLambdaExpression(builder)
            KotlinLightParser.parseLambdaExpression(builder)
            return builder.lightTree
        }
    }

    override fun buildAST(code: String): FlyweightCapableTreeStructure<LighterASTNode> {
        val builder = PsiBuilderFactoryImpl().createBuilder(parserDefinition, makeLexer(), code)
        KotlinLightParser.parse(builder)
        return builder.lightTree
    }
}