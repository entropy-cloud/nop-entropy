# 2326 full-code-review 2.8 nop-stream-sql 首审

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 2.8（MA2，v6 新增）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 05/10/14/15 + 共享前缀）
> Related: 自身 roadmap `ai-dev/backlog/nop-stream-sql-roadmap.md`（31 WI 逐项 closure audit 为既有覆盖——本项为独立深审，不重复其结论）；nop-stream 40+ 轮既有审计（7.2 增量域）；2.2（EQL 编译器已审——SQL 编译先例对照）
> Skill 适配注记：维度 05 → 生成管线（TUMBLE grammar→`<sql>` xdef→SPI 装配链完整性）；维度 10 → XDSL/XLang 正确性（StreamSqlCompiler 编译语义）；维度 14 → 异步与状态（OverWindowOperator keyed MapState 持久化与跨 key 清理）；维度 15 → 类型安全（表达式编译/聚合类型）。算子实现面跨模块（OverWindowOperator 在 nop-stream-runtime、EquiJoinOperator/PerKeyOrderedBuffer 在 nop-stream-core）——审计范围与验证命令按受影响模块扩展。
> 只读审计（P0 走异步注入）。
> Draft Review: agent_705b771f 首轮 2 Major（维度 05 锚点全在声明外——TUMBLE grammar 实在 nop-orm-eql/stream.xdef 在 nop-xdefs/SPI 装配在 nop-stream-flow+两 beans.xml，In Scope 补三类只读锚点；31 WI prior art 未操作化——补盘点执行项+发现标注新增/推翻/重复三分类）+ 5 Minor（绿色基线锚改自身 roadmap 10-03 记录、直接协作类=一跳判定、Closure Gates 补 checklist 项、2.2 P0 波及面显式核对）全修；事实底座（计数/行数/路径/31 WI/命令）全部核实无误。

## Purpose

执行 roadmap 工作项 2.8：首审 nop-stream-sql（2026-10-02 入库，14 main Java 但行为密度高），产出报告 `2.8-stream-sql-first-audit.md`。

## Current Baseline

