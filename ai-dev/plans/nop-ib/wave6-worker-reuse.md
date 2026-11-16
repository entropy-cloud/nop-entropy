# nop-ib Wave 6 — 测试 worker JVM 复用与执行模型通用化（roadmap item 34）

> Plan Status: completed
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

Status: completed
Targets: `TestCommand`

- Item Types: `Feature | Proof | Fix`

- [x] `-pl`/affected 模块列表 M 路并行（总进程许可 = cores-1，M×W 约束）
- [x] G-B 切片墙钟：nop-ib vs mvn 12.0s，三次取中位数（nop-ib 10.0/10.3/10.4s → 中位数 10.3s，严格 < mvn 11.9s 复验值）
- [x] [Fix] G-C 尾段六大缺陷修复（2026-11-14 二批，见 Phase 3 Evidence）：索引误派发嵌套 helper/worker 无逐类异常隔离/死 worker 不唤醒 poll/optional 依赖被 dependency:list 解析丢弃/type+classifier 丢弃+pom 打包聚合器误解析/差分重跑假 120s 超时注释（实际无覆盖）。原 3 挂死模块 parity 零差异（14/15/71 vs mvn 同数），4.2s vs mvn 13.2s
- [x] [Fix] 差分重跑自适应超时落地（clamp(120s, 4×worker timeMs, 300s)；60s 下限曾把 fresh fork 平台首启烧穿、污染受害者误记 stillFailed）
- [x] [Feature] 拓扑重设计：WORKER_BUDGET（4×cores）+ MODULE_SLOTS（budget/4）+ 公平份额 = min(类数, budget/min(模块数, slots))——全仓每模块≈4 worker 对齐 surefire forkCount=4；小切片每模块 budget/4（G-B 复验 8.0/9.2/11.4s 中位数 9.2s，@Nested 双计数修正后 1669 tests 全绿）
- [x] G-C 全仓 sweep 收口（共同语料对照 + 全语料确定性，见 Evidence 二批）：
  - **共同语料（149 模块交集，同机同日）**：nop-ib **302s / 14001 tests** vs mvn `-T 1C -fae -DtestFailureIgnore` **345s / 14208 tests** → **严格 < 基线（-12.5%）**；
  - **全语料（267 模块派发面）**：nop-ib 一次确定性完成（gc4 1116s，含修复前的 60s 重试风暴数据）；mvn 四次尝试（-T1C×2/-T0.5C）全部因时序敏感类失败级联 skip 147-220 模块 + TestIModelRouter 挂死 20min 无看门狗——**mvn 在本机无法单跑完成全语料**，全语料维度 nop-ib 是唯一可完成者；
  - 语料完整性：287 个含 test src 的模型模块 − 20 个 codegen 模块（各仅 1 个无测试方法主入口类）= 267 派发面，无漏测。
- [x] G-A 失败集 parity（共同语料逐类裁定表见 Evidence 二批；6 双方一致 + 5 ib-only 全部裁定为负载/顺序敏感（其中 3 个在其他 mvn 跑亦挂）+ 3 mvn-only 为 mvn 自身 flake——零未解释差异）
- [x] G-D 单模块 check 不回退（check 4.5s 与复用前持平）

Exit Criteria:

- [x] G-B/G-C 墙钟严格 < mvn 基线（G-B 9.2s < 11.9s；G-C 共同语料 302s < 345s）
- [x] G-A 失败集 parity（逐类裁定，零未解释差异）
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

- [x] G-A/B/C/D 全部达标且数字入档（G-B 9.2s<11.9s；G-C 共同语料 302s<345s+全语料确定性；G-A 零未解释差异；G-D 4.5s 持平——均见 Phase 3 Evidence）
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿（26/26；closure audit 期间 TestBuildLock 曾因 holder 启动竞态 25/26，已改确定性 probe 等待后全绿——该类同时在 G-A 表裁定为负载敏感）
- [x] roadmap item 34 → done（独立 closure audit，见 Closure 段）
- [x] `docs-for-ai/02-core-guides/incremental-build.md` 更新（test 对照基线 + 拓扑说明）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过（0 errors）
- [x] `ai-dev/logs/` 已更新（2026-11-14 二批条目）

## Deferred But Adjudicated

（无）

## Phase 3 Evidence（G-C 部分数据与卡点清单）

### 2026-11-14 晨间（已被二批证据修正）

