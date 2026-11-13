# concurrency-audit-remediation 合并批次：无界并发 Map（频控/去重/锁存族）复查

> Status: resolved
> Date: 2026-11-14
> Scope: merge commit 44719346a9（concurrency-audit-remediation 全量合入，1577 文件）主代码侧全部静态/实例级 `ConcurrentHashMap` 频控、冷却、去重、per-key 锁存、generation 记账字段
> Conclusion: 16 处候选中 1 处 LEAK（`RetryEngineImpl.recordGenerations`，已修）+ 7 处 MINOR（3 处已修、4 处 watch-only）+ 8 处 OK。无其他 LEAK 级问题。

## Context

- 起点：`WeightedPartitionAssigner.NON_POSITIVE_WEIGHT_WARN_AT`（per-instanceId 时间戳频控 map，put 后永不 remove，实例下线即残留）被识别为同族缺陷代表。
- 审计问题：整个并发审计修复批次合入 master 的代码中，是否还有同类「动态 key 写入、无清理路径、长期运行无界增长」的并发 Map。
- 方法：对 merge 分支侧 diff（+161k 行）grep 全部新增 `ConcurrentHashMap` 字段，按字段名/语义筛出频控/去重/冷却/锁存/generation 记账候选 16 处，逐一读源码核实 key 基数与清理路径。

## 判级标准

- **LEAK**：key 按请求/记录级唯一（无配置上界），且全路径无 remove → 长期运行单调增长。
- **MINOR**：key 动态但有界（实例数/用户数/索引数/topic 数），残留量 = 曾出现过的 key 总数 × 每 entry 几十字节；或仅在特定路径残留。
- **OK**：key 配置封闭、终态全路径 remove、或有硬上界兜底。

## 审计结论清单

### LEAK（已修复）

| 字段 | 位置 | 问题 | 修复 |
|---|---|---|---|
| `RetryEngineImpl.recordGenerations` | `nop-retry-engine/.../RetryEngineImpl.java:78` | key=recordId（每条重试记录唯一），OVERWRITE 路径 `computeIfAbsent` 递增（:159），**全文无任何 remove**；DB 账本清理链（`runLedgerCleanup`）只 purge DB 行不触碰此 map → 增长率 = OVERWRITE 重建次数，方向无界 | 新增 `clearGeneration(recordId)`，在三个终态收口点调用：`handleExecutionSuccess`（COMPLETED）、两个 `moveToDeadLetter` 重载（死信终态）。回收后 `currentGeneration` 返回 0，持有非零代际的旧链立即失配短路（保守方向安全）；OVERWRITE 重建经 `computeIfAbsent` 从 1 重算，与旧值无关联 |

### MINOR（已修复 2 处 + watch-only 1 处）

| 字段 | 位置 | 问题 | 处置 |
|---|---|---|---|
| `WeightedPartitionAssigner.NON_POSITIVE_WEIGHT_WARN_AT` | `nop-cluster-core/.../WeightedPartitionAssigner.java`（原 :27） | 原始代表案例：时间窗频控 map，实例下线残留时间戳条目 | **重设计为轮次 diff 告警**：每轮分配收集 offender（instanceId→原始权重）到局部 map，轮末仅对上一轮未出现的 key WARN（R25-1 载体断言：WARN 留痕原始权重值，形态不变），然后 volatile 整体替换。条目集合有界于本轮 server 列表，下线实例自动回收。语义变化：告警粒度从「per-instance 1s 窗口」变为「每分配轮一次」（持续存在的非法权重只在新出现时告警一次，消失后复现再告警） |
| `GraphMetricMaterializer.failureCooldownUntil` | `nop-code-service/.../GraphMetricMaterializer.java:85` | 失败 put / 成功 remove，但 `invalidate(indexId)` 路径不清除 → 索引失败后删除即残留 | `invalidate` 入口补 `failureCooldownUntil.remove(indexId)` |

watch-only（登记不改）：

| 字段 | 位置 | 理由 |
|---|---|---|
| `AvroSchemaRegistryClient.readerSchemaFailures` | `nop-stream-format-avro/.../AvroSchemaRegistryClient.java:80` | TTL 过期仅读时惰性判断，entry 永久残留；key=subject 准有界，量级可忽略，收益不抵改动。同文件 `schemaByIdCache`/`latestBySubject` 同型（schema id 有界） |

### MINOR（watch-only，不改）

