# Dockerfile 修复说明

## 问题诊断

### 问题1: OpenCode CLI 符号链接链路失效

**现象**：
```
opencode --version
# sh: opencode: not found
```

**根本原因**：
OpenCode CLI 安装后创建了二级符号链接：
```
/usr/local/bin/opencode -> ../lib/node_modules/opencode-ai/bin/opencode
└─> /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64/bin/opencode
```

问题：
1. 第一个链接使用相对路径 `../`，解析时可能失败
2. 第二个链接使用绝对路径，但在容器启动时工作目录改变
3. docker-entrypoint.sh 会尝试用 node 执行，但 opencode 是 ELF 二进制文件

### 问题2: PATH 配置不当

**Dockerfile 中**：
```dockerfile
ENV PATH=/app/node_modules/.bin:/root/.opencode/node_modules/.bin:$PATH
```

问题：
- 这些路径可能不存在或为空
- 没有确保 `/usr/local/bin` 优先
- 用户切换后路径可能失效

### 问题3: 容器启动命令不稳定

**docker-compose.yml 中**：
```yaml
command: ["sh", "-c", "opencode server --port 3000 --hostname 0.0.0.0 & sleep infinity"]
```

问题：
- `&` 后台运行可能导致进程意外退出
- `sleep infinity` 不是最佳实践
- 难以监控 server 进程状态

## 解决方案

### 修复1: 直接复制 opencode 二进制文件

**原 Dockerfile**：
```dockerfile
RUN npm i -g opencode-ai@latest
RUN opencode --version || echo "OpenCode CLI installed"
```

**修复后**：
```dockerfile
RUN npm i -g opencode-ai@latest

# 直接复制 opencode 二进制文件，避免符号链接问题
RUN cp /usr/local/lib/node_modules/opencode-ai/node_modules/opencode-linux-x64/bin/opencode \
    /usr/local/bin/opencode && \
    chmod +x /usr/local/bin/opencode

# 验证安装
RUN opencode --version
```

**优点**：
- 消除符号链接问题
- 直接访问 `/usr/local/bin/opencode`
- 构建时验证安装成功

### 修复2: 简化 PATH 配置

**原 Dockerfile**：
```dockerfile
ENV PATH=/app/node_modules/.bin:/root/.opencode/node_modules/.bin:$PATH
```

**修复后**：
```dockerfile
ENV PATH=/usr/local/bin:/usr/bin:/bin:/sbin
```

**优点**：
- 确保系统工具优先
- 避免不存在的路径
- 简洁明确

### 修复3: 改进容器启动方式

**原 docker-compose.yml**：
```yaml
command: ["sh", "-c", "opencode server --port 3000 --hostname 0.0.0.0 & sleep infinity"]
```

**修复后**：
```yaml
command: >
  sh -c "
    echo 'Starting OpenCode Server on port 3000...' &&
    opencode server --port 3000 --hostname 0.0.0.0
  "
```

**优点**：
- Server 在前台运行
- 容器随 server 进程退出而退出
- 便于监控和日志查看
- 无需 `sleep infinity`

### 修复4: 确保 openspec 可执行

**新增**：
```dockerfile
# 确保 openspec 可执行
RUN chmod +x /usr/local/bin/openspec
```

## 验证步骤

### 1. 重新构建镜像

```bash
cd .docker-for-opencode
docker build -f Dockerfile.fixed -t opencode-hybrid:fixed .
```

### 2. 验证 opencode 安装

```bash
# 使用 docker run 测试（不启动服务）
docker run --rm opencode-hybrid:fixed opencode --version

# 预期输出：
# opencode version x.x.x
```

### 3. 验证 openspec 安装

```bash
docker run --rm opencode-hybrid:fixed openspec --version

# 预期输出：
# openspec v0.18.0
```

### 4. 验证完整功能

```bash
# 启动容器
docker-compose -f docker-compose.fixed.yml up -d

# 测试 CLI 交互
docker exec -it opencode-cli opencode --help

# 测试 Server 连接
curl http://localhost:3000

# 进入容器测试
docker exec -it opencode-cli bash
opencode --version
openspec --version
```

## 对比总结

| 项目 | 原版本 | 修复版本 |
|------|--------|----------|
| opencode 安装 | 符号链接（二级） | 直接复制二进制 |
| PATH 配置 | 复杂，包含不存在的路径 | 简化，确保系统路径 |
| 容器启动 | 后台运行 + sleep infinity | 前台运行 server |
| 构建验证 | 弱验证（或忽略失败） | 强验证，失败即停止 |
| openspec | 依赖 npm 安装权限 | 显式 chmod +x |
| 稳定性 | 符号链接可能失效 | 直接访问，100% 可靠 |

## 关键改进点

1. **消除符号链接问题** - 直接复制二进制文件
2. **简化环境配置** - 移除不必要的 PATH
3. **改进进程管理** - 前台运行，便于监控
4. **增强构建验证** - 每个步骤都验证成功
5. **提高可维护性** - 代码更清晰，更容易理解

## 后续建议

1. 使用 Dockerfile.fixed 替换原 Dockerfile
2. 使用 docker-compose.fixed.yml 替换原配置
3. 更新 build.bat 和 build.sh 使用新配置
4. 添加自动化测试脚本验证镜像功能
