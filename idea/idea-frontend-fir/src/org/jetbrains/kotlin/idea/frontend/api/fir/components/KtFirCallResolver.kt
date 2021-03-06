/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.frontend.api.fir.components

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.FirErrorNamedReference
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.impl.FirSimpleNamedReference
import org.jetbrains.kotlin.fir.resolve.calls.FirErrorReferenceWithCandidate
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.idea.fir.getCandidateSymbols
import org.jetbrains.kotlin.idea.fir.isImplicitFunctionCall
import org.jetbrains.kotlin.idea.fir.low.level.api.api.getOrBuildFir
import org.jetbrains.kotlin.idea.frontend.api.calls.*
import org.jetbrains.kotlin.idea.frontend.api.components.KtCallResolver
import org.jetbrains.kotlin.idea.frontend.api.diagnostics.KtNonBoundToPsiErrorDiagnostic
import org.jetbrains.kotlin.idea.frontend.api.fir.KtFirAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.fir.buildSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtFunctionLikeSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtVariableLikeSymbol
import org.jetbrains.kotlin.idea.frontend.api.tokens.ValidityToken
import org.jetbrains.kotlin.idea.frontend.api.withValidityAssertion
import org.jetbrains.kotlin.idea.references.FirReferenceResolveHelper
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class KtFirCallResolver(
    override val analysisSession: KtFirAnalysisSession,
    override val token: ValidityToken,
) : KtCallResolver(), KtFirAnalysisSessionComponent {

    override fun resolveCall(call: KtBinaryExpression): KtCall? = withValidityAssertion {
        when (val fir = call.getOrBuildFir(firResolveState)) {
            is FirFunctionCall -> resolveCall(fir)
            is FirComparisonExpression -> resolveCall(fir.compareToCall)
            is FirEqualityOperatorCall -> null // TODO
            else -> null
        }
    }

    override fun resolveCall(call: KtUnaryExpression): KtCall? = withValidityAssertion {
        when (val fir = call.getOrBuildFir(firResolveState)) {
            is FirFunctionCall -> resolveCall(fir)
            is FirBlock -> {
                // Desugared increment or decrement block. See [BaseFirBuilder#generateIncrementOrDecrementBlock]
                // There would be corresponding inc()/dec() call that is assigned back to a temp variable.
                val prefix = fir.statements.filterIsInstance<FirVariableAssignment>().find { it.rValue is FirFunctionCall }
                (prefix?.rValue as? FirFunctionCall)?.let { resolveCall(it) }
            }
            is FirCheckNotNullCall -> null // TODO
            else -> null
        }
    }

    override fun resolveCall(call: KtCallExpression): KtCall? = withValidityAssertion {
        val firCall = when (val fir = call.getOrBuildFir(firResolveState)) {
            is FirFunctionCall -> fir
            is FirSafeCallExpression -> fir.regularQualifiedAccess as? FirFunctionCall
            else -> null
        } ?: return null
        return resolveCall(firCall)
    }

    private fun resolveCall(firCall: FirFunctionCall): KtCall? {
        val session = firResolveState.rootModuleSession
        return when {
            firCall.isImplicitFunctionCall() -> {
                val target = with(FirReferenceResolveHelper) {
                    val calleeReference = (firCall.dispatchReceiver as FirQualifiedAccessExpression).calleeReference
                    calleeReference.toTargetSymbol(session, firSymbolBuilder).singleOrNull()
                }
                when (target) {
                    is KtVariableLikeSymbol -> firCall.createCallByVariableLikeSymbolCall(target)
                    null -> null
                    else -> firCall.asSimpleFunctionCall()
                }
            }
            else -> firCall.asSimpleFunctionCall()
        }
    }

    private fun FirFunctionCall.createCallByVariableLikeSymbolCall(variableLikeSymbol: KtVariableLikeSymbol): KtCall? {
        return when (val callReference = calleeReference) {
            is FirResolvedNamedReference -> {
                val functionSymbol = callReference.resolvedSymbol as? FirNamedFunctionSymbol
                val callableId = functionSymbol?.callableId ?: return null
                (callReference.resolvedSymbol.fir.buildSymbol(firSymbolBuilder) as? KtFunctionSymbol)
                    ?.let {
                        if (callableId in kotlinFunctionInvokeCallableIds) {
                            KtFunctionalTypeVariableCall(variableLikeSymbol, KtSuccessCallTarget(it))
                        } else {
                            KtVariableWithInvokeFunctionCall(variableLikeSymbol, KtSuccessCallTarget(it))
                        }
                    }
            }
            is FirErrorNamedReference -> KtVariableWithInvokeFunctionCall(
                variableLikeSymbol,
                callReference.createErrorCallTarget(source)
            )
            else -> error("Unexpected call reference ${callReference::class.simpleName}")
        }
    }

    private fun FirFunctionCall.asSimpleFunctionCall(): KtFunctionCall? {
        val target = when (val calleeReference = calleeReference) {
            is FirResolvedNamedReference -> calleeReference.getKtFunctionOrConstructorSymbol()?.let { KtSuccessCallTarget(it) }
            is FirErrorNamedReference -> calleeReference.createErrorCallTarget(source)
            is FirErrorReferenceWithCandidate -> calleeReference.createErrorCallTarget(source)
            is FirSimpleNamedReference ->
                null
              /*  error(
                    """
                      Looks like ${this::class.simpleName} && it calle reference ${calleeReference::class.simpleName} were not resolved to BODY_RESOLVE phase,
                      consider resolving it containing declaration before starting resolve calls
                      ${this.render()}
                      ${(this.psi as? KtElement)?.getElementTextInContext()}
                      """.trimIndent()
                )*/
            else -> error("Unexpected call reference ${calleeReference::class.simpleName}")
        } ?: return null
        return KtFunctionCall(target)
    }

    private fun FirErrorNamedReference.createErrorCallTarget(qualifiedAccessSource: FirSourceElement?): KtErrorCallTarget =
        KtErrorCallTarget(
            getCandidateSymbols().mapNotNull { it.fir.buildSymbol(firSymbolBuilder) as? KtFunctionLikeSymbol },
            source?.let { diagnostic.asKtDiagnostic(it, qualifiedAccessSource) } ?: KtNonBoundToPsiErrorDiagnostic(factoryName = null, diagnostic.reason, token)
        )

    private fun FirErrorReferenceWithCandidate.createErrorCallTarget(qualifiedAccessSource: FirSourceElement?): KtErrorCallTarget =
        KtErrorCallTarget(
            getCandidateSymbols().mapNotNull { it.fir.buildSymbol(firSymbolBuilder) as? KtFunctionLikeSymbol },
            source?.let { diagnostic.asKtDiagnostic(it,qualifiedAccessSource) } ?: KtNonBoundToPsiErrorDiagnostic(factoryName = null, diagnostic.reason, token)
        )

    private fun FirResolvedNamedReference.getKtFunctionOrConstructorSymbol(): KtFunctionLikeSymbol? =
        resolvedSymbol.fir.buildSymbol(firSymbolBuilder) as? KtFunctionLikeSymbol

    companion object {
        private val kotlinFunctionInvokeCallableIds = (0..23).flatMapTo(hashSetOf()) { arity ->
            listOf(
                CallableId(StandardNames.getFunctionClassId(arity), OperatorNameConventions.INVOKE),
                CallableId(StandardNames.getSuspendFunctionClassId(arity), OperatorNameConventions.INVOKE)
            )
        }
    }
}
