# Bridgic Amphibious 深度分析：双循环 OTA 与 Amphibious FSM 的统一编排

> Status: open（已 review 修正若干 overclaim，见下文 [2026-08-17 修正记录] 一节）
> Date: 2026-08-17
> Scope: `~/ai/bridgic`（bitsky-tech/bridgic，截至 2026-08-04，bridgic-amphibious 0.2.0 + bridgic-core 0.3.x + bridgic-asl 0.1.x，MIT）vs `nop-ai-agent`（nop-entropy/nop-ai/nop-ai-agent，~536 个 Java 文件，截至 2026-08-17 设计 + 实现对照）
> Conclusion: Bridgic 的核心创新是把"双模式智能体（Logic 工作流 ↔ Magic 智能体）"收敛到**同一个 Dynamic Directed Graph runtime** 之上，并用 **OTA（Observe-Think-Act）小循环** + **Context 大循环**两段语义不变式，把"yield-driven coroutine 状态机"作为唯一的协作接口；这是少有的**用 Python async-generator + yield 协议同时承载 agent / workflow / sub-agent-delegation 三种调度模型**的设计。**对 nop-ai-agent 的可借鉴点（已 review 修正）**：(1) **OTAContext 双段不变式**（framework-owned small-loop + free-form big-loop）作为"context 拆分"的概念参考——但要诚实承认这是 cleanliness 重构，对 nop 现有 25 字段 `AgentExecutionContext` 没有 user-facing 价值，应作为 P2 而非 P0；(2) **AmphibiousFSM 的 step-level fallback + mode switch 概念**值得作为 future-direction 研究（plan-first），但要**先做 gap analysis**——nop 现有 `IToolCallRepairer` + `IDenialLedger` + `LlmCallCoordinator` + `AgentCompactionCoordinator` 的 reliability 栈已覆盖大部分场景，新加 step-level fallback agent 是叠层，无明确未被现有栈覆盖的 user-facing 痛点驱动；(3) **`OTARecord.extra="allow"`** 模式（hook 向当前 round 折叠自定义字段无需 subclass）有借鉴价值；(4) **`@human_channel` + 内置 `request_human_tool`** 作为显式 HITL 抽象值得借鉴；(5) **CognitiveWorker / AgentWorker 对称 peer 设计**——经过 review 修正后降级为 P3 future-direction：nop 当前 call-agent 是同进程 fork session（即使是 async mailbox pathway），不需要外部 CLI 委派；强行提取 CognitiveWorker 抽象就是重命名 `ReActAgentExecutor`，零功能收益。**核心不可借鉴点**：Python async-generator + yield 的协作接口对 Java 价值有限（Java 没有原生 PEP 525 等价物，只能用回调 + Future 链模拟）。**本节已纠正先前版本的几个 overclaim**：(a) 原报告"nop 用 @Tool 反射扫描"——**错**；正确为"VFS + XLang DSL 组件模型注册表（`IToolManager` 经 `ResourceComponentManager.loadComponentModel` 从 `/nop/ai/tools/*.tool.xml` 装载）"；(b) 原报告"call-agent 仅同进程 fork"——**错**；正确为"call-agent 有 sync fork+exec 与 async mailbox 两条路径；async 路径经 `agent.call-agent` topic 投递到 `IMessageService`，可经 `DBMessageService`（plan 224）实现跨进程"；(c) 原报告把"拆分 AgentExecutionContext"标 P0 是被设计美学带着走，**实际是 P2 cleanliness 重构**。

---

## 一、总览

| 维度 | Bridgic Amphibious | nop-ai-agent |
|------|---------------------|----------------|
| 语言 / 运行时 | Python 3.9+，asyncio / async-generator / yield | Java 21，CompletableFuture + Nop IoC |
| 核心定位 | "Logic ↔ Magic" 双模式智能体框架，**DDG runtime 同时承载 workflow 和 agent** | 单 ReAct 主循环为主 + sustainer 续跑 + team/plan/runtime 周边机制 |
| 核心抽象 | `AmphibiousAutoma[OTAContext, Context]` + yield 协议 + 显式 OTAContext（双 Context 与 bridgic 类似：small-loop OTA + big-loop Context） | `DefaultAgentEngine`（运行时容器）+ `AgentExecutionContext`（单 Context） + `IAgentExecutor`（ReAct/SingleTurn 两种实现） |
| 状态机驱动 | **yield-driven coroutine FSM**（`AsyncExitStack` + `_AmphiState` 显式 FSM 字段） | ReAct `while` 双层（reactLoop + sustainLoop），currentIteration 计数 |
| 上下文模型 | **双 Context**：小循环 OTAContext（framework-owned，user_input + ota_record + tools）+ 大循环 Context（free-form，fields + overridable summary） | 单 Context：`AgentExecutionContext` + `AgentSession`（消息数组 + 计数器 + planId） |
| 模式切换原语 | `EnterAgent`（workflow 切 agent，新 OTAContext 实例）+ `ThinkUnit`（agent 内命名 think step）+ `ThinkAgent`（agent 内命名 external-CLI 委派 step） | 工具级：`call-agent`（fork+exec）+ `send-message`（fire-and-forget） + Actor mailbox 异步 |
| 子代理机制 | **in-process CognitiveWorker**（LLM 思考）+ **out-of-process AgentWorker**（CLI 委派，通过 in-process FastMCP HTTP server 桥接项目工具），二者**对称 peer** | `call-agent` 工具（fork+exec via `IAgentEngine.execute()`）+ actor mailbox 异步 + DB takeover lock |
| 错误恢复 | **AmphibiousFSM step-level fallback**（原子调用失败 → 内联 bounded recovery sub-run）+ full fallback（工作流关闭，全切 agent）+ recovery cycle safety bound | **checkpoint journal**（LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR）+ 幂等键发散检测 + 60s 恢复守护 + DB 接管锁 |
| LLM 协议 | bridgic-core 提供 `BaseLlm` 抽象 + `achat/aselect_tool/astructured_output/astream` 多协议 + LiteLLM/OpenAI/vLLM/OpenAI-like 适配器 | nop-ai-core 提供 `IChatService` + `IChatModel` + `ModelKeys` (composite provider:model 路由) |
| 工具注册位置 | **OTAContext.tool** 类级装饰器（声明式、不自动注入、subclass 继承） | nop-toolkit `IToolManager` 经 `ResourceComponentManager.loadComponentModel` 从 VFS `/nop/ai/tools/*.tool.xml` 装载 + `IToolExecutor` 执行链 + tool-tag system（**VFS + XLang DSL 组件模型注册表**，**非反射扫描**） |
| 人机协作（HITL） | `@human_channel` 类级装饰器（多通道注册）+ `HumanCall` yield + 内置 `request_human_tool`（任何 OTAContext 可声明，contextvars 解析当前 agent） | **`ask-oracle` 工具已经具备 HITL 抽象语义**：`{question, options[]}` 形态 + `timeoutMs` + 返回选项 key；当前 backend 是 AI agent 占位（`ORACLE_ENDPOINT` 未设 → fast-fail），**HITL 实现路径是扩展 oracle backend dispatcher**（加 `ORACLE_CHANNEL` 环境变量 + `OracleBackend` SPI：人 backend、Slack backend、Feishu backend），**不需要新原语**。**这比 bridgic 的 `HumanCall` 设计更优雅**：形态完全自洽、"决策者" 抽象天然包含人 |
| 持久化 | `AgentTrace`（flat execution path recorder，workdir-based）+ `run_dir`（每 run 一个目录）+ replay fingerprint（observation_fingerprint） | `ISessionStore`（InMemory/FileBacked/DB）+ checkpoint journal + session resume/restore |
| 工具集动态切换 | `_ota_scope` 上下文管理器在 EnterAgent 时新建 OTAContext 实例，subclass 继承的 tools 自动按类声明装载 + per-call 过滤 `expose_tools` | call-agent fork 时把 agentModel 的 tool 集合整体带过去；`@Tool.tags` + agent.activeTags 过滤 |
| 工作流模式 | `on_workflow` 异步生成器 + yield ActionCall/HumanCall/LLMCall/EnterAgent/RETURN | xwf 工作流（DSL 驱动）+ `TeamTaskFlowOrchestrator`（DAG）+ nop-task runtime |
| DSL | **ASL**（Agent Structure Language，Python-native DSL，类级 canvas 装饰器 + `_Canvas` 追踪命名空间 + `_Fragment` 复用）+ bridgic-core 自带 `worker`/`ferry_to` DDG 原语 | XLang 通用 DSL（xdef 校验 + XScript / Xpl / Xpl 标签）+ agent.xdef 专门的 Agent DSL + plan.xdef |
| 序列化 / Replay | `AgentTrace.save/load` + `observation_fingerprint`（SHA-256 prefix） + 工作目录 dump | checkpoint snapshot（LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR）+ session restore（`restoreSession`） + checkpointSeq 跨 execute() 单调 |
| License | MIT | Apache 2.0（Nop 平台） |

**核心结论先行**：Bridgic 把 2026 年"agent 框架"的两条主流路线（**workflow-first / agent-first**）通过 **DDG runtime + yield 协议 + 双 Context** 三件套合并成一条——同一份 `AmphibiousAutoma` 类既能只跑确定性 workflow、也能只跑 LLM agent、还能跑 "workflow-first with agent fallback" 的 AMPHIFLOW（这是该框架最独特的卖点）。nop-ai-agent 是**典型的 ReAct 单循环 + 多周边机制（plan/team/actor/memory/skill/security/compaction/reliability）**路线，运行时循环模型只有 reactLoop + sustainLoop 两种，**没有原生 workflow 概念**，workflow 走 xwf DSL + TeamTaskFlowOrchestrator 在另一个模块编排。对 nop-ai-agent 的三层价值：**模式可借鉴**（AmphibiousFSM 的双层 FSM + step-level fallback）、**抽象可借鉴**（CognitiveWorker/AgentWorker 对称 peer 设计 + OTAContext.tool 声明式生命周期）、**不可借鉴**（Python yield 协议对 Java 价值有限，asyncio 单线程 + FastMCP HTTP host 是 in-process 不能拆出）。

---

## 二、Context（调研背景）

