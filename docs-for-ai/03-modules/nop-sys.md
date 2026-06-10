# nop-sys — 系统管理模块

## 功能概览

系统级基础设施服务，提供多个通用能力。

- **序列号生成**：可配置步长、缓存大小、最大值、重置策略
- **数据字典**：字典 + 选项键值对
- **国际化(i18n)**：按 locale 查询翻译
- **Maker-Checker 审批**：制单人→审核人审批流
- **业务编码规则**：模式化编号生成（如 `ORD-{yyyy}{MM}-{seq}`）
- **通知模板**：消息通知模板管理
- **用户/系统变量**：作用域变量存储
- **扩展字段(EAV)**：实体-属性-值的动态扩展字段
- **分布式锁**：数据库锁，带过期时间
- **集群选举**：Leader 选举
- **事件队列**：进程内事件队列，分区扫描，支持延迟事件

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopSysSequence | `nop_sys_sequence` | 序列号生成器 |
| NopSysDict | `nop_sys_dict` | 数据字典 |
| NopSysDictOption | `nop_sys_dict_option` | 字典选项 |
| NopSysI18n | `nop_sys_i18n` | 国际化消息 |
| NopSysCheckerRecord | `nop_sys_checker_record` | Maker-Checker 审批记录 |
| NopSysCodeRule | `nop_sys_code_rule` | 业务编码规则 |
| NopSysNoticeTemplate | `nop_sys_notice_template` | 通知模板 |
| NopSysUserVariable | `nop_sys_user_variable` | 用户变量 |
| NopSysVariable | `nop_sys_variable` | 系统变量 |
| NopSysExtField | `nop_sys_ext_field` | 扩展字段(EAV) |
| NopSysLock | `nop_sys_lock` | 分布式锁 |
| NopSysClusterLeader | `nop_sys_cluster_leader` | 集群 Leader |
| NopSysEvent | `nop_sys_event` | 事件队列 |

## 序列号生成

```java
// 注入序列号服务
@Inject
ISequenceGenerator sequenceGenerator;

// 生成下一个序列号
long nextVal = sequenceGenerator.generateLong("order_seq");
```

支持的重置策略（`resetType`）：
- `NONE`：不重置
- `DAILY`：每日重置
- `MONTHLY`：每月重置
- `YEARLY`：每年重置

## 数据字典

字典通过 ORM 模型的 `<dict>` 标签定义，运行时通过 `NopSysDict` 和 `NopSysDictOption` 管理。

前端通过 AMIS 的 `select` 组件自动加载字典选项。

## 分布式锁

```java
@Inject
ILockService lockService;

// 获取锁
boolean acquired = lockService.tryLock("order_lock", "lock_group", holderId, expireSeconds);
// 释放锁
lockService.unlock("order_lock", "lock_group", holderId);
```

## 事件队列

- 基于 `nop_sys_event` 表的进程内事件队列
- 支持分区扫描（`partitionIndex`）
- 支持延迟事件（`processTime`）
- 支持事件状态追踪（CREATED/PROCESSED/FAILED）

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-sys-api` | API DTO 与接口定义 |
| `nop-sys-dao` | ORM 实体与 DAO |
| `nop-sys-service` | 业务逻辑 |
| `nop-sys-web` | Web 层与 AMIS 页面 |

## 依赖

- `nop-auth`（需要用户上下文）

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-sys/model/nop-sys.orm.xml` |
| 序列号服务 | `nop-sys/nop-sys-service/` |

## 相关文档

- `../reusable-modules-overview.md`
