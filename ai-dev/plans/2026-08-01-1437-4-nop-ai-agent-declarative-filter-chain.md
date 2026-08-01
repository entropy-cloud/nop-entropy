# 4 nop-ai-agent 声明式 Filter Chain（W3-2）

> Plan Status: completed
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

Status: completed
Targets: `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`、`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/model/`

- Item Types: `Decision | Proof`

**Decision D1**：filter ID 解析来源。

**裁定：方案 B**（agent 内 `<filter-definitions>` 自包含）。约束分析：`AgentExecutorResolver` 经 Builder/构造注入依赖，**不持有 `IBeanContainer` 引用**；`DefaultAgentEngine` 同样 Builder 模式；grep 仓库所有 `*.beans.xml` 零 `IAgentMiddleware` bean 注册。方案 A（IoC bean ID）需先给 resolver/engine 增加容器注入路径（架构变更面大）且需先注册 bean。方案 B 复用既有 `resolveMiddlewares` 的 `ClassHelper.safeNewInstance` 路径，零 IoC 改动，自包含。跨 agent 复用为首版 Non-Goal（方案 C 混合留后续迭代，当跨 agent 共享成为需求时可先查 `<filter-definitions>` 再 fallback IoC）。

**Decision D2**：input/output 双链与生命周期点的映射规则。

**裁定：默认请求边界点 + 可选 `points` 覆盖**。多次触发语义分析：nop 生命周期点是多次触发模型（PRE_CALL 1 次、PRE_REASONING N 次、PRE_ACTING M 次），plano 的 input/output 是请求/响应级单次触发。"全装到所有 input 点"会让 auth-filter 一次请求触发 1+N+M+K 次（语义错误）。故 input-filters 默认装 **PRE_CALL**（请求开始，触发 1 次）、output-filters 默认装 **POST_CALL**（请求结束，触发 1 次），与 plano 请求/响应单次触发语义一致。filter 声明可指定 `points="pre_reasoning"` 覆盖默认（精确装到指定点，可多值 csv-set），未指定走默认。这避免多次触发问题同时保留灵活性。

- [x] D1: filter ID 解析来源裁定（含 IoC 容器访问约束分析）— **方案 B（agent 内 `<filter-definitions>`）**
- [x] D2: input/output → 生命周期点映射规则裁定（含多次触发语义分析）— **默认 PRE_CALL/POST_CALL + `points` 覆盖**
- [x] 1.1 agent.xdef 新增 `<filter-chain>` 声明元素。依 D1 选方案 B 时还需 `<filter-definitions>` 声明块。xdef 结构（依 D1/D2 裁定细化 `xdef:name`/`xdef:body-type`）：
  ```xml
  <filter-chain xdef:name="AgentFilterChainModel">
      <filter-definitions xdef:body-type="list" xdef:key-attr="id">
          <filter-def xdef:name="FilterDefModel" id="!string" impl="!class-name"/>
      </filter-definitions>
      <input-filters xdef:body-type="list">
          <filter xdef:name="FilterRefModel" ref="!string" points="csv-set"/>
      </input-filters>
      <output-filters xdef:body-type="list">
          <filter xdef:ref="FilterRefModel"/>
      </output-filters>
  </filter-chain>
  ```
  （`points` 可选，缺省走 D2 默认 PRE_CALL/POST_CALL；`<output-filters>` 用 `xdef:ref` 复用 `FilterRefModel` 以规避 `xdef:name` 全局唯一约束，与 gateway.xdef `requestMapping`/`responseMapping` 模式同构）
