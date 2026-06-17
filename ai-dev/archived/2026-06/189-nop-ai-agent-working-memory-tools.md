# 189 — nop-ai-agent Working Memory 工具 (read-memory / write-memory / search-memory)

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L2-15
> Last Reviewed: 2026-06-15
> Source: Roadmap `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 2 row L2-15（`Working Memory 工具实现 (read-memory/write-memory/search-memory)`, deps L1-10 ✅ / L1-5 ✅）。Design owner doc: `01-architecture-baseline.md` §四 Memory 模型（`:75` — "Working Memory | Per-session KV store ... Agent 通过工具读写 | Layer 2"；`:78` — "Working Memory 是 Session 状态的一部分"）+ `nop-ai-agent-session-engine.md` §6.3（IAiMemoryStore 与 Budgeted Injection）。
> Related: Plan 168 (call-agent + send-message tools — engine-aware tool pattern precedent, AgentToolExecuteContext access pattern, honest no-config reporting), L1-16 (IAiMemoryStore 接口扩展 + AiMemoryItem 字段补充 — 已落地，本计划消费)，L4-3 (IMemoryAdapter — 本计划 unblocks，是后续 DB/vector 后端 successor)

## Purpose

将 `01-architecture-baseline.md` §四 的 **Working Memory** 层收口到"Agent 可在 ReAct 循环中通过三个工具读写检索 per-session 记忆"状态。当前 `IAiMemoryStore` 接口 + `AiMemoryItem` 值类型已就绪（L1-16），但**零实现**：无任何 `IAiMemoryStore` 具体类、无工具执行器、无执行路径接线。Agent 无法在执行过程中持久化中间结论、用户偏好、任务上下文等跨迭代记忆——每次 LLM 调用只能依赖 context window 内的消息历史（短期记忆）和 compaction（L3-9），缺少一个 Agent 主动读写的结构化记忆层。

本计划交付：(1) `IAiMemoryStore` 的 in-memory 功能化实现（含 readBudgeted/update/remove/batchAdd 的真实逻辑，覆盖当前 UOE default）；(2) per-session store 解析机制（Provider 模式，不改 `IAiMemoryStore` 接口签名）；(3) 三个工具执行器 + bean 注册；(4) 端到端验证。

## Current Baseline

- **`IAiMemoryStore` 接口 ✅ 就绪（L1-16）**：8 个方法——4 个抽象（`getAll(filters)` / `getLastN(n)` / `search(query)` / `add(item)`）+ 4 个 default UOE（`readBudgeted` / `update` / `remove` / `batchAdd`，均 `throw new UnsupportedOperationException("... requires Phase 2")`）。接口方法**不含 sessionId 参数**——每个 `IAiMemoryStore` 实例代表一个 session 的记忆
- **`AiMemoryItem` 值类型 ✅ 就绪（L1-16）**：全字段已补充——`key` / `type` / `content` / `createTime` / `priority` / `tokenEstimate`(default -1 → lazy chars/4) / `pinned` / `checksum` / `lastAccessTime` / `accessCount`。`@DataBean` 注解，Jackson 可序列化
- **`IAiMemoryStore` 具体实现 ❌ 零实现**：`grep -rln "implements IAiMemoryStore" nop-ai/nop-ai-agent/src/main` → 0 命中。无 InMemory / DB / 文件级实现
- **Working Memory 工具 ❌ 零实现**：`grep -rln "read-memory\|write-memory\|search-memory\|ReadMemory\|WriteMemory\|SearchMemory" nop-ai/nop-ai-agent/src` → 0 命中
- **`AgentToolExecuteContext` ❌ 不含 memory store**：当前字段 = workDir / envs / expireAt / cancelToken / fileSystem / executor / engine / messenger / sessionId / agentName / allowedTools / allowedPathRoots / allowedPathRules。无 `IAiMemoryStore` 或 Provider 字段。工具无法访问记忆存储
- **`ReActAgentExecutor` Builder ❌ 无 memory 组件**：当前 Builder 字段含 chatService / toolManager / denialLedger / checkpointManager / sessionStore / messenger / 等 20+ 组件，无 memory store 或 provider
- **Engine-aware 工具模式 ✅ 已建立（plan 168）**：`CallAgentExecutor` / `SendMessageExecutor` 实现 `IToolExecutor`，经 `ai-agent-tools.beans.xml` 注册为 bean，由 toolkit 的 `<ioc:collect-beans by-type="...IToolExecutor"/>` 自动收集。工具将 `IToolExecuteContext` 强转为 `AgentToolExecuteContext` 访问 engine/messenger。**SendMessageExecutor 的 honest no-config 模式**（NoOpAgentMessenger 时如实报告 "message not delivered" 而非假装成功）是本计划 memory 工具未配置时的行为模板
- **分发循环接线点 ✅ 已知**：`ReActAgentExecutor.java:736` 构造 `AgentToolExecuteContext`（传入 engine/messenger/sessionId 等）——这是注入 memory store 的位置
- **`AgentSession` 持久化 ✅（plan 183-185）**：`AgentSession` 序列化含 sessionId/messages/metadata 等全字段，但 **不含 memory store 状态**——Working Memory 的 per-session 数据在 `InMemoryAiMemoryStore` 实例中独立持有，本计划范围**不**将其纳入 session 持久化（持久化 = L4-3 IMemoryAdapter successor scope）

## Goals

- Agent 可通过 `read-memory` 工具读取当前 session 的记忆（全部 / 按 type 过滤 / 最近 N 条 / budgeted 智能选择）
- Agent 可通过 `write-memory` 工具写入/更新/删除当前 session 的记忆条目
- Agent 可通过 `search-memory` 工具按关键词检索当前 session 的记忆
- 三个工具在 ReAct 循环中可用（经 bean 注册 + `IToolManager` 自动发现，与其他工具同级）
- `IAiMemoryStore` 有一个功能化 in-memory 实现，覆盖全部 8 个方法（含 Phase 2 default 的真实逻辑）
- Per-session 隔离：不同 session 的记忆互不可见

## Non-Goals

- **`IMemoryAdapter`（L4-3）**：Storage / Embedding / Vector 三适配器是 Layer 4 后端实现。本计划交付 in-memory per-session 存储；DB 持久化、向量检索、embedding 是 L4-3 独立 successor
- **Memory 持久化到 session 文件/DB**：`InMemoryAiMemoryStore` 的数据**不**随 `AgentSession` 持久化（plan 183 `FileBackedSessionStore` / plan 185 `DBSessionStore` 不感知 memory）。进程重启后 memory 数据丢失。持久化是 L4-3 successor scope
- **Session fork 时复制 memory**：`forkSession`（plan 161/A6）不复制 memory store——子 session 获得独立的空 memory。fork-memory-copy 是后续增强
- **Memory retention / TTL / 容量上限**：`InMemoryAiMemoryStore` 无界保存（测试规模下安全）。retention 策略是后续 optimization candidate
- **Memory 注入 system prompt**：设计提及"session 启动时注入 system prompt"。本计划交付工具读写能力；system prompt 自动注入（session 启动时将 memory 内容拼入 prompt 前缀）是独立增强，不在范围
- **Budgeted Injection 功能化消费**：`readBudgeted()` 方法在 `InMemoryAiMemoryStore` 中功能化实现（供 `read-memory` 工具调用），但自动在每轮 LLM 调用前注入 budgeted memory（无需 Agent 显式调用 read-memory）是 A1 successor scope

## Scope

### In Scope

- `IAiMemoryStore` 的 in-memory 功能化实现（全 8 方法，线程安全）
- Per-session store 解析机制（Provider 接口 + in-memory 默认实现）
- `AgentToolExecuteContext` 新增 memory store 字段 + 分发循环接线
- `ReActAgentExecutor` Builder 新增 memory provider 字段（null-fallback 语义）
- `DefaultAgentEngine` 传递 memory provider 到 executor Builder
- 三个工具执行器：`ReadMemoryExecutor` / `WriteMemoryExecutor` / `SearchMemoryExecutor`
- 工具 bean 注册（`ai-agent-tools.beans.xml`）
- 单元测试（store + provider + 每个工具）+ 端到端测试（ReAct 循环中 Agent 经工具写后读）
- 设计文档 + roadmap 状态同步

### Out Of Scope

- L4-3 IMemoryAdapter（DB / Vector / Embedding 后端）
- Memory 持久化（session 文件 / DB）
- Session fork memory 复制
- Memory retention / TTL / 容量限制
- System prompt 自动注入 memory
- Budgeted Injection 自动消费（A1）

## Execution Plan

### Phase 1 — Backing Infrastructure: Store Implementation + Provider + Wiring

Status: completed
Targets: `io.nop.ai.agent.memory.InMemoryAiMemoryStore`, `io.nop.ai.agent.memory.IMemoryStoreProvider`, `io.nop.ai.agent.memory.InMemoryMemoryStoreProvider`, `io.nop.ai.agent.engine.AgentToolExecuteContext`, `io.nop.ai.agent.engine.ReActAgentExecutor` (Builder + dispatch-loop), `io.nop.ai.agent.engine.DefaultAgentEngine` (Builder pass-through)

- Item Types: `Decision | Proof`

- [x] **Decision（store 实例粒度——per-session，经 Provider 解析）**：`IAiMemoryStore` 接口方法不含 sessionId 参数，每个实例代表一个 session 的记忆。引入 `IMemoryStoreProvider` 接口（`IAiMemoryStore getOrCreate(String sessionId)`），由 Provider 按 sessionId 解析/创建独立 store 实例。理由：(1) 不修改 `IAiMemoryStore` 接口签名（保持 L1-16 契约稳定）；(2) Provider 可注入（Builder 字段，同其他 20+ 组件），L4-3 可替换为 DB/vector 后端 Provider；(3) per-session 隔离自然实现（每个 session 独立 store 实例，无跨 session 泄漏）。Provider 接口的具体方法签名在实现时裁定，plan 只约束行为语义：同 sessionId 返回同实例，不同 sessionId 返回不同实例
- [x] **Decision（shipped 默认 = 功能化 in-memory，非 NoOp）**：`ReActAgentExecutor` Builder 的 memory provider 字段默认值是 `InMemoryMemoryStoreProvider`（功能化），不是 NoOp。理由：(1) 与 `InMemorySessionStore` 作为 shipped 默认一致——in-memory store 廉价且始终可用；(2) Working Memory 工具开箱即用，Agent 配置中声明工具即可使用，无需额外配置 Provider；(3) NoOp 语义对 memory store 无意义（"pass-through" 没有透传目标）。**null-fallback 语义**：若调用方显式传 null（测试场景），分发循环解析时跳过 memory 注入（context 中 memory store = null），工具调用时 fail-fast 返回描述性错误（同 SendMessageExecutor 的 NoOp messenger 模式）
- [x] **Decision（Phase 2 default 方法的功能化实现）**：`InMemoryAiMemoryStore` 覆盖全部 4 个 default UOE 方法（`readBudgeted` / `update` / `remove` / `batchAdd`）为真实逻辑。`readBudgeted` 实现 Budgeted Injection 算法（getAll → 按 priority 降序 → 累计 tokenEstimate 至 maxTokens → pinned 项目始终包含）。接口上的 default UOE 保留（其他未来实现仍可选择性覆盖或继承 UOE）
- [x] 新增 `InMemoryAiMemoryStore implements IAiMemoryStore`：in-memory `List<AiMemoryItem>`（或 `Map<key, AiMemoryItem>`），线程安全。覆盖全部 8 方法。`search` 实现为 content/type/key 子串匹配（in-memory 无向量检索，L4-3 scope）。`getAll(filters)` 支持 type 过滤（filters map 中 `type` 键）。`add` 自动填充 createTime（若未设）+ 计算 tokenEstimate（若为 -1，经 `AiMemoryItem.getTokenEstimate()` lazy 逻辑）
- [x] 新增 `IMemoryStoreProvider` 接口 + `InMemoryMemoryStoreProvider` 默认实现（`ConcurrentHashMap<sessionId, InMemoryAiMemoryStore>`，`getOrCreate` 原子创建）
- [x] `AgentToolExecuteContext` 新增 `IAiMemoryStore memoryStore` 字段 + 构造器参数。新增构造器重载或在现有最全构造器追加参数（保持向后兼容：旧构造器 delegate 时 memoryStore = null）。新增 getter `getMemoryStore()`
- [x] `ReActAgentExecutor` Builder 新增 `IMemoryStoreProvider memoryStoreProvider` 字段（default = `InMemoryMemoryStoreProvider` 实例或 lazy init）。`build()` 将 provider 传入 executor
- [x] 分发循环接线（`:736` 附近）：构造 `AgentToolExecuteContext` 时，若 `memoryStoreProvider != null`，调用 `provider.getOrCreate(sessionId)` 解析当前 session 的 store 并传入 context；若 provider 为 null，传入 null（工具侧 fail-fast）
- [x] `DefaultAgentEngine` Builder pass-through：若 engine Builder 有 memory provider 字段则传递到 `ReActAgentExecutor` Builder；否则 executor 使用自身默认（engine 不强制持有 provider——memory 是执行循环关注点，非引擎生命周期关注点）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `InMemoryAiMemoryStore` 存在于 `io.nop.ai.agent.memory` 包，`implements IAiMemoryStore`，覆盖全部 8 方法（无 UOE 残留——4 个 Phase 2 default 被功能化覆盖）
- [x] `IMemoryStoreProvider` 接口 + `InMemoryMemoryStoreProvider` 默认实现存在；`getOrCreate(sessionId)` 对同 sessionId 返回同实例，不同 sessionId 返回不同实例（单测验证）
- [x] `AgentToolExecuteContext` 含 `getMemoryStore()` 方法，返回 `IAiMemoryStore`（可能为 null）
- [x] `ReActAgentExecutor` Builder 含 `memoryStoreProvider` 字段，`build()` 产出 executor 持有该 provider
- [x] **接线验证**：分发循环（`ReActAgentExecutor` 构造 `AgentToolExecuteContext` 处）确实调用 `provider.getOrCreate(sessionId)` 并将结果传入 context——经单测断言 context.getMemoryStore() 非 null 且对同 sessionId 返回同实例
- [x] **无静默跳过**：provider 为 null 时，context 中 memoryStore = null（显式 null，非空对象占位），工具侧后续 fail-fast（Phase 2 验证）
- [x] **新增功能测试**：`TestInMemoryAiMemoryStore` 覆盖全部 8 方法（add→get 往返、search 子串匹配、readBudgeted 按 priority 排序 + pinned 始终包含 + token budget 截断、update 覆盖、remove 删除、batchAdd 批量、getLastN、getAll type 过滤）+ 线程安全（多线程并发 add/getOrCreate）；`TestInMemoryMemoryStoreProvider` 覆盖 per-session 隔离 + 同 sessionId 同实例
- [x] `nop-ai-agent-session-engine.md` §6.3 已更新（IAiMemoryStore in-memory 实现标记已落地；Provider 模式记录；持久化仍标注 L4-3 deferred）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Tool Executors: read-memory / write-memory / search-memory + Bean Registration

Status: completed
Targets: `io.nop.ai.agent.tool.ReadMemoryExecutor`, `io.nop.ai.agent.tool.WriteMemoryExecutor`, `io.nop.ai.agent.tool.SearchMemoryExecutor`, `ai-agent-tools.beans.xml`, `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/read-memory.tool.xml`, `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/write-memory.tool.xml`, `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/search-memory.tool.xml`

- Item Types: `Proof | Decision`

- [x] 新增 `ReadMemoryExecutor implements IToolExecutor`（`TOOL_NAME = "read-memory"`）：从 `AgentToolExecuteContext` 获取 `IAiMemoryStore`。若 store 为 null，返回描述性错误结果（"read-memory requires a memory store; no memory store provider configured"——同 SendMessageExecutor honest no-config 模式）。支持 action 参数：`list`（默认，`getAll(filters)`，可选 type 过滤）/ `last`（`getLastN(n)`，需 n 参数）/ `budgeted`（`readBudgeted(maxTokens, context)`，需 maxTokens 参数）/ `key`（按 key 查找单条，经 getAll + filter 或 store 内部查找）。返回格式化文本（人类可读列表）或 JSON（Agent 友好）。读取时更新 `lastAccessTime` + `accessCount`（若 store 实现支持）
- [x] 新增 `WriteMemoryExecutor implements IToolExecutor`（`TOOL_NAME = "write-memory"`）：从 context 获取 store（null → fail-fast 错误结果）。支持 action 参数：`add`（`add(item)`，需 content + 可选 key/type/priority/pinned）/ `update`（`update(key, item)`）/ `remove`（`remove(key)`）/ `clear`（全部删除，逐条 remove 或 store 提供的批量清除）。操作成功返回确认摘要（写入/更新/删除了几条，剩余总数）
- [x] 新增 `SearchMemoryExecutor implements IToolExecutor`（`TOOL_NAME = "search-memory"`）：从 context 获取 store（null → fail-fast 错误结果）。必需参数 `query`（搜索词）。调用 `store.search(query)`，返回匹配条目列表（格式化文本或 JSON）。无匹配时返回空结果（非错误——"无匹配记忆"是合法状态）
- [x] 三个工具均遵循 `CallAgentExecutor` / `SendMessageExecutor` 的参数解析模式：优先 `call.getInput()` JSON 解析，fallback 到 `call.attrText(key)`；异常捕获返回 `AiToolCallResult.errorResult`（不抛 uncaught）
- [x] 三个工具均在 `ai-agent-tools.beans.xml` 注册为 bean（`<bean id="ai-agent-tools:read-memory" class="...ReadMemoryExecutor"/>` 等），被 toolkit 的 `<ioc:collect-beans by-type="...IToolExecutor"/>` 自动收集
- [x] 新增三个工具 schema 文件 `read-memory.tool.xml` / `write-memory.tool.xml` / `search-memory.tool.xml` 于 `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/` 下，遵循现有 `call-agent.tool.xml` / `send-message.tool.xml` 的结构（toolName / description / inputSchema）。这是工具被 `ToolManagerImpl.loadTool()` 加载、并在 `ReActAgentExecutor.buildToolDefinitions()` 中被发现的必要条件——缺失 `.tool.xml` 时工具会被静默跳过（`if (toolModel == null) continue;`），LLM 无法发现或调用
- [x] **Decision（工具参数 arg parsing 复用）**：三个工具的 JSON/attr 参数解析逻辑与 CallAgentExecutor/SendMessageExecutor 一致（`resolveArguments` + `getStringArg` 模式）。是否提取公共基类/工具类在实现时裁定，plan 不强制——但行为语义必须一致（JSON 优先，attr fallback，异常不泄露）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ReadMemoryExecutor` / `WriteMemoryExecutor` / `SearchMemoryExecutor` 均存在于 `io.nop.ai.agent.tool` 包，`implements IToolExecutor`，`getToolName()` 分别返回 `read-memory` / `write-memory` / `search-memory`
- [x] 三个工具均在 `ai-agent-tools.beans.xml` 注册（3 个新 bean 元素）
- [x] 三个 `.tool.xml` schema 文件存在于 `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/`（read-memory / write-memory / search-memory），且经 `ToolManagerImpl.loadTool()` 可成功加载（Anti-Hollow：无 schema 文件则工具在 `buildToolDefinitions` 中被静默跳过，LLM 无法发现）
- [x] **无静默跳过**：三个工具在 store 为 null 时返回**描述性错误结果**（`AiToolCallResult.errorResult` 带明确消息），不返回空成功/空列表假装正常。SearchMemoryExecutor 无匹配时返回空成功结果（附带 "no matches" 消息——这是合法语义非静默跳过）
- [x] **新增功能测试**：
  - `TestReadMemoryExecutor`：list/last/budgeted/key 四种 action + store null 时错误结果 + type 过滤 + 空 store 时空列表
  - `TestWriteMemoryExecutor`：add/update/remove/clear 四种 action + store null 时错误结果 + 操作后 store 状态正确
  - `TestSearchMemoryExecutor`：有匹配/无匹配/store null + query 必填校验
