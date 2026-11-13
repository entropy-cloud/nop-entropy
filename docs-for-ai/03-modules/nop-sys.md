# nop-sys — 系统管理模块

## 功能概览

系统级基础设施服务，提供多个通用能力。

- **序列号生成**：可配置步长、缓存大小、snowflake/uuid 兜底；DB 行锁保证无重号
- **业务编码规则**：模式化编号生成（如 `ORD-{@year}{@month}-{@seq:5}`），与 ORM `tagSet="code"` + xmeta autoExpr 自动集成
- **数据字典**：字典 + 选项键值对
- **国际化(i18n)**：按 locale 查询翻译
- **Maker-Checker 审批**：制单人→审核人审批流
- **通知模板**：消息通知模板管理
- **用户/系统变量**：作用域变量存储
- **扩展字段(EAV)**：实体-属性-值的动态扩展字段
- **分布式锁**：数据库锁，带过期时间
- **集群选举**：Leader 选举
- **事件队列**：数据库 outbox 事件服务，`nop-sys-dao` 提供事件存储与触发原语；普通事件可由 `nop-batch-sys` 以 batch trigger 方式扫描执行，广播事件通过 `findNext` keyset pagination + 内存游标 + 时间窗口消费
- **字段级变更日志**：`NopSysChangeLog` + `OrmEntityChangeLogInterceptor`，实体加 `tagSet="audit"` 自动记录字段级 old→new，默认启用
- **通用 ORM 拦截器**：`IOrmInterceptor` + `orm-interceptor.xml` 配置式回调，提供应用层 trigger 机制

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopSysSequence | `nop_sys_sequence` | 序列号生成器配置（编号真正递增的来源） |
| NopSysCodeRule | `nop_sys_code_rule` | 业务编码规则（模式 + 引用一个 Sequence） |
| NopSysDict | `nop_sys_dict` | 数据字典 |
| NopSysDictOption | `nop_sys_dict_option` | 字典选项 |
| NopSysI18n | `nop_sys_i18n` | 国际化消息 |
| NopSysCheckerRecord | `nop_sys_checker_record` | Maker-Checker 审批记录 |
| NopSysNoticeTemplate | `nop_sys_notice_template` | 通知模板 |
| NopSysUserVariable | `nop_sys_user_variable` | 用户变量 |
| NopSysVariable | `nop_sys_variable` | 系统变量 |
| NopSysExtField | `nop_sys_ext_field` | 扩展字段(EAV) |
| NopSysLock | `nop_sys_lock` | 分布式锁 |
| NopSysClusterLeader | `nop_sys_cluster_leader` | 集群 Leader |
| NopSysEvent | `nop_sys_event` | 普通事件队列（shared queue + 分区串行 + lease） |
| NopSysBroadcastEvent | `nop_sys_broadcast_event` | 广播事件流（append-only，`findNext` + 时间窗口消费） |
| NopSysChangeLog | `nop_sys_change_log` | 字段级变更日志（每 dirty 字段一行：propName/oldValue/newValue/operatorId 等） |

## 主要能力入口

| 能力 | 怎么做 | 入口 |
|------|--------|------|
| **业务编码 / 单据编号** | ORM 列加 `tagSet="code"` → autoExpr 自动取号；或 BizModel 注入 `ICodeRuleGenerator` | **`../03-runbooks/generate-business-code.md`**（模式语法 / Sequence 并发模型 / 三种调用方式 / 反模式） |
| **序列号 / 自增主键** | 注入 `ISequenceGenerator.generateLong(seqName, useDefault)`；主键列用 `tagSet="seq"` / `seq-default` | `../03-runbooks/generate-business-code.md`（Sequence 并发模型节）、`../02-core-guides/orm-model-design.md`（主键策略） |
| **数据字典** | ORM `<dict>` 标签 + `NopSysDict`/`NopSysDictOption`；前端 select 控件自动加载 | `../03-runbooks/add-dict-and-constants.md` |
| **分布式锁** | 注入 `IResourceLockManager.getLock/callWithLock`（实现 `SysDaoResourceLockManager`，bean `nopSysDaoResourceLockManager`，`ioc:default=true`） | 见下方"分布式锁" |
| **扩展字段(EAV)** | 实体 `tagSet="use-ext-field"` + `NopSysExtField` | `../02-core-guides/orm-model-design.md` |
| **国际化(i18n)** | ORM 列 `i18n-en:displayName` + `NopSysI18n` | `../02-core-guides/orm-model-design.md` |
| **事件队列** | `SysDaoMessageService`（事件存储 + 广播消费 + 普通事件触发原语） / `nop-batch-sys`（普通事件 batch trigger） | 见下方"事件队列" |
| **字段级变更日志（ChangeLog）** | 实体加 `tagSet="audit"` → 自动记字段级 old→new 到 `NopSysChangeLog`；列加 `tagSet="no-audit"` 排除 | **`../03-runbooks/audit-field-changes.md`**（声明式开关 / 查询历史 / TTL / 反模式） |
| **通用 ORM 拦截器（应用层 trigger）** | 注册 `IOrmInterceptor` 或写 `orm-interceptor.xml` 配置式回调（per-entity + 8 hook + XPL） | **`../03-runbooks/orm-interceptor-trigger.md`**（trigger 概念 / 两种实现 / 与 BizModel 钩子选型） |

## DB 行协调原语并发契约（MA-01，plan 2525）

nop-sys-dao 三类 DB 行协调原语（分布式锁 / 集群选主 / 事件队列租约）统一遵循**条件写/fencing 纪律**：

- **乐观锁 CAS 惯用法**：实体 UPDATE 经 ORM 乐观锁版本条件（生成 SQL 恒带 `AND version=?`）；`orm_disableVersionCheckError(true)` 只把 0 行结果降级为 `orm_readonly` 标记（不关闭版本条件）——写点按 readonly 判定 CAS 胜负。`SysDaoLeaderElector.changeLeader/tryRenewLease` 即此形态（双 follower 同周期抢占至多一个当选）。
- **手工条件写**：`SysDaoResourceLockManager` 的删除/续约谓词恒带 `holderId` 条件——重建锁行 version 恒 0，仅 version 条件无法区分新旧持有者；`tryResetLease` DB 成功后必须回写内存实体 version/expireAt（否则二次续约起全部失效）。
- **事件租约**：`NonBroadcastEventProcessor` 认领 CAS 后，消费等待期间按 `leaseTimeout/3` 切片续租（`renewLease`，乐观锁承载，被接管即 0 行 false）；完成写（含错误路径）走 `fencedComplete` 条件写放弃语义——0 行显式 WARN（`nop.sys.event.complete-abandon-lease-taken`）终态归接管方，禁静默吞。
- **消费等待有界**：`invokeConsumer` 等待循环 = `FutureHelper.waitResult` 切片 × 续租回调 × 总预算 `nop.sys.event.consume-wait-timeout`（默认=leaseTimeout 10000ms）——预算耗尽返回 `ConsumeLater(0)` 回 WAITING 重排（at-least-once），挂死消费者不再永久停摆轮询线程。注意与异常路径 `retryDelayProvider`（可判 FAILED）语义不同，禁混同。

## 序列号生成（Sequence）

`ISequenceGenerator`（`io.nop.dao.seq.ISequenceGenerator`）是序列号统一入口，按 `seqName` 取号：

```java
@Inject
ISequenceGenerator sequenceGenerator;

long nextVal = sequenceGenerator.generateLong("order_seq", false);
```

实现 `SysSequenceGenerator` 的并发与性能模型（cacheSize 批量取号 / DB 行锁 / snowflake/uuid 兜底 / `REQUIRES_NEW` 独立事务不被外层回滚），以及业务编码规则的完整用法，见 **`../03-runbooks/generate-business-code.md`**。

**默认回退路径语义（MA-05，plan 2525）**：未知序列名回退 `SEQ_DEFAULT` 时按 seqName 各建独立 item（配置快照同源、取值流仍来自 SEQ_DEFAULT 行、锁对象独立）——一个冷门序列的 DB 慢不再拖住全部默认序列取号。DB 环节有独立超时上限 `nop.sys.seq.sync-db-timeout`（默认 10000ms，经代理上下文 callExpireTime 传导到语句超时，取号失败显式抛出）；`clearCache`/`removeCache` 同步失效回退项缓存（管理端回拨 nextValue 即时生效，无 TTL 兜底窗）。

## 分布式锁

分布式锁统一入口为 `IResourceLockManager`（`io.nop.commons.concurrent.lock.IResourceLockManager`），nop-sys 提供基于 DB 的实现 `SysDaoResourceLockManager`（bean `nopResourceLockManager`，经 `nop_sys_resource_lock` / `nop_sys_resource_lock_state` 表持久化锁与租约状态）。

```java
@Inject
IResourceLockManager lockManager;

// 推荐：callWithLock 封装获取-执行-释放（默认 waitTime/leaseTime 见 SysDaoResourceLockManager 配置）
String result = lockManager.callWithLock("order_lock", holderId, "reason",
        lock -> doInLock());

// 或手动 getLock：IResourceLock extends java.util.concurrent.locks.Lock
// （lock()/tryLock()/unlock()），另有带租约的 tryLockWithLease(waitTime, leaseTime)
IResourceLock lock = lockManager.getLock("order_lock", holderId);
if (lock.tryLockWithLease(0, expireTime)) {
    try {
        // ...
    } finally {
        lock.unlock();
    }
}
```

> **勘误记录（P1-3.5-001，plan 2383）**：本文档历史版本记载的唯一入口 `ILockService` 全仓不存在（文档→API→装配三层断裂）；真实入口即上述 `IResourceLockManager`。相关死装配族见审计 P2-3.5-008/009（台账行）。

## 事件队列

- 同一数据库事务内发布的本地 outbox 能力，不依赖外部 MQ
- 普通事件：`nop_sys_event`，按 `partitionIndex` 稳定路由，至少一次；`nop-sys-dao` 负责 shared queue + lease + 重排队语义，`nop-batch-sys` 通过 batch DSL（`.batch.xml`）+ `IBatchTaskManager` + `findNext` keyset pagination 周期扫描并触发执行
- 普通事件支持两种触发模式：simple mode（`SysDaoMessageService` 内建定时器）和 batch mode（`nop-job` 调度 `SysEventBatchTrigger` 执行 batch DSL）；通过 `nonBroadcastAutoScanEnabled` 开关切换
- 普通事件扫描禁止使用 OFFSET 分页；simple mode 和 batch mode 都基于 `findNext` keyset pagination
- 广播事件：`nop_sys_broadcast_event`，通过 `findNext` keyset pagination + 内存游标 + 时间窗口消费，不持久化消费进度，重启后从时间窗口起点重新消费（at-least-once），消费失败不阻塞后续事件
- 普通事件与广播事件都只保证 at-least-once；listener 必须自行幂等
- `partitionIndex` 默认来自 `bizObjName + '|' + bizKey` 的 stable short hash；没有顺序键时退化为 topic hash，不保证同键顺序
- batch mode 的分区过滤由 `nopBatchTaskRunner` 注入的 `PartitionResolver`（job 侧 resolve）驱动：`BatchTaskRunner.executeAsync` 调 `resolvePartitions()` 写入 `IBatchTaskContext.partitionRange`，`non-broadcast-consumer.batch.xml` 的 `<orm-reader partitionIndexField="partitionIndex">` 据此通过 `QueryBean.addPartitionFilter` 自动追加 `partition_index` 过滤（单区间 between / 多区间 OR）。集群未启用或未配置时不过滤（扫全表）。程序式 simple mode 仍由 `SysDaoMessageService.assignedPartitions` 驱动，过滤构建复用 `FilterBeans.inRanges`
- batch trigger 只是普通事件的一种触发执行机制，不改变 event row 状态机；成功/失败仍回写到 `nop_sys_event` 行本身（如 `PROCESSED`、`WAITING + scheduleTime + retryTimes`）
- batch DSL 是 `nop-batch-sys` 的支持基线，不再使用编程式 `BatchTaskBuilder`；`SysEventBatchTrigger` 通过 `IBatchTaskManager` 加载 `.batch.xml`，DSL 中的 loader/processor/consumer 通过 `<source>` XPL 委托 `SysDaoMessageService` 方法

## 字段级变更日志与 ORM 拦截器

nop-sys 内置字段级变更日志（`NopSysChangeLog` + `OrmEntityChangeLogInterceptor`），并基于通用 `IOrmInterceptor` / `orm-interceptor.xml` 提供应用层 trigger 机制。两者均为高频任务，完整用法（声明式开关、查询历史、TTL、trigger 写法、与 BizModel 钩子选型）见：