| 字段 | 位置 | 残留形态 | watch-only 理由 |
|---|---|---|---|
| `InMemoryTeamManager.teamBindLocks` | `nop-ai-agent/.../InMemoryTeamManager.java:78` | per-teamId 锁对象，`disbandTeam` 不清 | 与 `teams` 主索引同基数同步增长（teams 本身声明永不清除，供历史/审计）；锁 map 不比主索引更差。**若未来给 teams 加淘汰，须同步补 remove** |
| `NopDatavExportTaskBizModel.userExportSemaphores` | `nop-datav-service/.../NopDatavExportTaskBizModel.java:129` | per-user 信号量，无 remove | 有界于注册用户数；代码 ：125-128 已自我声明裁定。附注：max 在创建时固化，配置下调不生效于既有用户 |
| `OrmMigrationOrchestrator.fixForwardRetries` | `nop-orm-migration/.../OrmMigrationOrchestrator.java:108` | jobId×phase 计数，无 remove | 迁移作业低频运维动作，量级 = 历史作业数 × 2，计数封顶 MAX_FIX_FORWARD_RETRIES=3 |
| `GraphMetricMaterializer.materializeGates` | `nop-code-service/.../GraphMetricMaterializer.java:77` | per-indexId 锁，invalidate 不清 | **有意保留**：物化进行中移除锁条目会使并发方拿到不同锁对象，破坏 single-flight 互斥（锁 map 移除的经典竞态）；与索引集合同基数有界 |
| `LocalMessageService.consumers`（空 topic 残留） | `nop-message-core/.../LocalMessageService.java:127` | `Subscription.cancel` 只 `list.remove(this)`，列表空后不删 topic key | topic 准有界；正确修复需 subscribe 侧 computeIfAbsent→remove 竞态闭环（subscribe 取到已 detach 列表丢失订阅），改动面大于收益 |

### OK（8 处，核实通过）

| 字段 | 位置 | 有界依据 |
|---|---|---|
| `GenerationFence.fences` | `nop-orm/.../GenerationFence.java:36` | CAP 100_000 越限全清兜底（:46-52），dead-ref 有重建路径 |
| `JdbcTemplateImpl.queryCacheEpochs` | `nop-dao/.../JdbcTemplateImpl.java:242` | key=配置声明的 cacheName，封闭集合 |
| `ServiceLoadBalanceAdapter.activeCounts` | `nop-cluster-core/.../ServiceLoadBalanceAdapter.java:24` | end 归零即 remove 自清（:54-55）；前提是调用方 begin/end 配对 |
| `DaoResourceFileStore.pendingDedupCleanups` | `nop-file-dao/.../DaoResourceFileStore.java:291` | 全路径 remove（复验有引用/文件已删/3 次尝试 give-up） |
| `DebeziumCdcSourceFunction.LIVE_CONNECTOR_CLAIMS` | `nop-stream-connector-debezium/.../DebeziumCdcSourceFunction.java:85` | finally 首行两参条件 remove（:372），put→try 无窗口，覆盖全部异常路径 |
| `DBMessageService.lastFailureReasons` | `nop-ai-agent/.../DBMessageService.java:165` | 消费成功/死信终态均 remove；毒消息上限保证必达清理点 |
| `AddAccessTokenHttpClientEnhancer.providerLocks` | key=配置 provider 名，封闭集合 | |
| `NacosNamingService.loadLocks` | `getInstances` finally 无条件 remove，即用即删 | |
| `TaskFlowManagerImpl.lastWarnAt`（cap WARN 频控） | 单 `AtomicLong` + CAS，无 map，天然有界 | |

（另外抽查：`DBMessageService.consumers` 注销对称、`InMemoryTeamManager` 主索引 declared-forever 语义 —— 均与上述一致。）

## Conclusion

- 修复批次整体质量良好：16 处候选中仅 1 处 LEAK。频控/锁存族大量采用「即用即删 finally remove」「CAP 兜底」「配置封闭 key」等正确形态。
- 共性教训：**时间窗频控 map（Map<id, timestamp>）是该批次最容易残留的形态**——本案 3 处（NON_POSITIVE_WEIGHT_WARN_AT / readerSchemaFailures / failureCooldownUntil）全部属于此类。优先选型：轮次 diff 告警（有自然回收语义）或带 TTL 的缓存实现。
- 被否决方案：给 `materializeGates` 补 remove —— 否决原因：锁条目在持有中被移除会破坏 single-flight 互斥（lock identity race），有界残留是更安全的取舍。

## Open Questions

- [ ] `userExportSemaphores` 的 max 固化问题（配置下调对既有用户不生效）是否值得改为读时校验——单独评估
- [ ] `LocalMessageService` 空 topic 清理若未来出现 per-session 动态 topic 订阅模式，需升级为 LEAK 并连同 subscribe 竞态一并修复

## References

- `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/assigner/WeightedPartitionAssigner.java`
- `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java`
- `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/GraphMetricMaterializer.java`
- merge commit `44719346a9`