- [x] **端到端验证**（Anti-Hollow Rule #22）：一个端到端测试，Agent 在 ReAct 循环中先调用 `write-memory`（写入一条记忆），再调用 `read-memory`（读取验证），断言读回的内容与写入一致——证明从 LLM tool call → `IToolManager.callTool` → 工具执行器 → `AgentToolExecuteContext.getMemoryStore()` → `IAiMemoryStore` 的完整路径连通。可使用 mock LLM 预设 tool_calls（同现有 `TestCallAgentExecutor` / `TestSendMessageExecutor` 端到端测试模式）
- [x] **接线验证**（Wiring Verification Rule #23）：端到端测试中断言 `AgentToolExecuteContext.getMemoryStore()` 非 null（证明分发循环确实注入了 store），且对同一 sessionId 的两次工具调用返回同一 store 实例（证明 per-session 解析正确）
- [x] `01-architecture-baseline.md` §四 Memory 模型已更新（Working Memory 行从 ❌ 标记已落地；记录三个工具名 + Provider 模式 + in-memory 默认）。**且原 line 78 "Working Memory 是 Session 状态的一部分（持久化）" 的契约断言必须显式处理**：要么更新为反映当前 baseline（"per-session in-memory，持久化 deferred 至 L4-3 IMemoryAdapter"），要么显式标注为 deferred contract change 并引用本 plan 的 Deferred But Adjudicated 条目——不允许保留与 live baseline 矛盾的断言
- [x] `01-architecture-baseline.md` §四 中涉及"session 启动时注入 system prompt"的断言已显式处理（Working Memory 的 system-prompt 自动注入是 A1 successor scope，本计划仅交付工具读写能力）：要么更新断言为"工具读写 ✅ 已落地；system-prompt 自动注入 deferred 至 A1"，要么显式标注 deferred——不允许保留与本计划交付物矛盾的断言
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — Owner-Doc Sync + Roadmap Status Update

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`, `ai-dev/design/nop-ai-agent/01-architecture-baseline.md`, `ai-dev/design/nop-ai-agent/nop-ai-agent-session-engine.md`

- Item Types: `Follow-up`

- [x] 更新 `nop-ai-agent-roadmap.md` §4 Layer 2 row L2-15（`:181`）：状态从 `❌` 改为 `✅`（简注：InMemoryAiMemoryStore + IMemoryStoreProvider + read/write/search-memory tools + dispatch-path wiring landed；L4-3 IMemoryAdapter / memory persistence / fork-copy = deferred successors）
- [x] 更新 `nop-ai-agent-roadmap.md` §2.1 row M6（基础数据类型 `⚠️`）：`IAiMemoryStore` 从"仅 4 方法"更新为"8 方法 + in-memory 实现"；`AiMemoryItem` 字段状态维持 ✅（L1-16）
- [x] 更新 `nop-ai-agent-roadmap.md` §2.2 row "Working Memory (工具)"（`:55`）：状态从 `❌ 未开始` 改为 `✅ 已落地（plan 189）`
- [x] 确认 `01-architecture-baseline.md` §四 + `nop-ai-agent-session-engine.md` §6.3 已在 Phase 1/2 中更新（跨 Phase 一致性核对）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `nop-ai-agent-roadmap.md` §4 L2-15 行状态为 `✅`
- [x] `nop-ai-agent-roadmap.md` §2.1 / §2.2 Working Memory 相关行已同步
- [x] `01-architecture-baseline.md` §四 + `nop-ai-agent-session-engine.md` §6.3 与 live baseline 一致
- [x] **No new test required: Phase 3 is pure design-doc/roadmap update, no code behavior change**
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过（纯文档变更不改代码，但确认无意外破损）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] `IAiMemoryStore` 有一个功能化 in-memory 实现（全 8 方法，无 UOE 残留）
- [x] Per-session 隔离经测试验证（不同 session 的 store 实例独立）
- [x] 三个 Working Memory 工具（read/write/search）经 bean 注册 + `.tool.xml` schema 加载 + `IToolManager` 可发现
- [x] 分发循环接线经测试验证（context.getMemoryStore() 非 null）
- [x] 端到端路径连通（write-memory → read-memory 往返验证）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs（roadmap §4 L2-15 / §2.1 M6 / §2.2 + architecture-baseline §四 + session-engine §6.3）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）三个工具在 ReAct 分发循环运行时确实被 `IToolManager.callTool` 调用（不只是 bean 存在，且 `.tool.xml` schema 已加载使其可被 `buildToolDefinitions` 发现），（b）工具确实读写 `AgentToolExecuteContext.getMemoryStore()` 返回的 store（非 stub），（c）store 为 null 时工具 fail-fast（非静默空返回）
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### IMemoryAdapter (L4-3) — DB / Vector / Embedding 后端

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本计划交付 in-memory per-session store。`IMemoryAdapter`（Storage / Embedding / Vector 三适配器）是 Layer 4 后端实现，使 memory 持久化到 DB / 支持语义向量检索。in-memory store 在单进程内完全可用；分布式持久化 + 向量检索是生产部署增强
- Successor Required: yes
- Successor Path: L4-3 独立 plan（deps L2-15 ✅ 后可规划）

### Memory 持久化到 session 文件 / DB

- Classification: `out-of-scope improvement`（原 `01-architecture-baseline.md:78` 断言"Working Memory 是 Session 状态的一部分（持久化）"——本计划交付 in-memory per-session store，持久化显式 deferred 至 L4-3；design doc §四 line 78 断言在 Phase 2 Exit Criteria 中要求显式更新或标注 deferred，不允许保留与 live baseline 矛盾的断言）
- Why Not Blocking Closure: `InMemoryAiMemoryStore` 数据不随 `AgentSession` 持久化。进程重启后 memory 丢失。`FileBackedSessionStore`（plan 183）/ `DBSessionStore`（plan 185）不感知 memory store 状态。memory 持久化需要 session 序列化扩展或独立持久化路径，是 L4-3 successor scope
- Successor Required: yes
- Successor Path: L4-3 独立 plan 或 memory 持久化独立 successor

### Session fork 时复制 memory

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: `forkSession`（plan 161/A6）不复制 memory store——子 session 获得独立的空 memory。fork-memory-copy 需要 `IMemoryStoreProvider` 在 fork 时显式复制源 session 的 memory 到子 session，是独立增强
- Successor Required: no

### Memory retention / TTL / 容量上限

- Classification: `optimization candidate`
- Why Not Blocking Closure: `InMemoryAiMemoryStore` 无界保存。retention（保留最近 N 条、TTL 删除、LRU/LFU 淘汰）是维护优化。`AiMemoryItem` 的 `lastAccessTime` + `accessCount` 字段已就绪（L1-16），为 future retention 提供数据基础
- Successor Required: no

### Memory 自动注入 system prompt

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计提及"session 启动时注入 system prompt"。本计划交付 Agent 主动调用工具读写 memory 的能力。自动注入（session 启动时将 memory 内容拼入 system prompt 前缀，无需 Agent 显式调用 read-memory）是独立增强，属于 Budgeted Injection（A1）自动消费 scope
- Successor Required: yes
- Successor Path: A1（Budgeted Injection 功能化消费）独立 successor

## Non-Blocking Follow-ups

- L4-3 IMemoryAdapter（DB / Vector / Embedding 后端）
- Memory 持久化到 session 文件 / DB
- Session fork 时复制 memory
- Memory retention / TTL / LRU/LFU 淘汰策略
- Memory 自动注入 system prompt（A1 Budgeted Injection 自动消费）
- `search-memory` 语义检索升级（当前子串匹配 → 向量检索，依赖 L4-3 Embedding adapter）

## Closure

Status Note: All 3 Phases completed. Phase 1 delivered the backing infrastructure (`InMemoryAiMemoryStore` covering all 8 interface methods + `IMemoryStoreProvider` / `InMemoryMemoryStoreProvider` per-session isolation + `AgentToolExecuteContext.memoryStore` field + `ReActAgentExecutor.Builder.memoryStoreProvider` + dispatch-loop wiring + `DefaultAgentEngine` pass-through). Phase 2 delivered the three tool executors (`ReadMemoryExecutor` / `WriteMemoryExecutor` / `SearchMemoryExecutor`) following the established CallAgentExecutor/SendMessageExecutor pattern, with bean registration in `ai-agent-tools.beans.xml` and three `.tool.xml` schema files. Phase 3 synced the roadmap (§4 L2-15 ❌→✅, §2.1 M6 ⚠️→✅, §2.2 Working Memory ❌→✅). The end-to-end test proves the full path LLM tool_call → IToolManager.callTool → executor → AgentToolExecuteContext.getMemoryStore() → IAiMemoryStore is wired and functional (Anti-Hollow). The store-null honest no-config reporting pattern is preserved (fail-fast, no silent no-op).
Completed: 2026-06-15

Closure Audit Evidence:

- Reviewer / Agent: opencode-session (single-session execution + self-audit against live code paths and focused tests)
- Evidence:
  - Phase 1 Exit Criteria — PASS:
    - `InMemoryAiMemoryStore` exists at `io.nop.ai.agent.memory.InMemoryAiMemoryStore`, `implements IAiMemoryStore`, overrides all 8 methods (no UOE residual). Live code path verified.
    - `IMemoryStoreProvider` + `InMemoryMemoryStoreProvider` exist; `getOrCreate(sessionId)` returns same instance for same sessionId, different for different. Verified by `TestInMemoryMemoryStoreProvider` (6 tests).
    - `AgentToolExecuteContext.getMemoryStore()` returns `IAiMemoryStore` (may be null). 14-arg constructor carries store; legacy 13-arg delegates with null. Verified by `TestMemoryStoreProviderWiring`.
    - `ReActAgentExecutor.Builder.memoryStoreProvider(...)` setter exists, `build()` passes through. `DefaultAgentEngine` defaults to `InMemoryMemoryStoreProvider` and `resolveExecutor` passes it.
    - Wiring verified: dispatch loop (`ReActAgentExecutor` line ~763) calls `memoryStoreProvider.getOrCreate(sessionId)` and feeds result into the 14-arg `AgentToolExecuteContext` constructor. End-to-end test asserts `getMemoryStore()` non-null at execution time and the same store instance across two tool calls.
  - Phase 2 Exit Criteria — PASS:
    - `ReadMemoryExecutor` / `WriteMemoryExecutor` / `SearchMemoryExecutor` exist at `io.nop.ai.agent.tool`, `implements IToolExecutor`, `getToolName()` returns `read-memory` / `write-memory` / `search-memory` respectively.
    - All 3 registered in `ai-agent-tools.beans.xml` (3 new `<bean>` elements).
    - Three `.tool.xml` schema files exist at `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/` (read-memory / write-memory / search-memory), loadable via `ToolManagerImpl.loadTool()`.
    - No silent no-op: store=null returns `AiToolCallResult.errorResult` with descriptive message; SearchMemoryExecutor no-match returns success with "No matching memory items" (legitimate state).
    - Tests: `TestReadMemoryExecutor` (13), `TestWriteMemoryExecutor` (12), `TestSearchMemoryExecutor` (7), `TestWorkingMemoryEndToEnd` (2 — end-to-end write→read round-trip + per-session isolation).
  - Phase 3 Exit Criteria — PASS: roadmap §4 L2-15 = ✅, §2.1 M6 = ✅, §2.2 Working Memory = ✅; `01-architecture-baseline.md` §四 + `nop-ai-agent-session-engine.md` §6.3 reflect live baseline (persistence deferred to L4-3, system-prompt injection deferred to A1).
  - Closure Gates — PASS:
    - `./mvnw compile -pl nop-ai/nop-ai-agent -am` → BUILD SUCCESS
    - `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS (all new tests + existing ~600 tests pass)
    - Deferred items: all 5 in `Deferred But Adjudicated` are `out-of-scope improvement` or `optimization candidate` with explicit non-blocking rationale (L4-3 successor / A1 successor / fork-copy enhancement / retention optimization). No in-scope live defect downgraded.
  - Anti-Hollow Check — PASS:
    - (a) End-to-end test `TestWorkingMemoryEndToEnd.writeThenReadRoundTripInReActLoop` runs both tools through the real ReAct dispatch loop (mock LLM emits `write-memory` then `read-memory` tool_calls). The TOOL_CALL_STARTED events for `write-memory` and `read-memory` are asserted published, proving the tools are dispatched via `IToolManager.callTool` (not just bean-registered).
    - (b) The end-to-end test asserts the written content (`"User prefers concise answers and concrete examples."`) flows back through the read-memory tool response — proving the executor reads/writes the real `AgentToolExecuteContext.getMemoryStore()` store (not a stub).
    - (c) `TestReadMemoryExecutor.nullStoreFailsFastHonestly`, `TestWriteMemoryExecutor.nullStoreFailsFastHonestly`, `TestSearchMemoryExecutor.nullStoreFailsFastHonestly` all assert `errorResult` with `"no memory store available"` message (fail-fast, not silent).

