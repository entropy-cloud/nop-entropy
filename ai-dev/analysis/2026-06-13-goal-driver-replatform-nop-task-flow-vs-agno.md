# 用哪个框架重写 Goal Driver 更容易：Nop Task Flow vs Agno Workflow

> Status: open
> Date: 2026-06-13
> Scope: `ai-dev/tools/opencode-goal-driver/`、`nop-task/`、`~/ai/agno/libs/agno/agno/workflow/`
> Conclusion: （待定，见§6）

## Context

Goal Driver (`ai-dev/tools/opencode-goal-driver/`) 是一个 Node.js 工作流编排引擎，驱动 opencode agent 执行模块级开发周期。它用 JSON flow + Markdown prompt 定义 AI 工作流，用 marker 路由（AI 输出 XML tag → 引擎查 transitions 表跳转）编排 step 序列。

当前痛点：
- 表达能力有限（无条件分支、无 true 循环、无并行）
- 无状态持久化（log 文件是唯一的持久化，无断点恢复）
- Group 实现不够灵活（只有第一个 sub-step 失败才能重试整轮）

问题：**如果用 Agno Workflow 或 Nop Task Flow 重写 Goal Driver 的 AI 工作流编排，哪个更容易？**

判断依据：基于对三个系统源码的逐行阅读（见§5 References），逐一映射 Goal Driver 的 8 个核心机制到两个框架的扩展点，评估实现代码量和语义匹配度。

---

## 1. Goal Driver 的 8 个核心机制

从 `flows/goal-driver.json`、`flows/plan-execution.json` 和 FlowEngine 源码提炼：

| # | 机制 | 当前实现 | 核心语义 |
|---|------|---------|---------|
| M1 | Spawn 子进程 | `child_process.spawn("opencode", ...)` | 引擎 spawn 独立进程，喂入 prompt |
| M2 | 解析 marker | 正则匹配 `<AI_STEP_RESULT>...</AI_STEP_RESULT>` | 从子进程 stdout/log 提取结构化标记 |
| M3 | Marker 路由 | `transitions[marker].goto / retry` | AI 输出 tag → 引擎查表跳转 |
| M4 | Append buffer | `appendSource: "CONTENT"` → 拼接到下次 prompt | 跨 step 上下文传递 |
| M5 | FLOW_VARS | `<FLOW_VARS>` → `{{varName}}` 模板替换 | 跨 step 变量传递 |
| M6 | Retry / Group / Subflow | `maxRetries` + `group` + `subflow` | 重试、嵌套、子流程 |
| M7 | 健康检查 / 超时 | 5min 轮询 log + 60min timeout + SIGTERM→SIGKILL | 进程级容错 |
| M8 | Ping-pong 检测 | 记录最近 N 个 step 序列，检测 A→B→A→B 震荡 | 防死循环 |

---

## 2. Nop Task Flow 的扩展点（源码验证）

### 2.1 `ITaskStep` 接口 — 自定义 step 的入口

```java
// ITaskStep.java:50
TaskStepReturn execute(ITaskStepRuntime stepRt);
```

只需实现一个方法。`ITaskStepRuntime` 提供：
- `getEvalScope()` — 变量作用域（天然支持 FLOW_VARS：`scope.setLocalValue("var", value)`）
- `saveState()` — 持久化状态到 `ITaskStateStore`（天然断点恢复）
- `getStateBean()` / `setStateBean()` — step 级别状态对象
- `getCancelToken()` — 取消支持
- `isRecoverMode()` — 恢复模式标志

### 2.2 `TaskStepReturn` — 动态路由的载体

```java
// TaskStepReturn.java:59-63 — 动态指定下一步
public static TaskStepReturn RETURN(String nextStepName, Map<String, Object> outputs)

// TaskStepReturn.java:65-67 — 异步返回
public static TaskStepReturn ASYNC_RETURN(CompletionStage<TaskStepReturn> future)

// TaskStepReturn.java:35 — 暂停（HITL）
public static final TaskStepReturn SUSPEND
```

**关键发现**：`RETURN(nextStepName, ...)` 完美映射 Goal Driver 的 `goto: "STEP_ID"`。step 可以在运行时动态决定跳转到哪个 step——这就是 marker 路由的本质。

### 2.3 `ChooseTaskStep` — 声明式 marker 路由

