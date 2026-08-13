# nop-stream red list（Cycle 3 / I2 权威版，移交 I3 裁决）

> Status: active（Cycle 3 / I2 权威版，2026-08-13 实测；Cycle 2 历史版见附录 §5、Cycle 1 历史版见附录 §6，I4 已全部修复，存档不动）
> Created: 2026-08-13 (Cycle 3 / I2)；Cycle 2 版见附录 §5、Cycle 1 版见附录 §6
> Sources: Cycle 3 / I1 plan `2026-08-13-0805-1-nop-stream-invariants-cycle3-I1-wiring-existence-gates.md`（门禁落档）；
> Cycle 3 / I2 plan `2026-08-13-0805-2-nop-stream-invariants-cycle3-I2-invariant-driven-audit.md`（本版权威化）；
> I1 输入 `ai-dev/audits/nop-stream-invariants/cycle3-I1-input.md`（11 门禁类 112 tests / 0 pin / 7 服务注入 API / 消费方 3 类，零悬挂基线）
> Semantics: **I2 权威版** — 每条含 位置 / 关联不变式 / 关联 finding 或注册表条目 / 族标注 / 验证或探查结论 / 裁决输入。
> I3 依此逐条裁决（严重度 + 派发），I4 执行修复。I2 不修复任何项、不做严重度裁决。

## 0. Cycle 3 / I2 门禁全量运行结果（Phase 1，2026-08-13 实测）

- **I1 硬前置预检（机械门 4 检，全部通过）**：(a) `2026-08-13-0805-1-...` Plan Status == `completed` ✓；
  (b) `wiring-registry.json` 与 `cycle3-I1-input.md` 存在 ✓；(c) `node ai-dev/tools/check-nop-stream-invariants.mjs all`
  输出含 `scan-wiring` 子命令 ✓（usage 列表 `inventory|sync|scan-iterations|scan-output-contract|scan-wiring|self-test|init|all`，
  且 `scan-wiring` 独立运行输出 `scan-wiring: OK`）；(d) JUnit 门禁类 ≥ 11 个（glob 实测 11 类，含
  `TestWiringExistenceInvariant`）✓。无缺项 → 未触发 blocked。
- **mjs `all` 退出码 0**：inventory / sync / scan-iterations / scan-output-contract / scan-wiring / self-test 六命令全绿
  （`node ai-dev/tools/check-nop-stream-invariants.mjs all` → exit 0；逐命令输出：
  `inventory: OK` / `sync: OK` / `scan-iterations: OK` / `scan-output-contract: OK` / `scan-wiring: OK` / `self-test: OK`）。
- **处置二分结论（Phase 1）**：全部落入「无变化」分支——**无新增 pre-existing residual**（六命令零违规输出：
  scan-wiring V1–V5 零命中、scan-output-contract V1/V4/V5 零命中、scan-iterations 零违规——pins 空表
  `pinnedViolations: []` 且无 unpinned 违规 = 吸收面为零仍需零 pin）、**无行为漂移**（注册表描述与 live 行为
  全部一致，见注册表自洽性复核表）、**无 stale pin**（pin 表为空，无 pin 可 stale）。
- **11 类 JUnit 门禁 + 3 模块表完备性**（`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'`，
  surefire 实测 2026-08-13）：TestCheckpointIDCounterInvariant 8 / TestSynchronizedCollectionInvariant 12 /
  TestInvariantTableCompleteness 10 / TestOutputContractInvariant 10 / TestWiringExistenceInvariant 10 /
  TestWindowRoundTripInvariant 9 / TestClusterRegistryConsistencyInvariant 10 / TestWindowOperatorMergingCleanupInvariant 4 /
  TestRuntimeInvariantTableCompleteness 11 / TestCepReleaseSymmetryInvariant 21 / TestCepInvariantTableCompleteness 7
  —— **合计 112 tests，0 failures / 0 errors / 0 skipped**，BUILD SUCCESS（core 50 / runtime 34 / cep 28）。
  与 `cycle3-I1-input.md` 落档一致（11 类 / 112 tests）。
- **注册表自洽性复核表**（live 行号 vs 注册表 / I1 基线，全部零漂移 ±0）：

| 类别 | 注册表条目 | live 复核（2026-08-13） | 结论 |
|---|---|---|---|
| 服务注入 API 1/7 | `setOutput` | `AbstractStreamOperator.java:140` | 一致 |
| 服务注入 API 2/7 | `setProcessingTimeService` | `AbstractStreamOperator.java:148` | 一致 |
| 服务注入 API 3/7 | `setStateBackend` | `AbstractStreamOperator.java:152` | 一致 |
| 服务注入 API 4/7 | `setKeyedStateBackend` | `AbstractStreamOperator.java:165` | 一致 |
| 服务注入 API 5/7 | `setOperatorStateBackend` | `AbstractStreamOperator.java:173` | 一致 |
| 服务注入 API 6/7 | `setTimeServiceManager` | `AbstractStreamOperator.java:181` | 一致 |
| 服务注入 API 7/7 | `setSnapshotCallback` | `AbstractStreamOperator.java:320` | 一致 |
| 接线点 PTS | `StreamTaskInvokable.java:462`（构造，setupProcessingTimeServices :456-477，4 构造 :151/:164/:180/:195 调用） | live :462 `setProcessingTimeService(pts)`；setupProcessingTimeServices :456 | 一致 |
| 接线点 TSM | `StreamTaskInvokable.java:463` | live :463 `setTimeServiceManager(tsm)` | 一致 |
| 接线点 Output ×5 | `StreamTaskInvokable.java:230/:268/:287/:293/:426`（wireOperators ×2 / fan-out 尾 ×2 / wireTailToRecordWriter） | live :230/:268 `setOutput(new ChainingOutput)`、:287 `RecordWriterOutput`、:293 `BroadcastingRecordWriterOutput`、:426 `setOutput(new RecordWriterOutput)` | 一致 |
| 接线点 snapshotCallback | `StreamTaskInvokable.java:435`（setupSnapshotCallbacks :430-438） | live :435 `setSnapshotCallback` | 一致 |
| 接线点 stateBackend | `GraphModelCheckpointExecutor.java:742`（wireTaskCheckpointPipeline :687，rebuild 路径调用 :660） | live :742 `abstractOp.setStateBackend(stateBackend)`（:733-744 供给块）；wireTaskCheckpointPipeline :687 | 一致 |
| 消费方 1/3 | `CepOperator` getProcessingTimeService :290/:300/:373/:381/:382/:401/:479/:490 | live 八行全部命中 | 一致 |
| 消费方 2/3 | `WindowOperator` getProcessingTimeService :479 + registerTimerService :471 | live :479/:471 命中 | 一致 |
| 消费方 3/3 | `ProcessOperator` registerTimerService :46 | live :46 命中 | 一致 |
| state backend 接线完备性专项 | `setKeyedStateBackend` / `setOperatorStateBackend` = internal-creation（main 零调用点） | live grep 全 main 仅声明命中（`AbstractStreamOperator.java:165/:173`），调用点 0；生产直建路径 `CepOperator.java:257` / `WindowOperator.java:421`（createKeyedStateBackend）、`AbstractStreamOperator.java:65` / `AbstractUdfStreamOperator.java:114`（createOperatorStateBackend）全部命中 | 一致（carve-out 成立） |
| 消费方 getTimeServiceManager | 注册表 consumers 无此 getter 调用方（main 零调用） | live grep 全 main：`getTimeServiceManager()` 仅 `StreamTaskInvokable.java:345` 方法声明（自身 getter 供诊断），无其他调用 | 一致 |
| 测试豁免清单 | 3 条 test-only 豁免机制（CepTestUtils.injectProcessingTimeService / cep 测试私有包装器 / TestCepOperatorStateBackendWiring） | 全部位于 `src/test`，src/main 无同名混入；main 接线点独立存在（见上） | 一致（无 main 混入） |

