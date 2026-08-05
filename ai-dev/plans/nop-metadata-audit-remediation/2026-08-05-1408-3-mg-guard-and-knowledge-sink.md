# MG Guard 与知识沉淀（G.1 失败模式 → lessons / G.2 重复审计维度 → skills / G.3 docs-for-ai 与 design 文档收口）

> Plan Status: completed
> Last Reviewed: 2026-08-05
> Draft Review: 2 轮独立子 agent 对抗性审查通过（第 1 轮 0 Blocker + 0 Major + 4 Minor 全部修复；第 2 轮复审 4 项修复全部 PASS，0 Blocker / 0 Major 残留，裁定可执行）。Session: ses_02f71cb81ffeIKd5aMFU0BCS6v / ses_02f6b5cd1ffe0vPaw7gLFVV20v。
> Mission: nop-metadata-audit-remediation
> Work Item: MG（G.1 新失败模式提升为 lessons；G.2 重复审计维度提升为 skills 新提示；G.3 更新 docs-for-ai/ 和 design 文档）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MG 里程碑）、`ai-dev/lessons/README.md`（编号规则 + 索引）、`ai-dev/skills/`（已有提示清单）、`ai-dev/design/nop-metadata/`（17+ 篇设计文档）、MV 验证产出（plan-2026-08-05-1408-2 输入清单）
> Related: 执行顺序 `{3}` of 3 — 启动门禁：MV done（roadmap V.1/V.2/V.3 → done，plan-2026-08-05-1408-2 completed）；本 plan 为**知识沉淀计划**（产出 = lessons/skills/docs 更新，无产品行为代码变更），是 nop-metadata-audit-remediation 路线图的收尾里程碑。

## Purpose

执行 roadmap MG 里程碑三项收口工作：G.1 把本路线图（M0.4 → MA1-MA7 → MR1-MR4 → MV）中新暴露的失败模式沉淀为 `ai-dev/lessons/` 新条目；G.2 把跨多轮审计反复使用的维度/流程提升为 `ai-dev/skills/` 新提示（或更新既有提示）；G.3 在全部修复落地后对 `docs-for-ai/` 与 `ai-dev/design/nop-metadata/` 做最终一致性收口（含 MR3 修复后 design 文档中可能出现的描述陈旧）。本 plan 使路线图产生的知识可复用，防止同类缺陷在后续模块审计中复发。

## Current Baseline

经 2026-08-05 live repo 核对：

