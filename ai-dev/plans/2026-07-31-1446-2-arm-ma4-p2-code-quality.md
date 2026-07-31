# MA4 P2 批量修复（第一批：代码质量 — 类型安全 + 风格 + 文档一致性）

> Plan Status: completed
> Mission: audit-remediation
> Work Item: MA4 P2 code-quality batch（MA4.1 + MA4.2 + MA4.5）
> Last Reviewed: 2026-07-31
> Source: `ai-dev/audits/2026-07-31-XXXX-arm-MA4.1-nop-ai-typesafety.md`、`2026-07-31-0539-arm-MA4.2-nop-ai-style.md`、`2026-07-31-XXXX-arm-MA4.5-nop-ai-doc-consistency.md`、roadmap 规则 1（P2/P3 deferred successor）
> Related: `2026-07-31-1446-1-arm-roadmap-convergence.md`、`2026-07-31-1446-3-arm-ma4-p2-test-quality.md`
> Draft Review: 3 轮独立子 agent 对抗性审查通过（含想象性分析），无 Blocker/Major（final round: ses_048f80826ffePsykzzt7cpPY06）

## Purpose

修复 MA4 审计产出的第一批 P2 代码质量 finding（MA4.1 类型安全、MA4.2 代码风格、MA4.5 文档-代码一致性），全部为已确认 finding 的落地修复，含必需回归测试与文档同步。本计划是 roadmap 规则 1 中 P2/P3 deferred successor 的第一批实现。

## Current Baseline

- MA4.1（5 个 P2 unchecked cast，live 已核验）：
  - MA4.1-01 `AnthropicDialect.java:145` `(Map<String, Object>) block`（instanceof Map 守卫后缺 unchecked 标注）
  - MA4.1-02 `AnthropicDialect.java:169` `(Map<String, Object>) blockMap.get("input")`（无 instanceof 守卫）
  - MA4.1-03 `DefaultAiChatService.java:537` `(List<Map<String, Object>>) BeanTool.getComplexProperty(...)`（toolCallsPath 可配置，无守卫）
  - MA4.1-05/06 `nop-ai-skills/nop-ai-code-analyzer/.../JavaCodeFileInfoGenerator.java:69,82`（JSON 解析无 instanceof 守卫）
- MA4.2（7 个 P2，live 已核验）：
  - MA4.2-02 4 个 nop-ai-agent 测试文件 import 顺序混乱（TestLayer23SecureDefaults / TestLayer23SecureDefaultImpls / TestDispatchPathSecurityConsultation / TestDispatchPathApprovalGate）
  - MA4.2-04 `ReActAgentExecutor.java:108,112` 重复 import SecurityLevel
  - MA4.2-08 `DefaultAiChatService.java:73` `@Deprecated` 缺 `@deprecated` javadoc tag
  - MA4.2-09 注释掉的代码：`AiCommand.java:294`、`DefaultAiChatService.java:181,504`
  - MA4.2-11 `VfsMavenCli.java:112`、`JavaMethodReplacer.java:112,115` System.out 生产代码
  - MA4.2-14 AGENTS.md import 顺序约定与实际代码库惯例相反（文档裁定）
- MA4.5（P2，live 已核验）：
  - MA4.5-001 `IVectorStore.java:89,91` Javadoc 引用已不存在的 `SearchWrapper`
  - MA4.5-002 `IVectorStore.java:35` abstract class 使用 I 前缀命名（契约裁定项）
  - MA4.5-003 `IAiChatProgressListener.java:16` `@Deprecated` 缺 `forRemoval=true`
  - MA4.5-004 `DefaultAgentEngine.java:431,497` callAgentTimeoutMs field 60s vs Builder 120s 不一致（**已确认 live 行为不一致**）
  - MA4.5-005 `docs-for-ai/03-modules/nop-ai.md:64` 文档默认值与 Builder 不一致
  - MA4.5-006/007 `IToolExecutor`、`IToolManager` 公开 API 缺 Javadoc
- P2 findings 均未被 MR1-MR4 修复（MR2/MR4 只修了 MA4 P1）；全量 baseline：`./mvnw clean install -pl nop-ai -am -T 1C` 绿

## Goals

