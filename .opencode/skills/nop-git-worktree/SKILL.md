---
name: nop-git-worktree
description: 涉及git worktree的操作优先使用这个skill。管理 Git bare 仓库和多个并行 worktree 的开发环境。支持初始化 bare 仓库结构、自动生成分支名并创建 feature worktree。
---

## 重要提示

### ⚠️ 必须遵守的操作规则

1. **所有 `git worktree` 相关命令必须在 `.bare` 目录中执行**
    - ✅ 正确：`git -C .bare worktree add ../feature-xxx -b feature-xxx`
    - ❌ 错误：`git -C .bare worktree add feature-xxx -b feature-xxx`

2. **创建 worktree 时使用相对路径**
    - ✅ 正确：使用相对路径 `../`
    - ❌ 错误：使用绝对路径

3. **遇到错误时，按顺序清理**
    ```bash
    cd ~/app/nop-entropy-wt
    git -C .bare worktree remove ../nop-entropy-feature-xxx 2>/dev/null || true
    rm -rf nop-entropy-feature-xxx 2>/dev/null || true
    git -C .bare worktree prune
    git -C .bare branch -D feature-xxx
    ```

4. **操作目录**
    | 操作类型 | 执行目录 |
    |---------|---------|
    | 创建/删除 worktree | `cd ~/app/nop-entropy-wt && git -C .bare worktree ...` |
    | 查看 worktree 列表 | `cd ~/app/nop-entropy-wt && git -C .bare worktree list` |
    | 查看/操作分支 | `cd ~/app/nop-entropy-wt && git -C .bare branch ...` |
    | 在 worktree 中开发 | `cd ~/app/nop-entropy-wt/nop-entropy-feature-xxx` |

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
project-root/                          # 项目根目录（如 ~/app/nop-entropy-wt）
├── .bare/                            # Git bare 仓库（中心），所有 git worktree 操作在此执行
│   ├── HEAD                          # 当前默认分支
│   ├── config                        # Git 配置
│   ├── objects/                      # Git 对象存储
│   ├── refs/                         # Git 引用
│   └── ...
├── nop-entropy-master/               # 主分支 worktree（开发时在此目录），目录名=项目名-分支名
│   └── ...                          # 项目文件
├── nop-entropy-feat-auth/            # 特性分支 worktree，目录名=项目名-分支名
│   ├── .mvn/
│   │   └── maven.config
│   └── ...
└── nop-entropy-fix-login/            # 修复分支 worktree，目录名=项目名-分支名
    ├── .mvn/
    │   └── maven.config
    └── ...
```

**关键说明**：
- `.bare/` 目录：这是 Git bare 仓库，所有 `git worktree` 命令必须在此目录或通过 `git -C .bare` 执行
- **worktree 目录命名**：`<PROJECT_NAME>-<BRANCH_NAME>` 格式，便于在 IDE 中区分不同项目的 worktree
- `nop-entropy-master/`, `nop-entropy-feat-auth/` 等目录：这些是实际的工作目录，开发时在这些目录中进行 git 操作（如 `git commit`, `git status`）

## 操作说明

### 初始化 bare + worktree 项目结构

**参数**：
- **项目根目录** (必填): 如 `~/app/nop-entropy-wt`
- **远程仓库 URL** (可选): 如 `https://gitee.com/user/repo.git`
- **本地仓库路径** (可选): 现有 git 仓库的路径

