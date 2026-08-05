# MR6 展开器裁决（R6.0：Follow-up Backlog 32 条逐项裁决——提级 12 条 + 终局归类 20 条）

> Plan Status: completed
> Last Reviewed: 2026-08-05
> Mission: nop-metadata-audit-remediation
> Work Item: MR6（R6.0 展开器）
> Source: `ai-dev/backlog/nop-metadata-audit-remediation-roadmap.md`（MR6 里程碑 + Follow-up Backlog 32 条）、`ai-dev/audits/2026-08-05-0655-multi-audit-nop-metadata-audit-remediation.md`（P2-01~27）、`ai-dev/audits/2026-08-05-0655-open-audit-nop-metadata-audit-remediation.md`（AR-06~10）、`ai-dev/audits/arm-index-nop-metadata.md`（§P2 追踪）
> Related: 执行顺序 `{1}` of 7 — 启动门禁：MR5 全部行 done（roadmap R6.0 Deps）；本 plan 为**裁决文档计划**（roadmap 规则 1 的显式豁免通道：P2 提级 P1 执行），产物 = 裁决记录 + arm-index §P2 更新；后续 R6.1~R6.5 为修复 plan，R6.6 为终局归类批量。

## Purpose

对 Follow-up Backlog 32 条（P2-01~27 = 27 条 + AR-06~10 = 5 条，共 32 条；roadmap header 的 "33 条" 为计数错误，R6.0 收口时一并纠正）逐项裁决：(a) **提级 12 条为 P1 修复**（安全类 4 条：P2-10/11/12/13；正确性类 5 条：P2-06/07/09 + AR-07/08；日志类 3 条：P2-01/02/04——提级依据：实测绕过面 / 凭据泄露面 / 静默失败或竞态退化；与 roadmap R6.1~R6.5 行承载项一一对应）；(b) **其余 20 条终局归类**（watch-only residual / out-of-scope improvement / docs 批量），每项附 Why Not Blocking Closure；(c) 裁决记录写入 roadmap MR6 段 + arm-index §P2，R6.0 → done，解除 R6.1~R6.6 的 Deps 门禁。

## Current Baseline

经 2026-08-05 live repo 核对（finding 描述以两轮审计报告为准；裁决依据以本次 live 复核为准）：

- **roadmap MR6 段全部 `todo`**（R6.0~R6.6）；MR5 段全部 done（Deps 满足）。Follow-up Backlog 32 条全部标 `backlog`，无一终态
- **安全类 4 条（提级候选）**：
  - P2-10：custom_sql 黑名单遗漏 `pg_read_binary_file` / `RUNSCRIPT` / `PG_LS_LOGDIR` / `PG_LS_WALDIR` / `PG_STAT_FILE` / `SCRIPT` 等——疑似可绕过面，需 live 核实（黑名单实现 + 分词机制）
  - P2-11：webhook 请求未显式关闭重定向跟随（`CheckpointActionDispatcher.dispatchWebhook` 经 `IHttpClient.fetch`，重定向行为依赖 HttpClientConfig 默认）——需 live 核实默认值
  - P2-12：`ARG_RAW_JDBC_URL`（rawJdbcUrl 明文）作为错误参数（`MetaDataSourceConnectionProcessor.java:227/:235` `.param(NopMetadataErrors.ARG_RAW_JDBC_URL, jdbcUrl)`）可能进入日志/错误响应——需核实参数是否被序列化输出
  - P2-13：`ExpressionMeasureValidator` 两个黑名单条目因分词机制永远无法命中（死条目）——需核实黑名单条目与分词逻辑
- **正确性类 5 条（提级候选）**：
  - P2-06：`AggregationHelper.checkTableExists`（`query/AggregationHelper.java:496-506`）把 `getTables` SQLException 归类为"表不可见"返回 false——错误诊断误导，应区分"查询失败"与"表不存在"
  - P2-07：`NopMetaModuleBizModel.parseDeltaModel`（`entity/NopMetaModuleBizModel.java:229-232`）解析失败降级 delta=full——数据完整性风险，应 fail-loud
  - P2-09：`NopMetaTagLabelBizModel` 提审失败仅 `LOG.warn` 继续（`:129`）——用户侧无感知，审批静默失败
  - AR-07：R4.2 后可空 `META_SCHEMA` 入 UK，NULL-schema 重复行不再被 DB 拦截（find-then-insert 竞态退化）——需原子 upsert 或空串占位
  - AR-08：`createSqlTable` 重复守卫查询缺 isDelta/schema 过滤，比 4 列 UK 更严（误报 `ERR_SQL_VIEW_TABLE_EXISTS`）
