# Cycle 3 / I1 输入统计（生产 wiring 存在性门禁结果，唯一落点）

> Status: active（Cycle 3 / I1 收口输入，2026-08-13 实测）
> Source: plan `ai-dev/plans/2026-08-13-0805-1-nop-stream-invariants-cycle3-I1-wiring-existence-gates.md`；surefire 报告 + mjs 工具输出
> Note: 本文件是 Cycle 3 门禁统计的**唯一落点**，供 Cycle 3 / I2 消费；`cycle1-I6-input.md` / `cycle2-I1-input.md` 已被消费，不回写不追加。

## 门禁总数（JUnit 门禁 11 个测试类，112 tests，0 failures）

实测命令：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` → BUILD SUCCESS。

| 模块 | 测试类 | 计数（surefire 实测） |
| --- | --- | --- |
| nop-stream-core | TestCheckpointIDCounterInvariant（门禁③） | 8 |
| nop-stream-core | TestSynchronizedCollectionInvariant（门禁②） | 12 |
| nop-stream-core | TestInvariantTableCompleteness（表完备性） | 10 |
| nop-stream-core | TestOutputContractInvariant（不变式 #6） | 10 |
| nop-stream-core | **TestWiringExistenceInvariant（I1 新增，不变式 #7 wiring 存在性）** | **10（注册表驱动参数化 7 服务 + API 面完备性反射 + 消费方表自洽 + 接线连通断言）** |
| nop-stream-runtime | TestWindowRoundTripInvariant（门禁①） | 9 |
| nop-stream-runtime | TestClusterRegistryConsistencyInvariant（门禁⑤） | 10 |
| nop-stream-runtime | TestWindowOperatorMergingCleanupInvariant（RL-6） | 4 |
| nop-stream-runtime | TestRuntimeInvariantTableCompleteness（表完备性） | 11 |
| nop-stream-cep | TestCepReleaseSymmetryInvariant（门禁④） | 21 |
| nop-stream-cep | TestCepInvariantTableCompleteness（表完备性） | 7 |
| **合计** | **11 个门禁类（10 → 11）** | **112 tests / 0 failures / 0 errors** |

## 生产 wiring 存在性门禁细节（I1 新增，不变式 #7）

- **JUnit**：`TestWiringExistenceInvariant`（nop-stream-core，10 用例）——注册表驱动参数化 7 个服务注入 API（`setOutput` / `setProcessingTimeService` / `setTimeServiceManager` / `setStateBackend` / `setSnapshotCallback` = production-wired；`setKeyedStateBackend` / `setOperatorStateBackend` = internal-creation carve-out）：API 面完备性（反射枚举 `AbstractStreamOperator` 全部 `set*` 注入方法，参数类型 ∈ 已登记服务类型——`setKeyContextElement1/2` 等不参与；每个已登记 API 存在且参数类型匹配（含 `Consumer<OperatorSnapshotResult>` 泛型实参）+ disposition 自洽）；接线运行时连通断言（`new StreamTaskInvokable(chain)` 后算子 `getProcessingTimeService()`/`getTimeServiceManager()` 非 null + 与 invokable 同一实例 + open 后 `numTimerServices()==1`）；消费方表自洽。先红后绿证据在案（删除注册表 `setTimeServiceManager` 条目 → API 面完备性红 + 消费方表红 → 恢复绿）。
- **接线点注册表**：`wiring-registry.json`（services 表 7 条 × 生产接线点（文件:行 + 注入时机：构造 PTS/TSM / 装配布线 Output / checkpoint 装配 stateBackend / 快照 snapshotCallback）× 消费方枚举 × disposition × test-only 豁免）——`setKeyedStateBackend`/`setOperatorStateBackend` = `internal-creation`（main 零调用点，生产经 `stateBackend.createKeyedStateBackend()` 在 open() 内直建，`CepOperator.java:257` / `WindowOperator.java:421` / `AbstractStreamOperator.java:65`；V1 carve-out 附理由受棘轮约束，非静默豁免）。
- **mjs**：`scan-wiring` 子命令（V1 仅测试注入检测（receiver 限定成员访问形态；production-wired main=0 → 红）/ V2 消费方枚举完备性 / V3 失效接线点 / V4 新注入 API / V5 失效消费方/服务）——独立运行 + `all` 均 exit 0；self-test 正反例覆盖 V1–V5 各一正一反；未识别形态显式 fail（main 侧未解析 receiver 硬 fail；声明-调用用续接字符判定）。
- **live 调用点统计（V1 全绿基线）**：setOutput main=5 test=193；setProcessingTimeService main=1 test=0；setTimeServiceManager main=1 test=3；setStateBackend main=1 test=38；setSnapshotCallback main=1 test=25；setKeyedStateBackend main=0 test=30（internal-creation，不红）；setOperatorStateBackend main=0 test=1（internal-creation，不红）。消费方 owners：`getProcessingTimeService()` = {CepOperator, WindowOperator}；`getTimeServiceManager()` = {}（main 零调用）；`registerTimerService(` = {ProcessOperator, WindowOperator}。

## E2E 基线（归属定案）

实测命令：`./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestProcessingTimeWindowProductionE2E` + `./mvnw test -pl nop-stream/nop-stream-cep -Dtest=TestCepProductionExecutionE2E` → BUILD SUCCESS。

- `TestProcessingTimeWindowProductionE2E`（nop-stream-runtime）**3/3 绿**：PT 窗口生产驱动 fire / cleanup 清态 / 无 driver 显式 WARN 断言。
- `TestCepProductionExecutionE2E`（nop-stream-cep）**4/4 绿**：event-time 生产路径 / PT 模式生产路径 / open 无服务不 NPE（guard+WARN）/ **PT 模式无服务 processElement fail-fast**（`StreamException` + "ProcessingTimeService" 参数）。无服务 fail-fast 行为归属 cep/runtime 既有 E2E（core 依赖方向约束），core 门禁不建副本。

## pin 数

- mjs pin：**0**（`mjs-pins.json` pinnedViolations = 0；`internal-creation` = 注册表 disposition 非 pin；预期零 pin 起步，已裁定 watch-only residual 如需登记以扫描器实际违规串为准）。

## red list 状态（Cycle 3 / I2 输入）

- 不变式 #7 门禁全绿（JUnit 11 类 112/112 + mjs `all` exit 0 含 `scan-wiring` + E2E 3/3 + 4/4）——为 Cycle 3 / I2 提供确定性全绿基线；I2 以此 + 对抗探查（checkpoint/watermark 服务同族"仅测试注入"复探等）产出 red list。
- 全量回归：`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（**2895 tests / 0 failures / 0 errors**，含新门禁类）。