- roadmap MG 行（G.1/G.2/G.3）状态全部 `todo`；**Deps：G.1-G.3 依赖 MV done（本 plan 启动时核查 roadmap V.1/V.2/V.3 → done + plan-2026-08-05-1408-2 completed，不满足则不启动并上报）**；MR1-MR4 全部 completed（启动时核查，draft 期为预期态）
- **`ai-dev/lessons/` 现状**：8 条教训（01-batch-memory-accumulation … 08-tool-executor-security-boundary），编号 `NN-简短标识.md`，索引表维护于 README.md；下一编号 = 09
- **`ai-dev/skills/` 现状**：24 项（deep-audit-prompts.md / orm-model-audit-prompt.md / cross-module-dependency-audit-prompt.md / design-doc-audit-prompt.md / open-ended-adversarial-review-prompt.md / unit-test-antipatterns.md / closure-audit-prompt.md / audit-remediation-roadmap-authoring-prompt.md / audit-remediation-verification-prompt.md / plan-reviewer-prompt.md / plan-closure-audit-prompt.md 等）——G.2 以**更新既有提示为主**（deep-audit-prompts.md 新增维度行、orm-model-audit-prompt.md 补 UK 发射核查），新增独立提示文件仅当维度确无归属
- **`ai-dev/design/nop-metadata/` 现状**：17+ 篇（00-vision / 01-architecture-baseline / 02-dto-module-restructure-decision / 02-gap-analysis / 03-version-management / 04-data-governance / 05-metadata-import / 06-data-quality-extended / 07-ai-integration / 08-reconciliation / 09-gap-analysis-extended / 10-event-model / 11-enterprise-semantic-layer / 12-data-contract-and-governance-workflow / aggctx-and-bizmodel-split / aggregation-processor-split / api-dto-spec / nop-metadata-roadmap / README）；MR3 R3.7 已批量修复 P2-MA5-101..184 文档 drift（25 项），MA5.1 报告 P3 级陈旧（记号/计数/行号级）未处理（roadmap 规则 1 维持 deferred）——G.3 的 design 收口以**修复落地后的新 drift** 与 P3 级陈旧中影响理解的部分为对象
- **`docs-for-ai/` 现状**：`03-modules/nop-metadata.md` + `01-repo-map/module-groups.md` + `04-reference/source-anchors.md` 已在 MR3 R3.7 同步（P2-MA5-201..205 修复）；G.3 复核 MR4 裁决是否引入新文档需求（预期：无——MR4 为裁决文档，未改变契约，除非提升项修复落地）
- **本路线图可沉淀的失败模式候选（G.1 输入，MV 后按实际验证补充）**：MA4.5 版权头 154 文件批量剥离；P2-MA5-401 getProp 恒 null 致"补日志≠修根因"（R2.11 只补 LOG.warn 未修根因，R3.6 才根因修复）；P2-MA6.6-001 DDL 零 UK 发射（`uniqueKey.constraint` 属性门——静态 DDL 供给部署失去全部 UK 保护且构建不报错）；MA7.3-01 计数勘误（grep 口径 69 vs 实际 36）；xwf listener 驳回即通过/发起人自批回归（R2.1 引入、R3.4 修复）；queryAggregation 11 参签名契约测试钉死 vs @RequestBean 规范张力；data-auth 双开关默认 false 下的旁路面归因（MA3.3 复核实证）；overclaimed-closure 已有教训 05 覆盖（不重复）
- **验证基线注意**：本 plan 为纯文档/知识沉淀计划，无代码变更；docs-for-ai/ 修改后必须跑 `node ai-dev/tools/check-doc-links.mjs --strict`（AGENTS.md 硬性要求）

## Goals

- G.1：新增 lessons 条目（编号 09+，按 README 规则 + 索引表追加），覆盖本路线图新暴露且未被既有 8 条覆盖的失败模式（候选：DDL 零 UK 发射静默缺失 / 补日志≠修根因 / 审计 grep 口径勘误 / xwf listener 驳回语义回归 等，以 MV 后实际结论为准），每条含触发场景 + 判定标准 + 预防措施
- G.2：更新 `ai-dev/skills/`（deep-audit-prompts.md 新增维度行 / orm-model-audit-prompt.md 补 UK 发射与 model-first 复核 / 或新增独立提示），覆盖本路线图反复使用且可复用的审计维度（DDL 生成产物审计、model-first 落地核对、xwf 流程语义审计、审计计数口径），复用面在 2+ 轮审计以上
- G.3：`docs-for-ai/` 与 `ai-dev/design/nop-metadata/` 与全部修复落地后的 live baseline 一致（复核 MR4 裁决影响 + MR3 修复后新 drift + 影响理解的 P3 级陈旧）；`check-doc-links.mjs --strict` exit 0
- roadmap G.1/G.2/G.3 → done，路线图收尾

## Non-Goals

- 不进行任何代码变更（本计划为知识沉淀；发现 live defect 时记录上报，不在此修复）
- 不重做 MR3 R3.7 已修复的文档 drift（25 项 P2-MA5-101..184 + 5 项 P2-MA5-201..205）
- 不新增无复用价值的提示文件（G.2 优先更新既有提示；新增仅当维度确无归属）
- 不回写已 completed 历史计划（Minimum Rule #20）
- 不处理 P3 级全量文档陈旧（只处理影响理解的项，其余维持 deferred 并记录）

## Scope

### In Scope

- G.1：lessons 新条目（编号 09+，按 MV 输入清单逐项核对既有教训覆盖后新增）+ README 索引更新
- G.2：skills 更新（deep-audit-prompts.md / orm-model-audit-prompt.md 等既有提示补充本路线图验证有效的维度；必要时新增独立提示）
- G.3：docs-for-ai/（03-modules/nop-metadata.md + module-groups.md + source-anchors.md，若 MR4 裁决有契约影响则同步）+ ai-dev/design/nop-metadata/（修复落地后的新 drift + 影响理解的 P3 级陈旧）+ check-doc-links exit 0
- roadmap G.1/G.2/G.3 → done + daily log 收口

