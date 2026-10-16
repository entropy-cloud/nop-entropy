# C7 长时运行稳定性 — nop-retry-nop-tcc 第 1 轮初审（盲审）

- 审计单元：`nop-retry`（8 jar，40 files/4,100 行手写）+ `nop-tcc`（9 jar，42 files/3,872 行手写）
- 审计维度：C7 长时运行稳定性（单调增长 / 挂死可检测性 / 重试风暴 / 自愈语义 / 时钟与期限 / 每调用小对象）
- 仓库基线：branch `concurrency-audit-remediation`，以当前 live code 为准；`_` 前缀生成文件不作发现对象
- 盲审纪律：未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/` 下任何文件
- 发现条目统一 13 字段：①发现ID ②维度子项 ③严重度与判级理由 ④位置 ⑤证据片段 ⑥机制（增长/劣化链）⑦时间尺度 ⑧触发前提 ⑨运行影响 ⑩缺陷族 ⑪对账（已知立案/已声明边界）⑫修复方向 ⑬置信度

## 检查范围说明

**精读文件**（全部为手写 live code）：
- nop-retry：`RetryEngineImpl.java`、`RetryScannerImpl.java`、`RetryRecordStoreImpl.java`、`RetryTaskImpl.java`、`NopRetryPolicy.java`、`NopRetryApiConstants.java`、`_NopRetryDaoConstants.java`、`NopRetryRecordBizModel/DeadLetterBizModel`、`app-engine.beans.xml`
- nop-tcc：`TccEngine.java`、`TccTransaction.java`、`TccTransactionRegistry.java`、`TccRunner.java`、`TccBranchTransaction.java`、`TccRecordStore.java`、`TccStatus.java`、`TccHelper.java`、`TccCoreConstants.java`、`TccRpcServiceInterceptor.java`、`TccGatewayInterceptor.java`、`DefaultTccServiceMetaLoader.java`、`NopTccRecordBizModel/BranchRecordBizModel`
- 模型：`nop-retry/model/nop-retry.orm.xml`、`nop-tcc/model/nop-tcc.orm.xml`（索引/唯一键核对）
- 平台设施核对（只为验证模块用法，不立案平台自身）：`DefaultScheduledExecutor`、`GlobalExecutors`、`DbEstimatedClock`、`PartitionResolver`、`CoreMetrics`、`HttpRpcServiceInvoker`/`ClusterRpcServiceInvoker`（timeout 关键字扫描）、`NopRetryJobRetryBridge`
- 全仓 grep 对账：`checkExpiredTransactions`、`cleanCompletedTransactions`、`removeCompletedRecords`、`ITccEngine`、`IRetryEngine`、`nextRetryTime`、`maxRetryTimes` 的全部生产/测试调用点

**已声明契约校准**：retry 的 DISCARD/OVERWRITE、SUSPENDED 运维暂停、COMPLETED 幂等重放、超时双参（deadlineTimeoutMs 总截止 + retryingTimeoutMs 执行租约）、回调开关；tcc 的崩溃恢复对称续跑（CONFIRMING→confirm、CANCELLING→cancel）、`nop.tcc.default-max-retry-times=100`、恢复入口 `timeout=true`、多跳传播优先级——均已对照实现核实（见覆盖度节）。tcc "外部驱动过期扫描、模块内无调度器" 按预注作为已声明边界处理，仅登记其长时运行后果面。

## 单调增长源清单 + 挂死点清单

### 单调增长源（只增不删/循环放大）

| # | 增长源 | 位置 | 删除路径 | 判定 |
|---|--------|------|----------|------|
| G1 | `nop_retry_record` COMPLETED 行（幂等账本） | RetryRecordStoreImpl（无任何 purge API） | 仅 OVERWRITE/死信化删除非终态行；COMPLETED 永存 | F4，DB 侧 |
| G2 | `nop_retry_attempt` 行（每 attempt 一行） | RetryRecordStoreImpl.java:142-149 | 无；死信化后成孤儿（代码注释自认，RetryRecordStoreImpl.java:306-308） | F4，DB 侧 |
| G3 | `nop_retry_dead_letter` 行 | RetryRecordStoreImpl.java:287-310 | 无；`retryFromDeadLetter` 为 fire-and-forget 不消费 | F4，DB 侧 |
| G4 | `nop_tcc_record`/`nop_tcc_branch_record` 已完结行 | TccRecordStore.java:332-355 | 清理 API 存在（`cleanCompletedTransactions`）但全仓库零生产调用方；`retryTimes≥max` 的非终态行被该 API 与过期扫描同时永久排除 | F4，DB 侧 |
| G5 | retry 僵尸记录的每租约周期 ERROR 全栈日志 | RetryEngineImpl.java:290 / RetryScannerImpl.java:149 | 无（记录永不终止） | F4 日志面 |
| G6 | 回调自指配置下的链式记录/死信 | RetryEngineImpl.java:541-574 | 无（链每一环生成新 idempotentId） | 配置门控 |

无内存单调累积：`TccTransactionRegistry` 为请求上下文作用域（ContextualizedRegistry，随 IContext 回收）；`PartitionResolver` 单值缓存 TTL 10s；`DefaultTccServiceMetaLoader.serviceMetas` 启动期装载后静态。

### 挂死点清单（同步等待/锁内 I/O/不产出也不报错）

| # | 挂死点 | 可见性 | 看门狗覆盖 |
|---|--------|--------|-----------|
| H1 | `RetryEngineImpl.doStart` processor 内 `CompletableFuture.allOf(futures).join()`（:648），单次 RPC 无引擎侧超时 | 无任何日志/指标 | 无——`scheduleWithFixedDelay` 上一轮不结束则永不开始，扫描器整体停摆 |
| H2 | `RetryScannerImpl.doScan` catch 仅 `Exception`（:152）；`DefaultScheduledExecutor.scheduleWithFixedDelay` 裸委托（:132-135） | Error 逃逸后周期任务被 JUC 静默取消 | 无重启/无告警 |
| H3 | tcc `checkExpiredTransactions`（TccEngine.java:315-343）单条补偿 `get(expireGap)` 有上限（有测试钉住） | 有 ERROR 日志 | 有，但**仓库内无任何驱动方**（已声明边界）；且整轮耗时随挂死记录数线性放大 |
| H4 | `retryTimes≥maxRetryCount` 的 tcc 事务静默退出扫描窗（TccRecordStore.java:303） | 无耗尽时刻的日志/终态标记 | 无——"不产出也不报错"（owner doc 声明的既有行为） |
| H5 | retry 记录 `namespaceId != scanner store namespaceId` 时永远不被 fetch（RetryRecordStoreImpl.java:160） | 无 | 无——PENDING 永滞 |
| H6 | retry `executeRetryFromScanner` 中 `JsonTool.parseMap`/`invokeAsync` 同步抛出 → 记录不计数不终止、attempt 卡 RUNNING | 每 10 分钟一条 ERROR | 无终态出口 |

## 发现条目

### [nop-retry-nop-tcc-C7-001] retry 三张账本表零保留机制，成功即沉淀永久行（F4）
① **发现ID**：nop-retry-nop-tcc-C7-001
② **维度子项**：C7-1 单调增长盘点（持久化账本）
③ **严重度**：P1——长时运行必然无界增长：每个成功任务至少沉淀 2 行（record COMPLETED + attempt），失败任务额外沉淀死信与 N 条 attempt，模块内不存在任何清理/归档/TTL 入口
④ **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java`（全文无 delete 除 :247-249/:309 两处非终态删除）；`RetryEngineImpl.java:499-507`（成功置 COMPLETED 不删除）
⑤ **证据片段**：
```java
// RetryRecordStoreImpl.java:285-310（moveToDeadLetter，模块内仅有的 record 删除点之一）
deadLetterDao.saveEntityDirectly(deadLetter);
// 死信保存成功后删除原 record 行，使同一 idempotentId 可以重新提交（plan 342 裁定）。
// 副作用：deadLetter.record 关系悬空（无 FK/cascade），attempt 行成为孤儿；
// 终态视图以死信页为准（死信行保留 recordId 快照）。
getRecordDao().deleteEntityDirectly(record);
```
```java
// RetryEngineImpl.java:499-502 —— 成功路径只置状态，行永存
private void handleExecutionSuccess(NopRetryRecord record, NopRetryPolicy policy, IRetryTask task) {
    record.setStatus(NopRetryConstants.RETRY_RECORD_STATUS_COMPLETED);
    recordStore.updateRecord(record);
```
⑥ **机制**：增长源 = 每次任务提交（成功 ≥ record+attempt 2 行；失败周期 = 1 死信 + N attempt + 中途 N 次 record 更新）。tcc 侧尚有 `cleanCompletedTransactions` API（虽未接线，见 C7-005），retry 侧连 API 都不存在——`NopRetryRecordBizModel`/`NopRetryDeadLetterBizModel` 为纯 CrudBizModel，无 purge action。死信化删除 record 后 attempt 孤儿化且无 FK 级联，孤儿随死信化单调累积。
⑦ **时间尺度**：线性于业务量。10k 任务/天 → 年级 nop_retry_record ~365 万行 + attempt 数倍；UK 索引（UK_RETRY_IDEMPOTENT_ID）与每次提交必经的 `findRecordByIdempotentId`（RetryEngineImpl.java:171）查询深度随之变深。
⑧ **触发前提**：无——正常使用即触发。
⑨ **运行影响**：表膨胀、扫描/查询渐慢、备份与迁移成本线性上升；死信表混入孤儿 attempt 关联断裂，运维视图失真。
⑩ **缺陷族**：F4（无界账本）。**DB 侧单独标注**：增长受 DB 约束（磁盘/归档策略）约束，非进程内存 OOM 面；死信保留或为运维有意设计，但"COMPLETED record + attempt 零保留"超出任何已声明边界。
⑪ **对账**：owner doc（nop-retry.md）声明死信"多次失败周期可产生多条"与死信重放语义，未声明任何保留策略；与已知交叉 F4 立案对账，本条为模块内零保留机制的新证据面。
⑫ **修复方向**：仿照 tcc `cleanCompletedTransactions(retentionTime)` 提供 COMPLETED/attempt/死信的保留期清理入口（attempt 孤儿随死信保留期一并清理），并在文档声明默认保留期。
⑬ **置信度**：高（全仓 grep 确认无任何删除路径）。
⑭ **复核状态**：保留 P1；新发现（cra-index WI 1.1–1.5 零 retry/tcc 交叉条目、`ai-dev/audits/` 无本模块历史审计目录、git log 无相关修复 commit；同族关联：WI 1.1 C8-003、WI 1.2 MA-05 均不同模块）；三表零删除路径独立复核属实——store 仅 :248（OVERWRITE）/:309（死信迁移）两处 record 删除，attempt/死信零删除、两 BizModel 为纯 CrudBizModel 无 purge action，owner doc（nop-retry.md）仅声明死信语义未声明保留策略；通用 CRUD 的 admin 手工删除不构成保留机制（无驱动）。