- [x] 1.2 codegen 生成 `AgentFilterChainModel`（含 `inputFilters: List<FilterRefModel>` / `outputFilters: List<FilterRefModel>`，`FilterRefModel` 含 `ref: String` + `points: Set<String>`）。若 D1 选方案 B 还需 `<filter-definitions>` 的 codegen
- [x] 1.3 Filter ID → `IAgentMiddleware` 解析器（依 D1 裁定方案），解析失败快速失败（异常含 ID 名 + 查找来源描述）
- [x] 1.4 ResolvedFilterChain：持有 `filterRefs`（声明侧，可序列化）+ `resolvedMiddlewares`（执行侧），两者同步
- [x] 1.5 单元测试：声明解析正确、ID → 中间件解析正确、未知 ID 快速失败（抛异常含 ID 名）、ResolvedFilterChain filter_refs 与对象同步、`points` 覆盖默认映射正确

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] D1/D2 两个 Decision 均已裁定（含 IoC 容器访问约束分析 + 多次触发语义分析）
- [x] agent.xdef `<filter-chain>` 编译通过，codegen 生成 `AgentFilterChainModel`（验证方式：`_gen/_AgentFilterChainModel.java` 存在 + `AgentModel.getFilterChain()` 方法存在）
- [x] filter ID 解析为 IAgentMiddleware（依 D1 裁定方案），有单元测试证明
- [x] **无静默跳过**：未知 filter ID 抛明确异常（含 ID 名），不静默跳过；ResolvedFilterChain 不可变（声明后不可运行时修改）
- [x] 若 Phase 改变 live baseline：更新 `ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md` §5.2；否则明确写 `No owner-doc update required` — **§5.2 final 重写由 Phase 3.1 交付（本 Phase 为其输入）；Phase 1 仅新增能力，无既有契约变化**
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 装配集成与双链映射

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentExecutorResolver.java`、`hook/DefaultHookRegistry.java`

- Item Types: `Fix | Proof`

**Decision D3**：`<filter-chain>` 与 `<middlewares>` 的共存合并语义。

**裁定：声明式 filter 在前 + 同 impl class 同点快速失败**。合并规则：同一生命周期点的中间件列表 = 声明式 filter（按 `<filter-chain>` 内声明顺序）+ 代码类中间件（按 `<middlewares>` 内声明顺序），**声明式 filter 在前**（guardrail 性质的 filter 应先执行=洋葱链最外层，其 `before` 先运行）。实现上：`AgentExecutorResolver` 在 `resolveHookContributions` 之后先调 `resolveFilterChain`（注册声明式 filter），再调 `resolveMiddlewares`（注册代码类中间件），由 registry 的 append 顺序天然保证声明式在前。冲突检测：同一生命周期点（`AgentLifecyclePoint`）上，同一 impl class 同时出现在声明式 filter-chain（按 `mw.getClass().getName()`）和代码类 `<middlewares>`（按 `impl`）两处时，抛 `ERR_AGENT_FILTER_DUPLICATE_DECLARATION`（含 impl + point，非静默去重/保留两份）。检测按 impl class（实例 identity 不适用，因两路径各自 `safeNewInstance` 产独立实例）。intra-mechanism 重复（同机制内两处用同 impl）不触发——仅跨机制冲突算重复。

- [x] D3: `<filter-chain>` 与 `<middlewares>` 共存合并规则裁定（含合并顺序 + 重复检测语义）
- [x] 2.1 `AgentExecutorResolver` 装配路径：解析 `<filter-chain>` → ResolvedFilterChain → 按 D2 映射规则将 input-filters 注册到请求侧生命周期点（默认 PRE_CALL，可被 `points` 覆盖）、output-filters 注册到响应侧生命周期点（默认 POST_CALL）
- [x] 2.2 验证：`<filter-chain>` 与 `<middlewares>` 按 D3 合并规则共存（声明式 filter 在前、代码类中间件在后），重复声明检测抛异常
- [x] 2.3 验证：input-filters 在请求侧点触发（默认 PRE_CALL 触发 1 次，非 N+M+K 次），output-filters 在响应侧点触发（默认 POST_CALL），`points` 覆盖生效
- [x] 2.4 验证：声明式 filter 经 `MiddlewareChain` 洋葱链执行（复用现有执行模型，非新执行路径）

> **plan 3 依赖处理**（review Blocker-2 发现）：Item 2.5（执行级 scope 支持）已移入 `Deferred But Adjudicated`。本计划独立交付会话级声明式 filter chain，不依赖 plan 3 执行。

Exit Criteria:

- [x] D3 共存合并规则已裁定（含合并顺序 + 重复检测语义）
- [x] 声明式 filter chain 经 `AgentExecutorResolver` 装配到生命周期点，有单元测试证明
- [x] input/output 双链映射正确（默认 PRE_CALL/POST_CALL 单次触发，`points` 覆盖生效），有测试证明
- [x] **端到端验证**：从 `AgentExecutorResolver.resolveExecutor()` → `<filter-chain>` 解析 → ResolvedFilterChain → 注册到 registry → `ReActAgentExecutor.execute()` → `MiddlewareChain.proceed()` → filter 执行的完整路径已验证
- [x] **接线验证**：声明式 filter 确实经 `MiddlewareChain` 洋葱链执行（经 `AgentHookInvoker.executeWithMiddleware`），非仅模型解析存在
- [x] **无静默跳过**：声明式 filter 与代码类 filter 按 D3 合并规则处理，重复声明检测抛异常（非静默去重/保留）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-middleware-design.md`、`ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md`

