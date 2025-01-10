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
import cn.enaium.jimmer.dto.lsp.compiler.Context
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableProp
import cn.enaium.jimmer.dto.lsp.compiler.get
import cn.enaium.jimmer.dto.lsp.utility.*
import org.antlr.v4.runtime.Token
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.dto.compiler.DtoLexer
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.MappedSuperclass
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentCompletionService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {
    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        val document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(Either.forLeft(emptyList()))

        val commentRange = getCommentRange(document.realTime.commonToken.tokens)

        if (commentRange.any { it.overlaps(params.position) }) {
            return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
        }

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
                val annotationNames = findAnnotationNames(document.context, document.realTime.classpath)
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
                            labelDetails = CompletionItemLabelDetails().apply {
                                detail = " (from $it)"
                            }
                            val sortedImportStatements =
                                document.realTime.ast.importStatements.sortedWith { o1, o2 -> o2.Identifier.line - o1.Identifier.line }
                            val exportStatement = document.realTime.ast.exportStatement()
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

                            if (sortedImportStatements.none { importStatement ->
                                    val joinToString = importStatement.parts.joinToString(".") { token -> token.text }
                                    joinToString == it || (it.startsWith(joinToString) && importStatement.importedTypes.map { type -> type.text }
                                        .contains(it.substring(joinToString.length + 1)))
                                }) {
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

                val rightTimeTokens =
                    document.rightTime.commonToken.tokens.filter { it.channel == DtoLexer.DEFAULT_TOKEN_CHANNEL }
                val realTimeTokens =
                    document.realTime.commonToken.tokens.filter { it.channel == DtoLexer.DEFAULT_TOKEN_CHANNEL }

                fun completionClass(keyword: String, names: () -> List<String>) {
                    val currentLineTokens =
                        realTimeTokens.filter { it.line - 1 == params.position.line && it.type != DtoLexer.EOF }
                    if (currentLineTokens.size > 1 && currentLineTokens.first()?.text == keyword) {
                        val classTokens =
                            currentLineTokens.filterIndexed { index, _ -> index != 0 }
                        val className = classTokens.joinToString("") { it.text }
                        names().forEach {
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

                completionClass(
                    "export"
                ) { findImmutableNames(document.context, document.realTime.classpath) }
                completionClass(
                    "import"
                ) { document.context.findClassNames(document.realTime.classpath) + findAnnotationNames(document.context) }

                val callTraceToRange = mutableMapOf<String, Pair<Token, Token>>()
                val callTraceToProps = mutableMapOf<String, List<ImmutableProp>>()

                document.rightTime.ast.dtoTypes.forEach { dtoType ->
                    if (dtoType.name == null) return@forEach
                    val bodyContext = dtoType.dtoBody() ?: return@forEach
                    getBodyRange(bodyContext, dtoType.name.text, callTraceToRange)
                }

                document.rightTime.dtoTypes.forEach { dtoType ->
                    if (dtoType.name == null) return@forEach
                    getProps(dtoType.baseType, "${dtoType.name}", callTraceToProps)
                }

                val props = document.getProps(params.position)

                val isInBlock = props != null

                if (isInBlock) {
                    completionItems += props.second.map { prop ->
                        CompletionItem(prop.name).apply {
                            kind = CompletionItemKind.Field
                            val type = prop.type()
                            labelDetails = CompletionItemLabelDetails().apply {
                                detail = "(from ${prop.declaringType.name})"
                                description = type.description
                            }

                            getParenthesisRange(realTimeTokens, params.position) ?: run {
                                if (type == PropType.ASSOCIATION) {
                                    insertText = "${prop.name} { \n\t$0\n}"
                                    insertTextFormat = InsertTextFormat.Snippet
                                } else if (type == PropType.RECURSIVE) {
                                    insertText = "${prop.name}*"
                                }
                            }
                            sortText = "${sort++}"
                        }
                    }
                }

                val isInSpecificationBlock =
                    document.rightTime.dtoTypes.find { props?.first?.startsWith("${it.name}") == true }?.modifiers?.contains(
                        DtoModifier.SPECIFICATION
                    ) == true

                if (isInBlock) {
                    completionItems += listOf("allScalars", "allReferences").map { name ->
                        CompletionItem(name).apply {
                            kind = CompletionItemKind.Function
                            labelDetails = CompletionItemLabelDetails().apply {
                                description = "macro"
                            }
                            insertText = "#$name"
                            insertTextFormat = InsertTextFormat.Snippet
                            sortText = "${sort++}"
                        }
                    }
                }

                completionItems += (if (isInSpecificationBlock) {
                    qbeFuncNames
                } else if (isInBlock) {
                    normalFuncNames
                } else {
                    emptyList()
                }).map {
                    CompletionItem(it).apply {
                        kind = CompletionItemKind.Method
                        labelDetails = CompletionItemLabelDetails().apply {
                            description = "function"
                        }

                        insertText = when (it) {
                            "id" -> {
                                "$it($1) as $0"
                            }

                            "flat" -> {
                                "$it($1) { \n\t$0\n}"
                            }

                            else -> {
                                "$it($0)"
                            }
                        }

                        insertTextFormat = InsertTextFormat.Snippet
                        sortText = "${sort++}"
                    }
                }

                completionItems += (listOf(
                    "as",
                    "implements",
                    "class"
                ) + DtoModifier.entries.map { it.name.lowercase() }).map {
                    CompletionItem(it).apply {
                        kind = CompletionItemKind.Keyword
                        sortText = "${sort++}"
                    }
                }

                if (!isInBlock) {
                    if (rightTimeTokens.none { it.text == "export" }) {
                        completionItems.add(CompletionItem("export").apply {
                            kind = CompletionItemKind.Keyword
                            sortText = "${sort++}"
                        })
                    }

                    if (rightTimeTokens.none { it.text == "package" }) {
                        completionItems.add(CompletionItem("package").apply {
                            kind = CompletionItemKind.Keyword
                            sortText = "${sort++}"
                        })
                    }

                    completionItems.add(CompletionItem("import").apply {
                        kind = CompletionItemKind.Keyword
                        sortText = "${sort++}"
                    })
                }
                return CompletableFuture.completedFuture(Either.forLeft(completionItems))
            }
        }
        return CompletableFuture.completedFuture(Either.forLeft(emptyList()))
    }

    private fun findImmutableNames(context: Context, classpath: List<Path>): List<String> {
        val results = mutableListOf<String>()
        context.findClassNames(classpath).forEach { name ->
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
        context.findClassNames(classpath).forEach { name ->
            context.loader[name]?.run {
                if (this.isAnnotation) {
                    results.add(name)
                }
            }
        }
        return results
    }

    private fun getCommentRange(tokens: List<Token>): List<Range> {
        val ranges = mutableListOf<Range>()
        tokens.forEach { token ->
            when (token.type) {
                DtoLexer.DocComment, DtoLexer.BlockComment, DtoLexer.LineComment -> {
                    ranges.add(token.range())
                }
            }
        }
        return ranges
    }

    private fun getParenthesisRange(tokens: List<Token>, position: Position): Range? {
        val token = tokens.find { it.line - 1 == position.line && it.charPositionInLine == position.character }
        if (token == null) {
            return null
        }
        return when (token.type) {
            TokenType.LEFT_PARENTHESIS.id -> {
                val rightParenthesis = tokens.find {
                    it.line == token.line && it.charPositionInLine > token.charPositionInLine
                } ?: return null
                Range(token.position(), rightParenthesis.position())
            }

            TokenType.RIGHT_PARENTHESIS.id -> {
                val leftParenthesis = tokens.find {
                    it.line == token.line && it.charPositionInLine < token.charPositionInLine
                } ?: return null
                Range(leftParenthesis.position(), token.position())
            }

            else -> null
        }
    }
}