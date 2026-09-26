# 阅读指南

> 本页引用基准（wiki 页相对本页 `deepwiki/nop-task/`；源码相对本页，即仓库根相对路径）：
>
> - [overview.md](overview.md)｜[quickstart.md](quickstart.md)｜[glossary.md](glossary.md)｜[architecture.md](architecture.md)
> - [flows/task-execution.md](flows/task-execution.md)｜[flows/state-and-recovery.md](flows/state-and-recovery.md)
> - [modules/task-core.md](modules/task-core.md)｜[modules/task-service-dao.md](modules/task-service-dao.md)｜[topics/error-model.md](topics/error-model.md)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java)
> - [../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java)
> - [../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef](../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef)

本页为 nop-task 全部 10 个 wiki 页给出三类读者的按序阅读路径。排序依据 fan-in（文件级 grep 口径：源码中引用该类型的文件数）——被引用最多的类型是引擎全部机制的交汇点，先读它们的定义页（术语表、核心引擎），再读使用它们的机制页（flows），最后读横切主题（错误模型）。三类读者：**A 业务集成者**（用 nop-task 编排任务）、**B 引擎维护者**（理解/修改执行引擎）、**C 扩展贡献者**（加步骤类型/改状态存储/改图语义）。

## fan-in 排序表

下表同时是三类路径的优先级索引：「读者优先级」列的编号对应下文各路径的步骤号，`·` 分隔多个读者的到达点。

| 类型 | fan-in | 所属页面 | 读者优先级 |
|---|---|---|---|
| `TaskConstants` | 51 | 核心引擎「TaskConstants 与 TaskStepHelper：常量枢纽与横切工具」 | B3·C2·A 按需查 |
| `TaskStepReturn` | 49 | 核心引擎「TaskStepReturn：一个对象表达四种出口」 | A4 前置·B3·C1 |
| `ITaskStepRuntime` | 48 | 任务执行管线「步骤执行层：TaskStepExecution 与 ITaskStepRuntime」 | B4·C3 |
| `ITaskStep` | 35 | 核心引擎「步骤抽象：一个接口，两条继承轴」 | B3·C1 |
| `TaskStepModel` | 33 | 核心引擎「模型层：xdef 生成的复合嵌套结构」 | C2 |
| `TaskErrors` | 25 | 错误模型「TaskErrors：33 条错误码的全表核查」 | A6·B6·C5 |
| `AbstractTaskStep` | 24 | 核心引擎「步骤抽象：一个接口，两条继承轴」 | B3·C1·C2 |

三条路径在下图汇于同一组 wiki 页，仅入口与顺序不同；边标为到达该页的步骤序号，每条路径起点即第 1 步。

```mermaid
flowchart TD
    subgraph SA["A 业务集成者"]
        A1["总览"] -->|"2"| A2["快速上手"]
        A2 -->|"3"| A3["术语表"]
        A3 -->|"4"| A4["任务执行管线"]
        A4 -->|"5"| A5["状态与恢复"]
        A5 -->|"6"| A6["错误模型"]
        A6 -->|"7"| A7["服务面与持久化"]
    end
    subgraph SB["B 引擎维护者"]
        B1["术语表"] -->|"2"| B2["架构与数据流"]
        B2 -->|"3"| B3["核心引擎"]
        B3 -->|"4"| B4["任务执行管线"]
        B4 -->|"5"| B5["状态与恢复"]
        B5 -->|"6"| B6["错误模型"]
    end
    subgraph SC["C 扩展贡献者"]
        C1["核心引擎"] -->|"2"| C2["任务执行管线"]
        C2 -->|"3"| C3["状态与恢复"]
        C3 -->|"4"| C4["服务面与持久化"]
        C4 -->|"5"| C5["错误模型"]
    end
```

## 路径 A：业务集成者（编排任务）

目标：写出能跑的 task.xml，并从代码启动、恢复、取消任务实例。不读引擎内部类，只读契约与行为面。

