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
import cn.enaium.jimmer.dto.lsp.utility.SemanticType
import cn.enaium.jimmer.dto.lsp.utility.TokenType
import cn.enaium.jimmer.dto.lsp.utility.literal
import cn.enaium.jimmer.dto.lsp.utility.range
import org.antlr.v4.runtime.Token
import org.babyfish.jimmer.dto.compiler.DtoLexer
import org.babyfish.jimmer.dto.compiler.DtoParser
import org.babyfish.jimmer.dto.compiler.DtoParser.AnnotationContext
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentSemanticTokensFullService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {

    private val tokenToType = mutableListOf<Pair<Token, SemanticType>>()

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(SemanticTokens())
        val data = mutableListOf<Int>()
        tokenToType.clear()

        var previousLine = 0
        var previousChar = 0
        val tokens = document.realTime.commonToken.tokens
        tokens.forEach { token ->
            when (token.type) {
                DtoLexer.DocComment, DtoLexer.BlockComment, DtoLexer.LineComment -> {
                    addToken(token, SemanticType.COMMENT)
                }

                DtoLexer.CharacterLiteral, DtoLexer.StringLiteral -> {
                    addToken(token, SemanticType.STRING)
                }

                DtoLexer.IntegerLiteral, DtoLexer.FloatingPointLiteral -> {
                    addToken(token, SemanticType.NUMBER)
                }

                TokenType.PACKAGE.id, TokenType.IMPLEMENTS.id, TokenType.AS.id -> {
                    addToken(token, SemanticType.KEYWORD)
                }
            }
        }

        fun addData(token: Token, semanticType: SemanticType) {
            token.text.split("\n").forEachIndexed { index, s ->
                val start = token.range().start
                val currentLine = start.line + index
                val currentChar = if (index == 0) start.character else 0
                data.add(currentLine - previousLine)
                data.add(if (previousLine == currentLine) currentChar - previousChar else currentChar)
                data.add(s.length)
                data.add(semanticType.id)
                data.add(0)
                previousLine = currentLine
                previousChar = currentChar
            }
        }

        val ast = document.realTime.ast
        ast.exportStatement()?.also { exportStatement ->
            val keyword = exportStatement.start
            if (keyword.literal() == TokenType.EXPORT.literal) {
                addToken(keyword, SemanticType.KEYWORD)
            }
            mutableListOf(exportStatement.typeParts).removeLast().forEach { part ->
                addToken(part, SemanticType.NAMESPACE)
            }
            addToken(exportStatement.typeParts.last(), SemanticType.TYPE)
            exportStatement.packageParts.forEach { part ->
                addToken(part, SemanticType.NAMESPACE)
            }
        }
        ast.importStatement().forEach { importStatement ->
            val keyword = importStatement.start
            if (keyword.literal() == TokenType.IMPORT.literal) {
                addToken(keyword, SemanticType.KEYWORD)
            }
            if (importStatement.importedTypes.isEmpty()) {
                mutableListOf(importStatement.parts).removeLast().forEach { part ->
                    addToken(part, SemanticType.NAMESPACE)
                }
                addToken(importStatement.parts.last(), SemanticType.TYPE)
            } else {
                importStatement.parts.forEach { part ->
                    addToken(part, SemanticType.NAMESPACE)
                }
                importStatement.importedTypes.forEach { importedType ->
                    addToken(importedType.name, SemanticType.TYPE)
                }
            }
        }

        ast.dtoTypes.forEach { dtoType ->
            annotations(dtoType.annotations)
            dtoType.modifiers.forEach { modifier ->
                addToken(modifier, SemanticType.KEYWORD)
            }
            addToken(dtoType.name, SemanticType.STRUCT)
            dtoType.superInterfaces.forEach { superInterface ->
                typeRef(superInterface)
            }
            dtoType.dtoBody()?.also { dtoBody ->
                body(dtoBody)
            }
        }

        tokenToType.sortedBy { it.first.tokenIndex }.forEach { (token, semanticType) ->
            addData(token, semanticType)
        }

        return CompletableFuture.completedFuture(SemanticTokens(data))
    }

    private fun addToken(token: Token?, semanticType: SemanticType) {
        token?.also {
            tokenToType.add(it to semanticType)
        }
    }

    private fun annotationArrayValue(annotationArrayValue: DtoParser.AnnotationArrayValueContext) {
        annotationArrayValue.annotationSingleValue()?.forEach { annotationSingleValue ->
            annotationSingleValue(annotationSingleValue)
        }
    }

    private fun annotationSingleValue(annotationSingleValue: DtoParser.AnnotationSingleValueContext) {
        annotationSingleValue.nestedAnnotation()?.also { nestedAnnotation ->
            nestedAnnotation.qualifiedName()?.parts?.forEach { part ->
                addToken(part, SemanticType.DECORATOR)
            }
            nestedAnnotation.annotationArguments()?.also { annotationArguments(it) }
        }
        annotationSingleValue.qualifiedName()?.parts?.forEach { part ->
            addToken(part, SemanticType.TYPE)
        }
        annotationSingleValue.classSuffix()?.also { classSuffix ->
            addToken(classSuffix.stop, SemanticType.KEYWORD)
        }
    }

    private fun annotationArguments(annotationArguments: DtoParser.AnnotationArgumentsContext) {
        annotationArguments.namedArguments?.forEach { namedArgument ->
            addToken(namedArgument.name, SemanticType.PARAMETER)
            namedArgument.annotationValue()?.also { annotationValue ->
                annotationValue.annotationArrayValue()?.also { annotationArrayValue ->
                    annotationArrayValue(annotationArrayValue)
                }

                annotationValue.annotationSingleValue()?.also { annotationSingleValue ->
                    annotationSingleValue(annotationSingleValue)
                }
            }
        }
        annotationArguments.annotationValue()?.also { annotationValue ->
            annotationValue.annotationArrayValue()?.also { annotationArrayValue ->
                annotationArrayValue(annotationArrayValue)
            }

            annotationValue.annotationSingleValue()?.also { annotationSingleValue ->
                annotationSingleValue(annotationSingleValue)
            }
        }
    }

    private fun annotations(annotations: List<AnnotationContext>) {
        annotations.forEach { annotation ->
            addToken(annotation.start, SemanticType.DECORATOR)
            annotation.qualifiedName().parts.forEach { part ->
                addToken(part, SemanticType.DECORATOR)
            }
            annotation.annotationArguments()?.also {
                annotationArguments(it)
            }
        }
    }

    private fun typeRef(typeRef: DtoParser.TypeRefContext) {
        typeRef.optional?.also { optional ->
            addToken(optional, SemanticType.KEYWORD)
        }
        typeRef.qualifiedName().parts.forEach { part ->
            addToken(part, SemanticType.TYPE_PARAMETER)
        }
        typeRef.genericArguments.forEach { genericArgument ->
            genericArgument.typeRef()?.also { typeRef(it) }
        }
        typeRef.genericArgument?.also { genericArgument ->
            genericArgument.wildcard?.also { wildcard ->
                addToken(wildcard, SemanticType.KEYWORD)
            }
            genericArgument.modifier?.also { modifier ->
                addToken(modifier, SemanticType.KEYWORD)
            }
        }
    }

    private fun macro(macro: DtoParser.MicroContext) {
        addToken(macro.start, SemanticType.MACRO)
        addToken(macro.name, SemanticType.MACRO)
        macro.optional?.also { optional ->
            addToken(optional, SemanticType.KEYWORD)
        }
        macro.required?.also { required ->
            addToken(required, SemanticType.KEYWORD)
        }
        macro.qualifiedName()?.forEach {
            it.parts.forEach { part ->
                addToken(part, SemanticType.PARAMETER)
            }
        }
    }

    private fun positiveProp(positiveProp: DtoParser.PositivePropContext) {
        positiveProp.props.forEach {
            addToken(it, SemanticType.PROPERTY)
        }
        annotations(positiveProp.annotations)
        annotations(positiveProp.bodyAnnotations)
        addToken(positiveProp.modifier, SemanticType.KEYWORD)
        addToken(positiveProp.optional, SemanticType.KEYWORD)
        addToken(positiveProp.required, SemanticType.KEYWORD)
        addToken(positiveProp.recursive, SemanticType.KEYWORD)
        addToken(positiveProp.func, SemanticType.FUNCTION)
        addToken(positiveProp.flag, SemanticType.KEYWORD)
        addToken(positiveProp.insensitive, SemanticType.FUNCTION)
        addToken(positiveProp.prefix, SemanticType.FUNCTION)
        addToken(positiveProp.suffix, SemanticType.FUNCTION)
        addToken(positiveProp.alias, SemanticType.VARIABLE)
        positiveProp.bodySuperInterfaces.forEach { superInterface ->
            typeRef(superInterface)
        }
        positiveProp.enumBody()?.also { enumBody ->
            enumBody.enumMapping()?.forEach { enumMapping ->
                addToken(enumMapping.constant, SemanticType.ENUM_MEMBER)
            }
        }
        positiveProp.dtoBody()?.also { dtoBody ->
            body(dtoBody)
        }
    }

    private fun body(body: DtoParser.DtoBodyContext) {
        body.explicitProps.forEach { prop ->
            prop.micro()?.also { macro ->
                macro(macro)
            }
            prop.positiveProp()?.also { positiveProp ->
                positiveProp(positiveProp)
            }
            prop.negativeProp()?.also { negativeProp ->
                addToken(negativeProp.start, SemanticType.KEYWORD)
                addToken(negativeProp.prop, SemanticType.PROPERTY)
            }
            prop.userProp()?.also {
                addToken(it.prop, SemanticType.PROPERTY)
                annotations(it.annotations)
                it.typeRef()?.also { typeRef ->
                    typeRef(typeRef)
                }
            }
            prop.aliasGroup()?.also { aliasGroup ->
                aliasGroup.pattern?.also { pattern ->
                    addToken(pattern.prefix, SemanticType.KEYWORD)
                    addToken(pattern.suffix, SemanticType.KEYWORD)
                    addToken(pattern.original, SemanticType.VARIABLE)
                    addToken(pattern.replacement, SemanticType.VARIABLE)
                    addToken(pattern.translator, SemanticType.FUNCTION)
                }
                aliasGroup.props.forEach { alias ->
                    alias.micro()?.also { micro ->
                        macro(micro)
                    }
                    alias.positiveProp()?.also { positiveProp ->
                        positiveProp(positiveProp)
                    }
                }
            }
        }
    }
}