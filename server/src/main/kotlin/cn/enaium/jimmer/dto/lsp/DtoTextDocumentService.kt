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

import cn.enaium.jimmer.dto.lsp.Main.client
import cn.enaium.jimmer.dto.lsp.Main.logger
import cn.enaium.jimmer.dto.lsp.compiler.*
import org.antlr.v4.runtime.*
import org.babyfish.jimmer.dto.compiler.*
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import java.io.Reader
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.toPath


class DtoTextDocumentService(private val workspaceFolders: MutableSet<String>) : TextDocumentService {
    private val documentManager = DocumentManager()

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val content = params.textDocument.text
        content.isBlank() && return
        validate(content, params.textDocument.uri)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val content = params.contentChanges[0].text
        content.isBlank() && return
        validate(content, params.textDocument.uri)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        documentManager.closeDocument(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        val content = params.text
        content.isBlank() && return
        validate(content, params.textDocument.uri)
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        return CompletableFuture.completedFuture(semanticTokens(params.textDocument.uri))
    }

    override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<List<FoldingRange>> {
        val ranges = mutableListOf<FoldingRange>()
        val document =
            documentManager.getDocument(params.textDocument.uri) ?: return CompletableFuture.completedFuture(ranges)
        return CompletableFuture.completedFuture(getBodyRange(document.commonToken.tokens).map {
            FoldingRange(it.start.line, it.end.line - 1).apply {
                kind = FoldingRangeKind.Region
            }
        })
    }

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

    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> {
        val document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(mutableListOf())

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

    private fun Token.range(): Range {
        return Range(
            Position(line - 1, charPositionInLine),
            Position(line - 1 + text.count { it == '\n' }, text.length - text.lastIndexOf('\n') - 1)
        )
    }

    private fun Token.position(): Position {
        return Position(line - 1, charPositionInLine)
    }

    private fun Range.overlaps(range: Range): Boolean {
        return start.line < range.start.line && end.line > range.end.line
    }

    private fun Range.overlaps(position: Position): Boolean {
        return start.line < position.line && end.line > position.line
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

    private fun semanticTokens(uri: String): SemanticTokens {
        val document = documentManager.getDocument(uri) ?: return SemanticTokens(emptyList())
        val lexer = DtoLexer(CharStreams.fromString(document.content))
        val commonToken = CommonTokenStream(lexer)
        commonToken.fill()
        val data = mutableListOf<Int>()
        var previousLine = 0
        var previousChar = 0
        val tokens = commonToken.tokens
        tokens.forEachIndexed { tokenIndex, token ->
            when (token.type) {
                DtoLexer.DocComment -> {
                    token.text.split("\n").forEachIndexed { index, s ->
                        val start = token.range().start
                        val currentLine = start.line + index
                        val currentChar = if (index == 0) start.character else 0
                        data.add(currentLine - previousLine)
                        data.add(currentChar)
                        data.add(s.length)
                        data.add(0)
                        data.add(0)
                        previousLine = currentLine
                        previousChar = currentChar
                    }
                }
                // export, package, import, as, fixed, static, dynamic, fuzzy, implements, ?, !, ^, $, *, -, class
                DtoLexer.T__0, DtoLexer.T__3, DtoLexer.T__4, DtoLexer.T__8, DtoLexer.T__9,
                DtoLexer.T__10, DtoLexer.T__11, DtoLexer.T__12, DtoLexer.T__13, DtoLexer.T__18,
                DtoLexer.T__19, DtoLexer.T__20, DtoLexer.T__21, DtoLexer.T__24, DtoLexer.T__25,
                DtoLexer.T__34 -> {
                    val start = token.range().start
                    data.add(start.line - previousLine)
                    data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                    data.add(token.text.length)
                    data.add(1)
                    data.add(0)
                    previousLine = start.line
                    previousChar = start.character
                }
                // #, @
                DtoLexer.T__15, DtoLexer.T__29 -> {
                    val nextToken = tokens.getOrNull(token.tokenIndex + 1)
                    if (nextToken?.type == DtoLexer.Identifier) {
                        val start = token.range().start
                        data.add(start.line - previousLine)
                        data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                        data.add(token.text.length + nextToken.text.length)
                        data.add(if (token.type == DtoLexer.T__29) 2 else 5)
                        data.add(0)
                        previousLine = start.line
                        previousChar = start.character
                    }
                }

                DtoLexer.StringLiteral -> {
                    val start = token.range().start
                    data.add(start.line - previousLine)
                    data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                    data.add(token.text.length)
                    data.add(3)
                    data.add(0)
                    previousLine = start.line
                    previousChar = start.character
                }

                DtoLexer.IntegerLiteral, DtoLexer.FloatingPointLiteral -> {
                    val start = token.range().start
                    data.add(start.line - previousLine)
                    data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                    data.add(token.text.length)
                    data.add(4)
                    data.add(0)
                    previousLine = start.line
                    previousChar = start.character
                }

                DtoLexer.Identifier -> run {
                    tokens.subList(tokenIndex, commonToken.size()).forEach {
                        if (tokens.getOrNull(tokenIndex - 1)?.type != DtoLexer.T__29
                            && it.type == DtoLexer.T__16
                            && (it.range().start.line == token.range().start.line || tokens.getOrNull(tokenIndex + 1)?.type == DtoLexer.T__16)
                        ) {
                            val start = token.range().start
                            data.add(start.line - previousLine)
                            data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                            data.add(token.text.length)
                            data.add(2)
                            data.add(0)
                            previousLine = start.line
                            previousChar = start.character
                            return@run
                        }
                    }

                    document.ast.dtoTypes.forEach { dtoType ->
                        dtoType.modifiers.forEach { modifier ->
                            if (modifier.isDtoModifier() && token.range() == modifier.range()) {
                                val start = token.range().start
                                data.add(start.line - previousLine)
                                data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                                data.add(token.text.length)
                                data.add(1)
                                data.add(0)
                                previousLine = start.line
                                previousChar = start.character
                                return@run
                            }
                        }
                    }
                }
            }
        }
        return SemanticTokens(data)
    }

    private fun validate(content: String, uri: String) {
        val classpath = mutableListOf<Path>()
        workspaceFolders.forEach workspaceFolder@{ workspaceFolder ->
            val path = URI.create(workspaceFolder).toPath()
            findClasspath(path, classpath)
        }
        val context = Context(URLClassLoader(classpath.map { it.toUri().toURL() }.toTypedArray()))
        val lexer = DtoLexer(CharStreams.fromString(content))
        val baseErrorListener = object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>,
                offendingSymbol: Any,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: RecognitionException?
            ) {
                client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                    this.uri = uri
                    diagnostics = listOf(Diagnostic().apply {
                        range =
                            Range(Position(line - 1, charPositionInLine), Position(line - 1, charPositionInLine + 1))
                        severity = DiagnosticSeverity.Error
                        message = msg
                    })
                })
            }
        }
        lexer.removeErrorListeners()
        lexer.addErrorListener(baseErrorListener)
        val token = CommonTokenStream(lexer)
        val parser = DtoParser(token)
        parser.removeErrorListeners()
        parser.addErrorListener(baseErrorListener)
        val ast = parser.dto()

        try {
            val documentDtoCompiler =
                DocumentDtoCompiler(DtoFile(object : OsFile {
                    override fun getAbsolutePath(): String {
                        return URI.create(uri).toPath().toFile().absolutePath
                    }

                    override fun openReader(): Reader {
                        return content.reader()
                    }
                }, "", "", emptyList(), ""))
            context.loader[documentDtoCompiler.sourceTypeName]?.run {
                val immutableType = ImmutableType(context, this)
                val compile = documentDtoCompiler.compile(immutableType)
                client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                    this.uri = uri
                    diagnostics = emptyList()
                })
                documentManager.openOrUpdateDocument(
                    uri,
                    DtoDocument(content, ast, lexer, token, immutableType, compile)
                )
            } ?: run {
                client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                    this.uri = uri
                    diagnostics = listOf(Diagnostic().apply {
                        range = Range(Position(0, 0), Position(0, 1))
                        severity = DiagnosticSeverity.Error
                        message = "No immutable type '${documentDtoCompiler.sourceTypeName}'"
                    })
                })
                documentManager.openOrUpdateDocument(
                    uri,
                    documentManager.getDocument(uri)?.copy(content = content) ?: DtoDocument(content, ast, lexer, token)
                )
            }
        } catch (dtoAst: DtoAstException) {
            client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                this.uri = uri
                diagnostics = listOf(Diagnostic().apply {
                    range = Range(
                        Position(dtoAst.lineNumber - 1, dtoAst.colNumber),
                        Position(dtoAst.lineNumber - 1, dtoAst.colNumber + 1)
                    )
                    severity = DiagnosticSeverity.Error
                    message = dtoAst.message
                })
            })
            documentManager.openOrUpdateDocument(
                uri,
                documentManager.getDocument(uri)?.copy(content = content) ?: DtoDocument(content, ast, lexer, token)
            )
        }
    }

    private fun Token.isDtoModifier(): Boolean {
        return DtoModifier.entries.map { it.name.lowercase() }.contains(text)
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