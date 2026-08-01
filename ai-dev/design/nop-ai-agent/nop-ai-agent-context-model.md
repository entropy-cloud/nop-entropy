# Nop AI Agent 上下文模型设计

## 1. 目标

本篇定义 Agent 的上下文模型——Agent 执行时携带的环境信息、与 Tool 的共享机制、以及子 Agent 的上下文继承与分叉语义。

核心隐喻：**Agent 类似操作系统子进程**。Agent 的上下文类似进程环境，工具调用类似系统调用，子 Agent 派生类似 fork/exec。

## 2. 设计定位

本篇属于架构总览层的补充，解决以下问题：

1. Agent 执行时能访问什么信息
2. Tool 执行时能访问什么信息
3. 父子 Agent 之间上下文如何传递
4. Fork 后的上下文独立性如何保证

本篇不定义具体的上下文数据结构——源码是唯一事实。

## 3. Agent 上下文的组成

Agent 上下文是引擎层概念，不是 DSL 层概念。它由以下维度构成：

| 维度 | 说明 | 是否持久化 |
|------|------|-----------|
| 消息历史 | 当前会话的完整消息序列 | 是（VFS `.nop/` Event Log） |
| 协调消息 | 其他 Agent 的 scope_claim/operation_intent（见 multi-agent.md §4） | 是（标记为 `pinned`，至少在 session 生命周期内保留。审计需要追溯协调决策的影响） |
| 计划状态 | 当前 Plan 的结构和进度 | 是（项目级 `ai-dev/plans/`，AGE 规范，跨 session） |
| 工具集 | 当前 Agent 可见的工具集合 | 否（运行时装配） |
| 约束配置 | maxIterations、token 预算、超时等 | 否（从 DSL 读取） |
| 环境信息 | 工作目录、环境变量、文件系统访问范围 | 否（运行时注入） |
| 会话标识 | sessionId、snapshotId、parentSession | 是（VFS `.nop/` Event Log） |

**决策**：Tool 执行时能访问 Agent 上下文的一个受控子集（只读或受限修改），而不是全部。

**理由**：Tool 不应该能直接修改 Agent 的消息历史或计划状态。Tool 只能通过返回值（tool result）间接影响 Agent 的后续推理。

**拒绝了**：Tool 直接操作 Agent 上下文。理由是这与操作系统的系统调用模型一致——系统调用通过返回值影响进程，而不是直接修改进程内存。

## 4. Tool 上下文环境

### 4.1 Tool 能访问什么

Tool 执行时，引擎提供以下上下文信息：

| 可见信息 | 访问方式 |
|---------|---------|
| 当前工具调用的参数 | 由 LLM 输出解析得到 |
| 工作目录和文件系统范围 | 由 Agent 环境配置决定 |
| Agent 的 sessionId | 只读，用于需要持久化的工具 |
| 当前请求的环境变量 | 只读 |

| 不可见信息 | 原因 |
|-----------|------|
| Agent 的完整消息历史 | Tool 不需要知道整个对话 |
| Agent 的 Plan 状态 | Tool 不需要知道全局计划 |
| 其他 Tool 的内部状态 | 隔离性 |
| 其他 Agent 的上下文 | 隔离性 |

### 4.2 ask-oracle（人机交互）的特殊性

`ask-oracle` 是一个特殊工具，它的语义是"向人类请求指导"。它的执行不依赖 Agent 上下文，但返回值（人类的回答）会写入消息历史。

**决策**：ask-oracle 作为标准 Tool 定义在 nop-ai-toolkit 中，不享受特殊引擎层待遇。

**理由**：所有交互都走 XML Tool 路径，保持一致性。ask-oracle 与 read-file 在引擎层面没有本质区别——都是"执行一个工具，返回一个结果"。

## 5. Agent-as-Subprocess 模型

### 5.1 进程隐喻映射

