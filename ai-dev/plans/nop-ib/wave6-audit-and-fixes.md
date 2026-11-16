# Wave 6 — nop-ib 深度审计 + 开放式审计 + 全量问题修复

> Plan Status: active
> Last Reviewed: 2026-11-16
> Source: [nop-ib-roadmap.md](../../backlog/nop-ib-roadmap.md) Wave 6（2026-11-16 立项）；方法论: `ai-dev/skills/invariant-loop-audit-prompt.md`
> Related: [external-project-support-campaign.md](external-project-support-campaign.md)（缺陷 23–31 来源）、[05-nop-ib-engine-design.md](../../design/nop-incremental-build/05-nop-ib-engine-design.md)、[06-junit-worker-reuse.md](../../design/nop-incremental-build/06-junit-worker-reuse.md)

## Purpose

对 nop-ib 引擎做一轮系统化深度审计（6A）+ 对抗性开放式审计（6B），把全部发现收口为修复或显式裁定（6C），使 roadmap Wave 6 的 Exit Criteria 全部成立。

## Current Baseline（2026-11-16，live repo 核对）

- 引擎主源码 ~34 文件于 `nop-tools/nop-ib/src/main/java/io/nop/ib/`：CLI 入口 `NopIbMain.java`（model/status/plan/compile/test/check/serve/bench/explain/cache）；`model/EffectivePomParser.parseSurefire` 消费 argLine/skipTests/includes/excludedGroups/excludes/environmentVariables/systemPropertyVariables（:236-296）——**未解析** forkCount/reuseForks/parallel/threadCount/groups/additionalClasspathElements/workingDirectory/runOrder。
- 派发与 worker：`cli/TestCommand.java`（rerun-gate :511-533，moduleRerunTimeoutSeconds 默认 1800）、`junit/JUnitWorkerPool.java`（per-class watchdog :355-383，debugPerClass :142）、`junit/JUnitForkExecutor.java`（batch watchdog :59,211,236；forkError/forkStdoutTail :281-285 已采集未透出为 warnings）。
- 锁：`lock/BuildLock.java`，与 mvnq MvnqLock 双向互通（TestBuildLock 3 用例）；Maven fallback 路径不外层取锁（TestCommand :747,788）。
- 测试：nop-ib 55/55 绿（campaign plan :168/:178 记录，2026-11-16）。
- 已知 Deferred 遗留：缺陷 29（vintage Enclosed 双跑 zeebe-gateway-grpc 36 vs mvn 18）、fork abort 134（flink-agents-runtime）、engine 大模块 rerun 超时覆盖（1800s knob）、forkError/forkStdoutTail 未透出、`-pl` 多模块语法（`NopIbMain.java:84` 仅单 `-pl <arg>`）。
- 12 已测项目清单以 [external-project-support-matrix.md](external-project-support-matrix.md) 实测 Runbook 节 + campaign plan Closure 段落为准（OpenPDF/liteflow/pdfbox/spring-batch/Activiti/flink/liquibase/zeebe/shardingsphere/mvnd/spring-batch 外部面/jsr352 等已实测项，执行时以 campaign plan 记录的具体 12 项为准）。
- Phase 2 外部项目重跑前置：先 `./mvnw install -pl nop-tools/nop-ib -am -DskipTests` 产出 target classes + `target/ib-cp.txt`。
- 外部项目资格矩阵：44 项目枚举完毕（7 parity / 2 preexisting / zeebe 已 deep-measured / 其余 todo 或 excluded）；runbook 固化于 matrix `实测 Runbook` 节。

## Goals

- 6A 五条定向审计（引擎全路径 invariant / 模型事实完备性 / 派发三向 / 失败语义 / 锁并发）每条产出 file:line 级证据清单，发现 100% 落 Fix 或 Deferred-But-Adjudicated。
- 6B 对抗审计：变异测试假绿=0、surefire 配置维度 fuzz 每差异一钉、12 已测项目零回退矩阵全绿。
- 6C 已知遗留收口：缺陷 29、fork abort 134 诊断、engine rerun 超时覆盖、forkError/forkStdoutTail 透出、`-pl` 语法统一，每项修复+钉或显式裁定。
- 文档收口：design 05/06 原地更新至 live baseline。

