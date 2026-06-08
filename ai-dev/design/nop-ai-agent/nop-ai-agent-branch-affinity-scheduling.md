# Nop AI Agent 分支亲和调度、工作空间隔离与文件快照

**日期**：2026-06-08（更新于 2026-06-08，审计修订 v2）
**范围**：`nop-ai-agent` 调度模型、工作空间管理、子 Agent 隔离、文件快照
**状态**：active
**审计**：2026-06-08 三轮独立审计（JGit 可行性、交叉比对、Edge Case），交叉比对无异议，其余问题已修订

---

## 一、设计结论

1. **Git Branch 作为调度亲和单元**：一个活跃分支同时只在一个节点的一个进程内操作，所有文件/git/编译操作在单进程多线程模型中完成
2. **Agent 是分布式开发者**：多个 Agent 可操作同一项目的不同分支，提交时做冲突合并，与人类的 feature branch 工作流一致
3. **双环境共存**：单个 Agent session 可同时操作 Java 和 Node.js 两种工具链（同项目前后端）
4. **子 Agent 按需隔离**：默认共享父 Agent 工作目录；探索性任务可选择 git worktree 隔离，生命周期与任务绑定
5. **独立外部 git 文件快照**：基于 JGit 的独立 git 目录（separated git dir）快照（零侵入、增量去重、选择性恢复），与项目 git 完全隔离
6. **Worktree 用隔离，Snapshot 用保护**：两个机制解决不同问题——worktree 解决并行隔离，snapshot 解决 undo/回滚
7. **混合实现策略**：Snapshot 用 JGit（纯 Java），Worktree 用 git CLI（JGit 不支持 worktree 操作）

---

## 二、背景与动机

### 2.1 使用场景

`nop-ai-agent` 面向自动化代码开发，支持：

- **Java 后端开发**：Maven 构建、代码生成、ORM 模型、IoC 配置
- **Node.js 前端/工具开发**：npm 构建、前端组件、脚本工具
- **同项目双环境**：一个 Agent session 可能同时修改 Java 后端代码和 Node.js 前端代码

### 2.2 问题

在分布式部署中，Agent 的操作涉及两类资源：

| 资源类型 | 分布式特性 | 示例 |
|---------|----------|------|
| **LLM API 调用** | 无状态、天然可分布式 | 调用 GPT/Claude API |
| **文件/git/编译操作** | 本地资源、不可分布式 | 读写文件、git commit、`mvn install` |

如果同一个 git repo 的文件操作分散到多个节点：

- git lock 冲突几乎无解（`.git/index.lock` 是进程级文件锁）
- 编译缓存（`target/`、`node_modules/`）无法跨节点共享
- 文件一致性无法保证

### 2.3 核心洞察

Agent 的文件操作和人类开发者的行为完全同构——都是"检出分支 → 修改文件 → 提交 → 合并"。因此应该**直接复用 git 的分布式协作模型**，而不是发明新的分布式文件协调协议。

---

## 三、核心设计

### 3.1 调度模型：Branch Affinity Scheduling

#### 3.1.1 调度单元

**调度单元 = (project, branch)**

一个活跃的 (project, branch) 组合同时只在一个节点的一个进程内操作。

```
┌─────────────────────────────────────────────────┐
│              Agent Cluster                       │
│                                                  │
│  Node A (JVM Process)          Node B (JVM)      │
│  ┌───────────────────────┐    ┌──────────────┐   │
│  │ Agent Session S1      │    │ Session S3   │   │
│  │ project=P, branch=feat│    │ P, branch=bar│   │
│  │ ┌─────┐ ┌─────┐      │    │              │   │
│  │ │VT-1 │ │VT-2 │      │    │              │   │
│  │ │(LLM)│ │(tool│      │    │              │   │
│  │ └─────┘ └─────┘      │    └──────────────┘   │
│  └───────────────────────┘                       │
│                                                  │
│  Node A: feat 分支的所有操作都在此进程内完成      │
│  Node B: bar 分支的所有操作都在此进程内完成      │
│                                                  │
│  S2 (project=P, branch=feat) → 排队等待或路由到 Node A
└─────────────────────────────────────────────────┘
```

#### 3.1.2 调度规则

| 规则 | 说明 |
|------|------|
| **强亲和**：同一 (project, branch) 同时只有一个活跃 session | git 工作目录是独占的（`HEAD` 只有一个） |
| **弱亲和**：同一 (project, branch) 倾向于路由到同一节点 | 减少工作目录切换、缓存预热成本 |
| **跨分支并行**：同一 project 的不同分支可在不同节点 | 和人类分布式开发一致 |

#### 3.1.3 调度器行为

