# 02 WI0b D1/D3/D4/D5/D6/D15 六条裁定落档——SQL 子集与语义

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/nop-stream-sql-roadmap.md（WI0b 行、前置裁定表 :74-90 及 :92 落档义务注、Risks R1 与 re-scope 表 :209-215、§3.3、§3.4）
> Related: ai-dev/plans/nop-stream-sql/01-wi0a-d2-vision-conflict-resolution.md
> Owner: 仓库 owner（2026-10-02 执行指令委托 ZCode 代理按 roadmap 建议项执行，授权原文见 ai-dev/design/nop-stream/sql-vision-conflict-resolution.md §2）

## Purpose

把六条前置裁定（D1 结果表语义、D3 语法分层〔已裁定 2026-09-30，本计划补落档〕、D4 流时间窗口语法、D5 全局 ORDER BY / LIMIT、D6 分析窗口执行语义、D15 多库实跑降级档）落档到 ai-dev/design/nop-stream/sql-subset-and-semantics.md（未来交付物，不加反引号），每条含选项、结论、理由（代码证据）、负责人、裁定日期、受影响 WI；按 D1=(a) 更新 roadmap 的 re-scope 表与 R1 风险关闭标注，推进 WI0b → done。WI0d（D9-D12）依赖本计划结论。

## Current Baseline

- roadmap 前置裁定表现状（roadmap :74-90，表后 :92 为落档义务注）：
  - D1 待裁，三个选项，建议 (a) 终值语义降级；代码依据已列并经审查实证：`StreamRecord` 仅 value/timestamp/hasTimestamp（`nop-stream/nop-stream-core/.../streamrecord/StreamRecord.java:38-48`）；`WindowOperator.java:449` 对 ACCUMULATING_AND_RETRACTING 开期 fail-fast；`SinkConsistencyCapability.UPSERT_BY_KEY` 全仓零调用方；`<reduce>` 是逐条 emit 当前归约值（`StreamReduceOperator.java:87-106`，last-value-wins）。**D1 行结论列含 load-bearing 否定性约束原文：「注意不是 append-only——引擎无逐条追加式聚合输出面」——roadmap 改写该行时必须保留该约束。**
  - D3 **已裁定 owner 2026-09-30**：语法全集直增，方言差异不进 grammar，能力经 dialect `<features>` 下发，缺省不启用，翻译期未启用抛 `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE`——落档义务（写进 sql-subset-and-semantics.md）未履行，归本计划。裁定日期记 2026-09-30，落档日期记 2026-10-02。
  - D4 待裁，建议伪表函数 `TUMBLE(t, INTERVAL)`，RDBMS 目标 T1/T2/T3 三档（§3.4：T1 直通标注 stream-only；T2 近似且语义不等价必须标注，唯一仓库内证据是 `oracle.dialect.xml:184-186` date→trunc 模板，PG 侧 date_trunc/time_bucket 属外部事实未在本仓库验证；T3 拒绝给替代建议）。
  - D5 待裁，建议首版排除，进不支持清单。
  - D6 待裁，建议事件时间，与 WI13 共用设施。
  - D15 待裁，建议允许降级但必须在交付说明逐方言标注未实测，禁止无标注收口；CI 不传 `-Dnop.test.docker.enabled=true`（A2）。