- **日志类 3 条（提级候选，成本低）**：P2-01（`NopMetaSearchProcessor` fail-closed 分支 `:56-66,:77-87` 抛错不带原始异常链）；P2-02（`AutoClassificationProcessor.java:129-134` 正则编译失败静默 continue）；P2-04（`MetaQualityCheckpointExecutor`/`Scorer`/`BizModel`/`Scheduler` 4 处 catch 后静默返回默认值）
- **终局归类候选 20 条**：P2-03（21 个错误码死码 + ERR_RECON_PARSE_PROPERTIES_FAILED 契约漂移）、P2-05（.param() 399 处裸字符串 vs 120 处 ARG_* 双风格）、P2-08（judgeRegex 方言 SKIP 状态）、P2-14~21（测试质量 8 条：错标快照/trivial 镜像/纯常量/手工清单/页面冒烟/死分支弱断言/脆弱扫描/反射私有）、P2-22/23/24/26（文档 4 条）、P2-25/27（pom 2 条）、AR-06（sourceTables 语义与名称相反，0 消费方）、AR-09（runId 未进错误路径 DTO + 多 schema 语义未进文档）、AR-10（状态确认）
- 绿色基线：`./mvnw test -pl nop-metadata -am -T 1C` 894/0（MR5 R5.3 收口口径）

## Goals

- 32 条逐项终态：提级 12 条（记录到 R6.1~R6.5 对应行）+ 终局归类 20 条（记录到 R6.6 行），0 条悬置
- 每条裁决附 Why Not Blocking Closure（终局归类项）或提级依据（安全/正确性项），写入 roadmap MR6 段
- arm-index §P2 同步更新（终态 + 归属 MR6 轮次），与 roadmap 追踪一致
- R6.0 → done，解除 R6.1~R6.6 Deps 门禁

## Non-Goals

- 不执行修复（修复属 R6.1~R6.5 各 plan）
- 不重新审计 Backlog 内容本身（以两轮审计报告为输入，仅做终态裁决）
- 不修改 Follow-up Backlog 的登记表结构（保留登记批次与来源路径）

## Execution Plan

### Phase 1 - 提级项 live 核实与裁决

Status: completed
Targets: 审计报告 2 份 + 相关源码

- Item Types: `Decision | Proof`

- [x] 核实安全类 4 条（P2-10 黑名单实现与分词机制 / P2-11 HttpClientConfig 重定向默认值 / P2-12 rawJdbcUrl 参数是否序列化输出 / P2-13 死条目命中路径），确认或修正提级依据
- [x] 核实正确性类 5 条（P2-06/07/09/AR-07/AR-08 的 live 行为与影响面），确认或修正提级依据
- [x] 核实日志类 3 条（P2-01/02/04 的静默路径），确认提级依据（低成本 + 诊断收益）
- [x] 逐项记录裁决结果（提级 → 归属 R6.x 行；理由）

Exit Criteria:

- [x] 12 条提级候选全部有 live 核实依据（代码路径 + 影响面），裁决一致（不因复核翻案或明确记录翻案原因）
- [x] 裁决记录写入 roadmap MR6 段（R6.1~R6.5 行提级项清单核对）
- [x] **无静默跳过**：无任何提级候选因"没时间"被降级为归类（见 Minimum Rules #24）
- [x] 本 Phase 为纯裁决 + 文档变更，无代码变更 → 构建验证由 Closure Gates 的 `./mvnw test` 承接
- [x] `ai-dev/logs/2026/08-05.md` 已更新

### Phase 2 - 终局归类裁决（20 条）

Status: completed
Targets: arm-index §P2 + roadmap R6.6 行

- Item Types: `Decision`

