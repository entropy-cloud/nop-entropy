# Agent 系统调研对比：Agno vs Goal Driver vs Nop AI Agent

## 1. 调研对象

| 系统 | 版本 | 语言 | 定位 |
|------|------|------|------|
| **Agno** | 2.6.14 | Python | 通用 Agent SDK + AgentOS 生产部署平台 |
| **Goal Driver** | N/A | Node.js | 工作流编排引擎，驱动 opencode agent 执行模块级开发周期 |
| **Nop AI Agent** | 2.0.0-SNAPSHOT | Java 21 | Nop 平台 Agent 执行引擎，XDSL/IoC/ORM 深度集成 |

---

## 2. 总体架构对比

### 2.1 架构分层

```
Agno:
┌──────────────────────────────────────────┐
│  AgentOS (FastAPI + JWT + RBAC + WS)     │  生产部署层
├──────────────────────────────────────────┤
│  Agent / Team / Workflow                 │  执行层
├──────────────────────────────────────────┤
│  Models → Tools → Knowledge → Memory     │  能力层
├──────────────────────────────────────────┤
│  DB / Vector DB / Learning Stores        │  存储层
└──────────────────────────────────────────┘

Nop AI Agent:
┌──────────────────────────────────────────┐
│  Layer 4: Platform Extensions            │  跨进程 Actor 运行时
├──────────────────────────────────────────┤
│  Layer 3: Reliability Extensions         │  断路器/检查点/恢复
├──────────────────────────────────────────┤
│  Layer 2: Execution Extensions           │  Guardrail/Router/Compactor
├──────────────────────────────────────────┤
│  Layer 1: Core Interfaces                │  ReAct 引擎 + Hook + Session
├──────────────────────────────────────────┤
│  LLM Layer (nop-ai-core)                 │  IChatService + ILlmDialect
├──────────────────────────────────────────┤
│  Tool Layer (nop-ai-toolkit)             │  IToolManager + Schema
└──────────────────────────────────────────┘

Goal Driver:
┌──────────────────────────────────────────┐
│  FlowEngine (状态机)                      │  主循环：marker 路由
├──────────────────────────────────────────┤
│  Step Executors                           │  agent / tool / script / group / subflow
├──────────────────────────────────────────┤
│  Runner (spawn opencode)                  │  子进程管理
└──────────────────────────────────────────┘
```

### 2.2 核心哲学对比

| 维度 | Agno | Nop AI Agent | Goal Driver |
|------|------|-------------|-------------|
| **Agent 是什么** | 全能实体：model + tools + knowledge + memory 的封装 | 可配置执行单元：DSL 定义，引擎执行 | 外部流程中的一个 step（不定义 agent） |
| **系统边界** | Agent 内部包含一切 | AgentModel = 纯配置，引擎 = 无状态执行器 | 不关心 agent 内部，只编排外部生命周期 |
| **扩展方式** | 子类化 + 插件 + adapter | SPI 接口（Router/Guardrail/Compactor/Hook） | 修改 JSON flow + Markdown prompt |
| **配置形式** | Python 代码 + dataclass 字段 | XDSL XML (`.xdef`) | JSON flow + Markdown prompt |
| **部署模型** | AgentOS 服务器 / embed | JVM 嵌入（IoC 容器） | CLI 工具，spawn 子进程 |
| **与框架耦合** | 无（framework-agnostic） | 强耦合 Nop 平台（IoC/XDSL/ORM） | 无（只调用 opencode CLI） |

---

## 3. 执行模型对比

### 3.1 Agent 内部循环

| 特征 | Agno | Nop AI Agent | Goal Driver |
|------|------|-------------|-------------|
| **循环类型** | run → invoke → tool_call | ReAct（推理→行动→观察） | 外部状态机（非 agent 循环） |
| **工具执行** | 串行（单 Agent），并行（Team） | 同轮并行（CompletableFuture.allOf） | N/A（委托给 opencode） |
| **循环控制** | tool_call_limit + max_retries | maxIterations + Hook 重新进入 | N/A |
| **流式** | content + event 双层流 | 仅事件发布（不逐 token 流） | N/A |
| **取消** | RunCancelledException | 两级取消（graceful/forced） | SIGTERM → SIGKILL |
| **重试** | 指数退避 + fallback models | Hook 回退 + Compactor 层降级 | maxRetries + onMaxRetries + ping-pong 检测 |
| **HITL** | 一等公民：@pause 装饰器 + 审批流 | 无内置（可通 Hook 实现） | N/A |

