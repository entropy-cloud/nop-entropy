---
name: nop-git-worktree
description: 涉及git worktree的操作优先使用这个skill。管理 Git bare 仓库和多个并行 worktree 的开发环境。支持初始化 bare 仓库结构、自动生成分支名并创建 feature worktree。
---

## 前置依赖

**本 skill 仅覆盖 worktree 生命周期管理**（初始化、创建、删除 worktree）。
所有通用 Git 操作（commit、sync、rebase、历史搜索）由 `nop-git-master` skill 处理。

当用户请求涉及 worktree 内的通用 git 操作时，应同时加载 `nop-git-master`。

## 职责边界

| 操作 | 归属 skill |
|------|-----------|
| 初始化 bare + worktree 项目结构 | **本 skill** |
| 创建/删除 feature worktree | **本 skill** |
| 在 worktree 中提交代码 | **nop-git-master** (COMMIT) |
| 在 worktree 中同步远程更新 | **nop-git-master** (SYNC) |
| 在 worktree 中 rebase/squash | **nop-git-master** (REBASE) |
| 历史搜索 (log/blame/bisect) | **nop-git-master** (HISTORY_SEARCH) |
| 合并 Feature 分支回主分支（编排流程） | **本 skill**（但各步骤内的 git 操作委托给 nop-git-master） |

---

# 重要提示

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

---

# 目录结构

```
project-root/                          # 项目根目录（如 ~/app/nop-entropy-wt）
├── .bare/                            # Git bare 仓库（中心），所有 git worktree 操作在此执行
│   ├── HEAD
│   ├── config
│   ├── objects/
│   ├── refs/
│   └── ...
├── nop-entropy-master/               # 主分支 worktree（开发时在此目录），目录名=项目名-分支名
│   └── ...
├── nop-entropy-feat-auth/            # 特性分支 worktree，目录名=项目名-分支名
│   ├── .mvn/
│   │   └── maven.config
│   └── ...
└── nop-entropy-fix-login/            # 修复分支 worktree，目录名=项目名-分支名
    ├── .mvn/
    │   └── maven.config
    └── ...
```

**命名规则**：worktree 目录名 = `<PROJECT_NAME>-<BRANCH_NAME>`，便于在 IDE 中区分。

---

# 操作：初始化 bare + worktree 项目结构

**参数**：
- **项目根目录** (必填): 如 `~/app/nop-entropy-wt`
- **远程仓库 URL** (可选): 如 `https://gitee.com/user/repo.git`
- **本地仓库路径** (可选): 现有 git 仓库的路径

```bash
cd ~/app/nop-entropy-wt

# 1. 创建 bare 仓库
git clone --bare https://gitee.com/canonical-entropy/nop-entropy.git .bare
# 或从本地克隆: git clone --bare ~/app/nop-entropy .bare

# 2. 启用长路径支持（Windows 平台重要配置）
git -C .bare config core.longpaths true

# 3. 识别项目名称（从 remote URL 提取）
PROJECT_NAME=$(git -C .bare config --get remote.origin.url | sed -E 's|.*/([^/]+?)(\.git)?$|\1|')

# 4. 识别默认分支
DEFAULT_BRANCH=$(cat .bare/HEAD 2>/dev/null | sed 's@^refs/heads/@@')
if [ -z "$DEFAULT_BRANCH" ]; then
    DEFAULT_BRANCH=$(git -C .bare symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@')
fi

# 5. 创建主分支 worktree（目录名: PROJECT_NAME-BRANCH_NAME）
git -C .bare worktree add "../${PROJECT_NAME}-${DEFAULT_BRANCH}" "$DEFAULT_BRANCH"

# 6. 验证
git -C .bare worktree list
```

---

# 操作：创建新的 feature worktree

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

```bash
cd ~/app/nop-entropy-wt

PROJECT_NAME=$(git -C .bare config --get remote.origin.url | sed -E 's|.*/([^/]+?)(\.git)?$|\1|')

BRANCH_NAME="refactor-job-scheduling"
WORKTREE_NAME="${PROJECT_NAME}-${BRANCH_NAME}"

# 1. 如果分支已存在，先清理
if git -C .bare branch --list "$BRANCH_NAME" > /dev/null; then
    git -C .bare worktree remove "../$WORKTREE_NAME" 2>/dev/null || true
    rm -rf "$WORKTREE_NAME" 2>/dev/null || true
    git -C .bare worktree prune
    git -C .bare branch -D "$BRANCH_NAME"
fi

# 2. 创建 worktree
git -C .bare worktree add "../$WORKTREE_NAME" -b "$BRANCH_NAME"

# 3. 创建 Maven 配置（Feature 分支专用）
mkdir -p "$WORKTREE_NAME/.mvn"
WORKTREE_ABS_PATH="$(cd "$WORKTREE_NAME" && pwd)"
cat > "$WORKTREE_NAME/.mvn/maven.config" <<EOF
-Dmaven.repo.local.head=$WORKTREE_ABS_PATH/.nop/repository
-Dmaven.repo.local.tail.ignoreAvailability=true
EOF

# 4. 验证
git -C .bare worktree list
```

---

# 操作：合并 Feature 分支回主分支

当 feature 分支开发完成后，将提交合并回主分支。