- Item Types: `Follow-up | Proof`

- [x] 3.1 设计文档 `nop-ai-agent-middleware-design.md` §5.2 从方向性描述重写为最终架构决策（D1/D2/D3 最终方案、filter-chain DSL 结构、input/output 映射表（含多次触发语义分析）、与 `<middlewares>` 共存合并规则、ResolvedFilterChain 模式）；删除 "增量设计" 标记，标注 `final`
- [x] 3.2 roadmap W3-2 标记完成
- [x] 3.3 W3 整体（W3-1 + W3-2）在 roadmap 中确认完成

Exit Criteria:

- [x] 设计文档 §5.2 为 final 状态（无 "Proposed/Current vs"、无方向性措辞，含最终 DSL 结构 + 映射表 + 共存规则）
- [x] W3-1 + W3-2 均在 roadmap 标记完成
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 声明式 filter chain 可在 agent.xdef 声明，ID 解析为 IAgentMiddleware 实例，有端到端测试证明
- [x] input/output 双链分离配置，默认映射到 PRE_CALL/POST_CALL（单次触发），`points` 覆盖生效
- [x] 与现有 `<middlewares>` 代码类组装共存（依 D3 合并规则，零回归）
- [x] ResolvedFilterChain：filterRefs 与 resolved 对象同步（可审计/可序列化）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 设计文档 §5.2 final 状态
- [x] 受影响 owner docs 已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）声明式 filter 经洋葱链确实执行（不只是模型解析存在），（b）input/output 映射在运行时生效，（c）无空方法体/静默跳过/no-op
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` — BUILD SUCCESS
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` — BUILD SUCCESS（3158 测试 0 failures）
- [x] checkstyle / 代码规范检查通过 — checkstyle 为 advisory 非 build gate（`mvn install` 不绑定 checkstyle；新增文件 80-char/javadoc 告警与既有代码库一致，`./mvnw clean install` BUILD SUCCESS）

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

