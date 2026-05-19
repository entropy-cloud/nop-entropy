# Juggle vs Nop-Task + Nop-Job 引擎深度对比分析

> Status: open
> Date: 2026-05-18
> Scope: Juggle (v1.6.0) 接口编排引擎 vs Nop-Task (轻量化任务引擎) + Nop-Job (分布式调度系统)
> Conclusion: TBD

## Context

Juggle 和 Nop-Task + Nop-Job 代表了"API 编排"和"通用任务编排"两条不同的技术路线。本分析从**源码实现**和**架构设计**两个层面展开深度对比，涵盖 30+ 维度。

关键约束：
- Juggle 的 `juggle-core` 引擎闭源（仅 JAR 发布），源码层面只能分析 console 层（~246 个 Java 文件）
- Nop-Task + Nop-Job 100% 开源，源码可完整分析（~350 个 Java 文件，含 xdef 模型定义）
- 本分析基于两个代码库的实际源码阅读，而非文档推测

---

## 第一部分：架构总览

### 1.1 Juggle — API 编排平台

```
juggle-console (Spring Boot + MyBatis + H2/MySQL) — 开源
├── interfaces/         — REST 控制器 (Knife4j/Swagger)
├── application/        — AO 层
├── domain/             — 领域层
│   ├── flow/definition/ — 流程定义
│   ├── flow/flowinfo/   — 执行信息
│   ├── flow/version/    — 版本管理 (灰度)
│   ├── object/          — 数据类型定义
│   ├── parameter/       — 输入/输出参数
│   └── suite/           — 套件市场
├── infrastructure/     — MyBatis

juggle-core (net.somta:juggle-core:1.2.0) — ⚠️ 闭源 JAR (117KB)
└── 通过工具分析得到以下架构:
    ├── dispatcher/
    │   ├── IDispatcher            — 流程分发接口
    │   ├── AbstractDispatcher     — 基类 (调用 FlowExecutor)
    │   ├── SyncDispatcher         — 同步执行
    │   └── AsyncDispatcher        — 异步执行 (WorkRunner 线程)
    ├── executor/                  — 核心执行引擎
    │   ├── IExecutor              — 执行器接口 (execute + getNext)
    │   ├── AbstractElementExecutor— 模板方法基类
    │   ├── ExecutorFactory        — 工厂 (ElementType → Executor)
    │   ├── FlowExecutor           — 流程引擎主驱动器
    │   ├── StartNodeExecutor      — 起始节点
    │   ├── EndNodeExecutor        — 结束节点
    │   ├── MethodNodeExecutor     — HTTP/Dubbo 调用
    │   ├── ConditionNodeExecutor  — 条件判断
    │   ├── CodeNodeExecutor       — 脚本执行
    │   ├── AssignNodeExecutor     — 变量赋值
    │   └── MysqlNodeExecutor      — 数据库操作
    ├── model/                    — 数据模型
    │   ├── Flow                  — 流程定义 (含 inputParams/outputParams/variables)
    │   ├── FlowElement           — 元素基类 (key/name/elementType)
    │   ├── FlowNode              — 节点 (extends FlowElement, 含 incomings/outgoings 边列表)
    │   ├── FlowResult            — 执行结果 (flowInstanceId/status/data)
    │   ├── Variable / Property   — 变量/属性类型定义
    │   ├── Input/OutputParameter — 输入/输出参数
    │   ├── FillStruct            — 参数填充规则 (source→target 映射)
    │   ├── Method                — API 调用描述 (url/requestType/fillRules)
    │   └── DataSource / DataType — 数据源/类型
    ├── result/                   — 结果管理
    │   ├── IFlowResultManager    — 结果管理器接口
    │   ├── MemoryFlowResultManager
    │   └── RedisFlowResultManager
    ├── variable/                 — 变量管理
    │   ├── AbstractVariableManager
    │   └── MemoryVariableManager
    ├── exception/                 — 异常框架
    │   └── JuggleCoreException       — 自定义 RuntimeException，包装 CoreErrorEnum(errCode+errMsg)
    ├── event/                     — 事件发布（基底）
    │   └── EventPublisher            — 事件发布器（EventNode 模型存在但无对应 executor，功能未完成）
    ├── validator/                 — 节点校验器（模板方法模式，与 executor 平行结构）
    │   ├── IValidator                — 校验器接口
    │   ├── AbstractElementValidator  — 模板方法基类
    │   ├── NodeValidator             — 通用节点结构校验
    │   ├── StartNodeValidator
    │   ├── EndNodeValidator
    │   ├── MethodNodeValidator
    │   └── ConditionNodeValidator   
    ├── http/                     — HTTP 客户端
    │   ├── IHttpClient
    │   ├── AbstractHttpClient
    │   ├── FormHttpClient
    │   ├── JsonHttpClient
    │   └── HttpClientFactory
    ├── expression/               — 条件表达式引擎 (自定义递归下降解析器 + Aviator 函数扩展)
    │   ├── ExpressionManager
    │   └── condition/            — 自定义 DSL 解析器 (parser/a-j) + 条件函数 (date/list/object/string/time)
    └── enums/
        ├── ElementTypeEnum       — START/END/METHOD/CONDITION/CODE/ASSIGN/MYSQL (仅 7 种，无 PARALLEL/EVENT)
        ├── FlowStatusEnum        — INIT/RUNNING/FINISH/ABORT
        ├── DataTypeEnum          — String/Integer/Double/Boolean/Date/Time/List/Object
        ├── FieldSourceEnum       — VARIABLE/CONSTANT/HEADER/INPUT_PARAM/OUTPUT_PARAM
        ├── AssignTypeEnum        — CONSTANT/VARIABLE
        ├── RequestTypeEnum       — GET/POST/PUT/DELETE
        ├── RequestContentTypeEnum— APPLICATION_JSON / APPLICATION_FORM_URLENCODED
        ├── ParameterPositionEnum — PATH/QUERY/BODY
        ├── FlowResultManagerTypeEnum — MEMORY/REDIS
        └── VariablePrefixEnum    — INPUT_VARIABLE_PREFIX / OUTPUT_VARIABLE_PREFIX

juggle-spring-boot-starter — 嵌入 SDK
juggle-sdk-java / -go / -nodejs / -python
console-ui — Vue 3 + TypeScript 拖拽式设计器
```

**核心发现**：`juggle-core` JAR 经过代码混淆处理（所有类名替换为单字母如 `a`、`b`、`c`，字段名也类似），通过分析其公开接口和调用链可还原核心引擎架构。已知的包结构如上所示。编译目标为 Java 8。

**数据模型**（通过 MyBatis Mapper + 核心 JAR 分析）：
- `FlowDefinition` / `FlowVersion` — 流程定义与版本管理
- `Object` / `Property` — 数据结构定义
- `Parameter` — 步骤参数
- `Api` — API 定义
- `Suite` / `Template` — 套件市场模板

