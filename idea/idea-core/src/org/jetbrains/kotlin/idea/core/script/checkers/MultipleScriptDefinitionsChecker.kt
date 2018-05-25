/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.core.script.checkers

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.HyperlinkLabel
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.StandardIdeScriptDefinition
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.scripting.compiler.plugin.KotlinScriptDefinitionAdapterFromNewAPIBase

class MultipleScriptDefinitionsChecker(private val project: Project) : EditorNotifications.Provider<EditorNotificationPanel>() {

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): EditorNotificationPanel? {
        if (file.fileType != KotlinFileType.INSTANCE) {
            return null
        }

        val psiFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null
        if (psiFile.language !== KotlinLanguage.INSTANCE) {
            return null
        }

        if (!psiFile.isScript()) return null
        if (KotlinScriptingSettings.getInstance(psiFile.project).suppressDefinitionsCheck) return null

        val allApplicableDefinitions = ScriptDefinitionsManager.getInstance(project)
            .getAllDefinitions()
            .filter { it !is StandardIdeScriptDefinition && it.isScript(psiFile.name) }
            .toList()
        if (allApplicableDefinitions.size < 2) return null

        return createNotification(psiFile, allApplicableDefinitions)
    }

    companion object {
        private val KEY = Key.create<EditorNotificationPanel>("MultipleScriptDefinitionsChecker")

        private fun createNotification(psiFile: KtFile, defs: List<KotlinScriptDefinition>): EditorNotificationPanel {
            return EditorNotificationPanel().apply {
                setText("Multiple script definitions are applicable for this script. ${defs.first().name} is used")
                createComponentActionLabel("Show all") {
                    val list = JBPopupFactory.getInstance().createListPopup(
                        object : BaseListPopupStep<KotlinScriptDefinition>(null, defs) {
                            override fun getTextFor(value: KotlinScriptDefinition): String {
                                return StringBuilder().apply {
                                    append(value.name)
                                    when (value) {
                                        is KotlinScriptDefinitionFromAnnotatedTemplate -> {
                                            append(" (pattern=")
                                            append(value.scriptFilePattern)
                                            append(")")
                                        }
                                        is KotlinScriptDefinitionAdapterFromNewAPIBase -> {
                                            append(" (extension=")
                                            append(value.scriptFileExtensionWithDot)
                                            append(")")
                                        }
                                        is StandardIdeScriptDefinition -> {
                                            append(" (extension=")
                                            append(".")
                                            append(KotlinFileType.EXTENSION)
                                            append(")")
                                        }
                                    }
                                }.toString()
                            }
                        }
                    )
                    list.showUnderneathOf(it)
                }

                createComponentActionLabel("Ignore") {
                    KotlinScriptingSettings.getInstance(psiFile.project).suppressDefinitionsCheck = true
                    EditorNotifications.getInstance(psiFile.project).updateAllNotifications()
                }
            }
        }

        private fun EditorNotificationPanel.createComponentActionLabel(labelText: String, callback: (HyperlinkLabel) -> Unit) {
            val label: Ref<HyperlinkLabel> = Ref.create()
            val action = Runnable {
                callback(label.get())
            }
            label.set(createActionLabel(labelText, action))
        }
    }
}