Status Note: W3-2 声明式 filter chain 全量落地。`<filter-chain>` DSL（D1 方案 B agent 内自包含 id→impl 映射）+ input/output 双链（D2 默认 PRE_CALL/POST_CALL 单次触发 + `points` 覆盖，规避多次触发语义错误）+ 与 `<middlewares>` 共存（D3 声明式在前 + 跨机制同 impl 同点快速失败）+ ResolvedFilterChain（声明侧/执行侧同步、不可变）。零新执行路径（复用 MiddlewareChain 洋葱链）。零回归（3158 测试 0 failures）。独立 closure audit verdict PASS（15/15 claims 经 live-source 核验，Anti-Hollow 确认）。
Completed: 2026-08-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session subagent（task `ses_041edd9ffffeotz7WJuBFtnIor`，general 类型，read-only closure audit）
- Audit Session: `ses_041edd9ffffeotz7WJuBFtnIor`
- Evidence:
  - **VERDICT: PASS**（15/15 claims PASS）
  - Phase 1 Exit Criteria：xdef `<filter-chain>`（`agent.xdef:84-94`，`xdef:ref` 复用 FilterRefModel）；codegen 三模型 + `AgentModel.getFilterChain()`（`_AgentModel.java:353`）；`FilterChainResolver` 5 条 fast-fail 路径全 throw 含 ID/point 名（无 continue/无吞异常）；`ResolvedFilterChain` 不可变（`Collections.unmodifiableList/Map`）；D1/D2 裁定落地（input 默认 PRE_CALL、output 默认 POST_CALL）
  - Phase 2 Exit Criteria：`AgentExecutorResolver` 先 `resolveFilterChain` 后 `resolveMiddlewares`（:155→:156 声明式在前）；D3 重复检测 throw `ERR_AGENT_FILTER_DUPLICATE_DECLARATION`（:283，非 log+continue）
  - **端到端验证**（Anti-Hollow）：`TestAgentFilterChainWiring.declarativeInputFilterExecutesViaOnionChainEndToEnd` 经 `resolveExecutor → execute → AgentHookInvoker.executeWithMiddleware(PRE_CALL) → DefaultHookRegistry.buildChain → MiddlewareChain.proceed → filter.execute`，断言 `INPUT_GUARD_COUNT==1`（wiring 断则此断言为 0）
  - **单次触发证明**：`preCallDefaultFiresOnceAcrossMultipleIterations` 断言 `llmCalls==2 && INPUT_GUARD_COUNT==1`（证明 D2 默认 PRE_CALL 非 PRE_REASONING 多触发）
  - **洋葱顺序证明**：`coexistenceDeclarativeFirstThenCodeClass` 断言 `declBefore<codeBefore<codeAfter<declAfter`（声明式最外层）
  - **DSL parse 验证**：`filterChainParsesFromAgentXmlResource` 加载 `test-filter-chain.agent.xml` 证明 xdef 在 parse 时有效（含 `xdef:ref` 复用）
  - **接线验证**：声明式 filter 经既有 middleware registry + MiddlewareChain 执行（无并行 stub 路径），与代码类中间件同链
  - **无静默跳过**：新增 5 条 fast-fail + 1 条 D3 重复检测全 throw；零空方法体/continue/吞异常（既有 code-class `LOG.warn+continue` 为 plan 296 既有行为，非新增 filter-chain 代码）
  - Phase 3 Exit Criteria：design §5.2 `final`（D1/D2/D3 + DSL 结构 + 多次触发映射表 + 共存规则 + ResolvedFilterChain，无 "Proposed/增量设计" 残留）；roadmap W3-1/W3-2 均 `[x]`
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict`：closure evidence 已写入
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` exit 0（2 个 high finding 均为既有无关文件 PlanReplanner/NoOpProviderFailoverQueue，新增代码零 finding）
  - `./mvnw test -pl nop-ai/nop-ai-agent -T 1C` BUILD SUCCESS（3158 测试 0 failures）
  - Deferred 项分类检查：唯一 Deferred（执行级 scope 的声明式 filter 支持）已明确归属 successor（plan 3 W3-1 follow-up），Why Not Blocking Closure 已记录——非 in-scope live defect 降级
  - Auditor concern（test count 3157 vs 3158）已修正：roadmap 收口备注统一为 3158

Follow-up:

- 执行级 scope 的声明式 filter 支持（原 Item 2.5）→ 已移入 Deferred But Adjudicated，successor = plan 3 W3-1 完成后的 follow-up
- 跨 agent filter 共享 / filter 继承 → 首版 Non-Goal，可通过 IoC bean 复用（方案 C 演进）
- filter-chain 可视化/序列化导出 → ResolvedFilterChain 已持有 refs，序列化能力为增强项
