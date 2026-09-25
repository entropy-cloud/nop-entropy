# nop-stream 深度审计（质量与性能）— 总体发现摘要

> Audit Date: 2026-09-26 05:46
> Scope: `nop-stream/` 全部 10 个子模块的 `src/main`（排除 `_gen/`、`_` 前缀生成物、`src/test`；fraud-example/quickstart 仅卫生抽查）
> Method: 4 个独立只读审计子代理（可读性卫生 / 结构可维护性 / 性能热点 / 连接器与并发）+ 2 个独立复核子代理逐条裁决（P0/P1 全复核，35 条裁决：32 CONFIRMED / 3 PARTIAL 修正 / 0 REJECTED）
> Baseline: `./mvnw compile -pl nop-stream/{core,runtime,cep,flow,rocksdb,connector} -am` 全绿；全模块 main 约 102,973 行（core 45,219 / runtime 26,528 / cep 11,147 / flow 8,230 / rocksdb 4,237 / connectors ~3,907 / fraud-example 3,256）

## 一句话结论

机器可检查项相当干净（错误消息全英文、0 TODO/FIXME、无注释掉的代码块、No-Silent-No-Op 约定执行良好），真正的债集中在四处：**数据面热路径上的每记录级序列化/分配开销（5 条 P0）**、**资源生命周期缺口（订阅泄漏/启动泄漏/2PC 占用派发线程，3 条 P1）**、**巨型类的结构熵（7 个 ≥1100 行类 + 三套重复 checkpoint 触发循环）**、**约 847 行变更考古型注释覆盖核心路径**。

## P0（live defect，须修）

| # | 位置 | 问题 | 报告 |
|---|------|------|------|
| P0-1 | `nop-stream-rocksdb/.../RocksDBAggregatingState.java:131-151` | 聚合窗口每条记录 RocksDB get+JSON 反序列化+agg+序列化+put（RMW 双 JSON） | 03-performance |
| P0-2 | `nop-stream-rocksdb/.../RocksDBListState.java:116-128` | add() 读出整列表再写回，窗口内 O(n²) | 03-performance |
| P0-3 | `nop-stream-cep/.../operator/CepOperator.java:972-983` | 事件时间定时器触发批次对整个 NFAState 做 Java 序列化往返（处理时间模式每元素一次）；状态随匹配数线性增长 | 03-performance |
| P0-4 | `nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java:197-306,388-391` | 每事件写穿状态 + accessor close 即 flushCache 清空全部缓存 → LRU 恒冷（清空本身是跨 key 正确性所需，修复须 key-scoped） | 03-performance |
| P0-5 | `nop-stream-core/.../transport/StreamElementCodec.java:58,121-168` + `nop-stream-runtime/.../transport/KafkaStringWireCodec.java:48,61` | 远程边每记录 4 次 JSON 编解码 + Class.forName 无缓存 + 每记录白名单扫描 | 03-performance |

## P1（显著，须修或裁定）

**正确性/资源**（04 报告 + 复核确认）：
- RemoteInputChannel 订阅永不关闭（`RemoteInputChannel.java:248` subscribe，close():472-485 全仓零调用方）→ 每次部署/重部署泄漏一个后端订阅
- EmbeddedDistributedExecutor 启动失败路径不回收已启动 TaskManager（:142-152，RPC 版有 teardownOnStartupFailure 先例、embedded 版缺失）
- 2PC commit（JDBC/文件）同步执行在 message-service 派发线程 / coordinator ACK 线程（TaskManager.java:1131-1160、JobCoordinator.java:1139-1181），慢提交拖死心跳与 ACK 派发
- 有界流 EOS 发送失败仅 WARN 且 close 已停心跳、生产 builder 传 channelTimeoutMs=0 禁用超时兜底 → 下游可永等（RemoteResultPartition.java:189-193 + RemoteGraphExecutionPlanBuilder.java:176）

**结构**（02 报告）：
- GraphModelCheckpointExecutor(2009 行，全静态 11 职责)/JobCoordinator(2366 行，10+ 职责)/WindowOperator(2331 行)/CheckpointCoordinator(1852 行)/TaskManager(1202 行) 职责过载（各类候选抽离切面见 02 报告末节）
- 三套周期 checkpoint 触发循环并存，其中 CheckpointCoordinator.startCheckpointScheduler(:348-414) 生产不可达（仅测试调用）
- WindowOperator.onEventTime/onProcessingTime 约 80 行近逐字复制（仅 3 处差异）
- 10 个 ≥100 行方法（最大 GraphExecutionPlan.build 204 行）

