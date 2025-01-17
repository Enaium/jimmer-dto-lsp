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

package cn.enaium.jimmer.dto.lsp.intellij.action

import cn.enaium.jimmer.dto.lsp.intellij.Constants
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.redhat.devtools.lsp4ij.commands.CommandExecutor
import com.redhat.devtools.lsp4ij.commands.LSPCommandContext
import org.eclipse.lsp4j.Command

/**
 * @author Enaium
 */
class ResolveDependencies : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.also {
            CommandExecutor.executeCommand(
                LSPCommandContext(
                    Command(
                        "Resolve Dependencies",
                        "jimmer.dto.resolveDependencies"
                    ), it
                ).apply {
                    preferredLanguageServerId = Constants.SERVER_ID
                })
        }
    }
}