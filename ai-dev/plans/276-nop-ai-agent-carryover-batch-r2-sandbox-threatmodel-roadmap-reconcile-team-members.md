# 276 nop-ai-agent——Carry-over 批次 (R2)：sandbox env-source 威胁模型收口 + roadmap 一致性修正 + Team.members 类型收敛

> Plan Status: completed
> **Module**: nop-ai-agent
> **Work Item**: env-source (carry-over, P2) + roadmap-§2.2-stale (P3) + 15-1 (carry-over, P3)
> Last Reviewed: 2026-06-20
> Source: carry-over 自 `ai-dev/plans/274-nop-ai-agent-docker-sandbox-flag-correctness.md` §Non-Blocking Follow-ups（env-source，successor required）；carry-over 自 `ai-dev/plans/272-nop-ai-agent-configuration-fixes.md` / `ai-dev/plans/275-nop-ai-agent-record-mapping-scope-fix.md`（15-1 `Team.members` 类型收敛，optimization candidate）；roadmap §2.1/§2.2/§7 owner-doc drift（roadmap 检查发现）
> Related: 274（env-source 来源）、272/275（15-1 来源）、219（sandbox 引入）、270（security hardening）

## Purpose

收口 nop-ai-agent 当前 carry-over 队列中三项彼此独立但均低于独立 plan 门槛的剩余项，合并为一个批次 plan：

1. **env-source（P2）**：完成 plan 274 显式 deferred 的 `SandboxRequest.environmentVariables` 来源链路追溯，闭合 sandbox 环境变量注入威胁模型（plan 274 已在消费侧 `DockerSandboxBackend.buildDockerCommand` 落地 fail-closed POSIX 键校验，但留下"键是否真的 LLM 可控"这一威胁模型前提未验证）。
2. **roadmap-§2.2-stale（P3）**：修正 `nop-ai-agent-roadmap.md` §2.1（M4b）、§2.2（组件状态表）、§7（审计检查清单）与 §4（Layer 工作项清单）及 live repo 之间的全面不一致——§2.2 声称"所有运行时代码均为零实现"，而实际 §4 与 live repo 显示 L0–L4 已完成。
3. **15-1（P3）**：`Team.members` 字段类型从 `Map<String, TeamMember>` 收敛为 `ConcurrentMap<String, TeamMember>`，使线程安全契约在类型系统中显式化（所有构造点已使用 `ConcurrentHashMap`）。

## Bundled Items

- **env-source** — 来源 plan 274 §Non-Blocking Follow-ups（"env 键来源链路……独立 successor，不影响本计划消费侧校验正确性"）。代码追溯结论：`SandboxRequest` 在 main src 中**无任何生产构造点**，`ISandboxBackend.execute` 在 main src 中**从未被调用** → env vars 当前仅来源于测试 fixture 与 Builder 默认，键当前**非 LLM 可控**。威胁模型结论：plan 274 的 fail-closed 校验是 defense-in-depth（为未来 shell-exec/code-exec 工具执行器接入 sandbox 时预留的正确性），当前无活跃攻击面。需将该结论写入设计文档与 roadmap。
- **roadmap-§2.2-stale** — roadmap 检查发现的 owner-doc drift。`nop-ai-agent-roadmap.md` §2.1 line 29（M4b ❌ 缺失）与 live repo 矛盾：`agent.register-model.xml` 已存在于 `_vfs/nop/core/registry/`；§2.2 line 37（"所有运行时代码均为零实现"）与 §2.2 表（ReAct 引擎/Agent Engine/AgentSession/IPermissionProvider/IToolCallRepairer/单元测试 等标记 ❌ 未开始）与 §4 Layer 表（均 ✅）及 live repo（`DefaultAgentEngine` 3435 行 + `ReActAgentExecutor` + 2753 测试）全面矛盾。
- **15-1** — 来源 plan 272 §Deferred But Adjudicated + plan 275 §Non-Blocking Follow-ups（"`Team.members` ConcurrentMap 类型优化，optimization candidate，无 successor"）。`Team.java:48` 字段类型为 `Map<String, TeamMember>`；**生产**构造点（`DbTeamManager.java:804`、`InMemoryTeamManager.java:128`）均传入 `ConcurrentHashMap`，但**测试**构造点 `TestTeamExecuteFlowExecutor.java:505` 传入 `Collections.emptyMap()`（非 `ConcurrentMap` 实例）→ 类型收敛后该测试调用点需迁移为 `ConcurrentMap` 兼容实例（如 `new ConcurrentHashMap<>()`）才能编译。收敛字段/构造参数/getter 返回类型为 `ConcurrentMap` 使 Javadoc 已声明的线程安全契约在类型层面可验证。

