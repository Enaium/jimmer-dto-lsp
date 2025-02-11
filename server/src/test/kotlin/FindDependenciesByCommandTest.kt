import cn.enaium.jimmer.dto.lsp.utility.findDependenciesByCommand
import kotlin.io.path.Path
import kotlin.test.Test

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
class FindDependenciesByCommandTest {
    @Test
    fun gradleDependencies() {
        val start = System.currentTimeMillis()
        val project = Path("D:\\Projects\\jimmer-gradle-test-java")
        println(findDependenciesByCommand(project))
        println(System.currentTimeMillis() - start)
    }

    @Test
    fun mavenDependencies() {
        val start = System.currentTimeMillis()
        val project = Path("D:\\Projects\\untitled2")
        println(findDependenciesByCommand(project))
        println(System.currentTimeMillis() - start)
    }
}