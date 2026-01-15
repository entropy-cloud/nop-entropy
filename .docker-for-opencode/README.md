# Opencode Docker 开发工具集

本目录包含 **Opencode AI 混合模式**的 Docker 部署配置，支持 CLI 和 GUI 两种交互方式。

## 快速开始

```bash
# 构建镜像
cd nop-entropy/.docker-for-opencode
./build.sh   # Linux/Mac
build.bat    # Windows

# 启动容器
docker-compose up -d

# 使用 CLI
docker exec -it opencode-cli bash

# 使用 GUI
# 配置 OpenCode Desktop 连接 http://localhost:3000
```

## 包含组件

- **OpenCode CLI + Server**: 命令行和服务器模式
- **oh-my-opencode**: OpenCode 插件（Oracle、Librarian 等智能体）
- **openspec**: 规范驱动开发工具

## 端口

- `3000`: OpenCode Server（供 OpenCode Desktop 连接）

## 常用命令

```bash
# 容器管理
docker-compose up -d      # 启动
docker-compose stop       # 停止
docker-compose restart    # 重启
docker-compose logs -f    # 查看日志

# CLI 使用
docker exec -it opencode-cli opencode "分析代码"
docker exec -it opencode-cli bash
```

## 文件说明

- **Dockerfile**: 镜像构建文件
- **docker-compose.yml**: 容器编排配置
- **build.sh/build.bat**: 构建脚本
- **verify.sh/verify.bat**: 验证脚本
