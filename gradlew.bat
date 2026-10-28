@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0apps\android\scripts\build.ps1" -Offline -Tasks %*
exit /b %ERRORLEVEL%