### 3.2 多 Agent / 协作

| 特征 | Agno | Nop AI Agent | Goal Driver |
|------|------|-------------|-------------|
| **协作模式** | Team（coordinate/route/broadcast/tasks） | 设计中有 call-agent 工具 + IMessageService | N/A（单线程流程） |
| **并行度** | Team 成员可并行 | 同 session 串行，跨 session 可并行 | 串行状态机 |
| **嵌套** | Team 可嵌套，Workflow 可嵌套 | 设计中有但未实现 | Subflow（子 FlowEngine） |

### 3.3 外部编排

| 特征 | Agno | Nop AI Agent | Goal Driver |
|------|------|-------------|-------------|
| **进程模型** | 单进程 | JVM 进程 | spawn 子进程（detached） |
| **通信** | Python 对象调用 | Java 方法调用 + CompletableFuture | 文件系统（log 文件）+ XML tag |
| **存活检测** | N/A | N/A | 5min 轮询 log 文件，60min 无输出则 kill |
| **超时** | 请求级别 | 工具级别（toolTimeoutSeconds） | 子进程级别（BASE_TIMEOUT_MS=60min） |

---

## 4. 上下文与记忆管理

| 特征 | Agno | Nop AI Agent | Goal Driver |
|------|------|-------------|-------------|
| **策略** | MemoryManager + SessionHistory + LearningMachine(5种store) | 5层压缩管线 + TokenEstimator EMA 校准 | Append buffer（AI 上次输出拼回下次 prompt） |
| **窗口控制** | compress_tool_results（LLM 压缩） | PipelineCompactor + ForcedStop 硬保护 | N/A（委托给 opencode） |
| **持久化** | DB-backed sessions + summaries + memories | ISessionStore（内存/VFS/DB） | 子进程 log 文件 |
| **记忆类型** | 用户画像、实体记忆、会话上下文、决策日志 | 无用户记忆概念 | 无记忆概念 |
| **压缩层数** | 1层（工具结果压缩） | 5层（L0截断→L1静默→L2剪枝→L3摘要→L4强制停止） | 0层 |

---

## 5. 安全与可观测性

| 特征 | Agno | Nop AI Agent | Goal Driver |
|------|------|-------------|-------------|
| **Guardrail** | PII + Prompt Injection | IContentGuardrail (input/output) | N/A |
| **权限/RBAC** | AgentOS 级别 JWT | 3级检查：ToolAccess + PathAccess + Permission，全审计 | N/A |
| **审计** | AgentOS 日志 | 每步工具调用 3 阶段审计 | log 文件 + FlowEngine log |
| **遥测** | OpenTelemetry | 事件发布（AgentEventPublisher） | N/A |

---

## 6. Workflow / 编排 DSL 对比

这是三个系统最显著的差异化维度。

### 6.1 Agno: Python 代码式编排

**定义方式**：纯 Python，Workflow 是 dataclass，step 是对象列表。

```python
# Agno Workflow
research_step = Step(name="Research", team=research_team)
fact_check = Condition(
    name="fact_check",
    evaluator=needs_fact_checking,
    steps=[Step(name="Verify", agent=fact_checker)],
)

workflow = Workflow(
    name="Content Workflow",
    steps=[research_step, summarize_step, fact_check, write_step],
)
```

**内置 step 类型**：
- `Step` — 单步（执行 agent / team / function）
- `Steps` — 顺序管线
- `Loop` — 循环直到条件满足（支持 CEL 表达式）
- `Parallel` — 并发执行
- `Condition` — if/else 分支
- `Router` — 动态路由（编程式 / CEL / HITL）

