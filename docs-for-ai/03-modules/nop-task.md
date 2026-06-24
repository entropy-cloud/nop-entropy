# nop-task — 任务/逻辑流引擎

## 功能概览

通用任务/逻辑流引擎，定义和执行多步骤任务流。核心能力是把一段复杂逻辑拆成可组合的步骤（step），用声明式的控制结构编排，并支持条件、重试、限流、并行、挂起、断点重启等编排语义。

- 任务定义与版本管理（`UNPUBLISHED` → `PUBLISHED` → `DEPRECATED` → `ARCHIVED`）
- 多步骤编排：sequential / selector / choose / if / loop / fork / parallel / graph（数据驱动 DAG）
- 子任务/子流程（`call-task`）
- 步骤库复用（`call-step`）
- 每步增强：`when` 条件跳过、`validator`、`retry`、`catch/finally`、`timeout`、`throttle`、`rate-limit`
- 断点重启（`saveState` + 持久化变量）
- 挂起等人工（`suspend` + `resume-when`）
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
| `nop-task-ext` | 扩展功能（事务/重试/限流/超时等 decorator） |

## 模型与加载

task flow 的权威定义文件是 `*.task.xml`，由 `nop/schema/task/task.xdef` 约束（XDSL），根节点 `<task>` 即 `TaskFlowModel`。

- **加载入口**：`ITaskFlowManager.getTask(name, version)` / `getTaskFlowModel(name, version)`，底层走 `ResourceComponentManager.loadComponentModel(path)`，从 VFS（`VirtualFileSystem`）按 `task:name/version` 路径解析。
- **缓存与动态更新**：模型由 `IResourceLoadingCache` 缓存。通过 `ResourceComponentManager.removeCachedModel(path)` 精确失效单个 task，或 `clearCache(modelType)` 批量失效。VFS 可挂载多种资源源（包括数据库资源源），因此"模型存库 + 失效缓存"即可在不重启、不重新部署的前提下动态更新流程逻辑。这是 task flow 作为可配置业务编排层的基础。

> 模型解析、XDSL 合并机制见 `../02-core-guides/xdef-and-xdsl.md`、`../02-core-guides/vfs-and-resource-resolution.md`。

## 事务模型

NopTaskFlow **默认复用外部环境中的事务**，不自带事务边界：

- task flow 作为 xbiz 的 action 直接定义（见下文"用法一"），或在某个 action（`@BizMutation`）内被调用时，整条流程跑在该 action 所开启的**同一个事务**内，零额外配置。
- 需要对某个步骤做更细粒度控制时（子流程独立事务、某步失败不回滚前置步骤），在对应 `<step>` 上配置 `<decorator bean="..." >`，使用 `nop-task-ext` 提供的 `TransactionTaskStepDecorator`，指定 `txnGroup` 与 `propagation`（`REQUIRES_NEW` 等）。
- 对应还有 `OrmSessionTaskStepDecorator`（管理 ORM 会话）等，都在 `nop-task-ext`。

**结论**：内部事务控制是可选项，默认即同事务。判断"这段流程要不要独立事务"时，先默认复用外层，仅在确有隔离需求时才按步声明。

## 两种用法

### 用法一：xbiz action 直接绑定 task flow（声明式，推荐）

xbiz schema 原生支持把一个 BizModel action 直接绑定到 task flow 定义，无需写 Java。`BizActionModel` 上有 `task:name` 与 `task:version` 属性（见 `nop/schema/biz/xbiz.xdef`，`xmlns:task="task"`）：

```xml
<mutation name="approveOrder" task:name="approve-order" task:version="1">
    <arg name="orderId" type="String" kind="FIELD"/>
    <return type="ApproveOrderResult"/>
</mutation>
```

绑定后，该 action 的执行体就是这个 task flow。事务由 action 的 `<txn>`（或 `@BizMutation` 默认）开启，task flow 内部所有 step 默认复用。适合"整个 action 就是多步骤业务流程"的场景——这是把流程编排从 Java 移到声明式模型、并获得 VFS 动态更新能力的主要方式。

### 用法二：在 BizModel/Processor Java 内调用 task flow（命令式）

当入口逻辑复杂、需要在 task flow 前后做 Java 编排（例如状态写回、乐观锁、业财回链）时，注入 `ITaskFlowManager` 显式调用：

```java
@BizModel("ErpSalOrder")
public class ErpSalOrderBizModel extends CrudBizModel<ErpSalOrder> {
    @Inject
    protected ITaskFlowManager taskFlowManager;

    @BizMutation
    public ApproveResult approveOrder(@RequestBean ApproveRequest req, IServiceContext ctx) {
        // 1. 事务由 @BizMutation 开启；状态真相源的写回留在 Java
        // 2. 内部编排交给 task flow（复用本事务）
        ITask task = taskFlowManager.getTask("sal-approve-order", 1);
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, ctx);
        TaskStepReturn ret = task.execute(taskRt);
        // 3. 状态权威写回 + post-commit 事件留在 Java
        return buildResult(ret);
    }
}
```

判断准则：

| 场景 | 用哪种 |
|------|--------|
| 整个 action 就是一段多步骤业务流程，前后无特殊 Java 逻辑 | 用法一（声明式绑定） |
| 流程前后需要状态写回、跨聚合一致性、post-commit 事件等必须在 Java 控制的真相源逻辑 | 用法二（Java 调用），把 task flow 作为内部编排段 |

## 步骤（step）怎么写

每个 step 是一个一等公民，声明 input/output，可选 `when/validator/retry/catch/timeout` 等。step 的执行体有几种形态：

