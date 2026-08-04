# MA4 工程质量审计（全模块）

> Plan Status: completed
> Last Reviewed: 2026-08-04
> Draft Review: 3 轮独立子 agent 对抗性审查通过（第 1 轮 2 Major + 6 Minor 全部修复；第 2 轮 2 新 Major A/B + 7 Minor C-I 全部修复；第 3 轮验证 9/9 PASS，3 处 Scope 层残留已修正，裁定可执行）。Session: ses_0356f5793ffed6Kln0cnyQMioD / ses_0356778cfffejvKmJcQAeu7jqc / ses_0355af5b7ffeAs78zjLrcGqghq。
> Mission: nop-metadata-audit-remediation
> Work Item: MA4（4.1 错误处理 / 4.2 类型安全 / 4.3+4.4 测试覆盖 / 4.5 代码风格 / 4.6+4.7 测试有效性）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MA4 里程碑）、`ai-dev/skills/deep-audit-prompts.md`（维度 09/15/16/17/21）、`ai-dev/skills/unit-test-antipatterns.md`
> Related: 执行顺序 `{2}` of 3 — 硬前置：M0 done + MA3（`{1}`）completed；以 M0.3 未闭包清单为历史发现对照源；产物（P0/P1 发现）是 MR2 批量修复的输入（含 P1 `2026-07-19-1118#维度20-01` 机械修复确认、P2 静默吞异常与 AutoTest 缺口的复核）。

## Purpose

对 nop-metadata 全模块执行工程质量审计（roadmap MA4 的 7 个工作项：错误处理、类型安全、测试覆盖（核心/其余两片）、代码风格、测试有效性（核心/其余两片）），产出审计报告并更新 arm-index，为 MR2 批量修复提供输入。

## Current Baseline

经 2026-08-04 live repo 核对（引用均与 roadmap 一致，已二次确认文件与计数）：

