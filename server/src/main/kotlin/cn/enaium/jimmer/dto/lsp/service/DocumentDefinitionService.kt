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
import cn.enaium.jimmer.dto.lsp.utility.findProjectDir
import cn.enaium.jimmer.dto.lsp.utility.getPackageName
import cn.enaium.jimmer.dto.lsp.utility.overlaps
import cn.enaium.jimmer.dto.lsp.utility.range
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class DocumentDefinitionService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {
    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
        val locations = mutableListOf<Location>()
        val document = documentManager.getDocument(params.textDocument.uri) ?: return CompletableFuture.completedFuture(
            Either.forLeft(locations)
        )

        val ast = document.rightTime.ast

        ast.exportStatement()?.also { exportStatement ->
            val typeParts = exportStatement.typeParts
            if (typeParts.isNotEmpty()) {
                if (typeParts.last().range().overlaps(params.position)) {
                    findProjectDir(URI.create(params.textDocument.uri).toPath())?.also { projectDir ->
                        listOf("main", "test").forEach { source ->
                            listOf("java" to "java", "kotlin" to "kt").forEach { language ->
                                val path =
                                    projectDir.resolve("src/${source}/${language.first}/${typeParts.joinToString("/") { it.text }}.${language.second}")
                                if (path.exists()) {
                                    locations.add(
                                        Location(
                                            path.toUri().toString(),
                                            Range(Position(0, 0), Position(0, 0))
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ast.dtoTypes.forEach { dtoType ->
            if (dtoType.name.range().overlaps(params.position)) {
                val packageName = ast.exportStatement()?.getPackageName()
                    ?: document.rightTime.immutable?.packageName?.let { "$it.dto" }
                    ?: return@forEach

                findProjectDir(URI.create(params.textDocument.uri).toPath())?.also { projectDir ->
                    listOf("build", "target").forEach { out ->
                        projectDir.resolve("$out/generated/ksp").toFile().listFiles()?.forEach { path ->
                            !path.isDirectory && return@forEach
                            val source =
                                path.resolve(
                                    "kotlin/${packageName.replace(".", "/")}/${dtoType.name.text}.kt"
                                )
                            if (source.exists()) {
                                locations.add(
                                    Location(
                                        source.toURI().toString(),
                                        Range(Position(0, 0), Position(0, 0))
                                    )
                                )
                            }
                        }
                        projectDir.resolve(
                            if (out == "build") {
                                "$out/generated/sources/annotationProcessor/java"
                            } else {
                                "$out/generated-sources/annotations"
                            }
                        ).toFile().listFiles()
                            ?.forEach { path ->
                                !path.isDirectory && return@forEach
                                val source =
                                    path.resolve("${packageName.replace(".", "/")}/${dtoType.name.text}.java")
                                if (source.exists()) {
                                    locations.add(
                                        Location(
                                            source.toURI().toString(),
                                            Range(Position(0, 0), Position(0, 0))
                                        )
                                    )
                                }
                            }
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(Either.forLeft(locations))
    }
}