- **为什么需要这个分析**：bridgic 在 2026-07 中旬做了**一次彻底的架构 refactor**——从"旧的多级 Context（多个 framework-owned context 类 + 大量隐式字段）"重构成 **OTAContext（small-loop，framework-owned）+ Context（big-loop，free-form）** 的两段语义不变式（commit `fe5af6c refactor(amphibious): rebuild the cognitive layer on the OTA context model`），并在 2026-08-04 发布 bridgic-amphibious 0.2.0。这是该框架有史以来最彻底的一次重构——`bridgic-amphibious` 包的核心文件（`_amphibious_automa.py` 2867 行 + `_context.py` 367 行 + `_cognitive_worker.py` 328 行 + `_agent_worker.py` 540 行 + `_type.py` 547 行）几乎全部受影响。`agent-survey/` 此前对 bridgic 无任何调研记录，需要新建。
- **要回答的问题**：(1) 两段 Context 不变式（OTAContext vs Context）的设计动机和收益；(2) AmphibiousFSM（workflow-first + step-level fallback + full fallback）的状态机结构；(3) CognitiveWorker / AgentWorker 对称 peer 设计如何统一内 LLM 思考 vs 外 CLI 委派；(4) 对 nop-ai-agent 的可借鉴点与不可借鉴点。
- **约束**：bridgic 是 Python 异步产物（`bridgic-amphibious` 10 个核心 py 文件共 5842 行 + `bridgic-core` 103 个 py 文件 + `bridgic-asl` 5 个 py 文件）；nop 是 Java/DSL 栈。本分析侧重**机制与契约**而非代码搬运；详细代码已 `~/ai/bridgic` 完整 clone。

---

## 三、版本与时间线（hard facts）

| 项 | 值 |
|---|---|
| 仓库 | `github.com/bitsky-tech/bridgic` |
| 创建时间 | 2026-04-17（首 commit） |
| 当前版本 | top-level `bridgic` 0.4.1，依赖 `bridgic-amphibious>=0.2.0,<0.3.0`、`bridgic-core>=0.3.0,<0.4.0`、`bridgic-asl>=0.1.1,<0.2.0` |
| 作者 | Tielei Zhang（zhangtl04@gmail.com）单作者主导，外部贡献者 RheagalFire / bitsky-tech review |
| 许可证 | MIT |
| 规模 | `bridgic-amphibious` 32 个 py 文件 + `bridgic-core` 103 个 py 文件 + `bridgic-asl` 5 个 py 文件 + `bridgic-integration/llms` 多个 provider 适配器 |
| 总 LOC | 仅 `bridgic-amphibious` 核心 5842 行；`bridgic-core` DDG runtime + recent memory + tool specs ~5000 行；README ~1400 行（与代码规模相当） |
| Python 版本 | >=3.9（README 要求） |
| 安装 | `pip install bridgic` 或 `uv add bridgic` |
| 运行 | `from bridgic.amphibious import AmphibiousAutoma, ...` |
| 最近重大变更 | 2026-08-04 bridgic-amphibious 0.2.0：OTAContext / Context 双段不变式重构（commit `fe5af6c` + `14c2dd1` + `6c356e2`）；同期集成 LiteLLM（commit `b6a2244`） |

### 3.1 8 月 4 日重构（本次分析的核心）

bridgic-amphibious 0.2.0 把"framework-owned 上下文"从**多类多字段**（之前的 design 中存在多个 framework-managed context 类，含大量 framework-injected field）收敛到**两段语义不变式**：

1. **`Context`**（free-form，base class）— `model_config = ConfigDict(arbitrary_types_allowed=True)` + 任何字段 + overridable `summary(fields)`。**不携带 tools**（这是关键设计——工具是 OTA-loop concern，不属于 base Context）。
3. **`OTAContext(Context)`**（framework-owned small-loop）— 新增 `user_input: Any` + `ota_record: List[OTARecord]` + `tools: List[ToolSpec]`；新增 `tool` 类装饰器（声明式）+ `add_tool` 实例方法 + `_current_record` 内部访问器 + `obs_result/think_result/action_result` 三套属性 setter（fold 模式）。

**动机**：旧版本 "framework 在 build 时往 context 上注入各种字段" 导致 (a) 上下文对象生命周期不清晰（谁负责字段初始化/清理）；(b) 工具集与 base context 耦合（Context 想用工具必须继承 framework 类）；(c) per-round 数据（obs/think/action）散落在 framework 字段，没有 trace 概念。新设计把"框架拥有的"和"用户拥有的"严格二分：OTAContext 是 framework-owned 的（每次 EnterAgent 创建新实例），Context 是 free-form 的（运行时构造一次，可改字段）。

---

## 四、核心架构：两段 Context 不变式 + AmphibiousFSM + 对称 Peer Worker

### 4.1 双段 Context 不变式（最关键创新）

#### 4.1.1 概念：Context（user-owned free-form）vs OTAContext（framework-owned small-loop）

**`Context`（base class）**——用户声明任何字段，是他们的，不是框架的。

**`OTAContext(Context)`**（framework-owned）——每轮 OTA 数据 + 工具集。

**为什么继承而非并列**：OTAContext "is-a" Context（也是 Pydantic BaseModel，可以任意字段），但 OTAContext **强制注入 framework-owned 字段**（user_input / ota_record / tools），并通过类装饰器 `tool()` 锁定工具集由 framework 管理。

```python
class Context(BaseModel):
    """Base context — fields + overridable summary(). Free-form, cross-turn state."""
    model_config = ConfigDict(arbitrary_types_allowed=True)

    def _raw_fields(self) -> Dict[str, Any]:
        return {name: getattr(self, name) for name in type(self).model_fields}

    def summary(self, fields: Optional[Dict[str, Any]] = None) -> Any:
        return fields if fields is not None else self._raw_fields()


class OTAContext(Context):
    """Framework-owned small-loop context — user_input + OTA round trace + tools."""
    _declared_tools: ClassVar[List[ToolSpec]] = []  # class-level registry

    user_input: Any = ""
    ota_record: List[OTARecord] = Field(default_factory=list)
    tools: List[ToolSpec] = Field(default_factory=list)

    @classmethod
    def tool(cls, obj):
        """声明式工具注册 — 类装饰器 / 直接调用皆可。"""
        cls._declared_tools.append(_to_tool_spec(obj))
        return obj

    def add_tool(self, tool: ToolSpec) -> None:
        self.tools.append(tool)

    # 每 round 的 fold-mode 访问器
    @property
    def obs_result(self):
        return self.ota_record[-1].observation_result if self.ota_record else None
    @obs_result.setter
    def obs_result(self, value):
        self._current_record().observation_result = value
    # think_result / action_result 同理

    def summary(self, fields=None) -> str:
        # 默认渲染：user input + OTA round trace
        parts = [f"User input: {self.user_input}"]
        for i, record in enumerate(self.ota_record):
            parts.append(f"[Round {i}]")
            if record.observation_result is not None: parts.append(f"  Observation: {record.observation_result}")
            if record.think_result is not None:         parts.append(f"  Think: {record.think_result}")
            if record.action_result is not None:         parts.append(f"  Action: {record.action_result}")
        return "\n".join(parts)
```

#### 4.1.2 双段分别放什么

| 放哪里 | 字段类别 | 例子 |
|---|---|---|
| **OTAContext**（framework-owned） | 本次 run 的 input | `user_input = "修复 bug"` |
| | 每轮 OTA trace | `ota_record = [OTARecord{obs, think, action}, ...]` |
| | 本次 run 的工具集（action phase 能力） | `tools = [read_file_tool, bash_tool, ...]` |
| **Context**（user-owned free-form） | 跨 turn 知识 | `goal`, `conversation_history`, `user_preferences` |
| | 跨 turn 状态 | `total_tokens_used`, `session_start_time` |
| | 业务对象 | `Order(id=..., items=...)`, `UserProfile(...)` |
| | 长期 facts | `skills_loaded`, `facts_learned` |

**两边严格不混**：用户字段进 OTAContext 会被框架误当 OTA 字段处理；框架字段进 Context 会被框架忽略，破坏 lifecycle。

#### 4.1.3 双段的好处

1. **Lifecycle 清晰** — OTAContext = framework lifecycle（per run / per EnterAgent  fresh 实例）；用户不能"重置" framework 字段——它由框架管理。Context = user lifecycle（per session 持久；用户自己决定何时创建、何时更新）。避免"用户字段被 framework reset" / "framework 字段被用户覆盖"。

2. **工具注册位置严格** — 工具只属于 OTAContext，不属于 base Context。这避免了"free-form Context 突然有了 tools字段"这种抽象泄漏——Context 是"用户数据"，tools 是"framework loop 关注的能力"。用户声明 `MyOTAContext.tool(my_tool)` ——显式生命周期；框架**不自动注入任何工具**。

4. **trace 与 action 严格绑定** — `ota_record` 是天然的 per-round trace。一次 `arun()` 后完整序列化 = `ota_ctx.summary()`，可直接 dump 为 trace。跨 turn 知识（Context）和 trace（OTAContext）分别序列化为不同 artifact —— 用于不同目的（Context 用于 session restore，OTAContext 用于 run replay）。

5. **fresh-instance 隔离 = 并发安全** — 每次 EnterAgent  都新建 OTAContext 实例，**父 ctx 不被 mutate**。多 sub-agent 并发执行时各自的 OTAContext 独立，无锁。bridgic 是单线程 asyncio，但这个设计可以**直接搬到多线程 Java runtime**——每个 sub-agent 持自己独立的 OTAContext 实例。

6. **OTARecord `extra="allow"` 是 hook 扩展点**（关键好处）：

```python
class OTARecord(BaseModel):
    model_config = ConfigDict(extra="allow")  # ← 关键
    observation_result: Optional[Any] = None
    think_result: Optional[Any] = None
    action_result: Optional[Any] = None

# Hook 可以向当前轮折叠任意字段，无需 subclass
async def before_action(self, ota_ctx, context=None):
    ota_ctx._current_record().permission_result = "approved"
    ota_ctx._current_record().tool_retry_count = 3
    # 不需要继承 OTARecord 添加字段

async def after_action(self, ota_ctx, context=None):
    ota_ctx._current_record().oracle_response = "A"  # HITL oracle 调用的回复
```

**对 nop 的真实价值（再 review）**：

