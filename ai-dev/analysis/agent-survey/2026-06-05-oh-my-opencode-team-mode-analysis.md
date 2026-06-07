# oh-my-opencode (oh-my-openagent) 技术分析

> Status: open
> Date: 2026-06-05
> Scope: ~/ai/oh-my-opencode — OpenCode 多模型编排增强插件（Team Mode 重点）
> Conclusion:

## Context

- oh-my-opencode (npm: `oh-my-opencode` / `oh-my-openagent`) 是 OpenCode 的增强插件，添加多模型编排和并行多 Agent 团队
- 核心差异化：Team Mode（并行多 Agent 协调）+ 11 Discipline Agents + 5 层 62 Hook 组合 + IntentGate 意图路由 + Hashline 编辑
- 调研目的：理解真正的 multi-agent team 实现模式——与 OpenCode 原生的层次委托模型对比

## Analysis

### 项目定位

- **组织**: 社区驱动 (samowrunner 等)
- **许可**: MIT
- **语言**: TypeScript (strict, ESM, OpenCode plugin)
- **LOC**: ~390K 行 TS (含测试)；`src/` 非测试代码 ~125K 行
- **插件类型**: OpenCode plugin (通过 `createPluginModule()` 注册)
- **版本**: v4.x (Team Mode 在 v4.0 引入)
- **双版本**: Ultimate (omo for OpenCode) + Light (omo for Codex CLI, 无 agent 编排)

### 顶层架构

```
oh-my-opencode/
├── src/                              # 插件核心 (~335K 行, 2440 文件)
│   ├── features/                     # 22 特性模块
│   │   ├── team-mode/               # ★ Team Mode 实现 (6,378 行非测试, 15,385 总)
│   │   │   ├── team-runtime/        # 创建/关闭/删除 (277+148+152 行)
│   │   │   ├── team-state-store/    # 状态持久化 + 文件锁 + 崩溃恢复 (862 行)
│   │   │   ├── team-registry/       # Team 规范加载 + 验证 (728 行)
│   │   │   ├── team-mailbox/        # Deferred-ack 消息系统 (505 行)
│   │   │   ├── team-tasklist/       # 共享任务列表 + 文件锁 (364 行)
│   │   │   ├── team-worktree/       # Git worktree 隔离 (143 行)
│   │   │   ├── team-layout-tmux/    # tmux 可视化 (450 行)
│   │   │   ├── tools/               # 12 个 team_* 工具 (1,351 行)
│   │   │   └── types.ts             # Zod 类型系统 (271 行)
│   │   ├── background-agent/        # 后台 Agent 管理
│   │   ├── skill-mcp-manager/       # 第 3 层 MCP (skill-embedded)
│   │   ├── opencode-skill-loader/   # Skill 加载器
│   │   ├── tmux-subagent/           # tmux subagent 集成
│   │   ├── mcp-oauth/               # MCP OAuth 流程
│   │   ├── boulder-state/           # 工作跟踪状态机 (56 行)
│   │   └── ...                      # 其他特性
│   ├── hooks/                       # 62 个 Hook (5 层)
│   │   ├── keyword-detector/        # IntentGate (635 行)
│   │   ├── team-tool-gating/        # Team 工具权限门控
│   │   ├── team-mode-status-injector/ # Team 状态注入
│   │   ├── team-mailbox-injector/   # Team 消息注入
│   │   ├── team-session-events/     # Team 事件处理 (4 handler)
│   │   └── ...                      # 55 个其他 Hook
│   ├── tools/                       # 20-39 工具 (config-gated)
│   ├── prompts-core/                # 模式 prompt (search/analyze/team/hyperplan)
│   ├── config/schema/               # Zod 配置 schema
│   └── plugin/                      # 插件注册入口
├── packages/                        # 26 子包
│   ├── boulder-state/              # 工作跟踪 (1,138 行)
│   ├── hashline-core/              # LINE#ID 编辑引擎
│   ├── agents-md-core/             # Agent 定义解析
│   ├── rules-engine/               # 规则引擎
│   ├── omo-codex/                  # Light 版本 (Codex CLI)
│   └── ...
├── .agents/skills/                  # 11 用户 Skill
│   ├── security-research/          # 安全研究 (3 猎手 + 2 PoC 工程师)
│   ├── hyperplan/                  # 对抗规划 (5 敌对评论者)
│   └── ...
└── docs/                            # 文档
```

