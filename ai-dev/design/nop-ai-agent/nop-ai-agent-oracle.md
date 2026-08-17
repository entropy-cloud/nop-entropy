# nop-ai-agent Oracle 抽象与 HITL 未来方向

**日期**：2026-08-17
**范围**：`ask-oracle` 工具的设计意图、当前实现状态、与未来 HITL（Human-in-the-Loop）的关系
**状态**：active（前瞻性 disambiguation doc；HITL 真正落地需独立 plan）

---

## 1. 设计意图：Oracle = "外部决策权威"

`ask-oracle` 工具在 nop-ai-agent 的抽象含义是**外部决策权威**——一个 agent 遇到不确定事项时，调用 oracle 让外部决策者从有限选项中选一个。

### 1.1 `ask-oracle` 工具形态

参见 `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/ask-oracle.tool.xml`：

```xml
<ask-oracle id="..." explanation="..." timeoutMs="...">
    <question>Which database should we use?</question>
    <options>
        <option key="A">PostgreSQL (ACID, strong community)</option>
        <option key="B">MongoDB (schema-less, horizontal scaling)</option>
        <option key="C">Redis (in-memory, fast but limited)</option>
    </options>
</ask-oracle>
```

工具形态特征：

- **LLM 主动决策**：LLM 自评"我不确定 → 应该 ask" + 自评最可能答案放选项 A（key=A）——降低误导风险
- **超时机制**：`timeoutMs` 默认 30s（`AskOracleExecutor.java:31`）
- **返回 key 而非 free text**：`AiToolCallResult.output` = `"A"` / `"B"` / `"C"` ——避免 LLM 误解析自然语言回答
- **AI-to-AI 决策协议**的最小形态：oracle 收到问题+选项 → 返回 key；调用方（agent）只需关心 key 对应的语义

### 1.2 当前实现状态（2026-08-17）

参见 `AskOracleExecutor.java`（`io.nop.ai.toolkit.tools`，99 行）：

| 状态 | 触发条件 | 实现位置 |
|---|---|---|
| `errorResult`（P2-MA1-011 裁定） | `ORACLE_ENDPOINT` 环境变量未设 | `AskOracleExecutor.java:42-49` |
| `errorResult`（Anti-Silent-NoOp） | `ORACLE_ENDPOINT` 已设但客户端未实现 | `AskOracleExecutor.java:54-56` |

**当前是 AI agent oracle 占位 + stub**，没有真正调用任何外部 oracle 服务。**fast-fail 而非静默返回伪造答案**是 P2-MA1-011 裁定的明确行为。

---

## 2. `ask-human` 占位名澄清

> **本节是 AI/读者消歧节**。"`ask-human` 工具不存在、也不会实现"——它仅作为截断例外的占位配置名。

`ToolResultTruncator.NON_TRUNCATABLE_TOOLS = Set.of("ask-oracle", "ask-human")`（`ToolResultTruncator.java:11-13`）列出 `ask-human` 是预防性配置——**当前 `ask-human` 没有 `.tool.xml` 文件，没有 `IToolExecutor` 实现，也不会规划实现为独立工具**。

引用上下文（`AgentToolDispatcher.java:469` 注释）也明确：NON_TRUNCATABLE_TOOLS（`ask-oracle`/`ask-human`）= 豁免例外的工具名清单，避免被工具结果截断影响人机交互输出。

为什么这么配置：人机交互的语义要求全文可达（不能截断），即使 `ask-human` 工具尚未存在，提前把名字放入例外集是"如果未来走 `ask-human` 工具名"的安全防御的。

---

## 3. HITL 实现路径：扩展 `ask-oracle` 的 backend dispatcher

> **本节是前瞻性设计 draft**——HITL 真正落地需要独立 plan-first 决策，本节提供概念蓝图。

### 3.1 为什么 HITL 不需要新原语

HITL（Human-in-the-Loop）的字面含义是"让人介入 agent 决策循环"。如果把它当作"新增 `HumanCall` 原语 / `@human_channel` 类装饰器 / `request_human_tool` 内置工具"来实现，会与 `ask-oracle` 工具**形成并行的双 HITL 机制**——双实现 + 概念混淆 + LLM 必须知道两套调用方式。

正确路径：**HITL 是 oracle 抽象的"人"backend 实现**。Oracle 字面意思 = "外部决策权威"；人是最强的 oracle。把 `ask-oracle` 的 backend 从 AI agent 扩展为"human channel handler"，HITL 自然落地，**不需要新原语**。

### 3.2 `IOracleBackend` SPI（拟）

```java
public interface IOracleBackend {
    /** @return chosen option key (e.g. "A") within timeoutMs; null if no decision */
    CompletionStage<String> ask(String question, List<OracleOption> options, long timeoutMs);
}

public class OracleOption {
    private final String key;       // "A" / "B" / "C" / ...
    private final String description;
}
```

### 3.3 拟 backend 实现

