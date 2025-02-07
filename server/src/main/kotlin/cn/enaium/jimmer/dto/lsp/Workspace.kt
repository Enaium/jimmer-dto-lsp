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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
        if (setting.classpath.findBuilder) {
            findBuilder()
        }
        if (setting.classpath.findConfiguration) {
            findConfiguration()
        }
        if (setting.classpath.findOtherProject) {
            indexClasses(true)
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
            }.get(5, TimeUnit.SECONDS)
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

    var loader = URLClassLoader(emptyArray(), Main::class.java.classLoader)
        private set

    private val classCache = mutableMapOf<String, Class<*>>()
    private val immutableCache = mutableMapOf<String, Class<*>>()
    private val annotationCache = mutableMapOf<String, Class<*>>()

    private fun findClasspath(fully: Boolean): List<Path> {
        return (if (fully) {
            (dependencies.values
                    + folders.map { findClasspath(URI.create(it).toPath()) }
                    + folders.map { findSubprojects(URI.create(it).toPath()) }.flatten().map { findClasspath(it) })
        } else {
            folders.map { findSubprojects(URI.create(it).toPath()) }.flatten().map { findClasspath(it) }
        }).flatten()
    }

    fun indexClasses(fully: Boolean) {
        val token = "Index Classes"
        client?.createProgress(WorkDoneProgressCreateParams(Either.forLeft(token)))
        client?.notifyProgress(ProgressParams(Either.forLeft(token), Either.forLeft(WorkDoneProgressBegin().apply {
            title = "$token in progress"
            cancellable = false
        })))

        val classpath =
            findClasspath(fully) + listOf(Main::class.java.protectionDomain.codeSource.location.toURI().toPath())
        loader = URLClassLoader((classpath.map { it.toUri().toURL() }).toTypedArray())

        if (fully) {
            classCache.clear()
            immutableCache.clear()
            annotationCache.clear()
        }

        val classNames = findClassNames(classpath)
        classNames.forEach { name ->
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

    fun findClass(name: String): Class<*>? {
        return classCache[name]
    }

    fun findImmutable(name: String): Class<*>? {
        return immutableCache[name]
    }

    fun findAnnotation(name: String): Class<*>? {
        return annotationCache[name]
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