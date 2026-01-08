# Opencode 混合模式使用指南

## 📖 概述

本指南介绍 Opencode AI 混合模式的详细使用方法。

混合模式结合了 **命令行交互（CLI）**和 **GUI 交互（OpenCode Desktop）**的优势。

### 架构

```
Windows 机器（Docker Desktop）:
  ┌─────────────────────────────────────┐
  │         Docker 容器                │
  │                                      │
  │  OpenCode CLI  ┌──┐         │
  │  + oh-my-opencode │   │         │
  │  + openspec        │   │         │
  │  └────────────────┘   │         │
  │          │   │         │
  ├──────────┼─────────┼────────┤┤
  │          ▼   ▼         │         │
  │     shell 命令执行    │         │
  │     文件访问       │         │
  │          │            │         │
  │     OpenCode Server   │         │
  │     (端口 3000）      │         │
  └──────────┼────────────┘         │
             │
             │ http://localhost:3000
             │
    ┌────────▼────────────┐
    │ OpenCode Desktop  │
    │   (GUI 交互）       │
    └────────────────────┘

用户交互:
  - CLI: docker exec -it opencode-cli bash
  - GUI: OpenCode Desktop 连接到 http://localhost:3000
```

### 核心特点

- ✅ **双重交互方式**: CLI 和 GUI 都支持
- ✅ **同时使用**: 可以同时使用两种方式，互不冲突
- ✅ **完整环境**: 所有组件在容器内（CLI + Server + oh-my-opencode）
- ✅ **隔离安全**: 容器隔离，不影响主机
- ✅ **统一环境**: Linux 环境，避免跨平台问题

---

## 🚀 快速开始

### 步骤 1: 构建 Docker 镜像

**Windows:**
```cmd
cd nop-entropy\.docker-for-opencode
build.bat
```

**Linux/Mac:**
```bash
cd nop-entropy/.docker-for-opencode
chmod +x build.sh
./build.sh
```

### 步骤 2: 启动容器

```bash
docker-compose up -d
```

### 步骤 3: 使用 CLI

```bash
# 进入容器
docker exec -it opencode-cli bash

# 使用 OpenCode CLI
opencode "请帮我分析代码"

# 或启动交互式会话
opencode
```

### 步骤 4: 使用 GUI（OpenCode Desktop）

#### 4.1 配置 OpenCode Desktop

编辑 `C:\Users\<用户名>\.opencode\config.yaml`：

```yaml
# OpenCode 全局配置
server:
  url: http://localhost:3000
  enabled: true
```

#### 4.2 重启 OpenCode Desktop

关闭并重新打开 OpenCode Desktop。

#### 4.3 测试连接

在 OpenCode Desktop 中尝试使用 AI 功能。

---

## 📋 使用场景

### 场景 1: 日常开发（使用 GUI）

**使用方式**: OpenCode Desktop GUI

**优点**：
- 完整的 GUI 体验
- 代码导航和补全
- 实时反馈和预览

**示例**：
```
在 OpenCode Desktop 中：
1. 打开项目文件
2. 使用 AI 辅助：右键菜单 → "让 AI 分析"
3. 查看代码提示和建议
4. 使用 Git 集成：提交代码
```

### 场景 2: 自动化任务（使用 CLI）

**使用方式**: `docker exec` 命令行

**优点**：
- 可以脚本化
- 批量处理
- CI/CD 集成

**示例**：
```bash
# 批量分析文件
docker exec opencode-cli opencode "分析 pom.xml"
docker exec opencode-cli opencode "分析 UserService.java"

# 自动化构建
docker exec opencode-cli bash -c "cd /app/workspace && mvn clean install"

# 批量测试
docker exec opencode-cli bash -c "cd /app/workspace && mvn test"
```

### 场景 3: 混合使用（推荐同时使用两种方式）

**使用方式**: 同时使用 CLI 和 GUI

**优点**：
- GUI 用于日常开发
- CLI 用于自动化任务
- 充分发挥两种方式的优势

**示例**：
```
1. 日常开发：使用 OpenCode Desktop GUI
   - 代码编辑
   - 代码导航
   - 实时 AI 辅助

2. 自动化：使用 CLI 脚本
   - 每天凌晨 2 点：代码分析
   - 每次 git push：运行测试
   - 每周：生成代码报告

3. 紧急修复：使用 CLI 快速定位问题
   - docker exec opencode-cli opencode "分析最近的失败测试"
   - docker exec opencode-cli bash -c "mvn test -Dtest=UserServiceTest"
```

