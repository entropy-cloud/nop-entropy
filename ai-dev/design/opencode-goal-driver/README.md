# opencode-goal-driver 设计文档

> 状态: active
> 创建: 2026-06-11

本目录记录 `ai-dev/tools/opencode-goal-driver` 工具的设计决策。

## 阅读顺序

| 文档 | 说明 |
|------|------|
| `flow-engine-design.md` | 引擎层：Step、Transition、StepResult、子流程 |
| `group-step-design.md` | Group step 的轮次和子步骤机制 |
| **`goal-driver-flow-design.md`** | 顶层流程编排：目标驱动的工作循环 |

## 职责边界

- **引擎文档**：解释 Step/Transition/Group/Subflow 的通用执行机制，不涉及具体业务步骤
- **流程文档**：解释具体步骤的排布、跳转逻辑、为什么这样排布