- **处置结论**：无行为漂移 → 无 blocked 升级；无 stale pin → 无 pin 移除；无新增 unpinned 违规 →
  无新增非 pin red list 项。red list 主体 = 探查发现（Phase 3，§3）+ 注册表裁定（Phase 2，§2）。

---

## 1. Cycle 3 red list 主体（I2 权威版条目）

> 仅含 Cycle 3 权威条目；Cycle 1/2 历史条目见附录 §5/§6。条目编号 C3-RL-n。
> 来源 = Phase 1 门禁（无新违规）+ Phase 2 注册表裁定（9/9 维持）+ Phase 3 探查（P2 backlog 触发确认升格 + 复探结论）。
> 每条含 位置 / 关联不变式 / 关联 finding 或注册表条目 / 族标注 / 验证或探查结论 / 裁决输入（供 I3 直接裁决）。

### C3-RL-1..3. 门禁全绿 + 注册表维持（无缺陷条目，记录性）

- **C3-RL-1（门禁全绿）**：mjs `all` exit 0（六命令）+ JUnit 11 类 112 tests 0 failures + pin 空表——无新违规、无行为漂移、无 stale pin（§0 详）。无修复项。
- **C3-RL-2（注册表 9/9 维持）**：7 服务注入 API + 9 接线点 + 消费方 3 类 11 行全部 live 零漂移（§2 详）。无修订项。
- **C3-RL-3（组合面/恢复面核查无缺口）**：注入/恢复时序组合面 + 恢复路径服务注入面保持（C3-PR-3/C3-PR-4）——判定依据维持，供 I3 参考，无缺陷条目。

### C3-RL-4. checkpoint 协调面 P2 触发确认（P2-01 checkpointSuccessMap 无界增长 + P2-02 onCompletePersistFailure 不 complete future + P2-03 getNodeLease 无锁 NPE）

- **位置**：`CheckpointCoordinator.java:1117`（checkpointSuccessMap.put，fail/abort 路径无 remove，:805/:1155/:1204 仅三处 remove）、
  `CheckpointCoordinator.java:821-831`（onCompletePersistFailure 直接 `getStatus().set(FAILED)` :826 绕过 `pending.fail()`——
  future 消费者 JobCoordinator :1439/:1462/:1485 + GraphModelCheckpointExecutor :353/:485 永久悬挂至超时）、
  `InMemoryClusterRegistry.java:99-108`（getNodeLease 无锁双 map 读，:104 自动拆箱 NPE；对照 getActiveNodes :134 防御检查）
- **关联不变式**：#7 相邻（checkpoint 协调面恢复行为——门禁盲区 c 联动）；open-audit P2 批次已登记
- **关联 finding / 注册表条目**：open-audit P2-01/P2-02/P2-03（`2026-08-12-1217-open-audit-...md`）；roadmap Follow-up Backlog
- **族标注**：已知族（open-audit P2 批次触发确认；checkpoint/cluster 面——非 #7 wiring 族，非新族）
- **门禁/pin 来源**：无（P2 backlog 条目，非门禁违约；本 plan 触发条件评估确认）
- **验证结论**：live 证据完整（触发路径代码 + 消费者 + 对照路径），触发条件成立
- **裁决输入**：P2 级候选（原判级不变）——I3 裁决严重度 + 派发（I4 修复候选：fail/abort 路径补 remove / onCompletePersistFailure 走 pending.fail() / getNodeLease 加锁或防御检查）

### C3-RL-5. WindowOperator.triggerAccumulators 永不裁剪 + 纯 FIRE 不调 clear（P2-04 触发确认）

- **位置**：`WindowOperator.java:2043-2071`（stateKey :2050 只增不删，全文件零 remove，仅 :536 close 置空）；纯 FIRE 路径 :722-727（合并）/ :755-760（常规）不调 triggerContext.clear()
- **关联不变式**：窗口生命周期收敛（#1 语义扩展面）；open-audit P2-04
- **关联 finding**：open-audit P2-04（live 行号重定位 :1944-1957 → :2043-2071）
- **族标注**：已知族（open-audit P2 批次触发确认；窗口面——非 #7 wiring 族）
- **验证结论**：触发确认（C3-PR-5）——长运行窗口 map 无界 + 复发窗口复用陈旧累加器
- **裁决输入**：P2 级候选——cleanup timer 路径同步删除 trigger_* 条目 + 合并路径迁移；I3 裁决严重度 + 派发

### C3-RL-6. evictor descriptor 路径不建 elementTimestampsState（P2-05 触发确认）

- **位置**：`WindowOperator.java:451-462`（仅 null-descriptor else 分支建 elementTimestampsState）/ :1337（storeElementTimestamp 早退）/
  :924-932（emitWindowContents 无时间戳拿当前 watermark 兜底）——TimeEvictor 永不驱逐
- **关联不变式**：窗口生命周期收敛面；open-audit P2-05（live 行号重定位 :442-453/:1230-1233 → :451-462/:1337）
- **族标注**：已知族（open-audit P2 批次触发确认；窗口面）
- **验证结论**：触发确认（C3-PR-5）——descriptor 路径（builder 恒传 stateDesc）elementTimestampsState 恒 null
- **裁决输入**：P2 级候选（当前 TimeEvictor 标 @Internal 潜伏）——descriptor 路径同样创建时间戳状态或并入窗口内容状态；I3 裁决

### C3-RL-7. 合并路径 pane 跟踪键错位 + purge 缺 triggerContext.clear（P2-06/P2-07 触发确认）

- **位置**：`WindowOperator.java:965-995`（computePaneInfo/paneKey = key + SEP + actualWindow）/ :729-731（合并路径清除用 stateWindow——
  (key, actualWindow) pane 条目泄漏 + DISCARDING 清错命名空间 + purge 无 triggerContext.clear，对照常规路径 :762-765）
- **关联不变式**：窗口生命周期收敛面；open-audit P2-06/P2-07（live 行号重定位 :917-943 → :965-991 / :681-683 → :729-731）
- **族标注**：已知族（open-audit P2 批次触发确认；窗口面）
- **验证结论**：触发确认（C3-PR-5）——合并路径清除键基准差异可证
- **裁决输入**：P2 级候选——pane 键与清除路径统一命名空间基准 + 合并路径补 triggerContext.clear()；I3 裁决

### C3-RL-8. CepOperator STEP-5 超时基准错误 + 绕过 TimedOutPartialMatchHandler（P2-08 触发确认）

- **位置**：`CepOperator.java:563-592`（:573 `cs.getStartTimestamp() + wt` 判定，start state 为 -1；:579-591 清理不触发
  processTimedOutSequences——超时事件静默丢弃；:564 size==1 守卫使破坏性路径巧合安全）
- **关联不变式**：CEP 清理语义面；open-audit P2-08（live 行号重定位 :540-554 → :563-592）
- **族标注**：已知族（open-audit P2 批次触发确认；CEP 面）
- **验证结论**：触发确认（C3-PR-5）——谓词语义与 NFA 逐状态窗口（PREVIOUS_AND_CURRENT）不一致
- **裁决输入**：P2 级候选（潜伏地雷）——以 previousTimestamp + 真实 isStateTimedOut 语义判定 + 清理前走超时通知路径；I3 裁决

