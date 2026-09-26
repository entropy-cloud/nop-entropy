# nop-stream 可读性/长期可维护性增量审计（R2）

> 基准：HEAD 56e35aeaff。全部行号本次实读。路径省略 `nop-stream/` 前缀。
> 已排除 359 已修复项（超长方法 10+1、onTimer 模板、死代码、重复块 top-2、ThreadFactory、top-5 注释、命名/魔法数字）。

## 一、超长方法（>150 行，本轮新发现）

| 方法 | 行区间 | 行数 | 备注 |
|------|--------|------|------|
| `runtime/.../execution/SupervisionLoop.rebuildTask` | 592-805 | 214 | 内嵌四段职责：checkpoint 恢复(629-681)/consumer 通道重建+replay(683-758)/producer writer 复用(759-785)/checkpoint 管线重接(787-799)；620 行注释 30+ 行 |
| `runtime/.../transport/RemoteGraphExecutionPlanBuilder.buildRemoteOnly` | 126-334 | 209 | 自带 --- 1..4 分段注释；336 行注释自认与 GraphExecutionPlan 逻辑重复 |
| `runtime/.../checkpoint/reshard/MaxParallelismReshardMigration.reshardCheckpoint` | 96-279 | 173（签名 96 起） | 迁移校验+状态重分组+manifest 重建多职责 |
| `runtime/.../operators/windowing/WindowOperator.open` | 386-535 | 150（恰在上限） | 零余量，触碰时顺手拆 |

## 二、重复代码（≥15 行近逐字）

1. **[P1]** `core/.../execution/GraphExecutionPlan.java:591-840` ↔ `runtime/.../transport/RemoteGraphExecutionPlanBuilder.java`（resolveParallelism/resolvePartitionPolicy/resolveEdgeConfig/topologicalSort 四 helper 双份 + fan-out 装配重复 ~100 行；Local 侧已抽 createWriterForEdge，Remote 未复用）→ core 建共享装配器，Remote 传 transport 工厂
2. **[P2]** `core/.../memory/MemoryStateSerDe.java:400-455` ↔ `rocksdb/.../RocksDBSnapshotSerDe.java:484-530` mapValue 逐 pair 校验循环近逐字（同错误消息）→ 抽共享校验器（core 层）
3. **[P2]** `MemoryStateSerDe.java:546-574` ↔ `RocksDBSnapshotSerDe.java:661-689` inferAccumulatorType 连 11 行 Javadoc 双份 → 上移 core
4. **[P3]** `core/.../typeutils/JavaStreamSerializer.java:45-74` ↔ `JsonToolSerializer.java:11-39` 6 样板方法双份；JsonToolSerializer 全仓唯一缺版权头 → AbstractDelegatingSerializer/default + 补头
5. **[P3]** `runtime/.../cluster/TaskAssignment.java:19-58`、`coordinator/TaskStatusReport.java:33-60`、`rpc/TaskDeploymentDescriptor.java` 五元组（jobId,vertexId,subtaskIndex,attemptNumber,fencingEpoch）三份手写 → TaskIdentity 值对象
6. **[P3]** `WindowOperator.java:733-768` ↔ `778-804` merging/regular 骨架同构 ~30 行 → 参数化差异点

## 三、巨型类（≥800 行，wc -l 实测）

| 类 | 行数 | 相对 359 清单 |
|----|------|--------------|
| JobCoordinator | 2317 | 已知 |
| WindowOperator | 2283 | 已知 |
| GraphModelCheckpointExecutor | 1996 | 已知 |
| CheckpointCoordinator | 1758 | 已知 |
| TaskManager | 1268 | 已知，**+66（358 新代码逆势增长）** |
| CepOperator | 1258 | **新发现** |
| InputGate | 1235 | **新发现** |
| StreamTaskInvokable | 1153 | **新发现** |
| NFACompiler | 1109 | **新发现** |
| NFA | 1104 | **新发现** |
| CheckpointSerDe | 945 | 新发现 |
| RocksDBKeyedStateBackend | 933 | 新发现 |
| JdbcCheckpointStorage | 918 | 新发现 |
| SupervisionLoop | 882 | 新发现 |
| GraphExecutionPlan | 840 | 新发现 |
| StreamModelDslBuilder | 834 | 新发现 |
| MemoryStateSerDe | 829 | 新发现 |

## 四、抽离切面评估（行为保持）

### JobCoordinator（2317）
| 切面 | 行区间 | 协作者 | 风险 | 建议 |
|------|--------|--------|------|------|
| Fencing epoch 派生/轮换 | 132-159 + 1499-1650 | FencingEpochManager | 中（recoveryLock 所有权移交） | 纳入 |
| 双预算重启策略 | 237-272 + 1388-1417 | RestartBudget | 低（纯状态机） | 纳入 |
| subtask 存活跟踪 | 203-220 + 980-1008 + 1186-1260 | SubtaskLivenessTracker | 低 | 纳入 |
| HA 领导生命周期 | 140-168 + 1651-1823 | LeaderLifecycle | 中高 | 否 |
| 终止四模式 | 1824-2053 | TerminationFlow | 中 | 否 |
| getter/setter 块 | 2146-2296 | — | 低但面宽 | 否 |
| 风险注记 | recoveryPending CAS 协议（294-321 注释）时序敏感（1449-1482），拆分必须整段搬移不得重排 | | | |

