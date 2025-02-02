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

import cn.enaium.jimmer.dto.lsp.Workspace
import java.io.File
import java.nio.file.Path
import kotlin.io.path.relativeTo

/**
 * @author Enaium
 */
class Context(val workspace: Workspace) {

    private val typeMap = mutableMapOf<Class<*>, ImmutableType>()

    fun ofType(klass: Class<*>): ImmutableType {
        return typeMap[klass] ?: ImmutableType(this, klass).also {
            typeMap[klass] = it
        }
    }

    fun findImmutableClass(projectDir: Path?, dto: Path, name: String): Class<*>? {
        var immutableClass = workspace.findImmutable(name)

        if (immutableClass == null && projectDir != null) {
            val relativeTo = dto.parent.relativeTo(projectDir)
            var path = relativeTo.toString().replace(File.separator, ".") + name
            path.substringAfter('.')
            path.substringAfter('.')
            for (i in 0 until path.count { it == '.' }) {
                immutableClass = workspace.findImmutable(path)
                if (immutableClass != null) {
                    break
                }
                path = path.substringAfter('.')
            }
        }
        return immutableClass
    }
}