```java
// ChooseTaskStep.java:54-73
public TaskStepReturn execute(ITaskStepRuntime stepRt) {
    String caseValue = ConvertHelper.toString(decider.invoke(stepRt));
    ITaskStepExecution chosenStep = caseSteps.get(caseValue);
    if (chosenStep == null) chosenStep = defaultStep;
    return chosenStep.executeWithParentRt(stepRt);
}
```

`decider` 是 `IEvalAction`（XLang 表达式），返回一个 string → 从 `caseSteps` Map 中选择。这就是声明式的 marker 路由：
```xml
<choose decider="${aiMarker}">
    <case value="pass">...</case>
    <case value="fail">...</case>
    <default>...</default>
</choose>
```

### 2.4 现成的 wrapper step

- `RetryTaskStepWrapper` — M6 retry
- `TimeoutTaskStepWrapper` — M7 超时
- `SequentialTaskStep` — M6 group
- `CallTaskStep` — M6 subflow
- `ForkTaskStep` — 并行（Goal Driver 没有，但是增强）

---

## 3. Agno Workflow 的扩展点（源码验证）

### 3.1 `Step.executor` — 自定义执行的入口

```python
# step.py:88-99
StepExecutor = Callable[
    [StepInput],
    Union[StepOutput, Iterator[StepOutput], ...],
]
```

可以传任意 callable 作为 executor。`StepOutput` 包含 `content`、`success`、`stop` 等字段。

### 3.2 `Router.selector` — 动态路由

```python
# router.py:566-599
def _route_steps(self, step_input, session_state=None):
    if isinstance(self.selector, str):
        # CEL 表达式评估
        step_name = evaluate_cel_router_selector(self.selector, ...)
        return self._resolve_selector_result(step_name)
    if callable(self.selector):
        result = self.selector(step_input, ...)
        return self._resolve_selector_result(result)
```

Router 从 `choices`（预定义 Step 列表）中选择执行。

### 3.3 HITL 一等公民

```python
# step.py:131-141
requires_confirmation: bool = False
requires_user_input: bool = False
on_error: Union[OnError, str] = OnError.skip
```

### 3.4 序列化（有限）

```python
# step.py:276-300
def to_dict(self) -> Dict[str, Any]:
    result = {"type": "Step", "name": self.name, ...}
    if self.executor is not None:
        result["executor_ref"] = self.executor.__name__  # 只存函数名
```

反序列化时需要从 Registry 查找函数。嵌套 workflow 无法完全序列化（`step.py:388-408` 有 warning）。

---

## 4. 逐机制映射对比

### M1: Spawn 子进程

| | Agno | Nop Task Flow |
|---|---|---|
| 实现方式 | `Step(executor=spawn_fn)` | 自定义 `SpawnStep implements ITaskStep` |
| 代码量 | ~30 行 Python（subprocess.run + parse output） | ~60 行 Java（ProcessBuilder + parse output） |
| 语义匹配 | ★★★（executor 是 callable，天然包裹任意逻辑） | ★★☆（需要定义新 class，但 ITaskStep 接口简洁） |

### M2: 解析 marker

| | Agno | Nop Task Flow |
|---|---|---|
| 实现方式 | executor 函数内 regex parse → 返回 `StepOutput(content=marker)` | step 的 execute 内 parse → 写入 scope → 返回 `RETURN_RESULT(marker)` |
| 代码量 | 相同（正则解析） | 相同 |

### M3: Marker 路由（**核心差异**）

| | Agno | Nop Task Flow |
|---|---|---|
| 语义 | Router 是"从 `choices` 中**选择**一个 Step 执行" | `TaskStepReturn.RETURN(nextStepName)` 是"**跳转**到指定 step" |
| 匹配度 | ★★☆（Router 语义不完全匹配：它执行选中的 step，不是"跳转到 state"。Goal Driver 的 `goto` 是状态机跳转，Router 是函数分发） | ★★★（`RETURN(nextStepName)` 完美映射 `goto`。`<choose decider>` 完美映射 transitions 表） |
| 声明式 | 无（必须在代码中定义 Router 对象） | 有（`<choose>` XML 声明式） |
| 动态 | `selector` callable 可以动态返回 step name | `RETURN(nextStepName)` 动态指定 |

**关键洞察**：Goal Driver 的 `transitions` 表本质是一个**状态机**——每个 step 是一个 state，marker 是 transition 的 trigger。Agno 的 Router 是**函数分发**——给定输入选择执行哪个函数。Nop Task Flow 的 `TaskStepReturn.RETURN(nextStepName)` + `<choose>` 是**状态机 + 声明式分发**——既支持代码内动态跳转，又支持 XML 声明式 case 分发。

