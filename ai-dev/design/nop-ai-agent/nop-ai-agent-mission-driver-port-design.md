# Mission-Driver 机制分析 & 基于 nop-ai-agent 的 Java 移植设计

> Status: draft
> Date: 2026-08-01
> Scope: attractor-guided-engineering-template/tools/mission-driver 的完整机制拆解；nop-ai-agent 通用框架的定位澄清；将 mission-driver 移植到 Java 的架构设计
> Conclusion: mission-driver 是"声明式状态机引擎 + 磁盘即状态 + 外部 agent 调用"的三位一体架构；nop-ai-agent 已具备 call-agent（continue/fork 两种会话模式）和丰富的 plan 静态模型。**流程引擎直接复用 nop-task**（ChooseTaskStep 条件路由/GraphTaskStep DAG/RetryTaskStepWrapper/TimeoutTaskStepWrapper 已完整覆盖 mission-driver 的状态机能力，且 task-flow-integration 已在 nop-ai-agent 落地）。**所有磁盘读写（plan/audit 文件的读、写、扫描）必须通过 `IToolFileSystem` 接口**（`nop-ai-toolkit` 的 `io.nop.ai.toolkit.fs.IToolFileSystem`，已有 `LocalToolFileSystem` 实现），禁止直接使用 `java.nio.file`——统一安全边界（isPathAllowed）、可插拔实现、可审计。移植增量收敛为：AgentTaskStep（自定义 TaskStep）、DiskStateScanner、marker 提取器、plan 校验器四块，全部基于 IToolFileSystem 读写。

---

## 1. nop-ai-agent 的定位：通用框架，非 mission 驱动器

### 1.1 与 AGE 模板的配合关系

nop-ai-agent 是一个**通用 AI agent 运行时框架**，提供：
- **引擎**：ReActAgentExecutor（推理-行动循环）
- **内置工具**：call-agent（子 agent 调用）、todo（计划跟踪）、team-* 系列（多 agent 协调）、memory-* 系列（记忆读写）
- **中间件/hook**：12 个 AgentLifecyclePoint + middleware 洋葱链
- **安全**：ContentOrigin + IContentTrustEvaluator + ICircuitBreaker
- **可靠性**：checkpoint（append-only INSERT）+ restore

但 nop-ai-agent **本身不包含 mission/plan 驱动逻辑**——它不决定"先做什么后做什么"、"plan 何时算完成"、"审计如何循环"。这些是 **AGE（Attractor-Guided Engineering）模板** 的职责：

```
┌─────────────────────────────────────────────────────────────┐
│  AGE 模板（attractor-guided-engineering-template）          │
│  ├── tools/mission-driver/    ← 声明式流程引擎（Node.js）   │
│  │   ├── src/engine.js        ← FlowEngine（94KB）          │
│  │   ├── src/runner.js        ← opencode 调用              │
│  │   ├── src/executor.js      ← spawn + 看门狗             │
│  │   └── flows/*.json         ← 声明式状态机定义            │
│  └── 文档骨架（plan guide、roadmap 模板等）                 │
│                                                             │
│  项目层（如 nop-entropy）                                    │
│  ├── missions/*.json         ← mission 静态配置             │
│  ├── ai-dev/plans/*.md       ← plan 文件（磁盘即状态）      │
│  ├── ai-dev/backlog/*roadmap ← 路线图（工作项索引）         │
│  ├── ai-dev/tools/mission-driver.sh ← 启动器（转发给模板） │
│  └── _tmp/<runId>/           ← 运行时输出                   │
│                                                             │
│  nop-ai-agent（通用框架）                                    │
│  └── 被 mission-driver 通过 opencode 间接调用               │
│      （当前：mission-driver → opencode → [LLM + 工具]）     │
│      （目标：mission-driver → nop-ai-agent → [LLM + 工具]）│
└─────────────────────────────────────────────────────────────┘
```

### 1.2 关键澄清

| 组件 | 职责 | 位置 |
|------|------|------|
| nop-ai-agent | 通用 agent 运行时（引擎+工具+安全+可靠性） | Java 库 |
| AGE 模板 | 工程方法论 + mission-driver 引擎 + 文档骨架 | 外部模板仓库 |
| mission JSON | mission 静态配置（命令/路径/模型） | 项目内 `missions/` |
| plan markdown | 执行计划（`[x]/[ ]` + `> Plan Status:`） | 项目内 `ai-dev/plans/` |
| plan guide | plan 编写与闭合规范 | 项目内 docs |
| FlowEngine | 声明式状态机驱动器 | AGE 模板内 |

**当前流程**：mission-driver.sh → AGE 模板的 Node.js 引擎 → spawn opencode → LLM 执行

**目标流程**：nop-ai-agent 内嵌 FlowEngine → call-agent 工具调用子 agent → LLM 执行

---

## 2. Mission-Driver 完整机制拆解

### 2.1 架构总览

mission-driver 是一个**声明式流程引擎驱动的 AI 自动开发循环**，核心三件：

