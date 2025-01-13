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
import cn.enaium.jimmer.dto.lsp.compiler.DocumentDtoCompiler
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableType
import cn.enaium.jimmer.dto.lsp.utility.findClasspath
import cn.enaium.jimmer.dto.lsp.utility.findDependenciesByFile
import cn.enaium.jimmer.dto.lsp.utility.findProjectDir
import cn.enaium.jimmer.dto.lsp.utility.toFile
import org.antlr.v4.runtime.*
import org.babyfish.jimmer.dto.compiler.*
import org.eclipse.lsp4j.*
import java.io.Reader
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class DocumentSyncService(private val workspace: Workspace, documentManager: DocumentManager) :
    DocumentServiceAdapter(documentManager) {
    override fun didOpen(params: DidOpenTextDocumentParams) {
        val content = params.textDocument.text
        content.isBlank() && return
        validate(content, params.textDocument.uri, Type.OPEN)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val content = params.contentChanges[0].text
        content.isBlank() && return
        validate(content, params.textDocument.uri, Type.CHANGE)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        documentManager.closeDocument(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        val content = params.text
        content.isBlank() && return
        validate(content, params.textDocument.uri, Type.SAVE)
    }

    private fun validate(content: String, uri: String, type: Type) {
        val path = URI.create(uri).toPath()
        val projectDir = findProjectDir(path)
        val classpath = mutableListOf<Path>()

        projectDir?.also {
            classpath += (findClasspath(it)
                    + findDependenciesByFile(it)
                    + workspace.dependencies.getOrDefault(projectDir.name, emptyList()))
        } ?: run {
            workspace.folders.forEach workspaceFolder@{ workspaceFolder ->
                val wf = URI.create(workspaceFolder).toPath()
                classpath += findClasspath(wf) + findDependenciesByFile(wf)
            }
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

        documentManager.openOrUpdateDocument(
            uri,
            documentManager.getDocument(uri)
                ?.copy(
                    content = content,
                    realTime = DocumentContext(ast, lexer, token, classpath = classpath),
                )
                ?: DtoDocument(
                    content,
                    context,
                    DocumentContext(ast, lexer, token, classpath = classpath),
                    DocumentContext(ast, lexer, token, classpath = classpath)
                )
        )

        try {
            val documentDtoCompiler =
                DocumentDtoCompiler(DtoFile(object : OsFile {
                    override fun getAbsolutePath(): String {
                        return URI.create(uri).toFile().absolutePath
                    }

                    override fun openReader(): Reader {
                        return content.reader()
                    }
                }, "", "", emptyList(), URI.create(uri).toFile().name))
            context.findImmutableClass(projectDir, path, documentDtoCompiler.sourceTypeName)?.run {
                val immutableType = ImmutableType(context, this)
                val compile = documentDtoCompiler.compile(immutableType)
                client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                    this.uri = uri
                    diagnostics = emptyList()
                })
                documentManager.openOrUpdateDocument(
                    uri,
                    DtoDocument(
                        content,
                        context,
                        DocumentContext(ast, lexer, token, immutableType, classpath, compile),
                        DocumentContext(ast, lexer, token, immutableType, classpath, compile)
                    )
                )
            } ?: run {
                client?.publishDiagnostics(PublishDiagnosticsParams().apply {
                    this.uri = uri
                    diagnostics = listOf(Diagnostic().apply {
                        range = Range(Position(0, 0), Position(0, 1))
                        severity = DiagnosticSeverity.Error
                        message =
                            "No immutable type '${documentDtoCompiler.sourceTypeName}' found. Please build the project or use the export statement."
                    })
                })
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
                documentManager.getDocument(uri)
                    ?.copy(
                        content = content,
                        realTime = DocumentContext(ast, lexer, token, classpath = classpath),
                    ) ?: DtoDocument(
                    content,
                    context,
                    DocumentContext(ast, lexer, token, classpath = classpath),
                    DocumentContext(ast, lexer, token, classpath = classpath)
                )
            )
        }
    }

    private enum class Type {
        OPEN, CHANGE, CLOSE, SAVE
    }
}