# MA7 元数据域特有风险审计专项（全模块）

> Plan Status: draft
> Last Reviewed: 2026-08-05
> Draft Review: 待独立子 agent 对抗性审查
> Mission: nop-metadata-audit-remediation
> Work Item: MA7（7.1 SQL/表达式注入面 / 7.2 凭据管理与联邦查询数据权限 / 7.3 导入引擎与元数据同步安全 / 7.4 血缘大图与查询性能 / 7.5 调度与事件可靠性 / 7.6 工作流与审批集成）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MA7 里程碑）、`ai-dev/skills/open-ended-adversarial-review-prompt.md`、`ai-dev/skills/closure-audit-prompt.md`、`ai-dev/audits/arm-index-nop-metadata.md`（交叉对照）、`ai-dev/audits/arm-audit-dimension-matrix-nop-metadata.md`（❓ 未审计维度来源）
> Related: 执行顺序 `{1}` of 2 — 硬前置：M0（0.1-0.4）全部 done、MA1-MA6 全部 done、MR1 completed、MR2 completed；本计划是 roadmap 最后一个审计里程碑（依赖图：MA7 → MR3）；产物（P0/P1 发现）是 MR3（plan-2026-08-05-0746-2）R3.0 展开器的硬输入；本计划为纯审计零代码变更。

## Purpose

对 nop-metadata 全模块执行元数据域特有风险审计（roadmap MA7 的 6 个工作项：SQL/表达式注入、凭据管理与数据权限、导入引擎安全、血缘/查询性能、调度/事件可靠性、工作流/审批集成），覆盖通用审计维度（MA1-MA6）之外的域特有风险面（M0.1 维度矩阵中的 ❓ 域特有维度），产出审计报告并更新 arm-index，为 MR3 批量修复提供输入。

## Current Baseline

经 2026-08-05 live repo 核对（引用均与 roadmap/arm-index 一致，已二次确认文件与计数）：

