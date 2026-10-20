@echo off
where gradle >nul 2>nul
if %errorlevel% equ 0 (gradle %* & exit /b)
python "%~dp0..\..\scripts\gradle_bootstrap.py" %*
