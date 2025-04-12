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
    EQUAL(DtoLexer.T__28, "'='"),
    LESS_THAN(DtoLexer.T__29, "'<'"),
    GREATER_THAN(DtoLexer.T__30, "'>'"),
    WHERE(DtoLexer.T__31, "'!where'"),
    OR(DtoLexer.T__32, "'or'"),
    AND(DtoLexer.T__33, "'and'"),
    DIAMOND(DtoLexer.T__34, "'<>'"),
    NOT_EQUAL(DtoLexer.T__35, "'!='"),
    LESS_THAN_OR_EQUAL(DtoLexer.T__36, "'<='"),
    GREATER_THAN_OR_EQUAL(DtoLexer.T__37, "'>='"),
    IS(DtoLexer.T__38, "'is'"),
    NOT(DtoLexer.T__39, "'not'"),
    ORDER_BY(DtoLexer.T__40, "'!orderBy'"),
    FILTER(DtoLexer.T__41, "'!filter'"),
    RECURSION(DtoLexer.T__42, "'!recursion'"),
    FETCH_TYPE(DtoLexer.T__43, "'!fetchType'"),
    LIMIT(DtoLexer.T__44, "'!limit'"),
    BATCH(DtoLexer.T__45, "'!batch'"),
    DEPTH(DtoLexer.T__46, "'!depth'"),
    AT(DtoLexer.T__47, "'@'"),
    LEFT_BRACKET(DtoLexer.T__48, "'['"),
    RIGHT_BRACKET(DtoLexer.T__49, "']'"),
    COLON_COLON(DtoLexer.T__50, "'::'"),
    CLASS(DtoLexer.T__51, "'class'");

    fun literal(): String {
        return literal.substring(1, literal.length - 1)
    }
}