**⚠️ 本流程步骤 1 和 4 需要 `nop-git-master` skill 已加载。**

**前置**：本流程假设 feature 分支有多个中间提交，需要软重置后重新组织提交。

```bash
PROJECT_NAME="nop-entropy"

# 检测默认分支名（与 nop-git-master ENVIRONMENT DETECTION 一致）
DEFAULT_BRANCH=$(git -C ../.bare symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@')
if [ -z "$DEFAULT_BRANCH" ]; then
  DEFAULT_BRANCH=$(git rev-parse --verify main 2>/dev/null && echo main || echo master)
fi

# 1. 更新主分支到最新
#    ⚠️ 使用 nop-git-master SYNC MODE 完整流程（ls-remote 检查 + refspec fetch + pull --ff-only）
#    不要在此内嵌简化版 SYNC 命令
cd ~/app/nop-entropy-wt/${PROJECT_NAME}-${DEFAULT_BRANCH}
# → 执行 nop-git-master SYNC MODE

# 2. 软重置 feature 分支到主分支（保留所有修改，撤销提交历史）
cd ../${PROJECT_NAME}-feat-xxx
git reset --soft "$DEFAULT_BRANCH"

# 3. 查看修改内容
git diff --cached --stat

# 4. 按 nop-git-master COMMIT MODE 生成提交信息并提交
# → 执行 nop-git-master COMMIT MODE

# 5. 合并到主分支
cd ../${PROJECT_NAME}-${DEFAULT_BRANCH}
git merge feat-xxx --ff-only

# 6. 同步主分支最新内容回 feature 分支
#    ⚠️ 使用 nop-git-master REBASE MODE 完整流程（安全评估 + refspec fetch + rebase）
cd ../${PROJECT_NAME}-feat-xxx
# → 执行 nop-git-master REBASE MODE（rebase onto）
```

---

# 操作：删除 feature worktree

**⚠️ 删除前必须检查是否有未保存的工作：**

```bash
cd ~/app/nop-entropy-wt

PROJECT_NAME=$(git -C .bare config --get remote.origin.url | sed -E 's|.*/([^/]+?)(\.git)?$|\1|')
BRANCH_NAME="feat-xxx"
WORKTREE_NAME="${PROJECT_NAME}-${BRANCH_NAME}"

# 1. 安全检查：确认没有未保存的工作
cd "$WORKTREE_NAME"
UNCOMMITTED=$(git status --porcelain 2>/dev/null)
UNPUSHED=$(git log --oneline "origin/$BRANCH_NAME..HEAD" 2>/dev/null)
if [ -n "$UNCOMMITTED" ] || [ -n "$UNPUSHED" ]; then
  echo "⚠️ 工作树有未保存/未推送的更改："
  [ -n "$UNCOMMITTED" ] && echo "  未提交: $(echo "$UNCOMMITTED" | wc -l) 个文件"
  [ -n "$UNPUSHED" ] && echo "  未推送: $(echo "$UNPUSHED" | head -5)"
  echo "请先处理后再删除。如确认丢弃，请明确告知。"
  exit 1
fi
cd ..

# 2. 删除 worktree
git -C .bare worktree remove "../$WORKTREE_NAME" 2>/dev/null || true

# 3. 清理目录和引用
rm -rf "$WORKTREE_NAME" 2>/dev/null || true
git -C .bare worktree prune

# 4. 删除分支
git -C .bare branch -D "$BRANCH_NAME"
```

---

# Maven 配置说明

| 分支类型 | Maven 配置 | 说明 |
|---------|-----------|------|
| **主分支 (main/master)** | **不配置** | 使用系统默认仓库（`~/.m2/repository`） |
| **Feature 分支** | **配置** `.mvn/maven.config` | 使用独立局部仓库（`.nop/repository`），避免依赖冲突 |

```bash
# Feature 分支 Maven 配置
WORKTREE_ABS_PATH="$(cd "nop-entropy-feat-auth" && pwd)"
cat > "nop-entropy-feat-auth/.mvn/maven.config" <<EOF
-Dmaven.repo.local.head=$WORKTREE_ABS_PATH/.nop/repository
-Dmaven.repo.local.tail.ignoreAvailability=true
EOF
```

---

# 故障排查

## 常见错误

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `fatal: a branch named 'xxx' already exists` | 分支已存在 | `git -C .bare branch -D xxx` |
| `fatal: '../xxx' already exists` | worktree 目录已存在 | `rm -rf xxx` |
| `Preparing worktree (new branch 'xxx') failed` | 路径格式错误或无效引用 | 参考清理步骤 |
| `Filename too long` | Windows 路径超过 260 字符 | `git -C .bare config core.longpaths true` |

## 清理步骤

```bash
cd ~/app/nop-entropy-wt
PROJECT_NAME=$(git -C .bare config --get remote.origin.url | sed -E 's|.*/([^/]+?)(\.git)?$|\1|')

git -C .bare worktree remove "../${PROJECT_NAME}-xxx" 2>/dev/null || true
rm -rf "${PROJECT_NAME}-xxx" 2>/dev/null || true
git -C .bare worktree prune
git -C .bare branch -D xxx
```

## Windows 长路径问题

```bash
cd ~/app/nop-entropy-wt
git -C .bare config core.longpaths true
```
