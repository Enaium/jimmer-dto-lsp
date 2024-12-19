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

package cn.enaium.jimmer.dto.lsp.utility

/**
 * @author Enaium
 */
enum class SemanticType(val id: Int, val type: String) {
    COMMENT(0, "comment"),
    KEYWORD(1, "keyword"),
    FUNCTION(2, "function"),
    STRING(3, "string"),
    NUMBER(4, "number"),
    DECORATOR(5, "decorator"),
    MACRO(6, "macro"),
    TYPE(7, "type"),
    TYPE_PARAMETER(8, "typeParameter"),
    CLASS(9, "class"),
    VARIABLE(10, "variable"),
    PROPERTY(11, "property"),
    STRUCT(12, "struct"),
    INTERFACE(13, "interface"),
    PARAMETER(14, "parameter"),
    ENUM_MEMBER(15, "enumMember"),
    NAMESPACE(16, "namespace"),
}