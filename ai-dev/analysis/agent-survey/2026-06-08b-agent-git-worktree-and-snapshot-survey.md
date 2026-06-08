# Agent 框架的 Git Worktree 与文件快照机制调研

> Status: done
> Date: 2026-06-08
> Scope: opencode、Claude Code、cline、agent-zero、oh-my-claudecode 对 git worktree 和文件快照的内部支持
> Conclusion: **opencode 和 cline 都使用了"独立外部 git"方案**——在工作目录外创建独立的 git 仓库（bare repo），将工作目录设为 worktree，独立跟踪所有文件变更。这是目前最成熟的文件快照方案。

---

## 一、核心发现：两种文件快照模式

| 模式 | 做法 | 使用者 |
|------|------|--------|
| **独立外部 git（Shadow Git）** | 在工作目录外创建独立的 bare git 仓库，将工作目录设为 `core.worktree`，定期 `git add` + `git write-tree` 生成快照 | opencode（8.2GB snapshot）、cline（shadow git checkpoints） |
| **项目内 git worktree** | 调用 `git worktree add` 创建独立工作目录，在不同分支上工作 | opencode（ProjectCopy API，git_worktree 策略） |
| **无快照** | 不做文件快照，依赖用户自行 git commit 或无保护 | Claude Code（file-history 仅在 projects/ 级别）、agent-zero（zip 备份）、oh-my-claudecode（无） |

---

## 二、opencode：最完整的快照系统

### 2.1 独立外部 git（Snapshot 模块）

opencode 的 Snapshot 模块（`packages/opencode/src/snapshot/index.ts`，约 400 行）实现了完整的独立外部 git 方案：

**架构**：

```
~/.local/share/opencode/snapshot/
└── {project_id}/
    └── {hash_of_worktree_path}/     # 独立的 bare git 目录
        ├── HEAD
        ├── config
        ├── index
        ├── objects/                  # git 对象存储
        ├── refs/
        └── info/
            └── exclude               # 排除规则（来自项目的 .gitignore）
```

**工作原理**：

1. **初始化**：`git init --git-dir={snapshot_dir} --work-tree={worktree}`
   - `--git-dir` 指向独立于项目 `.git` 的外部目录
   - `--work-tree` 指向用户的工作目录
   - 配置 `core.autocrlf=false`、`core.longpaths=true`、`core.symlinks=true`、`core.fsmonitor=false`

2. **跟踪（track）**：
   - 读取项目的 `.gitignore` 规则（`git check-ignore`），排除不需要跟踪的文件
   - `git add --all --sparse` 将变更文件加入暂存区
   - `git write-tree` 生成一个 tree hash（不创建 commit 对象，只是 tree）
   - 返回 tree hash 作为快照 ID
   - **限制**：单个文件 >2MB 的未跟踪文件不纳入快照

3. **对比（diff）**：
   - `git diff --cached --no-ext-diff {hash} -- .` 获取指定快照与当前状态的差异
   - `diffFull` 方法使用 `git cat-file --batch` 批量读取文件内容，生成完整 diff

4. **恢复（restore）**：
   - `git read-tree {snapshot}` + `git checkout-index -a -f` 恢复到指定快照

5. **回滚（revert）**：
   - 逐文件 `git checkout {hash} -- {file}` 恢复单个文件
   - 支持批量回滚（最多 100 文件/批）
   - 快照中不存在的文件会被删除

6. **清理**：每小时自动 `git gc --prune=7.days`

**实测数据**：

| 维度 | 值 |
|------|-----|
| 快照目录大小 | 8.2 GB |
| 项目数 | 26 个 |
| 单项目最大 | 约 2 GB（nop-entropy 项目） |
| 快照格式 | git tree hash（纯对象存储，无 commit） |

**关键设计决策**：

- **不创建 commit 对象**：使用 `write-tree` 而非 `commit-tree`，只存储 tree hash。这避免了 commit 元数据的开销，每次 track 只产生 tree + blob 对象
- **独立于项目 git**：`--git-dir` 完全独立于项目的 `.git`，不会干扰用户的 git 操作
- **复用项目的 .gitignore**：通过 `git check-ignore --no-index` 检查每个文件，尊重项目的忽略规则
- **文件大小限制**：2MB 以上的未跟踪文件不纳入快照（避免 snapshot 膨胀）
- **信号量锁**：每个 gitdir 一个 `Semaphore(1)`，防止并发操作

