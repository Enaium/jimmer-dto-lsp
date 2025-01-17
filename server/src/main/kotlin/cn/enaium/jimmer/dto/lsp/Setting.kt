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

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path

data class Setting(
    val formatting: Formatting = Formatting(),
    val classpath: Classpath = Classpath()
) {
    data class Formatting(
        val propsSpaceLine: PropsSpaceLine = PropsSpaceLine.HAS_ANNOTATION
    ) {
        enum class PropsSpaceLine {
            ALWAYS, NEVER, HAS_ANNOTATION
        }
    }

    data class Classpath(
        val findBuilder: Boolean = true,
        val findConfiguration: Boolean = true,
        val findOtherProject: Boolean = true
    )

    companion object
}

fun Setting.Companion.read(path: Path): Setting {
    return jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
        .readValue(path.toFile(), Setting::class.java)
}

fun Setting.save(path: Path) {
    jacksonObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
        .writeValue(path.toFile(), this)
}