### [nop-retry-nop-tcc-C7-002] 扫描器单次 RPC 挂死即整体永久停摆，且无任何健康可见性（F5）
① **发现ID**：nop-retry-nop-tcc-C7-002
② **维度子项**：C7-2 挂死可检测性（retry scanner 覆盖面与看门狗缺口）
③ **严重度**：P1——停摆后长时运行必然无界积压且唯一"自愈"手段是进程重启；从停摆发生到进程重启，全部重试任务静默滞留
④ **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:632-652`；`RetryScannerImpl.java:90-102`
⑤ **证据片段**：
```java
// RetryEngineImpl.java:637-648 —— doStart 注入的 processor：join 无超时
CompletableFuture<?>[] futures = records.stream()
        .map(record -> executeRetryFromScanner(record, null)...)
        .toArray(CompletableFuture[]::new);
// 等待所有 record 执行完成
CompletableFuture.allOf(futures).join();
```
```java
// RetryScannerImpl.java:96-101 —— fixedDelay：上一轮不完赛，下一轮永不启动
scanFuture = getExecutor().scheduleWithFixedDelay(
        () -> doScan(processor), 0, scanIntervalMs, TimeUnit.MILLISECONDS);
```
⑥ **机制**：`rpcServiceInvoker.invokeAsync`（RetryEngineImpl.java:458-463）由引擎发起时无任何超时包裹；本仓两个 invoker 实现（`HttpRpcServiceInvoker`/`ClusterRpcServiceInvoker`）源文件中亦无 timeout 处理关键字（更深层 HTTP client 未审，见覆盖度）。下游一次挂死（连接建立后无响应）→ 对应 future 永不完成 → `join()` 永久阻塞 → doScan 永不返回 → fixedDelay 调度器不再发起任何后续扫描。被锁批次停留 RETRYING、租约 10 分钟后本应被重拾，但重拾者（扫描器自身）就是挂死方。
⑦ **时间尺度**：单次挂死即触发（秒级）；此后 PENDING/RETRYING 积压随提交速率无界增长，直至进程重启。
⑧ **触发前提**：任一下游服务在重试调用路径上挂死（不返回也不断连）。
⑨ **运行影响**：整个重试子系统静默失效——新任务的延迟重试、死信前的最后重试全部停止；仅占用的 1 条全局 worker 线程异常（见 CL-002），无 ERROR、无指标、无积压暴露，健康检测不可见。
⑩ **缺陷族**：F5（挂死对健康检测不可见）。
⑪ **对账**：已知交叉"扫描停摆"已在 C1/C3/C6 立案——本条**不重复立案根因**，登记 C7 新面：停摆的累积性（积压无界、无积压指标）与自愈缺失（唯一恢复 = 重启；租约机制因扫描器同殁而失效）。`stopScanning` 的 `cancel(false)`（RetryScannerImpl.java:105-111）对已阻塞线程无效，优雅停机同样回收不了该线程。
⑫ **修复方向**：processor 对单条执行施加 `retryingTimeoutMs` 上限（`orTimeout`/带超时 get），或扫描线程与执行线程解耦（提交后不 join）；补积压深度与"上次扫描完成时间"健康指标。
⑬ **置信度**：高（机制链完整；下游是否可挂死依赖 rpc 底层超时配置，已按覆盖度声明边界处理）。
⑭ **复核状态**：**降级 P1→P2（条件化）**；新发现（同族关联：WI 1.2 MA-04 Avro registry 无请求超时，不同模块）；复核亲自核实默认装配链并采纳第 2 轮修订——`RetryEngineImpl` @Inject 按类型注入（app-engine.beans.xml 不指定实现），默认绑定 `nopRpcServiceInvoker` 别名=`nopClusterRpcServiceInvoker`（rpc-cluster-defaults.beans.xml primary=true；mesh on 时为 nopHttpRpcServiceInvoker），全仓 `IRpcServiceInvoker` 实现仅此两类；委托 `HttpRpcService`（toHttpRequest 不设 per-request timeout，HttpRequest.timeout 默认 0）→ 默认 raw client = JdkHttpClient（http-client-jdk.beans.xml `nopRawHttpClient` ioc:default="true"；okhttp/apache 无 main-source bean）；HttpClientConfig readTimeout=30s，JdkHttpClient :218-221 回退 readTimeout 且 :166-167 `sendAsync(BodyHandlers.ofByteArray())` 使超时覆盖整个响应交换（慢滴亦封顶）。**30s 超时后循环行为**：future 异常完成（非同步抛）→ doExecute whenComplete attempt 落账 FAILED → handleExecutionFailure 计数入退避 → :639-643 打 scanner.execute-failed → join 解除 → doScan 排水循环继续，**循环存活**。永久停摆前提收窄为：自定义无超时 invoker / 显式禁超时 / 非默认 client 慢滴（**修正**：Apache responseTimeout 只覆盖到响应头 + soTimeout 按读间隔，同样可被慢滴绕过，第 2 轮仅指 OkHttp 不完整；均非默认装配，不改判级）。机制本体经 BindScheduledExecutor（RetryScannerImpl :165-167 → :72-105 在 worker 上 command.run() 返回后才重排下一轮）核实**增强**：doScan 阻塞即链条永久停摆且绝不重入；`stopScanning` cancel(false) 不中断阻塞线程（:105-111）核实。与 C7-015 关系（修订载体、默认装配残余退化面）与合并建议见文末「维度复核结论」，供 Phase 6 裁定（不合并条目）。

### [nop-retry-nop-tcc-C7-003] 解析/同步异常路径产生永不终止的僵尸记录，每租约周期放大一条全栈 ERROR（F9/F4）
① **发现ID**：nop-retry-nop-tcc-C7-003
② **维度子项**：C7-1 日志放大 + C7-4 自愈语义（非终态出口缺失）
③ **严重度**：P1——一旦进入即必然无界增长（日志/DB churn 永续）；入口频率低（需行内 payload 损坏或 invoker 同步抛出），但后果链完全具体化，故按"罕见但可具体化"从 P1 判级
④ **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:268-293`（守卫仅覆盖两个空字段）、`:290`（裸 parseMap）；`RetryScannerImpl.java:146-150`（catch 后无状态推进）；`RetryEngineImpl.java:454-456`（attempt 先落 RUNNING）
⑤ **证据片段**：
```java
// RetryEngineImpl.java:289-292 —— 空 payload 已兜底死信，但损坏 JSON 在此处裸抛
NopRetryPolicy policy = loadPolicyOrDefault(record.getPolicyId());
ApiRequest<?> request = ApiRequest.build(JsonTool.parseMap(requestPayload));
return executeWithRetry(record, policy, request, cancelToken, null);
```
```java
// RetryScannerImpl.java:146-150 —— 同步异常被捕获后循环继续，但记录状态/计数零推进
try {
    processor.accept(lockedRecords);
} catch (Exception e) {
    LOG.error("nop.retry.execute-retry-fail", e);
}
```
⑥ **机制**：`tryLockRecordsForProcess` 在处理前已把记录置 RETRYING 并推 `nextTriggerTime = now + retryingTimeoutMs`（RetryRecordStoreImpl.java:195-204）。随后 `parseMap`（或 `invokeAsync` 同步抛出，此时 attempt 行已以 RUNNING 落库且 whenComplete 未挂接）同步抛出 → 异常止于 scanner 的 catch → 记录 retryCount 不增、状态不进死信。10 分钟后租约到期被再次拾取 → 同点再抛 → **无限循环**。对照：空 serviceName/payload 的坏记录已被修复为直接死信（:275-287），解析失败是同一族漏网路径。
⑦ **时间尺度**：每条僵尸记录 144 条全栈 ERROR/天（默认租约 10 分钟），年级 ~5.3 万条/记录；attempt 的 RUNNING 行永滞。
⑧ **触发前提**：requestPayload 为非法 JSON / 非对象 JSON（外部改数、迁移失误），或 invoker 对某记录同步抛 RuntimeException。
⑨ **运行影响**：错误日志无界放大干扰告警信噪比；僵尸记录永远占据"活跃重试"视图；RUNNING attempt 使执行耗时统计失真；DB 每 10 分钟一次无效读-写循环。
⑩ **缺陷族**：F9（静默吞异常后无出口）+ F4（日志/状态账本面）。
⑪ **对账**：与已知 C1/C3/C6 立案无重叠（该族立案针对并发挂接/资源，本条是状态机出口缺失）；owner doc "死信化"清单未含解析失败分支。
⑫ **修复方向**：`executeRetryFromScanner` 内以 try/catch 包裹反序列化与策略装载，异常直接走 `moveToDeadLetter`（同 :275-287 的既有兜底）；为同步抛出的 invokeAsync 补 attempt 失败落账。
⑬ **置信度**：高（代码路径确定；入口频率评估为中低）。
⑭ **复核状态**：保留 P1；新发现；:290 裸 parseMap 与 invoker 同步抛（doExecute :454-456 attempt 先落 RUNNING、whenComplete 未挂接）两入口、tryLock 先置 RETRYING+租约（:195-204）、scanner catch 无状态推进（:146-150）逐步核实；:275-287 空字段兜底及其注释自认「此前僵尸问题」反证本条为同族漏网路径；失效面完备（第 2 轮已并入 R2(b) 瞬态僵尸与 :510+:641 双 ERROR），F3 兄弟站点 grep 复核无缺漏。

