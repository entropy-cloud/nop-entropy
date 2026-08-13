# 1 AI Invariant Loop I0 — 不变式盘点与基线

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 1 / I0. 不变式盘点与基线
> Last Reviewed: 2026-08-12
> Source: `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` §I0；`ai-dev/skills/invariant-loop-audit-prompt.md` 步骤 2 强制纪律（基线核对 live）
> Related: `ai-dev/plans/2026-08-12-1120-2-ai-invariant-i1-gate-codification.md`（I1 消费本 plan 的目标集表与目录）

## Purpose

把 nop-ai 历史审计-修复闭环（6 deep + ARM MA1-MA7 + MR1-MR4/MV，50/50 关闭）中识别的五个失败族沉淀为不变式目录 `ai-dev/audits/nop-ai-invariants/invariant-catalog.md`，live 枚举四个审计目标集（Default* 类 / 编排入口 / ToolExecutor 实现 / 资源 entry-point 方法）作为 I1 门禁的表完备性数据源，并确认「当前零可执行不变式门禁」基线成立。

## Current Baseline

（以下事实均于 2026-08-12 live 核实）

- nop-ai 审计-修复 mission 已关闭：`ai-dev/audits/arm-index.md`、`ai-dev/audits/nop-ai-agent-audit-tracker.md` 记录 6 轮 deep audit + ARM MA1-MA7 + MR1-MR4/MV 的发现与修复状态（AUDIT-13-01/02/04、L23-SDI、AUDIT-14-01~06、AUDIT-09-01、MA6.2 四 P1 等均已 ✅）。
- 五个失败族及其历史证据（roadmap §目的）：
  1. **Secure-by-default 缺失族**：AUDIT-13-01/02/04（plan 193/194/199）+ L23-SDI（plan 200，4 个 Default*）+ AUDIT-14-01（runningExecutions putIfAbsent）+ AUDIT-09-01（NopAiAgentException 基类型）。
  2. **异步编排缺 timeout 族**：AUDIT-14-01（callAgent child timeout）/ -02（DBMessageService at-least-once）/ -03（LLM/tool orTimeout）/ -04（cached 守护线程池）/ -06（DbSessionTakeoverLock heartbeat），plan 271/273。
  3. **资源清理不对称族**：AR-02（entry-point try/cleanup 对称性）、AR-09（IAgentEngine AutoCloseable）、AR-10（ICheckpointManager.remove default）。
  4. **ToolExecutor 安全边界族**：Lesson 08 整篇；MA6.2 四 P1（HttpRequestExecutor SSRF / GraphqlQueryExecutor SSRF / LocalToolFileSystem 路径逃逸未接线 / BashExecutor 命令注入）。
  5. **虚假关闭族**：Lesson 05 整篇 3 例（MR2 overclaim / MR1 codegen 产物 / MR3 AES 未提交）。
- **零可执行不变式门禁**（live 核实）：
  - 全仓 `pom.xml` 无 ArchUnit（`grep -rl archunit --include=pom.xml` 为空）。
  - `ai-dev/tools/` 下无针对上述失败族的 mjs 静态检查（`check-ai-dict-consistency`/`check-import-order` 等与五族无关；`scan-hollow-implementations.mjs` 是通用空壳扫描）。
  - nop-ai 测试中仅 1 处 `@ParameterizedTest`（`TestSsrfAddressGuard`，SSRF 守卫实例级测试，非方法表/类表穷举门禁）。
  - 既有测试（`TestSecureByDefault`、`TestLayer23SecureDefaults`、`TestPlan271AsyncTimeoutReliability`、`TestEngineEntryCleanupSymmetry`）均为已修复实例的行为验证，非「目标集 × 不变式」穷举契约——新类/新方法不会自动进入这些测试的检查范围。