### C3-RL-9. multi-audit P2 预枚举候选触发确认（P2-11 beans 重复 id / P2-01 serializer 死字段 / P2-03 RocksDB Options 泄漏 / P2-05 上帝类）

- **位置**：`stream-control-rpc.beans.xml:34` + `stream-data-plane.beans.xml:38/:68`（同一 bean id `streamMessageService`）；
  `WindowOperator.java:151/:161`（keySerializer/windowSerializer 死字段）+ `WindowOperatorFactoryImpl.java:151-189`（createDummySerializer :166-171 反射失败返回 null）；
  `RocksDBKeyedStateBackend.java:206-210`（Options 无 try-with-resources，对照 RocksDBIncrementalRestore :151 正确写法）；
  `GraphModelCheckpointExecutor.java`（1728 行，执行与恢复职责混合）
- **关联不变式**：#7 相邻（beans 模板面 / state backend 接线面）；multi-audit P2 批次已登记
- **关联 finding**：multi-audit P2-11 / P2-01 / P2-03 / P2-05
- **族标注**：已知族（multi-audit P2 批次预枚举触发确认——beans 面 / 窗口面 / RocksDB 面 / 架构面，非新族）
- **验证结论**：4 条逐一 live 复核触发成立（C3-PR-6 §2 表）；其余 multi-audit 条目标注「不触发 + 一行依据」维持 backlog 原状
- **裁决输入**：各按原 P2 级候选——beans 二选一加载 / serializer 消费或删字段 / RocksDB try-with-resources / 上帝类重构（架构级）；I3 裁决

### C3-RL-10. open-audit P2-09 引用 claim 过期（multi P0-01 触发条件已解决，处置记录）

- **位置**：`TestWindowOperatorCorrectness.java:551-585/:607-625`（MixedTypeWindowOperator 2 处实例化 :554/:610 +
  `ERR_STREAM_WINDOW_NON_ACCUMULATOR_MERGE_CONFLICT` 断言 :576-581）
- **关联不变式**：门禁审计证据准确性
- **关联 finding**：open-audit P2-09（「零回归测试 / 零实例化」claim 与 live 不符）；multi-audit P0-01（已解决）
- **族标注**：不属 #7 wiring 族——claim 过期处置记录
- **验证结论**：触发条件「merge fail-fast 零回归测试」已解决（2 个 assertThrows 回归用例在案，全绿）——P2-09 不触发
- **裁决输入**：I3 关闭 multi-audit P0-01 引用项 + backlog 修订 P2-09 条目（处置记录，非缺陷）

---

## 2. 注册表一致性裁定与 pin 复核（Phase 2，2026-08-13 回写）

### 接线点 live 复核表（注册表每条生产接线点 vs live 代码，零漂移 ±0）

| # | 服务 | 注册表（文件:行 + 注入时机） | live 复核（2026-08-13） | 裁定 |
|---|---|---|---|---|
| 1 | PTS | `StreamTaskInvokable.java:462`（构造） | :462 `abstractOp.setProcessingTimeService(pts)`；setupProcessingTimeServices :456-468 为 4 个构造 :149/:162/:178/:193 后置调用（:151/:164/:180/:195），javadoc :446-450 明示「runs in the constructor — BEFORE any operatorChain.open()」 | **维持**（行号零漂移 + 注入时机分类成立） |
| 2 | TSM | `StreamTaskInvokable.java:463`（构造） | :463 `abstractOp.setTimeServiceManager(tsm)`（同 setupProcessingTimeServices） | **维持** |
| 3 | Output | `StreamTaskInvokable.java:230`（装配布线） | :230 `currentOp.setOutput(new ChainingOutput<>(...))`（wireOperators，链内非尾算子） | **维持** |
| 4 | Output | `StreamTaskInvokable.java:268`（装配布线） | :268 `currentOp.setOutput(new ChainingOutput<>(...))`（wireOperators(List)，fan-out 链内） | **维持** |
| 5 | Output | `StreamTaskInvokable.java:287`（装配布线） | :287 `op.setOutput(new RecordWriterOutput(fanOutWriters.get(0)))`（fan-out 尾，单 writer） | **维持** |
| 6 | Output | `StreamTaskInvokable.java:293`（装配布线） | :293 `op.setOutput(new BroadcastingRecordWriterOutput(outputs))`（fan-out 尾，2+ writers） | **维持** |
| 7 | Output | `StreamTaskInvokable.java:426`（装配布线） | :426 `op.setOutput(new RecordWriterOutput(outputWriter))`（wireTailToRecordWriter :422-428） | **维持** |
| 8 | snapshotCallback | `StreamTaskInvokable.java:435`（快照） | :435 `setSnapshotCallback(...)`（setupSnapshotCallbacks :430-438） | **维持** |
| 9 | stateBackend | `GraphModelCheckpointExecutor.java:742`（checkpoint 装配） | :742 `abstractOp.setStateBackend(stateBackend)`（:733-744 供给块，wireTaskCheckpointPipeline :687） | **维持** |

### pin key 匹配复核

- `mjs-pins.json` `pinnedViolations` = **空表**（`[]`）——无 pin 可匹配、无 pin 可 stale；`internal-creation` 为注册表 disposition 非 pin。**复核结论：N/A（零 pin 基线维持）**。

### 注册表 ↔ pin ↔ JUnit 断言三方一致

- `TestWiringExistenceInvariant`（nop-stream-core，10 用例）为**注册表驱动**（`REGISTRY_REL_PATH` :81 加载 `wiring-registry.json`，参数化 7 服务 :136-138；API 面完备性反射 + 消费方表自洽 + 接线连通断言），与注册表同源——三方一致按构造成立，surefire 实测 10/10 绿（2026-08-13，Phase 1 记录）。
- **结论：一致（注册表分类 ↔ 零 pin ↔ JUnit 断言同源，无差异修正项）**。

### 接线路径复核（决策输入，供 I3 参考）

- **(a) PTS/TSM 构造函数注入（invoke 前）与算子 open/restore 顺序**：构造注入先于一切 `open()`（javadoc :446-450；4 构造全部调用 setupProcessingTimeServices）；`TestStreamTaskInvokableProcessingTimeWiring` 非 checkpoint 路径 + `CepOperator.open` 守卫（:373 `getProcessingTimeService() == null` → WARN + skip）双面在案；PTS driver 在 `invoke()` 时才启动（:475-481，构造即启动会泄漏线程——注释在案）——**顺序不破坏**。
- **(b) state backend 供给时序**：初始注册 `registerTasksAndTrackers` :660 → `wireTaskCheckpointPipeline` :687 → :742（checkpointConfig 驱动 + MemoryStateBackend 默认）；重建路径 `SupervisionLoop.rewireCheckpointPipeline` :816-840 复用**同一 helper**（:838 `wireTaskCheckpointPipeline`）——重建 task 与初始注册不可区分（javadoc :675-679），P0-02 修复（plan `2026-08-13-0132-2`）在案；注册表 :742 单接线点覆盖两路径（helper 复用）——**登记完整**。
- **(c) `setOutput` 布线面**：wireOperators（:230/:268 ChainingOutput 链内）/ wireTailToRecordWriter（:426 RecordWriterOutput 单 writer）/ fan-out 尾（:287 RecordWriterOutput / :293 BroadcastingRecordWriterOutput）；关闭面 P1-03 `closeOutputWriters` :509-528 显式遍历全 fanOutWriters（BRWO close 为 no-op 委托，注释 :500-507 明示不可依赖——no-silent-skip）——**布线判定依据仍成立**。
- **结论**：注册表 9 条接线点全部 live 核实、注入时机分类与代码路径语义一致、rebuild 路径登记完整——**判定依据全部维持，供 I3 参考，无修订项**。

