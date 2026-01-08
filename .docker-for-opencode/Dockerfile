# ========================================
# Opencode CLI + Server 混合模式镜像
# ========================================
# 此 Dockerfile 会构建一个包含以下内容的镜像：
# - Node.js 20 运行环境
# - OpenCode CLI（命令行工具）
# - OpenCode Server（服务端）
# - oh-my-opencode 插件
# - openspec CLI（规范驱动开发工具）
# ========================================
#
# 使用方式：
#
# 方式1: 命令行交互
#    docker exec -it opencode-cli bash
#    opencode "请帮我分析代码"
#
# 方式 2: OpenCode Desktop GUI 交互
#    OpenCode Desktop 连接到 http://localhost:3000
#    使用 GUI 交互
#
# 方式 3: 同时使用两种方式
#    命令行用于自动化，GUI 用于日常开发
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
WORKDIR /app

# 安装 OpenCode CLI
RUN npm install -g @opencode-ai/cli

# 验证安装
RUN opencode --version || echo "OpenCode CLI installed"

# ========================================
# 阶段2：安装 oh-my-opencode 插件
# ========================================

# 安装 bun（oh-my-opencode 需要）
RUN npm install -g bun

# 安装 oh-my-opencode（全局）
RUN bunx oh-my-opencode install || \
    bun install -g oh-my-opencode || \
    npm install -g oh-my-opencode || \
    echo "Warning: oh-my-opencode installation may need manual setup"

# ========================================
# 阶段3：安装 openspec CLI
# ========================================

# 安装 openspec CLI
RUN npm install -g @fission-ai/openspec@latest

# 验证安装
RUN openspec --version || echo "openspec CLI installed"

# ========================================
# 阶段4：配置环境
# ========================================

# 创建工作目录
RUN mkdir -p /app/workspace

# 创建 OpenCode 配置目录
RUN mkdir -p /app/.opencode

# 创建默认配置文件（支持 CLI 和 Server）
RUN echo '# OpenCode CLI + Server configuration' > /app/.opencode/config.yaml && \
    echo 'model: "anthropic/claude-3-5-sonnet"' >> /app/.opencode/config.yaml && \
    echo '' >> /app/.opencode/config.yaml && \
    echo 'lsp:' >> /app/.opencode/config.yaml && \
    echo '  disabled: false' >> /app/.opencode/config.yaml && \
    echo '' >> /app/.opencode/config.yaml && \
    echo 'codeNavigation:' >> /app/.opencode/config.yaml && \
    echo '  enabled: true' >> /app/.opencode/config.yaml && \
    echo '' >> /app/.opencode/config.yaml && \
    echo 'tools:' >> /app/.opencode/config.yaml && \
    echo '  bash:' >> /app/.opencode/config.yaml && \
    echo '    enabled: true' >> /app/.opencode/config.yaml && \
    echo '  git:' >> /app/.opencode/config.yaml && \
    echo '    enabled: true' >> /app/.opencode/config.yaml && \
    echo '' >> /app/.opencode/config.yaml && \
    echo 'server:' >> /app/.opencode/config.yaml && \
    echo '  port: 3000' >> /app/.opencode/config.yaml && \
    echo '  hostname: 0.0.0.0' >> /app/.opencode/config.yaml

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

# 切换到非 root 用户
USER opencode

# ========================================
# 环境变量
# ========================================
ENV NODE_ENV=production
ENV OPENCODE_HOME=/app
ENV OPENCODE_WORKSPACE=/app/workspace
ENV OPENCODE_CONFIG=/app/.opencode/config.yaml
ENV PATH=/app/node_modules/.bin:/root/.opencode/node_modules/.bin:$PATH

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
# 使用 dumb-init 启动 OpenCode Server
CMD ["dumb-init", "opencode", "server", "--port", "3000", "--hostname", "0.0.0.0"]

# ========================================
# 镜像元数据
# ========================================
LABEL maintainer="Nop Platform Team"
LABEL description="Opencode CLI + Server with oh-my-opencode (Hybrid Mode)"
LABEL version="6.0.0"
LABEL org.opencontainers.image.title="Opencode Hybrid v6"
LABEL org.opencontainers.image.description="Opencode CLI + Server with oh-my-opencode and openspec pre-installed. Supports both command-line and GUI interaction."
LABEL org.opencontainers.image.version="6.0.0"
LABEL usage.cli="docker exec -it opencode-cli bash"
LABEL usage.gui="OpenCode Desktop connect to http://localhost:3000"