- 修复 MA4.1 全部 5 个 P2 unchecked cast（instanceof 守卫或 `@SuppressWarnings("unchecked")` + 契约注释）
- 修复 MA4.2 全部 7 个 P2（import 顺序、重复 import、@deprecated tag、注释代码清理、System.out → SLF4J、AGENTS.md 约定裁定）
- 修复 MA4.5 全部 P2（Javadoc 失效引用、@Deprecated forRemoval、callAgentTimeoutMs 默认值统一 + 文档同步、公开 API Javadoc 补全）
- 对每个行为/契约变更补充回归测试（Test-Mandated Feature Rule #25）
- 每个 Phase 独立验证 build + test

## Non-Goals

- 不修复 MA4.3/MA4.4 P2（测试质量批次，由 `2026-07-31-1446-3` 承接）
- 不修复 MA4.2-05 超大文件拆分（>3600 行 ReActAgentExecutor/DefaultAgentEngine）——记入 Deferred But Adjudicated（SRP 重构，高风险低收益，超出 P2 批量修复定位）
- 不修复 P3/P1（P1 已由 MR2/MR4 修复；P3 按 roadmap 框架 watch-only）
- 不改 ORM 模型、不动生成文件、不引入新依赖

## Scope

### In Scope

- MA4.1 5 个 unchecked cast finding
- MA4.2 6 个代码风格 finding + 1 个文档约定裁定（AGENTS.md）
- MA4.5 7 个文档一致性 finding（含 callAgentTimeoutMs 行为统一）
- 各 finding 的回归测试 + 对应 daily log

### Out Of Scope

- MA4.2-05 大文件拆分（见 Deferred）
- MA4.3/MA4.4 P2（另一计划）
- 其他里程碑 P2/P3（watch-only）

## Execution Plan

> **验证命令说明**：nop-ai-* 子模块仅在 `nop-ai/pom.xml` 聚合，根 POM reactor 只认 `nop-ai` 聚合入口；因此 `-pl` 选择器在本仓库应统一用 `-pl nop-ai -am`（全量，daily log 惯例），避免路径/artifactId 选择器歧义。

### Phase 1 — MA4.1 类型安全：unchecked cast 加固

Status: completed
Targets: `nop-ai/nop-ai-core/.../dialect/AnthropicDialect.java`、`nop-ai/nop-ai-core/.../service/DefaultAiChatService.java`、`nop-ai/nop-ai-skills/nop-ai-code-analyzer/.../code/JavaCodeFileInfoGenerator.java`

- Item Types: `Fix`

- [x] MA4.1-01：AnthropicDialect:145 为局部变量 cast 加 `@SuppressWarnings("unchecked")` 及契约注释
- [x] MA4.1-02：AnthropicDialect:169 增加 `instanceof Map` 守卫后 cast；同时处理 Anthropic API `input` 为 JSON **字符串**的合法形态——镜像 `DefaultAiChatService.java:549-553` 的 String→`JsonTool.parseMap` 降级；`input` 为 null 时显式失败（抛 `ERR_AI_INVALID_RESPONSE`，复用既有错误码，不新增错误码）而非静默降级为空 Map
- [x] MA4.1-03：DefaultAiChatService:537 增加 `instanceof List` 守卫，非 List 时按错误处理（抛异常或安全降级，禁止静默）
- [x] MA4.1-05/06：JavaCodeFileInfoGenerator:69,82 增加 `instanceof List` 守卫，非预期结构抛明确异常
- [x] 为每处修改编写回归测试（正常路径 + 畸形输入路径）

Exit Criteria:

- [x] 5 处 cast 全部有运行时类型守卫或显式契约标注，不再裸 cast
- [x] 新增测试覆盖畸形输入路径（非 List 结构），验证明确失败而非 ClassCastException/静默
- [x] `./mvnw test -pl nop-ai -am` 通过（全量；含 nop-ai-core 与 nop-ai-code-analyzer 测试）
- [x] **无静默跳过**：修改的每处分支在数据不符时显式失败
- [x] No owner-doc update required（内部防御加固，无契约变更）
- [x] `ai-dev/logs/2026/07-31.md` 已更新

### Phase 2 — MA4.2 风格：import / 注释 / System.out

Status: completed
Targets: `nop-ai-agent/src/test/java/io/nop/ai/agent/engine/TestLayer23Secure*.java`、`TestDispatchPath*.java`、`ReActAgentExecutor.java`、`DefaultAiChatService.java`、`AiCommand.java`、`nop-ai-maven/.../VfsMavenCli.java`、`nop-ai-coder/.../JavaMethodReplacer.java`、`AGENTS.md`