**步骤**：
```bash
cd ~/app/nop-entropy-wt

# 1. 创建 bare 仓库
git clone --bare https://gitee.com/canonical-entropy/nop-entropy.git .bare
# 或从本地克隆: git clone --bare ~/app/nop-entropy .bare

# 2. 启用长路径支持（Windows 平台重要配置）
# Windows 默认路径限制 260 字符，某些项目文件路径可能超限
git -C .bare config core.longpaths true
echo "Long paths enabled"

# 3. 识别项目名称（从 remote URL 提取）
PROJECT_NAME=$(git -C .bare config --get remote.origin.url | sed -E 's|.*/([^/]+?)(\.git)?$|\1|')
echo "Project name: $PROJECT_NAME"

# 4. 识别默认分支（优先从 .bare/HEAD 读取，兼容 bare 仓库）
DEFAULT_BRANCH=$(cat .bare/HEAD 2>/dev/null | sed 's@^refs/heads/@@')
# 如果 .bare/HEAD 读取失败，尝试从 remote 读取
if [ -z "$DEFAULT_BRANCH" ]; then
    DEFAULT_BRANCH=$(git -C .bare symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@')
fi
echo "Default branch: $DEFAULT_BRANCH"

# 5. 创建主分支 worktree（目录名: PROJECT_NAME-BRANCH_NAME）
git -C .bare worktree add "../${PROJECT_NAME}-${DEFAULT_BRANCH}" "$DEFAULT_BRANCH"

# 6. 验证
git -C .bare worktree list
```

### 创建新的 feature worktree

**参数**：
- **需求描述** (必填): 自然语言描述的功能需求
- **项目根目录** (必填): 如 `~/app/nop-entropy-wt`

**分支名生成规则**：
| 需求类型 | 前缀 | 示例 |
|---------|------|------|
| 新功能 | `add-`, `feat-` | `add-user-auth`, `feat-payment` |
| 修复 | `fix-` | `fix-login-error`, `fix-crash` |
| 重构 | `refactor-` | `refactor-db`, `refactor-job-scheduling` |
| 性能优化 | `perf-`, `optimize-` | `perf-query`, `optimize-cache` |
| 紧急修复 | `hotfix-` | `hotfix-security` |
| 文档 | `docs-` | `docs-api` |
| 测试/杂项 | `test-`, `chore-` | `test-auth`, `chore-deps` |

**步骤**：
```bash
cd ~/app/nop-entropy-wt

# 获取项目名称（从 remote URL 提取）
PROJECT_NAME=$(git -C .bare config --get remote.origin.url | sed -E 's|.*/([^/]+?)(\.git)?$|\1|')

BRANCH_NAME="refactor-job-scheduling"
WORKTREE_NAME="${PROJECT_NAME}-${BRANCH_NAME}"  # worktree 目录名: PROJECT_NAME-BRANCH_NAME

# 1. 如果分支已存在，先清理
if git -C .bare branch --list "$BRANCH_NAME" > /dev/null; then
    git -C .bare worktree remove "../$WORKTREE_NAME" 2>/dev/null || true
    rm -rf "$WORKTREE_NAME" 2>/dev/null || true
    git -C .bare worktree prune
    git -C .bare branch -D "$BRANCH_NAME"
fi

# 2. 创建 worktree（目录名: PROJECT_NAME-BRANCH_NAME）
git -C .bare worktree add "../$WORKTREE_NAME" -b "$BRANCH_NAME"

# 3. 创建 Maven 配置（Feature 分支专用）
mkdir -p "$WORKTREE_NAME/.mvn"
cat > "$WORKTREE_NAME/.mvn/maven.config" <<'EOF'
-Dmaven.repo.local.head=${maven.multiModuleProjectDirectory}/.nop/repository
-Dmaven.repo.local.tail.ignoreAvailability=true
EOF

# 4. 验证
git -C .bare worktree list
```

## 常用命令

**注意**：所有 `git worktree` 相关命令必须在 `.bare` 目录中执行，或使用 `git -C .bare` 参数。

```bash
# 查看 worktree 列表
git -C .bare worktree list

# 删除 worktree（指定 worktree 目录名）
git -C .bare worktree remove nop-entropy-feature-auth

# 清理无效引用
git -C .bare worktree prune

# 移动 worktree
git -C .bare worktree move nop-entropy-old-name nop-entropy-new-name

# 切换 worktree 目录（目录名 = PROJECT_NAME-BRANCH_NAME）
cd ~/app/nop-entropy-wt/nop-entropy-master           # 主分支
cd ~/app/nop-entropy-wt/nop-entropy-feature-auth      # 功能分支

# 在 worktree 中查看状态
cd ~/app/nop-entropy-wt/nop-entropy-feature-auth
git status
git branch
```

