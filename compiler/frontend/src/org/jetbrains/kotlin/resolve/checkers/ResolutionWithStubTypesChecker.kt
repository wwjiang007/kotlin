/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.checkers

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.diagnostics.Errors.*
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.calls.KotlinCallResolver
import org.jetbrains.kotlin.resolve.calls.checkers.CallCheckerWithAdditionalResolve
import org.jetbrains.kotlin.resolve.calls.components.KotlinResolutionCallbacks
import org.jetbrains.kotlin.resolve.calls.components.stableType
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.inference.BuilderInferenceSession
import org.jetbrains.kotlin.resolve.calls.inference.components.NewTypeSubstitutor
import org.jetbrains.kotlin.resolve.calls.model.KotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.model.LambdaKotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.SimpleKotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.results.OverloadResolutionResults
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.tower.*
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.ClassicTypeCheckerState
import org.jetbrains.kotlin.types.checker.ClassicTypeCheckerStateInternals
import org.jetbrains.kotlin.types.checker.intersectTypes
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

@OptIn(ClassicTypeCheckerStateInternals::class)
class ResolutionWithStubTypesChecker(private val kotlinCallResolver: KotlinCallResolver) : CallCheckerWithAdditionalResolve {
    private val typeCheckerState = ClassicTypeCheckerState(isErrorTypeEqualsToAnything = false)

    override fun check(
        overloadResolutionResults: OverloadResolutionResults<*>,
        scopeTower: ImplicitScopeTower,
        resolutionCallbacks: KotlinResolutionCallbacks,
        expectedType: UnwrappedType?,
        context: BasicCallResolutionContext,
    ) {
        if (!overloadResolutionResults.isSingleResult) return
        val resolvedCall = overloadResolutionResults.resultingCall as? NewAbstractResolvedCall<*> ?: return

        val builderLambdas = (resolvedCall.psiKotlinCall.argumentsInParenthesis + resolvedCall.psiKotlinCall.externalArgument)
            .filterIsInstance<LambdaKotlinCallArgument>()
            .filter { it.hasBuilderInferenceAnnotation }

        for (lambda in builderLambdas) {
            val session = lambda.builderInferenceSession as BuilderInferenceSession
            val errorCalls = session.errorCallsInfo
            for (errorCall in errorCalls) {
                val res = errorCall.result
                if (res.isAmbiguity) {
                    val resolveCall = res.resultingCalls.first() as NewAbstractResolvedCall<*>? ?: return
                    val kotlinCall = resolveCall.psiKotlinCall

                    val valueArguments = kotlinCall.argumentsInParenthesis.takeIf { it.isNotEmpty() } ?: return
                    val newArguments = replaceArgumentsWithCollectionIfNeeded(
                        valueArguments,
                        session.getNotFixedToInferredTypesSubstitutor(),
                        context.trace
                    )

                    val newCall = PSIKotlinCallImpl(
                        kotlinCall.callKind, kotlinCall.psiCall, kotlinCall.tracingStrategy, kotlinCall.explicitReceiver,
                        kotlinCall.dispatchReceiverForInvokeExtension, kotlinCall.name, kotlinCall.typeArguments, newArguments,
                        kotlinCall.externalArgument, kotlinCall.startingDataFlowInfo, kotlinCall.resultDataFlowInfo,
                        kotlinCall.dataFlowInfoForArguments, kotlinCall.isForImplicitInvoke
                    )

                    val candidateForCollectionReplacedArgument = kotlinCallResolver.resolveCall(
                        scopeTower, resolutionCallbacks, newCall, expectedType, context.collectAllCandidates
                    ).singleOrNull() ?: return

                    for ((i, valueArgument) in valueArguments.withIndex()) {
                        if (valueArgument !is ExpressionKotlinCallArgumentImpl) continue
                        val type1 = valueArgument.receiver.stableType
                        val type2 = (newArguments[i] as ExpressionKotlinCallArgumentImpl).receiver.stableType
                        if (type1 != type2) {
                            context.trace.report(
                                STUB_TYPE_IN_ARGUMENT_CAUSES_AMBIGUITY.on(
                                    valueArgument.psiExpression!!,
                                    type1,
                                    type2
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun replaceArgumentsWithCollectionIfNeeded(
        valueArguments: List<KotlinCallArgument>,
        sub: NewTypeSubstitutor,
        trace: BindingTrace,
    ): List<KotlinCallArgument> = valueArguments.mapIndexed { i, argument ->
        if (argument !is ExpressionKotlinCallArgumentImpl) return@mapIndexed argument
        val psiExpression = argument.psiExpression ?: return@mapIndexed argument
        val newType = sub.safeSubstitute(argument.receiver.stableType)

        ExpressionKotlinCallArgumentImpl(
            argument.psiCallArgument.valueArgument,
            DataFlowInfo.EMPTY,
            DataFlowInfo.EMPTY,
            ReceiverValueWithSmartCastInfo(
                ExpressionReceiver.create(psiExpression, newType, trace.bindingContext),
                emptySet(),
                true
            )
        )
    }
}
