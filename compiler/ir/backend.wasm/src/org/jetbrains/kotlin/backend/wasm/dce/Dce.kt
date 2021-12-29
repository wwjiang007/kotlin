/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.dce

import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.ir.backend.js.dce.UselessDeclarationsRemover
import org.jetbrains.kotlin.ir.backend.js.utils.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.js.config.JSConfigurationKeys

internal fun eliminateDeadDeclarations(
    modules: Iterable<IrModuleFragment>,
    context: WasmBackendContext,
) {
    val allRoots = buildRoots(modules)

    val additionalDeclarations = listOf(
        context.irBuiltIns.throwableClass.owner,
        context.fieldInitFunction,
        context.mainCallsWrapperFunction
    )

    val printReachabilityInfo =
        context.configuration.getBoolean(JSConfigurationKeys.PRINT_REACHABILITY_INFO) ||
                java.lang.Boolean.getBoolean("kotlin.wasm.dce.print.reachability.info")

    val usefulDeclarations = WasmUsefulDeclarationProcessor(context, printReachabilityInfo)
        .collectDeclarations(allRoots, additionalDeclarations)

    val uselessDeclarationsProcessor = UselessDeclarationsRemover(removeUnusedAssociatedObjects = false, usefulDeclarations, context, null)
    modules.forEach { module ->
        module.files.forEach {
            it.acceptVoid(uselessDeclarationsProcessor)
        }
    }
}

private fun buildRoots(modules: Iterable<IrModuleFragment>): List<IrDeclaration> {
    val rootDeclarations = mutableListOf<IrDeclaration>()
    modules.forEach { module ->
        module.files.forEach { file ->
            file.declarations.forEach { declaration ->
                if (declaration.isJsExport() || declaration.isEffectivelyExternal()) {
                    rootDeclarations.add(declaration)
                }
            }
        }
    }
    return rootDeclarations
}
