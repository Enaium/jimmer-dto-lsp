param ($Jar = $( throw "Jar is required" ), $Version = $( throw "Version is required" ))

Copy-Item $Jar "${env:USERPROFILE}/.vscode/extensions/enaium.jimmer-dto-lsp-vscode-$Version/out/server.jar"

$VSCode

try {
    $VSCode = Get-Process code -ErrorAction Stop
} catch {
    $VSCode = $null
}

if ($null -ne $VSCode)
{
    Stop-Process $VSCode
}

Start-Process code -NoNewWindow