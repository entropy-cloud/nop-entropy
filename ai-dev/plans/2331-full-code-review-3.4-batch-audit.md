# 2331 full-code-review 3.4 nop-batch 深审 b

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 3.4（MA3）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 12/14/16 + 共享前缀）
> Related: owner doc `docs-for-ai/03-modules/nop-batch.md`；plan 2330（3.3 已审 biz/dsl/core 域——**3.4 交接**：:101 JdbcPageBatchLoader 游标标定已由 3.3 完成撞车对照〔游标成功后提交在位〕、:125 DaoBatchRecordHistoryStore already-fixed 撞车对照一致、TestBatchTaskCheckpoint flaky 根因已收编 3.3 P1-3.3-003 修复方案在案）；2.8 报告预存失败登记
> 只读审计（P0 走异步注入）；check2/nop-batch.md 为点名例外输入。
> Draft Review: agent_b81da9d4 首轮 2 Major（**:180 虚假交接**——「3.3 已标定 still-live 归 3.2/DSL 面」源自被推翻的 scratch 草稿，与 3.3 正式报告/check2 处置块/live synchronized:176 三方矛盾，改正为已修对照；**nop-batch-orm 7 类静默收缩**——纳入 JDBC/ORM 面子 agent+Goals+Closure Gate+P1-3.3-001 链路锚对照）+ 5 Minor（:180 补入执行项、P3 归属口径、3.3 既有发现撞车对照三条、sys 资源清单、flaky 措辞升级）全修；修后换 task_id 复审。

## Purpose

执行 roadmap 工作项 3.4：首审 nop-batch 后半——JDBC 读写事务、导入导出路径、批任务记录（jdbc/dao/orm/sys + api/codegen 契约），产出报告 `3.4-batch-deep-audit-b.md`。

## Current Baseline

- nop-batch 3.4 域子模块：jdbc 8/dao 23〔DaoTaskStateStore 不在此——在 nop-task〕/orm 7/sys 0/api 12/codegen 0 main。核心：JdbcBatchLoaderProvider 296、JdbcPageBatchLoaderProvider、DaoBatchStateStore 297、_gen 实体族。
- check2 3.4 域条目：:101 JdbcPageBatchLoader 游标推进（P1——**3.3 已完成撞车对照「游标成功后提交在位」，本项深审其分页语义**）；:125 DaoBatchRecordHistoryStore（P1——3.3 已标定 already-fixed）；:153 DefaultBizEntityImporter 返回 null（P2）；:180 ResourceRecordConsumerProvider 并发（P2——**已修复**：synchronized(state) :176 在位，3.3 复核无漂移——本项撞车对照确认即可）；:205 EtlTaskStateStore 非原子（P2）；:231 JDBC 密码 INFO 日志（P2）；:249 loadRetryPolicy 静默忽略（P2）+ P3×9。
- check2 :75/:19/:50 已由 3.3 标定 already-fixed（不动）。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS；排除 TestBatchTaskCheckpoint 口径沿用（该 flaky 已升级 P1-3.3-003 真产品缺陷，修复方案在案——排除仅因修复未落地）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.4-batch-deep-audit-b.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-3.4-<序号>`）+ 驳回台账（正文）+ 裁决。
- JDBC 读写事务面（维度 12/14）：JdbcBatchLoaderProvider/JdbcPageBatchLoaderProvider 分页语义（**:101 深审——3.3 已撞车对照「游标成功后提交」，本项审分页语义与重试交互**）；JDBC 写路径事务边界；JdbcBatchSupport 字段映射。
- 导入导出面（维度 12/16）：DefaultBizEntityImporter/ExportDbTool/ImportDbTool（check2 :153/:205/:231 域标定）；EtlTaskStateStore 并发语义。
- 批任务记录面（维度 14/16）：DaoBatchStateStore（297 行——状态映射/检查点持久化语义；**撞车对照：3.3 P3-3.3-013 方法级 synchronized 已立案**）+ batch 记录实体/history（:125/:153 撞车对照）+ api DataBean 对齐 + codegen 模板消费。
- api/codegen 契约面（维度 16）：api DataBean 与实体对齐抽查；codegen 模板消费。
- index 登记 + 独立 closure audit + roadmap 3.4 翻 done。

## Non-Goals

- biz/dsl/core 域（3.3 已审——含 P1-3.3-003 allOf 根因修复方案）；nop-task（3.2 已审）；TestBatchTaskCheckpoint flaky 根因（已收编 P1-3.3-003，本项定向测试排除沿用）。

## Scope

### In Scope

- `nop-batch/nop-batch-jdbc/**`、`nop-batch/nop-batch-dao/**`、`nop-batch/nop-batch-orm/**`、`nop-batch/nop-batch-sys/**`、`nop-batch/nop-batch-api/**`、`nop-batch/nop-batch-codegen/**`
- 只读输入：check2/nop-batch.md、`docs-for-ai/03-modules/nop-batch.md`、3.3 报告（交接对照）

### Out Of Scope

- nop-batch-biz/dsl/core/exp/gen/service 深审（3.3 已审）；nop-task/nop-wf；2.8 已登记预存失败根因。

## Execution Plan

### Phase 1 - 深挖（双 agent 并行）

Status: planned
Targets: nop-batch jdbc/dao/orm/sys/api/codegen

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：六子模块文件与行数清单（_gen 豁免清单；**sys 3 个 main 资源**：scheduler.yaml/sys-event-batch-consumer.job.yaml/non-broadcast-consumer.batch.xml——撞车对照 3.3 P2-3.3-011 分区批调度配置）、JDBC loader/provider 类图、check2 3.4 域条目处置块状态盘点
- [ ] JDBC 面子 agent（维度 12+14）：JdbcBatchLoaderProvider/JdbcPageBatchLoaderProvider 分页语义（**:101 深审**）+ JDBC 写路径事务 + JdbcBatchSupport 字段映射 + EtlTaskStateStore（:205 标定）
- [ ] 记录与契约面子 agent（维度 14+16）：DaoBatchStateStore 持久化语义（状态映射/检查点）+ batch 记录实体/history（:125/:153 撞车对照）+ api DataBean 对齐 + codegen 模板消费 + :231 密码日志/:249 loadRetryPolicy 标定
- [ ] 深挖迭代：有发现的子 agent 派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-3.4-{jdbc,records}.md`

Exit Criteria:

- [ ] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] check2 3.4 域条目标定结论落盘（:101 深审 + :125/:153/:180/:205/:231/:249 标定）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + check2 标定独立验证
- [ ] 主 agent 汇总：判级统一、与 3.3（交接对照）/3.2（JsonTool 先例）边界协调
- [ ] JDBC 链正结论收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-3.4-{jdbc,records}-review.md`
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `3.4-batch-deep-audit-b.md`；index 登记 3.4 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 定向验证锚点：`./mvnw test -pl nop-batch/nop-batch-jdbc,nop-batch/nop-batch-dao -am -Dtest='!TestBatchTaskCheckpoint' -Dsurefire.failIfNoSpecifiedTests=false`（排除口径沿用；如遇其他失败归因预存并登记）
- [ ] 独立子 agent closure audit；通过后 roadmap 3.4 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 定向测试 PASS，或失败已归因预存并登记
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除全量构建验证；以定向 test 运行为验证锚点。

- [ ] 报告落盘且 JDBC+记录契约面全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 3.4 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；修复工单归 R1.0/R1.x。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
