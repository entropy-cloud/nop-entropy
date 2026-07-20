# 304 Nop AI Agent Dispatch & Engine Refinement

> Plan Status: active
> Last Reviewed: 2026-07-20
> Source: `agent-survey/opencode-v2-architecture-analysis.md` §5.8, pangu design review
> Related: `200`-`303` series nop-ai-agent plans

## Current Baseline

- `ReActAgentExecutor` 是一个 3793 行的类，`reactLoop` 占约 1050 行（L1169-2216），内联了 compaction、model routing、LLM call、guardrails、goal tracking、tool dispatch 六个不同职责
- dispatch loop 包含 7 段几乎完全相同的 deny 路径（含 post-denial-guard L1768-1794 + L1797-1936），每段都重复 audit event 发布、threshold 处理、循环控制逻辑
- `DefaultAgentEngine` 有 8 个重载构造函数（L490-557），后续~30 个组件通过 setter 注入，每个 setter 调用 `warnIfInsecureDefaults()` 的 11 路 if 链
- agent.xdef 中同时存在 `<hooks>` 和 `<middlewares>`，两者最终注册到同一个 `DefaultHookRegistry`，功能重叠
- 工具定义需要 `.tool.xml` + `beans.xml` bean 声明两个文件，无 `class` 属性自动注册约定
- 源码中散布大量 plan 编号引用（`ReActAgentExecutor.java` 含 ~68 处 `Plan \d+` + ~40 处 `plan \d+`），干扰可读性
- 工具 schema 使用原始 XML (`<schema>xml</schema>`)，但 LLM 工具调用通常是 JSON 格式
- `ILevelHintsProducer` 只有一个实现 `DefaultLevelHintsProducer`，是多余的间接层
- `IConflictStrategy` 检查位于 dispatch loop 内部（L1921），但其职责是 multi-agent coordination 而非安全检查，混入安全链职责不清

## Goals

- 消除 dispatch loop 中的 7 段重复 deny 路径，引入 `SecurityCheckpoint` 抽象
- 将 `reactLoop` 按职责拆分为独立的可组合方法/类
- 将 `DefaultAgentEngine` 的 8 个重载构造函数统一为单一构造 + Builder
- 合并 `<hooks>` 和 `<middlewares>` 为单一 lifecycle 机制（推荐保留 middlewares 作为主机制）
- 引入工具定义的 convention-over-configuration：`.tool.xml` 声明 `class` 即可自动注册 bean
- 将 plan 编号从源码迁移到 `ai-dev/` 文档中
- 将 `ILevelHintsProducer` 合并入 `ISecurityLevelResolver`
- 将 `IConflictStrategy` 移出 dispatch loop 到 pre/post execution hook
- 同步更新相关 `ai-dev/design/` 和 `docs-for-ai/` 文档

## Non-Goals

- 不改变现有的四层安全架构的概念完整性
- 不改动现有的 XDef schema 兼容性（增量添加，非破坏性变更）
- 不改动现有的工具语义或 LLM 可见的接口行为
- 不涉及新功能开发（如 multi-tenant、new tools）
- 不重构单测（只需新增确保重构后行为保持的 regression tests）
- `IToolManager` 接口不变，但 `IToolManager.loadTool()` 的实现逻辑允许变更（Phase 5）

## Scope

### In Scope

- `ReActAgentExecutor` dispatch loop 安全检查路径去重（含 post-denial-guard 路径）
- `ReActAgentExecutor.reactLoop` 职责拆分
- `DefaultAgentEngine` 构造函数链简化（引入 `DefaultAgentEngine.Builder`）
- agent.xdef 的 hooks/middlewares 合并（保留 middlewares 为主机制，hooks 重定向）
- 工具注册的 convention-over-configuration（`.tool.xml` 增加 `class` 属性）
- `ILevelHintsProducer` 合并入 `ISecurityLevelResolver`
- `IConflictStrategy` 移出 dispatch loop
- 源码 plan 编号清理（使用 `rg '// plan \d+' --include='*.java'` 枚举 + 批量删除）
- 工具 schema 增加 JSON Schema 支持（与 XML 并存，增量迁移 3-5 个工具作为示范）
- 对应 `ai-dev/design/` 设计文档编写
- `docs-for-ai/` 中 agent/tool 相关 owner doc 同步