### 2.2 项目内 git worktree（ProjectCopy 模块）

opencode 的 `ProjectCopy` 模块是一个完整的 **git worktree 生命周期管理器**，包含三层：策略层（`copy-strategies.ts`）、服务层（`copy.ts`）、API 层（`project-copy.ts`）。

#### 2.2.1 数据模型

```typescript
// project_directory 表：记录每个项目关联的所有工作目录
{
  project_id: string,       // FK → project 表
  directory: string,        // 工作目录绝对路径
  type: "main" | "root" | "git_worktree",  // 目录类型
  time_created: number,
}
// PK: (project_id, directory)
```

- `main`：项目主工作目录
- `root`：项目根目录（可能包含多个 worktree）
- `git_worktree`：通过 `git worktree add` 创建的副本

#### 2.2.2 策略层（copy-strategies.ts）

唯一支持的策略是 `git_worktree`，实现了 4 个操作：

| 操作 | git 命令 | 说明 |
|------|---------|------|
| `create` | `git worktree add --detach {directory} HEAD` | 在指定目录创建 detached HEAD 的 worktree |
| `remove` | `git worktree remove --force {directory}` | 强制删除 worktree |
| `list` | `git worktree list --porcelain` | 列出所有 worktree，排除主仓库 |
| `detect` | 检查 `.git` 是否为文件（非目录） | linked worktree 的 `.git` 是文件而非目录 |

**detect 的巧妙设计**：正常的 git clone 的 `.git` 是目录，而 linked worktree 的 `.git` 是一个指向主仓库 `.git/worktrees/{name}` 的文件。通过检查 `.git` 是文件还是目录，就能判断当前目录是否是 worktree。

#### 2.2.3 服务层（copy.ts）

`ProjectCopy` 服务提供完整的 worktree 生命周期管理：

```
create(sourceDirectory, directory, name?, context?)
  │
  ├── 1. 验证 sourceDirectory 存在于 project_directory 表
  ├── 2. 生成唯一目录名（name 或随机 slug，冲突时加后缀 -2, -3...最多 10 次）
  ├── 3. 调用 strategy.create() → git worktree add --detach {dir} HEAD
  ├── 4. INSERT 到 project_directory 表（type=git_worktree）
  └── 5. 发布 event: project.directories.updated

remove(directory)
  │
  ├── 1. detect 策略类型
  ├── 2. 调用 strategy.remove() → git worktree remove --force {dir}
  ├── 3. DELETE 从 project_directory 表
  └── 4. 发布 event: project.directories.updated

refresh(projectID)
  │
  ├── 1. 查出所有 main/root 类型的目录
  ├── 2. 对每个目录，调用 strategy.list() 发现已有的 worktree
  ├── 3. 对比 project_directory 表，INSERT 新发现的 / DELETE 已不存在的
  └── 4. 如有变化，发布 event: project.directories.updated
```

#### 2.2.4 API 层（project-copy.ts）