> **Granularity note（bundle justification）**：三项单独均低于独立 plan 门槛——env-source ≈ 0 行生产代码（追溯 + 威胁模型文档 ≈ 40 行 docs）；roadmap-§2.2-stale ≈ 0 行生产代码（docs 一致性修正 ≈ 50–70 行 docs）；15-1 ≈ 10 行生产代码 + 15 行测试。env-source 与 roadmap-§2.2-stale 同文件（`nop-ai-agent-roadmap.md`），同属 docs/owner-doc 一致性收口面，是 bundle-eligible sibling。15-1 作为 carry-over 队列中需安置的小型代码项并入，使批次同时包含 docs 收口与代码收口。合并后总 churn ≈ 115–135 行（docs ≈ 90–110 + 代码 ≈ 25），超过 ~100 行门槛。批次主题明确：清理 nop-ai-agent carry-over 队列（R2，继 plan 275 R1 之后）。

## Current Baseline

- **Sandbox 来源链路（env-source）现状**：
  - `SandboxRequest.java`（`io.nop.ai.agent.security`）：`environmentVariables` 为 `Map<String,String>`，经 `Map.copyOf` 防御性拷贝（plan 274 baseline）；键/值无字符约束。
  - `grep -rn "new SandboxRequest|SandboxRequest.Builder|SandboxRequest(" nop-ai/ --include="*.java" | grep -v /test/` → 仅 `SandboxRequest.java` 自身构造（`:35` private + `:108` Builder.build），**main src 无任何生产构造点**。
  - `grep -rn "sandboxBackend.execute|backend.execute" nop-ai/nop-ai-agent/src/main/java/` → **0 命中**；`ISandboxBackend.execute` 在 main src 从未被调用。
  - `DefaultAgentEngine.java:346-347`：`sandboxBackend` 字段默认 `NoOpSandboxBackend.INSTANCE`；`:1520` `setSandboxBackend(...)` setter 存在；`:3114` 传递给 `ReActAgentExecutor.Builder.sandboxBackend(...)`。`ReActAgentExecutor.java:299` 存储该字段但从不调用 `execute`。
  - 设计文档 `nop-ai-agent-security-and-permissions.md:612`：明确写"高风险工具执行器（shell-exec / code-exec `IToolExecutor`）如何消费 `ISandboxBackend` 是独立 successor plan；本契约仅提供平台级隔离能力"——即 sandbox→工具执行器接线是**显式记录的设计性 deferred**，非缺陷。
  - 结论前提：env vars 当前仅来源于测试 fixture（`TestDockerSandboxBackend`/`TestNoOpSandboxBackend` 构造 `SandboxRequest` 时直接传 env map）与 Builder 默认空 map；**无路径**从 LLM tool call 到达 `SandboxRequest.environmentVariables`。
- **Roadmap 不一致现状**：
  - `nop-ai-agent-roadmap.md:29`（§2.1 M4b）：`❌ **缺失** | 无 agent.register-model.xml，.agent.xml 文件无法被加载` —— 与 live repo 矛盾：`nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent.register-model.xml` **已存在**。
  - `nop-ai-agent-roadmap.md:37`（§2.2 表前说明）：`所有运行时代码均为零实现` —— 与 live repo 矛盾：`DefaultAgentEngine.java`（3435 行）、`ReActAgentExecutor.java`、`AgentSession` 系列等均存在，2753 测试全绿。
  - `nop-ai-agent-roadmap.md:39-56`（§2.2 表）：ReAct 执行引擎/Agent Engine/AgentSession/AgentEventPublisher/IPermissionProvider/IToolCallRepairer/IContextCompactor/IContentGuardrail/IModelRouter/IRetryPolicy/单元测试 均标记 `❌ 未开始`，而 §4 Layer 表（L0–L4）显示对应工作项 ✅。
  - `nop-ai-agent-roadmap.md:411-413`（§7 审计检查清单）：需核查是否同样含过时状态标记。
  - `nop-ai-agent-roadmap.md:359`（§5c AUDIT-13-9）：已标 ✅（plan 274 完成）；env-source 无独立 §5c 行（为 plan 274 follow-up）。
