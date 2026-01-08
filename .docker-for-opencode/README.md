# Opencode Docker 开发工具集

本目录包含 **Opencode AI 混合模式**的 Docker 部署配置。

---

## 📦 混合模式概述

混合模式结合了 **命令行交互（CLI）**和 **GUI 交互（OpenCode Desktop）**的优势：

- ✅ **双重交互方式**: CLI 和 GUI 都支持
- ✅ **同时使用**: 可以同时使用两种方式，互不冲突
- ✅ **完整环境**: 所有组件在容器内（CLI + Server + oh-my-opencode）
- ✅ **隔离安全**: 容器隔离，不影响主机
- ✅ **统一环境**: Linux 环境，避免跨平台问题

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

### 步骤 3: 使用 CLI（命令行交互）

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

编辑 `C:\Users\<用户名>\.opencode\config.yaml`（如果不存在则创建）：

```yaml
# OpenCode 全局配置
server:
  url: http://localhost:3000
  enabled: true
```

#### 4.2 重启 OpenCode Desktop

关闭并重新打开 OpenCode Desktop，使配置生效。

#### 4.3 测试连接

在 OpenCode Desktop 中尝试使用 AI 功能，应该能够访问容器内的代码和工具。

---

## 📋 文件说明

### 核心配置文件

- **Dockerfile**: 混合模式 Docker 镜像构建文件
  - 包含 OpenCode CLI + Server + oh-my-opencode + openspec
  - 基于 Node.js 20 Alpine
  - 同时启动 Server 和保持容器运行（支持 docker exec）

- **docker-compose.yml**: 混合模式 Docker Compose 配置
  - 暴露端口 3000（允许外部连接）
  - 挂载项目文件到 `/app/workspace`
  - 支持环境变量配置（API Key）

- **build.bat**: Windows 构建脚本
  - 自动构建镜像
  - 支持选项：`--no-cache`, `--push`
  - 自动验证和测试

### 配置文件

- **opencode.json**: OpenCode 全局配置文件
- **openspec.config.json**: openspec 配置文件
- **.dockerignore**: Docker 构建时忽略的文件和目录

### 文档

- **README.md**: 本文档（完整使用说明）
- **USAGE.md**: 详细使用指南 ⭐ **首先阅读**
- **SUMMARY.md**: 部署总结和快速开始
- **TROUBLESHOOTING.md**: 故障排查指南

---

## 📦 镜像包含内容

### 基础环境

- ✅ Node.js 20 Alpine 基础环境
- ✅ 系统工具：curl、git、bash、openssh-client
- ✅ 证书：CA 证书库

### OpenCode 组件

- ✅ **OpenCode CLI**: 正确安装的命令行工具
- ✅ **OpenCode Server**: 使用 `opencode server` 命令启动
- ✅ **oh-my-opencode**: 完整安装的 OpenCode 插件（包含 Oracle、Librarian 等智能体）
- ✅ **配置管理**: 统一的 opencode.yaml 配置

### openspec（规范驱动开发工具）

- ✅ **openspec CLI**: `@fission-ai/openspec@latest`
- ✅ **初始化**: `openspec init --yes --no-prompt`
- ✅ **目录结构**: 创建 `openspec/specs/` 和 `openspec/changes/`
- ✅ **配置文件**: openspec.config.json

---

## 🎯 使用场景

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

### 场景 3: 混合使用（同时使用 CLI 和 GUI）⭐ 推荐

**使用方式**: 同时使用 GUI 和 CLI

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

## 🔧 配置说明

### API Key 配置

**方法 1: 在 docker-compose.yml 中设置**（推荐）

```yaml
environment:
  - ANTHROPIC_API_KEY=sk-ant-xxx  # 直接设置（不推荐）
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

### OpenCode Desktop 配置

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

重启 OpenCode Desktop 使配置生效。

---

## 📚 常用命令

### 容器管理

```bash
# 启动容器
docker-compose up -d

# 停止容器
docker-compose stop

# 重启容器
docker-compose restart

# 删除容器
docker-compose down

# 查看实时日志
docker-compose logs -f

# 查看最近 100 行
docker-compose logs --tail=100 opencode-cli
```

### CLI 交互方式

```bash
# 方式 1: 进入容器（推荐用于复杂任务）
docker exec -it opencode-cli bash
opencode "分析代码"
exit

# 方式 2: 直接执行（推荐用于简单任务）
docker exec opencode-cli opencode "分析 pom.xml"

# 方式 3: 启动持续会话（推荐用于多轮对话）
docker exec -it opencode-cli opencode
> 分析代码
> 运行构建
> 退出
```

### 文件操作

```bash
# 从容器复制文件到 Windows
docker cp opencode-cli:/app/workspace/target/app.jar C:\Users\YourName\Downloads\

# 从 Windows 复制文件到容器
docker cp C:\Users\YourName\Downloads\file.txt opencode-cli:/app/workspace/
```

---

## 🐛 故障排查

### 1. 容器无法启动

**检查 Docker Server 是否运行**：
```bash
docker-compose ps
docker-compose logs opencode-cli
```

**测试服务是否可访问**：
```bash
curl http://localhost:3000
```

### 2. OpenCode Desktop 无法连接

**检查容器是否运行**：
```bash
docker ps | findstr opencode-cli
```

**检查端口是否暴露**：
```bash
docker port opencode-cli 3000
```

### 3. CLI 无法执行命令

**检查容器是否运行**：
```bash
docker ps | findstr opencode-cli
```

**测试命令执行**：
```bash
docker exec opencode-cli pwd
```

---

## 📚 文档阅读顺序

### 新用户推荐

1. **SUMMARY.md** ⭐ 首先阅读
   - 部署总结和快速开始
   - 三种模式对比

2. **USAGE.md** ⭐ 必读
   - 完整的使用指南
   - 所有交互方式和场景示例

3. **TROUBLESHOOTING.md**（遇到问题时）
   - 详细的故障排查指南
   - 常见问题和解决方案

---

## 💡 最佳实践

1. **使用 GUI 进行日常开发**: OpenCode Desktop 提供完整的 GUI 功能
2. **使用 CLI 进行自动化任务**: 脚本化和批量处理
3. **同时使用两种方式**: 最大化效率
4. **设置 API Key**: 在 docker-compose.yml 中配置
5. **定期查看日志**: 遇到问题先查看容器日志

---

## 🔗 技术架构

```
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
```

---

**目录名称**: `.docker-for-opencode`
**用途**: Opencode AI 混合模式 Docker 部署（CLI + GUI 双重交互）
**版本**: 6.0.0
**最后更新**: 2025-01-08

**关键特点**：
- ✅ CLI + Server 双模式
- ✅ oh-my-opencode 完整安装
- ✅ 同时支持命令行和 GUI 交互
- ✅ 统一 Linux 环境和容器隔离
