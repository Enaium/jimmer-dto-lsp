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
import cn.enaium.jimmer.dto.lsp.DtoDocument
import cn.enaium.jimmer.dto.lsp.compiler.ImmutableProp
import cn.enaium.jimmer.dto.lsp.utility.*
import org.antlr.v4.runtime.Token
import org.babyfish.jimmer.dto.compiler.DtoParser
import org.eclipse.lsp4j.*
import java.util.concurrent.CompletableFuture

/**
 * @author Enaium
 */
class DocumentHoverService(documentManager: DocumentManager) : DocumentServiceAdapter(documentManager) {

    private var hover: Hover? = null
    private lateinit var params: HoverParams
    private lateinit var document: DtoDocument

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        document = documentManager.getDocument(params.textDocument.uri)
            ?: return CompletableFuture.completedFuture(null)

        hover = null
        this.params = params

        val ast = document.realTime.ast

        ast.exportStatement()?.also { exportStatement ->
            val range = Range(
                exportStatement.start.position(),
                exportStatement.stop.position(true)
            )
            if (range.overlaps(params.position)) {
                hover = Hover(
                    MarkupContent(
                        MarkupKind.MARKDOWN,
                        """
                        ## Export
                        `${exportStatement.typeParts.joinToString(".") { it.text }}`
                        ## Package
                        `${
                            exportStatement.getPackageName()
                        }`
                    """.trimIndent()
                    ), range
                )
            }
        }

        ast.importStatement()?.also { importStatements ->
            importStatements.forEach { importStatement ->
                val parts = importStatement.parts
                val range = Range(
                    importStatement.start.position(),
                    importStatement.stop.position(true)
                )
                if (range.overlaps(params.position)) {
                    var types = if (importStatement.importedTypes.isEmpty()) {
                        "`${mutableListOf(*parts.toTypedArray()).removeLast().text}`"
                    } else {
                        importStatement.importedTypes.joinToString(", ") { "`${it.text}`" }
                    }
                    var packageName = if (importStatement.importedTypes.isEmpty()) {
                        parts.removeLast()
                        parts.joinToString(".") { it.text }
                    } else {
                        importStatement.parts.joinToString(".") { it.text }
                    }

                    hover = Hover(
                        MarkupContent(
                            MarkupKind.MARKDOWN,
                            """
                            ## Import
                            `$packageName`
                            ## Types
                            $types
                        """.trimIndent()
                        ), range
                    )
                }
            }
        }

        ast.dtoTypes.forEach { dtoType ->
            dtoType.dtoBody()?.also { dtoBody ->
                body(dtoBody)
            }
        }

        return CompletableFuture.completedFuture(hover)
    }

    private fun macro(macro: DtoParser.MicroContext, pattern: DtoParser.AliasPatternContext? = null) {
        val macroRange = Range(
            macro.start.position(),
            macro.stop.position(true)
        )
        if (macroRange.overlaps(params.position)) {
            val props = document.getProps(params.position) ?: return
            val isAllReferences = macro.name.text == "allReferences"
            if (macro.args.isEmpty() || macro.args[0].text == "this") {
                hover = Hover(
                    MarkupContent(
                        MarkupKind.MARKDOWN,
                        """
                                ## ${macro.name.text}
                                ${
                            props.second.filter { if (isAllReferences) isAutoReference(it) else isAutoScalar(it) }
                                .joinToString {
                                    "`${
                                        pattern?.let { pattern ->
                                            pattern(
                                                it.name,
                                                pattern
                                            )
                                        } ?: it.name
                                    }`"
                                }
                        }
                            """.trimIndent()
                    ), macroRange
                )
            }
        }
    }

    private fun prop(
        token: Token,
        optional: Boolean = false,
        required: Boolean = false,
        negative: Boolean = false,
        alias: Token? = null,
        pattern: DtoParser.AliasPatternContext? = null
    ) {
        val range = Range(
            token.position(),
            token.position(true)
        )

        if (range.overlaps(params.position)) {
            val props = document.getProps(params.position)
            val prop = props?.second?.find { prop -> prop.name == token.text } ?: return
            hover = Hover(
                MarkupContent(
                    MarkupKind.MARKDOWN,
                    """
                        ## ${prop.name} ${
                        if (pattern != null) {
                            pattern(prop.name, pattern)
                        } else {
                            alias?.let { "`${it.text}`" } ?: ""
                        }
                    }
                        Trace: `${props.first}.${prop.name}`
                        
                        From: `${prop.declaringType.name}`
                        
                        Type: `${prop.type().description}`
                        
                    """.trimIndent() + if (optional) {
                        """
                        ## Optional
                        This property is optional
                    """.trimIndent()
                    } else if (required) {
                        """
                        ## Required
                        This property is required
                    """.trimIndent()
                    } else if (negative) {
                        """
                        ## Negative
                        This property is negative
                    """.trimIndent()
                    } else {
                        ""
                    }
                ), range
            )
        }
    }

    private fun positiveProp(
        positiveProp: DtoParser.PositivePropContext,
        pattern: DtoParser.AliasPatternContext? = null
    ) {

        positiveProp.props.forEach {
            prop(
                it,
                optional = positiveProp.optional != null,
                required = positiveProp.required != null,
                alias = positiveProp.alias,
                pattern = pattern
            )
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
                prop(negativeProp.prop, negative = true)
            }
            prop.aliasGroup()?.also { aliasGroup ->
                aliasGroup.pattern?.also { pattern ->
                    aliasGroup.props.forEach { alias ->
                        alias.micro()?.also { micro ->
                            macro(micro, pattern)
                        }
                        alias.positiveProp()?.also { positiveProp ->
                            positiveProp(positiveProp, pattern)
                        }
                    }
                }
            }
        }
    }

    private fun pattern(name: String, pattern: DtoParser.AliasPatternContext): String {
        val original = pattern.original
        val replace = pattern.replacement?.text ?: ""
        return if (pattern.prefix != null) {
            if (original == null) {
                "`${replace}${name.replaceFirstChar { it.uppercase() }}`"
            } else {
                "`${
                    name.replaceFirst(
                        original.text,
                        replace
                    )
                }`"
            }
        } else if (pattern.suffix != null) {
            if (original == null) {
                "`${name}${replace}`"
            } else {
                "`${
                    name.replaceFirst(
                        original.text,
                        replace
                    )
                }`"
            }
        } else {
            if (original != null) {
                "`${name.replaceFirst(original.text, replace)}`"
            } else {
                ""
            }
        }
    }

    private fun isAutoReference(baseProp: ImmutableProp): Boolean {
        return baseProp.isAssociation(true) && !baseProp.isList && !baseProp.isTransient
    }

    private fun isAutoScalar(baseProp: ImmutableProp): Boolean {
        return !baseProp.isFormula &&
                !baseProp.isTransient &&
                !baseProp.isIdView &&
                !baseProp.isManyToManyView &&
                !baseProp.isList &&
                !baseProp.isAssociation(true) &&
                !baseProp.isLogicalDeleted &&
                !baseProp.isExcludedFromAllScalars
    }
}