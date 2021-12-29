/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.dce

import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.ir.backend.js.dce.UselessDeclarationsRemover
import org.jetbrains.kotlin.ir.backend.js.utils.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.js.config.JSConfigurationKeys

private fun processFieldInitializers(
    context: WasmBackendContext,
    collector: WasmUsefulDeclarationProcessor,
    usefulDeclarationsWithoutInitializers: Set<IrDeclaration>
): Set<IrDeclaration> {
    val fieldInitFunctionAsList = listOf(context.fieldInitFunction)
    val initializerListInBody = (context.fieldInitFunction.body as IrBlockBody).statements
    val allInitializers = initializerListInBody.map { it as IrSetField }

    //We have to iteratively add used field initializers into fieldInitFunction function body and check if it's add new usages
    var currentUsefulDeclarations = usefulDeclarationsWithoutInitializers
    var initializersCountBefore = -1
    do {
        //Restore body with useful initializers (with original order)
        initializerListInBody.clear()
        allInitializers.forEach { initializer ->
            if (initializer.symbol.owner in currentUsefulDeclarations) {
                initializerListInBody.add(initializer)
            }
        }
        if (initializerListInBody.size == initializersCountBefore) break
        initializersCountBefore = initializerListInBody.size

        val currentUsefulDeclarationsCountBefore = currentUsefulDeclarations.size
        collector.setDeclarationNotVisited(context.fieldInitFunction)
        currentUsefulDeclarations = collector.collectDeclarations(
            rootDeclarations = emptyList(),
            additionalDeclarations = fieldInitFunctionAsList
        )
    } while (currentUsefulDeclarations.size != currentUsefulDeclarationsCountBefore)

    return currentUsefulDeclarations
}

internal fun eliminateDeadDeclarations(
    modules: Iterable<IrModuleFragment>,
    context: WasmBackendContext,
) {
    val printReachabilityInfo =
        context.configuration.getBoolean(JSConfigurationKeys.PRINT_REACHABILITY_INFO) ||
                java.lang.Boolean.getBoolean("kotlin.wasm.dce.print.reachability.info")
    val collector = WasmUsefulDeclarationProcessor(context, printReachabilityInfo)

    val rootDeclarations = buildRoots(modules)
    val additionalDeclarations = listOf(
        context.irBuiltIns.throwableClass.owner,
        context.mainCallsWrapperFunction,
    )

    val usefulDeclarationsWithoutInitializers = collector.collectDeclarations(
        rootDeclarations = rootDeclarations,
        additionalDeclarations = additionalDeclarations
    )

    val usefulDeclarations = processFieldInitializers(
        context = context,
        collector = collector,
        usefulDeclarationsWithoutInitializers = usefulDeclarationsWithoutInitializers
    )

    val remover = UselessDeclarationsRemover(
        removeUnusedAssociatedObjects = false,
        usefulDeclarations = usefulDeclarations,
        context = context,
        dceRuntimeDiagnostic = null,
    )

    modules.forEach { module ->
        module.files.forEach {
            it.acceptVoid(remover)
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
