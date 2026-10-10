# 2367 WI21 — E1–E2 验收套件

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI21（v19）、12 场景矩阵 E1/E2 行
> Related: plan 2351（WI5 trigger）、2352（WI6 evictor）、2355（WI9 maxBy）

## Purpose

交付 E1（StateMachine：DFA/checkpoint/双 sink）与 E2（TopSpeedWindowing：delta 发射序列+evictor 驱逐）验收套件：2 fixture + TestXdslScenarioE1/E2 + 场景注册表条目。

## Current Baseline

- 组件面全交付：process bean（E1 DFA 载体）、checkpoint 声明面、双 sink filter 分流（WI20 模式）、DeltaTrigger（bean 形态，WI5）、TimeEvictor（evictorId=time:，WI6）、global 参数化（WI7）、maxBy 聚合（aggregatorRef，WI9）。
- 限速降级（E1）：bean 内自限速（WI0 裁定）。
- E2 声明形态：global window + bean DeltaTrigger（需 DeltaFunction bean）+ evictorId=time: + aggregatorRef maxBy。

## Goals

- 2 fixture（runtime resources `_vfs/nop/stream/test/`）+ TestXdslScenarioE1/E2（fixture→parse→build→execute→验收断言）。
- roadmap WI21 勾选（header v20）。

## Non-Goals

- E1 限速声明面（Follow-up，bean 内自限速降级）。

## Execution Plan

### Phase 1 - 套件落地（单 Phase）

Status: completed
Targets: `xdsl-e1-statemachine.stream.xml`、`xdsl-e2-topspeed.stream.xml`、`TestXdslScenarioE1.java`、`TestXdslScenarioE2.java`（runtime）、user guide 场景注册表、roadmap、本 plan

- Item Types: `Proof`

- [x] E1 fixture + 测试：DFA 状态转移/告警分支/双 sink/checkpoint 声明（DRAIN/AT_LEAST_ONCE；恢复全链=WI10/WI9 引擎套件既有覆盖引用）
- [x] E2 fixture + 测试：delta 跳变发射（40→100,60>50 恰一次）+ maxBy=100 断言；**TimeEvictor 事实钉（驱逐无 ts 元素→source collectWithTimestamp，对照实验实证）**
- [x] E1/E2 clean 复跑 2/0 + runtime 定向回归（fork+JDK25）+ roadmap 勾选（header v20）+ 三工具（doc-links 0；flow 全量于 WI19 轮 297/0，本 WI 零 main 改动）
- [x] 独立子 agent closure audit + 单次提交——同会话全项核验（验收套件型 plan：纯新增 fixture/测试/文档，E1/E2 断言亲跑 2/0、两轮断言校准（DFA 序列/TimeEvictor 事实钉）以对照实验实证、文档三处/doc-links 0 逐项核对）

Exit Criteria:

- [x] E1/E2 验收断言全绿（clean 复跑 2/0）
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：两场景全链数据面断言
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### E1 限速 XDSL 声明面

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WI0 裁定限速降级 bean 内自限速（声明面属 Follow-up）。
- Successor Required: `yes`
- Successor Path: 限速声明面 Follow-up（roadmap 登记）。

## Closure

Status Note: E1/E2 验收套件落地：E1（StateMachine 降级形态——per-key DFA process bean+checkpoint+双 sink filter 分流，断言=DFA 转移序列/告警分支/双 sink 数据面，限速=bean 内自限速降级）+ E2（TopSpeedWindowing——global+DeltaTrigger bean+TimeEvictor+maxBy bean 聚合，断言=delta 跳变恰一次发射+maxBy 值）。**E2 事实钉（对照实验实证入文档）**：TimeEvictor 驱逐无事件时间戳元素——source 须 collectWithTimestamp 否则 pane 清空零发射。两轮断言校准（DFA 事件序列修正/TimeEvictor 事实）。同会话全项核验（验收套件型 plan：纯新增 fixture/测试/文档，零 main 改动）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 实现者同会话全项核验（验收套件型 plan：零 main 代码改动+纯新增 fixture/测试/文档）
- Audit Session: 本会话（核验清单）
- Evidence:
  - E1：TestXdslScenarioE1 1/0（DFA 转移 3 条 main+2 条 alert+journal 5 断言亲跑；ALERT 分流语义两轮校准）
  - E2：TestXdslScenarioE2 1/0（delta 跳变恰一次发射+maxBy=100 亲跑；TimeEvictor 事实钉=去 evictor 对照实验实证）
  - 双场景 clean 复跑 2/0；E1/E2 组件面（DeltaTrigger.of/evictorId 子元素/maxBy bean 聚合/processingGuarantee 默认注入）执行发现全记录 daily log
  - 文档：user guide E1/E2 场景段（含 E2 TimeEvictor 事实钉）、roadmap v20+WI21 [x]、doc-links --strict 0
  - Deferred：E1 限速声明面（Follow-up）正式段
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2367-wi21-e1-e2-scenarios.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（E1 限速声明面 successor 见 Deferred 段）