### [nop-retry-nop-tcc-C7-004] Error 逃逸使周期扫描任务被 JUC 静默取消，扫描器死亡无痕迹（F5）
① **发现ID**：nop-retry-nop-tcc-C7-004
② **维度子项**：C7-2 挂死可检测性（调度存活）
③ **严重度**：P2——触发源为 Error（OOM/StackOverflow/NoClassDefFound 等），频率低，属监控盲区 + 理论风险；但一旦发生与 C7-002 同效且更彻底（连占用线程都不留）
④ **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/scanner/RetryScannerImpl.java:119-154`；平台设施 `nop-kernel/nop-commons/.../DefaultScheduledExecutor.java:132-135`
⑤ **证据片段**：
```java
// RetryScannerImpl.java:119-154 —— 两层防护均只 catch Exception
try {
    do { ... processor.accept(lockedRecords); ... }
    while (running);
} catch (Exception e) {
    LOG.error("nop.retry.scanner.scan-failed", e);
}
```
```java
// DefaultScheduledExecutor.java:132-135 —— 裸委托 ScheduledThreadPoolExecutor
public Future<?> scheduleWithFixedDelay(Runnable command, ...) {
    checkEnabled();
    return executor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
}
```
⑥ **机制**：`ScheduledThreadPoolExecutor.scheduleWithFixedDelay` 的 JUC 契约为"任务抛出异常则后续执行全部抑制"。doScan 的防护面是 `Exception`，任何 `Error`（如 ORM 层抛出的 NoClassDefFoundError、OOM）穿透后：周期任务被静默取消、无重试、无告警钩子；`running` 仍为 true，`stopScanning` 语义与实际状态背离。
⑦ **时间尺度**：Error 发生时刻起永久失效，直至进程重启。
⑧ **触发前提**：扫描路径上抛出任意 Error 子类（堆耗尽、类加载失败、平台库升级错配等）。
⑨ **运行影响**：重试全量停摆且无日志可查（Error 多被线程工厂吞或打至不同 appender），故障定位极度困难。
⑩ **缺陷族**：F5。
⑪ **对账**：与 C7-002 同症状不同根因（C7-002 是挂起，本条是被取消）；平台设施自身实现按误报校准不单独立案，此处立案对象是模块未做 Throwable 自防御。
⑫ **修复方向**：doScan 最外层改 catch `Throwable`（或调度包装 runnable 捕获 Throwable 记日志后正常返回），并增加"扫描心跳"指标。
⑬ **置信度**：高（JUC 契约确定）。
⑭ **复核状态**：保留 P2（**证据修正**）；新发现；生产绑定路径实为 BindScheduledExecutor（RetryScannerImpl :165-167 `globalTimer().executeOn(globalWorker())`），其对 command.run() 的 **catch(Throwable) → LOG.error("nop.err.thread-pool.execute-fail") → future.completeExceptionally → 链条终止不重排**（BindScheduledExecutor :82-91）——「Error 逃逸→周期任务永久停摆、`running` 语义背离、需重启」成立，但初审「Error 多被线程工厂吞或打至不同 appender、无日志可查」表述**不成立**，修正为「单条 ERROR 日志后静默停摆」；裸 JUC 静默取消契约（DefaultScheduledExecutor :130-134，行号微漂）仅适用引擎/扫描器被直接注入 DefaultScheduledExecutor 的场景（测试面）。

### [nop-retry-nop-tcc-C7-005] tcc 账本清理 API 无生产调用方 + 耗尽行双重永久排除 + 全表零索引，扫描代价随时间必然劣化（F4）
① **发现ID**：nop-retry-nop-tcc-C7-005
② **维度子项**：C7-1 单调增长盘点（tcc 事务/分支记录）
③ **严重度**：P1——增长无界（清理入口存在但全仓库无人调用）且查询代价随表增长单调上升（过期扫描与清理查询均为全表扫描），时间尺度明确可具体化
④ **位置**：`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:291-326, 332-355`；`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:350-355`；`nop-tcc/model/nop-tcc.orm.xml`（全文件 150 行，`<indexes>` 出现次数 = 0）
⑤ **证据片段**：
```java
// TccEngine.java:350-355 —— 唯一的清理入口
@Override
public void cleanCompletedTransactions(long retentionTime) {
    // 契约：只删除已成功完结/取消的事务，未知状态保留等待人工处理。
    repository.removeCompletedRecords(retentionTime, true);
}
```
```java
// TccRecordStore.java:297-305 —— 过期扫描查询：无索引可用的三条件 + 排序
query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_expireTime, minTime));
query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_status, TccStatus.CONFIRM_SUCCESS.getCode()));
query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_retryTimes, maxRetryCount));
query.setLimit(pageSize);
query.addOrderField(NopTccRecord.PROP_NAME_beginTime, true);
```
⑥ **机制**：三重叠加。(a) 全仓 grep 证实 `cleanCompletedTransactions`/`removeCompletedRecords` 零生产调用方（仅定义与测试）——每笔 TCC 事务沉淀 1 record + N branch 行，已完结行也只增不删；(b) `retryTimes≥maxRetryCount` 的非终态行被过期扫描（:303）与 `onlyCompleted=true` 清理（:339）**同时**永久排除，成为既不可恢复也不可清理的死行（见 C7-006）；(c) `nop_tcc_record`/`nop_tcc_branch_record` 无任何索引，`fetchExpiredRecords`（每轮驱动一次）与 `removeCompletedRecords` 均为全表扫描 + `beginTime` 排序。
⑦ **时间尺度**：随事务量线性。日均 10k 事务 → 年级 ~730 万行（record+branch）；每轮扫描代价从毫秒级滑向秒级乃至全表 IO。
⑧ **触发前提**：生产部署未外挂定时清理任务（仓库内不存在任何接线，默认部署即如此）。
⑨ **运行影响**：恢复扫描（唯一崩溃恢复通道）越来越慢——恢复 RTO 随运行时长单调恶化；DB 压力单调上升。
⑩ **缺陷族**：F4。**DB 侧单独标注**：增长本身受 DB 容量约束；索引缺失与清理未接线是模块自有模型/装配问题，非 DB 侧责任。
⑪ **对账**：与已知交叉"扫描停摆/在途补偿超时丢弃"不同面（本条是稳态劣化而非停摆）；`onlyCompleted=true` 的保守契约在 TccEngine 注释中有声明（未知状态保留人工处理），故 (b) 的语义有意识，但"耗尽行连清理语义都不覆盖"未在任何文档声明。
⑫ **修复方向**：为 `expireTime+status+retryTimes` 与 `beginTime` 建索引（orm 模型层）；提供/接线的清理任务额外处理"耗尽超期"行（先标 KILLED 类终态再清理，保留审计窗口）。
⑬ **置信度**：高。
⑭ **复核状态**：保留 P1；新发现（同族关联：WI 1.2 MA-05 持久化台账无保留策略，不同模块）；三重叠加独立复核属实——`cleanCompletedTransactions`/`removeCompletedRecords`/`checkExpiredTransactions` 全仓 grep 零生产调用方（仅接口/实现/测试）、`nop-tcc.orm.xml` 150 行 grep "index" 零命中（对照 retry 侧 IDX_RETRY_SCHEDULER_SCAN 等齐备索引）、:303 与 :338-339 耗尽行双重永久排除；beans 全量无调度/清理钩子（第 2 轮 ② 反向核查维持）。

### [nop-retry-nop-tcc-C7-006] 补偿耗尽的 tcc 事务静默退出扫描窗：不产出、不报错、不终态（F5，已声明边界下的监控盲区面）
① **发现ID**：nop-retry-nop-tcc-C7-006
② **维度子项**：C7-2 挂死可检测性（"不产出也不报错"路径）
③ **严重度**：P2——行为本身是 owner doc 声明的既有语义（判级不按缺陷升级），但"耗尽时刻无任何日志/指标/终态标记"构成监控盲区 + 长时运行下的预留资源悬挂累积
④ **位置**：`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:302-303`；`TccRecordStore.java:313-316`（fetch 即 bump retryTimes）
⑤ **证据片段**：
```java
// TccRecordStore.java:299-303
query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_expireTime, minTime));
// 状态为还没有结束
query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_status, TccStatus.CONFIRM_SUCCESS.getCode()));
// 重试次数未超过上限，超过上限的事务会被忽略，不再处理
query.addFilter(FilterBeans.lt(NopTccRecord.PROP_NAME_retryTimes, maxRetryCount));
```
⑥ **机制**：`fetchExpiredRecords` 每次拾取即 `retryTimes+1`（:313-316）；达到驱动传入的 `maxRetryCount` 后该行不再满足查询条件——**没有**任何代码路径在耗尽时刻写终态、打日志或发指标。事务停留 CANCEL_FAILED/CONFIRM_FAILED 等非终态，参与方 Try 预留的资源永久悬挂。
⑦ **时间尺度**：每条持续故障的悬挂事务经 `maxRetryCount × 驱动周期`（默认参数下 100 个扫描窗）后进入静默区；悬挂行随故障事务数累积（叠加 C7-005 永不可清理）。
⑧ **触发前提**：参与方 cancel/confirm 持续失败达上限（下游长时间故障后恢复，但协调者已放弃）。
⑨ **运行影响**：资金/库存类预留资源泄漏无告警；数据面只能靠人工巡检 `nop_tcc_record` 非终态行发现；与 C7-005 叠加后这些行连清理语义都不覆盖。
⑩ **缺陷族**：F5（监控盲区）。
⑪ **对账**：**已声明边界**——owner doc（nop-tcc.md 崩溃恢复语义节）明确记载"分支补偿计数耗尽 `nop.tcc.default-max-retry-times`（默认 100）后被扫描窗口永久排除、参与方预留资源悬挂"。本条不推翻该裁定，仅登记其长时运行累积后果与可观测缺口。
⑫ **修复方向**：在 fetch 发现 `retryTimes` 达上限时（或达到前最后一击）写一条 WARN + 考虑置显式"放弃补偿"终态/标志位，供运维视图与 `cleanCompletedTransactions` 扩展语义消费。
⑬ **置信度**：高。
⑭ **复核状态**：保留 P2；新发现（登记面——owner doc（nop-tcc.md 崩溃恢复语义节）确已声明「计数耗尽默认 100 后被扫描窗口永久排除、参与方预留资源悬挂」，行为本体按误报排除规则非缺陷；对照面为 plan 2388 修复声明而非审计台账，故现状仍标新发现）；:302-303 排除过滤与 :313-316 拾取即 bump 核实，耗尽时刻零日志/零终态/零指标的可观测缺口维持 P2，第 2 轮 T1/T2「100 轮封顶后转静默」与本条自洽。

### [nop-retry-nop-tcc-C7-007] tcc 期限/租约全链路使用原始墙钟，NTP 步进触发恢复风暴或恢复冻结（C7-5 时钟敏感性）
① **发现ID**：nop-retry-nop-tcc-C7-007
② **维度子项**：C7-5 时钟与期限
③ **严重度**：P2——NTP 大步进为低频事件，但后果（批量误恢复风暴）完全具体化；同机部署的 retry 侧已示范正确设施（DbEstimatedClock），tcc 未采用
④ **位置**：`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:294, 328-330`（以及 :88-90/:173/:240 的 expireTime/endTime 写入）；平台 `nop-kernel/nop-api-core/.../CoreMetrics.java:23-24`
⑤ **证据片段**：
```java
// TccRecordStore.java:328-330 —— 过期判定的时钟源
protected long getCurrentTime(IOrmEntityDao<NopTccRecord> dao) {
    return CoreMetrics.currentTimeMillis();
}
// CoreMetrics.java:23-24 —— 即 System.currentTimeMillis()
public long currentTimeMillis() {
    return System.currentTimeMillis();
}
```
对照 retry 侧同场景使用 DB 估计时钟：`RetryRecordStoreImpl.java:154`（`getDbEstimatedClock().getMinCurrentTimeMillis()`）、`:190/:314`（getMax）。
⑥ **机制**：事务 `expireTime = beginTime + defaultTxnTimeout(60s)`（:88-90）、扫描判定 `expireTime < CoreMetrics.currentTimeMillis() - expireGap`（:294）全部绑定本机墙钟。系统时钟**前跳**（NTP 校时、虚拟机迁移）：所有 `expireTime` 瞬间落入选窗，一批 TRYING/CANCELLING 事务被同时恢复，对参与方 cancel/confirm 接口形成突发放大（下游本已过载时恰为最坏时点）；时钟**回拨**：`expireTime` 集体"未到期"，恢复冻结直至墙钟追回，悬挂窗口最长等于回拨量。
⑦ **时间尺度**：单次校时事件（亚秒级跳变）；风暴持续一个 expireGap 窗口，影响面 = 当时全部未完结事务数。
⑧ **触发前提**：主机时钟步进 > expireGap（默认扫描窗口粒度），常见于未启用平滑同步（slew）的 VM/容器环境。
⑨ **运行影响**：恢复风暴 = 对下游的 cancel 请求尖峰 + DB 状态行集中写；恢复冻结 = 参与方预留资源悬挂时间不可控。
⑩ **缺陷族**：时钟域混用（F5 邻接——期限语义失真）。
⑪ **对账**：owner doc 未声明时钟假设；retry 侧 `newRecord` 亦有局部混用（另立 C7-011），本条为 tcc 全链路墙钟。
⑫ **修复方向**：tcc 扫描/租约统一改用 `IOrmEntityDao#getDbEstimatedClock`（与 retry 一致），或文档强制部署要求 chrony slew 模式。
⑬ **置信度**：高（时钟源与比较逻辑直接可读）。
⑭ **复核状态**：保留 P2；新发现；`getCurrentTime` = `CoreMetrics.currentTimeMillis()` = `System.currentTimeMillis()`（默认 IClock 直读核实）、expireTime 写入 :88-90 同源核实，retry 侧 DB 估计钟对照（RetryRecordStoreImpl :154/:190/:314）属实；恢复风暴/冻结后果链具体，NTP 大步进为单一低频条件故 P2 恰当。

