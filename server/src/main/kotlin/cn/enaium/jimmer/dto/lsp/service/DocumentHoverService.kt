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
import cn.enaium.jimmer.dto.lsp.utility.overlaps
import cn.enaium.jimmer.dto.lsp.utility.position
import org.eclipse.lsp4j.*
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentHoverService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        val document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(null)

        var hover: Hover? = null

        val ast = document.realTime.ast

        ast.exportStatement()?.also { exportStatement ->
            val range = Range(
                exportStatement.start.position(),
                exportStatement.stop.position(true)
            )
            if (range.overlaps(params.position)) {
                hover = Hover(
                    MarkupContent(
                        MarkupKind.MARKDOWN,
                        """
                        ## Export
                        `${exportStatement.typeParts.joinToString(".") { it.text }}`
                        ## Package
                        `${
                            exportStatement.packageParts.let {
                                if (it.isEmpty() && exportStatement.typeParts.size > 1) {
                                    "${exportStatement.typeParts.dropLast(1).joinToString(".") { it.text }}.dto"
                                } else {
                                    it.joinToString(".") { it.text }
                                }
                            }
                        }`
                    """.trimIndent()
                    ), range
                )
            }
        }

        ast.importStatement()?.also { importStatements ->
            importStatements.forEach { importStatement ->
                val parts = importStatement.parts
                val range = Range(
                    importStatement.start.position(),
                    importStatement.stop.position(true)
                )
                if (range.overlaps(params.position)) {
                    var types = if (importStatement.importedTypes.isEmpty()) {
                        "`${mutableListOf(*parts.toTypedArray()).removeLast().text}`"
                    } else {
                        importStatement.importedTypes.joinToString(", ") { "`${it.text}`" }
                    }
                    var packageName = if (importStatement.importedTypes.isEmpty()) {
                        parts.removeLast()
                        parts.joinToString(".") { it.text }
                    } else {
                        importStatement.parts.joinToString(".") { it.text }
                    }

                    hover = Hover(
                        MarkupContent(
                            MarkupKind.MARKDOWN,
                            """
                            ## Import
                            `$packageName`
                            ## Types
                            $types
                        """.trimIndent()
                        ), range
                    )
                }
            }
        }
        return CompletableFuture.completedFuture(hover)
    }
}