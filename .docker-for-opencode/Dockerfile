# ========================================
# Opencode CLI + Server 混合模式镜像（修复版）
# ========================================
# 修复内容：
# 1. 直接复制 opencode 二进制文件，避免符号链接问题
# 2. 简化 PATH 配置，确保工具可访问
# 3. 移除 docker-entrypoint.sh，直接使用 dumb-init
# 4. 改进容器启动方式，使用更稳定的保持运行方式
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

# 安装 OpenCode CLI
RUN npm i -g opencode-ai@latest

# 【修复】直接复制 opencode 二进制文件到 /usr/local/bin
# 避免符号链接链路问题
# 注意：Alpine 使用 musl libc，使用 opencode-linux-x64-musl 版本
RUN cp /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64-musl/bin/opencode /usr/local/bin/opencode && \
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
# 阶段6：设置权限和用户
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

# ========================================
# 健康检查
# ========================================
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD opencode --version || exit 1

# ========================================
# 暴露端口
# ========================================
EXPOSE 3000

# ========================================
# 挂载点
# ========================================
VOLUME ["/app/workspace"]

# ========================================
# 启动命令（启动 OpenCode Server）
# ========================================
# 【修复】使用 dumb-init 启动 OpenCode Server
CMD ["dumb-init", "opencode", "server", "--port", "3000", "--hostname", "0.0.0.0"]

# ========================================
# 镜像元数据
# ========================================
LABEL maintainer="Nop Platform Team"
LABEL description="Opencode CLI + Server with oh-my-opencode (Hybrid Mode) - FIXED"
LABEL version="6.0.1"
LABEL org.opencontainers.image.title="Opencode Hybrid v6.1 (Fixed)"
LABEL org.opencontainers.image.description="Opencode CLI + Server with oh-my-opencode and openspec pre-installed. Supports both command-line and GUI interaction. FIXED: Direct binary copy to avoid symlink issues."
LABEL org.opencontainers.image.version="6.0.1"
LABEL usage.cli="docker exec -it opencode-cli bash"
LABEL usage.gui="OpenCode Desktop connect to http://localhost:3000"