| 操作系统概念 | Agent 对应 | 说明 |
|------------|-----------|------|
| 进程 (Process) | Agent 执行实例 | 一次运行中的 Agent |
| fork | 上下文复制 + 新 sessionId | 子 Agent 获得父 Agent 的上下文快照 |
| exec | agentName 切换 | 在 fork 的基础上切换到新 Agent 配置 |
| 环境变量继承 | 上下文继承 | 子 Agent 继承父 Agent 的环境信息 |
| 标准输入 (stdin) | 初始消息/请求 | Agent 启动时的输入 |
| 标准输出 (stdout) | 最终结果 | Agent 执行完成后的输出 |
| 标准错误 (stderr) | 错误和日志 | Agent 执行过程中的错误信息 |
| 进程间通信 (pipe) | 消息传递 | 父子 Agent 之间的消息流 |
| 退出码 (exit code) | 执行状态 | success/failure/timeout 等 |

### 5.2 call-agent 的语义重述

`call-agent` 工具是 Agent-as-Subprocess 模型的入口：

| call-agent 参数 | 进程隐喻 | 说明 |
|----------------|---------|------|
| agentName | exec 的目标程序 | 要执行的 Agent 配置 |
| sessionId | 进程的会话关联 | 延续已有会话或新建 |
| inheritContext | 是否继承环境变量 | self 模式下的上下文继承 |
| skills | 进程的能力配置 | 本次调用的技能集合 |
| input | stdin | 传递给子 Agent 的输入 |
| output | stdout | 子 Agent 的返回结果 |

### 5.3 Fork 语义

**决策**：Fork 是 Session 级别的操作，不是 Agent 级别。

**期望行为**：

1. Fork 基于当前 session 创建新的 session
2. 新 session 的消息历史是当前 session 的快照
3. 新 session 继承父 session 的 planId 引用（Plan 是项目级共享实体，见 `nop-ai-agent-session-and-storage.md` §6，不深拷贝）
4. 新 session 的工具集和约束配置从新 Agent 配置重新装配
5. Fork 后，父子 session 完全独立——修改互不影响

**与 `call-agent` 的关系**：`call-agent` 的 `inheritContext=true` + `agentName="self"` 等价于 fork。

**拒绝了**：写时复制（Copy-on-Write）。理由是 Agent 的消息历史不是高频修改的热数据，快照拷贝的性能开销可接受，而 CoW 的实现复杂度不值得。Plan 是项目级共享实体，fork 时仅继承 planId 引用，无需拷贝。

### 5.4 上下文继承协议

当子 Agent 通过 `call-agent` 启动时，上下文传递规则：

| 上下文维度 | 继承行为 |
|-----------|---------|
| 消息历史 | inheritContext=true 时继承快照，否则为空 |
| Plan 状态 | inheritContext=true 时继承 planId 引用（Plan 是项目级共享实体，见 `nop-ai-agent-session-and-storage.md` §6，不深拷贝），否则为空 |
| 工具集 | 不继承——从子 Agent 的 agent.xdef 重新装配 |
| 约束配置 | 不继承——从子 Agent 的 agent.xdef 重新装配 |
| 环境信息 | 继承（工作目录、文件系统范围） |
| Session 标识 | 新 sessionId，parentSession 指向父 |

## 6. 内部 Agent 化

### 6.1 概念

部分引擎内部能力（如上下文压缩、错误修复、结果评审）可以用 Agent 来实现。这些内部 Agent 与外部 Agent 的区别在于：

| 维度 | 内部 Agent | 外部 Agent |
|------|-----------|-----------|
| 触发方式 | 引擎内部自动触发 | 外部请求触发 |
| Session | 可共享父 Agent 的 session | 独立 session |
| 工具集 | 受限（只暴露必要工具） | 完整 |
| 可见性 | 对外部不可见 | 对外部可见 |
| 失败影响 | 不中止父 Agent（降级为默认策略） | 可中止 |

### 6.2 可 Agent 化的能力

以下引擎内部能力可以设计为 Agent 接口：

| 能力 | 输入 | 输出 | 失败降级策略 |
|------|------|------|------------|
| 上下文压缩 | 当前消息历史 | 压缩后的消息历史 | 5 层渐进管道（Layer 0-4，见 reliability.md §7） |
| 错误修复 | 错误信息 + 当前上下文 | 修复后的消息或指令 | 标准错误处理 |
| 结果评审 | Agent 的执行结果 | 通过/不通过 + 理由 | 信任原始结果 |
| Plan 调整 | 当前 Plan + 执行进度 | 调整后的 Plan | 保持原 Plan |

