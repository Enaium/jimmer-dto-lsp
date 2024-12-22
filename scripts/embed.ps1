param ($Jar = $( throw "Jar is required" ))

$Location = Get-Location

Copy-Item $Jar "$Location/vscode/out/server.jar"
Copy-Item $Jar "$Location/intellij/src/main/resources/server.jar"
Copy-Item $Jar "$Location/eclipse/src/main/resources/server.jar"