1. **FlowEngine**（`engine.js`，94KB）：通用声明式状态机，驱动 step 循环、marker 提取、retry、subflow 递归、run-state 持久化。
2. **磁盘即状态**：所有跨 step/跨 run 的上下文都落在文件系统——plan markdown 的 `[x]/[ ]` 勾选是断点，`> Plan Status: draft|active|completed` 是 forEach 分发依据。
3. **外部 agent 调用**：每个 step 通过 `runner.js` 拼装 `opencode run` 命令，`executor.js` spawn 子进程执行。

### 2.2 声明式状态机（flow JSON）

三个 flow JSON 定义全部控制流：

**mission-driver.json**（主循环）：
```
CHECK → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS → (回 REVIEW_PLANS)
                                           ↘ DEEP_AUDIT → REVIEW_PLANS
```

**plan-execution.json**（子流程，对每个 active plan 执行）：
```
EXECUTE → CLOSURE_SCRIPT_CHECK → (fail) CLOSURE_AUDIT →(issues→retry EXECUTE带反馈)
                               → BUILD_VERIFY → done
```

**deep-audit-loop.json**（审计子流程）：
```
CHECK_OPEN_AUDITS → MULTI_AUDIT → OPEN_AUDIT → SCAN_NEW_RESULTS
```

每个 step 的声明式定义：
```json
{
  "name": "EXECUTE",
  "type": "agent",                    // agent | script | subflow
  "prompt": "templates/execute.md",   // prompt 模板（含 {{...}} 变量）
  "when": "activePlans().length > 0", // 条件守卫（空则跳过）
  "transitions": {
    "pass":      { "goto": "CLOSURE_SCRIPT_CHECK" },
    "issues":    { "retry": "EXECUTE", "maxRetries": 3,
                   "append": { "extract": "REMAINING", "template": "..." } },
    "incomplete":{ "retry": "EXECUTE", "maxRetries": 5 }
  }
}
```

**转换模型**：每个 step 的 `transitions` 以 **marker** 为 key。引擎从 AI 输出中提取 `<AI_STEP_RESULT>value</AI_STEP_RESULT>` 标签作为 marker，按 marker 决定下一步（goto/retry/done）。

### 2.3 磁盘即状态（核心设计智慧）

mission-driver 的恢复**不依赖会话历史回放**，而是**直接扫描磁盘产物状态**：

| 磁盘产物 | 状态语义 | 恢复用途 |
|----------|----------|----------|
| `> Plan Status: draft` | 计划待审阅 | REVIEW_PLANS 提升 |
| `> Plan Status: active` | 计划待执行 | EXEC_PLANS forEach 分发 |
| `> Plan Status: completed` | 计划已完成 | 跳过 |
| `[ ]` 未勾选项 | 该步骤未完成 | EXECUTE 断点恢复 |
| `[x]` 已勾选项 | 该步骤已完成 | EXECUTE 跳过 |
| `## Closure` 段 | 有非占位证据 | CLOSURE 检查通过 |
| `> Audit Status: open` | 审计未闭合 | DEEP_AUDIT 处理 |

**恢复流程**：重启后 → `activePlans()` 扫描所有 `active` plan → 对每个 plan 从第一个 `[ ]` 项继续执行。天然幂等、抗崩溃——不需要记住"上次执行到哪了"，磁盘自己告诉你。

### 2.4 三层上下文转交

| 层次 | 机制 | 适用场景 |
|------|------|----------|
| **磁盘共享** | plan/audit/roadmap 文件是所有 agent 共享的"内存" | 跨 step、跨 run 的持久上下文 |
| **append buffer** | 上一步发现的 `<REMAINING>` 内容追加到下一步 prompt | 紧密反馈（审计→修复） |
| **session 续跑** | `--session ses_xxx` 延续同一 opencode 会话 | marker 修正/parse 模型续跑 |

**关键洞察**：主 step 每次以**新 session** 启动——因为真正的"记忆"已落在磁盘上，无需依赖会话历史。session 续跑仅用于修正/parse 等辅助场景。

### 2.5 Marker 契约与多级提取

AI 输出必须含 `<AI_STEP_RESULT>value</AI_STEP_RESULT>`。引擎四级提取：
1. **strict 正则**：精确匹配标签
2. **tolerant**：容错空格/大小写/代码块包裹
3. **fuzzy**：tag 名拼写错误扫描（如 `Ai_STEP_RESULT`）
4. **LLM parse fallback**：让子进程在同一 session 只输出合法 marker

配合 `markerAliases`（`ok→pass`, `none→created`, `approved→all_complete`）做归一化。

### 2.6 Subflow + forEach 并行

`forEach: "activePlans()"` 表达式函数（纯磁盘扫描）返回 active plan 文件路径列表。引擎对每个 plan **递归创建新 FlowEngine 实例**（状态隔离但共享 delegates），顺序执行（同进程内，非真并发），全部完成聚合为 `all_complete`。

### 2.7 执行器隔离

每次 agent 调用独立 `spawn` 子进程 + 5min 心跳看门狗 + 60min 无输出超时 `killGroup()`（SIGTERM→10s→SIGKILL）。stdout 直接写日志文件 fd，stderr 独立管道滚动捕获。

---

## 3. nop-ai-agent 已有能力映射

### 3.1 call-agent 工具（已有，核心）

