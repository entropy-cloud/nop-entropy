# Nop AI Agent Phase 1 接口锁定风险分析

> Status: open
> Date: 2026-06-12
> Context: Nop AI Agent 采用渐进式设计——Phase 1 接口锁死后不重构，后续只深化实现。本报告系统审计每个 Phase 1 接口是否足够宽，以承载 Phase 2+ 的需求而不破坏兼容性。

---

## 核心约束

**渐进式设计规则**：Phase 1 接口 lock down 后，后续 phase 只能通过以下方式扩展：
- 新增方法（默认实现/空方法）
- 新增字段（null-safe defaults）
- 新增扩展接口（独立 SPI，不修改原接口）

**不允许**：
- 修改已有方法签名
- 删除字段
- 改变已有语义
- 要求已有实现类适配新方法（除非有默认实现）

---

## 审计结果总览

| # | 接口/组件 | 风险等级 | 核心问题 | 建议 |
|---|----------|---------|---------|------|
| 1 | ISessionStore | 🔴 HIGH | 4 CRUD 方法，缺少 fork/event sourcing/compaction/locking | Phase 1 立即扩展 |
| 2 | IAiMemoryStore | 🔴 HIGH | 4 方法，缺少 update/delete/budgeted inject/ranking | Phase 1 立即扩展 |
| 3 | AiMemoryItem | 🔴 HIGH | 4 字段，缺少 priority/tokenEstimate/pinned/version | Phase 1 立即补充 |
| 4 | IAgentEngine | 🔴 HIGH | 2 方法，缺少 fork/lifecycle/coordination | Phase 1 立即扩展 |
| 5 | AgentSession | 🟡 MED | 基础结构正确，但缺少 parentSessionId/planId/compaction 字段 | Phase 1 补充（向下兼容） |
| 6 | IToolManager | 🟡 MED | callTool 签名缺少流式/cancel 回调，无动态注册 SPI | Phase 1 加注册 SPI |
| 7 | agent.xdef | 🟢 LOW | XDEF 天然可扩展，问题在执行态消费方式 | 修改 ReActAgentExecutor |
| 8 | ReActAgentExecutor | 🔴 HIGH | 紧耦合所有接口用法 + 硬编码 `new File(".")` + null cancelToken | Phase 1 回调化 |
| 9 | IChatService | 🟢 LOW | 抽象合理，流式/同步/异步俱备 | 保持不动 |
| 10 | IToolExecuteContext | 🟢 LOW | 扩展点充分（IToolFileSystem + ICancelToken） | 保持不动 |

---

## 详细分析

### 1. ISessionStore — 🔴 HIGH RISK

**Phase 1 接口** (`nop-ai-agent/.../session/ISessionStore.java`):
```java
public interface ISessionStore {
    AgentSession getOrCreate(String sessionId, String agentName);
    AgentSession get(String sessionId);
    void remove(String sessionId);
    Collection<AgentSession> getAll();
}
```

**实现**: `InMemorySessionStore` — `ConcurrentHashMap` 包装。

**Phase 2+ 需求** (来自 `session-and-storage.md`):

| 需求 | 当前支持 | 新增方式 |
|------|---------|---------|
| Session fork (`forkSession(parentId, inheritContext)`) | ❌ 无 | 需要新方法 |
| Event Log append (`appendEvent(sessionId, event)`) | ❌ 无 | 需要新方法 |
| Compaction (`compact(sessionId, config)` → CompactionEntry) | ❌ 无 | 需要新方法 |
| Snapshot (`createSnapshot(sessionId)`, `loadSnapshot(sessionId, snapshotId)`) | ❌ 无 | 需要新方法 |
| VFS path mapping（`.nop/sessions/{sessionId}/events`） | ❌ 无 | 需要新方法 |
| Session-level lock | ❌ 无 | 需要新方法 |
| Plan 引用管理 (`setPlanId(sessionId, planId)`) | ❌ 无 | 需要新方法 |

**锁定机制**: Phase 1 的 `ISessionStore` 只有最基础的 CRUD——getOrCreate/get/remove/getAll。Phase 2+ 的 Event Sourcing（append-only event log + compaction entry + snapshot 重建）是完全不同的存储范式，无法通过"添加几个方法"来桥接。

**毁灭场景**: 如果 Phase 1 的 `InMemorySessionStore` 被广泛使用，`DefaultAgentEngine` 和 `ReActAgentExecutor` 的构造函数都绑定了 `ISessionStore`，那么 Phase 2 引入 VFS Event Log 存储时：
1. 要么 `ISessionStore` 接口爆炸成 20+ 方法（包含 CRUD + event + compaction + snapshot + lock）
2. 要么新增子接口，但消费者引用需要转型
3. 要么 `InMemorySessionStore` 的 `getOrCreate` 返回值（`AgentSession`）被序列化存储，Phase 2 改成 DB 后数据结构不兼容