### Team Mode 架构（核心重点）

#### 设计理念

Team Mode 将 oh-my-openagent 从"一个 agent + 子 agent"升级为**真正的并行多 Agent 系统**：
- Lead agent 协调最多 8 个并行成员
- 每个成员在独立 session 中运行，可选独立 git worktree
- 通过专用 `team_*` 工具族通信（非共享内存）
- 可选 tmux 实时可视化

#### 12 个 team_* 工具

| 工具 | 用途 | 权限 |
|------|------|------|
| `team_create` | 从规范或内联 JSON 创建团队 | Lead only |
| `team_delete` | 拆除状态/消息/任务/worktree | Lead only |
| `team_shutdown_request` | 请求关闭 | Lead only |
| `team_approve_shutdown` | 批准关闭 | Target member or Lead |
| `team_reject_shutdown` | 拒绝关闭 | Target member or Lead |
| `team_send_message` | 发送消息（点对点或广播） | Lead 可广播 |
| `team_task_create` | 创建共享任务 | 任意成员 |
| `team_task_list` | 列出任务（可按状态/负责人过滤） | 任意成员 |
| `team_task_update` | 认领/完成/删除任务 | 任意成员 |
| `team_task_get` | 获取单个任务 | 任意成员 |
| `team_status` | 查询团队运行状态 | 任意成员 |
| `team_list` | 列出已声明 + 活跃团队 | 任意成员 |

#### Team 生命周期

```
┌──────────────────────────────────────────────────────────┐
│ Phase 1: CREATE (team_create, 277 行)                    │
│   1. 加载/验证 TeamSpec                                   │
│   2. 幂等检查（同 lead+spec 已存在则复用）                   │
│   3. 清理残留 session                                      │
│   4. 创建目录结构 (state, inbox, tasks, worktrees)         │
│   5. 生成 RuntimeState (status: creating, UUID teamRunId)  │
│   6. 注册 lead session                                     │
│   7. 并行 spawn 成员 (max_parallel_members 并发上限)       │
│   8. 激活 tmux 布局                                        │
│   9. 转换状态 → active                                    │
│   10. 失败 → cleanupTeamRunResources()                    │
├──────────────────────────────────────────────────────────┤
│ Phase 2: ACTIVE (并行运行)                                │
│   - Lead 通过 team_send_message + team_task_create 委派    │
│   - Members 通过 team_task_update 认领并执行任务            │
│   - Mailbox injector hook 拉取未读消息到 member 上下文      │
│   - Live delivery: idle member 直接接收消息 (promptAsync)  │
├──────────────────────────────────────────────────────────┤
│ Phase 3: SHUTDOWN (148 行)                                │
│   - 成员/lead 请求关闭 → shutdown_request 消息             │
│   - Lead 批准 → shutdown_approved 消息                     │
│   - Lead 可拒绝并给出理由                                  │
├──────────────────────────────────────────────────────────┤
│ Phase 4: DELETE (team_delete, 152 行)                     │
│   1. 取消所有后台任务                                      │
│   2. 状态 → deleting                                      │
│   3. 移除 tmux 布局                                       │
│   4. 移除 worktrees (git worktree remove --force)         │
│   5. 状态 → deleted                                       │
│   6. 清理运行时目录                                        │
│   7. 注销 session 注册                                    │
└──────────────────────────────────────────────────────────┘
```

