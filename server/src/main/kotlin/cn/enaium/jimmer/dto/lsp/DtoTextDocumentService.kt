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
import cn.enaium.jimmer.dto.lsp.compiler.Context
import cn.enaium.jimmer.dto.lsp.compiler.DocumentDtoCompiler
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableType
import cn.enaium.jimmer.dto.lsp.compiler.get
import org.antlr.v4.runtime.*
import org.babyfish.jimmer.dto.compiler.*
import org.babyfish.jimmer.dto.compiler.DtoParser.DtoContext
import org.eclipse.lsp4j.*
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
        documentManager.openOrUpdateDocument(
            params.textDocument.uri,
            DtoDocument(content, validate(content, params.textDocument.uri))
        )
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val content = params.contentChanges[0].text
        content.isBlank() && return
        documentManager.openOrUpdateDocument(
            params.textDocument.uri,
            DtoDocument(content, validate(content, params.textDocument.uri))
        )
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        documentManager.closeDocument(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        documentManager.getDocument(params.textDocument.uri)?.content?.also {
            validate(it, params.textDocument.uri)
        }
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        return CompletableFuture.completedFuture(semanticTokens(params.textDocument.uri))
    }

    private fun Token.range(): Range {
        return Range(
            Position(line - 1, charPositionInLine),
            Position(line - 1 + text.count { it == '\n' }, text.length - text.lastIndexOf('\n') - 1)
        )
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

    private fun validate(content: String, uri: String): DtoContext {
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
                documentDtoCompiler.compile(immutableType)
            } ?: run {
                client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                    this.uri = uri
                    diagnostics = listOf(Diagnostic().apply {
                        range = Range(Position(0, 0), Position(0, 1))
                        severity = DiagnosticSeverity.Error
                        message = "No immutable type '${documentDtoCompiler.sourceTypeName}'"
                    })
                })
                return ast
            }
        } catch (dtoAst: DtoAstException) {
            val colNumber = dtoAst.javaClass.getDeclaredField("colNumber").also {
                it.isAccessible = true
            }.getInt(dtoAst)
            client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                this.uri = uri
                diagnostics = listOf(Diagnostic().apply {
                    range = Range(
                        Position(dtoAst.lineNumber - 1, colNumber),
                        Position(dtoAst.lineNumber - 1, colNumber + 1)
                    )
                    severity = DiagnosticSeverity.Error
                    message = dtoAst.message
                })
            })
            return ast
        }

        if (parser.numberOfSyntaxErrors == 0) {
            client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                this.uri = uri
                diagnostics = emptyList()
            })
        }
        return ast
    }

    private fun Token.isDtoModifier(): Boolean {
        return DtoModifier.entries.map { it.name.lowercase() }.contains(text)
    }
}