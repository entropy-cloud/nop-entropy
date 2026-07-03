# nop-wf — 工作流引擎

## 功能概览

完整的工作流/BPM 引擎。nop-wf 是独立引擎，**并非基于 nop-task 构建**（`nop-wf` 各模块 pom 不依赖 `nop-task`，源码不引用 `io.nop.task`）。

`nop-wf` 相比很多传统工作流引擎的一个关键差异是：它大量复用了 Nop 平台通用的 XDSL、Delta、模板继承和编译期元编程机制，而不是为 workflow 单独发明一套扩展体系。详见 `../06-extensibility/platform-extensibility-mechanism.md` 与 `../06-extensibility/nop-wf-as-example.md`。

- 复杂审批工作流
- 多步骤类型（人工步骤、自动步骤、子流程）
- 参与者分配（指定人、角色、部门）
- **委托**：将待办委托给他人
- **转办**：转交给他人处理
- **会签**：多人同时审批
- **加签**：动态增加审批人
- **顺序签**：按顺序逐一审批
- **投票**：按权重投票
- 子工作流
- 信号机制
- 超时升级
- 步骤级动作
- 完整状态生命周期

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopWfDefinition | `nop_wf_definition` | 工作流定义 |
| NopWfDefinitionAuth | `nop_wf_definition_auth` | 定义级权限 |
| NopWfInstance | `nop_wf_instance` | 运行中的工作流实例 |
| NopWfStepInstance | `nop_wf_step_instance` | 步骤实例 |
| NopWfStepInstanceLink | `nop_wf_step_instance_link` | 步骤转换链接 |
| NopWfAction | `nop_wf_action` | 工作流动作 |
| NopWfStatusHistory | `nop_wf_status_history` | 状态变更历史 |
| NopWfOutput | `nop_wf_output` | 工作流输出 |
| NopWfVar | `nop_wf_var` | 工作流变量 |
| NopWfWork | `nop_wf_work` | 待办工作项 |

## 步骤状态

`created` → `suspended` → `waiting` → `activated` → `completed` / `expired` / `failed` / `killed` / `skipped` / `rejected` / `withdrawn` / `cancelled` / `transferred`

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-wf-core` | 工作流核心引擎 |
| `nop-wf-dao` | ORM 实体与 DAO |
| `nop-wf-service` | 业务逻辑 |
| `nop-wf-web` | Web 层与 AMIS 页面 |

## 依赖

nop-wf 是独立工作流引擎，不依赖 nop-task。

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-wf/model/nop-wf.orm.xml` |

## 相关文档

- `../nop-task.md`
- `../reusable-modules-overview.md`
- `../06-extensibility/platform-extensibility-mechanism.md`
- `../06-extensibility/nop-wf-as-example.md`
- `../02-core-guides/workflow-configuration.md`
- `../03-runbooks/build-approval-flow.md`
- 审批流设计文档见 ai-dev/design/nop-wf/approval-flow-design.md（串签/并签/会签/条件路由/驳回等模式映射，仅平台开发者参考）
