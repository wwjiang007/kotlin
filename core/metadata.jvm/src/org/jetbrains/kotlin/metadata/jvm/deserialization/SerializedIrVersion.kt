/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.metadata.jvm.deserialization

import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion

class SerializedIrVersion(major: Int, minor: Int, patch: Int) : BinaryVersion(major, minor, patch) {
    override fun isCompatible(): Boolean = isCompatibleTo(INSTANCE)

    companion object {
        @JvmField
        val INSTANCE = SerializedIrVersion(0, 0, 1)

        @JvmField
        val INVALID_VERSION = SerializedIrVersion(-1, -1, -1)
    }
}

fun SerializedIrVersion(vararg values: Int): SerializedIrVersion {
    if (values.size != 3) error("Serialized Ir version should be in major.minor.patch format: $values")
    return SerializedIrVersion(values[0], values[1], values[2])
}