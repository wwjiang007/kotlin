/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.inline.InlinerExpressionLocationHint
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal val inlineFunctionReferenceLowering = makeIrFilePhase<JvmBackendContext>(
    ::InlineFunctionReferenceLowering,
    name = "InlineFunctionReferenceLowering",
    description = "..."
)

class InlineFunctionReferenceLowering(val context: JvmBackendContext) : FileLoweringPass, IrElementTransformerVoidWithContext() {
    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(this)
    }

//    override fun visitVariable(declaration: IrVariable): IrStatement {
//        val initializer = declaration.initializer
//        if (initializer is IrBlock && initializer.origin is InlinerExpressionLocationHint && initializer.statements.singleOrNull() is IrBlock) {
//            val lambda = initializer.statements.single() as IrBlock
//            if (lambda.origin == IrStatementOrigin.LAMBDA) {
//                val functionReference = lambda.statements.last() as IrFunctionReference
//                val tempVars = functionReference.copyReceiversToVariables()
//                return declaration
//            }
//        }
//        return super.visitVariable(declaration)
//    }

    override fun visitComposite(expression: IrComposite): IrExpression {
        var newBody = expression
        while (true) {
            var index = -1
            var reference: IrFunctionReference? = null
            for ((i, stmt) in newBody.statements.withIndex()) {
                if (stmt is IrVariable) {
                    val initializer = stmt.initializer
                    if (initializer is IrBlock && initializer.origin is InlinerExpressionLocationHint && initializer.statements.singleOrNull() is IrBlock) {
                        val lambda = initializer.statements.single() as IrBlock
                        if (lambda.origin == IrStatementOrigin.LAMBDA) {
                            reference = lambda.statements.last() as IrFunctionReference
                            index = i
                            break
                        }
                    }
                }
            }

            if (reference == null) {
                break
            }

            val (dispatch, extension) = reference.copyReceiversToVariables()
            currentClass!!.irElement.transformChildrenVoid(VarReplace(newBody.statements[index] as IrVariable, reference, dispatch, extension))
            val newStatements = newBody.statements.toMutableList()

            newStatements.removeAt(index)
            for ((i, tmp) in listOfNotNull(dispatch, extension).withIndex()) {
                newStatements.add(index + i, tmp)
            }

            newBody = IrCompositeImpl(newBody.startOffset, newBody.endOffset, newBody.type, null, newStatements)
        }
        return newBody
    }

    private fun IrFunctionReference.copyReceiversToVariables(): Pair<IrVariable?, IrVariable?> {
        var dispatch: IrVariable? = null
        var extension: IrVariable? = null

        val irFun = this.symbol.owner
        if (irFun.dispatchReceiverParameter != null) {
            dispatch = currentScope!!.scope.createTemporaryVariable(
                irExpression = dispatchReceiver!!,
                nameHint = "inline_reference_dispatch",
                isMutable = false
            )
        }
        if (irFun.extensionReceiverParameter != null) {
            extension = currentScope!!.scope.createTemporaryVariable(
                irExpression = extensionReceiver!!,
                nameHint = "inline_reference_extension",
                isMutable = false
            )
        }

        return dispatch to extension
    }

    private class VarReplace(val irVariable: IrVariable, val reference: IrFunctionReference, val dispatch: IrVariable?, val extension: IrVariable?) : IrElementTransformerVoid() {
        override fun visitCall(expression: IrCall): IrExpression {
            if ((expression.dispatchReceiver as? IrGetValue)?.symbol == irVariable.symbol) {
                val function = reference.symbol.owner as IrSimpleFunction
                val newCall = IrCallImpl.fromSymbolOwner(expression.startOffset, expression.endOffset, function.symbol)
                if (function.dispatchReceiverParameter != null) {
                    newCall.dispatchReceiver = IrGetValueImpl(0, 0, dispatch!!.symbol)
                }
                if (function.extensionReceiverParameter != null) {
                    newCall.extensionReceiver = IrGetValueImpl(0, 0, extension!!.symbol)
                }

                var index = 0
                for (i in 0 until reference.valueArgumentsCount) {
                    val arg = reference.getValueArgument(i)
                    if (arg != null) {
                        index++
                        newCall.putValueArgument(i, reference.getValueArgument(i))
                    }
                }
                for (i in 0 until expression.valueArgumentsCount) {
                    newCall.putValueArgument(index + i, expression.getValueArgument(i))
                }
                return newCall
            }
            return super.visitCall(expression)
        }

        override fun visitGetValue(expression: IrGetValue): IrExpression {
            if (expression.symbol == irVariable.symbol) {
                //return IrFunctionExpressionImpl(0, 0, reference.type, reference.symbol.owner as IrSimpleFunction, IrStatementOrigin.LAMBDA)
                return reference
            }
            return super.visitGetValue(expression)
        }
    }
}