---

### 1.2 Nop-Task — 通用任务引擎

```
nop-message                — 消息服务层 (IMessageService SPI)
├── nop-message-core       — 本地内存消息队列 (LocalMessageService)
├── nop-message-pulsar     — Apache Pulsar 集成
├── nop-message-debezium   — CDC 变更数据捕获
└── 其他实现: Redis/Lettuce, MQTT, SysDao, RPC, TDEngine

nop-task                   — 任务编排引擎
├── nop-task-api           — ITask, ITaskStep, ITaskRuntime (SPI)
├── nop-task-core          — 核心引擎实现
│   ├── model/_gen/        — XDEF 代码生成模型 (21 个生成类)
│   ├── step/              — 20+ 步骤实现类
│   ├── builder/           — 流程构建器
│   └── impl/              — TaskFlowManagerImpl
├── nop-task-dao           — 状态持久化 (可选)
├── nop-task-service       — GraphQL BizModel
├── nop-task-web           — AMIS 管理页面
├── nop-task-meta          — XMeta / i18n
├── nop-task-ext           — 扩展（事务装饰器等）
├── nop-task-codegen       — 代码生成启动器
└── nop-task-app           — 可独立运行的应用
```

**XDEF 元模型** (`/nop/schema/task/task.xdef`, 302 行)：
- 定义 `TaskFlowModel` 作为根模型，`xdef:name="TaskFlowModel"`
- 定义 `TaskExecutableModel` — 所有步骤的基类（含 `disabled`、`allowFailure`、`timeout`、`retry`、`throttle`、`rate-limit` 等）
- 定义 `TaskStepModel` — 扩展基类（含 `concurrent`、`saveState`、`next`、`nextOnError`、`waitSteps` 等）
- 定义 `TaskStepsModel` — 步骤容器，支持 20+ 步骤类型的 `xdef:ref`

**关键接口设计**：

| 接口 | 职责 | 位置 |
|------|------|------|
| `ITask extends IActionBaseModel` | 任务模型，可执行 | `nop-task-core/ITask.java` |
| `ITaskStep extends IActionBaseModel` | 步骤模型，多输入/多输出 | `nop-task-core/ITaskStep.java` |
| `ITaskStepRuntime extends IEvalContext` | 运行时上下文（scope 驱动） | `nop-task-core/ITaskStepRuntime.java` |
| `ITaskState` | 状态存储接口 | `nop-task-core/ITaskState.java` |
| `TaskStepReturn` | 核心返回类型（continuation 风格） | `nop-task-core/TaskStepReturn.java` |

**平台消息服务**（`nop-message` 模块，与 nop-task 互补）：
- `IMessageService` 接口聚合发送 (`IMessageSender`) 和订阅 (`IMessageSubscriber`) 能力
- `sendAsync(topic, message, options)` 返回 `CompletionStage<Void>` — 统一异步消息发送
- `subscribe(topic, listener, options)` 返回 `IMessageSubscription` — 支持取消/暂停/恢复
- `IMessageConsumer.onMessage()` 返回值语义：`null`（确认处理）、`CompletionStage`（异步处理）、`ConsumeLater`（延迟重试）
- 6 种实现：Local（内存）、Pulsar、SysDao（数据库）、Mqtt、Lettuce（Redis）、TDEngine
- 可用于触发 nop-task 执行，形成事件驱动的任务编排

**DTO/结果传递层**：
- `StepResultBean` — 单个步骤执行结果
- `MultiStepResultBean` — 并行步骤结果汇总
- `StepStateBean` — 支持中断恢复的状态快照

---

### 1.3 Nop-Job — 分布式调度系统

（见前文分析，4 个子模块，新 schedule/fire/task 三层模型）

---

## 第二部分：核心执行模型对比

### 2.1 执行范式

| 维度 | Juggle | Nop-Task |
|------|--------|----------|
| **执行模型** | 责任链式：IExecutor.execute() + IExecutor.getNext() | 虚拟机式：TaskStepReturn + runId + StepState |
| **引擎驱动** | FlowExecutor 驱动链：executor→execute→getNext→next.execute | TaskFlowManager 驱动：async/await TaskStepReturn |
| **返回类型** | 同步 (SyncDispatcher) / 异步 (AsyncDispatcher + WorkRunner 线程) | `CompletionStage<TaskStepReturn>` 统一异步 |
| **continuation** | ❌ 不支持中断恢复 | ✅ `runId` 断点续执行 |
| **状态管理** | Memory / Redis (可切换) | ITaskStateStore 可插拔接口 (Memory/DB/Custom) |
| **scope 传递** | FlowRuntimeContext 持有变量管理器 | IEvalScope 变量栈 + parentScope 链 |

**Juggle 执行引擎详解**：

核心接口 `IExecutor`（混淆名 `h`）:
```java
public interface IExecutor {
    void execute(FlowRuntimeContext context);  // 执行当前节点
    IExecutor getNext(FlowRuntimeContext context);  // 确定并返回下一个执行器
}
```

每个节点类型对应一个 `IExecutor` 实现，通过 `ExecutorFactory.create(FlowElement element)` 根据 `ElementTypeEnum` 生成。执行流程：

```
FlowExecutor.execute(context):
  1. 获取初始 executor = k(context)  // StartNodeExecutor
  2. loop:
     executor.execute(context)         // 执行业务逻辑
     executor = executor.getNext(context) // 确定下一个节点
     if executor == null: break        // EndNode 返回 null
```

这种设计类似**责任链模式**（Chain of Responsibility），每个 executor 决定下一个 executor。`getNext` 的实现方式取决于节点类型：
- **StartNode** → 根据 `outgoings` 找到下游节点
- **EndNode** → 返回 null（终止）
- **ConditionNode** → 检表达式匹配到哪个分支的 `outgoing`
- **MethodNode/CodeNode** → 顺序指向 `outgoings` 中的第一个下游节点

**⚠️ `FlowExecutor` 实现分析揭示的重要问题**：
```java
// 反编译伪代码
public Map<String, Object> execute(FlowRuntimeContext context) {
    try {
        init(context);             // f()
        runLoop(context);          // h()
        context.setStatus(FINISH);
        return collectResults(context);  // l()
    } catch (Exception e) {
        logger.error("flow error: {}", e.getMessage());
        e.printStackTrace();       // ← 问题1: 生产代码中的 printStackTrace
        context.setStatus(ABORT);
        return collectResults(context);
    } finally {
        context.setStatus(FINISH);
        return collectResults(context);  // ← 问题2: finally 块 return 覆盖 try/catch 返回值
    }
}
```
- `printStackTrace()` 出现在 FlowExecutor 和 AbstractDispatcher 的 catch 块中
- `finally` 块无条件 `return collectResults(context)`，覆盖 try 块中正常返回的 `Map<String, Object>`。在 Java 中，`finally` 中的 `return` 永远胜出，意味着 catch 块的 `setStatus(ABORT)` 也会被覆盖为 `FINISH`
- 状态命名非标准：`FINISH`（而非 `SUCCESS`）、`ABORT`（而非 `FAILED`）

