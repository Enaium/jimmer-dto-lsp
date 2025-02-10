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
import cn.enaium.jimmer.dto.lsp.utility.*
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.MappedSuperclass
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.*
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.toPath

/**
 * @author Enaium
 */
data class Workspace(
    var setting: Setting = Setting(),
    val folders: MutableList<String> = mutableListOf(),
    val dependencies: MutableMap<String, List<Path>> = mutableMapOf()
) {
    fun resolve() {
        processSource()
        if (setting.classpath.findBuilder) {
            CompletableFuture.runAsync {
                findBuilder()
            }
        }
        if (setting.classpath.findConfiguration) {
            CompletableFuture.supplyAsync {
                findConfiguration()
            }
        }
        if (setting.classpath.findOtherProject) {
            CompletableFuture.supplyAsync {
                indexClasses()
            }
        }
    }

    private fun findBuilder() {
        val token = "Find Dependencies By Command"
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
            client?.notifyProgress(
                ProgressParams(
                    Either.forLeft(token),
                    Either.forLeft(WorkDoneProgressEnd().apply {
                        message = "$token timeout"
                    })
                )
            )
            client?.showMessage(MessageParams().apply {
                message = "Resolve Dependencies timeout, please resolve dependencies manually"
                type = MessageType.Warning
            })
        }
    }

    private fun findConfiguration() {
        val token = "Find Dependencies By Configuration"
        client?.createProgress(WorkDoneProgressCreateParams(Either.forLeft(token)))
        client?.notifyProgress(ProgressParams(Either.forLeft(token), Either.forLeft(WorkDoneProgressBegin().apply {
            title = "$token in progress"
            cancellable = false
        })))
        folders.forEach {
            dependencies += findDependenciesByConfiguration(URI.create(it).toPath())
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

    private fun processSource() {
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
        findProjects(URI.create(folders.first()).toPath()).forEach { project ->
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

    var loader = URLClassLoader(emptyArray(), Main::class.java.classLoader)
        private set

    private val classCache = mutableMapOf<String, Class<*>>()
    private val immutableCache = mutableMapOf<String, Class<*>>()
    private val annotationCache = mutableMapOf<String, Class<*>>()

    private fun findClasspath(): List<Path> {
        return (dependencies.values + folders.map {
            findClasspath(
                URI.create(it).toPath()
            )
        } + folders.map { findProjects(URI.create(it).toPath()) }.flatten().map { findClasspath(it) }).flatten()
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
        loader = URLClassLoader((classpath.map { it.toUri().toURL() }).toTypedArray())

        classCache.clear()
        immutableCache.clear()
        annotationCache.clear()

        val classNames = findClassNames(classpath)
        classNames.forEach { name ->
            if (name.startsWith(Main::class.java.packageName)) {
                return@forEach
            }
            try {
                loader[name]?.run {
                    if (this.isAnnotation) {
                        annotationCache[name] = this
                    } else if (this.annotations.any {
                            listOf(
                                Entity::class,
                                MappedSuperclass::class,
                                Embeddable::class,
                                Immutable::class
                            ).contains(it.annotationClass)
                        }) {
                        immutableCache[name] = this
                    } else {
                        classCache[name] = this
                    }
                }
            } catch (_: Throwable) {
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
        return annotationCache.values.map { it.name }
    }

    fun findImmutableNames(): List<String> {
        return immutableCache.values.map { it.name }
    }

    fun findClassNames(): List<String> {
        return classCache.values.map { it.name }
    }

    fun findSource(name: String): Source? {
        return sourceCache[name]
    }

    fun findSources(): List<Source> {
        return sourceCache.values.toList()
    }

    operator fun ClassLoader.get(name: String?): Class<*>? {
        if (name == null) {
            return null
        }
        return try {
            loadClass(name)
        } catch (_: Throwable) {
            return null
        }
    }
}