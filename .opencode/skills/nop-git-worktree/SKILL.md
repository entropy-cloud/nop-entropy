---
name: nop-git-worktree
description: 涉及git worktree的操作优先使用这个skill。管理 Git bare 仓库和多个并行 worktree 的开发环境。支持初始化 bare 仓库结构、自动生成分支名并创建 feature worktree。
---

## 功能

### 操作类型 1: 初始化 bare + worktree 项目结构

创建适合 worktree 开发的项目结构：
- 创建 `.bare` 目录作为 Git bare 仓库（中心）
- 支持从远程仓库 clone 或从本地现有仓库迁移
- 自动创建 main/master 分支的 worktree 目录

### 操作类型 2: 创建新的 feature worktree

在已初始化的项目下创建新 worktree：
- 根据需求描述智能生成分支名
- 自动创建对应的 worktree 目录
- 确保分支名唯一且符合 Git 命名规范

## 使用时机

**初始化项目结构**：
- "初始化 bare + worktree 开发环境"
- "从远程仓库创建 bare 仓库和多个 worktree"
- "把现有 git 仓库转换成 bare + worktree 结构"

**创建新 worktree**：
- "创建一个新分支用于 [需求描述]"
- "添加一个新的 worktree 来开发 [功能]"

## 目录结构

```
project-root/
├── .bare/              # Git bare 仓库（中心）
├── main/               # 主分支 worktree
├── feature-auth/       # 特性分支 worktree
└── fix-login/          # 修复分支 worktree
```

## 操作说明

### 初始化 bare + worktree 项目结构

**参数**：
- **远程仓库 URL** (可选): 如 `git@github.com:user/repo.git`
- **本地仓库路径** (可选): 现有 git 仓库的路径

**步骤**：
1. 检查当前目录是否为空
2. 创建 `.bare` 目录作为 bare 仓库
3. 执行 `git clone --bare <url|path> .bare`
4. 识别默认分支（main 或 master）
5. 执行 `git worktree add main <default-branch>` 创建主分支 worktree
6. **创建 Maven 配置文件**（重要，用于限制分支使用本地仓库）：
   - 在主分支 worktree 中创建 `.mvn/maven.config` 文件，内容为：
     ```bash
     -Dmaven.repo.local.head=.nop/repository
     -Dmaven.repo.local.tail.ignoreAvailability=true
     ```

### 创建新的 feature worktree

**参数**：
- **需求描述** (必填): 自然语言描述的功能需求

**分支名生成规则**：

| 需求类型 | 前缀 | 示例 |
|---------|------|------|
| 新功能 | `add-`, `feat-` | `add-user-auth`, `feat-payment` |
| 修复 | `fix-` | `fix-login-error`, `fix-crash` |
| 重构 | `refactor-` | `refactor-auth`, `refactor-db` |
| 性能优化 | `perf-`, `optimize-` | `perf-query`, `optimize-cache` |
| 紧急修复 | `hotfix-` | `hotfix-security` |
| 文档 | `docs-` | `docs-api` |
| 测试/杂项 | `test-`, `chore-` | `test-auth`, `chore-deps` |

**步骤**：
1. 分析需求描述，选择合适的前缀
2. 提取核心关键词组合生成分支名
3. 检查分支名是否已存在，必要时添加序号
4. 执行 `git worktree add <branch-name> -b <branch-name>`
5. **创建 Maven 配置文件**（重要，用于限制分支使用本地仓库）：
   - 创建 `.mvn/maven.config` 文件，内容为：
     ```bash
     -Dmaven.repo.local.head=.nop/repository
     -Dmaven.repo.local.tail.ignoreAvailability=true
     ```
   - 此配置让每个 worktree 使用独立的本地仓库（`.nop/repository`），避免相互干扰
   - 可选：创建元数据文件 `.worktree-branch` 和 `.worktree-base` 并添加到 `.gitignore`

## 常用命令

```bash
# 查看所有 worktree
git worktree list

# 删除 worktree
git worktree remove feature-auth
git worktree prune  # 清理无效引用

# 移动 worktree
git worktree move old-path new-path

# 切换 worktree
cd main/           # 主分支
cd feature-auth/   # 功能分支
```

## 最佳实践

1. **分支命名**: 使用清晰的语义化分支名（小写字母、数字、连字符，20-30 字符）
2. **及时清理**: 完成功能后及时删除 worktree
3. **定期同步**: 在 main worktree 中定期 pull 远程更新
4. **独立环境**: 每个 worktree 独立安装依赖，避免冲突

## Maven 配置说明

### 为什么要配置 Maven 本地仓库

每个 worktree 使用独立的 Maven 本地仓库有以下好处：
1. **隔离性**: 避免不同分支之间的依赖冲突
2. **速度**: 不需要频繁切换或清理本地仓库
3. **稳定性**: 每个分支有其稳定的依赖环境

### Maven 配置文件格式

在 worktree 根目录下创建 `.mvn/maven.config` 文件：

```bash
-Dmaven.repo.local.head=.nop/repository
-Dmaven.repo.local.tail.ignoreAvailability=true
```

**参数说明**：
- `maven.repo.local.head`: Maven 4.x 支持的本地仓库链的前端路径
  - 设置为 `.nop/repository` 意味着优先使用当前 worktree 下的 `.nop/repository` 目录
  - Maven 会先从 head 仓库查找依赖，找不到再从主仓库查找
- `maven.repo.local.tail.ignoreAvailability=true`: 忽略尾部仓库的可用性检查
  - 提高启动速度，避免不必要的网络检查

### 目录结构

```
project-root/
├── .bare/                      # Git bare 仓库
├── main/                       # 主分支 worktree
│   ├── .mvn/
│   │   └── maven.config        # Maven 配置
│   └── .nop/
│       └── repository/          # 独立的本地仓库
└── feat-auth/                  # 功能分支 worktree
    ├── .mvn/
    │   └── maven.config
    └── .nop/
        └── repository/
```


## 故障排查

- **worktree 目录损坏**: `git worktree prune`
- **无法切换分支**: 使用 `git worktree list` 检查状态
- **bare 仓库推送失败**: 检查 remote 配置和访问权限
- **Maven 配置未生效**: 确保 `.mvn/maven.config` 文件位于 worktree 根目录，路径正确
