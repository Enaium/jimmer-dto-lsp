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

import org.babyfish.jimmer.dto.compiler.DtoLexer

/**
 * @author Enaium
 */
enum class TokenType(val id: Int, val literal: String) {
    EXPORT(DtoLexer.T__0, "'export'"),
    DOT(DtoLexer.T__1, "'.'"),
    ARROW(DtoLexer.T__2, "'->'"),
    PACKAGE(DtoLexer.T__3, "'package'"),
    IMPORT(DtoLexer.T__4, "'import'"),
    LEFT_BRACE(DtoLexer.T__5, "'{'"),
    COMMA(DtoLexer.T__6, "','"),
    RIGHT_BRACE(DtoLexer.T__7, "'}'"),
    AS(DtoLexer.T__8, "'as'"),
    FIXED(DtoLexer.T__9, "'fixed'"),
    STATIC(DtoLexer.T__10, "'static'"),
    DYNAMIC(DtoLexer.T__11, "'dynamic'"),
    FUZZY(DtoLexer.T__12, "'fuzzy'"),
    IMPLEMENTS(DtoLexer.T__13, "'implements'"),
    SEMICOLON(DtoLexer.T__14, "';'"),
    HASH(DtoLexer.T__15, "'#'"),
    LEFT_PARENTHESIS(DtoLexer.T__16, "'('"),
    RIGHT_PARENTHESIS(DtoLexer.T__17, "')'"),
    QUESTION_MARK(DtoLexer.T__18, "'?'"),
    EXCLAMATION_MARK(DtoLexer.T__19, "'!'"),
    CARET(DtoLexer.T__20, "'^'"),
    DOLLAR(DtoLexer.T__21, "'$'"),
    PLUS(DtoLexer.T__22, "'+'"),
    SLASH(DtoLexer.T__23, "'/'"),
    ASTERISK(DtoLexer.T__24, "'*'"),
    MINUS(DtoLexer.T__25, "'-'"),
    COLON(DtoLexer.T__26, "':'"),
    LESS_THAN(DtoLexer.T__27, "'<'"),
    GREATER_THAN(DtoLexer.T__28, "'>'"),
    AT(DtoLexer.T__29, "'@'"),
    EQUAL(DtoLexer.T__30, "'='"),
    LEFT_BRACKET(DtoLexer.T__31, "'['"),
    RIGHT_BRACKET(DtoLexer.T__32, "']'"),
    DOUBLE_COLON(DtoLexer.T__33, "'::'"),
    CLASS(DtoLexer.T__34, "'class'");
}