### 6.3 接口契约

内部 Agent 化的接口遵循统一的薄接口模式：

- 输入是结构化数据（不是自由文本 prompt）
- 输出是结构化数据（不是自由文本 response）
- 接口实现可以是硬编码逻辑，也可以是 Agent 调用
- 引擎不关心实现方式，只关心接口契约

**决策**：内部 Agent 化是 Phase 2 的设计重点。Phase 1 所有内部能力用硬编码逻辑实现。

**理由**：先稳定核心循环，再逐步将硬编码逻辑替换为 Agent 实现。接口设计时预留 Agent 化空间即可。

## 7. 与现有文档的关系

| 本篇内容 | 相关文档 | 关系 |
|---------|---------|------|
| §3 上下文组成 | session-and-storage.md | 本篇定义维度，session-and-storage.md 定义持久化格式 |
| §4 Tool 上下文 | tool-dsl.md | 本篇定义运行时可见性，tool-dsl.md 定义 DSL 形态 |
| §5 Agent-as-Subprocess | call-agent-dsl.md | 本篇定义语义模型，call-agent-dsl.md 定义 DSL 字段 |
| §6 内部 Agent 化 | hook-skill-engine.md | 本篇定义可 Agent 化的能力清单，hook-skill-engine.md 定义扩展机制 |

---

## 8. 外部调研驱动的增量设计（2026-08-01：引用式压缩双轨确认）

> 来源：agent-survey（context-mode 引用式压缩 / beads snapshot 可逆归档）。确认 nop 压缩管线的增量。

### 8.1 现状确认

nop PipelineCompactor 三层（MicroCompressionCompactor → Layer2TurnPruningStrategy → Layer3FullSummaryStrategy）+ ToolResultTruncator（offloading）已实现**摘要式压缩**（有损）与**工具输出截断**（保头保尾）。缺"引用式"（无损指针）路径。

### 8.2 引用式压缩双轨

按内容类型分流的压缩策略第二条路径。摘要式压缩（Layer 1-3）处理可概括内容（对话轮次/中间推理），引用式压缩处理保真内容（文件内容/配置/长文档原文）——后者把可定位的长内容替换为 `shortRef{type, path, range, hash}` 指针，原文入 per-session 归档，需要时通过 `read-ref` 工具按 hash 校验后读回。引用失效防护：content hash 校验，不一致 fail-loud 提示"内容已变更/引用失效"（不静默返回空或旧文）。

#### A. 内容类型路由信号源（不改 ChatMessage / nop-ai-api）

引用式策略在**策略侧推断**可保真内容，**不**给 `ChatMessage` 加 origin/content-type 字段。判定信号：

1. 角色：仅 tool-response 类消息进入引用候选；对话/推理消息仍走摘要。
2. 来源工具：tool-response 携带的工具名（`ChatToolResponseMessage.getName()`）落在"可引用工具集"（文件类工具 `read-file` / `search-*` / `grep` / `glob` 等）内才算可引用保真内容。
3. 内容长度：超过可配置阈值的长 tool result 才值得引用化（短结果直接保留）。

**拒绝了**：给 `ChatMessage` / `nop-ai-api` 加 origin/content-type 字段。理由：跨模块公共 API 扩展（Protected Area plan-first），且 tool name + 长度阈值已足够识别可保真内容，无需扩展公共契约。

#### B. 引用归档接口的模块归属

归档接口定义在 **nop-ai-toolkit**（接口消费方 = `read-ref` 工具所在模块）：read-only 视图（按 hash 读回）+ 完整视图（put 原文 + get）。实现在 nop-ai-agent/compact（agent 依赖 toolkit，可 import 接口并实现）。

**拒绝了**：接口放 nop-ai-agent（toolkit→agent 反向依赖，循环依赖）；接口放 nop-ai-api（归档非 chat API 核心概念，公共 API plan-first scope 超本计划）。

#### C. read-ref 如何到达归档（爆炸半径处理）