**HITL**：`@pause` 装饰器，支持 requires_confirmation / requires_user_input / user_input_schema

**评价**：表达能力最强，但表达形式是 Python 代码而非 DSL——step 之间的流转封装在 Workflow 实现的 `arun()` 方法内部，对 AI 不可读、不可写。

### 6.2 Nop AI Agent: XDSL 声明式

**定义方式**：XDSL XML，基于 xdef schema，支持 Delta 定制。

```xml
<!-- agent.xdef: Agent 配置 DSL -->
<agent name="coder" mode="react">
    <chatOptions provider="openai" model="gpt-4.1"/>
    <tools>read-file,write-file,call-agent</tools>
    <constraints maxIterations="10" tokenCompactionThreshold="0.78"/>
    <prompt><![CDATA[You are a coding agent.]]></prompt>
</agent>

<!-- agent-plan.xdef: Plan 执行 DSL -->
<plan title="Example" status="active">
    <phases>
        <phase name="analysis" status="completed">
            <tasks>
                <task taskNo="A1" title="Read schema" status="completed"/>
            </tasks>
        </phase>
    </phases>
</plan>
```

**关键设计**：
- **Hard Contract + Soft Narrative 分离**：运行时强校验的部分（phase/task/status/checks）与 AI 自由发挥的部分（description/notes）用不同结构表达
- **XDSL 的 Delta 定制**：可以通过 Delta 层覆盖任何 agent 配置——这是 Nop 平台独有的能力
- **Plan DSL vs agent.xdef 分工**：agent.xdef 定义 Agent 是什么，agent-plan.xdef 定义 Agent 要做什么

**评价**：结构最强，schema 约束确保运行时可校验，但定义(agent)和执行(plan)用两套 DSL，学习曲线比 Agno 陡。XDSL 的 Delta 定制对 Nop 平台用户是天然优势，对非 Nop 用户是额外负担。

### 6.3 Goal Driver: JSON 状态机 + Markdown Prompt

**定义方式**：JSON flow 定义步骤 + 过渡表，Markdown 文件定义 prompt。

```json
{
  "steps": {
    "HEALTH_CHECK": {
      "type": "agent",
      "promptPath": "prompts/health-check.md",
      "transitions": {
        "pass": { "goto": "PLAN_ROUTER" },
        "fail": { "retry": "HEALTH_CHECK", "maxRetries": 3 }
      }
    }
  }
}
```

**关键设计**：
- **Marker 路由**：每个 step 输出 `<AI_STEP_RESULT>` 标记，引擎根据标记查过渡表
- **Group + Subflow**：group 实现子步骤序列（含重试轮次），subflow 实现可复用子流程（通过子 FlowEngine）
- **Append buffer**：当 retry 或有 goto 时，可以将上次输出拼接到目标 step 的 prompt 中——这是整个系统中唯一的"上下文传递"机制
- **Flow variables**：`<FLOW_VARS>` 在 step 间传递变量，支持模板替换 `{{varName}}`
- **Ping-pong 检测**：检测 A→B→A→B 震荡，防止死循环

**评价**：结构最轻量，JSON + Markdown 对 AI 可读可写（AI 可以理解和编辑 flow 文件）。但表达能力有限——没有条件分支（只能用 marker 映射）、没有循环（只有 retry）、没有并行。`transitions` 表是"平面"的，复杂流程需要通过 group/subflow 嵌套来表达。

### 6.4 深潜：Step 执行模型内部对比

这是§3执行模型与§6 DSL配置之间的"缺失拼图"——三个系统的 step 在运行时究竟怎么跑。

#### 6.4.1 Agno：面向对象的 `Step` 模型 + asyncio + 线程池