**建议**: 直接修改 `ISessionStore`，Phase 1 就加上 default 方法（抛 `UnsupportedOperationException`），为 Phase 2 预留扩展点：
```java
// Phase 2 扩展点：default 抛 UOE，Phase 1 实现类不受影响
default String forkSession(String parentSessionId, boolean inheritContext, Map<String, Object> props) {
    throw new UnsupportedOperationException("forkSession requires VfsSessionStore");
}
default long appendEvent(String sessionId, VfsEvent event) {
    throw new UnsupportedOperationException("appendEvent requires VfsSessionStore");
}
default CompactionResult compact(String sessionId, CompactConfig config) {
    throw new UnsupportedOperationException("compact requires VfsSessionStore");
}
default SessionSnapshot loadSnapshot(String sessionId, String snapshotId) {
    throw new UnsupportedOperationException("loadSnapshot requires VfsSessionStore");
}
default void setPlanRef(String sessionId, String planId) {
    throw new UnsupportedOperationException("setPlanRef requires VfsSessionStore");
}
```
Phase 1 的 `InMemorySessionStore` 不继承任何额外行为（4 个原始方法不变，5 个 default 抛异常，不支持也就不可能被误用）。Phase 2 的 `VfsEventLogSessionStore` 覆盖全部 9 个方法。消费者始终只依赖 `ISessionStore`，无需 `instanceof` 或双接口感知。

### 2. IAiMemoryStore — 🔴 HIGH RISK

**Phase 1 接口** (`nop-ai-agent/.../memory/IAiMemoryStore.java`):
```java
public interface IAiMemoryStore {
    List<AiMemoryItem> getAll(Map<String, Object> filters);
    List<AiMemoryItem> getLastN(int n);
    List<AiMemoryItem> search(String query);
    void add(AiMemoryItem item);
}
```

**实现**: 无——`InMemoryAiMemoryStore` 未实现。

**Phase 2+ 需求**:

| 需求 | 当前支持 | 新增方式 |
|------|---------|---------|
| update(key, item) | ❌ 无 | 需要新方法 |
| delete(key) / deleteByFilter(filter) | ❌ 无 | 需要新方法 |
| batchAdd(items) / batchDelete(keys) | ❌ 无 | 需要新方法 |
| readBudgeted(maxTokens, context) — 重要性排序注入 | ❌ 无 | 需要新方法 |
| compact() — 清理过期/低分条目 | ❌ 无 | 需要新方法 |
| FTS full-text search | ❌ 无 | search() 需要扩展 |
| Importance ranking / priority 排序 | ❌ 无 | search() 需要扩展 |
| Cross-session merge | ❌ 无 | 需要新方法 |

**锁定机制**: 4 个方法完全是"append-only bag of items"风格，缺少任何更新/删除/排序/预算控制能力。Phase 2 的 Budgeted Injection（MiMoCode 创新）需要：
- `readBudgeted(maxTokens, priorityFn)` — 按重要性排序后注入 token 预算内的记忆
- AiMemoryItem 需要 `priority`, `tokenEstimate`, `pinned`, `lastAccessTime`, `accessCount` 字段

当前 `AiMemoryItem` 只有 4 个字段（key/type/content/createTime），Phase 2 需要至少 5 个额外字段。

**建议**:
1. 立即补充 AiMemoryItem：`priority`(int), `tokenEstimate`(int), `pinned`(boolean), `lastAccessTime`, `accessCount`, `tags`(Set), `checksum`(String)
2. IAiMemoryStore 立即增加：`update(key, item)`, `remove(key)`, `batchAdd(List)`, `readBudgeted(budget, context)`（can be default 调用 getAll + 截断）
3. 增加 `IAiMemorySearch` 扩展接口（FTS + 向量搜索），不污染 IAiMemoryStore 主接口

### 3. AiMemoryItem — 🔴 HIGH RISK

**Phase 1 数据对象** (`AiMemoryItem.java`):
```java
@DataBean
public class AiMemoryItem {
    private String key;
    private String type;
    private String content;
    private LocalDateTime createTime;
}
```

**Phase 2+ 需求**:

