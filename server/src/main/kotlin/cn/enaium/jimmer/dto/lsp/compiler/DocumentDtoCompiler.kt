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
import org.babyfish.jimmer.dto.compiler.DtoCompiler
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.PropConfig
import org.babyfish.jimmer.dto.compiler.SimplePropType

/**
 * @author Enaium
 */
class DocumentDtoCompiler(dtoFile: DtoFile) : DtoCompiler<ImmutableType, ImmutableProp>(dtoFile) {
    override fun getSuperTypes(baseType: ImmutableType): Collection<ImmutableType> =
        baseType.superTypes

    override fun getDeclaredProps(baseType: ImmutableType): Map<String, ImmutableProp> =
        baseType.declaredProperties

    override fun getProps(baseType: ImmutableType): Map<String, ImmutableProp> =
        baseType.properties

    override fun getTargetType(baseProp: ImmutableProp): ImmutableType? =
        baseProp.targetType

    override fun getIdProp(baseType: ImmutableType): ImmutableProp? =
        baseType.idProp

    override fun isGeneratedValue(baseProp: ImmutableProp): Boolean =
        baseProp.isGeneratedValue

    override fun getEnumConstants(baseProp: ImmutableProp): List<String> =
        baseProp.enumConstants

    override fun getSimplePropType(baseProp: ImmutableProp): SimplePropType? =
        simplePropType(baseProp.prop.type)


    override fun getSimplePropType(pathNode: PropConfig.PathNode<ImmutableProp>): SimplePropType? =
        simplePropType(
            if (pathNode.isAssociatedId) {
                pathNode.prop.targetType!!.idProp!!.prop.type
            } else {
                pathNode.prop.prop.type
            }
        )

    override fun isSameType(baseProp1: ImmutableProp, baseProp2: ImmutableProp): Boolean =
        baseProp1.declaringType.qualifiedName == baseProp2.declaringType.qualifiedName

    override fun getGenericTypeCount(qualifiedName: String): Int =
        if (baseType.source is Immutable) {
            (baseType.source as Immutable).typeParameters.size
        } else {
            0
        }

    private fun simplePropType(type: String): SimplePropType {
        if (type.endsWith("String")) {
            return SimplePropType.STRING
        } else if (type.endsWith("Int") || type.endsWith("Integer") || type.endsWith("int")) {
            return SimplePropType.INT
        } else if (type.endsWith("Long") || type.endsWith("long")) {
            return SimplePropType.LONG
        } else if (type.endsWith("Float") || type.endsWith("float")) {
            return SimplePropType.FLOAT
        } else if (type.endsWith("Double") || type.endsWith("double")) {
            return SimplePropType.DOUBLE
        } else if (type.endsWith("Boolean") || type.endsWith("boolean")) {
            return SimplePropType.BOOLEAN
        } else if (type.endsWith("Byte") || type.endsWith("byte")) {
            return SimplePropType.BYTE
        } else if (type.endsWith("Short") || type.endsWith("short")) {
            return SimplePropType.SHORT
        } else if (type.endsWith("BigInteger")) {
            return SimplePropType.BIG_INTEGER
        } else if (type.endsWith("BigDecimal")) {
            return SimplePropType.BIG_DECIMAL
        } else {
            return SimplePropType.NONE
        }
    }
}