### [nop-retry-nop-tcc-C7-008] 回调策略成环无护栏：每轮终态孵化下一轮记录，副作用与死信链式自增殖（重试风暴面）
① **发现ID**：nop-retry-nop-tcc-C7-008
② **维度子项**：C7-3 重试风暴（策略组合缺陷）
③ **严重度**：P2——需要管理员把 callbackPolicyId 配置成含环的链（边界配置缺陷），但一旦成环即为自维持的无限副作用调用 + 无界记录增长，运行时无任何深度护栏
④ **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:541-574`；`nop-retry/nop-retry-dao/.../entity/NopRetryPolicy.java:58-61`
⑤ **证据片段**：
```java
// RetryEngineImpl.java:555-563 —— 每次终态按当前记录 idempotentId 再孵化 "_callback" 新键
IRetryTask callbackTask = newRetryTask(callbackService, callbackMethod)
        .withPolicyId(policy.getCallbackPolicyId())
        .withIdempotentId(record.getIdempotentId() + "_callback");
...
executeTask(callbackTask, callbackRequest, null)
```
⑥ **机制**：回调记录使用的策略是 `callbackPolicyId`；回调记录自身到达终态时，`handleExecutionSuccess/Failure` 再次检查**该策略**的 `hasCallbackPolicy()`（NopRetryPolicy.java:58-61）。若 A→A 或 A→B→A 成环，每条记录终态孵化一条新记录，idempotentId 逐环追加 `_callback`（`X_callback`、`X_callback_callback`…），永远不与 UK_RETRY_IDEMPOTENT_ID 冲突，环内无深度/次数计数。每环都会以回调数据为 payload 真实调用原业务 service/method。
⑦ **时间尺度**：单环周期 = 回调记录自身执行+退避（默认 initial 5s、max 60s、3 次重试、失败入死信）≈ 分钟级；记录数按环长指数分支、时间上永续。
⑧ **触发前提**：policy 表中回调链存在环（含自环）。无任何配置校验拦截。
⑨ **运行影响**：下游接口被持续重放、retry 三表（叠加 C7-001）加速膨胀、日志风暴；与 job bridge 等自动提交方叠加时难以人工止血（只能改 policy 断环）。
⑩ **缺陷族**：重试风暴（F4 联动）。
⑪ **对账**：owner doc 声明了回调开关与 `_callback` 后缀语义，未声明环检测缺失；非已知交叉立案对象。
⑫ **修复方向**：孵化回调前做 callbackPolicyId 链路环检测（装载期校验），或对同源记录的回调深度计数（payload 内传 depth，超限拒孵）。
⑬ **置信度**：高（代码无任何环防护；触发依赖配置错误故降 P2）。
⑭ **复核状态**：保留 P2；新发现；回调记录按 callbackPolicyId 策略、其终态再查该策略自身 `hasCallbackPolicy`（NopRetryPolicy.java:58）再孵化、idempotentId 逐环追加 `_callback` 永不撞 UK、无环检测/深度计数（:555-563）逐行核实；owner doc 声明回调语义与 `_callback` 后缀但未声明环风险——不构成误报排除；触发需管理员配置成环（边界配置缺陷）故 P2。

### [nop-retry-nop-tcc-C7-009] 分支级 maxRetryTimes/nextRetryTime 为死字段，"max-retry-times 100" 契约实际由 record 级计数承载（契约漂移）
① **发现ID**：nop-retry-nop-tcc-C7-009
② **维度子项**：C7-3 重试风暴（上限实现核对）
③ **严重度**：P3——不构成直接稳定性缺陷，但声明的"分支补偿计数耗尽 max-retry-times（默认 100）"与真实生效机制存在语义错位，影响后续维护与调参判断
④ **位置**：`nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java:68-71, 110`；`nop-tcc/nop-tcc-api/src/main/java/io/nop/tcc/api/ITccBranchRecord.java:37`
⑤ **证据片段**：
```java
// TccRecordStore.java:108-110 —— 每条分支写入 maxRetryTimes=100
branchRecord.setBranchNo(request.getParentBranchNo() + 1);
branchRecord.setMaxRetryTimes(defaultMaxRetryTimes);
// ITccBranchRecord.java:37 —— 生产代码零读取（全仓 grep 仅测试调用）
Integer getMaxRetryTimes();
```
⑥ **机制**：`branchRecord.maxRetryTimes`（默认 100）与 `nextRetryTime` 列在生产代码中**只写不读**（grep 全仓：仅测试 set/assert）。实际耗尽机制是：`fetchExpiredRecords` 每拾取一次对 **record 级** `retryTimes+1`，由**驱动方传入的** `maxRetryCount` 参数决定上限（`checkExpiredTransactions(long expireGap, int maxRetryCount, ...)`，TccEngine.java:315）——即真实上限既不是分支列上的 100，也不受分支级粒度控制，而是每个外部驱动调用点的临时参数。
⑦ **时间尺度**：n/a（语义面）。
⑧ **触发前提**：驱动方传参与 `nop.tcc.default-max-retry-times` 不一致时（如运维脚本传 5），实际耗尽速度与文档描述背离。
⑨ **运行影响**：调参误导（改配置 `nop.tcc.default-max-retry-times` 只影响新分支的死列，不影响既定驱动参数）；分支级细粒度重试上限的合理预期落空。
⑩ **缺陷族**：契约漂移（非 F 族稳定性缺陷）。
⑪ **对账**：owner doc 的耗尽描述（"分支补偿计数耗尽后被扫描窗口永久排除"）按 C7-006 已声明；本条指出"计数主体与上限来源"与文档措辞的偏差。
⑫ **修复方向**：让 `fetchExpiredRecords` 的上限回退读取分支/记录列（驱动参数缺省时），或在文档明确"分支 maxRetryTimes 列当前仅为信息性"。
⑬ **置信度**：高（grep 覆盖全仓）。
⑭ **复核状态**：保留 P3；新发现；grep 全仓核实 `getMaxRetryTimes` 生产代码只写不读（TccRecordStore :110 唯一写入点；读取仅接口声明 ITccBranchRecord.java:37 + 生成 bean + 测试）、分支 `nextRetryTime` 仅存在于生成 bean，真实上限 = record 级 retryTimes（:313-316 bump）+ 驱动参数 maxRetryCount（TccEngine :315、TccRecordStore :303）——契约漂移属实，语义面 P3 恰当。

### [nop-retry-nop-tcc-C7-010] 过期扫描整轮耗时随挂死补偿数线性放大（100×expireGap 上限），看门狗自身可退化为小时级节律（F5 邻接）
① **发现ID**：nop-retry-nop-tcc-C7-010
② **维度子项**：C7-2 挂死可检测性（看门狗自身的时延退化）
③ **严重度**：P2——单条挂死已有超时保护（有测试钉住），但"多条同时挂死时整轮串行等待"是未声明的新面；不致停摆故不判 P1
④ **位置**：`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:315-343`
⑤ **证据片段**：
```java
// TccEngine.java:324-340 —— 页内逐条串行阻塞等待，单条上限 expireGap
for (ITccRecord record : records) {
    ...
    try {
        checkExpiredAsync(record).toCompletableFuture().get(expireGap, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        LOG.error("TccEngine.checkExpiredTransactions compensation timeout for txnId={}", ...);
```
⑥ **机制**：单页 100 条逐条 `get(expireGap)` 串行；每页最多耗时 `100 × expireGap`（expireGap 为驱动参数，如 60s → 单轮上限 100 分钟）。挂死的补偿 future 虽被放弃等待，但其后台链仍在运行（叠加已知交叉"在途补偿超时丢弃"：下一轮同记录重入时可能与残留在途补偿并发）。此外 while 循环内 `repository.fetchExpiredRecords`（:320）无 try/catch，一次 DB 异常即终止整轮恢复（由外部驱动的重试节律兜底，若驱动无重试则本轮恢复静默缺失）。
⑦ **时间尺度**：随同时挂死的补偿记录数线性：10 条挂死 × 60s gap ≈ 单轮 10 分钟；100 条 ≈ 100 分钟，恢复 RTO 从分钟级滑向小时级。
⑧ **触发前提**：一批参与方同时挂死（网络分区黑洞场景——连接不断也不响应）。
⑨ **运行影响**：崩溃恢复通道的实效节律在故障场景下退化 1-2 个数量级；耗尽的悬挂窗口（C7-006）被间接拉长。
⑩ **缺陷族**：F5（期限/节律退化面）。
⑪ **对账**：单条超时修复已在测试 `TestTccEngine.java:435-473` 钉住（"单条补偿挂死时必须受超时保护继续运行"）；本条登记的是聚合维度的未声明退化。与"在途补偿超时丢弃"交叉对账：本条不改判其并发面，仅指出超时丢弃后的在途残留会加重下轮重入。
⑫ **修复方向**：页内并行化补偿（带全页总超时）或对连续超时记录提前熔断跳过；`fetchExpiredRecords` 包 try/catch 记日志后退出本轮。
⑬ **置信度**：高。
⑭ **复核状态**：保留 P2；新发现；:324-341 页内逐条 `get(expireGap)` 串行、:320 `fetchExpiredRecords` 无 try/catch（DB 异常终止整轮恢复）、单条超时由测试钉住（TestTccEngine :435-473）均核实；与 C7-015（retry 排水轮）同族不同机制，两轮表述一致。

### [nop-retry-nop-tcc-C7-011] retry 首提租约混用本地墙钟与 DB 估计时钟：时钟偏差直接折算为首执行重复窗口（C7-5 时钟敏感性）
① **发现ID**：nop-retry-nop-tcc-C7-011
② **维度子项**：C7-5 时钟与期限（模块内时钟域一致性）
③ **严重度**：P2——需要主机与 DB 时钟偏差（理论风险，但偏差量精确折算为重复执行窗口，链路完全具体化）
④ **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:116-119`（写入用本地钟）vs `:154`（拾取判定用 DB 钟）、`:190/:314`（锁定/期限用 DB 钟）
⑤ **证据片段**：
```java
// RetryRecordStoreImpl.java:116-119 —— 首提租约用 CoreMetrics（本地墙钟）
// 到期时间推迟一个租约周期：调用方发起首次执行期间记录保持PENDING且nextTriggerTime=now，
// 会被扫描器误拾取并发起第二次执行（重复副作用）。推迟后扫描器只在调用方失联后接管
record.setNextTriggerTime(new Timestamp(CoreMetrics.currentTimestamp().getTime()
        + NopRetryConstants.DEFAULT_RETRYING_TIMEOUT_MS));
// RetryRecordStoreImpl.java:154 —— 扫描拾取却用 DB 估计时钟
long now = dao.getDbEstimatedClock().getMinCurrentTimeMillis();
```
⑥ **机制**：扫描器拾取条件为 `dbNow ≥ nextTriggerTime`。设本机时钟落后 DB 时钟 δ：有效首提租约 = 10 分钟 − δ；δ > 10 分钟（主机时钟配置错误、容器节点漂移）时记录落库即可被拾取，扫描器与首提方**并发执行同一业务副作用**（正是该注释声称要防止的场景）。deadline 判定（RetryEngineImpl.java:306）同样跨域比较（DB 钟 − 本地钟写入的 createTime），偏差单向拉伸/压缩总截止。
⑦ **时间尺度**：偏差存在期间持续有效；单条记录的风险窗口为首次执行时长。
⑧ **触发前提**：应用主机与 DB 时钟偏差 δ 接近或超过 `DEFAULT_RETRYING_TIMEOUT_MS`（10 分钟）。
⑨ **运行影响**：非默认 NTP 配置的部署中，防重复执行的租约保护静默缩水乃至失效；deadline 语义漂移。
⑩ **缺陷族**：时钟域混用。
⑪ **对账**：与 C7-007（tcc 全链路墙钟）同族不同模块；retry 侧扫描/锁定主链路已正确使用 DB 钟，本条是残留混用点。
⑫ **修复方向**：`newRecord` 的 `nextTriggerTime` 改用 `getDbEstimatedClock().getMaxCurrentTimeMillis()`（保守方向，宁长勿短）。
⑬ **置信度**：高（两处时钟源均直接可读）。
⑭ **复核状态**：保留 P2；新发现；:116-119（newRecord 用 `CoreMetrics.currentTimestamp()` 本地钟）vs :154（fetch 用 DB 估计钟 min）/:190/:314（锁定/期限用 max）直接可读核实，deadline 判定（engine :306）跨域比较成立；偏差量精确折算为重复执行窗口、链路具体，但前提（主机-DB 时钟偏差 ≈ 10min）属罕见配置，条件化 P2 恰当。

### [nop-retry-nop-tcc-C7-012] 扫描器只服务单一 namespace：非 default 命名空间的记录永久 PENDING、零日志零接管（F5/F2）
① **发现ID**：nop-retry-nop-tcc-C7-012
② **维度子项**：C7-2 挂死可检测性（retry scanner 覆盖面）+ C7-4 自愈语义
③ **严重度**：P1——任何一次带非 default namespace 的提交失败后，其重试语义**必然**静默失效（半个成功：首次执行照常、失败重试永不发生），且无任何日志暴露
④ **位置**：`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java:60, 88-95, 159-163`；提交侧 `nop-job/nop-job-retry-adapter/.../NopRetryJobRetryBridge.java:40`
⑤ **证据片段**：
```java
// RetryRecordStoreImpl.java:59-65 —— 单实例 store 绑定单 namespace（默认 "default"，setter 无 @InjectValue）
private String namespaceId = DEFAULT_NAMESPACE_ID;
```
```java
// RetryRecordStoreImpl.java:159-163 —— fetch 硬过滤本 store 的 namespaceId
// 自动过滤命名空间
query.addFilter(FilterBeans.eq(PROP_NAME_namespaceId, namespaceId));
// 查询 nextTriggerTime <= now 的 PENDING/RETRYING 记录
query.addFilter(FilterBeans.in(PROP_NAME_status, PENDING_STATUSES));
```
```java
// NopRetryJobRetryBridge.java:37-41 —— 提交侧按事件携带任意 namespaceId
IRetryTask task = retryEngine.newRetryTask(SERVICE_NAME, SERVICE_METHOD)
        .withPolicyId(event.getRetryPolicyId())
        .withIdempotentId(event.getJobFireId())
        .withNamespaceId(event.getNamespaceId())
```
⑥ **机制**：提交路径按 task 的 namespaceId 落库（RetryRecordStoreImpl.java:94），扫描路径按 store 单值 `namespaceId="default"` 过滤（:160）；`setNamespaceId` 是普通 setter，beans 装配（app-engine.beans.xml）只注册单例 store，不按 namespace 分片。job-retry-adapter 更是直接透传 job 事件的 namespaceId。于是 namespace ≠ "default" 的记录：首次执行由提交方完成（照常），一旦失败进入 `PENDING + nextTriggerTime`，扫描器查询永远看不到它——重试链静默断裂。
⑦ **时间尺度**：单条记录失败后即刻（下个扫描周期起）持续；随非 default namespace 的使用面线性放大。
⑧ **触发前提**：任一调用方使用 `withNamespaceId`（job 桥接即如此）且值非 "default"。
⑨ **运行影响**：重试承诺静默违约（提交方以为有 3 次重试+死信兜底，实际一次失败即沉没）；记录无终态、无死信、无日志，`nop_retry_record` 中形成只增不减的 PENDING 堆积（叠加 C7-001）。
⑩ **缺陷族**：F5（覆盖面盲区）+ F2（半个成功）。
⑪ **对账**：owner doc 声明"命名空间/分组隔离"为功能特性，未声明"扫描器仅覆盖单 namespace"的限制；与已知交叉立案无重叠。
⑫ **修复方向**：fetch 过滤放宽为全部 namespace（隔离仅靠分区键/UK 已足够），或按 namespace 多实例装配扫描器；最低限度在提交时校验 namespace 与 store 匹配并告警。
⑬ **置信度**：高（查询过滤与装配方式直接可读）。
⑭ **复核状态**：保留 P1（**附条件面标注**）；新发现；store 单值 `namespaceId="default"` 硬过滤（:60/:160）+ 提交侧透传（newRecord :94 优先 task 值、job bridge :40 无条件携带 `schedule.getNamespaceId()`——JobCompletionProcessorImpl :230-234 锚定来源、MessageConsumeFailureBridge :89-90 同形）逐步核实；触发 = 「非 default namespace 的提交」单一配置条件（多租户为平台常规用法面），时序每步可信维持 P1；**default-only 单租户部署不触发**——若 Phase 6 合并裁定采「部署形态收窄」口径，可与 C7-002 同规则再裁，本复核以单一常见配置条件维持 P1。

### [维度集外线索-CL-002] 重试扫描任务以阻塞 join 长期占用共享全局 worker 池线程（C4 形态线索，不立案展开）
- 维度集外线索（C4 线程/池形态，P1 级），与 C7-002 同根因，供 C4 维度对账：
- 证据：`RetryEngineImpl.java:648` 的 `CompletableFuture.allOf(futures).join()` 运行于 `RetryScannerImpl.getExecutor()`（:165-167）绑定的 `GlobalExecutors.globalWorker()`（`GlobalExecutors.java:85-96`，maxPoolSize 默认 30，`CommonConfigs.java:26-28`）。
- 链：一次下游挂死 → 该全局 worker 线程被 join 永久占住；`stopScanning` 的 `cancel(false)` 不中断线程，线程不回收。长时运行下每发生一次占一条，属共享池的主路径占用泄漏面。
- 另：tcc `checkExpiredTransactions` 的 `get(expireGap)`（TccEngine.java:331）同样在调用线程阻塞，占用时长有界（≤expireGap/条）但串行放大见 C7-010。
- 维度集外线索，不立案展开。
- **复核状态**：确认；归属 C4 维度，建议严重度 P2（C7-002 条件下 P1）；形态独立复核属实——doScan 体（含 :648 join 与整轮排水）经 `globalTimer().executeOn(globalWorker())`（RetryScannerImpl :165-167）绑定 BindScheduledExecutor 后在 worker 线程执行（BindScheduledExecutor :66-105 `executor.execute(command)`），globalWorker 默认 maxPoolSize=30（GlobalExecutors :85-96、CommonConfigs :26-27）；与 C7-002/015 不重复（彼为停摆/节律退化面，此为共享池占用面），有独立行动意义（scanner 专用执行器/脱离共享池）；同族先例 WI 1.2 MA-06、WI 1.5 MA-04（均不同模块）。

### [维度集外线索-CL-003] TccContext thread-local 在 future 永不完成时残留，池线程后续请求可能误 join 旧事务（C2 形态线索，不立案展开）
- 维度集外线索（C2 thread-local 形态，P1 级候选），供 C2/C3 维度对账：
- 证据：`TccRpcServiceInterceptor.java:46-52` —— 桥接上下文的清理挂在 `whenComplete` 上：
```java
TccContext.setCurrent(fromRequest);
return thenOnContext(runWithTccContext(fromRequest, inv)).whenComplete((ret, err) ->
        TccContext.removeCurrent(fromRequest));
```
- 链：inbound 调用的 future 因下游挂死永不完成 → `removeCurrent` 永不执行 → 池化线程残留 TccContext；下一个无关请求落到该线程时 `tccContext != null` 命中"thread-local 赢"优先级（`TccRpcServiceInterceptor.java:44-45` 注释钉定），携带上一笔事务的 txnId 进入 `runInTransactionAsync` → 命中 `ERR_TCC_TRANSACTION_ALREADY_FINISHED` 或对已死事务做分支记录。`TccGatewayInterceptor.java:102-111` 的三分支清理同构同险。
- 触发前提为一次挂死调用，罕见但完全可具体化；维稳影响跨请求、难复现。
- 维度集外线索，不立案展开。
- **复核状态**：**确认（带描述修正）**；归属 C2 维度，建议严重度 P2（初审「P1 级候选」降级）；清理挂 whenComplete 的结构真实（:50-51、TccGatewayInterceptor :102-111 三分支同构核实），但两点修正——① **载体非裸 ThreadLocal 变量**：TccContext 存于 IContext 属性（`ContextProvider.currentContext()/getOrCreateContext()` → `BaseContextProvider.t_context` ThreadLocal<IContext>，与 plan 2466 R1 审查预注 m-B 一致）；② **跨请求毒化面窄于初审描述**：主 HTTP 入口 ContextHttpServerFilter 每请求 `ContextProvider.newContext()`（attach 替换 ThreadLocal）+ whenComplete close 自愈（BaseContext :315-325 close 时 holder==this 则 clear），毒化仅在「复用环境上下文」的入口路径可达（ApiContextInterceptor/ContextBinder 复用 `currentContext()` 且不滤 closed——同一池线程 + 陈旧 IContext 未关闭 + 同 txnGroup），且「future 永不完成」前提在默认传输装配下受 30s readTimeout 封顶（依赖 C8-012 族 never-completing future 形态）；触发条件收窄故降 P2，形态本身确认保留供 C2 对账。

## 覆盖度与残留边界

**已核实为健壮/有界的点**（不立案）：
- retry 退避上限实现正确：指数退避移位封顶 `1L << Math.min(retryCount-1, 10)` 且受 `maxIntervalMs`（默认 60s）封顶（RetryEngineImpl.java:600-619）；重试次数封顶 `maxRetryCount`（默认 3，不足即死信）；总截止 `deadlineTimeoutMs`（默认 24h）双参核对无误；immediateRetry 环有 `immediateRetryCount`（默认 0）上限。
- retry 崩溃自愈主链成立：租约（RETRYING + nextTriggerTime=now+10min）到期后 `PENDING_STATUSES` 含 RETRYING 会被重新拾取；锁定阶段有乐观锁 CAS（`tryUpdateManyWithVersionCheck`，双扫描实例的拾取互斥成立；执行阶段无续租/fencing token 属已立案交叉"重试链不续租"，其重复执行的副作用沉淀量并入 C7-001 评估）。
- tcc 崩溃恢复对称续跑契约落实到位：`TccTransaction.endAsync` 对 CONFIRMING/CONFIRM_FAILED→confirm、CANCELLING/CANCEL_FAILED→cancel 的路由与守卫（doConfirmAsync/doCancelAsync 的互斥拦截，含 null 脏数据防御）与 owner doc 一致；恢复入口 `timeout=true`、分支 TIMEOUT_SUCCESS 聚合 CANCEL_SUCCESS（TccRunner.java:178-196）核对无误。
- tcc 扫描认领有 fencing：`fetchExpiredRecords` 以版本 CAS 认领（TccRecordStore.java:319-321，"其他线程也在扫描"互斥）+ `updateTccStatusAsync` 按 [id+fromStatus] 单语句 CAS（:188-195），双驱动实例不会双写终态；计数器（retryTimes/retryCount/attemptNo，int）与移位封顶无溢出面。
- `TccStatus` 编码序（finished=11-15）与 `lt(status, CONFIRM_SUCCESS)` 扫描过滤匹配；`fromCode` 对非法 code 返回 null 有防御。
- 每调用小对象面：`DefaultTccServiceMetaLoader.getMethodMeta` 头驱动分支每请求 new 一个 `TccMethodMeta`（DefaultTccServiceMetaLoader.java:90），短命垃圾、无保留，判 P3 以下不立案；meta 表启动期装载后静态。
- 日志抑制核对：tcc `skip-status-update-due-to-conflict` WARN 仅在 CAS 冲突时产生，量级与并发写冲突同阶，有界。

**残留边界（本轮未完全闭合）**：
1. rpc 底层 HTTP client 的内部超时未审（`HttpRpcServiceInvoker`/`ClusterRpcServiceInvoker` 文件内无 timeout 关键字，但更底层 transport 可能有默认超时）——C7-002/C7-010 的触发频率依赖该层结论；即便底层有超时，C7-003 僵尸链与 C7-001 账本增长不受影响。
2. tcc "外部驱动"的部署形态（谁调度 `checkExpiredTransactions`/`cleanCompletedTransactions`）超出本仓库可证范围：仓库内确认零接线（已声明边界），C7-005/006 的生产后果以"默认部署无清理"为前提。
3. nop-job `NopJobFire__rerunFire` 失败→`JobFireFailedEvent`→retry→rerun 的跨模块回环是否可能自激励（任务持续失败时的 storm），属 nop-job 单元审计面，本轮仅在 C7-008 机制中提示。
4. `PartitionResolver` 集群分区分配（enableCluster=true 路径）的正确性属 nop-cluster 单元；retry 侧仅核实其缓存有界与默认全分区扫描（enableCluster=false）。
5. `DbEstimatedClock` 自身精度/DB 抖动行为按平台设施不立案，仅核对其在 retry 侧的 min/max 使用方向正确（fetch 用 min、锁定/期限用 max，保守方向一致）。

**覆盖声明**：审计单元 82 个手写文件中，两模块全部引擎/调度/存储/拦截器/状态机文件已逐一精读（剩余为纯 CRUD BizModel、常量/错误码、API bean/接口声明，均经目录级浏览与定向 grep 排除调度/账本逻辑），C7 六个子项（单调增长、挂死可检测、重试风暴、自愈语义、时钟与期限、每调用小对象）全部覆盖；F4/F5/F9/F2 定向对照均有对应发现或明确排除结论。

---

## 维度复核结论（Phase 4 独立复核，2026-10-16）

> 复核人：C7 维度独立复核子代理（与初审/深挖代理相互独立，怀疑姿态复核）。
> 复核方法：逐条亲自打开引用 file:line 核对证据（行号漂移以 live code 为准修正）；P1 时序逐步可信度校验；owner doc 声明节（`docs-for-ai/03-modules/nop-retry.md`、`nop-tcc.md`）误报排除对照；F3 同族兄弟站点全量 grep 核查；历史对照面（`ai-dev/audits/cra-index.md`、plans 2461–2466、`ai-dev/bugs/`、`ai-dev/lessons/`、`git log -- nop-retry/ nop-tcc/`）现状标注。
> 现状标注基线（复核实测）：`ai-dev/audits/` 下无 nop-retry/nop-tcc 历史审计目录；cra-index WI 1.1–1.5 台账（stream/job/wf/task 单元）零 retry/tcc 交叉条目；两模块最新代码修复 commit 均早于本次审计基线、工作树 clean——**15 条发现全部标注 `新发现`**，与 WI 1.1–1.5 同族不同模块者在条目复核状态行备注关联。唯一相关历史 bug（2026-08-18-nop-retry-save-attempt-managed-double-save）与本维度发现无涉。

### 逐条裁定表

| 发现ID | 裁定 | 最终严重度 | 现状 | 一句话理由 |
|---|---|---|---|---|
| C7-001 | 保留 | P1 | 新发现 | 三表零删除路径/零 purge 入口逐点复核属实（store 仅 :248/:309 两处 record 删除；attempt/死信零删除；BizModel 纯 CRUD），owner doc 无保留声明 |
| C7-002 | **降级** | P2（条件化） | 新发现 | 默认装配链 30s readTimeout 硬封顶单次 attempt、30s 超时后循环存活；永久停摆需三种非默认配置/行为之一；机制本体核实维持并增强（完整证据链见下节） |
| C7-003 | 保留 | P1 | 新发现 | :290 裸 parseMap 与同步抛 invoker 两入口、tryLock 先置 RETRYING、catch 无状态推进逐步核实，僵尸循环时序每步可信 |
| C7-004 | 保留（证据修正） | P2 | 新发现 | 生产绑定 BindScheduledExecutor 对 Error 有 catch(Throwable)+单条 ERROR 后终止链条——永久停摆成立，「无日志可查」表述修正 |
| C7-005 | 保留 | P1 | 新发现 | 三 API 全仓零生产调用方、tcc orm 150 行 0 索引、:303/:339 耗尽双排除均独立复核属实 |
| C7-006 | 保留 | P2 | 新发现 | owner doc 已声明耗尽排除语义（行为本体非缺陷），本条仅登记耗尽时刻零日志/零终态/零指标的可观测缺口 |
| C7-007 | 保留 | P2 | 新发现 | getCurrentTime=System.currentTimeMillis() 直读核实，恢复风暴/冻结后果链具体，NTP 步进低频故 P2 |
| C7-008 | 保留 | P2 | 新发现 | _callback 逐环追加 + 无环检测/深度计数核实；触发需管理员配置成环故 P2 |
| C7-009 | 保留 | P3 | 新发现 | getMaxRetryTimes 生产只写不读（grep 全仓）、真实上限=record 级 retryTimes+驱动参数——契约漂移属实 |
| C7-010 | 保留 | P2 | 新发现 | 逐条 get(expireGap) 串行 + :320 fetch 无 try/catch 核实；单条超时有测试钉住，聚合退化未声明 |
| C7-011 | 保留 | P2 | 新发现 | :116-119 本地钟 vs :154/:190/:314 DB 钟核实，偏差折算重复窗口链路具体、前提罕见故 P2 |
| C7-012 | 保留（附条件面标注） | P1 | 新发现 | store 单值 namespace 硬过滤 + 提交侧透传（job bridge 无条件携带 schedule.namespaceId）逐步核实；触发=单一配置条件且时序每步可信，维持 P1；default-only 部署不触发 |
| C7-013 | 保留 | P2 | 新发现 | 守卫读陈旧内存实体、REQUIRES_NEW 立即落库、CAS 0 行不门控 thenCompose、分支表无独立扫描入口四处核实；三条件合取降 P2 恰当 |
| C7-014 | 保留 | P2 | 新发现 | 惰性流中断 + 同批 nextTriggerTime 同值 + sid 单调三要素核实（QueryBean :435 false=asc）；确定性饿死成立，依赖 C7-003 入口故 P2 |
| C7-015 | 保留 | P2 | 新发现 | do-while 排水 + join 最慢 attempt + BindScheduledExecutor 完成后才重排（较初审更强证据）核实；默认装配单批 30s 上界成立 |

### C7-002 特别复核：触发条件修订终裁（完整证据链）

**终裁：采纳第 2 轮修订，C7-002 由 P1 降为 P2（条件化）。机制本体维持并增强，停摆面收窄为三种非默认配置/行为；默认装配下的残余退化面由 C7-015 承载。**

**a) retry invoker 调用链与默认 readTimeout（逐环核实）**：
- 引擎注入：`RetryEngineImpl.setRpcServiceInvoker` 为 @Inject 按类型注入（:55-58）；retry 模块 beans（app-engine.beans.xml）仅装配 store/scanner/engine 三 bean，不指定 invoker 实现——由平台默认装配决定。
- 默认绑定：`nop-cluster/nop-rpc-cluster/.../rpc-cluster-defaults.beans.xml` 中 `nopClusterRpcServiceInvoker`（ClusterRpcServiceInvoker）`primary="true"`，并经 alias 绑定 `nopRpcServiceInvoker`（service-mesh off 默认分支）；mesh on 时别名指向 `nopHttpRpcServiceInvoker`。全仓 `implements IRpcServiceInvoker` 仅此两类（main 源码 grep 证实，与第 2 轮 ⑤ 一致）。
- 传输层：两类 invoker 均最终委托 `HttpRpcService`（nop-rpc-http）→ `IHttpClient`；`HttpRpcService.toHttpRequest` 不设置 per-request timeout（文件内 grep "timeout" 零命中），`HttpRequest.timeout` 字段默认 0。
- 默认 raw client：`nop-network/nop-http/nop-http-client-jdk/.../http-client-jdk.beans.xml` 定义 `nopRawHttpClient` `ioc:default="true"` = `JdkHttpClient`；okhttp/apache 模块无 main-source nopRawHttpClient bean（grep 证实）——默认装配即 JDK client。
- 超时默认值：`HttpClientConfig` readTimeout=Duration.of(30s)/writeTimeout=30s/connectTimeout=10s。三分支应用点：JdkHttpClient :218-221（request.timeout==0 → `builder.timeout(readTimeout)`；:166-167 `sendAsync(req, BodyHandlers.ofByteArray())` 使超时覆盖整个响应交换，**慢滴亦被封顶**）；ApacheHttpClientHelper :96-98（defaultRequestConfig `setResponseTimeout(30s)`）+ ApacheHttpClient :191-196（仅 request.timeout>0 时覆盖）；OkHttpClientProvider :116（`builder.readTimeout(config)`）。

**b) 30s 超时后扫描循环行为**：超时表现为 future 异常完成（非同步抛）——`doExecute` whenComplete（:463-488）将 attempt 置 FAILED 落账 → `executeSingleAttempt` whenComplete（:430-446）走 `handleExecutionFailure` 计数+1、写回退避 nextTriggerTime、状态 PENDING → processor whenComplete（:639-643）打 `scanner.execute-failed` ERROR → allOf join 解除 → doScan 排水循环继续下一批。**循环存活，无停摆；默认装配下 C7-002 的「整体永久停摆」不可达。**

**c) 机制本体维持且增强**：生产调度绑定路径为 `RetryScannerImpl.getExecutor()`（:165-167）= `GlobalExecutors.globalTimer().executeOn(GlobalExecutors.globalWorker())` → `BindScheduledExecutor.scheduleWithFixedDelay`（:72-105）在 worker 上 `command.run()` **返回后**才重排下一轮——doScan 阻塞即整链永久停摆且**绝不重入**（较初审「fixedDelay 语义」更强的直接证据）；`stopScanning` 的 `cancel(false)`（:105-111）不中断已阻塞线程；零指标（第 2 轮 ④）核实。