通过 HTTP API 暴露，路径前缀 `/experimental/project/:projectID/copy`：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/experimental/project/:projectID/copy` | POST | 创建 worktree 副本 |
| `/experimental/project/:projectID/copy` | DELETE | 删除 worktree 副本 |
| `/experimental/project/:projectID/copy/refresh` | POST | 重新发现所有 worktree |

所有端点标记为 `experimental`，需要 `Authorization` + `WorkspaceRouting` + `InstanceContext` 中间件。

#### 2.2.5 与 Snapshot 的区别

| 维度 | Snapshot（独立外部 git） | ProjectCopy（git worktree） |
|------|------------------------|--------------------------|
| 目的 | 文件快照/回滚/undo | 创建独立工作副本 |
| 存储位置 | `~/.local/share/opencode/snapshot/` | 用户指定的目录 |
| git 仓库 | 独立 bare repo（与项目 git 无关） | 共享项目的 `.git`（linked worktree） |
| 对项目 git 的影响 | 无影响 | 在项目 git 中可见（`git worktree list`） |
| 文件隔离 | 不隔离（直接在工作目录上快照） | 完全隔离（独立目录，独立文件系统） |
| 分支关系 | 无分支（纯 tree hash） | `--detach HEAD`（无分支，detached HEAD） |
| 恢复方式 | `git checkout {hash} -- {file}` | `git worktree remove`（删除整个 worktree） |
| 调用者 | Agent 自动调用（每次 turn） | 用户/API 手动调用 |
| 数据库记录 | session_message（type=snapshot） | project_directory 表（type=git_worktree） |

#### 2.2.6 设计亮点

1. **策略模式**：`StrategyID` 目前只有 `git_worktree`，但接口设计支持扩展其他策略（如 filesystem copy、rsync 等）
2. **自动发现（refresh）**：不需要手动注册，`refresh` 操作会扫描所有 main/root 目录的 worktree 并同步到 project_directory 表
3. **目录名冲突处理**：自动加后缀 `-2`, `-3`...，最多尝试 10 次
4. **事件驱动**：`project.directories.updated` 事件，UI 可以实时响应 worktree 变化
5. **级联删除**：`project_directory` 表的 `onDelete: cascade`，删除 project 时自动清理所有 worktree 记录

---

## 三、cline：Shadow Git Checkpoints

cline 的 Checkpoints 系统（`src/integrations/checkpoints/`）使用了几乎相同的独立外部 git 方案：

### 3.1 Shadow Git 架构

```
{VSCode globalStorage}/cline/checkpoints/{cwdHash}/.git/
├── HEAD
├── config
│   └── core.worktree = {workspace_path}  # 指向工作目录
├── objects/
└── refs/
```

### 3.2 工作原理

1. **初始化**（`initShadowGit`）：
   - 在 `globalStorage/cline/checkpoints/{cwdHash}/` 创建独立 git 仓库
   - `git init` + `git config core.worktree {workspace_path}`
   - `git config commit.gpgSign false`
   - `git config user.name "Cline Checkpoint"` + `user.email "checkpoint@cline.bot"`
   - 初始 `git commit --allow-empty`

2. **创建 checkpoint**：
   - `git add --all` 添加所有变更文件
   - `git commit` 创建 checkpoint（与 opencode 不同，cline 创建完整 commit）
   - 处理嵌套 git 仓库：临时 `.git` → `.git_disabled` 重命名，add 完成后恢复

3. **恢复 checkpoint**：
   - 读取指定 checkpoint 的 commit
   - 计算差异并恢复文件
   - 支持 "恢复到指定点" 和 "仅恢复文件" 两种模式

### 3.3 与 opencode Snapshot 的对比

| 维度 | opencode Snapshot | cline Shadow Git |
|------|-----------------|-----------------|
| 存储 tree vs commit | **tree hash**（write-tree，无 commit） | **完整 commit**（git commit） |
| 嵌套 git 处理 | `git check-ignore` 排除 | `.git` → `.git_disabled` 临时重命名 |
| 文件大小限制 | 2MB 以上排除 | 无明确限制 |
| 清理策略 | `git gc --prune=7.days` 每小时 | 无自动清理 |
| 并发控制 | Semaphore 锁 | CheckpointLockUtils |
| LFS 支持 | 无 | 有（`getLfsPatterns` + `.gitattributes`） |
| 初始化成本 | 低（write-tree 无需 commit） | 中（需要初始 commit + add 所有文件） |
| git 库 | 原生 `git` CLI | `simple-git` npm 库 |

---

## 四、Claude Code：file-history 版本文件（非 git）

Claude Code 的文件快照方案**不使用 git**：

```
~/.claude/file-history/{session-id}/
└── {content_hash}@v{N}    # 文件内容快照
    例如: 07ad2d1d37f1ea0d@v1
          07ad2d1d37f1ea0d@v2
          07ad2d1d37f1ea0d@v3
