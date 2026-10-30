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



## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-tcc/model/nop-tcc.orm.xml` |
| 并发契约（plan 2515 R2-12） | `nop-tcc/nop-tcc-dao/src/main/java/io/nop/tcc/dao/store/TccRecordStore.java`（全局/分支 CAS 异常完成 :238/:333、afterCommit 配对 :184、耗尽终态化 `killExhaustedRecords` :423、有界批删 `removeCompletedRecords(long,boolean,int)` :464）；`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java`（生命周期链 `doStart`/`startLedgerCleanup` :136-212、超时 `cancel(true)` :487）；`TccTransaction`（impl/）（挂点 err==null 门控 doCancelAsync/doConfirmAsync :100/:122）；`TccRunner`（impl/）（DB 权威聚合 :178/:198）；错误码 `nop-tcc/nop-tcc-api/src/main/java/io/nop/tcc/api/TccErrors.java`（`ERR_TCC_STATUS_CAS_CONFLICT` :27）；清理参数 `TccEngineConfigs`（`nop.tcc.ledger.*`）；索引迁移脚本 `nop-tcc/deploy/sql/postgresql/2026-10-add-tcc-record-indexes.sql` |

## 相关文档

- `../reusable-modules-overview.md`