### 裁定结论回写

- 注册表条目：**9/9 维持**（零漂移、零修订）；pin：**零 pin 维持**（空表）；消费方枚举：**3 类 11 行维持**（Phase 1 复核表）。
- 无行为漂移 → 无 blocked 升级路径触发；无注册表 → JUnit 断言差异项。

---

## 3. 聚焦对抗探查发现（Phase 3，2026-08-13 回写）

> 完整探查报告：`ai-dev/audits/nop-stream-invariants/cycle3-I2-probing-report.md`（Phase 3 新建产物）。
> 本节收录探查结论（C3-PR-1..8）：同族复探零新实例 + 守卫复核 + 组合面/恢复面核查 + P2 触发确认 + 非族评估。

- **C3-PR-1（同族"仅测试注入"复探）**：checkpoint 服务（CheckpointCoordinator / CheckpointBarrierTracker / SharedStateRegistry）、
  watermark 服务（TimestampsAndWatermarksOperator）、其他 `set*Service` 注入点（setTimerService / setCoordinatorRpcService /
  RPC 面）逐服务 grep 归属分类——**全部 main 已接线，零 P0-01 同形态新实例**；plan `2026-08-13-0132-1` Non-Blocking 登记项
  处置 = 复探完成可闭合。
- **C3-PR-2（TimestampsAndWatermarksOperator 静默守卫复核）**：:82-84 守卫接线后 PTS 恒非 null（4 构造无条件注入）→ 守卫分支
  生产不可达（仅直接单测可达）——plan 1 裁定「接线后自然失效，语义不破坏」**成立**，维持 watch-only residual。
- **C3-PR-3（注入/恢复时序组合面）**：构造注入 vs open() vs restoreState() vs rebuildTask 四维组合——双算子 restore-before-open
  延迟应用模式在案（CepOperator :278-285/:259、WindowOperator :486-492/:405-416）、rebuild 复用同一 wire helper
  （SupervisionLoop :838 = 初始 :660）、driver 延迟至 invoke() 启动（:475-481）——**无新 null 面 / 无重复注入 / 无顺序依赖破坏**。
- **C3-PR-4（恢复路径行为盲区）**：descriptor 路径 snapshot→restore 服务注入面保持（timer 注册表 restoreTimers→registerTimerService
  闭环 + state backend applyPendingRestoreState→getter 可用）；既有恢复测试（TestWindowRoundTripInvariant / TestE2EWindowAggregateRestore /
  TestE2EManifestRestoreIdAdvance）全绿——无新服务注入缺口；联动 P2-01/P2-02 触发确认（C3-RL-4）。
- **C3-PR-5（窗口/CEP 清理语义触发确认）**：P2-04~P2-08 五条全部触发（live 行号重定位：triggerAccumulators :2043-2071 /
  elementTimestampsState :451-462/:1337 / computePaneInfo :965-991 / STEP-5 :563-592）→ C3-RL-5~C3-RL-8。
- **C3-PR-6（P2 backlog 触发评估表）**：open-audit 批 P2-01/02/03/04/05/06/07/08 触发（C3-RL-4~C3-RL-8）、P2-09 不触发
  （claim 过期，C3-RL-10）、P2-10 不触发（工具面）；multi-audit 批 P2-11/P2-01/P2-03/P2-05 触发（C3-RL-9）、其余逐条一行依据不触发。
- **C3-PR-7（multi P0-01 已解决）**：open-audit P2-09 声称「merge fail-fast 零回归测试 / MixedTypeWindowOperator 零实例化」
  与 live 不符——2 处实例化 + 2 个 assertThrows 回归用例在案（:551/:607）——触发条件已解决，claim 过期（C3-RL-10）。
- **C3-PR-8（非族候选评估）**：setCurrentKey/setKeyContextElement1/2（非服务注入面，V4 排除清单正确）**不升格**；
  HeapInternalTimerService 计时器真实触发机制 vs 记账结构对齐（driver→mailbox→fireProcessingTimeTimers 链路一致，
  nextProcessingTimeTimer volatile 单读驱动调度）**不升格**（plan `2026-08-13-0132-3` Non-Blocking 处置闭合）；
  StreamTaskInvokable 自身 getter 声明（:345/:353）非消费调用点 **不升格**。
- **综合结论**：无新族候选派生（全部发现归属已知族 #7 或既有 backlog 批次）；无 in-scope confirmed live defect 被静默遗漏
  （全部发现已在 §1 red list 主体或本报告）；P2 backlog 触发条件评估表完整在案（§2 probing report，无静默跳过）。

---

## 4. 移交声明（I3 裁决输入就绪，2026-08-13 回写）

- **零悬挂核对（vs `cycle3-I1-input.md` 登记项，全部有处置）**：

| I1 登记项 | 数量 | I2 处置 |
|---|---|---|
| JUnit 门禁（11 类 / 112 tests） | — | §0 实测 112/112 绿（core 50 / runtime 34 / cep 28） |
| mjs 门禁（`all` 六命令） | — | §0 实测 exit 0（inventory / sync / scan-iterations / scan-output-contract / scan-wiring / self-test） |
| pin 数（0） | — | §0/§2 pin 空表维持（零 pin 基线，无 stale 无新增） |
| 服务注入 API（7） | 7 | §0 复核表 7/7 零漂移 + §2 接线点 9/9 维持 |
| 消费方（CepOperator / WindowOperator / ProcessOperator） | 3 | §0 复核表 3 类 11 行零漂移 |
| E2E 基线（PT 3/3 + CEP 4/4） | — | Phase 4 Anti-Hollow 实跑 8/8 绿（+ 重连 1/1，2026-08-13） |
| internal-creation carve-out（setKeyedStateBackend / setOperatorStateBackend） | 2 | §0 专项复核成立（main 零调用点，生产直建路径 4 处命中） |

- **Cycle 3 权威 red list 条目**（I3 逐条裁决输入）：C3-RL-1（门禁全绿记录性）、C3-RL-2（注册表 9/9 维持记录性）、
  C3-RL-3（组合面/恢复面核查无缺口记录性）、C3-RL-4（checkpoint 协调面 P2-01/02/03 触发确认）、
  C3-RL-5（triggerAccumulators 裁剪 P2-04 触发确认）、C3-RL-6（evictor descriptor 路径 P2-05 触发确认）、
  C3-RL-7（pane 键错位 + purge 缺 clear P2-06/07 触发确认）、C3-RL-8（STEP-5 超时基准 P2-08 触发确认）、
  C3-RL-9（multi-audit P2-11/01/03/05 预枚举触发确认）、C3-RL-10（open-audit P2-09 claim 过期处置记录）。
  C3-RL-4~C3-RL-9 = 复探确认项（含 live 证据 `文件:行` + 原 backlog 条目追溯），严重度裁决属 I3；
  C3-RL-10 = 处置记录（非缺陷）。**无新族候选**（全部发现归属已知族 #7 或既有 backlog 批次）。
- **每条含裁决信息**：位置（`文件:行`，live 重定位）/ 关联不变式 / 关联 finding（open-audit P2-n / multi-audit P2-n 追溯）/
  族标注 / 验证或探查结论 / 修复方向候选——**I3 可直接裁决，无 NEEDS_SUPPLEMENT 条目**。
- **门禁保持全绿**：mjs `all` exit 0 + JUnit 11 类 112 tests 0 failures + E2E 8/8；本 plan 未改动任何被测类代码
  （Non-Goals 遵守）；pin 空表维持；注册表零修订。
