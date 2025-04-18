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

package cn.enaium.jimmer.dto.lsp.service

import cn.enaium.jimmer.dto.lsp.Workspace
import cn.enaium.jimmer.dto.lsp.utility.CommandType
import org.eclipse.lsp4j.ExecuteCommandParams
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class WorkspaceExecuteCommandService(private val workspace: Workspace) : WorkspaceServiceAdapter(workspace) {
    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        when (params.command) {
            CommandType.RESOLVE_DEPENDENCIES.command -> {
                workspace.resolve()
            }
        }
        return CompletableFuture.completedFuture(null)
    }
}