```
Workflow 是 @dataclass
  ├── steps: List[Step]        ← 步骤对象列表
  ├── run(input) / arun(input) ← 同步/异步双入口
  │     └── _run_steps():
  │           for step in steps:
  │              if isinstance(step, Parallel):
  │                  ThreadPoolExecutor().map(...)
  │              elif isinstance(step, Condition):
  │                  eval(CEL expression) → run sub-steps 或 skip
  │              elif isinstance(step, Loop):
  │                  while eval(CEL): run sub-steps
  │              elif isinstance(step, Router):
  │                  call router_fn() → route to step
  │              else:
  │                  step.run() / step.arun()  ← agent.run() / team.run()
  └── session_state: Dict       ← 运行时 session 状态（不序列化）
```

**关键特点**：

1. **Step 是"全量构建"对象**：`Workflow([...])` 创建时所有 Step 已经实例化。`Condition` 的 steps 和 default_steps 也都是预构建对象。没有懒加载——所有步骤在构建时全部解析完毕
2. **执行是硬编码编排**：`_run_steps()` 遍历 `steps` 列表，用 `isinstance` 检查类型。新增 step 类型要修改 Workflow 源码。这个循环在 `run()` 方法中硬编码，不是可配置的
3. **并行依赖线程池**：`Parallel` 使用 `concurrent.futures.ThreadPoolExecutor` 的 `map()`。线程池是每次执行才创建，不能复用。没有 DAG 的 wait-for 语义——Parallel 里的 steps 相互独立
4. **CEL 表达式评估**：`Condition` 和 `Loop` 使用 `google-cel-python` 评估表达式。表达式是字符串字段，运行时才编译评估。`evaluator` 参数也可以传 callable 替代 CEL
5. **`@pause` 通过 SessionState**：带 `@pause` 的 step 执行前会检查 session_state。`resume()` 方法加载 session_state 从 DB，恢复执行。暂停期间 workflow 状态持久化在 DB
6. **同步 vs 异步双轨**：`run()` 用 `asyncio.run()` 调 `arun()` 再转同步。`arun()` 是原生 async。`Parallel` 的线程池在同步模式下阻塞等待

**优点**：直观，Python 开发者零门槛。CEL 表达式提供运行时动态性。
**缺点**：步骤拓扑硬编码在 `_run_steps()` 中，不能序列化、不能动态生成。线程池不是虚拟线程。没有懒加载——复杂 workflow 构建时所有 step 初始化完毕。

#### 6.4.2 Nop Task Flow：异步 DAG 引擎 + CompletableFuture + 声明式等待

```
TaskFlowModel 是 XDSL XML 解析而来
  ├── stepType: sequential | graph   ← 声明式拓扑
  ├── steps: List<TaskStepModel>     ← 步骤列表（声明式，非对象）
  │     └── each step:
  │           ├── type: xpl | script | simple | invoke | fork | ...
  │           ├── waitSteps: [stepId]← 前置依赖（graph 模式）
  │           ├── next: stepId       ← 顺序模式的后继
  │           ├── when: condition    ← guard 条件
  │           └── nextOnError: stepId← 错误路由
  └── 加载方式：ITaskRuntime → ResourceComponentManager → XDSL 解析
```

**执行流程**：

```
TaskFlowEngine.execute():
  1. 解析 XDSL → TaskFlowModel
  2. 确定拓扑：sequential 走线性链，graph 走 DAG
  3. 初始化 TaskState（ITaskStateStore 持久化）
  4. executeStep(stepId):
       └── 检查 waitSteps 是否全部完成（Graph 模式）
       └── 检查 when guard
       └── 执行 step（返回 TaskStepReturn）
       └── 持久化 state
       └── 根据 next / waitSteps 决定下一步
       └── resume(): 加载 state → 从中断位置恢复
```

**关键特点**：

1. **声明式拓扑**：`sequential` 模式是线性链（`next` 属性）。`graph` 模式是 DAG（`waitSteps` 声明前置依赖）。引擎计算拓扑，不依赖 step 声明顺序
2. **所有 step 返回 `TaskStepReturn`**：这是一种 `CompletableFuture` 的风格——step 返回"下一步做什么"（`RESULT` / `SUSPEND` / `WAIT`），引擎消费这个信号决定下一步。根本上异步
3. **两种并行模型**：
   - **fork**：从数据集合动态生成 step 实例。`step.keyExpr` 决定 step 名，`step.join` 是数据合并
   - **graph**：无关联的 step 天然并行执行，引擎通过 `waitSteps` 检测依赖
