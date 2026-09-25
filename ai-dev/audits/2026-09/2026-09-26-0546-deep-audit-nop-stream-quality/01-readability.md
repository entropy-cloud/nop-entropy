# 01 可读性与代码卫生（nop-stream-core / flow / cep）

> 审计代理：独立只读子代理；抽样复核通过。路径相对 `nop-stream/`。

## 超长方法清单（>=100 行，降序，已核实）

| # | 位置 | 行数 |
|---|------|------|
| 1 | nop-stream-core/.../execution/GraphExecutionPlan.java:293-496 `build` | 204 |
| 2 | nop-stream-cep/.../operator/CepOperator.java:306-445 `open` | 140 |
| 3 | nop-stream-cep/.../nfa/NFA.java:631-766 `computeNextStates` | 136 |
| 4 | nop-stream-core/.../environment/StreamExecutionEnvironment.java:337-468 `execute` | 132 |
| 5 | nop-stream-core/.../execution/InputGate.java:569-682 `readMultiChannel` | 114 |
| 6 | nop-stream-cep/.../nfa/NFA.java:237-347 `process` | 111 |
| 7 | nop-stream-core/.../execution/CheckpointBarrierTracker.java:153-257 `acknowledgeOperator` | 105 |
| 8 | nop-stream-flow/.../builder/StreamModelDslBuilder.java:282-382 `buildTransforms` | 101 |
| 9 | nop-stream-core/.../execution/task/StreamTaskInvokable.java:851-951 `processInputGate` | 101 |
| 10 | nop-stream-core/.../execution/ResultPartition.java:176-276 `write` | 101 |

临界（90-99 行）：InputGate.readSingleChannel(97)、InputGate.handleBarrierNonRecursive(95)、NFA.doProcess(89)。

## 超长类清单（>=800 行）

CepOperator(1208)、InputGate(1155)、NFACompiler(1109)、StreamTaskInvokable(1114)、NFA(968)、MemoryStateSerDe(829)、StreamModelDslBuilder(811)。注：NFACompiler 方法粒度健康（Flink 同名类移植），实际多职责的是 CepOperator 与 InputGate。

## 发现清单

**P1**
- [P1] GraphExecutionPlan.build（204 行）：单方法内联 "--- 0. Decompose … 6. Build subtasks" 7 阶段注释。拆分方向：decompose/buildPartitionMatrix/buildWriters/buildInputGate/createSubtasks。
- [P1] CepOperator.open（140 行）：keyed backend 创建/回退、NFA serializer 装配、per-key 定时器账本恢复、65 行匿名 InternalTimerService(365-420)、CepRuntimeContext 匿名类。拆 createTimerService/initStateAccess/resolveKeyedBackend；保留 restore-before-open 不变式注释。
- [P1] NFA.computeNextStates（136 行）：IGNORE/TAKE 双 switch 各自维护 DeweyNumber 版本算术（三个交叉递减计数器）。抽 handleIgnoreEdge/handleTakeEdge + 集中版本递增不变式注释。
- [P1] StreamExecutionEnvironment.execute（132 行）：校验→StreamGraph→JobGraph→PartitionedPlan→DeploymentPlan→checkpoint 分派→本地提交/等待/善后全内联，finally 混 coordinator 注销(459-463)。拆 compilePlans/runLocal/shutdownLocalExecution。
- [P1] 变更考古型注释大面积（全 scope 307 处窄口径 / 77 文件；core+runtime 含 javadoc 宽口径 ~847 行）：InputGate.java:472 "AR-1 (audit ...)…"、ResultPartition.java:199 "Stage 44 successor 4…"、P1-05/P1-INV-2/HG-01/S-6/F-06/G52/review B1 等编号。"旧行为 vs 修复"叙事应迁 commit/设计文档，原地只留当前不变式。前 5 集中：JobCoordinator(88)/StreamTaskInvokable(53)/InputGate(52)/CheckpointCoordinator(43)/GraphModelCheckpointExecutor(40)。
- [P1] InputGate.readMultiChannel（114 行）：pendingBarrier 派发+alignment 超时+round-robin+四路分发+idle 退避+`retry:` 标签，7 段编号注释占约 45 行。抽 pollChannelOnce/emitPendingBarriers + 与 readSingleChannel 共享 idle/EOS 谓词。

