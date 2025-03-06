/*
 * Copyright 2025 Enaium
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
import cn.enaium.jimmer.dto.lsp.utility.range
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
class DocumentCodeLensService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {
    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> {
        val document = documentManager.getDocument(params.textDocument.uri) ?: return CompletableFuture.completedFuture(
            emptyList()
        )

        val codeLens = mutableListOf<CodeLens>()

        val ast = document.rightTime.ast

        ast.dtoTypes.forEach { dtoType ->
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
                            codeLens.add(
                                CodeLens(
                                    dtoType.name.range(),
                                    Command("Generated", ""),
                                    null
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
                                codeLens.add(
                                    CodeLens(
                                        dtoType.name.range(),
                                        Command("Generated", ""),
                                        null
                                    )
                                )
                            }
                        }
                }
            }
        }
        return CompletableFuture.completedFuture(codeLens)
    }
}