```
新 Session 请求 (project=P, branch=feat)
│
├── 1. 查询 Registry：是否有活跃 session 占用 (P, feat)?
│   ├── 有 → 排队等待（或拒绝，由配置决定）
│   └── 无 → 继续
│
├── 2. 查询 Affinity Table：(P, feat) 上次绑定到哪个节点?
│   ├── 有记录且节点健康 → 路由到该节点
│   └── 无记录或节点不可用 → 选最优节点（负载最低/延迟最低）
│
├── 3. 目标节点检查：工作目录是否就绪?
│   ├── feat 分支已检出 → 直接启动
│   ├── 其他分支已检出 → stash/save + 切换到 feat 分支
│   ├── 无工作目录 → clone + checkout feat
│   └── 切换失败（有未提交修改无法 stash）→ 拒绝请求，提示先完成当前工作
│
└── 4. 注册 Affinity：(P, feat) → Node X, 启动 Agent Session
```

### 3.2 工作空间模型

#### 3.2.1 项目工作空间

每个 Agent session 绑定一个**项目工作空间**（Working Directory）。工作空间的生命周期：

```
Session 创建 → 工作空间就绪 → Agent 执行 → Session 结束 → 工作空间保留/清理
```

**工作空间内容**：

```
{workspace-root}/
├── .git/                    # git 仓库
├── src/                     # 项目源码
├── target/                  # Java 构建产物
├── node_modules/            # Node.js 依赖
├── pom.xml                  # Java 项目配置
├── package.json             # Node.js 项目配置
└── ...
```

子 Agent 的 worktree 存储在工作空间**外部**（见 §3.3.4），不在 `{workspace-root}/` 下创建。

#### 3.2.2 双环境共存

单个 Agent session 可同时操作 Java 和 Node.js 工具链：

| 操作 | 执行方式 | 隔离 |
|------|---------|------|
| `mvn clean install` | `ProcessBuilder` 调用系统 `mvn` | 需要 `JAVA_HOME` 配置正确 |
| `npm run build` | `ProcessBuilder` 调用系统 `npm` | 需要 `NODE_HOME` / `PATH` 配置正确 |
| `./mvnw test` | `ProcessBuilder` 调用项目自带 wrapper | 自动使用项目配置的 JDK |
| 文件读写 | Java NIO | 进程内线程安全 |

**环境探测**：Agent session 启动时，自动探测工作空间中的环境：

```
workspace 探测
├── 存在 pom.xml → Java 环境：检测 JAVA_HOME、mvn/mvnw
├── 存在 package.json → Node.js 环境：检测 node、npm/pnpm/yarn
├── 两者都存在 → 双环境模式：同时启用两种工具链
└── 两者都不存在 → 纯文件操作模式
```

**工具链配置**：通过 Agent 配置或项目级配置文件指定环境路径，支持 Delta 定制。

### 3.3 文件快照系统（Snapshot）

#### 3.3.1 独立外部 git 方案

采用 opencode 和 cline 验证过的**独立外部 git（Shadow Git）**方案：

- 在工作目录外创建独立的 git 目录（non-bare repo with separated git dir），`gitDir` 和 `workTree` 分离
- 将工作目录设为 `core.worktree`
- 定期 `git add` + `git write-tree` 生成快照（tree hash，不创建 commit）
- 与项目的 `.git` 完全隔离，零侵入

**不使用项目 git 做快照的理由**：项目 git 是用户的资产，Agent 不应通过快照操作污染用户的 commit 历史、reflog 或 index。

#### 3.3.2 架构

```
项目目录（用户的 git 仓库，不受影响）
├── .git/                        ← 用户的 git
├── src/
├── pom.xml
└── ...

独立外部 git（{data_dir}/ai-snapshot/{project_id}/{hash}/）
├── HEAD
├── config                       ← core.worktree = {workspace-root}
├── index
├── objects/                     ← git 对象存储（去重）
├── refs/
└── info/
    └── exclude                  ← 从项目的 .gitignore 复制
```

**实现方式**：基于 JGit（纯 Java git 实现），不依赖系统 git CLI。

**初始化方式**：使用 `Git.init().setGitDir(snapshotDir).setDirectory(workDir)` 创建 non-bare repo with separated git dir（不是 bare repo + worktree）。初始化时设置关键配置：`core.autocrlf=false`、`core.longpaths=true`、`core.symlinks=true`、`core.fsmonitor=false`。

**Symlink 安全**：Snapshot 的 `git add` 操作必须**不跟随 symlink**（JGit `FollowSymlinks=false`），防止 symlink 的大目录（target/、node_modules/ 等）被 snapshot 跟踪导致体积暴涨和 restore 误破坏主工作目录文件。

#### 3.3.3 快照操作

> 以下用 git CLI 语法描述概念性操作，实际通过 JGit API 实现。

