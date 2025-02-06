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

import org.babyfish.jimmer.dto.compiler.DtoCompiler
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.PropConfig
import org.babyfish.jimmer.dto.compiler.SimplePropType
import java.math.BigDecimal
import java.math.BigInteger

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
        simplePropMap[baseProp.propName] ?: SimplePropType.NONE


    override fun getSimplePropType(pathNode: PropConfig.PathNode<ImmutableProp>): SimplePropType? =
        simplePropMap[
            if (pathNode.isAssociatedId) {
                pathNode.prop.targetType!!.idProp!!.propName
            } else {
                pathNode.prop.propName
            }
        ] ?: SimplePropType.NONE

    override fun isSameType(baseProp1: ImmutableProp, baseProp2: ImmutableProp): Boolean =
        baseProp1.declaringType.qualifiedName == baseProp2.declaringType.qualifiedName

    override fun getGenericTypeCount(qualifiedName: String): Int =
        baseType.klass.typeParameters.size

    private val simplePropMap = mapOf<String, SimplePropType>(
        "kotlin.String" to SimplePropType.STRING,
        "kotlin.Int" to SimplePropType.INT,
        "kotlin.Long" to SimplePropType.LONG,
        "kotlin.Float" to SimplePropType.FLOAT,
        "kotlin.Double" to SimplePropType.DOUBLE,
        "kotlin.Boolean" to SimplePropType.BOOLEAN,
        "kotlin.Byte" to SimplePropType.BYTE,
        "kotlin.Short" to SimplePropType.SHORT,
        BigInteger::class.java.name to SimplePropType.BIG_INTEGER,
        BigDecimal::class.java.name to SimplePropType.BIG_DECIMAL,
        String::class.java.name to SimplePropType.STRING,
        Int::class.java.name to SimplePropType.INT,
        Long::class.java.name to SimplePropType.LONG,
        Float::class.java.name to SimplePropType.FLOAT,
        Double::class.java.name to SimplePropType.DOUBLE,
        Boolean::class.java.name to SimplePropType.BOOLEAN,
        Byte::class.java.name to SimplePropType.BYTE,
        Short::class.java.name to SimplePropType.SHORT
    )
}