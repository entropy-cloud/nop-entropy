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

Status: completed
Targets: `IbJUnitRunner`（worker 模式）、`TestCommand`、`nop-ib.config.json`

- Item Types: `Feature`

- [x] runner worker 模式：stdin 拉式类队列（`{"cmd":"run","class":...}` → 结果行 / `shutdown`），EOF=崩溃由 parent 检测（协议行 `@@NOP-IB@@` 前缀防被测代码 stdout 日志污染——实测踩坑修复）
- [x] 通用重置层：System.properties 类前快照/类后恢复；`nop.autotest.*` 快照开关强制 false
- [x] resetter 反射契约：`resetBefore/resetAfter` 可选静态方法；类缺失/方法缺失跳过并告警；抛异常 → worker 退役
- [x] 配置：仓库根 `nop-ib.config.json`（`test.resetters/workerMaxClasses`）
- [x] 差分重跑：worker 内失败 → 新鲜 per-class fork 重跑一次，fresh 结果为准 + 污染告警
- [x] worker 轮换：≤workerMaxClasses 类强制退役；重置钩子异常立即退役；worker EOF 自愈重试（并发 spawn 竞争实测踩坑修复）

Exit Criteria:

- [x] nop-javac 全模块对照：复用模型 vs surefire = 77/0 失败集 parity
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿（19 tests）
- [x] `ai-dev/logs/` 已更新

### Phase 2 - Nop resetter + 盈亏判据

Status: completed
Targets: `nop-autotest-core`（`NopIbTestEnvResetter`）、`JUnitForkExecutor`

- Item Types: `Feature`

- [x] `NopIbTestEnvResetter.resetBefore` = 平台未初始化时 `CoreInitialization.reinitialize()`（nop-autotest-core 宿主，零 nop-ib 编译依赖）
- [x] `nop-ib.config.json` 登记 resetter（作用于有该类在 classpath 的模块；缺失=跳过+告警——nop-javac 等非 Nop 测试模块实测优雅跳过）
- [x] 盈亏判据：单类重置耗时 > 独立 fork 启动成本 → degrade 告警（reuseNotProfitable）写进 JSON；实测 Nop 模块 reinitialize 为 no-op-first 设计（测试自管 init/destroy，reuseForks 已验证兼容）

Exit Criteria:

- [x] nop-javac + nop-treesitter 对照 parity（77/0、629/0 vs surefire 77/631-2 skipped）
- [x] treesitter 长尾类入档：并发 spawn 竞争下的 EOF 已由自愈重试覆盖；逐类诊断无稳定挂死
- [x] `ai-dev/logs/` 已更新

### Phase 3 - 模块间并行 + 性能验收（G-A~G-D）

Status: in progress
Targets: `TestCommand`

- Item Types: `Feature | Proof`

- [x] `-pl`/affected 模块列表 M 路并行（总进程许可 = cores-1，M×W 约束）
- [x] G-B 切片墙钟：nop-ib vs mvn 12.0s，三次取中位数（nop-ib 10.0/10.3/10.4s → 中位数 10.3s，严格 < mvn 11.9s 复验值）
- [ ] G-C 全仓 sweep：部分完成——mvn -fae 基线 423s/43790 tests/0 fail/6 err（预存环境敏感）；nop-ib sweep 实测 362 模块报告/24017 tests 后卡于尾段 L3/AOP 大模块（差分重跑 fresh fork 与 L3 测试环境兼容性，详见 Evidence）
- [x] G-D 单模块 check 不回退（check 4.5s 与复用前持平）

Exit Criteria:

- [ ] G-B/G-C 墙钟严格 < mvn 基线（G-B 已达标入档；G-C 尾段卡点未解，plan 不得关闭）
- [ ] G-A 失败集 parity（G-B 切片 parity 达成 1671 tests 全绿；全仓预存失败 204 处 parity 分析未完成）
- [x] `ai-dev/logs/` 已更新

### Phase 4 - 通用化证明（S5）

Status: completed
Targets: `_tmp/nop-ib-sample-project/`（非 Nop 迷你 Maven 项目）

- Item Types: `Proof`

- [x] 迷你项目（2 个互依模块 + JUnit5 测试，mvn 3.9.12）用 nop-ib model refresh/index/test 全链路跑通：2 模块 2 测试全绿
- [x] resetter 契约在非 Nop 项目上按通用层工作（无 resetter=纯通用层）；泛化修复：repo root 发现支持任意 pom.xml 项目（mvnw/.mvn 优先）、ModelRefresher 支持裸 mvn on PATH

Exit Criteria:

- [x] E2E 记录入档
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [ ] G-A/B/C/D 全部达标且数字入档（G-C 不达标则 plan 不得标 completed）
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [ ] roadmap item 34 → done（独立 closure audit）
- [ ] `docs-for-ai/02-core-guides/incremental-build.md` 更新
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 通过
- [ ] `ai-dev/logs/` 已更新

## Deferred But Adjudicated

（无）

## Phase 3 Evidence（G-C 部分数据与卡点清单）

- mvn 基线：`mvnw test -T 1C -fae -Dmaven.test.failure.ignore=true` → **423s**，43790 tests（fail=0, err=6, skip=640，预存环境敏感失败：nop-core 资源缓存 mtime tick×3、debezium 虚拟线程 carrier×1 等）。
- nop-ib sweep（修复 5 个性能 bug 后）：362 模块报告 / 24017 tests / 204 failed（含预存 + parity 待分析）/ 55min——**速率 7.3 tests/s vs mvn 103.5 tests/s，尾段未达**。
- 尾段卡点（后续优化清单，全部有代码锚点）：
  1. L3/AOP 大模块（nop-biz、nop-auth-service、nop-wf-service 等 200+ 测试类）的测试环境在 fork 模型下兼容性未逐模块对照（surefire 专属 argLine/JVM 开关语义）；
  2. 差分重跑 fresh fork 的类级 10min 看门狗在预存挂死场景放大尾段墙钟（已并行化重跑，但单 fork 上限仍需按模块自适应）;
  3. maven-module 委托模块（无产物 L3）在 sweep 内串行取锁执行 mvn。
- 收敛路径：G-C 反超需完成尾段 L3 模块的逐模块对照门（IB-G3 方法）+ 差分重跑自适应超时；**plan 保持 in progress，不得在 G-C 达标前标记 completed**。

## Closure

Status Note: Phase 1/2/4 completed；Phase 3 in progress（G-B 达标，G-C 部分数据+卡点清单在档，不虚报达标）。
Completed: （Phase 3 达标后填写）

Closure Audit Evidence: （待 Phase 3 收口）