| 字段 | 必要性 | 场景 |
|------|--------|------|
| priority (int) | 🔴 必要 | Budgeted Injection 排序依据 |
| tokenEstimate (int) | 🔴 必要 | Token 预算控制 |
| pinned (boolean) | 🟡 重要 | 标记永久保留的记忆（系统指令等） |
| lastAccessTime | 🟡 重要 | LRU 淘汰策略 |
| accessCount (int) | 🟢 有用 | LFU 淘汰策略 |
| tags (Set\<String\>) | 🟡 重要 | 分类过滤、语义域识别 |
| checksum (String) | 🟢 有用 | 去重、变更检测 |
| source (String) | 🟢 有用 | 记录来源（dream/distill/user/manual） |

**锁定机制**: Phase 1 只定义了 4 个字段。Phase 2 如果补充 5+ 字段，虽然 Java 类可以加字段（null-safe），但：
1. 所有序列化/反序列化路径需要兼容 null
2. 现有的 `InMemorySessionStore` 在内存中存储 `AiMemoryItem`，如果 Phase 2 切换到 DB，序列化格式不兼容
3. 现有使用者（目前无，因为 store 未实现没有风险，但接口定义一旦被外部实现则不可改）

**建议**: 立即补充 priority/tokenEstimate/pinned/checksum 四个字段（最紧迫）。tags/source 可以延迟到 Phase 2。

### 4. IAgentEngine — 🔴 HIGH RISK

**Phase 1 接口** (`IAgentEngine.java`):
```java
public interface IAgentEngine {
    AgentMessageAck sendMessage(AgentMessageRequest request);
    CompletableFuture<AgentExecutionResult> execute(AgentMessageRequest request);
}
```

**Phase 2+ 需求**:
- `forkSession(request, inheritContext)` → 子 session 创建
- `getSessionStatus(sessionId)` → AgentExecStatus + metadata
- `cancelSession(sessionId, reason)` → graceful/forced cancel
- `joinSession(sessionId, timeout)` → 等待异步 actor 完成
- `resumeSession(sessionId, checkpointId)` → 从 checkpoint 恢复
- 多 Agent 协调操作（scope_claim, operation_intent, conflict_alert）

**锁定机制**: 2 个方法完全不够。`sendMessage` 是 fire-and-forget，`execute` 是 wait-for-completion。Phase 2 需要 session 生命周期管理（fork/cancel/resume/join），都不是当前接口能通过"添加新方法"解决的——因为它们与 `DefaultAgentEngine` 的实现紧密耦合。

**建议**: Phase 1 立即扩展 IAgentEngine：
```java
// 新增（Phase 1，带默认 UnsupportedOperationException）
default CompletableFuture<String> forkSession(AgentMessageRequest request, boolean inheritContext) {
    throw new UnsupportedOperationException("forkSession not supported in Phase 1");
}
default AgentExecStatus getSessionStatus(String sessionId) {
    throw new UnsupportedOperationException("getSessionStatus not supported in Phase 1");
}
default CompletableFuture<Void> cancelSession(String sessionId, String reason, boolean forced) {
    throw new UnsupportedOperationException("cancelSession not supported in Phase 1");
}
```

### 5. AgentSession — 🟡 MEDIUM RISK

**Phase 1 类** (`AgentSession.java`, 106 行): sessionId, agentName, messages list, totalTokensUsed, totalIterations, createdAt, updatedAt, status, metadata map.

**Phase 2+ 需求**:
- `parentSessionId` — fork 链追踪
- `planId` — Plan 引用
- `compactedAt`, `lastCompactionEntryId` — 压缩追踪
- `currentSnapshotId` — 快照引用
- `eventSeq` — Event Log 当前偏移

**锁定风险评估**: 🟡 **中等**。因为：
1. `AgentSession` 是 POJO/DataBean，Java 类可以添加字段
2. 新增字段默认 null/0，不影响现有序列化
3. `metadata` Map 提供了 escape hatch，但不应滥用

**建议**: Phase 1 立即添加：
```java
private String parentSessionId;    // null for root sessions
private String planId;              // null if no plan
private long compactedAt;           // 0 if never compacted
private String currentSnapshotId;   // null if no snapshot
```
这些全是 nullable 字段，Phase 2 填充。

### 6. IToolManager — 🟡 MEDIUM RISK

**Phase 1 接口** (`IToolManager.java`):
```java
public interface IToolManager {
    CompletableFuture<AiToolCallResult> callTool(String name, AiToolCall call, IToolExecuteContext context);
    CompletableFuture<AiToolCallsResponse> callTools(AiToolCalls calls, IToolExecuteContext context);
    List<AiToolModel> listTools();
    AiToolModel loadTool(String toolName);
}
```

