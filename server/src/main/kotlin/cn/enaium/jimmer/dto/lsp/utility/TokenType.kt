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
    NULL(DtoLexer.T__23, "'null'"),
    DIV(DtoLexer.T__24, "'/'"),
    ASTERISK(DtoLexer.T__25, "'*'"),
    MINUS(DtoLexer.T__26, "'-'"),
    COLON(DtoLexer.T__27, "':'"),
    LESS_THAN(DtoLexer.T__28, "'<'"),
    GREATER_THAN(DtoLexer.T__29, "'>'"),
    WHERE(DtoLexer.T__30, "'!where'"),
    OR(DtoLexer.T__31, "'or'"),
    AND(DtoLexer.T__32, "'and'"),
    EQUAL(DtoLexer.T__33, "'='"),
    NOT_EQUAL(DtoLexer.T__34, "'<>'"),
    LESS_THAN_OR_EQUAL(DtoLexer.T__35, "'<='"),
    GREATER_THAN_OR_EQUAL(DtoLexer.T__36, "'>='"),
    IS(DtoLexer.T__37, "'is'"),
    NOT(DtoLexer.T__38, "'not'"),
    ORDER_BY(DtoLexer.T__39, "'!orderBy'"),
    ASC(DtoLexer.T__40, "'asc'"),
    DESC(DtoLexer.T__41, "'desc'"),
    FILTER(DtoLexer.T__42, "'!filter'"),
    RECURSION(DtoLexer.T__43, "'!recursion'"),
    FETCH_TYPE(DtoLexer.T__44, "'!fetchType'"),
    LIMIT(DtoLexer.T__45, "'!limit'"),
    OFFSET(DtoLexer.T__46, "'!offset'"),
    BATCH(DtoLexer.T__47, "'!batch'"),
    DEPTH(DtoLexer.T__48, "'!depth'"),
    AT(DtoLexer.T__49, "'@'"),
    LEFT_BRACKET(DtoLexer.T__50, "'['"),
    RIGHT_BRACKET(DtoLexer.T__51, "']'"),
    COLON_COLON(DtoLexer.T__52, "'::'"),
    CLASS(DtoLexer.T__53, "'class'");

    fun literal(): String {
        return literal.substring(1, literal.length - 1)
    }
}