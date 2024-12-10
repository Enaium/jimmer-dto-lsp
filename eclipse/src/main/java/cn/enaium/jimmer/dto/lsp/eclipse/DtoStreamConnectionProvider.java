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

package cn.enaium.jimmer.dto.lsp.eclipse;

import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DtoStreamConnectionProvider extends ProcessStreamConnectionProvider {
    public DtoStreamConnectionProvider() throws IOException {
        var dir = Paths.get(System.getProperty("user.home"), "jimmer-dto-lsp");

        if (!dir.toFile().exists()) {
            Files.createDirectories(dir);
        }

        var localJarFile = dir.resolve("server.jar");

        try {

            if (Files.exists(localJarFile)) {
                Files.delete(localJarFile);
            }

            try (var inputStream = getClass().getClassLoader().getResourceAsStream("server.jar")) {
                assert inputStream != null;
                Files.copy(inputStream, localJarFile);
            }
        } catch (Exception ignored) {

        }

        if (!Files.exists(localJarFile)) {
            throw new IOException("Local server jar not found");
        }
        setCommands(List.of("java", "-cp", localJarFile.toFile().getAbsolutePath(), "cn.enaium.jimmer.dto.lsp.MainKt"));
    }
}
