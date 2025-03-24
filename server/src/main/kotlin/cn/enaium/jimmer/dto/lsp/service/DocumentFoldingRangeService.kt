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
import cn.enaium.jimmer.dto.lsp.utility.range
import org.babyfish.jimmer.dto.compiler.DtoParser
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeKind
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Range
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentFoldingRangeService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {

    private val ranges = mutableListOf<Range>()

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
        val document =
            documentManager.getDocument(params.textDocument.uri)
                ?: return CompletableFuture.completedFuture(emptyList())
        ranges.clear()
        dto(document.realTime.ast)

        return CompletableFuture.completedFuture(ranges.map {
            FoldingRange(it.start.line, it.end.line - 1).apply {
                kind = FoldingRangeKind.Region
            }
        })
    }

    private fun positiveProp(positivePropContext: DtoParser.PositivePropContext) {
        positivePropContext.dtoBody()?.also {
            ranges.add(it.range())
            it.explicitProp()?.takeIf { it.isNotEmpty() }?.forEach { explicitProp ->
                explicitProp(explicitProp)
            }
        }
    }

    private fun explicitProp(explicitPropContext: DtoParser.ExplicitPropContext) {
        explicitPropContext.positiveProp()?.also {
            positiveProp(it)
        }
        explicitPropContext.aliasGroup()?.also {
            ranges.add(it.range())
            it.positiveProp()?.takeIf { it.isNotEmpty() }?.forEach { positiveProp ->
                positiveProp(positiveProp)
            }
        }
    }

    private fun dto(ast: DtoParser.DtoContext): List<Range> {
        ast.dtoTypes.forEach { dtoType ->
            dtoType.dtoBody()?.also { dtoTypeBody ->
                ranges.add(dtoTypeBody.range())
                dtoTypeBody.explicitProp()?.takeIf { it.isNotEmpty() }?.forEach { explicitProp ->
                    explicitProp(explicitProp)
                }
            }
        }
        return ranges
    }
}