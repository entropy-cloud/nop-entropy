# 4 nop-ai-agent 声明式 Filter Chain（W3-2）

> Plan Status: active
> Last Reviewed: 2026-08-01
> Source: `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W3-2；`ai-dev/analysis/agent-survey/2026-08-01-plano-declarative-filter-chain-analysis.md`；`ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md` §5.2
> Mission: nop-ai-agent-harness-evolution
> Work Item: W3-2 声明式 filter chain：DSL 声明有序 filter ID 列表 + input/output 双链分离
> Related: `296-nop-ai-agent-middleware-and-tool-tag-system-implementation.md`（前置基础）；`2026-08-01-1437-3-nop-ai-agent-execution-level-middleware.md`（前置，建立 scope 概念）

## Purpose

将 nop-ai-agent 中间件装配从**代码类引用**（`<middleware impl="class-name" point="..."/>`）升级为支持**声明式 filter chain**：DSL 声明有序 filter ID 列表，input（请求侧）/ output（响应侧）双链独立配置，ID 在装配时解析为 `IAgentMiddleware` 实例（ResolvedFilterChain 模式）。这是 plano 声明式 filter chain 揭示的增量——使 guardrail 管道可审计、可序列化、零代码编排。

## Current Baseline

> 以下事实基于 live repo 核对（2026-08-01），由 explore agent 验证。

- **中间件装配为代码类引用**：`agent.xdef`（`nop-kernel/nop-xdefs/.../ai/agent.xdef:54-62`）声明 `<middlewares><middleware impl="!class-name" point="!string"/></middlewares>`。每个 `<middleware>` 直接指定实现类全名 + 生命周期 point。
- **装配路径**：`AgentExecutorResolver.resolveMiddlewares(AgentModel, IHookRegistry)`（`engine/AgentExecutorResolver.java:236-270`）遍历 `model.getMiddlewares()`，`ClassHelper.safeNewInstance(impl)` 实例化，cast 为 `IAgentMiddleware`，调 `hookRegistry.registerMiddleware(point, instance)`。
- **无 filter-chain / input-filters / output-filters 元素**：agent.xdef 全文 83 行无任何声明式 ID 引用组装。仅有工具可见性声明（`activeTags`/`denyTags`/`denyTools`）和 `<path-rules>`。
- **`AgentExecutorResolver` 无 IoC 容器访问**（review Blocker-1 发现）：`AgentExecutorResolver` 通过构造函数/Builder 注入依赖，**不持有 `IBeanContainer` 引用**。`DefaultAgentEngine` 同样用 Builder 模式构造。仓库中**不存在任何已注册的 `IAgentMiddleware` bean**（grep 所有 `*.beans.xml` 零匹配）。因此"filter ID = IoC bean ID"方案需要先解决容器注入路径，或改用 agent 内 `<filter-definitions>` 自包含方案（见 D1）。
- **生命周期点是多次触发模型**（review Major-1 发现）：不同于 plano 的请求/响应单次触发，nop 的生命周期点在一次请求中触发多次——PRE_CALL 触发 1 次，PRE_REASONING 触发 N 次（每次 LLM 调用），PRE_ACTING 触发 M 次（每次工具调用）。声明式 filter chain 的映射必须考虑这个多次触发语义。
- **会话级 + 执行级中间件（前置 plan 3）**：plan 3 建立了 scope 概念（会话级 per-request / 执行级 per-attempt）。**plan 3 尚未执行**（`Plan Status: draft`），本计划可独立交付声明式 filter chain（会话级），执行级 scope 支持为条件性 follow-up。
- **真正 gap**：guardrail 管道无法声明为有序 ID 列表；input（PRE_* 请求侧）与 output（POST_* 响应侧）无法分离配置；filter 装配不可审计/不可序列化（类名硬编码在 agent 模型中，无法反射出"这条管道由哪些 named filter 组成"）。

## Goals

- agent.xdef 支持声明式 filter chain：`<filter-chain>` 含 `<input-filters>` / `<output-filters>`，每个为有序 filter ID（ref）列表
- Filter ID 在装配时解析为 `IAgentMiddleware` 实例（解析来源依 D1 裁定——倾向 agent 内 `<filter-definitions>` 自包含，避免 IoC 容器注入改动）
- ResolvedFilterChain 模式：`filterRefs`（声明侧，可审计/序列化）与 resolved 中间件对象（执行侧）保持同步
- input-filters 默认映射到 PRE_CALL（请求边界，单次触发），output-filters 默认映射到 POST_CALL；filter 可用 `points` 属性覆盖默认映射
- 与现有 `<middlewares>` 代码类组装**共存**（依 D3 合并规则：声明式 filter 在前、代码类在后，重复检测）

## Non-Goals

- **不改变洋葱链执行模型**——声明式是装配方式，执行仍是 `MiddlewareChain` 洋葱链
- **不删除 `<middlewares>` 代码类组装**——两种装配方式共存
- **不引入字节级传递**——nop 保持对象级类型化（与 plano 字节级 filter chain 的关键区别）
- **不引入 plano 的 4B 路由模型**——nop 已有 `IModelRouter`（plan 209），正交
- **不新增 turn-cap guardrail**——nop 已有 LoopLimit / `maxIterations`（executor constraints），正交
- **不做执行级 scope 建模**——执行级 scope 属 plan 3（W3-1）范畴，本计划不涉及
- 不引入 filter 的跨 agent 共享/继承（首版仅 per-agent 声明）

## Scope

### In Scope

- agent.xdef `<filter-chain>` 声明元素（`<input-filters>` / `<output-filters>` 有序 ref ID 列表）
- Filter ID → `IAgentMiddleware` 实例解析（依 D1 裁定方案，ResolvedFilterChain 模式）
- input-filters → PRE_* 生命周期点映射，output-filters → POST_* 生命周期点映射
- `AgentExecutorResolver` 装配路径：声明式 filter chain 与现有 `<middlewares>` 共存合并
- codegen 生成 `AgentFilterChainModel`
- 设计文档 §5.2 从方向性描述重写为最终架构决策

### Out Of Scope

- 跨 agent filter 共享 / filter 继承
- 运行时动态增删 filter（nop 声明式装配，不支持运行时重排——与 plan 296 D 决策一致）
- plano 字节级传递 / 4B 路由模型 / turn-cap
- 执行级 scope 建模（plan 3 前置）

## Execution Plan

### Phase 1 — 声明式 filter-chain DSL 与解析

Status: planned
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/`