| backend | 状态 | 触发 |
|---|---|---|
| `AiAgentOracleBackend` | 当前占位 stub | `ORACLE_CHANNEL=ai-agent`（默认） |
| `NoOpOracleBackend` | shipped 默认（保留 P2-MA1-011 fast-fail） | `ORACLE_CHANNEL=no-op` |
| `HumanChannelOracleBackend` | future | `ORACLE_CHANNEL=human-stdin` / `human-feishu` / `human-slack` |
| 其他 backend | future | 按需 |

`ORACLE_CHANNEL` 环境变量决定 backend。配置扩展只新增环境变量，不动 agent.xdef 字段。

### 3.4 与 messenger 投递机制复用

人 backend 可复用现有 `IMessageService.request()` 给"人 session"（人 = 一个特殊 session ID）发 oracle request + 等 reply，与 `agent.{sessionId}.inbox` topic + `AgentMessageTopics` 投递机制一致——**复用 > 发明**。

### 3.5 与 `skill` 工具协同

`skill(action="list")` 可列出可用 oracle backend 描述（AI backend / 人 backend / Slack backend 等），让 LLM 知道有哪些 oracle backend 可用。这是天然的"oracle backend 目录"机制——但需在 skill 工具扩展点实现后才适用。

---

## 4. 与 steering queue 的语义区分

| | ask-oracle HITL | steering queue（plan 220） |
|---|---|---|
| 触发方式 | LLM 主动调工具 | 外部 message 注入主消息流 |
| 内容 | 决策问题 + 候选选项 | 任意 chat message |
| 触发点 | 任何 tool call 周期 | round boundary（plan 220） |
| 引擎内部位置 | `OTARecord.extras["oracle_request"]` / `"oracle_response"` | `ctx.steeringQueue` 在 round 边界 drain |
| 阻塞语义 | 工具 call 阻塞等 backend 返回 key | round 之间可注入不阻塞 |

**两者互补不互斥**：HITL 决策过程中可同时通过 steering 注入额外上下文（人边看 oracle 选项边发补充信息）。

---

## 5. 明确拒绝的方案

### 5.1 拒绝：新增 `HumanCall` yield 原语

理由：

- 与 `ask-oracle` 工具并行，**双 HITL 实现 + 概念混淆**——LLM 必须知道两套调用方式
- nop-ai-agent 当前没有 workflow 概念（plan 模式下走 xwf DSL，不走 agent mode），yield 原语无落点
- 即使未来有 workflow 概念，HITL 通过 `ask-oracle` 工具调用已经覆盖需求

### 5.2 拒绝：新增 `request_human_tool` 内置工具

理由：

- 与 `ask-oracle` 重复，**强制 LLM 改用另一个工具名**——无 user-facing 价值
- `ask-oracle` 的 backend 扩展机制已经覆盖 HITL 需求

### 5.3 拒绝：新增 `@human_channel` 类装饰器

理由：

- 与现有的 `AgentMessageTopics` `agent.{sessionId}.inbox` topic + `IMessageService` 投递机制重叠
- 人 backend 应该是 `IOracleBackend` SPI 的一种实现，不是 agent 类的扩展点

---

## 6. 当前能力边界

**nop-ai-agent 当前（2026-08-17）**：

- ✅ `ask-oracle` 工具形态定义完整（`.tool.xml` DSL + `AiToolModel` schema + `AskOracleExecutor` 99 行最小实现）
- ✅ `AiAgentOracleBackend` 占位 stub 存在但不可用（fast-fail）
- ❌ `IOracleBackend` SPI 未尚未抽出——backend 与 executor 耦合
- ❌ `HumanChannelOracleBackend` 未实现——HITL 实际不可用

**HITL 落地需要**（独立 plan-first 决策）：

1. 抽取 `IOracleBackend` SPI（不破坏现有 fast-fail 行为，shipped 默认仍是 NoOp）
3. 实现 `HumanChannelOracleBackend`（具体 channel：stdin / feishu / slack，由 plan 决定）
4. 扩展 `ORACLE_CHANNEL` 环境变量配置
5. 测试覆盖：HITL oracle 在无人值守模式下正确 fail-fast（vs human backend 可用时返回 key）

**当前的设计意图已通过 oracle 抽象确立，落地时机由业务驱动决定**——本 doc 提供前瞻性蓝图，避免后续实现时"重复造 HITL 轮子"。

---

## 7. References

- `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/ask-oracle.tool.xml` — 工具 DSL 定义
- `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/AskOracleExecutor.java` — 当前占位实现（99 行）
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/compact/ToolResultTruncator.java:11-13` — NON_TRUNCATABLE_TOOLS 配置
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentToolDispatcher.java:469` — 豁免语义说明注释
- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/message/AgentMessageTopics.java` — `agent.{sessionId}.inbox` topic 命名
- `nop-ai/nop-ai-agent/.../plan 220 / L4-8-steering` — steering queue 设计