### Out Of Scope

- 产品行为代码变更、新审计、P3 全量处理、历史计划回写

## Execution Plan

### Workstream 1 - G.1 新失败模式提升为 lessons

Status: completed
Targets: `ai-dev/lessons/`（新增 `09-*.md` 起条目）+ `ai-dev/lessons/README.md`（索引）

- Item Types: `Decision | Follow-up`

- [x] 逐项核对 MV 输入清单中的失败模式候选与既有 8 条 lessons 的覆盖关系（如 overclaimed-closure 已由 05 覆盖 → 不重复；补日志≠修根因与 05 相关但焦点不同 → 评估独立条目）——覆盖矩阵记录：**覆盖矩阵（候选 × 既有 8 条）**——① DDL 零 UK 发射（P2-MA6.6-001/MA7.3-01）：无既有覆盖 → **新增 09**；② 补日志≠修根因（P2-MA5-401，R2.11 补 LOG.warn → R3.6 根因）：与 05（claim 无 live 证据）焦点不同（症状修复 vs 根因修复）→ **新增 10**；③ 审计计数口径勘误（MA7.3-01 69 vs 36 容器标签 vs 元素；MV surefire 文件名数字污染；跨 MR 描述口径漂移）：无既有覆盖 → **新增 11**；④ xwf listener 结束判定缺失（P1-MA7.6-01 驳回即通过）：无既有覆盖 → **新增 12**；⑤ 空洞断言测试（P1-MA4-401）：与 07（零测试模块）不同（测试存在但零保护力 vs 无测试）→ **新增 13**；⑥ 条件激活旁路面（P2-MA3-02/P2-MA7.2-02 data-auth 双开关默认 false）：无既有覆盖 → **新增 14**；⑦ MA4.5 版权头批量剥离（机械操作，非失败模式）→ 不新增；⑧ 11 参签名契约测试张力（R2.9/MR4 裁决先例）→ 非失败模式，归 G.2 裁决通道维度；⑨ overclaimed-closure（05 已覆盖）→ 不重复；⑩ MV 空洞断言复发（与 ⑤ 合并为 13）；⑪ 测试文件名数字污染（与 ③ 合并为 11）
- [x] 为确认未被覆盖的失败模式新增 lessons 条目（编号 09+，格式**参照既有条目（如 05-overclaimed-closure-fix-status-drift.md）的多节结构：触发场景 + 根因 + 正确做法 + 判定规则**，含本路线图修复实例作证据，不设死字数约束）——条目清单记录：`09-ddl-unique-key-silent-absence.md`（36 个 unique-key 零 constraint → 三方言 DDL 零 UK 发射，R3.19 修复 + DdlSqlCreator 断言测试）/ `10-log-is-not-a-fix.md`（R2.11 补 LOG.warn vs R3.6 prop_get 根因修复）/ `11-audit-count-calibration.md`（MA7.3-01 69 vs 36 + MV surefire I18n 文件名污染）/ `12-xwf-listener-end-reason.md`（R2.1 引入驳回即通过 → R3.4 appState 守卫）/ `13-hollow-assertion-test.md`（judgeByRuleId 非存在 ruleId + assertNotNull-only → R2.3 行为断言）/ `14-conditional-activation-bypass.md`（data-auth 双开关默认 false 三方实证裁决，R2.14/MR4）；全部带 finding ID / plan / 报告 live 证据引用
- [x] `ai-dev/lessons/README.md` 索引表追加新条目行——执行结果记录：README 索引追加 09-14 六行（标题 + 日期 2026-08-05）

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [x] 新条目均基于本路线图实际发生的失败模式（有 live 证据引用：finding ID / plan / 报告），无泛泛而谈——6 条全部含 finding ID（P2-MA6.6-001 / P2-MA5-401 / MA7.3-01 / P1-MA7.6-01 / P1-MA4-401 / P2-MA3-02）+ 修复 plan（R2.x/R3.x）+ live 代码位置
- [x] 无重复条目（既有 8 条覆盖的失败模式未重复新增；覆盖矩阵可追溯）——覆盖矩阵记录于 WS1 执行项 1；05（overclaimed）/07（零测试）明确评估后不重复
- [x] README 索引与新增文件一致——09-14 六行与 6 个文件一一对应
- [x] `No new test required`: 纯知识沉淀（无代码变更）
- [x] 文档变化：`ai-dev/lessons/` 更新即本工作项本身（docs-for-ai 不变，`No owner-doc update required`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 2 - G.2 重复审计维度提升为 skills 新提示

