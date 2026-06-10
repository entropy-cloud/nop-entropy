# nop-task — 任务/逻辑流引擎

## 功能概览

通用任务/逻辑流引擎，定义和执行多步骤任务流。

- 任务定义与版本管理（PUBLISHED/DEPRECATED/ARCHIVED）
- 多步骤编排
- 子任务/子流程
- 信号机制（signals）
- 重试与超时
- 任务权限管理

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopTaskDefinition | `nop_task_definition` | 任务流定义 |
| NopTaskDefinitionAuth | `nop_task_definition_auth` | 任务定义权限 |
| NopTaskInstance | `nop_task_instance` | 运行中的任务实例 |
| NopTaskStepInstance | `nop_task_step_instance` | 步骤实例 |

## 任务状态

定义状态：`UNPUBLISHED` → `PUBLISHED` → `DEPRECATED` → `ARCHIVED`

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-task-core` | 任务核心引擎 |
| `nop-task-dao` | ORM 实体与 DAO |
| `nop-task-service` | 业务逻辑 |
| `nop-task-web` | Web 层与 AMIS 页面 |
| `nop-task-queue` | 队列支持 |
| `nop-task-ext` | 扩展功能 |

## 关系

- **nop-wf**（工作流引擎）基于 nop-task 构建
- nop-task 提供底层任务编排能力，nop-wf 在其上增加工作流语义

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-task/model/nop-task.orm.xml` |

## 相关文档

- `../nop-wf.md`
- `../reusable-modules-overview.md`