### Out Of Scope

- ReAct 语义或 tool dispatch 决策逻辑的改变
- 安全层级的增删（L1-L4 保持不变）
- `IToolManager` 接口签名的改动
- Nop IoC 核心机制的改动
- 全面迁移所有 23 个工具 schema 到 JSON（Phase 5 仅建立并存机制 + 3-5 个示范）

## Execution Plan

### Phase 1 - SecurityChain Abstraction

Status: planned
Targets: `ReActAgentExecutor.java` dispatch loop, `nop-ai-agent/src/main/java/io/nop/ai/agent/security/`

- Item Types: `Fix`

- [ ] 定义 `SecurityCheckpoint` 接口，返回 `Decision` 枚举 (`ALLOW | DENY | DENY_AND_BREAK`)
- [ ] 将 7 个 deny 路径各封装为一个 `SecurityCheckpoint` 实现（含 post-denial-guard L1768-1794）
- [ ] 引擎启动时构建 `SecurityCheckpoint` 链（顺序与现有 dispatch loop 一致）
- [ ] 替换 dispatch loop 中内联的 if-else 链为 chain 遍历
- [ ] 编写 regression tests：验证重构后对同一 tool call 产生与重构前相同的 allow/deny 决策

Exit Criteria:

- [ ] dispatch loop 不再包含重复的 audit/publish/threshold 代码段
- [ ] 新 `SecurityCheckpoint` 接口和至少 7 个实现已存在
- [ ] **接线验证**：dispatch loop 确实通过 chain 调用每个 checkpoint（在 end-to-end 测试中添加计数器或标志位断言）
- [ ] **Anti-Hollow Check**：每个 checkpoint 在 dispatch loop 中被运行时调用，非空壳实现
- [ ] **无静默跳过**：任何未实现的 checkpoint 抛出 `UnsupportedOperationException`
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] 新增的 regression test 验证重构前后行为一致
- [ ] No owner-doc update required（纯内部重构）

### Phase 2 - reactLoop Responsibility Split

Status: planned
Targets: `ReActAgentExecutor.java`

- Item Types: `Fix`

- [ ] 将 compaction 决策提取为 `compactionStrategy.shouldCompact(ctx)`
- [ ] 将 model routing + circuit breaker 提取为 `modelRouter.resolve(ctx)` 的调用序列
- [ ] 将 LLM call + retry 提取为 `llmCaller.callWithRetry(ctx)`
- [ ] 将 guardrail 检查提取为 `guardrailChain.check(ctx, direction)`
- [ ] 将 goal tracking 提取为 `goalTracker.recordAndAssess(ctx)`（方法级提取，不提升为 service）
- [ ] 确保 reactLoop 主体只做编排（for 循环 + 各步骤调用），不包含步骤内部实现细节

Exit Criteria:

- [ ] `reactLoop` 方法体不超过 250 行（仅编排逻辑，步骤实现委托给提取方法/类）
- [ ] 每个提取的方法/类有对应的回归测试覆盖（验证提取前后行为一致而非新行为）
- [ ] **接线验证**：从 `reactLoop` 入口到每个提取方法被调用的路径在运行时连通（端到端测试断言）
- [ ] **Anti-Hollow Check**：提取的方法包含真实逻辑（非空壳委托），可从测试中验证
- [ ] **端到端验证**：一个完整的 ReAct 循环（用户消息 → 多轮工具调用 → 最终回复）在重构后行为不变
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] No owner-doc update required（纯内部重构）

### Phase 3 - Engine Wiring Simplification

Status: planned
Targets: `DefaultAgentEngine.java`

- Item Types: `Fix`

