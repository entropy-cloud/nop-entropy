# OpenCode 斜杠命令故障排除指南

## 问题：OpenCode 中看不到 /openspec-proposal 等命令

如果你在 OpenCode desktop 中看不到 `/openspec-proposal`、`/openspec-apply`、`/openspec-archive` 等斜杠命令，请按照以下步骤排查。

## 1. 确认命令文件已创建

首先，确认 OpenSpec 已经创建了命令文件：

```bash
# 检查 .opencode/command 目录是否存在
ls -la .opencode/command

# 应该看到以下三个文件：
# openspec-proposal.md
# openspec-apply.md
# openspec-archive.md
```

**预期输出：**
```
openspec-proposal.md
openspec-apply.md
openspec-archive.md
```

如果命令文件不存在，重新运行：
```bash
openspec init --tools opencode
```

## 2. 完全重启 OpenCode Desktop

这是最常见的问题！OpenCode 只在启动时加载斜杠命令。

### Windows

1. 完全退出 OpenCode（确保在任务管理器中没有残留进程）
2. 重新启动 OpenCode
3. 打开项目后，尝试输入 `/` 查看是否出现命令

### macOS

1. 完全退出 OpenCode（Command+Q）
2. 重新启动 OpenCode
3. 打开项目后，尝试输入 `/` 查看是否出现命令

### Linux

1. 完全退出 OpenCode
2. 重新启动 OpenCode
3. 打开项目后，尝试输入 `/` 查看是否出现命令

**重要提示**：仅仅重新加载项目是不够的，必须完全退出并重新启动 OpenCode。

## 3. 检查命令文件格式

确保命令文件格式正确。查看其中一个文件：

```bash
cat .opencode/command/openspec-proposal.md
```

**预期内容应该包含：**
- `---` 开头和结尾的 YAML front matter
- `description:` 字段
- `<UserRequest>` 和 `$ARGUMENTS` 占位符

**正确的格式示例：**
```markdown
---
description: Scaffold a new OpenSpec change and validate strictly.
---
The user has requested to create the following change proposal.
<UserRequest>
  $ARGUMENTS
</UserRequest>
<!-- ... 更多内容 ... -->
```

如果格式不正确，重新初始化：
```bash
# 删除 .opencode 目录
rm -rf .opencode

# 重新初始化
openspec init --tools opencode
```

## 4. 清除 OpenCode 缓存

如果重启后仍然看不到命令，尝试清除 OpenCode 的缓存。

### Windows

1. 关闭 OpenCode
2. 删除缓存目录：
   ```
   %APPDATA%\opencode\Cache
   ```
3. 重新启动 OpenCode

### macOS

1. 关闭 OpenCode
2. 删除缓存目录：
   ```bash
   rm -rf ~/Library/Caches/opencode
   ```
3. 重新启动 OpenCode

### Linux

1. 关闭 OpenCode
2. 删除缓存目录：
   ```bash
   rm -rf ~/.cache/opencode
   ```
3. 重新启动 OpenCode

## 5. 检查 OpenCode 版本

确认你的 OpenCode 版本支持 `.opencode/command` 集成。

在 OpenCode 中查看版本信息（通常在 Help > About 中）。

**最低版本要求**：OpenCode 应该支持读取项目根目录下的 `.opencode/` 配置目录。

如果版本过低，请升级到最新版本。

## 6. 检查项目结构

确保 `.opencode/command/` 目录在项目根目录：

```bash
# 查看项目根目录
ls -la | grep opencode

# 应该看到：
# .opencode/
```

正确的目录结构：
```
nop-entropy/
├── .opencode/
│   └── command/
│       ├── openspec-proposal.md
│       ├── openspec-apply.md
│       └── openspec-archive.md
├── openspec/
│   ├── AGENTS.md
│   ├── project.md
│   └── ...
├── AGENTS.md
└── pom.xml
```

## 7. 验证 AGENTS.md 文件

检查根目录的 AGENTS.md 文件：

```bash
cat AGENTS.md
```

应该看到：
```markdown
<!-- OPENSPEC:START -->
# OpenSpec Instructions
...
<!-- OPENSPEC:END -->
```

如果 AGENTS.md 不存在或内容不正确，重新初始化：
```bash
openspec update
```

## 8. 使用自然语言指令（备用方案）

如果斜杠命令仍然不可用，你可以使用自然语言指令，OpenCode 仍然可以理解：

### 替代 /openspec-proposal

```
请创建一个 OpenSpec 变更提案
我想规划一个新功能
帮我创建一个规格提案
```