4. **Stateful 恢复**：`ITaskStateStore` 序列化每一步的 state。`resume()` 加载 state 从中断点恢复。支持虚拟机暂停（`suspend` step 类型）
5. **20+ 内置 step 类型**：xpl, script, simple, sequential, parallel, graph, fork, fork-n, loop, loop-n, if, choose, selector, invoke, invoke-static, call-task, call-step, delay, sleep, suspend, end, exit, custom
6. **装饰器扩展**：`@Step` 注解标记方法为 step runner。`ITaskStep` 接口允许完全自定义 step 类型
7. **虚拟线程友好**：底层使用 `CompletableFuture` + 异步链，`TaskStepReturn` 是 `AsyncFunction` 风格。在 Java 21 虚拟线程上自然适配

**优点**：声明式拓扑 + graph DAG，表达能力最强（20+ step 类型）。状态持久化 + 中断恢复。同步/异步统一为 `TaskStepReturn`。
**缺点**：学习曲线陡——需要理解 XDSL、xdef schema、异步返回模型。XML 定义冗长（业务逻辑仍需 xpl/script 模板）。

#### 6.4.3 Goal Driver：平面状态机 + 文件 IO + 子进程

```
FlowEngine (状态机主循环):
  while true:
    1. 读取 AI_RESULT 标记
    2. 查 transitions 表 → 确定 next step
    3. spawnStep(stepId):
         ├── type=agent:   spawn opencode, 喂入 prompt
         ├── type=tool:    spawn opencode, 喂入 tool prompt
         ├── type=group:   push child flow, 递归
         └── type=subflow: push child flow, 递归
    4. pipe: prompt 模板渲染 (FLOW_VARS + append buffer)
    5. wait: 轮询 log 文件 (5min 存活, 60min timeout)
    6. collect: 解析 AI_RESULT + FLOW_VARS
    7. 回到 1
```

**关键特点**：

1. **Marker 路由是核心**：不是"引擎判断条件分支"，而是"AI 输出 tag，引擎查表路由"。`AI_RESULT` 标记匹配 `transitions` 表的 key。这是三系统中唯一的"AI 输出驱动流程"模式
2. **文件系统是总线**：log 文件 = 存储 + 通信介质的统一体。引擎写入 prompt→step 写入 log→引擎读取 log→解析标记（XML tag）。无内存态共享，天生分布式/进程隔离
3. **状态机就是平面 JSON**：`goto: "STEP_ID"` 是无条件跳转，`retry` 是回跳。没有条件分支、没有并行、没有递归（只有嵌套 subflow）
4. **Append buffer 实现"上下文记忆"**：`"appendSource": "CONTENT"` 将上次 step 的输出拼到下次 prompt 尾。这是唯一跨 step 传递上下文的方式
5. **group 是"垂直"而非"水平"嵌套**：group 的 sub-step 按顺序执行，失败时整个 group 重试（由 maxRounds 控制）。没有 sub-step 级别重试或条件跨越

**优点**：最简单——状态机+JSON+Markdown。不依赖任何语言运行时（只依赖 Node.js CLI）。
**缺点**：平面表达能力——没有条件分支、没有 true 循环、没有并行。文件 IO 是性能瓶颈。

#### 6.4.4 执行模型横向对比