**Phase 2+ 需求**:
- `registerTool(String name, AiToolModel, IToolExecutor)` — 动态注册
- `unregisterTool(String name)` — 动态卸载
- `getToolExecutor(String name)` — executor 访问
- 拦截器链管理（addInterceptor/removeInterceptor）

**锁定风险评估**: 🟡 **中等**。Phase 1 的 `callTool/callTools` 签名是合理的。动态注册可以作为一个新的 `IToolRegistry` 扩展接口，不修改 `IToolManager`。拦截器链也可以通过 `IToolCallInterceptor` 链在现有的 `callTool` 内部实现。

**建议**: Phase 1 创建 `IToolRegistry` 接口：
```java
public interface IToolRegistry {
    void registerTool(String name, AiToolModel model, IToolExecutor executor);
    void unregisterTool(String name);
    boolean hasTool(String name);
}
```
`IToolManager` 保持不动（`listTools/loadTool` 可以从 registry 派生）。

### 7. agent.xdef — 🟢 LOW RISK

**Phase 1 schema** (40 行): name, description, meta, chatOptions, tools, availableSkills, requiredSkills, permissions, constraints, prompt, hooks.

**Phase 2+ 需求**:
- `mode` (react/plan/single-turn) — 执行模式
- `forkPolicy` — 子 agent fork 行为
- `memoryConfig` — 记忆存储配置
- `model` 段 — 每 agent 模型路由

**锁定风险评估**: 🟢 **低**。XDEF 天然支持 optional elements。新增字段不影响现有 `.agent.xml` 文件。但需要注意：

**消费方的隐性锁定**: 如果 Phase 1 的 `ReActAgentExecutor` 在 `execute()` 中假设"agent 就是 ReAct 循环"，那么 Phase 2 添加 `mode="plan"` 时，ReActAgentExecutor 不消费该字段，需要新增 `PlanAgentExecutor` 或 `IAgentExecutor` 的另一个实现。这不破坏接口，但如果 `DefaultAgentEngine` 只分发到 `ReActAgentExecutor`，则新 mode 不可用。

**建议**: 
1. agent.xdef 立即增加 `mode` 属性（默认 `"react"`）
2. `IAgentExecutor` 保持通用的 `execute(AgentExecutionContext)` 签名，不耦合 ReAct 细节
3. `DefaultAgentEngine` 在 Phase 1 就根据 `agentModel.getMode()` 分发到不同 executor

### 8. ReActAgentExecutor — 🔴 HIGH RISK

**Phase 1 实现** (363 行): 单层 while 循环，同步 `CompletableFuture.allOf().join()`，硬编码 `SimpleToolExecuteContext(new File("."))`，null `cancelToken`。

**关键锁定点**:

| 问题 | 风险 | Phase 2 需求 |
|------|------|-------------|
| `new File(".")` 硬编码 | 🔴 HIGH | 需要真实工作目录，来自 Agent 环境配置 |
| `chatService.call(request, null)` | 🟡 MED | cancelToken 来自 AgentExecutionContext |
| `CompletableFuture.allOf().join()` 阻塞 | 🟡 MED | Phase 2 需要异步非阻塞 |
| 单层 while (iter < maxIterations) | 🟢 LOW | 可以被 hook 扩展 |
| 直接调用 checkAccess → resolve → checkPath | 🟡 MED | Phase 2 需要 interceptor 链 |
| 没有 session 持久化集成 | 🔴 HIGH | 每条消息/结果需要写入 Event Log |
| 没有事件流的中断/暂停 | 🟡 MED | Phase 2 需要 cancel/resume |

**锁定机制**: 虽然 `ReActAgentExecutor` 是实现类（不是接口），但它被 `DefaultAgentEngine` 直接引用。Phase 2 如果：
- 需要 fork（`ReActAgentExecutor` 需要操作 `ISessionStore.forkSession()`）→ 当前没有该参数
- 需要每步压缩检查 → 没有 `IContextCompactor` 集成点
- 需要 checkpoint → 没有 `ICheckpointManager` 参数

当前 `ReActAgentExecutor` 的 6 个构造函数（从 3-arg 到 6-arg）已经是复杂度警示——每个新能力（permission → toolAccess → pathAccess）都增加一个构造函数。Phase 2 继续加参数会导致组合爆炸。

**建议**:
1. 将 `ReActAgentExecutor` 改为 Builder 模式，不继续加构造函数
2. Phase 1 增加 `ICheckpointManager` 和 `IContextCompactor` 的 pass-through 参数位置（带 null-safe 默认）
3. 去掉 `new File(".")` 硬编码，从 `AgentExecutionContext` 获取

