/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.util

import org.jetbrains.kotlin.util.TypeRegistryBase
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

abstract class TypeRegistry<K : Any, V : Any> : TypeRegistryBase<K, V>() {
    override fun <T : K> ConcurrentHashMap<KClass<out K>, Int>.customComputeIfAbsent(
        kClass: KClass<T>,
        compute: (KClass<out K>) -> Int
    ): Int {
        return this.computeIfAbsent(kClass, compute)
    }
}