- **D4 语法落点事实（2026-10-02 实测）**：分析窗口 OVER(...) 在 `nop-persistence/nop-orm-eql/model/antlr/BaseRule.g4`（表达式层，sqlWindowExpr）；**FROM 表源链不在 BaseRule.g4**——`nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4:131-133 sqlFrom`、`:135 tableSources_`、`:144-148 sqlTableSource`（两分支 `#SqlSingleTableSource_ex` / `#SqlSubqueryTableSource_ex` 在 `:145-146`）、`:150 sqlSingleTableSource`；TUMBLE 注释残迹在 `nop-persistence/nop-orm-eql/model/antlr/DMLStatement.g4:216-218`、`//groupWindow : regularFunction;` 残迹在 `:191-192`。伪表函数语法的落点裁定必须写 DMLStatement.g4 表源分支；AST 落点为 `model/ast/io/nop/orm/eql/ast/EqlAST.xjava`。
- Risks 节 re-scope 表（roadmap :209-215）已按 D1 三分支预写；D1=(a) 行：WI11 落为 `<reduce>` 映射加 last-value-wins 语义标注（不得标 append-only），R1 关闭。
- Q2（窗口 frame 重开/修正表达）缓解列写「随 D1/D6 一并裁」；D1/D6 裁定后 Q2 实质归属 WI12 设计（事件时间下 frame 重开按重算语义，无 RowKind 不引入修正），需在 roadmap Q2 行补括注指向落档。
- D9-D12 与 D1 耦合（roadmap :84-86）；D1=(a) 改变其前提：retract 路线关闭 → D10 放行前提消失、ACCUMULATING_AND_RETRACTING 维持 spec-only fail-fast、D12 的 per-transform parallelism 与 2PC 门禁不因 (a) 改变。本计划落档文档须含「对 D9-D12 的输入约束」小节。

## Goals

- 新建 ai-dev/design/nop-stream/sql-subset-and-semantics.md：六条裁定各含选项集、结论、理由与代码证据、负责人（仓库 owner，委托链同 WI0a）、裁定日期（D3=2026-09-30，其余五条=2026-10-02）与落档日期 2026-10-02、受影响 WI 清单；文末含「对 D9-D12 的输入约束」小节。
- D4 裁定明确：TUMBLE/HOP/SESSION 伪表函数语法面落在 DMLStatement.g4 的 `sqlTableSource`/`sqlSingleTableSource` 表源分支（新增伪表函数变体），AST 新增节点类型落 `EqlAST.xjava`；到流模型的映射为窗口 assigner 参数化（WI10 承接）；到 RDBMS 的映射按 T1/T2/T3 三档。
- D5 裁定附首版不支持清单草案（全局 ORDER BY / LIMIT、非等值 join、CDC retract 全链路、Flink 方言兼容——与 roadmap Non-Goals 一致），定稿归 WI16。
- D15 裁定明确降级协议：默认 H2 实跑；opt-in 多库（PG/Oracle/MySQL/MariaDB/MSSQL）可用时实跑；不可用时快照比对降级且交付说明逐方言标注「未实测」，禁止无标注收口。
- roadmap 更新（六行 + 四处辅助行）：前置裁定表 D1/D3/D4/D5/D6/D15 六行同步；R1 行标注关闭；re-scope 表 (a) 行标注生效、(b)/(c) 行标注未采纳；Q2 行补归属括注；WI0b 状态行 → done。D1 行改写**保留** append-only/retract 否定性约束原文（或改写为含该约束的短句 + 落档指针）。

## Non-Goals

- 不实施任何代码（WI1-WI5、WI10-WI12 等后续承载）。
- 不裁 D9-D12（WI0d 承接；本计划只落「输入约束」小节）。
- roadmap 改动限于：前置裁定表 D1/D3/D4/D5/D6/D15 六行、R1 行、re-scope 表标注、Q2 行括注、WI0b 状态行。
- 不改 §3.4 的外部数据库事实标注口径（「未在本仓库验证」保持原样）。

## Scope

### In Scope

- 新建 ai-dev/design/nop-stream/sql-subset-and-semantics.md（六条裁定全文 + D9-D12 输入约束小节）。
- roadmap `ai-dev/backlog/nop-stream-sql-roadmap.md`：前置裁定表 D1/D3/D4/D5/D6/D15 六行、Risks R1 行、re-scope 表标注、Q2 行括注、WI0b 状态行。
- 当日 ai-dev/logs/2026/10-02.md 更新。
- plan 文件自身维护。

### Out Of Scope

- D7/D8/D13/D14 落档（WI0c/WI8a）；D9-D12（WI0d）。
- 任何产品代码、grammar、xdef、dialect.xml 变更。
- `sql-subset-and-semantics.md` 之外的任何设计文档。

## Execution Plan

### Phase 1 - 六条裁定落档

Status: completed
Targets: ai-dev/design/nop-stream/sql-subset-and-semantics.md（未来交付物）