| 操作 | 说明 |
|------|------|
| **track** | 每次 agent turn 开始前调用。`git add --all` + `git write-tree` → 返回 tree hash。存入 session_message（type=snapshot） |
| **diff** | `git diff {old_hash} {new_hash}` → 变更文件列表 + diff 内容。用于 UI 展示 |
| **restore** | `git read-tree {hash}` + `git checkout-index -a -f` → 恢复整个快照 |
| **revert** | `git checkout {hash} -- {file}` → 恢复单个文件。支持批量回滚 |
| **gc** | 定期 `git gc --prune=7.days` 清理过期对象 |

#### 3.3.4 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| tree hash vs commit | **tree hash**（write-tree，无 commit） | opencode 验证：避免 commit 元数据开销，每次 track 只产生 tree + blob 对象 |
| 存储位置 | `{data_dir}/ai-snapshot/{project_id}/{hash}/` | 独立于项目目录，不影响用户 git |
| 文件大小限制 | 无限制（与 opencode 的 2MB 限制不同） | Nop 是企业平台，不应跳过大文件；JGit + 增量存储可控制体积 |
| 并发控制 | 每个 snapshot 仓库一个 `ReentrantLock`（支持 tryLock 超时防死锁） | 防止并发 track/diff/restore 操作冲突 |
| 排除规则 | 复用**当前工作目录**（主工作空间或 worktree）的 `.gitignore` | 尊重用户配置，不纳入 `target/`、`node_modules/` 等 |
| Symlink 处理 | 不跟随 symlink（`FollowSymlinks=false`） | 避免 symlink 的大目录被跟踪导致体积暴涨 |
| 快照触发时机 | 每次 agent turn 开始前自动 track | 与 opencode 一致，保证每步都有回滚点 |
| 启动时清理 | 清理残留的 `index.lock` 文件 | 防止 JVM 崩溃后遗留的锁文件阻塞后续操作 |

#### 3.3.5 快照与数据库的集成

快照元数据存入 `ai_session_message` 表：

| 字段 | 说明 |
|------|------|
| `sessionId` | 所属 session |
| `type` | `snapshot` |
| `snapshotHash` | tree hash（快照 ID） |
| `snapshotStats` | JSON：文件数、变更文件列表、总大小 |
| `createdAt` | 快照时间 |

数据库记录用于 UI 展示和查询。实际文件内容存储在 JGit 对象库中，不在数据库中存 blob。

### 3.4 子 Agent 隔离模型

#### 3.4.1 两种模式

`call-agent` 工具支持 `workspace` 参数选择隔离模式：

| 模式 | 参数 | 工作目录 | 适用场景 | 生命周期 |
|------|------|---------|---------|---------|
| **共享** | `workspace=shared`（默认） | 与父 Agent 共享主工作目录 | 修改业务逻辑、执行构建 | 随父 Agent |

> **Shared 模式并发约束**：shared 模式下，子 Agent 的 git 操作和文件写操作必须通过父 Agent 的 `ReentrantLock` 序列化，防止 `.git/index.lock` 冲突和文件写覆盖。子 Agent 共享父 Agent 的 `FileOperationLock`。
| **Worktree 隔离** | `workspace=worktree` | 独立 git worktree | 探索性任务、并行实验 | 随子 Agent 任务 |

#### 3.4.2 Worktree 隔离

```
父 Agent Session (branch=feature-foo, node=A, process=P)
│
├── call-agent(task=修改业务逻辑, workspace=shared)
│   └── 同进程 P，共享工作目录，Virtual Thread 隔离
│
├── call-agent(task=探索方案A, workspace=worktree)
│   ├── 创建 worktree（见 §3.4.3 创建流程）
│   ├── 同进程 P，独立 Virtual Thread
│   ├── 探索完毕后：
│   │   ├── 有价值的修改 → git commit + cherry-pick 到主分支
│   │   └── 无价值 → 清理 worktree
│   └── worktree 生命周期 = 子 Agent 任务生命周期
│
└── call-agent(task=并行实验B, workspace=worktree)
    ├── 创建 worktree
    └── 同上
```

#### 3.4.3 Worktree 创建流程

> **实现方式**：worktree 的创建/删除/列表操作通过 `ProcessBuilder` 调用系统 `git` CLI 实现（JGit 不支持 `git worktree add/remove`，见 §5.8a）。Snapshot 操作仍使用 JGit。

> 以下用 git CLI 语法描述概念性操作。

