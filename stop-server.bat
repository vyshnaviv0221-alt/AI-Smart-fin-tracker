@echo off
setlocal enabledelayedexpansion
title Stop ML Server

REM ---------------------------------------------------------------------
REM  Stops the ML server and removes the USB bridge.
REM
REM  Normally you just close the server window. This is for when that
REM  window was lost, or a previous run left the port occupied and
REM  start-server.bat reports the address is already in use.
REM ---------------------------------------------------------------------

set "PORT=8081"

echo.
echo   Stopping ML server on port %PORT% ...

set "FOUND="
REM Only kill a python process. Something else listening on this port is not
REM ours to stop, and killing it silently would be a nasty surprise.
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:"TCP .*:%PORT% .*LISTENING"') do (
    if not "%%P"=="0" (
        set "OWNER="
        for /f "tokens=1 delims=," %%N in ('tasklist /fi "PID eq %%P" /fo csv /nh 2^>nul') do set "OWNER=%%~N"
        echo !OWNER! | findstr /i "python" >nul
        if !errorlevel! equ 0 (
            taskkill /F /PID %%P >nul 2>&1
            if !errorlevel! equ 0 (
                echo   Stopped !OWNER! ^(PID %%P^).
                set "FOUND=1"
            )
        ) else (
            echo   Port %PORT% is held by !OWNER! ^(PID %%P^), which is not this server.
            echo   Leaving it alone.
            set "FOUND=1"
        )
    )
)

if not defined FOUND echo   Nothing was listening on port %PORT%.

REM Remove only this project's mapping, not every reverse the user has.
set "ADB="
for %%A in (adb.exe) do if not defined ADB if not "%%~$PATH:A"=="" set "ADB=%%~$PATH:A"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"

if defined ADB (
    "%ADB%" reverse --remove tcp:%PORT% >nul 2>&1
    if errorlevel 1 (
        echo   No USB bridge to remove.
    ) else (
        echo   USB bridge removed.
    )
)

echo.
REM Hold the window open briefly so a double-click is readable. `timeout`
REM is not used: it reads the console and fails outright when stdin is
REM redirected, which is exactly what happens when this is run from a
REM script rather than by hand.
ping -n 4 127.0.0.1 >nul 2>&1
exit /b 0
