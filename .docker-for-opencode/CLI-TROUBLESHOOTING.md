# Opencode CLI 故障排查指南

## 📖 概述

本指南提供 Opencode CLI 容器的常见问题和解决方案。

---

## 🚨 紧急问题

### 容器无法启动

**症状**：
```bash
docker-compose -f docker-compose.cli.yml up -d
# Error: Cannot start service opencode-cli
```

**诊断步骤**：

1. 检查 Docker 状态：
```bash
docker ps -a | findstr opencode-cli
```

2. 查看容器日志：
```bash
docker logs opencode-cli
```

3. 检查端口占用：
```bash
netstat -ano | findstr ":3000"
```

**解决方案**：

```bash
# 1. 重新构建镜像
docker-compose -f docker-compose.cli.yml up -d --build

# 2. 如果还是失败，完全清理
docker-compose -f docker-compose.cli.yml down
docker rmi opencode-cli:latest
docker-compose -f docker-compose.cli.yml up -d --build

# 3. 检查 Docker Desktop 是否运行
# 在 Windows 上，确保 Docker Desktop 已启动
```

---

## 🔧 安装问题

### 问题 1: 镜像构建失败

**症状**：
```bash
build-cli.bat
# Error: Failed to build image
```

**可能原因**：
- Docker Desktop 未运行
- 网络连接问题
- Dockerfile 语法错误
- 磁盘空间不足

**解决方案**：

```bash
# 1. 检查 Docker 状态
docker ps

# 2. 检查磁盘空间
docker system df

# 3. 清理 Docker 缓存
docker system prune -a

# 4. 不使用缓存重新构建
build-cli.bat --no-cache

# 5. 查看详细构建日志
docker build -f Dockerfile.cli -t opencode-cli:latest --progress=plain .
```

### 问题 2: OpenCode CLI 安装失败

**症状**：
```bash
# 构建日志显示：
# Error: Cannot install @opencode-ai/cli
```

**可能原因**：
- npm registry 连接失败
- 包名错误
- 网络代理问题

**解决方案**：

```bash
# 1. 使用国内镜像（中国用户）
docker build -f Dockerfile.cli -t opencode-cli:latest \
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com \
  .

# 2. 修改 Dockerfile，添加国内镜像
# 在 Dockerfile.cli 中添加：
# RUN npm config set registry https://registry.npmmirror.com

# 3. 使用代理
docker build --build-arg HTTP_PROXY=http://proxy:port \
  --build-arg HTTPS_PROXY=http://proxy:port \
  -f Dockerfile.cli -t opencode-cli:latest .
```

### 问题 3: oh-my-opencode 安装失败

**症状**：
```bash
# 构建日志显示：
# Error: Cannot install oh-my-opencode
```

**可能原因**：
- bun 未正确安装
- npm 版本不兼容
- 网络连接问题

**解决方案**：

修改 `Dockerfile.cli`，手动安装：

```dockerfile
# 阶段2：安装 oh-my-opencode 插件
RUN npm install -g bun

# 方案 A: 使用 npm
RUN npm install -g oh-my-opencode

# 方案 B: 使用 bun
RUN bun install -g oh-my-opencode

# 方案 C: 使用 npx（不安装）
# 然后在运行时使用：npx oh-my-opencode
```

重新构建：
```bash
build-cli.bat --no-cache
```

---

## 🚀 运行时问题

### 问题 1: 无法进入容器

**症状**：
```bash
docker exec -it opencode-cli bash
# Error: Unable to find image 'opencode-cli:latest' locally
```

**诊断步骤**：

```bash
# 1. 检查镜像是否存在
docker images | findstr opencode-cli

# 2. 检查容器状态
docker ps -a | findstr opencode-cli
```

**解决方案**：

```bash
# 1. 构建镜像
build-cli.bat

# 2. 启动容器
docker-compose -f docker-compose.cli.yml up -d

# 3. 等待容器启动
timeout /t 5

# 4. 再次尝试进入
docker exec -it opencode-cli bash
```

### 问题 2: 容器立即退出

**症状**：
```bash
docker-compose -f docker-compose.cli.yml up -d
docker ps
# 容器不在运行列表中

docker ps -a | findstr opencode-cli
# STATUS: Exited (1) 2 seconds ago
```

**诊断步骤**：

```bash
# 查看容器退出原因
docker logs opencode-cli

# 查看容器状态
docker inspect opencode-cli
```

**可能原因和解决方案**：

**原因 1**: 配置文件错误
```bash
# 检查 docker-compose.cli.yml 语法
# 使用在线验证器：https://www.yamllint.com/
```

**原因 2**: 卷挂载失败
```bash
# 检查路径是否存在
dir C:\can\nop

# 检查 Docker Desktop 是否有权限访问目录
# Docker Desktop → Settings → Resources → File Sharing
```

**原因 3**: 环境变量错误
```bash
# 检查 docker-compose.cli.yml 中的环境变量
# 确保没有未设置的变量引用
```

