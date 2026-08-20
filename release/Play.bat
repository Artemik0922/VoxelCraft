@echo off
chcp 65001 >nul
title VoxelCraft

setlocal enabledelayedexpansion

set GAME_DIR=%~dp0
set LIB_DIR=%GAME_DIR%lib
set NATIVES_DIR=%GAME_DIR%natives

REM Collect all JAR files
set CLASSPATH=
for %%j in (%LIB_DIR%\*.jar) do (
    if "!CLASSPATH!"=="" (
        set CLASSPATH=%%j
    ) else (
        set CLASSPATH=!CLASSPATH!;%%j
    )
)

REM Add game classes and resources
set CLASSPATH=%GAME_DIR%;!CLASSPATH!

REM Launch game
start "" /B java -cp "!CLASSPATH!" -Djava.library.path="%NATIVES_DIR%" com.voxelgame.Main

exit
