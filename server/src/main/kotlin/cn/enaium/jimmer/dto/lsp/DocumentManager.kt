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

import java.util.concurrent.ConcurrentHashMap

class DocumentManager {
    private val documentCache = ConcurrentHashMap<String, DtoDocument>()

    fun openOrUpdateDocument(uri: String, content: DtoDocument) {
        documentCache[uri] = content
    }

    fun getDocument(uri: String): DtoDocument? {
        return documentCache[uri]
    }

    fun closeDocument(uri: String) {
        documentCache.remove(uri)
    }
}