- **OTAContext 双段不变式 = 概念参考**（P2 cleanliness）——拆 `AgentExecutionContext` 为 framework-owned + user-owned 是 concept 清晰化重构，**无 user-facing bug 驱动**。
- **`extra="allow"` 模式 = 真价值**（P1）——Java 等价物 `RoundRecord.extras: Map<String, Object>`。hook 不通过 `ctx.getMetadata()` 而是直接向当前 round 折叠字段，**语义更清晰**（`permission_result` 不应该和"用户自定义 metadata" 混在一起）。
- **工具严格属于 OTAContext**——是 design discipline 提醒：nop 当前的 `IToolManager` 是引擎级全局注册表，**OTA 工具是 per-instance 集**。这意味着 bridgic 的"per-arun 工具集可重写" + "per-EnterAgent  工具集可过滤" 比 nop 的"全局 + activeTags 过滤"更精细。

**对 nop 的启发**：
- nop 当前 **VFS + XLang DSL 组件模型注册表**（`IToolManager` 经 `ResourceComponentManager.loadComponentModel` 从 `/nop/ai/tools/*.tool.xml` 装载），**非反射扫描**。`OTAContext.tool` 的"显式声明在类上"的契约更干净（Python-specific 优势，Java 用不上）。
- nop 当前 `AgentExecutionContext` 把所有东西塞一个类（messages、plan、sessionId、chatOptions、metadata、status、tokensUsed、delegationDepth、steeringQueue...）。可以借鉴"framework-owned vs user-owned 二分"做减法。

### 4.2 AmphibiousFSM（workflow-first + step-level fallback + full fallback）

```python
class _AmphiState:
    """Per-AMPHIFLOW-run FSM state. Held on ``self._amphi`` for one _amphiflow call."""
    workflow_gen: Any
    workflow_send: Any = None
    agent_gen: Optional[Any] = None
    agent_send: Any = None
    scope: Literal["workflow", "agent"] = "workflow"
    agent_mode_stack: Optional[AsyncExitStack] = None
    failed_steps: List[str] = field(default_factory=list)
    max_consecutive_fallbacks: int = 1
    consecutive_failures: int = 0
    step_index: int = 0
    return_value: Any = None
    should_break: bool = False

class AmphibiousAutoma(GraphAutoma, Generic[OTAContextT, ContextT]):
    """Base class — dual-mode orchestration engine."""

    def __init_subclass__(cls, ...):
        # 1. 从 Generic[OTAContextT, ContextT] 参数提取两 Context 类
        cls._detect_context_classes()
        # 2. 扫描 @human_channel 装饰的方法，构建 channel-name → method-name 注册表
        cls._build_human_channel_registry()
        # 3. 验证所有可重写模板方法是 async-generator（带 yield）
        cls._validate_template_forms()

    async def arun(self, *, user_input="", llm=None, context=None,
                   ota_context=None, mode=RunMode.AUTO,
                   max_consecutive_fallbacks=1, trace=False, workdir=None):
        # mode=AUTO 时：仅 on_agent → AGENT，仅 on_workflow → WORKFLOW，两者都有 → AMPHIFLOW
        ...
```

**`RunMode`**：

| 值 | 触发条件 | 行为 |
|---|---|---|
| `AGENT` | 仅 `on_agent` 被 override | 仅跑 agent 模式 |
| `WORKFLOW` | 仅 `on_workflow` 被 override | 仅跑 workflow 模式 |
| `AMPHIFLOW` | 两者都 override（且 mode=AUTO 或显式 AMPHIFLOW） | **workflow-first + step-level fallback on atomic-Call failure + full fallback if threshold breach** |
| `AUTO` | 默认 | 根据 override 情况自动推导 |

**Yield 协议（在 on_workflow/on_agent 内部 yield 这些对象）**：

```python
# Atomic Calls — 确定性操作
@dataclass(init=False)
class ActionCall:   # 单工具执行
class HumanCall:     # 暂停 + 等人输入（通过 @human_channel）
class EnterAgent:    # 模式切换原语：workflow → agent
class LLMCall:       # 直接 LLM 调用（chat/structure_output/tool_selector）

# Cognitive composition — 在 on_agent 内部 yield
class ThinkUnit:     # 调度 class-level 声明的 CognitiveWorker（named + until/max_attempts overlays）
class ThinkAgent:    # 调度 class-level 声明的 AgentWorker（外部 CLI 委派，expose_tools 过滤）

# Control flow
class RETURN:        # PEP 525 禁止 async generator 用 return value；用 RETURN(value) 作为框架层 workaround
```

**`_amphiflow` 主循环**（核心 FSM）：

```python
async def _amphiflow(self, max_consecutive_fallbacks: int) -> str:
    fsm = _AmphiState(workflow_gen=self.on_workflow(self.ota_ctx, self.ctx), ...)

    while not fsm.should_break:
        # 选择当前激活的 generator slot
        gen, send = (fsm.agent_gen, fsm.agent_send) if fsm.scope == "agent" \
                    else (fsm.workflow_gen, fsm.workflow_send)

        try:
            item = await (gen.__anext__() if send is None else gen.asend(send))
        except StopAsyncIteration:
            if fsm.scope == "agent":
                # Agent 自然 exhaust → 默认恢复到 workflow（如果有）；否则结束
                ...
            else:
                fsm.should_break = True
        except Exception as e:
            if fsm.scope == "agent":
                raise  # agent 错误不回退
            # workflow 内部错误 → 全 fallback（关闭 workflow_gen，进入 agent mode）
            fsm.workflow_gen = None
            await self._enter_agent()
            continue

        # RETURN 是控制流信号
        if isinstance(item, RETURN):
            fsm.return_value = item.value
            fsm.should_break = True
            continue

        try:
            outcome = await self._dispatch_step(item, scope=fsm.scope)
        except Exception as e:
            if fsm.scope == "agent" or not _is_atomic_step(item):
                raise  # agent 错误 / 非原子 yield → 传播

            # step-level fallback 计数
            fsm.consecutive_failures += 1
            fsm.step_index += 1
            fsm.failed_steps.append(f"Step {fsm.step_index}: {_describe_atomic_step(item)} — {e}")

            # 连续失败超阈值 → 全 fallback
            if fsm.consecutive_failures >= fsm.max_consecutive_fallbacks:
                await fsm.workflow_gen.aclose()
                fsm.workflow_gen = None
                await self._enter_agent()
                continue

            # step-level fallback：跑 bounded 内联恢复子 run，结果 shaped 成失败 step 的返回类型 asend 给工作流
            fallback_goal = _build_fallback_goal(item, _describe_atomic_step(item), e, fsm)
            recovered = await self._run_fallback_agent(fallback_goal)
            fsm.workflow_send = _shape_fallback_value(item, recovered)
            self._final_answer = None  # 清除恢复子 run 写的 final_answer
            continue
        else:
            if fsm.scope == "agent":
                fsm.agent_send = outcome
            else:
                fsm.workflow_send = outcome
                fsm.consecutive_failures = 0
                fsm.step_index += 1

    return self._final_answer or self.ota_ctx.summary()
```

**关键设计决策**：

1. **workflow 是默认主循环**——`workflow_gen` 在 `_amphi` 构造时立即创建（`self.on_workflow(self.ota_ctx, self.ctx)`），agent 是被动切换。
2. **EnterAgent 是唯一显式 workflow → agent 模式切换**——`agent_mode_stack: AsyncExitStack` 保存切换时的 scope swap，agent generator exhaust 时自动 aclose。
3. **step-level fallback 是 bounded 内联 sub-run**——`_run_fallback_agent` 跑 `on_agent` 在 fresh OTAContext 里，shaping 函数把结果映射成失败 step 的返回类型（ActionCall → List[ToolResult]、HumanCall → str、LLMCall → 协议对应形态）。`consecutive_failures` 累积到 `max_consecutive_fallbacks` 后升级为 full fallback（关闭 workflow_gen）。
4. **agent → workflow 是隐式（exhaustion = return）**——`_dispatch_step` 注释中明确写到这是有意的非对称设计，并 TODO 一个对称原语 `enter_workflow(value)`。

**对 nop 的启发**：
- nop 当前 `ReActAgentExecutor` 是单一 reactLoop + sustainLoop，没有 "workflow → agent" 模式切换原语。`EnterAgent` 的"显式 + fresh-instance 隔离"是值得借鉴的。
- nop 当前的"原子 step 失败"通过 `LlmCallCoordinator` + circuit breaker 处理，没有 step-level fallback + full fallback 的分层。
- nop 的 `ReActAgentExecutor` 注释说 "execute(ctx) 是单实例，没有 fork/restore of the context"——bridgic 的 fresh-instance 隔离（每次 EnterAgent / ThinkAgent 都新建 OTAContext）是个清晰解。

### 4.3 CognitiveWorker + AgentWorker 对称 Peer 设计

```python
class CognitiveWorker(GraphAutoma):
    """In-process think unit — one observe-think-act cycle, anchored on a BaseLlm."""
    _llm: BaseLlm

    @worker(is_start=True, is_output=True)
    async def _thinking(self, ota_context, context=None) -> Any:
        if self._llm is None: raise RuntimeError(...)
        if ota_context is None: ota_context = OTAContext()

        result = await self.thinking(ota_context, context)  # 用户 override 的模板方法
        return self._assemble_decision(result)              # 框架的 adaptation 层

    def _assemble_decision(self, result: Any) -> ThinkResult:
        """把任意 LLM 协议结果（Response / (tool_calls, content) / BaseModel / dict / str）适配成 ThinkResult。"""
        if isinstance(result, Response):
            return ThinkResult(step_content=result.message.content or "", tool_calls=[])
        if isinstance(result, BaseModel):
            return ThinkResult(step_content=result.model_dump_json(), tool_calls=[])
        if isinstance(result, dict):
            return ThinkResult(step_content=json.dumps(result, ensure_ascii=False, default=str), tool_calls=[])
        if isinstance(result, str):
            return ThinkResult(step_content=result, tool_calls=[])
        # tool_calls first
        tool_calls, content = result
        return ThinkResult(step_content=content or "",
                           tool_calls=[StepToolCall(call_id=..., tool=..., tool_arguments=[...]) for ...])

    # 用户 override 的模板方法（coroutine 形态）
    async def observation(self, ota_context, context=None) -> Any: return _DELEGATE
    async def thinking(self, ota_context, context=None) -> Any: raise NotImplementedError(...)
    async def before_action(self, ota_context, context=None) -> Any: return _DELEGATE
    async def after_action(self, ota_context, context=None) -> Any: return _DELEGATE


class AgentWorker(GraphAutoma):
    """Out-of-process think unit — one delegated cycle, anchored on a BaseAgent (CLI driver)."""
    _agent: BaseAgent  # ClaudeCodeAgent / CodexAgent / subclass

    @worker(is_start=True, is_output=True)
    async def _think(self, ota_context, context=None) -> Any:
        return await self._run_think(ota_context, context)

    async def _run_think(self, ota_context, context) -> AgentResult:
        # 1. MCP-ify ctx.tools → 启动 in-process FastMCP HTTP host（project tools + agent_done 信号）
        # 2. 组装 message 通过 thinking() 模板
        # 3. 打包 AgentRequest（message + cwd + mcp_servers + allow-list + completion future）
        # 4. await self._agent.run(request)  ← BaseAgent 拥有 CLI mechanics
        # 5. 关闭 host；返回 AgentResult
        ...
```

