@echo off
REM ========================================
REM Opencode Docker 验证脚本（Windows）
REM ========================================

setlocal enabledelayedexpansion

set PASS=0
set FAIL=0
set WARN=0

echo ========================================
echo Opencode Docker 验证测试
echo ========================================
echo.

REM 检查 Docker
echo [TEST] 检查 Docker 是否运行
docker info >nul 2>&1
if errorlevel 1 (
    echo [FAIL] Docker 未运行，请先启动 Docker
    exit /b 1
) else (
    echo [PASS] Docker 正在运行
    set /a PASS=PASS+1
)
echo.

REM 检查容器
echo [TEST] 检查容器是否运行
docker ps --format "{{.Names}}" | findstr opencode-cli >nul 2>&1
if errorlevel 1 (
    echo [FAIL] 容器未运行，请先启动容器: docker-compose up -d
    exit /b 1
) else (
    echo [PASS] 容器正在运行
    set /a PASS=PASS+1
)
echo.

REM 核心功能测试
echo ========================================
echo 核心功能测试
echo ========================================

echo [TEST] 检查 OpenCode CLI 版本
docker exec opencode-cli opencode --version >nul 2>&1
if errorlevel 1 (
    echo [FAIL] OpenCode CLI 不可用
    set /a FAIL=FAIL+1
) else (
    for /f "delims=" %%i in ('docker exec opencode-cli opencode --version 2^>^&1') do set OPENCODE_VERSION=%%i
    echo [PASS] OpenCode CLI 版本: !OPENCODE_VERSION!
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 openspec 版本
docker exec opencode-cli openspec --version >nul 2>&1
if errorlevel 1 (
    echo [FAIL] openspec 不可用
    set /a FAIL=FAIL+1
) else (
    for /f "delims=" %%i in ('docker exec opencode-cli openspec --version 2^>^&1') do set OPENSPEC_VERSION=%%i
    echo [PASS] openspec 版本: !OPENSPEC_VERSION!
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 Node.js 版本
docker exec opencode-cli node --version >nul 2>&1
if errorlevel 1 (
    echo [FAIL] Node.js 不可用
    set /a FAIL=FAIL+1
) else (
    for /f "delims=" %%i in ('docker exec opencode-cli node --version 2^>^&1') do set NODE_VERSION=%%i
    echo [PASS] Node.js 版本: !NODE_VERSION!
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 npm 版本
docker exec opencode-cli npm --version >nul 2>&1
if errorlevel 1 (
    echo [FAIL] npm 不可用
    set /a FAIL=FAIL+1
) else (
    for /f "delims=" %%i in ('docker exec opencode-cli npm --version 2^>^&1') do set NPM_VERSION=%%i
    echo [PASS] npm 版本: !NPM_VERSION!
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 Git 版本
docker exec opencode-cli git --version >nul 2>&1
if errorlevel 1 (
    echo [FAIL] Git 不可用
    set /a FAIL=FAIL+1
) else (
    for /f "tokens=*" %%i in ('docker exec opencode-cli git --version 2^>^&1') do set GIT_VERSION=%%i
    echo [PASS] Git 版本: !GIT_VERSION!
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 Bash 版本
docker exec opencode-cli bash --version >nul 2>&1
if errorlevel 1 (
    echo [FAIL] Bash 不可用
    set /a FAIL=FAIL+1
) else (
    for /f "tokens=1,2,3,4,5,6,7,8" %%a in ('docker exec opencode-cli bash --version 2^>^&1') do set BASH_VERSION=%%a %%b %%c %%d %%e %%f %%g %%h
    echo [PASS] Bash 版本: !BASH_VERSION!
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 vibe-kanban（使用 npx）
docker exec opencode-cli sh -c "command -v npx > /dev/null 2>&1" >nul 2>&1
if errorlevel 1 (
    echo [FAIL] npx 不可用
    set /a FAIL=FAIL+1
) else (
    echo [PASS] npx 可用
    echo [INFO] vibe-kanban 应该已在构建时预下载
    docker exec opencode-cli sh -c "ls ~/.npm/_npx 2>/dev/null && echo '  ✓ 缓存已创建（构建时预下载）' || echo '  ⚠ 缓存未创建（运行时会下载）'"
    set /a PASS=PASS+1
)
echo.

REM Server 功能测试
echo ========================================
echo Server 功能测试
echo ========================================

echo [TEST] 检查 Server 端口监听
docker port opencode-cli 3000 >nul 2>&1
if errorlevel 1 (
    echo [FAIL] Server 端口未映射
    set /a FAIL=FAIL+1
) else (
    for /f "delims=" %%i in ('docker port opencode-cli 3000 2^>^&1') do set PORT_MAPPING=%%i
    echo [PASS] Server 端口映射: !PORT_MAPPING!
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 Server HTTP 响应
curl -s -o nul -w "%%{http_code}" http://localhost:3000 2>nul | findstr "200" >nul 2>&1
if errorlevel 1 (
    echo [FAIL] Server HTTP 响应失败
    set /a FAIL=FAIL+1
) else (
    for /f "delims=" %%i in ('curl -s -o nul -w "%%{http_code}" http://localhost:3000 2^>nul') do set HTTP_CODE=%%i
    echo [PASS] Server HTTP 响应: !HTTP_CODE!
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 Vibe Kanban 端口监听
docker port opencode-cli 3001 >nul 2>&1
if errorlevel 1 (
    echo [FAIL] Vibe Kanban 端口未映射
    set /a FAIL=FAIL+1
) else (
    for /f "delims=" %%i in ('docker port opencode-cli 3001 2^>^&1') do set VIBE_PORT_MAPPING=%%i
    echo [PASS] Vibe Kanban 端口映射: !VIBE_PORT_MAPPING!
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 Vibe Kanban HTTP 响应
curl -s -o nul -w "%%{http_code}" http://localhost:3001 2>nul | findstr "200" >nul 2>&1
if errorlevel 1 (
    echo [WARN] Vibe Kanban HTTP 响应失败（可能还在启动中）
    set /a WARN=WARN+1
) else (
    for /f "delims=" %%i in ('curl -s -o nul -w "%%{http_code}" http://localhost:3001 2^>nul') do set VIBE_HTTP_CODE=%%i
    echo [PASS] Vibe Kanban HTTP 响应: !VIBE_HTTP_CODE!
    set /a PASS=PASS+1
)
echo.

REM 系统配置测试
echo ========================================
echo 系统配置测试
echo ========================================

echo [TEST] 检查文件权限
docker exec opencode-cli ls -l /usr/local/bin/opencode >nul 2>&1
if errorlevel 1 (
    echo [FAIL] opencode 文件不存在或权限不足
    set /a FAIL=FAIL+1
) else (
    echo [PASS] opencode 文件存在
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查配置文件
docker exec opencode-cli cat /app/.opencode/config.json >nul 2>&1
if errorlevel 1 (
    echo [FAIL] 配置文件不存在
    set /a FAIL=FAIL+1
) else (
    echo [PASS] 配置文件存在
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查工作目录挂载
docker exec opencode-cli ls -d /app/workspace >nul 2>&1
if errorlevel 1 (
    echo [FAIL] 工作目录未挂载
    set /a FAIL=FAIL+1
) else (
    echo [PASS] 工作目录挂载: /app/workspace
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 nop-entropy 项目挂载
docker exec opencode-cli ls -d /app/workspace/nop-entropy >nul 2>&1
if errorlevel 1 (
    echo [FAIL] nop-entropy 项目未挂载
    set /a FAIL=FAIL+1
) else (
    echo [PASS] nop-entropy 项目已挂载
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查 share 目录挂载
docker exec opencode-cli ls -d /home/opencode/share >nul 2>&1
if errorlevel 1 (
    echo [WARN] share 目录未挂载
    set /a WARN=WARN+1
) else (
    echo [PASS] share 目录已挂载: /home/opencode/share
    set /a PASS=PASS+1
)
echo.

echo [TEST] 检查环境变量
docker exec opencode-cli printenv OPENCODE_HOME >nul 2>&1
if errorlevel 1 (
    echo [FAIL] 环境变量不正确
    set /a FAIL=FAIL+1
) else (
    for /f "delims=" %%i in ('docker exec opencode-cli printenv OPENCODE_HOME 2^>^&1') do set OPENCODE_HOME=%%i
    for /f "delims=" %%i in ('docker exec opencode-cli printenv OPENCODE_WORKSPACE 2^>^&1') do set OPENCODE_WORKSPACE=%%i
    echo [PASS] 环境变量正确: OPENCODE_HOME=!OPENCODE_HOME!, OPENCODE_WORKSPACE=!OPENCODE_WORKSPACE!
    set /a PASS=PASS+1
)
echo.

REM 总结
echo ========================================
echo 测试结果总结
echo ========================================
echo 通过: !PASS!
echo 失败: !FAIL!
echo 警告: !WARN!
set /a TOTAL=PASS+FAIL+WARN
echo 总计: !TOTAL!
echo.

if !FAIL! equ 0 (
    echo [PASS] 所有测试通过！镜像可以正常使用！
    exit /b 0
) else (
    echo [FAIL] 有 !FAIL! 个测试失败，请检查配置
    exit /b 1
)
