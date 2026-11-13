# WI 2.5 链复制/PubSub 关联束（R4-20，plan 2554）设计裁定

> Status: active
> Last Reviewed: 2026-11-10
> Source: `ai-dev/audits/2026-10/2026-10-19-0624-deep-audit-nop-persistence-peri-concurrency/`（MA-07/08/09/11 = merge-adjudication §族；C1-001/002/008、C3-010/011/012、C7-006/008/009 = 各维度文件）+ fresh-session 取证（2026-11-10，10 原样/3 部分覆盖）

## 一、设计结论（十四条裁定 A-N）

1. **裁定 A（MA-07 校验规模包络）**：`ConsistencyVerifier` 全量 checksum 改**归并流式 + keyset 分页**——双侧按 pk 升序各以 `WHERE pk > lastKey ORDER BY pk LIMIT N`（keyset，消 offset 二次方 IO）分页拉取，归并游标对比（missing/added/mismatch 增量检出），任一时刻堆内仅 O(N=CHECKSUM_BATCH) 行；全量 Map/TreeSet 驻堆形态移除。CL-025（FullCopyBatchJob 深分页）**移出本批 scope**：其分页由通用 `JdbcPageBatchLoaderProvider`（nop-batch-jdbc，offset 分页+断点语义）承载，keyset 化需扩展 batch-jdbc provider——successor 登记见裁定 N。

2. **裁定 B（MA-08 写-TTL 原子化）**：hash 三入口 + session 入口全部 Lua 原子化（复用在册 `SESSION_SET_FIELD`/`HASH_PUT_IF_ABSENT_OR_MATCH` 的 script 装配范式）：`HASH_PUT_EX`（hset+pexpire）、`HASH_PUT_IF_ABSENT_EX`（hsetnx+pexpire）、`HASH_GETSET_EX`（旧值返回+hset+pexpire）、`SESSION_SET`（hmset+pexpire）。D03-02 历史收口义务随 session 入口闭合。string 键 psetex/SET NX PX 原子兄弟面保持不动。

3. **裁定 C（MA-09 computeIfAbsent 原子化）**：`LettuceMessageService`（KV）与 `LettuceHashOperations`（hash）的 computeIfAbsent/getAndSet 族改 Lua 原子路径——KV: `SET k v NX`（失败回落 GET，保证「仅一个结果生效」契约）；hash: `HSETNX` 判定 + `HGET` 回读组合脚本；getAndSet 用 GETSET 语义脚本。`NosqlCache`（nop-nosql-core）覆写面随契约文本对齐（委托原子实现或显式声明原子性由实现层承载）。auth 先例（RedisMfaChallengeStore 绕开语义陷阱）不改。

4. **裁定 D（MA-11 驱动层批写 chunk 化）**：tdengine——`batchExecuteAsync` 按「行数 × 子表声明数 × 估算字节」三因子切分为多个 executeAsync 子批（配置键 `nop.orm.td.batch-max-rows` / `nop.orm.td.batch-max-bytes`，缺省有界）；同子表 tag 分歧不再静默取首行（分歧检测显式 WARN 留痕——round2 扩展面）。rpc——`batchExecuteAsync`/`batchLoadAsync` 按 `nop.orm.rpc.batch-size`（缺省 500）拆分多请求顺序投递、waitAll 聚合。上游 `BatchActionQueueImpl`/`EntityPersisterImpl` 接缝零改动（chunk 收敛在驱动层）。**2553 守卫不变式（硬约束）**：chunk 化后 `batchExecuteAsync` 返回 future 必须同步完成（驱动内 block-join 各 chunk 后返回 done future）——`BatchActionQueueImpl:175-179,205-209` 两阶段守卫对未完成 future fail-fast，不得造真异步分块路径。

5. **裁定 E（C1-001 RPC 回调线程契约）**：`RpcEntityPersistDriver` 的 `loadAsync`/`batchLoadAsync` 完成回调（bindEntity/internalAssemble/markMissing）改经 **session context 出口泵**投递回调用方 context 执行（复用 2517 session mutex 的 context 纪律：先 `ContextProvider.currentContext()` 判空（`thenOnContext` 无 context 时会新建 context 而非内联——禁止在 RPC 线程静默创建 context），null 则走裸 future 回调（=同步等待的调用线程内联）；接口 javadoc 补「会话状态变更不得落传输线程」契约；null-future 已有钉面（TestRpcEntityPersistDriverNullFuture）零回退。

