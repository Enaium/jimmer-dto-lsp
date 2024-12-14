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

package cn.enaium.jimmer.dto.lsp.service

import cn.enaium.jimmer.dto.lsp.DocumentManager
import cn.enaium.jimmer.dto.lsp.range
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.babyfish.jimmer.dto.compiler.DtoLexer
import org.babyfish.jimmer.dto.compiler.DtoModifier
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentSemanticTokensFullService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {
    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val document = documentManager.getDocument(params.textDocument.uri) ?: return CompletableFuture.completedFuture(
            SemanticTokens()
        )
        val lexer = DtoLexer(CharStreams.fromString(document.content))
        val commonToken = CommonTokenStream(lexer)
        commonToken.fill()
        val data = mutableListOf<Int>()
        var previousLine = 0
        var previousChar = 0
        val tokens = commonToken.tokens
        tokens.forEachIndexed { tokenIndex, token ->
            when (token.type) {
                DtoLexer.DocComment -> {
                    token.text.split("\n").forEachIndexed { index, s ->
                        val start = token.range().start
                        val currentLine = start.line + index
                        val currentChar = if (index == 0) start.character else 0
                        data.add(currentLine - previousLine)
                        data.add(currentChar)
                        data.add(s.length)
                        data.add(0)
                        data.add(0)
                        previousLine = currentLine
                        previousChar = currentChar
                    }
                }
                // export, package, import, as, fixed, static, dynamic, fuzzy, implements, ?, !, ^, $, *, -, class
                DtoLexer.T__0, DtoLexer.T__3, DtoLexer.T__4, DtoLexer.T__8, DtoLexer.T__9,
                DtoLexer.T__10, DtoLexer.T__11, DtoLexer.T__12, DtoLexer.T__13, DtoLexer.T__18,
                DtoLexer.T__19, DtoLexer.T__20, DtoLexer.T__21, DtoLexer.T__24, DtoLexer.T__25,
                DtoLexer.T__34 -> {
                    val start = token.range().start
                    data.add(start.line - previousLine)
                    data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                    data.add(token.text.length)
                    data.add(1)
                    data.add(0)
                    previousLine = start.line
                    previousChar = start.character
                }
                // #, @
                DtoLexer.T__15, DtoLexer.T__29 -> {
                    val nextToken = tokens.getOrNull(token.tokenIndex + 1)
                    if (nextToken?.type == DtoLexer.Identifier) {
                        val start = token.range().start
                        data.add(start.line - previousLine)
                        data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                        data.add(token.text.length + nextToken.text.length)
                        data.add(if (token.type == DtoLexer.T__29) 2 else 5)
                        data.add(0)
                        previousLine = start.line
                        previousChar = start.character
                    }
                }

                DtoLexer.StringLiteral -> {
                    val start = token.range().start
                    data.add(start.line - previousLine)
                    data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                    data.add(token.text.length)
                    data.add(3)
                    data.add(0)
                    previousLine = start.line
                    previousChar = start.character
                }

                DtoLexer.IntegerLiteral, DtoLexer.FloatingPointLiteral -> {
                    val start = token.range().start
                    data.add(start.line - previousLine)
                    data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                    data.add(token.text.length)
                    data.add(4)
                    data.add(0)
                    previousLine = start.line
                    previousChar = start.character
                }

                DtoLexer.Identifier -> run {
                    tokens.subList(tokenIndex, commonToken.size()).forEach {
                        if (tokens.getOrNull(tokenIndex - 1)?.type != DtoLexer.T__29
                            && it.type == DtoLexer.T__16
                            && (it.range().start.line == token.range().start.line || tokens.getOrNull(tokenIndex + 1)?.type == DtoLexer.T__16)
                        ) {
                            val start = token.range().start
                            data.add(start.line - previousLine)
                            data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                            data.add(token.text.length)
                            data.add(2)
                            data.add(0)
                            previousLine = start.line
                            previousChar = start.character
                            return@run
                        }
                    }

                    document.ast.dtoTypes.forEach { dtoType ->
                        dtoType.modifiers.forEach { modifier ->
                            if (modifier.isDtoModifier() && token.range() == modifier.range()) {
                                val start = token.range().start
                                data.add(start.line - previousLine)
                                data.add(if (previousLine == start.line) start.character - previousChar else start.character)
                                data.add(token.text.length)
                                data.add(1)
                                data.add(0)
                                previousLine = start.line
                                previousChar = start.character
                                return@run
                            }
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(/* value = */ SemanticTokens(/* data = */ data))
    }

    private fun Token.isDtoModifier(): Boolean {
        return DtoModifier.entries.map { it.name.lowercase() }.contains(text)
    }
}