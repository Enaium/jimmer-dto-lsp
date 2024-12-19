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

    private val tokenToType = mutableMapOf<Token, SemanticType>()

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(SemanticTokens())
        val data = mutableListOf<Int>()
        tokenToType.clear()
        var previousLine = 0
        var previousChar = 0
        val tokens = document.commonToken.tokens
        tokens.forEach { token ->
            when (token.type) {
                DtoLexer.DocComment, DtoLexer.BlockComment, DtoLexer.LineComment -> {
                    tokenToType[token] = SemanticType.COMMENT
                }

                DtoLexer.CharacterLiteral, DtoLexer.StringLiteral -> {
                    tokenToType[token] = SemanticType.STRING
                }

                DtoLexer.IntegerLiteral, DtoLexer.FloatingPointLiteral -> {
                    tokenToType[token] = SemanticType.NUMBER
                }

                TokenType.PACKAGE.id, TokenType.IMPLEMENTS.id, TokenType.AS.id -> {
                    tokenToType[token] = SemanticType.KEYWORD
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

        val ast = document.ast
        ast.exportStatement()?.also { exportStatement ->
            val keyword = exportStatement.start
            if (keyword.literal() == TokenType.EXPORT.literal) {
                tokenToType[keyword] = SemanticType.KEYWORD
            }
            mutableListOf(exportStatement.typeParts).removeLast().forEach { part ->
                tokenToType[part] = SemanticType.NAMESPACE
            }
            tokenToType[exportStatement.typeParts.last()] = SemanticType.TYPE
            exportStatement.packageParts.forEach { part ->
                tokenToType[part] = SemanticType.NAMESPACE
            }
        }
        ast.importStatement().forEach { importStatement ->
            val keyword = importStatement.start
            if (keyword.literal() == TokenType.IMPORT.literal) {
                tokenToType[keyword] = SemanticType.KEYWORD
            }
            if (importStatement.importedTypes.isEmpty()) {
                mutableListOf(importStatement.parts).removeLast().forEach { part ->
                    tokenToType[part] = SemanticType.NAMESPACE
                }
                tokenToType[importStatement.parts.last()] = SemanticType.TYPE
            } else {
                importStatement.parts.forEach { part ->
                    tokenToType[part] = SemanticType.NAMESPACE
                }
                importStatement.importedTypes.forEach { importedType ->
                    tokenToType[importedType.name] = SemanticType.TYPE
                }
            }
        }

        ast.dtoTypes.forEach { dtoType ->
            annotations(dtoType.annotations)
            dtoType.modifiers.forEach { modifier ->
                tokenToType[modifier] = SemanticType.KEYWORD
            }
            tokenToType[dtoType.name] = SemanticType.STRUCT
            dtoType.superInterfaces.forEach { superInterface ->
                typeRef(superInterface)
            }
            body(dtoType.dtoBody())
        }

        tokenToType.toSortedMap(compareBy { it.tokenIndex }).forEach { (token, semanticType) ->
            addData(token, semanticType)
        }

        return CompletableFuture.completedFuture(SemanticTokens(data))
    }

    private fun annotationArrayValue(annotationArrayValue: DtoParser.AnnotationArrayValueContext) {
        annotationArrayValue.annotationSingleValue()?.forEach { annotationSingleValue ->
            annotationSingleValue(annotationSingleValue)
        }
    }

    private fun annotationSingleValue(annotationSingleValue: DtoParser.AnnotationSingleValueContext) {
        annotationSingleValue.nestedAnnotation()?.also { nestedAnnotation ->
            nestedAnnotation.qualifiedName()?.parts?.forEach { part ->
                tokenToType[part] = SemanticType.DECORATOR
            }
            nestedAnnotation.annotationArguments()?.also { annotationArguments(it) }
        }
        annotationSingleValue.qualifiedName()?.parts?.forEach { part ->
            tokenToType[part] = SemanticType.TYPE
        }
        annotationSingleValue.classSuffix()?.also { classSuffix ->
            tokenToType[classSuffix.stop] = SemanticType.KEYWORD
        }
    }

    private fun annotationArguments(annotationArguments: DtoParser.AnnotationArgumentsContext) {
        annotationArguments.namedArguments?.forEach { namedArgument ->
            tokenToType[namedArgument.name] = SemanticType.PARAMETER
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
            tokenToType[annotation.start] = SemanticType.DECORATOR
            annotation.qualifiedName().parts.forEach { part ->
                tokenToType[part] = SemanticType.DECORATOR
            }
            annotation.annotationArguments()?.also {
                annotationArguments(it)
            }
        }
    }

    private fun typeRef(typeRef: DtoParser.TypeRefContext) {
        typeRef.optional?.also { optional ->
            tokenToType[optional] = SemanticType.KEYWORD
        }
        typeRef.qualifiedName().parts.forEach { part ->
            tokenToType[part] = SemanticType.TYPE_PARAMETER
        }
        typeRef.genericArguments.forEach { genericArgument ->
            genericArgument.typeRef()?.also { typeRef(it) }
        }
        typeRef.genericArgument?.also { genericArgument ->
            genericArgument.wildcard?.also { wildcard ->
                tokenToType[wildcard] = SemanticType.KEYWORD
            }
            genericArgument.modifier?.also { modifier ->
                tokenToType[modifier] = SemanticType.KEYWORD
            }
        }
    }

    private fun macro(macro: DtoParser.MicroContext) {
        tokenToType[macro.start] = SemanticType.MACRO
        tokenToType[macro.name] = SemanticType.MACRO
        macro.optional?.also { optional ->
            tokenToType[optional] = SemanticType.KEYWORD
        }
        macro.required?.also { required ->
            tokenToType[required] = SemanticType.KEYWORD
        }
        macro.qualifiedName()?.forEach {
            it.parts.forEach { part ->
                tokenToType[part] = SemanticType.PARAMETER
            }
        }
    }

    private fun positiveProp(positiveProp: DtoParser.PositivePropContext) {
        positiveProp.props.forEach {
            tokenToType[it] = SemanticType.PROPERTY
        }
        annotations(positiveProp.annotations)
        annotations(positiveProp.bodyAnnotations)
        positiveProp.modifier?.also { modifier ->
            tokenToType[modifier] = SemanticType.KEYWORD
        }
        positiveProp.optional?.also { optional ->
            tokenToType[optional] = SemanticType.KEYWORD
        }
        positiveProp.required?.also { required ->
            tokenToType[required] = SemanticType.KEYWORD
        }
        positiveProp.recursive?.also { recursive ->
            tokenToType[recursive] = SemanticType.KEYWORD
        }
        positiveProp.func?.also { func ->
            tokenToType[func] = SemanticType.FUNCTION
        }
        positiveProp.flag?.also { flag ->
            tokenToType[flag] = SemanticType.KEYWORD
        }
        positiveProp.insensitive?.also { insensitive ->
            tokenToType[insensitive] = SemanticType.FUNCTION
        }
        positiveProp.prefix?.also { prefix ->
            tokenToType[prefix] = SemanticType.FUNCTION
        }
        positiveProp.suffix?.also { suffix ->
            tokenToType[suffix] = SemanticType.FUNCTION
        }
        positiveProp.alias?.also { alias ->
            tokenToType[alias] = SemanticType.VARIABLE
        }
        positiveProp.bodySuperInterfaces.forEach { superInterface ->
            typeRef(superInterface)
        }
        positiveProp.enumBody()?.also { enumBody ->
            enumBody.enumMapping()?.forEach { enumMapping ->
                tokenToType[enumMapping.constant] = SemanticType.ENUM_MEMBER
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
                tokenToType[negativeProp.start] = SemanticType.KEYWORD
            }
            prop.userProp()?.also {
                tokenToType[it.prop] = SemanticType.PROPERTY
                annotations(it.annotations)
                it.typeRef()?.also { typeRef ->
                    typeRef(typeRef)
                }
            }
            prop.aliasGroup()?.also { aliasGroup ->
                aliasGroup.pattern?.also { pattern ->
                    pattern.prefix?.also { prefix ->
                        tokenToType[prefix] = SemanticType.KEYWORD
                    }
                    pattern.suffix?.also { suffix ->
                        tokenToType[suffix] = SemanticType.KEYWORD
                    }
                    pattern.original?.also { original ->
                        tokenToType[original] = SemanticType.VARIABLE
                    }
                    pattern.replacement?.also { replacement ->
                        tokenToType[replacement] = SemanticType.VARIABLE
                    }
                    pattern.translator?.also { translator ->
                        tokenToType[translator] = SemanticType.FUNCTION
                    }
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