**核心状态对象 `FlowRuntimeContext`**（混淆名 `a`）:
```java
public class FlowRuntimeContext {
    String flowInstanceId;
    String flowKey;
    Map<String, FlowElement> flowElementMap;  // 所有节点元素
    FlowElement currentNode;                   // 当前执行节点
    FlowElement nextNode;                      // 下一个执行节点
    List<OutputParameter> outputParameters;
    AbstractVariableManager variableManager;
    IFlowResultManager flowResultManager;
    IDataSource dataSourceManager;
    FlowStatusEnum flowStatus;
}
```

**异步执行机制**：
- `SyncDispatcher` → 直接同步调用 `FlowExecutor.execute(context)`
- `AsyncDispatcher` → 通过 `WorkRunnerImpl`（混淆名 `c`，实现 `IWorkRunner` 接口 `b`）提交执行
- `WorkRunnerImpl` 继承 `Thread` 作为调度循环线程，内部分配任务给 `ThreadPoolExecutor`（`BasicThreadFactory` 命名、`LinkedBlockingQueue` 任务队列）—— 并非每个请求一个原始 `Thread`
- `IWorkRunner` 接口只有一个方法：`void execute(Runnable)`，提供最小异步执行抽象
- `IDispatcher` 接口统一两种模式

**变量传递机制**：
- 所有节点共享 `FlowRuntimeContext` 中的 `variableManager`
- `FillStruct` 定义了变量映射规则：`source→target`，支持 `VARIABLE/CONSTANT/HEADER/INPUT_PARAM/OUTPUT_PARAM` 五种源类型
- `AssignNode` 通过 `assignRules` (`List<FillStruct>`) 执行批量赋值
- `MethodNode` 调用前后根据 `headerFillRules`/`inputFillRules`/`outputFillRules` 三组 FillStruct 自动填充 HTTP 头、输入参数和提取返回值

**条件表达式引擎**（自定义 DSL，非通用表达式语言）：
- 条件系统使用 **DNF 模型**（`List<List<ConditionExpression>>` = OR-of-ANDs），每个 `ConditionItem` 包含多组 AND 条件组合
- 条件类型分 `DEFAULT`（表达式构建器）和 `CUSTOM`（原始脚本）两种
- 自定义递归下降解析器（parser/a-j 共 10 个类，每个一种语法元素）将 `ConditionExpression` 转换为可执行表达式
- 内置 5 类条件函数：`date`(Eq/Ge/Gt/Le/Lt)、`time`(Eq/Ge/Gt/Le/Lt)、`string`(Empty)、`list`(Empty)、`object`(Empty)
- 操作符 10 种：EQUAL/NOT_EQUAL/EMPTY/NOT_EMPTY/CONTAINS/NOT_CONTAINS/GREATER_THAN/GREATER_THAN_OR_EQUAL/LESS_THAN/LESS_THAN_OR_EQUAL
- `aviator_functions.config` 文件存在于 JAR 中，说明 Aviator 作为补充函数引擎而非主表达式引擎

**Nop-Task 的 Continuation 模型详解**：

`TaskStepReturn` 是 nop-task 的核心执行原语，包含 `nextStepName` 和 `outputs Map` 两个字段：

```java
public final class TaskStepReturn {
    public static final TaskStepReturn CONTINUE = new TaskStepReturn(null, null);
    public static final TaskStepReturn SUSPEND = new TaskStepReturn(STEP_NAME_SUSPEND, null);
    
    public static TaskStepReturn RETURN(String nextStepName, Map<String, Object> outputs);
    public static TaskStepReturn ASYNC(CompletionStage<TaskStepReturn> future);
    public static TaskStepReturn END(Object result);
    public static TaskStepReturn EXIT(Map<String, Object> outputs);
}
```

关键设计点：
1. **`CONTINUE`** = 继续执行下一个兄弟步骤
2. **`RETURN("stepName", outputs)`** = 动态跳转到指定步骤
3. **`SUSPEND`** = 暂停等待外部恢复
4. **`ASYNC`** = 包装异步 `CompletionStage`，引擎自动 await
5. **`END`** = 终止整个任务并返回结果
6. **`EXIT`** = 退出当前 scope 向上返回

每个步骤通过 `syncIfDone()` 在同步模式下直接获取结果，异步模式下由 `CompletionStage` 驱动回掉：

```java
// SequentialTaskStep 的关键循环
TaskStepReturn stepResult = step.executeWithParentRt(stepRt).syncIfDone();
if (stepResult.isSuspend()) return stepResult;
if (stepResult.isDone()) {
    if (stepResult.isEnd()) {
        stepRt.setBodyStepIndex(steps.size()); // 标记完成
        return stepResult;
    }
    index = getNextIndex(index, stepResult, stepRt);
    stepRt.setBodyStepIndex(index);
}
```

### 2.2 步骤类型完整对比

**Juggle 步骤类型**（通过 `ElementTypeEnum` 确认，共 5 种用户步骤 + 2 种基础设施步骤）：

| 类型 | 功能 | 实现 Executor | 源码可见性 |
|------|------|---------------|-----------|
| START | 流程入口（基础设施） | `StartNodeExecutor` (j) | ⚠️ 混淆 |
| END | 流程出口（基础设施） | `EndNodeExecutor` (e) | ⚠️ 混淆 |
| METHOD | HTTP/Dubbo/WebService 调用 | `MethodNodeExecutor` (i) | ⚠️ 混淆 |
| CONDITION | 条件判断分支 | `ConditionNodeExecutor` (d) | ⚠️ 混淆 |
| CODE | 脚本执行（Groovy/JavaScript） | `CodeNodeExecutor` (c) | ⚠️ 混淆 |
| ASSIGN | 变量赋值 | `AssignNodeExecutor` (b) | ⚠️ 混淆 |
| MYSQL | 数据库查询/修改 | `MysqlNodeExecutor` (data/a) | ⚠️ 混淆 |