**`BaseAgent`**（外部 coding-agent CLI 抽象）：

```python
class BaseAgent:
    """Abstract driver for one external coding-agent CLI."""

    async def run(self, request: AgentRequest) -> AgentResult:
        raise NotImplementedError(...)  # subclass 必须 override

    async def _run_subprocess(self, argv, *, stdin_payload=None, cwd=None, env=None,
                               timeout=180.0, done_signal=None) -> Tuple[Optional[str], Optional[int], str]:
        # 通用 sub-process spawn / drain / wait helper
        # Race: done_signal (MCP agent_done) vs process exit vs timeout
        proc = await asyncio.create_subprocess_exec(*argv, stdin=PIPE, stdout=PIPE, stderr=PIPE, ...)
        if proc.stdin and stdin_payload: proc.stdin.write(stdin_payload); await proc.stdin.drain()
        proc.stdin.close()
        stdout_task = asyncio.create_task(_consume_stream(proc.stdout))
        stderr_task = asyncio.create_task(_consume_stream(proc.stderr))
        proc_wait_task = asyncio.create_task(proc.wait())
        # Race done_signal vs proc_wait vs timeout
        ...
        return output, exit_code, completion


class ClaudeCodeAgent(BaseAgent):
    """`claude -p` driver."""
    DEFAULT_BUILTIN_TOOLS = ("Read", "Write", "Edit", "Bash", "Glob", "Grep")
    def __init__(self, *, bin="claude", allowed_builtin_tools=None,
                 permission_mode="bypassPermissions", completion_timeout=180.0):
        self.bin = bin
        self.allowed_builtin_tools = list(allowed_builtin_tools) or list(self.DEFAULT_BUILTIN_TOOLS)
        self.permission_mode = permission_mode
        self.completion_timeout = completion_timeout

    async def run(self, request: AgentRequest) -> AgentResult:
        mcp_config_path = self._write_mcp_config(request.cwd, request.mcp_servers)
        argv = [self.bin, "-p", "--mcp-config", str(mcp_config_path), "--strict-mcp-config",
                "--input-format", "stream-json", "--output-format", "stream-json",
                "--verbose", "--permission-mode", self.permission_mode,
                "--allowedTools", ",".join(self.allowed_builtin_tools + request.allowed_tools),
                "--no-session-persistence"]
        payload = (json.dumps({"type": "user", "message": {"role": "user", "content": request.message}}) + "\n").encode()
        output, exit_code, completion = await self._run_subprocess(
            argv, stdin_payload=payload, cwd=request.cwd,
            timeout=self.completion_timeout, done_signal=request.done_signal)
        return AgentResult(output=output, exit_code=exit_code, completion=completion)


class CodexAgent(BaseAgent):
    """`codex exec` driver — OpenAI Codex CLI."""
    def __init__(self, *, bin="codex", sandbox_mode="workspace-write", completion_timeout=180.0):
        self.bin = bin; self.sandbox_mode = sandbox_mode; self.completion_timeout = completion_timeout

    async def run(self, request: AgentRequest) -> AgentResult:
        argv = [self.bin, "exec", "--cd", str(request.cwd), "--skip-git-repo-check",
                "--ephemeral", "--sandbox", self.sandbox_mode, "--ignore-user-config",
                "-c", "approval_policy=never"]
        for name, spec in request.mcp_servers.items():
            url = spec.get("url")
            if url:
                argv += ["-c", f"mcp_servers.{name}.url={url}",
                         "-c", f"mcp_servers.{name}.default_tools_approval_mode=auto"]
        argv.append("-")  # read prompt from stdin
        output, exit_code, completion = await self._run_subprocess(
            argv, stdin_payload=request.message.encode(), cwd=request.cwd,
            timeout=self.completion_timeout, done_signal=request.done_signal)
        return AgentResult(output=output, exit_code=exit_code, completion=completion)
```

**关键设计决策**：

1. **CognitiveWorker 和 AgentWorker 都是 `GraphAutoma` 子类**——`@worker(is_start=True, is_output=True)` 是同一个框架原语。两边都有 `_assemble_decision` 的对称适配层。
3. **ThinkResult 是统一数据契约**——`{step_content: str, tool_calls: List[StepToolCall]}`；`tool_calls` 为空即为 finish（无显式 finish flag）。
4. **`_DELEGATE` sentinel**——Worker 的 hook 返回它表示"转交到 agent 层 hook"；返回具体值则覆盖。
5. **`agent_done` MCP signal tool**——MCP host 暴露 `agent_done(result: str)` 工具，external CLI 必须调用它一次以触发 `done_signal` 完成事件；race against process exit / timeout。
6. **stdout / stderr 内容丢弃**——只有 `agent_done` MCP 工具调用或 project tool MCP 调用才能"出"数据；这是显式的"stdout 是噪声，事实是 MCP 调用"边界。

**对 nop 的启发**：
- nop 当前无反射扫描机制（`grep -rn "@Tool\b" nop-ai-toolkit/src/main` 零命中）；`CognitiveWorker` 的"继承 + override `thinking` + 框架适配" 是 Python-specific 模板模式，nop 现有 `ReActAgentExecutor` 没有对应重构需求。
- `AgentWorker` 的"外部 CLI + in-process MCP bridge" 是 `call-agent` 的"内部 sub-agent"做不到的——它把"父 agent 的项目工具"通过 MCP 协议暴露给"外部子 CLI agent"，子 agent 调用任何项目工具都经父 agent 的 hook 链。
- nop `call-agent` 当前有两条路径：(a) **sync fork+exec**——直接 `engine.execute()`（同进程同步等结果）；(b) **async mailbox**——经 `agent.call-agent` topic 投递到 `IMessageService.request()`，可经 `DBMessageService`（plan 224）实现跨进程。两条路径共享同一个 `CallAgentRequestPayload` 不可变载荷。**[已 review 修正]** 原报告误称为"仅同进程 fork session"。

### 4.4 `MCPHost`（in-process FastMCP HTTP server）

```python
class MCPHost:
    """A FastMCP host bound to the running asyncio loop."""

    def __init__(self, *, server_name, bindings, on_tool_call, on_agent_done,
                 host="127.0.0.1", port=0):
        ...

    async def start(self):
        app = self._build_app()
        self.port = self._reserve_port()
        config = uvicorn.Config(app=app, host=self.host, port=self.port, log_level="warning", lifespan="on")
        self._uv_server = uvicorn.Server(config)
        self._serve_task = asyncio.create_task(self._uv_server.serve())
        await self._await_started(timeout=15.0)
        self.url = f"http://{self.host}:{self.port}/mcp"

    def _build_app(self):
        mcp = FastMCP(self.server_name)
        for binding in self.bindings:
            handler = _build_handler(binding, on_tool_call)  # 生成匹配 JSON Schema 的 Python 函数
            mcp.tool(name=binding.name, description=binding.description)(handler)

        @mcp.tool(name="agent_done", description="...")
        async def agent_done(result: str) -> str:
            on_agent_done(result)
            return "Acknowledged. Goal recorded as complete; you may finish now."

        return mcp.http_app()
```

**关键决策**：

- **每个 AgentWorker 委派独立 MCP host 实例**——`start() → url → 传给 AgentRequest.mcp_servers → 父 Amphi._run_think_agent 创建 consumer task 监听 decision_channel → agent close → MCP host stop`。
- **handler 用 `exec` 生成**——FastMCP 通过 `inspect.signature(handler)` 推断 schema；从 `binding.parameters`（JSON Schema）反推 required vs optional + 类型，生成匹配的 Python async 函数。这是"协议镜像" 的具体实现——fastmcp 3.x 没有 imperative JSON Schema 注入，只能通过 signature 注入。
- **端口 0 + 系统分配**——`_reserve_port()` 用 `socket.bind` 占位获取空闲端口；多并发委派不会冲突。

**对 nop 的启发**：
- **[已 review 修正]** 原报告称 nop 当前 call-agent 是同进程 fork，事实是 nop call-agent 有 sync fork+exec + async mailbox 两条路径；async 路径经 IMessageService 可跨进程。in-process MCP host 主要为 bridgic 外部 CLI 委派场景设计，nop 不需要。
- 但 `MCPHost` 的"每次委派一个独立 host + JSON Schema 镜像"模式可借鉴为 nop-ai-mcp-server 的子代理 bridge 设计（如果将来需要 sub-CLI-agent）。

### 4.5 `_dispatch_step` yield 路由

```python
async def _dispatch_step(self, item, *, scope="hook") -> Any:
    if isinstance(item, EnterAgent):
        if scope != "workflow":
            raise RuntimeError("EnterAgent is workflow-only.")
        if not self._has_agent():
            raise RuntimeError("EnterAgent requires on_agent() override.")
        return await self._enter_agent(item=item)

    if isinstance(item, HumanCall):
        if scope == "agent":
            raise RuntimeError("HumanCall is not allowed inside on_agent.")
        return await self._run_human_call(item)

    if isinstance(item, LLMCall):
        # 三种 protocol：chat / structure_output / tool_selector；scope=agent 抛错
        ...

    if isinstance(item, ActionCall):
        return await self._run_action_call(item, _worker=...)  # 不在 hook scope 时 with_hooks=False

    if isinstance(item, ThinkUnit):
        if scope != "agent":
            raise RuntimeError("ThinkUnit is agent-only.")
        return await self._run_think_unit(item)

    if isinstance(item, ThinkAgent):
        if scope != "agent":
            raise RuntimeError("ThinkAgent is agent-only.")
        return await self._run_think_agent(item)

    raise RuntimeError(f"Unknown yield type: {type(item).__name__}")
```

**Scope rules**（yield 类型与产生位置的双重校验）：