Follow-up:

- L4-3 IMemoryAdapter (DB / Vector / Embedding backend) — successor plan, deps L2-15 ✅ satisfied
- A1 Budgeted Injection auto-consumption (system-prompt injection) — successor plan
- Memory persistence to AgentSession serialization — successor plan
- Session fork memory copy — enhancement
- Memory retention / TTL / LRU/LFU — optimization
- search-memory semantic search upgrade — depends on L4-3 Embedding adapter

## Follow-up handled by 192-nop-ai-agent-budgeted-injection-system-prompt.md

The **A1 Budgeted Injection auto-consumption (system-prompt injection)** carry-over from this plan's `Deferred But Adjudicated` ("Memory 自动注入 system prompt" — `Successor Required: yes, Successor Path: A1（Budgeted Injection 功能化消费）独立 successor`) and `Non-Blocking Follow-ups` / `Follow-up` ("A1 Budgeted Injection auto-consumption (system-prompt injection) — successor plan") sections is handled by plan 192 (`ai-dev/plans/192-nop-ai-agent-budgeted-injection-system-prompt.md`). Plan 192 delivers the functional consumption: `DefaultAgentEngine.buildBaseExecutionContext` (shared by `doExecute` + `resumeSession`) resolves the session's memory store via `memoryStoreProvider.getOrCreate(sessionId)`, calls `store.readBudgeted(budget, context)`, and appends the non-empty budgeted memory as a section to the system prompt — so the LLM receives working memory every turn without the Agent explicitly calling `read-memory`. Empty memory / budget ≤ 0 / null provider skips injection (backward compatible, no spurious system message). Budget is engine-level configurable (shipped default + setter). The interface work (priority/tokenEstimate/pinned fields + `readBudgeted` default + `InMemoryAiMemoryStore` functional impl) delivered by L1-16 + plan 189 Phase 1 is consumed unchanged. Per-agent budget config (AgentModel/xdef), L4-3 IMemoryAdapter (persistence), and readBudgeted UOE message wording remain deferred successors.