### 问题 3: OpenCode CLI 报错 "Command not found"

**症状**：
```bash
docker exec -it opencode-cli bash
opencode "你好"
# bash: opencode: command not found
```

**可能原因**：
- OpenCode CLI 未正确安装
- PATH 环境变量错误

**解决方案**：

```bash
# 1. 检查 opencode 是否安装
docker exec opencode-cli which opencode

# 2. 检查 PATH
docker exec opencode-cli echo $PATH

# 3. 如果未找到，手动安装
docker exec -it opencode-cli npm install -g @opencode-ai/cli

# 4. 重新构建镜像
build-cli.bat --no-cache
```

### 问题 4: API Key 错误

**症状**：
```bash
docker exec opencode-cli opencode "你好"
# Error: ANTHROPIC_API_KEY not set
```

**解决方案**：

**方法 1: 在 docker-compose.yml 中设置**：

```yaml
environment:
  - ANTHROPIC_API_KEY=sk-ant-xxx
  # 或使用环境变量
  # - ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}
```

重新启动容器：
```bash
docker-compose -f docker-compose.cli.yml up -d
```

**方法 2: 在容器内临时设置**：

```bash
docker exec -it opencode-cli bash
export ANTHROPIC_API_KEY=sk-ant-xxx
opencode "你好"
```

**方法 3: 在 Windows 环境变量中设置**：

```cmd
# 在 Windows PowerShell 中
$env:ANTHROPIC_API_KEY="sk-ant-xxx"

# 或在系统环境变量中设置
# 设置 → 环境变量 → 新建系统变量
```

### 问题 5: 网络连接错误

**症状**：
```bash
docker exec opencode-cli opencode "你好"
# Error: Failed to connect to Anthropic API
# Error: Network timeout
```

**诊断步骤**：

```bash
# 1. 测试容器网络连接
docker exec opencode-cli ping google.com

# 2. 测试 API 连接
docker exec opencode-cli curl -I https://api.anthropic.com
```

**解决方案**：

**方法 1: 检查 Docker 网络**
```bash
# 查看容器网络
docker network ls

# 查看容器使用的网络
docker inspect opencode-cli | findstr NetworkMode
```

**方法 2: 使用代理**
```yaml
# 在 docker-compose.cli.yml 中添加
environment:
  - HTTP_PROXY=http://proxy:port
  - HTTPS_PROXY=http://proxy:port
  - NO_PROXY=localhost,127.0.0.1
```

**方法 3: 检查防火墙**
```bash
# Windows 防火墙可能阻止容器访问外部网络
# 临时关闭防火墙测试
# 如果问题解决，添加 Docker 到防火墙允许列表
```

---

## 📂 文件系统问题

### 问题 1: 文件权限错误

**症状**：
```bash
docker exec opencode-cli ls -la /app/workspace
# ls: cannot access '/app/workspace': Permission denied
```

**解决方案**：

**方法 1: 修改 docker-compose.yml**
```yaml
services:
  opencode-cli:
    user: "0:0"  # 使用 root 用户
```

**方法 2: 修改宿主机目录权限**
```bash
# 在 Windows 上，确保 Docker Desktop 有权限访问目录
# Docker Desktop → Settings → Resources → File Sharing
# 添加: C:\can\nop
```

**方法 3: 修改容器内权限**
```bash
# 作为 root 用户进入容器
docker exec -u root -it opencode-cli bash

# 修改权限
chown -R opencode:opencode /app/workspace
chmod -R 755 /app/workspace

# 退出并重新以普通用户进入
exit
docker exec -it opencode-cli bash
```

### 问题 2: 文件不同步

**症状**：
```bash
# 在容器内修改文件
docker exec -it opencode-cli bash
echo "test" > /app/workspace/test.txt

# 在 Windows 上看不到修改
dir C:\can\nop\test.txt
# 文件不存在或内容不对
```

**诊断步骤**：

```bash
# 检查挂载配置
docker inspect opencode-cli | findstring Mounts

# 检查容器内路径
docker exec opencode-cli pwd
docker exec opencode-cli ls -la /app
```

**解决方案**：

**方法 1: 检查 docker-compose.yml 中的卷挂载**
```yaml
volumes:
  - /c/can/nop:/app/workspace:rw
  # 确保路径正确！
```

**方法 2: 重新启动容器**
```bash
docker-compose -f docker-compose.cli.yml down
docker-compose -f docker-compose.cli.yml up -d
```

**方法 3: 检查 Windows 路径格式**
```yaml
# Windows 路径格式
volumes:
  - C:\can\nop:/app/workspace:rw  # 可能不工作

# Docker 推荐格式
volumes:
  - /c/can/nop:/app/workspace:rw  # 使用 /c/ 代替 C:\
```

### 问题 3: 文件损坏或丢失

**症状**：
```bash
# 容器内文件突然消失或损坏
```

