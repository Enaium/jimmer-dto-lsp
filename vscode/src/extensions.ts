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

import * as path from "path";
import * as fs from "fs";
import * as os from "os";
import { ExtensionContext, window } from "vscode";

import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
  TransportKind,
} from "vscode-languageclient/node";

const serverJar = path.join(os.homedir(), "jimmer-dto-lsp", "server.jar");
const embedJar = path.join(__dirname, "server.jar");
let client: LanguageClient;

export function activate(context: ExtensionContext) {
  if (fs.existsSync(serverJar)) {
    try {
      fs.unlinkSync(serverJar);
    } catch (e) {}
  }

  fs.mkdirSync(path.join(os.homedir(), "jimmer-dto-lsp"), { recursive: true });
  fs.copyFileSync(embedJar, serverJar);

  const serverOptions: ServerOptions = {
    command: "java",
    args: ["-cp", serverJar, "cn.enaium.jimmer.dto.lsp.MainKt"],
    transport: TransportKind.stdio,
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", pattern: "**/*.dto" }],
  };

  client = new LanguageClient(
    "Jimmer DTO Language Server",
    "Jimmer DTO Language Server",
    serverOptions,
    clientOptions
  );

  client.start();
}

export function deactivate(): Thenable<void> | undefined {
  if (!client) {
    return undefined;
  }
  return client.stop();
}
