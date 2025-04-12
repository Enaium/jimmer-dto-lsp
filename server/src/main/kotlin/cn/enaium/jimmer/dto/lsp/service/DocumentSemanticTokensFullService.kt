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
import org.babyfish.jimmer.dto.compiler.DtoModifier
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

                TokenType.PACKAGE.id, TokenType.IMPLEMENTS.id, TokenType.AS.id, TokenType.NULL.id,
                TokenType.OR.id, TokenType.AND.id, TokenType.IS.id -> {
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
                if (DtoModifier.entries.map { it.name.lowercase() }.contains(modifier.text)) {
                    addToken(modifier, SemanticType.KEYWORD)
                }
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

    private fun macro(macro: DtoParser.MacroContext) {
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
        positiveProp.configuration()?.takeIf { it.isNotEmpty() }?.forEach { configuration ->
            configuration(configuration)
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

    private fun cmpPredicate(cmpPredicate: DtoParser.CmpPredicateContext) {
        cmpPredicate.propPath()?.also { propPath ->
            propPath(propPath)
        }
        cmpPredicate.propValue()?.also { propValue ->
            propValue.SqlStringLiteral()?.also {
                addToken(it.symbol, SemanticType.STRING)
            }
        }
        cmpPredicate.op?.also { op ->
            addToken(op, SemanticType.KEYWORD)
        }
    }

    private fun nullityPredicate(nullityPredicate: DtoParser.NullityPredicateContext) {
        nullityPredicate.propPath()?.also { propPath ->
            propPath(propPath)
        }
        nullityPredicate.not?.also { not ->
            addToken(not, SemanticType.KEYWORD)
        }
    }

    private fun atomPredicate(atomPredicate: DtoParser.AtomPredicateContext) {
        atomPredicate.predicate()?.also {
            predicate(it)
        }
        atomPredicate.cmpPredicate()?.also { cmpPredicate ->
            cmpPredicate(cmpPredicate)
        }
        atomPredicate.nullityPredicate()?.also { nullityPredicate ->
            nullityPredicate(nullityPredicate)
        }
    }

    private fun andPredicate(andPredicate: DtoParser.AndPredicateContext) {
        andPredicate.atomPredicate()?.takeIf { it.isNotEmpty() }?.forEach { atomPredicate ->
            atomPredicate(atomPredicate)
        }
    }

    private fun predicate(predicate: DtoParser.PredicateContext) {
        predicate.andPredicate()?.takeIf { it.isNotEmpty() }?.forEach { andPredicate ->
            andPredicate(andPredicate)
        }
    }

    private fun where(where: DtoParser.WhereContext) {
        addToken(where.start, SemanticType.MACRO)
        where.predicate()?.also { predicate ->
            predicate(predicate)
        }
    }

    private fun propPath(propPath: DtoParser.PropPathContext) {
        propPath.parts.forEach { part ->
            addToken(part, SemanticType.PROPERTY)
        }
    }

    private fun orderByItem(orderByItem: DtoParser.OrderByItemContext) {
        orderByItem.propPath()?.also { propPath ->
            propPath(propPath)
        }
    }

    private fun orderBy(orderBy: DtoParser.OrderByContext) {
        addToken(orderBy.start, SemanticType.MACRO)
        orderBy.orderByItem()?.takeIf { it.isNotEmpty() }?.forEach { orderByItem ->
            orderByItem(orderByItem)
        }
    }

    private fun filter(filter: DtoParser.FilterContext) {
        addToken(filter.start, SemanticType.MACRO)
        filter.qualifiedName()?.also { qualifiedName ->
            qualifiedName.parts.forEach { part ->
                addToken(part, SemanticType.TYPE)
            }
        }
    }

    private fun recursion(recursion: DtoParser.RecursionContext) {
        addToken(recursion.start, SemanticType.MACRO)
        recursion.qualifiedName()?.also { qualifiedName ->
            qualifiedName.parts.forEach { part ->
                addToken(part, SemanticType.TYPE)
            }
        }
    }

    private fun fetchType(fetchType: DtoParser.FetchTypeContext) {
        addToken(fetchType.start, SemanticType.MACRO)
        fetchType.fetchMode?.also { fetchMode ->
            addToken(fetchMode, SemanticType.ENUM_MEMBER)
        }
    }

    private fun limit(limit: DtoParser.LimitContext) {
        addToken(limit.start, SemanticType.MACRO)
    }

    private fun batch(batch: DtoParser.BatchContext) {
        addToken(batch.start, SemanticType.MACRO)
    }

    private fun recursionDepth(recursionDepth: DtoParser.RecursionDepthContext) {
        addToken(recursionDepth.start, SemanticType.MACRO)
    }

    private fun configuration(configuration: DtoParser.ConfigurationContext) {
        configuration.where()?.also { where ->
            where(where)
        }
        configuration.orderBy()?.also { orderBy ->
            orderBy(orderBy)
        }
        configuration.filter()?.also { filter ->
            filter(filter)
        }
        configuration.recursion()?.also { recursion ->
            recursion(recursion)
        }
        configuration.fetchType()?.also { fetchType ->
            fetchType(fetchType)
        }
        configuration.limit()?.also { limit ->
            limit(limit)
        }
        configuration.batch()?.also { batch ->
            batch(batch)
        }
        configuration.recursionDepth()?.also { recursionDepth ->
            recursionDepth(recursionDepth)
        }
    }

    private fun body(body: DtoParser.DtoBodyContext) {
        body.macro()?.takeIf { it.isNotEmpty() }?.forEach { macro ->
            macro(macro)
        }
        body.explicitProps.forEach { prop ->
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
                aliasGroup.positiveProp()?.takeIf { it.isNotEmpty() }?.forEach { alias ->
                    positiveProp(alias)
                }
                aliasGroup.macro()?.takeIf { it.isNotEmpty() }?.forEach { macro ->
                    macro(macro)
                }
            }
        }
    }
}