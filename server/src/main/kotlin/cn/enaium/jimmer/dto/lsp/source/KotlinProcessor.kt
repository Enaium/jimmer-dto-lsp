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

package cn.enaium.jimmer.dto.lsp.source

import KotlinLexer
import KotlinParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

/**
 * @author Enaium
 */
class KotlinProcessor(val paths: List<Path>) : AbstractProcessor(paths) {
    override fun process(): List<Source> {
        val sourceFiles = paths.mapNotNull {
            if (it.isDirectory()) {
                it.walk().filter { it.extension == "kt" }.toList()
            } else if (it.extension == "kt") {
                listOf(it)
            } else {
                null
            }
        }.flatten()

        val sources = mutableListOf<Source>()
        sourceFiles.forEach { sourceFile ->
            val kotlinLexer =
                KotlinLexer(CharStreams.fromStream(sourceFile.inputStream()))
            val input = CommonTokenStream(kotlinLexer)
            input.fill()
            if (input.tokens
                    .any { needProcessedTokens.contains(it.text.substring(1)) }
                    .not()
            ) {
                return@forEach
            }
            val kotlinParser = KotlinParser(input)
            kotlinParser.errorListeners.clear()
            val kotlinFileContext = kotlinParser.kotlinFile()
            kotlinFileContext.topLevelObject().forEach { topLevelObjectContext ->
                if (topLevelObjectContext.classDeclaration() != null) {
                    val classDeclarationContext = topLevelObjectContext.classDeclaration()
                    val name = classDeclarationContext.simpleIdentifier().Identifier().symbol.text
                    val packageName = kotlinFileContext.preamble()?.packageHeader()?.identifier()
                        ?.simpleIdentifier()
                        ?.joinToString(".") { it.text } ?: ""
                    if (classDeclarationContext.modifierList()?.annotations()
                            ?.any { it ->
                                needProcessedTokens.contains(
                                    "${
                                        it.annotation()?.LabelReference()?.symbol?.text?.substring(1)
                                    }"
                                )
                            } == true
                    ) {
                        val immutable = Immutable(
                            name = name,
                            packageName = packageName,
                            file = sourceFile,
                            immutableType = if (classDeclarationContext.modifierList()?.annotations()?.any { it ->
                                    needProcessedTokens.contains(
                                        "${
                                            it.annotation()?.LabelReference()?.symbol?.text?.substring(1)
                                        }"
                                    )
                                } == true) {
                                var type = Immutable.ImmutableType.IMMUTABLE

                                classDeclarationContext.modifierList()?.annotations()?.forEach {
                                    when (it.annotation().LabelReference()?.symbol?.text?.substring(1)) {
                                        "Immutable" -> type = Immutable.ImmutableType.IMMUTABLE
                                        "Entity" -> type = Immutable.ImmutableType.ENTITY
                                        "Embeddable" -> type = Immutable.ImmutableType.EMBEDDABLE
                                        "MappedSuperclass" -> type = Immutable.ImmutableType.MAPPED_SUPERCLASS
                                    }
                                }

                                type
                            } else {
                                return@forEach
                            },
                            superTypes = classDeclarationContext.delegationSpecifiers()?.delegationSpecifier()
                                ?.flatMap { delegationSpecifierContext ->
                                    ((delegationSpecifierContext.userType()?.simpleUserType()
                                        ?.map { it.simpleIdentifier().Identifier().symbol.text }
                                        ?: emptyList())
                                            + (delegationSpecifierContext.constructorInvocation()?.userType()
                                        ?.simpleUserType()
                                        ?.map { it.simpleIdentifier().Identifier().symbol.text }
                                        ?: emptyList()))
                                } ?: emptyList(),
                            typeParameters = classDeclarationContext.typeParameters()?.typeParameter()
                                ?.map { it.simpleIdentifier().Identifier().symbol.text }
                                ?: emptyList()
                        )
                        classDeclarationContext.classBody()?.classMemberDeclaration()
                            ?.forEach { classMemberDeclarationContext ->
                                classMemberDeclarationContext.propertyDeclaration()
                                    ?.also { propertyDeclarationContext ->
                                        val annotations = propertyDeclarationContext.modifierList()?.annotations()
                                            ?.mapNotNull { annotationContext ->
                                                Immutable.Anno(
                                                    (annotationContext.annotation().LabelReference()?.symbol?.text
                                                        ?: annotationContext.annotation().unescapedAnnotation()
                                                            .identifier()
                                                            .simpleIdentifier().first()
                                                            .Identifier().symbol.text)?.substring(1)
                                                        ?: return@mapNotNull null
                                                )
                                            } ?: emptyList()
                                        val type =
                                            propertyDeclarationContext.variableDeclaration().type()?.text
                                                ?: return@forEach
                                        val prop = Immutable.Prop(
                                            propertyDeclarationContext.variableDeclaration().simpleIdentifier().text,
                                            type.let { if (it.endsWith("?")) it.substring(0, it.length - 1) else it },
                                            type.endsWith("?"),
                                            annotations
                                        )
                                        immutable.props += prop
                                    }
                            }
                        if (classDeclarationContext.classBody() == null) {
                            classDeclarationContext.delegationSpecifiers()?.delegationSpecifier()?.first()
                                ?.constructorInvocation()?.callSuffix()?.annotatedLambda()?.first()?.functionLiteral()
                                ?.statements()?.statement()?.forEach { statement ->
                                    val name = statement.declaration()?.propertyDeclaration()?.variableDeclaration()
                                        ?.simpleIdentifier()?.Identifier()?.symbol?.text ?: return@forEach
                                    val type =
                                        statement.declaration()?.propertyDeclaration()?.variableDeclaration()
                                            ?.type()?.text ?: return@forEach
                                    val prop = Immutable.Prop(
                                        name,
                                        type.let { if (it.endsWith("?")) it.substring(0, it.length - 1) else it },
                                        type.endsWith("?"),
                                        statement.declaration()?.propertyDeclaration()?.modifierList()?.annotations()
                                            ?.mapNotNull {
                                                Immutable.Anno(
                                                    it.annotation().LabelReference()?.symbol?.text?.substring(1)
                                                        ?: return@mapNotNull null
                                                )
                                            } ?: emptyList()
                                    )
                                    immutable.props += prop
                                }
                        }
                        sources += immutable
                    }
                    classDeclarationContext.enumClassBody()?.also { enumClassBody ->
                        val enum = Enum(
                            name = name,
                            packageName = packageName,
                            file = sourceFile,
                            enumConstants = enumClassBody.enumEntries()?.enumEntry()
                                ?.map { it.simpleIdentifier().Identifier().symbol.text }
                                ?: emptyList()
                        )
                        sources += enum
                    }
                }
            }
        }
        return sources
    }
}