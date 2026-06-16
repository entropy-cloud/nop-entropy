# 215 nop-ai-agent 记忆适配器契约（IMemoryAdapter: Storage / Embedding / Vector）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-3
> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（L4-3 ❌ 未实现，line 242）；`ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §Memory 模型（line 78：持久化 deferred 至 L4-3 IMemoryAdapter）；`ai-dev/design/nop-ai-agent/nop-ai-agent-session-engine.md` §6.3（Deferred L4-3 scope：DB 持久化 / 向量检索 / session fork memory copy / retention TTL）；`nop-ai-core` 既有 embedding/vector 基础设施（`IEmbeddingModel` / `IVectorStore` / `CosineSimilarity` / `VectorData`）
> Related: `189`（交付 `IAiMemoryStore` + `IMemoryStoreProvider` + `InMemoryAiMemoryStore` + read/write/search-memory 工具，L2-15 ✅）、`192`（交付 system-prompt budgeted injection，使用 `readBudgeted`）

## Purpose

把 nop-ai-agent 的 Working Memory 后端从"开箱仅 in-memory（进程重启丢失、search 仅子串匹配）"扩展为"可通过三适配器（Storage / Embedding / Vector）替换为 DB/vector 后端"。本计划交付**适配器契约表面 + NoOp 默认 + 复合 store + 引擎接线 + 端到端验证**，闭合 L4-3 roadmap gap。生产级 DB 持久化实现、真实 embedding API 集成、真实向量索引（FAISS/pgvector）均为显式 Non-Goals（独立 successor），本计划只交付让它们可以被插入的契约和接线。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`IAiMemoryStore`（8 方法）✅**（plan 189 / L2-15）：4 抽象（`getAll` / `getLastN` / `search` / `add`）+ 4 default UOE（`readBudgeted` / `update` / `remove` / `batchAdd`）。接口**不含 sessionId 参数**——每个实例代表一个 session 的记忆。
- **`IMemoryStoreProvider` ✅**（plan 189）：`getOrCreate(sessionId) → IAiMemoryStore`，Provider 模式保持 `IAiMemoryStore` 签名稳定。Javadoc 明确标注"L4-3 `IMemoryAdapter` 可替换为 DB/vector 后端 Provider"。
- **`InMemoryAiMemoryStore` ✅**（plan 189）：覆盖全部 8 方法（`ConcurrentHashMap` + `synchronized` 复合操作）。`search` 为 content/type/key **子串匹配**（in-memory 无向量检索，L4-3 scope）。数据**不**随 `AgentSession` 序列化——进程重启后 memory 丢失。
- **`InMemoryMemoryStoreProvider` ✅**（plan 189）：`ConcurrentHashMap.computeIfAbsent` 原子创建 per-session store。`DefaultAgentEngine` 默认持有此 Provider（开箱即用）。
- **`DefaultAgentEngine` 接线 ✅**：`memoryStoreProvider` 字段（`:136`，默认 `new InMemoryMemoryStoreProvider()`）+ `setMemoryStoreProvider` setter（`:731`）+ `resolveExecutor` Builder 链透传 `.memoryStoreProvider(this.memoryStoreProvider)`（`:1785`）+ system-prompt budgeted injection 读取 store（`:1242-1251`）。
- **`ReActAgentExecutor` 接线 ✅**：Builder `memoryStoreProvider()` 方法（`:634`）+ 分发循环经 `provider.getOrCreate(sessionId)` 解析注入 context（`:1403-1404`）。
- **nop-ai-core 既有 embedding/vector 基础设施**：`IEmbeddingModel`（`embed`/`embedAll` → `VectorData`）、`IVectorStore<T extends VectorData>`（`store`/`search`/`delete`/`update`）、`CosineSimilarity.between(VectorData, VectorData)`、`RelevanceScore.fromCosineSimilarity(double)`、`EmbeddingOptions`、`VectorData`（`getVector() → double[]`）。这些类型在 classpath 上（nop-ai-agent 依赖 nop-ai-core）。
- **L4-3 适配器零实现**：grep `IStorageAdapter|IEmbeddingAdapter|IVectorAdapter|IMemoryAdapter` 在 `nop-ai-agent/src/main` 返回 0 命中（仅 design doc + javadoc 注释引用"deferred to L4-3"）。
- **roadmap §4**：`L4-3 | IMemoryAdapter 三适配器 (Storage / Embedding / Vector) | L2-15 | ❌`（line 242）。本计划关闭这一行。

## Goals

- **三个适配器接口定义**，每个有清晰的行为契约：`IStorageAdapter`（AiMemoryItem 的持久化 CRUD）、`IEmbeddingAdapter`（文本 → 向量）、`IVectorAdapter`（向量索引 + 相似度检索）。
- **NoOp 默认实现**：三个适配器各有 NoOp/fail-fast 默认——`IStorageAdapter`/`IVectorAdapter` 的方法调用抛异常显式失败；`IEmbeddingAdapter` 的 `isAvailable()` 返回 `false` 使调用方优雅降级到 keyword search（遵循 Minimum Rules #24，无静默跳过）。
- **复合 adapter-backed `IAiMemoryStore` 实现**：组合三个适配器，将 `IAiMemoryStore` 的 8 方法委托到适配器后端。`search` 在 embedding 可用时使用向量相似度检索，否则 fallback 到关键词子串匹配（向后兼容当前行为）。
- **adapter-backed `IMemoryStoreProvider` 实现**：per-session 创建复合 store。
- **引擎接线**：`DefaultAgentEngine` 可通过 setter 注入 adapter-backed provider（或适配器三元组）。shipped 默认不变（`InMemoryMemoryStoreProvider`，零行为回归）。
- **in-memory 功能性适配器实现**：三个适配器各有简单 in-memory 功能实现（用于端到端测试验证，参照 `InMemoryAiMemoryStore` 的现有模式）。
- **focused 测试**：每个适配器接口 + 复合 store + provider + 引擎接线各有对应测试覆盖。
- **端到端验证**：从引擎入口 → 工具写入 memory → 适配器持久化 → 工具检索 memory → 适配器返回结果的完整路径跑通。
- **roadmap §4**：`L4-3` 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **DB-backed storage adapter（生产实现）**：raw JDBC 写 `nop_ai_session_memory` 或类似表的 `DbStorageAdapter`。本计划只交付接口 + in-memory 功能实现，DB 实现是独立 successor（参照 L2-17→L2-18 的接口→Db 拆分模式）。
- **真实 embedding API 集成**：包装 `nop-ai-core` 的 `IEmbeddingModel` 为 `IEmbeddingAdapter` 的功能性实现（调用真实 LLM embedding API）。in-memory 功能实现使用确定性 mock 向量（如 hash-based pseudo-embedding），不调用外部 API。
- **真实向量索引**：`IVectorAdapter` 的生产实现包装 `nop-ai-core` 的 `IVectorStore`（FAISS / pgvector / Milvus 等）。in-memory 功能实现使用线性扫描 + cosine 相似度（参照 `CosineSimilarity.between`）。
- **Memory retention / TTL / 容量上限**：`InMemoryAiMemoryStore` 当前无界保存。retention 策略是独立增强。
- **Session fork 时复制 memory**：子 session 当前获得独立的空 memory。fork-copy 是独立增强。
- **`AgentSession` 序列化 memory 状态**：memory 不随 session 快照序列化。序列化是独立增强。
- **长期记忆子系统**：`IMessageService` + retain/recall/reflect 工具 + EdgeClaw 风格 captureTurn/retrieve（roadmap 长期记忆 ❌）。本计划只覆盖 Working Memory 的后端适配器，不触及长期记忆。
- **XDSL 配置化**：`agent.xdef` 增加 `<memory-adapters>` 元素绑定适配器配置。当前通过 setter 编程注入。
- **`search-memory` 工具语义变更**：工具的 `keyword` 参数语义不变。向量检索是透明增强（当 embedding adapter 功能化时自动启用），不改变工具接口。

## Scope

### In Scope

- `io.nop.ai.agent.memory` 包下新增三个适配器接口 + 三个 NoOp 默认实现 + 三个 in-memory 功能性实现（test/reference）
- `io.nop.ai.agent.memory` 包下新增复合 adapter-backed `IAiMemoryStore` 实现 + adapter-backed `IMemoryStoreProvider` 实现
- `DefaultAgentEngine` + `ReActAgentExecutor.Builder` 接线（新增 setter 或扩展已有 provider setter）
- `nop-ai-agent-session-engine.md` §6.3 + `01-architecture-baseline.md` §Memory 设计文档更新
- roadmap §4 L4-3 ❌→✅
- focused 测试 + 端到端测试

### Out Of Scope

- 见 Non-Goals（全部 DB / 真实 API / 真实向量索引 / retention / fork-copy / 序列化 / 长期记忆 / XDSL 均为显式 successor）

### 设计裁定

**裁定 1：三个独立接口（非单一 `IMemoryAdapter`）**
- glossary 将 `IMemoryAdapter` 描述为"记忆持久化适配器（Storage / Embedding / Vector）"，但 roadmap 标题为"三适配器"。
- 裁定：定义三个独立接口 `IStorageAdapter` / `IEmbeddingAdapter` / `IVectorAdapter`，而非单一 `IMemoryAdapter` 包含三方法组。理由：(1) separation of concerns——存储、嵌入、检索是三个正交关注点，各自可独立替换；(2) 集成商可能只需要 DB 持久化但不需要向量检索（keyword search 足够），此时只需功能化 `IStorageAdapter`、其余保持 NoOp；(3) 与 nop-ai-core 的 `IEmbeddingModel` / `IVectorStore` 分离一一对应。
- `IMemoryAdapter` 作为术语保留在 glossary 中，指代三适配器的组合概念，不是代码层面的单一接口。

**裁定 2：复合 store 的 search fallback 策略**
- 当 `IEmbeddingAdapter` 为 NoOp（不支持嵌入）时，复合 store 的 `search()` fallback 到关键词子串匹配（与当前 `InMemoryAiMemoryStore.search` 行为一致）。
- 当 `IEmbeddingAdapter` 为功能性实现时，`search()` 使用向量相似度检索（经 `IVectorAdapter`），但仍可配置 fallback 到关键词匹配（如向量检索结果为空时）。
- 裁定：首版实现——embedding NoOp → keyword fallback；embedding 功能化 → 向量检索，向量结果为空时不 fallback（避免语义不一致）。

**裁定 3：与 nop-ai-core 基础设施的关系**
- agent 层适配器接口定义在 `io.nop.ai.agent.memory` 包，不直接引用 `nop-ai-core` 的 `IEmbeddingModel` / `IVectorStore` 类型（保持 agent 层契约的独立性）。
- 功能性实现（successor）可包装 `nop-ai-core` 类型。in-memory 功能实现使用自带的 `CosineSimilarity` 计算或简单确定性向量。

**裁定 4：shipped 默认不变**
- `DefaultAgentEngine.memoryStoreProvider` 默认值保持 `InMemoryMemoryStoreProvider`（零行为回归）。
- adapter-backed provider 是 opt-in（集成商显式 `engine.setMemoryStoreProvider(new AdapterBackedMemoryStoreProvider(...))`）。
- 不引入 `warnIfInsecureDefaults` WARN——memory adapter 缺失不构成安全风险（与 IUsageRecorder 裁定一致：NoOp 下系统正常运行，只是不持久化/不向量化）。

**裁定 5：NoOp 检测机制——capability-query 而非异常**
- `IEmbeddingAdapter` 接口包含一个 capability-query 方法（如 `boolean isAvailable()`）。NoOp 默认实现返回 `false`，功能性实现返回 `true`。
- 复合 store 的 `search()` 在调用 `embed()` 前先检查 `isAvailable()`：返回 `false` → 直接走 keyword fallback 路径（不调用 `embed()`、不抛异常、不 catch-and-swallow）；返回 `true` → 调用 `embed()` 执行向量检索。
- 同理，复合 store 的 `add()`/`update()` 在 `isAvailable()` 为 `false` 时跳过向量索引（只存储不索引），为 `true` 时才 embed + index。
- 理由：(1) 避免 catch-and-swallow 异常的反模式（Minimum Rules #24）；(2) 使 NoOp→fallback 路径无异常开销；(3) capability-query 是 SPI 惯例（同 `ICircuitBreaker.allowCall()` / `IBudgetProvider.isExceeded()` 模式）；(4) `IStorageAdapter` 和 `IVectorAdapter` 的 NoOp 默认仍 fail-fast（抛异常），因为它们的方法被 composite store 直接调用且没有"优雅降级"的 fallback 路径——只有 embedding 有 keyword fallback。

## Execution Plan

### Phase 1 - 适配器契约表面 + NoOp 默认 + 设计裁定落档

Status: completed
Targets: `io.nop.ai.agent.memory` 包（新增接口 + NoOp 默认）、`ai-dev/design/nop-ai-agent/nop-ai-agent-session-engine.md` §6.3、`ai-dev/design/nop-ai-agent/01-architecture-baseline.md` §Memory

- Item Types: `Decision`、`Proof`

- [x] **裁定并落档**三个适配器接口的行为契约（设计裁定 1-5 写入设计文档）：
  - `IStorageAdapter`：per-session AiMemoryItem 的 CRUD（save / load-all / load-by-key / update / remove / batch-save）。行为契约须明确：(a) 每个 store 实例代表一个 session（无 sessionId 参数，同 `IAiMemoryStore` 模式）；(b) `load-all` 支持可选 type filter；(c) `load-by-key` 返回 null 表示不存在（非异常）
  - `IEmbeddingAdapter`：文本 → 向量（embed single / embed batch）+ capability-query（`isAvailable()`）。行为契约须明确：(a) 输入为 AiMemoryItem.content 文本；(b) 输出为 `double[]`（与 nop-ai-core `VectorData.getVector()` 类型一致）；(c) `isAvailable()` 返回 `false` 表示嵌入不可用（NoOp 默认），调用方据此走 keyword fallback 而非调用 `embed()`；(d) `embed()` 方法仅在 `isAvailable()` 为 `true` 时被调用，NoOp 实现的 `embed()` 可抛 `UnsupportedOperationException` 作为防御（不会被正常调用）
  - `IVectorAdapter`：向量索引 + 相似度检索（index / search / remove）。行为契约须明确：(a) `index` 接受 item-key + 向量；(b) `search` 接受查询向量 + top-k，返回相似 item-key 列表（按相似度降序）；(c) NoOp 默认 fail-fast（抛 `NopAiAgentException`，表明"向量检索不可用"——与 `IStorageAdapter` 一致，无优雅降级路径）
- [x] 实现三个 NoOp/fail-fast 默认（每个适配器一个）：`NoOpStorageAdapter` 和 `NoOpVectorAdapter` 的方法调用抛 `NopAiAgentException`（fail-fast，无优雅降级路径）；`NoOpEmbeddingAdapter` 的 `isAvailable()` 返回 `false`（使调用方走 keyword fallback），`embed()` 抛 `UnsupportedOperationException` 作为防御性不可达路径。**不**静默返回空/null 当作正常结果（Minimum Rules #24）
- [x] 设计文档更新：`nop-ai-agent-session-engine.md` §6.3 的"Deferred（L4-3 scope）"段落改写为"L4-3 已交付"状态，记录三适配器契约 + 设计裁定 1-5；`01-architecture-baseline.md` §Memory line 78 的"deferred 至 L4-3"标注更新为"L4-3 ✅ 适配器契约已交付，DB/vector 生产实现为 successor"

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 三个适配器接口文件存在于 `io.nop.ai.agent.memory` 包，各自有清晰的行为契约 Javadoc
- [x] 三个 NoOp 默认实现文件存在：`NoOpStorageAdapter`/`NoOpVectorAdapter` 方法抛异常，`NoOpEmbeddingAdapter` 的 `isAvailable()` 返回 `false` 且 `embed()` 抛异常（非空方法体、非静默跳过——Minimum Rules #24）
- [x] `nop-ai-agent-session-engine.md` §6.3 已更新，"Deferred"段落标注 L4-3 契约已交付
- [x] `01-architecture-baseline.md` §Memory 已更新，L4-3 状态从 deferred 变为已交付契约
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 复合 store + provider + 引擎接线 + in-memory 功能实现 + 测试

Status: completed
Targets: `io.nop.ai.agent.memory` 包（新增复合 store + provider + in-memory 功能适配器）、`io.nop.ai.agent.engine.DefaultAgentEngine`（接线）、`io.nop.ai.agent.engine.ReActAgentExecutor.Builder`（接线）、`src/test/`（测试）

- Item Types: `Proof`、`Follow-up`

- [x] 实现复合 adapter-backed `IAiMemoryStore`：组合三个适配器，将 `IAiMemoryStore` 的 8 方法委托到适配器后端
  - CRUD 方法（`add` / `update` / `remove` / `batchAdd` / `getAll` / `getLastN`）→ `IStorageAdapter`，同时在 `add`/`update` 时检查 `IEmbeddingAdapter.isAvailable()`：为 `true` 则经 embedding 生成向量并经 `IVectorAdapter` 索引，为 `false` 则跳过向量索引（只存储不索引，不影响存储功能）
  - `search` → 先检查 `IEmbeddingAdapter.isAvailable()`：为 `true` 时使用 `IVectorAdapter` 向量检索（embed query → vector search → resolve keys → load items from storage）；为 `false` 时 fallback 到关键词子串匹配（参照 `InMemoryAiMemoryStore.search` 现有逻辑，无异常 catch-and-swallow）
  - `readBudgeted` → 从 `IStorageAdapter` 加载全部 items，按 priority/pinned/tokenEstimate 预算选择（参照 `InMemoryAiMemoryStore.readBudgeted` 现有算法）
- [x] 实现 adapter-backed `IMemoryStoreProvider`：per-session 创建复合 store 实例（`ConcurrentHashMap.computeIfAbsent`，参照 `InMemoryMemoryStoreProvider` 模式）
- [x] 实现三个 in-memory 功能性适配器（test/reference 用，非生产）：
  - `InMemoryStorageAdapter`：`ConcurrentHashMap<String, AiMemoryItem>` 持有 items（参照 `InMemoryAiMemoryStore` 现有模式）
  - `InMemoryEmbeddingAdapter`：确定性 pseudo-embedding based on character n-gram frequency vectors（如 bigram/trigram 频率 → 归一化 double[]），捕捉词法相似度使语义相近文本产生高 cosine 相似度（不调用外部 API；相同文本 → 相同向量、共享 n-gram 的文本 → 高相似度、无共享的文本 → 低相似度）。`isAvailable()` 返回 `true`
  - `InMemoryVectorAdapter`：线性扫描 + cosine 相似度（参照 `nop-ai-core.CosineSimilarity.between` 算法），返回 top-k 相似 keys
- [x] 引擎接线：`DefaultAgentEngine` 可通过 setter 注入 adapter-backed provider（**复用现有 `setMemoryStoreProvider`**——`AdapterBackedMemoryStoreProvider implements IMemoryStoreProvider`，经接口类型化字段 `memoryStoreProvider` + `resolveExecutor` Builder 链 `.memoryStoreProvider(this.memoryStoreProvider)` 透传，无 engine 代码变更，shipped 默认 `InMemoryMemoryStoreProvider` 不变）。接线由接线验证 + 端到端测试证明运行时连通
- [x] **接线验证**（Minimum Rules #23）：测试通过注入 adapter-backed provider 到 `DefaultAgentEngine` 并运行 ReAct 分发循环，断言 working-memory 工具的 `getMemoryStore()` 返回的是 adapter-backed store 实例
- [x] 编写 focused 测试：
  - `IStorageAdapter` in-memory 实现的 CRUD 测试（save/load/update/remove/batch + type filter + null-key 行为）
  - `IEmbeddingAdapter` in-memory 实现的嵌入测试（`isAvailable()` 返回 `true`、相同内容 → 相同向量、共享 n-gram 的不同内容 → 高 cosine 相似度、无共享内容 → 低相似度、空内容处理）
  - `IVectorAdapter` in-memory 实现的索引+检索测试（index → search top-k → 相似度排序正确、remove 后不再返回）
  - 复合 store 测试（全 8 方法委托正确、embedding `isAvailable()=false` 时 search fallback 到 keyword 且不调用 `embed()`、embedding `isAvailable()=true` 时 search 使用向量检索）
  - NoOp 默认 fail-fast 测试（每个 NoOp 适配器的方法调用显式失败）
- [x] **端到端验证**（Minimum Rules #22）：一个测试从 `DefaultAgentEngine`（注入 adapter-backed provider + in-memory 功能适配器）出发，经 working-memory 工具 `write-memory`（add action）→ 适配器存储 → `search-memory` → 适配器向量检索 → 返回结果，完整路径跑通；另验证 embedding `isAvailable()=false`（NoOp）时 `search-memory` fallback 到 keyword 子串匹配

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 复合 adapter-backed `IAiMemoryStore` 实现存在，覆盖全部 8 接口方法，无空方法体或静默跳过（Minimum Rules #24）
- [x] adapter-backed `IMemoryStoreProvider` 实现存在，per-session 隔离（同 sessionId → 同 store，不同 sessionId → 不同 store）
- [x] 三个 in-memory 功能性适配器实现存在（`InMemoryStorageAdapter` / `InMemoryEmbeddingAdapter` / `InMemoryVectorAdapter`），各有对应 focused 测试
- [x] 三个 NoOp 默认的 fail-fast 行为有 focused 测试覆盖
- [x] **接线验证**：测试断言 adapter-backed provider 注入 engine 后，working-memory 工具获得的 store 是 adapter-backed 实例（非 `InMemoryAiMemoryStore`）
- [x] **端到端验证**：从 `write-memory`（add）→ 适配器存储 → `search-memory` → 适配器检索的完整路径测试通过；embedding `isAvailable()=false` fallback 到 keyword 的路径测试通过（无异常 catch-and-swallow）
- [x] 复合 store 的 `search` 在 embedding 功能化（`isAvailable()=true`）时使用向量相似度（测试断言共享词法特征的条目排名高于无共享特征的条目）
- [x] `readBudgeted` 在 adapter-backed store 上行为正确（pinned 始终包含、priority 降序、tokenEstimate budget 截断）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 全量通过（含新增测试 + 既有测试零回归）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 三个适配器接口 + NoOp 默认 + in-memory 功能实现全部落地，无空壳
- [x] 复合 adapter-backed store 覆盖 `IAiMemoryStore` 全 8 方法，行为正确
- [x] adapter-backed provider 实现 per-session 隔离
- [x] 引擎接线完成（`DefaultAgentEngine` 可注入 adapter-backed provider，shipped 默认不变）
- [x] focused 测试覆盖每个适配器 + 复合 store + NoOp fail-fast + 接线
- [x] 端到端测试从 write-memory → 适配器存储 → search-memory → 适配器检索完整跑通
- [x] shipped 默认行为零回归（`InMemoryMemoryStoreProvider` 默认不变，既有测试全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（DB adapter / 真实 embedding / 真实 vector index 等均显式在 Non-Goals 切出）
- [x] 受影响的 owner docs 已同步到 live baseline（`nop-ai-agent-session-engine.md` §6.3 + `01-architecture-baseline.md` §Memory + roadmap §4 L4-3 ❌→✅）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）复合 store 确实在运行时被 working-memory 工具调用（不只是类型存在），（b）适配器方法链从工具入口到适配器出口完整连通，（c）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；DB storage adapter / 真实 embedding API / 真实 vector index / retention TTL / fork-copy / 序列化 / 长期记忆 / XDSL 均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **DB-backed `StorageAdapter`（生产实现）**：raw JDBC 写 memory 表（参照 `DbUsageRecorder` / `DBSessionStore` 的 raw JDBC 模式），使进程重启后 memory 不丢失。Classification: successor plan required。
- **真实 embedding API 集成**：包装 `nop-ai-core` 的 `IEmbeddingModel` 为 `IEmbeddingAdapter` 功能性实现（调用真实 LLM embedding API）。Classification: successor plan required。
- **真实向量索引**：`IVectorAdapter` 生产实现包装 `nop-ai-core` 的 `IVectorStore`（FAISS / pgvector / Milvus）。Classification: successor plan required。
- **Memory retention / TTL / 容量上限**：`InMemoryAiMemoryStore` 和 in-memory 功能适配器当前无界保存。retention 策略（LRU/LFU 淘汰、TTL 过期、容量上限）是独立增强。Classification: optimization candidate。
- **Session fork 时复制 memory**：子 session 当前获得独立的空 memory。fork-copy 使子 session 继承父 session 的 memory 快照。Classification: out-of-scope improvement。
- **`AgentSession` 序列化 memory 状态**：memory 不随 session 快照序列化。序列化使 memory 可跨进程恢复。Classification: out-of-scope improvement。
- **XDSL 配置化**：`agent.xdef` 增加 `<memory-adapters>` 元素绑定适配器配置。Classification: optimization candidate。
- **`search-memory` 工具向量检索暴露**：当前向量检索是透明增强（embedding 功能化时自动启用）。如需工具参数控制（如 `mode=vector|keyword|hybrid` + `threshold` 过滤），是独立增强。Classification: optimization candidate。

## Closure

Status Note: L4-3 关闭。交付了 `IStorageAdapter` / `IEmbeddingAdapter` / `IVectorAdapter` 三适配器契约（`io.nop.ai.agent.memory` 包）+ 三个 NoOp/fail-fast 默认 + 三个 in-memory 功能性参考实现 + 复合 `AdapterBackedAiMemoryStore`（覆盖 `IAiMemoryStore` 全 8 方法，embedding capability-query 驱动 search 的向量/keyword 双路径）+ `AdapterBackedMemoryStoreProvider`（per-session 隔离）+ 经 `DefaultAgentEngine.setMemoryStoreProvider` 的引擎接线（shipped 默认 `InMemoryMemoryStoreProvider` 不变，零行为回归）。57 个新增 focused + e2e 测试全绿，既有 1835 测试零回归。生产级 DB / 真实 embedding API / 真实向量索引实现均显式切为独立 successor（见 Non-Goals）。
Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（explore session `ses_130f457a6ffecwlEl8NDfGX3xP`，fresh closure-audit session，非实现 session）
- Audit Session: `ses_130f457a6ffecwlEl8NDfGX3xP`
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS：三接口（`IStorageAdapter.java:41-52` / `IEmbeddingAdapter.java:44-49` / `IVectorAdapter.java:38-43`）+ 三 NoOp 默认（`NoOpStorageAdapter.java:31-59` 全方法抛 `NopAiAgentException`；`NoOpVectorAdapter.java:31-44` 全方法抛 `NopAiAgentException`；`NoOpEmbeddingAdapter.java:25-43` `isAvailable()=false` + `embed`/`embedBatch` 抛 `UnsupportedOperationException`）——无静默空/null 返回
  - Phase 2 Exit Criteria 全部 PASS：`AdapterBackedAiMemoryStore.java` 覆盖全 8 方法（`getAll`:113 / `getLastN`:119 / `search`:132 / `add`:143 / `readBudgeted`:151 / `update`:183 / `remove`:200 / `batchAdd`:211，无空方法体）；search capability-query 模式（`:132-140`，false → keyword 不调 embed 不 catch-and-swallow，true → 向量检索）；`AdapterBackedMemoryStoreProvider.java:74` `computeIfAbsent` per-session 隔离；三个 in-memory 功能适配器（`InMemoryStorageAdapter` / `InMemoryEmbeddingAdapter` `isAvailable()=true` 确定性 / `InMemoryVectorAdapter` cosine + top-k）
  - **接线验证 PASS**：`DefaultAgentEngine.java:731` `setMemoryStoreProvider(IMemoryStoreProvider)` 接口类型化接受 `AdapterBackedMemoryStoreProvider`；`:136` shipped 默认 `InMemoryMemoryStoreProvider` 不变；`:1785` → `ReActAgentExecutor.java:1403-1404` → `AgentToolExecuteContext.java:300` `getMemoryStore()` → `AbstractMemoryToolExecutor.java:45` 工具消费——调用链运行时连通
  - **端到端验证 PASS**：`TestAdapterBackedMemoryEndToEnd`（2 tests）经真实 `DefaultAgentEngine` ReAct 循环驱动 write-memory→search-memory，断言工具时 `getMemoryStore()` 为 `AdapterBackedAiMemoryStore`（非 `InMemoryAiMemoryStore`）+ search 结果含写入内容；含 NoOp embedding keyword-fallback 场景
  - keyword-fallback 不调 embed 断言：`TestAdapterBackedAiMemoryStore.searchKeywordFallbackDoesNotCallEmbed`（计数 spy + `assertEquals(0, embedCalls.get())`）
  - 语义排序断言：`TestAdapterBackedAiMemoryStore.semanticSearchRanksSharedLexicalFeaturesHigher`（shared 排名高于 unrelated）
  - `node ai-dev/tools/check-plan-checklist.mjs 215-nop-ai-agent-memory-adapter.md --strict` 退出码 0（无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查：`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-agent --severity high` 退出码 0（0 high/critical 空壳发现）；audit 确认无空方法体 / 无静默 no-op / 无 catch-and-swallow / 无 untested 接口方法
  - 构建：`./mvnw test -pl nop-ai/nop-ai-agent -am` → Tests run: 1892, Failures: 0, Errors: 0（+57 新测试，既有 1835 零回归）
  - Deferred 项分类检查：DB storage / 真实 embedding / 真实 vector index / retention TTL / fork-copy / 序列化 / 长期记忆 / XDSL 均为显式 Non-Goals（successor），无 in-scope live defect 被降级
  - Owner docs 同步：`nop-ai-agent-session-engine.md` §6.3（L4-3 已交付 + 设计裁定 1-5）、`01-architecture-baseline.md` §Memory（line 78 + line 83）、`nop-ai-agent-roadmap.md:242`（L4-3 ❌→✅）

Follow-up:

- no remaining plan-owned work（DB / 真实 embedding / 真实 vector index / retention / fork-copy / 序列化 / 长期记忆 / XDSL 均为显式 successor，见 Non-Blocking Follow-ups）
