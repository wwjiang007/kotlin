/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.jvm.checkers

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.MemberComparator
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.isTypeRefinementEnabled
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.jvm.JvmDelegationFilter
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm

object JvmDelegationChecker : DeclarationChecker {
    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is ClassDescriptor || declaration !is KtClassOrObject) return
        if (!context.languageVersionSettings.supportsFeature(LanguageFeature.WarnOnDelegationToJvmDefaultInterfaceMembers)) return

        val explicitlyOverriddenFunctions = descriptor.defaultType.memberScope.getContributedDescriptors()
            .filterIsInstance<FunctionDescriptor>()
            .filter { it.kind == CallableMemberDescriptor.Kind.DECLARATION }
            .groupBy { it.name }

        for (specifier in declaration.superTypeListEntries) {
            if (specifier !is KtDelegatedSuperTypeEntry) continue
            val supertype = specifier.typeReference?.let { context.trace.get(BindingContext.TYPE, it) } ?: continue

            val defaultMethods = supertype.memberScope.getContributedDescriptors()
                .filterIsInstance<FunctionDescriptor>()
                .filter { isNonOverriddenJvmDefaultMethod(it, explicitlyOverriddenFunctions) }

            if (defaultMethods.isNotEmpty()) {
                context.trace.report(
                    ErrorsJvm.DELEGATION_TO_JAVA_DEFAULT_METHODS.on(specifier, defaultMethods.sortedWith(MemberComparator.INSTANCE))
                )
            }
        }
    }

    private fun isNonOverriddenJvmDefaultMethod(
        function: FunctionDescriptor,
        explicitlyOverriddenFunctions: Map<Name, List<FunctionDescriptor>>
    ): Boolean {
        if (!JvmDelegationFilter.isJvmDefaultMember(function)) return false

        val functionsWithThisName = explicitlyOverriddenFunctions[function.name].orEmpty()
        if (functionsWithThisName.any { OverridingUtil.overrides(it, function, it.module.isTypeRefinementEnabled(), true) })
            return false

        return true
    }
}