- **门禁统计唯一落点**：`cycle3-I1-input.md`（I1 落档，本 plan 消费未改写；I2 复跑记录在 §0）。
- **族标注汇总**：全部发现属已知族（#7 wiring 族兄弟实例 / open-audit P2 批次 / multi-audit P2 批次）；
  **无新独立族**——I6 按 Loop Rule 无需新增派生输入（同族复探 = 零新实例，plan `2026-08-13-0132-1` Non-Blocking 可闭合）。
- **roadmap 流转**：Cycle 3 / I2 行 `planned` → `done`（closure audit 通过后由 Closure 流程记录，执行结果回写 roadmap）。

---

## 5. 附录：Cycle 2 历史版本（I4 已全部修复，存档）

> Cycle 2 / I2 权威版（2026-08-12）——C2-RL-1/2（RWO/BRWO 跨 task collect(OutputTag) 空体 no-op）已于
> Cycle 2 / I4 修复（plan `2026-08-12-1217-11`，interim fail-fast 落地 + pin 移除 + 断言翻转）；
> C2-RL-3（注册表措辞过 claim）为评估项。本节为历史裁定与修复记录，供追溯；Cycle 3 裁决输入以 §0-§4 为准。

### Cycle 2 §0. 门禁全量运行结果（I2 Phase 1，2026-08-12 实测）

- **mjs `all` 退出码 0**：inventory / sync / scan-iterations / scan-output-contract / self-test 五命令全绿
  （`node ai-dev/tools/check-nop-stream-invariants.mjs all` → exit 0；逐命令输出：
  `inventory: OK` / `sync: OK` / `scan-iterations: OK` / `scan-output-contract: OK` / `self-test: OK`）。
- **处置二分结论**：全部落入「无变化」分支——**无新增 pre-existing residual**（scan-output-contract 无新
  unpinned 违规：V1 类级枚举 / V4 新发射点 / V5 失效点均无命中；scan-iterations 无新违规）、**无行为漂移**
  （2 条过渡 pin 的 live 行为与 pin 描述一致，见 Cycle 2 §1 C2-RL-1/2 复核）、**无 stale pin**（2 条 pin 的 key 与
  scanner 实际违规串匹配，pin 吸收生效——否则 scan-output-contract 将报 unpinned/stale 而 exit ≠ 0）。
- **10 类 JUnit 门禁 + 3 模块表完备性**（`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'`，
  surefire 实测 2026-08-12）：TestCheckpointIDCounterInvariant 8 / TestSynchronizedCollectionInvariant 12 /
  TestOutputContractInvariant 10 / TestInvariantTableCompleteness 10 / TestWindowRoundTripInvariant 9 /
  TestClusterRegistryConsistencyInvariant 10 / TestWindowOperatorMergingCleanupInvariant 4 /
  TestRuntimeInvariantTableCompleteness 11 / TestCepReleaseSymmetryInvariant 21 / TestCepInvariantTableCompleteness 7
  —— **合计 102 tests，0 failures / 0 errors / 0 skipped**，BUILD SUCCESS。与 `cycle2-I1-input.md` 落档一致。
- **注册表自洽性复核表**（live 行号 vs 注册表，全部零漂移 ±0）：

| 类别 | 注册表条目 | live 复核（2026-08-12） | 结论 |
|---|---|---|---|
| 实现类 1/4 | `ChainingOutput.java` :111（forward） | :111 `collect(OutputTag, StreamRecord)` | 一致 |
| 实现类 2/4 | `TimestampedCollector.java` :97（forward） | :97 `collect(OutputTag, StreamRecord)`（:98 转发） | 一致 |
| 实现类 3/4 | `StreamTaskInvokable$RecordWriterOutput` :645（pinned-known-violation） | :645 `collect(OutputTag)` 空体（:646 注释 only） | 一致 |
| 实现类 4/4 | `StreamTaskInvokable$BroadcastingRecordWriterOutput` :705（pinned-known-violation） | :705 `collect(OutputTag)` 空体（无注释） | 一致 |
| 发射点 1/6 | `ProcessOperator.java` :111 | :111 `output.collect(outputTag, ...)` | 一致 |
| 发射点 2/6 | `ProcessOperator.java` :134 | :134 `output.collect(outputTag, ...)` | 一致 |
| 发射点 3/6 | `WindowOperator.java` :1030 | :1030 `output.collect(lateDataOutputTag, ...)` | 一致 |
| 发射点 4/6 | `WindowOperator.java` :1860 | :1860 `output.collect(outputTag, ...)` | 一致 |
| 发射点 5/6 | `CepOperator.java` :483 | :483 `output.collect(lateDataOutputTag, ...)` | 一致 |
| 发射点 6/6 | `CepOperator.java` :777 | :777 `output.collect(outputTag, ...)` | 一致 |
| V1 类级枚举 | 4 个 main `implements Output` 类 | live grep `implements Output`（3 模块 src/main）= 恰好 4 类，全部在注册表 | 一致 |
| 豁免清单 | 4 条 test-only 豁免 | 4 个类/文件名全部位于 `src/test`（`TestOutput` / `TestTimestampedCollectorJavadoc$MockOutput` / `TestMailboxWiring$NopOutput` / `TestWindowOperatorWatermarkReception$CapturingOutput`），src/main 无同名混入 | 一致 |

- **注册表 disposition 措辞与 E2E 实际覆盖不一致（已记录，供 I3 评估，不静默放过）**：
  注册表 6 发射点 disposition 均写 "E2E covered by TestSideOutputChainingE2E"，实测该 E2E
  （`nop-stream-runtime/.../integration/TestSideOutputChainingE2E.java`，3 用例）**仅覆盖 WindowOperator
  late-data 路径**（`WindowOperator.java:1030` sideOutput(lateDataOutputTag) → ChainingOutput → 消费者；
  无消费者 fail-fast；StreamTaskInvokable 接线）——即 6 条中 5 条过 claim（仅 :1030 条属实）。
  `ProcessOperator:111/:134`（ProcessFunction/OnTimer ctx.output）、`WindowOperator:1860`（ProcessWindowFunction ctx.output）、
  `CepOperator:483`（late-data）、`CepOperator:777`（PatternProcessFunction）五个发射点**无 E2E 覆盖**——措辞过 claim。
  列入 Cycle 2 §3 探查盲区 d 评估 + I3 参考。**Cycle 3 状态**：C2-RL-3 已由 Cycle 3 / I1 执行结果处理
  （E2E 基线扩展：`TestProcessingTimeWindowProductionE2E` 3/3 + `TestCepProductionExecutionE2E` 4/4 落档，
  措辞问题随 Cycle 2 / I3 裁决表（C2-RL-3 = P3 backlog）挂接）。
- **处置结论**：无行为漂移 → 无 blocked 升级；无 stale pin → 无 pin 移除；无新增 unpinned 违规 →
  无新增非 pin red list 项。red list 主体 = 2 条过渡 pin 已知实例（Cycle 2 §1）+ 探查发现（Cycle 2 §3）。

### Cycle 2 §1. red list 主体（I2 权威版条目）

#### C2-RL-1. `StreamTaskInvokable$RecordWriterOutput.collect(OutputTag)` 跨 task 空体 no-op（过渡 pin `RWO-cross-task-noop`）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:645-647`
  （`collect(OutputTag, record)` 空体，:646 仅注释「Side outputs not supported in cross-task exchange」）