```
创建 worktree(slug="explore-{sessionId}-{taskId}")
│
├── 1. Slug 验证
│   ├── 正则匹配：^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$
│   ├── 禁止 ".." 和绝对路径（防路径穿越）
│   └── 长度上限 64 字符
│
├── 2. 生成路径
│   ├── worktree_path = {data_dir}/ai-worktrees/{project_id}/{slug}
│   └── 路径冲突时追加 -2, -3...（最多尝试 10 次）
│
├── 3. Fast Resume 检查
│   ├── worktree_path 已存在且是有效 linked worktree → 直接返回
│   └── 不存在或无效 → 继续
│
├── 4. 创建 worktree
│   ├── git worktree add -B agent/worktree/{slug} {worktree_path} HEAD
│   ├── -B 创建命名分支（非 detached HEAD）
│   └── 分支名约定：agent/worktree/{slug}
│
├── 5. Symlink 大目录
│   ├── 检测源目录中的 {target, node_modules, .m2, .gradle, .venv, __pycache__}
│   ├── Linux/macOS：ln -s {source}/{dir} {worktree_path}/{dir}
│   ├── Windows：mklink /J（junction）替代 symlink（JGit 默认 core.symlinks=false）
│   └── symlink 在 worktree 删除前先清理
│
├── 6. 环境适配
│   ├── 继承父 Agent 的环境配置（JAVA_HOME 等）作为默认值
│   ├── 检查 worktree 分支是否有特定的工具链要求（.java-version、.nvmrc）
│   └── 记录到 WorktreeInfo.environmentOverrides
│
├── 7. 磁盘空间预检
│   ├── 检查可用空间是否超过配置阈值（默认 1GB）
│   └── 不足则拒绝创建并返回明确错误
│
├── 8. 注册到 Worktree Registry（数据库）
│
└── 9. 返回 WorktreeInfo
    ├── slug, path, branch, originalPath
    ├── parentSessionId, createdAt
    ├── symlinkedDirs[], environmentOverrides
```

#### 3.4.4 Worktree 存储位置

**决策**：worktree 存储在 `{data_dir}/ai-worktrees/{project_hash}/`，独立于项目目录。`project_hash` = SHA-1(project_id)[:12]，避免长路径问题。

**理由**：
- 不污染用户的项目目录（用户不会在项目内看到 Agent 的 worktree 目录）
- 便于统一管理和清理（所有 worktree 集中在 `{data_dir}/ai-worktrees/` 下）
- worktree 仍会在项目的 `git worktree list` 中可见（git linked worktree 机制），但其物理路径在项目外，不影响项目文件结构

**注意**：git linked worktree 会在主仓库的 `.git/worktrees/` 目录下注册元数据。这是 git 的正常行为，不影响用户代码。Agent 清理 worktree 时会同步清理这些元数据。

**拒绝了**：在项目内创建 `.git-worktrees/` 目录。理由：用户项目目录是用户资产，Agent 不应向其中添加非项目文件。

#### 3.4.5 Worktree 分支策略

**决策**：使用命名分支（`-B`），不用 detached HEAD。

**理由**：
- 命名分支可通过 `git branch -a` 查看，便于调试和审计
- detached HEAD 在 git 中容易丢失（gc 会清理不可达 commit）
- 命名分支遵循约定 `agent/worktree/{slug}`，便于识别和批量清理

**拒绝了**：opencode 的 `--detach HEAD` 方案。理由：opencode 的 ProjectCopy 是用户手动管理的工具，生命周期可控。Nop 的 Agent worktree 是自动创建的，命名分支提供更好的可追溯性。

#### 3.4.6 Worktree 清理流程

> **实现方式**：通过 `ProcessBuilder` 调用系统 `git` CLI 实现。

```
清理 worktree(slug)
│
├── 1. 删除 symlink（node_modules, target 等）
│
├── 2. 检查是否有未提交的修改
│   ├── 有修改 + 需要保留 → git commit + 通知父 Agent
│   └── 无修改或不需要 → 继续
│
├── 3. git worktree remove --force {worktree_path}
│
├── 4. 删除关联分支（可选，配置决定）
│   └── git branch -D agent/worktree/{slug}
│
├── 5. 从 Worktree Registry 注销
│
└── 6. Fallback：如果 git worktree remove 失败
    ├── 从 {data_dir}/ai-worktrees/ 直接删除目录
    └── 通过 git rev-parse --git-common-dir 找到主仓库，清理 worktree 引用
```

#### 3.4.6a Crash 策略

当子 Agent crash 或被强制终止时，清理流程需要预定义策略：

| 策略 | 行为 | 适用场景 |
|------|------|---------|
| `preserve`（默认） | 自动 commit 未提交修改到 `agent/worktree/{slug}` 分支，标记 `hasUncommittedChanges=true`，保留分支不删除 | 探索性任务可能产生有价值代码 |
| `discard` | 强制丢弃所有未提交修改，删除 worktree 和分支 | 纯实验性任务 |

Orphaned 回收使用 `preserve` 策略（安全优先），正常结束使用子 Agent 的显式选择。

#### 3.4.6b 级联终止策略

父 Agent session 结束时，子 Agent 的终止遵循 graceful shutdown 协议：

```
父 Agent session 结束
│
├── 1. 向所有活跃子 Agent 发送 shutdown 信号
│
├── 2. 等待子 Agent 完成当前 turn
│   ├── 超时内完成（默认 30 秒，可配置）→ 正常收集结果 → 清理 worktree
│   └── 超时未完成 → 强制终止 → 进入 orphaned 回收流程（§3.4.7）
│
├── 3. 记录级联终止事件到 session log
│
└── 4. 释放 Affinity Table 中父 session 的占用
```

