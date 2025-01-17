# Jimmer DTO LSP

[Jimmer](https://github.com/babyfish-ct/jimmer)

![GitHub top language](https://img.shields.io/github/languages/top/enaium/jimmer-dto-lsp?style=flat-square&logo=kotlin)
![GitHub](https://img.shields.io/github/license/enaium/jimmer-dto-lsp?style=flat-square)

![intellij](https://img.shields.io/badge/-IntelliJ_IDEA-blue?style=flat-square&logo=intellijidea&logoColor=white)
![vscode](https://img.shields.io/badge/-Visual_Studio_Code-blue?style=flat-square&logo=materialdesignicons&logoColor=white)
![eclipse](https://img.shields.io/badge/-Eclipse-blue?style=flat-square&logo=eclipse&logoColor=white)
![neovim](https://img.shields.io/badge/-Neovim-blue?style=flat-square&logo=neovim&logoColor=white)

## Features

- Syntax highlighting
- Compiler checking
- Automatic completion for prop, macro, function, keyword, comment etc.
- Folding
- Structure view
- Format export and import
- Hover for export, import, macro, prop, alias group etc.
- Go to definition
- Find symbol

## Implemented LSP features

- ✅ [textDocument/didOpen](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didOpen).
- ✅ [textDocument/didChange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didChange).
- ✅ [textDocument/didSave](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didSave).
- ✅ [textDocument/didClose](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didClose).
- ✅ [textDocument/completion](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion).
- ✅ [textDocument/foldingRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_foldingRange).
- ✅ [textDocument/formatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_formatting).
- ✅ [textDocument/semanticTokens/full](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens_full).
- ✅ [textDocument/documentSymbol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentSymbol).
- ✅ [textDocument/hover](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover).
- ✅ [textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition).
- ✅ [workspace/executeCommand](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_executeCommand).
- ✅ [workspace/symbol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_symbol).
- ✅ [workspace/configuration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_configuration).
- ✅ [$/progress](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#progress).

## Screenshots

![highlighting](https://s2.loli.net/2024/12/13/yes1EwWzq3UHJYv.png)

![compiler checking](https://s2.loli.net/2024/12/13/mEx8oJfgVs7BtpK.png)

![comment](https://s2.loli.net/2024/12/13/zlIqFZaEOKfXmTG.gif)

![prop](https://s2.loli.net/2024/12/13/DvS4n6xOLCuH23e.gif)

![folding](https://s2.loli.net/2024/12/13/Rz5th8pDkrKT7Su.gif)

![structure](https://s2.loli.net/2024/12/13/rjh9IMgSbdJ2o4n.gif)

![import annotation](https://s2.loli.net/2024/12/15/W6fSpoEUZXmguK8.gif)

![formatting export and import](https://s2.loli.net/2024/12/19/45Ja3uSgzhyCjYp.gif)

![export package hover](https://s2.loli.net/2025/01/03/A57apRUf8VyvrQl.png)

![import hover](https://s2.loli.net/2025/01/03/xVEL8l5kH7q3eOP.png)

![macro hover](https://s2.loli.net/2025/01/03/1QRXJnE6i4FOvdI.png)

![prop hover](https://s2.loli.net/2025/01/03/ayzH4lGgtUBWpSO.png)

![alias group hover](https://s2.loli.net/2025/01/03/jD9vxHoeylE5kWc.png)

![go to definition](https://s2.loli.net/2025/01/08/3QoBxhJDfgZdvus.gif)

![workspace symbol](https://s2.loli.net/2025/01/08/7VCLn8ZuSK9MTkA.gif)

## Supported IDEs

- Visual Studio Code
- IntelliJ IDEA (Recommend you change the default theme because the default theme semantic token color is not good)
- Eclipse
- Neovim
- Any IDEs that support LSP

## Requirements

- You need to install JDK 17 or later in your system environment, but make sure your jdk version is compatible with your
  project
- Your project should have `gradlew` or `mvnw` file otherwise you need to install Gradle or Maven in your
  system environment

## Installation

- [Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=enaium.jimmer-dto-lsp-vscode): Install the
  extension from the marketplace
- [IntelliJ IDEA](./intellij/README.md): Install the plugin from the marketplace
- Eclipse: First install new software [LSP4E](https://download.eclipse.org/lsp4e/releases/latest/) and then move the
  plugin
  to the `dropins` folder
- [Neovim](./neovim/README.md): Install the neovim plugin and then move the server jar file to the
  `<userdir>/jimmer-dto-lsp/server.jar`
- Other IDEs: Install the LSP server from the release page

## Usage

- Build your project with Jimmer apt/ksp plugin
- Open a Jimmer DTO file
- Enjoy the features

## Other Dependencies

If your project has other immutable dependencies you need to add
the [jimmer-gradle](https://github.com/Enaium/jimmer-gradle) plugin to your project.
If you use Maven, you don't need to do anything.

## Supported classpath

- `build/classes/kotlin/main` Gradle Kotlin
- `build/classes/kotlin/test` Gradle Kotlin
- `build/classes/java/main` Gradle Java
- `build/classes/java/test` Gradle Java
- `target/classes` Maven Java or Kotlin
- `build/tmp/kotlin-classes/debug` Gradle Android Kotlin
- `build/intermediates/javac/debug/classes` Gradle Android Java
- `build/intermediates/javac/debug/compileDebugJavaWithJavac/classes` Gradle Android Java

If you want to add a new classpath, you can add it to the `dependencies.json` file in the LSP server
directory(`<userdir>/jimmer-dto-lsp`).

In Windows your path should be like this:

```json5
{
  "X:\\path\\to\\your\\project": [
    "X:\\path\\to\\your\\xx.jar",
    //Jar classpath
    "X:\\path\\to\\your\\project\\build\\classes\\kotlin\\main",
    //Directory classpath
  ],
}
```

In Linux or macOS your path should be like this:

```json5
{
  "/path/to/your/project": [
    "/path/to/your/xx.jar",
    //Jar classpath
    "/path/to/your/project/build/classes/kotlin/main",
    //Directory classpath
  ],
}
```

If you want to add a new jar classpath automatically, you can use
the [jimmer-gradle](https://github.com/Enaium/jimmer-gradle) plugin and then run the `generateLspDependencies` task.