---

## 💻 交互方式详解

### 方式 1: CLI - 进入容器（复杂任务）

```bash
# 进入容器
docker exec -it opencode-cli bash

# 在容器内工作
cd /app/workspace
ls -la

# 使用 OpenCode CLI
opencode "分析这个项目"

# 执行命令
mvn clean install

# 查看结果
ls -la target/

# 退出容器
exit
```

**适用场景**：
- 需要执行多个命令
- 需要在容器内长时间工作
- 需要手动干预

### 方式 2: CLI - 直接执行（简单任务）

```bash
# 直接执行命令，不进入容器
docker exec opencode-cli bash -c "cd /app/workspace && mvn clean install"

# 使用 OpenCode CLI
docker exec opencode-cli opencode "分析代码"

# 查看文件
docker exec opencode-cli ls -la /app/workspace
```

**适用场景**：
- 执行单个命令
- 脚本化操作
- 自动化任务

### 方式 3: CLI - 持续会话（多轮对话）

```bash
# 启动 OpenCode CLI 交互式会话
docker exec -it opencode-cli opencode

# 在会话中对话
> 请帮我分析 pom.xml
> [AI 分析结果]
> 哪些依赖可以升级？
> [AI 建议]
> 帮我升级 spring-boot 依赖
> [AI 修改 pom.xml]
> 运行构建
> [执行构建]
> 退出
```

**适用场景**：
- 需要多轮对话
- 需要上下文保持
- 复杂任务拆解

### 方式 4: GUI - OpenCode Desktop（日常开发）

```bash
# 1. 确保容器在运行
docker ps | findstr opencode-cli

# 2. 打开 OpenCode Desktop

# 3. 配置连接（如果未配置）
# 编辑 ~/.opencode/config.yaml
# 添加：
# server:
#   url: http://localhost:3000

# 4. 在 OpenCode Desktop 中工作
# - 打开项目文件
# - 使用 AI 功能
# - 代码导航和补全
```

**适用场景**：
- 日常代码编辑
- 代码导航和补全
- 需要 GUI 反馈

---

## 🔧 配置说明

### Server 配置（GUI 使用）

#### 1. OpenCode Desktop 配置

编辑 `C:\Users\<用户名>\.opencode\config.yaml`：

```yaml
# OpenCode 全局配置
server:
  url: http://localhost:3000
  enabled: true

# 可选：LSP 配置
lsp:
  disabled: false
```

#### 2. 容器内 Server 配置

编辑容器内 `/app/.opencode/config.yaml`：

```bash
# 进入容器
docker exec -it opencode-cli bash

# 编辑配置
vi /app/.opencode/config.yaml

# 修改 Server 配置
# server:
#   port: 3000
#   hostname: 0.0.0.0  # 监听所有接口（允许外部连接）

# 退出容器
exit

# 重启容器
docker-compose restart
```

### CLI 配置（命令行使用）

CLI 配置与容器内 Server 配置共享（同一个 config.yaml）。

### API Key 配置

**方法 1: 在 docker-compose.yml 中设置**（推荐）

```yaml
environment:
  - ANTHROPIC_API_KEY=sk-ant-xxx  # 直接设置
  # 或使用环境变量
  # - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
```

重新启动容器：
```bash
docker-compose up -d
```

**方法 2: 在容器内临时设置**

```bash
# 进入容器
docker exec -it opencode-cli bash

# 设置 API Key
export ANTHROPIC_API_KEY=sk-ant-xxx

# 使用 OpenCode
opencode "你好"
```

---

## 📂 文件操作

### 从容器内访问项目文件

```bash
# 进入容器
docker exec -it opencode-cli bash

# 工作目录
cd /app/workspace
pwd  # 输出: /app/workspace

# 查看文件
ls -la

# 编辑文件
vi pom.xml

# 创建文件
touch newfile.txt
echo "Hello" > newfile.txt
```

### 从容器外访问项目文件

容器内的 `/app/workspace` 对应宿主机的 `/c/can/nop`（Windows）：

```bash
# 在 Windows 上
# C:\can\nop\  ← 对应容器内的 /app/workspace/

# 在容器内修改的文件会立即反映到 Windows 上
```

### 从容器向外部复制文件

