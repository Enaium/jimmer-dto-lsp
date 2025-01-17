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

package cn.enaium.jimmer.dto.lsp.intellij.status

import cn.enaium.jimmer.dto.lsp.intellij.Constants
import cn.enaium.jimmer.dto.lsp.intellij.DtoFileType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup

/**
 * @author Enaium
 */
class StatusBarItem(project: Project) : EditorBasedStatusBarPopup(project, false) {
    override fun ID(): String {
        return "${Constants.LANGUAGE_ID}BarItem"
    }

    override fun createInstance(project: Project): StatusBarWidget {
        return StatusBarItem(project)
    }

    override fun createPopup(context: DataContext): ListPopup {
        val viewLogs = ActionManager.getInstance().getAction("cn.enaium.jimmer.dto.lsp.intellij.action.ViewLogs")
        val resolveDependencies =
            ActionManager.getInstance().getAction("cn.enaium.jimmer.dto.lsp.intellij.action.ResolveDependencies")
        return JBPopupFactory.getInstance().createActionGroupPopup(
            Constants.NAME,
            DefaultActionGroup(listOf(viewLogs, resolveDependencies)),
            context,
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            false
        )
    }

    override fun getWidgetState(file: VirtualFile?): WidgetState {
        return WidgetState(Constants.NAME, null, true).apply {
            icon = Constants.ICON
        }
    }

    override fun isEnabledForFile(file: VirtualFile?): Boolean {
        return file?.fileType == DtoFileType
    }
}