1. **总览**：读 [overview.md](overview.md)「定位与形态：引擎实现，不是业务应用」与「能力边界：支持什么、不提供什么、什么在修」两节，判断你的场景是否属于任务编排而非普通服务调用。
2. **快速上手**：读 [quickstart.md](quickstart.md)「1. 构建：模块坐标与依赖」与「3. 最小用法示例」两节，跑通最小任务定义（task.xml + 构建调用）。后续步骤都在解释你在这个最小例子里碰到的对象。
3. **术语表**：读 [glossary.md](glossary.md)「核心术语」表的前四行（Task/TaskFlow/ITaskStep/TaskStepModel）与「边界组辨析」的 Task vs TaskFlow 一行。源码对照 `ITask.java:20-29`（`execute(taskRt, outputNames)` 是唯一执行入口）。
4. **任务执行管线**：读 [flows/task-execution.md](flows/task-execution.md) 两节——「DSL 面：task.xdef 支持的步骤类型」（17 种步骤类型选型表）与「运行时装配：ITaskFlowManager 与 TaskRuntime」。两个编程入口都在这里：fresh 执行 `newTaskRuntime`（`TaskFlowManagerImpl.java:97-106`），恢复执行 `getTaskRuntime(taskInstanceId, ...)`（`TaskFlowManagerImpl.java:120-134`，实例不存在抛 `ERR_TASK_UNKNOWN_TASK_INSTANCE`）。读步骤类型表前先扫一遍 `TaskConstants.java:31-48`（保留步骤名 `@main`/`@end`/`@exit`/`@suspend`）。
5. **状态与恢复**：读 [flows/state-and-recovery.md](flows/state-and-recovery.md)「挂起 → 快照：三次落盘点」。挂起语义由 `<suspend>` 步骤实现（`SuspendTaskStep.java:28-43`，首次必挂起、按 `resumeWhen` 放行）；要让挂起可跨进程恢复，`defaultSaveState` 必须为 true 且注入持久化 store，否则状态只是内存 no-op、resume 报 `ERR_TASK_UNKNOWN_TASK_INSTANCE`（`TaskFlowManagerImpl.java:95-117`）。
6. **错误模型**：读 [topics/error-model.md](topics/error-model.md)「消费方如何拿到错误」一节。你的代码只会遇到四种错误形态：nextOnError 分支的 `ErrorBean`、BizModel 面的 `ERR_TASK_CRUD_WRITE_DISABLED`、resume 终态任务的 `ERR_TASK_ALREADY_*` 合成异常、取消链的 `ERR_TASK_CANCELLED`。超时不抛 `ERR_TASK_STEP_TIMEOUT` 而走 reason 为 timeout 的取消异常（`TaskStepHelper.java:184-244`）。
7. **服务面与持久化**：读 [modules/task-service-dao.md](modules/task-service-dao.md)「服务面：CRUD 门面加一道写保护闸门」。四个实例/定义 BizModel 只暴露查询，写操作一律抛异常（`TaskErrors.java:184-185`）——任务实例行的业务写入只走引擎，不要试图经 CRUD 启动任务。

## 路径 B：引擎维护者（理解/修改执行引擎）

目标：建立"XML 声明 → 模型 → 装饰链 → 执行外壳 → 状态机"的完整心智模型，能定位任何一处运行时行为的责任类。顺序 = fan-in 降序阅读：先常量与产出协议的交汇页，再机制页。

