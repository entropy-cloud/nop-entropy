# ========================================
# Opencode CLI + Server 混合模式镜像（简化版）
# ========================================
# 功能：
# 1. OpenCode CLI + Server + oh-my-opencode
# 2. openspec CLI
# ========================================

FROM node:20-alpine3.20

# 设置工作目录
WORKDIR /app

# 创建非 root 用户
RUN addgroup -g 1001 opencode && \
    adduser -D -u 1001 -G opencode opencode

# 安装所有依赖
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
    # 清理不需要的平台二进制
    rm -rf /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64 && \
    rm -rf /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-baseline && \
    rm -rf /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-baseline-musl && \
    cp /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-musl/bin/opencode /usr/local/bin/opencode && \
    chmod +x /usr/local/bin/opencode && \
    \
    # 安装 oh-my-opencode
    npx oh-my-opencode install || \
    echo "Warning: oh-my-opencode installation may need manual setup" && \
    \
    # 安装 openspec CLI
    npm install -g @fission-ai/openspec@latest && \
    chmod +x /usr/local/bin/openspec && \
    \
    # 清理缓存
    npm cache clean --force && \
    rm -rf /root/.npm/_cacache && \
    rm -rf /var/cache/apk/* && \
    \
    # 创建必要目录
    mkdir -p /app/workspace && \
    mkdir -p /root/.local/share && \
    \
    # 设置所有权
    chown -R opencode:opencode /app

# 创建启动脚本
RUN echo '#!/bin/sh' > /app/start-services.sh && \
    echo 'set -x' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo 'cleanup() {' >> /app/start-services.sh && \
    echo '    echo "Shutting down services..."' >> /app/start-services.sh && \
    echo '    pkill -f "opencode serve" 2>/dev/null || true' >> /app/start-services.sh && \
    echo '    exit 0' >> /app/start-services.sh && \
    echo '}' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo 'trap cleanup SIGTERM SIGINT' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 启动 OpenCode Server' >> /app/start-services.sh && \
    echo 'echo "Starting OpenCode Server on port 3000..."' >> /app/start-services.sh && \
    echo 'opencode serve --port 3000 --hostname 0.0.0.0 --mdns &' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo 'sleep 5' >> /app/start-services.sh && \
    echo '' >> /app/start-services.sh && \
    echo '# 打印服务状态' >> /app/start-services.sh && \
    echo 'echo "OpenCode Server is running on port 3000"' >> /app/start-services.sh && \
    echo 'wait' >> /app/start-services.sh && \
    chmod +x /app/start-services.sh

# 环境变量
ENV NODE_ENV=production
ENV OPENCODE_HOME=/app
ENV OPENCODE_WORKSPACE=/app/workspace
ENV OPENCODE_CONFIG=/app/.opencode/config.json
ENV PATH=/usr/local/bin:/usr/bin:/bin:/sbin

# 暴露端口
EXPOSE 3000

# 挂载点
VOLUME ["/app/workspace"]

# 启动命令
CMD ["dumb-init", "/app/start-services.sh"]

# 镜像元数据
LABEL maintainer="Nop Platform Team"
LABEL description="Opencode CLI + Server with oh-my-opencode + openspec"
LABEL version="7.0.0"
LABEL org.opencontainers.image.title="Opencode Hybrid v7.0"
LABEL org.opencontainers.image.description="Opencode CLI + Server + oh-my-opencode + openspec"
LABEL org.opencontainers.image.version="7.0.0"
LABEL usage.cli="docker exec -it opencode-cli bash"
LABEL usage.gui="OpenCode Desktop connect to http://localhost:3000"
LABEL services="opencode-server:3000"
LABEL build.type="single-layer"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD opencode --version || exit 1
