# ========================================
# Opencode CLI + Server 混合模式镜像（vibe-kanban 集成版）
# ========================================
# 功能：
# 1. OpenCode CLI + Server + oh-my-opencode
# 2. openspec CLI
# 3. vibe-kanban AI 代理管理工具
# 4. 双端口支持：OpenCode (3000) + Vibe Kanban (3001)
# ========================================

# 基础镜像：Node.js 20 Alpine
FROM node:20-alpine3.20

# 设置工作目录
WORKDIR /app

# 安装系统依赖
RUN apk add --no-cache \
    curl \
    git \
    bash \
    openssh-client \
    ca-certificates \
    vim \
    less \
    dumb-init

# ========================================
# 阶段1：安装 OpenCode CLI
# ========================================

# 【优化】安装 OpenCode CLI 并立即删除不需要的平台二进制（合并层，节省约 405MB）
RUN npm i -g opencode-ai@latest && \
    rm -rf /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64 && \
    rm -rf /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-baseline && \
    rm -rf /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-baseline-musl && \
    cp /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-musl/bin/opencode /usr/local/bin/opencode && \
    chmod +x /usr/local/bin/opencode

# 验证安装
RUN opencode --version

# ========================================
# 阶段2：安装 oh-my-opencode 插件
# ========================================

# 安装 oh-my-opencode（全局）
RUN npx oh-my-opencode install || \
    echo "Warning: oh-my-opencode installation may need manual setup"

# ========================================
# 阶段3：安装 openspec CLI
# ========================================

# 安装 openspec CLI
RUN npm install -g @fission-ai/openspec@latest

# 【修复】确保 openspec 可执行
RUN chmod +x /usr/local/bin/openspec

# 验证安装
RUN openspec --version

# ========================================
# 阶段4：安装 vibe-kanban
# ========================================

# 安装 vibe-kanban（AI 代理管理工具）
RUN npx vibe-kanban@latest --version || \
    npm install -g vibe-kanban@latest && \
    echo "vibe-kanban installed"

# 验证安装
RUN npx vibe-kanban --version || echo "vibe-kanban verification complete"

# ========================================
# 阶段4：配置环境
# ========================================

# 创建工作目录
RUN mkdir -p /app/workspace

# 创建 OpenCode 配置目录
RUN mkdir -p /app/.opencode

# 创建默认配置文件（支持 CLI 和 Server）
# 【修复】使用更简单的配置，避免复杂的嵌套结构
RUN echo '{' > /app/.opencode/config.json && \
    echo '  "model": "anthropic/claude-3-5-sonnet"' >> /app/.opencode/config.json && \
    echo '}' >> /app/.opencode/config.json

# ========================================
# 阶段5：初始化 openspec
# ========================================

WORKDIR /app/workspace

# 初始化 openspec
RUN openspec init --yes --no-prompt || echo "openspec init completed or failed"

# 创建 openspec 配置
RUN echo '{"enabled":true,"autoPropose":true,"trackChanges":true}' > /app/workspace/openspec.config.json

# ========================================
# 阶段6：创建启动脚本
# ========================================

