# OpenCode Java LSP 快速参考

## ✅ 配置状态

- **LSP 服务器**: jdtls (Eclipse JDT.LS)
- **Java 版本**: OpenJDK 21.0.5 ✅
- **项目 Java 文件**: 7,861 个
- **OpenCode 版本**: 1.0.152 ✅
- **状态**: 已配置并启用

## 🚀 快速开始

```bash
# 启动 OpenCode
opencode

# 在 TUI 中打开任意 Java 文件
# LSP 会自动启动并提供智能功能
```

## 📋 LSP 提供的功能

| 功能 | 说明 |
|------|------|
| 代码补全 | 智能自动补全，包括方法和参数 |
| 错误诊断 | 实时显示编译错误和警告 |
| 跳转定义 | Ctrl+F12 跳转到符号定义 |
| 查找引用 | Shift+F12 查找符号的所有引用 |
| 代码格式化 | 自动格式化 Java 代码 |
| 快速修复 | 自动修复常见问题 |
| 重构 | 安全的重构操作 |

## 🧪 测试 LSP

```bash
# 运行测试脚本
bash scripts/test-java-lsp.sh
```

## 📝 实用示例

### 1. 让 AI 分析 LSP 报告的问题

```bash
# 先在 OpenCode TUI 中打开文件，让 LSP 识别问题
# 然后运行：
opencode run "Review the LSP diagnostics and suggest fixes for all reported issues"
```

### 2. 代码审查结合 LSP

```bash
# 使用 LSP 提供的上下文进行更准确的代码审查
opencode run "Analyze src/main/java/MyService.java considering the LSP-provided code structure and diagnostics"
```

### 3. 生成测试代码

```bash
opencode run "Generate comprehensive unit tests for src/main/java/com/example/MyService.java using the code completion information from LSP"
```

## ⚙️ 配置文件位置

```
.opencode/opencode.json
```

## 🔧 常用配置调整

### 调整 Maven 设置

```json
"java": {
  "configuration": {
    "maven": {
      "downloadSources": true,
      "updateSnapshots": false
    }
  }
}
```

### 自动导入

```json
"java": {
  "saveActions": {
    "organizeImports": true
  }
}
```

### 禁用自动构建

```json
"java": {
  "autobuild": {
    "enabled": false
  }
}
```

## 🎯 最佳实践

1. **提交前检查**: 使用 LSP 诊断发现的问题
2. **结合审查命令**: `npm run review` + LSP 诊断
3. **利用代码补全**: 加速开发
4. **快速修复**: 使用 LSP 的快速修复功能
5. **重构安全**: 使用 LSP 提供的重构功能

## 📚 文档

- 详细指南: `docs/opencode-java-lsp-guide.md`
- 测试脚本: `scripts/test-java-lsp.sh`

## 🔗 相关命令

```bash
# 代码审查
npm run review

# 测试 LSP
bash scripts/test-java-lsp.sh

# 启动 OpenCode
opencode

# 查看 LSP 日志
opencode --print-logs
```

## ⚡ 性能提示

对于大型项目（如当前 7,861 个 Java 文件）：

- LSP 首次启动可能需要较长时间（索引）
- 考虑设置 `maxConcurrentBuilds: 2` 限制并发
- 使用 `updateBuildConfiguration: "interactive"` 控制自动构建

## 🐛 故障排除

| 问题 | 解决方案 |
|------|---------|
| LSP 未启动 | 检查 Java 版本 ≥ 21 |
| 响应慢 | 减少并发数，关闭自动构建 |
| 错误不准确 | 清理 LSP 缓存并重启 |
| 内存不足 | 增加 JVM 内存设置 |

---

**开始使用**: `opencode` → 打开 Java 文件 → LSP 自动激活！
