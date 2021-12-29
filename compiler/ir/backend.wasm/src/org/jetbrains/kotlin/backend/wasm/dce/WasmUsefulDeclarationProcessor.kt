/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.dce

import org.jetbrains.kotlin.backend.common.ir.isOverridable
import org.jetbrains.kotlin.backend.wasm.WasmBackendContext
import org.jetbrains.kotlin.backend.wasm.ir2wasm.*
import org.jetbrains.kotlin.backend.wasm.utils.*
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.backend.js.dce.UsefulDeclarationProcessor
import org.jetbrains.kotlin.ir.backend.js.utils.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.wasm.ir.*

internal class WasmUsefulDeclarationProcessor(override val context: WasmBackendContext, printReachabilityInfo: Boolean) :
    UsefulDeclarationProcessor(printReachabilityInfo, removeUnusedAssociatedObjects = false) {

    private val unitGetInstance: IrSimpleFunction = context.findUnitGetInstanceFunction()

    override val bodyVisitor: BodyVisitorBase = object : BodyVisitorBase() {
        override fun <T> visitConst(expression: IrConst<T>) {
            if (expression.kind is IrConstKind.String) {
                context.wasmSymbols.stringGetLiteral.owner.enqueue("String literal intrinsic getter stringGetLiteral")
            }
        }

        private fun tryToProcessIntrinsicCall(call: IrCall): Boolean = when (call.symbol) {
            context.wasmSymbols.unboxIntrinsic -> {
                val fromType = call.getTypeArgument(0)
                if (fromType != null && !fromType.isNothing() && !fromType.isNullableNothing()) {
                    val backingField = call.getTypeArgument(1)
                        ?.let { context.inlineClassesUtils.getInlinedClass(it) }
                        ?.let { getInlineClassBackingField(it) }
                    if (backingField != null) {
                        backingField.parentAsClass.enqueue("type for unboxIntrinsic")
                        backingField.enqueue("backing inline class field for unboxIntrinsic")
                    }
                }
                true
            }
            context.wasmSymbols.wasmClassId,
            context.wasmSymbols.wasmInterfaceId,
            context.wasmSymbols.wasmRefCast -> {
                call.getTypeArgument(0)?.getClass()?.symbol?.owner?.enqueue("generic intrinsic ${call.symbol.owner.name}")
                true
            }
            else -> false
        }

        private fun tryToProcessWasmOpIntrinsicCall(call: IrCall, function: IrFunction): Boolean {
            if (function.hasWasmNoOpCastAnnotation()) {
                return true
            }

            val opString = function.getWasmOpAnnotation()
            if (opString != null) {
                val op = WasmOp.valueOf(opString)
                when (op.immediates.size) {
                    0 -> {
                        if (op == WasmOp.REF_TEST) {
                            call.getTypeArgument(0)?.getRuntimeClass?.enqueue("REF_TEST")
                        }
                    }
                    1 -> {
                        if (op.immediates.firstOrNull() == WasmImmediateKind.STRUCT_TYPE_IDX) {
                            function.dispatchReceiverParameter?.type?.classOrNull?.owner?.enqueue("STRUCT_TYPE_IDX")
                        }
                    }
                }
                return true
            }
            return false
        }

        override fun visitCall(expression: IrCall) {
            super.visitCall(expression)

            if (expression.symbol == context.wasmSymbols.boxIntrinsic) {
                expression.getTypeArgument(0)?.getRuntimeClass?.enqueue("boxIntrinsic")
                return
            }

            if (expression.symbol == unitGetInstance.symbol) {
                unitGetInstance.enqueue("unitGetInstance")
                return
            }

            val function: IrFunction = expression.symbol.owner.realOverrideTarget
            if (function.returnType == context.irBuiltIns.unitType) {
                unitGetInstance.enqueue("unitGetInstance")
                return
            }

            if (tryToProcessIntrinsicCall(expression)) return
            if (tryToProcessWasmOpIntrinsicCall(expression, function)) return

            val isSuperCall = expression.superQualifierSymbol != null
            if (function is IrSimpleFunction && function.isOverridable && !isSuperCall) {
                val klass = function.parentAsClass
                if (!klass.isInterface) {
                    context.wasmSymbols.getVirtualMethodId.owner.enqueue("getVirtualMethodId")
                    function.symbol.owner.enqueue("referenceFunctionType")
                } else {
                    klass.symbol.owner.enqueue("referenceInterfaceId")
                    context.wasmSymbols.getInterfaceImplId.owner.enqueue("getInterfaceImplId")
                    function.symbol.owner.enqueue("referenceInterfaceTable and referenceFunctionType")
                }
            }
        }
    }

    private fun IrType.needToEnqueueType(): Boolean = when (this) {
        context.builtIns.booleanType,
        context.builtIns.byteType,
        context.builtIns.shortType,
        context.builtIns.charType,
        context.builtIns.booleanType,
        context.builtIns.byteType,
        context.builtIns.shortType,
        context.builtIns.intType,
        context.builtIns.charType,
        context.builtIns.longType,
        context.builtIns.floatType,
        context.builtIns.doubleType,
        context.builtIns.nothingType,
        context.wasmSymbols.voidType -> false
        else -> this.erasedUpperBound?.isExternal != true &&
                context.inlineClassesUtils.getInlinedClass(this) == null &&
                !isBuiltInWasmRefType(this)
    }

    private fun IrType.enqueueRuntimeClassOrAny(info: String): Unit =
        (this.getRuntimeClass ?: context.wasmSymbols.any.owner).enqueue(info)

    private fun IrType.enqueueType(info: String) {
        if (needToEnqueueType()) {
            enqueueRuntimeClassOrAny(info)
        }
    }

    override fun processClass(irClass: IrClass) {
        irClass.getWasmArrayAnnotation()?.type
            ?.enqueueType("array type for wasm array annotated")

        if (context.inlineClassesUtils.isClassInlineLike(irClass)) {
            irClass.declarations
                .firstIsInstanceOrNull<IrConstructor>()
                ?.takeIf { it.isPrimary }
                ?.enqueue("inline class primary ctor")
        }

        if (irClass.isInterface) {
            val metadata = InterfaceMetadata(irClass, context.irBuiltIns)
            for (method in metadata.methods) {
                method.function.enqueue("interface method")
            }
        } else {
            irClass.allFields(context.irBuiltIns).map {
                it.type.enqueueType("declaration fields types")
            }
        }
        super.processClass(irClass)
    }

    private fun IrValueParameter.enqueueValueParameterType() {
        if (context.inlineClassesUtils.shouldValueParameterBeBoxed(this)) {
            type.enqueueRuntimeClassOrAny("function ValueParameterType")
        } else {
            type.enqueueType("function ValueParameterType")
        }
    }

    private fun processIrFunction(irFunction: IrFunction) {
        if (irFunction.isFakeOverride) return

        if (irFunction is IrConstructor && context.inlineClassesUtils.isClassInlineLike(irFunction.parentAsClass)) {
            return
        }

        val isIntrinsic = irFunction.hasWasmNoOpCastAnnotation() || irFunction.getWasmOpAnnotation() != null
        if (isIntrinsic) return

        irFunction.getEffectiveValueParameters().forEach { it.enqueueValueParameterType() }
        irFunction.returnType.enqueueType("function return type")
    }

    override fun processSimpleFunction(irFunction: IrSimpleFunction) {
        super.processSimpleFunction(irFunction)
        processIrFunction(irFunction)
    }

    override fun processConstructor(irConstructor: IrConstructor) {
        super.processConstructor(irConstructor)
        processIrFunction(irConstructor)
    }

    override fun isExported(declaration: IrDeclaration): Boolean =
        declaration.isJsExport() || declaration.isEffectivelyExternal()
}