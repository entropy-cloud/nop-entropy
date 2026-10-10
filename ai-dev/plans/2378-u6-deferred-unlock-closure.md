# Plan 2378: U6 deferred-unlock roadmap 收口（全量回归 + 门禁补类 + roadmap 宣告收口）

- Status: completed
- Item Type: Closure
- Roadmap: `ai-dev/backlog/nop-stream-deferred-unlock-roadmap.md` U6
- Related: U1（plan 2371 勘误注记 + TestXdslScenarioE9 补钉，已收口）、U2（plan 2375 `<sqlTransform>`，已收口）、U3（plan 2376 限速声明面，已收口）、U4（plan 2377 tableId 声明面，已收口）、U5（plan 2368 勘误注记 + E3 钉启用，已收口）
- Owner Direction: 2026-10-10「执行 nop-stream-xdsl-full-coverage-roadmap.md 直到彻底完成；每个工作项按照 plan guide 拟制计划执行；每个计划执行完毕自动提交一次」

## Purpose

把 deferred-unlock roadmap 收口：U1–U5 全部闭环后，本 plan 做三件事——①全量回归（flow+sql+runtime+core，fork+JDK25）证据固化；②`compliance.yml` xdsl-scenarios CI 门禁补入 U2–U5 交付的新测试类（失败即阻塞）；③roadmap header 收口注记 + U6 勾选 + 独立 closure audit + 单次提交。

## Current Baseline（2026-10-10 代码实证）

- U1 ✅（当日落地）：plan 2371 勘误注记 + roadmap v27→v28 + `TestXdslScenarioE9.maxDelayDeclarationPathIsConsumedOnScenarioWindow`（E9 2/0）。
- U5 ✅（当日落地）：plan 2368 勘误注记 + E3 误判反转（期望 [3,3]、@Disabled 钉启用、e3WordSource prototype scope）+ roadmap v29。
- U2 ✅（当日落地）：plan 2375 `<sqlTransform>` 顶点 + SPI `compileTransform` + `expandSqlTransforms`；sql 模块钉 `TestSqlTransformFragmentCompile`/`TestSqlTransformE2E`（`nop-stream/nop-stream-sql/src/test/java/io/nop/stream/sql/compile/`）。
- U3 ✅（plan 2376，commit 339c4528da）：`TestRateLimitedSourceFunction`（core）/`TestSourceRateLimitDeclaration`（flow）/`TestSourceRateLimitE2E`（runtime）。
- U4 ✅（plan 2377，commit baf606c06a）：`TestDeclaredTableIdStamping`（core）/`TestSourceTableIdDeclaration`（flow）/`TestSourceTableIdE2E`（runtime）。
- 全量回归（本会话亲跑，HEAD=baf606c06a）：core 1765/0（1 skip 存量）+ flow 315/0 + sql 165/0 + runtime 1372（1 失败=`TestE2ETwoPhaseCommitSink.testTwoPhaseCommitSequence` 白名单时序抖动族，10-10 日志 WI15 条目在案，隔离复跑 3/0 绿）。
- `compliance.yml` xdsl-scenarios 作业（.github/workflows/compliance.yml:104-122）：现两步——runtime 12 场景类（E1-E12）+ flow 4 释放面钉类；构建步 `-pl nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am -DskipTests install`（sql/core 不在测试面）。
- 维持延期项（U1-U6 之外）：E6 async / 区间谓词 / file tailing / topN 目录化——触发条件不变，非本 plan 范围。

## Goals

- 新声明面钉进 CI 门禁：U2-U5 的 flow/runtime/core 新测试类补入 xdsl-scenarios 作业（失败即阻塞）；U2 的 sql 模块钉以最小代价入镜（构建步加 sql + 专步）。
- roadmap 宣告收口：header v3 注记（收口状态 + 维持延期清单重申）+ U6 勾选。
- 独立 closure audit APPROVE + 单次提交。

## Non-Goals

- 不新增/修改任何产品代码或声明面行为（纯门禁接线 + 文档收口）。
- 不处理维持延期项（E6 async/区间谓词/file tailing/topN 目录化——各带触发条件，owner 裁定通道）。
- 不改 xdsl-lint / bean-naming 等其他 compliance 作业。

## Execution Plan

### Phase 1: compliance.yml 门禁补类

Status: completed
Targets: `.github/workflows/compliance.yml`（xdsl-scenarios 作业）

- Item Types: `Feature`（门禁接线）+ `Proof`

- [x] runtime 步补 `TestSourceRateLimitE2E,TestSourceTableIdE2E`（U3/U4 元数据与限速 E2E）
- [x] flow 步补 `TestSourceRateLimitDeclaration,TestSourceTableIdDeclaration`（U3/U4 声明钉）
- [x] core 步新增 `TestRateLimitedSourceFunction,TestDeclaredTableIdStamping` + WI7 桥钉 `TestChangeMetadataBridgeAndGuard`（core 在 -am 构建闭包内，测试可直接跑）
- [x] sql：构建步加 `nop-stream/nop-stream-sql` + 新步跑 `TestSqlTransformFragmentCompile,TestSqlTransformE2E`（U2 片段契约钉入镜）

Exit Criteria:

- [x] YAML 语法有效（python yaml.safe_load 解析通过；9 个门禁类名与仓库真实类一一对应，各恰 1 处）
- [x] 门禁类清单 = U2-U5 新钉全集（不漏不多——sql 仅收 U2 两类，非 sql 全量）
- [x] 端到端等效验证：core 步（5/0+6/0+5/0）与 sql 步（8/0+2/0）本地等价命令亲跑绿；runtime 步两类（2/0+2/0）与 flow 步两类（5/0+3/0）本会话 U3/U4 收口时已亲跑绿