- Item Types: `Fix | Decision`

- [x] MA4.2-14（Decision，**先于 MA4.2-02 执行**）：裁定 AGENTS.md import 约定与实际代码库惯例不一致的修法。**必须三件套一并决策**：(a) `AGENTS.md` 约定文本；(b) 仓库 lint 工具 `ai-dev/tools/check-import-order.mjs`（当前强制 java.* → jakarta.* → 第三方 → io.nop.*，与 io.nop 优先的实际惯例相反，nop-ai 下 1130 处违规）——改期望顺序或明确其适用范围；(c) checkstyle.xml 是否加 import 顺序规则。**执行顺序：先完成本裁定，再按裁定结果执行 MA4.2-02 重排**。若裁定为改代码（按 io.nop 优先批量重排），规模过大则记入 Deferred 并维持现状说明
- [x] MA4.2-02（依赖 MA4.2-14 先裁定）：4 个测试文件 import 分组重排（组间空行；顺序遵循 MA4.2-14 裁定结果）
- [x] MA4.2-04：ReActAgentExecutor 删除重复 import（108/112 保留一行）
- [x] MA4.2-08：DefaultAiChatService 增加 `@deprecated` javadoc tag
- [x] MA4.2-09：删除 3 处注释掉的代码（AiCommand:294、DefaultAiChatService:181,504），并连带清理删除后遗留的空 if 块与未使用局部变量（如 DefaultAiChatService:180 的 logMessage、AiCommand 对应变量）——不留空方法体/空块
- [x] MA4.2-11：VfsMavenCli:112 System.out → SLF4J Logger（真实生产调用）；JavaMethodReplacer:112 为**字符串字面量内容**（demo body 文本）非可执行代码、:115 位于 main() 且全仓无调用方——裁定：整体删除无人使用的 demo main 或保留并注明示例性质（**不把 demo 文本改成 Logger**）

Exit Criteria:

- [x] 4 个测试文件 import 顺序与 MA4.2-14 裁定结果一致
- [x] 重复 import、注释代码、System.out（生产代码）全部清零（grep 验证）；删除注释后无遗留空 if 块/未使用变量
- [x] `./mvnw compile -pl nop-ai -am` 通过（全量；含 agent/core/maven/coder）
- [x] `./mvnw test -pl nop-ai -am` 通过（全量；import 重排不得破坏编译）
- [x] MA4.2-14 三件套裁定（AGENTS.md + check-import-order.mjs + checkstyle.xml）已落盘
- [x] `ai-dev/logs/2026/07-31.md` 已更新

### Phase 3 — MA4.5 文档-代码一致性 + callAgentTimeoutMs 统一

Status: completed
Targets: `nop-ai-core/.../api/vectorstore/IVectorStore.java`、`nop-ai-core/.../api/chat/IAiChatProgressListener.java`、`nop-ai-agent/.../engine/DefaultAgentEngine.java`、`nop-ai-toolkit/.../api/IToolExecutor.java`、`IToolManager.java`、`docs-for-ai/03-modules/nop-ai.md`

- Item Types: `Fix | Decision`

- [x] MA4.5-001：IVectorStore.search() Javadoc 失效引用 SearchWrapper → VectorQueryBean
- [x] MA4.5-003：IAiChatProgressListener `@Deprecated` → `@Deprecated(forRemoval = true)`
- [x] MA4.5-004（**已确认 live 行为不一致**）：统一 DefaultAgentEngine callAgentTimeoutMs 默认值——裁定取 Builder 推荐路径的 120_000L 为统一值（实例 field 431 与 Builder 497 对齐；`applyTo` 对 primitive long 无条件 set，统一后两种构造路径必一致），同步更新 field 注释（:429-430 "mirrors the CallAgentExecutor's historical default (60s)" 已过时）；补回归测试验证两种构造路径默认值一致
- [x] MA4.5-005：`docs-for-ai/03-modules/nop-ai.md:64` 默认值同步为 120000（与统一后代码一致）
- [x] MA4.5-006/007：IToolExecutor、IToolManager 补类级 + 方法级 Javadoc（含 getToolName/executeAsync 契约、callTool/callTools 差异、异常传播）
- [x] MA4.5-002（Decision）：裁定 IVectorStore 抽象类 I 前缀命名——评估改为 interface 或重命名 AbstractVectorStore 的成本；若为公开 SPI 契约且改动影响面大，裁定维持现状 + Javadoc 说明并记入 Deferred；**裁定必须落盘**