Status: completed
Targets: `ai-dev/skills/`（deep-audit-prompts.md / orm-model-audit-prompt.md 等既有提示 + 必要的新增提示文件）

- Item Types: `Decision | Follow-up`

- [x] 盘点本路线图（MA1-MA7 + MR1-MR4 + MV）中反复使用且可复用的审计维度/流程：DDL 生成产物审计（UK 发射核查、deploy/sql 三方言 DDL 断言）、model-first 落地核对（_gen/ 与源模型一致性、禁止手编生成产物）、xwf 流程语义审计（listener 结束判定、approve/reject 单一事实源）、审计计数口径（grep 含容器标签 vs 元素计数）、裁决通道记录（MA2.1/MR2/MR3 裁决先例防默认修 P2）——维度清单记录：**维度清单（8 项，含 MV 候选）**——① DDL 生成产物审计（MA7.3-01/MA6.6 + R3.19，复用面 2+ 轮）；② model-first 落地核对（MA2.2 + R3.19 + 教训 05/06，多轮）；③ xwf 流程语义审计（MA7.6 + R2.1/R2.2 + R3.4/R3.14，3+ 轮）；④ 审计计数口径（MA7.3-01 + MV V.1 surefire 计数，2 轮）；⑤ 裁决通道记录（MA2.1 + R2.6/R2.9/R2.14/R3.20 + MR4，多轮）；⑥ 白名单双向断言审计法（MA7.1-01 HAVING，MV G.2 候选）；⑦ SSRF 主机 URL 解析规范化（MA7.2-01 userinfo/IPv6/IPv4-mapped，MV G.2 候选）；⑧ deploy/sql UK 物化断言（MV G.2 候选，与 ① 合并）
- [x] 逐项定位归属提示文件：deep-audit-prompts.md（21 维度表补行）/ orm-model-audit-prompt.md（补 UK 发射核查项）/ closure-audit-prompt.md（补 anti-hollow 调用链追踪）/ 新增独立提示（仅当维度确无归属且复用面 ≥2 轮）——归属矩阵记录：**归属矩阵**——③ xwf 流程语义审计 → `deep-audit-prompts.md` 新增维度 22（21 维度表补行，类别 I. 流程语义）；④ 审计计数口径 → `deep-audit-prompts.md` 共享前缀"通用审计口径"第 10 条（计数口径必须先定义）；⑥ 白名单双向断言 + ⑦ SSRF URL 规范化 → `deep-audit-prompts.md` 维度 13 执行步骤 8/9；①⑧ DDL 生成产物物化 + ② model-first 落地核对 → `orm-model-audit-prompt.md` 新增审计维度 8（UK DDL 物化）/9（model-first 落地核对）；Anti-Hollow 调用链追踪 → `closure-audit-prompt.md` 新增检查项；⑤ 裁决通道记录 → `audit-remediation-roadmap-authoring-prompt.md` 三通道模型补 P2 裁决通道记录节；**新增独立提示：无**（全部维度有归属，复用面全部 ≥2 轮）
- [x] 更新归属提示文件（新增检查项/维度行，标注来源 = 本路线图实例），新增提示文件按既有命名/格式（若新增）——执行结果记录：`deep-audit-prompts.md`（22 维度表 + 第六批批次 + 归档文件清单 + 共享前缀 #10 + 维度 13 步骤 8/9 + 维度 22 正文含教训/audit 引用）；`orm-model-audit-prompt.md`（维度 8/9 + 严重性指南补 blocker/major）；`closure-audit-prompt.md`（Anti-Hollow 调用链追踪检查项）；`audit-remediation-roadmap-authoring-prompt.md`（P2 裁决通道记录节）；无新增文件

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [x] 每个沉淀维度均有对应归属（既有提示更新或新增文件），可追溯——8 项维度全部落入 4 个既有提示文件（deep-audit-prompts.md / orm-model-audit-prompt.md / closure-audit-prompt.md / audit-remediation-roadmap-authoring-prompt.md），归属矩阵记录于 WS2 执行项 2，无无归属维度
- [x] 更新的提示内容包含本路线图的实际检查手段（如 `rg` 证据命令、断言测试模式），非空泛建议——维度 22 含 appState 判定/单一事实源/fail-closed 检查步骤；orm-model 维度 8 含 `grep -c '<unique-key name='` vs `constraint=` 计数命令；维度 13 含 extractHost userinfo/IPv6 检查项；共享前缀 #10 含计数口径规则
- [x] 新增文件（如适用时）命名/格式与既有 skills 一致——无新增文件（全部归属既有提示）
- [x] `No new test required`: 纯知识沉淀（无代码变更）
- [x] 文档变化：`ai-dev/skills/` 更新即本工作项本身（docs-for-ai 不变，`No owner-doc update required`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Workstream 3 - G.3 更新 docs-for-ai/ 和 design 文档

