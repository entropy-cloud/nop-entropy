# 2426 WI10 共享 checkpoint 内核抽取（路线 a：先钉后拆 + 裁定归一）

> Plan Status: completed
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md Phase D WI10；ai-dev/design/nop-stream/checkpoint-engine-convergence.md §4.1/§4.3/§4.4（owner 确认事件：设计档交付后用户下达「继续」，按 2423 记录的确认机制视为 gate 通过）
> Related: plan 2423（WI9 裁定）；下一可用编号承接 plan（创建时落号，当前 ≥2431——并发会话持续占用 2427-2430）（CC-06/CC-17 独立承接，本 WI 差分绿后同波次执行）

## Purpose

按 WI9 路线 (a) 抽取共享 checkpoint 内核（savepoint 物化下沉 + 终态预算命名配置）并落地 6a 裁定归一（coordinator 级 failover 重建 manifest-first 校验门）。**存储格式/序列化字节/epoch manifest 结构零变更**。E1 先钉后拆：差分 harness 对现状跑绿留档，存量 checkpoint fixture 首次入库。CC-06/CC-17 切至独立 下一可用编号承接 plan（创建时落号，当前 ≥2431——并发会话持续占用 2427-2430）（差分绿后、本波次内独立 commit——roadmap「独立 commit 在差分绿之后」顺序约束不变）。

## Current Baseline

2026-10-11 实测（commit 4443843acc 工作树；行号锚经对抗审查逐条复核，含勘误）：

- 差分 harness 分布式腿蓝本：`nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/deploy/TestRemoteDeployCheckpointWiringE2E.java`（~250 行手工拓扑：LocalMessageService + 2 个 in-process TaskManager + StreamControlRpcServer/Proxy + CheckpointCoordinator + JobCoordinator `setRemoteDeployMode(true)` + `assignTasks()` + `startPeriodicCheckpoints(150ms)`；断言已覆盖 manifest 落盘、2PC exactly-once、**restore-on-deploy**）——这是唯一 checkpoint 启用的 ungated 分布式先例（`TestDistributedE2EIntegration` 三用例均未启用 checkpoint，不可作蓝本）。
- 本地腿入口：`StreamExecutionEnvironment.execute()` 经 :406-409 路由（checkpoint 启用时优先 `executeWithCheckpointEngine` :514-518 → GMCE）。**无 `executeWithCheckpoint` 公共 API**。
- 下沉面实测：savepoint 物化薄切片（storage 覆盖 GMCE :374-376、KeyGroup 归属戳 :454（已委托 `RescaleStateAssembler`）、路径返回 :455/:460）；终态预算三处 GMCE **:433/:549/:791**（沿自设计档的 :432/:548/:790 已漂移 1 行）读 `config.getCheckpointTimeout()`；:449/:563 两处 future.get 等待预算亦读同字段（**等待预算 ≠ 触发预算，本 WI 不动**）。
- 6a 实测：`restoreLatestEpochManifest`（CheckpointCoordinator :1739）、`restoreFromCheckpoint`（:1169，内含 counter 推进与 `latestCompletedCheckpoint` 置位 :1173）、`advanceCheckpointIdCounterAfterRestore`（:1200）均为 **CheckpointCoordinator public 方法**（非 GMCE 私有）；GMCE 私有的只是编排序列 :1054-1113。JC LEADERSHIP 分支 :2020-2052 自消费 `getLatestCheckpoint()`（:2026/:2055）。既有 failover 测试（`TestJobCoordinatorFailoverRestore`）的 storage 只 `storeCheckPoint` 不写 manifest → 走 raw 路径；裸 coordinator 不设指纹 → 指纹校验 skip。
- abort 处置：本地 handler 为 legacy 单参 `Consumer<Long>`（无 reason 输入，CheckpointCoordinator :347-348 注释自认 legacy）；分布式判定点仅 `reason.startsWith("Timeout")`（JC :2568）——**共享 AbortPolicy 契约已裁弃**（伪抽象，见 Deferred），DIV-02 冻结。
- 存量 checkpoint fixture：test resources 无（roadmap 断言成立，本 WI 首固化）。
- 测试基线（WI7/WI8 收口钉）：core 1963/0/1、runtime 1195/0/10（coordinator 测试族 25 文件 = 24 Test* + CoordinatorTestSupport）、flow 315/0/0。
- 平台版本 2.0.0-SNAPSHOT；本计划编号 2426 为当前最大+1（2424/2425 并发会话占用）。

## Goals

