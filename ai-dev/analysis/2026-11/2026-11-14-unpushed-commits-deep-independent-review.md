# 未推送提交（853 commits）深度独立评审

> Status: resolved
> Date: 2026-11-14
> Scope: origin/master..HEAD 全量生产代码（按用户指令排除 nop-ai / nop-metadata / nop-datav / nop-code 四模块与全部测试代码；含 pom/beans.xml/xdef/SQL 等非测试变更）
> Conclusion: 总体质量高（系统性并发加固战役，设计-实现-测试自洽），但独立复核确认 4 个应修复缺陷（A1 JWK 失败路径限流失效、A3 WS 推送 null-session NPE、A4 PartitionResolver 负缓存脏正缓存、A5 会话清理 TOCTOU）+ 1 个需重新裁定的默认值翻转（A2 组件缓存 5min 强制过期）+ 一批已声明/可接受的运行时行为变更；修复计划与执行见 `ai-dev/plans/2573-unpushed-review-confirmed-defects-fix.md`（已执行完毕，含收口审计追加的第 5 个缺陷 X-1 JWK 成功速率窗滑动自锁，见 Addendum）。

## Context

- 853 个未推送提交（含 776 提交的 concurrency-audit-remediation 全量合入 + 后续 ~77 个修复/测试/构建提交），生产代码 diff = 1033 文件 / +57,714 / -5,156。
- 用户要求：不以既有裁定为准的**独立深度评审**——检查合理性、漏洞、引入的问题、性能退化；一次性生成完整 diff 后通读逐项检查。
- 方法：`git diff origin/master..HEAD` 过滤后落盘 `_tmp/review/full_prod.diff`（90,884 行），全文分块通读（约 98% 行数精读，前端构建产物/反射配置/生成视图 XML 略读），可疑点回到 live 源码独立验证（不采信 diff 注释中的既有裁定）。

## 覆盖面声明

逐模块精读：nop-auth（session/MFA/Redis TTL/SSO/账本清理）、nop-batch（core/dsl/jdbc/orm/exp 全链）、nop-cluster（elector/LB/nacos/sentinel/rpc-cluster）、nop-config、nop-ioc（BeanContainerImpl/BeanDefinition 状态机重做）、nop-plugin（双轨生命周期+forceUnload）、nop-credential、nop-duckdb、nop-dyn、nop-file、nop-format（record/docx/ooxml 新模型）、nop-dev-tools（xlang-debugger resumeEpoch/共享初始化器）、nop-graph、nop-integration、nop-job、nop-kernel（api-core/commons/core 执行器/锁/缓存核心）、nop-message（debezium/kafka/pulsar）、nop-network（http/rpc/netty/socket/vertx）、nop-persistence（dao 事务状态机/迁移/nosql）、nop-retry（引擎重做）、nop-service-framework（biz/gateway 流式缓冲/graphql core/ws）、nop-stream（core/connector/runtime）、nop-sys、nop-task、nop-tcc、nop-tools。

## 总体评价（独立判断）

- **正面**：这是一次大规模、纪律性强的并发与资源生命周期加固战役。核心设施重做（周期任务绝缘层 InsulatedPeriodicChain/Task、ResourceRetirementQueue 退役代际、ResourceCacheEntry 装载状态机 + LoadWaitGraph、AbstractTransaction CAS 状态机、TransactionRegistry suspend/resume、DebeziumEngineWrapper stop 契约、RetryEngine 代际/簿记隔离、TCC 恢复纪元）在独立通读下逻辑自洽，锁序/终态/幂等论证充分，测试钉密集。绝大多数"行为变更"都有显式声明与配置逃生门。
- **风险面**：变更体量本身即风险——核心路径（IoC 容器、资源缓存、事务、执行器）几乎全部重写，任何生产回归都需要多个会话才能收敛；若干**默认值/默认语义翻转**在升级时无显式提示（见 A2/A5/B1/B2）；少量实现细节存在真实缺陷（A 类）。

## A 类：确认缺陷（建议修复）

### A1【Major·安全/可用性】JWKPublicKeyLocator 失败路径丢失限流（DoS 放大）