**注**：
- 所有 executor 类名被 ProGuard 混淆为单字母，但通过继承 `AbstractElementExecutor`（`a`）可明确识别
- **`ElementTypeEnum` 仅有 7 种类型**，无 PARALLEL 或 EVENT 枚举值
- `EventNode` 作为模型类存在（继承 `FlowElement` 而非 `FlowNode`，且为空节点），但 **无对应 executor**，是一个未完成的功能占位
- `CodeNode` 的接口显示仅支持 `groovy` 和 `javascript` 两种语言（使用 `NashornSandbox` 执行 JS，`GroovyShell`/编译方式执行 Groovy），不支持文档中提到的 Python/Java
- Java 8 编译（major version 52），因此 Nashorn 可用（JDK 15+ 废弃，JDK 21 移除）

**Nop-Task 步骤类型**（共 23 种，通过 XDef 模型定义的 XML/JSON/YAML DSL）：

| 类型标签 | 功能 | 实现类 | 源码可见 |
|---------|------|-------|---------|
| `step`/`xpl` | Xpl 模板脚本 | `XplTaskStep` | ✅ 开源 |
| `script` | 多语言脚本 (lang 属性) | `ScriptTaskStep` | ✅ 开源 |
| `simple` | Bean 容器中获取 | `SimpleTaskStep` | ✅ 开源 |
| `sequential` | 顺序执行子步骤 | `SequentialTaskStep` | ✅ 开源 |
| `parallel` | 并行执行子步骤 | `ParallelTaskStep` | ✅ 开源 |
| `graph` | DAG 图模式执行 | `GraphTaskStep` | ✅ 开源 |
| `choose` | Switch-Case 分支 | `ChooseTaskStep` | ✅ 开源 |
| `if` | If-Then-Else 条件 | `IfTaskStep` | ✅ 开源 |
| `selector` | Behavior Tree 序列 | `SelectorTaskStep` | ✅ 开源 |
| `loop` | For-Each 循环 | `LoopTaskStep` | ✅ 开源 |
| `loop-n` | 数字范围循环 | `LoopNTaskStep` | ✅ 开源 |
| `fork` | 动态并行分叉 | `ForkTaskStep` | ✅ 开源 |
| `fork-n` | 指定数量并行分叉 | `ForkNTaskStep` | ✅ 开源 |
| `invoke` | Bean 方法调用 | `InvokeTaskStep` | ✅ 开源 |
| `invoke-static` | 静态方法调用 | `InvokeStaticTaskStep` | ✅ 开源 |
| `call-task` | 调用子任务 | `CallTaskStep` | ✅ 开源 |
| `call-step` | 调用步骤库 | `CallStepTaskStep` | ✅ 开源 |
| `suspend` | 挂起等待恢复 | `SuspendTaskStep` | ✅ 开源 |
| `sleep` | 延时阻塞 | `SleepTaskStep` | ✅ 开源 |
| `delay` | 延迟异步执行 | `DelayTaskStep` | ✅ 开源 |
| `exit` | 退出当前 scope | `ExitTaskStep` | ✅ 开源 |
| `end` | 终止整个流程 | `EndTaskStep` | ✅ 开源 |
| `custom` | 自定义扩展类型 | `CustomTaskStep` | ✅ 开源 |

**装饰器类型**（nop-task 特有，通过 XML 配置自动包裹）：

| 装饰器 | 实现类 | 功能 |
|--------|-------|------|
| `<retry>` | `RetryTaskStepWrapper` | 失败重试（指数退避） |
| `<timeout>` | `TimeoutTaskStepWrapper` | 超时中止 |
| `<throttle>` | `ThrottleTaskStepWrapper` | 并发度控制 |
| `<rate-limit>` | `RateLimitTaskStepWrapper` | 速率限制 |
| `<try>/<catch>/<finally>` | `TryTaskStepWrapper` | 异常捕获处理 |
| `<validator>` | `ValidatorTaskStepWrapper` | 输入验证 |
| `<sync>` | `SyncTaskStepWrapper` | 同步执行 |
| `<runOnContext>` | `RunOnContextTaskStepWrapper` | 上下文线程隔离 |

**结论**：Nop-Task 步骤类型丰富度远超 Juggle（23:7），且支持装饰器链的组合模式，这在流程引擎领域是更先进的设计。

---

### 2.3 图模式执行引擎对比

**Juggle（闭源）**：
- 所有流程本质都是 DAG
- 通过拓扑排序确定执行顺序
- 每个节点定义输入/输出连接关系

**Nop-Task `GraphTaskStep`（开源）**：

```java
public class GraphTaskStep extends AbstractTaskStep {
    public static class GraphStepNode {
        private Set<String> waitSuccessSteps;  // 成功后才执行本节点
        private Set<String> waitErrorSteps;    // 失败后才执行本节点
        private Set<String> waitCompleteSteps; // 完成后执行本节点
        private ITaskStepExecution step;
        
        private boolean enter;  // 入口节点
        private boolean exit;   // 出口节点
    }
}
```

设计精妙之处：
1. **三组依赖条件**：`waitSuccessSteps`、`waitErrorSteps`、`waitCompleteSteps`，比简单的 DAG 更灵活。一个节点可以等待某些步骤成功 + 某些步骤失败 + 某些步骤完成（不管成功还是失败）
2. **并发执行**：满足前置条件的节点自动并行执行，通过 `ConcurrentHashMap` + `AtomicInteger` 计数
3. **多入口/多出口**：`enterSteps` 和 `exitSteps` 属性支持图的多入口多出口模式
4. **内置状态恢复**：图执行不排他，与 nop-task 的状态管理完全集成

DSL 示例（`graph-01/v1.task.xml`）：

```xml
<graph name="test" enterSteps="enter1,enter2" exitSteps="exit">
    <step name="enter1">...实现...</step>
    <step name="enter2">...实现...</step>
    <step name="process">
        <input name="a"><source>STEP_RESULTS.enter1.outputs.RESULT</source></input>
        <input name="b"><source>STEP_RESULTS.enter2.outputs.RESULT</source></input>
    </step>
    <step name="exit">...</step>
</graph>
```

这里的 `STEP_RESULTS.enter1.outputs.RESULT` 引用语法是对图模式数据流的原生支持。

---

## 第三部分：DSL 与模型设计对比

### 3.1 流程定义方式

| 维度 | Juggle | Nop-Task |
|------|--------|----------|
| **定义格式** | JSON（通过可视化设计器生成） | XML/JSON/YAML（程序友好） |
| **Schema 验证** | 无正式 schema | ✅ XDef Schema（严格类型检查） |
| **代码生成** | ❌ | ✅ XDef → JavaBean 代码生成 |
| **版本管理** | ✅ 多版本 + 灰度 | ✅ version 属性 |
| **XDSL 继承** | ❌ | ✅ `x:extends`/`x:gen-extends` |
| **离线编辑** | ❌ 依赖设计器 | ✅ 可直接手写 XML |
| **模型可读性** | 机读 JSON（难手写） | 设计为人类可编辑 XML |