经 `IToolExecuteContext`（toolkit api）新增 default 方法暴露归档只读视图，**default 抛 `UnsupportedOperationException`**（参照 `ISessionStore.save`/`listAllSessions` 的 default UOE 先例；toolkit 不能 import agent 的 `NopAiAgentException`，故用 JDK 的 `UnsupportedOperationException`）。仅 `AgentToolExecuteContext`（nop-ai-agent）覆写为非空实现——从 `AgentSession` 取归档实例。其余 22 处 `IToolExecuteContext` 实现（生产 + 测试 mock）继承 default，无需逐处更新。

**拒绝了**：全量给 22 处实现加方法。理由：爆炸半径大、维护成本高，且 read-ref 只在 agent 引擎装配路径下可用，非 toolkit 通用能力。

#### D. CompactionResult 不扩展

引用归档句柄**不进** `CompactionResult`。`shortRef` 自带 hash（即读回键），`read-ref` 按 hash 直读归档，不需要结果对象转交归档句柄。`CompactionResult` 形态保持不变（与 W4-2 snapshot-archive successor 的扩展正交、无冲突）。

#### E. shortRef 结构、序列化格式、read-ref 输入契约

`shortRef` 字段（数据契约，非实现签名）：

- `type`：内容来源类型（`file` | `search` | `grep` | `glob`，对应可引用工具的归类）
- `path`：定位信息（文件路径、搜索目录等，可为空）
- `range`：范围信息（行号区间如 `1-100`，可为空）
- `hash`：内容 hash（`sha256:<hex>`），**唯一读回键**

消息内容中的序列化格式（LLM 据此调用 read-ref）：

```
[SHORT_REF type=file path=/path/to/file range=1-100 hash=sha256:<hex>]
```

单行、空格分隔、字段顺序固定。该格式可被严格解析（非 `startsWith` 模糊检测，与 Layer 3 `SUMMARY_MARKER` 用途不同——后者仅系统侧标记，前者供 LLM 生成 read-ref 工具调用）。

`read-ref` 工具 input schema 与 `shortRef` 字段一一映射：`hash`（必填，读回键）+ `type` / `path` / `range`（可选，提示性）。读回按 hash 寻址；hash 校验失败或引用不存在返回显式错误结果（"内容已变更/引用失效，请重新读取"），fail-loud，不静默返回空或旧文。

#### F. 共存与 escalation 顺序

引用式策略插入位置：**摘要式之前**（先剥离可保真内容→再摘要剩余）。`PipelineCompactor` 的 escalation 顺序：**Reference compaction（新增）→ Layer 1 micro → Layer 2 turn pruning → Layer 3 full summary**。引用式必须在 micro 之前运行——micro（`MicroCompressionCompactor`）是有损的（把旧 tool result 替换为 `[COMPRESSED ...]` 占位符，丢弃原文），若 micro 先运行会把长内容原文摧毁，引用式将无原文可归档。引用剥离后若仍超阈值，继续 escalate 到 micro / Layer 2 / Layer 3。引用式策略对"无可保真内容"返回显式 unchanged 结果（`tokensAfter==tokensBefore` + `compactedMessages` 为 null，与 PipelineCompactor 既有 skip-layer 路径兼容），不抛异常、不静默丢消息。`PipelineCompactor` 接受任意策略顺序（装配时决定），上述为推荐顺序。

#### G. 归档实例宿主与双侧 wiring

per-session 归档**实例**（非接口）宿主：`AgentSession`（per-session 生命周期天然匹配，会话结束释放；`AgentSession` 已有非 final 可变字段 + setter 先例，加 archive 字段不破坏既有约束）。

**写侧**（引用式策略 PUT 原文）：`AgentCompactionCoordinator.performCompaction` 经已持有的 `ISessionStore.get(sessionId)→AgentSession`（现成模式）取 session → 取/初始化 session 上的归档实例 → 经 `CompactionContext` 传递给策略 → 引用式策略从 context 拿到实例 PUT 原文。

**读侧**（read-ref GET 原文）：`read-ref` 工具经 `IToolExecuteContext` 的归档访问器（裁定 C）拿到归档只读视图；`AgentToolExecuteContext` 覆写该访问器，从已持有的 `AgentSession` 取归档实例。

双侧经同一 `AgentSession` 宿主共享同一实例——compact 写入 → 归档 → read-ref 读回的完整 wiring。

