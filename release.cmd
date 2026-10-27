@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
where node.exe >nul 2>nul
if errorlevel 1 (
  echo Node.js 22 or later is required. Install Node.js and reopen CMD.
  exit /b 1
)
node.exe "%~dp0scripts\release-console.cjs" %*
exit /b %ERRORLEVEL%