- 审计目标集规模（live 枚举，精确清单由本 plan Phase 2 固化）：
  - **Default* 类**（非测试）：按 `find nop-ai -name "Default*.java" -not -path "*/target/*" -not -path "*/_gen/*"` + 排除路径含 `/test/` 段 → 33 个，分布在 nop-ai-agent（22，engine/security/runtime/team/fencing/reliability/quota/hook）、nop-ai-core（8，service/persist/prompt/api.tool）、nop-ai-shell（2）、nop-ai-toolkit（1）。**边缘类**：`nop-ai-agent/.../guardrail/test/DefaultGuardrailGrader.java` 位于 src/main 但路径含 `/test/` 段——包含/排除须在 Phase 2 显式裁定并记录理由（否则 I1 门禁①会因表内/表外不一致误报）。
  - **编排入口**：`IAgentEngine`（`nop-ai-agent/.../engine/IAgentEngine.java`，execute/sendMessage/forkSession/cancelSession/resumeSession，extends AutoCloseable）、`ReActAgentExecutor.execute`（:339）、`AgentToolDispatcher.executeAllowedCalls`（:165）、`CallAgentExecutor.executeAsync`（:144）等；Phase 2 用机械判定标准枚举（见该 phase，如「engine/executor 类上返回 `CompletionStage`/`CompletableFuture` 的公共方法」），标准原文记入表头。
  - **ToolExecutor 实现**：接口 `nop-ai-toolkit/.../api/IToolExecutor.java`（`getToolName` + `executeAsync`）；枚举规则 = 直接 `implements IToolExecutor` 的具体类（grep 须用 `implements\s+IToolExecutor\b` 避免误匹配 `IToolExecutorProvider`，如 `DefaultToolExecutorProvider`），抽象基类（`AbstractMemoryToolExecutor`）与其间接子类（ReadMemory/WriteMemory/SearchMemoryExecutor）按表头规则显式归类；非测试实现约 27 个，分布在 nop-ai-toolkit tools/（文件/网络/命令类）与 nop-ai-agent tool/（团队/内存类）。
  - **资源 entry-point 方法**：IAgentEngine（AutoCloseable）、ICheckpointManager.remove、会话接管锁 heartbeat 等；以既有 TestEngineEntryCleanupSymmetry / plan 271 的修复面为起点。
- 目标交付物 `ai-dev/audits/nop-ai-invariants/` 目录当前不存在（`ls ai-dev/audits/` 无 nop-ai-invariants）。

## Goals

- `ai-dev/audits/nop-ai-invariants/invariant-catalog.md` 落地，含 **5 条不变式**（五失败族各一），每条含四要素：陈述 / 覆盖失败族 / 历史 audit-finding-ID + Lesson 证据 / 检测方法。
- **4 个审计目标集表**（Default* 类 / 编排入口 / ToolExecutor / 资源 entry-point），每条目可追溯到 live 代码位置（`文件:行`），并提供机械复现命令（grep/反射/脚本），保证表完备性——新增成员不入表可被 I1 门禁发现。
- 「零可执行不变式门禁」基线结论写入目录，附 live 证据记录（pom 无 ArchUnit、无族系 mjs 检查、无参数化穷举测试）。
- 目录经独立子 agent 审查至共识（跨引用双向核对：finding-ID → 修复 plan → live 代码）。

## Non-Goals

- **不实现任何门禁**（属 I1，`2026-08-12-1120-2`）。
- **不运行门禁、不做对抗探查**（属 I2，`2026-08-12-1120-3`）。
- **不裁决、不修复任何实例**（属 I3/I4）。
- 不改写历史审计文档（arm-index / lesson / tracker 只作为证据源引用，不重写）。
- 不改产品代码、不改 CI 配置。

## Scope

### In Scope

- 不变式目录的编写与共识审查。
- 四个审计目标集表的 live 枚举与复现机制定义。
- 基线「零可执行门禁」的 live 证据收集与记录。

### Out Of Scope

- 门禁实现、CI 接线（I1）。
- 门禁运行、red list（I2）。
- 缺陷裁决表（I3）、修复（I4）、全量验证（I5）、收口（I6）。

## Execution Plan

### Phase 1 - 基线核实与证据收集

Status: completed
Targets: `nop-ai/**/pom.xml`、`ai-dev/tools/`、`nop-ai/*/src/test/`、`ai-dev/audits/arm-index.md`、`ai-dev/audits/nop-ai-agent-audit-tracker.md`、`ai-dev/archived/`、`ai-dev/lessons/05*`、`ai-dev/lessons/08*`

- Item Types: `Proof | Decision`

- [x] `Proof` 核实「零可执行不变式门禁」：pom 全仓无 ArchUnit；ai-dev/tools 无五族 mjs 检查；nop-ai 测试无目标集 × 不变式穷举门禁（现有 `@ParameterizedTest` 仅 TestSsrfAddressGuard 1 处）——每项结论记录核实命令与结果。
- [x] `Proof` 回溯五失败族的 finding-ID → 修复 plan 编号 → live 代码位置的完整证据链（用 `nop-ai-agent-audit-tracker.md` 与 `arm-index.md`，抽查至少 3 条到 live 代码）。**注意**：tracker 中部分 plan 编号（如 193/194/196/197/199）指向 `ai-dev/archived/2026-06/` 下的已归档 plan——不在 `ai-dev/plans/` 时须到 `ai-dev/archived/` 查找，不得误报「悬空引用」。
- [x] `Decision` 定稿不变式目录的文件结构与条目模板（含「检测方法」字段的取值枚举，与 roadmap §I1 的五种落地形式对齐）。