- 位置：`nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWKPublicKeyLocator.java:90-135`
- 独立验证：`lastRequestTime` 仅在拉取**成功**后推进（`if (keys != null) { lastRequestTime = ... }`，:120-122）；`minTimeBetweenJwksRequests` 缺省 60s（SsoConfig:29）。
- 旧实现：`sendRequest()` 发出前即推进 `lastRequestTime` → 失败也占用 60s 速率窗。新实现失败不占窗（注释自称"裁定 F④"）。
- 后果：JWKS 端点故障/网络黑洞期间，**每个**携带未知 `kid` 的认证请求（攻击者可用随机 kid JWT 低成本制造）都会发起一次新的 IdP 拉取（仅"在飞单飞"可合并并发，时间上无界）。旧代码失败后至多 1 次/60s，新代码退化为无节流 → 对 IdP 的请求放大 + commonPool 线程churn。这是把"失败可立即重试"（合理诉求）实现成了"失败后完全无限流"（回归）。
- 修复方向：失败路径保留一个**短**退避窗（如 `minTimeBetweenRequests/10` 或独立 failure-backoff 1-5s），既不占满 60s 窗又封住放大。

### A2【Major·性能/默认值翻转】组件缓存缺省从"永不过期"翻转为"5 分钟强制过期"

- 位置：`nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java:95-97`（`CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT` 缺省 `null` → `Duration.ofMinutes(5)`）；消费点 `ResourceLoadingCache.createCache`（expireAfterWrite）与 `ResourceCacheEntry.shouldReloadObject`（maxAge 强制重载，:36032-36035）。
- 独立验证：所有未显式传 timeout 的 `ResourceLoadingCache`（views/xmeta/xpl/dict/i18n/api 模型等组件缓存）现在双通道 5min 过期：(1) LocalCache expireAfterWrite 整条目驱逐 → 下次访问冷加载重建；(2) entry 层 maxAge 强制 reload（deps 非空条目）。
- 后果：中大型部署（数百个已编译组件）每 5 分钟出现一次周期性重解析/重编译风暴（CPU 尖峰 + 首请求延迟毛刺）；标注为"漏检自愈通道"但把自愈通道开成默认常开。属于**默认性能行为退化**，且升级方无提示。
- 修复方向（二选一，需裁定）：恢复缺省 `null`（自愈通道 opt-in）；或保留 5min 但在 owner doc + 启动日志显式声明，并评估 expireAfterWrite 与 maxAge 双通道叠加是否必要（单 maxAge 通道即可保语义，驱逐通道才是冷风暴来源）。

### A3【Medium·NPE】GraphQLSubscriptionManager.pushToClient 对 null session 调 CHM.remove

- 位置：`nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java:216-221`
- 代码：`if (session == null || session.isClosed()) { sendPumps.remove(session); ... }` —— 分支显式预期 session 可为 null，但 `ConcurrentHashMap.remove(null)` 抛 NPE。该异常沿消息分发线程上抛，中断该 topic 的后续订阅推送。
- 修复：null 判空后再 remove；一行修复 + 回归钉（session=null 时仅 unregisterSubscription 不抛）。

### A4【Medium·逻辑缺陷】PartitionResolver 负缓存未清正缓存 → 未命中后回吐陈旧分区集

- 位置：`nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/PartitionResolver.java:106-160`
- 独立验证：负缓存命中写点 `negativeCached=true; lastResolveTime=now`（:131-133、:140-142）**不置空 `cachedPartitions`**；`lastResolveTime` 为正/负缓存共享。负 TTL 过期后（`negativeTtlMs` 可配，缺省 5000 < `CACHE_TTL_MS=10000`，:44），`cachedPartitions != null && (now - lastResolveTime) < CACHE_TTL_MS` 命中 → 返回**负解析之前**的旧分区集（本节点当时已不在 server 列表）长达 `CACHE_TTL_MS - negativeTtlMs`。
- 后果：`negativeTtlMs` 配得越短（运维想更快感知下线）反而回吐越长的陈旧分区；消费方（分区分配）在"应返回 null"的窗口拿旧分区继续认领。
- 修复：进入负缓存时同时 `cachedPartitions = null`；补一条"负缓存过期后首查必须真实 resolve"的钉。

### A5【Medium·运行时竞态】NopAuthLedgerCleaner session 族 pick-then-delete TOCTOU

- 位置：`nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/cleanup/NopAuthLedgerCleaner.java:356-374`
- pick（`lastAccessTime < cutoff OR logoutTime < cutoff`，limit 1000）与 delete（`sessionId IN (...)`）之间无谓词复验：30 天未活跃会话恰在窗口内被用户访问（flush 更新 lastAccessTime）后仍被删除 → 活会话被强制终结（用户重登录）。
- 概率极低（需 30 天不活跃会话在毫秒窗内复活），五短 TTL 族无写入面不受影响；但修复廉价：delete 改带同谓词（`deleteByQuery` 复用 pick 过滤）即可消除。
- 注：同型 `DaoBatchLedgerCleaner` 的终态任务谓词单向（终态不回退）无此面。

