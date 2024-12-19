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
import cn.enaium.jimmer.dto.lsp.utility.position
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentFormattingService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {
    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        val document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(emptyList())

        val textEdits = mutableListOf<TextEdit>()

        val tokens = document.commonToken.tokens

        if (tokens.isNotEmpty()) {
            val exportStatement = document.ast.exportStatement()
            val importStatements = document.ast.importStatement()
            if (exportStatement != null) {
                val start = Position()
                var stop = Position()
                var text = ""

                if (exportStatement.typeParts.isNotEmpty()) {
                    stop = exportStatement.typeParts.last().position(textLength = true)
                    text += "export ${exportStatement.typeParts.joinToString(".") { it.text }}"
                }

                if (exportStatement.packageParts.isNotEmpty()) {
                    stop = exportStatement.packageParts.last().position(textLength = true)
                    text += "\n    -> package ${exportStatement.packageParts.joinToString(".") { it.text }}"
                }

                textEdits.add(TextEdit(Range(start, stop), text))
            }

            if (importStatements.isNotEmpty()) {

                val imports = mutableListOf<Pair<String, String>>()

                importStatements.forEach { importStatement ->
                    val parts = importStatement.parts
                    if (importStatement.importedTypes.isEmpty()) {
                        val importedType = parts.removeLast()
                        imports.add(parts.joinToString(".") { it.text } to importedType.text)
                    } else {
                        importStatement.importedTypes.forEach { importedType ->
                            imports.add(parts.joinToString(".") { it.text } to importedType.text)
                        }
                    }
                }

                textEdits.add(
                    TextEdit(
                        Range(
                            importStatements.first().start.position(),
                            importStatements.last().stop.position(textLength = true)
                        ),
                        imports.groupBy({ it.first }) { it.second }.map {
                            "import ${it.key}.${
                                if (it.value.count() == 1) {
                                    it.value.first()
                                } else {
                                    it.value.joinToString(", ", "{ ", " }")
                                }
                            }"
                        }.joinToString("\n")
                    )
                )
            }
        }

        return CompletableFuture.completedFuture(textEdits)
    }
}