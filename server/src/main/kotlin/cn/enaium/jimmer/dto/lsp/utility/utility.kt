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
import cn.enaium.jimmer.dto.lsp.compiler.Context
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableProp
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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

fun findDependenciesByFile(project: Path): MutableList<Path> {
    val results = mutableListOf<Path>()
    val lspHome = Main::class.java.protectionDomain.codeSource.location.toURI().toPath().parent
    lspHome.resolve("dependencies.json").takeIf { it.exists() }?.also {
        val dependenciesJson = ObjectMapper().readTree(it.toFile())
        dependenciesJson[project.absolutePathString().let { projectPath ->
            if (projectPath.first() != '/') {
                projectPath.first().uppercase() + projectPath.substring(1)
            } else {
                projectPath
            }
        }]?.forEach { dependency ->
            Path(dependency.asText()).also { path ->
                if (path.exists()) {
                    results.add(path)
                }
            }
        }
    }
    return results
}

fun findDependenciesByCommand(project: Path): Map<String, List<Path>> {
    val dependencyMap = mutableMapOf<String, List<Path>>()
    val processBuilder = ProcessBuilder()
    processBuilder.directory(project.toFile())
    processBuilder.redirectErrorStream(true)
    if (isGradleProject(project)) {
        processBuilder.command(
            *(if (System.getProperty("os.name").contains("Win")) {
                arrayOf("powershell", "/c") + if (project.resolve("gradlew.bat").exists()) {
                    arrayOf(project.resolve("gradlew.bat").absolutePathString())
                } else {
                    arrayOf("gradle")
                }
            } else {
                arrayOf("bash", "-c") + if (project.resolve("gradlew").exists()) {
                    arrayOf(project.resolve("gradlew").absolutePathString())
                } else {
                    arrayOf("gradle")
                }
            }),
            "--build-cache",
            "allProjectDependencies"
        )
        val start = processBuilder.start()
        start.inputStream.use { input ->
            val readText = input.reader().readText()
            if (!readText.contains("SUCCESS")) {
                return emptyMap()
            }
            readText.lines().find { it.startsWith("{") }?.also { json ->
                jacksonObjectMapper().readValue<Map<String, List<String>>>(json).forEach { (key, value) ->
                    dependencyMap[key] = value.map { Path(it) }
                }
            }
        }
        start.waitFor()
    } else if (isMavenProject(project)) {
        processBuilder.command(
            *(if (System.getProperty("os.name").contains("Win")) {
                arrayOf("powershell", "/c") + if (project.resolve("mvnw.cmd").exists()) {
                    arrayOf(project.resolve("mvnw.cmd").absolutePathString())
                } else {
                    arrayOf("mvn")
                }
            } else {
                arrayOf("bash", "-c") + if (project.resolve("mvnw").exists()) {
                    arrayOf(project.resolve("mvnw").absolutePathString())
                } else {
                    arrayOf("mvn")
                }
            }),
            "dependency:build-classpath",
        )
        val start = processBuilder.start()
        start.inputStream.use { input ->
            val readText = input.reader().readText()
            if (!readText.contains("SUCCESS")) {
                return emptyMap()
            }

            val iterator = readText.lines().iterator()
            while (iterator.hasNext()) {
                val line = iterator.next()
                if (line.contains("@")) {
                    val substring = line.substring(line.indexOf("@") + 1)
                    val name = substring.split(" ")[1]
                    if (iterator.next() == "[INFO] Dependencies classpath:") {
                        val dependencies = iterator.next()
                        dependencyMap[name] = dependencies.split(File.pathSeparator).map { Path(it) }
                    }
                }
            }
        }
    }
    return dependencyMap
}

fun isGradleProject(project: Path): Boolean {
    return listOf("build.gradle.kts", "build.gradle").any { project.resolve(it).exists() }
}

fun isMavenProject(project: Path): Boolean {
    return project.resolve("pom.xml").exists()
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

fun findSubprojects(rootProject: Path): List<Path> {
    val results = mutableListOf<Path>()
    findSubprojects(rootProject, results)
    return results
}

private fun findSubprojects(rootProject: Path, results: MutableList<Path>, level: Int = 0) {
    rootProject.toFile().listFiles()?.forEach {
        val file = it.toPath()
        if (file.isDirectory() && isProject(file)) {
            results.add(file)
            if (level < 4)
                findSubprojects(file, results, level + 1)
        }
    }
}

fun Context.findClassNames(classpath: List<Path>): List<String> {
    if (this.classNames.isNotEmpty()) {
        return this.classNames
    }

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
                if (entry.name.endsWith(".class")
                    && !listOf("META-INF", "module-info.class", "$").any { entry.name.contains(it) }
                ) {
                    val name = entry.name.substringBeforeLast(".").replace("/", ".")
                    results.add(name)
                }
            }
            jar.close()
        }
    }
    return results.also {
        this.classNames = it
    }
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

fun getProps(
    immutableType: ImmutableType,
    prefix: String,
    results: MutableMap<String, List<ImmutableProp>>
) {
    results[prefix] = immutableType.properties.values.toList()
    for (prop in immutableType.properties.values) {
        if (prop.isAssociation(false) && prefix.count { it == '.' } < 10) {
            prop.targetType?.run {
                getProps(this, prefix + "." + prop.name, results)
            }
        }
    }
}

fun getBodyRange(
    bodyContext: DtoParser.DtoBodyContext,
    prefix: String,
    results: MutableMap<String, Pair<Token, Token>>
) {
    results[prefix] = bodyContext.start to bodyContext.stop
    for (explicitProp in bodyContext.explicitProps) {
        val positivePropContext = explicitProp.positiveProp() ?: continue
        val dtoBodyContext = positivePropContext.dtoBody() ?: continue
        val text = explicitProp.start.text
        val x = prefix + "." + (if (text == "flat") explicitProp.positiveProp().props[0].text else text)
        results[x] = explicitProp.start to explicitProp.stop
        getBodyRange(dtoBodyContext, x, results)
    }
}

fun DtoDocument.getProps(position: Position): Pair<String, List<ImmutableProp>>? {
    val callTraceToRange = mutableMapOf<String, Pair<Token, Token>>()
    val callTraceToProps = mutableMapOf<String, List<ImmutableProp>>()

    rightTime.ast.dtoTypes.forEach { dtoType ->
        if (dtoType.name == null) return@forEach
        val bodyContext = dtoType.dtoBody() ?: return@forEach
        getBodyRange(bodyContext, dtoType.name.text, callTraceToRange)
    }

    rightTime.dtoTypes.forEach { dtoType ->
        if (dtoType.name == null) return@forEach
        getProps(dtoType.baseType, "${dtoType.name}", callTraceToProps)
    }

    val callTrace = callTraceToRange.filter {
        Range(
            it.value.first.position(),
            it.value.second.position()
        ).overlaps(position)
    }.entries.sortedWith { o1, o2 ->
        o2.value.first.line - o1.value.first.line
    }.firstOrNull()?.key ?: return null

    return callTraceToProps[callTrace]?.let { callTrace to it }
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

val commonFuncNames = setOf("flat")
val qbeFuncNames = Constants.QBE_FUNC_NAMES + commonFuncNames
val normalFuncNames = setOf("id") + commonFuncNames