- roadmap MA4 七行（4.1-4.7）状态为 `todo`；M0/MA1/MA2 已 completed
- 对照源：`ai-dev/audits/arm-unclosed-findings-nop-metadata.md`（M0.3 清单，轮次限定 ID 为权威）+ `arm-index-nop-metadata.md`
- 错误码面（live）：service 下 **11 个 `*Errors.java`**（NopMetadataErrors / AggregationErrors / ModuleErrors / SqlErrors / QualityErrors / MiscErrors / ReconErrors / JoinErrors / DataSourceErrors / FieldErrors / LineageErrors，11 个互不相同的文件）；`nop.err.metadata.*` 前缀集中化已由历史修复完成（08-04 日志）；`2026-07-23-0714#维度09-07` hyphen 分隔符为 watch-only 有意裁定（NopMetadataErrors.java:22），MA4.1 复核
- 静默吞异常（live 复核 2026-08-04，**清单以 live 为准，计划不预设计数**）：M0.3 登记 `2026-07-23-0714#维度09-02/09-03/09-06` 家族经 live 核查——**候选清单（行号需执行时核对）**：MetaQualityRuleExecutor:599,606（实际 catch 行 600/607）与 :714（`NumberFormatException → return null` 无日志，第 3 处静默）、NopMetaTagLabelBizModel.getWfNameFromMeta（catch→return null）；MetaTableProfiler:485 为 `catch (SQLException ignore) { LOG.trace }`（半静默，有 trace 日志）；MetaDataSourceConnectionProcessor 实际 catch 在 **314**（isInternalHost 内 NumberFormatException ignored）；CheckpointActionDispatcher ~325（实际 325）；**`AggregationContext.safeProductName` 不存在**（真实 `safeProductName` 在 AggregationHelper.java:447（有 LOG.error）、NopMetaQualityRuleBizModel.java:400（有 LOG.warn）、NopMetaProfilingRuleBizModel.java:197（有 LOG.warn）——均有日志，非静默吞异常）；07-23 09-02/09-03 原为 bare NopException finding（非吞异常），M0.3 并入吞异常家族系归并口径——MA4.1 复核时逐条以 live 为准判定，不得照抄清单计数
- **错误码前缀迁移状态（live）**：`nop.err.metadata.*` 集中化已由历史修复完成（08-04 日志）；NopMetadataException 4 构造器（String/ErrorCode 等）已存在（07-23 09-01 已修复）
- P1 `2026-07-19-1118#维度20-01`：`System.currentTimeMillis()` DDD-006 违规残余 2 处（`nop-metadata-dao/.../OrmModelImporter.java:58,68`，live 实测存在），open，**归 MR2 机械修复（随 MA4.2/4.5 审计后确认）**
- `@SuppressWarnings("unchecked")`（live 2026-08-04）：98 个文件含 `@SuppressWarnings`、`"unchecked"` 出现 157 处（07-19 记录"50+"已过时）；`2026-07-19-1118#维度15-03` 裁定"边界保留（≤25 目标外剩余）"——MA4.2 复核维持或更新，**基线以 live 计数为准**
- 测试面（live 2026-08-04）：service test 目录 94 文件（其中 Test 前缀 88，另 6 个为 BiSemanticTestHelper/LineageTestBase/Mock×3/NopMetadataHelperTest 非 Test 前缀）、全模块 97（+web 2 +codegen 1）、`@NopTest` 全模块 50（service 49 + web 1）、AutoTest `_cases/` 5 个类（`TestAutoNopMetaDataSourceCrud` 等）、测试基线 813/0（M0.4）
- 待 MA4 复核的 P2 登记：`2026-07-23-0714#维度16-01` AutoTest 覆盖偏低 5/97（MA4.3 复核 + MR2 增量）、`维度16-03` 重复 CRUD 测试（MA4.6/4.7）、`维度16-04`（judgeByRuleId/activateContract/deprecateContract/retireContract 4 方法无测试）——**live 与 MA2.3 结论：activateContract/deprecateContract/retireContract 已被 plan-2026-07-20-2000-2（contract 审批流改造）移除（MA2.3 已记 07-008 fixed），judgeByRuleId 已有测试（TestNopMetaQualityRuleBizModel:295-300）→ 预期复核结论为 16-04 已闭包**（存在性核对归 Phase 4（其余域，M0.3 的 MA4.6 归属按域规则 override）、测试质量复核归 Phase 7）、`维度16-09` Thread.sleep(1100ms)——**live 实测 service 测试零 Thread.sleep（TestNopMetaTableBizModel:205 注释明确不依赖 sleep），预期复核结果为"已修复/已不存在"**（复核归 Phase 7）、`维度16-07` data-auth 测试只验 XML 结构（MA6.6）
- P1 watch-only 复核：`2026-07-21-2039#维度16-01`（19/40 BizModel 零测试，剩余 14 个 CRUD-only）→ **MA4.4/4.7 复核**
- `2026-07-19-1118#维度02-01` 残余：`*Service` 命名违规 2 处（NopMetaSearchService / QualityAlertWorkflowService，live 实测存在）→ MA4.5 复核后归 MR2 命名批量修复
- **@BizModel 计数口径（live，MA2.3 登记）**：grep 命中 41 处 / 实际 40（第 41 处为 MetaQualityCheckpointScheduler 的 javadoc 假阳性）；含测试 42——MA4.3 报告记录该口径
- MA2 已登记口径：@BizModel service main 41、含测试 42（MA4.3 测试覆盖审计以该口径为准）
- 测试基线：813 tests / 0 failures（M0.4，2026-08-04 实测；范围 `-pl nop-metadata -am -T 1C`）

## Goals

