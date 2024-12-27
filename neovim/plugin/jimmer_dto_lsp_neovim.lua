vim.cmd [[au BufRead,BufNewFile *.dto                set filetype=JimmerDTO]]

local lsp = require('lspconfig')
local lsp_config = require('lspconfig.configs')
local home = vim.env.HOME

lsp_config.jimmer_dto_lsp = {
    default_config = {
        cmd = { 'java', '-cp', home .. '/jimmer-dto-lsp/server.jar', 'cn.enaium.jimmer.dto.lsp.MainKt' },
        filetypes = { 'JimmerDTO' },
        root_dir = function(fname)
            return lsp.util.root_pattern('build.gradle', 'build.gradle.kts', 'pom.xml')(fname)
        end,
    }
}

lsp_config.jimmer_dto_lsp.setup {}
