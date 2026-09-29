# 术语表

> 本页源文件基准（相对于 `deepwiki/nop-task/`，逐条验证可达仓库文件）：
>
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskRuntime.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskRuntime.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskState.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskState.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java)
> - [../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef](../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef)

本页精确划定 nop-task 领域的同名/近名术语。执行机制细节见[任务执行管线](./flows/task-execution.md)，状态恢复见[状态与恢复](./flows/state-and-recovery.md)，步骤抽象与装饰链全貌见[核心引擎](./modules/task-core.md)，错误码分工见[错误模型](./topics/error-model.md)。

## 核心术语

| 术语 | 定义 | 出处 | 易混淆项 |
|---|---|---|---|
| Task / `ITask` | 单次任务请求的可执行定义：继承 `IActionBaseModel`，以 taskName+taskVersion 标识，持有 mainStep；`execute(taskRt, outputNames)` 驱动主步骤，并负责把 task 驱动到终态（COMPLETED/FAILED/KILLED/TIMEOUT）或挂起态（SUSPENDED）后落盘。运行时实现为 `TaskImpl`。 | `nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java:20-29`；`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:36,91-175` | TaskFlow、TaskInstance、task.xdef |
| TaskFlow | 不是独立运行时对象，而是 task.xml DSL 体系在类型名上的前缀：`TaskFlowModel`（task.xdef 解析出的模型，`getTask()` 惰性构建并缓存 `ITask`）、`ITaskFlowManager`（编排入口）、`ITaskFlowMetrics`。仓库中不存在 `ITaskFlow` 接口。 | `nop-task/nop-task-core/src/main/java/io/nop/task/model/TaskFlowModel.java:22,38-43` | Task/`ITask`（可执行定义 vs 模型/管理器侧面命名） |
| `ITaskStep`（步骤抽象） | 步骤契约，接口注释定位为"相当于是一种函数定义，支持多输入和多输出"：`getStepType/getPersistVars/isConcurrent/getInputs/getOutputs` 五个元数据读取器 + `execute(stepRt)` 返回 `@Nonnull TaskStepReturn`（允许 `CompletionStage`，同步/异步由返回值统一表达）。 | `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java:19-22,49-50` | TaskStepModel、ITaskStepExecution |
| `TaskStepModel`（模型层） | task.xdef 中 `TaskStepModel` 节点生成的 DSL 模型抽象基类：继承生成类 `_TaskStepModel`，携带 next/nextOnError/saveState/waitSteps 等声明属性；是构建期输入，经装饰链管线编译成可执行的 `ITaskStep`，本身不执行。 | `nop-task/nop-task-core/src/main/java/io/nop/task/model/TaskStepModel.java:19-28`；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:133-146` | `ITaskStep`（模型=XML 声明侧，ITaskStep=引擎可执行侧） |
| `ITaskStepRuntime`（步骤执行期上下文） | 单个步骤的执行期上下文：evalScope 变量读写、`getCancelToken`、输出选择集 `getOutputNames`、`getState()` 返回 `ITaskStepState`、`saveState`/`isRecoverMode`，并经 `newStepRuntime` 派生子步骤运行时；生命周期与单个 step 实例执行对应。 | `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java:12,30-34,64,93,121-129` | `ITaskRuntime`（task 级句柄，经 `getTaskRuntime()` 可达） |
| `ITaskRuntime`（运行时句柄） | 整个 Task 实例的运行时句柄，接口注释约定：request/response 保存请求与结果、taskVars 保存需持久化的 task 级变量、attributes 保存不持久化的临时变量；提供 `getTaskState`、`newMainStepRuntime`、`newChildRuntime`（子任务）、线程池/限流/信号量获取与 `cancel(reason)`。 | `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskRuntime.java:27-36,53,86,140,154-163` | `ITaskStepRuntime`（一对多：一个 taskRt 派生全部 stepRt） |
| `TaskStepReturn`（步骤产出） | 步骤产出协议：`nextStepName + outputs + 可选 future` 三元组；工厂 `CONTINUE`/`SUSPEND`/`EXIT`/`RETURN`/`ASYNC` 等以哨兵步名 `@end`/`@exit`/`@suspend` 表达控制流；`isSuspend/isEnd/isExit` 按值判定而非对象身份（包装层重建返回值时身份判断会丢失挂起语义）。 | `nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java:33-37,44-52,215-228` | StepResultBean（图内按步名归档的结果记录）、`IExecution` |
| `TaskConstants`（常量枢纽） | 任务引擎常量单点：xdef 路径 `/nop/schema/task/task.xdef`、主步骤名 `@main`、三个哨兵步名、`VAR_*`/`PARAM_*` 变量与参数名、`TASK_STATUS_*`/`TASK_STEP_STATUS_*` 状态码（数值单源自生成的 `_NopTaskCoreConstants`，与 ORM 字典对齐，错位即编译期暴露）、全部 `STEP_TYPE_*` 步骤类型。 | `nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java:13,31,38-48,81-121,123-181` | `_NopTaskCoreConstants`（生成层，TaskConstants 引用它而非相反）、`TaskErrors`（错误码） |
| `ITaskState` / `ITaskStateStore` | `ITaskState`：一个 TaskInstance 的可持久化状态——taskName/version、taskStatus、request/response、taskVars，`isTerminal()` 判定 COMPLETED/KILLED/FAILED/TIMEOUT 四终态；步骤级对应物是 `ITaskStepState`（stepPath+runId 唯一定位、stateBean、result/exception），两者公共部分在 `ITaskStateCommon`。`ITaskStateStore`：状态存储契约——`newMainStepState/loadMainStepState/loadStepState/saveStepState/saveTaskState`，`isSupportPersist()` 为 false（in-memory）时不支持断点恢复。 | `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskState.java:12,20-28,73-90`；`nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepState.java:18,28-41`；`nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java:10-41` | `ITaskRuntime`（运行期句柄 vs `ITaskState` 可持久化快照）；DaoTaskStateStore（DB 实现） |
| `TaskFlowManager`（编排入口） | `ITaskFlowManager`/`TaskFlowManagerImpl`：按 taskName+version 提供 `getTask/parseTask/loadTaskFromPath` 与 `getTaskFlowModel`；`newTaskRuntime` 创建运行时（`saveState` 为 true 时要求持久化 store，否则用 in-memory store）；`getTaskRuntime(taskInstanceId,...)` 从存储恢复既有实例。 | `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java:16-40`；`nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:50,97-118` | `ITaskStepLib`（可复用步骤库）、`ITaskFlowMetrics`（指标） |
| task.xdef（DSL） | 任务定义 DSL 的 schema：`/nop/schema/task/task.xdef`，根节点 `task` 声明 `xdef:name="TaskFlowModel"` 与 `xdef:bean-package="io.nop.task.model"`（模型层由此生成）；`TaskExecutableModel` 公共段定义 decorator/retry/throttle/rate-limit/when/validator/catch/finally，`TaskStepModel` 定义步骤跳转属性（next/nextOnError/waitSteps/saveState），`graph` 节点定义图步骤。`TaskConstants.XDEF_PATH_TASK` 指向它。 | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:9-15,30-32,82,133-146,175-176` | `TaskStepModel`/`TaskFlowModel`（xdef 派生的模型类）、`FILE_TYPE_TASK="task.xml"` |
| `GraphTaskStep`（图步骤） | `STEP_TYPE_GRAPH="graph"` 的实现：按节点声明的 `waitSteps/waitErrorSteps/waitCompleteSteps` 依赖并发调度（`GraphStepNode`），`enterSteps` 起步、`exitSteps` 收官；错误可被 waitError/waitComplete 节点消费（不 fail-fast），无消费者才取消全图；节点返回挂起时以挂起语义终结全图并停止派发兄弟分支。 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:41,54-97,179-246,275-309`；`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:175-176` | `ParallelTaskStep`（无条件并行 + join 汇总，非依赖驱动）、`ForkTaskStep` |
| 装饰链（`AbstractTaskStep`/`DelegateTaskStep`/`ITaskStepDecorator`） | `ITaskStep` 的两条继承轴：`AbstractTaskStep` 是具体步骤基类（落元数据字段 + `makeReturn` 工厂规约产出），Sequential/Graph/Eval 等沿此轴；`DelegateTaskStep` 是包装器基类（持有内层 `ITaskStep`、元数据全部透传、只改写 execute），retry/timeout/throttle/rate-limit 等横切 wrapper 沿此轴；`ITaskStepDecorator` 按 task.xdef 的 `<decorator order bean>` 声明对步骤做增强，order 小者先应用。 | `nop-task/nop-task-core/src/main/java/io/nop/task/step/AbstractTaskStep.java:20,86-92`；`nop-task/nop-task-core/src/main/java/io/nop/task/step/DelegateTaskStep.java:11-26`；`nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepDecorator.java:7` | `ITaskStepExecution`（不是 ITaskStep：包住装饰链整体做输入输出绑定与状态驱动的外壳，`ITaskStepExecution.java:10-16`） |

## 边界组辨析

| 边界组 | 划界依据（源码） |
|---|---|
| Task vs TaskFlow | Task 是可执行定义：`ITask.execute` 直接驱动 mainStep 并写终态（`ITask.java:29`、`TaskImpl.java:91-175`）。TaskFlow 只出现在模型/管理器/指标类型名中：`TaskFlowModel` 持有解析产物并惰性构建 `ITask`（`TaskFlowModel.java:38-43`），`ITaskFlowManager` 负责创建与恢复运行时（`ITaskFlowManager.java:18-20`）。判断口诀：能 `execute` 的是 Task，带 TaskFlow 前缀的都是"定义的管理侧面"。 |
| `ITaskStepRuntime` vs `ITaskRuntime` | `ITaskRuntime` 是 task 级：持有 `ITaskState`、分配 runId、创建 `newMainStepRuntime()`、管理线程池/限流资源与 `addTaskCleanup`（`ITaskRuntime.java:53,60,140,163`）。`ITaskStepRuntime` 是 step 级：持有 `ITaskStepState` 与 cancelToken、管理输出选择集与 `saveState`（`ITaskStepRuntime.java:32-34,64,121`）；它经 `getTaskRuntime()` 回指 task 级（`ITaskStepRuntime.java:30`），`isRecoverMode`/`saveState` 两侧各有一份镜像语义（task 级见 `ITaskRuntime.java:146,152`）。 |
| `TaskStepModel` vs `ITaskStep` | 模型 vs 抽象的分界在构建管线：`TaskStepModel`（抽象基类继承生成的 `_TaskStepModel`）承载 XML 声明属性，由 `getType()` 标明节点类型（`TaskStepModel.java:19-28`）；`ITaskStep` 是编译产物接口，`execute` 是唯一执行入口（`ITaskStep.java:50`）。同名陷阱：task.xdef 里的 `xdef:name="TaskStepModel"`（`task.xdef:133`）生成的正是前者，不是任何可执行类。 |
| 挂起 vs 取消 vs 失败 | 挂起：步骤返回 `TaskStepReturn.SUSPEND`（哨兵 `@suspend`），非终态——TaskImpl 置 `TASK_STATUS_SUSPENDED` 并落盘等待 resume，task 级 bean 容器保留（`TaskStepReturn.java:37,215-220`；`TaskImpl.java:153-160`；`TaskConstants.java:46-48`）；`SuspendTaskStep` 的语义类似 yield（`SuspendTaskStep.java:32-33`）。取消：`ITaskRuntime.cancel(reason)` 驱动 cancelToken 传播，步骤侧抛 reason-carrying 取消异常（`TaskRuntimeImpl.java:85-106`；`TaskStepHelper.java:71-80`）；task seam 按 reason 分流终态——`CANCEL_REASON_TIMEOUT` → TIMEOUT，其余 kill 类 → KILLED（`TaskImpl.java:218-229`；`TaskStepHelper.java:134-136`）；SUSPENDED 任务被 cancel 直接驱动 KILLED 落盘（`TaskRuntimeImpl.java:90-105`）。失败：非 cancellation 异常 → FAILED；step 级 `ITaskStepState.fail` 仅记账 exception 不置终态，终态须由 driver 配对 `setStepStatus`（`TaskImpl.java:200-209`；`ITaskStepState.java:130-136`）。三者终态码均对齐 ORM 字典（`TaskConstants.java:81-121`），错误码定义见 `TaskErrors.java:89,175-181`。 |
| `ITaskState` vs `ITaskStepState` | 同一持久化体系的两个粒度：task 级以 taskInstanceId 定位、`isTerminal` 判四终态（`ITaskState.java:20-28,77`）；step 级以 stepPath（静态 stepName）+runId（动态执行路径，如循环嵌套 `:3:2`）唯一定位，持久化 result/exception/stateBean（`ITaskStepState.java:28-41,73-77`）。公共字段收口在 `ITaskStateCommon`（`ITaskStateCommon.java:16-19`）。 |

## Sources

- [nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java:20-29](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java#L20-L29)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java:19-50](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java#L19-L50)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java:12-129](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java#L12-L129)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskRuntime.java:27-163](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskRuntime.java#L27-L163)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java:33-228](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java#L33-L228)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java:13-181](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java#L13-L181)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskState.java:12-129](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskState.java#L12-L129)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java:10-41](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java#L10-L41)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepState.java:18-149](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepState.java#L18-L149)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateCommon.java:16-19](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateCommon.java#L16-L19)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java:16-40](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskFlowManager.java#L16-L40)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepDecorator.java:7](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepDecorator.java#L7)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepExecution.java:10-16](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepExecution.java#L10-L16)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskErrors.java:89-181](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/TaskErrors.java#L89-L181)
- [nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:36-295](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java#L36-L295)
- [nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:50-118](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java#L50-L118)
- [nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java:85-106](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java#L85-L106)
- [nop-task/nop-task-core/src/main/java/io/nop/task/model/TaskStepModel.java:19-28](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/model/TaskStepModel.java#L19-L28)
- [nop-task/nop-task-core/src/main/java/io/nop/task/model/TaskFlowModel.java:22-43](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/model/TaskFlowModel.java#L22-L43)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/AbstractTaskStep.java:20-92](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/step/AbstractTaskStep.java#L20-L92)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/DelegateTaskStep.java:11-26](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/step/DelegateTaskStep.java#L11-L26)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:41-309](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java#L41-L309)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/SuspendTaskStep.java:15-41](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/step/SuspendTaskStep.java#L15-L41)
- [nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:71-136](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java#L71-L136)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef:9-176](https://gitee.com/canonical-entropy/nop-entropy/blob/0e67dba845/nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef#L9-L176)

---

## On this page

- 核心术语
- 边界组辨析
