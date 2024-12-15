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

import java.net.URLClassLoader

/**
 * @author Enaium
 */
class Context(val loader: URLClassLoader) {

    private val typeMap = mutableMapOf<Class<*>, ImmutableType>()

    fun ofType(klass: Class<*>): ImmutableType {
        return typeMap[klass] ?: ImmutableType(this, klass).also {
            typeMap[klass] = it
        }
    }
}

operator fun ClassLoader.get(name: String?): Class<*>? {
    if (name == null) {
        return null
    }
    return try {
        loadClass(name)
    } catch (e: ClassNotFoundException) {
        return null
    } catch (e: NoClassDefFoundError) {
        return null
    }
}