**P2**
- [P2] NFA.process（111 行）：抽 timeoutPartialMatches/advanceComputationStates。
- [P2] CheckpointBarrierTracker.acknowledgeOperator（105 行）：231-235 与 238-242 两段完成逻辑逐字相同，合并完成块；按"路由→校验→迁移→回调"拆 3 私有方法。
- [P2] StreamTaskInvokable.processInputGate（101 行）：抽 classifyAndDispatch；sideOutputConsumers 按 tagId 线性扫 keySet（918-923）改 Map 直查。
- [P2] StreamModelDslBuilder.buildTransforms（101 行）：抽 validateDag/topologicalOrderTransforms。
- [P2] ResultPartition.write（101 行）：抽 dualWriteToMaterialization/enqueueWithBackpressure。
- [P2] InputGate.java:211 死字段 `emptyRounds`（全仓唯一出现处）。
- [P2] GraphExecutionPlan.java:390-408 vs 412-428：单/多出边两分支 ~19 行近似复制，抽 createWriterForEdge 共用。
- [P2] CepOperator.java:49,101 同概念双名 timerService vs cepTimerService → internalTimerService/userTimerService。

**P3**
- [P3] PrintSink.java:40、PrintSinkFunction.java:57,59 System.out 逐记录路径（属 print 算子语义，可注入 PrintStream）。
- [P3] InputGate.java:493,618 魔法数字 50ms 轮询 ×2；:680 parkNanos(10_000_000L) 未命名。
- [P3] ResultPartition.java:239-246、MemoryStateSerDe.java:578 中英混排注释。
- [P3] SharedBuffer.java:177-180,329 翻译腔 Javadoc；:293,319 拼写 "shareBufferNode"。
- [P3] CepOperator.java:1131-1159 主源码内 "Testing Methods" 区 5 个 public 测试钩子（生产零引用）→ 收窄包私有或注明。
- [P3] CepOperator.java:38-39,43 NFA_STATE_NAME 值误导、computationStates 实为单值状态。
- [P3] KeyGroupRange.java:99-100 非循环单字母 s/e；MemoryStateSerDe.java:663 `String s`。
- [P3] AdvancedTransforms.java:248-257 面向测试的内建 window 目录硬编码主代码。
- [P3] StreamModelDslBuilder.java:548-605 buildMap/Filter/FlatMap/KeyBy 四连同构 14 行模板 → resolveFunctionOrXpl。
- [P3] SharedBuffer.java:334-349 vs 357-372 getEntry/getEvent 同构 → getWithCache。
- [P3] InputGate.java:213-319 七个伸缩式构造器。
- [P3] CepOperator.java:304-306 缩进异常（3 空格 ×2 行）。
- [P3] InputGate readSingleChannel(471-567)/readMultiChannel(569-682) 平行结构，idle/EOS 判定语义须一致却无共享代码。

## 干净项

错误消息全英文（CJK 全扫描 0 命中）；无 TODO/FIXME/HACK；无被注释掉的代码；无死 public API（抽查 abortBarrierAlignment/getUnalignedThreshold 等均有生产调用方）；关键算法不变式注释（SharedBuffer 跨 key 清理、CepOperator restore-before-open）质量高。

## 总体评价

机器可检查项干净。可读性税集中于：①307+ 处审计阶段编号注释把 readMultiChannel、ResultPartition.write 等关键路径注释噪声推到 40%+；②十个 100-204 行核心方法内部已有阶段编号注释，拆分成本低收益高。优先：先拆 GraphExecutionPlan.build 与 CepOperator.open，再做 top-5 文件的 change-log 注释外迁。
