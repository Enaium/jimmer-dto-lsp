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

import cn.enaium.jimmer.dto.lsp.DocumentManager
import cn.enaium.jimmer.dto.lsp.position
import org.babyfish.jimmer.dto.compiler.DtoParser
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentSymbolService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {
    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
        val document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(emptyList())

        fun getDocumentSymbols(
            bodyContext: DtoParser.DtoBodyContext,
        ): MutableList<DocumentSymbol> {
            val symbols = mutableListOf<DocumentSymbol>()
            for (explicitProp in bodyContext.explicitProps) {
                val positivePropContext = explicitProp.positiveProp() ?: continue
                val dtoBodyContext = positivePropContext.dtoBody() ?: continue
                val text = explicitProp.start.text
                symbols.add(DocumentSymbol().apply {
                    name = if (text == "flat") explicitProp.positiveProp().props[0].text else text
                    kind = SymbolKind.Field
                    range = Range(explicitProp.start.position(), explicitProp.stop.position())
                    selectionRange = Range(explicitProp.start.position(), explicitProp.stop.position())
                    children = getDocumentSymbols(dtoBodyContext)
                })
            }
            return symbols
        }

        val documentSymbols = mutableListOf<DocumentSymbol>()

        document.ast.dtoTypes.forEach { dtoType ->
            if (dtoType.name == null) return@forEach
            documentSymbols.add(DocumentSymbol().apply {
                name = dtoType.name.text
                kind = SymbolKind.Class
                range = Range(dtoType.name.position(), dtoType.stop.position())
                selectionRange = Range(dtoType.name.position(), dtoType.stop.position())
                children = getDocumentSymbols(dtoType.dtoBody())
            })
        }

        return CompletableFuture.completedFuture(documentSymbols.map {
            Either.forRight<SymbolInformation, DocumentSymbol>(
                it
            )
        }.toMutableList())
    }
}