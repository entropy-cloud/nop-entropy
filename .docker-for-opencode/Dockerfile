# ========================================
# Opencode CLI + Server 混合模式镜像（简化版 - 单层构建）
# ========================================
# 功能：
# 1. OpenCode CLI + Server + oh-my-opencode
# 2. openspec CLI
# 3. vibe-kanban AI 代理管理工具
# 4. 双端口支持：OpenCode (3000) + Vibe Kanban (3001)
# ========================================

FROM node:20-alpine3.20

# 设置工作目录
WORKDIR /app

# 创建非 root 用户（安装前创建，避免权限问题）
RUN addgroup -g 1001 opencode && \
    adduser -D -u 1001 -G opencode opencode

# ========================================
# 单层安装：安装所有依赖并清理缓存
# ========================================
RUN apk add --no-cache \
        curl \
        git \
        bash \
        openssh-client \
        ca-certificates \
        vim \
        less \
        dumb-init && \
    \
    # 安装 OpenCode CLI
    npm install -g opencode-ai@latest && \
    \
    # 清理不需要的平台二进制（节省约 405MB）
    rm -rf /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64 && \
    rm -rf /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-baseline && \
    rm -rf /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-baseline-musl && \
    cp /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-musl/bin/opencode /usr/local/bin/opencode && \
    chmod +x /usr/local/bin/opencode && \
    \
    # 安装 oh-my-opencode（全局）
    npx oh-my-opencode install || \
    echo "Warning: oh-my-opencode installation may need manual setup" && \
    \
    # 安装 openspec CLI
    npm install -g @fission-ai/openspec@latest && \
    chmod +x /usr/local/bin/openspec && \
    \
    # 全局安装 vibe-kanban
    npm install -g vibe-kanban@latest && \
    \
    # 清理 npm 缓存
    npm cache clean --force && \
    rm -rf /root/.npm/_cacache && \
    \
    # 清理 apk 缓存（如果有的话）
    rm -rf /var/cache/apk/* && \
    \
    # 创建必要的目录
    mkdir -p /app/workspace && \
    mkdir -p /root/.local/share && \
    \
    # 设置所有权
    chown -R opencode:opencode /app

# ========================================
# 创建启动脚本
# ========================================
RUN echo '#!/bin/sh' > /app/start-services.sh && \
    echo 'set -x' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 函数：优雅地终止所有服务' >> /app/start-services.sh && \
    echo 'cleanup() {' >> /app/start-services.sh && \
    echo '    echo "Shutting down services..."' >> /app/start-services.sh && \
    echo '    pkill -f "opencode serve" 2>/dev/null || true' >> /app/start-services.sh && \
    echo '    pkill -f "vibe-kanban" 2>/dev/null || true' >> /app/start-services.sh && \
    echo '    exit 0' >> /app/start-services.sh && \
    echo '}' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 捕获终止信号' >> /app/start-services.sh && \
    echo 'trap cleanup SIGTERM SIGINT' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 启动 OpenCode Server (端口 3000)' >> /app/start-services.sh && \
    echo 'echo "Starting OpenCode Server on port 3000..."' >> /app/start-services.sh && \
    echo 'opencode serve --port 3000 --hostname 0.0.0.0 --mdns &' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 启动 Vibe Kanban (端口 3001) - 带重试机制' >> /app/start-services.sh && \
    echo 'echo "Starting Vibe Kanban on port 3001..."' >> /app/start-services.sh && \
    echo 'echo "Note: First run may take 10-20 seconds to start vibe-kanban"' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 启动 vibe-kanban 并监控日志' >> /app/start-services.sh && \
    echo 'MAX_RETRIES=5' >> /app/start-services.sh && \
    echo 'RETRY_COUNT=0' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo 'while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do' >> /app/start-services.sh && \
    echo '    RETRY_COUNT=$((RETRY_COUNT + 1))' >> /app/start-services.sh && \
    echo '    echo "Attempt $RETRY_COUNT to start vibe-kanban..."' >> /app/start-services.sh && \
    echo '    ' >> /app/start-services.sh && \
    echo '    # 清空之前的日志' >> /app/start-services.sh && \
    echo '    > /tmp/vibe.log' >> /app/start-services.sh && \
    echo '    ' >> /app/start-services.sh && \
    echo '    # 启动 vibe-kanban（后台运行）' >> /app/start-services.sh && \
    echo '    PORT=3001 HOST=0.0.0.0 vibe-kanban > /tmp/vibe.log 2>&1 &' >> /app/start-services.sh && \
    echo '    VIBE_PID=$!' >> /app/start-services.sh && \
    echo '    ' >> /app/start-services.sh && \
    echo '    # 等待一段时间让 vibe-kanban 初始化' >> /app/start-services.sh && \
    echo '    sleep 15' >> /app/start-services.sh && \
    echo '    ' >> /app/start-services.sh && \
    echo '    # 检查是否下载失败' >> /app/start-services.sh && \
    echo '    if grep -q "Download failed" /tmp/vibe.log; then' >> /app/start-services.sh && \
    echo '        echo "Warning: vibe-kanban download failed on attempt $RETRY_COUNT"' >> /app/start-services.sh && \
    echo '        pkill -f "vibe-kanban" 2>/dev/null || true' >> /app/start-services.sh && \
    echo '        ' >> /app/start-services.sh && \
    echo '        if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then' >> /app/start-services.sh && \
    echo '            echo "Retrying in 10 seconds..."' >> /app/start-services.sh && \
    echo '            sleep 10' >> /app/start-services.sh && \
    echo '            continue' >> /app/start-services.sh && \
    echo '        else' >> /app/start-services.sh && \
    echo '            echo "Warning: Failed to start vibe-kanban after $MAX_RETRIES attempts"' >> /app/start-services.sh && \
    echo '            echo "Vibe Kanban may not be available due to network issues."' >> /app/start-services.sh && \
    echo '            echo "This is NOT a critical error - OpenCode Server is still running."' >> /app/start-services.sh && \
    echo '            echo "To retry vibe-kanban: docker exec -it <container> bash -c '\''PORT=3001 HOST=0.0.0.0 vibe-kanban'\''"' >> /app/start-services.sh && \
    echo '        fi' >> /app/start-services.sh && \
    echo '        break' >> /app/start-services.sh && \
    echo '    else' >> /app/start-services.sh && \
    echo '        # 检查进程是否还在运行' >> /app/start-services.sh && \
    echo '        if ps -p $VIBE_PID > /dev/null 2>&1; then' >> /app/start-services.sh && \
    echo '            echo "Vibe Kanban started successfully"' >> /app/start-services.sh && \
    echo '            break' >> /app/start-services.sh && \
    echo '        else' >> /app/start-services.sh && \
    echo '            echo "Warning: vibe-kanban process died on attempt $RETRY_COUNT"' >> /app/start-services.sh && \
    echo '            if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then' >> /app/start-services.sh && \
    echo '            echo "Retrying in 10 seconds..."' >> /app/start-services.sh && \
    echo '            sleep 10' >> /app/start-services.sh && \
    echo '        else' >> /app/start-services.sh && \
    echo '            echo "Warning: Failed to start vibe-kanban after $MAX_RETRIES attempts"' >> /app/start-services.sh && \
    echo '            echo "Vibe Kanban may not be available due to network issues."' >> /app/start-services.sh && \
    echo '            fi' >> /app/start-services.sh && \
    echo '        fi' >> /app/start-services.sh && \
    echo '    fi' >> /app/start-services.sh && \
    echo 'done' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 等待服务启动' >> /app/start-services.sh && \
    echo 'sleep 5' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 打印服务状态' >> /app/start-services.sh && \
    echo 'echo "======================================="' >> /app/start-services.sh && \
    echo 'echo "Services Status:"' >> /app/start-services.sh && \
    echo 'echo "  OpenCode Server - http://localhost:3000 [RUNNING]"' >> /app/start-services.sh && \
    echo 'echo "  Vibe Kanban - http://localhost:3001 [CHECK MANUALLY]"' >> /app/start-services.sh && \
    echo 'echo "======================================="' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 如果 vibe-kanban 进程存在，则监控其日志' >> /app/start-services.sh && \
    echo 'if pgrep -f "vibe-kanban" > /dev/null 2>&1; then' >> /app/start-services.sh && \
    echo '    echo "Monitoring vibe-kanban logs (Ctrl+C to stop monitoring)..."' >> /app/start-services.sh && \
    echo '    tail -f /tmp/vibe.log &' >> /app/start-services.sh && \
    echo 'fi' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 保持容器运行（监控 OpenCode Server）' >> /app/start-services.sh && \
    echo 'echo "Container is running. OpenCode Server is available."' >> /app/start-services.sh && \
    echo 'echo "Use Ctrl+C to stop monitoring, but container will continue running."' >> /app/start-services.sh && \
    echo 'wait' >> /app/start-services.sh && \
    chmod +x /app/start-services.sh

# ========================================
# 环境变量
# ========================================
ENV NODE_ENV=production
ENV OPENCODE_HOME=/app
ENV OPENCODE_WORKSPACE=/app/workspace
ENV OPENCODE_CONFIG=/app/.opencode/config.json
ENV PATH=/usr/local/bin:/usr/bin:/bin:/sbin
ENV VIBE_KANBAN_PORT=3001
ENV VIBE_KANBAN_HOST=0.0.0.0

# ========================================
# 暴露端口
# ========================================
# 3000: OpenCode Server
# 3001: Vibe Kanban
EXPOSE 3000 3001

# ========================================
# 挂载点
# ========================================
VOLUME ["/app/workspace"]

# ========================================
# 启动命令（启动 OpenCode Server + Vibe Kanban）
# ========================================
CMD ["dumb-init", "/app/start-services.sh"]

# ========================================
# 镜像元数据
# ========================================
LABEL maintainer="Nop Platform Team"
LABEL description="Opencode CLI + Server with oh-my-opencode + vibe-kanban (Simplified Single-Layer Build)"
LABEL version="7.0.0"
LABEL org.opencontainers.image.title="Opencode Hybrid v7.0 (Simplified - Single Layer)"
LABEL org.opencontainers.image.description="Opencode CLI + Server + oh-my-opencode + openspec + vibe-kanban. Simplified single-layer build without multi-stage complexity."
LABEL org.opencontainers.image.version="7.0.0"
LABEL usage.cli="docker exec -it opencode-cli bash"
LABEL usage.gui="OpenCode Desktop connect to http://localhost:3000"
LABEL usage.kanban="Vibe Kanban available at http://localhost:3001"
LABEL services="opencode-server:3000,vibe-kanban:3001"
LABEL build.type="single-layer"

# ========================================
# 健康检查
# ========================================
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD opencode --version || exit 1