Status: completed
Targets: `docs-for-ai/03-modules/nop-metadata.md` + `docs-for-ai/01-repo-map/module-groups.md` + `docs-for-ai/04-reference/source-anchors.md`（若需）+ `ai-dev/design/nop-metadata/`（修复落地后的新 drift）

- Item Types: `Fix | Decision | Proof`

- [x] 复核 MR4 裁决对 docs-for-ai/ 的契约影响（MR4 为裁决文档，预期无契约变化；若 MR4 提升项修复改变了 GraphQL/API 面则必须同步 owner doc）——影响评估记录：**MR4 契约影响 = 无**——MR4 终局裁决 8 项全 deferred（4 watch-only residual + 4 out-of-scope improvement），提升项 0（roadmap MR4 段"提升项：0，纯裁决计划，Phase 3 无代码变更"），无 GraphQL/API 面变化 → docs-for-ai 三文件无需因 MR4 变更
- [x] 核对 MR3/MR4 修复落地后 `ai-dev/design/nop-metadata/` 的新 drift（如 R3.19 UK 修复后模型描述、R3.8 AggregationRowDTO 移除后 dto 描述、R3.15 改名后组件名）与影响理解的 P3 级陈旧（MA5.1 报告口径），修复为 live baseline——drift 清单与修复记录：**新 drift 修复**——① R3.15 改名：`01-architecture-baseline.md:1460/:1487/:1491`（NopMetaSearchService→NopMetaSearchProcessor）+ `12-data-contract-and-governance-workflow.md:360-362` + `nop-metadata-roadmap.md:41/:43/:221/:222/:233`（QualityAlertWorkflowService→QualityAlertWorkflowProcessor / NopMetaSearchService→NopMetaSearchProcessor）；② R3.8 AggregationRowDTO 移除：`api-dto-spec.md` + `02-dto-module-restructure-decision.md` + `docs-for-ai/03-modules/nop-metadata.md` "32 个 @DataBean"→"31 个"（R3.8 移除后 live 31）；③ R3.19 UK/isDelta：`01-architecture-baseline.md:1533` IS_DELTA "8 处"→"10 处"（+NopMetaDictItem +NopMetaTable，live 核实 10 实体含 IS_DELTA 列）；**P3 级陈旧（影响理解项）修复**——01-architecture-baseline（aggFunc countDistinct→count_distinct ×5、status 小写→大写 ×3、MetaModule long PK→String metaModuleId + version→moduleVersion、MemoryRowComparator→MemoryOrderByComparator ×2、NopMetaLineage__findPage 笔误、executeScheduledCheckpoint(String)→Map、nop-search-lucene runtime→optional、"全量 32 实体"→39）、03-version-management（version→moduleVersion ×4、MetaOrmModel modelId/moduleId→ormModelId/metaModuleId、releaseModule ErrorCode module-not-found→requireEntity 路径、查询表 status='released'→'RELEASED'）、04-data-governance（MetaDomain modelId→ormModelId、MetaDictItem dictId/group→metaDictId/itemGroup、entityTableId→metaTableId、transformExpression→transformExpr、ownerUserId domain 移除、severity 小写→大写、GraphQL 示例 moduleId→metaModuleId）、05-metadata-import（content precision 16777216→16777215、sources 无 NopMetaDataSource、collectCatalogForTable 已落地非 follow-up）、06-data-quality（CheckPoint 已建模非后续 plan、定时执行已实现、tableId→metaTableId ×2、profilingRuleId mandatory→nullable ×2）、08-reconciliation（IReconciliationService/LocalReconciliationService→Processor、IX_NOP_META_RECON_*→IX_NOP_META_RECONCILIATION_* ×3）、09-gap-analysis-extended（MetaEntityRepository→MetaModelChangedEventPublisher、MetaModelChangedEvent/MetaSemanticType Nop 前缀、MetaManifest statistics→content）、10-event-model（@Inject IEntityDao→IDaoProvider）、11-enterprise-semantic-layer（wf/approve-status→meta/approve-status、domainType Source-aligned→SourceAligned、幂等键 app 层 vs DB UK 澄清 ×2）、12-data-contract（ErrorCode 定义位置 MiscErrors+参数常量）、aggctx-and-bizmodel-split（行数 464/389/168→382/357/169、helper 34→43）、aggregation-processor-split（ERR_AGGR_* 已迁 AggregationErrors、SUPPORTED_DIALECTS 归属、包内 static 可见性）、api-dto-spec（Map overload 保留→仅 DTO 返回 ×2）、02-gap-analysis（MetaEntityRepository→Publisher）；**维持 deferred 记录**（P3 行号级/决策历史，不影响理解）：P3-MA5-109 行号引用簇、P3-MA5-125/126 决策文档现在时陈述（执行更新注已覆盖）
- [x] 复核 docs-for-ai 三文件与 live baseline 一致性（MR3 R3.7 后新增变化：修复计数、UK 现状、命名）——核对记录：`03-modules/nop-metadata.md`（31 个 @DataBean 已同步 R3.8；`_NopMetadataCoreConstants` "70+"→"125 个"实测；78 xmeta ✓；I*Biz 清单 ✓；META-001..005 ✓）；`module-groups.md` §2.6（类名/职责 ✓ 无改名残留）；`source-anchors.md` META-001（MetaAggregationExecutor "264 行"→"268 行"实测 MR3 后行数；META-002..005 锚点 ✓）
- [x] 运行 `node ai-dev/tools/check-doc-links.mjs --strict`（修改 docs-for-ai/ 后必跑，AGENTS.md 硬性要求）——exit code 记录：**exit 0（0 errors）**——首轮 1 error（aggregation-processor-split.md:117 `service/AggregationErrors.java` 相对路径解析失败，本次编辑引入）已修复为仓库绝对路径后复跑 0 errors；12 条 BROKEN_LINK warnings 为 ai-dev 相对路径代码引用仓库级容忍（历史惯例）
- [x] 更新 roadmap G.3 行 → done——执行结果记录：roadmap `nop-metadata-audit-remediation-roadmap.md` MG 段 G.3 行 → done（含 check-doc-links exit 0 注记）

