/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.dce

import org.jetbrains.kotlin.backend.common.ir.isMemberOfOpenClass
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.JsCommonBackendContext
import org.jetbrains.kotlin.ir.backend.js.utils.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import java.util.*

abstract class UsefulDeclarationProcessor(
    private val printReachabilityInfo: Boolean,
    protected val removeUnusedAssociatedObjects: Boolean
) {
    abstract val context: JsCommonBackendContext

    protected fun getDeclarationByName(name: String): IrDeclaration =
        context.irBuiltIns.anyClass.owner.declarations.filterIsInstance<IrFunction>().single { it.name.asString() == name }

    protected val toStringMethod get() = getDeclarationByName("toString")
    protected abstract fun isExported(declaration: IrDeclaration): Boolean
    protected abstract val bodyVisitor: BodyVisitorBase

    protected open inner class BodyVisitorBase : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitFunctionAccess(expression: IrFunctionAccessExpression) {
            super.visitFunctionAccess(expression)
            expression.symbol.owner.enqueue("function access")
        }

        override fun visitRawFunctionReference(expression: IrRawFunctionReference) {
            super.visitRawFunctionReference(expression)
            expression.symbol.owner.enqueue("raw function access")
        }

        override fun visitVariableAccess(expression: IrValueAccessExpression) {
            super.visitVariableAccess(expression)
            expression.symbol.owner.enqueue("variable access")
        }

        override fun visitFieldAccess(expression: IrFieldAccessExpression) {
            super.visitFieldAccess(expression)
            expression.symbol.owner.enqueue("field access")
        }

        override fun visitStringConcatenation(expression: IrStringConcatenation) {
            super.visitStringConcatenation(expression)
            toStringMethod.enqueue("string concatenation")
        }
    }

    private fun addReachabilityInfoIfNeeded(
        from: IrDeclaration?,
        description: String?,
        isContagiousOverridableDeclaration: Boolean,
        altFromFqn: String?
    ) {
        if (!printReachabilityInfo) return
        val fromFqn = (from as? IrDeclarationWithName)?.fqNameWhenAvailable?.asString() ?: altFromFqn ?: "<unknown>"
        val toFqn = (this as? IrDeclarationWithName)?.fqNameWhenAvailable?.asString() ?: "<unknown>"
        val comment = (description ?: "") + (if (isContagiousOverridableDeclaration) "[CONTAGIOUS!]" else "")
        val info = "\"$fromFqn\" -> \"$toFqn\"" + (if (comment.isBlank()) "" else " // $comment")
        reachabilityInfo.add(info)
    }

    protected fun IrDeclaration.enqueue(
        from: IrDeclaration?,
        description: String?,
        isContagious: Boolean = true,
        altFromFqn: String? = null
    ) {
        // Ignore non-external IrProperty because we don't want to generate code for them and codegen doesn't support it.
        if (this is IrProperty && !this.isExternal) return

        // TODO check that this is overridable
        // it requires fixing how functions with default arguments is handled
        val isContagiousOverridableDeclaration = isContagious && this is IrOverridableDeclaration<*> && this.isMemberOfOpenClass

        addReachabilityInfoIfNeeded(from, description, isContagiousOverridableDeclaration, altFromFqn)

        if (isContagiousOverridableDeclaration) {
            contagiousReachableDeclarations.add(this as IrOverridableDeclaration<*>)
        }

        if (this !in result) {
            result.add(this)
            queue.addLast(this)
        }
    }

    protected fun IrDeclaration.enqueue(description: String, isContagious: Boolean = true) {
        enqueue(this, description, isContagious)
    }

    private val nestedDeclarationVisitor = object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitBody(body: IrBody) = Unit // Skip

        override fun visitDeclaration(declaration: IrDeclarationBase) {
            declaration.enqueue(declaration.parentClassOrNull, "roots' nested declaration")
            super.visitDeclaration(declaration)
        }
    }

    // This collection contains declarations whose reachability should be propagated to overrides.
    // Overriding uncontagious declaration will not lead to becoming a declaration reachable.
    // By default, all declarations treated as contagious, it's not the most efficient, but it's safest.
    // In case when we access a declaration through a fake-override declaration, the original (real) one will not be marked as contagious,
    // so, later, other overrides will not be processed unconditionally only because it overrides a reachable declaration.
    //
    // The collection must be a subset of [result] set.
    private val contagiousReachableDeclarations = hashSetOf<IrOverridableDeclaration<*>>()
    protected val constructedClasses = hashSetOf<IrClass>()
    private val reachabilityInfo: MutableSet<String> = if (printReachabilityInfo) linkedSetOf() else Collections.emptySet()
    private val queue = ArrayDeque<IrDeclaration>()
    protected val result = hashSetOf<IrDeclaration>()
    protected val classesWithObjectAssociations = hashSetOf<IrClass>()

    protected open fun processClass(irClass: IrClass) {
        irClass.superTypes.forEach {
            (it.classifierOrNull as? IrClassSymbol)?.owner?.enqueue("superTypes")
        }

        if (irClass.isObject && isExported(irClass)) {
            context.mapping.objectToGetInstanceFunction[irClass]!!
                .enqueue(irClass, "Exported object getInstance function")
        }

        irClass.annotations.forEach {
            val annotationClass = it.symbol.owner.constructedClass
            if (annotationClass.isAssociatedObjectAnnotatedAnnotation) {
                classesWithObjectAssociations += irClass
                annotationClass.enqueue("@AssociatedObject annotated annotation class")
            }
        }
    }

    protected open fun processSimpleFunction(irFunction: IrSimpleFunction) {
        if (irFunction.isFakeOverride) {
            irFunction.resolveFakeOverride()?.enqueue("real overridden fun", isContagious = false)
        }
    }

    protected open fun processConstructor(irConstructor: IrConstructor) {
        // Collect instantiated classes.
        irConstructor.constructedClass.let {
            it.enqueue("constructed class")
            constructedClasses += it
        }
    }

    protected open fun processConstructedClassDeclaration(declaration: IrDeclaration) {
        if (declaration in result) return

        fun IrOverridableDeclaration<*>.findOverriddenContagiousDeclaration(): IrOverridableDeclaration<*>? {
            for (overriddenSymbol in this.overriddenSymbols) {
                val overriddenDeclaration = overriddenSymbol.owner as? IrOverridableDeclaration<*> ?: continue

                if (overriddenDeclaration in contagiousReachableDeclarations) return overriddenDeclaration

                overriddenDeclaration.findOverriddenContagiousDeclaration()?.let {
                    return it
                }
            }
            return null
        }

        if (declaration is IrOverridableDeclaration<*>) {
            declaration.findOverriddenContagiousDeclaration()?.let {
                declaration.enqueue(it, "overrides useful declaration")
            }
        }

        // A hack to enforce property lowering.
        // Until a getter is accessed it doesn't get moved to the declaration list.
        if (declaration is IrProperty) {
            declaration.getter?.run {
                findOverriddenContagiousDeclaration()?.let { enqueue(it, "(getter) overrides useful declaration") }
            }
            declaration.setter?.run {
                findOverriddenContagiousDeclaration()?.let { enqueue(it, "(setter) overrides useful declaration") }
            }
        }
    }

    protected open fun handleAssociatedObjects(): Unit = Unit

    fun collectDeclarations(
        roots: List<IrDeclaration>,
        additionalDeclarations: Iterable<IrDeclaration>,
    ): Set<IrDeclaration> {

        // use withInitialIr to avoid ConcurrentModificationException in dce-driven lowering when adding roots' nested declarations (members)
        // Add roots
        roots.forEach {
            it.enqueue(null, null, altFromFqn = "<ROOT>")
        }
        // Add roots' nested declarations
        roots.forEach { rootDeclaration ->
            rootDeclaration.acceptChildren(nestedDeclarationVisitor, null)
        }

        additionalDeclarations.forEach {
            it.enqueue(null, "additional declaration", altFromFqn = "<ROOT>")
        }

        while (queue.isNotEmpty()) {
            while (queue.isNotEmpty()) {
                val declaration = queue.pollFirst()

                when (declaration) {
                    is IrClass -> processClass(declaration)
                    is IrSimpleFunction -> processSimpleFunction(declaration)
                    is IrConstructor -> processConstructor(declaration)
                }

                val body = when (declaration) {
                    is IrFunction -> declaration.body
                    is IrField -> declaration.initializer
                    is IrVariable -> declaration.initializer
                    else -> null
                }

                body?.acceptVoid(bodyVisitor)
            }

            handleAssociatedObjects()

            for (klass in constructedClasses) {
                // TODO a better way to support inverse overrides.
                for (declaration in klass.declarations) {
                    processConstructedClassDeclaration(declaration)
                }
            }
        }

        if (printReachabilityInfo) {
            reachabilityInfo.forEach(::println)
        }

        return result
    }
}