### 替代 /openspec-apply

```
请开始实施这个变更
应用 OpenSpec 变更
按照 tasks.md 实施变更
```

### 替代 /openspec-archive

```
请归档这个变更
这个变更已经完成，请归档
```

OpenCode 会通过读取 `AGENTS.md` 和 `openspec/AGENTS.md` 来理解这些自然语言指令。

## 9. 手动验证命令文件

手动检查命令文件的内容：

```bash
# 查看所有命令文件
cat .opencode/command/openspec-proposal.md
echo "---"
cat .opencode/command/openspec-apply.md
echo "---"
cat .opencode/command/openspec-archive.md
```

确认每个文件都有：
- 正确的 YAML front matter（`---` 包围）
- `description:` 字段
- `<UserRequest>` 和 `$ARGUMENTS`

## 10. 检查权限问题

确保命令文件有正确的读取权限：

```bash
# 检查文件权限
ls -la .opencode/command/

# 应该看到类似：
# -rw-r--r-- ... openspec-proposal.md
# -rw-r--r-- ... openspec-apply.md
# -rw-r--r-- ... openspec-archive.md
```

如果权限不正确，修复权限：
```bash
chmod 644 .opencode/command/*.md
```

## 11. 查看 OpenCode 日志

如果以上步骤都无效，查看 OpenCode 的日志文件：

### Windows

日志位置：
```
%APPDATA%\opencode\logs\
```

### macOS

日志位置：
```bash
~/Library/Logs/opencode/
```

### Linux

日志位置：
```bash
~/.local/share/opencode/logs/
```

查找与 `command`、`slash`、`opencode` 相关的错误信息。

## 12. 重新安装 OpenSpec

如果所有步骤都失败，尝试重新安装 OpenSpec：

```bash
# 卸载 OpenSpec
npm uninstall -g @fission-ai/openspec

# 重新安装
npm install -g @fission-ai/openspec@latest

# 验证安装
openspec --version

# 重新初始化项目
openspec init --tools opencode

# 重启 OpenCode
```

## 13. 联系支持

如果以上所有步骤都无法解决问题：

1. 收集以下信息：
   - OpenCode 版本
   - OpenSpec 版本（`openspec --version`）
   - 操作系统版本
   - 命令文件内容
   - OpenCode 日志中的错误信息

2. 提交问题：
   - Nop Platform GitHub Issues: https://github.com/entropy-cloud/nop-entropy/issues
   - 或联系 OpenCode 支持

## 验证步骤

完成以上步骤后，验证命令是否可用：

### 方法1：在 OpenCode 中测试

1. 打开 OpenCode
2. 打开项目
3. 在聊天框中输入 `/`
4. 查看是否出现 `openspec-proposal`、`openspec-apply`、`openspec-archive`

### 方法2：测试命令

```
/openspec-proposal 测试功能
```

如果命令正常工作，OpenCode 应该：
1. 识别命令
2. 读取 `openspec/project.md`
3. 创建变更提案目录
4. 生成必要的文件

## 常见错误和解决方案

### 错误1：命令列表中看不到任何命令

**原因**：OpenCode 未加载命令文件

**解决方案**：
1. 完全退出并重启 OpenCode（最常见）
2. 清除 OpenCode 缓存
3. 检查命令文件格式

### 错误2：看到命令但执行无反应

**原因**：命令文件格式错误

**解决方案**：
1. 检查 YAML front matter 格式
2. 确保包含 `<UserRequest>` 和 `$ARGUMENTS`
3. 重新初始化：`openspec init --tools opencode`

### 错误3：命令执行但报错

**原因**：OpenSpec CLI 未正确安装或路径不在 PATH 中

**解决方案**：
1. 验证 OpenSpec 安装：`openspec --version`
2. 检查 PATH 环境变量
3. 重新安装 OpenSpec

## 成功标志

当你成功配置后，应该看到：

✅ OpenCode 中输入 `/` 显示 `openspec-proposal`、`openspec-apply`、`openspec-archive`
✅ 执行命令时 OpenCode 正确响应
✅ 能够创建和管理 OpenSpec 变更

## 总结

**最重要的步骤**：完全退出并重启 OpenCode！

这是 90% 的情况下的解决方案。OpenCode 只在启动时加载斜杠命令，仅仅重新加载项目或刷新是不够的。

---

**需要更多帮助？**

查看相关文档：
- [OpenSpec 安装指南](./openspec-installation.md)
- [OpenSpec 快速参考](./openspec-quick-reference.md)
- [OpenSpec 快速开始](./openspec-quickstart.md)