- **Team.members 现状（15-1）**：
  - `Team.java:48`：`private final Map<String, TeamMember> members;`
  - `Team.java:62`：构造参数 `Map<String, TeamMember> members`
  - `Team.java:89`：`public Map<String, TeamMember> getMembers()`
  - `Team.java:20-23` Javadoc：已声明"the manager serialises access via ConcurrentHashMap"——即运行时始终为 `ConcurrentHashMap`，但静态类型未表达。
  - 构造点现状：**生产**构造点 `DbTeamManager.java:804`（`new ConcurrentHashMap<>(memberRows.size())`）、`InMemoryTeamManager.java:128`（`new ConcurrentHashMap<>()`）均传入 `ConcurrentHashMap`，已是 `ConcurrentMap` 子类型——类型收敛对生产调用点零破坏。**测试**构造点 `TestTeamExecuteFlowExecutor.java:505` 传入 `Collections.emptyMap()`（`Collections.EmptyMap` **不实现** `ConcurrentMap`）→ 类型收敛后该调用点**无法编译**，需迁移为 `new ConcurrentHashMap<>()`。
  - 消费点现状：`InMemoryTeamManager.java:219`（以及 :247/:273）为 `(ConcurrentHashMap<String,TeamMember>) team.getMembers()` 强制 cast（消费点，非构造点）；`InMemoryTeamManager.java:338` 为 `Map<String,TeamMember> members = team.getMembers()`（`Map` 类型消费点，`ConcurrentMap` 是 `Map` 子接口，仍兼容）。

## Goals

- **env-source 威胁模型收口**：以可追溯的代码 grep 证据 + 设计文档注记，明确记录"`SandboxRequest.environmentVariables` 当前键非 LLM 可控（无生产构造点、`execute` 从未被调用），plan 274 fail-closed 校验为 defense-in-depth"；同步设计 `nop-ai-agent-security-and-permissions.md` §7.1 与 roadmap §5c。
- **roadmap 一致性修正**：使 `nop-ai-agent-roadmap.md` §2.1（M4b）、§2.2（表 + 表前说明）、§7（检查清单）与 §4（Layer 工作项清单）及 live repo 一致——M4b 改为 ✅（文件存在并已验证可加载）、§2.2 表与表前说明反映 L0–L4 已完成的真实状态。
- **Team.members 类型收敛（15-1）**：`Team.java` 字段/构造参数/getter 返回类型从 `Map` 收敛为 `ConcurrentMap`，import 同步，Javadoc 线程安全契约与类型一致；生产构造点零破坏（已传 `ConcurrentHashMap`），测试构造点 `TestTeamExecuteFlowExecutor.java:505`（`Collections.emptyMap()`）迁移为 `ConcurrentMap` 兼容实例以保证编译通过。

## Non-Goals

- **不接线 sandbox → 工具执行器**：sandbox→shell-exec/code-exec 工具执行器的消费接线是设计文档 `nop-ai-agent-security-and-permissions.md:612` 明确的独立 successor plan（跨 nop-ai-agent 契约层 + nop-ai-shell 工具层的大特性），不属于本 carry-over 批次。本计划只完成威胁模型追溯与文档结论。
- **不新增 SandboxRequest 生产构造点**：不在本计划内创建任何消费 sandbox 的工具执行器。
- **不改变 env-key 校验逻辑**：plan 274 已落地的 `DockerSandboxBackend.buildDockerCommand` POSIX 键校验与 `INVALID_ENVIRONMENT_VARIABLE` reason 保持不变；本计划只记录其威胁模型定位。
- **不处理 05-2（plan-model xdef codegen 管线重设计）/14-04（executor 完整重设计）**：均为 ill-defined / 大型 optimization candidate，no successor required，超出本批次范围。
- **不重写 §4 Layer 工作项清单本身**：§4 是 source of truth；本计划只把 §2.1/§2.2/§7 对齐到 §4，不反向修改 §4。
- **不改 `Team` 的线程安全模型**：仅收敛静态类型以表达既有 Javadoc 已声明的契约，不引入新的同步机制或改变 manager 的串行化职责。

