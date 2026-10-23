# 2495 CRA Phase 8.1 — R1-00 P0 收尾确认批（3 项已修 P0 回归确认）

> Plan Status: active
> Last Reviewed: 2026-10-24
> Source: roadmap 工作项 8.1（R1-00，batch-proposal §二 R1-00）；执行基线 @ 2e9dcdd059
> Related: plan 2494（Phase 8 展开完成）；本批为全部修复批的基线门禁（先行于 R1-01~R4-33）
> Draft Review: R1 agent_f25cb79e（1 Blocker[F1 focused 命令缺 -Dsurefire.failIfNoSpecifiedTests=false]+3 Major[F2 基线锚点 2.5→4.3/F3 impl 叶核对命令/F4 owner-doc 裁定缺失]+4 Minor——全量采纳；其 F6 日期质疑系审查环境日历误差，执行日为 2026-10-24）

## Purpose

确认 campaign 三项 P0（1.2-C6-001 Kafka producer close / 4.3-C6-001 下载成功路径 sink.close / 2.5-C6-002 搬迁成功路径停链）的修复与回归测试在当前 HEAD 在位且通过，P0 即时通道零悬空——作为 Phase 8 全部修复批的基线门禁。本批为验证型批次，预期零产品代码变更。

## Current Baseline

- cra-index「P0 发现追踪」表 3 项全部「已修复」：1.2（commit 8dcfa4cd0c，回归测试 TestKafkaTwoPhaseCommitSinkLifecycle——修复新增 3 例，类共 9 例）、4.3（commit 2a2d546fc0，TestFileDownloadSinkClose fd 计数断言、**回退实测必红**）、2.5（commit 22dc8bd3df，TestMigrationSwitchStopsChains 2 例、**收敛停链断言回退实测必红**）——必红证据：4.3/2.5 为回退实测探针；1.2 为修复 plan 演绎记录（回退即编译失败+断言失败，未实测探针）。本批不重复探针，引用式裁定据此成立。
- 三项 P0 修复后各模块套件全绿记录在案（1.2 BUILD SUCCESS / 4.3 两模块 33 测试全绿 / 2.5 52 测试全绿）。
- 自 4.3 P0 修复（2a2d546fc0）后 campaign 各 WI 均为审计/文档提交，无产品代码变更（`git diff 2a2d546fc0..HEAD` 排除 ai-dev/docs/docs-for-ai 为空，R1 实测）——预期模块测试仍然通过。
- 4.3 P0 修复仅限 impl 叶（足迹 nop-http-client-jdk/nop-http-client-okhttp，nop-http-api/nop-rpc-api/nop-integration-api 三叶零触碰）声明维持。

## Goals

- 三项 P0 回归测试在当前 HEAD focused 运行全绿：`TestKafkaTwoPhaseCommitSinkLifecycle`（nop-stream）、`TestFileDownloadSinkClose`（nop-network）、`TestMigrationSwitchStopsChains`（nop-persistence）。
- 4.3 `scan-hollow-implementations.mjs --module nop-network --severity high` 退出码 0。
- cra-index P0 台账零 open 遗留确认；roadmap 8.1 → done。

## Non-Goals

- 不做新的 mutation probe（必红证据引用修复时记录）。
- 不触碰任何产品代码；不重跑全模块套件（修复时全绿记录在案，本批 focused 确认）。

## Scope

### In Scope

- 三个回归测试类的 focused 运行（`./mvnw test -pl <模块> -am -Dtest=<类>` 形式）
- `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-network --severity high`
- cra-index P0 台账核对、roadmap 8.1 状态、daily log、本 plan

### Out Of Scope

- 产品代码与测试代码修改（如 focused 运行暴露失败，按 plan guide 停止并升级为 Fix plan——那将是新计划）
- 其他批次工作项

## Execution Plan

### Phase 1 - 前置核对与 focused 运行

Status: planned
Targets: 三个模块测试、scan-hollow、cra-index

- Item Types: `Proof`

- [ ] 前置核对：cra-index P0 台账 3 项均「已修复」零 open；三个回归测试类文件在位（grep 路径登记）；三笔修复 commit 可溯——`8dcfa4cd0c`（1.2）/`22dc8bd3df`（2.5）/`2a2d546fc0`（4.3）
- [ ] focused 运行（**必须带 `-Dsurefire.failIfNoSpecifiedTests=false`**——`-am` 联动上游模块无匹配测试时 surefire 3.2.2 默认失败，本仓三处在案先例）：
  - `./mvnw test -pl nop-stream -am -Dtest=TestKafkaTwoPhaseCommitSinkLifecycle -Dsurefire.failIfNoSpecifiedTests=false`
  - `./mvnw test -pl nop-network -am -Dtest=TestFileDownloadSinkClose -Dsurefire.failIfNoSpecifiedTests=false`
  - `./mvnw test -pl nop-persistence -am -Dtest=TestMigrationSwitchStopsChains -Dsurefire.failIfNoSpecifiedTests=false`
  - 三处 BUILD SUCCESS 且目标类 Tests run >0（Kafka 类预期 Tests run: 9 = 存量 6 + 修复新增 3）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-network --severity high` 退出码 0
- [ ] **4.3 impl 叶声明维持取证（F3）**：①`git show 2a2d546fc0 --name-only --format=""`——足迹仅 nop-http-client-jdk/nop-http-client-okhttp（+docs），三 API 叶零触碰；②`git diff --name-only 2a2d546fc0..HEAD -- . ':(exclude)ai-dev' ':(exclude)docs' ':(exclude)docs-for-ai'`——期望空 = 冻结维持
- [ ] 每项运行结果（测试数/退出码）登记本 plan 执行记录

Exit Criteria:

- [ ] 三个 focused 测试全绿（BUILD SUCCESS + 目标类 Tests run >0 且 Failures=0）
- [ ] scan-hollow 退出码 0
- [ ] P0 台账零 open 遗留；测试文件/commit 前置核对通过；impl 叶两项取证通过
- [ ] `ai-dev/logs/` 对应日期条目已更新（与 Phase 2 合并记录）

### Phase 2 - 收口

Status: planned
Targets: roadmap、cra-index、daily log、本 plan

- Item Types: `Proof`

- [ ] roadmap 8.1 行 Status todo → done（closure audit PASS 后）；cra-index P0 追踪表无需改动（已闭环，仅核对）；daily log 记录
- [ ] **owner doc 裁定（F4）**：零产品代码变更 → No owner-doc update required（nop-stream-connectors.md / nop-network.md 无 API 或行为变化需同步；nop-persistence 无 owner doc 缺口声明维持原状）
- [ ] 独立子代理 closure audit（focused 运行证据、scan-hollow 退出码、台账核对、impl 叶取证、门禁）
- [ ] checklist 全勾 + `check-plan-checklist.mjs --strict` 退出码 0；`check-doc-links.mjs --strict` 退出码 0

Exit Criteria:

- [ ] closure audit PASS 记录在本 plan Closure 段
- [ ] 两个门禁退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 三项 P0 回归测试当前 HEAD 全绿（focused 运行证据；必红引用修复时记录：4.3/2.5 回退实测、1.2 演绎记录）
- [ ] 4.3 scan-hollow 门禁退出码 0
- [ ] P0 台账零 open 遗留；4.3 impl 叶声明维持（两项 git 取证在案）
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] 两个门禁退出码 0
- [ ] 本计划零产品代码变更
- [ ] 受影响 owner docs：No owner-doc update required（显式裁定，见 Phase 2）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

Follow-up:

- <<或者明确写 no remaining plan-owned work>>