1. **术语表**：读 [glossary.md](glossary.md)「边界组辨析」全部五行。五组划界（Task/TaskFlow、两级 Runtime、Model/ITaskStep、挂起/取消/失败、两级 State）各自给出源码行号，是后续所有页面的坐标系。
2. **架构与数据流**：读 [architecture.md](architecture.md)「十个子模块的分层与依赖方向」与「一次任务从定义到终态的数据流」两节，拿到子模块分层（core/dao/service/api/queue）与"加载→模型→图执行→状态落盘"的全景；「机制索引：从本页进入细节子页」一节给出后续各细节页的入口。
3. **核心引擎**：读 [modules/task-core.md](modules/task-core.md) 全页六节，按 fan-in 顺序重点消化三节——「步骤抽象：一个接口，两条继承轴」（`ITaskStep.java:19-50` 五个元数据读取器 + `execute`；具体步骤沿 `AbstractTaskStep.java:20-92`、横切包装沿 `DelegateTaskStep.java:11-51`）、「TaskStepReturn：一个对象表达四种出口」（哨兵按值判定而非对象身份，`TaskStepReturn.java:215-228`，这是包装层不丢挂起语义的关键）、「从模型到运行时：装饰链的固定组装顺序」（包装顺序即语义：timeout 在 retry 之外，`TaskStepEnhancer.java:107-166`；类型分发在 `TaskStepBuilder.java:90-180`）。「控制流步骤」一节先读 SequentialTaskStep 的 `bodyStepIndex` 推进（`SequentialTaskStep.java:46-110`），GraphTaskStep 留到下一步。
4. **任务执行管线**：读 [flows/task-execution.md](flows/task-execution.md) 全页七节，重点三节——「步骤执行层：TaskStepExecution 与 ITaskStepRuntime」（步骤生命周期真正的现场，`TaskStepExecution.java:206-288`；`ITaskStepRuntime.java:12-138` 是步骤内可用的全部上下文）、「图执行：GraphTaskStep」（`GraphTaskStep.java:179-246` 调度循环、`GraphTaskStep.java:363-368` 单一收敛判据 `completeGraphIfDrained`）、「结果汇聚与终态」（`TaskImpl.java:153-275` 三分支汇聚，first-terminal-wins 守卫）。
5. **状态与恢复**：读 [flows/state-and-recovery.md](flows/state-and-recovery.md)「TaskRuntimeImpl 如何写状态、读状态」与「重入恢复：resume 的完整时序」两节。写路径 `saveTaskState` → store（`TaskRuntimeImpl.java:241-243`）；恢复的层判定链（task 短路 → mainStep 装载 → 子步骤门控 → continuation-skip）在「重入恢复」节逐层展开，每层附源码行号。
6. **错误模型**：读 [topics/error-model.md](topics/error-model.md)「步骤失败驱动：driveStepFailure 与三语义分流」与「取消与超时：reason 编码链」两节。失败/挂起/取消三条出口收敛在 `TaskStepExecution.java:380-407` 的单一方法；取消 reason 必须编码进异常本身（`NopTaskCancelledException.java:11-17` 注释），因为 task seam 处的 cancel reason 不可靠。最后回看核心引擎「TaskConstants 与 TaskStepHelper」一节，确认状态码单源于生成的 `_NopTaskCoreConstants`（`TaskConstants.java:81-121`）——改状态码数值先改生成层。

维护者改 `GraphTaskStep` 前另有一条硬约束：`GraphTaskStep.java:352-361` 的 javadoc 记录了两条已证不变式（级联先于减计数、收敛只认 `completeGraphIfDrained`），四条出口路径（错误消费 `:277-291`、fail-fast `:292-297`、挂起 `:301-309`、error-handoff `:313-330`）都按此对齐，新分支必须遵守，否则会复现"瞬态 runningCount==0 误报 `ERR_TASK_GRAPH_NO_ACTIVE_STEP`"或图挂死。

## 路径 C：扩展贡献者（加步骤类型/改状态存储）

目标：在正确的层落改动，不碰生成物。步骤 1 是与 B 路径重叠的前置；步骤 2-5 按两类扩展目标组织。