**d) 永久停摆前提（复核最终版）**：(a) 部署注入无内部超时的自定义 `IRpcServiceInvoker`；(b) 显式将传输超时置 0/禁用；(c) 慢滴响应——**一处修正**：第 2 轮将慢滴限定 OkHttp（readTimeout 为读间隔语义），实测 Apache 同样可被慢滴绕过（responseTimeout 只覆盖到响应头 + reactor soTimeout 按读间隔 30s）；但 ok/apache 均非默认装配，默认 JDK client 全交换封顶，不影响判级。

**e) 与 C7-015 关系（供 Phase 6 合并裁定，本复核不合并条目）**：C7-015 承载默认装配下的有界退化面（排水线性放大 ⌈N/100⌉×30s、心跳退化、租约跨窗），其 ⑫/⑨ 与第 2 轮 ⑤ 节互引自洽，作为 C7-002 触发条件修订的载体成立；两文件描述自洽。建议 Phase 6 将 C7-002（条件化停摆面）并入 C7-015 统一处置或以 C7-015 为主条目（C7-002 机制链作为其极端配置证据面）；另与 dispatch-log :56 登记的「C8-001/C7-002/C8-012 停摆三形态合并裁定」交叉项对齐。

### 判级校准说明

- **P1→P2 唯一降级为 C7-002**：依据「默认装配 + 单一常见条件不可达，需非默认配置或退化服务端行为」；机制非误报，第 2 轮修订经独立复现核实后采纳。
- **C7-012 维持 P1 但标注条件面**：触发 = 「非 default namespace 的提交」单一配置条件（多租户为平台常规用法面、job bridge 无条件透传使触发面代码级锚定），时序每步可信；default-only 单租户部署不触发。若 Phase 6 合并裁定采「部署形态收窄」口径，可与 C7-002 同规则再裁——本复核以「单一常见配置条件 + 半个成功 + 零暴露」维持 P1。
- **C7-004 证据修正不改判级**：生产绑定路径下 Error 逃逸有单条 ERROR 日志（BindScheduledExecutor catch(Throwable) → LOG.error("nop.err.thread-pool.execute-fail") → completeExceptionally 终止链条，不重排），「无日志可查」表述不成立；「周期任务永久停摆、running 语义背离、需重启」成立，P2 维持。该平台站点为生产扫描链路的实际 catch 点，补录进 F3 站点清单（第 2 轮 ① 未列）。
- 驳回为零：未发现引用不存在的代码；15 条发现证据全部在 live code 落点核实（个别行号微漂已按实际修正，如 DefaultScheduledExecutor scheduleWithFixedDelay 实为 :130-134）。

