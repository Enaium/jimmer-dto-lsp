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

import cn.enaium.jimmer.dto.lsp.utility.CommandType
import cn.enaium.jimmer.dto.lsp.utility.SemanticType
import cn.enaium.jimmer.dto.lsp.utility.location
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists

/**
 * @author Enaium
 */
class DtoLanguageServer : LanguageServer {

    private val workspace: Workspace = Workspace()

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {

        val settingFile = Main.location.parent.resolve("settings.json")

        if (settingFile.exists()) {
            workspace.setting = Setting.read(settingFile)
        } else {
            workspace.setting.save(settingFile)
        }

        params.workspaceFolders?.forEach {
            workspace.folders.add(it.uri)
        }

        workspace.resolve()

        return CompletableFuture.completedFuture(InitializeResult(ServerCapabilities().apply {
            workspace = WorkspaceServerCapabilities().apply {
                workspaceFolders = WorkspaceFoldersOptions().apply {
                    supported = true
                    setChangeNotifications(true)
                }
                executeCommandProvider = ExecuteCommandOptions().apply {
                    commands = CommandType.entries.map { it.command }
                }
            }
            setTextDocumentSync(TextDocumentSyncOptions().apply {
                openClose = true
                change = TextDocumentSyncKind.Full
                setSave(SaveOptions().apply {
                    includeText = true
                })
            })
            semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
                legend = SemanticTokensLegend().apply {
                    tokenTypes = SemanticType.entries.map { it.type }
                }
                setFull(true)
            }
            setFoldingRangeProvider(true)
            completionProvider = CompletionOptions().apply {
                triggerCharacters = listOf("*", "@")
                completionItem = CompletionItemOptions().apply {
                    labelDetailsSupport = true
                }
            }
            setDocumentSymbolProvider(DocumentSymbolOptions().apply {
                label = "Jimmer DTO"
            })
            setDocumentFormattingProvider(true)
            setHoverProvider(true)
            setDefinitionProvider(true)
            setWorkspaceSymbolProvider(true)
            codeLensProvider = CodeLensOptions().apply {
                resolveProvider = true
            }
        }))
    }

    override fun shutdown(): CompletableFuture<Any> {
        return CompletableFuture.completedFuture(true)
    }

    override fun exit() {

    }

    override fun getTextDocumentService(): TextDocumentService {
        return DtoTextDocumentService(workspace)
    }

    override fun getWorkspaceService(): WorkspaceService {
        return DtoWorkspaceService(workspace)
    }

    override fun setTrace(params: SetTraceParams) {
        logger.info("Trace: ${params.value}")
    }
}