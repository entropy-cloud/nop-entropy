# 故障排查指南

## 📋 目录

- [容器启动问题](#容器启动问题)
- [Server 连接问题](#server-连接问题)
- [CLI 执行问题](#cli-执行问题)
- [网络问题](#网络问题)
- [权限问题](#权限问题)
- [性能问题](#性能问题)
- [日志分析](#日志分析)

## 🔧 容器启动问题

### 问题 1: 容器无法启动

**症状:**
```
docker-compose up -d
# 容器启动后立即退出
```

**排查步骤:**

1. 查看容器状态
```bash
docker ps -a | findstr opencode-cli
```

2. 查看容器日志
```bash
docker logs opencode-cli
```

**常见原因和解决方案:**

| 原因 | 解决方案 |
|------|----------|
| 端口被占用 | 修改 `docker-compose.yml` 中的端口映射 |
| 权限不足 | 检查文件权限，确保挂载目录可读写 |
| 配置错误 | 检查 `docker-compose.yml` 配置 |
| 镜像损坏 | 重新构建镜像: `docker build -f Dockerfile -t opencode-hybrid:latest .` |

### 问题 2: 容器重启循环

**症状:**
```
docker ps
# 状态显示: Restarting (xxx) X seconds ago
```

**排查步骤:**

1. 查看最近的日志
```bash
docker logs --tail 50 opencode-cli
```

2. 检查健康检查
```bash
docker inspect opencode-cli | grep -A 10 Health
```

**常见原因和解决方案:**

| 原因 | 解决方案 |
|------|----------|
| 命令执行失败 | 检查 entrypoint 和 command 配置 |
| 权限被拒绝 | 确保文件对所有用户可执行 |
| 配置文件错误 | 检查 `/app/.opencode/config.json` 格式 |
| 内存不足 | 检查 Docker 内存限制 |

## 🌐 Server 连接问题

### 问题 1: 无法访问 Server

**症状:**
```bash
curl http://localhost:3000
# curl: (7) Failed to connect to localhost port 3000
```

**排查步骤:**

1. 检查容器是否运行
```bash
docker ps | findstr opencode-cli
```

2. 检查端口映射
```bash
docker port opencode-cli 3000
```

3. 检查容器内部 Server
```bash
docker exec opencode-cli netstat -tuln | grep 3000
# 或
docker exec opencode-cli apk add netcat-openbsd
docker exec opencode-cli nc -zv 0.0.0.0 3000
```

**解决方案:**

| 问题 | 解决方案 |
|------|----------|
| 容器未运行 | 启动容器: `docker-compose up -d` |
| 端口未映射 | 检查 `docker-compose.yml` 端口配置 |
| Server 未启动 | 查看 Server 日志: `docker logs opencode-cli` |
| 防火墙阻止 | 检查防火墙设置 |

### 问题 2: Server 响应错误

**症状:**
```bash
curl http://localhost:3000
# 返回错误信息或配置错误
```

**排查步骤:**

1. 查看 Server 日志
```bash
docker logs opencode-cli | tail -50
```

2. 检查配置文件
```bash
docker exec opencode-cli cat /app/.opencode/config.json
```

**解决方案:**

| 问题 | 解决方案 |
|------|----------|
| 配置文件格式错误 | 使用 JSONC 格式，不要使用 YAML |
| 模型配置错误 | 检查 model 字段是否正确 |
| API Key 缺失 | 在 `docker-compose.yml` 中添加 API Key |
| 工作目录问题 | 检查挂载目录是否正确 |

## 💻 CLI 执行问题

### 问题 1: opencode 命令找不到

**症状:**
```bash
docker exec opencode-cli opencode --version
# sh: opencode: not found
```

**排查步骤:**

1. 检查二进制文件
```bash
docker exec opencode-cli ls -la /usr/local/bin/opencode
```

2. 检查 PATH
```bash
docker exec opencode-cli echo $PATH
```

3. 检查文件权限
```bash
docker exec opencode-cli ls -l /usr/local/bin/opencode
```

**解决方案:**

| 问题 | 解决方案 |
|------|----------|
| 二进制文件不存在 | 重新构建镜像 |
| PATH 配置错误 | 确保 `/usr/local/bin` 在 PATH 中 |
| 权限不足 | 重新构建镜像，确保 `chmod 755` |
| 符号链接失效 | 使用修复版 Dockerfile（直接复制二进制） |

### 问题 2: opencode 执行权限被拒绝

**症状:**
```bash
docker exec opencode-cli opencode --version
# permission denied
```

**排查步骤:**

1. 检查当前用户
```bash
docker exec opencode-cli whoami
```

2. 检查文件权限
```bash
docker exec opencode-cli ls -l /usr/local/bin/opencode
```

**解决方案:**

| 问题 | 解决方案 |
|------|----------|
| 非 root 用户 | 使用修复版 Dockerfile（已配置用户权限） |
| 文件权限不足 | 重新构建镜像，确保 `chmod 755` |
| entrypoint 问题 | 确保 entrypoint 为 `["dumb-init", "--"]` |

## 🌍 网络问题

### 问题 1: 无法下载依赖

**症状:**
```
# 构建时网络错误
fetch https://dl-cdn.alpinelinux.org/... failed
```

**解决方案:**

1. 使用国内镜像源（Alpine）
```dockerfile
# 在 Dockerfile 中添加
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories
```

2. 使用 npm 国内镜像
```bash
npm config set registry https://registry.npmmirror.com
```

3. 检查网络连接
```bash
ping dl-cdn.alpinelinux.org
```

### 问题 2: 容器无法访问外部网络

**症状:**
```
# 容器内无法访问外部 API
curl https://api.anthropic.com
# timeout or failed
```

**排查步骤:**

1. 检查 Docker 网络
```bash
docker network ls
docker network inspect docker-for-opencode_default
```

2. 测试 DNS 解析
```bash
docker exec opencode-cli nslookup api.anthropic.com
```

**解决方案:**

| 问题 | 解决方案 |
|------|----------|
| DNS 问题 | 配置 Docker DNS: `--dns 8.8.8.8` |
| 网络模式 | 使用 `network_mode: host` |
| 代理问题 | 配置 HTTP/HTTPS 代理环境变量 |

## 🔐 权限问题

### 问题 1: 挂载目录权限问题

**症状:**
```
# 容器内无法写入挂载目录
docker exec opencode-cli touch /app/workspace/test.txt
# permission denied
```

**排查步骤:**

1. 检查宿主机目录权限
```bash
ls -ld /c/can/nop
```

2. 检查容器内用户
```bash
docker exec opencode-cli id
```

**解决方案:**

| 问题 | 解决方案 |
|------|----------|
| 宿主机目录权限不足 | 修改宿主机目录权限: `chmod 755 /c/can/nop` |
| UID 不匹配 | 修改 `docker-compose.yml` 添加 `user: "${UID}:${GID}"` |
| Windows 路径问题 | 使用 `/c/can/nop` 而非 `C:\can\nop` |

### 问题 2: 配置文件权限问题

**症状:**
```
# opencode 无法读取配置文件
ConfigInvalidError: Invalid input
```

**排查步骤:**

1. 检查配置文件
```bash
docker exec opencode-cli cat /app/.opencode/config.json
```

2. 检查文件权限
```bash
docker exec opencode-cli ls -l /app/.opencode/config.json
```

**解决方案:**

| 问题 | 解决方案 |
|------|----------|
| 文件格式错误 | 使用 JSONC 格式 |
| 文件权限不足 | 确保文件可读: `chmod 644` |
| 配置路径错误 | 检查 `OPENCODE_CONFIG` 环境变量 |

## ⚡ 性能问题

### 问题 1: 容器启动慢

**症状:**
```
# 容器启动需要很长时间
docker-compose up -d
# 等待 30+ 秒
```

**解决方案:**

| 优化 | 说明 |
|------|------|
| 预拉取镜像 | 使用 `docker pull` 预先拉取基础镜像 |
| 优化 Dockerfile | 合并 RUN 指令减少层数 |
| 使用构建缓存 | 不要使用 `--no-cache` |

### 问题 2: CLI 响应慢

**症状:**
```
# opencode 命令响应慢
docker exec opencode-cli opencode --version
# 等待 5+ 秒
```

**解决方案:**

| 优化 | 说明 |
|------|------|
| 增加内存 | 检查 Docker 内存限制，至少 2GB |
| 使用 SSD | 确保宿主机使用 SSD |
| 减少 IO | 避免频繁的文件操作 |

## 📊 日志分析

### 查看实时日志

```bash
# 查看所有日志
docker logs -f opencode-cli

# 查看最近 50 行
docker logs --tail 50 opencode-cli

# 查看最近 1 小时的日志
docker logs --since 1h opencode-cli
```

### 常见日志分析

| 日志信息 | 原因 | 解决方案 |
|----------|------|----------|
| `opencode: not found` | 二进制文件不存在 | 重新构建镜像 |
| `permission denied` | 权限不足 | 检查文件权限 |
| `ConfigInvalidError` | 配置文件错误 | 检查 JSONC 格式 |
| `connection refused` | Server 未启动 | 检查 Server 日志 |
| `address already in use` | 端口被占用 | 修改端口映射 |

## 🔍 诊断工具

### 完整诊断脚本

```bash
#!/bin/bash

echo "=== Opencode Docker 诊断 ==="
echo ""

# 1. Docker 状态
echo "1. Docker 状态"
docker --version
docker ps | findstr opencode-cli
echo ""

# 2. 容器状态
echo "2. 容器状态"
docker ps -a | findstr opencode-cli
echo ""

# 3. 镜像状态
echo "3. 镜像状态"
docker images opencode-hybrid
echo ""

# 4. 容器日志
echo "4. 容器日志（最近 20 行）"
docker logs --tail 20 opencode-cli
echo ""

# 5. 网络连接
echo "5. 网络连接"
curl -s http://localhost:3000 || echo "Server 无法访问"
echo ""

# 6. CLI 功能
echo "6. CLI 功能"
docker exec opencode-cli opencode --version || echo "CLI 不可用"
echo ""

# 7. 工具版本
echo "7. 工具版本"
docker exec opencode-cli bash -c 'node --version && npm --version && git --version && openspec --version'
echo ""

echo "=== 诊断完成 ==="
```

## 📞 获取帮助

### 常用命令

```bash
# 查看容器状态
docker ps | findstr opencode-cli

# 查看容器日志
docker logs opencode-cli

# 进入容器
docker exec -it opencode-cli bash

# 重启容器
docker-compose restart opencode-cli

# 重新构建镜像
docker build -f Dockerfile -t opencode-hybrid:latest .

# 清理所有
docker-compose down
docker rmi opencode-hybrid:latest
```

### 参考文档

- **USAGE.md** - 详细使用指南
- **SUMMARY.md** - 快速开始
- **RELEASE.md** - 版本发布说明
- **FIX.md** - 技术细节

---

**最后更新**: 2025-01-09
**文档版本**: 1.0
**适用版本**: opencode-hybrid v6.0.1