- 产出 MA4 审计报告（7 份：4.1 错误处理 / 4.2 类型安全 / 4.3 测试覆盖-核心 / 4.4 测试覆盖-其余 / 4.5 代码风格 / 4.6 测试有效性-核心 / 4.7 测试有效性-其余），发现一律使用轮次限定 ID 或 `P<级别>-<里程碑>-<序号>`
- 每个发现标注 P 级 + 修复归属（MR2/即时通道/非阻塞）
- 更新 arm-index-nop-metadata.md 报告清单与 P0/P1 追踪
- 对 P1 维度20-01（currentTimeMillis 2 处）给出复核确认并维持 MR2 归属；对 3 项 watch-only/待复核 P2（09-07 hyphen / 16-01 AutoTest / 16-01 zero-test P1）给出复核结论
- 无 P0 时保持绿色基线；发现 P0 走即时通道

## Non-Goals

- 不修复审计发现（修复归 MR2 批量修复，P0 例外走即时通道）
- 不审计运行时/安全（MA3 承接）、文档一致性（MA5 承接）、域特有风险（MA7 承接）
- 不改任何 `src/` 代码或测试代码（纯审计计划）

## Scope

### In Scope

- 4.1 错误处理与错误码审计（维度 09）：11 个 `*Errors.java` 两档策略遵循、String/ErrorCode 构造器、ARG_* 常量、`nop.err.metadata.*` 命名、.param() 上下文、异常链保留、吞异常清单复核（09-02/03/06 家族）、日志规范（SLF4J vs System.out）、中文消息
- 4.2 类型安全与泛型审计（维度 15）：raw type、@SuppressWarnings("unchecked") 50+ 处复核（15-03）、I*Biz<T> 泛型精度、instanceof+cast、Object 收窄、DTO 类型定义（`2026-07-23-0714#维度07-004` 动态行 Map 复核，MR2 归属确认）
- 4.3 测试覆盖与质量审计-核心执行域（维度 16）：**query/aggregation/lineage/sqlview 域**（域划分以 service 测试包结构为准：`io.nop.metadata.service.query/`、`aggregation/`、`lineage/`、`sqlview/` 或含 query/aggregation/lineage/sqlview 语义的测试类，判定规则 = 测试类名或包路径含上述域关键词；边界模糊的测试类记录归属决策不重复计）测试的边界条件与错误路径覆盖、AutoTest 快照缺口复核（16-01）、测试计数口径（88/94/97、@BizModel 40 real/42 含测试、@NopTest 50）记录
- 4.4 测试覆盖与质量审计-其余域（维度 16）：import/datasource/quality/reconciliation/semantic/search/contract/event 域（判定规则同上，域关键词 = import|datasource|quality|reconciliation|semantic|search|contract|event，其余一律归此片）测试覆盖、19/40 BizModel 零测试 watch-only P1 复核
- 4.5 代码风格与规范审计（维度 17）：命名规范（PascalCase/camelCase/UPPER_SNAKE_CASE、`*Service` 命名违规 2 处复核）、import 分组（io.nop.* → jakarta.* → third-party → java.*）、行宽缩进、过度注释、System.out/System.err、未使用 import
- 4.6 单元测试有效性审计-核心执行域（维度 21 + unit-test-antipatterns P-1..P-8）：query/aggregation/lineage/sqlview 域（域划分判定规则同 4.3）测试反模式扫描（16-03 重复 CRUD 核心域部分）
- 4.7 单元测试有效性审计-其余域（维度 21 + unit-test-antipatterns）：其余域（判定规则同 4.4）测试反模式扫描（16-03 家族、零测试 BizModel 复核）
- 审计报告（`ai-dev/audits/YYYY-MM-DD-HHmm-arm-MA4.<n>-nop-metadata-<dimension>.md`）+ arm-index 更新

### Out Of Scope

- MA3/MA5-MA7 审计（后续计划）
- 任何修复（MR1/MR2/MR3 承接）
- `docs-for-ai/` 文档修改（MA5 覆盖；审计发现记录为 finding 即可）
- MA6.5 测试隔离性审计（roadmap 归属 MA6，不在本计划复核 16-05）

## Execution Plan

### Phase 1 - MA4.1 错误处理与错误码审计

