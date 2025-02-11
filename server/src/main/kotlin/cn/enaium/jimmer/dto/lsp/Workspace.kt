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

import cn.enaium.jimmer.dto.lsp.source.JavaProcessor
import cn.enaium.jimmer.dto.lsp.source.KotlinProcessor
import cn.enaium.jimmer.dto.lsp.source.Lang
import cn.enaium.jimmer.dto.lsp.source.Source
import cn.enaium.jimmer.dto.lsp.utility.findClassNames
import cn.enaium.jimmer.dto.lsp.utility.findClasspath
import cn.enaium.jimmer.dto.lsp.utility.findProjects
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.MappedSuperclass
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.tomlj.Toml
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.*
import kotlin.io.path.*

/**
 * @author Enaium
 */
data class Workspace(
    var setting: Setting = Setting(),
    val folders: MutableList<String> = mutableListOf(),
    private val dependencies: MutableList<Path> = mutableListOf()
) {
    fun resolve() {
        val projects = folders.map { findProjects(URI.create(it).toPath()) }.flatten()
        if (setting.classpath.findConfiguration) {
            findConfiguration(projects)
        }
        if (setting.classpath.findOtherProject) {
            indexClasses()
        }
        processSource(projects)
    }

    private fun findConfiguration(projects: List<Path>) {
        val token = "Find Dependencies By Configuration"
        client?.createProgress(WorkDoneProgressCreateParams(Either.forLeft(token)))
        client?.notifyProgress(ProgressParams(Either.forLeft(token), Either.forLeft(WorkDoneProgressBegin().apply {
            title = "$token in progress"
            cancellable = false
        })))
        projects.forEach { project ->
            project.resolve("pom.xml").takeIf { it.exists() }?.also { pomPath ->
                val pomTree = XmlMapper().readTree(pomPath.toFile())
                val properties = pomTree["properties"]
                val dependencies = pomTree["dependencies"] ?: return@forEach
                dependencies.forEach { dependency ->
                    val groupId = dependency["groupId"]?.asText() ?: return@forEach
                    val artifactId = dependency["artifactId"]?.asText() ?: return@forEach
                    val version = dependency["version"]?.asText()
                        ?.let { properties?.get(it.substringAfter("\${").substringBefore("}"))?.asText() ?: it }
                        ?: return@forEach
                    Path(System.getProperty("user.home")).resolve(".m2")
                        .resolve("repository").resolve(groupId.replace(".", "/")).resolve(artifactId)
                        .resolve(version).resolve("$artifactId-$version.jar").takeIf { it.exists() }?.also { jarPath ->
                            this.dependencies.add(jarPath)
                        }
                }
            }
        }

        folders.forEach { folder ->
            URI.create(folder).toPath().resolve("gradle").walk().filter { it.extension == "toml" }
                .forEach { tomlPath ->
                    val toml = Toml.parse(tomlPath)
                    val versions = toml.getTable("versions")
                    toml.getTable("libraries")?.also {
                        it.keySet().forEach { key ->
                            val dependency = it.getTable(key) ?: return@forEach
                            val module = dependency.getString("module") ?: dependency.getString("group")
                                ?.let { group -> dependency.getString("name")?.let { name -> "$group:$name" } }
                            ?: return@forEach
                            val group = module.substringBefore(":")
                            val name = module.substringAfter(":")

                            val version = versions?.getString(
                                dependency.getString("version.ref") ?: dependency.getString("version") ?: return@forEach
                            ) ?: return@forEach
                            Path(System.getProperty("user.home")).resolve(".gradle")
                                .resolve("caches").resolve("modules-2").resolve("files-2.1")
                                .resolve(group).resolve(name).resolve(version).walk()
                                .find { it.name == "$name-$version.jar" }?.takeIf { it.exists() }?.also { jarPath ->
                                    this.dependencies.add(jarPath)
                                }
                        }
                    }
                }
        }
        client?.notifyProgress(
            ProgressParams(
                Either.forLeft(token),
                Either.forLeft(WorkDoneProgressEnd().apply {
                    message = "$token done"
                })
            )
        )
    }

    private val sourceDirs = mapOf(
        Lang.JAVA to "src/main/java",
        Lang.JAVA to "src/test/java",
        Lang.KOTLIN to "src/main/kotlin",
        Lang.KOTLIN to "src/test/kotlin"
    )

    private val sourceCache = mutableMapOf<String, Source>()

    private fun processSource(projects: List<Path>) {
        val token = "Process Source"
        client?.createProgress(WorkDoneProgressCreateParams(Either.forLeft(token)))
        client?.notifyProgress(ProgressParams(Either.forLeft(token), Either.forLeft(WorkDoneProgressBegin().apply {
            title = "$token in progress"
            cancellable = false
        })))
        val pool = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 4,
            60L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue()
        )
        val futures = mutableListOf<Future<List<Source>>>()
        projects.forEach { project ->
            sourceDirs.forEach { (lang, dir) ->
                val source = project.resolve(dir)
                if (source.exists().not()) return@forEach
                futures += pool.submit(Callable {
                    when (lang) {
                        Lang.JAVA -> {
                            JavaProcessor(listOf(source)).process()
                        }

                        Lang.KOTLIN -> {
                            KotlinProcessor(listOf(source)).process()
                        }
                    }
                })
            }
        }
        futures.forEach {
            it.get().forEach { source ->
                sourceCache[source.name] = source
            }
        }
        pool.shutdown()
        client?.notifyProgress(
            ProgressParams(
                Either.forLeft(token),
                Either.forLeft(WorkDoneProgressEnd().apply {
                    message = "$token done"
                })
            )
        )
    }

    fun updateSources(names: Set<String>) {
        val token = "Update Sources"
        client?.createProgress(WorkDoneProgressCreateParams(Either.forLeft(token)))
        client?.notifyProgress(ProgressParams(Either.forLeft(token), Either.forLeft(WorkDoneProgressBegin().apply {
            title = "$token in progress"
            cancellable = false
        })))
        val nameSources = names.mapNotNull { sourceCache[it] }
        JavaProcessor(nameSources.filter { it.file.extension == "java" }.map { it.file }).process().forEach {
            sourceCache[it.name] = it
        }
        KotlinProcessor(nameSources.filter { it.file.extension == "kt" }.map { it.file }).process().forEach {
            sourceCache[it.name] = it
        }
        client?.notifyProgress(
            ProgressParams(
                Either.forLeft(token),
                Either.forLeft(WorkDoneProgressEnd().apply {
                    message = "$token done"
                })
            )
        )
    }

    private val classCache = mutableListOf<String>()
    private val annotationCache = mutableListOf<String>()

    private fun findClasspath(): List<Path> {
        return (dependencies + folders.map {
            findClasspath(
                URI.create(it).toPath()
            )
        }.flatten() + folders.map { findProjects(URI.create(it).toPath()) }.flatten().map { findClasspath(it) }
            .flatten())
    }

    fun indexClasses() {
        val token = "Index Classes"
        client?.createProgress(WorkDoneProgressCreateParams(Either.forLeft(token)))
        client?.notifyProgress(ProgressParams(Either.forLeft(token), Either.forLeft(WorkDoneProgressBegin().apply {
            title = "$token in progress"
            cancellable = false
        })))

        val classpath =
            findClasspath() + listOf(Main::class.java.protectionDomain.codeSource.location.toURI().toPath())
        val loader = URLClassLoader((classpath.map { it.toUri().toURL() }).toTypedArray())

        classCache.clear()
        annotationCache.clear()

        val classNames = findClassNames(classpath)
        val hasImmutableJars = mutableSetOf<Path>()
        classNames.forEach { (path, name) ->
            if (name.startsWith(Main::class.java.packageName)) {
                return@forEach
            }

            val classReader = ClassReader(loader.getResourceAsStream(name.replace(".", "/") + ".class"))
            val classNode = ClassNode()
            classReader.accept(classNode, 0)
            if (classNode.access == Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT or Opcodes.ACC_ANNOTATION) {
                annotationCache.add(name)
            } else if (classNode.visibleAnnotations?.any {
                    listOf(
                        Entity::class,
                        MappedSuperclass::class,
                        Embeddable::class,
                        Immutable::class
                    ).any { ia -> Type.getDescriptor(ia.java) == it.desc }
                } == true) {
                if (path.extension == "jar") {
                    hasImmutableJars.add(path)
                }
            } else {
                classCache.add(name)
            }
        }

        hasImmutableJars.forEach { jar ->
            var sourceJar = jar.parent.resolve("${jar.nameWithoutExtension}-sources.jar")

            if (sourceJar.exists().not()) {
                jar.parent.parent.walk().find { it.name == "${jar.nameWithoutExtension}-sources.jar" }?.also {
                    sourceJar = it
                }
            }

            JavaProcessor(listOf(sourceJar)).process().forEach { source ->
                sourceCache[source.name] = source
            }
            KotlinProcessor(listOf(sourceJar)).process().forEach { source ->
                sourceCache[source.name] = source
            }
        }

        client?.notifyProgress(
            ProgressParams(
                Either.forLeft(token),
                Either.forLeft(WorkDoneProgressEnd().apply {
                    message = "$token done"
                })
            )
        )
    }


    fun findAnnotationNames(): List<String> {
        return annotationCache
    }

    fun findClassNames(): List<String> {
        return classCache
    }

    fun findSource(name: String): Source? {
        return sourceCache[name]
    }

    fun findSources(): List<Source> {
        return sourceCache.values.toList()
    }
}