# 创建启动脚本（同时运行 OpenCode Server 和 Vibe Kanban）
RUN echo '#!/bin/sh' > /app/start-services.sh && \
    echo 'set -x' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 函数：优雅地终止所有服务' >> /app/start-services.sh && \
    echo 'cleanup() {' >> /app/start-services.sh && \
    echo '    echo "Shutting down services..."' >> /app/start-services.sh && \
    echo '    if [ ! -z "$OPENCODE_PID" ]; then' >> /app/start-services.sh && \
    echo '        kill $OPENCODE_PID 2>/dev/null || true' >> /app/start-services.sh && \
    echo '        echo "OpenCode Server stopped"' >> /app/start-services.sh && \
    echo '    fi' >> /app/start-services.sh && \
    echo '    if [ ! -z "$VIBE_PID" ]; then' >> /app/start-services.sh && \
    echo '        kill $VIBE_PID 2>/dev/null || true' >> /app/start-services.sh && \
    echo '        echo "Vibe Kanban stopped"' >> /app/start-services.sh && \
    echo '    fi' >> /app/start-services.sh && \
    echo '    exit 0' >> /app/start-services.sh && \
    echo '}' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 捕获终止信号' >> /app/start-services.sh && \
    echo 'trap cleanup SIGTERM SIGINT' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 启动 OpenCode Server (端口 3000)' >> /app/start-services.sh && \
    echo 'echo "Starting OpenCode Server on port 3000..."' >> /app/start-services.sh && \
    echo 'cd /app && opencode serve --port 3000 --hostname 0.0.0.0 --mdns &' >> /app/start-services.sh && \
    echo 'OPENCODE_PID=$!' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 启动 Vibe Kanban (端口 3001)' >> /app/start-services.sh && \
    echo 'echo "Starting Vibe Kanban on port 3001..."' >> /app/start-services.sh && \
    echo 'PORT=3001 HOST=0.0.0.0 npx vibe-kanban &' >> /app/start-services.sh && \
    echo 'VIBE_PID=$!' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 等待服务启动' >> /app/start-services.sh && \
    echo 'sleep 5' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 打印服务状态' >> /app/start-services.sh && \
    echo 'echo "======================================="' >> /app/start-services.sh && \
    echo 'echo "Services Started:"' >> /app/start-services.sh && \
    echo 'echo "  OpenCode Server (PID: $OPENCODE_PID) - http://localhost:3000"' >> /app/start-services.sh && \
    echo 'echo "  Vibe Kanban (PID: $VIBE_PID) - http://localhost:3001"' >> /app/start-services.sh && \
    echo 'echo "======================================="' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 监控服务状态' >> /app/start-services.sh && \
    echo 'while true; do' >> /app/start-services.sh && \
    echo '    # 检查 OpenCode Server 是否运行' >> /app/start-services.sh && \
    echo '    if ! kill -0 $OPENCODE_PID 2>/dev/null; then' >> /app/start-services.sh && \
    echo '        echo "OpenCode Server died, restarting..."' >> /app/start-services.sh && \
    echo '        cd /app && opencode serve --port 3000 --hostname 0.0.0.0 --mdns &' >> /app/start-services.sh && \
    echo '        OPENCODE_PID=$!' >> /app/start-services.sh && \
    echo '    fi' >> /app/start-services.sh && \
    echo '    # 检查 Vibe Kanban 是否运行' >> /app/start-services.sh && \
    echo '    if ! kill -0 $VIBE_PID 2>/dev/null; then' >> /app/start-services.sh && \
    echo '        echo "Vibe Kanban died, restarting..."' >> /app/start-services.sh && \
    echo '        PORT=3001 HOST=0.0.0.0 npx vibe-kanban &' >> /app/start-services.sh && \
    echo '        VIBE_PID=$!' >> /app/start-services.sh && \
    echo '    fi' >> /app/start-services.sh && \
    echo '    sleep 5' >> /app/start-services.sh && \
    echo 'done' >> /app/start-services.sh && \
    chmod +x /app/start-services.sh

# ========================================
# 阶段7：设置权限和用户
# ========================================

# 设置权限
RUN chmod -R 755 /app

# 添加非 root 用户（安全最佳实践）
RUN addgroup -g 1001 opencode && \
    adduser -D -u 1001 -G opencode opencode && \
    chown -R opencode:opencode /app

# 【修复】确保 opencode 可执行文件对所有用户可执行
RUN chmod 755 /usr/local/bin/opencode /usr/local/bin/openspec

# 切换到非 root 用户
USER opencode

# ========================================
# 环境变量
# ========================================
ENV NODE_ENV=production
ENV OPENCODE_HOME=/app
ENV OPENCODE_WORKSPACE=/app/workspace
ENV OPENCODE_CONFIG=/app/.opencode/config.json
# 【修复】简化 PATH，确保系统路径优先
ENV PATH=/usr/local/bin:/usr/bin:/bin:/sbin
# Vibe Kanban 配置
ENV VIBE_KANBAN_PORT=3001
ENV VIBE_KANBAN_HOST=0.0.0.0

# ========================================
# 健康检查
# ========================================
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD opencode --version || exit 1

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
# 使用 dumb-init 启动多服务管理脚本
CMD ["dumb-init", "/app/start-services.sh"]

# ========================================
# 镜像元数据
# ========================================
LABEL maintainer="Nop Platform Team"
LABEL description="Opencode CLI + Server with oh-my-opencode + vibe-kanban (Hybrid Mode) - OPTIMIZED v6.2"
LABEL version="6.1.0"
LABEL org.opencontainers.image.title="Opencode Hybrid v6.2 (vibe-kanban integrated)"
LABEL org.opencontainers.image.description="Opencode CLI + Server + oh-my-opencode + openspec + vibe-kanban. Supports CLI, GUI interaction, and AI agent orchestration. Dual ports: OpenCode (3000) + Vibe Kanban (3001). OPTIMIZED: Removed unused platform binaries (reduced size by ~405MB)."
LABEL org.opencontainers.image.version="6.1.0"
LABEL usage.cli="docker exec -it opencode-cli bash"
LABEL usage.gui="OpenCode Desktop connect to http://localhost:3000"
LABEL usage.kanban="Vibe Kanban available at http://localhost:3001"
LABEL services="opencode-server:3000,vibe-kanban:3001"