`CallAgentExecutor`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java`）已实现三种会话模式：

| 模式 | sessionId | 语义 | 对标 mission-driver |
|------|-----------|------|---------------------|
| **新建** | 空 | 创建全新子会话 | 主 step 默认（新 session） |
| **延续** | 非空 | 延续已有会话 | session 续跑（marker 修正） |
| **fork** | — | fork 父会话创建子会话 | 上下文继承（inheritContext） |

```xml
<!-- call-agent DSL（已有） -->
<call-agent id="!int" explanation="!string" timeoutMs="int"
            agentId="!string" sessionId="string" skills="csv-set" inheritContext="boolean">
    <input>string</input>
    <input-files>
        <input-file path="!full-path" description="string"/>
    </input-files>
</call-agent>
```

**关键**：`agentId="self"` + `inheritContext=true` 就是"上下文完全转交给自己的副本"；`agentId="other"` + `sessionId=null` 就是"创建一个全新的子 agent"。这已经是 mission-driver 的核心调用原语。

### 3.2 内置工具清单（已有）

| 工具 | 类 | 语义 |
|------|-----|------|
| call-agent | CallAgentExecutor | 子 agent 调用（continue/fork/new） |
| team-task-create | TeamTaskCreateExecutor | 创建任务 |
| team-task-update | TeamTaskUpdateExecutor | 更新任务状态 |
| team-status | TeamStatusExecutor | 团队状态查询 |
| team-send-message | TeamSendMessageExecutor | agent 间消息 |
| send-message | SendMessageExecutor | 消息发送 |
| team-execute-flow | TeamExecuteFlowExecutor | **执行流程**（已有！） |
| search-memory | SearchMemoryExecutor | 记忆检索 |
| write-memory | WriteMemoryExecutor | 记忆写入 |
| read-memory | ReadMemoryExecutor | 记忆读取 |
| set-active-tags | SetActiveTagsExecutor | 标签管理 |

### 3.3 plan 静态模型（已有，无运行时执行器）

plan/model/ 下已有 21 个静态模型类（AgentPlan/AgentPlanPhase/AgentPlanTaskModel/AgentPlanClosure/...），**结构丰富但没有运行时执行器驱动状态转换**——这正是 mission-driver 要补的。

### 3.4 checkpoint（已有，append-only INSERT）

`DBCheckpointManager` 的 append-only INSERT 多行 + 按 watermark 检索，比 mission-driver 的 run-state.json 整文件重写更健壮。

### 3.5 hook 12 生命周期点（已有）

AgentLifecyclePoint 的 12 点可对接 mission-driver 的 step 级事件。

---

## 4. 移植设计：Mission-Driver → nop-ai-agent Java 实现

> **核心决策：流程引擎直接复用 nop-task，不新建 FlowEngine。**
>
> nop-task（`nop-task-core`）已提供完整、成熟、被 nop-ai-coder / nop-batch-dsl / nop-cli-core 验证的 DAG 任务引擎：
> - **ChooseTaskStep**（decider + caseSteps）→ 完全对应 mission-driver 的 marker 提取 + transitions 路由
> - **GraphTaskStep + GraphStepAnalyzer**（拓扑 + 环检测）→ 完全对应 forEach subflow + DAG 依赖序
> - **RetryTaskStepWrapper** / **TimeoutTaskStepWrapper** / **LoopTaskStep** → 对应 retry/maxRetries/看门狗
> - **task.xdef DSL**（graphMode/enterSteps/exitSteps/defaultSaveState/restartable/flags/timeout）→ 对应 flow JSON + run-state
> - `ITaskFlowManager.newTaskRuntime(task, saveState)` + `ITask.execute(taskRt).syncGetOutputs()` → 同步执行入口
>
> 且 **nop-ai-agent 已落地 nop-task DAG 集成**（`nop-ai-agent-task-flow-integration.md`，Status: landed）：`TeamTask.blockedBy` 已映射为 nop-task 图 `waitSteps`，编排器已消费 `ITaskFlowManager`。本次移植是同一模式的**mission 级扩展**，不是新引擎。

### 4.1 整体架构

```
┌────────────────────────────────────────────────────────────────┐
│  Mission Driver（基于 nop-task 的任务定义，XML DSL）            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  task.xml（对标 mission-driver 的 flow JSON）             │  │
│  │  <task graphMode="true" enterSteps="CHECK"                │  │
│  │         defaultSaveState="true" restartable="true">       │  │
│  │    <steps>                                                │  │
│  │      <choose name="REVIEW_PLANS" ...>   ← 条件路由         │  │
│  │      <graph name="EXEC_PLANS" ...>      ← DAG subflow      │  │
│  │      <retry name="EXECUTE" ...>         ← retry            │  │
│  │      <agent-step name="DRAFT_PLANS"/>   ← call-agent 自定义 │  │
│  │    </steps>                                               │  │
│  │  </task>                                                  │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                │
│  nop-task（复用，不新建）：                                     │
│  ├── ITaskFlowManager.newTaskRuntime(task, saveState, ctx)     │
│  ├── ITask.execute(taskRt).syncGetOutputs()                    │
│  ├── ChooseTaskStep         ← marker→case 条件路由             │
│  ├── GraphTaskStep          ← forEach subflow + 依赖序         │
│  ├── RetryTaskStepWrapper   ← retry + maxRetries               │
│  ├── TimeoutTaskStepWrapper ← 超时                             │
│  ├── TaskStepStateStore     ← run-state 持久化（saveState）    │
│  └── task.xdef              ← 声明式 DSL                        │
│                                                                │
│  新增（仅四块，全部围绕"agent 语义"而非流程引擎）：             │
│  ├── AgentTaskStep          ← 自定义 TaskStep（封装 call-agent）│
│  ├── ScriptTaskStep         ← 自定义 TaskStep（check/build 命令）│
│  ├── IMarkerExtractor       ← marker 提取链（ChooseTaskStep decider）│
│  ├── DiskStateScanner       ← 磁盘状态扫描（表达式函数）        │
│  ├── PlanValidator          ← plan 格式校验（Validator wrapper）│
│  └── AppendBuffer           ← 上下文反馈注入（task scope 变量） │
│                                                                │
│  磁盘读写统一通道（强制约束）：                                 │
│  └── IToolFileSystem        ← 唯一文件访问接口                  │
│      （nop-ai-toolkit: io.nop.ai.toolkit.fs.IToolFileSystem）   │
│      ├── readText/readLines/countLines   ← 读 plan/audit        │
│      ├── writeText(append)               ← 更新 [x]/Status      │
│      ├── listDirectory/glob/grep         ← 扫描 activePlans     │
│      ├── exists/isFile/isDirectory       ← 状态判断             │
│      └── isPathAllowed/normalizePath     ← 路径权限校验         │
│      实现：LocalToolFileSystem（本地）；可替换为沙箱/远程实现    │
│                                                                │
│  依赖 nop-ai-agent 已有能力：                                   │
│  ├── CallAgentExecutor   ← 子 agent 调用（continue/fork/new）  │
│  ├── AgentPlan 模型      ← plan 静态模型（21 类）              │
│  ├── DBCheckpointManager ← append-only 持久化                  │
│  ├── AgentLifecyclePoint ← 12 个 hook 点                       │
│  └── middleware 洋葱链   ← 安全/预算/审计                       │
└────────────────────────────────────────────────────────────────┘
```

### 4.2 核心组件设计

#### 4.2.1 流程定义：task.xml（复用 nop-task DSL，对标 flow JSON）

mission-driver 的 flow JSON 直接翻译为 nop-task 的 task.xml：

```xml
<!-- mission-driver.json 主循环 → nop-task task.xml -->
<task graphMode="true" enterSteps="CHECK" exitSteps="EXIT"
      defaultSaveState="true" restartable="true"
      xmlns:x="/nop/schema/xdsl.xdef" x:schema="/nop/schema/task/task.xdef">

    <steps>
        <!-- CHECK（脚本步骤：test/build/lint） -->
        <xpl name="CHECK" allowFailure="false" timeout="600000">
            <source>...</source>
        </xpl>

        <!-- REVIEW_PLANS（条件路由：扫描 draft plan 提升为 active） -->
        <choose name="REVIEW_PLANS" decider="activePlans().isEmpty() ? 'all_complete' : 'created'">
            <case value="all_complete" goto="EXEC_PLANS"/>
            <case value="created" goto="DRAFT_PLANS"/>
            <default goto="EXEC_PLANS"/>
        </choose>

        <!-- EXEC_PLANS（DAG subflow：对每个 active plan 执行） -->
        <graph name="EXEC_PLANS" forEach="activePlans()">
            <steps>
                <call name="EXECUTE" taskPath="/mission/plan-execution.task.xml"/>
            </steps>
        </graph>

        <!-- EXECUTE（agent 步骤，封装 call-agent） -->
        <agent-step name="EXECUTE" agentId="self" inheritContext="false">
            <prompt><source>...mission driver prompt 模板...</source></prompt>
            <!-- marker 提取：输出经 IMarkerExtractor → decider 值 -->
        </agent-step>

        <!-- DRAFT_PLANS（agent 步骤） -->
        <agent-step name="DRAFT_PLANS" agentId="self"/>

        <!-- DEEP_AUDIT（DAG subflow） -->
        <graph name="DEEP_AUDIT">
            <steps>
                <call name="MULTI_AUDIT" taskPath="/mission/deep-audit-loop.task.xml"/>
            </steps>
        </graph>
    </steps>
