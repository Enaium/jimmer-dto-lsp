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
import com.google.gson.annotations.Expose
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * @author Enaium
 */
@State(
    name = "cn.enaium.jimmer.dto.lsp.intellij.setting.DtoSetting",
    storages = [Storage("${Constants.ID}.xml")]
)
class PluginSetting : PersistentStateComponent<PluginSetting.Setting> {
    data class Setting(
        var jimmerDTO: JimmerDTO = JimmerDTO(),
        var useJetBrainsRuntime: Boolean = true
    ) {
        data class JimmerDTO(
            var formatting: Formatting = Formatting(),
            var classpath: Classpath = Classpath()
        ) {
            data class Formatting(
                var propsSpaceLine: PropsSpaceLine = PropsSpaceLine.HAS_ANNOTATION
            ) {
                enum class PropsSpaceLine {
                    ALWAYS, NEVER, HAS_ANNOTATION
                }
            }

            data class Classpath(
                var findBuilder: Boolean = true,
                var findConfiguration: Boolean = true,
                var findOtherProject: Boolean = true
            )
        }
    }

    private var state = Setting()

    override fun getState(): Setting {
        return state
    }

    override fun loadState(setting: Setting) {
        state = setting
    }

    companion object {
        val INSTANCE: PluginSetting
            get() = ApplicationManager.getApplication().getService(PluginSetting::class.java)
    }
}