## Scope

### In Scope

- **env-source**：
  - 代码 grep 证据固化（在 plan 文件内记录追溯命令与结果，作为威胁模型依据）。
  - 设计 `nop-ai-agent-security-and-permissions.md` §7.1 补一段威胁模型注记：env vars 当前来源（测试 fixture + Builder 默认）、键非 LLM 可控的依据（无生产构造点 / `execute` 未被调用）、plan 274 校验的 defense-in-depth 定位、以及"当 shell-exec/code-exec 工具执行器接入 sandbox 时键将变为 LLM 可控"的前瞻说明。
  - roadmap §5c 补 env-source 行（标 ✅ 已追溯，引用本 plan）。
- **roadmap-§2.2-stale**：
  - §2.1 M4b（line 29）：`❌ 缺失` → `✅ 已完成`，说明改为"`agent.register-model.xml` 已存在，`.agent.xml` 可经 xdsl-loader 加载"。
  - §2.2 表前说明（line 37）：删除/改写"所有运行时代码均为零实现"为反映 L0–L4 已完成的真实状态描述。
  - §2.2 表（line 39-56）：逐行核对，已完成的组件状态从 `❌ 未开始` 改为对应 §4 的真实状态（✅ 或注明所在 plan），未完成项保留并注明真实状态。
  - §7 审计检查清单（line 411+）：核查并修正过时状态标记，与 §4 一致。
- **15-1**：
  - `Team.java`：字段类型（`:48`）、构造参数类型（`:62`）、getter 返回类型（`:89`）从 `Map<String, TeamMember>` 改为 `ConcurrentMap<String, TeamMember>`；import `java.util.concurrent.ConcurrentMap`。
  - Javadoc（`:20-23`、`:86-88`）同步：线程安全契约与类型一致。
  - 既有构造点（`DbTeamManager`/`InMemoryTeamManager`）无需改动（已传 `ConcurrentHashMap`），但需编译验证零破坏。
  - 新增/调整 focused 测试：断言 `getMembers()` 返回类型为 `ConcurrentMap`（或构造传入 `ConcurrentHashMap` 后 `getMembers()` 可作为 `ConcurrentMap` 使用）。

### Out Of Scope

- sandbox → 工具执行器接线（设计文档明确的独立 successor）。
- 05-2 / 14-04（大型 optimization candidate，no successor required）。
- roadmap §4 Layer 工作项清单本身的重写（§4 为 source of truth）。
- `Team` 线程安全模型变更。
- env-key 校验逻辑变更（plan 274 已完成）。

## Execution Plan

### Phase 1 - env-source 威胁模型追溯与收口

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-security-and-permissions.md`（§7.1 威胁模型注记）、`ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`（§5c env-source 行）、源 plan 274（follow-up 链接）

- Item Types: `Proof | Follow-up`

- [x] 固化 env-source 追溯证据：在 plan 文件记录 `grep` 命令与结果——main src 无 `SandboxRequest` 生产构造点、`ISandboxBackend.execute` 从未被调用、`environmentVariables` 仅来源于测试 fixture 与 Builder 默认空 map。
- [x] 在设计 `nop-ai-agent-security-and-permissions.md` §7.1（ISandboxBackend 段落）补一段威胁模型注记，明确：(a) env vars 当前来源（测试 fixture + Builder 默认）；(b) 键当前非 LLM 可控的依据（无生产构造点 / `execute` 未被调用）；(c) plan 274 fail-closed POSIX 键校验的 defense-in-depth 定位；(d) 前瞻说明——当 shell-exec/code-exec 工具执行器接入 sandbox（独立 successor）时键将变为 LLM 可控，届时 plan 274 校验从 defense-in-depth 升级为活跃防线。
- [x] 在 roadmap §5c 补 env-source 行：标 ✅ 已追溯（引用本 plan + 设计 §7.1 注记）。
- [x] **Carry-over workflow admin**：在源 plan `274-nop-ai-agent-docker-sandbox-flag-correctness.md` 末尾添加 `## Follow-up handled by 276-nop-ai-agent-carryover-batch-r2-sandbox-threatmodel-roadmap-reconcile-team-members.md`（创建可追溯链接）。