| primitive | on_workflow | on_agent | hooks |
|---|---|---|---|
| ActionCall | ✓ | ✗ | ✓ |
| HumanCall | ✓ | ✗ | ✓ |
| LLMCall | ✓ | ✗ | ✓ |
| EnterAgent | ✓ | ✗ | ✗ |
| ThinkUnit | ✗ | ✓ | ✗ |
| ThinkAgent | ✗ | ✓ | ✗ |
| RETURN | ✓ | ✓ | ✓ |

**核心要点**：

- **`ActionCall` 在 hook scope 时 `with_hooks=False`**——hook scope 是 hook 内部 yield 出来的 ActionCall（已通过 hook 链处理过），再走 hook 链会递归。
- **`HumanCall` 不允许在 `on_agent`**——因为 `on_agent` 是 LLM-driven，应当通过 `request_human_tool` 让 LLM 主动调工具。
- **`LLMCall` 在 agent scope 抛错**——agent 已经用 ThinkUnit 驱动 LLM，LLMCall 是冗余。

### 4.6 `_run_think_unit` / `_run_think_agent`（cognitive composition drivers）

二者结构对称：

- **`_run_think_unit`**：克隆 worker template（state isolation）→ fresh nested OTAContext（`user_input` + `tools`） → 跑 `_run_think_unit_body`（OTC observe-think-act 循环，user 可重写 `until` lambda / `on_error` / `max_retries`）。
- **`_run_think_agent`**：克隆 worker template → fresh nested OTAContext（`sub_goal` + `_filter_tools(parent_ota.tools, expose_tools)`）→ 创建 `decision_channel: asyncio.Queue` → 创建 consumer task `_execute_decisions`（pull decision，run via `_run_action_call`，resolve future）→ 启动 consumer，run `_run_think_agent_body`（含 `MCPHost` 启动 + agent.run() + host stop）→ `_DELEGATION_DONE` sentinel 关闭 consumer。

**`expose_tools` 是构造期 narrowing**——不是渲染时过滤，sub-context 真不携带未列出的 tool。这是"硬隔离"原则的体现。

### 4.7 OTARecord 的 `extra="allow"` 设计

```python
class OTARecord(BaseModel):
    """One OTA (observe-think-act) round."""
    model_config = ConfigDict(extra="allow")
    observation_result: Optional[Any] = None
    think_result: Optional[Any] = None
    action_result: Optional[Any] = None
```

**为什么 `extra="allow"`？** 用户可向当前 round 折叠自定义字段（如 `permission_result`、`retry_count`），无需子类化。这是协议扩展点。

**用法示例**：
```python
async def before_action(self, ota_context, context=None):
    ota_context._current_record().permission_result = "approved"
```

### 4.8 built-in tools（human + shell + filesystem）

```python
ALL_BUILTIN_TOOLS = (
    request_human_tool,        # Human
    bash_tool,                 # Shell
    read_file_tool,
    write_file_tool,
    edit_file_tool,
    glob_tool,
    grep_tool,                 # Filesystem
)
```

`request_human_tool` 是个 `FunctionToolSpec`，内部通过 `contextvars.ContextVar` 解析"当前运行的 agent"，调用 `agent._run_human_call(HumanCall(prompt=..., channel=...))`：

```python
async def request_human(prompt: str, channel: Optional[str] = None) -> str:
    agent = current_agent.get(None)
    if agent is None:
        raise RuntimeError("request_human can only be called during agent execution.")
    return await agent._run_human_call(HumanCall(prompt=prompt, channel=channel))
```

**关键**：所有内置工具都是 `FunctionToolSpec`——可直接 `MyOTAContext.tool(request_human_tool)` 声明。任何 OTAContext 都可声明 HITL，不需 agent mode。

### 4.9 DDG runtime（bridgeic-core 0.3.x）

```python
class GraphAutoma(Automa, metaclass=GraphMeta):
    """Dynamic Directed Graph (DDG) runtime."""

    def ferry_to(self, key: str, /, *args, **kwargs):
        """Defer invocation to the specified worker; creates a delayed call,
        ensuring worker will be scheduled asynchronously in the next event loop,
        independent of its dependencies."""
        ...
```

**核心**：worker 通过 `@worker` 装饰器注册；初始拓扑由 `@worker(dependencies=[...])` 定义；运行时通过 `self.ferry_to(key)` 触发 **conditional branching** 或 **cyclic graph**。**DDG 是 bridgeic 一切的底座**——`AmphibiousAutoma` 继承 `GraphAutoma`，CognitiveWorker / AgentWorker 也继承 `GraphAutoma`。

---

## 五、与 nop-ai-agent 当前设计的对比

### 5.1 整体对比表