#### 3.4.7 Orphaned Worktree 回收

**问题**：Agent 崩溃或 session 异常终止时，worktree 可能残留。

**回收机制**：

| 触发 | 行为 |
|------|------|
| Session 正常结束 | 清理该 session 创建的所有 worktree |
| RecoveryManager 定时扫描 | 检查 Worktree Registry 中 `status=active` 但 session 已结束的 worktree → 标记 `orphaned` |
| Orphaned worktree 超过 TTL（默认 1 小时） | 自动执行清理流程 |
| 节点启动时 | 扫描 `{data_dir}/ai-worktrees/`，对比 Registry，同步状态 |

**设计参考**：OpenHarness 的 `cleanup_stale(active_agent_ids)` 模式。

#### 3.4.8 关键约束

| 约束 | 理由 |
|------|------|
| Worktree 创建/销毁通过 git CLI（ProcessBuilder） | JGit 不支持 worktree 操作（§5.8a） |
| Worktree 操作必须在主工作空间的同一进程内 | git lock 是进程级文件锁，跨进程会冲突 |
| 同一 (project, branch) 同时只有一个活跃 session | `HEAD` 只有一个，避免 git 操作冲突 |
| Shared 模式下父子 Agent 共享 `FileOperationLock` | 防止 `.git/index.lock` 冲突和文件写覆盖 |
| Worktree 分支命名遵循约定：`agent/{task-type}/{slug}` | 避免分支名冲突，便于清理 |
| Worktree 数量有上限（默认 4 / session，16 / node） | 文件系统资源有限 |
| 每个 worktree 操作持有 `ReentrantLock`（支持 tryLock 超时） | 防止同一 worktree 的并发 git 操作和死锁 |
| 父 session 结束时级联终止子 Agent（§3.4.6b） | 防止孤儿 worktree 和资源泄漏 |

#### 3.4.9 Affinity Table 分布式一致性

Affinity Table 的注册操作使用数据库级唯一约束防止 TOCTOU 竞态：

```sql
UNIQUE(project, branch) WHERE status = 'active'
```

步骤 1（查询）→ 步骤 4（注册）在事务中完成（或使用 compare-and-swap）。增加 fencing token 机制：注册时携带递增 token，旧 session 的 token 自动失效。

### 3.5 Snapshot 与 Worktree 的关系

两个机制解决不同问题，互补使用：

| 维度 | Snapshot（独立外部 git） | Worktree（独立工作目录） |
|------|------------------------|-------------------------------|
| **解决的问题** | undo / 回滚 / 变更追踪 | 并行工作目录隔离 |
| **影响范围** | 不隔离，直接在工作目录上快照 | 完全隔离，独立文件系统 |
| **对项目 git 的影响** | 无影响（独立 git 目录，与项目 git 完全隔离） | 在项目 git 中可见（`git worktree list`） |
| **分支关系** | 无分支（纯 tree hash） | 命名分支 `agent/worktree/{slug}` |
| **触发方式** | Agent 每轮自动 track | `call-agent(workspace=worktree)` 时创建 |
| **所属层** | Layer 2（必须） | Layer 4（可选） |
| **恢复方式** | 单文件/整体回滚 | cherry-pick 或丢弃 |

**使用场景矩阵**：

| 场景 | Snapshot | Worktree |
|------|----------|----------|
| 单 Agent 顺序工作 | ✅ 每步保护 | 不需要 |
| 子 Agent 只读探索 | ✅ 父 Agent 快照保护 | 不需要 |
| 子 Agent 修改不同文件 | ✅ 父 Agent 快照保护 | 可选（推荐） |
| 子 Agent 可能修改同一文件 | ✅ 各自快照 | ✅ 必须（避免冲突） |
| 子 Agent 并行实验多个方案 | ✅ 各自快照 | ✅ 必须（独立分支） |

**Worktree 中的快照**：每个 worktree 创建时，Snapshot 服务为其在独立的外部 git 仓库中建立快照。worktree 的 snapshot 仓库路径为 `{data_dir}/ai-snapshot/{project_hash}/worktree-{slug}/`。snapshot 的 `.gitignore` 排除规则来自 worktree 检出分支自身的 `.gitignore`（而非主工作目录）。snapshot **不跟随 symlink**，所以 symlink 的大目录（target/、node_modules/ 等）不会被跟踪。worktree 销毁时，对应的 snapshot 仓库可以选择保留（用于审计）或清理。

### 3.6 提交与合并模型

#### 3.6.1 分支策略

```
main (保护分支，Agent 不直接 push)
│
├── agent/{session-id}            # Agent 主工作分支
│
├── agent/worktree/{slug}         # 子 Agent Worktree 分支（对应 §3.4.5 的命名约定）
│
└── user/{user-id}/{feature}      # 用户指定分支
```