## 分支更新模式

### ⚠️ 重要：区分两种更新模式

在更新分支时，必须明确区分以下两种情况：

| 模式 | 场景 | 操作 | 是否需要确认 |
|------|------|------|-------------|
| **普通更新** | 特性分支同步主分支最新内容，保留自己的修改 | `git rebase origin/master` | 否（默认行为） |
| **强制重置** | 丢弃本地所有修改，完全同步主分支 | `git reset --hard origin/master` | **是（必须确认）** |

### ⚠️ 关键：正确更新远程跟踪分支

**问题**：`git fetch origin` 可能只更新 FETCH_HEAD，而不更新 `refs/remotes/origin/master`，导致 `origin/master` 指向旧版本。

**解决方案**：必须使用强制更新语法：

```bash
# ❌ 错误：可能不会更新 refs/remotes/origin/master
git fetch origin

# ✅ 正确：强制更新远程跟踪分支
git fetch origin +refs/heads/master:refs/remotes/origin/master
```

**验证远程同步状态**：在执行任何分支更新前，必须验证本地 `origin/master` 是否真正同步：

```bash
# 1. 检查远程实际最新提交
git ls-remote origin refs/heads/master

# 2. 检查本地 origin/master 指向的提交
git rev-parse origin/master

# 3. 两者应该相同，如果不同说明需要强制 fetch
```

### 普通更新（保留本地修改）

特性分支的默认行为：拉取主分支最新内容，然后将自己的提交叠加在上面。

```bash
cd ~/app/nop-entropy-wt

# 1. 【关键】强制更新远程跟踪分支（不是简单的 git fetch origin）
git fetch origin +refs/heads/master:refs/remotes/origin/master

# 2. 验证同步状态（可选但推荐）
git ls-remote origin refs/heads/master  # 远程实际
git rev-parse origin/master              # 本地跟踪

# 3. 在特性分支中 rebase（保留本地修改）
cd nop-entropy-feat-xxx
git rebase origin/master

# 4. 如果有冲突，智能解决后继续
git add .
git rebase --continue
```

### 强制重置（丢弃本地修改）

⚠️ **必须先向用户确认**：此操作会丢失所有本地修改！

```bash
cd ~/app/nop-entropy-wt

# 1. 【关键】强制更新远程跟踪分支
git fetch origin +refs/heads/master:refs/remotes/origin/master

# 2. 确认后强制重置
cd nop-entropy-feat-xxx
git reset --hard origin/master
```

### 主分支更新

主分支（master）始终与远程同步：

```bash
cd ~/app/nop-entropy-wt

# 1. 【关键】强制更新远程跟踪分支
git fetch origin +refs/heads/master:refs/remotes/origin/master

# 2. 验证同步
git log --oneline HEAD..origin/master  # 应该为空

# 3. 更新主分支
cd nop-entropy-master
git pull --ff-only origin master
# 或者：git reset --hard origin/master
```

### 批量更新所有分支

当需要更新所有 worktree 分支时：

```bash
cd ~/app/nop-entropy-wt

# 1. 【关键】强制更新远程跟踪分支
git fetch origin +refs/heads/master:refs/remotes/origin/master

# 2. 遍历所有 worktree 并更新
for wt in nop-entropy-*; do
  if [ -d "$wt" ]; then
    echo "=== Updating $wt ==="
    cd "$wt"
    behind=$(git rev-list --count HEAD..origin/master 2>/dev/null || echo "N/A")
    if [ "$behind" != "0" ] && [ "$behind" != "N/A" ]; then
      git stash -u -m "pre-rebase stash" 2>/dev/null
      git rebase origin/master 2>&1 | head -5
      git stash pop 2>/dev/null || true
    fi
    cd ..
  fi
done
```

