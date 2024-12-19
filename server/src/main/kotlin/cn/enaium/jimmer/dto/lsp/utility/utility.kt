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

package cn.enaium.jimmer.dto.lsp.utility

import org.antlr.v4.runtime.Token
import org.babyfish.jimmer.dto.compiler.Constants
import org.babyfish.jimmer.dto.compiler.DtoLexer
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.*

/**
 * @author Enaium
 */
private val classpathDirs = listOf(
    "build/classes/kotlin/main",
    "build/classes/kotlin/test",
    "build/classes/java/main",
    "build/classes/java/test",
    "target/classes",
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

fun findProjectDir(dtoPath: Path): Path? {
    var parent = dtoPath.parent
    while (parent != null) {
        val main = parent
        val src = parent.parent?.name
        val project = parent.parent?.parent
        if (listOf("main", "test").contains(main.name) && src == "src") {
            return project
        }
        parent = parent.parent
    }
    return null
}

fun findClassNames(classpath: List<Path>): List<String> {
    val results = mutableListOf<String>()
    classpath.forEach { cp ->
        if (cp.isDirectory()) {
            cp.walk().filter { it.extension == "class" && !it.name.contains("$") }.forEach { classFile ->
                val name = classFile.relativeTo(cp).pathString.substringBeforeLast(".").replace(File.separator, ".")
                results.add(name)
            }
        } else if (cp.extension == "jar") {
            val jarFile = cp.toFile()
            val jar = JarFile(jarFile)
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                if (entry.name.endsWith(".class") && !entry
                        .name.contains("$")
                ) {
                    val name = entry.name.substringBeforeLast(".").replace("/", ".")
                    results.add(name)
                }
            }
            jar.close()
        }
    }
    return results
}

fun URI.toFile(): File {
    return File(this)
}

fun Token.range(): Range {
    return Range(
        Position(line - 1, charPositionInLine),
        Position(line - 1 + text.count { it == '\n' }, text.length - text.lastIndexOf('\n') - 1)
    )
}

fun Token.position(textLength: Boolean = false): Position {
    return Position(line - 1, charPositionInLine.let { if (textLength) it + text.length else it })
}

fun Range.overlaps(position: Position): Boolean {
    return start.line < position.line && end.line > position.line
}

fun Token.literal(): String? {
    return DtoLexer.VOCABULARY.getLiteralName(this.type)
}

val commonFuncNames = setOf("flat")
val qbeFuncNames = Constants.QBE_FUNC_NAMES + commonFuncNames
val normalFuncNames = setOf("id") + commonFuncNames