#### Deferred-Ack 消息系统

`team-mailbox/` (505 行) — **基于文件系统的异步消息传递**：

```
发送方                                  接收方
team_send_message()                    Mailbox Injector Hook
  │                                        │
  ├→ 写入 {messageId}.json                 ├→ pollAndBuildInjection()
  │  到 inbox/{recipient}/                 │  读取未处理 .json
  │                                        │  过滤已 pending 的
  │  背压控制:                              │  构建 <peer_message> XML
  │  - 单收件箱上限 256KB                   │
  │  - 消息体上限 32KB                      ├→ 注入 member 上下文
  │  - 广播仅限 Lead                        │
  │                                        │
  │  Live Delivery (idle member):          ├→ ack() 
  │  .delivering-{msgId}.json              │  移至 processed/
  │  → dispatchInternalPrompt              │
  │  → commit/release reservation          │
  └────────────────────────────────────────┘
```

**三阶段投递预留**：
1. `reserveMessageForDelivery()` — 重命名为 `.delivering-` 文件
2. `commitDeliveryReservation()` — 移至 `processed/`
3. `releaseDeliveryReservation()` — 恢复为普通收件箱文件

#### 共享任务列表

`team-tasklist/` (364 行) — **文件锁 + 原子写入的任务管理**：

任务状态机：
```
pending → claimed → in_progress → completed → deleted
```

- `claimTask()`: 原子文件锁，检查 `blockedBy` 依赖是否全部完成，5 分钟过期检测
- `createTask()`: 文件锁保护的高水位标记递增，原子写入
- `updateTaskStatus()`: 跨 owner 更新防护，pending→in_progress 自动认领

#### 状态持久化与崩溃恢复

`team-state-store/` (862 行)：

状态机转换：
```
creating → active | failed
active → shutdown_requested | deleting
shutdown_requested → deleting
deleting → deleted
```

**文件锁**: `withLock()` — 独占文件锁 + 过期检测（PID 存活 + 年龄 > 5 分钟），50ms 重试，4s 超时

**原子写入**: `atomicWrite()` — 写入 `.tmp.{uuid}` 临时文件 → fsync → rename

**崩溃恢复** (`resume.ts`, 86 行)：
- `creating` 卡住 > 30 分钟 → 标记 `failed`
- `active` 且 lead 已死 → 标记 `orphaned`
- `active` 且 worker 已死 → 标记 `errored`，协调邮箱预留
- `deleting` → 完成删除

#### 存储布局

```
~/.omo/
├── teams/{name}/                    # Team 规范
│   └── config.json                  # TeamSpec JSON
├── runtime/{teamRunId}/             # 运行时状态
│   ├── state.json                   # RuntimeState (原子写入)
│   ├── state.lock                   # 状态转换文件锁
│   ├── inboxes/{memberName}/        # 每人邮箱
│   │   ├── {messageId}.json         # 未读消息
│   │   ├── .delivering-{msgId}.json # Live delivery 预留
│   │   └── processed/{msgId}.json   # 已确认消息
│   └── tasks/                       # 共享任务列表
│       ├── .lock                    # 任务创建锁
│       ├── .highwatermark           # 单调递增 ID
│       ├── {taskId}.json            # 任务数据
│       └── claims/{taskId}.lock     # 每任务认领锁
└── worktrees/{teamRunId}/{member}/  # 每人 git worktree
```

#### Agent 资格注册表

```typescript
AGENT_ELIGIBILITY_REGISTRY = {
  sisyphus:           "eligible",
  atlas:              "eligible",
  "sisyphus-junior":  "eligible",
  hephaestus:         "conditional",   // 需要 D-36 补丁
  oracle:             "hard-reject",   // 只读，不可做 team member
  librarian:          "hard-reject",
  explore:            "hard-reject",
  "multimodal-looker":"hard-reject",
  metis:              "hard-reject",
  momus:              "hard-reject",
  prometheus:         "hard-reject",
}
```

