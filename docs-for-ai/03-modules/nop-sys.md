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
| **数据字典** | ORM `<dict>` 标签 + `NopSysDict`/`NopSysDictOption`；AMIS select 自动加载 | `../03-runbooks/add-dict-and-constants.md` |
| **分布式锁** | 注入 `ILockService.tryLock/unlock` | 见下方"分布式锁" |
| **扩展字段(EAV)** | 实体 `tagSet="use-ext-field"` + `NopSysExtField` | `../02-core-guides/orm-model-design.md` |
| **国际化(i18n)** | ORM 列 `i18n-en:displayName` + `NopSysI18n` | `../02-core-guides/orm-model-design.md` |
| **事件队列** | `SysDaoMessageService`（事件存储 + 广播消费 + 普通事件触发原语） / `nop-batch-sys`（普通事件 batch trigger） | 见下方"事件队列" |
| **字段级变更日志（ChangeLog）** | 实体加 `tagSet="audit"` → 自动记字段级 old→new 到 `NopSysChangeLog`；列加 `tagSet="no-audit"` 排除 | **`../03-runbooks/audit-field-changes.md`**（声明式开关 / 查询历史 / TTL / 反模式） |
| **通用 ORM 拦截器（应用层 trigger）** | 注册 `IOrmInterceptor` 或写 `orm-interceptor.xml` 配置式回调（per-entity + 8 hook + XPL） | **`../03-runbooks/orm-interceptor-trigger.md`**（trigger 概念 / 两种实现 / 与 BizModel 钩子选型） |

## 序列号生成（Sequence）

`ISequenceGenerator`（`io.nop.dao.seq.ISequenceGenerator`）是序列号统一入口，按 `seqName` 取号：

```java
@Inject
ISequenceGenerator sequenceGenerator;

long nextVal = sequenceGenerator.generateLong("order_seq", false);
```

实现 `SysSequenceGenerator` 的并发与性能模型（cacheSize 批量取号 / DB 行锁 / snowflake/uuid 兜底 / `REQUIRES_NEW` 独立事务不被外层回滚），以及业务编码规则的完整用法，见 **`../03-runbooks/generate-business-code.md`**。

## 分布式锁

```java
@Inject
ILockService lockService;

boolean acquired = lockService.tryLock("order_lock", "lock_group", holderId, expireSeconds);
lockService.unlock("order_lock", "lock_group", holderId);
```

## 事件队列

- 同一数据库事务内发布的本地 outbox 能力，不依赖外部 MQ
- 普通事件：`nop_sys_event`，按 `partitionIndex` 稳定路由，至少一次；`nop-sys-dao` 负责 shared queue + lease + 重排队语义，`nop-batch-sys` 通过 batch DSL（`.batch.xml`）+ `IBatchTaskManager` + `findNext` keyset pagination 周期扫描并触发执行
- 普通事件支持两种触发模式：simple mode（`SysDaoMessageService` 内建定时器）和 batch mode（`nop-job` 调度 `SysEventBatchTrigger` 执行 batch DSL）；通过 `nonBroadcastAutoScanEnabled` 开关切换
- 普通事件扫描禁止使用 OFFSET 分页；simple mode 和 batch mode 都基于 `findNext` keyset pagination
- 广播事件：`nop_sys_broadcast_event`，通过 `findNext` keyset pagination + 内存游标 + 时间窗口消费，不持久化消费进度，重启后从时间窗口起点重新消费（at-least-once），消费失败不阻塞后续事件
- 普通事件与广播事件都只保证 at-least-once；listener 必须自行幂等
- `partitionIndex` 默认来自 `bizObjName + '|' + bizKey` 的 stable short hash；没有顺序键时退化为 topic hash，不保证同键顺序
- batch trigger 只是普通事件的一种触发执行机制，不改变 event row 状态机；成功/失败仍回写到 `nop_sys_event` 行本身（如 `PROCESSED`、`WAITING + scheduleTime + retryTimes`）
- batch DSL 是 `nop-batch-sys` 的支持基线，不再使用编程式 `BatchTaskBuilder`；`SysEventBatchTrigger` 通过 `IBatchTaskManager` 加载 `.batch.xml`，DSL 中的 loader/processor/consumer 通过 `<source>` XPL 委托 `SysDaoMessageService` 方法

## 字段级变更日志与 ORM 拦截器

nop-sys 内置字段级变更日志（`NopSysChangeLog` + `OrmEntityChangeLogInterceptor`），并基于通用 `IOrmInterceptor` / `orm-interceptor.xml` 提供应用层 trigger 机制。两者均为高频任务，完整用法（声明式开关、查询历史、TTL、trigger 写法、与 BizModel 钩子选型）见：

- **`../03-runbooks/audit-field-changes.md`** — 字段级变更日志：实体加 `tagSet="audit"` 自动记 old→new，默认启用
- **`../03-runbooks/orm-interceptor-trigger.md`** — 通用 ORM 拦截器（应用层 trigger）：`IOrmInterceptor` + `orm-interceptor.xml` 配置式回调

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-sys-api` | API DTO 与接口定义 |
| `nop-sys-dao` | ORM 实体、DAO、`SysSequenceGenerator`、`SysCodeRuleGenerator` |
| `nop-sys-service` | 业务逻辑 |
| `nop-sys-web` | Web 层与 AMIS 页面 |

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
| Bean 注册 | `nop-sys/nop-sys-dao/src/main/resources/_vfs/nop/sys/beans/app-dao.beans.xml` |

## 相关文档

- `../03-runbooks/generate-business-code.md` — 业务编码 / 单据编号（CodeRule + Sequence 完整用法）
- `../reusable-modules-overview.md`
