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
import cn.enaium.jimmer.dto.lsp.compiler.Context
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableProp
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableType
import cn.enaium.jimmer.dto.lsp.compiler.get
import org.antlr.v4.runtime.Token
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.dto.compiler.DtoParser
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.MappedSuperclass
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.collections.set
import kotlin.io.path.toPath

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
                    Either.forLeft(listOf(CompletionItem("DocComment").apply {
                        insertText = "\n * $0 \n */"
                        kind = CompletionItemKind.Text
                        insertTextFormat = InsertTextFormat.Snippet
                    }))
                )
            }

            "@" -> run {
                var sort = 0
                val annotationNames = findAnnotationNames(document.context, document.classpath)
                return CompletableFuture.completedFuture(
                    Either.forLeft(annotationNames.map {
                        CompletionItem(it.let {
                            if (it.contains(".")) {
                                it.substringAfterLast(".")
                            } else {
                                it
                            }
                        }).apply {
                            kind = CompletionItemKind.Class
                            sortText = "${sort++}"
                            val sortedImportStatements =
                                document.ast.importStatements.sortedWith { o1, o2 -> o2.Identifier.line - o1.Identifier.line }
                            val exportStatement = document.ast.exportStatement()
                            var importLine = if (exportStatement != null) {
                                val exportLine =
                                    if (exportStatement.packageParts.isNotEmpty()) {
                                        exportStatement.packageParts.last().line
                                    } else if (exportStatement.typeParts.isNotEmpty()) {
                                        exportStatement.typeParts.last().line
                                    } else {
                                        exportStatement.Identifier.line
                                    }
                                Range(Position(exportLine, 0), Position(exportLine, 0))
                            } else {
                                Range(Position(0, 0), Position(0, 0))
                            }
                            if (sortedImportStatements.isNotEmpty()) {
                                val lastImportLine = sortedImportStatements.first().Identifier.line
                                importLine = Range(Position(lastImportLine, 0), Position(lastImportLine, 0))
                            }

                            if (sortedImportStatements.none { importStatement -> importStatement.parts.joinToString(".") { token -> token.text } == it }) {
                                additionalTextEdits = listOf(
                                    TextEdit(importLine, "import $it\n")
                                )
                            }
                        }
                    })
                )
            }

            null -> run {
                var sort = 0

                val completionItems = mutableListOf<CompletionItem>()

                val tokens = document.commonToken.tokens
                fun completionClass(keyword: String, names: List<String>) {
                    val currentLineTokens = tokens.filter { it.line - 1 == params.position.line }
                    if (currentLineTokens.size > 1 && currentLineTokens.first()?.text == keyword) {
                        val classTokens =
                            currentLineTokens.filterIndexed { index, _ -> index != 0 }
                        val className = classTokens.joinToString("") { it.text }
                        names.forEach {
                            if (it.startsWith(className)) {
                                completionItems.add(CompletionItem(it).apply {
                                    insertText = it.substring(className.length - classTokens.last().text.length)
                                    kind = CompletionItemKind.Class
                                    sortText = "${sort++}"
                                })
                            }
                        }
                    }
                }

                completionClass("export", findImmutableNames(document.context, document.classpath))
                completionClass("import", findClassNames(document.classpath) + findAnnotationNames(document.context))

                val callTraceToRange = mutableMapOf<String, Pair<Token, Token>>()
                val callTraceToProps = mutableMapOf<String, List<ImmutableProp>>()

                document.ast.dtoTypes.forEach { dtoType ->
                    if (dtoType.name == null) return@forEach
                    val bodyContext = dtoType.dtoBody() ?: return@forEach
                    getBodyRange(bodyContext, dtoType.name.text, callTraceToRange)
                }

                document.dtoTypes.forEach { dtoType ->
                    if (dtoType.name == null) return@forEach
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
                    DtoModifier.entries.map { it.name.lowercase() } + listOf("import")
                }.map {
                    CompletionItem(it).apply {
                        kind = CompletionItemKind.Keyword
                        sortText = "${sort++}"
                    }
                }

                if (!isInBlock) {
                    if (tokens.none { it.text == "export" }) {
                        completionItems.add(CompletionItem("export").apply {
                            kind = CompletionItemKind.Keyword
                            sortText = "${sort++}"
                        })
                    }

                    if (tokens.none { it.text == "package" }) {
                        completionItems.add(CompletionItem("package").apply {
                            kind = CompletionItemKind.Keyword
                            sortText = "${sort++}"
                        })
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

    private fun findImmutableNames(context: Context, classpath: List<Path>): List<String> {
        val results = mutableListOf<String>()
        findClassNames(classpath).forEach { name ->
            context.loader[name]?.run {
                if (this.annotations.any {
                        listOf(
                            Entity::class,
                            MappedSuperclass::class,
                            Embeddable::class,
                            Immutable::class
                        ).contains(it.annotationClass)
                    }) {
                    results.add(name)
                }
            }
        }
        return results
    }

    private fun findAnnotationNames(context: Context, classpath: List<Path> = emptyList()): List<String> {
        val results = mutableListOf<String>()
        findClassNames(
            listOf(
                Main::class.java.protectionDomain.codeSource.location.toURI().toPath()
            ) + classpath
        ).forEach { name ->
            context.loader[name]?.run {
                if (this.isAnnotation) {
                    results.add(name)
                }
            }
        }
        return results
    }
}