### F3 失效面完备性核查（复核补全）

全量 grep 两模块 main 源码 `LOG.error|LOG.warn` 实测 18 个物理站点（RetryEngineImpl ×7、RetryScannerImpl ×2、TccEngine ×2、TccTransaction ×2、TccRecordStore ×2、TccRunner ×3），与第 2 轮 ① 的 19 站点枚举一致（R4 三站点并作一行的计数口径差异）；**补录 1 个平台侧站点**：`BindScheduledExecutor` 的 `nop.err.thread-pool.execute-fail` ERROR——它是生产扫描链路（BindScheduledExecutor 包装）对 command 异常（含 Error）的实际 catch 点，归入 C7-004 证据面。除 C7-003 族外确认无新的无界放大站点，第 2 轮 ① 结论维持。

### 维度集外线索处置（CL-002/003/005）

| 线索 | 裁定 | 归属维度 | 建议严重度 | 复核依据 |
|---|---|---|---|---|
| CL-002 | **确认** | C4（线程/池形态） | P2（C7-002 条件下 P1） | 形态核实：doScan 体（含 :648 join 与整轮排水）经 BindScheduledExecutor 绑定在 globalWorker 线程执行（RetryScannerImpl :165-167 → BindScheduledExecutor :66-105）；globalWorker 默认 maxPoolSize=30（GlobalExecutors :85-96、CommonConfigs :26-27）。与 C7-002/015 不重复（彼为停摆/节律退化面，此为共享池占用面），有独立行动意义（scanner 专用执行器/脱离共享池）；同族先例 WI 1.2 MA-06、WI 1.5 MA-04（不同模块） |
| CL-003 | **确认（带描述修正）** | C2（上下文残留） | P2（初审 P1 候选降级） | 形态真实：清理挂 whenComplete（TccRpcServiceInterceptor :50-51、TccGatewayInterceptor :102-111 三分支同构），future 永不完成则清理不执行。两点修正：① 载体非裸 ThreadLocal——TccContext 存于 IContext 属性（ContextProvider → BaseContextProvider.t_context，与 plan 2466 R1 预注 m-B 一致）；② 跨请求毒化面窄于初审描述——主 HTTP 入口 ContextHttpServerFilter 每请求 `newContext()` 替换 ThreadLocal + close 自愈（BaseContext :315-325），毒化仅在「复用环境上下文」入口可达（ApiContextInterceptor/ContextBinder 复用 currentContext() 且不滤 closed：同一池线程 + 陈旧 IContext 未关闭 + 同 txnGroup），且「永不完成」前提默认装配受 30s 封顶（依赖 C8-012 族形态）。触发条件收窄故降 P2 |
| CL-005 | **确认** | C5（批量无界物化）/C9 | P2 | 形态核实：TccRecordStore :344 findAllByQuery 无 setLimit 全量物化 + :346-351 txnIds 无界列表 + :352/:354 deleteByQuery。与 C7-005 不重复（彼=清理零驱动+零索引增长面，此=清理 API 自身物化面），独立行动意义（接线清理前必须分批）；同族先例 WI 1.1 C5-010（不同模块） |

