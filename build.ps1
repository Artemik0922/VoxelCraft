# Minecraft Clone Build Script
param(
    [switch]$Run,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$libDir = Join-Path $projectRoot "lib"
$buildDir = Join-Path $projectRoot "build"
$srcDir = Join-Path $projectRoot "src\main\java"
$resDir = Join-Path $projectRoot "src\main\resources"

# LWJGL version and modules
$lwjglVersion = "3.3.3"
$lwjglNatives = "natives-windows"
$modules = @("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb", "lwjgl-nanovg", "lwjgl-openal")

# Maven Central base URL
$mavenBase = "https://repo1.maven.org/maven2/org/lwjgl"

function Download-LWJGL {
    if (-not (Test-Path $libDir)) { New-Item -ItemType Directory -Path $libDir -Force | Out-Null }
    
    foreach ($mod in $modules) {
        $jarName = "$mod-$lwjglVersion.jar"
        $jarPath = Join-Path $libDir $jarName
        $url = "$mavenBase/$mod/$lwjglVersion/$jarName"
        
        if (-not (Test-Path $jarPath)) {
            Write-Host "Downloading $jarName..." -ForegroundColor Yellow
            Invoke-WebRequest -Uri $url -OutFile $jarPath
        }
        
        # Download natives
        $nativesName = "$mod-$lwjglVersion-$lwjglNatives.jar"
        $nativesPath = Join-Path $libDir $nativesName
        $nativesUrl = "$mavenBase/$mod/$lwjglVersion/$nativesName"
        
        if (-not (Test-Path $nativesPath)) {
            Write-Host "Downloading $nativesName..." -ForegroundColor Yellow
            Invoke-WebRequest -Uri $nativesUrl -OutFile $nativesPath
        }
    }
    
    # Download JOML
    $jomlUrl = "https://repo1.maven.org/maven2/org/joml/joml/1.10.5/joml-1.10.5.jar"
    $jomlPath = Join-Path $libDir "joml-1.10.5.jar"
    if (-not (Test-Path $jomlPath)) {
        Write-Host "Downloading joml-1.10.5.jar..." -ForegroundColor Yellow
        Invoke-WebRequest -Uri $jomlUrl -OutFile $jomlPath
    }
    
    Write-Host "All dependencies ready!" -ForegroundColor Green
}

function Build-Project {
    # Collect all JARs for classpath
    $jarFiles = Get-ChildItem -Path $libDir -Filter "*.jar" | ForEach-Object { $_.FullName }
    $classpath = ($jarFiles -join ";")
    
    # Find all Java source files
    $javaFiles = Get-ChildItem -Path $srcDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
    
    if ($javaFiles.Count -eq 0) {
        Write-Host "No Java source files found!" -ForegroundColor Red
        exit 1
    }
    
    # Create build directory
    if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir -Force | Out-Null }
    
    Write-Host "Compiling $($javaFiles.Count) source files..." -ForegroundColor Cyan
    
    # Compile
    # Sources contain Cyrillic glyph definitions and translated strings, so
    # the encoding has to be stated explicitly: javac would otherwise use the
    # platform default (cp1251 here) and fail to parse them.
    $compileArgs = @("-cp", $classpath, "-d", $buildDir, "-encoding", "UTF-8") + $javaFiles
    & javac @compileArgs
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Compilation failed!" -ForegroundColor Red
        exit 1
    }
    
    # Copy resources
    if (Test-Path $resDir) {
        Copy-Item -Path "$resDir\*" -Destination $buildDir -Recurse -Force
    }
    
    Write-Host "Build successful!" -ForegroundColor Green
}

function Run-Project {
    $jarFiles = Get-ChildItem -Path $libDir -Filter "*.jar" | ForEach-Object { $_.FullName }
    $classpath = ($jarFiles -join ";") + ";$buildDir"
    
    # Extract natives from jars
    $nativesDir = Join-Path $buildDir "natives"
    
    # Clean up old natives to prevent stale DLLs
    if (Test-Path $nativesDir) {
        Remove-Item -Path $nativesDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $nativesDir -Force | Out-Null
    
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Get-ChildItem -Path $libDir -Filter "*natives*.jar" | ForEach-Object {
        Write-Host "Extracting natives from $($_.Name)..." -ForegroundColor Yellow
        $zip = [System.IO.Compression.ZipFile]::OpenRead($_.FullName)
        foreach ($entry in $zip.Entries) {
            if ($entry.Name -match "\.(dll|so|dylib)$") {
                # Preserve directory structure
                $relativePath = $entry.FullName
                $destPath = Join-Path $nativesDir $relativePath
                $destDir = Split-Path $destPath -Parent
                if (-not (Test-Path $destDir)) {
                    New-Item -ItemType Directory -Path $destDir -Force | Out-Null
                }
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destPath, $true)
            }
        }
        $zip.Dispose()
    }
    
    Write-Host "Starting VoxelGame..." -ForegroundColor Green
    $javaArgs = @("-cp", $classpath, "-Djava.library.path=$nativesDir", "com.voxelgame.Main")
    & java @javaArgs
}

# Main execution
if ($Clean) {
    Write-Host "Cleaning build directory..." -ForegroundColor Yellow
    if (Test-Path $buildDir) { Remove-Item -Path $buildDir -Recurse -Force }
    exit 0
}

Download-LWJGL
Build-Project

if ($Run) {
    Run-Project
}
