/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.sir.lightclasses.extensions

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.sir.providers.SirSession
import org.jetbrains.sir.lightclasses.SirFromKtSymbol

@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
internal inline fun <reified S : KaDeclarationSymbol, reified R> SirFromKtSymbol<S>.lazyWithSessions(
    crossinline block: context(SirSession, KaSession) () -> R
): Lazy<R> {
    return lazy {
        withSessions(block)
    }
}

@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
internal inline fun <reified S : KaDeclarationSymbol, reified R> SirFromKtSymbol<S>.withSessions(
    crossinline block: context(SirSession, KaSession) () -> R,
): R {
    return analyze(ktModule) {
        block(sirSession, useSiteSession)
    }
}