**可读性**（01 报告）：
- ~847 行变更考古型注释（AR-n/Stage n/P1-INV-n/HG-n 等内部编号）覆盖 core+runtime，前 5 集中文件：JobCoordinator(88)/StreamTaskInvokable(53)/InputGate(52)/CheckpointCoordinator(43)/GraphModelCheckpointExecutor(40)

## P2/P3 摘要（详见分项报告）

- 资源/并发 P2：JdbcTwoPhaseCommitSink unsafe lazy init、heartbeat 后半段无守卫（可杀死调度）、injectElements/close EOS offer 返回值忽略、DataPlaneMessageServiceAdapter 丢弃不可解码记录、TaskNodeMetrics/EngineMetrics gauge 静态泄漏+stale supplier、JDBC 批无上限、FileTwoPhaseCommitSink 无 fsync
- 可读性 P2/P3：死字段（InputGate.emptyRounds、FileSplitEnumerator.nextSubtaskIndex）、CheckpointBarrierTracker 完成块逐字重复、GraphExecutionPlan 单/多出边 ~19 行复制、StreamModelDslBuilder 四连同构、14 处手写 daemon ThreadFactory、魔法数字（50ms 轮询/10ms park）、中英混排注释、命名（timerService vs cepTimerService、KeyGroupRange s/e）
- 性能 P1/P2（复核确认）：Micrometer 每 record 分配 Duration×2 + 4 次 nanoTime、MergingWindowSet 每记录全量重建、storeElementTimestamp 整列表 get+put、RocksDBKeyEncoder 每访问重编码、JEP290 filter 每次重建、MemoryKeyedStateBackend 每次 new TypedNamespaceAndKey + Objects.hash 装箱、InputGate 空通道固定 50ms 阻塞轮询、RemoteResultPartition.write 全程 synchronized 且锁内同步 send、CEP getSortedTimestamps 每 timer 批全扫 elementQueueState

## 干净项（避免误报）

- 错误消息语言全合规（CJK 全扫描 0 命中于字符串/异常）；连接器四模块完全遵循 NopException+ErrorCode+.param() 两档规范
- 无 TODO/FIXME/HACK 残留；无被注释掉的代码块；无 `catch(Exception e){}` 真静默吞没（470 处 catch 中空体仅 3 处且均有注释）
- core→runtime 依赖单向（core 0 反向 import）；getter 普遍返回 unmodifiable；线程全部命名且 daemon
- 线程卫生高质量：SupervisionLoop 有界等待+fail-loud、recoveryPending CAS 去重、InputGate CME 防护、TaskManager 信号量守恒

## 复核修正记录（防以讹传讹）

1. P0-3 量级修正：事件时间模式下 NFAState 全量序列化发生在每次事件时间定时器触发（按时间戳分批），非字面每元素一次；处理时间模式（comparator==null）才是每元素一次。
2. SharedBuffer flushCache 不可简单移除：EventId/NodeId 不含 key，清空是跨 key 正确性所需；修复方向是 key-scoped 缓存键。
3. "triggerCheckpoint 返回 null 作控制流"实际位置在 JobCoordinator.java:784-845（非 GraphModelCheckpointExecutor），且 CheckpointCoordinator 已有带 TriggerRejectionReason 的正确 API，属 API 风格双轨问题。
4. 三套触发循环中 CheckpointCoordinator 自带的一套生产不可达（仅 3 处测试调用），运行时最多两套并存。
5. WindowOperatorFactoryImpl dummy serializer 的 copy/createInstance 全仓无活跃调用点，定性为契约错误+潜在风险（非当前活跃缺陷）。

## 修复归属

- 计划 358（正确性/资源 Fix）：订阅泄漏、启动回收、2PC 派发线程、EOS fail-fast、unsafe lazy init、heartbeat 守卫、offer 返回值、DataPlane 毒丸、gauge 泄漏、ACK 有限重试、dummy serializer 契约
- 计划 359（可读性/结构重构，行为保持）：超长方法拆分、onEventTime/onProcessingTime 合并、死代码、重复块、触发循环收敛、ThreadFactory 工具、注释卫生、命名/魔法数字
- 计划 360（性能，JMH+JFR 迭代至无 ≥2% 收益）：基准模块 + 基线，然后逐轮优化（Class/filter/键编码缓存、指标打点降耗、MergingWindowSet 复用、SharedBuffer key-scoped 缓存、RocksDB 聚合路径、InputGate 非阻塞轮询等），每轮 JFR 观测 + 基准对比，收益 <2% 即停
