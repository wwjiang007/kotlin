/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.pm20.util

import org.gradle.api.Project
import org.gradle.api.file.FileCollection

interface DependencyFilesHolder {
    val dependencyConfigurationName: String
    var dependencyFiles: FileCollection
}

class SimpleDependencyFilesHolder(
    override val dependencyConfigurationName: String,
    override var dependencyFiles: FileCollection
) : DependencyFilesHolder

internal fun Project.newDependencyFilesHolder(dependencyConfigurationName: String): DependencyFilesHolder =
    SimpleDependencyFilesHolder(dependencyConfigurationName, project.files())