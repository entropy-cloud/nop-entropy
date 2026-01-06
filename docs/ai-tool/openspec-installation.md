# OpenSpec 安装指南

## 目录
- [什么是OpenSpec](#什么是openspec)
- [为什么使用OpenSpec](#为什么使用openspec)
- [环境要求](#环境要求)
- [安装步骤](#安装步骤)
- [与OpenCode集成](#与opencode集成)
- [验证安装](#验证安装)
- [基本使用](#基本使用)
- [常见问题](#常见问题)
- [更新OpenSpec](#更新openspec)

---

## 什么是OpenSpec

OpenSpec是一个规范驱动的开发工作流工具，用于AI编码助手。它通过在编写代码之前锁定需求，为人类和AI助手提供确定性、可审查的输出。

OpenSpec的核心特点：
- **无API密钥要求**：完全本地运行，不需要外部服务
- **轻量级**：简单的工作流，最小化设置
- **Brownfield优先**：适用于已有项目，保持差异明确和可控
- **变更跟踪**：提案、任务和规格更新一起管理
- **AGENTS.md兼容**：所有AI工具都能读取工作流指令

## 为什么使用OpenSpec

### 对开发者的价值
- **需求明确**：在编码前明确需求，减少返工
- **可追溯**：所有变更都有明确的规格说明
- **可审查**：代码变更与规格直接对应
- **版本控制友好**：规格和代码都纳入Git管理

### 对AI助手的优势
- **上下文清晰**：AI助手能准确理解需求
- **输出可预测**：基于明确的规格生成代码
- **减少误解**：避免模糊指令导致的问题
- **工作流标准化**：统一的工作流程

### 对团队协作的好处
- **知识共享**：规格说明作为团队知识库
- **并行开发**：多个开发者可基于同一规格工作
- **代码审查**：规格作为审查的基准
- **新人上手**：规格说明帮助新人快速理解系统

## 环境要求

### 必需环境
- **Node.js**：版本 >= 20.19.0
  - 检查版本：`node --version`
  - 如版本过低，请从 [Node.js官网](https://nodejs.org/) 下载安装

### 可选环境
- **npm**：Node.js自带
- **OpenCode**：AI编码助手，建议最新版本

### 操作系统
- Windows 10/11
- macOS 10.15+
- Linux (主流发行版)

## 安装步骤

### 步骤1：检查Node.js版本

打开终端/命令提示符，运行以下命令检查Node.js版本：

```bash
node --version
```

如果版本低于20.19.0，需要先升级Node.js。

### 步骤2：全局安装OpenSpec CLI

使用npm全局安装OpenSpec命令行工具：

```bash
npm install -g @fission-ai/openspec@latest
```

安装过程会显示：
- 安装的包数量
- 版本信息
- 更新提示（如有）

### 步骤3：验证安装

验证OpenSpec是否成功安装：

```bash
openspec --version
```

成功安装会显示版本号，例如：`0.17.2`

如果出现"command not found"错误，请：
1. 检查npm全局安装路径是否在PATH中
2. 重新打开终端窗口
3. 或者尝试重启电脑

### 步骤4：进入项目目录

导航到您要使用OpenSpec的项目目录：

```bash
cd /path/to/your/project
```

### 步骤5：初始化OpenSpec

在项目根目录初始化OpenSpec：

```bash
openspec init --tools opencode
```

这个命令会：
- 创建`openspec/`目录结构
- 配置OpenCode工具支持
- 生成AGENTS.md文件
- 创建项目配置文件

**参数说明：**
- `--tools opencode`：指定使用OpenCode工具
- 其他可选工具：`claude`, `cursor`, `codex`, `codebuddy`, `windsurf`, `github-copilot`等
- 使用`--tools all`可以配置所有支持的AI工具

### 步骤6：确认目录结构

检查生成的目录结构：

```bash
ls -la openspec/
```

应该看到以下内容：
```
openspec/
├── AGENTS.md           # OpenCode的AI代理指令
├── changes/            # 变更提案目录
├── specs/              # 规格说明目录
└── project.md          # 项目上下文文件
```

同时检查根目录是否生成了`AGENTS.md`文件：

```bash
ls -la AGENTS.md
```

### 步骤7：（可选）填写项目上下文

使用AI助手帮助填写`openspec/project.md`，包含项目信息、技术栈、编码规范等。

## 与OpenCode集成

### 集成方式

OpenSpec与OpenCode的集成已经通过`openspec init --tools opencode`自动完成。

### 检查集成状态

OpenCode会自动读取项目根目录的`AGENTS.md`文件，该文件包含了OpenSpec的工作流指令。

### 重启OpenCode

为了确保OpenCode能加载新的斜杠命令，建议重启OpenCode。

### 可用的斜杠命令

重启后，OpenCode中会自动出现以下斜杠命令：

- `/openspec-proposal`：创建变更提案
- `/openspec-apply`：应用变更
- `/openspec-archive`：归档变更

### 使用斜杠命令

在OpenCode中，您可以直接使用这些命令，例如：

```
/openspec-proposal 添加用户认证功能
```

OpenCode会：
1. 解析命令
2. 创建变更提案目录
3. 生成必要的文件结构
4. 填充初始内容

## 验证安装

### 验证OpenSpec CLI

运行以下命令验证OpenSpec CLI功能：

```bash
openspec list
```

应该显示：`No active changes found.`

如果初始化了变更，会列出所有活动的变更。

### 验证OpenCode集成

1. 启动OpenCode
2. 尝试使用斜杠命令：`/openspec-proposal`
3. 检查是否能看到OpenSpec相关命令

### 验证目录结构

确认所有必需的目录和文件都已创建：

```bash
tree openspec/
```

或者手动检查：

```bash
ls -la openspec/
ls -la openspec/changes/
ls -la openspec/specs/
cat openspec/project.md
```

### 验证AGENTS.md

检查AGENTS.md文件是否包含OpenSpec指令：

```bash
cat AGENTS.md
```

应该看到包含OpenSpec工作流说明的内容。

## 基本使用

### 创建第一个变更提案

#### 方式1：使用OpenCode斜杠命令

在OpenCode中输入：

```
/openspec-proposal 添加用户登录功能
```

#### 方式2：使用命令行

```bash
# OpenCode会自动识别并处理以下请求
# 告诉AI助手：创建一个OpenSpec变更提案，添加用户登录功能
```

### 查看变更

```bash
openspec list
```

### 显示变更详情

```bash
openspec show <change-name>
```

### 验证规格格式

```bash
openspec validate <change-name>
```

### 归档已完成变更

```bash
openspec archive <change-name> --yes
```

## 常见问题

### Q1: npm install失败

**问题**：运行`npm install -g @fission-ai/openspec@latest`时出错

**解决方案**：
1. 检查网络连接
2. 使用国内镜像源：
   ```bash
   npm config set registry https://registry.npmmirror.com
   ```
3. 重新尝试安装

### Q2: 命令找不到

**问题**：运行`openspec --version`提示"command not found"

**解决方案**：
1. 检查npm全局安装路径：
   ```bash
   npm config get prefix
   ```
2. 将npm全局安装路径添加到PATH环境变量
3. 在Windows上，路径通常是`%APPDATA%\npm`
4. 在Unix系统上，通常是`/usr/local`

### Q3: OpenCode不识别斜杠命令

**问题**：OpenCode中看不到`/openspec-proposal`等命令

**解决方案**：
1. 确认`AGENTS.md`文件存在于项目根目录
2. 完全重启OpenCode（不仅仅是重新加载项目）
3. 检查OpenCode版本是否支持AGENTS.md规范
4. 确认初始化时使用了`--tools opencode`参数

### Q4: Node.js版本过低

**问题**：`node --version`显示版本低于20.19.0

**解决方案**：
1. 使用nvm（Node Version Manager）安装新版本：
   ```bash
   nvm install 20
   nvm use 20
   ```
2. 或从Node.js官网下载安装包

### Q5: 初始化时交互式提示无法操作

**问题**：运行`openspec init`时出现交互式提示但无法输入

**解决方案**：
使用非交互式模式：
```bash
openspec init --tools opencode
```

### Q6: 权限问题

**问题**：安装时提示权限不足

**解决方案**：
- **Windows**：以管理员身份运行命令提示符
- **macOS/Linux**：使用sudo
  ```bash
  sudo npm install -g @fission-ai/openspec@latest
  ```

### Q7: 端口被占用

**问题**：OpenSpec需要使用某些端口但被占用

**解决方案**：
OpenSpec本身不占用端口，但集成工具可能需要。检查并关闭冲突的服务。

## 更新OpenSpec

### 检查当前版本

```bash
openspec --version
```

### 更新到最新版本

```bash
npm install -g @fission-ai/openspec@latest
```

### 更新项目中的OpenSpec配置

更新后，在项目目录中运行：

```bash
openspec update
```

这会：
- 更新AGENTS.md文件
- 确保最新的斜杠命令可用
- 刷新AI代理指令

## 下一步

安装完成后，建议按以下顺序进行：

1. **填写项目信息**：使用AI助手帮助完成`openspec/project.md`
2. **创建第一个变更**：使用`/openspec-proposal`创建小规模的测试变更
3. **学习工作流**：阅读`openspec/AGENTS.md`了解完整工作流
4. **团队推广**：将本安装指南分享给团队成员

## 获取帮助

### 官方资源
- GitHub仓库：[https://github.com/Fission-AI/OpenSpec](https://github.com/Fission-AI/OpenSpec)
- 文档：查看OpenSpec官方文档
- Discord社区：加入OpenSpec Discord获取帮助

### 本地帮助
```bash
openspec --help           # 查看所有命令
openspec <command> --help # 查看特定命令的帮助
```

### 项目文档
- `openspec/AGENTS.md`：OpenSpec工作流详细说明
- `openspec/project.md`：项目特定配置（需要填写）

## 卸载OpenSpec

如果需要卸载OpenSpec：

```bash
npm uninstall -g @fission-ai/openspec
```

然后删除项目中的OpenSpec目录：

```bash
rm -rf openspec/
rm AGENTS.md
```

## 总结

通过本指南，您应该已经成功：
- ✓ 安装了OpenSpec CLI
- ✓ 在项目中初始化了OpenSpec
- ✓ 配置了与OpenCode的集成
- ✓ 验证了安装的正确性

现在可以开始使用OpenSpec进行规范驱动的开发了！
