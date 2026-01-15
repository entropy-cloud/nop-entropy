#!/bin/bash
# ========================================
# Opencode Docker 验证脚本
# ========================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PASS=0
FAIL=0
WARN=0

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

test_container_running() {
    print_test "检查容器是否运行"
    RUNNING=$(docker ps --format '{{.Names}}' | grep opencode-cli)
    if [ -n "$RUNNING" ]; then
        print_pass "容器正在运行"
    else
        print_fail "容器未运行"
    fi
}

test_opencode_version() {
    print_test "检查 OpenCode CLI 版本"
    VERSION=$(docker exec opencode-cli opencode --version 2>/dev/null)
    if [ $? -eq 0 ]; then
        print_pass "OpenCode CLI 版本: $VERSION"
    else
        print_fail "OpenCode CLI 不可用"
    fi
}

test_openspec_version() {
    print_test "检查 openspec 版本"
    VERSION=$(docker exec opencode-cli openspec --version 2>/dev/null)
    if [ $? -eq 0 ]; then
        print_pass "openspec 版本: $VERSION"
    else
        print_fail "openspec 不可用"
    fi
}

test_node_version() {
    print_test "检查 Node.js 版本"
    VERSION=$(docker exec opencode-cli node --version 2>/dev/null)
    if [ $? -eq 0 ]; then
        print_pass "Node.js 版本: $VERSION"
    else
        print_fail "Node.js 不可用"
    fi
}

test_server_port() {
    print_test "检查 Server 端口"
    PORT=$(docker port opencode-cli 3000 2>/dev/null)
    if [ -n "$PORT" ]; then
        print_pass "Server 端口映射: $PORT"
    else
        print_fail "Server 端口未映射"
    fi
}

test_server_http() {
    print_test "检查 Server HTTP 响应"
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000 2>/dev/null)
    if [ "$HTTP_CODE" = "200" ]; then
        print_pass "Server HTTP 响应: $HTTP_CODE"
    else
        print_fail "Server HTTP 响应失败: $HTTP_CODE"
    fi
}

test_config_file() {
    print_test "检查配置文件"
    CONFIG=$(docker exec opencode-cli cat /app/.opencode/config.json 2>/dev/null)
    if [ -n "$CONFIG" ]; then
        print_pass "配置文件存在"
    else
        print_fail "配置文件不存在"
    fi
}

main() {
    print_header "Opencode Docker 验证测试"

    # 检查 Docker
    print_test "检查 Docker 是否运行"
    if ! docker info > /dev/null 2>&1; then
        print_fail "Docker 未运行"
        exit 1
    else
        print_pass "Docker 正在运行"
    fi

    test_container_running
    if [ $FAIL -gt 0 ]; then
        exit 1
    fi

    print_header "核心功能测试"
    test_opencode_version
    test_openspec_version
    test_node_version

    print_header "Server 功能测试"
    test_server_port
    test_server_http

    print_header "系统配置测试"
    test_config_file

    print_header "测试结果总结"
    TOTAL=$((PASS + FAIL + WARN))
    echo -e "${GREEN}通过: $PASS${NC}"
    echo -e "${RED}失败: $FAIL${NC}"
    echo -e "总计: $TOTAL${NC}"

    if [ $FAIL -eq 0 ]; then
        echo ""
        print_pass "所有测试通过！"
        exit 0
    else
        echo ""
        print_fail "有 $FAIL 个测试失败"
        exit 1
    fi
}

main
