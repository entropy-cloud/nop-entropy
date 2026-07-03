# nop-wf 设计文档索引

> 本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 文档结构与阅读顺序

### 必读路径（Architecture Baseline）

| 文档 | 层级 | 职责 |
|------|------|------|
| `approval-flow-design.md` | Architecture Baseline | 审批流核心模式：步骤类型、执行分组（会签/或签/串签/票签）、分支路由、action 系统、actor 模型、ORM 持久化、状态机、DAG 约束 |
| `extensions-design.md` | Architecture Baseline | 扩展机制：AI 审批集成、调度器集成、离职转办、票签策略标签库、动态审批模式。回答"如何不改引擎核心地补齐功能缺口" |
| `dingflow-json-format.md` | Interface Contract | DingFlow JSON 格式规格：审批流 JSON 导入格式的完整定义（节点类型、审批人分配、协作模式、条件路由、表单权限、转换映射规则）。所有枚举值使用字符串类型 |

### Vision 层

nop-wf 的 Vision（产品定位、不可违反约束）分散在两个 Architecture Baseline 文档的约束章节中：

- **引擎核心不可侵蚀**：`WorkflowEngineImpl` 是状态机的唯一实现，新增能力不得通过修改引擎实现，必须通过扩展点（action `<source>`、`<when>`、`<check-exec-group-complete>`、listener、signal、标签库）或外置可选模块实现。见 `extensions-design.md` §三 设计原则。
- **模型即代码**：工作流定义是 `.xwf` XDSL 文件，受 `/nop/schema/wf/wf.xdef` 校验，支持 `x:extends` 继承与 Delta 定制。运行时不得修改实例的模型结构。见 `approval-flow-design.md` 设计约束章节。
- **DAG 无环**：模型加载时构建 DAG 并检测循环，回退连接需 `backLink="true"` 豁免。见 `approval-flow-design.md` DAG 无环结构。

### 按需深入

- XDef Schema 完整定义：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/wf/wf.xdef`
- 引擎实现：`nop-wf/nop-wf-core/src/main/java/io/nop/wf/core/engine/WorkflowEngineImpl.java`
- 配置使用手册：`docs-for-ai/02-core-guides/workflow-configuration.md`
- 实操指南：`docs-for-ai/03-runbooks/build-approval-flow.md`
- 15 个产品级示例：`nop-wf/nop-wf-service/src/main/resources/_vfs/nop/wf/examples/`

## 职责边界

- `approval-flow-design.md` 回答"**引擎能做什么**"——核心审批流模式的模型表达和执行语义。
- `extensions-design.md` 回答"**如何扩展引擎**"——在不修改 `WorkflowEngineImpl` 的前提下，通过外置模块、标签库、扩展点补齐功能。
- `dingflow-json-format.md` 回答"**外部如何对接**"——DingFlow JSON 格式的完整规格，作为转换器（`dingflow-tran.xlib`）的输入契约。
- 三篇文档共享同一个 Vision（引擎核心不可侵蚀、模型即代码），共同构成 nop-wf 的完整设计基线。
