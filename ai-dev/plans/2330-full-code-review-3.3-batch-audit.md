# 2330 full-code-review 3.3 nop-batch 深审 a

> Plan Status: completed
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.3（MA3）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 04/07/14 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-batch.md`；check2/nop-batch.md 20 条：P1×5（:19 BizExportTaskBuilder NPE〔biz〕/:50 FileBatchSupport NPE〔dsl〕/:75 AsyncFetchPartitionDispatch 线程泄漏〔**core**〕/:101 JdbcPageBatchLoader 游标推进〔jdbc〕/:125 DaoBatchRecordHistoryStore 空实现〔**dao**〕）+ P2×6 + P3×9。**3.3/3.4 分割（按模块归属，对抗审查勘正）**：:19/:50/:75→3.3 深标定（biz/dsl/core——:75 是 core 分区派发机制，本项分区语义核心）；:101/:125→3.4 深标定（jdbc/dao 域，本项仅撞车对照）。全部带 fix-ai-check 处置块——以 live code 为准标定。
> 只读审计（P0 走异步注入）；check2/nop-batch.md 为点名例外输入。
> Draft Review: agent_b0d451a6 首轮 3 Blocker（**P1 标定分割与子模块归属错位**：:75 在 core 非 jdbc、:125 在 dao——正确分割 :19/:50/:75→3.3、:101/:125→3.4；**集群分区一致性零覆盖**——BatchTaskRunner+PartitionResolver 在 dsl 域；**子模块清单与 roadmap 矛盾**——exp/gen 归 3.3、api 归 3.4、service 失踪须收编）+ 2 Major（TestBatchTaskCheckpoint flaky 归属改判收编本项、Goals 引用不存在的 ChunkTask/timeout 装饰器/step 概念——改真实类名）+ 3 Minor（15 子模块/P2×6/P3×9 勘正、api 移出、装配面纳入）全修；修后换 task_id 复核。

## Purpose

执行 roadmap 工作项 3.3：首审 nop-batch（S 级零专项审计）前半——DSL 任务模型→执行链（biz/dsl/core 域），产出报告 `3.3-batch-deep-audit-a.md`。3.4（后续项）审 jdbc/dao/exp/gen 域。

## Current Baseline

- nop-batch：252 main（15 Maven 子模块，29217 行；deploy/model 非模块）。3.3 范围子域（对齐 roadmap）：nop-batch-biz/dsl/core/exp/gen/service + app/meta/web 装配。
- check2/nop-batch.md 20 条：P1×5（:19 BizExportTaskBuilder NPE/:50 FileBatchSupport NPE/:75 AsyncFetchPartitionDispatch 线程泄漏/:101 JdbcPageBatchLoader 游标推进/:125 DaoBatchRecordHistoryStore 空实现）+ P2×6 + P3×9。**范围分割：:19/:50/:125（biz/dsl/DSL historyStore 面）归 3.3 点名复核；:75/:101（jdbc loader 面）3.3 只标定、3.4 深审；其余 P2/P3 撞车对照**。全部带 fix-ai-check 处置块——以 live code 为准标定。
- 2.8 报告预存失败登记：TestBatchTaskCheckpoint.testFailFastStopsSiblingThreads flaky（nop-batch-core——**归属改判收编本项**：fail-fast 取消竞态是执行链并发审计直接对象，列入执行链子 agent 必查证据；定向测试排除口径沿用）。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS；nop-batch 组在基线内。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.3-batch-deep-audit-a.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-3.3-<序号>`）+ 驳回台账（正文）+ 裁决。
- DSL 任务模型面（维度 04+07）：BatchTaskModel DSL 全链（模型→编译→执行拓扑：task/loader/processor/consumer 四类节点——batch.xdef 无 step 概念）；模型校验完备性。
- 执行链面（维度 14）：BatchTask/chunk 循环执行模型状态机与并发（IBatchChunkContext/fail-fast/取消竞态——含 TestBatchTaskCheckpoint flaky 用例收编审计）；**集群分区一致性（roadmap 点名）**：BatchTaskRunner+PartitionResolver 的 partitionRange 解析、跨节点一致性、与 dispatcher/historyStore 去重交互；retry（RetryBatchLoader/AbstractRetryBatchConsumer）/并发写入防护（RateLimitConsumer）语义。
- check2 点名标定：:19/:50/:75 逐条 live 深标定（实测优先）；:101/:125 仅撞车对照（3.4 域）。
- index 登记 + 独立 closure audit + roadmap 3.3 翻 done。

## Non-Goals

- jdbc/dao/exp/gen/web/app 域深审（3.4）；nop-task 装饰器深审（3.2 已审）；TestBatchTaskCheckpoint flaky 根因（3.4 域登记在案）。

## Scope

### In Scope

- `nop-batch/nop-batch-biz/**`、`nop-batch/nop-batch-dsl/**`、`nop-batch/nop-batch-core/**`、`nop-batch/nop-batch-exp/**`（DSL 衔接面）、`nop-batch/nop-batch-gen/**`（DSL 衔接面）、`nop-batch/nop-batch-service/**`、app/meta/web 装配配置
- 只读输入：`ai-dev/audits/check2/nop-batch.md`、`docs-for-ai/03-modules/nop-batch.md`

### Out Of Scope

