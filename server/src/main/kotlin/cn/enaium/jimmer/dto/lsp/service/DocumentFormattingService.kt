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
import cn.enaium.jimmer.dto.lsp.utility.TokenType
import cn.enaium.jimmer.dto.lsp.utility.position
import org.babyfish.jimmer.dto.compiler.DtoParser
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentFormattingService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {

    private val table = "    "
    private val space = " "
    private val enter = "\n"

    private val textEdits = mutableListOf<TextEdit>()

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
        val document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(emptyList())
        textEdits.clear()

        val ast = document.realTime.ast
        ast.exportStatement()?.also { exportStatement ->
            exportStatement(exportStatement)
        }
        ast.importStatement()?.also { importStatements ->
            importStatements(importStatements)
        }

        ast.dtoTypes.forEach { dtoType ->
            dtoType(dtoType)
        }

        return CompletableFuture.completedFuture(textEdits)
    }

    private fun exportStatement(exportStatement: DtoParser.ExportStatementContext) {
        val start = Position()
        var stop = Position()
        var text = ""

        if (exportStatement.typeParts.isNotEmpty()) {
            stop = exportStatement.typeParts.last().position(textLength = true)
            text += "${TokenType.EXPORT.literal()} ${exportStatement.typeParts.joinToString(TokenType.DOT.literal()) { it.text }}"
        }

        if (exportStatement.packageParts.isNotEmpty()) {
            stop = exportStatement.packageParts.last().position(textLength = true)
            text += "\n    ${TokenType.ARROW.literal()} ${TokenType.PACKAGE.literal()} ${
                exportStatement.packageParts.joinToString(
                    TokenType.DOT.literal()
                ) { it.text }
            }"
        }

        textEdits.add(TextEdit(Range(start, stop), text))
    }

    private fun importStatements(importStatements: List<DtoParser.ImportStatementContext>) {
        if (importStatements.isNotEmpty()) {
            val imports = mutableListOf<Pair<String, String>>()

            importStatements.forEach { importStatement ->
                val parts = importStatement.parts
                if (importStatement.importedTypes.isEmpty()) {
                    imports.add(
                        parts.subList(0, parts.size - 1)
                            .joinToString(TokenType.DOT.literal()) { it.text } to parts.last().text)
                } else {
                    importStatement.importedTypes.forEach { importedType ->
                        imports.add(parts.joinToString(TokenType.DOT.literal()) { it.text } to importedType.text)
                    }
                }
            }

            textEdits.add(
                TextEdit(
                    Range(
                        importStatements.first().start.position(),
                        importStatements.last().stop.position(textLength = true)
                    ),
                    imports.groupBy({ it.first }) { it.second }.map {
                        "${TokenType.IMPORT.literal()} ${it.key}.${
                            if (it.value.count() == 1) {
                                it.value.first()
                            } else {
                                it.value.joinToString(
                                    "${TokenType.COMMA.literal()}$space",
                                    "${TokenType.LEFT_BRACE.literal()}$space",
                                    "$space${TokenType.RIGHT_BRACE.literal()}"
                                )
                            }
                        }"
                    }.joinToString(enter)
                )
            )
        }
    }

    private fun annotationArrayValue(
        annotationArrayValue: DtoParser.AnnotationArrayValueContext,
        indent: String
    ): String {
        var text = ""
        text += TokenType.LEFT_BRACKET.literal()
        text += enter
        text += indent
        text += annotationArrayValue.annotationSingleValue()
            .joinToString("${TokenType.COMMA.literal()}$enter$indent") { annotationSingleValue(it, table) }
        text += enter
        text += indent
        text += TokenType.RIGHT_BRACKET.literal()
        return text
    }

    private fun annotationSingleValue(
        annotationSingleValue: DtoParser.AnnotationSingleValueContext,
        indent: String
    ): String {
        var text = indent
        annotationSingleValue.nestedAnnotation()?.also { nestedAnnotation ->
            nestedAnnotation.qualifiedName()?.also { qualifiedName ->
                text += qualifiedName.parts.joinToString(TokenType.DOT.literal()) { it.text }
            }
            text += TokenType.LEFT_PARENTHESIS.literal()
            nestedAnnotation.annotationArguments()?.also {
                text += annotationArguments(it, "")
            }
            text += TokenType.RIGHT_PARENTHESIS.literal()
        }
        annotationSingleValue.qualifiedName()?.also { qualifiedName ->
            text += qualifiedName.parts.joinToString(TokenType.DOT.literal()) { it.text }
        }
        annotationSingleValue.booleanToken?.also { booleanToken ->
            text += booleanToken.text
        }
        annotationSingleValue.characterToken?.also { characterToken ->
            text += characterToken.text
        }
        annotationSingleValue.StringLiteral?.also { stringLiteral ->
            text += stringLiteral.text
        }
        annotationSingleValue.integerToken?.also { integerToken ->
            text += integerToken.text
        }
        annotationSingleValue.floatingPointToken?.also { floatingPointToken ->
            text += floatingPointToken.text
        }
        annotationSingleValue.classSuffix()?.also { classSuffix ->
            text += classSuffix.text
        }
        return text
    }

    private fun annotationArguments(annotationArguments: DtoParser.AnnotationArgumentsContext, indent: String): String {
        var text = ""
        val namedArguments = annotationArguments.annotationNamedArgument()
        annotationArguments.annotationValue()?.also { annotationValue ->
            annotationValue.annotationArrayValue()?.also { annotationArrayValue ->
                text += annotationArrayValue(annotationArrayValue, indent)
            }
            annotationValue.annotationSingleValue()?.also { annotationSingleValue ->
                text += annotationSingleValue(annotationSingleValue, "")
            }
            if (namedArguments.isNullOrEmpty().not()) {
                text += TokenType.COMMA.literal()
                text += space
            }
        }
        namedArguments?.forEach { namedArgument ->
            text += namedArgument.name.text
            namedArgument.annotationValue()?.also { annotationValue ->
                text += space
                text += TokenType.EQUAL.literal()
                text += space
                annotationValue.annotationArrayValue()?.also { annotationArrayValue ->
                    text += annotationArrayValue(annotationArrayValue, indent)
                }
                annotationValue.annotationSingleValue()?.also { annotationSingleValue ->
                    text += annotationSingleValue(annotationSingleValue, "")
                }
                if (namedArgument != namedArguments.last()) {
                    text += TokenType.COMMA.literal()
                    text += space
                }
            }
        }
        return text
    }

    private fun annotation(annotation: DtoParser.AnnotationContext, indent: String): String {
        var text = ""
        text += TokenType.AT.literal()
        annotation.qualifiedName()?.also { qualifiedName ->
            text += qualifiedName.parts.joinToString(TokenType.DOT.literal()) { it.text }
        }
        annotation.annotationArguments()?.also { annotationArguments ->
            text += TokenType.LEFT_PARENTHESIS.literal()
            text += annotationArguments(annotationArguments, indent)
            text += TokenType.RIGHT_PARENTHESIS.literal()
        }
        return text
    }

    private fun genericArgument(genericArgument: DtoParser.GenericArgumentContext): String {
        var text = ""
        genericArgument.modifier?.also {
            text += it.text
            text += space
        }
        genericArgument.typeRef()?.also { typeRef ->
            text += typeRef(typeRef, "")
        }
        genericArgument.wildcard?.also {
            text += it.text
        }
        return text
    }

    private fun typeRef(typeRef: DtoParser.TypeRefContext, indent: String): String {
        var text = indent
        typeRef.qualifiedName()?.also { qualifiedName ->
            text += qualifiedName.parts.joinToString(TokenType.DOT.literal()) { it.text }
        }
        typeRef.genericArguments.takeIf { it.isNotEmpty() }?.also { genericArguments ->
            text += TokenType.LESS_THAN.literal()
            genericArguments.forEach { genericArgument ->
                text += genericArgument(genericArgument)
                if (genericArgument != genericArguments.last()) {
                    text += TokenType.COMMA.literal()
                    text += space
                }
            }
            text += TokenType.GREATER_THAN.literal()
        }
        typeRef.optional?.also { optional ->
            text += TokenType.QUESTION_MARK.literal()
        }
        return text
    }

    private fun enumBody(enumBody: DtoParser.EnumBodyContext, indent: String): String {
        var text = ""
        enumBody.enumMapping()?.forEach { enumMapping ->
            text += indent
            text += enumMapping.constant.text
            enumMapping.value?.also { value ->
                text += TokenType.COLON.literal()
                text += space
                text += value.text
            }
            if (enumMapping != enumBody.enumMapping().last()) {
                text += enter
            }
        }
        return text
    }

    private fun positiveProp(positiveProp: DtoParser.PositivePropContext, indent: String): String {
        var text = indent
        positiveProp.doc?.also { doc ->
            text += doc.text
            text += enter
            text += indent
        }
        positiveProp.annotation()?.also { annotations ->
            annotations.filter { !positiveProp.bodyAnnotations.contains(it) }.takeIf { it.isNotEmpty() }
                ?.also { annotationsWithoutBody ->
                    text += annotationsWithoutBody.joinToString("$enter$indent") { annotation(it, indent) }
                    text += enter
                    text += indent
                }
        }
        positiveProp.modifier?.also { modifier ->
            text += modifier.text
            text += space
        }
        positiveProp.func?.also {
            text += positiveProp.func.text
            positiveProp.flag?.also { flag ->
                text += flag.text
            }
            positiveProp.insensitive?.also { insensitive ->
                text += insensitive.text
            }
            text += TokenType.LEFT_PARENTHESIS.literal()
            text += positiveProp.props.joinToString("${TokenType.COMMA.literal()}$space") { it.text }
            text += TokenType.RIGHT_PARENTHESIS.literal()
        } ?: positiveProp.props.forEach {
            text += it.text
        }
        positiveProp.optional?.also { optional ->
            text += TokenType.QUESTION_MARK.literal()
        }
        positiveProp.required?.also { required ->
            text += TokenType.EXCLAMATION_MARK.literal()
        }
        positiveProp.recursive?.also { recursive ->
            text += TokenType.ASTERISK.literal()
        }
        positiveProp.alias?.also { alias ->
            text += space
            text += TokenType.AS.literal()
            text += space
            text += alias.text
        }
        positiveProp.bodyAnnotations.takeIf { it.isNotEmpty() }?.also { bodyAnnotations ->
            text += space
            text += bodyAnnotations.joinToString("$enter$indent") { annotation(it, indent) }
        }
        positiveProp.typeRef()?.also { typeRef ->
            if (typeRef.isNotEmpty()) {
                text += space
                text += TokenType.IMPLEMENTS.literal()
                text += space
                text += typeRef.joinToString("${TokenType.COMMA.literal()}$space") { typeRef(it, "") }
            }
        }
        positiveProp.enumBody()?.also { enumBody ->
            text += space
            text += TokenType.ARROW.literal()
            text += space
            text += TokenType.LEFT_BRACE.literal()
            text += enter
            text += enumBody(enumBody, "$indent$table")
            text += enter
            text += indent
            text += TokenType.RIGHT_BRACE.literal()
        }
        positiveProp.dtoBody()?.also { dtoBody ->
            text += dtoBody(dtoBody, indent)
        }
        return text
    }

    private fun macro(macro: DtoParser.MacroContext, indent: String): String {
        var text = indent
        text += TokenType.HASH.literal()
        text += macro.name.text
        macro.args.takeIf { it.isNotEmpty() }?.also { args ->
            text += TokenType.LEFT_PARENTHESIS.literal()
            text += args.joinToString("${TokenType.COMMA.literal()}$space") { it.text }
            text += TokenType.RIGHT_PARENTHESIS.literal()
        }
        macro.required?.also { required ->
            text += TokenType.EXCLAMATION_MARK.literal()
        }
        macro.optional?.also { optional ->
            text += TokenType.QUESTION_MARK.literal()
        }
        return text
    }

    private fun userProp(userProp: DtoParser.UserPropContext, indent: String): String {
        var text = indent
        userProp.doc?.also { doc ->
            text += doc.text
            text += enter
            text += indent
        }
        userProp.prop?.also { prop ->
            text += prop.text
        }
        userProp.typeRef()?.also { typeRef ->
            text += TokenType.COLON.literal()
            text += space
            text += typeRef(typeRef, "")
        }
        return text
    }

    private fun negativeProp(negativeProp: DtoParser.NegativePropContext, indent: String): String {
        var text = indent
        negativeProp.prop?.also { prop ->
            text += TokenType.MINUS.literal()
            text += prop.text
        }
        return text
    }

    private fun aliasGroup(aliasGroup: DtoParser.AliasGroupContext, indent: String): String {
        var text = indent
        text += TokenType.AS.literal()
        text += TokenType.LEFT_PARENTHESIS.literal()
        aliasGroup.aliasPattern()?.also { pattern ->
            pattern.prefix?.also { prefix ->
                text += prefix.text
            }
            pattern.suffix?.also { suffix ->
                text += suffix.text
            }
            text += space
            pattern.translator?.also { translator ->
                text += translator.text
            }
            text += space
            pattern.replacement?.also { replacement ->
                text += replacement.text
            }
        }
        text += TokenType.RIGHT_PARENTHESIS.literal()
        aliasGroup.aliasGroupProp()?.also { aliasGroupProp ->
            text += space
            text += TokenType.LEFT_BRACE.literal()
            text += enter
            aliasGroupProp.forEach { prop ->
                prop.positiveProp()?.also { positiveProp ->
                    text += positiveProp(positiveProp, "$indent    ")
                }
                prop.macro()?.also { macro ->
                    text += macro(macro, "$indent    ")
                }
                if (prop != aliasGroupProp.last()) {
                    text += enter
                    text += enter
                }
            }
            text += enter
            text += indent
            text += TokenType.RIGHT_BRACE.literal()
        }
        return text
    }

    private fun dtoBody(dtoBody: DtoParser.DtoBodyContext, indent: String): String {
        var text = ""
        text += space
        text += TokenType.LEFT_BRACE.literal()
        text += enter
        val explicitProps = dtoBody.explicitProps
        explicitProps.takeIf { it.isNotEmpty() }?.forEach { explicitProp ->
            explicitProp.positiveProp()?.also { positiveProp ->
                text += positiveProp(positiveProp, "$indent$table")
            }
            explicitProp.userProp()?.also { userProp ->
                text += userProp(userProp, "$indent$table")
            }
            explicitProp.negativeProp()?.also { negativeProp ->
                text += negativeProp(negativeProp, "$indent$table")
            }
            explicitProp.macro()?.also { macro ->
                text += macro(macro, "$indent$table")
            }
            explicitProp.aliasGroup()?.also { aliasGroup ->
                text += aliasGroup(aliasGroup, "$indent$table")
            }
            if (explicitProp != explicitProps.last()) {
                text += enter
                text += enter
            }
        }
        text += enter
        text += indent
        text += TokenType.RIGHT_BRACE.literal()
        return text
    }

    private fun dtoType(dtoType: DtoParser.DtoTypeContext) {
        val range = Range(
            dtoType.start.position(),
            dtoType.stop.position(textLength = true)
        )

        var text = ""
        dtoType.doc?.also { doc ->
            text += doc.text
            text += enter
        }
        dtoType.annotation()?.takeIf { it.isNotEmpty() }?.also { annotations ->
            text += annotations.joinToString(enter) { annotation(it, "") }
            text += enter
        }
        dtoType.modifiers.forEach { modifier ->
            text += modifier.text
            text += space
        }
        text += dtoType.name.text
        dtoType.superInterfaces.takeIf { it.isNotEmpty() }?.also { superInterfaces ->
            text += space
            text += "${TokenType.IMPLEMENTS.literal()} ${
                superInterfaces.joinToString("${TokenType.COMMA.literal()}$space") {
                    typeRef(
                        it,
                        ""
                    )
                }
            }"
        }
        dtoType.dtoBody()?.also { dtoBody ->
            text += dtoBody(dtoBody, "")
        }
        textEdits.add(TextEdit(range, text))
    }
}