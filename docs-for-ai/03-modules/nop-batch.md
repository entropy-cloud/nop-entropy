# nop-batch — 批处理引擎

## 功能概览

企业级批处理引擎，支持大数据量场景。

- **Chunk 处理模式**：按批次读取→处理→写入
- **文件输入/输出**：支持文件作为数据源
- **断点续传**：通过 `completedIndex` 记录进度
- **记录级幂等**：每条记录独立追踪状态
- **重试机制**：失败记录可重试
- **进度追踪**：处理数/写入数/跳过数/重试数

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopBatchTask | `nop_batch_task` | 批处理任务 |
| NopBatchTaskVar | `nop_batch_task_var` | 任务状态变量（EAV） |
| NopBatchRecordResult | `nop_batch_record_result` | 记录级处理结果（幂等） |
| NopBatchFile | `nop_batch_file` | 批处理文件追踪 |

## 关键字段

**NopBatchTask**:
- `taskStatus`：任务状态
- `completedIndex`：已完成位置（断点续传）
- `processItemCount` / `writeItemCount` / `skipItemCount` / `retryItemCount`：统计计数
- `loadRetryCount`：重试加载次数

**NopBatchRecordResult**:
- `recordKey`：记录键（幂等标识）
- `resultStatus`：处理结果状态
- `retryCount`：重试次数
- `handleStatus`：处理状态

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-batch-core` | 批处理核心引擎 |
| `nop-batch-dsl` | 批处理 DSL 定义 |
| `nop-batch-orm` | ORM 集成 |
| `nop-batch-jdbc` | JDBC 批处理 |
| `nop-batch-biz` | BizModel 集成 |
| `nop-batch-dao` | ORM 实体与 DAO |
| `nop-batch-service` | 业务逻辑 |
| `nop-batch-web` | Web 层与 AMIS 页面 |

## 典型使用场景

- 大批量数据导入（CSV/Excel → 数据库）
- 定期数据同步
- 批量报表生成
- 批量消息发送

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-batch/model/nop-batch.orm.xml` |

## 相关文档

- `../reusable-modules-overview.md`
