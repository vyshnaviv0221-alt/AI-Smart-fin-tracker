@echo off
setlocal enabledelayedexpansion
title ML Server - AI Smart Finance Tracker

REM ---------------------------------------------------------------------
REM  Starts the ML server and points the phone at it.
REM
REM  Safe to run on a machine that has never seen this project: it creates
REM  the virtual environment, installs dependencies and trains the models
REM  if they are missing. Later runs skip whatever is already done.
REM
REM  Leave this window open -- the server runs in it. Ctrl+C or closing the
REM  window stops it; stop-server.bat is there for when the window is lost.
REM ---------------------------------------------------------------------

set "ROOT=%~dp0"
set "SERVER=%ROOT%server"
set "VENV=%SERVER%\.venv"
set "PY=%VENV%\Scripts\python.exe"
set "STAMP=%VENV%\.requirements-installed"

REM One port, both sides of the USB bridge, so there is a single number to
REM remember. 8081 rather than the conventional 8000 because 8000 is heavily
REM used on Android and a busy device port makes `adb reverse` fail.
set "PORT=8081"

echo.
echo   AI Smart Finance Tracker -- ML server
echo   =====================================
echo.

if not exist "%SERVER%\app\main.py" (
    echo   [X] Cannot find server\app\main.py
    echo       Run this file from the folder it shipped in.
    goto :fail
)

REM --- 1. interpreter ---------------------------------------------------
if exist "%PY%" goto :have_venv

set "BOOTSTRAP="
for %%C in ("py -3.13" "py -3.12" "py -3.11" "py -3" "python") do (
    if not defined BOOTSTRAP (
        %%~C -c "import sys; sys.exit(0 if sys.version_info >= (3,11) else 1)" >nul 2>&1
        if !errorlevel! equ 0 set "BOOTSTRAP=%%~C"
    )
)
if not defined BOOTSTRAP (
    echo   [X] No Python 3.11 or newer found.
    echo.
    echo       Install it from https://www.python.org/downloads/
    echo       and tick "Add python.exe to PATH", then run this again.
    goto :fail
)

echo   [1/4] Creating virtual environment ...
%BOOTSTRAP% -m venv "%VENV%"
if errorlevel 1 goto :fail

:have_venv

REM --- 2. dependencies --------------------------------------------------
REM Reinstall when requirements.txt is newer than the last install, so
REM pulling an updated file does not leave a stale environment behind.
set "NEEDS_INSTALL=1"
if exist "%STAMP%" (
    powershell -NoProfile -Command "if ((Get-Item '%SERVER%\requirements.txt').LastWriteTime -gt (Get-Item '%STAMP%').LastWriteTime) { exit 1 } else { exit 0 }"
    if !errorlevel! equ 0 set "NEEDS_INSTALL="
)

if defined NEEDS_INSTALL (
    echo   [2/4] Installing dependencies ^(a few minutes the first time^) ...
    "%PY%" -m pip install --upgrade pip --quiet
    "%PY%" -m pip install -r "%SERVER%\requirements.txt" --quiet
    if errorlevel 1 goto :fail
    echo installed> "%STAMP%"
) else (
    echo   [2/4] Dependencies already installed.
)

REM --- 3. models --------------------------------------------------------
REM The .joblib files are git-ignored (they are build output, not source),
REM so a fresh clone has none and every prediction endpoint would return
REM 503 until they are trained.
if exist "%SERVER%\models\categorizer.joblib" (
    echo   [3/4] Models present.
) else (
    echo   [3/4] Training models ^(one time, ~1 minute^) ...
    pushd "%SERVER%"
    "%PY%" train_server_models.py
    set "TRAIN_FAILED=!errorlevel!"
    popd
    if not "!TRAIN_FAILED!"=="0" goto :fail
)

REM --- 4. phone bridge --------------------------------------------------
REM Maps the phone's 127.0.0.1:PORT to this machine over USB. No Wi-Fi, no
REM LAN address, and it survives campus networks that isolate clients.
set "ADB="
for %%A in (adb.exe) do if not defined ADB if not "%%~$PATH:A"=="" set "ADB=%%~$PATH:A"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"

if not defined ADB (
    echo   [4/4] adb not found -- skipping USB bridge.
    echo         The app will only reach this server over Wi-Fi.
) else (
    "%ADB%" reverse tcp:%PORT% tcp:%PORT% >nul 2>&1
    if errorlevel 1 (
        echo   [4/4] USB bridge not set up -- is the phone plugged in with
        echo         USB debugging on and the "Allow debugging" prompt accepted?
        echo         The server still starts; run this file again once connected.
    ) else (
        echo   [4/4] Phone bridged: device 127.0.0.1:%PORT% -^> this PC.
    )
)

REM --- run --------------------------------------------------------------
REM --host 0.0.0.0 also exposes the server on the LAN, so a phone on the
REM same Wi-Fi can use http://<this-pc-ip>:%PORT%/ if USB is unavailable.
echo.
echo   Server starting on http://127.0.0.1:%PORT%/
echo   API docs:          http://127.0.0.1:%PORT%/docs
echo.
echo   Keep this window open. Press Ctrl+C to stop.
echo   -----------------------------------------------------------------
echo.

pushd "%SERVER%"
"%PY%" -m uvicorn app.main:app --host 0.0.0.0 --port %PORT%
popd

echo.
echo   Server stopped.
pause
exit /b 0

:fail
echo.
echo   Setup failed -- see the message above.
pause
exit /b 1