| 维度 | Bridgic Amphibious | nop-ai-agent |
|---|---|---|
| **核心循环** | `AmphibiousAutoma[OTAContext, Context]` yield-driven FSM（workflow-first / agent-first / AMPHIFLOW） | `ReActAgentExecutor` while 双层（reactLoop + sustainLoop），无 workflow 概念 |
| **Context 模型** | **双段**：OTAContext（framework-owned small-loop）+ Context（free-form big-loop），语义严格二分 | 单 Context：`AgentExecutionContext`（含 messages、plan、status、metadata、steeringQueue、delegationDepth、bailReason...），用户态与框架态混合 |
| **循环构造** | 每个 arun 都新建 OTAContext 实例；EnterAgent 再嵌套 fresh OTAContext | reactLoop 复用同一个 ctx；call-agent fork 时新建 childSessionId 但 ctx 仍共享 |
| **模式切换** | 显式 `EnterAgent` yield（workflow → agent）+ agent 自然 exhaustion 隐式回 workflow | 无模式切换；call-agent 是工具调用（agent → sub-agent 是异步/同步委派） |
| **Sub-agent 委派** | **内进程 CognitiveWorker**（LLM 思考）+ **外部 CLI AgentWorker**（通过 in-process FastMCP HTTP server 桥接），二者对称 peer | call-agent 工具：**(a) sync fork+exec 路径**——`IAgentEngine.execute()` 同进程同步；(b) **async mailbox 路径**——`agent.call-agent` topic 投递 `IMessageService.request()`，可经 `DBMessageService` 跨进程（plan 224） |
| **LLM 调用协议** | `BaseLlm.astructured_output/aselect_tool/achat/astream` 4 种协议 + `_assemble_decision` 统一适配为 ThinkResult | `IChatService.chat()` + `IChatModel` + `ModelKeys` (composite provider:model 路由) + `ChatOptions` 配置 |
| **工具注册** | **显式 OTAContext.tool 类装饰器**（声明式、subclass 继承、框架不自动注入） | nop-toolkit `IToolManager` 经 `ResourceComponentManager.loadComponentModel` 从 VFS `/nop/ai/tools/*.tool.xml` 装载 + `IToolExecutor` 执行链 + tool-tag system（**VFS + XLang DSL 组件模型注册表**，**非反射扫描**） |
| **HITL** | `@human_channel` 类装饰器（多通道）+ `HumanCall` yield + 内置 `request_human_tool`（任何 OTAContext 可声明） | 无显式 HITL；steering queue（Actor mailbox → ctx.steeringQueue）+ skill hooks |
| **持久化 / Replay** | `AgentTrace`（flat execution path recorder，workdir-based）+ `run_dir` + `observation_fingerprint`（SHA-256 prefix） | checkpoint journal（LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR）+ session restore + checkpointSeq 跨 execute() 单调 |
| **错误恢复** | AmphibiousFSM step-level fallback（bounded 内联 recovery sub-run）+ full fallback（关闭 workflow_gen）+ recovery cycle safety bound | **显式容错栈**：重试（指数退避全抖动 + Retry-After floor + 熔断 + 三通道故障转移）+ checkpoint journal + 幂等键发散检测 + 60s 恢复守护 + DB 接管锁 |
| **安全** | 轻量（request_human、@human_channel、agent_done 信号、permission_result via OTARecord.extra） | **纵深 7-checkpoint 链**：denial ledger 阈值 3 / post-denial 指纹 / 权限矩阵 / 路径检查含 symlink / 审批门 / 写冲突 fail-fast + SandboxBackend fail-closed |
| **DSL** | **ASL**（Agent Structure Language，Python-native DSL）+ bridgic-core 自带 `worker`/`ferry_to` DDG 原语 | XLang 通用 DSL + agent.xdef + plan.xdef（DSL-first） |
| **Plan / Goal** | 无显式 plan；OTARecord 是天然的"当前轮 + 历史"，但无 long-running plan 状态机 | **PlanExecutor 独立状态机**（phase 门控 / replanner / 停滞检测 / DAG 校验）+ goal-tracker 卡死检测 |
| **Team / Multi-agent** | 无显式 team 抽象（多 agent 通过 EnterAgent 嵌套实现） | **team/**（TeamManager / ACL / 8 成员配额 / TeamTaskFlowOrchestrator）+ `nop-task` 真实 DAG runtime |
| **Actor / Mailbox** | 无显式 actor（async-generator 单线程模型） | **runtime/** AgentActor + ActorRuntime + Mailbox + 跨进程 DbDaemonCoordinator |
| **License** | MIT | Apache 2.0 |

### 5.2 循环模型对比（核心）

| | Bridgic Amphibious | nop-ai-agent |
|---|---|---|
| 驱动方式 | **yield-driven coroutine FSM**（`__anext__` / `asend`） | `while (currentIteration < maxIterations)` reactLoop + sustainLoop |
| 入口模板 | `on_workflow` + `on_agent`（两个 async generator） | 一个 `execute(ctx)` 入口 + 多个 lifecycle hook（PRE_CALL/PRE_REASONING/POST_REASONING/PRE_ACT/POST_ACT 等 10+ 个） |
| 状态机 | `_AmphiState` dataclass（scope、workflow_gen、agent_gen、send、failed_steps...）显式 FSM | `_AmphiState` 字段分布：`ctx.currentIteration` + `ctx.status` + `reentryCounters` map + `consecutiveContinues` 等 |
| 模式切换 | `EnterAgent` yield（workflow → agent）+ agent exhaust 隐式回 workflow | 无模式切换原语 |
| Recovery | step-level fallback（bounded 内联 sub-run）+ full fallback + cycle safety bound | sustainer CONTIN（额外预算）+ checkpoint journal + 60s 恢复守护 |

**核心差异**：bridgic 把"模式切换 + 恢复"统一到一个 yield FSM；nop 把这些分散到 lifecycle hooks + sustainer + reliability 子系统。**架构哲学**：bridgic 是"少原语 + 组合性"；nop 是"多原语 + 显式容错栈"。

### 5.3 上下文模型对比（核心）

| | Bridgic OTAContext + Context | nop AgentExecutionContext + AgentSession |
|---|---|---|
| 类数量 | 2（OTAContext + Context），OTAContext 继承 Context | 2（AgentExecutionContext + AgentSession），但 AgentSession 是持久化对象 |
| 字段数量 | OTAContext ~4 字段 + Context 用户自定义 + OTARecord 3 字段 + extra="allow" | AgentExecutionContext ~25+ 字段（含 messages, plan, sessionId, chatOptions, metadata, status, tokensUsed, delegationDepth, steeringQueue, bailReason, budgetSnapshot, leaseLost, ...） |
| 字段生命周期 | OTAContext per-arun / per-EnterAgent fresh 实例；OTARecord per-round | AgentExecutionContext per-execute；AgentSession 跨 execute 持久化 |
| Framework-owned vs User-owned | **严格二分**：OTAContext = framework-owned；Context = user-owned | 混合：messages / status / tokensUsed 是 framework-owned；plan / chatOptions 是 mixed；metadata 是 user-owned |
| 工具注册位置 | `OTAContext.tool` 类装饰器 | nop-toolkit `IToolManager` + VFS `/nop/ai/tools/*.tool.xml` 经 `ResourceComponentManager` 装载 |

**核心差异**：bridgic 的"二分不变式"是清晰的——`OTAContext.tool` 显式声明工具，`Context` 不携带工具。nop 把所有东西塞一个类，**字段越多越难维护**。

### 5.4 Sub-agent 委派机制对比

| | Bridgic CognitiveWorker + AgentWorker | nop call-agent + call-send-message |
|---|---|---|
| 内进程委派 | CognitiveWorker（继承 GraphAutoma，持有 BaseLlm） | call-agent 工具 sync 路径（fork+exec via `IAgentEngine.execute()`） |
| 外部 CLI 委派 | AgentWorker（继承 GraphAutoma，持有 BaseAgent = ClaudeCodeAgent / CodexAgent；通过 in-process FastMCP HTTP server 桥接项目工具） | **[已 review 修正]** 原报告称"无；call-agent 仅同进程"——**错**。call-agent async mailbox 路径经 `IMessageService`（可配 `DBMessageService`）实现跨进程投递，但子 agent 仍是同引擎 sub-agent 调用，不是外部 CLI 子进程 |
| 数据契约 | ThinkResult `{step_content, tool_calls}`，tool_calls 为空即为 finish | `ChatAssistantMessage`（content + tool_calls），tool_calls 为空由 Completion Gate 判定 |
| 状态隔离 | `_clone_worker`（descriptor 模板克隆 per yield）+ fresh OTAContext 实例 | fork session ID + child sessionId 1:1 映射 |

**核心差异**：bridgic 提供了"**内 LLM 思考 vs 外 CLI 委派**"的对称 peer 设计；nop 只支持内进程委派。

### 5.5 错误恢复 / 容错对比

| | Bridgic AmphibiousFSM | nop Reliability |
|---|---|---|
| 概念 | step-level fallback（bounded 内联 sub-run）+ full fallback（关闭 workflow_gen）+ cycle safety bound | checkpoint journal（LLM_TURN/TOOL_EXECUTION/COMPACTION/WAIT_FOR）+ 60s 恢复守护 + DB 接管锁 + 幂等键发散检测 + 6 类错误分类 + 指数退避全抖动 + 熔断（阈值 3/60s）+ 三通道故障转移（账号链/模型 tier/跨 provider） |
| 实现 | `_AmphiState.consecutive_failures` + `max_consecutive_fallbacks` | `ReActAgentExecutor` 显式字段 + LlmCallCoordinator + ICircuitBreaker + StandardRetryPolicy |
| 复杂度 | 简单（核心 ~100 行 _amphiflow 代码） | 复杂（IWaitCoordinator + CheckpointManager + DbDaemonCoordinator + IDenialLedger 等多子系统） |
| 适用场景 | 单进程 agent 框架 | 生产级无人值守多 agent（跨跨进程 takeover lock） |

**核心差异**：bridgic 是"框架级 fallback"（框架内置恢复策略）；nop 是"运维级 reliability"（生产化容错栈）。

### 5.6 DSL 对比

| | Bridgic ASL + worker/ferry_to | nop XLang + agent.xdef + plan.xdef |
|---|---|---|
| 表达力 | Python-native DSL（类级 canvas 装饰器 + TrackingNamespace 自动注册） + GraphAutoma DDG | 完整 XLang DSL（xdef schema 校验 + xscript + xpl + xpl 标签） |
| 编译期 | `__init_subclass__` 自动追踪 | XLang 编译期模型校验 |
| 用例 | 在 Python 类体内用 DSL 语法声明 canvas / element / data / settings | 用 XLang 写 agent.xdef / plan.xdef，代码生成 |
| 模板机制 | ASLField + Settings | xdef schema + agent model |

**核心差异**：bridgic ASL 是"装饰器 + 命名空间追踪"，是 Python-specific 模式；nop XLang 是"schema-driven 代码生成"，是 Java-specific 模式。两者不在同一抽象层。

### 5.7 与 nop-ai-agent 的设计哲学差异

| | Bridgic | nop |
|---|---|---|
| 哲学 | **少原语 + 高组合性**：DDG runtime + yield 协议 + 双 Context + 对称 Peer Worker，组合出 workflow / agent / AMPHIFLOW | **多原语 + 显式容错栈**：ReAct + sustainer + checkpoint journal + actor mailbox + plan + team + skill + memory + security + reliability |
| 抽象选择 | "framework 拥有尽量少的状态，其余通过用户态扩展（hook + extra='allow' + DECLARE 工具）" | "framework 拥有显式的多种 state machine（plan state machine / team task DAG / actor lifecycle / session lifecycle）" |
| 生态深度 | 单仓包（bridgic-core + bridgic-asl + bridgic-amphibious + bridgic-integration） | 多模块仓（nop-ai-core + nop-ai-agent + nop-ai-tools + nop-ai-skill + nop-ai-mcp-server + nop-ai-rag + nop-ai-coder + ...） |
| 生产化程度 | 中（轻量容错 + AgentTrace 持久化） | 高（7-checkpoint 安全链 + DB 接管锁 + 60s 守护 + 幂等键 + 三通道故障转移） |
| 用户门槛 | 低（Python 类继承 + override 模板方法） | 中高（XLang DSL + 多模块装配 + bean 配置 + agent.xdef） |

---

## 六、对 nop-ai-agent 的可借鉴点与不可借鉴点

### 6.1 可借鉴的机制与契约（已 review 修正后）

**[2026-08-17 review 修正]** 原报告把"拆分 AgentExecutionContext"、"AmphibiousFSM"标"标 P0 / P1，是被 bridgic 设计美学带着走，没看 nop 现有 reliability 栈已覆盖到什么程度。本节按诚实标准重写分级——只有**有具体 user-facing 痛点 + 现有栈未覆盖** 的项才标 P0/P1。

#### 6.1.1 AmphibiousFSM 的 "workflow-first + step-level fallback" 概念（P3 future-direction，先做 gap analysis）

**[已 review 修正]** 原标 P0 是错位。现状：nop ReAct loop 由 `LlmCallCoordinator` + `IToolCallRepairer` + `IDenialLedger` + `AgentCompactionCoordinator` + `sustainer` + `checkpoint journal` 组成，已覆盖大部分 reliability 痛点。新加 step-level fallback agent 是**叠层**，无明确未被现有栈覆盖的 user-facing 痛点驱动。

**借鉴价值**：

- `EnterAgent` 显式模式切换原语 + fresh-instance 隔离——这是 nop 未来如果要支持"workflow-style 多步任务 + 异常时切 agent" 的概念参考。
- step-level fallback 的"shaped value" 思路——`ActionCall → List[ToolResult]` / `LLMCall.chat → str` / `LLMCall.structure_output → BaseModel` 的 shape 矩阵，可作为"如果要做 fallback agent"的契约参考。

**为什么不是 P0**：

- nop 现有的 `IToolCallRepairer` 已经处理"工具调用失败 → 修复后重试"。
- nop 现有的 `IDenialLedger` threshold（默认 3）已经处理"连续失败 → 暂停 session"。
- nop 现有的 `LlmCallCoordinator` circuit breaker（60s 阈值）已经处理"LLM 端三通道故障转移"。
- **gap analysis 才能确定 step-level fallback agent 是否补的是真实缺口**。

**落地建议（前置）**：写 `ai-dev/analysis/.../nop-ai-agent-recovery-gap-analysis.md`，列出 `ReActAgentExecutor` 现有 recovery 路径的所有场景，标出哪些场景现有栈覆盖不到、且需要 fallback agent 的形态。只有 gap analysis 得出"确实有缺口"才进入 plan-first 阶段。在 gap analysis 完成前不要直接落地。

#### 6.1.2 CognitiveWorker / AgentWorker 对称 Peer 设计（P3 future-direction，nop 无对应需求）

**[已 review 修正]** 原标 P1 是错位。理由：

- **CognitiveWorker 在 bridgic 里就是"内进程 LLM 思考 + 框架适配为 ThinkResult"**——映射到 nop 就是 `ReActAgentExecutor` 本身。提取 `ICognitiveWorker` 抽象 = 重命名 `ReActAgentExecutor`，**零功能收益**。
- **AgentWorker 是"外部 CLI 委派 + in-process FastMCP bridge"**——nop 没有外部 CLI 子进程 agent 的诉求（call-agent 总是调同引擎 sub-agent）。所以"对称 peer"在 nop 缺一半。

**借鉴价值**（仅未来 sub-CLI-agent 场景）：

- `ThinkResult {step_content, tool_calls}` 数据契约——统一 cognitive step 输出格式。`tool_calls` 为空 = finish（无显式 finish flag）。
- `_assemble_decision` 适配层——把任意 LLM 协议结果（Response / (tool_calls, content) / BaseModel / dict / str）映射到 ThinkResult。

**落地建议**：不要为了对称做对称。如果未来 nop 真的需要 sub-CLI-agent（外部 coding agent 委派），再考虑此 pattern。在那之前，`ICognitiveWorker` 抽象无价值。

#### 6.1.3 OTAContext 双段不变式（P2 cleanliness 重构）

**[已 review 修正]** 原标 P0 是错位。理由：拆分 `AgentExecutionContext` 是 **cleanliness 重构**，**没有 user-facing 价值**——原 25 字段都有专门 setter/getter，运维链路收敛。

**借鉴价值**：

- **拆分** `AgentExecutionContext` 为两部分：
  - `AgentExecutionContext`（framework-owned）：messages、status、currentIteration、tokensUsed、bailReason、cancelRequested、leaseLost、delegationDepth、steeringQueue、budgetSnapshot、lastError、sessionId、startTimeMs、maxIterations、chatOptions
  - `AgentUserState`（user-owned）：plan、metadata、principal、channelKind
- **引入 `OTARecord` 等价物 `RoundRecord`**：framework-owned ctx 内增 `recordTrace: List<RoundRecord>`，`RoundRecord` 含 `observation_result / think_result / action_result + extra="allow"`。

**具体例子**（假设落地）：
```java
// 现状
ctx.setPlan(plan);
ctx.getMetadata().put("customKey", value);

// 重构后
ctx.getUserState().setPlan(plan);                  // 用户态，framework 不读不写
ctx.getUserState().addMetadata("customKey", value); // 显式边界

// RoundRecord 用法（hook 里）
RoundRecord current = ctx.getCurrentRecord();        // latest round（auto-open if empty）
current.setExtraField("permission_result", verdict); // 不需 subclass，pydantic extra="allow" 等价
```

**为什么是 P2 不是 P0**：没有 user-facing bug 在驱动；25 字段 ctx 当前工作正常；重构带来的是 conceptual clarity 不是 feature。

**落地建议（如果做）**：作为大型 refactor 的一部分而非专项工作。保留 `AgentExecutionContext` 的所有 getter/setter 作为兼容层，新代码走 `getUserState()`。

#### 6.1.4 OTAContext.tool 显式声明（P3，nop 现状不不需要）

**[已 review 修正]** 原报告误称"nop 当前 @Tool + 反射扫描"——**错**。实际是 VFS + XLang DSL 组件模型注册表（`IToolManager` 经 `ResourceComponentManager.loadComponentModel` 从 `/nop/ai/tools/*.tool.xml` 装载）。原 P2 标也是错——既然现状不不存在问题，就没有"改进"必要。

**借鉴价值**（如果有具体问题）：

- bridgic `OTAContext.tool` 类装饰器 = "**显式声明在类上**"——这是声明式生命周期管理的具体形式。
- 但 nop 的 `agent.xdef` 已经支持 `<activeTags>` / <denyTags> / <denyTools>（plan 296 WS2）——这是 DSL 层的工具可见性管理，与 OTAContext.tool 是**等价但不同形态**。

**为什么不是 P2**：因为现状已经解决了（agent.xdef 是声明式 DSL，不是反射）。引入 ToolSlot 抽象会**破坏现有 DSL 流程**——没有具体收益驱动。

#### 6.1.5 agent_done MCP signal tool 模式（P3，未来用）

**现状**：[已 review 修正] nop 当前 call-agent 是同引擎 sub-agent 调用（即使是 async mailbox 跨进程投递，子 agent 仍是同引擎执行）——没有"外部 CLI 子进程 agent"。

**借鉴价值**（仅未来 sub-CLI-agent 场景）：

- 如果将来 nop 需要 sub-CLI-agent（外部 coding agent 委派），可借鉴 bridgic 的"每个委派独立 MCP host + agent_done signal" 模式。
- 端口 0 + 系统分配；in-process uvicorn + FastMCP；handler 用 `exec` 生成镜像 JSON Schema。
- async mailbox 跨进程投递模式可借鉴 `IMessageService` + topic-based 抽象（nop 已在做，`AgentMessageTopics` 已定义 `agent.call-agent` 等 topic）。

**落地建议**：暂时不需要落地。记录为 future-direction。如果做，需要先做 sub-CLI-agent 需求调研。

#### 6.1.6 [新增] OTARecord.extra="allow" 模式（P1，未独立列出过）

**现状**：当前 nop-ai-agent hook（AgentHookInvoker + middleware chain）返回值是 `HookResult`（pass/veto + 修改 payload），没有"向当前 round 折叠自定义字段"的机制。如果 hook 想附加 `permission_result` / `retry_count` / 任何非预定义字段，要么 hook 自己维护本地变量（不上 trace），要么 hook 持有 ctx 写 metadata。

**借鉴价值**：

- `OTARecord` 用 Pydantic `extra="allow"` 模式——hook 可向当前 round 写任意字段，无需 subclass。
- 映射到 nop：可在 `AgentExecutionContext` 加 `recordTrace: List<RoundRecord>`，其中 `RoundRecord` 是普通 Java class with `Map<String, Object> extras` 字段。

**具体例子**（假设落地）：
```java
// 现状
HookResult result = hookInvoker.executeWithMiddleware(PRE_ACTING, ctx, ...);
// "permission_result" 想附加到当前 round 怎么办？ctx 没字段；只能 ctx.getMetadata().put(...) 

// 重构后（extra="allow" 等价物）
RoundRecord current = ctx.openRecord();  // auto-create if empty
current.putExtra("permission_result", verdict);  // 任意 key
current.putExtra("tool_retry_count", 3);        // 多 hook 共享
// 然后 hook 读 / write 都不需协调——所有字段都是 extra
```

**为什么是 P1 而非 P0**：当前 hook 通过 `HookResult.payload` + metadata 能 cover；`extra="allow"` 是更优雅的扩展点，但当前不缺失功能。

**落地建议**：作为 hook 改造的一部分而非专项工作。当前的 hook chain 已经能工作，引入 `RoundRecord` 抽象是 nice-to-have。

#### 6.1.7 [修正] HITL 不是"加新原语"，而是"扩展 ask-oracle 的 backend dispatcher"（P1 plan-first）

**[2026-08-17 review 第二次修正]** 用户指出"人不是一个高智能体吗？ask-oracle 的形式下直接接入 human channel 不行吗"——这个观察完全正确。原报告 §6.1.7 建议的"加 `@human_channel` + `HumanCall` yield 原语" 是**错误方向**：nop 已有完美形态的 `ask-oracle` 工具，**HITL 不需要任何新原语**，只需要扩展 oracle 的 backend dispatcher。

**为什么 nop 的 `ask-oracle` 已是 HITL 的完美形态**（看 `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/ask-oracle.tool.xml`）：

```xml
<ask-oracle id="..." explanation="..." timeoutMs="...">
    <question>...</question>
    <options>
        <option key="A">...</option>
        <option key="B">...</option>
    </options>
</ask-oracle>
```

- **抽象语义**："外部决策者从有限选项中选一个"——oracle 字面意思就是"外部决策权威"，人是最强 oracle。把 backend 从 AI agent 换成 human channel handler，HITL 自然落地。
- **LLM 友好形态**：LLM 主动决策何时 ask（不需要新原语），自评最可能答案放第一个选项（key=A）——降低误导风险。
- **超时机制已就位**：`timeoutMs` 默认 30s（`AskOracleExecutor.java:31`），HITL 同步需求满足。
- **返回 key 而非 free text**：避免 LLM 误解析自然语言回答，与 oracle 一致。

**当前实现**（`AskOracleExecutor.java:42-56`）：
- `ORACLE_ENDPOINT` 未设 → fast-fail（"ask-oracle is not configured"，**P2-MA1-011 裁定**）
- `ORACLE_ENDPOINT` 已设但 client 未实现 → fast-fail（"oracle invocation is not implemented yet"）
- **当前 backend 只有 AI agent 占位**

**HITL 实现路径**（plan-first）：

1. **引入 `OracleBackend` SPI**：
```java
public interface IOracleBackend {
    /** @return chosen option key (e.g. "A") within timeoutMs */
    CompletionStage<String> ask(String question, List<OracleOption> options, long timeoutMs);
}

