# nop-job — 分布式定时任务

## 功能概览

分布式定时任务调度系统，协调器/工作者架构。

- **CRON 表达式**：标准 cron 调度
- **固定频率**：fixed-rate 固定间隔执行
- **固定延迟**：fixed-delay 上次结束后延迟执行
- **一次性触发**：手动触发单次执行
- **Misfire 处理**：错过触发的补偿策略
- **暂停日历**：指定日期不执行
- **阻塞策略**：任务重叠时的处理策略
- **超时控制**：任务执行超时设置
- **分片执行**：支持任务分片到多个 worker
- **与 nop-retry 集成**：失败重试

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopJobSchedule | `nop_job_schedule` | 调度定义 |
| NopJobFire | `nop_job_fire` | 触发批次 |
| NopJobTask | `nop_job_task` | 执行任务 |

## 架构

```
Coordinator (协调器)                    Worker (工作者)
┌─────────────────┐                   ┌─────────────────┐
│ NopJobSchedule   │──fire──>          │ NopJobTask       │
│ NopJobFire       │                   │ taskPayload      │
│ CRON/调度计算     │                   │ 执行 → result    │
└─────────────────┘                   └─────────────────┘
```

## 关键字段

**NopJobSchedule**:
- `triggerType`：触发类型（CRON/FIXED_RATE/FIXED_DELAY/ONCE）
- `cronExpr`：CRON 表达式
- `blockStrategy`：阻塞策略
- `timeoutSeconds`：超时秒数
- `nextFireTime`：下次触发时间
- `partitionIndex`：分区索引

**NopJobFire**:
- `triggerSource`：触发来源（SCHEDULE/MANUAL/RECOVERY）
- `fireStatus`：触发状态
- `durationMs`：执行时长

**NopJobTask**:
- `taskStatus`：任务状态
- `shardingIndex/shardingTotal`：分片信息
- `progress`：执行进度

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-job-coordinator` | 协调器 |
| `nop-job-worker` | 工作者 |
| `nop-job-core` | 核心接口 |
| `nop-job-dao` | ORM 实体与 DAO |
| `nop-job-service` | 业务逻辑 |
| `nop-job-web` | Web 层与 AMIS 页面 |
| `nop-job-retry-adapter` | nop-retry 集成适配器 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-job/model/nop-job.orm.xml` |

## 相关文档

- `../nop-retry.md`
- `../reusable-modules-overview.md`
