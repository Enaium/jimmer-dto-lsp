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

import cn.enaium.jimmer.dto.lsp.logger
import cn.enaium.jimmer.dto.lsp.utility.toPropName
import org.babyfish.jimmer.Formula
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.dto.compiler.spi.BaseProp
import org.babyfish.jimmer.sql.*
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import kotlin.reflect.KCallable
import kotlin.reflect.KClass

/**
 * @author Enaium
 */
class ImmutableProp(
    private val context: Context,
    val declaringType: ImmutableType,
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
        } == true

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

    override val idViewBaseProp: BaseProp? = null

    val isIdView: Boolean = member.annotations.any { it.annotationClass.qualifiedName == IdView::class.qualifiedName }

    override val isEmbedded: Boolean
        get() = targetType?.isEmbeddable == true

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

    override val isNullable: Boolean
        get() {
            var nullable = (member.returnType.isMarkedNullable
                    || member.annotations.any { it.annotationClass.qualifiedName?.startsWith("Null") == true }
                    || listOf(
                java.lang.Long::class.java.name,
                Integer::class.java.name,
                java.lang.Short::class.java.name,
                java.lang.Byte::class.java.name,
                java.lang.Double::class.java.name,
                java.lang.Float::class.java.name,
                java.lang.Boolean::class.java.name
            ).contains((member.returnType.classifier as KClass<*>).java.name))

            fun isNullable(className: String): Boolean {
                try {
                    val inputStream = context.workspace.loader.getResourceAsStream(
                        className.replace(
                            '.',
                            '/'
                        ) + ".class"
                    )
                    val classNode = ClassNode()
                    ClassReader(
                        inputStream
                    ).accept(classNode, 0)
                    classNode.methods.forEach { method ->
                        if (method.name.toPropName() == name) {
                            method.invisibleAnnotations?.forEach {
                                if (it.desc.substringAfterLast("/").startsWith("Null")) {
                                    return true
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {

                }
                return false
            }
            if (!nullable) {
                nullable = isNullable(declaringType.klass.name)
            }
            if (!nullable) {
                for (it in declaringType.superTypes) {
                    nullable = isNullable(it.klass.name)
                    if (nullable) {
                        break
                    }
                }
            }
            return nullable
        }

    override val isRecursive: Boolean by lazy {
        declaringType.isEntity && manyToManyViewBaseProp === null && targetDeclaration !== null && declaringType.klass.isAssignableFrom(
            targetDeclaration
        )
    }

    override val isTransient: Boolean =
        member.annotations.any { it.annotationClass.qualifiedName == Transient::class.qualifiedName }

    override val manyToManyViewBaseProp: BaseProp? = null

    val isManyToManyView: Boolean =
        member.annotations.any { it.annotationClass.qualifiedName == ManyToManyView::class.qualifiedName }

    override val name: String
        get() = member.name.toPropName()

    override fun hasTransientResolver(): Boolean {
        return isTransient
    }

    override fun isAssociation(entityLevel: Boolean): Boolean {
        return isAssociation && (!entityLevel || targetType?.isEntity == true)
    }

    override val isReference: Boolean
        get() = !isList && isAssociation

    val propTypeName: String
        get() = member.returnType.toString().let {
            if (it.matches(Regex(".+[!|?]"))) {
                it.substring(0, it.length - 1)
            } else {
                it
            }
        }
}