- [x] 逐条终局归类 20 条（P2-03/05/08/14~21/22/23/24/26/25/27 = 17 条 + AR-06/09/10 = 3 条；watch-only residual / out-of-scope improvement / docs 批量），每条附 Why Not Blocking Closure（依据 = 无消费方 / 非 live defect / 破坏性变更 / 无实际暴露 / 低概率自愈，沿 MR4 先例）
- [x] AR-06 单独裁决并登记：roadmap R6.0 行误将其列入"安全类提级"清单但无任何 R6.x 行承载、R6.6 行亦未列出——按"字段语义与名称相反、0 消费方"归类为 watch-only residual，纠正 roadmap 归属（R6.0 提级清单删除 AR-06、R6.6 行补入）
- [x] P2-03 单独裁决：错误码死码属"删码 vs 保留"决策——live defect（契约漂移）还是清理项，明确归属（修入 R6.6 或另开专项）
- [x] 裁决记录写入 roadmap R6.6 行 + arm-index §P2 终态更新（含 AR-09/AR-10 状态确认）

Exit Criteria:

- [x] 20 条全部终态（无悬置、无"后续再说"式模糊归类），每条 Why Not Blocking Closure 可复核
- [x] 无已确认 live defect 被降级为终局归类（见 Minimum Rules #15/#16）
- [x] arm-index §P2 与 roadmap R6.6 行一致（grep 20 条可追溯）
- [x] 本 Phase 为纯裁决 + 文档变更，无代码变更 → 构建验证由 Closure Gates 承接
- [x] `ai-dev/logs/2026/08-05.md` 已更新

### Phase 3 - 收口

Status: completed
Targets: roadmap + arm-index + 文档

- Item Types: `Fix | Proof`

- [x] roadmap MR6 段状态同步：R6.0 → done，注明裁决统计（提级 12 / 归类 20 / 悬置 0）
- [x] 纠正 roadmap MR6 段计数错误（header "33 条"→32；R6.0 行"提级 10"→12、"归类 22"→20、AR-06 移出安全类提级清单；R6.6 行"22 条"→20 并补入 AR-06），与 Follow-up Backlog 表 32 行一致
- [x] 更新 roadmap header 最后更新记录（MR6 R6.0 收口）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过（0 errors）

Exit Criteria:

- [x] roadmap MR6 段与 arm-index §P2 追踪一致（提级/归类逐条可追溯）
- [x] R6.1~R6.6 的 Deps（R6.0 done）已解除
- [x] check-doc-links --strict exit 0
- [x] `ai-dev/logs/2026/08-05.md` 已更新

## Closure Gates

> 关闭条件：本 section 所有条目与每个 Phase 的 Exit Criteria 全部 `[x]` 后，才能将 Plan Status 改为 `completed`。

- [x] 32 条全部终态（12 提级 + 20 归类，含 AR-09/AR-10 状态确认），0 悬置
- [x] 无已确认 live defect / contract drift 被静默降级到非 blocking 区域
- [x] roadmap MR6 段 + arm-index §P2 双向一致
- [x] 独立子 agent closure-audit 完成并记录证据（Anti-Hollow：裁决记录与实际代码行为一致，无"纸面裁决"）
- [x] `./mvnw test -pl nop-metadata -am -T 1C`（本 plan 无代码变更，验证无回归）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0

## Deferred But Adjudicated

### 终局归类 20 条（Phase 2 产物）

- Classification: `watch-only residual`（P2-05/14/15/16/17/18/19/20/21/25/27/AR-06/AR-10 = 13 条 + AR-09 DTO 面）+ `out-of-scope improvement`（P2-03 = 1 条，裁定修入 R6.6 批量）+ `docs batch`（P2-08/22/23/24/26/AR-09 文档面 = 6 条，并入 R6.6 docs sweep）——逐条归类 + Why Not Blocking Closure 见 roadmap MR6 段「R6.0 裁决记录」+ arm-index §P2「MR6 裁决记录」
- Why Not Blocking Closure: 逐条填写完成（依据 = 无消费方 / 非 live defect / 破坏性变更 / 无实际暴露 / 低概率自愈，沿 MR4 先例）——20 条全部声明，无"后续再说"式模糊归类
- Successor Required: 否（P2-03 裁定修入 R6.6 批量，不另开专项；其余 19 条无 successor，终态即 watch-only 跟踪）

## Non-Blocking Follow-ups

