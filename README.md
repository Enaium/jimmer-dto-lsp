# Jimmer DTO LSP

[Jimmer](https://github.com/babyfish-ct/jimmer)

## Features

- Syntax highlighting
- Compiler checking

## Supported IDEs

- Visual Studio Code
- IntelliJ IDEA
- Eclipse
- Any IDEs that support LSP

## Supported Platforms

- You need to install JDK 21 or later in your system environment

## Installation

- [Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=enaium.jimmer-dto-lsp-vscode): Install the
  extension from the marketplace
- [IntelliJ IDEA](https://plugins.jetbrains.com/plugin/26045-jimmer-dto-lsp): Install the plugin from the marketplace
- Eclipse: First install new software [LSP4E](https://download.eclipse.org/lsp4e/releases/latest/) and then move the plugin
  to the `dropins` folder
- Other IDEs: Install the LSP server from the release page

## Usage

- Build your project with Jimmer apt/ksp plugin
- Open a Jimmer DTO file
- Enjoy the features

## Supported classpath

- `build/classes/kotlin/main` Gradle Kotlin
- `build/classes/kotlin/test` Gradle Kotlin
- `build/classes/java/main` Gradle Java
- `build/classes/java/test` Gradle Java
- `target/classes` Maven Java or Kotlin
- `build/tmp/kotlin-classes/debug` Gradle Android Kotlin
- `build/intermediates/javac/debug/classes` Gradle Android Java
- `build/intermediates/javac/debug/compileDebugJavaWithJavac/classes` Gradle Android Java