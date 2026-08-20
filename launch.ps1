$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$classpath = ((Get-ChildItem "$root\lib\*.jar").FullName -join ";") + ";$root\build"

$nativesDir = Join-Path $root "build\natives"
if (Test-Path $nativesDir) { Remove-Item $nativesDir -Recurse -Force }
New-Item -ItemType Directory -Path $nativesDir -Force | Out-Null

Add-Type -AssemblyName System.IO.Compression.FileSystem
Get-ChildItem "$root\lib\*natives*.jar" | ForEach-Object {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -match "\.(dll|so|dylib)$") {
            $dest = Join-Path $nativesDir $entry.FullName
            $destDir = Split-Path $dest -Parent
            if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force | Out-Null }
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
        }
    }
    $zip.Dispose()
}

Write-Host "Starting VoxelGame..."
& java "-cp" $classpath "-Djava.library.path=$nativesDir" "com.voxelgame.Main"