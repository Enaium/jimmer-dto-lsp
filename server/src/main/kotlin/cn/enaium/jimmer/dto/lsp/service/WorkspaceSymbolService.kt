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

import cn.enaium.jimmer.dto.lsp.Workspace
import cn.enaium.jimmer.dto.lsp.utility.findSubprojects
import cn.enaium.jimmer.dto.lsp.utility.range
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.babyfish.jimmer.dto.compiler.DtoLexer
import org.babyfish.jimmer.dto.compiler.DtoParser
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.*

/**
 * @author Enaium
 */
class WorkspaceSymbolService(val workspace: Workspace) : WorkspaceServiceAdapter(workspace) {

    val workspaceSymbols = mutableListOf<WorkspaceSymbol>()

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<List<SymbolInformation>, List<WorkspaceSymbol>>> {
        workspaceSymbols.clear()
        workspace.folders.forEach { folder ->
            findSubprojects(URI.create(folder).toPath()).forEach { projectDir ->
                listOf("main", "test").forEach { source ->
                    val dtoSource = projectDir.resolve("src/$source/dto")
                    if (dtoSource.exists()) {
                        dtoSource.walk().filter { it.extension == "dto" }.forEach { dtoPath ->
                            try {
                                val lexer = DtoLexer(CharStreams.fromString(dtoPath.readText()))
                                val tokenStream = CommonTokenStream(lexer)
                                val parser = DtoParser(tokenStream)
                                val dto = parser.dto()

                                dto.dtoTypes.forEach { dtoType ->
                                    val name = dtoType.name.text

                                    if (name.contains(params.query)) {
                                        workspaceSymbols.add(
                                            WorkspaceSymbol(
                                                name,
                                                SymbolKind.Class,
                                                Either.forLeft(
                                                    Location(
                                                        dtoPath.toUri().toString(),
                                                        dtoType.name.range()
                                                    )
                                                )
                                            )
                                        )
                                    }

                                    dtoType.dtoBody()?.also { dtoBody ->
                                        body(dtoPath, dtoBody)
                                    }
                                }
                            } catch (_: Exception) {

                            }
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(Either.forRight(workspaceSymbols))
    }

    private fun positiveProp(dtoPath: Path, positiveProp: DtoParser.PositivePropContext) {
        positiveProp.props.forEach {
            workspaceSymbols.add(
                WorkspaceSymbol(
                    it.text,
                    SymbolKind.Field,
                    Either.forLeft(
                        Location(
                            dtoPath.toUri().toString(),
                            it.range()
                        )
                    )
                )
            )
        }

        positiveProp.dtoBody()?.also { dtoBody ->
            body(dtoPath, dtoBody)
        }
    }

    private fun body(dtoPath: Path, body: DtoParser.DtoBodyContext) {
        body.explicitProps.forEach { prop ->
            prop.positiveProp()?.also { positiveProp ->
                positiveProp(dtoPath, positiveProp)
            }
        }
    }
}