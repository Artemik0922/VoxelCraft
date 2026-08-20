@echo off
cd /d "%~dp0"
powershell -ExecutionPolicy Bypass -Command "& {./build.ps1 -Run}"
pause