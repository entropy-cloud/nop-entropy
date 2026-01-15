#!/bin/bash
# ========================================
# Opencode Hybrid Mode Build Script
# ========================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
print_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

main() {
    echo "========================================"
    echo "Opencode Hybrid Mode Build Script"
    echo "========================================"
    echo ""

    BUILD_ARGS=""
    PUSH=0

    while [[ $# -gt 0 ]]; do
        case $1 in
            --no-cache) BUILD_ARGS="--no-cache"; shift ;;
            --push) PUSH=1; shift ;;
            --help)
                echo "Usage: $0 [options]"
                echo "  --no-cache    Build without cache"
                echo "  --push        Push to Docker Hub"
                echo "  --help        Show help"
                exit 0 ;;
            *) echo "Unknown: $1"; exit 1 ;;
        esac
    done

    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    cd "$SCRIPT_DIR"

    echo "Step 1/4: Clean old image (optional)"
    read -p "Delete old? (y/N): " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker rmi opencode-hybrid:latest 2>/dev/null
        print_info "Old image deleted"
    fi

    echo "Step 2/4: Build Docker image"
    docker build $BUILD_ARGS -f Dockerfile -t opencode-hybrid:latest .
    if [ $? -ne 0 ]; then
        print_error "Build failed!"
        exit 1
    fi
    print_info "Build successful!"

    echo "Step 3/4: Verify image"
    docker images opencode-hybrid:latest
    print_info "Verification successful"

    if [ "$PUSH" -eq 1 ]; then
        echo "Step 4/4: Push to Docker Hub"
        docker login
        docker push opencode-hybrid:latest
        print_info "Push successful"
    else
        echo "Step 4/4: Done"
    fi

    echo ""
    echo "========================================"
    print_info "Build complete!"
    echo "========================================"
    echo ""
    echo "Usage:"
    echo "  docker-compose up -d"
    echo "  docker exec -it opencode-cli bash"
    echo "  OpenCode Desktop: http://localhost:3000"
    echo ""
}

main "$@"
