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

import cn.enaium.jimmer.dto.lsp.*
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableProp
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableType
import org.antlr.v4.runtime.Token
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoParser
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentCompletionService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {
    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        val document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
        val triggerChar = params.context?.triggerCharacter
        when (triggerChar) {
            "*" -> run {
                val position = params.position
                position.character < 3 && return@run
                val line = document.content.split("\n")[position.line]
                val before3Chars = line.substring(position.character - 3, params.position.character)
                before3Chars != "/**" && return@run
                return CompletableFuture.completedFuture(
                    Either.forLeft(listOf(CompletionItem("BlockComment").apply {
                        insertText = "\n * $0 \n */"
                        kind = CompletionItemKind.Text
                        insertTextFormat = InsertTextFormat.Snippet
                    }))
                )
            }

            null -> run {
                var sort = 0

                val completionItems = mutableListOf<CompletionItem>()
                val callTraceToRange = mutableMapOf<String, Pair<Token, Token>>()
                val callTraceToProps = mutableMapOf<String, List<ImmutableProp>>()

                document.ast.dtoTypes.forEach { dtoType ->
                    getBodyRange(dtoType.dtoBody(), dtoType.name.text, callTraceToRange)
                }

                document.dtoTypes.forEach { dtoType ->
                    getProps(dtoType.baseType, "${dtoType.name}", callTraceToProps)
                }

                val current = callTraceToRange.filter {
                    Range(
                        it.value.first.position(),
                        it.value.second.position()
                    ).overlaps(params.position)
                }.entries.sortedWith { o1, o2 ->
                    o2.value.first.line - o1.value.first.line
                }.firstOrNull()?.let {
                    callTraceToProps[it.key]?.run {
                        completionItems += map { prop ->
                            prop.completeItem(prop.name, sort++)
                        }
                    }
                    it
                }

                val isInBlock = current != null
                val isInSpecificationBlock =
                    document.dtoTypes.find { current?.key?.startsWith("${it.name}") == true }?.modifiers?.contains(
                        DtoModifier.SPECIFICATION
                    ) == true

                completionItems.addAll(listOf("allScalars", "allReferences").map { name ->
                    CompletionItem(name).apply {
                        kind = CompletionItemKind.Function
                        labelDetails = CompletionItemLabelDetails().apply {
                            description = "macro"
                        }
                        insertText = "#$name"
                        insertTextFormat = InsertTextFormat.Snippet
                        sortText = "${sort++}"
                    }
                })

                completionItems += (if (isInSpecificationBlock) qbeFuncNames else normalFuncNames).map {
                    CompletionItem(it).apply {
                        kind = CompletionItemKind.Method
                        labelDetails = CompletionItemLabelDetails().apply {
                            description = "function"
                        }
                        insertText = "$it($0)"
                        insertTextFormat = InsertTextFormat.Snippet
                        sortText = "${sort++}"
                    }
                }

                completionItems += if (isInBlock) {
                    listOf("as", "implements", "class")
                } else {
                    DtoModifier.entries.map { it.name.lowercase() } + listOf("export", "package", "import")
                }.map {
                    CompletionItem(it).apply {
                        kind = CompletionItemKind.Keyword
                        sortText = "${sort++}"
                    }
                }
                return CompletableFuture.completedFuture(Either.forLeft(completionItems))
            }
        }
        return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
    }

    private fun ImmutableProp.type(): PropType {
        return if (isId) {
            PropType.ID
        } else if (isKey) {
            PropType.KEY
        } else if (isEmbedded) {
            PropType.EMBEDDED
        } else if (isFormula) {
            PropType.FORMULA
        } else if (isTransient) {
            if (hasTransientResolver()) PropType.CALCULATION else PropType.TRANSIENT
        } else if (isRecursive) {
            PropType.RECURSIVE
        } else if (isAssociation(true)) {
            PropType.ASSOCIATION
        } else if (isList) {
            PropType.LIST
        } else if (isLogicalDeleted) {
            PropType.LOGICAL_DELETED
        } else if (isNullable) {
            PropType.NULLABLE
        } else {
            PropType.PROPERTY
        }
    }

    private fun ImmutableProp.completeItem(name: String, sort: Int): CompletionItem {
        return CompletionItem(name).apply {
            kind = CompletionItemKind.Field
            val type = type()
            labelDetails = CompletionItemLabelDetails().apply {
                description = "from ${declaringType.name} is ${type.description}"
            }

            if (type == PropType.ASSOCIATION) {
                insertText = "$name { \n\t$0\n}"
                insertTextFormat = InsertTextFormat.Snippet
            } else if (type == PropType.RECURSIVE) {
                insertText = "$name*"
            }

            sortText = "$sort"
        }
    }

    private fun getBodyRange(
        bodyContext: DtoParser.DtoBodyContext,
        prefix: String,
        results: MutableMap<String, Pair<Token, Token>>
    ) {
        results[prefix] = bodyContext.start to bodyContext.stop
        for (explicitProp in bodyContext.explicitProps) {
            val positivePropContext = explicitProp.positiveProp() ?: continue
            val dtoBodyContext = positivePropContext.dtoBody() ?: continue
            val text = explicitProp.start.text
            val x = prefix + "." + (if (text == "flat") explicitProp.positiveProp().props[0].text else text)
            results[x] = explicitProp.start to explicitProp.stop
            getBodyRange(dtoBodyContext, x, results)
        }
    }

    private fun getProps(
        immutableType: ImmutableType,
        prefix: String,
        results: MutableMap<String, List<ImmutableProp>>
    ) {
        results[prefix] = immutableType.properties.values.toList()
        for (prop in immutableType.properties.values) {
            if (prop.isAssociation(false) && prefix.count { it == '.' } < 10) {
                prop.targetType?.run {
                    getProps(this, prefix + "." + prop.name, results)
                }
            }
        }
    }
}