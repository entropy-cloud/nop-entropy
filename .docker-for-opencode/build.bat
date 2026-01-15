@echo off
REM ========================================
REM Opencode Hybrid Mode Build Script
REM ========================================

setlocal EnableDelayedExpansion

:main
echo ========================================
echo Opencode Hybrid Mode Build Script
echo ========================================
echo.

set BUILD_ARGS=
set PUSH=0

:parse_args
if "%~1"=="" goto :parse_args_done
if "%~1"=="--no-cache" (set BUILD_ARGS=--no-cache& shift& goto :parse_args)
if "%~1"=="--push" (set PUSH=1& shift& goto :parse_args)
if "%~1"=="--help" goto :show_help
:parse_args_done

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

echo Step 1/4: Clean old image (optional)
choice /C YN /M "Delete old? (Y/N)"
if errorlevel 2 goto :skip_cleanup
docker rmi opencode-hybrid:latest 2>nul
if errorlevel 1 (echo [WARN] Old image not found) else (echo [SUCCESS] Deleted)
:skip_cleanup

echo Step 2/4: Build Docker image
docker build %BUILD_ARGS% -f Dockerfile -t opencode-hybrid:latest .
if errorlevel 1 (
    echo [ERROR] Build failed!
    exit /b 1
)
echo [SUCCESS] Build successful!

echo Step 3/4: Verify image
docker images opencode-hybrid:latest
echo [SUCCESS] Verification successful

if "%PUSH%"=="1" (
    echo Step 4/4: Push to Docker Hub
    docker login
    docker push opencode-hybrid:latest
    echo [SUCCESS] Push successful
) else (
    echo Step 4/4: Done
)

echo.
echo ========================================
echo Build complete!
echo ========================================
echo.
echo Usage:
echo   docker-compose up -d
echo   docker exec -it opencode-cli bash
echo   OpenCode Desktop: http://localhost:3000
echo.

exit /b 0

:show_help
echo Usage: %~n0 [options]
echo   --no-cache    Build without cache
echo   --push        Push to Docker Hub
echo   --help        Show help
echo.
exit /b 0
