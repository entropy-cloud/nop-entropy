#!/bin/bash
# ========================================
# Opencode Docker 验证脚本
# ========================================

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 计数器
PASS=0
FAIL=0
WARN=0

# 打印信息
print_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

print_test() {
    echo -e "\n${YELLOW}[TEST]${NC} $1"
}

print_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASS++))
}

print_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAIL++))
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    ((WARN++))
}

# 测试函数
test_container_running() {
    print_test "检查容器是否运行"

    RUNNING=$(docker ps --format '{{.Names}}' | grep opencode-cli)
    if [ -n "$RUNNING" ]; then
        print_pass "容器正在运行"
        return 0
    else
        print_fail "容器未运行"
        return 1
    fi
}

test_opencode_version() {
    print_test "检查 OpenCode CLI 版本"

    VERSION=$(docker exec opencode-cli opencode --version 2>/dev/null)
    if [ $? -eq 0 ]; then
        print_pass "OpenCode CLI 版本: $VERSION"
        return 0
    else
        print_fail "OpenCode CLI 不可用"
        return 1
    fi
}

test_openspec_version() {
    print_test "检查 openspec 版本"

    VERSION=$(docker exec opencode-cli openspec --version 2>/dev/null)
    if [ $? -eq 0 ]; then
        print_pass "openspec 版本: $VERSION"
        return 0
    else
        print_fail "openspec 不可用"
        return 1
    fi
}

test_node_version() {
    print_test "检查 Node.js 版本"

    VERSION=$(docker exec opencode-cli node --version 2>/dev/null)
    if [ $? -eq 0 ]; then
        print_pass "Node.js 版本: $VERSION"
        return 0
    else
        print_fail "Node.js 不可用"
        return 1
    fi
}

test_npm_version() {
    print_test "检查 npm 版本"

    VERSION=$(docker exec opencode-cli npm --version 2>/dev/null)
    if [ $? -eq 0 ]; then
        print_pass "npm 版本: $VERSION"
        return 0
    else
        print_fail "npm 不可用"
        return 1
    fi
}

test_git_version() {
    print_test "检查 Git 版本"

    VERSION=$(docker exec opencode-cli git --version 2>/dev/null)
    if [ $? -eq 0 ]; then
        print_pass "Git 版本: $VERSION"
        return 0
    else
        print_fail "Git 不可用"
        return 1
    fi
}

test_bash_version() {
    print_test "检查 Bash 版本"

    VERSION=$(docker exec opencode-cli bash --version 2>/dev/null | head -1)
    if [ $? -eq 0 ]; then
        print_pass "Bash 版本: $VERSION"
        return 0
    else
        print_fail "Bash 不可用"
        return 1
    fi
}

test_server_port() {
    print_test "检查 Server 端口监听"

    PORT=$(docker port opencode-cli 3000 2>/dev/null)
    if [ -n "$PORT" ]; then
        print_pass "Server 端口映射: $PORT"
        return 0
    else
        print_fail "Server 端口未映射"
        return 1
    fi
}

test_server_http() {
    print_test "检查 Server HTTP 响应"

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000 2>/dev/null)
    if [ "$HTTP_CODE" = "200" ]; then
        print_pass "Server HTTP 响应: $HTTP_CODE"
        return 0
    else
        print_fail "Server HTTP 响应失败: $HTTP_CODE"
        return 1
    fi
}

test_file_permissions() {
    print_test "检查文件权限"

    PERM=$(docker exec opencode-cli ls -l /usr/local/bin/opencode 2>/dev/null | awk '{print $1}')
    if [ -n "$PERM" ]; then
        print_pass "opencode 权限: $PERM"
        return 0
    else
        print_fail "opencode 文件不存在或权限不足"
        return 1
    fi
}

test_config_file() {
    print_test "检查配置文件"

    CONFIG=$(docker exec opencode-cli cat /app/.opencode/config.json 2>/dev/null)
    if [ -n "$CONFIG" ]; then
        print_pass "配置文件存在"
        return 0
    else
        print_fail "配置文件不存在"
        return 1
    fi
}

test_workspace_mount() {
    print_test "检查工作目录挂载"

    MOUNT=$(docker exec opencode-cli ls -d /app/workspace 2>/dev/null)
    if [ -n "$MOUNT" ]; then
        print_pass "工作目录挂载: $MOUNT"
        return 0
    else
        print_fail "工作目录未挂载"
        return 1
    fi
}

test_environment_vars() {
    print_test "检查环境变量"

    OPENCODE_HOME=$(docker exec opencode-cli printenv OPENCODE_HOME 2>/dev/null)
    OPENCODE_WORKSPACE=$(docker exec opencode-cli printenv OPENCODE_WORKSPACE 2>/dev/null)

    if [ -n "$OPENCODE_HOME" ] && [ -n "$OPENCODE_WORKSPACE" ]; then
        print_pass "环境变量正确: OPENCODE_HOME=$OPENCODE_HOME, OPENCODE_WORKSPACE=$OPENCODE_WORKSPACE"
        return 0
    else
        print_fail "环境变量不正确"
        return 1
    fi
}

# 主函数
main() {
    print_header "Opencode Docker 验证测试"

    # 检查 Docker
    print_test "检查 Docker 是否运行"
    if ! docker info > /dev/null 2>&1; then
        print_fail "Docker 未运行，请先启动 Docker"
        exit 1
    else
        print_pass "Docker 正在运行"
    fi

    # 检查容器
    if ! test_container_running; then
        print_fail "容器未运行，请先启动容器: docker-compose up -d"
        exit 1
    fi

    # 核心功能测试
    print_header "核心功能测试"

    test_opencode_version
    test_openspec_version
    test_node_version
    test_npm_version
    test_git_version
    test_bash_version

    # Server 功能测试
    print_header "Server 功能测试"

    test_server_port
    test_server_http

    # 系统配置测试
    print_header "系统配置测试"

    test_file_permissions
    test_config_file
    test_workspace_mount
    test_environment_vars

    # 总结
    print_header "测试结果总结"

    TOTAL=$((PASS + FAIL + WARN))

    echo -e "${GREEN}通过: $PASS${NC}"
    echo -e "${RED}失败: $FAIL${NC}"
    echo -e "${YELLOW}警告: $WARN${NC}"
    echo -e "总计: $TOTAL${NC}"

    if [ $FAIL -eq 0 ]; then
        echo ""
        print_pass "所有测试通过！镜像可以正常使用！"
        exit 0
    else
        echo ""
        print_fail "有 $FAIL 个测试失败，请检查配置"
        exit 1
    fi
}

# 执行主函数
main