## B 类：已声明但升级敏感的行为/语义变更（建议 release-notes + 个别重裁）

### B1 cached 线程池从无界翻转为硬上界 200 + 达界拒绝

- `ExecutorHelper.newThreadPoolExecutor`（maxPoolSize<0 分支）→ `CachedPoolRejectHandler`（`nop.commons.concurrent.cached-pool.maxPoolSize` 缺省 200，REE + 错误码消息）；`DefaultThreadPoolExecutor.newExecutor` 三参工厂现拒绝 poolSize/queueSize < 1（JavaScriptService 已同步改配置形态，仓内无其他负参调用方）。
- 影响：多批任务并发时（每任务 concurrency ≤ 200，多任务共享平台 cached 池）提交可被拒——旧行为是无界建线程。正确方向，但**默认 200 对重负载部署偏紧**且失败形态从"变慢"变为"任务失败"，需部署文档显著声明。

### B2 权重 0 语义翻转：weight<=0 不再表示"不接流量"

- `WeightedPartitionAssigner`（weight<=0 → 按 1 下限参与分配，且删除了公开 `setDefaultWeight` API——仓内零调用方，外部使用方破坏）与 `WeightedRandomLoadBalance` 同口径。控制台置 0 摘流的旧运维习惯会失效；注释有声明（R25-1），但这是运维可见语义翻转，应进 release notes。

### B3 平台缺省等待上界全面引入

- `IRateLimiter.acquire` 缺省 5min 超时（原无限等）、`throttleExecute` 缺省 60s、`IRateLimitExecutor` 语义同型、`SentinelFlowControlRunner` orTimeout 缺省 60s（task stage 永不完成时调用方 60s 收 TimeoutException——原为无限等）。方向正确（消灭无限挂死），但**正常长任务**（如 >5min 的合法限流排队、>60s 的合法慢调用）现在会以 typed 超时失败；部署需按负载校准这些新键。

### B4 事务/调度契约语义变化

- `AbstractTransaction`：markRollbackOnly 后 commit 由抛错改为**自动 rollback + WARN**（C2-016 显式裁定）；二次 commit 变 no-op-with-WARN；commit 失败尽力置 ROLLED_BACK。调用方若依赖旧异常信号需适配。
- `DaoBatchStateStore.saveTaskState` 改裸条件 UPDATE（execCount fencing）：**不再递增 VERSION 列**，ORM 乐观锁对该行的其他并发更新感知不到本写（批运行时独占写面，影响有限，但与 ORM 版本契约漂移，宜登记）。
- `BatchTaskContextImpl.onTaskBegin` 在 fire 之后注册由"静默接受但永不触发"改为抛 `IllegalStateException`（响亮化，存量监听器注册时序若踩线会炸）。
- `FutureHelper.bindCancel` 由"任意完结即 cancel 关联 future"收紧为"仅 CancellationException 才 cancel"（修复误取消，但旧行为下被顺带回收的排队任务现在会照常执行）。
- `BatchSkipPolicy.setMaxSkipCount`/`BatchRetryPolicyModel` 负值由"无限"改为装配期 fail-fast——存量负值配置升级即炸（有意为之，需迁移提示）。
- `TimeOut`/`IResourceLockState.getExpireTime` 语义从 epoch 毫秒变单调纳秒（仓内零外部消费者，已 javadoc 声明）。

### B5 执行器/IO 细节行为变化

- `DefaultThreadPoolExecutor.execute` 现对每个命令包 finally detachContext（修复 context 泄漏）；`ExecutorHelper.submitTracked` finally `Thread.interrupted()` 清 worker 残留中断位。
- `ExportDbTool/ImportDbTool` executor 队列 `Integer.MAX_VALUE` → 10000（提交可被拒）+ 资源段整体入 try（修复泄漏，正确）。
- `JdbcBatchLoaderProvider` fetchSize 坏门修复正确，但未配置 fetchSize 的路径现在**每次任务装配 WARN 且带全量 SQL 文本**（`nop.err.batch.jdbc-fetch-size-unset`）——存量未配 fetchSize 的部署日志噪音显著；建议降频（一次性/每任务名一次）且 SQL 截断。
- `NioFileWatchService` watcher 线程 daemon 化（JVM 退出不再等待，已声明）。
- `ResourceTimestampCache`（lastModified 系统调用缓存）删除 + 变更判据加 2s 写入新鲜度宽限（`change-grace-period`）——最近 2s 内写过的资源会被保守判"已变更"触发重载；配合 verdict 记忆化（100ms）抵消部分 stat 开销。方向（正确性优先）可接受，但高频写+高频读的工作目录会看到重载放大。