#### Team-Mode Hook (7 个，条件启用)

| Hook | 层级 | 用途 |
|------|------|------|
| `team-tool-gating` | ToolGuard | 按角色限制 `team_*` 工具使用 |
| `team-mode-status-injector` | Transform | 注入 `<team_mode_status>` 上下文 |
| `team-mailbox-injector` | Transform | 拉取未读消息到 agent 上下文 |
| `team-idle-wake-hint` | Event | Nudge idle member 重新工作 |
| `team-lead-orphan-handler` | Event | 检测 lead session 消失，标记 orphaned |
| `team-member-error-handler` | Event | 处理 member 错误，重新排队消息 |
| `team-member-status-handler` | Event | 跟踪 member 状态转换 |

### Agent 系统（11 个 Discipline Agents）

| Agent | 默认模型 | 模式 | 回退链 | 用途 |
|-------|----------|------|--------|------|
| **Sisyphus** | claude-opus-4-7 max | primary | kimi-k2.6→k2p5→kimi-k2.5→gpt-5.5→glm-5→big-pickle | 主编排器，thinking budget 32K |
| **Hephaestus** | gpt-5.5 medium | primary | (需要 openai/github-copilot/venice) | 深度自主工作者 |
| **Atlas** | claude-sonnet-4-6 | primary | kimi-k2.6→gpt-5.5→minimax-m3→minimax-m2.7 | Todo-list 编排器 |
| **Prometheus** | claude-opus-4-7 max | primary | gpt-5.5→glm-5.1→gemini-3.1-pro | 战略规划师 |
| **Oracle** | gpt-5.5 high | subagent | gemini-3.1-pro→claude-opus-4-7→glm-5.1 | 只读咨询 |
| **Librarian** | gpt-5.4-mini-fast | subagent | qwen3.5-plus→minimax→claude-haiku→gpt-5.4-nano | 外部文档搜索 |
| **Explore** | gpt-5.4-mini-fast | subagent | (同 Librarian) | 代码搜索 |
| **Multimodal-Looker** | gpt-5.5 medium | subagent | kimi-k2.6→glm-4.6v→gpt-5-nano | 图片/PDF 分析 |
| **Metis** | claude-sonnet-4-6 | subagent | claude-opus-4-7→gpt-5.5→glm-5.1→k2p5 | 规划顾问 |
| **Momus** | gpt-5.5 xhigh | subagent | claude-opus-4-7→gemini-3.1-pro→glm-5.1 | 计划评审 |
| **Sisyphus-Junior** | claude-sonnet-4-6 | subagent | kimi-k2.6→gpt-5.5→minimax→big-pickle | Category 路由执行器 |

**成员规范** (`MemberSchema`)：discriminated union
- `kind: "category"` — 通过 category 路由到 `sisyphus-junior`
- `kind: "subagent_type"` — 直接指定 agent

### 5 层 Hook 组合

| 层 | 基础数 | +Team | 关键 Hook |
|----|--------|-------|-----------|
| Session | 24 | 0 | preemptiveCompaction, sessionRecovery, modelFallback, runtimeFallback |
| ToolGuard | 17 | +1 | comment-checker, rules-injector, **team-tool-gating** |
| Transform | 5 | +2 | keyword-detector, **team-mode-status-injector**, **team-mailbox-injector** |
| Continuation | 7 | 0 | todo-continuation-enforcer, compaction-todo-preserver |
| Skill | 2 | 0 | category-skill-reminder, auto-slash-command |
| Event | 0 | +4 | team-idle-wake-hint, team-lead-orphan-handler, team-member-error-handler, team-member-status-handler |
| **Total** | **55** | **+7** | **= 62** |

### IntentGate（意图门）

`src/hooks/keyword-detector/` (635 行):