- **E1 差分钉（先于一切内核改动）**：`TestCheckpointDualRunDifferential`——同一 `.stream.xml` 模型双跑，断言四件套（架构裁定见 Phase 1）。
- **存量 fixture 首固化**：现行代码物化 checkpoint 目录入库，`TestLegacyCheckpointFixtureRestore` classpath 只读 restore 成功；fixture 再生政策注释固化（格式钉只能显式有意再生）。
- **内核抽取（行为零变更）**：`io.nop.stream.runtime.checkpoint.core.SavepointMaterializer`（薄切片：输入 (coordinator, storage, execPlan, allInvokables, config, targetPath) → 触发+等待+归属戳+落盘+返路径的静态助手搬迁，GMCE 委托逐字节等价）；`CheckpointConfig.terminationCheckpointTimeoutMs` **nullable 字段**（null → 回退 `getCheckpointTimeout()`，默认 null = 全量行为零变更；JC :2691 自有字段不动）。
- **6a 裁定归一（行为变化，独立 commit + 差分门）**：JC failover 重建入口在 raw restore 前插入 manifest-first 校验门（manifest 读取 → 指纹快失败 → counter 推进），raw restore 保留为 `latestCompletedCheckpoint` 视图填充步（后置条件不破坏）；编排适用子集 = 前三步（**不含** enumerator stash 与 task 状态重建——coordinator 级无本地 invokable）。
- 三模块基线不下降（core 1963 / runtime 1195 / flow 315）；设计档回写归一记录与 abort 裁弃裁定。

## Non-Goals

- DIV-01～06 冻结项零触碰（2c/5b/5c/7/状态后端；abort 共享契约抽取已裁弃——见 Deferred But Adjudicated 首条）。
- manifest/存储格式/序列化零变更（E1+fixture 双钉）。
- 终态预算默认值归一（留 WI11；本 WI 只加 nullable 命名旋钮，默认 null 全量回退）。
- CC-06/CC-17（→ 下一可用编号承接 plan（创建时落号，当前 ≥2431——并发会话持续占用 2427-2430））；JC EXPORT 接入 SavepointMaterializer（→ WI11，Deferred）；execution 包拆分（WI11）。
- JobCoordinatorMain :175-186 启动路径 raw restore 未在本 WI 对齐（设计档 6a 行只圈 JC :2029 failover 分支；启动路径残留如实记录于设计档回写）。

## Scope

### In Scope

