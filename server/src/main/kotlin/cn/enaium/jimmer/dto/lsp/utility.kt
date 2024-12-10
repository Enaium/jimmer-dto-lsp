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

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * @author Enaium
 */
private val classpathDirs = listOf(
    "build/classes/kotlin/main",
    "build/classes/kotlin/test",
    "build/classes/java/main",
    "build/classes/java/test",
    "build/tmp/kotlin-classes/debug",
    "build/intermediates/javac/debug/classes",
    "build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
)

private val noClassPathDirNames = listOf("node_modules", "src")

fun findClasspath(path: Path, results: MutableList<Path>) {
    path.toFile().listFiles()?.forEach {
        val file = it.toPath()
        if (!file.isDirectory() || file.name.startsWith(".") || noClassPathDirNames.contains(file.name)) {
            return@forEach
        }

        var ok = false
        for (classpathDir in classpathDirs) {
            val splitClasspath = classpathDir.split("/").reversed()
            if (splitClasspath[0] == file.name) {
                var parent: Path? = file.parent
                var okCount = 0
                for (i in 1..<splitClasspath.size) {
                    if (parent?.name == splitClasspath[i]) {
                        parent = parent.parent
                        okCount++
                    }
                }
                if (okCount == splitClasspath.size - 1) {
                    ok = true
                }
            }
        }

        if (!ok) {
            findClasspath(file, results)
        } else {
            results.add(file)
        }
    }
}