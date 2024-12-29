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
- ✅ [workspace/executeCommand](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_executeCommand).
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

## Supported IDEs

- Visual Studio Code
- IntelliJ IDEA
- Eclipse
- Neovim
- Any IDEs that support LSP

## Requirements

- You need to install JDK 21 or later in your system environment
- Your project or subproject should have `gradlew` or `mvnw` file otherwise you need to install Gradle or Maven in your system environment

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
    "X:\\path\\to\\your\\project\\build\\classes\\kotlin\\main"
    //Directory classpath
  ]
}
```

In Linux or MacOS your path should be like this:

```json5
{
  "/path/to/your/project": [
    "/path/to/your/xx.jar",
    //Jar classpath
    "/path/to/your/project/build/classes/kotlin/main"
    //Directory classpath
  ]
}
```

If you want to add a new jar classpath automatically, you can use
the [jimmer-gradle](https://github.com/Enaium/jimmer-gradle) plugin and then run the `generateLspDependencies` task.