// 已实现：
class AiAgentOracleBackend implements IOracleBackend { ... }  // 当前占位 + future AI agent
class NoOpOracleBackend implements IOracleBackend { return errorResult; }  // shipped 默认（与现有 fast-fail 一致）
// future:
class HumanChannelOracleBackend implements IOracleBackend { ... }  // 人 backend
class SlackOracleBackend implements IOracleBackend { ... }
class FeishuOracleBackend implements IOracleBackend { ... }
```

2. **`ORACLE_CHANNEL` 环境变量** 决定 backend：`ai-agent`（默认）/ `human-stdin` / `human-slack` / `human-feishu` / `no-op`。

3. **`AgentMessageTopics` 复用**：`agent.{sessionId}.inbox` topic + `IMessageService.request()` 是已有的 messenger 投递机制；人 backend 可通过给目标人（人 = 一个特殊 session）发 oracle request + 等 reply 实现。

4. **对齐 `skill` 工具**：`skill(action="list")` 可列出已注册 oracle backend 描述，让 LLM 知道有哪些 oracle backend 可用（"channel" 选项 A=AI / B=human / C=slack ...）。

**为什么这是 P1 plan-first**（不是 plan-only / P0）：

- **HITL 是 nop-ai-agent 重要的能力缺口**：当前无人值守模式下 agent 遇到模糊决策只能猜；HITL 让 agent 把决策委托给人。
- **`ask-oracle` 形态完全正确**：避免发明新原语；扩展 backend dispatcher 是渐进式。
- **影响面**：只涉及 `AskOracleExecutor` 重构 + `OracleBackend` SPI + 配置扩展；不动 ReAct 主循环、不动 hook chain、不动 agent.xdef 字段（仅追加 `<availableOracleChannels>` 类似 `<availableSkills>`）。

**具体例子**（假设落地）：
```java
// 当前 nop：ask-oracle 总是 fast-fail
<ask-oracle id="1" question="Continue?" options="A=yes/B=no" timeoutMs="10000"/>
// → errorResult("ask-oracle is not configured")

// 未来：设 ORACLE_CHANNEL=human-stdin，AskOracleExecutor 走 HumanChannelOracleBackend
<ask-oracle id="1" question="Continue?" options="A=yes/B=no" timeoutMs="10000"/>
// → 工具 print 问题到 stdin + 阻塞等用户输入
// → 用户在终端输入 "A"
// → tool result: "A"
// → ReAct 下一轮 LLM call 用 "A" 作为 tool result