- nop-batch-jdbc/dao/orm/sys/api/codegen 深审（3.4，与 roadmap 3.4 清单对齐）；nop-batch-exp/gen 的非 DSL 编译面（exp/gen 归 3.3 仅限其与 DSL→执行链衔接处）；nop-task 装饰器；2.8 已登记预存失败根因。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: completed
Targets: nop-batch biz/dsl/core/exp/gen/service + app/meta/web 装配

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [x] 机械基线：In Scope 子模块文件与行数清单、BatchTaskModel DSL 结构清单、执行引擎类图、check2 20 条处置块状态盘点
- [x] DSL 模型子 agent（维度 04+07）：BatchTaskModel 全链 + 模型校验 + :19/:50 标定（:125 归 3.4 仅撞车对照）+ exp/gen DSL 衔接面
- [x] 执行链子 agent（维度 14）：BatchTask/chunk 循环状态机与并发（含 TestBatchTaskCheckpoint flaky 收编）+ 集群分区一致性（BatchTaskRunner+PartitionResolver）+ 分区/聚合 core 语义 + :75 深标定 + :101 仅标定 + service 与 app/meta/web 装配面机械盘点
- [x] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [x] 产物 `_tmp/fcr-3.3-{dsl,engine}.md`

Exit Criteria:

- [x] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] check2 点名条目标定结论落盘（:19/:50/:75 深标定 + :101/:125 撞车对照；P2×6/P3×9 域撞车盘点）
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + check2 标定独立验证
- [x] 主 agent 汇总：判级统一、与 3.2（装饰器对照）/3.4 边界协调
- [x] DSL 链正结论收敛

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-3.3-{dsl,engine}-review.md`
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `3.3-batch-deep-audit-a.md`；index 登记 3.3 行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [x] 定向验证锚点：`./mvnw test -pl nop-batch/nop-batch-biz,nop-batch/nop-batch-dsl,nop-batch/nop-batch-core -am -Dtest='!TestBatchTaskCheckpoint' -Dsurefire.failIfNoSpecifiedTests=false`（排除 2.8 已登记 flaky；如遇其他失败归因预存并登记）
- [x] 独立子 agent closure audit；通过后 roadmap 3.3 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 定向测试 PASS，或失败已归因预存并登记
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [x] 报告落盘且 DSL+执行链面全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 3.3 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；:75/:101 jdbc loader 深审归 3.4；修复工单归 R1.0/R1.x。）

## Closure

Status Note: 只读审计计划收口。报告 `3.3-batch-deep-audit-a.md` 落盘：不通过附 P1×3（orm-reader `<eql>` 解析未接线静默全表加载/processor order 声明失效/allOf 左优先掩蔽 fail-fast 根因终态误持久化 KILLED 不可重启——TestBatchTaskCheckpoint flaky 收编升级为真产品缺陷）+P2×11/P3×14（28 条全保留，降 0 驳 0）。**check2 五条 P1 标定全部 already-fixed**（:19/:50/:75/:125 修复+Locator/Finish 测试复跑绿、:101 归 3.4 对照一致）；:75-:363 处置无回归。closure audit 首轮 FAIL（B1：报告 P3 段 2 条 DSL P3 标题脱漏+拆分标注 7+7 失实+G.2 悬空引用）→ 补录重排+聚焦复审二轮残留修正 → **SATISFIED**。定向测试排除 flaky 后三模块 BUILD SUCCESS。
Completed: 2026-10-05

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_53df37f6-78e8-47b1-928c-531f32df1af7（首轮 FAIL，B1 补录 2 条 DSL P3+拆分勘正）+ agent_2b6e5cbd-e1a0-46d9-9f7c-85c19e943254（聚焦复审指出 G.2 悬空引用+族④ 008/008 重复）+ agent_89cfb315-7fba-4e86-bed4-38f880a13a12（最终聚焦复核 **SATISFIED**：P3 编号连续无重复、分族无悬空、G.2 同步终编号）
- Evidence:
  - 计数四方勾稽：P0=0/P1=3/P2=11/P3=14=28 ↔ 报告实点（P1 表 3/P2 逐数 11/P3 9+5=14）↔ index 3.3 行 ↔ 复核保留数（DSL 21+ENG 7=28）零缺陷
  - P1 台账：3 行与 index 3 行一一对应；修复分族终编号统一
  - check2 台账：五条 P1 全部 already-fixed（:19/:50/:75/:125 修复+Locator/Finish 测试实跑绿；:101 归 3.4 对照一致）；flaky 收编改判 P1-3.3-003 三处一致
  - 判级调和：P1 两条 DSL（公开声明+文档承诺→执行面忽略且静默出错）与 ENG（持久化终态误分类数据损坏类）横向核查通过
  - 锚点抽验 7/7 live 命中（OrmBatchSupport:33/ModelBasedBatchTaskBuilderFactory:258-269/BatchTask:229-234/DaoBatchStateStore:259-266+:108-113/FileBatchSupport:169-171/BatchTaskRunner:42-47）
  - 定向测试：batch-test.log BUILD SUCCESS（排除 TestBatchTaskCheckpoint flaky 口径）
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2330-full-code-review-3.3-batch-audit.md --strict` 退出码 0
  - Anti-Hollow：只读审计计划，无新增组件/代码路径；验证以定向 test 全绿 + 探针为锚
  - Deferred 项分类检查：无 deferred；P1×3 经 index「P1 发现汇总」进 R1.0，无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work（P1×3+P2×11+P3×14 修复归属 R1.0 四分族；:101/:125 归 3.4 深标定；TestBatchTaskCheckpoint 根因修复方案在案；G.2 移交两项在报告移交注记）