- [ ] 引入 `DefaultAgentEngine.Builder`，接收所有 ~40 个组件作为 builder 参数
- [ ] 将 8 个重载构造函数替换为单一 private 构造 + Builder
- [ ] 将 `warnIfInsecureDefaults()` 合并到 builder 的 `build()` 方法中一次性执行（而非每个 setter）
- [ ] 更新所有已有的 `DefaultAgentEngine` 构造调用点使用 Builder（grep 查找所有 `new DefaultAgentEngine(` 出现位置）
- [ ] 编写 regression tests 验证引擎在简化后的构造方式下行为不变

Exit Criteria:

- [ ] 不再有 `DefaultAgentEngine` 的构造函数重载链（仅 1 个 private 构造 + Builder）
- [ ] `warnIfInsecureDefaults` 在 builder 的 `build()` 中只执行一次而非每个 setter
- [ ] **接线验证**：通过 Builder 构建的引擎能正常启动并处理一条消息
- [ ] **Anti-Hollow Check**：Builder 构建的引擎完整连通，无遗漏组件
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] No owner-doc update required（纯内部重构）

### Phase 4 - Hooks & Middlewares Unification

Status: planned
Targets: `agent.xdef` (`nop-kernel/nop-xdefs/`), `DefaultHookRegistry.java` (`io/nop/ai/agent/hook/`), `ReActAgentExecutor.java`

- Item Types: `Fix | Decision`

- [ ] 决策依据：推荐保留 **middlewares（IAgentMiddleware）** 作为主机制。理由：(1) 已完整实现为接口 + SPI，支持链式组合；(2) hooks 是基于 XPL 模板的事件绑定，表达能力受限；(3) 未来新增 lifecycle point 只需加接口方法，hooks 需要加 schema 和解析逻辑
- [ ] 将 hooks 的注册点重定向到 middlewares 机制（`DefaultHookRegistry` 的 hooks 注册改为创建 IAgentMiddleware 委托）
- [ ] 更新 `agent.xdef` schema：合并 `<hooks>` 和 `<middlewares>` 为 `<lifecycle>`（保留 `<hooks>` 和 `<middlewares>` 作为向后兼容别名，映射到 `<lifecycle>`）
- [ ] 清理 `DefaultHookRegistry` 中 hooks 的独立注册路径（保留识别别名后重定向的逻辑）
- [ ] 更新现有 `*.agent.xml` 文件使用新 `<lifecycle>` 格式（至少覆盖 `nop-ai-agent` 内置 agent）

Exit Criteria:

- [ ] hooks 和 middlewares 的功能通过同一套 IAgentMiddleware 机制实现
- [ ] 旧的 `<hooks>` 和 `<middlewares>` 元素仍被兼容识别（非破坏性变更）
- [ ] 所有现有 `*.agent.xml` 文件加载正常且行为不变
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `docs-for-ai/02-core-guides/service-layer.md` 中 agent lifecycle 部分已更新

### Phase 5 - Tool Registration Convention

Status: planned
Targets: `tool.xdef`, `_vfs/nop/ai/tools/*.tool.xml`, `DefaultToolExecutorProvider.java`

- Item Types: `Fix | Follow-up`

- [ ] 在 `tool.xdef` 中增加 `class` 可选属性（`xdef:attr "class"`）
- [ ] 修改 `DefaultToolExecutorProvider`（非 `IToolManager` 接口）中解析 `.tool.xml` 的逻辑：当声明 `class` 时自动实例化对应的 executor bean 并注册到 executor 集合
- [ ] 选取 10 个核心工具（如 read-file, write-file, bash, search-files, search-content），在其 `.tool.xml` 中添加 `class` 属性
- [ ] 逐步移除这些工具的 `ai-tools-defaults.beans.xml` 中对应的 bean 声明（验证移除后仍可正常加载）
- [ ] 工具 schema 增加 `application/json` 格式的参数描述支持：`tool.xdef` 新增 `<schemaJson>json</schemaJson>` 可选元素，与现有 `<schema>` XML 并存
- [ ] 迁移 3-5 个核心工具的 schema 定义使用 JSON 格式作为示范

Exit Criteria:

- [ ] 新增工具可以只写 `.tool.xml` + Java 类两个文件，不需要第三个 `beans.xml` 声明
- [ ] 10 个工具的 `.tool.xml` 已添加 `class` 属性，移除对应 beans.xml 声明后仍可用
- [ ] `tool.xdef` 中已增加 `class` 属性和 `schemaJson` 元素
- [ ] 至少 3 个工具可同时支持 XML 和 JSON 两种格式的参数描述
- [ ] `./mvnw test -pl nop-ai/nop-ai-toolkit -am` 通过
- [ ] `docs-for-ai/` 中工具注册相关文档已更新

### Phase 6 - Incidental Cleanups

Status: planned
Targets: 全 `nop-ai-agent/src/main/java/io/nop/ai/agent/`

- Item Types: `Fix | Follow-up`

- [ ] 运行 `rg '// [Pp]lan \d+' --include='*.java' -c` 枚举所有 plan 编号出现位置
- [ ] 从所有 Java 源码中删除 `// [Pp]lan \d+` 模式的注释（移至 `ai-dev/` 对应计划文档）
- [ ] 将 `ILevelHintsProducer.java` 的 `produceHints(ctx)` 方法合并到 `ISecurityLevelResolver`，删除 `ILevelHintsProducer` 接口
- [ ] 将 `IConflictStrategy` 检查从 dispatch loop（L1921）移到 `BEFORE_TOOL_RESULT_PROCESSED` hook/中间件中
- [ ] 确保移出后跨会话冲突检测行为不变：冲突时产生与重构前相同的错误响应

Exit Criteria:

- [ ] `rg '// [Pp]lan \d+' --include='*.java' nop-ai-agent/` 返回 0 条匹配
- [ ] `ILevelHintsProducer.java` 文件已删除，其方法已合并入 `ISecurityLevelResolver`
- [ ] `ConflictStrategy` 不再在 dispatch loop 中被调用（改为在 `BEFORE_TOOL_RESULT_PROCESSED` 中执行）
- [ ] **接线验证**：跨会话冲突检测仍然通过 hook 路径被触发（验证检测逻辑在 `BEFORE_TOOL_RESULT_PROCESSED` 中执行且产生相同效果）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [ ] `docs-for-ai/` 中安全架构文档已更新

## Closure Gates

- [ ] 所有 6 个 Phase 的 Exit Criteria 全部通过
- [ ] `./mvnw clean install -DskipTests` 通过（所有涉及模块编译通过）
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-toolkit -am` 通过
- [ ] 所有涉及 Phase 的 owner docs 已同步
- [ ] 不存在静默降级到 deferred/follow-up 的 in-scope item
- [ ] 独立子 agent closure-audit 完成并记录
- [ ] **Anti-Hollow Check（所有 Phase）**：
  - Phase 1: `SecurityCheckpoint` chain 在 dispatch loop 中被运行时调用，非空壳
  - Phase 2: 提取方法包含真实逻辑，端到端测试验证调用链连通
  - Phase 3: Builder 构建的引擎完整连通，无遗漏组件
  - Phase 4: lifecycle 机制统一后，hooks 和 middlewares 行为不变
  - Phase 5: `class` 属性的工具加载路径完整连通
  - Phase 6: plan 编号已清理、`ILevelHintsProducer` 已合并、`ConflictStrategy` 通过 hook 路径触发

## Deferred But Adjudicated

### 工具 Schema 全面迁移至 JSON

- Classification: `optimization candidate`
- Why Not Blocking Closure: Phase 5 已建立 JSON 并存机制并迁移 3-5 个示范工具，全面迁移所有工具属增量工作，不影响当前 engine 重构的 closure
- Successor Required: `no`

### `goalTracker` 从方法提取提升为 `GoalTrackingService`

- Classification: `optimization candidate`
- Why Not Blocking Closure: Phase 2 已将其提取为独立方法调用；提升为独立 service 是独立优化项，不影响 reactLoop 拆分核心目标
- Successor Required: `no`

## Closure

Status Note: 预留 — 由 closure audit 填写
Completed:

Closure Audit Evidence:

Follow-up:
