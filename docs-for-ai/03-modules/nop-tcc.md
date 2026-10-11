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

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-tcc/model/nop-tcc.orm.xml` |

## 相关文档

- `../reusable-modules-overview.md`
