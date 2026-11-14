# nop-ib Wave 6 — 测试 worker JVM 复用与执行模型通用化（roadmap item 34）

> Plan Status: active
> Last Reviewed: 2026-11-14
> Source: ai-dev/design/nop-incremental-build/06-junit-worker-reuse-and-generalization-design.md（WR-D1~D6）; ai-dev/analysis/2026-11/2026-11-14-nop-ib-test-jvm-reuse-feasibility.md
> Related: wave3-test-selection.md（per-class fork 现状，降级保留）

## Purpose

消除 per-class fork 的 JVM 启动开销（5168 类 × ~0.5s），以持久 worker JVM + 可插拔重置 SPI 取代，模块间并行补齐与 `mvn -T 1C` 的并行维度差；**验收 = 实测墙钟严格低于 mvn 同语料基线且失败集 parity**。

## Current Baseline

- per-class fork（Wave 3 阶段二）已落地：nop-javac 全模块 2.4s vs mvn 4.3s；整仓 sweep 输给 mvn（模块串行 + 5168 次 JVM 启动）。
- mvn 基线（2026-11-14 实测，16 核 zulu-26/macOS arm64）：4 模块切片（treesitter/jq/javac/stream-cep）`mvn test -T 1C` = **12.0s**，631 tests / 0 fail。
- 设计 06 落档：WR-D1~D6；重置契约 = 反射静态方法 `resetBefore/resetAfter`（零编译依赖）。

## Goals

- S1 worker 协议 + 通用重置层 + 配置面（`nop-ib.config.json`）。
- S2 Nop 平台 resetter（`reinitialize()` 钩子，宿主 nop-autotest-core）+ 差分重跑 + worker 轮换 + 盈亏判据。
- S3 模块间并行。
- S4 性能验收：G-A 模块级失败集 parity、G-B 切片墙钟 < 12.0s、G-C 全仓 sweep < mvn 基线、G-D AI 循环不回退。
- S5 通用化证明：非 Nop 迷你项目 E2E（model refresh + test 跑通）。

## Non-Goals

- 自研覆盖率、JUnit4 引擎、网络端点。

## Execution Plan

### Phase 1 - worker 协议与通用重置层

Status: planned
Targets: `IbJUnitRunner`（worker 模式）、`TestCommand`、`nop-ib.config.json`

- Item Types: `Feature`

- [ ] runner worker 模式：stdin 拉式类队列（`{"cmd":"run","class":...}` → 结果行 / `shutdown`），EOF=崩溃由 parent 检测
- [ ] 通用重置层：System.properties 类前快照/类后恢复；`nop.autotest.*` 快照开关强制 false
- [ ] resetter 反射契约：`resetBefore/resetAfter` 可选静态方法；类缺失/方法缺失跳过并告警；抛异常 → worker 退役
- [ ] 配置：仓库根 `nop-ib.config.json`（`test.resetters/parallelism/workerMaxClasses/forkTimeoutSeconds`）
- [ ] 差分重跑：worker 内失败 → 新鲜 per-class fork 重跑一次，fresh 结果为准 + 污染告警
- [ ] worker 轮换：≤workerMaxClasses 类强制退役；重置钩子异常立即退役

Exit Criteria:

- [ ] nop-javac 全模块对照：复用模型 vs surefire = 77/0 失败集 parity
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿（新增 worker 协议/重置层单测）
- [ ] `ai-dev/logs/` 已更新

### Phase 2 - Nop resetter + 盈亏判据

Status: planned
Targets: `nop-autotest-core`（`NopIbTestEnvResetter`）、`JUnitForkExecutor`

- Item Types: `Feature`

- [ ] `NopIbTestEnvResetter.resetAfter` = `CoreInitialization.reinitialize()`（nop-autotest-core 宿主，测试基础设施类）
- [ ] `nop-ib.config.json` 登记 resetter（作用于有该类在 classpath 的模块；缺失=跳过+告警）
- [ ] 盈亏判据：单类重置耗时 > 独立 fork 启动成本（实测标定）→ 该模块降级 per-class fork，JSON 记录原因

Exit Criteria:

- [ ] nop-javac + nop-core（L1）对照 parity
- [ ] treesitter 长尾类在 worker 模型下表现入档（对照或降级）
- [ ] `ai-dev/logs/` 已更新

### Phase 3 - 模块间并行 + 性能验收（G-A~G-D）

Status: planned
Targets: `TestCommand`

- Item Types: `Feature | Proof`

- [ ] `-pl`/affected 模块列表 M 路并行（总进程许可 = cores-1，M×W 约束）
- [ ] G-B 切片墙钟：nop-ib vs mvn 12.0s，三次取中位数
- [ ] G-C 全仓 sweep：先实测 mvn 全仓基线，再 nop-ib 全仓对照，失败集 parity
- [ ] G-D 单模块 check 不回退

Exit Criteria:

- [ ] G-B/G-C 墙钟严格 < mvn 基线（数字入档；不达标不得关闭本 plan，需继续优化或显式裁定）
- [ ] G-A 失败集 parity（差异逐条修复或降级登记）
- [ ] `ai-dev/logs/` 已更新

### Phase 4 - 通用化证明（S5）

Status: planned
Targets: `_tmp/nop-ib-sample-project/`（非 Nop 迷你 Maven 项目）

- Item Types: `Proof`

- [ ] 迷你项目（2 个互依模块 + JUnit5 测试）用 nop-ib model refresh/test 跑通
- [ ] resetter 契约在非 Nop 项目上按通用层工作

Exit Criteria:

- [ ] E2E 记录入档
- [ ] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] G-A/B/C/D 全部达标且数字入档（G-C 不达标则 plan 不得标 completed）
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [ ] roadmap item 34 → done（独立 closure audit）
- [ ] `docs-for-ai/02-core-guides/incremental-build.md` 更新
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 通过
- [ ] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

（无）

## Closure

Status Note: （待收口）
Completed: （待填）

Closure Audit Evidence: （待填）