#### Trace Evidence（固化追溯命令与结果，作为威胁模型依据）

执行于 `nop-entropy-master` 根，日期 2026-06-20：

1. `SandboxRequest` 生产构造点（main src，排除 test）：
   - 命令：`grep -rn "SandboxRequest\.(builder|of)|new SandboxRequest" nop-ai/ --include="*.java"` 后过滤 `src/main/java/`
   - 结果：**0 命中**于 main src（唯一命中 `SandboxRequest.java:108` 为 `Builder.build()` 自身，即 `return new SandboxRequest(this)`）。全部其余 26 处构造点位于 `src/test/java/`（`TestDockerSandboxBackend`/`TestNoOpSandboxBackend`/`TestSandboxWiring`）。
   - 结论：`SandboxRequest` 在 main src 中**无任何生产构造点**。

2. `ISandboxBackend.execute` 调用点（main src）：
   - 命令：`grep -rn "sandboxBackend\.execute|getSandboxBackend\(\)\.execute|\.execute\(.*[Ss]andbox" nop-ai/nop-ai-agent/src/main/java/`
   - 结果：**0 命中**。
   - 结论：`ISandboxBackend.execute(...)` 在 main src 中**从未被调用**。

3. sandbox 字段存储链路（佐证"仅存储不调用"）：
   - `DefaultAgentEngine.java:346-347`：`private ISandboxBackend sandboxBackend = NoOpSandboxBackend.INSTANCE;`（默认 NoOp）
   - `DefaultAgentEngine.java:1520`：`setSandboxBackend(...)` setter（null-safe 回退 NoOp）
   - `DefaultAgentEngine.java:3114`：`.sandboxBackend(this.sandboxBackend)`（透传到 `ReActAgentExecutor.Builder`）
   - `ReActAgentExecutor.java:299`：`private final ISandboxBackend sandboxBackend;`（存储），`:445-447` 构造器赋值，**无任何 `sandboxBackend.execute(...)` 调用**。