- roadmap MA7 六行（7.1-7.6）状态为 `todo`；Deps（0.4 done）已满足；MA1-MA6 全部 completed；MR1/MR2 completed（R2.1~R2.19 全部终态确定）
- **7.1 对象（live 核实）**：`field/ExpressionMeasureValidator.java`（标识符白名单校验 161/173/179 行）、`query/`（`FilterToSqlTranslator`、`MetaJoinExecutor`、`MetaTableQueryExecutor`、`MemoryFilterEvaluator`、`DefaultFilterApplicator`、各 AggregationProcessor）、custom_sql 分词黑名单与 join 注入点；交叉对照：MA3.3 安全报告（13 维度已初步审计）、历史 AR-01 修复（normalizeSchema 补 validateIdentifier）、`MiscErrors`/`QualityErrors`/`AggregationErrors` identifier whitelist 错误码（live 存在，`^[A-Za-z_][A-Za-z0-9_]*$`）
- **7.2 对象（live 核实）**：`connection/MetaDataSourceConnectionProcessor.java` + `IMetaDataSourceConnectionProcessor.java`（AR-02 修复面：协议/驱动/主机白名单 + loginTimeout，`DataSourceErrors` 对应错误码）、connectionConfig 凭据加密/脱敏链路（MA6.4 已扫泄露面：4 P3 + 2 informational，无硬编码凭据命中）、withConnection 直查旁路（MA3.3 初步审计过 withConnection 面，本计划做数据权限旁路深挖）；**MA6.4 报告 Follow-up 显式把 P3-MA6.4-01/02/03（错误路径凭据回显）归入本计划 7.2 深挖**
- **7.3 对象（live 核实）**：`nop-metadata-dao/.../dao/model/OrmModelImporter.java`（ORM XML 解析入口，20-01 时钟修复后状态）、`service/entity/NopMetaModuleBizModel.java`（`importOrmModel`:165 / `importOrmModels`:359）、`service/sync/ExternalTableStructureReader.java` + `ExternalTableInfo/ExternalColumnInfo`（外部表结构同步）、`service/manifest/MetaManifestBuilder.java`（manifest 快照）；多 schema 面与 RACE/P2-MA3-03 交叉核对（MA6.6 已裁定 P2-MA6.6-001 归 MR3/DDL 管线，本计划不重复裁定）
- **7.4 对象（live 核实）**：`service/lineage/`（`MetaLineageEdgeQueryAction`、`LineageTagPropagationProcessor`、`SqlColumnLineageExtractor`、`SqlSourceTableExtractor`、`ColumnLineageCandidate`）、`service/query/`（`MetaTableQueryExecutor`、`MetaJoinExecutor`、`MetaAggregationExecutor`、各 AggregationProcessor）；**AR-25（血缘抽取 N+1 upsert）已裁定 optimization candidate，arm-index 登记"MA7.4 复核后按需 MR3"——本计划 7.4 必须给复核结论（维持/按需 MR3）**；RACE 终局（MA6.6：并发面维持 watch-only）不重复裁定
- **7.5 对象（live 核实）**：`service/quality/`（`MetaQualityCheckpointScheduler`（cron）、`MetaQualityCheckpointExecutor`、`CheckpointActionDispatcher`、`MetaQualityRuleExecutor`、`QualityResultWriter`、`MetaQualityScorer`）、`service/event/MetaModelChangedEventPublisher.java`；交叉对照：MA3.4 异步/事务报告、MA4.1 错误处理报告、MR2 R2.8（QualityResultWriter status 字典 fail-fast 修复后状态）与 R2.12（CheckpointExtConfig parseBeanFromText 修复后状态）
- **7.6 对象（live 核实）**：`_vfs/nop/wf/` 3 个 xwf（metaDataContractApproval/qualityBreachApproval/tagLabelConfirmApproval，MR2 R2.1 迁移+修复后状态——本计划验证修复后可用性与失败路径，非重复审计修复前缺陷）、`service/contract/MetaContractChecker.java`、`service/quality/QualityAlertWorkflowService.java`（MR2 R2.16 命名违规 deferred 候选之一，本计划不裁决改名）、`CheckpointActionDispatcher` webhook SSRF allowlist（维度13-04：fail-closed 策略 + `QualityErrors` checkpoint-webhook-* 错误码，live 存在）
- **执行顺序约束（防批次串扰）**：本 plan（`{1}`）执行时 MR3（`{2}`）必须未启动——7.2/7.6 对 MR2 修复项（R2.8/R2.12/R2.1）的"修复后状态验证"以 MR3 未改代码为前提；若 MR3 已先行启动，相关条目改为复核 MR3 修复后状态并注明
- 审计方式约束（沿用 MA1-MA6 先例）：纯审计零代码变更；发现一律使用轮次限定 ID 或 `P<级别>-<里程碑>-<序号>` 并标注修复归属（MA7 新发现归 MR3，MR4 跨维度裁决项单独标注）
- 绿色基线：**822 tests / 0 failures / 0 errors / 0 skipped**（MR2 收口实测 2026-08-05，范围 `-pl nop-metadata -am -T 1C`；工作树含 nop-frontend-support 未提交变更，验证以 clean 基线为准，见 MR2 closure 注记）

## Goals

- 产出 MA7 审计报告（6 份：7.1-7.6），每份含 P 级标注 + 修复归属 + 可追溯 file:line
- 覆盖元数据域特有风险面（注入/凭据/导入/性能/调度/工作流），补齐 M0.1 维度矩阵 ❓ 域特有维度
- AR-25 给复核结论；MA6.4 P3 家族（错误路径凭据回显）给深挖结论；MR2 修复项（R2.1/R2.8/R2.12）给修复后状态验证结论
- 更新 arm-index-nop-metadata.md 报告清单与 P0/P1 追踪；roadmap 7.1-7.6 → done
- 无 P0 时保持绿色基线；发现 P0 走即时通道

## Non-Goals