- **关联不变式**：#6（输出契约族——任何 `Output.collect(OutputTag, X)` 必须转发到注册消费者，不得静默丢弃；无消费者 → fail-fast）
- **关联 finding / 注册表条目**：`output-contract-registry.json` implementationClasses[2]（pinned-known-violation）；
  过渡 pin `mjs-pins.json` `RWO-cross-task-noop`；关联 `HG-01` 人工确认门；I6 双层裁决（adjudication-table.md §I6）
- **族标注**：已知族（不变式 #6 输出契约族；跨 task 实例兄弟，RL-7 修复 `b20fcd0e1` 的同族残余）
- **门禁/pin 来源**：mjs `scan-output-contract` V3 违规串（pin key 精确匹配）；`TestOutputContractInvariant`
  `testCollectOutputTagBehaviorMatchesRegistry` pinned-known-violation 分支反射断言（`partition.size()==0`）
- **验证结论（Phase 1 + Phase 2 live 复核）**：live :645-647 空体确认，行为与 pin 描述 / 注册表分类一致；
  pin key 与 scanner 违规串精确匹配（`compareViolationsToPins` 协议）；行号零漂移
- **裁决输入**：已确认契约缺口（P1，I6 预裁决）；修复方向（Cycle 2 / I4）= 空体 → 抛
  `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常（interim fail-fast，类内部行为修复，`Output` 接口零变更）；
  pin 移除条件 = I4 修复落地 + 注册表分类更新；线协议结构性支持 = `HG-01` 人工确认门（不阻塞 pin 移除）
- **修复状态（Cycle 2 / I4，plan `2026-08-12-1217-11`）**：**已修复**（2026-08-12）——`collect(OutputTag)`
  空体 → 抛 `StreamRuntimeException(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER)`（`ARG_OUTPUT_TAG` / `ARG_DETAIL`，
  镜像 `ChainingOutput.java:119`）；注册表分类迁移 `pinned-known-violation` → `fail-fast`；过渡 pin
  `RWO-cross-task-noop` 移除（removalTrigger 满足，留痕在案，非静默移除）；`TestOutputContractInvariant`
  断言翻转（反射断言 → fail-fast 分支）全绿 + 跨 task E2E 第 4 用例（`testCrossTaskTailOutputFailsFastOnSideOutput`）
  4/4 绿 + core/runtime 全量回归绿（core 1428 / runtime 805，0 failures）。`HG-01` 线协议支持 = 人工确认待办延续

#### C2-RL-2. `StreamTaskInvokable$BroadcastingRecordWriterOutput.collect(OutputTag)` 跨 task 空体 no-op（过渡 pin `BRWO-cross-task-noop`）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:705-706`
  （`collect(OutputTag, record)` 空体，无注释）
- **关联不变式**：#6（同上）
- **关联 finding / 注册表条目**：`output-contract-registry.json` implementationClasses[3]（pinned-known-violation）；
  过渡 pin `mjs-pins.json` `BRWO-cross-task-noop`；关联 `HG-01`；I6 双层裁决
- **族标注**：已知族（不变式 #6 输出契约族，跨 task 实例兄弟）
- **门禁/pin 来源**：mjs `scan-output-contract` V3 违规串（pin key 精确匹配）；`TestOutputContractInvariant`
  pinned-known-violation 分支反射断言（`sideReceived.size()==0`）
- **验证结论（Phase 1 + Phase 2 live 复核）**：live :705-706 空体确认，行为与 pin 描述 / 注册表分类一致；
  pin key 精确匹配；行号零漂移
- **裁决输入**：同 C2-RL-1（interim fail-fast 预授权分派 Cycle 2 / I4；`HG-01` 线协议人工确认门）
- **修复状态（Cycle 2 / I4，plan `2026-08-12-1217-11`）**：**已修复**（2026-08-12）——同 C2-RL-1
  （`collect(OutputTag)` 空体 → 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）；注册表分类迁移 → `fail-fast`；
  过渡 pin `BRWO-cross-task-noop` 移除（留痕在案）；断言翻转 + E2E 第 4 用例 BRWO 路径（2 fanOut writer）
  覆盖在案。`HG-01` 线协议支持 = 人工确认待办延续

#### C2-RL-3. 注册表 disposition 措辞与 E2E 覆盖实际不符（过 claim，评估项）

- **位置**：`ai-dev/audits/nop-stream-invariants/output-contract-registry.json` emissionPoints 表
  （5 条 disposition 写 "E2E covered by TestSideOutputChainingE2E"，实际该 E2E 仅覆盖 WindowOperator late-data 路径）
- **关联不变式**：#6（门禁审计证据准确性）
- **关联 finding**：I2 Phase 1 注册表自洽性复核发现（Cycle 2 red list 首发；I1 落档时未核对该措辞）
- **族标注**：已知族（输出契约族审计证据质量问题，非代码缺陷）
- **门禁/pin 来源**：无（注册表文档措辞；mjs 不消费 disposition 字段，无门禁影响）
- **验证结论**：`TestSideOutputChainingE2E`（3 用例）源码确认仅覆盖 `WindowOperator.sideOutput(lateDataOutputTag)`
  → ChainingOutput 链路；ProcessOperator :111/:134、WindowOperator :1860、CepOperator :483/:777 共 5 个发射点
  无 E2E 覆盖（C2-PR-4 实测，探查报告盲区 d）
- **裁决输入**：I3 参考——修订注册表措辞（准确表述覆盖范围）与/或扩展 E2E 覆盖（6 发射点全路径），
  属优化项（非 defect），不阻塞移交。**Cycle 3 / I3 状态**：C2-RL-3 = P3 backlog（adjudication-table.md §7-§10）

### Cycle 2 §2. 过渡 pin 状态裁定（Phase 2，2026-08-12 回写）

> 2 条过渡 pin（`RWO-cross-task-noop` / `BRWO-cross-task-noop`）逐项 live 复核 + 裁定。结论：**均维持**（跨 task 实例未修复，移除条件 = Cycle 2 / I4 interim fail-fast 落地 + 注册表分类更新，禁静默移除）。

| # | pin id | live 行为（2026-08-12 实测） | 行号 | pin key 匹配 | 三方一致 | 裁定 |
|---|---|---|---|---|---|---|
| 1 | `RWO-cross-task-noop` | `StreamTaskInvokable.java:645-647` `collect(OutputTag, record)` 空体，:646 仅注释「Side outputs not supported in cross-task exchange」 | :645（注册表 645，零漂移） | 精确匹配（scanner V3 违规串 `[scan-output-contract] V3 behavior drift: io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput collect(OutputTag) body-classification=no-op registry-classification=pinned-known-violation (...StreamTaskInvokable.java:645)`；mjs scan 无 unpinned 无 stale = 吸收生效证明） | 一致（注册表 pinned-known-violation ↔ pin known-violation 过渡语义 ↔ JUnit 反射断言 `partition.size()==0`） | **维持**（依据：跨 task 实例未修复；interim fail-fast 预授权 Cycle 2 / I4；`HG-01` 线协议人工确认门未过） |
| 2 | `BRWO-cross-task-noop` | `StreamTaskInvokable.java:705-706` `collect(OutputTag, record)` 空体（无注释） | :705（注册表 705，零漂移） | 精确匹配（同上格式，:705；mjs scan 吸收生效证明） | 一致（注册表 pinned-known-violation ↔ pin ↔ JUnit 反射断言 `sideReceived.size()==0`） | **维持**（依据同上） |

### Cycle 2 §3. 聚焦对抗探查发现（Phase 3，2026-08-12 回写）

> 完整探查报告：`ai-dev/audits/nop-stream-invariants/cycle2-I2-probing-report.md`（Cycle 2 产物）。