| 意图类型 | 模式 | 触发模式 |
|----------|------|----------|
| `ultrawork` (ulw) | `\b(ultrawork|ulw)\b` | 高强度工作模式 |
| `search` | 自定义模式 | 搜索聚焦模式 |
| `analyze` | 自定义模式 | 分析聚焦模式 |
| `team` | 自定义模式 | Team Mode 激活 |
| `hyperplan` (hpp) | 自定义模式 | 对抗规划模式 |
| `hyperplan-ultrawork` | 组合模式 | 对抗 + 工作组合 |

分类管线：剥离代码块 → 逐模式匹配 → 过滤禁用项 → 应用白名单 → 注入模式特定 prompt

### Skill 驱动的 Team 组合

**security-research** (3 猎手 + 2 PoC 工程师):
- surface-hunter (deep), auth-data-hunter (ultrabrain), runtime-supply-hunter (unspecified-high)
- poc-engineer-a (unspecified-high), poc-engineer-b (deep)

**hyperplan** (5 敌对评论者，验证层强制 4 类 category):
- skeptic (unspecified-low), integrator (unspecified-high), researcher (deep)
- architect (ultrabrain), challenger (artistry)
- `validateHyperplanComposition()` 在验证器中强制 composition 约束

### 工具系统

**Always-on (20)**: lsp_goto_definition, lsp_find_references, lsp_symbols, lsp_diagnostics, lsp_prepare_rename, lsp_rename, grep, glob, ast_grep_search, ast_grep_replace, session_list, session_read, session_search, session_info, background_output, background_cancel, call_omo_agent, task, skill, skill_mcp

**Conditional (+19)**: look_at (+1), interactive_bash (+1), task_create/get/list/update (+4), hashline edit (+1), team_* ×12 (+12)

### MCP 系统（3 层）

| 层 | 来源 | 机制 |
|----|------|------|
| 1. Built-in | `src/mcp/` (5 个) | 3 远程 HTTP + 2 本地 stdio (LSP, ast_grep) |
| 2. Claude Code | `.mcp.json` (项目+用户) | `${VAR}` 环境变量展开 |
| 3. Skill-embedded | SKILL.md YAML frontmatter | stdio + HTTP, OAuth 2.0 + PKCE + DCR, per-session 隔离 |

### 消息传递与并发处理详解

#### 消息传递：基于文件系统的 Deferred-Ack Mailbox（非消息队列）

**消息生命周期**：

```
发送方 team_send_message()
  ├→ 1. 背压检查：payload ≤ 32KB, 收件箱未读 ≤ 256KB
  ├→ 2. 去重检查：messageId 不能重复（含 .delivering- 前缀）
  ├→ 3. 广播限制：仅 Lead 可广播 (to: "*")
  ├→ 4. 状态门控：team 状态 deleting/deleted 时拒绝
  ├→ 5. atomicWrite → .tmp.{uuid} → fsync → rename → {messageId}.json
  └→ 6. 尝试 live delivery（如果 member idle）

接收方（两条路径，互斥）
  ├─ Poll 路径：team-mailbox-injector hook → pollAndBuildInjection()
  │    每个 provider turn 触发一次
  │    turn 去重：lastInjectedTurnMarker = {sessionID}#{msgCount}
  │    pending ack 去重：跳过 pendingInjectedMessageIds 中的
  │
  └─ Live 路径：idle member → promptAsync gate → 直接 dispatch
       三阶段预留：reserve → dispatch → commit/release
```

**三阶段投递预留状态机**：
```
Unreserved ({msgId}.json)          ← poll 可见
    │ reserveMessageForDelivery()
    ▼
Reserved (.delivering-{msgId}.json) ← poll 不可见（.前缀过滤）
    │ commitDeliveryReservation()   │ releaseDeliveryReservation()
    ▼                               ▼
Processed (processed/{msgId}.json)  Unreserved（回退）
```

**Live Delivery 失败处理**：