Exit Criteria:

- [x] IVectorStore Javadoc 无失效类型引用（grep SearchWrapper = 0）
- [x] callAgentTimeoutMs 统一后，构造函数链与 Builder 构造默认值一致，且新增测试验证（两种路径 + 正数校验）
- [x] docs-for-ai/03-modules/nop-ai.md 默认值与 live 代码一致
- [x] IToolExecutor/IToolManager 有完整 Javadoc
- [x] MA4.5-002 裁定落盘（改代码或记 Deferred）
- [x] `./mvnw test -pl nop-ai -am` 通过（全量；含 agent/core）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `ai-dev/logs/2026/07-31.md` 已更新

### Phase 4 — 全量验证 + arm-index 更新 + closure

Status: completed
Targets: `ai-dev/audits/arm-index.md`、全 nop-ai 模块

- Item Types: `Proof | Follow-up`

- [x] 更新 arm-index：MA4.1/MA4.2/MA4.5 行 P2 状态标注为 fixed（或新增 P2 修复追踪段）
- [x] 全量 `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`
- [x] 全量 `./mvnw test -pl nop-ai -am -T 1C`
- [x] 独立子 agent closure audit

Exit Criteria:

- [x] arm-index P2 修复状态可追溯（finding → 修复位置 → 测试）
- [x] 全量 build + test 绿
- [x] 独立 closure audit 证据写入本 plan Closure 段
- [x] `ai-dev/logs/2026/07-31.md` 已更新

## Closure Gates