### 复核统计

- 15 条发现：**保留原判 14**（P1×4 / P2×9 / P3×1，含 C7-004 证据修正不改判级）、**降级 1**（C7-002 P1→P2 条件化）、**驳回 0**。
- **最终分布：P1×4（C7-001/003/005/012）、P2×10（C7-002/004/006/007/008/010/011/013/014/015）、P3×1（C7-009）。**
- CL 线索：**确认 3**（CL-002→C4/P2、CL-003→C2/P2、CL-005→C5/C9/P2），驳回 0。
- 现状标注：15 条全部 `新发现`（对照基线见节首）；同族关联备注见各条复核状态行。
- 终止信号：复核未产生新发现、未发现引用不存在的代码、无降级证据反转；无第 3 轮补审事由（C7-013 参与方幂等契约面维持第 2 轮边界判定；C7-002 自定义 invoker 生产装配清单维持部署面边界，超出本仓库可证范围）。

---

## 最终保留项

| # | 发现ID | 标题 | 最终严重度 | 现状 |
|---|---|---|---|---|
| 1 | nop-retry-nop-tcc-C7-001 | retry 三张账本表零保留机制，成功即沉淀永久行 | P1 | 新发现 |
| 2 | nop-retry-nop-tcc-C7-003 | 解析/同步异常路径产生永不终止的僵尸记录 + 每租约周期全栈 ERROR 放大 | P1 | 新发现 |
| 3 | nop-retry-nop-tcc-C7-005 | tcc 账本清理 API 零生产调用方 + 耗尽行双重永久排除 + 全表零索引 | P1 | 新发现 |
| 4 | nop-retry-nop-tcc-C7-012 | 扫描器只服务单一 namespace：非 default 记录永久 PENDING、零日志零接管 | P1（条件面：非 default namespace 使用；default-only 部署不触发） | 新发现 |
| 5 | nop-retry-nop-tcc-C7-002 | 扫描器单次 RPC 挂死即整体永久停摆，且无任何健康可见性（条件化） | P2（条件化：自定义无超时 invoker/显式禁超时/非默认 client 慢滴；默认装配 JDK client 30s 全交换封顶） | 新发现 |
| 6 | nop-retry-nop-tcc-C7-004 | Error 逃逸使周期扫描任务终止（证据修正：生产绑定下有单条 ERROR 后静默停摆） | P2 | 新发现 |
| 7 | nop-retry-nop-tcc-C7-006 | 补偿耗尽的 tcc 事务静默退出扫描窗（已声明边界下的监控盲区面） | P2 | 新发现 |
| 8 | nop-retry-nop-tcc-C7-007 | tcc 期限/租约全链路原始墙钟，NTP 步进触发恢复风暴/冻结 | P2 | 新发现 |
| 9 | nop-retry-nop-tcc-C7-008 | 回调策略成环无护栏：副作用与死信链式自增殖 | P2 | 新发现 |
| 10 | nop-retry-nop-tcc-C7-010 | tcc 过期扫描整轮耗时随挂死补偿数线性放大（100×expireGap 上限） | P2 | 新发现 |
| 11 | nop-retry-nop-tcc-C7-011 | retry 首提租约混用本地墙钟与 DB 估计时钟 | P2 | 新发现 |
| 12 | nop-retry-nop-tcc-C7-013 | tcc 分支创建守卫读陈旧内存态 + CAS 不门控补偿 RPC：终态父事务下孤儿分支 | P2 | 新发现 |
| 13 | nop-retry-nop-tcc-C7-014 | scanner 批内单点同步异常中断整批流式执行：排序靠后记录确定性饿死 | P2 | 新发现 |
| 14 | nop-retry-nop-tcc-C7-015 | retry 扫描整轮排水式串行执行：心跳无上限退化（C7-002 触发条件修订载体） | P2 | 新发现 |
| 15 | nop-retry-nop-tcc-C7-009 | 分支级 maxRetryTimes/nextRetryTime 死字段，上限契约漂移 | P3 | 新发现 |

### 维度集外线索（确认）

| 线索ID | 形态 | 归属维度 | 建议严重度 | 一句话 |
|---|---|---|---|---|
| nop-retry-nop-tcc-CL-002 | C4 | C4 | P2（C7-002 条件下 P1） | retry 扫描排水轮以阻塞 join 占用共享 globalWorker 池线程（默认 30），C7-002 条件下永久占用；建议 scanner 专用执行器 |
| nop-retry-nop-tcc-CL-003 | C2 | C2 | P2 | TccContext 清理挂 whenComplete，永不完成 future 时残留于 IContext；载体为 IContext 属性而非裸 ThreadLocal，跨请求毒化仅在复用环境上下文的入口路径可达（主 HTTP 入口每请求替换自愈） |
| nop-retry-nop-tcc-CL-005 | C5/C9 | C5/C9 | P2 | removeCompletedRecords 无 limit 全量物化已完结事务 + txnIds 无界列表；接线清理前必须分批 |