- 新增 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/core/SavepointMaterializer.java`（单类型薄切片）；`CheckpointConfig` nullable 新字段 + 回退语义。
- 新测试：`TestCheckpointDualRunDifferential`、`TestLegacyCheckpointFixtureRestore`、`TestJobCoordinatorFailoverRestore` 扩展（三场景）、`CheckpointConfig` 新字段回退语义测试、`SavepointMaterializer` 委托等价测试（规则 25 逐类型覆盖）。
- GMCE 委托改造（savepoint 薄切片 + 终态三处读新字段）；JC failover 分支插校验门。
- 设计档 §4.1 回写（归一记录 + abort 裁弃 + JC 启动路径残留）；roadmap WI10 翻转；daily log。

### Out Of Scope

- docs-for-ai（WI12）；DIV 冻结项；CC-06/17（下一可用编号承接 plan（创建时落号，当前 ≥2431——并发会话持续占用 2427-2430））；WI11 项（默认值归一、EXPORT 接入、execution 包拆分）。

## Execution Plan

提交序列（设计档 §五硬约束：行为变化独立提交）：C1 = Phase 1（E1 钉 + fixture，纯测试）；C2a = Phase 2（抽取，行为零变更）；C2b = Phase 3（6a 归一，行为变化，独立差分留档）；C3 = Phase 4（docs + plan 收口）。CC 批在 C2b 后由 下一可用编号承接 plan（创建时落号，当前 ≥2431——并发会话持续占用 2427-2430） 承接。

### Phase 1 - E1 差分钉 + 存量 fixture 首固化（重构前）

Status: completed
Targets: 新增 `nop-stream/nop-stream-runtime/src/test/java/io/nop/stream/runtime/checkpoint/TestCheckpointDualRunDifferential.java`、`TestLegacyCheckpointFixtureRestore.java`、`src/test/resources/checkpoint-legacy-fixture/`

- Item Types: `Proof`（E1 钉）、`Feature`（harness + fixture）

- [x] **harness 架构（执行前已裁定；实现期偏差已记录）**：
  - 分布式腿拓扑 = `TestRemoteDeployCheckpointWiringE2E` 蓝本（LocalMessageService + 2 in-process TaskManager + StreamControlRpcServer/Proxy + 双 coordinator + setRemoteDeployMode + assignTasks + startPeriodicCheckpoints 短间隔）；稳定性手段照搬其 40ms 限速 + 订阅窗 + waitFor 模式
  - 同模型双腿：同一 `.stream.xml` 字符串（有界 source → keyBy → 聚合 → 收集 sink）解析为同一 `StreamModel`；本地腿经 `SEE.execute()`（内部编译）；分布式腿经同源编译面（GMCE `buildExecutionPlan` 静态入口或等价，实现时验证可达性）→ 部署 plan → 手工拓扑；若同源编译不可达，则双腿各自编译同一模型并在 plan 注记编译确定性依据
  - manifest 比对规格（白名单 + 归一）：比对 `checkpointType`、`stateFormatVersion`、taskSnapshots **键形状**（vertex/subtask，剥离 jobId/pipelineId 前缀）、快照内描述符名与指纹、`sourceEnumeratorSnapshots` 形状、segments **数量与归一描述符**（剥路径前缀）；**排除** epochId/checksum/时间戳/存储路径；`streamModelFingerprint` 以双腿对称 `setCurrentFingerprint` 后比对；`pipelineId` 归一为常量；epoch 对齐 = 仅比对最终（latest）manifest + 双腿各自 epoch 数 ≥2（个数不跨腿断言——调度非确定）
  - restore 断言腿：本地 = manifest-first restore 后逐 key 状态一致；分布式 = restore-on-deploy（蓝本已有机制）第二拓扑重部署重放成功 + 重放后输出与本地腿 restore 后续跑输出逐 key 一致；若重放等价在实现中证明 flaky，**降级裁定**：(a)-(c) 断言（双输出/manifest 白名单/本地 restore）保持 ungated，重放等价断言改挂既有 multi-JVM 门控属性并在 plan 记录降级归因
  - **实现期偏差记录（全部落定）**：①本地腿编译面 = `env.buildJobGraph` + `GMCE.executeWithCheckpoint(jg,…)`（SEE 一次执行守卫禁止同 env 二跑；SEE.execute 与 buildJobGraph 的 id 族不同，混用致 restore fresh-start——已实测）；②manifest 白名单实测修正：指纹为已钉非对称（分布式携带、本地 JobGraph 入口无指纹源〔GMCE pinned asymmetry〕；dagTopologyHash 作业实例绑定排除）；恢复点=最新**数据** checkpoint（终态 COMPLETED_POINT_TYPE 仅 manifest 无数据行），随定时漂移→(c) 钉边界无关不变量（恢复累加器∈run1 边界值集 + 逐键差分序列 + 恰好一次总量）；③p=1 下键归因用序号（k1 发射值奇偶交替）；④2PC sink 建 JVM 级外部账本模型（终态继承 pendings 的重投递由账本吸收——生产 sink 按 epoch 幂等同型）；⑤AT_LEAST_ONCE 声明（SEE 连接器一致性门要求 STRICT 配 REPLAYABLE source；2PC 机制不受影响）；⑥VertexPlan 按 vertex ID 键控；⑦顺带修复 `StreamReduceOperator.restoreState` null 容错（fresh-start 契约，独立前置 commit）
- [x] 重构前跑绿留档：四件套断言全绿——TestCheckpointDualRunDifferential 连续 3 次 0F0E（11.14s/11.19s/11.13s）——E1 钉成立
- [x] fixture：checkpoint-legacy-fixture/storage/（4 manifest + 4 数据文件）+ README 再生政策 + 门控再生器（-Dnop.stream.test.fixture.regenerate=true，拒绝覆盖已存在 fixture）；TestLegacyCheckpointFixtureRestore classpath 提取→manifest-first auto-restore（恢复 epoch 3）→续跑不变量绿；外部账本预置 fixture 时代已提交 epoch（终态继承 pendings 重投递由账本吸收——生产 sink 按 epoch 幂等同型）
- [x] 三模块基线复核留档：core 1963/0/1 实测；runtime/flow 见 Exit Criteria 终跑
- [x] C1 提交（纯测试 + 资源，零生产代码；例外：StreamReduceOperator.restoreState null 容错修复以独立前置 commit 落地——fresh-start 契约要求 restoreState(null) 传播 initializeState 而 reduce 违之 NPE，harness 首次部署即触发）

Exit Criteria:

- [x] harness 四件套全绿且运行留档（3 连绿 0F0E；四件套按实现期偏差记录收敛为双输出多集/白名单/恢复不变量/epoch 计数——降级条款未触发，重放等价由 restore-on-deploy 蓝本测试保持覆盖）
- [x] fixture 入库（4 manifest + 4 数据文件 + README 政策）+ restore 测试绿（账本预置吸收重投递）
- [x] 重构前三模块基线留档（core 1963/0/1 实测、runtime 1198/0/11、flow 315/0/0）；C1 已提交 b2820d8e39 且零生产代码

### Phase 2 - 内核抽取（行为零变更）

Status: completed
Targets: 新增 `nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/checkpoint/core/SavepointMaterializer.java`；`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/checkpoint/CheckpointConfig.java` nullable 字段；GMCE 委托改造

- Item Types: `Feature`（内核契约）、`Fix`（散落语义下沉——WI9 §4.1 立项）

- [x] `SavepointMaterializer` 薄切片落地（checkpoint.core）：触发/等待/归属戳/落盘序列原样下沉，屏障扇出与归属戳为面级 ThrowingConsumer 回调注入（GMCE 私有助手零改动）；GMCE.triggerSavepoint 委托；委托等价由既有 savepoint 测试族全绿实证（TestSavepointEndToEnd 4/TestSavepointVertexSetDifferential 6/TestGraphModelCheckpointExecutor 9/TestCheckpointTriggerBoundedRetry 3）+ 差分 harness 复跑绿
- [x] terminationCheckpointTimeoutMs nullable 旋钮（默认 null 全量回退）；GMCE SAVEPOINT/terminal/final 三处触发预算改读 resolve（await 预算保持 checkpointTimeout，预算分离注记于 materializer）；TestCheckpointTerminationBudget 4 例（null 回退/显式覆盖/重置回退/builder 透传）全绿
- [x] JC 终态路径不动（自有 60s 字段维持；nullable 旋钮存在即概念统一，默认值归属留 WI11）
- [x] 焦点验证：savepoint 族 + 差分 harness + fixture restore 24/24 绿；runtime 全量 1203/0/11

Exit Criteria:

- [x] 新类型落地、GMCE 委托编译绿、逐类型新测试绿（TerminationBudget 4 例；委托等价由 savepoint 族 + 差分 harness 复跑实证）
- [x] 差分 harness 复跑全绿（10.82s，与 C1 留档同断言）
- [x] 三模块基线不下降；fixture restore 绿
- [x] C2a 提交 239eed4a2a（audit B8 git diff 审计：JC 零改动、GMCE 仅委托与字段读取）

### Phase 3 - 裁定归一（6a，行为变化，独立提交）

Status: completed
Targets: `JobCoordinator` LEADERSHIP 分支；`TestJobCoordinatorFailoverRestore` 扩展

- Item Types: `Fix`（6a 裁定归一——WI9 §4.3）

- [x] JC :2020-2052 raw restore **前置校验门**：①manifest 读取（缺失→跳过校验，现行为）；②指纹快失败（manifest 带指纹 + 当前无指纹源 → fail-fast；mismatch → fail-fast；均无 → skip）；③**counter 对 manifest epoch 推进**（校验通过后 `advanceCheckpointIdCounterAfterRestore(manifest.epochId)`——monotonic-only 幂等 :1200-1207，与 raw 内含的 data-epoch 推进天然共存不重复；封堵 shadow-window：manifest epoch > 最新可读 data epoch 时 counter 必须越过 manifest epoch，否则下次触发与 durable manifest 撞号）；随后 **raw `restoreFromCheckpoint()` 照常执行以填充 `latestCompletedCheckpoint` 视图**（:2026/:2055 自消费后置条件不破坏）
- [x] `TestJobCoordinatorFailoverRestore` 扩展 6→11 例（含四场景 + shadow-window 共 5 新增）：manifest 缺失→raw（既有绿保持）；manifest 无指纹→校验 skip + raw；manifest 带指纹+coordinator 设指纹→match 放行 / mismatch fail-fast / 带指纹+无指纹源→fail-fast（leadership 激活失败）；**manifest epoch > data epoch → counter 越过 manifest epoch、下一触发不撞 durable epoch**（shadow-window 场景）
- [x] 差分 harness（10.85s 绿）+ 三模块全量（core 1967/0/1、runtime 1203/0/11、flow 315/0/0）+ fixture restore 复跑绿；C2b 独立提交 f0a45bfb7a（与 C2a 239eed4a2a 分离——设计档 §五硬约束）

Exit Criteria:

- [x] 五场景测试全绿（6 既有不回归 + 无指纹/兼容/mismatch/无源/shadow-window；11/11）
- [x] 差分 harness 复跑全绿并独立留档（10.85s）
- [x] 三模块基线不下降；C2b 已提交 f0a45bfb7a 且与 C2a 分离（audit B8 逐 hunk 确认）

### Phase 4 - 设计档回写 + roadmap 翻转 + 收口

Status: completed
Targets: checkpoint-engine-convergence.md、structure roadmap、daily log

- Item Types: `Fix`（owner-doc 同步）

- [x] 设计档 §四.5 回写：6a 落地形态（校验门前置 + raw 视图填充）；1c nullable 旋钮与默认值归属留 WI11；**abort 机制下沉裁弃裁定**（legacy 单参 handler 无 reason 输入 + 共享契约会成 1 常量 + 1 前缀判断的伪抽象 + DIV-02 owner 裁决未决——机制下沉 defer 至 DIV-02 裁决后，随 r5-successors）；JC 启动路径 :175-186 残留如实记录
- [x] roadmap WI10 `todo`→`done`（注记含 CC 承接说明——承接 plan 创建时落号〔并发会话持续占用 2427-2430〕、解析器合规格式）+ 复跑 12 条目 10 done
- [x] daily log WI10 条目；doc-links 触及文件 0 新增断链；C3 收口提交

Exit Criteria:

- [x] 设计档回写与实施一致（audit D13）；roadmap-check 12 条目、WI10 done（audit D14）
- [x] daily log 条目已写；doc-links 达标（audit D15/D17）

## Closure Gates

- [x] E1 差分钉重构前 3 连绿 + 留档；fixture 首固化 + restore 钉 + 再生政策（audit A1/A2/A3 亲跑绿）
- [x] manifest/存储格式/序列化零变更（fixture restore + manifest 白名单断言全程绿——跨构建恢复实证）
- [x] 抽取（C2a 239eed4a2a）与归一（C2b f0a45bfb7a）提交分离且各自差分留档；DIV 冻结项零触碰（audit C12 逐 hunk 审查：timeout-abort 分支/外部 abort/savepoint 恢复入口/CANCEL/状态后端原样，行号漂移纯由门插入推挤）
- [x] 三模块基线不下降且全升（core 1967/0/1、runtime 1203/0/11、flow 315/0/0——audit E18 实测 _tmp/wi10-c2b-baselines.log）
- [x] 设计档回写（§四.5 含 abort 裁弃与启动路径残留——audit D13）+ roadmap WI10 done（audit D14）+ daily log（audit D15）
- [x] 独立 closure audit 完成且证据写入本 plan Closure 段（fresh session，见下方 Closure Audit Evidence）
- [x] **Anti-Hollow Check**：audit 实证 harness 双腿分别真实经 GMCE（executeWithCheckpoint 调用 :172/:176）与 JobCoordinator（jc-periodic 线程触发 + RPC reportTaskStatus 回环）、fixture restore 真实读 classpath 资源（classloader 提取 + 恢复断言绿）、SavepointMaterializer 唯一真实委托方 GMCE :437（与 Deferred 登记一致）；新生产代码无空方法体/静默跳过
- [x] `./mvnw -o test` 三模块全绿（BUILD SUCCESS，audit E18 实测日志）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2426-wi10-shared-checkpoint-kernel-extraction.md --strict` exit 0（关闭前终跑）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0（788 文件，Critical/High 0——audit C11 亲跑）
- [x] 路径限定提交序列（C1 b2820d8e39 / C2a 239eed4a2a / C2b f0a45bfb7a / C3 收口；audit B8 确认中间未卷入并发会话提交）