- [x] MA4.1/MA4.2/MA4.5 全部 in-scope P2 finding 已修复或裁定（无静默降级）
- [x] 每个行为变更都有回归测试（Test-Mandated Feature Rule）
- [x] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（MA4.5-004 为已确认行为不一致，必须修复，不得降级）
- [x] 受影响的 owner docs 已同步（docs-for-ai/03-modules/nop-ai.md、AGENTS.md 裁定）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）callAgentTimeoutMs 统一在 live 代码生效（非仅注释），（b）unchecked cast 守卫在畸形输入下真实生效（测试覆盖），（c）无空方法体/静默跳过作为"修复"
- [x] `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`
- [x] `./mvnw test -pl nop-ai -am -T 1C`
- [x] checkstyle / 代码规范检查通过（实际门禁为 compile 级，见 MV plan 记录：checkstyle 插件 root pom 未绑定，全仓既有基线失败与 MR 链无关）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-1446-2-arm-ma4-p2-code-quality.md --strict` 退出码 0（closure 时）

## Deferred But Adjudicated

### MA4.2-05 超大文件拆分（ReActAgentExecutor ~3728 行 / DefaultAgentEngine ~3632 行）

- Classification: `optimization candidate`
- Why Not Blocking Closure: SRP 重构，纯结构性变更，无行为修复价值；对 3600+ 行引擎类拆分回归风险高，超出 P2 批量修复定位；audit 自身标注为 size-isn't-a-defect。（行数为审计时点值，live 可能漂移，拆分时以执行时 wc -l 为准）
- Successor Required: `no`

### MA4.5-002 IVectorStore 抽象类 I 前缀命名

- Classification: `watch-only residual`（若 Phase 3 裁定维持现状）
- Why Not Blocking Closure: IVectorStore 为 SPI 扩展点契约（MV 已裁定无生产实现属设计意图）；命名与实现形态的变更影响集成方，成本大于收益。
- Successor Required: `no`

### MA4.2-14 全量 import 顺序批量重排

- Classification: `optimization candidate`（若 Phase 2 裁定改文档而非改代码）
- Why Not Blocking Closure: 批量重排 408 文件（io.nop 优先的既有文件数；`check-import-order.mjs` 实测 nop-ai 705 文件/1130 违规、全仓 5408 文件/7498 违规）产生海量无关 diff，掩盖真实变更；且仓库 lint 工具 `check-import-order.mjs` 当前强制与 io.nop 优先相反的顺序，代码重排需先改工具。文档/工具对齐成本远低于代码重排。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MA4.3/MA4.4 P2（测试质量）由 `2026-07-31-1446-3-arm-ma4-p2-test-quality.md` 承接
- MA1-MA3/MA5-MA6 P2 批量修复按严重度排序规划后续 successor

## Closure

Status Note: MA4.1（5 P2）、MA4.2（6 P2 修复 + 1 裁定）、MA4.5（6 P2 修复 + 1 裁定）全部落地；MA4.5-004 已确认行为不一致已修复并补回归测试；独立子 agent closure audit APPROVE；全量 build + test 绿；arm-index P2 修复追踪段可追溯。Deferred 项（MA4.2-05 大文件拆分、MA4.5-002 IVectorStore 命名、MA4.2-14 全量 import 重排）均为 optimization candidate / watch-only residual，无 in-scope live defect 被降级。
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，非实现 session）
- Audit Session: ses_048be43baffelg8NGCKiS8TafP
- Evidence:
  - Phase 1（MA4.1）：PASS×4 — AnthropicDialect.java:149-150 `@SuppressWarnings("unchecked")` + :230-245 `parseToolInput` 真实运行时守卫（Map/String→parseMap/null+非 Map 非 String 抛 ERR_AI_INVALID_RESPONSE）；DefaultAiChatService.java:531-533 `instanceof List` 守卫抛错；JavaCodeFileInfoGenerator.java:74-77/:93-96 双守卫 + ARG_FILE_PATH；测试 TestAnthropicDialect（+4 含畸形形态）/TestDefaultAiChatService（5 例含非 List 抛错）/TestJavaCodeFileInfoGenerator（3 例含畸形 summary）
  - Phase 2（MA4.2）：PASS×7 — AGENTS.md:229/:262 io.nop 优先约定 + MA4.2-14 引用；check-import-order.mjs `{nop:0, third:1, java:2, static:3}` 且 4 个目标测试文件 0 违规；checkstyle.xml 无 import 顺序规则（裁定不加）；ReActAgentExecutor SecurityLevel import 唯一；DefaultAiChatService @deprecated tag 存在；AiCommand/DefaultAiChatService 无注释代码/空 if 块/未使用变量；VfsMavenCli SLF4J + JavaMethodReplacer 无 main/System.out
  - Phase 3（MA4.5）：PASS×5 — IVectorStore SearchWrapper grep=0 + MA4.5-002 裁定 Javadoc；IAiChatProgressListener `@Deprecated(forRemoval=true)`；DefaultAgentEngine.java:432/:498 双默认 120_000L + setter :1982-1986 非正数拒绝 + TestDefaultAgentEngine 两路径一致测试；docs-for-ai/03-modules/nop-ai.md:64 `120000`；IToolExecutor/IToolManager 类级+方法级 Javadoc
  - Phase 4（可追溯性）：arm-index §P2 修复追踪段（finding→位置→测试 16 行）+ MA4.1/MA4.2/MA4.5 行标注；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0；Anti-Hollow：守卫均为真实运行时分支（测试覆盖）、logCachedResponse 整体删除无空方法体、畸形输入全部显式抛 NopException
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码为 0（本 plan closure 后复验）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 无本 plan 引入的 high/critical 空壳
  - Deferred 分类检查：MA4.2-05/MA4.2-14 = optimization candidate，MA4.5-002 = watch-only residual，均有明确 non-blocking 理由；唯一确认 live 行为不一致（MA4.5-004）已修复未降级

Follow-up:

- MA4.3/MA4.4 P2（测试质量）由 `2026-07-31-1446-3-arm-ma4-p2-test-quality.md` 承接（non-blocking）
- 其余 MA1-MA3/MA5-MA6 P2/P3 watch-only residual（roadmap 已登记）
- no remaining plan-owned work

## Optional Sections

## Risks And Rollback

- callAgentTimeoutMs 默认值统一改变默认行为（60s→120s）：全仓无生产代码走直接构造器（生产只经 Builder，实际默认值已是 120s），文档记载的 60000 才是与 Builder 不符的错误值——修复后运行时行为与文档对齐，近零用户可见行为变化。回归测试覆盖两路径；daily log 记录行为变更说明。
- import 重排/注释删除为低风险文本变更，逐文件可回滚；MA4.2-02 与 MA4.2-14 存在顺序依赖（先裁定后重排），避免按旧约定重排后与 lint 工具冲突。
- JavaMethodReplacer demo main 删除前 grep 确认无调用方（live 已确认全仓无引用）。