## C 类：次要观察（watch-only / 低危）

- `ExecutorHelper.CachedPoolRejectHandler` 用 `new NopException(...).getMessage()` 串进 REE——错误码/params 丢失，仅剩文案（排障时无 code 可 grep；小改进：自定义 REE 子类携带 errorCode）。
- `BatchProcessorConsumer` 输出上界 check-then-act 非原子（并发异步 processor 可略微超限，默认 0=不启用，无实际面）。
- `WeightedPartitionAssigner.nonPositiveWeightOffenders` 为 **static** 字段——多 assigner 实例共享 diff-WARN 状态，可能漏报/误报一条 WARN（纯观测面）。
- `DaoBatchLedgerCleaner` record_result 独立 TTL 通道逐行 `deleteEntityDirectly`（≤1000 行/小时，性能影响可忽略；与 auth 清理器的 IN 批删形态不一致）。
- `DefaultBizEntityExporter.clampConfig`：`batchSize<=0 → maxBatchSize`（取上界）与 `concurrency<=0 → 1`（取下界）不对称——注释有解释但直觉上应取缺省批尺寸而非最大值。
- `DebeziumEngineWrapper`：`stop()` 先于 `start()` 调用后再 `start()` 会"成功返回但 engine 永不运行"（stopRequested 不重置；一次性契约的极端边角，文档已覆盖大形态）。
- `ServiceLoadBalanceAdapter.begin/end` 已在 ClusterRpcClient call/callAsync 挂接（经 rpc-cluster-defaults.beans.xml 装配 `nopServiceLoadBalanceAdapter`），LeastActiveLoadBalance 计数面真实可达（验证过接线，非空壳）。
- `MfaTrustedDeviceManager.register` per-user 锁为 **Local**ResourceLockManager——跨节点 max-count 竞态仍在（beans 注释声明可换分布式实现，当前部署语义需知晓）。
- `AbstractLeaderElector.newElection()` 在 stop 后调用会把未完成 promise 停放进 outstandingPromises 且无人再结算（极端窗口，等待者悬挂直至下次 start/stop；低危）。
- `DaoUserContextCache` 条件 flush（C1-010 重做）：基线条件 UPDATE + logoutType 守卫 + isRowAbsent 实体回退，独立推演各交错窗（读基线→token 轮换→flush 失配放弃；登录 insert 未提交→回退实体改写随事务提交）均收敛，未见新洞；行为变更（良性并发下后到 flush 放弃 = 单请求内非 token 字段不落库）已在 javadoc 声明。
- IoC `BeanContainerImpl.getOrCreateSingleton` per-BD ReentrantLock + LoadWaitGraph 环检 + 100ms 切片轮换：逻辑推演自洽（等待边随 owner 换手刷新、构造器环走同线程检测、错误构造守卫防 i18n 重入递归）；高复杂度核心改动，建议保持现有测试钉密度并观察启动期竞态。
- GraphQL WS 全局订阅闸 `GLOBAL_SUBSCRIPTION_GATE` 为 **static final**，构造时读配置一次（`buildGlobalSubscriptionGate`）——测试/多容器场景不可二次配置；键热更不生效（与其它可热更键不一致，宜声明）。
- `JsonRpcWebSocketHandler.maxActiveOperations` 字段每次 new 时读配置（端点为宿主 CDI bean 的声明理由成立）。

## 已复核无问题的重点面（抽样列举）