### M4: Append buffer

| | Agno | Nop Task Flow |
|---|---|---|
| 实现方式 | `session_state['append'] = last_output` → 手动在下一个 step 的 prompt 中拼接 | scope 变量：`scope.setLocalValue("append", lastOutput)` → XLang 模板 `${append}` |
| 自然度 | ★★☆（session_state 是全局 Dict，语义松散） | ★★★（scope 是结构化的变量作用域，XLang 模板天然支持） |

### M5: FLOW_VARS

| | Agno | Nop Task Flow |
|---|---|---|
| 实现方式 | session_state 手动管理 | scope 变量 + `IEvalAction` 表达式 |
| 自然度 | ★★☆ | ★★★（XLang 表达式 `${varName}` 天然模板替换） |

### M6: Retry / Group / Subflow

| | Agno | Nop Task Flow |
|---|---|---|
| Retry | `Step.max_retries` — 内置 | `RetryTaskStepWrapper` — 内置 |
| Group | `Steps([...])` — 内置容器 | `SequentialTaskStep` — 内置 |
| Subflow | `Step(workflow=nested)` — 内置 | `CallTaskStep` — 内置 |
| 匹配度 | ★★★ | ★★★（两者都有现成实现） |

### M7: 健康检查 / 超时

| | Agno | Nop Task Flow |
|---|---|---|
| 超时 | executor 内自行实现（无内置 step 级超时） | `TimeoutTaskStepWrapper` — 内置 |
| 健康检查 | executor 内自行实现 | 自定义（在 step 内轮询进程 + `ICancelToken` 取消） |
| 匹配度 | ★★☆ | ★★★（Timeout wrapper 现成） |

### M8: Ping-pong 检测

| | Agno | Nop Task Flow |
|---|---|---|
| 内置支持 | 无 | 无 |
| 实现方式 | 自定义（记录最近 N step 序列） | 自定义（同左） |
| 匹配度 | ★☆☆ | ★☆☆（两者都需要自定义） |

---

## 5. 综合评分

| 维度 | Agno | Nop Task Flow | 说明 |
|------|------|---------------|------|
| M3 Marker 路由语义匹配 | ★★☆ | ★★★ | **决定性差异**：Nop 的 `RETURN(nextStep)` + `<choose>` 天然状态机 |
| M4/M5 上下文传递 | ★★☆ | ★★★ | Nop 的 scope + XLang 表达式更自然 |
| M6 Retry/Group/Subflow | ★★★ | ★★★ | 两者都有现成实现 |
| M7 超时 | ★★☆ | ★★★ | Nop 有 `TimeoutTaskStepWrapper` |
| 状态持久化 | ★★☆ | ★★★ | Nop 有 `saveState()` + ITaskStateStore |
| 异步模型 | ★★☆ | ★★★ | Nop 的 `ASYNC_RETURN(CompletableFuture)` 天然异步 |
| **AI 可读写 flow 定义** | ★☆☆ | ★★☆ | **Agno 是 Python 代码，不可读写；Nop 是 XML，可读但冗长** |
| 自定义代码量 | 中（~200 行） | 少（~100 行：一个 SpawnStep bean） | Nop 只需一个自定义 step |
| 学习曲线 | 低（Python 开发者即用） | 高（需理解 XDSL/IoC/xdef） | **Agno 上手更快** |
| 平台依赖 | 无（framework-agnostic） | 强耦合 Nop 平台 | **Agno 更独立** |

### 加权结论（按 Goal Driver 核心需求加权）

Goal Driver 的核心是 **marker 路由（M3）+ 状态持久化 + AI 可读写 flow**。

- 如果**最看重语义匹配和功能完整**（marker 路由、状态恢复、超时、异步）→ **Nop Task Flow 胜出**（7:3）
- 如果**最看重 AI 可读写 flow 定义**（JSON + Markdown 的核心优势）→ **两者都不如 Goal Driver 现状**（Agno Python 不可读写，Nop XML 冗长）
- 如果**最看重上手速度和独立性**（不想依赖 Nop 平台）→ **Agno 胜出**（Python 原生，framework-agnostic）

---

## 6. 结论

### 6.1 推荐：Nop Task Flow 更容易实现功能等价物

