# Java LSP 配置指南

## 概述

OpenCode 已配置支持 Java Language Server Protocol (LSP)，使用 Eclipse JDT.LS 作为 Java 语言服务器。

## 当前配置

### 系统要求

- ✅ Java SDK: OpenJDK 21.0.5
- ✅ OpenCode CLI: v1.0.152
- ✅ 项目包含大量 Java 文件

### 配置文件

`.opencode/opencode.json` 已配置启用 jdtls LSP 服务器：

```json
{
  "$schema": "https://opencode.ai/config.json",
  "share": "disabled",
  "lsp": {
    "jdtls": {
      "disabled": false,
      "extensions": [".java"],
      "initialization": {
        "settings": {
          "java": {
            "configuration": {
              "updateBuildConfiguration": "interactive"
            },
            "format": {
              "enabled": true
            },
            "completion": {
              "favoriteStaticMembers": [
                "org.junit.Assert.*",
                "org.junit.jupiter.api.Assertions.*",
                "org.mockito.Mockito.*"
              ]
            }
          }
        }
      }
    }
  }
}
```

## 功能特性

### 1. 代码补全
- 方法参数提示
- 变量自动补全
- 类型推断补全

### 2. 诊断功能
- 实时错误检测
- 编译错误提示
- 语法高亮错误
- 代码警告

### 3. 代码导航
- 跳转到定义 (Go to Definition)
- 查找引用 (Find References)
- 查看实现 (Find Implementations)

### 4. 代码操作
- 快速修复 (Quick Fix)
- 重构操作
- 导入优化
- 代码格式化

### 5. 其他功能
- 代码大纲 (Code Outline)
- Javadoc 悬停提示
- 代码透镜 (Code Lens)
- 符号搜索

## 使用方法

### 启动 OpenCode

```bash
# 在项目根目录运行
opencode
```

### 使用 LSP 功能

当你在 OpenCode 中打开 Java 文件时，LSP 服务器会自动启动：

```bash
# 打开 OpenCode 后
opencode

# 在 TUI 中导航到 Java 文件
# LSP 会自动启动并提供智能提示
```

### 测试 LSP 配置

运行测试脚本验证配置：

```bash
bash scripts/test-java-lsp.sh
```

## 常用命令

### 在 OpenCode TUI 中

- `Ctrl+P` - 打开文件
- `Ctrl+Shift+P` - 命令面板
- `F12` - 跳转到定义
- `Shift+F12` - 查找引用
- `Ctrl+Space` - 代码补全
- `Ctrl+Shift+F` - 格式化代码

### CLI 使用

```bash
# 让 OpenCode 分析特定 Java 文件
opencode run "Review the code in src/main/java/MyClass.java for any issues"

# 让 OpenCode 带着上下理解代码
opencode
# 然后在 TUI 中打开 Java 文件，LSP 会提供代码上下文
```

## 配置选项

### 自定义 LSP 设置

编辑 `.opencode/opencode.json`：

```json
{
  "lsp": {
    "jdtls": {
      "initialization": {
        "settings": {
          "java": {
            "configuration": {
              "updateBuildConfiguration": "automatic", // automatic|interactive|disabled
              "maven": {
                "downloadSources": true,
                "updateSnapshots": true
              }
            },
            "format": {
              "enabled": true,
              "profile": "GoogleStyle" // 或其他格式配置
            },
            "saveActions": {
              "organizeImports": true
            }
          }
        }
      }
    }
  }
}
```

### 禁用特定 LSP

如果需要禁用 Java LSP：

```json
{
  "lsp": {
    "jdtls": {
      "disabled": true
    }
  }
}
```

### 禁用所有 LSP

```json
{
  "lsp": false
}
```

## 与 AI 协作的示例

### 1. 代码审查

```bash
# 先让 LSP 识别代码问题
# 然后：
opencode run "Review the errors and warnings reported by LSP in src/main/java/MyService.java and suggest fixes"
```

### 2. 重构建议

```bash
opencode run "Analyze the code structure of src/main/java/MyController.java and suggest refactoring improvements"
```

### 3. 测试生成

```bash
opencode run "Generate unit tests for src/main/java/com/example/Calculator.java using JUnit 5"
```

## 故障排除

### LSP 未启动

1. 检查 Java 版本：
   ```bash
   java -version  # 需要 21+
   ```

2. 确认配置文件：
   ```bash
   cat .opencode/opencode.json
   ```

3. 查看日志：
   ```bash
   opencode --print-logs
   ```

### 缓存问题

如果遇到缓存问题，可以：

```bash
# 删除 OpenCode 缓存
rm -rf ~/.opencode/cache

# 或删除项目特定的 LSP 缓存
rm -rf .opencode/lsp
```

### 禁用自动下载

如果不想 OpenCode 自动下载 LSP 服务器：

```bash
export OPENCODE_DISABLE_LSP_DOWNLOAD=true
```

## 性能优化

### 对于大型项目

1. 减少索引范围
2. 禁用不必要的功能
3. 调整内存设置

```json
{
  "lsp": {
    "jdtls": {
      "initialization": {
        "settings": {
          "java": {
            "maxConcurrentBuilds": 2,
            "autobuild": {
              "enabled": false
            }
          }
        }
      }
    }
  }
}
```

## 相关资源

- [OpenCode LSP 文档](https://opencode.ai/docs/lsp/)
- [Eclipse JDT.LS 文档](https://github.com/eclipse/eclipse.jdt.ls)
- [JDT.LS 维基](https://github.com/redhat-developer/vscode-java/wiki)

## 高级用法

### 添加自定义 LSP 服务器

```json
{
  "lsp": {
    "my-custom-lsp": {
      "command": ["my-lsp-server", "--stdio"],
      "extensions": [".mylang"],
      "initialization": {
        "customOptions": {}
      }
    }
  }
}
```

### 环境变量

可以在 LSP 启动时设置环境变量：

```json
{
  "lsp": {
    "jdtls": {
      "env": {
        "JAVA_HOME": "/path/to/java",
        "MAVEN_OPTS": "-Xmx2g"
      }
    }
  }
}
```

## 最佳实践

1. **保持 Java 版本更新** - 使用最新的 LTS 版本（21+）
2. **定期清理缓存** - 对于大型项目
3. **合理配置内存** - 根据项目大小调整
4. **利用 LSP 诊断** - 在提交代码前修复 LSP 报告的问题
5. **结合 AI 使用** - 让 OpenCode 理解 LSP 提供的上下文

## 总结

Java LSP 已在项目中正确配置并启用。通过结合 OpenCode 的 AI 能力和 JDT.LS 的代码理解能力，你可以：

- 获得更准确的代码分析
- 更快的代码补全
- 更智能的重构建议
- 更好的错误诊断

开始使用：

```bash
opencode
```

在 TUI 中打开任意 Java 文件，LSP 将自动激活！
