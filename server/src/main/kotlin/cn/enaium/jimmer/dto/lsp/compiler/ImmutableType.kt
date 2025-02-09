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

package cn.enaium.jimmer.dto.lsp.compiler

import cn.enaium.jimmer.dto.lsp.source.Immutable
import cn.enaium.jimmer.dto.lsp.source.Source
import cn.enaium.jimmer.dto.lsp.utility.toPropName
import org.babyfish.jimmer.dto.compiler.spi.BaseType
import org.babyfish.jimmer.sql.Id

/**
 * @author Enaium
 */
data class ImmutableType(
    private val context: Context,
    val source: Source
) : BaseType {
    val superTypes: List<ImmutableType> = if (source is Immutable) {
        source.superTypes.mapNotNull { context.ofType(it) }
    } else {
        emptyList()
    }

    private val primarySuperType: ImmutableType? =
        superTypes.firstOrNull { !it.isMappedSuperclass }

    val declaredProperties: Map<String, ImmutableProp>
        get() = if (source is Immutable) {
            source.props
                .filter { it.annotations.any { it.name.endsWith(Id::class.simpleName!!) } }
                .associateBy({ it.name.toPropName() }) {
                    ImmutableProp(context, this, it)
                } + source.props
                .filter { it.annotations.any { !it.name.endsWith(Id::class.simpleName!!) } || it.annotations.isEmpty() }
                .associateBy({ it.name.toPropName() }) {
                    ImmutableProp(context, this, it)
                }
        } else {
            emptyMap()
        }

    private val superPropMap: Map<String, ImmutableProp> = superTypes
        .flatMap { it.properties.values }
        .groupBy { it.name }
        .toList()
        .associateBy({ it.first }) {
            it.second.first()
        }

    private val redefinedProps = superPropMap.filterKeys {
        primarySuperType == null || !primarySuperType.properties.contains(it)
    }.mapValues {
        ImmutableProp(context, this, it.value.prop)
    }

    val properties: Map<String, ImmutableProp> =
        if (superTypes.isEmpty()) {
            declaredProperties
        } else {
            val map = mutableMapOf<String, ImmutableProp>()
            for (superType in superTypes) {
                for ((name, prop) in superType.properties) {
                    if (prop.isId) {
                        map[name] = prop
                    }
                }
            }
            for ((name, prop) in redefinedProps) {
                if (prop.isId) {
                    map[name] = prop
                }
            }
            for ((name, prop) in declaredProperties) {
                if (prop.isId) {
                    map[name] = prop
                }
            }
            for (superType in superTypes) {
                for ((name, prop) in superType.properties) {
                    if (!prop.isId) {
                        map[name] = prop
                    }
                }
            }
            for ((name, prop) in redefinedProps) {
                if (!prop.isId) {
                    map[name] = prop
                }
            }
            for ((name, prop) in declaredProperties) {
                if (!prop.isId) {
                    map[name] = prop
                }
            }
            map
        }

    val idProp: ImmutableProp? by lazy {
        val idProps = declaredProperties.values.filter { it.isId }
        val superIdProp = superTypes.firstOrNull { it.idProp !== null }?.idProp
        val prop = idProps.firstOrNull() ?: superIdProp
        prop
    }

    override val isEntity: Boolean =
        source is Immutable && source.immutableType == Immutable.ImmutableType.ENTITY
    private val isMappedSuperclass: Boolean =
        source is Immutable && source.immutableType == Immutable.ImmutableType.MAPPED_SUPERCLASS
    val isEmbeddable: Boolean =
        source is Immutable && source.immutableType == Immutable.ImmutableType.EMBEDDABLE

    override val name: String = source.name
    override val packageName: String = source.packageName
    override val qualifiedName: String = source.name

    override fun toString(): String {
        return name
    }
}