/*
 * Copyright 2024 Enaium
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.enaium.jimmer.dto.lsp.intellij.setting

import cn.enaium.jimmer.dto.lsp.intellij.Constants
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.redhat.devtools.lsp4ij.LanguageServerManager
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.jdesktop.swingx.JXTitledSeparator
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * @author Enaium
 */
class SettingConfigurable : Configurable {

    private val formattingPropsSpaceLine: ComboBox<PluginSetting.Setting.JimmerDTO.Formatting.PropsSpaceLine> =
        ComboBox<PluginSetting.Setting.JimmerDTO.Formatting.PropsSpaceLine>().apply {
            PluginSetting.Setting.JimmerDTO.Formatting.PropsSpaceLine.values().forEach { addItem(it) }
        }

    private val classpathFindBuilder: JBCheckBox = JBCheckBox("Find Builder")
    private val classpathFindConfiguration: JBCheckBox = JBCheckBox("Find Configuration")
    private val classpathFindOtherProject: JBCheckBox = JBCheckBox("Find Other Project")
    private val useJetBrainsRuntime: JBCheckBox = JBCheckBox("Use JetBrains Runtime")

    override fun createComponent(): JComponent {
        val builder = FormBuilder.createFormBuilder()

        builder.addComponent(JXTitledSeparator("Formatting"))
        builder.addLabeledComponent(JLabel("Props Space Line"), formattingPropsSpaceLine, 1, false)
        builder.addComponent(JXTitledSeparator("Classpath"))
        builder.addComponent(classpathFindBuilder, 1)
        builder.addComponent(classpathFindConfiguration, 1)
        builder.addComponent(classpathFindOtherProject, 1)
        builder.addComponent(JXTitledSeparator("Other"))
        builder.addComponent(useJetBrainsRuntime, 1)
        return builder.addComponentFillVertically(JPanel(), 0).panel
    }

    override fun isModified(): Boolean {
        val settings = PluginSetting.INSTANCE.state
        return settings.jimmerDTO.formatting.propsSpaceLine != formattingPropsSpaceLine.selectedItem ||
                settings.jimmerDTO.classpath.findBuilder != classpathFindBuilder.isSelected ||
                settings.jimmerDTO.classpath.findConfiguration != classpathFindConfiguration.isSelected ||
                settings.jimmerDTO.classpath.findOtherProject != classpathFindOtherProject.isSelected ||
                settings.useJetBrainsRuntime != useJetBrainsRuntime.isSelected
    }

    override fun apply() {
        val settings = PluginSetting.INSTANCE.state
        settings.jimmerDTO.formatting.propsSpaceLine =
            formattingPropsSpaceLine.selectedItem as PluginSetting.Setting.JimmerDTO.Formatting.PropsSpaceLine
        settings.jimmerDTO.classpath.findBuilder = classpathFindBuilder.isSelected
        settings.jimmerDTO.classpath.findConfiguration = classpathFindConfiguration.isSelected
        settings.jimmerDTO.classpath.findOtherProject = classpathFindOtherProject.isSelected
        settings.useJetBrainsRuntime = useJetBrainsRuntime.isSelected
        ProjectManager.getInstance().openProjects.firstOrNull()?.also { project ->
            LanguageServerManager.getInstance(project)
                .getLanguageServer(Constants.SERVER_ID).thenApply {
                    it?.workspaceService?.didChangeConfiguration(
                        DidChangeConfigurationParams(
                            GsonBuilder().setFieldNamingStrategy(FieldNamingPolicy.UPPER_CAMEL_CASE).create()
                                .toJsonTree(PluginSetting.INSTANCE.state.jimmerDTO).asJsonObject
                        )
                    )
                }
        }
    }

    override fun reset() {
        val settings = PluginSetting.INSTANCE.state
        formattingPropsSpaceLine.selectedItem = settings.jimmerDTO.formatting.propsSpaceLine
        classpathFindBuilder.isSelected = settings.jimmerDTO.classpath.findBuilder
        classpathFindConfiguration.isSelected = settings.jimmerDTO.classpath.findConfiguration
        classpathFindOtherProject.isSelected = settings.jimmerDTO.classpath.findOtherProject
        useJetBrainsRuntime.isSelected = settings.useJetBrainsRuntime
    }

    override fun getDisplayName(): String {
        return Constants.NAME
    }
}