#### 3.6.2 提交流程

```
Agent 修改文件 → git add + git commit (本地)
│
├── Agent 自动提交（小步提交策略）
│   ├── 每完成一个有意义的步骤 → 自动 commit
│   ├── commit message 由 LLM 生成或从 plan step 派生
│   └── 本地提交，不自动 push
│
├── Session 结束或用户触发 → git push
│   ├── push 到远端：refs/heads/agent/{session-id}
│   └── 远端可配置保护规则
│
└── 合并到主干
    ├── 自动合并：无冲突 → 自动 merge/PR
    ├── LLM 辅助合并：简单冲突 → LLM 分析 + 自动解决
    └── 人工介入：复杂冲突 → 标记 conflict + 通知用户
```

#### 3.6.3 关键规则

| 规则 | 理由 |
|------|------|
| Agent 不自动 push 到共享分支（main/develop） | 避免破坏主干稳定性 |
| Agent 的每次 commit 都可回滚 | 小步提交保证回滚粒度 |
| 合并到主干由专门的 merge agent 或人工触发 | 控制质量门禁 |

### 3.7 安全防护

#### 3.7.1 硬编码路径保护

Agent 不可访问以下路径（不可被 Delta 定制覆盖）：

| 路径模式 | 保护内容 |
|---------|---------|
| `*/.ssh/*` | SSH 密钥 |
| `*/.aws/credentials` | AWS 凭证 |
| `*/.config/gcloud/*` | GCP 凭证 |
| `*/.gnupg/*` | GPG 密钥 |
| `*/.kube/config` | Kubernetes 配置 |

#### 3.7.2 Worktree 安全

| 安全措施 | 说明 |
|---------|------|
| Slug 验证 | 正则 `^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$`，禁止 `..` 和绝对路径 |
| 路径穿越防护 | worktree 路径必须在 `{data_dir}/ai-worktrees/` 下 |
| 资源上限 | 单 session 最多 4 个 worktree，单 node 最多 16 个，总大小限制 |
| 自动清理 | session 结束/超时后自动清理所有关联 worktree |
| 并发锁 | 每个 worktree 目录一个 `ReentrantLock`，防止并发 git 操作 |

---

## 四、对分层架构的影响

### 4.1 Layer 1 (Core)

无需变更。调度和快照是运行时行为，不影响核心接口契约。

### 4.2 Layer 2 (Execution)

| 变更 | 说明 |
|------|------|
| `call-agent` 增加 `workspace` 参数 | `shared`（默认）或 `worktree`。shared 模式下子 Agent 共享父 Agent 的 `FileOperationLock` |
| 工具执行增加工作空间上下文 | 每个工具调用可感知当前工作目录 |
| 环境探测作为 session 初始化步骤 | 自动检测 Java/Node.js 工具链 |
| **新增 `IFileSnapshotService`** | 独立外部 git 快照服务，基于 **JGit**（纯 Java）。track/diff/restore/revert 操作。每次 agent turn 自动 track。不跟随 symlink |
| **新增快照元数据存储** | `ai_session_message` 增加 `type=snapshot` 记录 |

### 4.3 Layer 3 (Reliability)

| 变更 | 说明 |
|------|------|
| 新增 `IBranchAffinity` 调度接口 | 分支亲和调度作为可靠性扩展 |
| 提交回滚机制 | 基于 snapshot 的 undo + git reflog 的安全网 |

### 4.4 Layer 4 (Platform)

| 变更 | 说明 |
|------|------|
| 分布式调度器 | 跨节点的分支亲和路由 |
| Affinity Table 持久化 | (project, branch) → Node 映射存储 |
| 双环境工具链注册表 | 按节点注册可用的 Java/Node.js 环境 |
| **Worktree 生命周期管理器** | 基于 **git CLI**（ProcessBuilder）的创建/销毁/Fast Resume/Orphaned 回收 |
| **Worktree Registry** | 数据库表记录 worktree 状态，含 nodeId 用于跨节点 orphaned 检测 |
| Merge Agent | 自动/半自动合并到主干 |

---

## 五、拒绝了什么

### 5.1 拒绝：分布式文件系统协调

**方案**：同一个 project 的文件操作可以分散到多个节点，通过分布式锁（Redis/ZooKeeper）协调。

**拒绝理由**：
- git 的 lock 机制是进程级文件锁，无法跨进程协调
- 编译缓存（`target/`、`node_modules/`）本质上是本地资源
- 复杂度极高但收益为零——直接复用 git branch 模型可以零成本实现同样的效果

### 5.2 拒绝：Session 级调度亲和

**方案**：调度亲和单元是 session，不关心分支。

**拒绝理由**：
- 同一 project 同一分支的两个 session 在不同节点上操作，必然产生 git 冲突
- 和人类的分布式开发模型不一致——人类是按分支隔离的