| step 类型 | 说明 | 适用 |
|-----------|------|------|
| `<simple bean="...">` | 从 BeanContainer 取 `ITaskStep` bean 执行 | 复用的 Java step bean |
| `<invoke bean="..." method="...">` | 调用任意 bean 的指定方法 | 直接复用现有 service 方法，无需实现 `ITaskStep` |
| `<step>` / `<xpl>` | 内联 xpl 脚本 | 轻量逻辑、数据转换、简单判断 |
| `<script lang="...">` | 执行注册的脚本语言 | 多语言脚本 |
| `<call-task>` | 调用子 task flow | 子流程 |
| `<call-step>` | 调用步骤库中的步骤 | 跨流程复用单步 |

控制结构（作为容器 step）：`sequential`（顺序）、`selector`（行为树选择，取第一个非空返回）、`choose`（switch）、`if`、`loop`/`loop-n`、`fork`/`fork-n`（动态分片并行）、`parallel`（并行+聚合）、`graph`（数据驱动 DAG）、`suspend`（挂起）、`exit`/`end`。

Java step bean 的最小实现（实现 `ITaskStep`，或继承 `AbstractTaskStep`）：

```java
@BizBean
public class DeductStockStep extends AbstractTaskStep {
    @Inject
    protected IErpInvStockMoveBiz stockMoveBiz;

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        // 从 step input 取参数
        ErpSalDelivery delivery = (ErpSalDelivery) stepRt.getValue("delivery");
        // 通过 I*Biz 安全能力执行，不直接 dao()
        stockMoveBiz.deductForOutgoing(delivery, stepRt.getServiceContext());
        // 输出到 step output
        stepRt.setValue("moveOrderId", ...);
        return TaskStepReturn.EMPTY;
    }
}
```

step 的 input/output 在 XML 里声明，`persist="true"` 的变量随 `saveState` 持久化，支持断点重启。

## 与其他机制的关系

### 与 nop-wf

nop-task 与 nop-wf 是**两个相互独立的引擎**。nop-wf 并非基于 nop-task 构建：`nop-wf` 各模块的 pom 不依赖 `nop-task`，源码中也不引用 `io.nop.task`。两者分工：

- **nop-task**：通用业务流程编排。强调"做什么、什么顺序"——多步骤、数据流、条件/重试/并行/挂起/断点重启。流程拓扑可由实施/管理员通过 VFS 动态更新。事务默认跟随外部。
- **nop-wf**：审批/BPM 工作流。强调"谁来批、怎么批"——参与者分配、会签/加签/委托/转办/驳回、待办工作项、状态生命周期、定义版本管理。

典型配合：业务单据提交审核时由 nop-wf 驱动审批段（几级审、谁审），审批通过后触发的**业务执行流程**（扣库存、生成凭证、业财回链）由 nop-task 编排。

### 与 BizModel / Processor / 状态机

- **事务入口**：留在 BizModel 的 `@BizMutation` 方法。它开启事务、做状态真相源写回、发 post-commit 事件。
- **内部编排**：交给 task flow。task flow 内的 step 只调 `I*Biz` / `CrudBizModel` 安全能力，不直接 `dao()`，保证事务/权限/状态机管道不被绕过（与 `../02-core-guides/domain-logic-and-ddd.md` 一致）。
- **状态字段写回**：不应出现在 task flow 的 step 里。task flow 的 step 可以读状态、产生"建议目标状态"，但状态字段的最终赋值由 BizModel 通过统一的状态权威完成，保证状态真相源唯一、不被可变的流程定义破坏。
- **xbiz 与状态机**：`mutation` action 上的 `bo:triggerStateChange` 属性用于声明该动作触发状态机迁移，与 nop-fsm（有限状态机，见 `nop/schema/biz/state-machine.xdef`）配合。

### 与 nop-rule

- task flow 编排"步骤结构"（拓扑、顺序、分支、重试）。
- nop-rule 解决"决策点"（容差校验、信用额度、科目映射等），决策矩阵运行时可配置、带版本与执行日志。
- 两者正交：task flow 的某步可以调用 nop-rule 做决策；nop-rule 不编排步骤，task flow 不替代规则配置。

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-task/model/nop-task.orm.xml` |
| task 模型 schema | `nop/schema/task/task.xdef` |
| xbiz 绑定属性 | `nop/schema/biz/xbiz.xdef`（`BizActionModel.task:name/task:version`） |
| 管理器 | `nop-task/nop-task-core/src/main/java/io/nop/task/impl/TaskFlowManagerImpl.java` |
| step 接口 | `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java` |
| 事务 decorator | `nop-task/nop-task-ext/src/main/java/io/nop/task/ext/dao/TransactionTaskStepDecorator.java` |
| xlib 例子 | `nop-task/nop-task-core/src/main/resources/_vfs/nop/task/xlib/task.xlib` |

## 相关文档

- `../nop-wf.md` — 工作流引擎（审批段，与 task flow 独立）
- `../nop-rule.md` — 规则引擎（决策点）
- `../reusable-modules-overview.md` — 模块选择总览
- `../02-core-guides/xdef-and-xdsl.md` — XDSL 模型与 Delta 合并机制
- `../02-core-guides/vfs-and-resource-resolution.md` — VFS 与模型加载/缓存
- `../02-core-guides/domain-logic-and-ddd.md` — step 应调 I*Biz 而非 dao 的约束
- `../03-runbooks/implement-complex-business-flow.md` — 何时用 Processor、何时上状态机/Workflow/Task