Status: completed
Targets: `nop-metadata/*/src/main/java/**/*Errors.java` + 全模块 throw/catch 语句

- Item Types: `Proof`

- [x] **启动门禁核查**：确认 MA3（`{1}`）已 completed（roadmap MA3 四行 done）；未满足则不启动并上报
- [x] **前置阅读**：完整阅读 `deep-audit-prompts.md` 维度 09 正文 + `docs-for-ai/02-core-guides/error-handling.md`（两档策略）
- [x] 执行维度 09 审计（错误处理与错误码）：11 个 `*Errors.java` 两档策略遵循、String/ErrorCode 构造器、ARG_* 参数常量、`nop.err.metadata.*` 命名（含 hyphen 分隔符 09-07 复核）、.param() 上下文、异常链保留、**吞异常候选清单逐条以 live 复核为准**（MetaQualityRuleExecutor:599,606 实际 catch 行 600/607 + :714 NumberFormatException→return null、NopMetaTagLabelBizModel.getWfNameFromMeta 为静默；MetaTableProfiler:485 有 LOG.trace 半静默；MetaDataSourceConnectionProcessor:314 行号核对；`AggregationContext.safeProductName` 不存在——safeProductName 在 AggregationHelper:447/NopMetaQualityRuleBizModel:400/NopMetaProfilingRuleBizModel:197 均有日志；07-23 09-02/03 bare-NopException 归并口径单独点名核对）、日志规范（SLF4J/System.out）、中文消息
- [x] 历史对照：以 M0.3 未闭包清单中维度 09 相关条目逐一核对（07-23 维度 09 全部（09-01 已修复 / 09-02/03/04 bare-NopException / 09-06 / 09-07）+ 07-21 维度 09 家族 + 07-19 维度 09 条目）；**每条以 live 现状为准判定终态，不得照抄清单计数**
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1234-arm-MA4.1-nop-metadata-error-handling.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 报告包含：11 个 Errors 类两档策略核对表、**吞异常复核结论（以 live 逐条判定为准，不预设计数；维持 open 的归 MR2）**、09-07 hyphen 复核结论、ARG_*/param() 使用结论
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`（审计报告为证据层）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MA4.2 类型安全与泛型审计

Status: completed
Targets: 全模块 main Java（`nop-metadata/*/src/main/java/`）

- Item Types: `Proof`

- [x] **前置阅读**：完整阅读 `deep-audit-prompts.md` 维度 15 正文 + `docs-for-ai/02-core-guides/code-style.md`
- [x] 执行维度 15 审计（类型安全与泛型）：raw type 扫描、@SuppressWarnings("unchecked") 计数复核（**基线为 live 计数 157 处 / 98 文件**，07-19 记录"50+"已过时；15-03 ≤25 目标外剩余裁定维持或更新）、I*Biz<T> 泛型精度、instanceof+cast、Object 参数/返回值收窄、DTO 类型定义（07-004 动态行 Map 复核，MR2 归属确认）、**泛型擦除是否导致运行时问题（维度 15 步骤 7）**
- [x] 运行 `rg -l "@SuppressWarnings" nop-metadata -g "*.java" -g "!**/target/**" | wc -l` 与 `rg -o "@SuppressWarnings\(\"unchecked\"\)" nop-metadata -g "*.java" -g "!**/target/**" | wc -l` 获取文件数/出现次数双口径基线（**用 rg（ripgrep）避免 macOS BSD grep 不支持 `--include` 的问题；口径：文件数 vs 出现次数分别记录，报告注明**）
- [x] 历史对照：以 M0.3 未闭包清单中维度 15 相关条目逐一核对（15-03 及 07-23 维度 15 条目）
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1347-arm-MA4.2-nop-metadata-typesafety.md`

Exit Criteria:

- [x] 报告包含：raw type/unchecked 计数基线、15-03 复核结论、07-004 复核结论（维持 MR2 归属）、泛型精度结论
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - MA4.3 测试覆盖与质量审计-核心执行域

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/test/`（query/aggregation/lineage/sqlview 域）

- Item Types: `Proof`

- [x] **前置阅读**：完整阅读 `deep-audit-prompts.md` 维度 16 正文 + `docs-for-ai/02-core-guides/testing.md`
- [x] 执行维度 16 审计（测试覆盖与质量）：核心执行域（query/aggregation/lineage/sqlview，域划分判定规则见 Scope）测试文件逐份评估（边界条件、错误路径、AutoTest 快照一致性、保护力弱的位置）
- [x] 复核 `2026-07-23-0714#维度16-01`（AutoTest 覆盖偏低 5/97）：live 核对 `_cases/` 5 个 AutoTest 类与 94 测试文件口径，给出复核结论（维持 open 则归 MR2 增量）
- [x] 记录测试计数口径（88 Test 前缀 / 94 service test / 97 全模块三口径、@BizModel 40 real（grep 命中 41 含 1 javadoc 假阳性）/42 含测试、@NopTest 50）
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1355-arm-MA4.3-nop-metadata-test-coverage-core.md`

Exit Criteria:

- [x] 报告包含：核心执行域测试覆盖评估表、16-01 AutoTest 复核结论、计数口径记录（88/94/97、40/41/42、50）
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - MA4.4 测试覆盖与质量审计-其余域

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/test/`（import/datasource/quality/reconciliation/semantic/search/contract/event 域）

- Item Types: `Proof`

- [x] **前置阅读**：完整阅读 `deep-audit-prompts.md` 维度 16 正文 + `docs-for-ai/02-core-guides/testing.md`
- [x] 执行维度 16 审计（测试覆盖与质量）：其余域（import/datasource/quality/reconciliation/semantic/search/contract/event，域划分判定规则见 Scope）测试文件逐份评估（边界条件、错误路径、AutoTest 快照、保护力弱的位置）
- [x] 复核 `2026-07-21-2039#维度16-01`（19/40 BizModel 零测试，剩余 14 个 CRUD-only）：live 核对零测试 BizModel 清单，给出复核结论（维持 watch-only 或升级）
- [x] 复核 `2026-07-23-0714#维度16-04`（judgeByRuleId/activateContract/deprecateContract/retireContract 方法无测试）：**预期结论 = 已闭包**——judgeByRuleId 已有测试（TestNopMetaQualityRuleBizModel:295-300）、activateContract/deprecateContract/retireContract 已被 plan-2026-07-20-2000-2 移除（MA2.3 已记 07-008 fixed，live 全仓无这三方法）；live 核对确认后记 closed（M0.3 原属 MA4.6 按域规则 override 归本 Phase 存在性核对，测试质量复核归 Phase 7）
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1355-arm-MA4.4-nop-metadata-test-coverage-rest.md`

Exit Criteria:

- [x] 报告包含：其余域测试覆盖评估表、零测试 BizModel watch-only P1 复核结论、16-04 已闭包核对结论
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - MA4.5 代码风格与规范审计

Status: completed
Targets: 全模块 main + test Java

- Item Types: `Proof`

- [x] **前置阅读**：完整阅读 `deep-audit-prompts.md` 维度 17 正文 + `docs-for-ai/02-core-guides/code-style.md`
- [x] 执行维度 17 审计（代码风格与规范）：命名规范（类/方法/常量/接口 I 前缀/包名）、import 分组（io.nop.* → jakarta.* → third-party → java.*）、行宽缩进（4 空格 80-120）、过度注释（AI 风格模板化 javadoc）、System.out/System.err、未使用 import、**版权头残余（roadmap 4.5 明确项）**
- [x] 复核 `2026-07-19-1118#维度02-01` 残余（`*Service` 命名违规 2 处：NopMetaSearchService / QualityAlertWorkflowService）：live 核对，确认 MR2 命名批量修复归属
- [x] 运行 `rg -n "System\.(out|err)" nop-metadata -g "*.java" -g "!**/target/**" | wc -l` 获取输出语句基线（**用 rg（ripgrep）避免 macOS BSD grep 不支持 `--include` 的问题**）
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1405-arm-MA4.5-nop-metadata-style.md`

Exit Criteria:

- [x] 报告包含：命名/import/行宽/注释/输出语句扫描结果（附命令基线）、02-01 复核结论
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - MA4.6 单元测试有效性审计-核心执行域

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/test/`（query/aggregation/lineage/sqlview 域）+ `ai-dev/skills/unit-test-antipatterns.md`

- Item Types: `Proof`

- [x] **前置阅读**：完整阅读 `ai-dev/skills/unit-test-antipatterns.md`（P-1..P-8 反模式清单 + 优先级排序为审计基线）
- [x] 执行维度 21 审计（单元测试有效性）：核心执行域测试逐方法按 P-1..P-8 扫描；对命中反模式用"核心逻辑改成错误实现后测试是否仍通过"验证保护力
- [x] 复核 `2026-07-23-0714#维度16-03`（重复 CRUD 反模式，核心域部分）：live 核对
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1415-arm-MA4.6-nop-metadata-test-effectiveness-core.md`

Exit Criteria:

- [x] 报告包含：核心域反模式命中清单（引用 P-N 编号 + 证据）、16-03（核心域部分）复核结论、有效测试与低价值测试比例
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - MA4.7 单元测试有效性审计-其余域

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/test/`（其余域）+ `ai-dev/skills/unit-test-antipatterns.md`

- Item Types: `Proof`

- [x] **前置阅读**：完整阅读 `ai-dev/skills/unit-test-antipatterns.md`（P-1..P-8 反模式清单 + 优先级排序为审计基线）
- [x] 执行维度 21 审计（单元测试有效性）：其余域（域划分判定规则见 Scope）测试逐方法按 P-1..P-8 扫描（含 16-03 家族其余部分、零测试 BizModel 有效性复核衔接）
- [x] 复核 `2026-07-23-0714#维度16-09`（TestNopMetaQualityRuleBizModel Thread.sleep(1100ms)）：live 核对——**预期复核结果为"已修复/已不存在"（live 实测 service 测试零 Thread.sleep，TestNopMetaTableBizModel:205 注释明确"不依赖 sleep"），记录为修复到位性确认而非新发现**
- [x] 复核 `2026-07-23-0714#维度16-04` 测试质量部分（judgeByRuleId 测试的捕获能力按 P-1..P-8 评估；存在性结论见 Phase 4）
- [x] 产出审计报告 `ai-dev/audits/2026-08-04-1415-arm-MA4.7-nop-metadata-test-effectiveness-rest.md`

Exit Criteria:

- [x] 报告包含：其余域反模式命中清单（引用 P-N 编号 + 证据）、16-09 修复到位性确认、16-04 测试质量评估、有效测试与低价值测试比例
- [x] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [x] 文档变化：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 纯审计计划（不改代码），构建验证以绿色基线保持为准。

- [x] 7 份 MA4 审计报告全部产出且含 P 级标注 + 修复归属
- [x] 以 M0.3 未闭包清单为对照源，维度 09/15/16/17/21 相关条目逐一核对（全轮次），无遗漏；P1 维度20-01 与 3 项 watch-only/待复核 P2 均有复核结论
- [x] arm-index-nop-metadata.md 报告清单 + P0/P1 追踪已更新
- [x] P0 发现已走即时通道（若存在）；P1 发现已归入 MR2 修复清单
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确写明 `No owner-doc update required`
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证报告非空壳（有实际发现清单与可追溯引用，非模板占位）
- [x] `./mvnw compile -pl nop-metadata -am`（绿色基线保持验证）
- [x] `./mvnw test -pl nop-metadata -am`（绿色基线保持验证）
- [x] checkstyle / 代码规范检查通过（无代码变更，以 mvn 默认检查为准）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（修改 `ai-dev/` 下文件后执行）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-metadata-audit-remediation/2026-08-04-1004-2-ma4-engineering-quality-audit.md --strict` 退出码 0（closure 时，Minimum Rule #26）

## Deferred But Adjudicated

### MA4 P2/P3 finding 的修复

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 规则 1 明示本 roadmap 只处理 P0/P1；P2/P3 记录为 deferred successor，由后续批次另行规划（同 MA1/MA2 裁定）。
- Successor Required: `no`（后续批次另行规划，非本批 scope）

## Non-Blocking Follow-ups

- watch-only 项复核维持原裁定者，登记结论即可（不产生修复债务）
- 测试域发现的系统性缺口（如 AutoTest 增量目标）作为 MR2 修复清单输入

## Closure

Status Note: 7 份 MA4 审计报告全部产出（MA4.1 错误处理 / MA4.2 类型安全 / MA4.3+4.4 测试覆盖 / MA4.5 代码风格 / MA4.6+4.7 测试有效性），发现 0 P0 / 2 P1 新增（judgeByRuleId 空洞测试、18 个 processor 空壳测试，归 MR2）+ 9 P2 + 27 P3；M0.3 清单维度 09/15/16/17/21 相关条目 15 项全部有 live 复核结论（含维度20-01 维持 open + MR2、3 项 watch-only 维持、16-04 存在性闭包、16-09 已修复）；arm-index 与 roadmap Items 4.1-4.7 已同步；独立 closure audit 通过；绿色基线保持（812 tests / 0 failures）。纯审计计划零代码变更，无 P0 走即时通道，P1 全部归 MR2 修复清单。
Completed: 2026-08-04

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，任务 id ses_0349c400dffeNUKUeVN0HFvSAZ）
- Audit Session: ses_0349c400dffeNUKUeVN0HFvSAZ
- Evidence:
  - 每条 Exit Criterion 的验证结果：Phase 1-7 全部 `Status: completed` 且 Exit Criteria 全部 `[x]`，PASS（closure audit 逐条核对）
  - 每条 Closure Gate 的验证结果：13/13 已勾选；closure audit 逐条 PASS（报告齐全非空壳 / M0.3 对照（修复后）/ arm-index 更新 / P1 归 MR2 / 无静默降级 / owner docs 裁定 / 独立审计 / Anti-Hollow / compile+test 绿 / checkstyle N/A / check-doc-links exit 0 / check-plan-checklist exit 0）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 退出码为 0（确认无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：closure audit 抽查 7 份报告 14 个 finding 的文件+行号+证据全部与 live repo 吻合（0 处不符）；纯审计计划无新增组件调用链，scan-hollow-implementations 不适用（无代码变更）；报告非模板占位
  - Deferred 项分类检查：MA4 P2/P3 修复入 `Deferred But Adjudicated`（Classification: out-of-scope improvement，Why Not Blocking = roadmap 规则 1 只处理 P0/P1，同 MA1/MA2 裁定）；无 in-scope live defect 被降级
- 收口修复（closure audit 提出后执行）：(1) 维度20-01 currentTimeMillis 复核结论补入 MA4.2 报告（OrmModelImporter.java:58,68 live 验证维持 MR2）；(2) MA4.1 补 `文档变化: No owner-doc update required` 段；(3) MA4.4 TestCoreMetricsUsage 行注明 dao 模块扫描盲区

Follow-up:

- 测试域系统性缺口（AutoTest 增量目标、processor 空壳改造）作为 MR2 修复清单输入（见 arm-index §P1/P2 与 MA4.3/4.4/4.6/4.7 报告）
- 8 个零测试纯 CRUD BizModel 维持 watch-only，新增自定义逻辑时强制补测（准入规则，见 MA4.4）
- 版权头剥离（154 文件）等整域风格裁定归 MR2/决策层（见 MA4.5）
- no remaining plan-owned work