</task>
```

关键映射（mission-driver → nop-task）：

| mission-driver 概念 | nop-task 元素 |
|---------------------|---------------|
| flow JSON steps | `<steps>` + 各 step 元素 |
| entry step | `enterSteps="CHECK"` |
| sink/done | `exitSteps="EXIT"` + `EndTaskStep` |
| transitions（marker→goto） | `ChooseTaskStep` 的 `<case value="marker" goto="next"/>` |
| when 守卫 | `<flags match="...">` 或 `<input mandatory>` |
| retry + maxRetries | `RetryTaskStepWrapper`（maxRetries） |
| timeout | `TimeoutTaskStepWrapper` / step `timeout` 属性 |
| forEach subflow | `GraphTaskStep` + `forEach` 表达式 |
| subflow 递归 | `CallTaskStep`（taskPath 引用子任务） |
| run-state.json | `defaultSaveState="true"` + `TaskStepStateStore` |
| marker 提取 | `IMarkerExtractor` 作为 ChooseTaskStep 的 decider 输入 |
| append buffer | task scope 变量（`toTaskScope="true"` 输出） |

#### 4.2.2 AgentTaskStep（自定义 TaskStep，封装 call-agent）

nop-task 没有"调用 LLM agent"的步骤，这是**唯一必须自定义的 step 类型**：

```java
/**
 * 自定义 nop-task step：执行一次 agent 调用。
 * 内部复用 CallAgentExecutor 的三种会话模式。
 */
