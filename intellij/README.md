# Jimmer DTO LSP IntelliJ

![highlighting](https://s2.loli.net/2024/12/13/yes1EwWzq3UHJYv.png)

## Install

### Settings

- Press `Ctrl` + `Alt` + `S` to open the `Settings` dialog.
- Search `Jimmer DTO LSP` in the search box.

![20241227220028](https://s2.loli.net/2024/12/27/BRMcmEwKJ2Y9HgP.png)

### Manual

- Open the [Plugin Marketplace](https://plugins.jetbrains.com/plugin/26045-jimmer-dto-lsp)
- Click the `Install` button.

![20241227220910](https://s2.loli.net/2024/12/27/Npz8HLXx7cRC65j.png)

## Settings

![20250116232205](https://s2.loli.net/2025/01/16/MBbm1uXcOrCdIkp.png)

## FAQ

### Plguin not work

- Please check your jdk version in your system environment, the plugin requires jdk 21 or later.
- Please check your intellij idea log of lsp4ij, the log information is in the `View` -> `Tool Windows` -> `Language Server`

![20241227221201](https://s2.loli.net/2024/12/27/gfpZQewNoVuHiym.png)

### Immutable not found

- Please check your intellij's jdk version, the plugin will use the jdk version in the intellij idea, please make sure
  the jdk version is compatible with your project.

![20250113205709](https://s2.loli.net/2025/01/13/ZFofEjPI8zGyMWw.png)

### Plguin conflict

![750bc317d5af0f7c46791257f832175a](https://s2.loli.net/2024/12/27/cZDw8aFdqLo6RGx.png)

- Open `C:\Users\Enaium\AppData\Roaming\JetBrains\IntelliJIdea202x.x\plugins` and delete the plugin that conflicts with the plugin.