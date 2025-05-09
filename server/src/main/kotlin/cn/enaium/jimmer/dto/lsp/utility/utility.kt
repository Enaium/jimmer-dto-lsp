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

import cn.enaium.jimmer.dto.lsp.DtoDocument
import cn.enaium.jimmer.dto.lsp.Main
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableProp
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableType
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.babyfish.jimmer.dto.compiler.Constants
import org.babyfish.jimmer.dto.compiler.DtoLexer
import org.babyfish.jimmer.dto.compiler.DtoParser
import org.babyfish.jimmer.dto.compiler.DtoParser.ExportStatementContext
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.*
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

fun findClasspath(path: Path): List<Path> {
    val results = mutableListOf<Path>()

    classpathDirs.forEach {
        val classpathDir = path.resolve(it)
        if (classpathDir.exists()) {
            results.add(classpathDir)
        }
    }

    if (results.isEmpty()) {
        findClasspath(path, results)
    }

    return results
}

private val noClassPathDirNames = listOf("node_modules", "src")

private fun findClasspath(path: Path, results: MutableList<Path>) {
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

fun isProject(path: Path): Boolean {
    return listOf("build.gradle.kts", "build.gradle", "pom.xml", ".git").any { path.resolve(it).exists() }
}

fun findProjectDir(dtoPath: Path, root: Boolean = false): Path? {
    var parent = dtoPath.parent
    var rootPath: Path? = null
    while (parent != null) {
        if (isProject(parent)) {
            if (root) {
                rootPath = parent
            } else {
                return parent
            }
        }
        parent = parent.parent
    }
    return rootPath
}

fun findProjects(rootProject: Path): List<Path> {
    val results = mutableListOf<Path>()
    findProjects(rootProject, results)
    return results
}

private fun findProjects(rootProject: Path, results: MutableList<Path>, level: Int = 0) {
    if (isProject(rootProject)) {
        results.add(rootProject)
    }
    rootProject.toFile().listFiles()?.forEach {
        val file = it.toPath()
        if (file.isDirectory() && isProject(file)) {
            results.add(file)
            if (level < 4)
                findProjects(file, results, level + 1)
        }
    }
}

fun findClassNames(classpath: List<Path>): List<Pair<Path, String>> {
    val results = mutableListOf<Pair<Path, String>>()
    classpath.forEach { cp ->
        if (cp.isDirectory()) {
            cp.walk().filter { it.extension == "class" && !it.name.contains("$") }.forEach { classFile ->
                val name = classFile.relativeTo(cp).pathString.substringBeforeLast(".").replace(File.separator, ".")
                results.add(classFile to name)
            }
        } else if (cp.extension == "jar") {
            val jarFile = cp.toFile()
            val jar = JarFile(jarFile)
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                if (entry.name.endsWith(".class")
                    && !listOf("META-INF", "module-info.class", "$").any { entry.name.contains(it) }
                ) {
                    val name = entry.name.substringBeforeLast(".").replace("/", ".")
                    results.add(cp to name)
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
        Position(
            line - 1 + text.count { it == '\n' },
            text.length - text.lastIndexOf('\n').let { if (it == -1) 0 else it } + charPositionInLine)
    )
}

fun Token.position(textLength: Boolean = false): Position {
    return Position(line - 1, charPositionInLine.let { if (textLength) it + text.length else it })
}

fun Range.overlaps(position: Position): Boolean {
    return if (start.line == position.line && end.line == position.line) {
        start.character <= position.character && end.character >= position.character
    } else if (start.line == position.line) {
        start.character <= position.character
    } else if (end.line == position.line) {
        end.character >= position.character
    } else {
        start.line <= position.line && end.line >= position.line
    }
}

fun Token.literal(): String? {
    return DtoLexer.VOCABULARY.getLiteralName(this.type)
}

fun ImmutableProp.type(): PropType {
    return if (isId) {
        PropType.ID
    } else if (isKey) {
        PropType.KEY
    } else if (isEmbedded) {
        PropType.EMBEDDED
    } else if (isFormula) {
        PropType.FORMULA
    } else if (isTransient) {
        if (hasTransientResolver()) PropType.CALCULATION else PropType.TRANSIENT
    } else if (isRecursive) {
        PropType.RECURSIVE
    } else if (isAssociation(true)) {
        PropType.ASSOCIATION
    } else if (isList) {
        PropType.LIST
    } else if (isLogicalDeleted) {
        PropType.LOGICAL_DELETED
    } else if (isNullable) {
        PropType.NULLABLE
    } else {
        PropType.PROPERTY
    }
}

fun getPropsByTrace(
    immutableType: ImmutableType,
    trace: String
): List<ImmutableProp> {
    if (trace.isEmpty()) {
        return immutableType.properties.values.toList()
    } else {
        val propName = trace.split(".").getOrNull(1) ?: return emptyList()
        val prop = immutableType.properties[propName]?.takeIf { it.isAssociation(false) } ?: return emptyList()
        prop.targetType?.also {
            return getPropsByTrace(it, trace.substring(".${propName}".length, trace.length))
        }
    }
    return emptyList()
}

fun getPropRange(bodyContext: DtoParser.DtoBodyContext): MutableMap<String, Range> {
    val results = mutableMapOf<String, Range>()
    getPropRange(bodyContext, "", results)
    return results
}

private fun getPropRange(
    bodyContext: DtoParser.DtoBodyContext,
    trace: String,
    results: MutableMap<String, Range>
) {
    results[trace] = bodyContext.range()
    for (explicitProp in bodyContext.explicitProps) {
        val positivePropContext = explicitProp.positiveProp() ?: continue
        val dtoBodyContext = positivePropContext.dtoBody() ?: continue
        val text = explicitProp.start.text
        val x = trace + "." + (if (text == "flat") explicitProp.positiveProp().props[0].text else text)
        results[x] = explicitProp.range()
        getPropRange(dtoBodyContext, x, results)
    }
}

fun DtoDocument.getProps(position: Position): Pair<String, List<ImmutableProp>>? {
    val dtoType = rightTime.ast.dtoTypes.find { it.dtoBody()?.range()?.overlaps(position) == true } ?: return null
    dtoType.dtoBody()?.also {
        val trace = getPropRange(it).entries.findLast { it.value.overlaps(position) }?.key ?: return null
        val immutable = this.rightTime.immutable ?: return null
        return trace to getPropsByTrace(immutable, trace)
    }
    return null
}

fun ExportStatementContext.getPackageName(): String {
    return this.packageParts.let {
        if (it.isEmpty() && this.typeParts.size > 1) {
            "${this.typeParts.dropLast(1).joinToString(".") { it.text }}.dto"
        } else {
            it.joinToString(".") { it.text }
        }
    }
}

fun String.toPropName(): String {
    return if (this.startsWith("get")) {
        substring(3).replaceFirstChar { it.lowercase(Locale.getDefault()) }
    } else {
        this
    }
}

fun ParserRuleContext.range(): Range {
    return Range(this.start.position(), this.stop.position())
}

val commonFuncNames = setOf("flat")
val qbeFuncNames = Constants.QBE_FUNC_NAMES + commonFuncNames
val normalFuncNames = setOf("id") + commonFuncNames
val Main.Companion.location: Path
    get() = Main::class.java.protectionDomain.codeSource.location.toURI().toPath()