- Item Types: `Decision | Proof`

**Decision D1**：filter ID 解析来源。

**关键约束**（review Blocker-1 发现）：`AgentExecutorResolver` 无 IoC 容器访问，仓库中不存在已注册的 `IAgentMiddleware` bean。因此方案 A（IoC bean ID）需要先解决容器注入路径。

待裁定方向：
- 方案 A：filter ID = IoC bean ID——需要给 `AgentExecutorResolver` 或 `DefaultAgentEngineConfig` 增加 `IBeanContainer` 注入路径（架构变更），且需先注册 `IAgentMiddleware` bean 到 `beans.xml`。优点：跨 agent 可复用；缺点：注入路径改动面大。
- 方案 B：agent 内 `<filter-definitions>` 声明块——filter ID 在 agent 模型内局部定义（`<filter-def id="auth" impl="..."/>`），`<input-filters>/<output-filters>` 按 ID 引用。自包含，零外部依赖，复用现有 `ClassHelper.safeNewInstance` 路径。优点：无 IoC 改动；缺点：filter 定义不可跨 agent 复用（首版 Non-Goal）。
- 方案 C：混合——agent 内 `<filter-definitions>` 优先，若未命中则查 IoC 容器。

裁定约束：
- 必须使 `filter_ids`（声明侧）与 resolved 对象（执行侧）可审计、可序列化
- 必须在 filter ID 无法解析时**快速失败**（抛明确异常含 ID 名，不静默跳过）
- **倾向方案 B**（agent 内自包含），因为它无需 IoC 容器注入改动，与现有 `resolveMiddlewares` 的 `ClassHelper.safeNewInstance` 路径同构。方案 A 的跨 agent 复用是 Non-Goal（首版），可在后续迭代中通过方案 C 演进。

**Decision D2**：input/output 双链与生命周期点的映射规则。

**核心问题**（review Major-1 发现）：nop 生命周期点是多次触发模型（PRE_CALL 1 次、PRE_REASONING N 次、PRE_ACTING M 次）。plano 的 input/output 是请求/响应级单次触发。"全装到所有 input 点"会导致 auth-filter 在一次请求中触发 1+N+M+K 次——语义错误。

裁定方向：
- **首版默认映射到请求边界点**：input-filters 默认装到 **PRE_CALL**（请求开始，触发 1 次），output-filters 默认装到 **POST_CALL**（请求结束，触发 1 次）。这与 plano 的请求/响应单次触发语义一致，避免 auth-filter 多次触发。
- **可选 point 覆盖**：filter 声明可指定 `points` 属性覆盖默认（如 `<filter ref="prompt-check" points="pre_reasoning"/>`），使 filter 精确装到指定点。未指定 `points` 的 filter 走默认 PRE_CALL/POST_CALL。
- 这避免了"全装"的多次触发问题，同时保留了灵活性。