### 8.3 压缩前 snapshot 归档与压缩比度量（W4-2 已落地）

压缩管线补上"可逆性 + 可度量性 + 失败安全"：压缩前对整段消息历史做 snapshot 归档（按 `snapshotId` 寻址取回原文），记录两维度压缩比（消息条数维度 `originalSize`/`compactedSize` + 复用既有 token 维度 `tokensBefore`/`tokensAfter`），并在压缩失败时显式保留原文 archive + 显式记录失败（fail-loud，非静默）。覆盖摘要式与引用式（§8.2）两类压缩——管线级横切增强，不绑定具体策略。

**边界声明（修正本节旧文 :258 自相矛盾）**：归档 **≠** checkpoint `snapshot.json`。旧文「归档即 checkpoint 的 compaction 类型」与本计划核心边界直接冲突，现改正为：压缩前 snapshot 归档是**压缩管线内部**的原文副本（供可回溯 / 失败安全，per-compaction-event、`snapshotId` 寻址、in-session 内存、会话级释放）；checkpoint `snapshot.json`（reliability §5.4 / §5.4a）是 **resume-point 持久化缓存**（crash/restart restore 用，journal.md + snapshot.json 双文件、跨进程持久化）。两者是**两个独立关注点**——归档服务于"压缩可回溯 + 失败安全"，checkpoint snapshot.json 服务于"崩溃恢复"。compaction-triggered snapshot.json 文件生成仍是 reliability §5.4 独立 successor，本节归档不产生 checkpoint 子系统的 snapshot.json 文件。

```
压缩流程（增强后）：
  1. PRE_COMPACT hook
  2. SnapshotArchive.put(整段 messages) → snapshotId        ← 压缩前归档原文
  3. new CompactionContext(messages, ..., snapshotId)        ← snapshotId 经 context 流入管线
  4. contextCompactor.compact(ctx) → CompactionResult        ← try-catch 包裹（失败保留 archive）
  5. CompactionResult 携带 snapshotId + originalSize/compactedSize  ← PipelineCompactor 唯一构造点填写
  6. 成功：替换 messages + COMPACTION checkpoint（compactSummary 含 snapshotId + 两维度压缩比）
     失败：保留 archive + LOG.warn（含 snapshotId），不静默吞
```

#### 裁定 A — snapshotId 数据流

`snapshotId` 单向数据流：coordinator 在 `compact()` **前** archive 原文 → 产出 `snapshotId` → 经 **`CompactionContext` 新增 `snapshotId` 字段**（`PipelineCompactor.rebuildContext` 透传该字段）传入 → `PipelineCompactor` 在最终结果唯一构造点（success / no-reduction 两分支）把 `ctx.getSnapshotId()` + `originalSize`/`compactedSize` 填入 `CompactionResult`。**不动 strategy 内部的 `CompactionResult` 构造**——strategy 产中间结果，`PipelineCompactor` 在唯一构造点重新构造最终结果为权威。

**拒绝了**：(1) 让 strategy 各自读归档（爆炸半径大、策略须感知归档）；(2) coordinator 在 `compact()` 后改 `CompactionResult` 的 final 字段（不可变，无法后置 setter）。

#### 裁定 B — 归档存储边界与命名空间

归档接口与首版实现均在 **nop-ai-agent 的 session 包**（归档对象 = `List<ChatMessage>`，session 级；无 toolkit 消费方，不进 toolkit）。归档键 = `snapshotId`（per-compaction-event 寻址，**非** content hash）。首版 in-session 内存实现 + 会话级释放（`AgentSession` 持实例，lazy init，与 §8.2 引用归档平行）。生命周期 = 会话级（session 结束归档 GC）。

**与 checkpoint `snapshot.json` 的明确边界**：归档 = 压缩管线内部原文副本（可回溯 / 失败安全）；checkpoint snapshot.json = resume-point 持久化缓存（reliability §5.4 独立 successor）。

**三套 snapshotId 命名空间（须区分，不复用）**：(1) `CheckpointSnapshot.snapshotId`（reliability，crash/restart restore 用）；(2) `SessionSnapshot.snapshotId`（session-and-storage，session 快照重建用）；(3) 本节引入的 compaction-archive `snapshotId`（压缩管线内部、per-compaction-event 整段历史归档的读回键，形如 `snap:<sessionId>:<ts>:<n>`）。三者独立，design 文档化以避免混淆。

