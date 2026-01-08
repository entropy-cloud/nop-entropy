#!/bin/bash
# ========================================
# Opencode Hybrid Mode Build Script (Linux/Mac)
# ========================================

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印信息
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 主函数
main() {
    echo "========================================"
    echo "Opencode Hybrid Mode Build Script"
    echo "========================================"
    echo ""

    # 解析参数
    BUILD_ARGS=""
    PUSH=0

    while [[ $# -gt 0 ]]; do
        case $1 in
            --no-cache)
                BUILD_ARGS="--no-cache"
                shift
                ;;
            --push)
                PUSH=1
                shift
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                echo "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done

    # 获取脚本目录
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    cd "$SCRIPT_DIR"

    # Step 1: 清理旧镜像（可选）
    echo ""
    print_info "Step 1/4: Clean old image (optional)"
    echo ""
    read -p "Delete old image? (y/N): " -n 1 -r
    echo ""

    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "Deleting old image..."
        docker rmi opencode-hybrid:latest 2>/dev/null

        if [ $? -eq 0 ]; then
            print_info "Old image deleted"
        else
            print_warn "Old image not found or already deleted"
        fi
    fi

    echo ""

    # Step 2: 构建 Docker 镜像
    print_info "Step 2/4: Build Docker image"
    echo ""
    print_info "Building image: opencode-hybrid:latest"
    echo ""

    docker build $BUILD_ARGS -f Dockerfile -t opencode-hybrid:latest .

    if [ $? -ne 0 ]; then
        echo ""
        print_error "Image build failed!"
        echo ""
        echo "Please check:"
        echo "  1. Docker is running"
        echo "  2. Dockerfile exists"
        echo "  3. Network connection is OK"
        echo ""
        exit 1
    fi

    echo ""
    print_info "Image build successful!"
    echo ""

    # Step 3: 验证镜像
    print_info "Step 3/4: Verify image"
    echo ""
    docker images opencode-hybrid:latest

    if [ $? -ne 0 ]; then
        print_error "Image verification failed"
        exit 1
    fi

    echo ""
    print_info "Image verification successful"
    echo ""

    # Step 4: 推送镜像（可选）
    if [ "$PUSH" -eq 1 ]; then
        print_info "Step 4/4: Push image to Docker Hub"
        echo ""

        print_info "Login to Docker Hub..."
        docker login

        if [ $? -ne 0 ]; then
            print_error "Docker Hub login failed"
            exit 1
        fi

        print_info "Pushing image..."
        docker push opencode-hybrid:latest

        if [ $? -ne 0 ]; then
            print_error "Image push failed"
            exit 1
        fi

        echo ""
        print_info "Image push successful"
        echo ""
    else
        print_info "Step 4/4: Done"
        echo ""
    fi

    # 显示使用说明
    echo "========================================"
    print_info "Image build successful!"
    echo "========================================"
    echo ""
    echo "Features of Hybrid Mode:"
    print_info "CLI + Server dual mode"
    print_info "Command-line: docker exec -it opencode-cli bash"
    print_info "GUI: OpenCode Desktop connect to http://localhost:3000"
    print_info "Can use both methods simultaneously"
    echo ""
    echo "Usage:"
    echo ""
    echo "1. Start container:"
    echo "   docker-compose up -d"
    echo ""
    echo "2. Use CLI:"
    echo "   docker exec -it opencode-cli bash"
    echo "   opencode \"Hello\""
    echo ""
    echo "3. Use GUI:"
    echo "   Configure OpenCode Desktop to connect to http://localhost:3000"
    echo ""
    echo "Detailed documentation:"
    echo "   - Hybrid mode usage: USAGE.md"
    echo "   - Deployment summary: SUMMARY.md"
    echo "   - Troubleshooting: TROUBLESHOOTING.md"
    echo ""

    exit 0
}

# 显示帮助信息
show_help() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  --no-cache    Build without cache"
    echo "  --push        Push image to Docker Hub after build"
    echo "  --help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0"
    echo "  $0 --no-cache"
    echo "  $0 --push"
}

# 执行主函数
main "$@"
