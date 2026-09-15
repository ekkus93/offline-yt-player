@echo off
setlocal enabledelayedexpansion
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=9.6.0"
set "GRADLE_SHA256=bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST_ROOT=%GRADLE_USER_HOME%\wrapper\dists\offline-yt-player-%GRADLE_VERSION%"
set "DIST_DIR=%DIST_ROOT%\gradle-%GRADLE_VERSION%"
set "ZIP=%DIST_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
if not exist "%DIST_DIR%\bin\gradle.bat" (
  if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
  if not exist "%ZIP%" powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'"
  for /f %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%ZIP%').Hash.ToLower()"') do set "ACTUAL=%%H"
  if not "!ACTUAL!"=="%GRADLE_SHA256%" (
    del /q "%ZIP%"
    echo Gradle distribution checksum mismatch 1>&2
    exit /b 3
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%DIST_ROOT%'"
)
call "%DIST_DIR%\bin\gradle.bat" -p "%APP_HOME%" %*