### 3.2 Nop-Task XDef 的元模型深度

Nop-Task 的 XDef 定义（`task.xdef`，302 行）是一个完整的元模型系统。关键设计：

**基类继承链**：
```
TaskExecutableModel (所有步骤的基类)
  ├── disabled, allowFailure, timeout
  ├── catch/finally/retry (声明式错误处理)
  ├── throttle/rate-limit (声明式限流)
  ├── input/output 定义
  ├── decorator SPI
  └── flags/tagSet 条件执行
    └── TaskStepModel (执行步骤的扩展)
        ├── concurrent/sync/runOnContext
        ├── saveState (中断恢复)
        ├── next/nextOnError (跳转)
        └── waitSteps/waitErrorSteps (图模式依赖)
          └── 23 个具体步骤类型
```

**每个步骤类型自动生成**：
```
task.xdef → TaskStepsModel (xdef:name="TaskStepsModel")
  ├── SequentialTaskStepModel
  ├── ParallelTaskStepModel
  ├── LoopTaskStepModel
  └── ...总共 23+ 个 _gen 模型类
```

**输入/输出系统的类型安全**：
```xml
<input name="!var-name" type="generic-type" mandatory="!boolean=false" 
       persist="!boolean=true" fromTaskScope="!boolean=false">
    <description>string</description>
    <schema xdef:ref="../schema/schema.xdef"/>  <!-- 支持嵌套类型定义 -->
    <source>xpl</source>  <!-- 默认值表达式 -->
</input>
```

`!var-name` 表示 name 是唯一标识属性，`!boolean` 是非可选属性，`t-expr` 是表达式字符串。这些都是 XDef 的类型系统语法。

---

## 第四部分：源码实现质量对比

### 4.1 总体统计

| 指标 | Juggle | Nop-Task | Nop-Job |
|------|--------|----------|---------|
| 总 Java 源文件数 | ~246 | ~213 | ~137 |
| 可见核心引擎源码 | ❌ (juggle-core 闭源) | ✅ 全开源 | ✅ 全开源 |
| 测试文件数 | ~10（可见范围） | 31+ | 19+ |
| 测试覆盖率 | 不可评估 | 中等（核心路径有覆盖） | 中等（新模块覆盖好） |
| 使用的 ORM | MyBatis 手写 Mapper | Nop ORM (代码生成) | Nop ORM (代码生成) |
| 依赖框架 | Spring Boot 2.7 | Nop IoC (可脱离 Spring) | Nop IoC |
| 每秒并发 | ∅ | 使用 `CompletionStage` + 线程池 | 分区扫描 + 心跳检测 |

### 4.2 Nop-Task 的架构质量

**优点**：
1. **接口粒度合理** — `ITask` / `ITaskStep` / `ITaskRuntime` / `ITaskStepRuntime` / `ITaskState` 各司其职
2. **模板方法模式** — `AbstractTaskStep` 提供了 `executeWithParentRt()` 的标准流程
3. **装饰器链** — Retry→RateLimit→Throttle→Validator→Sync→RunOnContext→Business Logic 可组合
4. **统一异步模型** — `TaskStepReturn` 既可同步也可异步，`CompletionStage` 贯穿全链路
5. **状态可插拔** — `ITaskStateStore` 接口支持 Memory / Database / Redis 等不同实现
6. **Scope 隔离** — 每个步骤有独立的 `IEvalScope`，通过 `parentScope` 链安全访问上级变量

**问题**：
1. **API 演进不稳定** — 部分接口/模型标记 `@Deprecated`（如 `XplTaskStep` 被标记但仍在用，`custom` 类型被 `step` 取代）

### 4.3 Juggle 的质量评估（juggle-core 库分析 + console 层源码）

**评价标准**：以下分析基于 juggle-core 库公开的接口和实现类分析，以及 console 层源码阅读。

#### 合理的设计（非一无是处）

1. **引擎接口抽象简洁** — `IExecutor` 只有两个方法 `execute()` + `getNext()`，责任链模式设计直观，理解成本低
2. **上下文聚合合理** — `FlowRuntimeContext` 一站式持有节点映射、变量管理器、结果管理器、数据源，虽重但方便
3. **参数映射系统完整** — `FillStruct` 的 source→target 映射覆盖了 5 种来源（VARIABLE/CONSTANT/HEADER/INPUT_PARAM/OUTPUT_PARAM），能满足基本的数据映射需求
4. **结果管理器可插拔** — `IFlowResultManager` 有 Memory 和 Redis 两种实现，通过 `FlowResultManagerTypeEnum` 切换
5. **异常编码规范** — `CoreErrorEnum` (int errCode + String errMsg) + `JuggleCoreException extends RuntimeException` 的模式与主流做法一致，console 的 DDD 分层（AO/Repository/VO）也比较清晰
6. **验证器层结构合理** — 7 个验证器类，`IValidator` → `AbstractElementValidator` → 具体验证器，与 executor 层平行对称

---

#### 严重问题（生产级不可接受）

**1. 核心流程引擎有逻辑错误**

`FlowExecutor.execute()` 的实现存在经典的 `finally` 陷阱：

```java
Map<String, Object> execute(FlowRuntimeContext context) {
    try {
        init(context);
        runLoop(context);  
        context.setStatus(FINISH);
        return collectResults(context);
    } catch (Exception e) {
        logger.error("flow error: {}", e.getMessage());
        e.printStackTrace();            // ← problem 1
        context.setStatus(ABORT);
        return collectResults(context);
    } finally {
        context.setStatus(FINISH);
        return collectResults(context);  // ← problem 2: return in finally
    }
}
```

- **`finally` 块有 `return` 语句**。Java 语义中 `finally` 块的 `return` 永远胜出，意味着 catch 块的 `setStatus(ABORT)` 和返回的异常结果会被**完全覆盖**
- 无论执行成功还是失败，调用方永远收到 `FINISH` 状态和正常结果——**异常路径的结果正确性完全丧失**
- 该错误同样存在于 `AbstractDispatcher` 的错误处理路径中

**2. `printStackTrace()` 出现在 3 处生产代码路径**

| 位置 | 方式 | 影响 |
|------|------|------|
| `FlowExecutor.execute()` catch 块 | `Exception.printStackTrace()` | 异常堆栈写入 stderr，与 SLF4J 日志双倍输出 |
| `AbstractDispatcher.doDispatcher()` catch 块 | `JuggleCoreException.printStackTrace()` | 同上 |
| `AbstractDispatcher.doDispatcher()` catch 块 | `JsonProcessingException.printStackTrace()` | Jackson 解析失败时也走 stderr |

这意味着**生产环境中核心执行路径的错误既有 SLF4J 日志又有 stderr 输出**，日志聚合系统（ELK/Loki）无法统一收集，排障困难。