- **`../03-runbooks/audit-field-changes.md`** — 字段级变更日志：实体加 `tagSet="audit"` 自动记 old→new，默认启用
- **`../03-runbooks/orm-interceptor-trigger.md`** — 通用 ORM 拦截器（应用层 trigger）：`IOrmInterceptor` + `orm-interceptor.xml` 配置式回调

字段级变更日志的写入形态：同一实体事件的全部审计列行在**一个**独立新会话中批量插入（单会话批量，非每列一会话）；批内任一行失败则整批失败、异常上抛（审计行全有或全无）。

## 账本保留策略

sys_lock 与 sys_change_log 双面账本由 `SysDaoLedgerCleaner`（bean `nopSysDaoLedgerCleaner`，IoC 容器 `@PostConstruct` 生命周期链周期调度，taskName=`nop-sys-ledger-cleanup`）周期清理。参数语义 SSOT = 仓库 ai-dev/design/concurrency/ledger-retention-policy.md §3.2 nop-sys 行（多语义 face 模块 face 段必配，平台内部文档——按 docs-for-ai 边界规则不直接链接），模块配置键：

| 配置键 | 缺省 | 语义 |
|--------|------|------|
| `nop.sys.ledger.lock.retention-days` | 7 | 过期僵尸锁行保留期（天）；`0`=显式禁用 lock 面清理；负值启动期 fail-fast |
| `nop.sys.ledger.changelog.retention-days` | 90 | 审计留痕保留期（天，全平台缺省最长——审计宁长勿短）；**`0`=永久保留的显式 opt-in**（合规场景）；负值启动期 fail-fast |
| `nop.sys.ledger.cleanup-interval` | 1h | 清理触发周期（双 face 共用）；下限 60s，低于下限启动期 fail-fast |
| `nop.sys.ledger.delete-batch-size` | 1000 | 单轮单表删除上限（先按锚列谓词+limit 有界拾取，再按拾取集批删）；非正数启动期 fail-fast |

清理谓词与留存语义：

- **sys_lock**：锚列 `expireAt`，谓词 `expireAt < now - 保留期`——覆盖「崩溃持有者+无人争用」僵尸锁行（既有删除点 `releaseLock`/`forceUnlock`/争用触发惰性删除对不再被争用的资源零覆盖）。活跃锁行随续约刷新 `expireAt`，不会进入清理谓词。
- **sys_change_log（留存语义裁定⑤）**：锚列 `createTime`。平台不裁定审计留痕为「永久有意保留」——缺省保留 90 天（宁长勿短全域上限）；合规场景需要长期保留时以 `nop.sys.ledger.changelog.retention-days=0` **显式声明**永久保留（opt-in），禁止无声明/无清理/无上限的三无状态延续。需要更长排查窗口的部署应调大该键而非关闭清理。
- 双面保留期均为 0 时不调度清理链。清理失败显式可见：失败轮 ERROR（`nop.sys.ledger.cleanup-fail`）+ 失败计数，周期链由绝缘层保障不断链。

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-sys-api` | API DTO 与接口定义 |
| `nop-sys-dao` | ORM 实体、DAO、`SysSequenceGenerator`、`SysCodeRuleGenerator` |
| `nop-sys-service` | 业务逻辑 |
| `nop-sys-web` | Web 层与管理页面 |

## 依赖

- `nop-auth`（需要用户上下文）

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-sys/model/nop-sys.orm.xml` |
| 事件队列实现 | `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java` |
| 普通事件 batch 调度入口 | `nop-batch/nop-batch-sys/src/main/resources/_vfs/nop/batch-task/sys-event/non-broadcast-consumer.batch.xml` + `nop-batch/nop-batch-sys/src/main/resources/_vfs/nop/job/conf/scheduler.yaml`（scheduler 直接调用 `nopBatchTaskRunner.executeAsync`） |
| 序列号生成器 | `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/seq/SysSequenceGenerator.java` |
| 编码规则生成器 | `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/coderule/SysCodeRuleGenerator.java` |
| 账本保留清理 | `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/cleanup/SysDaoLedgerCleaner.java` |
| Bean 注册 | `nop-sys/nop-sys-dao/src/main/resources/_vfs/nop/sys/beans/app-dao.beans.xml` |

## 相关文档

- `../03-runbooks/generate-business-code.md` — 业务编码 / 单据编号（CodeRule + Sequence 完整用法）
- `../reusable-modules-overview.md`