```bash
# 从容器复制文件到 Windows
docker cp opencode-cli:/app/workspace/target/app.jar C:\Users\YourName\Downloads\
```

### 从外部向容器复制文件

```bash
# 从 Windows 复制文件到容器
docker cp C:\Users\YourName\Downloads\file.txt opencode-cli:/app/workspace/
```

---

## 🎯 高级用法

### 脚本化操作

创建脚本 `run-tasks.bat`：

```bash
@echo off
echo 开始自动化任务...

docker exec opencode-cli opencode "分析 pom.xml"
docker exec opencode-cli bash -c "cd /app/workspace && mvn clean install -DskipTests"
docker exec opencode-cli bash -c "cd /app/workspace && mvn test"

echo 任务完成！
```

执行：
```bash
run-tasks.bat
```

### 多容器协作

如果有多个容器，可以让它们协作：

```bash
# 容器 1: opencode-cli（AI 助手）
# 容器 2: mysql-dev（数据库）

# 在容器内使用数据库连接
docker exec opencode-cli opencode "连接到 mysql-dev 容器并查询用户数据"

# OpenCode 可以通过 docker exec 访问其他容器
```

### 定时任务

使用 cron 或 Windows 计划任务：

```bash
# 每天凌晨 2 点运行代码分析
0 2 * * * docker exec opencode-cli opencode "分析项目并生成报告"
```

---

## 🐛 故障排查

### 问题 1: OpenCode Desktop 无法连接到 Server

**症状**：
```
OpenCode Desktop 显示连接失败
```

**诊断步骤**：

```bash
# 1. 检查容器是否运行
docker ps | findstr opencode-cli

# 2. 检查端口是否暴露
docker port opencode-cli 3000

# 3. 测试端口是否可访问
curl http://localhost:3000
```

**解决方案**：

**原因 1**: 容器未运行
```bash
# 启动容器
docker-compose up -d
```

**原因 2**: 端口被占用
```bash
# 检查端口占用
netstat -ano | findstr ":3000"

# 修改 docker-compose.yml 使用其他端口
# ports:
#   - "3001:3000"
```

**原因 3**: 防火墙阻止
```bash
# Windows 防火墙允许端口 3000
# 控制面板 → Windows Defender 防火墙 → 高级设置
# 添加入站规则：端口 3000
```

### 问题 2: CLI 无法连接到 Server

**症状**：
```
docker exec opencode-cli opencode
# Error: Cannot connect to server
```

**诊断步骤**：

```bash
# 1. 检查 Server 是否运行
docker exec opencode-cli ps aux | grep opencode

# 2. 查看 Server 日志
docker logs opencode-cli | grep server

# 3. 检查配置文件
docker exec opencode-cli cat /app/.opencode/config.yaml
```

**解决方案**：

**原因 1**: Server 未启动
```bash
# 检查 docker-compose.yml 中的启动命令
# 应该同时启动 Server 和保持容器运行
command: ["sh", "-c", "opencode server --port 3000 --hostname 0.0.0.0 & sleep infinity"]
```

**原因 2**: 配置错误
```bash
# 编辑配置文件
docker exec -it opencode-cli vi /app/.opencode/config.yaml

# 确保正确
# server:
#   port: 3000
#   hostname: 0.0.0.0
```

### 问题 3: 两种方式无法同时使用

**症状**：
```
使用 OpenCode Desktop 时，CLI 无法使用
或使用 CLI 时，OpenCode Desktop 无法连接
```

**解决方案**：

混合模式支持同时使用，应该不存在这个问题。如果出现：

```bash
# 检查容器资源使用
docker stats opencode-cli

# 检查容器日志
docker logs -f opencode-cli

# 查看是否有连接限制
# 默认应该没有连接数限制
```

---

## 📚 相关文档

- [SUMMARY.md](./SUMMARY.md) - 部署总结和快速开始
- [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) - 故障排查指南
- [README.md](./README.md) - Docker 配置总览

---

## 💡 最佳实践

1. **使用 GUI 进行日常开发**: OpenCode Desktop 提供完整的 GUI 功能
2. **使用 CLI 进行自动化任务**: `docker exec` 命令
3. **混合使用**: 同时打开 GUI 和 CLI，最大化效率
4. **设置 API Key**: 在 docker-compose.yml 中配置
5. **定期查看日志**: 遇到问题先查看容器日志

---

**版本**: 6.0.0
**最后更新**: 2025-01-08
