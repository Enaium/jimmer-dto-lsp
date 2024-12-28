import cn.enaium.jimmer.dto.lsp.utility.findSubprojects
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertContentEquals

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

/**
 * @author Enaium
 */
class FindSubprojectsTest {
    @Test
    fun test() {
        val start = System.currentTimeMillis()
        val project = Path("D:\\Projects\\jimmer\\project\\")
        assertContentEquals(
            findSubprojects(project).map { it.name },
            listOf(
                "buildSrc",
                "jimmer-apt",
                "jimmer-bom",
                "jimmer-client",
                "jimmer-client-swagger",
                "jimmer-core",
                "jimmer-core-kotlin",
                "jimmer-dto-compiler",
                "jimmer-ksp",
                "jimmer-mapstruct-apt",
                "jimmer-spring-boot-starter",
                "jimmer-sql",
                "jimmer-sql-kotlin"
            )
        )
        println(System.currentTimeMillis() - start)
    }
}