public class AgentTaskStep extends AbstractTaskStep {
    private String agentId;          // 目标 agent（self = 当前）
    private boolean inheritContext;  // fork 时是否继承上下文
    private String skills;           // 技能列表
    private IMarkerExtractor markerExtractor;  // marker 提取链

    @Override
    public TaskStepReturn execute(ITaskStepRuntime rt) {
        // 1. 从 task scope 读取 prompt（上一步输出/append buffer 已注入）
        String prompt = rt.getScope().getValue("prompt");

        // 2. 调用 call-agent（复用 CallAgentExecutor 逻辑）
        //    - sessionId 为空 → 新会话（mission-driver 主模式）
        //    - 或按 task 输入指定 sessionId → 续跑（continue agent）
        AiAgentCallResult result = callAgent(agentId, sessionId, prompt, skills);

        // 3. 提取 marker（驱动 ChooseTaskStep 路由）
        String marker = markerExtractor.extract(result.getOutput());
        rt.getScope().setLocalValue("__marker", marker);

        // 4. 更新磁盘状态（plan markdown [x]/[ ] 勾选）——经 IToolFileSystem 写回
        planUpdater.updateFromAgentOutput(planFile, result);  // 内部 = fs.readText + 修改 + fs.writeText

        return TaskStepReturn.RETURN_RESULT(result.getOutput());
    }
}
```

- **注册方式**：继承 `AbstractTaskStep` + 在 task.xdef 扩展（或 bean 注册），与 `BeanTaskStep`/`EvalTaskStep` 同级。
- **三种会话模式复用**：`agentId/sessionId/inheritContext` 三属性完整映射 mission-driver 的三层上下文转交。
- **timeout**：nop-task 的 `TimeoutTaskStepWrapper` 已提供（无需自定义看门狗）。

#### 4.2.3 ScriptTaskStep（自定义 TaskStep，执行 check/build/test）

```java
public class ScriptTaskStep extends AbstractTaskStep {
    private String command;   // 如 "./mvnw test -pl nop-ai -am -T 1C"
    // 执行 shell 命令，超时经 TimeoutTaskStepWrapper 包装
}
```

对应 mission-driver 的 CHECK / BUILD_VERIFY / CLOSURE_SCRIPT_CHECK（脚本型 step）。

#### 4.2.4 IMarkerExtractor（marker 提取链，作为 ChooseTaskStep 的 decider）

```java
public interface IMarkerExtractor {
    String extract(String agentOutput);
}

// 四级责任链（对标 mission-driver 的 strict→tolerant→fuzzy→LLM）
public class MarkerExtractorChain implements IMarkerExtractor {
    // 1. strict: 精确正则 <AI_STEP_RESULT>value</AI_STEP_RESULT>
    // 2. tolerant: 容错空格/大小写/代码块
    // 3. fuzzy: tag 名拼写错误扫描
    // 4. LLM fallback: call-agent with sessionId 让模型只输出合法 marker
}
```

使用方式：`ChooseTaskStep.decider` 是一个 XPL 表达式，内部调用 `markerExtractor.extract(output)` 得到 marker 值，再匹配 `<case value>`。也可直接实现为 `decider` 的 XPL 片段（`<c:script>markerExtractor.extract(taskScope.output)</c:script>`）。

#### 4.2.5 DiskStateScanner（磁盘即状态，基于 IToolFileSystem）

> **强制约束**：所有磁盘读写必须通过 `IToolFileSystem` 接口，禁止直接使用 `java.nio.file`。理由：
> 1. **统一安全边界**：`isPathAllowed(path)` 保证 agent 只能访问允许的路径（沙箱/权限策略在此拦截）
> 2. **可插拔实现**：`LocalToolFileSystem`（本地）可替换为沙箱/远程/VFS 实现，agent 代码零改动
> 3. **可观测性**：所有文件访问经过同一接口，可审计、可计数、可限流
> 4. **与工具生态一致**：ReadFileExecutor/WriteFileExecutor 等工具已通过该接口读写，mission 层与其行为一致

```java
public class DiskStateScanner {
    private final IToolFileSystem fs;   // 注入（默认 LocalToolFileSystem）

    // 扫描 active plan（对标 activePlans() 表达式函数，注册为 XPL 函数）
    public List<String> activePlans(String plansDir) {
        // 用 IToolFileSystem.glob 扫描 *.md，读 front matter 判断 Plan Status: active
        return fs.glob("*.md", plansDir, false, 1, 100).stream()
            .map(FileInfo::path)
            .filter(p -> isActive(fs.readText(p, 2000)))
            .toList();
    }

    // 扫描 plan 断点（第一个未勾选 [ ] 项）——continue agent 的断点
    public PlanCheckpoint findResumePoint(String planFile) {
        // 用 IToolFileSystem.readLines 读 plan → 找第一个 [ ] → 返回 {phase, step, line}
        // 这就是 "continue agent" 的断点
    }