4. 综合结论：`environmentVariables` 当前仅来源于测试 fixture 与 Builder 默认空 map；**无路径**从 LLM tool call 到达 `SandboxRequest.environmentVariables`，env 键当前**非 LLM 可控**。plan 274 fail-closed POSIX 键校验定位为 defense-in-depth（无活跃攻击面，但消费侧校验为独立 successor 预留正确性）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 设计 `nop-ai-agent-security-and-permissions.md` §7.1 存在威胁模型注记段落，内容覆盖 (a)–(d) 四点（grep 文件 + 章节标题可验证）。
- [x] roadmap §5c 存在 env-source 行且标 ✅（grep `env-source` 于 roadmap 命中该行可验证）。
- [x] plan 文件内固化了追溯 grep 命令与结果（main src 0 生产构造点 + `execute` 0 调用）。
- [x] 源 plan 274 末尾含 `## Follow-up handled by 276-*.md` 段落（grep 可验证）。
- [x] **接线验证**（如适用）：本 Phase 为 docs/analysis，无新增组件接线——明确写 `No wiring verification required: docs/analysis-only phase`。
- [x] **无静默跳过**（如适用）：明确写 `No silent no-op risk: docs-only phase, no new code branches`。
- [x] 若该 Phase 改变 live baseline：相关 `ai-dev/design/` 已更新（§7.1 注记 + roadmap §5c）；`ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - roadmap §2.1/§2.2/§7 一致性修正

Status: completed
Targets: `ai-dev/design/nop-ai-agent/nop-ai-agent-roadmap.md`（§2.1 M4b、§2.2 表 + 表前说明、§7 审计检查清单）

- Item Types: `Fix`

- [x] §2.1 M4b（line 29）：状态从 `❌ 缺失` 改为 `✅ 已完成`；说明改为"`agent.register-model.xml` 已存在（`_vfs/nop/core/registry/`），`.agent.xml` 经 xdsl-loader 加载"。
- [x] §2.2 表前说明（line 37）：改写"所有运行时代码均为零实现"为反映 L0–L4 已完成真实状态的描述（如"§2.2 表记录初始分解时的未实现项；截至本更新，多数组件已由 §4 Layer 工作项实现，状态以 §4 为准"）。
- [x] §2.2 表（line 39-56）：逐行核对每个组件——已完成者（ReAct 引擎/Agent Engine/AgentSession/IPermissionProvider/IToolCallRepairer/单元测试 等对应 §4 ✅ 的项）状态改为 ✅ 并注明对应 Layer/plan；真实未完成者保留 ❌ 并注明真实状态；确保与 §4 无矛盾。
- [x] §7 审计检查清单（line 411+）：核查并修正过时状态标记，使其与 §4 一致。
- [x] 更新 roadmap 顶部 `> Updated:` 行（line 4）记录本次一致性修正（引用本 plan）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap §2.1 M4b 状态为 ✅ 且说明引用 `agent.register-model.xml` 已存在（grep roadmap M4b 行可验证，且 `find` 确认文件仍存在于 `_vfs/nop/core/registry/agent.register-model.xml`）。
- [x] roadmap §2.2 不再含"所有运行时代码均为零实现"表述（grep 该句 0 命中）。
- [x] roadmap §2.2 表中每个已完成组件状态与 §4 Layer 表一致（无 ❌ 与 §4 ✅ 的矛盾行）。
- [x] roadmap §7 审计检查清单无与 §4 矛盾的过时标记。
- [x] roadmap `> Updated:` 行已记录本次修正并引用本 plan。
- [x] **接线验证**（如适用）：明确写 `No wiring verification required: docs-only phase`。
- [x] **无静默跳过**（如适用）：明确写 `No silent no-op risk: docs-only phase`。
- [x] owner-doc 已更新（本 Phase 即 owner-doc 修正）；`ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - Team.members ConcurrentMap 类型收敛（15-1）

Status: completed
Targets: `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/team/Team.java`、对应 `src/test/.../team/` 测试、源 plan 272/275（follow-up 链接）

- Item Types: `Fix`

