$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$classpath = ((Get-ChildItem "$root\lib\*.jar").FullName -join ";") + ";$root\build"

# Usage: launch-server.ps1 [port] [seed] [maxPlayers]
$port = if ($args.Length -ge 1) { $args[0] } else { "25565" }
$seed = if ($args.Length -ge 2) { $args[1] } else { (Get-Random).ToString() }
$maxPlayers = if ($args.Length -ge 3) { $args[2] } else { "4" }

Write-Host "Starting co-op server (port $port, seed $seed, slots $maxPlayers)..."
& java "-cp" $classpath "com.voxelgame.net.ServerMain" $port $seed $maxPlayers