    // 扫描 open audit
    public List<String> openAudits(String auditsDir) {
        // 用 IToolFileSystem.glob + readText 判断 "Audit Status: open"
        return ...;
    }

    // 更新 plan 状态（勾选 [x]、更新 Status）——写回走 IToolFileSystem.writeText
    public void updatePlan(String planFile, String newContent) {
        fs.writeText(planFile, newContent, false);  // 整文件覆写（IToolFileSystem 无局部编辑）
    }

    // 追加日志（audit-remediation.log 等价物）——append 模式
    public void appendLog(String logFile, String line) {
        fs.writeText(logFile, line + "\n", true);  // append=true
    }
}
```

**实现要点**：
- `IToolFileSystem` 无"局部编辑"API（只有整文件 writeText）——plan 的 `[x]` 勾选采用 **readText 全读 → 内存修改 → writeText 整写** 模式。mission-driver 的 file_plan_store 也是整文件重写（`std::ios::trunc`），行为一致；但由于走 `IToolFileSystem`，权限校验和审计自动生效。
- `isPathAllowed` 在每次调用前由实现层保证（或 scanner 显式调用），agent 无法绕过。

注册为 nop-task 表达式函数（XPL 全局函数或 task beans），供 `decider`/`forEach`/`<flags match>` 使用——即 mission-driver 的 `activePlans()`/`draftPlans()`/`openAudits()` 纯磁盘扫描函数的 Java 等价物。

#### 4.2.6 PlanValidator（plan 格式校验，基于 IToolFileSystem，对标 plan-check.mjs）

```java
public class PlanValidator {
    private final IToolFileSystem fs;   // 注入

    // 校验 plan markdown 格式（作为 ValidatorTaskStepWrapper 的校验器）
    public ValidationResult validate(String planFile, boolean strict) {
        String content = fs.readText(planFile, MAX_PLAN_CHARS);  // 经 IToolFileSystem 读
        // 1. front matter: > Plan Status: draft|active|completed
        // 2. Phase 结构: ### Phase N - Name + Status: + Exit Criteria:
        // 3. checkbox 统计: [ ] 未完成数 / [x] 已完成数
        // 4. Closure 段: ## Closure 存在且有非占位证据
    }
}
```

nop-task 已有 `ValidatorTaskStepWrapper`——包装任意 step 前后执行校验。`PlanValidator` 作为校验器插入，实现 CLOSURE_SCRIPT_CHECK 的确定性 hard gate。**读 plan 一律走 `IToolFileSystem.readText`，路径先经 `isPathAllowed` 校验。**

#### 4.2.7 AppendBuffer（上下文反馈注入，用 task scope 变量实现）

```java
public class AppendBuffer {
    private final Map<String, String> buffers = new ConcurrentHashMap<>();
    private final int maxPromptSize;

    // 从上一步输出提取 <REMAINING> 内容，追加到下一步 prompt
    public String injectFeedback(String stepName, String prompt) {
        String feedback = buffers.get(stepName);
        if (feedback == null) return prompt;
        String injected = prompt + "\n\n## Feedback from previous round\n" + feedback;
        return truncate(injected, maxPromptSize);  // boundPromptSize 防爆
    }
}
```

nop-task 等价的更原生做法：CLOSURE_AUDIT 的输出声明 `toTaskScope="true"` 的 `feedback` 变量，EXECUTE 的 `<input name="feedback" fromTaskScope="true">` 读取并拼入 prompt——**task scope 变量即 append buffer**，无需自建 Map。

### 4.3 Continue Agent 的实现

mission-driver 的 "continue agent" **不是会话级续跑**，而是**磁盘断点恢复**：

```java
// Continue Agent = 磁盘扫描断点 + call-agent 延续
// 所有磁盘访问经 IToolFileSystem（注入），不直接操作 java.nio.file
public class ContinueAgentService {
    private final IToolFileSystem fs;
    private final DiskStateScanner scanner;   // 内部也基于 IToolFileSystem