- **C2-PR-1（透传链中间形态）**：全部 6 发射点 side-output 直连算子 `output` 字段（ctx.output → output.collect），
  不经 TimestampedCollector（`Collector<T>` 不继承 `Output`，用户函数无法经 collector 发 side-output）；
  JUnit `testTimestampedCollectorWrappingRecordWriterOutputEqualsCrossTaskDrop` 为合成场景但分类语义有效。
  已知族（#6）表达完备性观察，非缺陷。—— **检查后无问题**（盲区 a）
- **C2-PR-2（扫描器静默跳过形态）**：`scan-output-contract` 显式 fail 五路径全部实现 + self-test 覆盖；
  静默跳过形态（匿名类 / record implements Output / raw OutputTag 声明）当前 0 实例，门禁表达扩展候选。
  已知族（#6）门禁扩展候选，非 live defect。—— **盲区 b 结论：显式 fail 面完备 + 零实例 + 候选登记**
- **C2-PR-3（重复注册覆盖）**：`sideOutputConsumers.put` last-wins，同一 OutputTag 重复注册静默覆盖前一消费者，
  无 fail-fast / 无广播语义。不违反 #6（仍转发到注册消费者），生产无重复注册调用面。观察项（I3 可裁 P3）。
  —— **盲区 c 其余时序（wiring 前后注册 / fanOutWriters 多 vertex / 单线程并发）检查后无问题**
- **C2-PR-4（E2E 缺口）**：6 发射点仅 `WindowOperator.java:1030`（late-data）有 `TestSideOutputChainingE2E`
  覆盖；ProcessOperator:111/:134、WindowOperator:1860、CepOperator:483/:777 共 5 个发射点零 E2E 覆盖
  （单元层亦无 OutputTag 发射断言）。缺口确认，优化级候选，**不升格 red list**（与 C2-RL-3 联动，I3 参考）。
- **C2-PR-5（控制面方法族候选）**：RWO :640-642/:650-652（注释文档化）与 BRWO :701-702/:709-710（空体无注释）
  的 `emitWatermarkStatus` / `emitLatencyMarker` 跨 task 空体 = 同根因（跨 task Output 方法无线协议支持）、
  同处置门（`HG-01`）；影响 = 控制面遥测 / 空闲检测降级，非用户数据丢失。**不变式 #6 陈述扩展候选**
  （「跨 task Output 控制面方法不得静默丢弃或显式文档化」），触发证据 `StreamTaskInvokable.java:640-642/:650-652/:701-702/:709-710`，
  供 I6 按 Loop Rule 评估派生 Cycle 3 / I1——**不升格独立新族**。

### Cycle 2 §4. 移交声明（I3 裁决输入就绪，2026-08-12 回写）

- **Cycle 2 权威 red list 条目**（I3 逐条裁决输入）：C2-RL-1（RWO 跨 task no-op，已确认契约缺口，interim
  fail-fast 预授权 Cycle 2 / I4）、C2-RL-2（BRWO 跨 task no-op，同上）、C2-RL-3（注册表 disposition 措辞
  过 claim——5 发射点无 E2E 覆盖却写 "E2E covered"，评估项）；探查发现 C2-PR-1..5（已知族观察 / 门禁表达
  扩展候选 / 不变式 #6 陈述扩展候选，详探查报告），无新增 red list 条目。
- **族标注汇总**：全部发现属已知族（不变式 #6 输出契约族）或其后继扩展候选（C2-PR-2 扫描器形态覆盖 /
  C2-PR-5 控制面陈述扩展）；**无新独立族**——I6 按 Loop Rule 评估 2 个扩展候选即可。
- **修复状态**：C2-RL-1/2 已由 Cycle 2 / I4 修复（interim fail-fast，`HG-01` 延续）；C2-RL-3 = P3 backlog；
  C2-PR-1/2/5 = 关闭（非 defect）；C2-PR-3/4 = P3 backlog（adjudication-table.md §7-§10）。

---

## 6. 附录：Cycle 1 历史版本（I4 已全部修复，存档）

> Cycle 1 / I2 权威版（2026-08-12）——7 条 RL（RL-1..7）已于 Cycle 1 / I4 全部修复
> （plan `2026-08-12-1217-5`，commit `fcc71fc05` / `58255014b` / `b20fcd0e1`），
> 本节为历史裁定与修复记录，供追溯；Cycle 3 裁决输入以 §0-§4 为准。

### Cycle 1 §0. 门禁全量运行结果（I2 Phase 1，2026-08-12 实测）

- **mjs `all` 退出码 0**：inventory / sync / scan-iterations / self-test 四命令全绿。
- **scan-iterations 输出**：live 违规 = 1（`TwoPhaseCommitSinkFunction.java:83` saveState 无锁 copy，
  copy-constructor 口径），被 `mjs-pins.json` 唯一 pin 吸收 → unpinned=0、stale=0。
  **无新增 pre-existing residual、无行为漂移、无 stale pin**。
- **五族 JUnit 门禁 + 3 模块表完备性**（surefire 实测）：TestWindowRoundTripInvariant 9 /
  TestSynchronizedCollectionInvariant 10 / TestCheckpointIDCounterInvariant 8 / TestCepReleaseSymmetryInvariant 21 /
  TestClusterRegistryConsistencyInvariant 8 / TestInvariantTableCompleteness 10 / TestRuntimeInvariantTableCompleteness 11 /
  TestCepInvariantTableCompleteness 7 —— 合计 84 tests，0 failures / 0 errors / 0 skipped，BUILD SUCCESS。
- **4 条已知 residual 复核表**（I2 Phase 1，live 行号 vs I1 pin 记录）：

| # | 位置（I1 pin） | live 行号（本次复核） | 漂移 | pin 状态 | 行为是否仍匹配 I1 断言 |
|---|---|---|---|---|---|
| 1 | `JdbcClusterRegistry.java:112-115`（INSERT lease_expire_at=0L） | :112-115（:115 写 0L） | 无（±0） | mjs 无 pin（JUnit pin） | 是 |
| 2 | `JdbcClusterRegistry.java:172-174`（getActiveNodes 按 > now 过滤） | :172-174（:174 `lease_expire_at > ?`） | 无（±0） | 同上 | 是 |
| 3 | `InMemoryClusterRegistry.java:68-81`（renewLease 忽略 leaseTimeoutMs） | :68-81（:74 只存时间戳） | 无（±0） | 同上 | 是 |
| 4 | `TwoPhaseCommitSinkFunction.java:83`（saveState 无锁 copy） | :83（`new TreeMap<>(pendingCommits)`） | 无（±0） | mjs pin 命中（`mjs-pins.json[0]`） | 是 |
| 5 | `TwoPhaseCommitSinkFunction.java:76-78`（setPendingCommits 接受任意 Map） | :76-78 | 无（±0） | mjs 无 pin | 是 |

**处置结论**：无行为漂移 → 无 blocked 升级；无 stale pin → 无 pin 移除；无新增 unpinned 违规 → 无新增 red list 项。

### Cycle 1 §1. red list 主体（I0 catalog §6 候选，I2 复核确认在册）

#### RL-1. JdbcClusterRegistry.registerNode 写 lease_expire_at=0L（catalog #1）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:112-115`
- **关联不变式**：#5（ClusterRegistry 多实现语义一致性）
- **关联 finding**：R16-AR-9
- **族标注**：F5（ClusterRegistry 族，已知族兄弟实例）
- **门禁/pin 来源**：`TestClusterRegistryConsistencyInvariant.testRegisterNodeVisibilityIsPinnedPerImpl` JDBC 分支
- **验证结论**：live 确认 :112-115 INSERT 写 0L，renewLease 后可见（per-renewal 生效路径绿）；差异属实。
- **裁决输入**：修复方向 = registerNode INSERT 写 `now + leaseTtlMs` 而非 0L；行为影响 = 新注册节点在首次 renewLease 前对调度器不可见。**I4 已修复（commit `fcc71fc05`，P1）**。