### 5.3 拒绝：子 Agent 跨节点 Worktree

**方案**：子 Agent 可以在另一个节点上创建 worktree，获得更多资源。

**拒绝理由**：
- git worktree 的创建/销毁必须在主仓库的同一进程内（git lock 是进程级文件锁）
- 跨节点意味着需要远程 git 操作，增加了延迟和失败风险
- 子 Agent 通常是轻量探索任务，不需要额外节点资源
- worktree 的物理存储在本地磁盘，跨节点无法访问（除非使用共享存储）

### 5.4 拒绝：Agent 自动 Merge 到主干

**方案**：Agent 完成任务后自动 merge 到 main/develop。

**拒绝理由**：
- 主干稳定性是最高优先级，Agent 的修改必须经过质量门禁
- 自动 merge 在冲突场景下风险不可控
- 合并决策应该由人工或专门的 Merge Agent（带审查能力）做出

### 5.5 拒绝：使用项目 git 做快照

**方案**：直接在项目的 `.git` 中创建 snapshot commit 或 stash。

**拒绝理由**：
- 污染用户的 commit 历史、reflog 和 index
- 用户运行 `git log` 会看到 Agent 的快照 commit
- 违反"Agent 不干预用户 git"的原则

### 5.6 拒绝：Worktree 存储在项目目录内

**方案**：在 `{workspace-root}/.git-worktrees/` 下创建 worktree。

**拒绝理由**：
- 污染用户的项目目录
- worktree 出现在用户的 `git worktree list` 输出中，增加困惑
- OpenHarness 使用 `~/.openharness/worktrees/`，opencode 的 ProjectCopy 由用户/API 指定目录，两者都选择项目外存储

> **注**：即使用了项目外存储，git linked worktree 仍会在主仓库的 `.git/worktrees/` 下注册元数据（这是 git 的正常行为）。清理 worktree 时会同步清理这些元数据。

### 5.7 拒绝：Detached HEAD Worktree

**方案**：使用 `git worktree add --detach HEAD`（opencode ProjectCopy 模式）。

**拒绝理由**：
- detached HEAD 的 commit 容易被 gc 清理（不可达 commit 在 gc 时被回收）
- 命名分支便于 `git branch -a` 审计和批量清理
- Nop 的 worktree 是自动管理的，需要更好的可追溯性
- OpenHarness 也选择了命名分支（`-B worktree-{slug}`），验证了此方案的可行性

### 5.8 拒绝：全部依赖系统 git CLI

**方案**：所有 git 操作（快照 + worktree）都通过 `ProcessBuilder` 调用系统 `git` 命令实现。

**拒绝理由**：
- Snapshot 操作 JGit 完全支持，不需要 git CLI
- JGit 跨平台兼容性好，不依赖系统预装 git
- JGit 是纯 Java，与 Nop 技术栈一致

### 5.8a 采用：混合实现策略（修订）

**审计发现**（2026-06-08）：JGit **不支持** `git worktree add/remove`（Feature Request GitHub Issue #264，2026-05 开启，截至审计日仍 Open，无 PR）。

**修订决策**：

| 子系统 | 实现方式 | 理由 |
|--------|---------|------|
| `IFileSnapshotService`（快照） | **JGit**（纯 Java） | JGit 完全支持 bare repo + write-tree + diff + checkout |
| `IWorktreeManager`（worktree 生命周期） | **git CLI**（ProcessBuilder） | JGit 不支持 worktree 操作，这是 Java 生态的客观限制 |

**代价**：部署时需要系统预装 git（≥2.17，支持 worktree）。这是所有 AI Agent 框架（opencode、cline、OpenHarness）的共同前提。Nop 的部署文档需注明此依赖。

**与其他框架的对比**：
- opencode（TypeScript）：全部使用 git CLI
- cline（TypeScript）：全部使用 git CLI（simple-git 库封装）
- OpenHarness（Python）：全部使用 git CLI
- **Nop（Java）**：Snapshot 用 JGit，Worktree 用 git CLI ← 最优：核心快照不依赖外部工具

---

## 六、数据模型

### 6.1 Affinity Table

调度器维护 (project, branch) → Node 的亲和映射：

| 字段 | 说明 |
|------|------|
| `project` | 项目标识（git repo URL 或逻辑名称） |
| `branch` | 分支名 |
| `nodeId` | 当前绑定的节点 ID |
| `sessionId` | 当前活跃 session ID（null 表示空闲） |
| `status` | `active` / `idle` / `queued` |
| `lastActiveAt` | 最后活跃时间（用于过期清理） |
| `worktreeCount` | 当前活跃 worktree 数量 |
| `fencingToken` | 递增令牌（防止 stale session 的注册覆盖新 session） |

### 6.2 Worktree Registry

每个节点维护本节点的 worktree 注册表：

