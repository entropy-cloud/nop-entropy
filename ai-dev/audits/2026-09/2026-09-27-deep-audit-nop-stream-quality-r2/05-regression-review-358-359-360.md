# 昨日三计划（358/359/360）落地代码正确性回归审查

> 审查基准：HEAD 56e35aeaff（行号为 HEAD 绝对行号）
> 方法：git show diff + HEAD 代码逐项验证；缓存失效语义全查、拆分等价抽查、358 副作用检查

## A. 缓存失效正确性（plan 360）——全部 PASS

| # | 对象 | 结论 | 关键证据 |
|---|------|------|---------|
| A1 | StreamElementCodec valueType→Class 缓存 | PASS | `StreamElementCodec.java:60` 白名单校验在缓存查找之前无条件执行，非法类名每次拒绝；`:63-70` computeIfAbsent 失败不入缓存；缓存键须过白名单且 Class.forName 成功，条目有界 |
| A2 | JEP290 filter CachedConfig | PASS | `StreamDeserializationFilter.java:92-100` 每次调用重读属性，以完整属性值为键比对，值变即重建；`:73` volatile 发布安全 |
| A3 | RocksDB (key,ns)→byte[] 前缀缓存 | PASS（低危注记 C5） | `RocksDBKeyedStateBackend.java:428-439` Objects.equals 双字段失效；值是纯内容编码无序列化器引用；恢复路径重建 state 对象；缓存数组不外泄（RocksDBMapState.buildFullKey 拷贝） |
| A4 | Memory TypedNamespaceAndKey 复用 | PASS | `MemoryKeyedStateBackend.java:437-448` 双入口共用同一槽、同失效判定；不可变 final 字段 + memoized hash 稳定 |
| A5 | RocksDB 聚合 accumulator 前向缓存 | PASS（F1 修复确认在位） | `RocksDBAggregatingState.java:188-193` clear() 无条件 invalidateAccumulatorCache()；`:151-177` ttl 旁路 + 写穿（DB 权威）；applyMigration:94-97 失效；孪生 InternalAggregatingState 继承失效 |
| A6 | SharedBuffer key-scoped accessor 缓存 | PASS（注记 C4） | `SharedBuffer.java:81-83`+`ScopedId.equals:107-112` 跨 key 隔离；`SharedBufferAccessor.java:382-391` 仅 scope==null flush；驱逐不伤进行中 accessor（同一引用） |
| A7 | ScopedId hashCode 预计算 | PASS | `SharedBuffer.java:86-96` scope/id 均 final，构造器一次算出，无过期可能 |

## B. 拆分等价性抽查（plan 359）——PASS

- **B1 InputGate** readMultiChannel/readSingleChannel/emitPendingBarriers：派发序 barrier→watermark→status→data 保留；empty→continue retry 等价；abort 分支逐字保留；emptyRounds 确为死代码删除。
- **B2 NFA** computeNextStates 拆分：TAKE 分支版本"先用后减"语义逐字保留；IGNORE 两路版本计算与减序一致；timeout→剪枝→setNewPartialMatches→advanceTime 顺序未变。
- **B3 StreamTaskInvokable** classifyAndDispatch：派发序/计时 try/finally/barrier 前注入顺序保留；side-output 由 keySet 线性扫描改 `OutputTag` hash 探测——已核实 `OutputTag.equals/hashCode` 仅由 id 决定（构造器拒绝 null/empty id），等价成立。
- **B-extra** CheckpointCoordinator 触发循环删除：周期触发由 JobCoordinator.startPeriodicCheckpoints→triggerCheckpoint→tryTriggerPendingCheckpoint 驱动，链路完整；注记 C3（计数器不再被喂养，可观测性）。

## C. plan 358 副作用

| # | 对象 | 结论 |
|---|------|------|
| C1 | InputGate.close 订阅关闭链 × EOS fail-fast 叠加 | **RISK（live defect，须修）**：`StreamTaskInvokable.invokeSource:713-719`/`invokeMiddle:758-763` finally 为 `closeOutputWriters(); operatorChain.close(); closeInputGate();`——`RemoteResultPartition.close()` EOS 发送失败 typed 抛出（:200-213）经 closeOutputWriters(:598-625) 重抛 → 后两个 close 被跳过 → 订阅/算子泄漏（即 358 欲修缺陷窄路径复活）。`RunningTask.cancel()`(TaskManager.java:1142-1167)/`TaskManager.stop()` 均不关 gate，无兜底。修复：finally 内 try/finally 重排 |
| C2 | 2PC 移出派发线程 | PASS：daemon 单线程 `tm-commit-`（:181-190）+ `:789` execute；注记：execute 未捕获 RejectedExecutionException（stop 后 racing 提交抛 REE 中断循环；由 subsumption/恢复幂等覆盖，低危） |
| C3 | EOS fail-fast | PASS：close() synchronized + isFinished 早退幂等；eosSendError volatile；唯一缺口即 C1 |
| C4 | JdbcTwoPhaseCommitSink 双检锁 + maxBatch | PASS：volatile initialized + fast-path；分段同事务边界不变 |

## 裁定

无阻塞发布的系统性问题；**C1 须立 Fix**（一行级重排 + focused 测试）；C2 注记/C3 可观测性/C4 SharedBuffer.isEmpty（test-only）/C5 可变 key 理论性过期——按 2277 计划裁定。