Exit Criteria:

- [x] 「零可执行门禁」三项核实结论各带可复现命令记录于目录基线节
- [x] 证据链抽查 ≥3 条通过（tracker/plan 中声明的文件在 live repo 存在且内容吻合）；3 条为抽查下限，Closure Gates 的「无悬空引用」约束全部证据链
- [x] **接线验证**（如适用）：证据链抽查确认修复产物确实存在于 live 代码（防 Lesson 05 类 overclaim 基线漂移）
- [x] 目录文件结构模板确定（本 phase 产出，无独立文件）
- [x] 本 phase 改变 `ai-dev/audits/` 布局（新建 `nop-ai-invariants/`）：无 owner-doc update required（audit 产物目录，不入 docs-for-ai）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 审计目标集枚举

Status: completed
Targets: `nop-ai/*/src/main/**`（agent/core/toolkit/shell）

- Item Types: `Proof`

- [x] `Proof` 枚举 Default* 类表：live `find` 全量，逐类记录 `包路径/文件`，标注是否 IoC 注入候选（有无 `beans.xml` 注册 / `@Inject` 使用方），并记录复现命令（`find nop-ai -name "Default*.java" -not -path "*/target/*" -not -path "*/test/*"`）。
- [x] `Proof` 枚举编排入口表：按表头判定标准（机械派生：engine/executor 类上返回 `CompletionStage`/`CompletableFuture` 的公共方法 + IAgentEngine 全量公共入口）枚举 IAgentEngine 族 + ReActAgentExecutor.execute + AgentToolDispatcher.executeAllowedCalls + CallAgentExecutor.executeAsync 等，每条目记录 `文件:行` 与复现命令；判定标准原文记录在表头，不依赖执行者主观判断。
- [x] `Proof` 枚举 ToolExecutor 实现表：从 `IToolExecutor` 实现反查（grep `implements\s+IToolExecutor\b`，排除误匹配 `IToolExecutorProvider`；抽象基类与间接子类按表头规则归类，非测试约 27 个），标注安全敏感面（网络/文件/命令/内存），记录复现命令。
- [x] `Proof` 枚举资源 entry-point 方法表：以既有修复面（IAgentEngine AutoCloseable、ICheckpointManager.remove、session takeover lock）为起点，枚举 acquire/start 与 release/stop 对称面。
- [x] `Proof` 每张表验证「机械复现」：执行记录的复现命令，结果与表内容一致（或记录差异原因）；Default* 表的 `DefaultGuardrailGrader` 边缘类（src/main 中路径含 `/test/` 段）显式裁定包含/排除并记录理由。

Exit Criteria:

- [x] 四张目标集表全部落地于 invariant-catalog.md（或同目录 companion 文件），每条目含 `文件:行` 引用
- [x] 每张表头含判定标准 + 复现命令，且复现命令 dry-run 与表一致
- [x] **表完备性**：表中条目数与 live 枚举数一致（命令输出 == 表行数，差异零或显式记录）
- [x] 无 owner-doc update required（目标集枚举是审计基础数据，不入 docs-for-ai；目录内不写实现方案）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 不变式目录编写与共识审查

Status: completed
Targets: `ai-dev/audits/nop-ai-invariants/invariant-catalog.md`

- Item Types: `Fix | Proof`

- [x] `Fix` 编写 5 条不变式条目（陈述 / 覆盖失败族 / 历史证据 ID+Lesson / 检测方法），检测方法与 roadmap §I1 五种落地形式一一对应。
- [x] `Proof` 交叉核对：每条不变式的证据（finding-ID/Lesson）与 Phase 1 证据链、Phase 2 目标集表双向一致；「检测方法」列引用 I1 计划（`2026-08-12-1120-2`）中的门禁面。
- [x] `Fix` 按独立子 agent 审查意见修订目录，直至共识（零 Blocker）。

Exit Criteria:

- [x] invariant-catalog.md 存在，含 ≥5 条不变式，每条四要素齐全
- [x] 每条不变式可沿证据链追溯到 live 代码或已关闭 plan（无悬空引用）
- [x] 检测方法列与 I1 计划的五族门禁一一对应（每族至少一种落地形式）
- [x] 独立子 agent 审查意见与修订记录写入目录尾部或对应 daily log（共识达成）
- [x] 无 owner-doc update required（目录为 ai-dev 审计产物；若审查发现 docs-for-ai 与实际行为不符，按 AGENTS.md 在 I1-I6 处理，不在本 plan 扩 scope）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 5 条不变式 + 4 张目标集表全部落地且可追溯 live 代码
- [x] 「零可执行门禁」基线结论有 live 证据（非凭记忆）
- [x] 无悬空引用：finding-ID ↔ plan ↔ live 代码双向核对通过
- [x] 独立子 agent 审查（零 Blocker）证据已记录
- [x] 不存在被静默降级的 in-scope 项（I0 交付物已全部收口）
- [x] 受影响的 owner docs 无更新需要（`No owner-doc update required`，目录为 ai-dev 审计产物）
- [x] 独立子 agent 完成 closure-audit 并记录证据
- [x] **Anti-Hollow Check**：目录中每条不变式都能映射到 I1 计划中将被落地的门禁（非仅文档表述）；目标集表可由命令复现（非手写幻觉清单）
- [x] 构建验证：纯文档/审计产物计划（仅改 `ai-dev/audits/`），`./mvnw compile` / `./mvnw test` / checkstyle 按 guide「纯文档计划」条款从 Closure Gates 删除；执行中若意外改动任何代码则恢复这些条目
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 时执行）

## Deferred But Adjudicated

None（I0 为纯盘点交付，无 in-scope 剩余项；I1/I2/I3 为 successor plans 明确接管后续步骤）

## Non-Blocking Follow-ups

- 目标集表的条目若在 I0-I6 期间因产品演进变化（新增/重命名 Default* 类等），由 I2/I5 按 Loop Rule 复触发处理——I0 不承担动态跟踪。

## Closure

Status Note: I0 纯盘点交付物全部收口——不变式目录（5 条不变式 + 4 张目标集表 + 零门禁基线）落地于 `ai-dev/audits/nop-ai-invariants/invariant-catalog.md`，经独立子 agent 两轮审查至共识（verdict approved），并经独立 closure audit（10/11 条 PASS）确认。纯文档计划（仅改 `ai-dev/audits/` + logs + backlog），构建验证按 guide「纯文档计划」条款删除。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session `ses_00be2889bffezOdJq7syA6JtGe`）
- Audit Session: `ses_00be2889bffezOdJq7syA6JtGe`；目录内容共识审查另有 2 轮（`ses_00bea761effewNaaMGkb6wDbYc` 第 1 轮 4 Blocker→修订；`ses_00be5c9aaffeDvbKc6kFixmG0n` 第 2 轮 zero Blocker verdict approved）
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS：「零可执行门禁」三项结论各带可复现命令（archunit grep exit 1 / ai-dev/tools 无五族 mjs / ParameterizedTest 仅 TestSsrfAddressGuard）记录于 catalog §1；证据链抽查 5 族 ≥4 条/族全部通过（含归档 plan 193/194/196/197/199 于 `ai-dev/archived/2026-06/`、活跃 plan 200/271/273/278、MR3 `2026-07-31-1300-5`）；接线验证确认修复产物 live 在位（`DefaultAgentEngine.java:786` putIfAbsent、`IAgentEngine.java:8` AutoCloseable、`LocalToolFileSystem.java:53-54` resolveFile→isPathAllowed、`BashExecutor.java:103` validateCommand、`CallAgentExecutor.java:459/:473` orTimeout+cancelSession）
  - Phase 2 Exit Criteria 全部 PASS：四张目标集表落地（Default* 33 行 / 编排入口 13 / ToolExecutor 31 行 / 资源 entry-point 8 条），每条目含 `文件:行`；表头含判定标准 + 复现命令且 dry-run 一致（find 输出 33 行 == 表行数；`implements\s+IToolExecutor\b` 28 行 + indirect 3 行）；DefaultGuardrailGrader 边缘类排除裁定已记录
  - Phase 3 Exit Criteria 全部 PASS：≥5 条不变式四要素齐全，检测方法与 I1 plan 五门禁一一对应；独立审查 2 轮意见与修订记录于 catalog §5 + daily log `2026/08-12.md`
  - Closure Gates 全部 PASS：无悬空引用（closure audit 抽查全部通过）；独立审查证据已记录；无静默降级（Deferred=None，Non-Blocking 仅动态跟踪 follow-up）；Anti-Hollow（表命令全可复现、门禁映射具体）；doc-link checker 28 处 broken link 全为 pre-existing（其他 roadmap/INDEX/I1/I2/credential 文件），`invariant-catalog.md` 零错误引入
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-12-1120-1-ai-invariant-i0-inventory-baseline.md --strict` 退出码为 0
  - 纯文档计划：`./mvnw compile` / `./mvnw test` / checkstyle / scan-hollow 按 guide「纯文档计划」条款从 Closure Gates 删除（执行中零代码改动）

Follow-up:

- 无 remaining plan-owned work；目标集表动态跟踪（新增/重命名 Default* 类等）由 I2/I5 按 Loop Rule 处理（Non-Blocking Follow-ups 已记录）
