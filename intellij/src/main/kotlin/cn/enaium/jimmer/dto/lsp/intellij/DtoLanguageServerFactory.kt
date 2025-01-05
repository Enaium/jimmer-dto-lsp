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

package cn.enaium.jimmer.dto.lsp.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.util.io.delete
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.server.JavaProcessCommandBuilder
import com.redhat.devtools.lsp4ij.server.OSProcessStreamConnectionProvider
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.path.*

/**
 * @author Enaium
 */
class DtoLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        val dir = Path(System.getProperty("user.home")).resolve(Constants.HOME)
        if (!dir.exists()) {
            dir.createDirectories()
        }
        val localJarFile = dir.resolve("server.jar")
        try {
            if (localJarFile.exists()) {
                localJarFile.delete()
            }
            object {}::class.java.classLoader.getResourceAsStream("server.jar")
                ?.use { inputStream: InputStream ->
                    localJarFile.outputStream().use { outputStream: OutputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
        } catch (_: Exception) {
        }

        if (!localJarFile.exists()) {
            throw RuntimeException("Local server jar not found")
        }

        return OSProcessStreamConnectionProvider(
            GeneralCommandLine(
                JavaProcessCommandBuilder(
                    project,
                    Constants.SERVER_ID,
                ).setCp(localJarFile.absolutePathString()).create() + "cn.enaium.jimmer.dto.lsp.MainKt"
            )
        )
    }
}