6. **裁定 F（C1-002 迁移引擎注册面）**：`MigrationEngine` 两裸 HashMap → `ConcurrentHashMap`；`MigrationExecutor` 引用 final 化（构造期注入）；`registerExecutor` 保留 public 但 javadoc 声明「构造后配置期调用」契约。

7. **裁定 G（C1-008 残余）**：五惰性字段 volatile 化；`doAbort` 直读点（:506）与 `setChainStopJoinTimeoutMs`（:152）改经 synchronized 访问器；**stopIncrementalChains 路径直读点显式豁免**（:268/:333/:636——javadoc 明文故意直读：只停本进程已启动链，访问器惰性构造会引入「停止时建 manager」误行为），volatile 化后直读安全。

8. **裁定 H（C3-010 lag 观测 qs 对齐）**：`IncrementalLagProbe` 观测 querySpace 与 sink 提交（`ShardedUpsertConsumerProvider` 的 `target.getQuerySpace()`）对齐——orchestrator 接线改传 target qs（relocate 形态 loud/silent 两型闭合）；`stateQuerySpace` 仅保留状态行自身 DDL 面。载体：qs 错位行为钉（fake template 捕获 SQL 的 querySpace 断言）。

9. **裁定 I（C3-011 taskKey qs 身份）**：`FullCopyBatchJob` taskKey 追加 querySpace 维度（`jobId:qs:table`），多 qs 同名表任务身份不撞。

10. **裁定 J（C3-012 位点三方对齐 fail-fast）**：`CdcOffsetSeedBuilder` 校验扩展——seed partition 表集 ⊆ mapping tableIncludeList 源表集，缺交显式类型化失败（不静默忽略错位 seed）；`IncrementalJobManager` 启动前同校验复用单点判定函数。

11. **裁定 K（C7-006 abort 覆盖面，R1-Major-4 修订）**：取消原语在 **context**（`BatchTaskContextImpl extends ExecutionContextImpl extends Cancellable`——`cancel(STOP)` 后 `BatchTask` chunk 循环 `isCancelled()` 抛 `BatchCancelException`），不在 IBatchTask。①FULL 期——`FullCopyBatchJob` 暴露运行中 context 句柄（volatile 字段，copyTable 构造处落、finally 清）+ **orchestrator abort 标志（volatile）**，`run()` 逐表循环每表开始前检查标志（表间缝隙闭合——cancel 单表 context 不阻止后续表）；doAbort 对在飞 context 调 `cancel(STOP)` + 置标志，「半个中止」闭合；②换实例面——跨实例句柄不可达为显式 residual：abort 写状态行 ABORT 后，其他实例 runVerify/runIncremental 入口按状态行 fencing 拒绝（既有 2518 机制承载），登记 watch-only（跨实例注册表=设施增强）。

12. **裁定 L（C7-008 残余，对齐 R4-32 拆分；R1-Major-3 谓词修订）**：本批收敛 **namespace 过滤面**——v2 ledger `sink_namespace` 列写入值为 `jobId|vertexId`（部署 task-identity 管道，`JdbcTwoPhaseCommitSink.java:284-289`，fallback 模式为目标表名）。观测谓词 = **`sink_namespace LIKE 'jobId|%'` 参数化前缀匹配**（链作业 namespace 恒带 jobId 前缀；fallback 表名模式的非链提交被正确排除）；部署侧若命中 fallback 模式（namespace 不含 `|` 前缀）则 WARN 留痕回落无过滤观测（不可过滤档显式声明）。**墙钟 sleep 面拆出至 R4-32**（墙钟敏感跨单元批，单调时钟注入同批设计，C7-013 同批）——deferred 登记 successor=R4-32。`awaitLagZeroChains` RP-2 双钉零改写。