```

- 每次文件变更前，保存当前文件内容到 `{hash}@v{N}`
- 文件名 = 内容 hash + 版本号
- 简单但功能有限：只能按版本号恢复，无 diff 能力
- 在 projects/ 的 JSONL 中有 `file-history-snapshot` 消息类型记录快照指针

**实测**：仅 1.3MB（6 个会话），使用率很低。

---

## 五、agent-zero：Zip 备份（非 git）

agent-zero 的 `BackupService`（`helpers/backup.py`）使用 zip 文件备份：

```python
# 备份范围：仅 agent-zero 自身的 usr/ 目录
f"{agent_root}/usr/**"
```

- 与项目代码无关，只备份 agent-zero 的用户数据
- 输出为 zip 文件
- 不涉及 git worktree 或文件快照

---

## 六、oh-my-claudecode：无文件快照

oh-my-claudecode 没有文件快照机制。它依赖 Claude Code 原生的文件操作能力。

---

## 七、对比与评判

### 7.1 独立外部 git 方案（Shadow Git）的优劣

**opencode 和 cline 都采用了这个方案**，说明它是目前最成熟的文件快照方案。

#### 优点

| 优点 | 说明 |
|------|------|
| **零侵入** | 不修改项目的 `.git`，不创建 commit，不影响用户的 git 历史 |
| **完整快照** | 利用 git 的对象存储（blob/tree），天然去重（相同内容只存一次） |
| **高效 diff** | `git diff` 直接比较快照，无需自己实现 diff 算法 |
| **选择性恢复** | `git checkout {hash} -- {file}` 可以恢复单个文件 |
| **增量存储** | git 的对象存储是增量的，只存储变更部分 |
| **自动 gc** | `git gc --prune=7.days` 自动清理过期快照 |

#### 缺点

| 缺点 | 说明 |
|------|------|
| **依赖 git CLI** | 需要系统安装 git，跨平台兼容性受限 |
| **快照体积** | opencode 实测 8.2GB snapshot（比 transcripts 1.0GB 大 8 倍） |
| **初始化成本** | 首次 `git add --all` 需要扫描整个工作目录，大项目可能很慢 |
| **并发问题** | 多个 agent 同时修改文件时，snapshot 可能不一致 |
| **2MB 文件限制** | opencode 排除 2MB 以上的未跟踪文件，可能丢失大文件 |

### 7.2 对比其他方案

| 方案 | 使用者 | 功能 | 复杂度 | 适用场景 |
|------|--------|------|--------|---------|
| **独立外部 git** | opencode、cline | ★★★★★ 完整快照+diff+恢复 | ★★★ | 需要 undo/redo 的 IDE |
| **git worktree** | opencode（ProjectCopy） | ★★★★☆ 并行分支 | ★★★★ | 多 agent 并行工作 |
| **文件版本文件** | Claude Code | ★★☆☆☆ 简单版本 | ★ | 轻量级回滚 |
| **zip 备份** | agent-zero | ★☆☆☆☆ 全量备份 | ★★ | 系统备份 |
| **无快照** | oh-my-claudecode | ☆☆☆☆☆ 无 | - | 依赖外部工具 |

### 7.3 "独立外部 git"方案的详细工作流

```
项目目录（用户的 git 仓库）
├── .git/                        ← 用户的 git（不受影响）
├── src/
├── pom.xml
└── ...

独立外部 git（~/.local/share/opencode/snapshot/{project}/{hash}/）
├── HEAD                         ← 指向当前快照
├── objects/                     ← git 对象存储（去重的文件内容）
│   ├── pack/                    ← 压缩存储
│   └── {hash}                   ← 松散对象
├── index                        ← 暂存区
└── info/
    └── exclude                  ← 从项目的 .gitignore 复制的排除规则