### WindowOperator（2283）
| 切面 | 行区间 | 协作者 | 风险 | 建议 |
|------|--------|--------|------|------|
| NamespaceAware 状态适配器 | 1815-2050（5 个 static 内部类） | 包级文件 | 低（零风险速赢） | 纳入 |
| 字符串键窗口内容通道 | 250-258 + 1335-1715 + 1092-1104 | WindowContentStore | 中高（\u0000 键格式已进 checkpoint，必须逐位不变） | 否（Deferred） |
| Pane 追踪 | 192-198 + 1064-1229 | WindowPaneTracker | 中（checkpoint DTO） | 否 |
| 溢出构造器 16/15/19 参 | 260-380 | WindowSpec + 旧构造器委托 | 低 | 纳入 |
| keyed-state 两 store | 1727-1813 | 随适配器外移 | 低 | 纳入 |

### GraphModelCheckpointExecutor（1996，全静态）
| 切面 | 行区间 | 协作者 | 风险 | 建议 |
|------|--------|--------|------|------|
| 恢复域 | 1081-1617 | CheckpointRestoreService | 中（测试直调包私有静态→保留签名委托） | 纳入 |
| Rescale 状态装配 | 1618-1996 | RescaleStateAssembler | 低（纯函数族） | 纳入 |
| 任务/Tracker 接线 | 620-787 + 945-1009 | TaskCheckpointWiring | 低 | 纳入 |
| Barrier 调度器 | 788-865 + 1010-1033 | BarrierScheduler | 低 | 否 |
| 入口重载扇出 | 109-198 | CheckpointExecutionSpec | 低 | 否 |

### CheckpointCoordinator（1758）
| 切面 | 行区间 | 协作者 | 风险 | 建议 |
|------|--------|--------|------|------|
| 增量 checkpoint 支撑 | 136-161 + 576-781 + 1526-1757（~500 行） | IncrementalCheckpointSupport | 中高（monitor 保护 + 注册表生命周期跨 shutdown） | 否（专项） |
| 保留清理 | 1195-1381 | RetentionCleaner | 中低（协议注释完整，整体可搬） | 纳入 |
| 观测历史 | 256-264 + 1094-1135 | CheckpointHistory | 低 | 纳入 |

### TaskManager（1268）
| 切面 | 行区间 | 协作者 | 风险 | 建议 |
|------|--------|--------|------|------|
| RunningTask | ~940-1268 | 顶层类 | 低（保留 semaphoreReleased CAS + put 原子置换契约 548-563 注释） | 纳入 |
| 槽位表 | 109-120 + 534-613 | TaskSlotTable | 中（许可守恒不变量） | 否 |
| ACK 发送 | 100-104 + 765-861 | CheckpointAckSender | 低 | 纳入 |

### InputGate（1235，附）
七构造器（230/241/254/269/300/326/336）收拢为全参构造器 + GateConfig（BarrierAlignment 枚举替代 boolean），旧签名保留委托；调用方 3 处生产入口（SupervisionLoop:756、GraphExecutionPlan.buildInputGate:537、RemoteGraphExecutionPlanBuilder:295）。对齐状态机抽 BarrierAligner（中风险）→ Deferred。

## 五、可读性硬伤

- **[P2] 注释考古债再生产**：昨日三计划新引入 37 处 "Plan 358/359/360" 编号注释（15 文件：TaskManager 5 处如 :100-104、SharedBuffer 4 处如 :73-79、RocksDBAggregatingState :130/:188 等）→ 去编号保留语义
- **[P2] 考古注释长尾 top-10**：TaskManager 33、CheckpointSerDe 32、RocksDBKeyedStateBackend 26、NopStreamErrors 25、RemoteInputChannel 23、CepOperator 22、CheckpointBarrierTracker 19、SupervisionLoop 18、WindowOperator 17（如 WindowOperator.java:476 "AR-22 (P0)"、713/720/760 "P1-INV-1/AR-4"、1128 "G48"）
- **[P2] 中文注释 66 文件**（CheckpointType.java:17-96 全中文 Javadoc、CheckpointIDCounter.java:14-61、IKeyedStateBackend.java:14-20 等）→ 分批英化登记 follow-up
- **[P2] 布尔陷阱**（代表性调用 SupervisionLoop.java:756 `new InputGate(newChannels, (EdgeConfig) null, false)`）：JobCoordinator.globalRecovery(boolean):1375、rotateFencingEpochCoreLocked(boolean):1540、terminateWithTerminalSavepoint(boolean):1906、executeWithCheckpointSkeleton(boolean):199、restoreAggregatingState(boolean)（Memory:497/RocksDB:586）、getOrCreateState(boolean adoptSerializer):191、InputGate 三构造器 barrierAlignment（:254/:269/:300）
- **[P2] 参数过多 >6**：GraphExecutionPlan.createSubtasks:409(20)、WindowOperator 溢出构造器 :260/:275/:291(16/15/19)、IWindowOperatorFactory.createAggregateOperator:22/:41(14/15)、EpochManifest:64-115(10-14)、AssignmentPlanner:76(12)、SupervisionLoop.restartRegion:418(11)/run:245(10)、TaskDeploymentDescriptor:114(10)、DeploymentPlan:35-67(10)、SourceEnumeratorState:34-44(10)、GraphModelCheckpointExecutor.submitAndRun 族(9-10)、NFA.addComputationState:877/:904(8)、CepOperator 构造器:240-256(8)
- **[P3] API 双轨**：InputGate 读取面 Optional<StreamElement> vs CheckpointCoordinator.getPendingCheckpoint:984 null；TriggerOutcome 与 Optional 风格并存
- **[P3] 双命名委托**：JobCoordinator.receiveCheckpointAck:877-880 单行委托 collectAck
- **[P3] SharedBuffer 双 regime**（scope null flush / 非 null 免清）并存一类 → Javadoc 补真值表