13. **裁定 M（C7-009 link 复位；R1-Minor-8 收敛）**：`LettucePubSubService.doStop` 停止路径锁内置空 `link`（与 subscriptions 清理同域）——`getOrCreateLink` 命中 null 自然重建（PubSubLink 接口无状态查询面，不加新状态 API）。stop→start 后首个 subscribe 走新建链路，消息面不再死亡。`TestLettucePubSubConcurrency` 回归面零回退。

14. **裁定 N（CL-025 FullCopyBatchJob 深分页——移出 scope，R1-Major-1/2）**：keyset 化需扩展 nop-batch-jdbc 通用 `JdbcPageBatchLoaderProvider`（含断点语义兼容），超本批边界——显式移出 scope，successor 登记于 Deferred（触发条款：同族无界账本批或 nop-batch 治理批）。

## 二、载体表（13 工单 × 载体）

| 工单 | 载体（测试类/方法/断言目标） | 模块 | 类型 |
|---|---|---|---|
| MA-07 | `TestConsistencyVerifierStreaming`（fake jdbc 分页归并：跨页 added/missing/mismatch 检出 + 结构钉[无全量 Map 驻留/SQL 含 keyset 谓词]） | nop-orm-migration | 行为钉+结构钉 |
| MA-08 | `TestLettuceHashTtlAtomic`（真 Redis：四入口写后 PTTL>0 即时断言 + Lua 脚本装配结构钉） | nop-nosql-lettuce | 行为钉（真 Redis） |
| MA-09 | `TestLettuceComputeIfAbsentAtomic`（并发同 key N 线程 computeIfAbsent：最终值单写者 + getIfPresent 一致；结构钉 SET NX） | nop-nosql-lettuce | 行为钉+结构钉 |
| MA-11 | `TestTdBatchChunking`（超上界批拆分为多 SQL + 字节/子表因子 + tag 分歧 WARN）；`TestRpcBatchChunking`（超 batch-size 拆分多请求 + waitAll 聚合） | nop-orm-tdengine + nop-orm-rpc | 行为钉 |
| C1-001 | `TestRpcLoadAsyncThreadContract`（有 context：装配落 context 线程断言；无 context：内联调用线程） | nop-orm-rpc | 行为钉 |
| C1-002 | `TestMigrationEngineRegistry`（并发注册不丢 + executor 引用 final 结构钉） | nop-db-migration | 行为钉+结构钉 |
| C1-008 | `TestOrchestratorLazyAccessors`（volatile 结构钉 + doAbort 走访问器结构钉） | nop-orm-migration | 结构钉 |
| C3-010 | `TestLagProbeQuerySpaceAlignment`（probe SQL 的 querySpace == sink 提交 qs 断言） | nop-orm-migration | 行为钉 |
| C3-011 | `TestFullCopyTaskKeyQs`（多 qs 同表 taskKey 不撞断言） | nop-orm-migration | 行为钉 |
| C3-012 | `TestCdcSeedThreeWayAlignment`（seed ⊄ includeList → 类型化失败断言） | nop-orm-migration | 行为钉 |
| C7-006 | `TestAbortCoversFullCopy`（FULL 期 abort → 批任务取消/停止标志生效断言） | nop-orm-migration | 行为钉 |
| C7-008 | `TestLagProbeNamespaceFilter`（COUNT/MAX SQL 含 sink_namespace 谓词断言；墙钟面=R4-32 deferred 结构注记） | nop-orm-migration | 行为钉+结构钉 |
| C7-009 | `TestPubSubStopStartRestart`（stop→start 后 subscribe 成功收消息断言） | nop-nosql-lettuce | 行为钉（真 Redis） |

## 三、回归面与接缝约束

- 每模块回归命令见取证报告表；聚合 `./mvnw -o test -pl <module> -am` 归因形态沿 2553。
- 三条硬接缝：① MA-08 携带 D03-02 收口义务；② `TestLettucePubSubConcurrency`（2518）零回退；③ `IncrementalLagProbe.awaitLagZeroChains` RP-2 双钉零改写（`IncrementalLagProbe.java:57-72` javadoc）。
- C7-008 墙钟面 → R4-32 deferred（successor 明确）。