| 字段 | 说明 |
|------|------|
| `worktreeId` | 主键 |
| `sessionId` | 所属 session |
| `parentSessionId` | 父 session（用于子 Agent） |
| `nodeId` | worktree 所在节点（用于跨节点 orphaned 检测） |
| `slug` | worktree 唯一标识（验证后的安全名称） |
| `worktreePath` | worktree 物理路径（`{data_dir}/ai-worktrees/{project_hash}/{slug}`） |
| `projectPath` | 主工作目录路径 |
| `branch` | worktree 分支名（`agent/worktree/{slug}`） |
| `status` | `active` / `completed` / `orphaned` / `stranded` |
| `symlinkedDirs` | JSON：被 symlink 的大目录列表 |
| `hasUncommittedChanges` | 清理时是否有未提交修改 |
| `environmentOverrides` | JSON：环境变量覆盖（如 JAVA_HOME） |
| `createdAt` | 创建时间 |
| `lastActiveAt` | 最后活跃时间（用于 orphaned 检测） |

**跨节点 orphaned 处理**：如果 `nodeId` 对应的节点不可达超过阈值（默认 30 分钟），worktree 标记为 `stranded`。`stranded` worktree 释放占用的分支名（允许其他 session 创建同名 worktree），原节点恢复后通过启动扫描自动清理物理目录。

### 6.3 Snapshot Record

快照记录嵌入 `ai_session_message` 表：

| 字段 | 说明 |
|------|------|
| `sessionId` | 所属 session |
| `type` | `snapshot` |
| `snapshotHash` | JGit tree hash（快照 ID） |
| `snapshotStats` | JSON：`{fileCount, changedFiles[], totalSize}` |
| `createdAt` | 快照时间 |

---

## 七、与其他文档的关系

- `01-architecture-baseline.md` — 本篇在其部署模型基础上细化调度策略和文件快照
- `nop-ai-agent-actor-runtime-vision.md` — 本篇在其 Platform Layer 基础上新增分支亲和调度和 Worktree 管理
- `nop-ai-agent-multi-agent.md` — 子 Agent 隔离模型与本篇的 worktree 设计互补
- `nop-ai-agent-security-and-permissions.md` — 硬编码路径保护是本篇安全措施的一部分
- `nop-ai-agent-reliability.md` — 本篇的 `IFileSnapshotService` 属于 Layer 2 执行扩展（在 roadmap 中归为 `IContextCompactor` 的配套机制），`IBranchAffinity` 属于 Layer 3 可靠性扩展
- `nop-ai-agent-roadmap.md` — 本篇的新增接口（`IFileSnapshotService`, `IBranchAffinity`）纳入分层架构

---

## 八、审计记录

### 8.1 2026-06-08 三轮独立审计

**审计方法**：三个独立子 agent（盘古角色）分别从不同角度审计，互不通信。

| 轮次 | 审计角度 | 结论 |
|------|---------|------|
| 第 1 轮 | JGit 可行性 | 🔴 Snapshot 方案 JGit 可行（附条件）；Worktree 方案 JGit **不支持**，需改用 git CLI |
| 第 2 轮 | 调研文档 vs 设计文档交叉比对 | ✅ 9 项比对全部一致或合理偏离，调研建议全部采纳 |
| 第 3 轮 | Edge Case 与完整性 | 🔴 3 个 P0（symlink+snapshot、shared 并发、级联终止），8 个 P1 |

**已修订的 P0 问题**：

| 问题 | 修订内容 |
|------|---------|
| JGit 不支持 worktree | §5.8a：改为混合实现策略（Snapshot JGit + Worktree git CLI） |
| Symlink 导致 snapshot 体积暴涨 | §3.3.2：snapshot 不跟随 symlink（FollowSymlinks=false）；§3.5：明确 worktree snapshot 的排除规则 |
| Shared 模式无并发控制 | §3.4.1：shared 模式下共享 `FileOperationLock` |
| 父 session 结束级联终止 | §3.4.6b：graceful shutdown 协议（30 秒超时） |

**已修订的 P1 问题**：

| 问题 | 修订内容 |
|------|---------|
| Affinity Table 竞态 | §3.4.9：数据库唯一约束 + 事务 + fencing token |
| Snapshot 术语不准确 | §3.3.1：修正为"non-bare repo with separated git dir" |
| 路径长度 | §3.4.4：`project_id` → `project_hash`（SHA-1[:12]） |
| Worktree 环境继承 | §3.4.3：增加环境适配步骤和 `environmentOverrides` 字段 |
| 跨节点 orphaned | §6.2：增加 `nodeId`、`stranded` 状态、跨节点检测逻辑 |
| Crash 策略 | §3.4.6a：`preserve`/`discard` 策略枚举 |
| Windows symlink | §3.4.3：Linux 用 symlink，Windows 用 junction |
| 启动时 index.lock 清理 | §3.3.4：增加启动时清理行 |