1. **核心引擎（前置）**：读 [modules/task-core.md](modules/task-core.md)「步骤抽象：一个接口，两条继承轴」与「从模型到运行时：装饰链的固定组装顺序」两节。新增具体步骤沿 `AbstractTaskStep` 轴（产出经 `makeReturn` 规约，`AbstractTaskStep.java:85-91`）；新增横切能力沿 `DelegateTaskStep` 轴并在 `TaskStepEnhancer` 的包装序列里找到插入位（`TaskStepEnhancer.java:107-166`）。产出协议必读 `TaskStepReturn.java:36-46`（四个哨兵）。
2. **加步骤类型（含图语义）**：读 [flows/task-execution.md](flows/task-execution.md)「DSL 面：task.xdef 支持的步骤类型」一节，改动链固定为五点：
   - `TaskConstants.java:123-182` 注册 `STEP_TYPE_*` 字面量——它既是 xdef 合法取值也是分发键；
   - [task.xdef](../../nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef) 增加 DSL 节点（路径常量 `XDEF_PATH_TASK`，`TaskConstants.java:13`）；task.xdef 位于 nop-kernel schema 面，按仓库规约属 plan-first 区域；
   - 新增模型子类并实现 `getType()`（`TaskStepModel.java:19-28`，抽象方法在 `:28`）；生成基类 `_TaskStepModel` 不可手改；
   - `TaskStepBuilder.java:90-180` 的 `buildRawStep` switch 增加分支，未知类型现抛 `ERR_TASK_UNSUPPORTED_STEP_TYPE`（`:171-175`）；
   - 静态校验同步扩展：`TaskFlowAnalyzer.java:47-79` 校验 `next`/`waitSteps`/`waitErrorSteps` 引用，新跳转属性需过 `ERR_TASK_UNKNOWN_NEXT_STEP`/`ERR_TASK_UNKNOWN_WAIT_STEP`。

   若新步骤要参与图执行，先读「图执行：GraphTaskStep」一节再动手。`GraphStepNode` 构造已做防御性复制并把 success/error 交集单列为 waitCompleteSteps（`GraphTaskStep.java:79-97`，改这里会破坏二次构建语义）；`buildWaitFuture` 的 waitError 分支在依赖成功时也放行（跳过级联，`GraphTaskStep.java:151-162`）；`errorTriggerFired` 判定错误分支是否真触发（`:374-383`）。
3. **改状态存储**：读 [flows/state-and-recovery.md](flows/state-and-recovery.md)「状态模型：task / step 两级 envelope」与「存储实现对照：内存降级 vs DAO 落库」两节。实现点是 [ITaskStateStore.java](../../nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java) 的 9 个成员：`isSupportPersist()`（`:11`）、task 级 `newTaskState`/`loadTaskState`/`saveTaskState`（`:37-41`）、step 级 `newMainStepState`/`newStepState`/`loadStepState`/`saveStepState`（`:13,31-35`）、以及带默认实现的 `loadMainStepState`（`:27-29`，默认返回 null 是非持久化 store 的兼容锚点，持久化实现必须 override 它才能让 composite mainStep 断点续跑）。状态载荷契约见 `ITaskStepState.java:27-77`（stepPath+runId 定位、bodyStepIndex 控制流游标、stateBean continuation）。内存参照 `DefaultTaskStateStore.java:19-21,66-83`（恒空实现），消费端在 `TaskRuntimeImpl.java:218-238`（`loadMainStepState` 优先、未命中回退新建）。
4. **服务面与持久化（DB 实现参照）**：读 [modules/task-service-dao.md](modules/task-service-dao.md)「DaoTaskStateStore：把引擎状态写成两类表行」。自定义 store 通常照抄它的三个工程决定：`isSupportPersist()=true`（`DaoTaskStateStore.java:116-119`）、并发写按 `(taskInstanceId, stepPath)` 条带锁串行化（`:327-360`）、步骤状态走版本化单列 `stateBeanData` 并对超长降级（`:542-577`）。装配点在 `TaskFlowManagerImpl.java:95-117`：`saveState=true` 时要求注入持久化 store，缺失抛 `ERR_TASK_NO_PERSIST_STATE_STORE`（`:116`）；主树不自动注册 `DaoTaskStateStore`，需应用侧覆盖 `nopTaskFlowManager` 的 `taskStateStore` 属性（示范见 [test-reliability.beans.xml:16-17](../../nop-task/nop-task-ext/src/test/resources/_vfs/nop/task/test/beans/test-reliability.beans.xml)）。
5. **错误模型（新码注册）**：读 [topics/error-model.md](topics/error-model.md)「TaskErrors：33 条错误码的全表核查」。新错误码声明在 `TaskErrors.java:13-185`（`ErrorCode.define` 格式，消息模板 `{argName}` 占位）；抛出点统一经 `TaskStepHelper.newError` 附加 `taskName`/`stepPath`/`runId`/`stepType` 四个诊断参数（`TaskStepHelper.java:54-69`）；模块扩展错误放 `TaskExtErrors` 等独立接口，不与核心族混用。

