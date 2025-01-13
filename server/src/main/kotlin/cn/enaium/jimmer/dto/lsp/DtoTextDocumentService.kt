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

import cn.enaium.jimmer.dto.lsp.service.*
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture


class DtoTextDocumentService(workspace: Workspace) : TextDocumentService {
    private val documentManager = DocumentManager()
    private val documentSyncService = DocumentSyncService(workspace, documentManager)
    private val documentSemanticTokensFullService = DocumentSemanticTokensFullService(documentManager)
    private val documentFoldingRangeService = DocumentFoldingRangeService(documentManager)
    private val documentCompletionService = DocumentCompletionService(workspace, documentManager)
    private val documentSymbolService = DocumentSymbolService(documentManager)
    private val documentFormattingService = DocumentFormattingService(documentManager)
    private val documentHoverService = DocumentHoverService(documentManager)
    private val documentDefinitionService = DocumentDefinitionService(documentManager)

    override fun didOpen(params: DidOpenTextDocumentParams) {
        documentSyncService.didOpen(params)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        documentSyncService.didChange(params)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        documentSyncService.didClose(params)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        documentSyncService.didSave(params)
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        return documentSemanticTokensFullService.semanticTokensFull(params)
    }

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
        return documentFoldingRangeService.foldingRange(params)
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        return documentCompletionService.completion(params)
    }

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        return documentSymbolService.documentSymbol(params)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        return documentFormattingService.formatting(params)
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        return documentHoverService.hover(params)
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        return documentDefinitionService.definition(params)
    }
}