Exit Criteria:

> 每个 Workstream 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Workstream Status 改为 `completed`。

- [x] design 文档与修复落地后的 live baseline 一致（新增 drift 已修复；P3 级陈旧逐项裁决：修复 or 维持 deferred + 记录）——R3.15 改名 / R3.8 DTO 计数 / R3.19 IS_DELTA 新 drift 全部修复；P3 级 35 项逐项裁决：影响理解项 30+ 修复（见 WS3 执行项 2 记录），行号级/决策历史 3 组（P3-MA5-109/125/126）维持 deferred 并记录
- [x] docs-for-ai 三文件复核一致（MR4 契约影响评估结论记录：无影响或已同步）——MR4 契约影响 = 无（0 提升项）；三文件计数/命名/锚点全部 live 复核一致（记录见 WS3 执行项 1/3）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0——实测 exit 0（0 errors；12 条 BROKEN_LINK warnings 为 ai-dev 相对路径代码引用仓库级容忍）
- [x] `No new test required`: 纯文档更新（修复文档内容与 live 代码一致性）
- [x] 文档变化：docs-for-ai/ + design 文档更新即本工作项本身
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Workstream 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。
>
> **纯文档计划**：本 plan 不涉及任何代码变更（仅修改 `docs/`、`ai-dev/` 下文件），`./mvnw test`、`./mvnw lint` 等构建验证条目直接从 Closure Gates 中删除，不需要执行（guide 模板明确允许）。