| 失败场景 | 处理 |
|----------|------|
| reserve 失败（文件不存在） | 跳过（消息已 processed） |
| member 有 pending ack | 释放预留，回退 poll |
| member 非 idle | 释放预留，回退 poll |
| promptAsync gate 拒绝 | 释放预留，回退 poll |
| dispatch 后模糊失败 (EOF/JSON/timeout) | **保守释放预留**（at-least-once，宁可重复） |
| markLiveDeliveryPending 失败 | 尝试直接 commitReservation；再失败 → 预留协调清理 |

#### 并发冲突：文件锁 + Double-Check Locking

**核心原语** (`locks.ts`)：
- `open(lockPath, "wx")` — OS 级原子排他创建
- 过期检测：`process.kill(pid, 0)` + 年龄 > staleAfterMs (默认 5 分钟)
- 重试：50ms 间隔，4s 超时
- `tolerantFsync()` — 容忍 EPERM/EACCES（网络文件系统兼容）
- `atomicWrite()` — .tmp.{uuid} + fsync + rename（POSIX 原子性）

**任务认领并发控制** (`claim.ts`) — 最复杂场景：
1. 预检查：status=pending && blockedBy 全完成
2. 预清理：检查 claims/{taskId}.lock 是否过期，未过期则立即 `AlreadyClaimedError`
3. withLock 内 double-check：重新读取 task，再次验证 status 和依赖，然后写入

**Runtime State 并发写入** (`store.ts`)：
- `transitionRuntimeState()` 在锁内序列化：load → apply → validate(Zod+状态机) → atomicWrite
- 状态机严格校验：creating→{active,failed}, active→{shutdown_requested,deleting}, deleting→deleted
- 同状态转换允许（幂等），任意状态可转 orphaned

#### 超时处理

| 超时类型 | 值 | 强制方式 |
|----------|-----|---------|
| 团队创建 | max_wall_clock_minutes (默认 120min) | deadline 检查，超时中止 spawn |
| 创建卡住恢复 | 30 分钟（硬编码） | resume 扫描 creating >30min → failed |
| 文件锁获取 | 4 秒 | 50ms 重试 ×80 次 |
| 文件锁过期 | 5 分钟 | PID 死 + 年龄 >5min → reap |
| Live delivery 预留过期 | 10 分钟 | reservation-reconciliation 扫描 |
| Deleting 卡住 | 1 分钟 | listActiveTeams 强制清理 |
| Member turns | max_member_turns (默认 500) | **仅记录不强制** |

**无心跳机制** — Liveness 仅在崩溃恢复时按需检查 (`sessionExists()`)。

#### 崩溃恢复（4 种异常状态）

| 异常状态 | 检测条件 | 恢复动作 |
|----------|---------|---------|
| creating 卡住 | creating >30 分钟 | 标记 failed，清理 worktree |
| lead 死亡 | lead session 404 | 标记 orphaned → 强制 deleteTeam |
| 全部 worker 死亡 | 所有 worker session 404 | 标记 errored，team orphaned |
| 部分 worker 死亡 | 部分 worker 404 | 标记 dead worker errored，team 保持 active |
| deleting 卡住 | deleting >1 分钟 | 强制移除运行时目录 |
| Live delivery 预留残留 | .delivering- 文件 >10 分钟 | 回退为 unreserved + 检查 session 历史是否已包含 |

**最精细的恢复是 reservation-reconciliation**：
1. 扫描 .delivering- 文件，超时回退为普通消息
2. 对每条回退消息检查 member session 历史是否已包含
3. 已包含（dispatch 成功但 commit 失败）→ 直接 ack
4. 清理 pendingInjectedMessageIds

#### 错误传播