## 最佳实践

1. **worktree 目录命名**：使用 `<PROJECT_NAME>-<BRANCH_NAME>` 格式（如 `nop-entropy-master`, `nop-entropy-feat-auth`），便于在 IDE 中区分不同项目的 worktree
2. **分支命名**：使用清晰的语义化分支名（小写字母、数字、连字符，20-30 字符）
3. **命令执行位置**：
   - `git worktree` 相关命令：在 `.bare` 目录中执行，或使用 `git -C .bare`
   - git 提交、状态查看等：在对应的 worktree 目录中执行
4. **及时清理**：完成功能后及时删除 worktree
5. **独立环境**：每个 worktree 独立安装依赖
6. **路径使用**：始终使用相对路径（如 `../nop-entropy-feature-xxx`）
7. **特性分支更新**：默认使用 rebase 保留本地修改，只有明确要求时才 reset --hard
8. **⚠️ 远程同步检查**：
   - **永远不要假设 `git fetch origin` 已同步远程**
   - 更新前必须用 `git ls-remote origin refs/heads/master` 验证远程实际状态
   - 使用 `git fetch origin +refs/heads/master:refs/remotes/origin/master` 强制更新
   - 用 `git log --oneline HEAD..origin/master` 确认是否有新提交

## Maven 配置说明

**主分支 vs Feature 分支的 Maven 配置**：
| 分支类型 | Maven 配置 | 说明 |
|---------|-----------|------|
| **主分支 (main/master)** | **不配置** | 使用系统默认仓库（`~/.m2/repository`） |
| **Feature 分支** | **配置** `.mvn/maven.config` | 使用独立局部仓库（`.nop/repository`），避免依赖冲突 |

**Feature 分支 Maven 配置文件格式**：
```bash
-Dmaven.repo.local.head=${maven.multiModuleProjectDirectory}/.nop/repository
-Dmaven.repo.local.tail.ignoreAvailability=true
```

**参数说明**：
- `maven.repo.local.head`: 优先使用当前 worktree 的 `.nop/repository` 目录
- `maven.repo.local.tail.ignoreAvailability=true`: 忽略尾部仓库可用性检查，提高启动速度


## Worktree 提交流程

**流程假设**：
- 项目根目录：`~/app/nop-entropy-wt`
- 项目名称：`nop-entropy`
- feature worktree：`~/app/nop-entropy-wt/nop-entropy-feat-stream-processing`
- 主分支 worktree：`~/app/nop-entropy-wt/nop-entropy-master`

**步骤**：
```bash
# 获取项目名称
PROJECT_NAME="nop-entropy"

# 1. 更新主分支到最新
cd ~/app/nop-entropy-wt/${PROJECT_NAME}-master
git pull origin master

# 2. 软重置 feature 分支到基础分支（保留所有修改，但撤销提交历史）
cd ../${PROJECT_NAME}-feat-stream-processing
git reset --soft master

# 3. 查看修改内容
git diff --cached --stat

# 4. 生成提交信息并提交（遵循约定式提交格式）
git commit -m "feat: 实现流处理数据源接入

支持多种数据源接入：
- Kafka 数据源
- 文件数据源
- HTTP 数据源
- 数据库数据源"

# 5. 生成差异文件（可选）
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
git diff master > "${PROJECT_NAME}-feat-stream-processing-${TIMESTAMP}.diff"

# 6. 合并到主分支
cd ../${PROJECT_NAME}-master
git merge feat-stream-processing --ff-only

# 7. 同步主分支最新内容回 feature 分支
cd ../${PROJECT_NAME}-feat-stream-processing
git rebase master
```