- Item Types: `Decision`

- [x] 新建文档：头部含负责人与裁定/落档日期与授权链；D1 裁定 (a) 终值语义降级——`<reduce>` last-value-wins 加模型语义标注，显式声明**非 append-only 非 retract**，理由引 StreamRecord 字段面、WindowOperator fail-fast、UPSERT_BY_KEY 零调用方、StreamReduceOperator 逐条 emit 证据；受影响 WI 重排按 re-scope 表 (a) 行记录；含「对 D9-D12 的输入约束」小节（retract 路线关闭→D10 放行前提消失、ACCUMULATING_AND_RETRACTING 维持 fail-fast、D12 per-transform parallelism 与 2PC 门禁不因 (a) 改变）
- [x] D3 落档（owner 2026-09-30 已裁，本计划补档，裁定日期 2026-09-30）：语法全集直增 + dialect features 开关 + `ERR_EQL_DIALECT_NOT_SUPPORT_FEATURE` 翻译期裁决，证据引 D3 行与 §3.3 分层机制
- [x] D4 裁定伪表函数：语法面落 DMLStatement.g4 的 sqlTableSource/sqlSingleTableSource 表源分支（引 :144-152 与 TUMBLE/groupWindow 注释残迹 :191-192,:216-218 为证据）、AST 节点落 EqlAST.xjava、流目标映射窗口 assigner、RDBMS 目标 T1/T2/T3 三档及「T2 全部语义不等价必须标注、外部数据库事实未在本仓库验证」口径
- [x] D5 裁定首版排除：全局 ORDER BY / LIMIT 进不支持清单（清单草案列出，定稿归 WI16）
- [x] D6 裁定事件时间：分析窗口流上按事件时间执行，与 WI13 共用 watermark/有序缓冲设施；Q2 归属记录（WI12 设计时处理，无 RowKind 下 frame 重开按事件时间重算语义，不新开裁定）
- [x] D15 裁定降级协议：H2 默认实跑 + opt-in 多库 + 快照比对降级须逐方言标注未实测、禁止无标注收口；Q1 保持未决（是否纳入 CI 不由本计划裁，WI3/WI5/WI20 按 opt-in 执行）

Exit Criteria:

- [x] 六条裁定各有：选项集、结论、理由与代码证据、负责人、裁定日期（D3=2026-09-30，其余=2026-10-02）、落档日期 2026-10-02、受影响 WI
- [x] D1 结论与 re-scope 表 (a) 行一致，全文无 append-only 误标；含「对 D9-D12 的输入约束」小节
- [x] No new test required: 纯文档变更，无产品代码
- [x] ai-dev/logs/2026/10-02.md 已更新

### Phase 2 - roadmap 同步与收口

Status: completed
Targets: `ai-dev/backlog/nop-stream-sql-roadmap.md`

- Item Types: `Proof`

- [x] 前置裁定表 D1/D4/D5/D6/D15 五行改「**已裁定 owner 2026-10-02**」+ 结论短句（D1 行保留 append-only/retract 否定性约束，短句含该约束 + 落档指针）；D3 行落档位置补记（裁定日期 2026-09-30 不变）
- [x] Risks R1 行标注已关闭（D1=(a) 已裁）；re-scope 表 (a) 行标注生效分支、(b)/(c) 行标注未采纳；Q2 行补括注（归属已记 sql-subset-and-semantics.md，WI12 设计时处理）
- [x] 独立子 agent closure audit（不同 task_id）：核验六条裁定与 roadmap 行、re-scope 表、WI0d 输入约束小节一致性 + 授权解释一致性；证据落 ai-dev/audits/nop-stream-sql/wi0b-closure-audit.md 与本 plan Closure 段（audit 首轮 FAIL：3 处 BROKEN_LINK error 属机械文本，修复后达成；实质内容全部 PASS）
- [x] audit 通过后 roadmap WI0b 状态行 `todo` → `done`（括注单层非嵌套、内部无右括号）；解析器核对 31 + 7：`node -e "import('./tools/mission-driver/src/roadmap-check.mjs').then(m=>{const r=m.parseRoadmapMarkdown(require('fs').readFileSync('ai-dev/backlog/nop-stream-sql-roadmap.md','utf8'));console.log('items',r.phases.filter(i=>!i.isMilestone).length,'milestones',r.phases.filter(i=>i.isMilestone).length)})"`（翻转后实测 items 31 milestones 7）
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0