- [x] `Team.java`：字段类型（`:48`）、构造参数类型（`:62`）、getter 返回类型（`:89`）从 `Map<String, TeamMember>` 改为 `ConcurrentMap<String, TeamMember>`；新增 `import java.util.concurrent.ConcurrentMap;`（同时移除不再使用的 `import java.util.Map;`）。
- [x] 同步 `Team.java` Javadoc（`:20-23` members 说明、`:86-88` getMembers 说明）：线程安全契约与 `ConcurrentMap` 类型一致。
- [x] 核查所有 `Team` 构造点（生产 `DbTeamManager.java:815`/`InMemoryTeamManager.java:137` + 测试 `TestTeamExecuteFlowExecutor.java:506` 及任何其他调用 `new Team(...)` 的位置）：确认传入类型为 `ConcurrentMap` 子类型；**特别修正 `TestTeamExecuteFlowExecutor.java:506` 的 `Collections.emptyMap()`**（非 `ConcurrentMap` 实例）为 `new ConcurrentHashMap<>()`（或等价），否则编译失败。（注：`DbTeamManager.java:804` 局部变量声明类型从 `Map` 窄化为 `ConcurrentMap` 以满足构造参数静态类型——对象本身已是 `ConcurrentHashMap`，属必要且最小的类型声明同步。）
- [x] 核查 `getMembers()` 的消费点：确认无调用方依赖 `Map`（非 `ConcurrentMap`）独有方法（grep `getMembers()` 消费点）。
- [x] 新增/调整 focused 测试：断言 `Team.getMembers()` 返回值可赋值给 `ConcurrentMap` 类型变量（或 `instanceof ConcurrentMap`），验证构造传入 `ConcurrentHashMap` 后类型收敛生效。
- [x] **Carry-over workflow admin**：在源 plan `272-nop-ai-agent-configuration-fixes.md` 与 `275-nop-ai-agent-record-mapping-scope-fix.md` 末尾添加 `## Follow-up handled by 276-*.md`（15-1 收口链接）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `Team.java` 中 `members` 相关标识符静态类型均为 `ConcurrentMap`（grep `Map<String, TeamMember> members` 在 `Team.java` 0 命中；`ConcurrentMap<String, TeamMember>` 命中）。
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过（含 test 编译，证明 `TestTeamExecuteFlowExecutor.java:506` 迁移后生产+测试构造点均兼容）。
- [x] focused 测试存在并断言 `getMembers()` 为 `ConcurrentMap` 类型；`./mvnw test -pl nop-ai/nop-ai-agent -am` 通过。
- [x] 源 plan 272 与 275 末尾均含 `## Follow-up handled by 276-*.md` 段落（grep 可验证）。
- [x] **接线验证**：`Team` 构造点（生产 `DbTeamManager`/`InMemoryTeamManager` 传 `ConcurrentHashMap` + 测试 `TestTeamExecuteFlowExecutor.java:506` 迁移为 `ConcurrentHashMap`）经类型收敛后仍编译通过，证明调用链连通（编译通过即证明）。
- [x] **无静默跳过**：类型收敛不引入空方法体或 `continue` 跳过——focused 测试覆盖。
- [x] 若该 Phase 改变 live baseline：`Team` 的公共契约（getter 返回类型）从 `Map` 收敛为 `ConcurrentMap`——这是 narrowing 收敛（`ConcurrentMap` 是 `Map` 子接口，既有 `Map` 消费点仍兼容）；在 `ai-dev/logs/` 记录此契约变化。`No owner-doc update required beyond Javadoc`（设计文档未细化 `Team.members` 字段类型）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。
>
> **纯文档 + 小型代码混合计划**：Phase 1/2 为纯文档/分析（无生产代码变更），Phase 3 含小型代码变更。Closure Gates 保留构建/测试条目以覆盖 Phase 3。

- [x] env-source 威胁模型已收口（设计 §7.1 注记 + roadmap §5c 行 + plan 内追溯证据固化）
- [x] roadmap §2.1/§2.2/§7 与 §4 及 live repo 一致（M4b ✅、§2.2 无"零实现"表述、表状态与 §4 一致）
- [x] `Team.members` 类型收敛完成（`ConcurrentMap`、编译通过、focused 测试覆盖）
- [x] 源 plan 274/272/275 均含 `## Follow-up handled by 276-*.md` 链接
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（env-source/roadmap-stale/15-1 均在本计划内收口）
- [x] 受影响 owner docs（设计 §7.1、roadmap §2.1/§2.2/§5c/§7、`Team.java` Javadoc）已同步到 live baseline
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：Phase 3 代码变更（`Team.members` 类型收敛）编译通过 + focused 测试验证返回类型；Phase 1/2 为 docs-only 无空壳风险；无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（覆盖 Phase 3 代码变更 + 零回归）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（本计划无 deferred 项；三项 carry-over 均在本计划内收口。）

## Non-Blocking Follow-ups

- sandbox → shell-exec/code-exec 工具执行器接线（设计 `nop-ai-agent-security-and-permissions.md:612` 明确的独立 successor plan，跨 nop-ai-agent 契约层 + nop-ai-shell 工具层的大特性；本计划威胁模型注记已为该 successor 预留"键将变为 LLM 可控"的前瞻说明）。
- 05-2 plan-model xdef codegen 管线重设计（optimization candidate，no successor required，ill-defined/large）。
- 14-04 完整 executor 架构重设计（optimization candidate，no successor required；当前 dedicated-thread-pool 隔离满足 baseline）。
- roadmap §4 Layer 工作项清单的进一步细化（本计划只对齐 §2.1/§2.2/§7 到 §4，不重写 §4 本身）。

## Closure