| 维度 | Agno | Nop Task Flow | Goal Driver |
|------|------|--------------|-------------|
| **拓扑描述** | Python list（硬编码顺序） | XDSL XML（sequential/graph） | JSON transitions 表 |
| **异步模型** | asyncio + ThreadPoolExecutor | CompletableFuture（统一 TaskStepReturn） | N/A（子进程阻塞） |
| **状态持久化** | DB session (WorkflowSession) | ITaskStateStore（ORM 实体） | Log 文件 |
| **中断恢复** | resume(session_id) 从 DB | resume() 从 state store | N/A |
| **并行实现** | ThreadPoolExecutor.map() | DAG waitSteps + fork | N/A |
| **条件分支** | CEL 表达式 / Python callable | `<when>` guard + `<choose>` switch | Marker 路由（AI 输出驱动） |
| **循环** | Loop step（CEL 条件） | `<loop>` / `<loop-n>` step | retry（等价于循环） |
| **动态步骤** | N/A（全量预构建） | `<fork>` 数据驱动 + `keyExpr` | N/A |
| **AI 可生成** | ★☆☆（Python 代码） | ★★☆（XML DSL） | ★★★（JSON + Markdown） |
| **跨进程** | ★☆☆（单进程） | ★★★（state 可序列化到 DB） | ★★★（子进程隔离） |

---

## 7. DSL 易用性对比

### 7.1 使用难度评级

| 维度 | Agno | Nop AI Agent | Goal Driver |
|------|------|-------------|-------------|
| **上手门槛** | ★☆☆（Python 开发者即用） | ★★★（需理解 XDSL/IoC/Delta） | ★★☆（JSON + Markdown，两天上手） |
| **表达能力** | ★★★（6种 step 类型 + CEL） | ★★☆（agent/plan 两套 DSL，各自完整） | ★☆☆（只有序列 + retry + group 嵌套） |
| **AI 可读写** | ★☆☆（代码不易被 AI 改写） | ★★☆（XML 对 AI 尚可，但 xdef schema 复杂） | ★★★（JSON + Markdown，AI 最容易理解） |
| **运行时校验** | ★★☆（Pydantic 校验，但流程逻辑在代码中） | ★★★（xdef schema + 强校验规则） | ★★☆（只校验 marker 是否在 transitions 中） |
| **可调试性** | ★★☆（Python 堆栈 + 事件流） | ★★☆（事件 + log + IoC debug） | ★☆☆（只看 log 文件，状态机难追踪） |
| **可扩展性** | ★★★（子类化 + adapter + 大量 SPI） | ★★★（XDSL Delta + SPI 接口） | ★☆☆（固定 flow + prompt，扩展需改 JSON） |

### 7.2 优缺点

#### Agno Workflow

**优点**：
- Python 原生，生态最强。50+ model providers，130+ tool integrations
- Workflow + Team + Agent 三层次架构覆盖从简到繁
- HITL 一等公民：`@pause` 装饰器即可添加人工审批
- 流式事件层面丰富（workflow_started / step_started / step_completed / workflow_completed）
- Workflow 的 run 接口与 Agent 一致（`run()` / `arun()` / `print_response()` / `stream_events=True`），统一用户体验

**缺点**：
- Workflow 定义在代码中，AI 不可读写、无法动态生成
- 无内置外部状态机——Workflow 的步骤流转由内部代码驱动，跨进程恢复困难
- 记忆管理只在 Agent 级别（MemoryManager），Workflow 级别无独立记忆
- 无内置工作流 DSL 序列化——你不能把 Workflow 存成文件再加载执行

#### Nop AI Agent

**优点**：
- **DSL 优先**：Agent 定义（agent.xdef）和执行计划（agent-plan.xdef）都是结构化 XML，AI 可读可写
- **Hard Contract / Soft Narrative 分离**：运行时能强校验的部分与 AI 自由发挥的部分显式区分
- **Delta 定制**：Nop 平台独有的能力——不需要修改原始定义，通过 Delta 层覆盖
- **Plan DSL 的完成阻断规则**：不满足强校验（未完成依赖、未解决的 blocking error、未通过的 check）就不能结束 plan——这是三个系统中最严格的完成契约
- **五层压缩管线**：三系统中最强（Agno 只有工具结果压缩，Goal Driver 无压缩）

