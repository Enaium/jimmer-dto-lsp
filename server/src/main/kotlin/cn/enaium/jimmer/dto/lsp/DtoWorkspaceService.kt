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

package cn.enaium.jimmer.dto.lsp

import cn.enaium.jimmer.dto.lsp.service.WorkspaceExecuteCommandService
import cn.enaium.jimmer.dto.lsp.service.WorkspaceSymbolService
import cn.enaium.jimmer.dto.lsp.utility.location
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DtoWorkspaceService(val workspace: Workspace) : WorkspaceService {
    private val executeCommand = WorkspaceExecuteCommandService(workspace)
    private val symbol = WorkspaceSymbolService(workspace)

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        val settings = params.settings ?: return
        workspace.setting = jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
            .readValue(settings.toString(), Setting::class.java)
        val settingFile = Main.location.parent.resolve("settings.json")
        workspace.setting.save(settingFile)
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {

    }

    override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
        return executeCommand.executeCommand(params)
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> {
        return symbol.symbol(params)
    }
}