    public AgentExecutionResult continueMission(String missionName) {
        // 1. 加载 mission 配置
        MissionConfig config = loadMission(missionName);

        // 2. 扫描磁盘状态（fs.glob + fs.readText），找到恢复点
        List<String> activePlans = scanner.activePlans(config.getPlansDir());

        for (String plan : activePlans) {
            // 3. 找到 plan 内的断点（第一个 [ ] 项）
            PlanCheckpoint checkpoint = scanner.findResumePoint(plan);

            // 4. 构建 prompt：从断点继续（告知 agent "已完成 X，从 Y 继续"）
            String prompt = buildResumePrompt(plan, checkpoint);

            // 5. 调用 call-agent 执行（新 session，因为上下文在磁盘上）
            //    agentId="self" inheritContext=false → 全新上下文，从磁盘读状态
            AiAgentCallResult result = callAgent(
                "self", null, prompt, config.getSkills());

            // 6. 解析输出，更新 plan markdown（fs.readText + 修改 + fs.writeText 整写）
            updatePlanFromAgentOutput(plan, result);
        }
    }
}
```

**核心洞察**：continue agent 不需要"记住上次的对话"——它需要的是**重新读磁盘状态，从断点继续**。这就是 mission-driver 的设计智慧：磁盘是最可靠的"记忆"。

**nop-task 原生的恢复方式（更优）**：无需手写 `ContinueAgentService`——nop-task 的 `restartable="true"` + `defaultSaveState="true"` 已支持从任意中断步骤恢复执行（`TaskStepStateStore` 持久化步骤状态）。continue agent = `ITaskFlowManager.newTaskRuntime(task, saveState=true)` 重新加载任务 + `ITask.execute()` 从断点步骤继续。磁盘扫描器（`DiskStateScanner`）作为**任务启动时的输入**（决定恢复哪个 plan），执行恢复本身交给 nop-task。

### 4.4 上下文完全转交的实现

"将上下文完全转交给另一个 agent" 有三种模式：

#### 模式 1：磁盘转交（mission-driver 主模式）
```java
// Agent A 把结果写入 plan markdown → Agent B 读 plan markdown 继续
// 上下文 = 磁盘文件（plan/audit/roadmap）
// 无需会话延续，每次都是全新 agent 从磁盘读取状态
// 读写一律经 IToolFileSystem：A 写 = fs.writeText(plan, ...)，B 读 = fs.readText(plan, ...)
// isPathAllowed 保证 A/B 只能访问授权的 plan/audit 目录
```

#### 模式 2：append buffer 转交（紧密反馈）
```java
// Agent A 的 <REMAINING> 输出 → 追加到 Agent B 的 prompt
// 上下文 = 结构化反馈片段
appendBuffer.append("EXECUTE", extractRemaining(agentA_output));
String promptB = appendBuffer.injectFeedback("EXECUTE", promptTemplate);
```

#### 模式 3：call-agent 会话转交（nop-ai-agent 已有）
```java
// 方式 A：fork + 继承上下文
call-agent(agentId="receiver", sessionId=null, inheritContext=true)
// → 子 agent 继承当前会话上下文

// 方式 B：延续同一会话
call-agent(agentId="receiver", sessionId="ses_xxx", inheritContext=false)
// → 在已有会话上继续

