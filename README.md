# Jimmer DTO LSP

[Jimmer](https://github.com/babyfish-ct/jimmer)

## Features

- Syntax highlighting
- Compiler checking
- Automatic completion for prop, macro, function, keyword, comment etc.
- Folding
- Structure view
- Format export and import

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
- Any IDEs that support LSP

## Supported Platforms

- You need to install JDK 21 or later in your system environment

## Installation

- [Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=enaium.jimmer-dto-lsp-vscode): Install the
  extension from the marketplace
- [IntelliJ IDEA](https://plugins.jetbrains.com/plugin/26045-jimmer-dto-lsp): Install the plugin from the marketplace
- Eclipse: First install new software [LSP4E](https://download.eclipse.org/lsp4e/releases/latest/) and then move the
  plugin
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

If you want to add a new classpath, you can add it to the `dependencies.json` file in the LSP server
directory(`<user>/jimmer-dto-lsp`).

In Windows your path should be like this:

```json5
{
  "X:\\path\\to\\your\\project": [
    "X:\\path\\to\\your\\xx.jar",//Jar classpath
    "X:\\path\\to\\your\\project\\build\\classes\\kotlin\\main"//Directory classpath
  ]
}
```

In Linux or MacOS your path should be like this:

```json5
{
  "/path/to/your/project": [
    "/path/to/your/xx.jar",//Jar classpath
    "/path/to/your/project/build/classes/kotlin/main"//Directory classpath
  ]
}
```

If you want to add a new jar classpath automatically, you can use
the [jimmer-gradle](https://github.com/Enaium/jimmer-gradle) plugin.