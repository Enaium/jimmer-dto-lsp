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

import cn.enaium.jimmer.dto.lsp.source.Enum
import cn.enaium.jimmer.dto.lsp.source.Immutable
import cn.enaium.jimmer.dto.lsp.utility.toPropName
import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.dto.compiler.spi.BaseProp
import org.babyfish.jimmer.sql.*

/**
 * @author Enaium
 */
data class ImmutableProp(
    private val context: Context,
    val declaringType: ImmutableType,
    val prop: Immutable.Prop
) : BaseProp {
    private val targetSource = run {
        if (isList) {
            context.ofSource(prop.type.substringAfter("<").substringBeforeLast(">"))
        } else {
            context.ofSource(prop.type)
        }
    }

    private val isAssociation: Boolean =
        targetSource is Immutable

    val targetType: ImmutableType? by lazy {
        targetSource
            ?.takeIf { isAssociation }
            ?.let {
                context.ofType(it.name)
            }
    }

    val isGeneratedValue: Boolean =
        prop.annotations.any { it.name.endsWith(GeneratedValue::class.simpleName!!) }

    val enumConstants: List<String> =
        if (targetSource is Enum) {
            targetSource.enumConstants.map { it.toString() }
        } else {
            emptyList()
        }

    override val idViewBaseProp: BaseProp? = null

    val isIdView: Boolean = prop.annotations.any { it.name.endsWith(IdView::class.simpleName!!) }

    override val isEmbedded: Boolean
        get() = targetType?.isEmbeddable == true

    override val isExcludedFromAllScalars: Boolean
        get() = prop.annotations.any { it.name.endsWith(ExcludeFromAllScalars::class.simpleName!!) }

    override val isFormula: Boolean =
        prop.annotations.any { it.name.endsWith(Formula::class.simpleName!!) }

    override val isId: Boolean =
        prop.annotations.any { it.name.endsWith(Id::class.simpleName!!) }

    override val isKey: Boolean =
        prop.annotations.any { it.name.endsWith(Key::class.simpleName!!) }

    override val isList: Boolean
        get() = listOf(
            "List",
            "Set",
            "Collection",
            "MutableList",
            "MutableSet",
            "MutableCollection"
        ).any { prop.type.startsWith("${it}<") }

    override val isLogicalDeleted: Boolean =
        prop.annotations.any { it.name.endsWith(LogicalDeleted::class.simpleName!!) }

    override val isNullable: Boolean
        get() = prop.nullable

    override val isRecursive: Boolean by lazy {
        declaringType.source is Immutable && targetSource is Immutable && manyToManyViewBaseProp === null && isAssignableFrom(
            declaringType.source,
            targetSource
        )
    }

    private fun isAssignableFrom(
        i1: Immutable,
        i2: Immutable
    ): Boolean {
        if (i1 == i2) {
            return true
        }
        if (i1.superTypes.any {
                val ofSource = context.ofSource(it)
                ofSource != null && ofSource is Immutable && isAssignableFrom(ofSource, i2)
            }) {
            return true
        }
        return false
    }

    override val isTransient: Boolean =
        prop.annotations.any { it.name.endsWith(Transient::class.simpleName!!) }

    override val manyToManyViewBaseProp: BaseProp? = null

    val isManyToManyView: Boolean =
        prop.annotations.any { it.name.endsWith(ManyToManyView::class.simpleName!!) }

    override val name: String
        get() = prop.name.toPropName()

    override fun hasTransientResolver(): Boolean {
        return isTransient
    }

    override fun isAssociation(entityLevel: Boolean): Boolean {
        return isAssociation && (!entityLevel || targetType?.isEntity == true)
    }

    override val isReference: Boolean
        get() = !isList && isAssociation

    override fun toString(): String {
        return name
    }
}