- 不修复审计发现（修复归 MR3 批量修复，P0 例外走即时通道）
- 不重新审计 MA1-MA6 已覆盖维度（通用安全/错误/测试/接线/泄露面等只交叉引用既有报告，不重复扫描）
- 不改任何 `src/` 代码或测试（纯审计计划）
- 不处理 P3（deferred successor，roadmap 规则 1）
- 不裁决 P2-MA6.6-001 / P2-MA3-03（已归 MR3/DDL 管线，本计划只交叉核对）

## Scope

### In Scope

- 7.1 SQL/表达式注入面：ExpressionMeasureValidator 黑名单绕过路径、custom_sql 分词黑名单、join 注入点、参数绑定顺序、跨库内存过滤
- 7.2 凭据管理与联邦查询数据权限：connectionConfig 凭据加密/脱敏（含 MA6.4 P3 家族深挖）、withConnection 直查绕过 ORM data-auth、多租户数据源隔离
- 7.3 导入引擎与元数据同步安全：ORM XML 解析（XXE/实体膨胀）、外部表同步、manifest 快照、多 schema
- 7.4 血缘大图与查询性能：BFS 遍历深度、列级血缘边数爆炸、N+1（AR-25 复核）、聚合内存上限、检查点扫描性能
- 7.5 调度与事件可靠性：cron 调度可靠性、事件脱敏（sourceSql/buildSql sensitive 标记）、幂等、失败重试
- 7.6 工作流与审批集成：.xwf 审批流（MR2 R2.1 修复后状态 + 失败路径）、webhook SSRF allowlist 回归、QualityAlertWorkflowService 失败路径、nop-wf 依赖完整性
- 审计报告（`ai-dev/audits/2026-08-05-{HHmm}-arm-MA7.<n>-nop-metadata-<dimension>.md`，`{HHmm}` 为报告实际产出时刻）+ arm-index 更新 + roadmap 7.1-7.6 → done

### Out Of Scope

- MR3/MR4 修复（后续计划）
- 任何修复（MR3 承接，P0 即时通道除外）
- `docs-for-ai/` 文档修改（MA5 已覆盖；审计发现记录为 finding 即可）

## Execution Plan

### Phase 1 - MA7.1 SQL/表达式注入面审计

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/field/` + `query/` + 相关 BizModel（custom_sql/sqlview 面）

- Item Types: `Proof`

- [ ] **启动门禁核查**：确认 M0/MA1-MA6 已 done、MR1/MR2 completed（roadmap 对应行 + arm-index 报告清单）、**MR3 未启动**（执行顺序约束，见 Current Baseline）；未满足则不启动并上报
- [ ] 对抗性审计（`open-ended-adversarial-review-prompt.md`）：ExpressionMeasureValidator 黑名单绕过路径（标识符/限定符/函数白名单的绕过、大小写/转义/嵌套）、custom_sql 分词黑名单、join 注入点（`MetaJoinExecutor`/`FilterToSqlTranslator` 的 join 条件拼装、参数绑定顺序）、跨库内存过滤（`MemoryFilterEvaluator`/`DefaultFilterApplicator` 的过滤语义与数据落地前校验）；与 MA3.3 安全报告 + AR-01 修复（validateIdentifier）交叉核对确认无新旁路
- [ ] 产出审计报告 `ai-dev/audits/2026-08-05-{HHmm}-arm-MA7.1-nop-metadata-sql-injection.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：审计面清单（文件 + 注入点类型）+ 发现清单（file:line + 注入向量 + P 级 + 归属）+ 与 MA3.3/AR-01 交叉核对表
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`（审计报告为证据层）
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - MA7.2 凭据管理与联邦查询数据权限审计

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/` + withConnection 直查调用链

- Item Types: `Proof`