**拒绝了**：复用 §8.2 的 `ICompactionArchive`（hash 寻址 per-content）——本节归档是 per-compaction-event 整段历史，寻址模型不同，独立接口。

#### 裁定 C — 修正旧文自相矛盾（强制）

旧文 §8.3 末行「归档即 checkpoint 的 compaction 类型（与 append-only 天然一致）」与本节核心边界冲突，**已改正**为本节开头的"边界声明"：归档 ≠ checkpoint snapshot.json。这是本节最先落地的裁定。

#### 裁定 D — 度量维度与字段关系

新增 `originalSize`（压缩前消息条数）/ `compactedSize`（压缩后消息条数）作为**消息条数维度的权威度量**；既有 `retainedMessageCount`（语义含混：成功路径 = 压缩后条数、no-op 路径 = 原始条数）**保留为向后兼容的 legacy 字段**，权威性让位于 `originalSize`/`compactedSize`。压缩比两维度各一：消息条数维度 = `compactedSize/originalSize`，token 维度 = `tokensAfter/tokensBefore`（既有，行为不变）。

`CompactionResult` 新增 `originalSize`/`compactedSize` 字段（`final`），既有 5 参/6 参构造器保留并向后兼容（新字段 default = `retainedMessageCount`，作为 caller 未区分时的 best-effort proxy）；新增 8 参构造器供 `PipelineCompactor` 唯一构造点填入正确区分值。`equals`/`hashCode`/`toString` 已同步包含新字段。

**拒绝了**：废弃 `retainedMessageCount`（coordinator `compactSummary` + 多个测试消费，破坏面大）；并存但不声明权威性（冗余且含混）。裁定 = 新字段权威、旧字段 legacy alias、关系显式文档化。

#### 裁定 E — 归档时机与构造顺序

archive 时机：`PRE_COMPACT` hook **之后**、`contextCompactor.compact()` **之前** archive 原文（保证压缩失败时原文副本已存在）。构造顺序：coordinator 在 archive **之后** new `CompactionContext`（snapshotId 是 final 字段，不可后置 setter，必须在构造时传入），即把 CompactionContext 构造从 archive 前移到 archive 后。

#### 裁定 F — 失败记录层级（厘清两层）

**coordinator 层**补三处（既有均为静默）：
1. `compact()` 调用加 **try-catch**（非 PipelineCompactor 的自定义 compactor 抛异常 → 捕获 → 保留 archive + `LOG.warn` 含 `snapshotId`，不冒泡中断 agent）。
2. `compactedMessages == null` 分支（全 strategy 未产出）补 `LOG.warn`（含 `snapshotId` + 原因）。
3. `compactedMessages` 非 null 但 `tokensAfter >= tokensBefore`（压缩尝试但未减 token）补 `LOG.warn`（含 `snapshotId`）。

区分两类："archive 不可用 / 未触发压缩"（snapshotId=null，不 archive、不记录失败——非异常）与"archive 后压缩未产出 / 未减"（archive 保留 + 显式失败记录）。**策略层**（`PipelineCompactor:96-100`）对单 strategy 异常已有 `LOG.warn` + `continue`，不动。

**拒绝了**：吞异常不记录（违反 Minimum Rules #24 静默跳过禁令）。

#### 裁定 G — 向后兼容

`snapshotId`/`originalSize`/`compactedSize` 新字段对既有消费方（`AgentCompactionCoordinator`、测试）保持兼容；既有 5 参/6 参构造器不破坏；`NoOpContextCompactor` 仍返回 `snapshotId=null`（NoOp = 无压缩 = 无归档，`TestNoOpContextCompactor` 断言 null 不破）。`PipelineCompactor` 的 edge 路径（messages 空 `:62-64` / strategies 空走 NoOp `:66-70`）按 NoOp 语义返回 `snapshotId=null`——archive 的 `snapshotId` 不进这些 edge 结果对象（coordinator LOG 仍可观测），只有管线完整跑完的唯一构造点（success/no-reduction）携带非 null `snapshotId`。