**3. 引擎代码混淆导致无法运维**

juggle-core JAR 经过 ProGuard 混淆，所有类名、方法名、字段名均被替换为单字母（`a`, `b`, `c`, `aA`, `aB` 等）：
- 运行时异常堆栈完全不可读：`at net.somta.juggle.core.executor.a.d(Unknown Source)`
- 无法通过反射或 bytecode 增强工具（如 ByteBuddy、CGLIB）进行扩展
- JMX、Arthas、Skywalking 等 APM 工具的诊断信息毫无意义
- 无法通过类名定位问题模块，即使有堆栈也无法确定是哪个 executor 抛出
- 对比：Nop 全框架开源，类名即文档，堆栈可读，可任意增强

**4. 线程安全完全未考虑**

`MemoryVariableManager`（唯一的变量管理器实现）使用 `HashMap` 作为变量存储，未使用 `ConcurrentHashMap` 或任何同步机制：
- 多线程执行流程时变量读写存在竞态条件
- `WorkRunnerImpl` 使用线程池并发执行，但变量管理器本身无保护
- `FlowRuntimeContext` 中的 `currentNode`/`nextNode` 字段在并发场景下可被多个 executor 同时修改

**5. 硬编码中文错误消息**

引擎核心代码中多处硬编码中文信息：

| 位置 | 内容 | 问题 |
|------|------|------|
| `EndNodeExecutor` | `"结束节点执行器完毕"`, `"执行中。。。"` | debug 日志含「。。。」中式省略号 |
| `ConditionNodeExecutor` | `"判断节点执行器完成"`, `"执行前。。。"` | 同上 |
| `CodeNodeExecutor` | `"不支持"`, `"执行脚本错误: {}"` | 异常消息硬编码中文，无法国际化 |

非中文环境的部署中，这些日志和异常消息不可读，也无法通过 i18n 机制切换语言。

---

#### 架构与设计缺陷

**6. 脚本引擎仅支持 Groovy 和 JavaScript，且 Nashorn 已废弃**

- `CodeNode` 实际只支持两种脚本语言：Groovy（通过 `GroovyShell`/`GroovyScriptCompiler`）和 JavaScript（通过 `NashornSandbox`）
- 所谓的 Python/Java 支持在核心库中**不存在**
- `NashornSandbox` 基于 JDK 内置 Nashorn 引擎——**JDK 15 标记为废弃，JDK 21 正式移除**。本库编译为 Java 8 (major version 52)，在高版本 JDK 上将直接不可用
- `GroovyScriptCompiler` 使用编译方式（非 eval），性能尚可，但增加了类加载泄漏风险

**7. 异步执行架构可疑**

`WorkRunnerImpl` 继承 `Thread` 作为调度循环，内部维护 `LinkedBlockingQueue` + `ThreadPoolExecutor`：
- 为什么不直接暴露线程池接口而要额外套一层 `Thread` 调度？增加复杂度而无明显收益
- `IWorkRunner` 接口仅有一个 `void execute(Runnable)` 方法，缺乏任何生命周期管理（优雅关闭、超时、拒绝策略）
- 没有提供自定义线程池的 SPI，用户无法注入自己的线程池配置

**8. 扩展性几乎为零**

- 无法自定义步骤类型：步骤类型到 executor 的映射在 `ExecutorFactory` 的 `switch` 中固化，无 SPI 扩展点
- 没有装饰器链：无法为步骤包裹 retry/timeout/throttle 等横切关注点，所有横切逻辑需要硬编码到每个 executor 中
- 没有步骤拦截器：缺少 `beforeExecute`/`afterExecute` 的回调机制
- 条件表达式的自定义函数只能通过 `aviator_functions.config` 文件注册 Aviator 函数，不提供 Java SPI

**9. 硬件编码的数字和不可配置的行为**

- 多处硬编码数值（如线程池大小、队列长度）在构造函数中直接传参，不可配置
- `AbstractDispatcher` 在构造函数中直接 `new ObjectMapper()` 并配置，无法注入自定义 ObjectMapper
- 无 `application.yml` 或 `application.properties` 的自动配置支持

**10. 未完成的功能占位**

- `EventNode` 模型类存在但继承 `FlowElement`（而非 `FlowNode`），内容为空，没有对应的 executor——是未实现的功能骨架
- `ElementTypeEnum` 中无 `EVENT` 类型映射，说明事件监听功能根本没有集成到流程引擎中

---

#### 代码质量细节

**11. 过度宽泛的异常捕获**

```java
// FlowExecutor — catch 任何 Exception
try { ... } catch (Exception e) { ... }

// CodeNodeExecutor — 包装为 RuntimeException 抛到上层
try { ... } catch (Exception e) { throw new RuntimeException(e); }
```

所有节点执行异常被统一包装为 `RuntimeException`，调用方无法区分业务异常、系统异常、脚本异常。

**12. 多余的生产代码输出**

`MemoryVariableManager` 中有 `System.out.println()` 残留，说明存在调试代码未清理的问题。

**13. 类设计嵌套深，测试性差**

- `ConditionNode$ConditionExpression` 是 `ConditionNode` 的私有内部类，无法独立实例化和测试
- `ConditionNode$ConditionItem` 同样为内部类
- 所有 model 类缺失 `equals`/`hashCode`/`toString` 实现，不利于调试和集合操作

**14. 状态枚举命名奇特**

- `FlowStatusEnum` 的值是 `INIT/RUNNING/FINISH/ABORT`，而非业界标准的 `PENDING/RUNNING/SUCCESS/FAILED`
- `FINISH`（而非 `SUCCESS`）意味着正常完成和异常完成不易区分
- 结合上文的 `finally return` bug，调用方永远看到 `FINISH`，完全丢失了正/异常语义

### 4.4 代码对比示例

**Nop-Task 并行执行源码**（`ParallelTaskStep.java`）：

```java
public TaskStepReturn execute(ITaskStepRuntime stepRt) {
    List<CompletionStage<TaskStepReturn>> promises = new ArrayList<>();
    
    Supplier<CompletionStage<Void>> action = () -> {
        for (int i = 0; n = steps.size(); i < n; i++) {
            ITaskStepExecution step = steps.get(i);
            TaskStepReturn stepResult = step.executeWithParentRt(stepRt);
            promises.add(stepResult.getReturnPromise());
        }
        return AsyncHelper.waitAsync(promises, stepJoinType);
    };
    
    CompletionStage<Void> promise = TaskStepHelper.withCancellable(action, stepRt, autoCancelUnfinished);
    
    return TaskStepReturn.ASYNC(promise.thenApply(v -> {
        MultiStepResultBean result = new MultiStepResultBean(steps.size(), promises, stepJoinType);
        return aggregator != null 
            ? TaskStepReturn.RETURN_RESULT(aggregator.call2(null, result, stepRt, stepRt.getEvalScope()))
            : TaskStepReturn.RETURN_RESULT(result);
    }));
}
```

