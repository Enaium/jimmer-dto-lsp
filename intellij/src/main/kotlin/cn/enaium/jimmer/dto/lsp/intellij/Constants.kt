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

package cn.enaium.jimmer.dto.lsp.intellij

import com.intellij.openapi.util.IconLoader

/**
 * @author Enaium
 */
object Constants {
    val ICON = IconLoader.getIcon("/icons/logo.svg", Constants::class.java)
    val DTO = Dto()
    const val LANGUAGE_ID = "JimmerDTO"
    const val LANGUAGE_NAME = "Jimmer DTO"
    const val NAME = "Jimmer DTO LSP"
    const val EXTENSION = "dto"
    const val ID = "jimmer-dto-lsp"
    const val SERVER_ID = "jimmer-dto"
}