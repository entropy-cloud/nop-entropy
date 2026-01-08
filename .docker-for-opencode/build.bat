@echo off
REM ========================================
REM Opencode Hybrid Mode Build Script
REM ========================================

setlocal EnableDelayedExpansion

REM Main function
:main
echo ========================================
echo Opencode Hybrid Mode Build Script
echo ========================================
echo.

REM Parse arguments
set BUILD_ARGS=
set PUSH=0

:parse_args
if "%~1"=="" goto :parse_args_done
if "%~1"=="--no-cache" (
    set BUILD_ARGS=--no-cache
    shift
    goto :parse_args
)
if "%~1"=="--push" (
    set PUSH=1
    shift
    goto :parse_args
)
if "%~1"=="--help" goto :show_help
goto :parse_args_done

:parse_args_done

REM Get script directory
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

echo Step 1/4: Clean old image (optional)
echo.
choice /C YN /M "Delete old image? (Y/N)"
if errorlevel 2 goto :skip_cleanup

echo Deleting old image...
docker rmi opencode-hybrid:latest 2>nul
if errorlevel 1 (
    echo [WARNING] Old image not found or already deleted
) else (
    echo [SUCCESS] Old image deleted
)
echo.

:skip_cleanup
echo Step 2/4: Build Docker image
echo.
echo Building image: opencode-hybrid:latest
echo.

docker build %BUILD_ARGS% -f Dockerfile -t opencode-hybrid:latest .

if errorlevel 1 (
    echo.
    echo [ERROR] Image build failed!
    echo.
    echo Please check:
    echo   1. Docker is running
    echo   2. Dockerfile exists
    echo   3. Network connection is OK
    echo.
    exit /b 1
)

echo.
echo [SUCCESS] Image build successful!
echo.

echo Step 3/4: Verify image
echo.
docker images opencode-hybrid:latest

if errorlevel 1 (
    echo [ERROR] Image verification failed
    exit /b 1
)

echo.
echo [SUCCESS] Image verification successful
echo.

REM Push image (optional)
if "%PUSH%"=="1" (
    echo Step 4/4: Push image to Docker Hub
    echo.
    
    echo Login to Docker Hub...
    docker login
    if errorlevel 1 (
        echo [ERROR] Docker Hub login failed
        exit /b 1
    )
    
    echo Pushing image...
    docker push opencode-hybrid:latest
    
    if errorlevel 1 (
        echo [ERROR] Image push failed
        exit /b 1
    )
    
    echo.
    echo [SUCCESS] Image push successful
    echo.
) else (
    echo Step 4/4: Done
    echo.
)

REM Show usage
echo ========================================
echo Image build successful!
echo ========================================
echo.
echo Features of Hybrid Mode:
echo  [SUCCESS] CLI + Server dual mode
echo  [SUCCESS] Command-line: docker exec -it opencode-cli bash
echo  [SUCCESS] GUI: OpenCode Desktop connect to http://localhost:3000
echo  [SUCCESS] Can use both methods simultaneously
echo.
echo Usage:
echo.
echo 1. Start container:
echo    docker-compose up -d
echo.
echo 2. Use CLI:
echo    docker exec -it opencode-cli bash
echo    opencode "Hello"
echo.
echo 3. Use GUI:
echo    Configure OpenCode Desktop to connect to http://localhost:3000
echo.
echo Detailed documentation:
echo    - Hybrid mode usage: USAGE.md
echo    - Deployment summary: SUMMARY.md
echo    - Troubleshooting: TROUBLESHOOTING.md
echo.

exit /b 0

:show_help
echo Usage: %~n0 [options]
echo.
echo Options:
echo   --no-cache    Build without cache
echo   --push        Push image to Docker Hub after build
echo   --help        Show this help message
echo.
echo Examples:
echo   %~n0
echo   %~n0 --no-cache
echo   %~n0 --push
echo.
exit /b 0

REM Execute main function
call :main %*
