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
import org.antlr.v4.runtime.Token
import org.babyfish.jimmer.dto.compiler.DtoLexer
import org.eclipse.lsp4j.*
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentFoldingRangeService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {
    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
        val ranges = mutableListOf<FoldingRange>()
        val document =
            documentManager.getDocument(params.textDocument.uri) ?: return CompletableFuture.completedFuture(ranges)
        return CompletableFuture.completedFuture(getBodyRange(document.realTime.commonToken.tokens).map {
            FoldingRange(it.start.line, it.end.line - 1).apply {
                kind = FoldingRangeKind.Region
            }
        })
    }

    private fun getBodyRange(tokens: List<Token>): List<Range> {
        val filter = tokens.filter { it.type == DtoLexer.T__5 || it.type == DtoLexer.T__7 }
        val ranges = mutableListOf<Range>()
        filter.forEach { _ ->
            var startLine = -1
            var startCount = 0

            filter.forEach {
                when (it.type) {
                    DtoLexer.T__5 -> {
                        if (!ranges.any { range -> range.start.line == it.line - 1 }) {
                            if (startLine == -1) {
                                startLine = it.line - 1
                            } else {
                                startCount++
                            }
                        }
                    }

                    DtoLexer.T__7 -> {
                        val endLine = it.line - 1
                        if (!ranges.any { range -> range.end.line == endLine }) {
                            if (startCount == 0) {
                                ranges.add(Range(Position(startLine, 0), Position(endLine, 0)))
                                startLine = -1
                            } else {
                                startCount--
                            }
                        }
                    }
                }
            }
        }
        return ranges
    }
}