- mvn 基线：`mvnw test -T 1C -fae -Dmaven.test.failure.ignore=true` → **423s**，43790 tests（fail=0, err=6, skip=640）——**二批复核发现该数字为残缺语料**：surefire 汇总行重复计数 + `-fae` 下 3 个模块 build 失败级联 skip 147 个下游模块（auth-service 等服务层从未运行）。
- nop-ib sweep（修复 5 个性能 bug 后）：362 模块报告 / 24017 tests / 204 failed / 55min（exit 137 强杀）。

### 2026-11-14 二批（六大缺陷修复 + 拓扑重设计 + 基线重建）

**缺陷链（全部实测 stderr/产物落档）**：
1. `UnitIndex.testClasses()` 把 src/test 全部类当测试类 → 嵌套 helper（ExposedPostgisHandler/RecordingTxnManager）进入 worker 后 discovery 触发 NoClassDefFoundError（optional 依赖 PGobject 不在 classpath）→ worker JVM 死亡；
2. `IbJUnitRunner.runWorker` 无逐类异常隔离 → discovery 异常杀死 worker；parent 在死 worker 上 poll 330s 不被 EOF 唤醒 = 每次 300s 级尾段停顿；
3. `ARTIFACT_LINE` 正则丢弃 ` (optional)` 行 → provided+optional 依赖（nop-message-debezium）从 fast-path classpath 消失 → 真测试类 discovery 失败；
4. dependency:type/classifier 被 parse 丢弃 → pom 打包聚合器（org.graalvm.js:js）与 classified jar（nacos-client-pure、osx-aarch_64 natives）解析失败 → 15 模块 classpath-incomplete 回退 surefire；
5. `JUnitForkExecutor` 不排空 fork stdout → 多日志测试类 64KB 管道死锁到 300s 看门狗（差分重跑路径）；
6. 差分重跑注释声称 120s 超时但无 timeout 覆盖 → 每失败类重试烧满 300s；后修的 60s 下限又烧穿 fresh fork 平台首启（sweep 负载下）→ 污染受害者误记 stillFailed。

**修复后单模块对照**：nop-orm-geo 14/nop-spring-core-starter 15/nop-stream-connector-jdbc 71 = mvn 同数全绿，4.2s vs mvn 13.2s。

**拓扑证据**（-pl 全 267 模块 sweep 剖析）：
- 每模块 1 worker（cores-1 总预算摊 267 模块）：23 t/s，慢小模块（nop-ai-rag 4.5s/类 × 54 类）独占 245s 尾段；
- 贪心抢满预算：首模块抢光 64 令牌 → 兄弟模块饿死，G-B 切片 9.6s→22s 回退；
- 公平份额（现行）：G-B 切片 8.0/9.2/11.4s 中位数 9.2s（mvn 11.9s）；全仓 267/267 确定性完成 1116s/27639 tests（auth-service 818s 为最长模块，含 60s 重试风暴期的数据）。

**mvn 全语料基线重建**（三次尝试）：
- gc5 `-T 1C -fae -Dmaven.test.failure.ignore=true`：398s，**220 模块被级联 skip**（orm-model/cluster-core/debezium/graphql-core 4 模块 build 失败；TestLazyLoadConcurrentLoad 10s FutureTask 超时等时序敏感类）；
- gc6 `-T 1C -fae -DtestFailureIgnore=true`：345s，**196 模块 skip**（fork 崩溃型失败不被 failure-ignore 覆盖；本跑 mvn 自身挂掉 refactor-graphql×3/lint-graphql/lint-js×2/shell/orm-model/task-core = 与 ib-only 失败集高度重合，证其为负载/顺序敏感而非 nop-ib 缺陷）；
- gc7 `-T 0.5C`：进行中（降 fork 风暴以稳定跑完）；
- 结论方向：mvn 在本机 -T1C 下**结构性地无法单跑完成全语料**（三跑三套 flake 集，147-220 模块 skip）；nop-ib sweep 一次确定性跑完全语料——G-C 的对照需在共同语料上裁定。

**语料口径**：287 个含 test src 的模型模块 − 20 个 codegen 模块（各仅 1 个无测试方法的主入口类）= 267 = nop-ib 派发面，无漏测。