- [ ] D1: filter ID 解析来源裁定（含 IoC 容器访问约束分析）
- [ ] D2: input/output → 生命周期点映射规则裁定（含多次触发语义分析）
- [ ] 1.1 agent.xdef 新增 `<filter-chain>` 声明元素。依 D1 选方案 B 时还需 `<filter-definitions>` 声明块。xdef 结构（依 D1/D2 裁定细化 `xdef:name`/`xdef:body-type`）：
  ```xml
  <filter-chain xdef:name="AgentFilterChainModel">
      <input-filters xdef:body-type="list">
          <filter xdef:name="FilterRefModel" ref="!string" points="csv-set"/>
      </input-filters>
      <output-filters xdef:body-type="list">
          <filter xdef:name="FilterRefModel" ref="!string" points="csv-set"/>
      </output-filters>
  </filter-chain>
  ```
  （`points` 可选，缺省走 D2 默认 PRE_CALL/POST_CALL）
- [ ] 1.2 codegen 生成 `AgentFilterChainModel`（含 `inputFilters: List<FilterRefModel>` / `outputFilters: List<FilterRefModel>`，`FilterRefModel` 含 `ref: String` + `points: Set<String>`）。若 D1 选方案 B 还需 `<filter-definitions>` 的 codegen
- [ ] 1.3 Filter ID → `IAgentMiddleware` 解析器（依 D1 裁定方案），解析失败快速失败（异常含 ID 名 + 查找来源描述）
- [ ] 1.4 ResolvedFilterChain：持有 `filterRefs`（声明侧，可序列化）+ `resolvedMiddlewares`（执行侧），两者同步
- [ ] 1.5 单元测试：声明解析正确、ID → 中间件解析正确、未知 ID 快速失败（抛异常含 ID 名）、ResolvedFilterChain filter_refs 与对象同步、`points` 覆盖默认映射正确

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] D1/D2 两个 Decision 均已裁定（含 IoC 容器访问约束分析 + 多次触发语义分析）
- [ ] agent.xdef `<filter-chain>` 编译通过，codegen 生成 `AgentFilterChainModel`（验证方式：`_gen/_AgentFilterChainModel.java` 存在 + `AgentModel.getFilterChain()` 方法存在）
- [ ] filter ID 解析为 IAgentMiddleware（依 D1 裁定方案），有单元测试证明
- [ ] **无静默跳过**：未知 filter ID 抛明确异常（含 ID 名），不静默跳过；ResolvedFilterChain 不可变（声明后不可运行时修改）
- [ ] 若 Phase 改变 live baseline：更新 `ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md` §5.2；否则明确写 `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 装配集成与双链映射

Status: planned
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutorResolver.java`、`hook/DefaultHookRegistry.java`

- Item Types: `Fix | Proof`

**Decision D3**：`<filter-chain>` 与 `<middlewares>` 的共存合并语义。

**核心问题**（review Major-2 发现）：现有 `<middlewares>` 是 point-based 注册（每个中间件声明 `point="..."`）。声明式 filter chain 依 D2 默认装到 PRE_CALL/POST_CALL（或 filter 声明的 `points` 覆盖）。两种装配方式共存时，同一生命周期点可能同时有代码类中间件和声明式 filter。

裁定方向：
- **合并规则**：同一生命周期点的中间件列表 = 声明式 filter（按 `<filter-chain>` 内声明顺序）+ 代码类中间件（按 `<middlewares>` 内声明顺序）。**声明式 filter 在前**（guardrail 性质的 filter 应先执行），代码类中间件在后。
- **冲突检测**：同一生命周期点（同一 `AgentLifecyclePoint`）上，同一中间件实例（按对象 identity 或 impl class）同时出现在两处时，抛异常提示重复声明（非静默去重，非静默保留两份）。
- 这使合并结果确定、可审计。

- [ ] D3: `<filter-chain>` 与 `<middlewares>` 共存合并规则裁定（含合并顺序 + 重复检测语义）
- [ ] 2.1 `AgentExecutorResolver` 装配路径：解析 `<filter-chain>` → ResolvedFilterChain → 按 D2 映射规则将 input-filters 注册到请求侧生命周期点（默认 PRE_CALL，可被 `points` 覆盖）、output-filters 注册到响应侧生命周期点（默认 POST_CALL）
- [ ] 2.2 验证：`<filter-chain>` 与 `<middlewares>` 按 D3 合并规则共存（声明式 filter 在前、代码类中间件在后），重复声明检测抛异常
- [ ] 2.3 验证：input-filters 在请求侧点触发（默认 PRE_CALL 触发 1 次，非 N+M+K 次），output-filters 在响应侧点触发（默认 POST_CALL），`points` 覆盖生效
- [ ] 2.4 验证：声明式 filter 经 `MiddlewareChain` 洋葱链执行（复用现有执行模型，非新执行路径）