| 事件 | 传播方式 |
|------|---------|
| Member error | → requeue pending → 标记 errored → announcement 给 lead |
| Lead death | → 标记 orphaned → 强制 deleteTeam |
| Member session idle | → ack pending live deliveries → 有未读则 wake hint (30s 去重) |
| Member session deleted | → 标记 member completed |
| Message send 失败 | → 具体错误类型 (PayloadTooLarge/Backpressure/Duplicate/BroadcastNotPermitted) |

#### 设计取舍

| 选择 | 理由 |
|------|------|
| 文件系统而非 DB | 零依赖，进程崩溃后数据不丢失，rename 原子性 |
| Deferred-ack 而非同步 RPC | Agent 不可预测的响应时间，异步更健壮 |
| 保守释放而非确认失败 | 宁可重复投递，不可丢消息（at-least-once） |
| 无心跳，按需检测 | 简化实现，崩溃恢复覆盖所有场景 |
| Double-check locking for claims | 防止 TOCTOU race |
| Member turns 不强制 | 过早终止比超时更危险，交给 lead agent 判断 |
| PID 检查 + 年龄双重过期 | 单一条件不够：进程可能被复用 PID |

### 关键架构不变量

1. **Spawn-race-safe**: 每个 team spawn 同步注册 session；hook 通过 `team-session-registry` 查找
2. **Deferred-ack mailbox**: 即发即忘消息 + 独立确认调用
3. **Locked tasks**: 原子文件锁 + 过期检测，支持并发认领
4. **Atomic writes**: 临时文件 + fsync + rename 模式贯穿始终
5. **Eligible agents only**: 解析时拒绝不合格 agent，非运行时
6. **No nested teams**: 成员不能调用 `team_create`（`team-tool-gating` 强制）
7. **Canonical agent order**: `Array.prototype` shim 强制排序
8. **Per-session MCP isolation**: Tier-3 客户端按 session+skill+server 键控
9. **双 fallback 系统**: model-fallback (主动) vs runtime-fallback (被动)
10. **prompt-async gate**: 所有内部 prompt dispatch 共享 gate 防重复注入

## Conclusion

### Team Mode 设计模式总结

oh-my-opencode 的 Team Mode 是**工具协调的并行多 Agent 系统**，与 OpenCode 原生的层次委托模型形成鲜明对比：

| 维度 | OpenCode 原生 (task tool) | oh-my-opencode Team Mode |
|------|---------------------------|--------------------------|
| **拓扑** | 树状层次 (parent→child) | 扁平团队 (lead + parallel members) |
| **并行** | 串行等待或后台异步 | 最多 8 成员真正并行 |
| **通信** | 返回值（子 agent 输出传回父） | Deferred-ack mailbox + live delivery |
| **任务** | 无共享任务系统 | 共享任务列表 + 文件锁认领 + 依赖 |
| **隔离** | 共享 DB，不同 session | 独立 session + 可选 git worktree |
| **可视化** | 无 | tmux 实时布局 |
| **容错** | 依赖 LLM 重试 | 崩溃恢复 + 过期检测 + orphan 处理 |
| **状态** | 内存为主 | 文件系统持久化 (atomic write + file lock) |
| **关闭** | session 结束即止 | 4 阶段生命周期 (create→active→shutdown→delete) |

### 对 Nop 的借鉴价值

| 借鉴点 | 优先级 | 说明 |
|--------|--------|------|
| Team Mode 的 **TeamSpec 即配置** | **P0** | JSON 定义团队结构，Nop XDSL 天然适配 |
| **Deferred-ack mailbox** 消息系统 | **P0** | 基于文件系统的异步通信，简单可靠，适合 Nop 的消息抽象 |
| **共享任务列表 + 文件锁认领** | **P0** | 并发安全的多 Agent 任务管理范式 |
| **崩溃恢复** (resume.ts) | **P0** | 4 种异常状态的自动恢复策略，生产必备 |
| **Atomic write + file lock** 模式 | **P0** | 临时文件 + fsync + rename，所有持久化操作的基础 |
| **Agent 资格注册表** | **P1** | 编译时拒绝不合格组合，防止运行时错误 |
| **IntentGate 意图路由** | **P1** | 关键词→模式路由，简单高效 |
| **Skill 驱动的团队组合** (security-research/hyperplan) | **P1** | Skill 定义团队结构而非硬编码 |
| **tmux 可视化** | **P2** | 开发友好但非核心 |
| **5 层 Hook 组合** | **P2** | 过于复杂（62 个 hook），但分层思想可借鉴 |