- `ResourceCacheEntry` 装载状态机：clear() 与在飞装载的交错（clear 标记失效→装载者随后发布对象→entry 已被逐出/摘除，不可见）影响有界。
- `LocalResourceLockManager` 单调时钟整域切换（含 C2-00008 过期判定入 latch 监视器修复）读写同源，无混源比较。
- `TransactionRegistry.getOrCreate/suspend/resume` + `TransactionTemplateImpl` cleanupIdentityMismatch：REQUIRED 并发语义与伪 NOT_REGISTERED 消除，推演成立。
- `InsulatedPeriodicChain/Task` + `DefaultScheduledExecutor.destroy()` 统一拒绝收口：fixedRate/fixedDelay 双路径无双层守卫（六参直连原生机制），挂起 future 必有终态。
- `DebeziumEngineWrapper.stop` 契约（STARTING_TASKS 有界重试→载体有界等待→latch 等待→shutdownNow 升级→ghost 声明 + finally 配对释放 offset 注册桶）时序闭合。
- RetryEngine OVERWRITE 四分支分流（UK×行身份）与代际短路、attempt 超时 < 租约装配校验、回调深度护栏（尾部 `_callback` 计数）均成立。
- stream：ResultPartition FAM-1 许可保留化（移除 overflow-bypass，恢复 exactly-once）与 InMemoryMaterializationPoint 有界化（opt-in 上界 + WARN 分层）语义一致。
- TccEngine 恢复纪元（timeout 后 `beginDriving` 弃链 + 写点闸）、分支 DB 复核 fail-close、单轮时长预算/连续超时熔断，独立推演无洞。
- `DaoResourceFileStore` 去重延迟两段清理（10min 宽限复验 + 3 次重试 give-up 日志）正确封住判-删竞态（残余极端长事务窗口已声明）。
- nacos `NacosNamingService` 有界 LRU + per-key 装载锁三阶段 + 败者 unsubscribe，无订阅泄漏面。
- `JavaScriptWorker` MAX_WAITING_TASKS 准入软上界 + 入队后 closed 复查（Dekker）+ FUNC_REINIT build-then-swap，推演成立。

## 结论与处置

- **A1/A3/A4/A5 确认为应修复缺陷**（A1 Major、A3/A4 Medium、A5 Low-Medium），A2 为需要**重新裁定**的默认值翻转（性能敏感）。
- **B 类**不需要代码回退，但需要 release-notes/部署文档显式登记（尤其 B1/B2/B3）。
- **C 类**登记 watch-only。
- 修复执行：`ai-dev/plans/2573-unpushed-review-confirmed-defects-fix.md`（draft，等待批准执行）。

## Addendum：收口审计追加发现（X-1，2026-11-14 晚）

- **X-1【Major·2573 收口审计新发现】JWK 成功速率窗被旧 future 无限滑动（密钥轮换自锁死）**：`JWKPublicKeyLocator.getPublicKey` 的 `!allowed` 分支（plan 2551 单飞改写引入）仍把 fetch 指向上次**已完成**的成功拉取；等待段以其成功结果把 `lastRequestTime` 刷新到本次请求时刻——未知 kid 请求到达间隔 < 60s 时窗口无限滑动，JWKS 永不重拉（IdP 密钥轮换后新 kid 持续到达 = 认证持续失败的自维持锁死；`lookupCachedKey` TTL 同源滑动，旧钥永不过期）。origin/master 无此行为（lastRequestTime 仅在真正 sendRequest 时推进）= 未推送批次回归。本评审 A1 检查覆盖了失败路径限流但未覆盖成功路径滑动窗——由 plan 2573 独立收口审计（jshell + ManualClock 实测）发现并已随 plan 2573 修复（三分支重排：窗内无在飞 → 不消费旧 future；钉 = pin_e，变异探针先红后绿）。

## Open Questions

- [x] A2 的 5min 缺省是否保留 → **已裁定（plan 2573 Phase 2 选项甲）：恢复缺省 null，自愈通道 opt-in**（性能默认翻转优先消除；裁定与理由落 `ai-dev/design/concurrency/cache-invalidation-single-point.md`）。
- [ ] B1 cached 池缺省 200 是否按部署画像上调（或至少在拒绝错误码消息中提示键名——现已含）。

## References

- diff 全文：`_tmp/review/full_prod.diff`（生成于本次评审，临时产物）
- A1: `nop-auth/nop-auth-sso/src/main/java/io/nop/auth/sso/jwk/JWKPublicKeyLocator.java`
- A2: `nop-kernel/nop-core/src/main/java/io/nop/core/CoreConfigs.java`、`nop-kernel/nop-core/src/main/java/io/nop/core/resource/cache/ResourceCacheEntry.java`、`ResourceLoadingCache.java`
- A3: `nop-service-framework/nop-graphql/nop-graphql-core/src/main/java/io/nop/graphql/core/subscription/GraphQLSubscriptionManager.java`
- A4: `nop-cluster/nop-cluster-core/src/main/java/io/nop/cluster/naming/PartitionResolver.java`
- A5: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/cleanup/NopAuthLedgerCleaner.java`