- `nop-stream/nop-stream-sql`：14 main/18 test（3090 行）。核心：StreamSqlCompiler 1044、StreamSqlExprCompiler 487、StreamSqlAggregation 381、SqlScalarXplPrinter 319、SqlRowAggregateSpec 165、RecordColumnAccess 162、StreamAggregatorFunctionResolver 126。
- 跨模块点名算子：OverWindowOperator（nop-stream-runtime）、EquiJoinOperator/PerKeyOrderedBuffer（nop-stream-core）。
- 既有覆盖：自身 roadmap 31 WI 逐项 closure audit（build 完成性已背书）——本项审行为语义与边界，不重复 WI 完成性核对。nop-stream 组 40+ 轮审计为既有资产（7.2 增量域）。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS（早于本模块 10-02 入库，不作本模块证据）；本模块锚=自身 roadmap WI17/21/22/24 的 2026-10-03 五模块全量测试零退化记录。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.8-stream-sql-first-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-2.8-<序号>`）+ 驳回台账（正文）+ 裁决。
- 编译正确性面（维度 05+10）：StreamSqlCompiler 全链（SQL 文本→AST→流拓扑）；TUMBLE grammar 与 `<sql>` xdef/SPI 装配面（生成管线完整性）；StreamSqlExprCompiler 表达式编译；SqlScalarXplPrinter 打印往返。
- 窗口与状态面（维度 14）：OverWindowOperator keyed MapState 持久化与跨 key 清理（roadmap 点名——状态泄漏/清理时机/水位交互）；StreamSqlAggregation 聚合语义。
- 双流 join 面（维度 14+15）：EquiJoinOperator/PerKeyOrderedBuffer 等值 join 语义（顺序保证/缓冲清理/迟到事件）。
- index 登记 + 独立 closure audit + roadmap 2.8 翻 done。

## Non-Goals

- nop-stream-core/runtime 全模块深审（40+ 轮既有审计覆盖；仅点名算子文件在范围）；nop-stream-sql 自身 roadmap 的 WI 完成性核对（已闭环）；流引擎 checkpoint 语义（既有审计域）。

## Scope

### In Scope

- `nop-stream/nop-stream-sql/**`
- 点名算子：`nop-stream/nop-stream-runtime/.../operators/windowing/OverWindowOperator.java` 及其直接协作类（判定规则：构造器/open/snapshotState 直接引用类型，一跳）、`nop-stream/nop-stream-core/.../operators/join/EquiJoinOperator.java`、`core/common/buffer/PerKeyOrderedBuffer.java` 及其直接协作类（同规则）
- **维度 05 只读锚点（生成管线跨模块，实测路径）**：TUMBLE grammar 在 nop-persistence/nop-orm-eql（`model/antlr/DMLStatement.g4:148-159` sqlTumbleTableSource、`SQL92Keyword.g4:650-652`、`BaseRule.g4:95`、AST 类 `orm/eql/ast/SqlTumbleTableSource.java`）；`<sql>` xdef 在 nop-kernel/nop-xdefs（`_vfs/nop/schema/stream/stream.xdef:100-111` StreamSqlModel）；SPI 装配在 nop-stream-flow（`flow/spi/ISqlStreamCompiler.java`、`flow/builder/StreamModelDslBuilder.expandSqlModel:255-320`）+ sql 模块内 `_vfs/nop/stream/sql/beans/app-sql-compiler.beans.xml`、`app-aggregator.beans.xml`

### Out Of Scope

- nop-stream 其余文件（7.2 增量域）——**除上述 flow SPI 装配面与点名算子外**；checkpoint 语义深审；自身 roadmap WI 完成性核对（prior art 盘点除外）。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: planned
Targets: nop-stream-sql + 点名算子

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：14 main 文件清单与职责表、TUMBLE/`<sql>` xdef 与 SPI 装配点、点名算子的状态字段清单、18 test 覆盖映射
- [ ] **prior art 盘点（防重复审计/防无据推翻）**：读自身 roadmap 31 WI 的审计结论与 FU 台账（FU-3 coders fail-fast/FU-8 accumulationMode/FU-9 union E2E 等 recorded residuals），对点名面（OverWindowOperator WI12/EquiJoinOperator WI13/StreamSqlCompiler WI17）建 prior-art 对照表；后续每条发现标注与既有裁定的关系：新增 / 推翻-须给新证据 / 重复-驳回
- [ ] 编译面子 agent（维度 05+10）：StreamSqlCompiler 全链 + TUMBLE grammar/xdef/SPI（生成管线完整性——xdef 声明↔解析↔装配闭合）+ StreamSqlExprCompiler/SqlScalarXplPrinter 往返 + **2.2 P0/P1 波及面显式核对**（StreamSqlExprCompiler 消费同一 EqlASTParser/AST——2.2 的否定编码族 P0 是否波及 stream SQL 表达式路径，逐条对照结论入报告）
- [ ] 窗口 join 子 agent（维度 14+15）：OverWindowOperator keyed MapState 持久化/跨 key 清理/水位交互 + EquiJoinOperator/PerKeyOrderedBuffer join 语义（顺序/缓冲清理/迟到事件）+ StreamSqlAggregation
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-2.8-{compile,window}.md`

Exit Criteria:

- [ ] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 每维度派独立复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总
- [ ] 统一 severity 判级；与 7.2（nop-stream 增量域）边界协调
- [ ] 编译链正结论收敛（一条总评）

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-2.8-{compile,window}-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `2.8-stream-sql-first-audit.md`；index 登记 2.8 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl nop-stream/nop-stream-sql,nop-stream/nop-stream-core,nop-stream/nop-stream-runtime -am`（无条件执行——验证按受影响模块扩展；如遇失败归因预存并登记）
- [ ] 独立子 agent closure audit；通过后 roadmap 2.8 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因为预存失败并在报告/log 登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且编译面+窗口 join 面全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 2.8 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定。修复工单归 R1.0/R1.x。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
