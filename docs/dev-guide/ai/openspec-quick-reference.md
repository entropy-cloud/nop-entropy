# OpenSpec 快速参考

## 常用命令速查

### 安装和初始化
```bash
# 全局安装OpenSpec
npm install -g @fission-ai/openspec@latest

# 验证安装
openspec --version

# 初始化项目（配置OpenCode）
openspec init --tools opencode

# 更新OpenSpec配置
openspec update
```

### 变更管理
```bash
# 查看所有活动变更
openspec list

# 显示变更详情
openspec show <change-name>

# 验证变更格式
openspec validate <change-name>

# 归档已完成变更
openspec archive <change-name> --yes
```

### 项目文件
```
openspec/
├── AGENTS.md           # OpenCode AI代理指令
├── changes/            # 变更提案目录
│   └── <change-name>/
│       ├── proposal.md    # 变更提案
│       ├── tasks.md       # 实施任务
│       ├── design.md      # 技术设计（可选）
│       └── specs/         # 规格变更
│           └── <domain>/
│               └── spec.md
├── specs/              # 当前规格（事实源）
│   └── <domain>/
│       └── spec.md
└── project.md          # 项目上下文

AGENTS.md              # 根目录代理指令存根
```

### OpenCode斜杠命令
```
/openspec-proposal <feature>  # 创建变更提案
/openspec-apply <change>      # 应用变更
/openspec-archive <change>    # 归档变更
```

## 典型工作流

### 1. 创建新功能
```
用户：/openspec-proposal 添加用户认证功能
AI：创建变更目录和初始文件
用户：审查和修改proposal.md和tasks.md
用户：/openspec-apply user-auth
AI：按tasks.md实现任务
用户：/openspec-archive user-auth
```

### 2. 修改现有功能
```
用户：创建提案修改用户认证逻辑
AI：生成变更delta
用户：审核spec.md中的变更
用户：批准并实施
AI：实现修改
用户：归档变更
```

## 规格（Spec）格式

### 基本结构
```markdown
# <Domain> Specification

## Purpose
描述这个规格的目的和范围

## Requirements
### Requirement: <需求名称>
系统 SHALL/MUST/MAY [需求描述]

#### Scenario: <场景名称>
- WHEN [前置条件]
- THEN [预期结果]
```

### 变更Delta格式
```markdown
# Delta for <Domain>

## ADDED Requirements
新增的需求...

## MODIFIED Requirements
修改的需求（完整文本）...

## REMOVED Requirements
移除的需求...
```

## 验证清单

### 安装验证
- [ ] Node.js版本 >= 20.19.0
- [ ] 运行`openspec --version`显示版本号
- [ ] 项目根目录存在`openspec/`目录
- [ ] 存在`AGENTS.md`文件
- [ ] OpenCode显示斜杠命令

### 变更验证
- [ ] `openspec list`显示预期变更
- [ ] `openspec validate <change>`通过
- [ ] proposal.md包含完整的需求说明
- [ ] tasks.md包含可执行的任务列表
- [ ] spec.md使用正确的格式

## 常见问题快速解决

### 命令找不到
```bash
# 检查PATH
echo $PATH  # Unix/Linux
echo %PATH%  # Windows

# 查找OpenSpec位置
which openspec  # Unix/Linux
where openspec  # Windows
```

### OpenCode不识别命令
```bash
# 1. 检查AGENTS.md
cat AGENTS.md

# 2. 完全重启OpenCode

# 3. 重新初始化
openspec init --tools opencode
```

### 权限问题
```bash
# Windows: 以管理员身份运行
# Unix/Linux: 使用sudo
sudo npm install -g @fission-ai/openspec@latest
```

## 最佳实践

1. **先写规格，再写代码**：使用OpenSpec确保需求明确
2. **小步迭代**：每个变更保持小而聚焦
3. **及时归档**：完成后立即归档，保持规格同步
4. **团队协作**：所有团队成员使用相同的规格格式
5. **版本控制**：将openspec/目录纳入Git管理
6. **持续更新**：定期运行`openspec update`保持最新

## 学习资源

- 详细安装指南：[openspec-installation.md](openspec-installation.md)
- 工作流说明：[../../openspec/AGENTS.md](../../openspec/AGENTS.md)
- 项目配置：[../../openspec/project.md](../../openspec/project.md)
- 官方文档：https://github.com/Fission-AI/OpenSpec

## 键盘快捷键

### OpenCode中
- 输入`/`快速打开命令面板
- 输入`/openspec`过滤OpenSpec命令
- 使用Tab键自动补全命令

### 命令行
- `↑/↓`：历史命令导航
- `Tab`：命令补全
- `Ctrl+C`：取消当前命令

## 环境变量

```bash
# 可选：设置OpenSpec配置目录（默认：./openspec）
export OPENSPEC_DIR=/path/to/openspec

# 可选：设置默认AI工具
export OPENSPEC_DEFAULT_TOOL=opencode
```

## 提示和技巧

1. **批量操作**：可以一次性创建多个提案，按优先级实施
2. **模板复用**：保留成功的变更模板，加速新提案
3. **审查流程**：在实施前进行团队代码审查
4. **自动化测试**：结合OpenSpec自动化测试框架
5. **文档同步**：规格说明可作为用户文档的来源

## 技术支持

- 查看日志：`openspec --verbose`
- 调试模式：`OPENSPEC_DEBUG=1 openspec list`
- 获取帮助：`openspec --help`
- 报告问题：https://github.com/Fission-AI/OpenSpec/issues

---

**快速查找：**
- 安装命令？见"安装和初始化"
- 创建提案？见"OpenCode斜杠命令"
- 格式问题？见"规格格式"
- 出错了？见"常见问题快速解决"
