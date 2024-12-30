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
import cn.enaium.jimmer.dto.lsp.utility.findDependenciesByCommand
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
data class Workspace(
    val folders: MutableList<String> = mutableListOf(),
    val dependencies: MutableMap<String, List<Path>> = mutableMapOf()
) {
    fun resolveDependencies() {
        val token = "Resolve Dependencies"
        client?.createProgress(WorkDoneProgressCreateParams(Either.forLeft(token)))
        client?.notifyProgress(ProgressParams(Either.forLeft(token), Either.forLeft(WorkDoneProgressBegin().apply {
            title = "$token in progress"
            cancellable = false
        })))
        try {
            CompletableFuture.supplyAsync {
                folders.forEach {
                    dependencies += findDependenciesByCommand(URI.create(it).toPath())
                }
                client?.notifyProgress(
                    ProgressParams(
                        Either.forLeft(token),
                        Either.forLeft(WorkDoneProgressEnd().apply {
                            message = "$token done"
                        })
                    )
                )
            }.get(10, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            client?.showMessage(MessageParams().apply {
                message = "Resolve Dependencies timeout, please resolve dependencies manually"
                type = MessageType.Error
            })
        }
    }
}