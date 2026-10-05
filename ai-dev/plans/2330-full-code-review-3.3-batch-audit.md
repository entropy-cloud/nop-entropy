# 2330 full-code-review 3.3 nop-batch 深审 a

> Plan Status: draft
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

Status: planned
Targets: nop-batch biz/dsl/core/exp/gen/service + app/meta/web 装配

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：In Scope 子模块文件与行数清单、BatchTaskModel DSL 结构清单、执行引擎类图、check2 20 条处置块状态盘点
- [ ] DSL 模型子 agent（维度 04+07）：BatchTaskModel 全链 + 模型校验 + :19/:50 标定（:125 归 3.4 仅撞车对照）+ exp/gen DSL 衔接面
- [ ] 执行链子 agent（维度 14）：BatchTask/chunk 循环状态机与并发（含 TestBatchTaskCheckpoint flaky 收编）+ 集群分区一致性（BatchTaskRunner+PartitionResolver）+ 分区/聚合 core 语义 + :75 深标定 + :101 仅标定 + service 与 app/meta/web 装配面机械盘点
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-3.3-{dsl,engine}.md`

Exit Criteria:

- [ ] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] check2 点名条目标定结论落盘（:19/:50/:75 深标定 + :101/:125 撞车对照；P2×6/P3×9 域撞车盘点）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + check2 标定独立验证
- [ ] 主 agent 汇总：判级统一、与 3.2（装饰器对照）/3.4 边界协调
- [ ] DSL 链正结论收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-3.3-{dsl,engine}-review.md`
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `3.3-batch-deep-audit-a.md`；index 登记 3.3 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl nop-batch/nop-batch-biz,nop-batch/nop-batch-dsl,nop-batch/nop-batch-core -am -Dtest='!TestBatchTaskCheckpoint' -Dsurefire.failIfNoSpecifiedTests=false`（排除 2.8 已登记 flaky；如遇其他失败归因预存并登记）
- [ ] 独立子 agent closure audit；通过后 roadmap 3.3 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因预存并登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且 DSL+执行链面全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 3.3 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；:75/:101 jdbc loader 深审归 3.4；修复工单归 R1.0/R1.x。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
