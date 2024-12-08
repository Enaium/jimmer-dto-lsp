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

import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.dto.compiler.spi.BaseProp
import org.babyfish.jimmer.sql.*
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

/**
 * @author Enaium
 */
class ImmutableProp(
    private val context: Context,
    val member: KCallable<*>
) : BaseProp {
    private val targetDeclaration = if (isList) {
        (member.returnType.arguments[0].type?.classifier as KClass<*>?)?.java
    } else {
        (member.returnType.classifier as KClass<*>?)?.java
    }

    private val isAssociation: Boolean =
        (targetDeclaration?.isInterface)?.let {
            targetDeclaration.annotations.any {
                listOf(
                    Entity::class.qualifiedName,
                    MappedSuperclass::class.qualifiedName,
                    Embeddable::class.qualifiedName,
                    Immutable::class.qualifiedName
                ).contains(it.annotationClass.qualifiedName)
            }
        } ?: false

    val targetType: ImmutableType? by lazy {
        targetDeclaration
            ?.takeIf { isAssociation }
            ?.let {
                context.ofType(it)
            }
    }

    val isGeneratedValue: Boolean =
        member.annotations.any { it.annotationClass.qualifiedName == GeneratedValue::class.qualifiedName }

    val enumConstants: List<String> =
        if (targetDeclaration?.isEnum == true) {
            targetDeclaration.enumConstants?.map { it.toString() } ?: emptyList()
        } else {
            emptyList()
        }

    val isStringProp: Boolean =
        member.returnType.classifier.toString() == String::class.qualifiedName ||
                member.returnType.classifier.toString() == java.lang.String::class.qualifiedName

    override val idViewBaseProp: BaseProp? = null

    override val isEmbedded: Boolean
        get() = targetType?.isEmbeddable ?: false

    override val isExcludedFromAllScalars: Boolean
        get() = member.annotations.any { it.annotationClass.qualifiedName == ExcludeFromAllScalars::class.qualifiedName }

    override val isFormula: Boolean =
        member.annotations.any { it.annotationClass.qualifiedName == Formula::class.qualifiedName }

    override val isId: Boolean =
        member.annotations.any { it.annotationClass.qualifiedName == Id::class.qualifiedName }

    override val isKey: Boolean =
        member.annotations.any { it.annotationClass.qualifiedName == Key::class.qualifiedName }

    override val isList: Boolean
        get() = listOf(
            List::class,
            Set::class,
            Collection::class,
            MutableList::class,
            MutableSet::class,
            MutableCollection::class,
            java.util.List::class,
            java.util.Set::class,
            java.util.Collection::class,
        ).contains(member.returnType.classifier)

    override val isLogicalDeleted: Boolean =
        member.annotations.any { it.annotationClass.qualifiedName == LogicalDeleted::class.qualifiedName }

    override val isNullable: Boolean =
        member.returnType.isMarkedNullable

    override val isRecursive: Boolean = false

    override val isTransient: Boolean =
        member.annotations.any { it.annotationClass.qualifiedName == Transient::class.qualifiedName }

    override val manyToManyViewBaseProp: BaseProp? = null

    override val name: String
        get() = member.name

    override fun hasTransientResolver(): Boolean {
        return isTransient
    }

    override fun isAssociation(entityLevel: Boolean): Boolean {
        return isAssociation && (!entityLevel || targetType?.isEntity == true)
    }
}