**缺点**：
- **两套 DSL 各自独立**：agent.xdef 定义 Agent 形态，agent-plan.xdef 定义执行计划，但两者之间的关联（谁在执行哪个 plan？plan 执行到哪一步了？）没有显式 DSL 表达
- **无内置 Workflow 层**：目前只有单 Agent ReAct + Plan DSL。如果需要多步骤编排（类似 Agno 的 Workflow），需要上层调用 nop-core 的 Flow 引擎或自行编排
- **学习曲线最陡**：XDSL + IoC + Delta + 权限配置 + Plan DSL 合计超过 5 套概念体系
- **Plan DSL 缺少运行时调度器**：当前 Plan DSL 只表达"结构化协作协议"，真正的 task 依赖拓扑执行器、自动恢复引擎还没有实现

#### Goal Driver

**优点**：
- **最轻量**：一个 JSON 文件定义全部流程，Markdown 文件定义 prompt
- **AI 最友好**：JSON + Markdown 对 AI 来说是最容易理解和生成的格式（对比 Python 代码或 XDSL XML）
- **进程隔离**：每个 step 是独立子进程，一个 step 的崩溃不影响引擎
- **内置存活检测和超时**：5min 轮询 log 文件，60min 无输出自动 kill——生产级基本保障
- **Ping-pong 检测**：自动检测 A→B→A→B 震荡，防止 AI 循环

**缺点**：
- **表达能力有限**：只有序列 + retry + group (maxRounds) + subflow。没有条件分支（只有 marker 映射）、没有 true 循环、没有并行
- **无状态**：append buffer 是唯一的跨 step 上下文，没有持久化的记忆/会话概念
- **扩展要靠修改 JSON**：不支持插件或 SPI，要添加新行为必须改 flow JSON 和 prompt Markdown
- **Debug 困难**：状态机不可见，只有 log 文件可追踪。两个 step 之间的"为什么走到这里"需要手动拼图
- **Group 实现不够灵活**：只有第一个 sub-step 失败才能 `_retry` 重试整轮，不支持 sub-step 级别的条件跨越

---

## 8. 综合结论

### 8.1 场景匹配

| 场景 | 推荐系统 | 理由 |
|------|---------|------|
| 快速构建多 Agent 应用，需要大量 provider/tool | **Agno** | 50+模型，130+工具，AgentOS 一键部署 |
| Nop 平台深度集成的 Agent，需要 DSL 定制 + Delta | **Nop AI Agent** | XDSL + IoC + ORM 深度耦合，Delta 定制 |
| 自动化完整代码开发周期（plan→code→verify→audit） | **Goal Driver** | 专为此场景设计 |
| 需要 HITL 的工作流 | **Agno** | @pause 装饰器内置支持 |
| 需要严格完成契约的 plan 执行 | **Nop AI Agent** | Plan DSL 强校验 + 完成阻断规则 |
| AI 需要动态生成和修改工作流 | **Goal Driver** | JSON + Markdown 对 AI 最友好 |

### 8.2 对 Nop AI Agent 的启示

1. **Workflow 层缺失**：当前无内置多步骤编排。Agno 的 Workflow 6 种 step 类型和 Goal Driver 的 marker 路由机制值得参考——特别是 marker 路由的"AI 输出 tag → 引擎自动路由"模式，天然适合 Agent 场景，比硬编码条件分支更灵活

2. **Agent DSL 和 Plan DSL 之间缺桥**：目前 agent.xdef 定义 Agent 的"what"，agent-plan.xdef 定义 Agent 的"how"，但"who executes which plan"和"plan progress tracking"没有 DSL 化的表达。Goal Driver 的 `<FLOW_VARS>` 机制——跨 step 传递变量——提供了一个轻量级的参考模型

3. **DSL 的 AI 可读写性是核心优势**：对比 Agno（Python 代码不可 AI 改写），Nop 的 XDSL XML 天然对 AI 可读可写。应保持这个优势——不要追求"DSL 表达一切"，而是追求"DSL 表达运行时能校验的那部分，其余让 AI 自由发挥"（Hard Contract / Soft Narrative 分离原则）

4. **安全体系是最大差异化优势**：三系统中 Nop AI Agent 的安全体系（3 级权限检查 + 全审计 + PathAccess + Guardrail）最完整。Goal Driver 完全没有安全层，Agno 的 AgentOS 有 JWT RBAC 但不在 agent 内部