**解决方案**：

```bash
# 1. 检查 Docker 磁盘使用
docker system df

# 2. 清理 Docker
docker system prune -a

# 3. 重新创建容器
docker-compose -f docker-compose.cli.yml down
docker-compose -f docker-compose.cli.yml up -d

# 4. 检查宿主机文件系统
# Windows 上运行 chkdsk
```

---

## 🎯 OpenCode CLI 问题

### 问题 1: AI 响应慢或超时

**症状**：
```bash
docker exec opencode-cli opencode "分析代码"
# 等待很长时间，无响应
```

**可能原因**：
- API 限流
- 网络延迟
- 模型选择不当

**解决方案**：

```bash
# 1. 检查 API 配额
# 登录 Anthropic 控制台查看使用情况

# 2. 使用更快的模型
# 在 opencode.yaml 中配置
model: "anthropic/claude-3-haiku-20240307"  # 更快，但能力较弱

# 3. 增加超时时间
# 在命令中指定
timeout 300 opencode "分析代码"

# 4. 使用代理（如果在中国）
# 配置 HTTP_PROXY 环境变量
```

### 问题 2: AI 拒绝执行某些命令

**症状**：
```bash
docker exec opencode-cli opencode "删除所有文件"
# AI: 我不能执行这个命令，因为它会删除重要文件
```

**解决方案**：

```bash
# 1. 更明确地说明
docker exec opencode-cli opencode "删除 /app/workspace/target 目录"

# 2. 提供上下文
docker exec opencode-cli opencode "删除 target 目录，这是构建输出目录"

# 3. 确认操作
docker exec opencode-cli opencode "删除 target 目录，确认操作"
```

### 问题 3: 上下文丢失

**症状**：
```bash
docker exec -it opencode-cli bash
opencode "分析 UserService"
> [AI 分析结果]
exit

docker exec -it opencode-cli bash
opencode "这个类有什么问题？"
> AI: 我不知道你说的"这个类"是什么
```

**解决方案**：

```bash
# 1. 使用交互式会话（保持上下文）
docker exec -it opencode-cli opencode
> 分析 UserService
> [AI 分析结果]
> 这个类有什么问题？
> [AI 知道你在说 UserService]

# 2. 重新提供上下文
opencode "UserService.java 这个类有什么问题？"

# 3. 使用 openspec 记录上下文
openspec create "分析 UserService"
```

---

## 🔍 调试技巧

### 1. 查看容器日志

```bash
# 实时查看日志
docker logs -f opencode-cli

# 查看最近 100 行
docker logs --tail=100 opencode-cli

# 查看带时间戳的日志
docker logs -t opencode-cli
```

### 2. 进入容器调试

```bash
# 以 root 用户进入（可以执行更多操作）
docker exec -u root -it opencode-cli bash

# 查看进程
ps aux

# 查看环境变量
env | sort

# 查看网络
ping google.com
```

### 3. 检查容器配置

```bash
# 查看容器详细信息
docker inspect opencode-cli

# 查看容器网络
docker network inspect bridge

# 查看挂载卷
docker volume ls
```

### 4. 使用 verbose 模式

```bash
# OpenCode CLI verbose 模式
opencode --verbose "分析代码"

# 查看详细日志
OPENCODE_LOG_LEVEL=debug opencode "分析代码"
```

---

## 📞 获取帮助

### 1. 查看文档

- [CLI-USAGE.md](./CLI-USAGE.md) - CLI 使用指南
- [README.md](./README.md) - Docker 配置总览
- [ARCHITECTURE_COMPARISON.md](./ARCHITECTURE_COMPARISON.md) - 架构对比

### 2. 查看日志

```bash
# 容器日志
docker logs opencode-cli

# Docker Compose 日志
docker-compose -f docker-compose.cli.yml logs -f
```

### 3. 测试网络连接

```bash
# 测试 Anthropic API
docker exec opencode-cli curl -I https://api.anthropic.com

# 测试 DNS 解析
docker exec opencode-cli nslookup api.anthropic.com
```

### 4. 重置容器

如果所有方法都失败，完全重置：

```bash
# 停止并删除容器
docker-compose -f docker-compose.cli.yml down

# 删除镜像
docker rmi opencode-cli:latest

# 清理 Docker
docker system prune -a

# 重新构建
build-cli.bat --no-cache

# 启动容器
docker-compose -f docker-compose.cli.yml up -d
```

---

## 📊 常见错误码

| 错误码 | 含义 | 解决方案 |
|--------|------|----------|
| `127` | 命令未找到 | 检查命令是否安装 |
| `1` | 一般错误 | 查看日志获取详情 |
| `126` | 权限被拒绝 | 检查文件权限 |
| `137` | 容器被杀死 | 检查内存使用 |
| `Exit 1` | 容器启动失败 | 查看容器日志 |

---

**版本**: 4.0.0
**最后更新**: 2025-01-08