### Phase 2: roadmap 收口与文档

Status: completed
Targets: `ai-dev/backlog/nop-stream-deferred-unlock-roadmap.md`、daily log

- Item Types: `Proof`

- [x] roadmap header 增 v3 收口注记（U1-U6 全闭环 + 维持延期清单重申 + 立项依据裁定全部兑现）
- [x] U6 勾选（独立 audit APPROVE 后——agent_18054aa1 APPROVE）
- [x] daily log 条目

Exit Criteria:

- [x] roadmap 状态面一致：U1-U6 全 `[x]`，无第二状态面
- [x] header 注记与工作项行、daily log 三处一致（audit 文本一致性核对 PASS）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（audit 复跑）

### Phase 3: 独立 closure audit + 提交

Status: completed
Targets: 本 plan、收口提交

- Item Types: `Proof`

- [x] fresh-session 子代理审计（plan guide Closure Audit Rule）：核对全量回归证据、门禁补类清单、roadmap 一致性、U1-U5 收口提交在案
- [x] audit 证据写入本 plan Closure 段 + Status: completed + 单次提交

Exit Criteria:

- [x] audit APPROVE 证据在 plan Closure 段
- [x] `node ai-dev/tools/check-plan-checklist.mjs` + `scan-hollow-implementations --severity high`（nop-stream）退出码 0（audit 复跑）
- [x] 收口提交仅含本 plan 相关文件（显式路径暂存——audit 圈定 diff 面恰为本 plan 4 文件）

## Closure Gates

- [x] U1-U5 收口提交在案（339c4528da=U3、baf606c06a=U4；U1/U5 随 WI 场景 plan 提交、U2=9e2d6e8165，git log 可核——audit 逐一验证）
- [x] 全量回归证据固化：core 1765/0（1 skip）+ flow 315/0 + sql 165/0 + runtime 1372（1=白名单时序抖动，隔离绿）
- [x] compliance.yml 补类完成且类名全部真实存在（25 个引用类逐一核实，各恰 1 处，无 @Disabled）
- [x] roadmap U1-U6 全勾选 + header v3 收口注记
- [x] 独立子 agent closure audit APPROVE + 证据写入 plan
- [x] 纯门禁+文档收口：无产品代码变更（audit 按 plan 路径圈定 diff 面核实，恰 4 文件）；No new test required beyond gate wiring（门禁步本身即测试清单变更）
- [x] check-doc-links / check-plan-checklist / scan-hollow 三工具退出码 0（audit 复跑）

## Deferred But Adjudicated

（无——维持延期项已在 roadmap「维持延期」节带触发条件登记，不属于本 plan scope）

## Closure

Status Note: deferred-unlock roadmap 宣告收口——U1（maxDelay 勘误）、U2（`<sqlTransform>`）、U3（限速声明面）、U4（tableId 声明面）、U5（E3 误判反转）、U6（本收口：门禁 9 类补入 4 步失败即阻塞 + 全量回归固化 + header v3）全部闭环；维持延期 4 项（E6 async/区间谓词/file tailing/topN 目录化）触发条件不变。至此 nop-stream-xdsl-full-coverage-roadmap 主线（v27 收口 + v28/v29 勘误）与其 deferred-unlock 支线全部彻底完成。纯门禁+文档收口，无产品代码变更。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_18054aa1-5d72-4f5c-a6ae-f7ff2a83aa12（独立 fresh-session Explore 审计）
- Audit Session: agent_18054aa1-5d72-4f5c-a6ae-f7ff2a83aa12
- Evidence:
  - Phase 1 PASS：xdsl-scenarios 5 步 25 个引用类逐一核实存在且各恰 1 处（无 @Disabled/docker 依赖）；YAML safe_load 通过；core 步与 sql 步 audit 以 CI 等价两步法（构建步 -am install → 定向 test）亲跑 16/0 与 10/0 BUILD SUCCESS。
  - Phase 2 PASS：roadmap v3 注记在案（:13）；U1-U5 [x]/U6 勾选前状态符合门控设计；维持延期表 4 项未动；U3/U4 提交引用逐一验证。
  - 回归 PASS：surefire 汇总 core 1765/0（1 skip）/flow 315/0/sql 165/0/runtime 1372（0 fail、10 环境门控 skip）；runtime 1 抖动与 WI15 条目白名单交叉核对通过。
  - Scoped diff PASS：plan-2378 未提交面恰 4 文件（compliance.yml/roadmap/plan/log），零 nop-stream 产品代码未提交改动。
  - 工具 PASS：doc-links --strict=0、scan-hollow --severity high=0、check-plan-checklist --strict=0（audit 三工具复跑）。
  - 审计发现处置：Finding-1（standalone 跑 sql/core 步对旧 ~/.m2 会 CNF——CI 同作业构建步先行刷新，自洽）记录在案无需动作；TEXTUAL_INCONSISTENCIES 两项均为时序注记，已随本收口提交归位。
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2378-u6-deferred-unlock-closure.md --strict` 退出码 0（关闭前复跑确认）

Follow-up:

- no remaining plan-owned work（deferred-unlock roadmap 无遗留工作项；维持延期项归 owner 裁定通道，非 plan-owned）