> **plan 3 依赖处理**（review Blocker-2 发现）：Item 2.5（执行级 scope 支持）已移入 `Deferred But Adjudicated`。本计划独立交付会话级声明式 filter chain，不依赖 plan 3 执行。

Exit Criteria:

- [ ] D3 共存合并规则已裁定（含合并顺序 + 重复检测语义）
- [ ] 声明式 filter chain 经 `AgentExecutorResolver` 装配到生命周期点，有单元测试证明
- [ ] input/output 双链映射正确（默认 PRE_CALL/POST_CALL 单次触发，`points` 覆盖生效），有测试证明
- [ ] **端到端验证**：从 `AgentExecutorResolver.resolveExecutor()` → `<filter-chain>` 解析 → ResolvedFilterChain → 注册到 registry → `ReActAgentExecutor.execute()` → `MiddlewareChain.proceed()` → filter 执行的完整路径已验证
- [ ] **接线验证**：声明式 filter 确实经 `MiddlewareChain` 洋葱链执行（经 `AgentHookInvoker.executeWithMiddleware`），非仅模型解析存在
- [ ] **无静默跳过**：声明式 filter 与代码类 filter 按 D3 合并规则处理，重复声明检测抛异常（非静默去重/保留）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 收口

Status: planned
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md`、`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md`

- Item Types: `Follow-up | Proof`

- [ ] 3.1 设计文档 `nop-ai-agent-middleware-design.md` §5.2 从方向性描述重写为最终架构决策（D1/D2/D3 最终方案、filter-chain DSL 结构、input/output 映射表（含多次触发语义分析）、与 `<middlewares>` 共存合并规则、ResolvedFilterChain 模式）；删除 "增量设计" 标记，标注 `final`
- [ ] 3.2 roadmap W3-2 标记完成
- [ ] 3.3 W3 整体（W3-1 + W3-2）在 roadmap 中确认完成

Exit Criteria:

- [ ] 设计文档 §5.2 为 final 状态（无 "Proposed/Current vs"、无方向性措辞，含最终 DSL 结构 + 映射表 + 共存规则）
- [ ] W3-1 + W3-2 均在 roadmap 标记完成
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 声明式 filter chain 可在 agent.xdef 声明，ID 解析为 IAgentMiddleware 实例，有端到端测试证明
- [ ] input/output 双链分离配置，默认映射到 PRE_CALL/POST_CALL（单次触发），`points` 覆盖生效
- [ ] 与现有 `<middlewares>` 代码类组装共存（依 D3 合并规则，零回归）
- [ ] ResolvedFilterChain：filterRefs 与 resolved 对象同步（可审计/可序列化）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 设计文档 §5.2 final 状态
- [ ] 受影响 owner docs 已同步到 live baseline
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）声明式 filter 经洋葱链确实执行（不只是模型解析存在），（b）input/output 映射在运行时生效，（c）无空方法体/静默跳过/no-op
- [ ] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [ ] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### 执行级 scope 的声明式 filter 支持（原 Item 2.5）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 本计划交付会话级声明式 filter chain（input/output 双链 + 默认 PRE_CALL/POST_CALL 映射），独立成立。执行级 scope 声明（`<filter-chain>` 声明 scope="execution"）依赖 plan 3（W3-1）建立执行级 scope 概念，plan 3 当前为 draft 未执行。声明式 filter chain 的会话级功能不依赖执行级 scope。
- Successor Required: yes
- Successor Path: plan 3（W3-1）完成后，后续 plan 或 plan 3 的 follow-up 增加执行级 scope 的声明式 filter 支持

## Non-Blocking Follow-ups

- 跨 agent filter 共享 / filter 继承 → 首版仅 per-agent 声明，共享可将来通过 IoC bean 复用实现
- filter-chain 的可视化/序列化输出（导出为可审计文档）→ ResolvedFilterChain 已持有 filter_ids，序列化能力是增强项
- 运行时动态增删 filter → 与 plan 296 决策一致，nop 声明式装配不支持运行时重排

## Closure

Status Note: （执行完成后填写）
Completed: YYYY-MM-DD

Closure Audit Evidence:

（执行完成后由独立子 agent 填写）

Follow-up:

（执行完成后填写）
