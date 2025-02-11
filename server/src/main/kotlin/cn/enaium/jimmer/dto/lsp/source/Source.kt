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

package cn.enaium.jimmer.dto.lsp.source

import java.nio.file.Path

/**
 * @author Enaium
 */
open class Source(
    open val name: String,
    open val packageName: String,
    open val file: Path
)

data class Immutable(
    override val name: String,
    override val packageName: String,
    override val file: Path,
    val superTypes: List<String>,
    val typeParameters: List<String>,
    val immutableType: ImmutableType,
    val props: MutableList<Prop> = mutableListOf()
) : Source(name, packageName, file) {
    enum class ImmutableType {
        IMMUTABLE,
        ENTITY,
        EMBEDDABLE,
        MAPPED_SUPERCLASS
    }

    data class Prop(
        val name: String,
        val type: String,
        val nullable: Boolean,
        val annotations: List<Anno>
    )

    data class Anno(
        val name: String
    )
}

data class Enum(
    override val name: String,
    override val packageName: String,
    override val file: Path,
    val enumConstants: List<String>
) : Source(name, packageName, file)