## Deferred But Adjudicated

### CheckpointAbortPolicy 共享契约（abort 机制下沉）

- Classification: `removed from scope through a recorded scope change`
- Why Not Blocking Closure: 本地 handler 为 legacy 单参签名（无 reason 输入），分布式判定点为单句前缀判断——共享契约只能是「1 常量 + 1 前缀判断」的伪抽象（自家 hollow 扫描 gate 会抓）；设计档 §4.1 bullet 4 的「机制下沉」依赖 DIV-02 owner 裁决（两侧策略语义何去何从未决），裁决前下沉机制属过早抽象。机制原地不动、策略语义冻结（DIV-02），裁决后随 r5-successors 处置。
- Successor Required: `yes`
- Successor Path: DIV-02 owner 裁决后的 r5-successors 处置计划

### JC EXPORT 接入 SavepointMaterializer（4a 机制接入）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: WI9 裁定 4a 双形态保留、只共享物化机制；EXPORT 接入涉及「不返回路径」契约参数化，属 WI11 分布式路径切换伴随改造。本 WI 内 SavepointMaterializer 唯一调用方为 GMCE——hollow 风险以「设计档 §4.1 已裁定的下沉序列 + WI11 接入承诺」为 non-blocking 依据。
- Successor Required: `yes`
- Successor Path: WI11 计划