Status Note: 三项 carry-over（env-source 威胁模型追溯、roadmap §2.1/§2.2/§7 一致性修正、Team.members ConcurrentMap 类型收敛）均已在本计划内落地并由独立 closure audit 复核通过。所有 Phase 的 Exit Criteria 与全部 Closure Gates 已勾选；`./mvnw test -pl nop-ai/nop-ai-agent -am` 通过（2759 tests, 0 failures）；无 in-scope live defect 被降级到 deferred/follow-up。
Completed: 2026-06-20

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，非执行阶段 session）
- Audit Session: opencode closure-audit session（2026-06-20）
- Evidence:
  - Phase 1 (env-source) Exit Criteria — 全 PASS：设计 `nop-ai-agent-security-and-permissions.md` §7.1 存在「威胁模型注记（plan 276 追溯收口）」段落（lines 614–619，覆盖 (a)–(d) 四点）；roadmap §5c 存在 `env-source` 行标 ✅ 已追溯（`nop-ai-agent-roadmap.md:360`）；plan 内 Trace Evidence 块固化了 grep 命令与结果（main src 0 生产构造点 + `execute` 0 调用）；源 plan 274 末尾含 `## Follow-up handled by 276-*.md`（`274-*.md:174`）。
  - Phase 2 (roadmap) Exit Criteria — 全 PASS：§2.1 M4b 状态 ✅ 已完成（`roadmap.md:29`，引用 `agent.register-model.xml` 已存在）；§2.2 无"所有运行时代码均为零实现"表述（grep 0 命中）；§2.2 表逐行与 §4 一致（`roadmap.md:41–56` 全 ✅ 并引用对应 Layer/plan）；§7 审计检查清单（`roadmap.md:418–431`）无与 §4 矛盾的过时标记；`> Updated:` 行（`:4`）记录本次修正并引用本 plan。
  - Phase 3 (15-1) Exit Criteria — 全 PASS：`Team.java` 字段（`:54`）/构造参数（`:70`）/getter（`:99`）静态类型均为 `ConcurrentMap<String, TeamMember>`（grep `Map<String, TeamMember>` 于 `Team.java` 0 命中）；测试构造点 `TestTeamExecuteFlowExecutor.java:506` 已从 `Collections.emptyMap()` 迁移为 `new ConcurrentHashMap<>()`（+import `:44`）；focused 测试 `TestTeam.java`（`getMembersReturnsConcurrentMapType` 断言 `ConcurrentMap<String,TeamMember> members = team.getMembers()` + `assertInstanceOf(ConcurrentMap.class, ...)`，`getMembersIsLiveMapOwnedByManager`，null 拒绝）；源 plan 272（`:158`）/275（`:150`）末尾含 `## Follow-up handled by 276-*.md`。
  - Closure Gates — 全 PASS（10/10 已勾选，详见上文）。
  - 构建验证：`./mvnw test -pl nop-ai/nop-ai-agent -am -T 1C` → BUILD SUCCESS，`Tests run: 2759, Failures: 0, Errors: 0, Skipped: 0`（含 test 编译，证明 Phase 3 类型收敛生产+测试构造点均兼容）；Maven 构建含 checkstyle 通过。
  - Anti-Hollow 检查：Phase 3 为类型 narrowing（`ConcurrentMap` 是 `Map` 子接口），编译通过即证明调用链连通；Phase 1/2 为 docs-only 无新增代码分支/空方法体；无 `continue` 跳过、无吞异常、无 placeholder 返回值。
  - Deferred 项分类检查：`## Deferred But Adjudicated` 为空（"本计划无 deferred 项"）；`## Non-Blocking Follow-ups` 仅含 sandbox→工具执行器接线（设计文档明确的独立 successor）、05-2、14-04、roadmap §4 细化——均为 watch-only/optimization candidate，无 in-scope live defect 被降级。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码：见本次 re-run 结果（修复后 0 未勾选项 + Reviewer/Agent 字段已写入）。
  - `ai-dev/logs/2026/06-20.md` 收口条目已存在并记录三项 phase 落地、契约变化、doc-sync。

Follow-up:

- sandbox → 工具执行器接线（独立 successor，设计文档已记录）。
- 05-2 / 14-04（optimization candidate，no successor required）。