- R6.6 终局归类 20 条在 MR6 收口后的 watch-only 跟踪（arm-index §P2 终态即跟踪载体）
- P2-08（judgeRegex 方言 SKIP）docs 例外说明，若 R6.6 归类为 docs 批量则并入对应文档更新

## Closure

Status Note: MR6 R6.0 裁决器收口——32 条 Backlog 逐项终态（12 提级落 R6.1~R6.5 + 20 终局归类落 R6.6，0 悬置），全部裁决基于 2026-08-05 live 代码复核（提级 12 条逐条记录代码路径 + 影响面；归类 20 条逐条 Why Not Blocking Closure）；roadmap MR6 段 + arm-index §P2（MR6 裁决记录段）双向一致；计数勘误 33/10/22 → 32/12/20；R6.0 → done 解除 R6.1~R6.6 Deps 门禁；纯裁决 + 文档计划零代码变更，构建验证 `./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS（nop-metadata 子树 895/0/0/0 = service 894 + web 1，与 R5.3 894/0 基线一致）+ `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（12 条 BROKEN_LINK warnings 为 ai-dev/plans 历史文件既有问题：2249/2250/331/nop-stream-2300-3，非本批引入）。
Completed: 2026-08-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session task `ses_02db1c883ffepZUbJM1Eb6OwKl`，read-only，未修改任何文件）
- Evidence:
  - Phase 1 Exit Criteria 5/5 PASS：12 条提级候选全部 live 核实（提级依据逐条含代码路径 + 影响面，记录于 roadmap MR6 段「R6.0 裁决记录」+ arm-index §P2「MR6 裁决记录」）；无静默跳过（Minimum Rules #24）；裁决记录已写入 roadmap MR6 段
  - Phase 2 Exit Criteria 5/5 PASS：20 条全部终态（watch-only ×13 + out-of-scope ×1 + docs ×6，每条 Why Not Blocking Closure 可复核）；AR-06 归属纠正（R6.0 提级清单移出 → R6.6 补入）；P2-03 单独裁定修入 R6.6 批量；arm-index §P2 与 roadmap R6.6 行一致（grep 20 条可追溯，audit 独立复核 32 个 ID 双文件集合一致）
  - Phase 3 Exit Criteria 4/4 PASS：roadmap MR6 段与 arm-index §P2 追踪一致；R6.1~R6.6 Deps（R6.0 done）已解除（audit 核实 R6.1~R6.5 行 Deps = `R6.0 done`）；check-doc-links --strict exit 0（audit 复跑确认，12 warnings 为 ai-dev/plans 历史文件既有）；日志已更新（audit required fix 补齐后闭环）
  - Closure Gates 6/6 PASS：32 条终态 0 悬置；无 live defect/contract drift 静默降级（P2-03 显式修入 R6.6，audit 独立确认）；roadmap + arm-index 双向一致；独立子 agent closure-audit 完成（Anti-Hollow：12/12 提级记录与 live 代码逐条相符，无"纸面裁决"）；`./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS（nop-metadata 子树 895/0/0/0 = service 894 + web 1，audit 独立聚合 surefire 报告相符；run1 并行编译瞬时竞态 + run2/3 文档化 pre-existing 性能基准 flaky 单跑 PASS 实证与本次零代码变更无关）；check-doc-links --strict exit 0
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-metadata-audit-remediation/2026-08-05-2154-1-mr6-expander-adjudication.md --strict` exit 0（无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow：纯裁决 + 文档计划，无新代码 → 调用链检查不适用；裁决记录与 live 代码行为一致性由 audit 12/12 抽样覆盖
  - Deferred 分类检查：20 条归类项全部符合 Minimum Rules #15/#16（无 in-scope live defect 被降级；P2-03 契约漂移显式进入修复批量）

Follow-up:

- R6.1~R6.5（提级 12 条修复）与 R6.6（终局归类 20 条批量，含 P2-03 删码 + ERR_RECON_PARSE_PROPERTIES_FAILED 二选一、P2-08/22/23/24/26/AR-09 文档 sweep、P2-05/14~21/25/27/AR-06/AR-10 watch-only 跟踪）为 roadmap 后续行，Deps（R6.0 done）已解除，由后续 plan 承接
- no remaining plan-owned work