### 与已分析项目的对比

| 维度 | oh-my-opencode | OpenCode 原生 | Reasonix | PilotDeck |
|------|---------------|---------------|----------|-----------|
| **多 Agent 并行** | 最多 8 并行成员 | 串行 subagent | 单 agent | 单核心 |
| **通信模型** | Deferred-ack mailbox | 返回值 | N/A | 23 通道 |
| **任务系统** | 共享任务 + 文件锁 | 无 | N/A | N/A |
| **容错** | 崩溃恢复 + 过期检测 + orphan | Session fork | Cache-First + Event Sourcing | 熔断器 + 三级压缩 |
| **持久化** | 文件系统 (atomic write) | SQLite (Drizzle) | Event log | 内存为主 |
| **LOC (非测试)** | ~125K (src) | ~107K (src) | ~94K | ~142K |
| **语言** | TypeScript (plugin) | TypeScript/Effect | TypeScript | Python |

### 不可借鉴的点

- **文件系统作状态存储**: Java/Nop 有更好的并发原语（DB 事务、分布式锁），不需要文件锁
- **tmux 可视化**: 仅适用于终端场景
- **62 个 Hook 的复杂度**: 过度工程化，Nop 的 biz interceptor 模式更简洁
- **OpenCode plugin 体系**: 紧耦合 OpenCode 的 plugin API

## Open Questions

- [ ] Team Mode 在高并发（8 成员同时活跃）下的消息延迟和吞吐量如何？
- [ ] 文件锁在 NFS/网络文件系统上的可靠性？
- [ ] Hyperplan 的 5 个敌对评论者在实际项目中的效果如何？是否导致过度分析？
- [ ] Sisyphus 的多级 fallback 链（6 级）切换延迟如何？用户体验影响？
- [ ] Light 版本 (Codex CLI) 的采用率如何？是否验证了组件化拆分的价值？

## References

- `~/ai/oh-my-opencode/` — 项目根目录
- `~/ai/oh-my-opencode/AGENTS.md` — 开发指南（详细）
- `~/ai/oh-my-opencode/src/features/team-mode/` — Team Mode 实现 (6,378 行非测试)
- `~/ai/oh-my-opencode/src/features/team-mode/types.ts` — 类型系统 (271 行)
- `~/ai/oh-my-opencode/src/features/team-mode/team-runtime/create.ts` — 创建逻辑 (277 行)
- `~/ai/oh-my-opencode/src/features/team-mode/team-state-store/` — 状态持久化 (862 行)
- `~/ai/oh-my-opencode/src/features/team-mode/team-mailbox/` — 消息系统 (505 行)
- `~/ai/oh-my-opencode/src/features/team-mode/team-tasklist/` — 任务系统 (364 行)
- `~/ai/oh-my-opencode/src/features/team-mode/tools/` — 12 个 team_* 工具 (1,351 行)
- `~/ai/oh-my-opencode/src/hooks/keyword-detector/` — IntentGate (635 行)
- `~/ai/oh-my-opencode/docs/guide/team-mode.md` — Team Mode 用户文档
- `~/ai/oh-my-opencode/docs/guide/orchestration.md` — 编排指南
- `ai-dev/analysis/agent-survey/2026-06-05-opencode-analysis.md` — OpenCode 分析
- `ai-dev/analysis/agent-survey/2026-06-05-agent-design-key-elements.md` — 综合文档