- [ ] 对抗性审计：connectionConfig 凭据加密/脱敏链路（存储/读取/日志/错误消息四环节；**MA6.4 P3-MA6.4-01/02/03 错误路径凭据回显深挖**）、AR-02 修复面回归（协议/驱动/主机白名单 + loginTimeout，`DataSourceErrors` 错误码语义）、withConnection 直查绕过 ORM data-auth 的旁路面（与 MA3.3 初步结论交叉核对，确认绕过面边界与默认开关状态）、多租户数据源隔离（连接池/DataSource 生命周期隔离）
- [ ] 产出审计报告 `ai-dev/audits/2026-08-05-{HHmm}-arm-MA7.2-nop-metadata-credential-datapriv.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：凭据链路四环节核对表 + MA6.4 P3 家族深挖结论（每项：维持/升级/降级 + 依据）+ AR-02 修复面回归结论 + withConnection 旁路面边界结论
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - MA7.3 导入引擎与元数据同步安全审计

Status: planned
Targets: `nop-metadata/nop-metadata-dao/src/main/java/io/nop/metadata/dao/model/OrmModelImporter.java` + `service/entity/NopMetaModuleBizModel.java` + `service/sync/` + `service/manifest/`

- Item Types: `Proof`

- [ ] 对抗性审计：ORM XML 解析（XXE/实体膨胀/路径穿越——`importOrmModel`/`importOrmModels` 的 path 输入校验）、外部表同步（`ExternalTableStructureReader` 的元数据读取面）、manifest 快照（`MetaManifestBuilder` 快照一致性与注入面）、多 schema 支持面；与 MA6.6 RACE 终局/P2-MA3-03 交叉核对（不重复裁定）
- [ ] 产出审计报告 `ai-dev/audits/2026-08-05-{HHmm}-arm-MA7.3-nop-metadata-import-sync.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：导入链路面清单（XML 解析/同步/manifest）+ 发现清单（file:line + 攻击面类型 + P 级 + 归属）+ 与 RACE/P2-MA3-03 交叉核对结论
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - MA7.4 血缘大图与查询性能审计

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/lineage/` + `query/`

- Item Types: `Proof`

- [ ] 对抗性审计：血缘大图 BFS 遍历深度上限、列级血缘边数爆炸（`LineageTagPropagationProcessor`/`SqlColumnLineageExtractor`）、N+1 查询（**AR-25 复核：血缘抽取 N+1 upsert 现状 → 维持 optimization candidate 或按需归 MR3，结论写入报告**）、聚合内存上限（`MetaAggregationExecutor`/各 Processor 的内存行集）、检查点扫描性能（`MetaQualityCheckpointScheduler` 扫描面）
- [ ] 产出审计报告 `ai-dev/audits/2026-08-05-{HHmm}-arm-MA7.4-nop-metadata-lineage-performance.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：性能风险点清单（file:line + 风险类型 + 数据规模触发条件 + P 级 + 归属）+ AR-25 复核结论（维持/按需 MR3 + 依据）
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - MA7.5 调度与事件可靠性审计

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/` + `event/`

- Item Types: `Proof`

- [ ] 对抗性审计：cron 调度可靠性（`MetaQualityCheckpointScheduler` 的 cron 解析/错失触发/并发保护）、事件脱敏（`MetaModelChangedEventPublisher` 事件载荷中 sourceSql/buildSql sensitive 标记）、幂等（检查点执行/结果写入/事件发布的重复触发面）、失败重试（`CheckpointActionDispatcher` 投递失败路径）；与 MA3.4 异步/事务报告 + MR2 R2.8（status 字典 fail-fast）/R2.12（CheckpointExtConfig）修复后状态交叉核对
- [ ] 产出审计报告 `ai-dev/audits/2026-08-05-{HHmm}-arm-MA7.5-nop-metadata-schedule-event.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：可靠性风险点清单（file:line + 风险类型 + P 级 + 归属）+ 与 MA3.4/R2.8/R2.12 交叉核对表
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - MA7.6 工作流与审批集成审计

Status: planned
Targets: `_vfs/nop/wf/`（3 个 xwf）+ `service/contract/MetaContractChecker.java` + `service/quality/QualityAlertWorkflowService.java` + `CheckpointActionDispatcher`（webhook SSRF）

- Item Types: `Proof`