## Non-Goals

- Wave 7 性能实验（E1–E6）与 bench harness 固化（后续 plan）。
- Wave 8 mvnd 对比（后续 plan）。
- JUnit4-only 项目承接（owner 裁定维持）。
- 新增功能能力（本 plan 只审计+修复+钉）。

## Scope

### In Scope

- `nop-tools/nop-ib`（main + test）修复与回归钉。
- `EffectivePomParser` surefire 解析面扩展（6A-2 发现的缺口逐项处置）。
- `ai-dev/design/nop-incremental-build/05、06` 原地更新；roadmap Wave 6 状态块勾选。
- 12 已测项目重跑（只读实测，不改 ~/sources 项目内容）。

### Out Of Scope

- ~/sources 下外部项目代码修改。
- CI 基础设施（Deferred 30 维持）。

## Execution Plan

### Phase 1 — 6A 深度审计（定向，静态+运行时）

Status: planned
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/**`、`ai-dev/analysis/`

Item Types: `Proof | Fix`

- [ ] 6A-1 引擎全路径 invariant 审计：`model refresh → plan → compile → index → dispatch → fork/worker → differential rerun → fallback` 逐环节 invariant 核对，每条 invariant 给 file:line 证据或缺陷登记（invariant-loop 方法论）
- [ ] 6A-2 模型事实完备性审计：EffectivePomParser 解析面 vs surefire 实际消费面差异清单，系统性枚举 `runOrder/groups/parallel/reuseForks/systemPropertyVariables ${...} 引用/additionalClasspathElements/additionalClasspathDependencies/profile 条件激活`，每项裁定（解析/回退/不等价登记）
- [ ] 6A-3 派发面三向审计：UNDER/OVER/IB-ONLY 在 ≥3 个已测项目类级差分复算（复用 nop.ib.debugPerClass + surefire-reports 口径），全量差异登记表归零
- [ ] 6A-4 失败语义审计：watchdog/rerun-gate/worker-crash/firstError 剪枝行为矩阵（每格：发生条件→可见输出→oracle 影响），消灭"结果被覆盖/丢弃"路径（spring-batch-core 1800s 覆盖、jsr352 firstError 剪枝先例）
- [ ] 6A-5 锁与并发审计：`.mvn/build.lock` 与 worker 池/fallback/多模块 sweep 交互矩阵；-pl 多模块并行 sweep 输出互斥证明
- [ ] 审计发现全部落 `ai-dev/analysis/`（时效性，带 Status）并逐条分流 Fix / Deferred-Adjudicated

Exit Criteria:

- [ ] 每条审计有 file:line 级证据清单文件（`ai-dev/analysis/2026-11-*-wave6-audit-*.md`）
- [ ] 发现项台账 100% 分流：Fix（进 Phase 3）或 Deferred（带 Why Not Blocking + successor）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 6B 开放式对抗审计

Status: planned
Targets: `~/sources` 已测项目、`nop-tools/nop-ib` 回归钉

Item Types: `Proof | Fix`

- [ ] 6B-1 独立审计 agent（fresh session，≥2 轮迭代）以"证明 nop-ib 会假绿/漏测/错判"为目标开放式探索；变异测试：对已知绿项目注入变更验证 ib 报红，对已知红项目验证 ib 不吞错
- [ ] 6B-2 surefire 配置维度组合变异 fuzz（变异→钉→复验纪律），每个差异一个回归钉
- [ ] 6B-3 跨项目不变式：12 已测项目全量重跑（每个 ≥1 模块），产出零回退矩阵

Exit Criteria:

- [ ] 变异测试全部按预期报红/不吞错（假绿=0），记录在 analysis 文档
- [ ] fuzz 差异钉入 `src/test/java/io/nop/ib/`（每差异一钉）
- [ ] 零回退矩阵（12 项目 × ≥1 模块）结果表入库
- [ ] 对抗审查两轮无新 Blocker
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 6C 修复收口

Status: planned
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/**`、`src/test/java/io/nop/ib/**`

Item Types: `Fix`

- [ ] 6C-1 缺陷 29：vintage Enclosed/Parameterized 容器双跑去重（zeebe-gateway-grpc 派发面=mvn 执行面）
- [ ] 6C-2 forkError/forkStdoutTail 诊断透出到 run warnings（fork abort 134 可定位）
- [ ] 6C-2b fork abort 134（flink-agents-runtime）根因诊断：修复或 Deferred-Adjudicated（带 root cause 证据 + successor），不得仅凭诊断透出收口
- [ ] 6C-3 engine 大模块 rerun 超时覆盖修复：rerun-gate 超时不得覆盖/丢弃已有结果，超时必须显式出现在结果与退出语义
- [ ] 6C-4 `-pl` 多模块语法统一（`a,b` / `-pl a -pl b` / `:artifactId`）
- [ ] 6C-5 Phase 1/2 审计发现的全部 Fix 项修复 + 回归钉
- [ ] 6C-6 不能修项逐条 `Deferred But Adjudicated`（本 plan Deferred 节）
- [ ] 6C-7 零回退矩阵复核：Phase 3 行为性修复（缺陷 29/rerun-gate/-pl）落地后，重跑受影响项目（至少 zeebe-gateway-grpc + flink-agents + spring-batch）确认矩阵不回退；全矩阵以 Phase 2 结果 + Phase 3 增量复核合并为收口证据（时间盒：单项目复核超 2h 即冻结为 watch-only 登记）

Exit Criteria:

- [ ] 每个 Fix 有对应回归钉（JUnit 5 测试），钉先红后绿（或变异探针验证）
- [ ] zeebe-gateway-grpc 类级差分复验：IB 派发=mvn 执行面（缺陷 29 归零或裁定）
- [ ] Phase 2 的 fuzz 回归钉在 Phase 3 修复后复验（由 `./mvnw test -pl nop-tools/nop-ib` 全绿覆盖）
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 文档收口 + closure

Status: planned
Targets: `ai-dev/design/nop-incremental-build/05、06`、roadmap Wave 6 状态块

Item Types: `Follow-up`

- [ ] design 05/06 原地更新至 live baseline（解析面、失败语义、诊断字段）
- [ ] roadmap Wave 6 checklist 全勾、Status 置 done；`docs-for-ai`/runbook 对齐
- [ ] 文档链接检查 `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [ ] 独立子 agent closure audit APPROVED，Evidence 写入本 plan Closure 节

Exit Criteria:

- [ ] design 05/06 与 live code 无矛盾（抽查 3 处）
- [ ] roadmap Wave 6 Exit Criteria 四条全部可对应本 plan 证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出 0
- [ ] closure audit evidence 落 plan Closure 节 + daily log
- [ ] 提交一次（git commit）

## Closure Gates

- [ ] 6A 每条审计有 file:line 证据；发现 100% 落 Fix 或 Deferred（无 open 残留）
- [ ] 6B 变异测试假绿=0；对抗审查两轮无新 Blocker
- [ ] 12 项目零回退矩阵全绿
- [ ] 已知遗留（缺陷 29/fork134/超时覆盖/诊断透出/-pl 语法）逐项修复或裁定
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [ ] 受影响 owner docs（design 05/06、roadmap）同步
- [ ] 独立子 agent closure audit APPROVED，evidence 落 plan
- [ ] Anti-Hollow 检查：修复路径运行时连通（每 Fix 有钉）

## Deferred But Adjudicated

（执行中按发现填充；每条须 Classification + Why Not Blocking + Successor）

## Non-Blocking Follow-ups

- （空）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence: （收口时填写）
