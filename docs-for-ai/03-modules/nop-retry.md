# nop-retry — 分布式重试引擎

## 功能概览

分布式重试引擎，支持可配置的退避策略。

- 可配置退避策略（固定间隔、指数退避）
- 重试策略管理
- 幂等键支持
- 命名空间/分组隔离
- 回调触发
- 重试尝试追踪

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopRetryPolicy | `nop_retry_policy` | 重试策略 |
| NopRetryRecord | `nop_retry_record` | 重试记录 |
| NopRetryAttempt | `nop_retry_attempt` | 重试尝试 |

## 退避策略

- **固定间隔**：每次重试间隔固定毫秒数
- **指数退避**：间隔随重试次数指数增长

## 关键字段

**NopRetryPolicy**:
- `backoffStrategy`：退避策略
- `maxRetryCount`：最大重试次数
- `intervalMs`：间隔毫秒数
- `immediateRetryCount`：立即重试次数

**NopRetryRecord**:
- `namespaceId`：命名空间
- `groupId`：分组
- `executorName`：执行器名称
- `idempotentId`：幂等键
- `status`：状态
- `nextTriggerTime`：下次触发时间

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-retry-engine` | 重试引擎核心 |
| `nop-retry-dao` | ORM 实体与 DAO |
| `nop-retry-service` | 业务逻辑 |
| `nop-retry-web` | Web 层与 AMIS 页面 |

## 关系

- `nop-job` 可通过 `nop-job-retry-adapter` 集成 nop-retry 实现任务失败重试

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-retry/model/nop-retry.orm.xml` |

## 相关文档

- `../nop-job.md`
- `../reusable-modules-overview.md`