- [x] G.1 lessons 新增条目（无重复、有证据）完成——6 条（09-14）全部带 finding ID + 修复 plan + live 文件位置（closure audit 独立复核 8+ 处 live 事实 PASS）
- [x] G.2 skills 维度沉淀完成（归属可追溯）——8 项维度全部落入 4 个既有提示文件，归属矩阵记录于 WS2 执行项 2
- [x] G.3 docs-for-ai/ + design 文档与 live baseline 一致（check-doc-links --strict exit 0）——三文件计数/命名/锚点实测一致；check-doc-links exit 0
- [x] 不存在被静默降级到 deferred / follow-up 的文档缺口（G.3 逐项裁决记录）——P3 级 35 项逐项裁决：影响理解项全部修复；仅行号级（P3-MA5-109）与决策文档历史（P3-MA5-125/126）3 组维持 deferred 并记录于 WS3 执行项 2 + 下方 Deferred 段
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（写入本 plan Closure 段）——fresh session task `ses_02f04ca99ffeE2GrrOlmRS5CG2`，详见 Closure 段
- [x] **Anti-Hollow Check**：closure audit 验证 lessons/skills 内容有 live 证据支撑（非空泛模板），docs 与 live 代码一致（抽查）——8+ 处 live 事实独立复核 PASS（UK 36/36、类存在 ×3、executeScheduledCheckpoint Map 签名、prop_get、appState 守卫、行为断言测试、配置默认 false）；docs 抽查一致
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时）——实测 exit 0（closure 阶段最终跑，见 Closure 段 Evidence）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（closure 时必跑）——实测 exit 0（0 errors；BROKEN_LINK warnings 为 ai-dev 相对路径代码引用仓库级容忍）

（纯文档计划：`./mvnw test` 等构建验证条目按 guide 模板"纯文档计划"条款直接删除，不执行）

## Deferred But Adjudicated

### 影响理解的 P3 级文档陈旧中维持 deferred 的项（G.3 裁决后填写）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: **P3-MA5-109（01-architecture-baseline.md 行号引用簇，~30 处）**——纯行号锚点陈旧，MR3/MR4 修复未改变所引用代码的存在性，仅位置漂移；行号天然随代码演进变化，不构成理解障碍；**P3-MA5-125/126（02-dto-module-restructure-decision.md:35-37 依赖分析现在时陈述 + 02-gap-analysis.md extConfig 表述）**——决策文档历史记录，R3.7 已加执行更新注（:63），原始裁定文本按决策文档惯例保留；gap-analysis 为对比文档的通用表述。三者均不影响 live baseline 理解，维持 deferred（沿 MA5.1 报告"记号/计数/行号级"口径，roadmap 规则 1）
- Successor Required: `no`（后续治理批次）
- Successor Path: —

## Non-Blocking Follow-ups

- MV 验证中记录的 pre-existing 失败（132b60979 xview.xdef 回归 / rocksdb flaky）处置归对应任务（本路线图已记录，不重复）
- 本路线图 P2-MA3-03 / P2-MA7.2-02 / P2-MA7.5-05 等 MR4 终局裁决的 successor（专门 data-auth / 多 schema / 调度可靠性 plan）不在本计划范围，维持 roadmap 登记

## Closure

