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

import cn.enaium.jimmer.dto.lsp.Main.logger
import org.babyfish.jimmer.dto.compiler.DtoCompiler
import org.babyfish.jimmer.dto.compiler.DtoFile

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

    override fun isGeneratedValue(baseProp: ImmutableProp): Boolean =
        baseProp.isGeneratedValue

    override fun getEnumConstants(baseProp: ImmutableProp): List<String> =
        baseProp.enumConstants

    override fun isStringProp(baseProp: ImmutableProp): Boolean =
        baseProp.isStringProp

    override fun isSameType(baseProp1: ImmutableProp, baseProp2: ImmutableProp): Boolean =
        baseProp1.declaringType.qualifiedName == baseProp2.declaringType.qualifiedName

    override fun getGenericTypeCount(qualifiedName: String): Int =
        baseType.klass.typeParameters.size
}