## Sources

- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java:31-48](/nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java#L31-L48)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java:81-121](/nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java#L81-L121)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java:123-182](/nop-task/nop-task-core/src/main/java/io/nop/task/TaskConstants.java#L123-L182)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java:36-46](/nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java#L36-L46)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java:215-228](/nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java#L215-L228)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java:19-50](/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java#L19-L50)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java:12-138](/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java#L12-L138)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java:10-42](/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStateStore.java#L10-L42)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java:20-29](/nop-task/nop-task-core/src/main/java/io/nop/task/ITask.java#L20-L29)
- [nop-task/nop-task-core/src/main/java/io/nop/task/TaskErrors.java:13-185](/nop-task/nop-task-core/src/main/java/io/nop/task/TaskErrors.java#L13-L185)
- [nop-task/nop-task-core/src/main/java/io/nop/task/model/TaskStepModel.java:19-28](/nop-task/nop-task-core/src/main/java/io/nop/task/model/TaskStepModel.java#L19-L28)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/AbstractTaskStep.java:85-91](/nop-task/nop-task-core/src/main/java/io/nop/task/step/AbstractTaskStep.java#L85-L91)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/DelegateTaskStep.java:11-51](/nop-task/nop-task-core/src/main/java/io/nop/task/step/DelegateTaskStep.java#L11-L51)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:206-288](/nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java#L206-L288)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java:380-407](/nop-task/nop-task-core/src/main/java/io/nop/task/step/TaskStepExecution.java#L380-L407)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:79-97](/nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java#L79-L97)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:151-162](/nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java#L151-L162)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:179-246](/nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java#L179-L246)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:277-330](/nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java#L277-L330)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:352-368](/nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java#L352-L368)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java:374-383](/nop-task/nop-task-core/src/main/java/io/nop/task/step/GraphTaskStep.java#L374-L383)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/SequentialTaskStep.java:46-110](/nop-task/nop-task-core/src/main/java/io/nop/task/step/SequentialTaskStep.java#L46-L110)
- [nop-task/nop-task-core/src/main/java/io/nop/task/step/SuspendTaskStep.java:28-43](/nop-task/nop-task-core/src/main/java/io/nop/task/step/SuspendTaskStep.java#L28-L43)
- [nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepBuilder.java:90-180](/nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepBuilder.java#L90-L180)
- [nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java:107-166](/nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskStepEnhancer.java#L107-L166)
- [nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskFlowAnalyzer.java:47-79](/nop-task/nop-task-core/src/main/java/io/nop/task/builder/TaskFlowAnalyzer.java#L47-L79)
- [nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java:95-134](/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java#L95-L134)
- [nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java:218-243](/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskRuntimeImpl.java#L218-L243)
- [nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java:153-275](/nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskImpl.java#L153-L275)
- [nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:54-69](/nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java#L54-L69)
- [nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java:184-244](/nop-task/nop-task-core/src/main/java/io/nop/task/utils/TaskStepHelper.java#L184-L244)
- [nop-task/nop-task-core/src/main/java/io/nop/task/state/DefaultTaskStateStore.java:19-83](/nop-task/nop-task-core/src/main/java/io/nop/task/state/DefaultTaskStateStore.java#L19-L83)
- [nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepState.java:27-77](/nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepState.java#L27-L77)
- [nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:116-119](/nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java#L116-L119)
- [nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:327-360](/nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java#L327-L360)
- [nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java:542-577](/nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/DaoTaskStateStore.java#L542-L577)
- [nop-task/nop-task-ext/src/test/resources/_vfs/nop/task/test/beans/test-reliability.beans.xml:16-17](/nop-task/nop-task-ext/src/test/resources/_vfs/nop/task/test/beans/test-reliability.beans.xml#L16-L17)
- [nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef]()

---

## On this page

- fan-in 排序表
- 路径 A：业务集成者（编排任务）
- 路径 B：引擎维护者（理解/修改执行引擎）
- 路径 C：扩展贡献者（加步骤类型/改状态存储）
