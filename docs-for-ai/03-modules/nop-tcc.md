# nop-tcc — TCC 分布式事务

## 功能概览

TCC（Try-Confirm-Cancel）分布式事务协调器。

- 分支事务管理
- 事务组
- 超时控制
- 错误追踪

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopTccRecord | `nop_tcc_record` | TCC 事务记录 |
| NopTccBranchRecord | `nop_tcc_branch_record` | 分支事务记录 |

## 关键字段

**NopTccRecord**:
- `txnId`：事务 ID
- `txnGroup`：事务组
- `txnName`：事务名称
- `status`：状态
- `expireTime`：过期时间

**NopTccBranchRecord**:
- `branchId`：分支 ID
- `txnId`：所属事务 ID
- `branchName`：分支名称
- `status`：状态

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-tcc-core` | TCC 核心引擎 |
| `nop-tcc-dao` | ORM 实体与 DAO |
| `nop-tcc-service` | 业务逻辑 |
| `nop-tcc-web` | Web 层与 AMIS 页面 |
| `nop-tcc-integration` | 集成适配 |

## 执行表访问权限（P1-3.8-003，plan 2386）

`NopTccRecord` / `NopTccBranchRecord` 的 xbiz 覆写 `save/update/delete/saveAll/deleteAll` mutations，声明 `<auth roles="admin"/>`——表内 `serviceName`/`requestPayload`/`confirmMethod` 等字段即执行凭据，不向普通用户开放写入口。该声明为 opt-in enforcement（`enable-action-auth=false` 默认部署行为不变，见 `service-layer.md`）。TccEngine 等引擎内部对执行表走 dao 直写，不受 biz 层 auth 约束。

Retry 侧（`NopRetryRecord`/`NopRetryPolicy`/`NopRetryAttempt`/`NopRetryDeadLetter`）同批采用相同收敛，见 `nop-retry` 模块。

## 崩溃恢复语义（P1-3.8-004，plan 2388）

超时扫描恢复（`checkExpiredTransactions` → `endAsync(true,null,null)`）对补偿中间态**对称续跑**：

- `CONFIRMING`/`CONFIRM_FAILED` → 续跑 confirm（confirm 开始后不允许回滚）；
- `CANCELLING`/`CANCEL_FAILED` → 续跑 cancel（cancel 开始后不允许 confirm；存活调用方与恢复路径同路由）。

修复前 CANCELLING 在「分支无回滚证据」组合下无转移出口（被 doConfirmAsync 守卫静默 no-op），每轮被扫描器重拾、分支补偿计数耗尽 `nop.tcc.default-max-retry-times`（默认 100）后被扫描窗口永久排除、参与方预留资源悬挂。恢复续跑打 `nop.tcc.resume-cancel` 日志（可观测点）；恢复入口固定 `timeout=true`，分支终态为 `TIMEOUT_SUCCESS`，全局经聚合折叠为 `CANCEL_SUCCESS`。

## 多跳 TCC 上下文传播（P1-3.8-005，plan 2388）

TCC 链路的多跳传播机制分三层：

- **出站**：`TccRunner.runBranchTryAsync` 将 `txnId`/`txnGroup`/`branchId`/`branchNo` 写入 RPC 请求头（`ApiHeaders.setTxn*`）。
- **入站（框架级）**：`TccRpcServiceInterceptor` 对服务端（inbound）调用在 thread-local 无 `TccContext` 时，从请求头 `TccContext.buildFromRequest` 桥接建立上下文（setCurrent 无条件——请求头描述入站事务，txnGroup 匹配检查只决定本方法是否 join；thread-local 已有上下文时不覆盖，**thread-local 赢**优先级，与网关拦截器的「请求头赢」相反），调用完成时恢复。参与方（非 initiator）不 startBranch——branch 由调用方记录。
- **网关层**：`TccGatewayInterceptor` 拦截网关入站请求（请求头赢）。

`TccContextInvoker`（nop-graphql-core，`IAsyncFunctionInvoker` 装饰器）为可选装配点，非框架默认机制——多跳桥接由 `TccRpcServiceInterceptor` 承载。

## 并发与账本卫生契约（plan 2515 R2-12）

- **CAS 冲突传播（裁定①③）**：全局/分支状态 CAS 失败（0 行或异常）一律**异常完成**（错误码 `nop.err.tcc.status-cas-conflict`，0 行保留 WARN 留痕后转异常，废除「WARN 后正常返回」的静默吞形态）。异常经 `thenCompose` 链结构性短路——doCancelAsync/doConfirmAsync 的分支驱动与 runBranchConfirmAsync/runBranchCancelAsync 的补偿 RPC 在 CAS 失败时不再照发，双驱动对撞败方零 RPC；聚合挂点补 err==null 显式门控：驱动链（含首跳 CAS）异常完成时不折叠终态，全局保持 CANCELLING/CONFIRMING 交恢复扫描续跑——**分支零驱动不可直写全局终态**，异常向调用方传播（调用方可感知）。
- **聚合读 DB 权威（裁定②）**：分支状态聚合的输入为聚合前经 store 按 txnId **重载的分支行权威状态**（内存副本仅作调度判定）；全局终态 CAS 前按分支 DB 状态终裁，「全局误报终态 × DB 分支失败」不可达。store CAS 的调用方可见 future 在**事务 afterCommit 完成**（rollback/commit-fail 分支以异常完成配对）——聚合重载必见本链 CAS 后状态，无提交可见性竞态。
- **幽灵双驱动封堵**：`checkExpiredTransactions` 超时分支对在飞补偿链 `future.cancel(true)` + ERROR 留痕后继续下一条，超时不再产生与调用方链并行的第二条驱动。
- **清理接线与保留策略（裁定⑧）**：TccEngine 经 `LifeCycleSupport` 生命周期链周期清理（doStart 启动/doStop 取消，`tcc-core-defaults.beans.xml` 装配触发；绝缘载体 + 失败可见，错配置启动期 fail-fast）。每轮先**耗尽行终态化**（`retryTimes ≥ nop.tcc.default-max-retry-times` 的未完结行置 `status=KILLED` 终态），再入既有 finished 清理谓词按锚列 `beginTime` 有界批删。参数语义 SSOT = 仓库 ai-dev/design/concurrency/ledger-retention-policy.md（R01 族级保留策略，按 docs-for-ai 边界规则不直接链接），模块配置键：

| 配置键 | 缺省 | 语义 |
|--------|------|------|
| `nop.tcc.ledger.retention-days` | 30 | 已完结（含 KILLED 终态化）记录保留期（天）；`0`=显式禁用周期清理 |
| `nop.tcc.ledger.cleanup-interval` | 1h | 清理触发周期；下限 60s，低于下限启动期 fail-fast |
| `nop.tcc.ledger.delete-batch-size` | 1000 | 单轮单表删除上限（锚列拾取+IN 批删）；非正数启动期 fail-fast |

- **恢复扫描/清理索引（裁定⑧）**：`nop_tcc_record` 两索引——`(expire_time, status, retry_times)` 复合索引（恢复扫描谓词）与 `begin_time` 索引（清理排序）。新装环境由 orm 源模型 `<indexes>` 段经生成链建索引；存量库升级执行迁移脚本 `nop-tcc/deploy/sql/postgresql/2026-10-add-tcc-record-indexes.sql`（PostgreSQL 幂等方言 `CREATE INDEX IF NOT EXISTS`，可重复执行；其他方言按语义等价转写）。
- **崩溃恢复与耗尽排除边界**：超时扫描对称续跑语义（上节）零回退；分支补偿计数耗尽（默认 100）的记录此前被扫描窗永久排除——现经每轮清理前置终态化为 KILLED 后进入保留期清理（保留审计窗内可观测；耗尽行的观测/干预工具本体归后续批次）。

## 补偿链韧性与收尾方向（plan 2543 R4-10）

- **超时恢复默认回滚（W-07）**：`endAsync(true, null, null)` 在记录处于**未定向态**（`TRYING`/`TRY_SUCCESS`/`CREATED`/`null`）时一律走 cancel 方向。原缺省是「既无回滚证据也无失败响应 → confirm」，会把一条没跑完的业务链以**确认方向**释放参与方预留资源——这是最严重的错误方向。进入 confirm/cancel 半区后仍走上节的对称续跑路由（`CONFIRMING`/`CONFIRM_FAILED` → confirm，`CANCELLING`/`CANCEL_FAILED` → cancel，零改动）。记录仍为未定向态时不存在「confirm 已经开始」的证据，故不违反 TCC「confirm 开始后不可回滚」。
- **首跳标记落库有限重试（W-07）**：`CANCELLING`/`CONFIRMING` 标记写是「业务失败/补偿方向」的唯一持久化证据。原实现一次失败即短路 → 记录滞留 TRYING 且无方向证据 → 后续恢复按缺省方向收敛。现对**非 CAS 冲突**错误最多重试 3 次（CAS 冲突是语义冲突，重试必然再失败），耗尽后 ERROR（`nop.tcc.status-write-retry-exhausted`）并显式失败。
- **证据保留（W-07）**：`runTaskWithNewTxnAsync` 的同步抛出补偿路径上，业务异常**优先**传播、补偿链异常以 `addSuppressed` 保留（原 `thenCompose(v2 -> failedFuture(e))` 在补偿链失败时整段跳过，调用方只看到补偿异常而业务异常丢失）。
- **分支创建守卫改 DB 复核（W-03）**：`runBranchTransactionAsync` 的内存守卫（`txn.getTccStatus() != TRYING`）保留为快路径，其后以 `getTccRecordAsync` 重载父记录复核。复核读失败按 **fail-close** 拒绝（`ERR_TCC_BRANCH_GUARD_CHECK_FAILED`）——放行可能向已回滚事务写入预留资源（不可逆），拒绝只让调用方在父事务不可用的明确信号下失败；父记录不存在 → `ERR_TCC_MISSING_TRANSACTION_RECORD`。
- **补偿期孤儿分支收敛（W-03）**：`endAsync` 末段（聚合前）按 branchId 求「DB 重载行 − 入口快照」差集，非空则以既有的 `cancelAllAsync`/`confirmAllAsync`（自带方向筛 + per-branch 隔离）驱动一轮再重读，最多 3 轮；仍未排空则不折叠终态（`ERR_TCC_BRANCH_CONVERGENCE_EXHAUSTED`），全局保持中间态交恢复扫描。**残余窗（诚实声明）**：腿 1 的 DB 复核与分支插入之间仍有 TOCTOU——分支在终态 CAS 之后才提交的场景需 DB 级约束才能彻底闭合（ORM 扩面，本批不做），登记为 watch-only。
- **弃链 driving-epoch（W-04）**：恢复扫描超时 `future.cancel(true)` 后推进该 txnId 的进程内驱动纪元；旧链苏醒到终态写点校验令牌，失配即 `ERR_TCC_DRIVING_ABANDONED` 拒绝折叠（传输层无法真正中止在途 RPC，故闸设在写点）。条目缺失按 inactive 判定（不变式：条目只在终态写成功后移除，而终态父事务不再被恢复扫描拾取、txnId 一次性）。**残余**：闸不阻止弃链已发起的分支补偿 RPC，由 CAS 短路（裁定①）+ 参与方幂等性承接。
- **逐分支隔离与聚合结算表（W-06）**：`confirmAllAsync`/`cancelAllAsync` 循环体内单分支 begin/finish 同步抛不再中断整批（失败分支进 `waitAll`，后续分支照常驱动）。聚合改为**逐状态结算表**——「成功」当且仅当每个分支在本方向已结算（已到本方向终态，或本方向本就不驱动它：`isBranchCancellable` 为假）；`KILLED` 与 `CONFIRM_SUCCESS` **计为已结算**（否则含这两类分支的父事务永不进 `getFinishedStatus()`，反向摧毁清理链）；未驱动的 `TRY_SUCCESS`/`TRYING`/`TRY_UNKNOWN`/`CONFIRMING` 计为未结算 → 折叠为本方向 FAILED 交恢复重试。
- **注册表作用域（W-02）**：`TccTransactionRegistry.instance()` 的惰性创建以**属性 Map 本体**为锁（`context.getAttrs()`；租户/委托包装下两线程可持不同 wrapper 而共享同一 Map，锁 wrapper 不互斥）做双重检查；档位恢复改为**条件恢复**（仅当我方事务仍是当前值时才放回旧值），消除并发交错下清掉对端注册。注册表作用域 = `IContext`；同上下文并发进入被防御性支持，但非推荐调用形态。
- **恢复轮双上界（W-10）**：`checkExpiredTransactions` 加**单轮总时长预算**（`nop.tcc.recovery.max-round-duration-ms`，缺省 60s；每页开始前检查）+ **连续超时熔断**（`nop.tcc.recovery.max-consecutive-timeouts`，缺省 3；一条成功即清零）。原形态单轮耗时 = 页数 × 页内条数 × `expireGap`（慢成功路径同样无界）；`fetchExpiredRecords` 亦补 try/catch，一次 DB 异常不再静默终止整轮。观测：`getRecoveryTimeoutCount()` / `getRecoveryCircuitBreakCount()`。**注**：`checkExpiredTransactions` 现零生产调用方（C6-005 dormant，归后续批次），故本项交付的是「保护力就位」，不是端到端恢复已接线。
- **账本清理分页（W-13）**：两参 `removeCompletedRecords(retention, onlyCompleted)` 保留公共签名，实现改为**内部按批分页**（批大小 = `nop.tcc.ledger.delete-batch-size`），消除「无 limit 的 `findAllByQuery` 全量物化 + 等长 txnIds + 巨型 IN」。事务形态显式裁定：整个调用仍是**单一事务**（公共契约的原子性不变；循环体是私有非注解助手，不触发自调用绕过事务拦截器）。
- **时钟源（W-08）**：tcc 期限/租约改走 DB 估算时钟的两个 seam——**读侧**（过期判定/清理窗口）用 `getMinCurrentTimeMillis()`（只有确定已过期才触发恢复，与 `expireTime < minTime` 谓词同向），**写侧**（所有落库时间戳，含 `fetchExpiredRecords` 推进的 `nextCheckTime`）用 `getMaxCurrentTimeMillis()`（保守偏后，绝不提前恢复）。任意两库/本地时钟偏差都不再造成「写入早→立即过期」或「写入晚→永不判过期」的夹逼错误。跨单元 `IClock` 单调时钟注入仍归后续批次。



## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-tcc/model/nop-tcc.orm.xml` |
| 并发契约（plan 2515 R2-12） | `nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java`（全局/分支 CAS 异常完成、afterCommit 配对、耗尽终态化 `killExhaustedRecords` :422、有界批删三参 `removeCompletedRecords(long,boolean,int)` :463、**时钟双 seam** `nowForRead`/`nowForWrite` :386-405、**两参清理内部分页** :392-431）；`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java`（生命周期链 `doStart`/`startLedgerCleanup` :136/:158、**守卫 DB 复核** `runBranchTransactionAsync` :443、**恢复轮预算/熔断/纪元** `checkExpiredTransactions` :466 / `checkExpiredAsync` :500 / `beginDriving`/`isActiveDriving`/`endDriving`）；`TccTransaction`（impl/）（挂点 err==null 门控 `doCancelAsync`/`doConfirmAsync` :100/:122、**孤儿收敛** `convergeAndAggregate` :149+、**探测器写点闸** + `retryStatusWrite`）；`TccRunner`（impl/）（DB 权威聚合 :178/:198、**逐分支隔离** :133/:164、**三通道 future 兜底** :40/:72/:98）；`TccTransactionRegistry`（impl/）（**原子身份 + 条件恢复** :18-60）；错误码 `nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/TccCoreErrors.java`（`ERR_TCC_BRANCH_GUARD_CHECK_FAILED`/`ERR_TCC_BRANCH_CONVERGENCE_EXHAUSTED`/`ERR_TCC_DRIVING_ABANDONED`）+ `nop-tcc-api` 的 `TccErrors`（`ERR_TCC_STATUS_CAS_CONFLICT`）；清理参数 `TccEngineConfigs`（`nop.tcc.ledger.*` + `nop.tcc.recovery.*`）；索引迁移脚本 `nop-tcc/deploy/sql/postgresql/2026-10-add-tcc-record-indexes.sql` |

## 相关文档

- `../reusable-modules-overview.md`
