/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi.psiUtil

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtPsiUtil

fun checkReservedPrefixWord(sink: DiagnosticSink, element: PsiElement, word: String, message: String) {
    KtPsiUtil.getPreviousWord(element, word)?.let {
        sink.report(Errors.UNSUPPORTED.on(it, message))
    }
}