- [ ] 对抗性审计：.xwf 审批流（**MR2 R2.1 修复后状态验证**：3 个 xwf 可达性/审批流转/失败路径——非重复审计修复前缺陷）+ `MetaContractChecker` 契约检查失败路径 + `QualityAlertWorkflowService` 失败路径（提审/通知失败的处理）+ webhook SSRF allowlist 回归（维度13-04：协议/method/主机白名单 fail-closed 语义，`QualityErrors` checkpoint-webhook-* 错误码）+ nop-wf 依赖完整性（pom 依赖 + 容器内 wf engine bean 可用性）
- [ ] 产出审计报告 `ai-dev/audits/2026-08-05-{HHmm}-arm-MA7.6-nop-metadata-workflow.md`

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 报告包含：MR2 R2.1 修复后状态验证表（可达性/流转/失败路径 PASS/FAIL + 证据）+ SSRF allowlist 回归结论 + 失败路径发现清单（file:line + P 级 + 归属）
- [ ] 每个发现标注轮次限定 ID 或新 ID + P 级 + 修复归属
- [ ] 文档变化：`No owner-doc update required`
- [ ] `No new test required`: 纯审计计划零代码变更
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 6 份 MA7 审计报告产出（7.1-7.6），每份含 P 级标注 + 修复归属 + 可追溯 file:line 引用
- [ ] arm-index-nop-metadata.md 报告清单 +6 行、P0/P1 追踪更新；roadmap 7.1-7.6 → done
- [ ] AR-25 复核结论与 MA6.4 P3 家族深挖结论已登记（arm-index 或报告）
- [ ] 所有 in-scope confirmed live defects / owner-doc drift 均有明确归属（MR3 修复 / watch-only + Why Not Blocking Closure），无静默降级
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步到 live baseline，或明确写明 `No owner-doc update required`
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（evidence 写入本 plan Closure 段）
- [ ] **Anti-Hollow Check**：closure audit 已验证 6 份报告为真实审计产物（实际发现清单 + 可追溯 file:line + 历史对照），非模板空壳
- [ ] `./mvnw compile -pl nop-metadata -am -q` 通过（纯审计零代码变更，确认无回归）
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 绿色基线保持（822/0 或重新实测记录）
- [ ] checkstyle / 代码规范检查通过（无代码变更，以 mvn 默认检查为准；历史计划惯例记 "checkstyle N/A"）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（修改 `ai-dev/` 下文件后执行）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0（closure 时，Minimum Rule #26）

## Deferred But Adjudicated

### MA7 P2/P3 finding 的修复

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap 规则 1 明示本 roadmap 只处理 P0/P1；P2/P3 记录为 deferred successor，由后续批次另行规划（同 MA1-MA6 裁定）。P2-MA6.6-001 / P2-MA3-03 已归 MR3/DDL 管线，不在本计划裁决范围。
- Successor Required: `no`（后续批次另行规划，非本批 scope）

## Non-Blocking Follow-ups

- watch-only 项复核维持原裁定者，登记结论即可（不产生修复债务）
- AR-25 若复核后按需归 MR3，作为 MR3 R3.0 展开器输入登记（roadmap 或 arm-index）
- MA7 新发现的 P2/P3 家族归 MR3 R3.0 展开器裁决（不产生独立计划）

## Closure

Status Note: <<本计划为 draft，执行完成后填写>>
Completed: YYYY-MM-DD

Closure Audit Evidence:

- Reviewer / Agent: <<独立子 agent（fresh session，closure audit 专用，非执行 session 复用）>>
- Evidence: <<执行后填写：6 份报告存在 + 发现清单 live 复核 + arm-index/roadmap 更新 + 绿色基线 + check-plan-checklist 退出码等>>

Follow-up:

- <<执行后填写>>

## Optional Sections

- `## Risks And Rollback`：纯审计计划零代码变更，无回滚面；唯一风险是报告质量（防空壳），由每 Phase 的交叉核对表 + closure audit 的 Anti-Hollow Check 兜底