Exit Criteria:

- [x] roadmap 六行 + R1 行 + re-scope 表标注 + Q2 括注 + WI0b 状态行全部同步；解析器命令实测输出 items 31 milestones 7
- [x] 独立 audit 证据已写入 plan Closure 段与 ai-dev/audits/nop-stream-sql/wi0b-closure-audit.md
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0（audit 指出的 3 error 及其余同类 12 处已全部修复，2026-10-02 实测退出码 0）
- [x] ai-dev/logs/2026/10-02.md 收口记录与 roadmap、plan 三处一致

## Closure Gates

> 纯文档计划：无产品代码变更，`./mvnw compile/test`、hollow-scan 按 guide 豁免条款删除。

- [x] 六条裁定全部落档且要素齐全（选项/结论/理由证据/负责人/裁定日期与落档日期/受影响 WI）
- [x] D1=(a) 与 roadmap re-scope 表 (a) 行、R1 关闭标注三处一致；D3 裁定日期 2026-09-30 三处（roadmap 行、落档文档、日志）一致
- [x] D1 行 append-only/retract 否定性约束在 roadmap 与落档文档两处均在
- [x] 无超授权内容（不裁 D9-D12、不改代码、roadmap 改动限于声明行）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] Anti-Hollow Check（文档版）：六条裁定均为实义内容且与 live 代码证据一致（audit 实测抽查 StreamRecord 字段面 :38-48、UPSERT_BY_KEY 零调用方、StreamReduceOperator :87-106、DMLStatement.g4 行号、oracle.dialect.xml :184-186 全部吻合）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/02-wi0b-subset-and-semantics-decisions.md --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- Q1（CI 是否纳入 `-Dnop.test.docker.enabled=true`）保持开放，归 WI3/WI5/WI20 执行时按 opt-in 处理。
- roadmap §3.1（:105）`StreamReduceOperator.java:89-107` 与实测 :87-106 的行号漂移系既有文本，audit 记录不改（append-only 原则），留 WI11 执行时顺带核对。

## Closure

Status Note: D1/D3/D4/D5/D6/D15 六条裁定全部落档 ai-dev/design/nop-stream/sql-subset-and-semantics.md，roadmap 六行 + R1 + re-scope 表 + Q2 + WI0b 状态行同步完成，解析器 31+7 复核通过。纯文档计划，无产品代码变更。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查、执行均不同 task）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi0b-closure-audit.md
- Evidence:
  - Phase 1 六条 checklist 与 Exit Criteria 逐条 PASS（audit 实测：落档要素齐全、D9-D12 输入约束三条在位、append-only 无误标）
  - 代码证据抽查全吻合（StreamRecord :38-48、WindowOperator :449 fail-fast、UPSERT_BY_KEY 零调用方、StreamReduceOperator :87-106、DMLStatement.g4 :131-150 行号、oracle.dialect.xml :184-186）
  - roadmap 一致性 PASS（六行 + R1 关闭 + re-scope 标注 + Q2 括注）；未越界 PASS（roadmap diff 仅 4 hunk、零代码改动）
  - 授权链 PASS（plan Owner 行、落档头部、sql-vision-conflict-resolution.md §2 三处一致；D3 裁定日期 2026-09-30 保留）
  - audit 首轮 FAIL 仅因 3 处 BROKEN_LINK error（机械文本：DMLStatement.g4 裸名与三个未来交付物反引号）；修复全部 15 处同类问题后 check-doc-links --strict 实测退出码 0
  - mission-driver 解析器翻转后实测 items 31 milestones 7
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/02-wi0b-subset-and-semantics-decisions.md --strict` 退出码 0

Follow-up:

- 见 Non-Blocking Follow-ups（Q1 开放、roadmap :105 行号漂移记录）；无 plan-owned 剩余工作