工作流：
1. Agent 开始工作 → snapshot.track() → 生成 tree hash S0
2. Agent 编辑文件 A → snapshot.track() → 生成 tree hash S1
3. Agent 编辑文件 B → snapshot.track() → 生成 tree hash S2
4. Agent 出错 → snapshot.diff(S0) → 看到 A 和 B 的变更
5. Agent 回滚 → snapshot.revert(S0 的 patches) → A 和 B 恢复到原始内容
```

---

## 八、对 Nop 的设计建议

### 8.1 建议采用独立外部 git 方案

Nop 应该采用 opencode 的独立外部 git 方案，原因：

| 理由 | 说明 |
|------|------|
| **已验证** | opencode 和 cline 都采用，经过大量实测验证 |
| **零侵入** | 不修改项目 git，符合 Nop 的"不干预用户仓库"原则 |
| **完整能力** | 快照/diff/恢复/单文件回滚，能力最完整 |
| **增量存储** | git 对象存储天然去重，Nop 用 MySQL/PG 不需要担心快照体积 |
| **Nop ORM 集成** | 可以将 git tree hash 存入 session 表的 `snapshot_hash` 字段 |

### 8.2 需要改进的地方

| opencode 的问题 | Nop 的改进 |
|----------------|----------|
| 依赖 git CLI | Nop 可以用 JGit（纯 Java git 实现），不依赖系统 git |
| 2MB 文件限制 | Nop 作为企业平台，不应限制文件大小 |
| 无 worktree 隔离 | Nop 可以为子 agent 创建独立 worktree（参考 opencode 的 ProjectCopy） |
| snapshot 体积 8.2GB | Nop 用外部数据库 + 定期归档，不需要担心体积 |
| 无快照元数据 | 在 session_message 中记录快照事件（`snapshot.track` → `type=snapshot` 消息） |

### 8.3 Nop 的快照架构建议

```
Nop Snapshot Service（基于 JGit）

1. 初始化：
   - 在 {data_dir}/ai-snapshot/{project_id}/ 创建 bare git 仓库
   - 设置 worktree = 项目目录
   - 读取项目的 .gitignore 作为排除规则

2. 跟踪：
   - 每次 agent turn 开始前调用 track()
   - git add + write-tree → 返回 tree hash
   - tree hash 存入 ai_session_message（type=snapshot）
   - 不创建 commit（与 opencode 一致）

3. 对比：
   - git diff {old_hash} {new_hash} → 变更文件列表 + diff
   - 用于 UI 展示 agent 的文件变更

4. 恢复：
   - git checkout {hash} -- {file} → 恢复单个文件
   - 或 git read-tree + checkout-index → 恢复整个快照

5. 子 agent 隔离（可选）：
   - git worktree add → 创建独立工作目录
   - 子 agent 在独立 worktree 中工作
   - 完成后合并或丢弃
```

### 8.4 是否需要 git worktree 隔离？

| 场景 | 是否需要 worktree | 说明 |
|------|-----------------|------|
| 单 agent 顺序工作 | 不需要 | 直接在工作目录操作 + snapshot 保护 |
| 多 agent 并行修改同一文件 | **需要** | worktree 提供文件系统级隔离 |
| 子 agent 只读探索 | 不需要 | 只读不需要隔离 |
| 子 agent 修改不同文件 | 不需要（但推荐） | 不冲突但可能有逻辑依赖 |

**建议**：Layer 2 实现 snapshot（必须），Layer 4 实现 worktree 隔离（可选）。

---

## References

- `~/ai/opencode/packages/opencode/src/snapshot/index.ts` — opencode 独立外部 git 快照（约 400 行）
- `~/ai/opencode/packages/core/src/project/copy.ts` — opencode ProjectCopy 模块
- `~/ai/opencode/packages/core/src/project/copy-strategies.ts` — opencode git_worktree 策略
- `~/ai/opencode/packages/core/src/git.ts` — opencode Git 接口（find/clone/worktree/patch/reset）
- `~/ai/opencode/packages/core/src/file-mutation.ts` — opencode 文件变更锁和验证
- `~/ai/cline/apps/vscode/src/integrations/checkpoints/CheckpointGitOperations.ts` — cline shadow git
- `~/ai/cline/apps/vscode/src/integrations/checkpoints/CheckpointTracker.ts` — cline checkpoint 跟踪
- `~/.claude/file-history/` — Claude Code 文件版本快照
- `~/.local/share/opencode/snapshot/` — opencode 实测快照数据（8.2GB，26 个项目）
- `~/ai/agent-zero/helpers/backup.py` — agent-zero zip 备份

> **注**：`~/ai/` 下的文件是调研时在宿主机器上的源码位置，不在 nop-entropy 仓库内。