### 9. IChatService — 🟢 LOW RISK

**Phase 1 接口** (34 行): callAsync/call/callStream 三大方法 + ICancelToken。

**评估**: 这是全 nop-ai 模块中最完善的接口之一。流式/同步/异步俱备，cancelToken 预留。Phase 2 的 prefix-cache、retry、circuit-breaker 都可以通过正交的包装器实现（`CachingChatService` / `RetryingChatService`），不需要改接口。

**建议**: 保持不动。

### 10. IToolExecuteContext — 🟢 LOW RISK

**Phase 1 接口** (21 行): workDir, envs, expireAt, cancelToken, fileSystem, executor.

**评估**: IToolFileSystem 和 ICancelToken 为 Phase 2 提供了充分的扩展点。Phase 2 可以：
- 实现 `SessionAwareToolExecuteContext extends IToolExecuteContext` 添加 session 引用
- 实现 `StreamingToolExecuteContext extends IToolExecuteContext` 添加输出流

**建议**: 保持不动。

---

## 按 Phase 2 模块的锁定影响

| Phase 2 模块 | 依赖的 Phase 1 接口 | 锁定风险 |
|-------------|-------------------|---------|
| Session 持久化 (VFS Event Log) | ISessionStore, AgentSession | 🔴 HIGH — 接口太窄 |
| 上下文压缩 (L2-3, L2-4) | 无（全新接口 IContextCompactor） | 🟢 LOW — 新接口 |
| Session fork (context-model §5) | IAgentEngine, ISessionStore | 🔴 HIGH — 需新方法 |
| Checkpoint (L3-4) | IAgentEngine, AgentSession | 🟡 MED — 可加新接口 |
| Multi-Agent (Coordination Bus) | IAgentEngine | 🟡 MED — 需新方法 |
| Working Memory (L2-15) | IAiMemoryStore, AiMemoryItem | 🔴 HIGH — 数据模型不够 |
| IToolCallRepairer (L2-1, L2-2) | IToolManager | 🟢 LOW — 拦截器链不冲突 |
| Hook/Skill 引擎 (L2-12) | agent.xdef | 🟢 LOW — XDEF 可扩展 |

---

## 行动建议

### Phase 1 结束前必须修复（否则 Phase 2 需破重构）

1. **ISessionStore** → 直接加 default 方法（fork/event/compaction/snapshot），抛 UnsupportedOperationException
2. **IAiMemoryStore** → 新增 update/remove/batchAdd/readBudgeted 四个方法（default UnsupportedOperationException）
3. **AiMemoryItem** → 补充 priority/tokenEstimate/pinned/checksum 字段
4. **IAgentEngine** → 新增 default 方法：forkSession/getSessionStatus/cancelSession
5. **ReActAgentExecutor** → Builder 模式替换构造函数链 + 去掉 `new File(".")` 硬编码

### Phase 1 可选但强烈建议

6. **AgentSession** → 补充 parentSessionId/planId/compactedAt（nullable 字段，向下兼容）
7. **agent.xdef** → 增加 `mode` 属性（默认 `"react"`）
8. **DefaultAgentEngine** → 根据 `agentModel.getMode()` 分发 executor

### Phase 1 保持不动

9. **IChatService** — 已经够好
10. **IToolExecuteContext** — 扩展点充分

---

## 参考

- `nop-ai-agent/.../session/ISessionStore.java` — 14 行接口
- `nop-ai-agent/.../memory/IAiMemoryStore.java` — 14 行接口
- `nop-ai-agent/.../memory/AiMemoryItem.java` — 45 行数据类
- `nop-ai-agent/.../session/AgentSession.java` — 106 行会话类
- `nop-ai-agent/.../engine/IAgentEngine.java` — 10 行接口
- `nop-ai-agent/.../engine/ReActAgentExecutor.java` — 363 行执行器
- `nop-xdefs/.../ai/agent.xdef` — 40 行 schema
- `nop-ai-api/.../chat/IChatService.java` — 34 行接口
- `nop-ai-toolkit/.../api/IToolManager.java` — 19 行接口
- `nop-ai-toolkit/.../api/IToolExecuteContext.java` — 21 行接口
- `ai-dev/design/nop-ai-agent/nop-ai-agent-session-and-storage.md` — 502 行存储设计
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` — 171 行上下文设计
- `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` — 306 行路线图
- `ai-dev/analysis/agent-survey/2026-06-12-nop-ai-vs-mimo-code-deep-comparison.md` — 基线对比
