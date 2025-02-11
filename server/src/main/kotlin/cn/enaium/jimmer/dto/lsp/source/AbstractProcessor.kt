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

package cn.enaium.jimmer.dto.lsp.source

import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.*

/**
 * @author Enaium
 */
abstract class AbstractProcessor(val paths: List<Path>) {

    val needProcessedTokens = listOf(
        "Immutable",
        "Entity",
        "Embeddable",
        "MappedSuperclass",
        "enum"
    )

    abstract fun process(): List<Source>

    fun getSourceFiles(suffix: String): List<Pair<Path, InputStream>> {
        val sourceFiles = paths.filter { it.exists() }.mapNotNull {
            if (it.isDirectory()) {
                it.walk().filter { it.extension == suffix }.toList()
            } else if (it.extension == suffix) {
                listOf(it)
            } else {
                null
            }
        }.flatten().map { it to it.inputStream() }.toMutableList()

        paths.forEach {
            if (it.extension == "jar") {
                val jarFile = JarFile(it.toFile())
                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.endsWith(".$suffix")) {
                        sourceFiles += it to jarFile.getInputStream(entry)
                    }
                }
            }
        }
        return sourceFiles
    }
}