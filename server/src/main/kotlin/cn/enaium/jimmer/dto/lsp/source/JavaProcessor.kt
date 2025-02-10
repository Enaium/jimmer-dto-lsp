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

import JavaLexer
import JavaParser
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
class JavaProcessor(val paths: List<Path>) : AbstractProcessor(paths) {
    override fun process(): List<Source> {
        val sourceFiles = paths.mapNotNull {
            if (it.isDirectory()) {
                it.walk().filter { it.extension == "java" }.toList()
            } else if (it.extension == "java") {
                listOf(it)
            } else {
                null
            }
        }.flatten()

        val sources = mutableListOf<Source>()
        sourceFiles.forEach { sourceFile ->
            val javaLexer = JavaLexer(CharStreams.fromStream(sourceFile.inputStream()))
            val input = CommonTokenStream(javaLexer)
            input.fill()
            if (input.tokens
                    .any { needProcessedTokens.contains(it.text) }
                    .not()
            ) {
                return@forEach
            }
            val javaParser = JavaParser(input)
            javaParser.errorListeners.clear()
            val javaFileContext = javaParser.compilationUnit()
            javaFileContext.typeDeclaration().forEach { typeDeclaration ->
                typeDeclaration.interfaceDeclaration()?.also { interfaceDeclaration ->
                    val immutable = Immutable(
                        name = interfaceDeclaration.identifier().IDENTIFIER().symbol.text,
                        packageName = javaFileContext.packageDeclaration()?.qualifiedName()?.identifier()
                            ?.joinToString(".") { it.text } ?: "",
                        file = sourceFile,
                        immutableType = if (typeDeclaration.classOrInterfaceModifier().any {
                                needProcessedTokens.contains(
                                    it.annotation()?.qualifiedName()?.text
                                )
                            } == true) {
                            var type = Immutable.ImmutableType.IMMUTABLE
                            when (typeDeclaration.classOrInterfaceModifier().first {
                                needProcessedTokens.contains(
                                    it.annotation()?.qualifiedName()?.text
                                )
                            }.annotation()?.qualifiedName()?.text) {
                                "Immutable" -> type = Immutable.ImmutableType.IMMUTABLE
                                "Entity" -> type = Immutable.ImmutableType.ENTITY
                                "Embeddable" -> type = Immutable.ImmutableType.EMBEDDABLE
                                "MappedSuperclass" -> type = Immutable.ImmutableType.MAPPED_SUPERCLASS
                            }
                            type
                        } else {
                            return@forEach
                        },
                        superTypes = interfaceDeclaration.typeList().flatMap { it.typeType().map { it.text } },
                        typeParameters = interfaceDeclaration.typeParameters()?.typeParameter()
                            ?.map { it.identifier().IDENTIFIER().symbol.text }
                            ?: emptyList()
                    )

                    interfaceDeclaration.interfaceBody()?.also { interfaceBody ->
                        interfaceBody.interfaceBodyDeclaration().forEach { interfaceBodyDeclaration ->
                            interfaceBodyDeclaration.interfaceMemberDeclaration()?.also { interfaceMemberDeclaration ->
                                interfaceMemberDeclaration.interfaceMethodDeclaration()
                                    ?.also { interfaceMethodDeclaration ->
                                        interfaceMethodDeclaration.interfaceCommonBodyDeclaration()
                                            ?.also { interfaceCommonBodyDeclaration ->
                                                val annotations =
                                                    interfaceBodyDeclaration.modifier()?.mapNotNull { modifier ->
                                                        Immutable.Anno(
                                                            modifier.classOrInterfaceModifier()?.annotation()
                                                                ?.qualifiedName()?.text ?: return@mapNotNull null
                                                        )
                                                    } ?: emptyList()
                                                val prop = Immutable.Prop(
                                                    interfaceCommonBodyDeclaration.identifier()
                                                        .IDENTIFIER().symbol.text,
                                                    interfaceCommonBodyDeclaration.typeTypeOrVoid().text,
                                                    annotations.any { it.name.startsWith("Null") },
                                                    annotations
                                                )
                                                immutable.props += prop
                                            }
                                    }
                            }
                        }
                    }

                    sources += immutable
                }
                typeDeclaration.enumDeclaration()?.also { enumDeclaration ->
                    val enum = Enum(
                        name = enumDeclaration.identifier().IDENTIFIER().symbol.text,
                        packageName = javaFileContext.packageDeclaration()?.qualifiedName()?.identifier()
                            ?.joinToString(".") { it.text } ?: "",
                        file = sourceFile,
                        enumConstants = enumDeclaration.enumConstants().enumConstant()
                            .map { it.identifier().IDENTIFIER().symbol.text }
                    )
                    sources += enum
                }
            }
        }
        return sources
    }
}