// 方式 C：全新上下文（mission-driver 主 step 模式）
call-agent(agentId="receiver", sessionId=null, inheritContext=false)
// → 从磁盘读取状态，全新会话
```

**推荐组合**：磁盘转交（持久状态）+ append buffer（紧密反馈）+ call-agent new session（执行）。这与 mission-driver 的三层模型完全对应。

---

## 5. 落地步骤

> 前置：`nop-ai-agent` 已依赖 `nop-task-core`（task-flow-integration 已落地），且 team 任务→DAG 映射已可用。本移植是在此基础上的 mission 级扩展。

### Phase 0：验证 nop-task 覆盖（最小验证）
1. 用一个 mission-driver 的 flow（如 CHECK→REVIEW_PLANS→EXEC_PLANS）手写为 task.xml，确认 ChooseTaskStep/GraphTaskStep/Retry 能表达全部转换。
2. 验证 `newTaskRuntime(saveState=true)` + `execute().syncGetOutputs()` 的恢复语义（中断→重启→从断点继续）。

### Phase 1：自定义 Step（AgentTaskStep + ScriptTaskStep）
3. `AgentTaskStep`（继承 AbstractTaskStep，封装 call-agent 三模式 + marker 提取）
4. `ScriptTaskStep`（执行 check/build/test 命令）
5. 在 task.xdef 注册（或 bean 注册），与 BeanTaskStep/EvalTaskStep 同级。

### Phase 2：磁盘状态 + Marker + Plan 校验
6. `DiskStateScanner` + 注册为 XPL 表达式函数（activePlans()/draftPlans()/openAudits()）
7. `MarkerExtractorChain`（strict → tolerant → fuzzy → LLM fallback）+ markerAliases 归一化
8. `PlanValidator`（front matter + checkbox + closure 证据校验，插入 ValidatorTaskStepWrapper）

### Phase 3：mission 定义落地
9. 编写 `missions/*.task.xml`（对标 missions/*.json + flows/*.json 合并为 nop-task DSL）
10. 主循环 task（CHECK/REVIEW_PLANS/EXEC_PLANS/DRAFT_PLANS/DEEP_AUDIT）
11. plan-execution 子 task（EXECUTE/CLOSURE_SCRIPT_CHECK/CLOSURE_AUDIT/BUILD_VERIFY）
12. append buffer 用 task scope 变量实现（toTaskScope/fromTaskScope）

### Phase 4：Continue Agent + 上下文转交
13. continue agent = `restartable` + saveState（nop-task 原生恢复）+ DiskStateScanner 输入
14. 上下文转交三模式（磁盘 + task scope 变量 + call-agent 会话模式）
15. 对接 team 包（多 agent 协调，复用 task-flow-integration 的编排器）

### Phase 5：可观测性
16. 事件流对接 AgentLifecyclePoint 12 点（events.jsonl 等价物）
17. `MonitorEndpoint`（HTTP/SSE 监控面板，对标 monitor.js，可选）

---

## 6. 与 nop-ai-agent 现有设计的关系

| mission-driver 概念 | nop-task / nop-ai-agent 已有 | 移植增量 |
|---------------------|-------------------------------|----------|
| FlowEngine（状态机引擎） | **nop-task**（ITaskFlowManager/ChooseTaskStep/GraphTaskStep） | **复用，不新建** |
| flow JSON（steps/transitions） | **nop-task task.xdef DSL**（steps/case/goto/enterSteps/exitSteps） | 转换（flow JSON → task.xml） |
| marker → transitions 路由 | **ChooseTaskStep**（decider + caseSteps） | 增加 IMarkerExtractor 作为 decider 输入 |
| forEach subflow | **GraphTaskStep** + GraphStepAnalyzer（拓扑/环检测） | 复用（task-flow-integration 已落地） |
| retry + maxRetries | **RetryTaskStepWrapper** | 复用 |
| timeout / 看门狗 | **TimeoutTaskStepWrapper** | 复用（无需自建看门狗） |
| run-state.json | **defaultSaveState + TaskStepStateStore** | 复用 |
| continue/resume | **restartable + saveState** | 复用（nop-task 原生恢复） |
| opencode spawn | **CallAgentExecutor**（AgentTaskStep 封装） | 自定义 step（唯一必须新增的执行器） |
| check/build 脚本 | **ScriptTaskStep** | 自定义 step（或 EvalTaskStep） |
| plan-check.mjs | **PlanValidator** | **新增**（插入 ValidatorTaskStepWrapper） |
| append buffer | task scope 变量（toTaskScope/fromTaskScope） | 复用（无需自建 Map） |
| events.jsonl | AgentLifecyclePoint 12 点 | 对接（hook 事件流） |
| plan markdown | AgentPlan 静态模型 21 类 | 增加 markdown 解析器（磁盘↔模型双向） |
| 磁盘读写（plan/audit 文件） | **IToolFileSystem**（nop-ai-toolkit，LocalToolFileSystem 实现） | **强制约束**：所有读写经该接口（isPathAllowed 安全校验/可插拔实现/可审计），禁止直接 java.nio.file |
| monitor.js | — | **新增**（可选，Phase 5） |

**核心结论**：移植**不是**"重写 mission-driver"，也**不是**"新建 FlowEngine"。流程引擎直接复用 **nop-task**（ChooseTaskStep/GraphTaskStep/Retry/Timeout/saveState 已完整覆盖 mission-driver 的状态机能力，且 task-flow-integration 已在 nop-ai-agent 落地）。磁盘读写**必须**通过 **IToolFileSystem** 接口（统一安全边界 + 可插拔 + 可审计）。真正需要新增的收敛为**四块**：
1. **AgentTaskStep**——唯一的自定义 TaskStep（封装 call-agent 三模式，mission-driver 的"外部 agent 调用"原语）
2. **DiskStateScanner**——磁盘即状态的扫描函数（activePlans/断点/openAudits，基于 IToolFileSystem.glob/readText）
3. **IMarkerExtractor**——marker 提取链（作为 ChooseTaskStep 的 decider）
4. **PlanValidator**——plan markdown 格式校验（插入 ValidatorTaskStepWrapper，基于 IToolFileSystem.readText）

nop-ai-agent 的 call-agent 三种会话模式（new/continue/fork）+ nop-task 的 saveState 恢复，共同实现 "continue agent" 与"上下文完全转交"；所有 plan/audit 文件的读、写、扫描统一走 IToolFileSystem。

---

## References

- mission-driver 引擎源码：`~/app/attractor-guided-engineering-template/tools/mission-driver/src/{engine.js, runner.js, executor.js, flow-loader.js, plan-check.mjs, main.js}`
- mission 定义：`nop-entropy-master/missions/*.json`
- 运行时输出：`nop-entropy-master/_tmp/2026-07-31-133105-mission-driver/`（run-state.json/events.jsonl/oc-*.log）
- plan 目录：`nop-entropy-master/ai-dev/plans/`
- plan guide：`ai-dev/plans/00-plan-authoring-and-execution-guide.md`
- **nop-task 引擎（复用）**：`nop-task/nop-task-core/src/main/java/io/nop/task/`（ITaskFlowManager.java、ITask.java、step/ChooseTaskStep.java、step/GraphTaskStep.java、step/RetryTaskStepWrapper.java、step/TimeoutTaskStepWrapper.java、step/CallTaskStep.java、builder/GraphStepAnalyzer.java）
- **nop-task DSL schema**：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/task/task.xdef`
- **task-flow-integration（已落地）**：`ai-dev/design/nop-ai-agent/nop-ai-agent-task-flow-integration.md`
- call-agent DSL：`nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/call-agent.tool.xml`
- call-agent 执行器：`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/tool/CallAgentExecutor.java`
- **IToolFileSystem（磁盘读写强制接口）**：`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/fs/{IToolFileSystem.java, LocalToolFileSystem.java}`（readText/readLines/writeText(append)/glob/grep/listDirectory/isPathAllowed/normalizePath）
- 工具文件执行器（基于 IToolFileSystem 的参考实现）：`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/{ReadFileExecutor, WriteFileExecutor, SearchFilesExecutor, ...}`
- AGE 对比报告：`ai-dev/analysis/2026-06-07-trellis-vs-age-comparison.md`
- call-agent DSL 设计：`ai-dev/design/nop-ai-agent/nop-ai-call-agent-dsl.md`