// 与 steering queue 语义区分：
// steering = 任意外部 message 注入主消息流（plan 220）
// ask-oracle = 决策点暂停 + 等特定形式回复（future HITL）
// 两者不互斥：HITL 决策可同时 steering 注入额外上下文
```

**与 §6.1.6（OTARecord.extra="allow"）的协同**：HITL 调用可在 `RoundRecord.extras` 折叠 `oracle_question` / `oracle_options` / `oracle_response` 字段，trace 上能看出 LLM 何时 ask、问什么、得到什么。

**落地建议**：
1. 先写 `ai-dev/design/nop-ai-agent/nop-ai-agent-oracle-backend.md` 设计稿，明确 `IOracleBackend` SPI + backend 注册机制 + 配置扩展。
2. plan-first 决策：是否要在本次改动中实现 `HumanChannelOracleBackend`（人 backend）实现？还是先做 SPI 抽象 + NoOp shipped 默认，人 backend 作为独立 successor？
3. 不动现有 fast-fail 行为——`NoOpOracleBackend` shipped 默认与 P2-MA1-011 裁定一致。

#### 6.1.8 [删除] 原 6.1.7 @human_channel + HumanCall 建议作废

[已 review 修正] 用户 review 后指出"HITL 不是有 ask-oracle 工具吗？ask-oracle 的形式下直接接入 human channel 不行吗"——这是更尖锐的观察。原建议**方向完全错**：新增 `HumanCall` yield 原语 + `@human_channel` 类装饰器 + 内置 `request_human_tool`，等于在已有 `ask-oracle` 完美形态旁边**再造一个并行的 HITL 机制**，是双实现 + 概念混淆。正确路径是 §6.1.7 修正后的 oracle backend dispatcher 扩展。**这条已从可借鉴列表中删除**。

### 6.2 不可借鉴 / 价值有限的点

#### 6.2.1 Python yield-driven FSM 协作接口

**原因**：Java 没有原生 async-generator 等价物（只能用回调 + Future + Reactive Streams 模拟）。Bridgic 的 yield-driven FSM 是 Python 特定的优雅，Java 移植价值有限。

**结论**：借鉴 FSM **概念**（`_AmphiState` 显式 FSM 字段、scope 切换、step-level fallback），不借鉴 yield 协议本身。

#### 6.2.2 In-process MCP host for AgentWorker

**原因**：[已 review 修正] 原报告"nop 不需要出进程"是基于错误的"call-agent 仅同进程"前提；修正后：nop call-agent sync 路径是同进程 fork，但 async mailbox 路径可跨进程（经 `DBMessageService`）。**真正的不可借鉴点**是：bridgic 的 in-process FastMCP HTTP server 是为"**外部 CLI 子进程 agent**"专门设计的协议封装，**nop 没有 sub-CLI-agent 的诉求**，所以 MCP host 用不上——但跨进程 agent 投递可借鉴 `IMessageService` + topic-based 抽象（nop 已在做，`AgentMessageTopics` 已定义 `agent.{sessionId}.inbox` / `agent.{sessionId}.reply` / `agent.call-agent` / `agent.broadcast.{scope}` 命名约定）。

**结论**：借鉴"每个委派独立 host instance + JSON Schema 镜像"的**模式**（如果将来需要 sub-CLI-agent），不直接移植代码。

#### 6.2.3 ASL（Python-native DSL）

**原因**：ASL 是装饰器 + TrackingNamespace 的 Python-specific 模式，与 nop 的 XLang DSL 不同抽象层。

**结论**：不借鉴。nop 已有 XLang + agent.xdef + plan.xdef，方向正确。

#### 6.2.4 OTARecord 默认 fold-on-write 模式

**原因**：bridgic 的 `obs_result.setter` 是直接在最新 OTARecord 上 fold——这是 Python 简洁写法（property + setter）。Java 需要更显式（`ctx.getCurrentRound().setObservationResult(value)`），可读性反而下降。

**结论**：可借鉴"每 round 一个 record + extra='allow'"，不直接模仿 fold setter API。

#### 6.2.5 AgentTrace workdir-based 持久化

**原因**：bridgic 的 `AgentTrace` 用 `workdir` 目录 + JSON dump；nop 的 checkpoint journal 是 DB-backed + 类型化。两者不在同一抽象层。

**结论**：nop 的 checkpoint journal 已经强于 AgentTrace。

#### 6.2.6 nop 现状不存在 "@Tool 反射扫描"

[已 review 修正] 原报告称"nop 当前 @Tool + @Tool.tags + IToolManager.callTool 是反射 + bean 装配"——**错**。实际是 VFS + XLang DSL 组件模型注册表（`IToolManager` 经 `ResourceComponentManager.loadComponentModel` 从 `/nop/ai/tools/*.tool.xml` 装载，`grep -rn "@Tool\b" nop-ai/nop-ai-toolkit/src/main` 零命中）。这一条作为"不可借鉴"也不成立，因为本来就没有需要被替代的反射机制。

---

## 七、对 nop-ai-agent 设计文档的影响（如果落地）

| 影响 | 现有文档 | 建议更新 |
|---|---|---|
| 引入 EnterAgent 类原语 | 无 | `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` 新增 §5.5 "EnterAgent + AmphibiousFSM step-level fallback" |
| 拆分 AgentExecutionContext | `02-execution-model.md` 描述了 steering + react loop | 新增 `nop-ai-agent-context-model.md` 描述"framework-owned vs user-owned 双段不变式" |
| 引入 OTARecord 等价物 | 无 | `nop-ai-agent-runtime-semantics.md` 新增 §X "RoundRecord + extra='allow'" |
| 引入 CognitiveWorker 抽象 | `nop-ai-agent-react-engine.md` §5.2 描述了 ReAct loop 流程 | 抽取 CognitiveWorker 抽象为独立设计 doc：`nop-ai-agent-cognitive-worker.md` |
| 引入 ToolSlot | `04-tool-invocation.md` 描述 tool 装配 | 新增"显式 ToolSlot 声明" 章节 |

---

## 八、Open Questions

- [ ] **OTAContext 双段不变式对 nop 的具体收益**：需要实测。把 `AgentExecutionContext` 拆为 framework-owned + user-owned 两段是否能显著降低维护负担？还是单纯的形式拆分？
- [ ] **CognitiveWorker / AgentWorker 对称 peer 价值**：nop 现有 call-agent 是同进程 fork；如果只支持内进程委派，对称 peer 的价值有限。**需要评估**：未来 nop 是否会支持 sub-CLI-agent？
- [ ] **AmphibiousFSM 在 nop 的应用面**：step-level fallback 与 nop 现有的 `IToolCallRepairer` + `LlmCallCoordinator` + `DefaultWaitCoordinator` 重叠较大，需要明确分工。
- [ ] **ToolSlot 与现有 IToolManager + VFS DSL 工具注册的兼容性**：[已 review 修正] 原报告误称为 "@Tool 反射扫描"；实际是 VFS + XLang DSL 组件模型注册表。如果未来要引入 ToolSlot 抽象，需要渐进式迁移路径，避免破坏现有 nop-toolkit 用户。

---

## 九、References

### 内部设计文档

- `ai-dev/design/nop-ai-agent/nop-ai-agent-react-engine.md` — ReAct 引擎详细设计
- `ai-dev/design/nop-ai-agent/02-execution-model.md` — 双循环模型
- `ai-dev/design/nop-ai-agent/nop-ai-agent-runtime-semantics.md` — runtime 语义
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md` — context 模型
- `ai-dev/design/nop-ai-agent/nop-ai-agent-multi-agent.md` — multi-agent 协同
- `ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md` — 容错栈

### Bridgic 源码（`~/ai/bridgic/`）

- `packages/bridgic-amphibious/bridgic/amphibious/__init__.py` — Amphibious 模块入口 + 核心类型导出
- `packages/bridgic-amphibious/bridgic/amphibious/_context.py` — Context + OTAContext 双段不变式
- `packages/bridgic-amphibious/bridgic/amphibious/_amphibious_automa.py` — AmphibiousAutoma 主类 + _amphiflow FSM
- `packages/bridgic-amphibious/bridgic/amphibious/_cognitive_worker.py` — CognitiveWorker（内进程 LLM 思考）
- `packages/bridgic-amphibious/bridgic/amphibious/_agent_worker.py` — AgentWorker（外部 CLI 委派）
- `packages/bridgic-amphibious/bridgic/amphibious/_think_unit.py` — ThinkUnitDescriptor
- `packages/bridgic-amphibious/bridgic/amphibious/_think_agent.py` — ThinkAgentDescriptor
- `packages/bridgic-amphibious/bridgic/amphibious/_type.py` — OTARecord + ThinkResult + ActionCall + HumanCall + LLMCall + EnterAgent + ThinkUnit + ThinkAgent + RETURN
- `packages/bridgic-amphibious/bridgic/amphibious/_mcp_host.py` — MCPHost（in-process FastMCP HTTP server）
- `packages/bridgic-amphibious/bridgic/amphibious/temp/_base_agent.py` — BaseAgent + ClaudeCodeAgent + CodexAgent
- `packages/bridgic-amphibious/bridgic/amphibious/builtin_tools/` — 内置工具（human / shell / filesystem）
- `packages/bridgic-amphibious/bridgic/amphibious/scaffold.py` — 项目脚手架生成器
- `packages/bridgic-core/bridgic/core/automa/_graph_automa.py` — GraphAutoma DDG runtime
- `packages/bridgic-core/bridgic/core/agentic/recent/_recent_automa.py` — ReCentAutoma（ReAct-like agentic automa + episodic memory）
- `packages/bridgic-asl/bridgic/asl/_asl_automa.py` — ASL（Python-native DSL）
- `~/ai/bridgic/README.md` — 1400 行总览

### Nop 源码（`nop-ai/nop-ai-agent/`）

- `engine/ReActAgentExecutor.java` — ReAct 主循环实现（1165 行）
- `engine/DefaultAgentEngine.java` — runtime 容器（988 行）
- `engine/AgentExecutionContext.java` — 单 Context（350 行）
- `engine/AgentToolDispatcher.java` — tool fan-out
- `runtime/AgentActor.java` — Actor 抽象（mailbox + steering queue）
- `runtime/coordination/DbDaemonCoordinator.java` — 跨进程守护协调
- `plan/runtime/PlanExecutor.java` — Plan 状态机（407 行）
- `team/flow/TeamTaskFlowOrchestrator.java` — Team DAG orchestrator