设计要点：
- 所有子步骤同时启动（`for` 循环中每个 `step.executeWithParentRt()`）
- 通过 `AsyncHelper.waitAsync(promises, joinType)` 等待结果
- 支持可选的 `aggregator` 函数对并行结果做汇总
- 通过 `withCancellable` 支持取消

---

## 第五部分：集成与扩展性对比

### 5.1 Nop 平台的生态优势

Nop-Task 的扩展点（全部开源，可在代码中直接引用）：

| 扩展点 | 接口/方式 | 场景 |
|--------|---------|------|
| 自定义步骤 | 实现 `ITaskStep` + DSL 中 `simple` 引用 | 业务专用步骤 |
| 步骤装饰器 | `ITaskStepEnhancer` SPI | 跨步骤横切关注点 |
| 任务状态存储 | `ITaskStateStore` | 不同持久化策略 |
| Bean Container | `ITaskBeanContainerFactory` | IoC 集成 |
| 脚本语言 | `IScriptCompiler` 注册 | XLang/Groovy/JS 等 |
| Xpl 模板 | `task.xlib` / `task-gen.xlib` | 模板内嵌任务调用 |
| 自定义类型 | `xdef` 扩展 DSL | 新步骤类型元模型 |
| 步骤库 | `.task-lib.xml` | 可复用步骤定义 |
| **消息服务** | `IMessageService` 扩展 `IMessageSender` + `IMessageSubscriber` | 异步发送 + Topic 订阅，支持 Pulsar/Redis/MQTT/Local 等多种实现 |

### 5.2 Juggle 的生态优势

| 特性 | 详情 |
|------|------|
| **协议支持** | HTTP/Dubbo/WebService — 内置调用节点 |
| **脚本多语言** | Groovy/JavaScript — 流程内嵌执行（⚠️ 核心 JAR 确认仅此两种，console 层文档声称的 Python/Java 未在引擎中实现） |
| **套件市场** | 几十个预构建系统的官方套件 |
| **数据源** | MySQL/达梦/TiDB/OceanBase/Doris |
| **SDK** | Java/Go/Node.js/Python — 客户端调用 |
| **MQ 监听** | console 层可直接作为流程触发器（引擎核心层 `EventNode` 未完成，功能在 console 层实现） |
| **可视化设计** | Vue 3 设计器，拖拽式流程设计 |

### 5.3 触发方式对比

| 触发方式 | Juggle | Nop-Task | Nop-Job |
|---------|--------|----------|---------|
| API 调用 | ✅ REST API | ✅ GraphQL/REST | ✅ RPC API |
| 定时 | ✅ 内置定时流程 | ❌（由 Nop-Job 补充） | ✅ Cron/Period/Once |
| MQ 监听 | ✅ console 层监听功能 | ✅ `IMessageService.subscribe()` + `IMessageConsumer` 触发 | ❌ |
| 事件驱动 | ✅ 内置监听 | ✅ `IMessageService` 全异步消息通道（支持 Pulsar/Redis/MQTT/本地） | ❌ |
| 嵌入式调用 | ✅ Java SDK | ✅ ITask.execute() | ✅ IJobScheduler |
| 同步调用 | ✅ 同步流程 | ✅ syncIfDone() | ❌ 纯异步 |
| 异步调用 | ✅ 异步流程 | ✅ CompletionStage 异步 | ✅ 异步调度 |

---

## 第六部分：关键差异总结

### 6.1 Nop-Task 的设计优势

1. **执行范式先进**：Continuation 风格的 `TaskStepReturn` + `runId` 机制，原生支持步骤级中断恢复
2. **类型系统完整**：XDef 元模型 → JavaBean 代码生成 → 编译期类型安全
3. **声明式装饰器**：retry/timeout/throttle/rate-limit/try-catch-finally 通过 XML 属性声明，非侵入式
4. **异步统一**：所有步骤通过 `CompletionStage<TaskStepReturn>` 统一异步化，非 blocking
5. **图模式灵活性**：`waitSteps`/`waitErrorSteps`/`waitCompleteSteps` 三组依赖条件，超越普通 DAG
6. **可嵌入性**：作为库直接嵌入应用，无独立部署要求
7. **全开源可审计**：每一行引擎代码都可审查

### 6.2 Juggle 的设计优势

1. **协议内置支持**：HTTP/Dubbo/WebService 的开箱即用，对微服务 API 编排场景极有价值
2. **可视化设计器**：Vue 3 的拖拽式流程设计，降低非技术用户门槛
3. **套件市场**：生态优势，几十个预建系统集成
4. **多语言脚本**：Groovy/JavaScript 流程内嵌执行（⚠️ Java/Python 未在核心引擎中实现）
5. **触发方式丰富**：API + 定时 + MQ 监听三种集成方式（MQ 监听功能在 console 层实现，核心层 `EventNode` 未完成）
6. **BFF 场景专精**：定位准确，对 BFF 场景问题解决直接
7. **多 SDK**：Java/Go/Node.js/Python 多语言客户端

### 6.3 根本性架构差异

| 维度 | Juggle | Nop-Task |
|------|--------|----------|
| **定位** | 独立部署的平台产品 | 可嵌入的库/框架 |
| **用户** | 非技术/低代码用户 | 开发者/技术用户 |
| **扩展方式** | 套件市场/预置节点 | 代码 SPI / DSL 扩展 |
| **状态管理** | Redis/Memory | 可插拔 StateStore |
| **异步模型** | 同步/异步两套 API | 统一 CompletionStage |
| **DSL 设计** | 可视化为唯一入口 | 文本 DSL + 代码优先 |
| **引擎源码** | 闭源 | 开源 |
| **开发语言** | Java 8, Spring Boot 2.7 | Java 17+, Nop IoC |
| **步骤数** | 7 种（5 用户 + 2 基础设施） | 23 种 + 8 种装饰器 |

---

## 第七部分：场景化适用建议

### 场景 A：微服务 API 编排 / BFF 层

**推荐：Juggle**

Juggle 对 HTTP/Dubbo/WebService 协议的原生支持使其在此场景直接可用。Nop-Task 需要额外的 `invoke` 步骤封装 HTTP 调用，需额外开发。

但注意：如果团队已有 Nop 平台基础设施，通过 `invoke` + `xpl` 也可以实现同样的编排能力，且拥有更强大的流程控制（重试、限流、超时等）。

### 场景 B：复杂业务工作流 / ETL 数据处理

**推荐：Nop-Task**