### 终态预算默认值归一（1c 后半）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 本 WI 交付 nullable 旋钮（默认 null 全量回退 = 零行为变更）；默认值归属按 WI9 §4.3 由差分语料放行后裁定。
- Successor Required: `yes`
- Successor Path: WI11 计划

## Non-Blocking Follow-ups

- JobCoordinatorMain :175-186 启动路径 raw restore 的 manifest 校验门对齐（设计档 6a 行只圈 failover 分支；残留如实记录于设计档回写，归 WI11 评估）。

## Closure

Status Note: WI10 四 Phase 全部完成：E1 差分 harness 双脸四件套重构前 3 连绿 + 存量 checkpoint fixture 首固化（格式零变更跨构建恢复实证）；SavepointMaterializer 下沉 checkpoint.core 字节级等价 + 终态预算 nullable 旋钮概念统一；6a 归一 failover manifest-first 校验门（指纹 fail-closed + shadow-window counter）经差分门放行且行为变化独立成 commit；顺带修复 StreamReduceOperator fresh-start 契约违约（独立 commit）。三模块基线全升零败（core 1967/0/1、runtime 1203/0/11、flow 315/0/0）。DIV 冻结项零触碰（逐 hunk 审计）。CC-06/CC-17 按 roadmap 顺序约束切独立承接 plan 同波次执行。独立 closure audit 18 项 17 PASS + 1 机械性 Blocker（Exit Criteria 勾选遗漏）已补勾复核。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（general-purpose，fresh session，agent_1d9049c1-3e22-4d79-9db7-9631915cacf5）
- Audit Session: agent_1d9049c1-3e22-4d79-9db7-9631915cacf5
- Evidence:
  - A 组（E1 钉与 fixture）A1-A3 全 PASS：四件套断言齐备 + 分布式手工拓扑；fixture 9 文件 + 门控再生器；audit 亲跑 dual-run + fixture restore 全绿（10.94s）。
  - B 组（抽取与归一）B4-B8 全 PASS：materializer 预算/序列逐行核对；GMCE 委托与 await 预算分离（行号漂移说明在案）；nullable 字段与 resolve 回退；JC 门三步序列 + raw 视图填充；提交序列四段分离（audit 逐 hunk）。
  - C 组（亲跑验证）C9-C12 全 PASS：failover 11/11；inventory exit 0；hollow 扫描 0；DIV 冻结项零触碰逐 hunk 审查。
  - D 组（文档与 roadmap）D13-D17 全 PASS：设计档 §四.5 六要素；roadmap 12 条目 10 done；daily log；Phase 一致性（唯一 FAIL 为 Exit Criteria 勾选遗漏——已补勾）；doc-links 0 errors。
  - E 组（基线证据）E18 PASS：三模块日志实测全升零败；工作树无未提交 nop-stream 代码残留。
  - Anti-Hollow 抽查 PASS：双腿真实经 GMCE/JobCoordinator（调用链 + 运行日志线程证据）、fixture 真实 classpath 提取、SavepointMaterializer 真实委托方。
  - 审计唯一 Blocker（机械性）：Phase 1/2/3 Exit Criteria 勾选遗漏 10 项——实质均已由审计证实，已补勾并复核；Phase 4 补勾 + C3 提交随本 Closure 落地。
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2426-wi10-shared-checkpoint-kernel-extraction.md --strict` 关闭前终跑 exit 0。

Follow-up:

- CC-06/CC-17：独立承接 plan（创建时落号 ≥2431）同波次执行——TestCommitFanoutOffMonitor2326 覆盖面复核 + 定向测试。
- WI11 承接：终态预算默认值归一（差分语料）、JC EXPORT 接入 SavepointMaterializer、execution 包拆分评估、JC 启动路径 manifest 校验门对齐、 DIV-01～06 处置（r5-successors）。
