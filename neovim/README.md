# Jimmer DTO LSP Neovim

![neovim](https://s2.loli.net/2024/12/27/MLb56A47U2cBVTN.png)

## Install

### Manual

- First you need to install the `nvim-lspconfig` plugin.
- Then you need to move the `neovim` folder to the `<&runtimepath>` directory.

### Use `vim-plug`

- First you need to install the `nvim-lspconfig` plugin.

```lua
Plug('neovim/nvim-lspconfig')
```

- Then you need to install the plugin.

```lua
Plug('Enaium/jimmer-dto-lsp', { ['rtp'] = 'neovim' })
```
