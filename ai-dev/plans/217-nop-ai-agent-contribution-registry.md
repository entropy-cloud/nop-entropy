# 217 nop-ai-agent 插件贡献注册表契约（IContributionRegistry 7 贡献类型）

> **Plan Status**: completed
> **Module**: nop-ai-agent
> **Work Item**: L4-6

> Last Reviewed: 2026-06-16
> Source: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md` §4 Layer 4（L4-6 ❌ 未实现，line 245，依赖 L2-12 ✅）；`ai-dev/analysis/agent-survey/2026-06-05-pilotdeck-analysis.md`（7 种 Contribution 类型：Tool / Command / Hook / MCP Server / Permission Rule / Prompt / Router）+ `ai-dev/analysis/2026-06-07-agent-design-patterns-for-nop.md` §2.8（Nop 映射：每种贡献类型 → 扩展点）；`ai-dev/design/nop-ai-agent/nop-ai-agent-hook-skill-engine.md` §2/§5a（Hook 引擎 + 注册表模式）
> Related: `150`（L2-12 `IAgentLifecycleHook` + `IHookRegistry` + `DefaultHookRegistry`，本计划的直接前置依赖）、`216`（L4-5 同层 registry 风格 plan，确立 NoOp 默认 + 功能实现 + 适配器接线 + 端到端验证 的范围范式）、`192`（budgeted injection system prompt，Prompt 贡献解析的注入点参照）

## Purpose

把 nop-ai-agent 的能力扩展从"仅靠静态 XDSL（`agent.xdef` 的 tools / hooks / permissions / prompt 声明）+ 构造器注入扩展点"扩展为"可通过统一 `IContributionRegistry` 在运行时由插件注册 7 种类型的贡献，引擎在装配期把可解析的贡献类型解析到既有扩展点"。本计划交付**贡献契约表面（7 类型全覆盖）+ NoOp 默认 + 功能性 InMemory 注册表 + Hook/Prompt 两类贡献的装配期解析 + 引擎接线 + 端到端验证**，闭合 L4-6 roadmap gap。其余 5 类（Tool / Command / MCP Server / Permission Rule / Router）的深度引擎解析为显式 Non-Goal（独立 successor），本计划只交付让它们可被注册、查询、消费的契约表面。

## Current Baseline

基于 live repo 核对（`nop-ai/nop-ai-agent/src/main`，2026-06-16）：

- **`IAgentLifecycleHook` + `IHookRegistry` + `DefaultHookRegistry` ✅**（plan 150 / L2-12，本计划直接依赖）：`IHookRegistry` 提供 `register(AgentLifecyclePoint, IAgentLifecycleHook)` + `getHooks(point, agentName)`；`AgentLifecyclePoint` 枚举 12 值（PRE_CALL ... AFTER_TOOL_RESULT_PROCESSED）。Hook 贡献的装配期解析目标就是向 `IHookRegistry` 注册 `IAgentLifecycleHook`。
- **System prompt 注入点已存在**：`ReActAgentExecutor.injectSystemInstruction(ctx, instruction)`（`:2962`）在现有 system 消息块尾部插入 `ChatSystemMessage`；`consultSkills`（`:2994`）已用同一机制注入 skill instruction 片段。Prompt 贡献解析的注入点就是该注入机制（additive）。
- **`DefaultAgentEngine` 装配期模式 ✅**：`resolveExecutor`（`engine/DefaultAgentEngine.java`）在执行前装配 `ReActAgentExecutor.Builder`，逐个透传扩展点（`.hookRegistry(...)` / `.messenger(...)` / `.auditLogger(...)` 等）。新增的 `IContributionRegistry` 装配遵循同一字段 + setter + resolveExecutor 透传模式（参照 plan 216 `messenger` 接线）。
- **模块扩展点惯例 ✅**：所有扩展点均为「接口 + NoOp/pass-through 默认（shipped，零行为回归）+ 可选功能实现」三件套（`IHookRegistry`/`NoOpHookRegistry`、`IMailbox`/`NoOpMailbox`、`IConflictStrategy`/`FailFastStrategy`、`IUsageRecorder`/`NoOpUsageRecorder` 等同构）。本计划的 NoOp 默认与装配期解析遵循同一惯例。
- **`IContributionRegistry` / `Contribution` / `ContributionType` 零实现**：grep `IContributionRegistry|ContributionType|ContributionRegistry` 在 `nop-ai-agent/src/main` 返回 0 命中（仅 design/analysis 文档与 javadoc 引用 PilotDeck 7 贡献概念）。
- **7 贡献类型定义（analysis §2.8）**：Tool（注册新工具）、Command（注册斜杠命令）、Hook（生命周期钩子）、MCP Server（提供 MCP 服务）、Permission Rule（自定义权限规则）、Prompt（注入提示模板）、Router（自定义路由策略）。
- **Actor Runtime（L4-8）未实现**：`IContributionRegistry` 的最终重度消费者（多租户 Actor 装配、跨进程贡献复制）尚未存在。本计划交付的注册表是 L4-8 可消费的独立原语。
- **roadmap §4**：`L4-6 | IContributionRegistry 7 贡献类型 | L2-12 | ❌`（line 245）。本计划关闭这一行。

## Goals

- **`ContributionType` 枚举**：7 值（`TOOL` / `COMMAND` / `HOOK` / `MCP_SERVER` / `PERMISSION_RULE` / `PROMPT` / `ROUTER`），与 analysis §2.8 一致。
- **`Contribution` 数据对象**：不可变，字段 `type`（ContributionType）+ `id`（同 type 内唯一）+ `source`（贡献来源标识，如插件 id）+ `priority`（int，升序，默认 0，与 Hook 引擎排序语义一致）+ `payload`（按 type 解释的对象，如 HOOK→`IAgentLifecycleHook`+绑定 point、PROMPT→String 片段、其他类型→typed payload）。
- **`IContributionRegistry` 接口**：`register(Contribution)`（重复 type+id 不同 source → fail-fast `NopAiAgentException`；同 source 重复 type+id → 替换）、`unregisterSource(source)`（按来源整体卸载，支持插件干净卸载）、`getContributions(type) → List<Contribution>`（按 type 升序 priority 返回）、`getContributions(type, source) → List`（来源过滤查询）、`getSources() → Set<String>`。Javadoc 明确线程安全契约与装配期解析时机。
- **`NoOpContributionRegistry` 默认**：shipped singleton；`register` 返回 false（显式 no-op，非静默成功，Minimum Rules #24）；查询返回空集；`getSources` 返回空。引擎默认使用它，零行为回归。
- **`InMemoryContributionRegistry` 功能实现**：线程安全（`ConcurrentHashMap` per-type + 复合操作同步），支持运行时动态注册/卸载，按 type/priority/source 稳定排序查询。
- **Hook 贡献装配期解析**：`DefaultAgentEngine.resolveExecutor` 在装配 `IHookRegistry` 后，把 `ContributionType.HOOK` 贡献解析为 `registry.register(point, hook)` 注册到 Hook 注册表（payload 含 `IAgentLifecycleHook` + 绑定的 `AgentLifecyclePoint`）。被解析的 Hook 在 ReAct 循环对应生命周期点真实触发（端到端可观测）。
- **Prompt 贡献装配期解析**：执行 setup 阶段，把 `ContributionType.PROMPT` 贡献的 String 片段经 `injectSystemInstruction` 注入 system prompt 上下文（additive，与 skill instruction 同模式），按 priority 升序拼接。
- **引擎接线**：`DefaultAgentEngine` 新增可选 `contributionRegistry` 字段（默认 `NoOpContributionRegistry`）+ `setContributionRegistry` setter；`resolveExecutor` 透传给 Builder；执行 setup 阶段执行装配期解析。
- **focused 测试**：注册表契约（register/unregister/查询/排序/去重/fail-fast）、NoOp 默认、Hook 解析、Prompt 解析各有覆盖。
- **端到端验证**：注册一个 HOOK 贡献 → 引擎装配 → Hook 在 ReAct 循环生命周期点真实触发（断言 hook 被调用）；注册一个 PROMPT 贡献 → 注入出现在 system 消息上下文中。
- **roadmap §4**：`L4-6` 行从 ❌ → ✅ 并标注本 plan。

## Non-Goals

- **Tool / Permission Rule / Router / Command / MCP Server 五类的深度引擎解析**：注册表存储这 5 类并提供 typed 查询，但不自动把它们解析进 `IToolManager` / 权限 checker / `IModelRouter` / 命令面 / MCP 服务面。理由：(1) L4-6 声明的唯一前置依赖是 L2-12（Hook），Tool/Permission/Router 各自有独立 DSL/构造器配置面与潜在依赖，自动解析是行为变更，应各自独立 successor；(2) Command 与 MCP Server 在引擎层无既有运行时消费面，需新建 surface；(3) Router 是单实例配置而非列表式贡献。Classification: successor plan required（每类独立 successor）。
- **Actor Runtime（L4-8）多租户装配与跨进程贡献复制**：注册表的最终重度消费者。本计划交付单进程 in-memory 注册表原语。Classification: successor plan required。
- **DB-backed 持久化贡献注册表**：跨进程/崩溃恢复的持久化贡献存储。Classification: successor plan required。
- **XDSL 配置化**：`agent.xdef` 增加 `<contributions>` 元素声明贡献。当前通过编程 API 注册。
- **动态热加载/卸载对正在运行的 session 生效**：本计划贡献在**装配期**（执行前 setup）解析一次，不在运行中 session 动态热生效。运行时热更新是 Actor Runtime 的语义裁定。Classification: successor plan required。
- **贡献间依赖/排序仲裁**：贡献声明对其他贡献的依赖、跨 type 全局排序。首版仅 per-type 内 priority 排序。Classification: optimization candidate。
- **warnIfInsecureDefaults 扩展**：贡献缺失不构成安全风险（贡献是增量能力，NoOp 下系统正常），无需 WARN（与 IMailbox / IUsageRecorder 裁定一致）。

## Scope

### In Scope

- 新增 `io.nop.ai.agent.contribution` 包：`ContributionType` 枚举（7 值）+ `Contribution` 数据对象 + `IContributionRegistry` 接口 + `NoOpContributionRegistry` 默认 + `InMemoryContributionRegistry` 功能实现
- `DefaultAgentEngine` + `ReActAgentExecutor.Builder` 接线（可选 `contributionRegistry` 字段 + setter + resolveExecutor 透传 + 装配期 Hook/Prompt 解析）
- `nop-ai-agent-hook-skill-engine.md`（新增「插件贡献注册表」节：7 类型 + 映射 + 装配期解析语义）+ `01-architecture-baseline.md`（扩展点清单补 IContributionRegistry）设计文档更新
- roadmap §4 L4-6 ❌→✅
- focused 测试 + 端到端测试

### Out Of Scope

- 见 Non-Goals（5 类深度解析 / Actor Runtime / DB 持久化 / XDSL / 热加载 / 跨 type 排序 / WARN 均为显式 successor）

### 设计裁定

**裁定 1：7 贡献类型与既有扩展点映射**——

| ContributionType | 装配期解析目标 | 本计划是否解析 |
|------------------|---------------|--------------|
| `HOOK` | `IHookRegistry.register(point, hook)` | ✅ 解析（L2-12 直接依赖） |
| `PROMPT` | `injectSystemInstruction` 注入 system prompt | ✅ 解析（additive，无重依赖，与 skill instruction 同模式） |
| `TOOL` | `IToolManager` 工具集 | ❌ successor（独立配置面 + executor 依赖） |
| `PERMISSION_RULE` | 权限 checker 合并 | ❌ successor（独立配置面） |
| `ROUTER` | `IModelRouter` 选择 | ❌ successor（单实例配置，非列表式） |
| `COMMAND` | 命令面（无既有运行时 surface） | ❌ successor（需新建 surface） |
| `MCP_SERVER` | MCP 服务面（无既有运行时 surface） | ❌ successor（需新建 surface） |

未解析的 5 类在注册表中可注册、可 typed 查询（消费者经 `getContributions(type)` 取出后自行消费），仅缺引擎自动装配。

**裁定 2：注册表身份与来源作用域**——每个 `Contribution` 带 `source`（贡献来源标识）。`unregisterSource(source)` 一次性卸载某来源的全部贡献，支持插件干净卸载/重装。`type+id` 在跨来源时唯一：不同 source 注册相同 `type+id` → fail-fast `NopAiAgentException`（避免静默覆盖）；同 source 重注册相同 `type+id` → 替换。

**裁定 3：解析时机 = 装配期一次**——贡献在 `ReActAgentExecutor` 执行 setup（首次 LLM 调用前，与 `consultSkills` 同阶段）解析一次，不在运行中 session 热生效。注册表对运行中 session 的后续变更不生效（符合模块既有装配期模型）。这使注册表与 session 生命周期解耦，避免运行时一致性复杂度。

**裁定 4：shipped 默认不变**——`DefaultAgentEngine.contributionRegistry` 默认 `NoOpContributionRegistry`（空注册表，无贡献可解析，零行为回归）。集成商显式 `engine.setContributionRegistry(new InMemoryContributionRegistry())` 并注册贡献后启用。不引入 WARN（贡献缺失非安全风险）。

**裁定 5：Hook 贡献 payload 契约**——`ContributionType.HOOK` 的 payload 须携带目标 `AgentLifecyclePoint` + `IAgentLifecycleHook` 实例（以 Hook 贡献能被装配期映射到 `IHookRegistry.register(point, hook)`）。装配期解析跳过 payload 类型不匹配的 Hook 贡献并 log WARN（fail-visible，非静默忽略，但单个坏贡献不阻断整批解析）。

## Execution Plan

### Phase 1 - 贡献契约表面 + NoOp 默认 + 设计裁定落档

Status: completed
Targets: `io.nop.ai.agent.contribution` 包（新增枚举 + 数据对象 + 接口 + NoOp 默认）、`ai-dev/design/nop-ai-agent/nop-ai-agent-hook-skill-engine.md`、`ai-dev/design/nop-ai-agent/01-architecture-baseline.md`

- Item Types: `Decision`、`Proof`

- [x] **裁定并落档** 7 贡献类型与既有扩展点映射表 + 解析时机 + 身份/来源作用域 + shipped 默认策略 + Hook payload 契约（设计裁定 1-5 写入设计文档）
- [x] 定义 `ContributionType` 枚举：7 值（TOOL / COMMAND / HOOK / MCP_SERVER / PERMISSION_RULE / PROMPT / ROUTER），与 analysis §2.8 一致
- [x] 定义 `Contribution` 不可变数据对象：`type` + `id` + `source` + `priority`（int，默认 0）+ `payload`（Object，按 type 解释）；构造期校验 type/id/source 非空
- [x] 定义 `IContributionRegistry` 接口行为契约：`register(Contribution) → boolean`、`unregisterSource(String source)`、`getContributions(ContributionType) → List<Contribution>`（升序 priority）、`getContributions(type, source)`、`getSources() → Set<String>`。Javadoc 明确：跨 source 重复 type+id fail-fast、装配期一次解析语义、线程安全契约
- [x] 实现 `NoOpContributionRegistry`（singleton，`register` 返回 false / 查询返回空集 / `getSources` 返回空）——显式 no-op，非静默成功（Minimum Rules #24）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `ContributionType` 枚举文件存在于 `io.nop.ai.agent.contribution` 包，恰好 7 个值且与 analysis §2.8 一致
- [x] `Contribution` + `IContributionRegistry` + `NoOpContributionRegistry` 文件存在，行为契约 Javadoc 清晰
- [x] `NoOpContributionRegistry.register(...)` 返回 false（非静默成功），查询返回空——无 no-op 伪装
- [x] `nop-ai-agent-hook-skill-engine.md` 已新增「插件贡献注册表」节：7 类型映射表 + 装配期解析语义 + 身份/来源作用域落档
- [x] `01-architecture-baseline.md` 扩展点清单已补 `IContributionRegistry`
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - InMemory 注册表 + Hook/Prompt 装配期解析 + 引擎接线 + 测试

Status: completed
Targets: `io.nop.ai.agent.contribution.InMemoryContributionRegistry`、`DefaultAgentEngine`、`ReActAgentExecutor.Builder`、`io.nop.ai.agent.contribution` 测试

- Item Types: `Proof`、`Follow-up`

- [x] 实现 `InMemoryContributionRegistry`：`ConcurrentHashMap` per-type 存储，复合操作（重复检测/替换/卸载）同步保护；跨 source 重复 type+id 抛 `NopAiAgentException`（含 type/id/source 上下文）；查询按 priority 升序稳定排序
- [x] `DefaultAgentEngine` 新增 `contributionRegistry` 字段（默认 `NoOpContributionRegistry`）+ `setContributionRegistry` setter；`resolveExecutor` Builder 链透传 `.contributionRegistry(this.contributionRegistry)`
- [x] Hook 贡献装配期解析：执行 setup 阶段，遍历 `getContributions(HOOK)`，把 payload（point + IAgentLifecycleHook）注册到 `IHookRegistry`；payload 不匹配的 Hook 贡献 log WARN 跳过（fail-visible）
- [x] Prompt 贡献装配期解析：执行 setup 阶段，遍历 `getContributions(PROMPT)`（升序 priority），把 String 片段经 `injectSystemInstruction` 注入 system prompt 上下文（additive）
- [x] 编写 focused 测试：注册表契约（register/unregister/查询/排序/同 source 替换/跨 source fail-fast/unregisterSource 整体卸载）、NoOp 默认、Hook 解析、Prompt 解析
- [x] 编写端到端测试：注册 HOOK 贡献 → `DefaultAgentEngine` 装配 → ReAct 循环对应生命周期点真实触发 hook（断言 hook 回调计数 > 0）；注册 PROMPT 贡献 → 片段出现在 system 消息上下文

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `InMemoryContributionRegistry` 线程安全，跨 source 重复 type+id fail-fast（NopAiAgentException），查询按 priority 升序
- [x] **接线验证**（Minimum Rules #23）：`DefaultAgentEngine` 设置的 `contributionRegistry` 在运行时被 `ReActAgentExecutor` 装配期解析（断言 HOOK 贡献注册到 `IHookRegistry`、PROMPT 贡献注入 system prompt）
- [x] **端到端验证**（Minimum Rules #22）：从 `engine.setContributionRegistry(...)` + `registry.register(hookContribution)` 入口，经装配期解析，到 ReAct 循环生命周期点 hook 真实触发的完整路径跑通（hook 回调断言）；PROMPT 贡献注入 system prompt 路径跑通
- [x] **无静默跳过**（Minimum Rules #24）：payload 不匹配的 Hook 贡献 log WARN 跳过（非静默吞没）；`NoOpContributionRegistry.register` 返回 false
- [x] shipped 默认（`NoOpContributionRegistry`）下既有测试零回归
- [x] 新增功能各有对应 focused 测试覆盖（注册表契约 / Hook 解析 / Prompt 解析 / NoOp 默认）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] roadmap §4 L4-6 ❌→✅ 已更新
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] `IContributionRegistry` 契约表面（7 类型枚举 + 数据对象 + 接口 + NoOp 默认）已落地
- [x] `InMemoryContributionRegistry` 功能实现 + Hook/Prompt 装配期解析 + 引擎接线已落地
- [x] 端到端：HOOK 贡献 → 装配 → ReAct 触发；PROMPT 贡献 → 注入 system prompt，两条解析路径完整跑通
- [x] shipped 默认（NoOp）下既有测试零回归
- [x] 必要 focused verification 已完成（注册表契约 / Hook 解析 / Prompt 解析 / NoOp 各有测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（5 类深度解析 / Actor Runtime / DB 持久化 / XDSL / 热加载均显式在 Non-Goals 切出）
- [x] 受影响 owner docs（`nop-ai-agent-hook-skill-engine.md` + `01-architecture-baseline.md`）已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）`IContributionRegistry` 在运行时被 `ReActAgentExecutor` 装配期解析（不只类型存在），（b）Hook 贡献在 ReAct 循环真实触发、Prompt 贡献真实注入，（c）无空方法体/静默跳过作为正常实现
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（暂无；5 类深度解析 / Actor Runtime / DB 持久化 / XDSL / 热加载 / 跨 type 排序均为显式 Non-Goals 独立 successor，非本计划 scope 内 deferred 项。）

## Non-Blocking Follow-ups

- **Tool 贡献解析**：把 `ContributionType.TOOL` 贡献解析进 `IToolManager` 工具集（payload 须含工具 spec + executor）。Classification: successor plan required。
- **Permission Rule 贡献解析**：把 `PERMISSION_RULE` 贡献合并进权限 checker。Classification: successor plan required。
- **Router 贡献解析**：`ROUTER` 贡献参与 `IModelRouter` 选择（单实例配置，需裁定贡献如何参与选择）。Classification: successor plan required。
- **Command / MCP Server 消费面**：在引擎层新建命令面 / MCP 服务面以消费这两类贡献。Classification: successor plan required。
- **DB-backed 持久化贡献注册表 / 跨进程复制**：依赖 L4-8 Actor Runtime。Classification: successor plan required。
- **XDSL 配置化**：`agent.xdef` 增加 `<contributions>` 元素。Classification: optimization candidate。
- **运行时热加载/卸载对运行中 session 生效**：Classification: successor plan required（依赖 L4-8）。
- **贡献间依赖/跨 type 全局排序**：首版仅 per-type priority 排序。Classification: optimization candidate。

## Closure

Status Note: Plan 217 closes roadmap §4 L4-6 (`IContributionRegistry` 7 贡献类型). The full contribution contract surface (7-type enum + immutable data object + typed payload for HOOK + registry interface + NoOp shipped default + InMemory functional implementation) is landed, with HOOK and PROMPT contribution types auto-resolved at engine assembly time (HOOK → IHookRegistry in `DefaultAgentEngine.resolveExecutor`; PROMPT → system prompt via `injectSystemInstruction` in `ReActAgentExecutor.execute()` setup). The remaining five contribution types (TOOL / COMMAND / MCP_SERVER / PERMISSION_RULE / ROUTER) are registerable/queryable but their deep engine integration is an explicit successor each — declared as Non-Goals with concrete rationale. Shipped default (NoOpContributionRegistry) is zero-regression: `register` returns false explicitly (no silent success), all queries return empty. No owner-doc drift: `nop-ai-agent-hook-skill-engine.md` §8 + `01-architecture-baseline.md` updated. All 1976 tests pass (1927 prior baseline + 49 new).

Completed: 2026-06-16

Closure Audit Evidence:

- Reviewer / Agent: independent closure-audit subagent (task_id `ses_13070a967ffePt3SyEU6nx3K2Q`, fresh session, not the implementation session).
- Audit Session: `ses_13070a967ffePt3SyEU6nx3K2Q`
- Evidence:
  - Phase 1 Exit Criteria — all PASS (contract surface + NoOp default + design docs synced)
  - Phase 2 Exit Criteria — all PASS (InMemory registry + engine wiring + executor wiring + focused tests + e2e tests)
  - Closure Gates — all 12 items PASS (see ticked checkboxes above)
  - **Contract surface** (`ContributionType.java:24-32`): exactly 7 enum values TOOL/COMMAND/HOOK/MCP_SERVER/PERMISSION_RULE/PROMPT/ROUTER; `TestContributionAndPayload.contributionTypeHasExactlySevenValues` asserts `assertEquals(7, ...)`.
  - **NoOp explicit no-op** (`NoOpContributionRegistry.java:29-31`): `register` returns `false`; `TestNoOpContributionRegistry.registerReturnsFalseExplicitNoOp` asserts `assertFalse(...)`. Not a silent success (Minimum Rules #24).
  - **InMemory thread-safety + cross-source fail-fast** (`InMemoryContributionRegistry.java:66-93`): `ConcurrentHashMap` outer + `synchronized(inner)` on compound ops; `NopAiAgentException` on cross-source (type,id) collision with type/id/source context; `TestInMemoryContributionRegistry.crossSourceCollisionThrows` verifies message + that the existing entry was NOT overwritten.
  - **Wiring Verification** (Minimum Rules #23): `DefaultAgentEngine.resolveExecutor` calls `resolveHookContributions(hookRegistry)` at `:1867` AND passes `.contributionRegistry(this.contributionRegistry)` to Builder at `:1904`; `ReActAgentExecutor.execute()` calls `consultPromptContributions(ctx)` at `:877` between `consultSkills` and `buildChatOptions`. Both wiring points confirmed non-trivial (not stubs).
  - **End-to-end** (Minimum Rules #22): `TestContributionResolution.hookContributionFiresAtLifecyclePoint_e2e` asserts `fireCount.get() > 0` after `engine.resolveExecutor` → `executor.execute(ctx)`; `promptContributionInjectedIntoSystemMessages_e2e` captures LLM system messages and asserts fragment is present. Full register→assembly→runtime-fire path verified.
  - **Anti-Hollow** (Minimum Rules #22 + #24): No empty method bodies; every `continue` skip is preceded by `LOG.warn` (HOOK `:1944`, PROMPT `:3092`); no caught-and-swallowed exceptions. NoOp is explicit false; bad payloads WARN-skip. Call chain traced: `setContributionRegistry` → `resolveExecutor` → `resolveHookContributions` → `hookRegistry.register` → executor lifecycle hook fire (HOOK chain); `registry.register(PROMPT)` → `execute()` → `consultPromptContributions` → `injectSystemInstruction` → LLM call (PROMPT chain). No missing links.
  - **Zero regression**: `./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → Tests run: 1976, Failures: 0, Errors: 0, Skipped: 0 (prior baseline 1927 + 49 new tests).
  - **Import-order / style**: `bash ai-dev/tools/check-import-order.sh nop-ai/nop-ai-agent` → 0 errors in nop-ai-agent (12 pre-existing errors are in nop-stream modules untouched by this plan).
  - **checklist verification**: `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/217-nop-ai-agent-contribution-registry.md --strict` → exit code 0.
  - **Deferred adjudication**: 5 类深度解析 (TOOL/PERMISSION_RULE/ROUTER/COMMAND/MCP_SERVER) + Actor Runtime (L4-8) + DB 持久化 + XDSL + 运行时热加载 + 跨 type 排序 — all explicitly declared as Non-Goals with concrete rationale (each is an independent successor with its own configuration surface or requires a new runtime surface). No in-scope live defect downgraded to follow-up.

Follow-up:

- No remaining plan-owned work. All Non-Goals are explicit successors (see `## Non-Blocking Follow-ups`).