#### RL-2. JdbcClusterRegistry.getActiveNodes 按 > now 过滤（catalog #2）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:172-174`
- **关联不变式**：#5；**关联 finding**：R16-AR-9（与 RL-1 同 finding）；**族标注**：F5
- **验证结论**：与 RL-1 构成因果链——INSERT 写 0L ⇒ 过滤条件立即排除新节点。
- **裁决输入**：与 RL-1 联合裁决/修复。**I4 已修复（commit `fcc71fc05`，P1）**。

#### RL-3. InMemoryClusterRegistry.renewLease 忽略 leaseTimeoutMs（catalog #3）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:68-81`
- **关联不变式**：#5；**关联 finding**：R16-AR-18；**族标注**：F5
- **门禁/pin 来源**：`TestClusterRegistryConsistencyInvariant.testRenewLeasePerRenewalTimeoutIsPinnedPerImpl` InMemory 分支
- **裁决输入**：修复方向 = renewLease 记录 `now + leaseTimeoutMs` 并按参数计算活性。**I4 已修复（commit `fcc71fc05`，P2 触发闭合）**。

#### RL-4. TwoPhaseCommitSinkFunction.saveState 无锁 copy（catalog #4）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:83`
- **关联不变式**：#2；**关联 finding**：R16-AR-1；**族标注**：F2
- **门禁/pin 来源**：mjs `mjs-pins.json` pinnedViolations[0] + `TestSynchronizedCollectionInvariant.testSaveStatePinsSnapshotContentComplete`
- **裁决输入**：修复方向 = saveState 内 `synchronized (pendingCommits)` 包裹 copy。**I4 已修复（commit `fcc71fc05`，P0）**。

#### RL-5. TwoPhaseCommitSinkFunction.setPendingCommits 接受任意 Map（catalog #5）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:76-78`
- **关联不变式**：#2；**关联 finding**：R16-AR-11；**族标注**：F2
- **裁决输入**：修复方向 = setter 内部包装 `Collections.synchronizedMap` 或文档化调用方契约。**I4 已修复（commit `fcc71fc05`，P1）**。

#### RL-6. WindowOperator onEventTime cleanup 未 retire 合并窗口（R15-AR-8 升格，I2 新增）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:773-782`
- **关联不变式**：#1 语义扩展——Window 生命周期收敛；**关联 finding**：R15-AR-8；**族标注**：F1
- **验证结论**：动态验证确认——cleanup 后 MergingWindowSet 映射不收敛（无界增长）+ stale 范围复活。
- **裁决输入**：修复方向 = onEventTime cleanup 分支补 `mergingWindows.retireWindow(stateWindow)`。**I4 已修复（commit `58255014b`，P1）**。

#### RL-7. ChainingOutput 静默丢弃 side-output（R15-AR-4 确认仍 live，I2 探查新增）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java:84-86`（原始丢弃点，已修复）
- **关联不变式**：新族候选——输出契约族（后续定稿为不变式 #6）；**对应 finding**：R15-AR-4；**族标注**：新族
- **验证/探查结论**：live 确认丢弃点；默认链式部署下 late-data 侧输出静默丢失。
- **裁决输入**：修复方向 = 转发至下游 side-output 通道或 fail-fast。**I4 已修复（commit `b20fcd0e1`，P1；修复基线 = Cycle 2 / PD-15 不变式 #6 的门禁依据）**。

### Cycle 1 §2. watch-only residual 裁定（I2 Phase 2 动态验证结果）

- **WO-1. LocalFileCheckpointStorage 按文件名 ID 排序（AR-15）→ verified**（`TestLocalFileCheckpointStorage` 13/13 绿；max-ID 排序正确 + 单调恢复级联解除）。
- **WO-2. WindowOperator onEventTime cleanup 未见 retireWindow（R15-AR-8）→ 升格 red list（RL-6）**。
- **WO-3. InputGate per-id inFlightAlignments（R15-AR-9）→ verified**（`TestInputGateMultiEpochBarrier` 5/5 绿；aligned 重叠 + AT_LEAST_ONCE 交错均正确）。

### Cycle 1 §3. 聚焦对抗探查发现（I2 Phase 3）

> 完整探查报告：`ai-dev/audits/nop-stream-invariants/I2-probing-report.md`（Cycle 1 产物）。

- **RL-7**（ChainingOutput 静默丢弃 side-output）→ 已修复（见上）。

### Cycle 1 §4. 移交声明（I3 裁决输入就绪）

- 零悬挂核对：I0 catalog §6 全部 8 项有处置（RL-1..5 在册 + RL-6/RL-7 新增 + WO-1..3 逐项裁定）。**零悬挂达成**。
- I2 未改动任何被测类代码；全部结论基于 live 代码 + 测试载体。

### Cycle 1 §5. 修复状态（I4 执行结果，2026-08-12）

| RL | 严重度 | 修复 commit | 翻转/新增测试 | 门禁复跑 |
|---|---|---|---|---|
| RL-1+RL-2 | P1 | `fcc71fc05` | `testRegisterNodeVisibilityIsPinnedPerImpl` 翻转 + 新增 `testJdbcReregisterAfterLeaseExpiryIsImmediatelyVisible` | 4 失败红 → 10/10 绿 |
| RL-3 | P2 触发闭合 | `fcc71fc05` | `testRenewLeasePerRenewalTimeoutIsPinnedPerImpl` 翻转 + 新增 `testInMemoryRenewLeaseHonorsPerRenewalTimeout` | 同上 |
| RL-4 | P0 | `fcc71fc05` | mjs pin 移除后 scan 变红 → 修复后违规清零；新增 `testSaveStateConcurrentWithCommitAbortIsSafe` | `mjs all` exit 0；core 1418 全绿 |
| RL-5 | P1 | `fcc71fc05` | 新增 `testSetPendingCommitsWrapsUnsafeMap` | 同上 |
| RL-6 | P1 | `58255014b` | `TestWindowOperatorMergingCleanupInvariant` 2 pin 翻转 + 长跑收敛 + processing-time 用例 | 4 失败红 → 4/4 绿 |
| RL-7 | P1 | `b20fcd0e1` | 新增 `TestSideOutputChainingE2E`（3 用例）——先红 3/3，修复后 3/3 绿 | runtime 804 / core 1418 / cep 320 全绿 |

- **修复基线门禁复跑**：五族 JUnit 门禁 92 tests 0 failures；`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS；
  `mjs all` exit 0；`mjs-pins.json` pinnedViolations 已清空（Cycle 1 末尾；Cycle 2 / I1 新增 2 条过渡 pin 为输出契约族，见 §5）。
- **类别清扫结论汇总（四族）**：F5 两实现全部 lease 路径一致；F2 pendingCommits 全使用点锁路径核对；
  F1 MergingWindowSet 生产消费方仅 WindowOperator；输出契约族 ChainingOutput 为唯一 in-task 丢弃点（已修）+
  跨 task no-op（RWO/BRWO）记录为同族已知实例 → 移交 I6 人工确认候选（`HG-01`）。
- **RL-3 backlog 状态**：已由 I4 触发闭合（roadmap Follow-up Backlog 条目状态已更新）。