**理由**：
1. **M3 marker 路由是决定性差异**：`TaskStepReturn.RETURN(nextStepName)` 完美映射 `goto`，`<choose decider>` 完美映射 transitions 表。Agno 的 Router 语义不匹配（函数分发 vs 状态机跳转）
2. **状态持久化内置**：`ITaskStepRuntime.saveState()` 天然断点恢复——这是 Goal Driver 当前缺失的能力
3. **只需一个自定义 step**：实现 `SpawnOpencodeStep implements ITaskStep`（~100 行），其余用现成 wrapper
4. **异步天然**：`ASYNC_RETURN(CompletableFuture)` 与 Java 21 虚拟线程适配

### 6.2 代价

1. **失去 AI 可读写 JSON 的优势**：XDSL XML 比 JSON 冗长，AI 生成/修改不如 JSON 友好
2. **强耦合 Nop 平台**：需要 JVM + IoC 容器 + XDSL 运行时
3. **学习曲线陡峭**：需要理解 XDSL、xdef、IEvalAction、IEvalScope

### 6.3 被否决的方案

**Agno Workflow** — 否决理由：
- Router 语义不匹配 marker 路由（函数分发 vs 状态机跳转）
- Workflow 定义是 Python 代码，AI 不可读写
- 无内置状态持久化（session 语义不匹配）
- 子进程管理、健康检查、ping-pong 都需要大量自定义

**保持 Goal Driver 现状** — 不否决，但可以作为"渐进增强"路径：
- 如果当前痛点（无条件分支、无状态恢复）不紧急 → 保持 JSON + Markdown 格式
- 在 JSON flow 中逐步扩展 transitions 表（加条件分支），保持 AI 可读写优势

### 6.4 混合方案（可选）

用 Nop Task Flow 作为**底层执行引擎**，但提供一个 **JSON → XDSL XML 的编译器**：
- AI 仍然写 JSON flow（保持可读写优势）
- 编译器将 JSON 转换为 Nop Task Flow 的 `.task.xml`
- 引擎执行 `.task.xml`，享受状态持久化、异步、超时等能力
- 代价：需要实现编译器（~500 行），但一次性投入

---

## 7. Open Questions

- [ ] Nop Task Flow 是否支持"子进程级隔离"（Goal Driver 的核心安全模型）？`SpawnOpencodeStep` 内部用 `ProcessBuilder` 是否足够？
- [ ] Nop Task Flow 的 `ITaskStateStore` 是否支持跨 JVM 重启恢复？还是只在同一 JVM 内有效？
- [ ] 如果走混合方案（§6.4），JSON → XML 编译器的维护成本是否可接受？
- [ ] Agno 的 `@pause` HITL 机制是否是 Goal Driver 需要的？（Goal Driver 目前无 HITL）

---

## References

- `ai-dev/tools/opencode-goal-driver/flows/goal-driver.json` — Goal Driver 主 flow 定义
- `ai-dev/tools/opencode-goal-driver/flows/plan-execution.json` — plan 执行子 flow
- `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStep.java:50` — 自定义 step 接口
- `nop-task/nop-task-core/src/main/java/io/nop/task/TaskStepReturn.java:59-67` — 动态路由 + 异步返回
- `nop-task/nop-task-core/src/main/java/io/nop/task/ITaskStepRuntime.java:121` — saveState()
- `nop-task/nop-task-core/src/main/java/io/nop/task/step/ChooseTaskStep.java:54-73` — 声明式 marker 路由
- `nop-task/nop-task-core/src/main/java/io/nop/task/step/InvokeTaskStep.java:43-55` — IoC bean 反射调用
- `nop-task/nop-task-core/src/main/java/io/nop/task/step/RetryTaskStepWrapper.java` — retry wrapper
- `nop-task/nop-task-core/src/main/java/io/nop/task/step/TimeoutTaskStepWrapper.java` — timeout wrapper
- `~/ai/agno/libs/agno/agno/workflow/step.py:88-99` — StepExecutor callable 定义
- `~/ai/agno/libs/agno/agno/workflow/step.py:766-979` — Step.execute 重试逻辑
- `~/ai/agno/libs/agno/agno/workflow/router.py:566-599` — Router._route_steps 路由逻辑
- `~/ai/agno/libs/agno/agno/workflow/router.py:48-94` — Router dataclass（choices + selector）
- `ai-dev/analysis/agent-survey/2026-06-13-agno-vs-goal-driver-vs-nop-agent-survey.md` §6.4 — 三系统执行模型内部对比