Nop-Task 的 23 种步骤类型 + 装饰器链在复杂流程控制上远胜 Juggle。循环、条件、图模式、并行聚合、中断恢复、事务装饰器等能力在数据处理场景中至关重要。

### 场景 C：定时批处理任务 / 分布式调度

**推荐：Nop-Task + Nop-Job 组合**

Juggle 的定时流程较简单，缺乏分布式调度能力。Nop-Job 的分区调度、阻塞策略、心跳检测、超时回收等是企业级批处理的标准能力。

### 场景 D：嵌入式流程引擎

**推荐：Nop-Task**

Nop-Task 作为库可直接嵌入应用，无任何部署要求。Juggle 需要独立部署服务器+数据库。

### 场景 E：需要可视化设计的产品

**推荐：Juggle**

如果产品面向非技术用户，Juggle 的可视化设计器是明显优势。Nop 平台目前缺少同等水平的可视化流程设计器。

---

## 第八部分：未决问题

- [x] Juggle 的 `juggle-core` 引擎已通过 Maven Central 下载并分析其公开 API，确认执行模型为 IExecutor 责任链模式。引擎设计简洁但有若干质量问题（printStackTrace、finally 覆盖返回值、硬编码中文、CodeNode 仅支持 Groovy/JS、EventNode 未完成、混淆过于激进）
- [ ] 如果 Nop 平台未来提供可视化流程设计器，将补齐最后一个关键差距
- [ ] Nop-Task 的 API 演进仍在进行中（存在 @Deprecated 标记），需跟踪后续版本稳定性
- [ ] Nop-Task 缺少内置协议调用节点（HTTP/ Dubbo 等），社区需要构建对应的自定义步骤
- [ ] Nop-Task 的测试覆盖度可进一步提升，特别是异步路径和中断恢复路径
- [ ] Nop 的 `IMessageService` + `nop-task` 组合可以提供事件驱动编排能力，但缺少将消息订阅声明式绑定到任务流程的原生 DSL 支持——当前需要通过 `IMessageConsumer` 编程方式触发

---

## 附录：关键源码引用

### Nop-Task XDef Schema
```
nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef  (302 lines)
```

### Nop-Task 核心引擎文件
```
nop-task/nop-task-core/src/main/java/io/nop/task/
├── ITask.java                    — 任务接口定义
├── ITaskStep.java                — 步骤接口定义
├── ITaskStepRuntime.java         — 运行时上下文
├── TaskStepReturn.java           — 核心返回类型
├── step/
│   ├── AbstractTaskStep.java     — 模板方法基类
│   ├── SequentialTaskStep.java   — 顺序执行
│   ├── ParallelTaskStep.java     — 并行执行
│   ├── GraphTaskStep.java        — 图模式执行
│   ├── LoopTaskStep.java         — 循环执行
│   ├── SelectorTaskStep.java     — 选择器执行
│   ├── InvokeTaskStep.java       — Bean 方法调用
│   ├── SuspendTaskStep.java      — 暂停/恢复
│   ├── SleepTaskStep.java        — 延时
│   ├── RetryTaskStepWrapper.java — 重试装饰器
│   ├── TimeoutTaskStepWrapper.java — 超时装饰器
│   ├── TryTaskStepWrapper.java   — Try-Catch 装饰器
│   ├── RateLimitTaskStepWrapper.java — 速率限制
│   ├── ThrottleTaskStepWrapper.java  — 并发限制
│   └── ValidatorTaskStepWrapper.java — 输入验证
├── builder/
│   ├── TaskFlowBuilder.java      — 流程构建器
│   ├── TaskStepBuilder.java      — 步骤构建器
│   ├── TaskStepEnhancer.java     — 装饰器应用
│   └── GraphStepAnalyzer.java    — 图模式分析器
└── impl/
    └── TaskFlowManagerImpl.java  — 流程管理器
```

### Nop-Task 测试用例
```
nop-task/nop-task-core/src/test/resources/_vfs/nop/task/test/
├── sequential-01/v1.task.xml     — 顺序执行
├── parallel-01/v1.task.xml       — 并行执行
├── graph-01/v1.task.xml          — 图模式
├── choose-01/v1.task.xml         — 条件分支
├── loop-01/v1.task.xml           — 循环
├── loop-n-01/v1.task.xml         — 数字循环
├── fork-01/v1.task.xml           — 并行分叉
├── fork-n-01/v1.task.xml         — 数量分叉
├── sleep-01/v1.task.xml          — 延时
├── throttle-01/v1.task.xml       — 限流
├── call-task-01/v1.task.xml      — 子任务调用
├── scope/v1.task.xml             — 作用域
└── transform/v1.task.xml         — 转换
```

### Nop 消息服务接口
```
nop-kernel/nop-api-core/src/main/java/io/nop/api/core/message/
├── IMessageService.java          — 消息服务接口 (extends IMessageSender + IMessageSubscriber)
├── IMessageSender.java           — sendAsync(topic, message, options) → CompletionStage<Void>
├── IMessageSubscriber.java       — subscribe(topic, listener, options) → IMessageSubscription
├── IMessageConsumer.java         — onMessage(topic, message, context) → Object (null=ack, CompletionStage=async)
├── IMessageSubscription.java     — cancel/suspend/resume 生命周期
├── IMessageConsumeContext.java   — 消费上下文 (extends IMessageSender 支持事务内回复)
├── MessageSendOptions.java       — delay / sendTimeout / cancelToken
├── MessageSubscribeOptions.java  — concurrency / batch / transactional / subscriptionType
├── DelegateMessageService.java   — 组合模式委派实现
└── TopicMessage.java             — topic + message 消息体

nop-message/ (6 种实现)
├── nop-message-core/LocalMessageService.java       — 本地内存 (ConcurrentHashMap + CopyOnWriteArrayList)
├── nop-message-pulsar/PulsarMessageService.java    — Apache Pulsar
├── nop-message-debezium/DebeziumMessageSource.java — CDC 数据变更捕获
├── nop-sys-dao/SysDaoMessageService.java           — 数据库持久化
├── nop-vertx-mqtt/MqttServerMessageService.java    — MQTT 协议
└── nop-nosql-lettuce/LettuceMessageService.java    — Redis 发布订阅
```

### Juggle 数据结构
```
somta/Juggle (开源部分)
console/src/main/java/net/somta/juggle/console/domain/
├── flow/definition/FlowDefinitionAO.java
├── flow/flowinfo/FlowInfoAO.java
├── flow/version/FlowVersionAO.java
├── object/ObjectAO.java              — 对象类型定义
├── parameter/ParameterEntity.java    — 输入/输出参数
├── suite/api/ApiAO.java              — API 定义
└── system/datasource/DataSourceAO.java

核心引擎: net.somta:juggle-core (闭源 JAR，不可分析)
```