**约定式提交格式**：
| 类型 | 含义 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat: 添加用户登录功能` |
| `fix` | 修复 bug | `fix: 修复登录时的空指针异常` |
| `refactor` | 重构 | `refactor: 优化数据库查询逻辑` |
| `perf` | 性能优化 | `perf: 减少重复的网络请求` |
| `docs` | 文档 | `docs: 更新 API 文档` |
| `test` | 测试 | `test: 添加单元测试覆盖` |
| `chore` | 构建/工具 | `chore: 升级依赖版本` |

## 故障排查

### 常见错误

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `fatal: a branch named 'xxx' already exists` | 分支已存在 | `git -C .bare branch -D xxx` |
| `fatal: '../xxx' already exists` | worktree 目录已存在 | `rm -rf xxx` |
| `Preparing worktree (new branch 'xxx') failed` | 路径格式错误或无效引用 | 参考[清理步骤](#清理步骤) |
| `worktree list` 显示奇怪路径 | 在错误目录执行命令 | 参考[清理步骤](#清理步骤) |
| `Filename too long` | Windows 路径超过 260 字符限制 | 启用长路径支持：`git -C .bare config core.longpaths true` |
| `error: unable to create file: Filename too long` | Windows 路径超过 260 字符限制 | 启用长路径支持：`git -C .bare config core.longpaths true` |
| 分支显示已同步但实际落后远程 | `git fetch` 未更新远程跟踪分支 | 使用强制 fetch：`git fetch origin +refs/heads/master:refs/remotes/origin/master` |

### 远程同步问题诊断

**症状**：`git status` 显示与 origin/master 同步，但实际远程有新提交。

**诊断步骤**：
```bash
# 1. 检查远程实际最新提交
git ls-remote origin refs/heads/master
# 输出: 4ed4a35629860072fec6f7c1c0f16b3aadd2c31b	refs/heads/master

# 2. 检查本地 origin/master
git rev-parse origin/master
# 输出: 813f1552cb826e3ddc72af8bd1fedc9d4d40df66

# 3. 如果两者不同，说明本地跟踪分支过期
```

**解决方案**：
```bash
# 强制更新远程跟踪分支
git fetch origin +refs/heads/master:refs/remotes/origin/master

# 验证更新成功
git rev-parse origin/master
# 应该输出与 git ls-remote 相同的 commit hash
```

### Windows 长路径问题

**问题描述**：
Windows 默认路径限制为 260 字符（MAX_PATH），某些项目（如 Nop 平台）的模板文件路径可能超过此限制。

**解决方案**：

1. **启用 Git 长路径支持**（推荐）：
```bash
cd ~/app/nop-entropy-wt
git -C .bare config core.longpaths true
```

2. **系统级别启用长路径**（可选，需要管理员权限）：
```powershell
# 以管理员身份运行 PowerShell
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" `
    -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force
```

**配置写入位置**：
- Git 长路径配置写入 `.bare/config` 文件：
```ini
[core]
    longpaths = true
```

**适用场景**：
- 创建 worktree 时遇到路径过长错误
- checkout 时遇到 `Filename too long` 错误
- 包含深层嵌套目录的项目

### 清理步骤

遇到路径混乱或无效引用时：
```bash
cd ~/app/nop-entropy-wt

# 获取项目名称（从 remote URL 提取）
PROJECT_NAME=$(git -C .bare config --get remote.origin.url | sed -E 's|.*/([^/]+?)(\.git)?$|\1|')

# 删除 worktree 引用（使用完整的 worktree 目录名）
git -C .bare worktree remove "../${PROJECT_NAME}-xxx" 2>/dev/null || true

# 删除目录
rm -rf "${PROJECT_NAME}-xxx" 2>/dev/null || true

# 清理无效引用
git -C .bare worktree prune

# 删除分支
git -C .bare branch -D xxx

# 重新创建（目录名: PROJECT_NAME-BRANCH_NAME）
git -C .bare worktree add "../${PROJECT_NAME}-xxx" -b xxx
```

### 诊断命令

```bash
cd ~/app/nop-entropy-wt

# 查看 worktree 详细状态
git -C .bare worktree list --porcelain

# 查看所有分支
git -C .bare branch -a
```