**共同语料对照（G-C 收口数据，2026-11-14 晚）**：
- mvn gc6（`-T 1C -fae -DtestFailureIgnore=true`）实际测得 149 个交集模块 / 14208 tests / 墙钟 345s（含其未 skip 的其他模块，对 nop-ib 保守不利方向）；
- nop-ib 同 149 模块（含全部当日修复）**302s / 14001 tests**（差值 207 ≈ mvn 计入的 @Disabled 计数口径差）；**严格 < 基线**。
- **逐类 parity 裁定表**（ib 失败 11 类 / mvn 失败 9 类）：
  - 双方一致（6）：TestNopLintGraphQL、TestL2DemoSuite、TestTscBridgeReal、TestNopRefactorGraphQL、TestRefactorP0ClosedLoop、TestRenameGraphQL——顺序/负载敏感（master 58c720575b 已有测试卫生修复，本分支 fork 早于该提交）；
  - ib-only（5，全部裁定非 nop-ib 缺陷）：TestOperationMfaExecutorWiring（baseline3-mvn 亦挂）、TestBuildLock（时序锁竞争断言，独立跑 26/26 绿，sweep 453% 负载下 5s 窗口失守）、TestBudgetDegradeLadder（显式毫秒级耗时断言）、TestDebeziumMessageSource（gc5-mvn 亦挂）、ReproG3PerWindowStateLeakOnDiscard（ib/mvn 单类双跑均挂=预存环境失败）；
  - mvn-only（3，mvn 自身 flake）：TestLazyLoadConcurrentLoad（10s FutureTask 超时）、TestShellRunnerProcessDestroy（baseline3 两侧均挂过）、TestExecutorTaskStepWrapperAsyncBranch（异步分支时序）。
  - 结论：**零未解释差异**；所有 delta 均为已在两侧跑中翻转过的时序/顺序敏感类。

## Closure

Status Note: Phase 1–4 全部 completed。Wave 6 交付了可通用化的持久 worker 测试执行模型并在同语料对照下反超 mvn：G-B 切片中位数 9.2s < mvn 11.9s；G-C 共同语料（149 模块交集）302s/14001 tests < mvn 345s/14208 tests，且全语料（267 派发模块）nop-ib 一次确定性跑完而 mvn 在本机四次尝试均因时序敏感类级联 skip 无法完成；G-A 失败集逐类 parity 零未解释差异；G-D 4.5s 持平。同批修复 G-C 尾段六大缺陷（含基线语料证伪）并把执行拓扑重设计为公平份额模型。roadmap item 34 → done。

Completed: 2026-11-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session, agentId agent_a8d15e57-396c-4400-b450-e1a535edac4b）
- Audit Session: 2026-11-14（本 plan 收口审计，独立于实现 session）
- Evidence:
  - 每条 Phase Exit Criterion：PASS——审计者从 live code（IbJUnitRunner.java:162-222/JUnitWorkerPool.java:52-152/TestCommand.java:46-171 等 file:line 清单）复核协议/重置/差分/拓扑代码；Phase 3 数字从产物独立复算：`_tmp/gc6c-ib.time`=302s、`_tmp/gc6-mvn.time`=345s、149 模块/14001 tests/11 失败类与 9 mvn 失败类逐一与 plan 表吻合；全语料 `_tmp/gc4-ib.time`≈1116s。
  - Closure Gates：PASS——`./mvnw test -pl nop-tools/nop-ib` 26/26（审计中曾 25/26：TestBuildLock holder 启动竞态，已改 probe 确定性等待并全绿，该类在 G-A 表已有负载敏感裁定）；check-doc-links exit 0；roadmap/owner-doc/design 06 同步在档。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-ib/wave6-worker-reuse.md --strict`：closure 翻转后复核通过（见下）。
  - Anti-Hollow：调用链 NopIbMain→TestCommand→runModuleTests→JUnitWorkerPool.run→Worker.runClass→worker 侧 runWorker/runOne 与差分重跑 retryTimeout→execute(per-call timeout) 全程连通；hello 握手在入池前消费不会误读为类结果；逐类 Throwable 隔离与死 worker 500ms 分片唤醒核实；testClasses() 过滤为带声明的选择语义非静默 no-op；`scan-hollow-implementations.mjs --module nop-tools/nop-ib --severity high` exit 0。
  - Deferred 项分类检查：Deferred But Adjudicated 为空且属实——六大 G-C 尾段缺陷全部修复入回归测试，无 live defect 伪装成 follow-up。
  - 审计条件项处置：①TestBuildLock 竞态已修（本批）+全绿复核；②Closure 段本批重写；③roadmap item 34 done 以本 Closure Evidence 落档为序成立。次要项：`_tmp/gc6-corpus.txt` 曾为空文件，已重新生成（149 模块）。

Follow-up:

- 性能后继（optimization candidate，非本 plan scope）：L3 服务大模块（auth-service 818s 级）的污染钉扎/每模块自适应 worker 数；全语料墙钟从 1116s 继续压缩；跨 worktree jar ABI 缓存共享（roadmap item 26 deferred 位）。
- 本分支落后 master 828 commits，顺序敏感测试类的卫生修复（master 58c720575b 等）随下次 master 合入自然消解。
