# DeepWiki Plan — nop-task

> Status: approved
> Target: /Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-task @ HEAD（并行会话 plan 364 修复中，以生成时工作区为准）
> Depth: standard（受限页面集：恒含 5 + flows 2 + modules 2 + topics 1 = 10 页）
> Language: zh
> Coverage: 模块 254 个源文件（core 175/dao 16/api 12/ext 9/service 7/queue 1）在扫描范围

## 1. 概念分析（两段式压缩结论）

**形态判定**：framework-repo（任务流编排引擎的实现仓库）带运行时语义——叙事=引擎机制（任务流模型、步骤生命周期、图执行、状态持久化与恢复），不讲"如何用 nop-task 写业务"。

**领域概念**：task.xdef DSL（XML 定义任务流）→ TaskFlowModel/TaskStepsModel/TaskStepModel（模型层，fan-in 33/16）→ ITaskStep（步骤抽象，fan-in 35）/AbstractTaskStep（24）/DelegateTaskStep(12) 装饰链 → ITaskStepRuntime（48，执行期上下文）→ TaskStepReturn/StepResultBean（49，步骤产出）→ ITaskRuntime（20，运行时句柄）→ ITaskFlowManager/TaskFlowManagerImpl（编排入口）→ ITaskStateStore/ITaskState（状态持久化）。TaskConstants(51) 为常量枢纽，TaskErrors(25) 错误模型。图执行：GraphTaskStep（并行会话正在修复其正确性——以生成时工作区为准）。nop-task-queue（队列对接）/dao（状态落库）/service（BizModel 服务面）。

**核心数据流**：task.xml 加载 → TaskFlowModel 构建 → ITaskFlowManager 启动 → TaskRuntime 创建 → 图步骤逐节点执行（ITaskStepRuntime 上下文）→ StepResult 汇聚 → 状态写 ITaskStateStore → 完成/挂起/恢复。
**核心子机制**：状态持久化与恢复（挂起→ITaskState 快照→重入继续）。
**形态注意**：引擎机制为主 → sequenceDiagram 加权（运行时交互多）；消费 API 面（service/web）简要带过。

## 2. 模块地图（证据来源）

| 模块 | 职责 | 关键类型（fan-in） | 供证页面 |
|------|------|-------------------|---------|
| nop-task-core | 引擎与步骤抽象（175 文件） | TaskConstants(51)/TaskStepReturn(49)/ITaskStepRuntime(48)/ITaskStep(35)/TaskStepModel(33)/AbstractTaskStep(24) | flows/*, modules/task-core |
| nop-task-dao | 状态落库 | ITaskStateStore 实现 | flows/state-and-recovery |
| nop-task-service | BizModel 服务面 | — | modules/task-service-dao |
| nop-task-queue | 队列对接 | — | modules/task-service-dao |
| nop-task-ext | xdef/扩展 | task.xdef | flows/query 对应 DSL 章 |
| 平台 | XDSL 体系 | task.xdef | overview |

## 3. 页面契约（路径锁定）

| 路径 | 所属章 | 标题 | 职责 | 源文件（≥5） | relatedPages | 计划图表 |
|------|--------|------|------|--------------|--------------|----------|
| overview.md | 指南 | nop-task 总览：DSL 任务流编排引擎 | 定位/形态判定/能力边界 | pom, task.xdef, ITaskFlowManager, TaskStepModel, pom(nop-task) | architecture | flowchart |
| quickstart.md | 指南 | 快速上手 | 构建/测试/最小任务定义 | pom, task.xdef, ITaskFlowManager, 测试类 | overview | 豁免 |
| glossary.md | 指南 | 术语表 | Task/Step/Runtime/State 等定义与划界 | ITask, ITaskStep, ITaskRuntime, ITaskState, TaskStepModel | modules/* | 豁免 |
| reading-guide.md | 指南 | 阅读指南 | 三类读者路径（fan-in 排序） | fan-in 数据+入口 | 全部 | flowchart |
| architecture.md | 指南 | 架构与数据流 | 子模块分层/执行数据流/DSL 体系位置 | TaskFlowManagerImpl, TaskRuntimeImpl, GraphTaskStep, ITaskStateStore, TaskStepModel | flows/*, modules/* | flowchart+sequence |
| flows/task-execution.md | 机制 | 任务流执行管线：从 task.xml 到步骤输出 | 加载→模型→图执行→步骤生命周期→结果汇聚 | TaskFlowManagerImpl, GraphTaskStep, ITaskStep, ITaskStepRuntime, TaskStepReturn, TaskStepModel | modules/task-core | flowchart+sequence |
| flows/state-and-recovery.md | 机制 | 状态持久化与恢复：挂起如何变成重入 | ITaskState 快照→Store→恢复重入；与 plan 364 修复中的正确性语义对齐（以工作区为准） | ITaskStateStore, ITaskState, TaskRuntimeImpl, dao 实体, ITaskRuntime | modules/task-core | flowchart |
| modules/task-core.md | 模块 | 核心引擎与步骤抽象 | 步骤装饰链/模型层/常量与错误枢纽/资源预算 | ITaskStep, AbstractTaskStep, TaskStepReturn, TaskConstants, TaskErrors, TaskStepHelper | flows/*, glossary | sequence+class+flow |
| modules/task-service-dao.md | 模块 | 服务面与持久化对接 | BizModel 服务/DAO/队列如何包装引擎 | service 类, dao 实体, queue 类, ITaskStateStore | modules/task-core, flows/state-and-recovery | flowchart |
| topics/error-model.md | 主题 | 错误模型：TaskErrors 体系 | 错误码分工/步骤失败语义/与平台 NopException 的关系 | TaskErrors, TaskStepReturn, AbstractTaskStep, ITaskStepRuntime | modules/task-core | 无强制 |

## 4. 生成顺序
批 1a：modules/task-core、modules/task-service-dao、flows/task-execution、flows/state-and-recovery → 批 1b：topics/error-model、glossary → 批 2：architecture → overview、quickstart、reading-guide

## 5. 覆盖缺口与风险
- fan-in 文件级口径；nop-task-web/codegen/meta 0-2 文件不单独成页（并入 service 页带过）。
- 并行会话（plan 364）正在修复 GraphTaskStep/TaskConstants——页面断言以生成时工作区为准，HEAD 漂移后由增量机制处理。