Status Note: 本 plan 为 nop-metadata-audit-remediation 路线图收尾里程碑（MG）——G.1 新增 6 条 lessons（09-14，覆盖矩阵无重复、全部带 live 证据）；G.2 8 项复用维度全部沉淀进 4 个既有 skills 提示（deep-audit-prompts 新增维度 22 / orm-model-audit 补 UK 物化 + model-first / closure-audit 补 anti-hollow 调用链 / roadmap-authoring 补 P2 裁决通道），无新增提示文件；G.3 修复 R3.15 改名、R3.8 DTO 计数、R3.19 IS_DELTA 新 drift + P3 影响理解项 30+，docs-for-ai 三文件与 live baseline 实测一致，check-doc-links --strict exit 0；roadmap G.1/G.2/G.3 → done，路线图全部里程碑（M0→MG）收尾；独立 closure audit（fresh session）验证全部 Exit Criteria PASS。可关闭。
Completed: 2026-08-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session）
- Audit Session: `ses_02f04ca99ffeE2GrrOlmRS5CG2`
- Evidence:
  - **WS1（G.1）验证结果（PASS）**：6 个 lessons 文件存在（09-14），均含场景/根因/正确做法/判定规则/参考 + finding ID + plan 引用；与既有 8 条无重复（05 vs 10、07 vs 13 焦点区分复核 PASS）；README 索引 09-14 六行一一对应；live 事实独立复核 PASS（orm.xml 36 constraint/36 unique-key/33 容器与 lesson 09/11 计数一致；MetaQualityCheckpointScheduler.java:201 Map 签名；NopMetaSearchProcessor/QualityAlertWorkflowProcessor/MemoryOrderByComparator 类存在；NopMetaTagLabelBizModel:112-115 prop_get；v1.xwf appState 守卫；TestNopMetaQualityRuleBizModel:305-321 行为断言；NopAuthConfigs.java:69-70 + biz-defaults.beans.xml:16 默认 false；IS_DELTA 10 处）；closure audit 指出 3 处引用路径笔误（ddl.xlib 路径 / MA7.6 报告文件名 / NopAuthConfigs 包路径）→ 本批已全部修正为 live 路径
  - **WS2（G.2）验证结果（PASS）**：deep-audit-prompts.md 22 维度表 + 维度 22 正文（:1204-1233）+ 共享前缀 #10（:327）+ 维度 13 步骤 8/9（:916-917）+ 第六批（:267）+ 归档清单（:227）；orm-model-audit-prompt.md 维度 8/9（:55-63）+ 严重性指南（:66-67）；closure-audit-prompt.md Anti-Hollow 调用链项（:25）；roadmap-authoring P2 裁决通道节（:198）；skills 仅 4 文件修改无新增（git status 复核）
  - **WS3（G.3）验证结果（PASS）**：nop-metadata.md "31 个 @DataBean"（live 31）+ "125 个" 常量（live 125）；source-anchors META-001 "268 行"（wc -l = 268）；01-architecture-baseline 无 7 个陈旧名残留（rg 0 命中）；12-data-contract:358-363 / nop-metadata-roadmap:42/:221-222/:233 用新名；roadmap G.1/G.2/G.3 → done（:212-214）；docs 抽查一致
  - **工具结果**：`check-doc-links.mjs --strict` exit 0（0 errors，13 条 BROKEN_LINK warnings 为 ai-dev 相对路径代码引用仓库级容忍）；`check-plan-checklist.mjs --strict` exit 0（closure 阶段最终跑，全部 41 项勾选 + Closure evidence 写入后实测）
  - **Deferred 项分类检查**：3 组 P3 维持 deferred（行号级 + 决策文档历史），均不影响理解，无 live defect 被降级（对照 Minimum Rule #16）
  - **Anti-Hollow 检查结果**：lessons/skills 内容 8+ 处 live 证据独立复核存在（非空泛模板）；docs 与 live 代码抽查一致；纯文档计划无代码，scan-hollow 不适用

Follow-up:

- no remaining plan-owned work（路线图 MR4/MV/MG 全部 done，nop-metadata-audit-remediation 收尾；G.3 维持 deferred 的 3 组 P3 行号/历史项已显式记录，后续治理批次）
- MV 验证记录的 pre-existing 失败（nop-stream-rocksdb 性能 flaky / TestAsyncSnapshotPipeline 超时竞态）维持归因记录，处置归对应任务（本路线图已记录，不重复）

## Optional Sections

- `## Risks And Rollback`：G.2 新增提示可能与其他既有提示重复——以归属矩阵先行核对，重复则并入既有提示不新增文件；G.3 修改 docs-for-ai/ 后必须跑 check-doc-links（硬门禁），未跑不 closure
- `## Outdated Note`：lessons/skills/docs 内容以本路线图（MR1-MR4 全部落地后）的 live baseline 为准
