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

import cn.enaium.jimmer.dto.lsp.utility.toPropName
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.dto.compiler.spi.BaseType
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.Id
import org.babyfish.jimmer.sql.MappedSuperclass

/**
 * @author Enaium
 */
class ImmutableType(
    private val context: Context,
    val klass: Class<*>
) : BaseType {
    private val immutableAnnoType: String? = klass.annotations.filter {
        listOf(
            Entity::class.qualifiedName,
            MappedSuperclass::class.qualifiedName,
            Embeddable::class.qualifiedName,
            Immutable::class.qualifiedName
        ).contains(it.annotationClass.qualifiedName)
    }.takeIf { it.isNotEmpty() }?.first()?.annotationClass?.qualifiedName

    val superTypes: List<ImmutableType> = klass.interfaces.map { context.ofType(it) }

    private val primarySuperType: ImmutableType? =
        superTypes.firstOrNull { !it.isMappedSuperclass }

    val declaredProperties: Map<String, ImmutableProp>
        get() = (klass.kotlin.members
            .filter { it.annotations.any { it.annotationClass.qualifiedName == Id::class.qualifiedName } }
            .associateBy({ it.name.toPropName() }) {
                ImmutableProp(context, this, it)
            } + klass.kotlin.members
            .filter { it.annotations.any { it.annotationClass.qualifiedName != Id::class.qualifiedName } || it.annotations.isEmpty() }
            .associateBy({ it.name.toPropName() }) {
                ImmutableProp(context, this, it)
            }).filter { !Any::class.members.map { any -> any.name.toPropName() }.contains(it.key) }

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
        ImmutableProp(context, this, it.value.member)
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

    override val isEntity: Boolean = immutableAnnoType == Entity::class.qualifiedName
    private val isMappedSuperclass: Boolean = immutableAnnoType == MappedSuperclass::class.qualifiedName
    val isEmbeddable: Boolean = immutableAnnoType == Embeddable::class.qualifiedName
    override val name: String = klass.simpleName
    override val packageName: String = klass.packageName
    override val qualifiedName: String = klass.name
}