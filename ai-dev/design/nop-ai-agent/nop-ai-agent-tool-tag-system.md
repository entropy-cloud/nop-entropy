# Tag-based Tool Visibility 设计

> Status: final
> Date: 2026-07-17
> Scope: `AgentModel.tools` + `IToolManager` 工具可见性控制
> Motivation: AgentScope ToolGroup 过度工程化（工厂 + 组 + 成员），Nop 需要更轻量的 K8s-style tag 方案

---

## 一、问题分析

### 当前工具可见性控制

`AgentModel._tools: Set<String>` 仅工具名白名单：
- **ALLOW 模式**：`tools` 集合列出可用工具
- **无 DENY**：没有否定列表
- **无动态选择**：工具可见性在 agent 装配时定死
- **无分组/分类**：所有配置的工具对所有人可见

### 为何不直接移植 AgentScope ToolGroup

AgentScope 的 `ToolGroup` 是一个复杂的对象体系（ToolGroupManager + ToolGroup + SubAgentTool + isActiveTool）。对于 Nop 的场景太重了。

**核心需求**是一个动态标签筛选器：
- 给每个工具打标签（如 `"readonly"`, `"admin"`, `"channel:webui"`）
- Agent 声明 `activeTags` 集合，运行时只暴露匹配标签的工具
- Agent 在不同上下文切换 `activeTags` 以控制工具面

### AgentScope ToolGroup 映射表

| AgentScope 概念 | Nop 方案 |
|----------------|---------|
| `ToolGroup.name` | 标签（平铺到工具上，一个工具可以有多个标签） |
| `ToolGroup.ToolInfo` | `AiToolModel.tags: Set<String>`（xdef 声明） |
| `ToolGroup.isActiveTool()` | `activeTags` + `tool.tags` 交集判断 |
| `ToolGroupManager` | `buildToolDefinitions` 内联筛选（不新增 `IToolManager` 方法） |
| `SubAgentTool` | 不涉及（P1 独立实现） |

---

## 二、设计

### 2.1 工具标签模型

`IToolDefinition` 接口（`nop-ai-api`）：

```java
public interface IToolDefinition {
    String getName();
    String getDescription();
    default Set<String> getTags() { return Collections.emptySet(); }
}
```

`ChatToolDefinition`（`nop-ai-api`）实现 `IToolDefinition`，`getTags()` 返回 `Collections.emptySet()`。

`AiToolModel`（`nop-ai-toolkit`）实现 `IToolDefinition`，`getTags()` 委托给 codegen 生成的 `_tags` 字段（来自 `tool.xdef` 的 `<tags>csv-set</tags>`）。

标签命名惯例（参考 K8s 标签 + Nop XMeta 前缀惯例）：

| 前缀 | 用途 | 示例 |
|------|------|------|
| `channel:` | 按信道过滤 | `channel:webui`, `channel:dm` |
| `scope:` | 按可见域过滤 | `scope:global`, `scope:tenant` |
| `phase:` | 按执行阶段过滤 | `phase:reasoning`, `phase:acting` |
| `env:` | 按环境过滤 | `env:prod`, `env:staging` |
| （无前缀） | 通用语义 | `readonly`, `admin`, `beta` |

### 2.2 Agent 标签字段

`AgentModel`（`agent.xdef`）新增 3 个 `csv-set` 字段：

```xml
<activeTags>csv-set</activeTags>
<denyTags>csv-set</denyTags>
<denyTools>csv-set</denyTools>
```

**可见性语义规则**（在 `buildToolDefinitions` 中实现）：

```
visible(tool) =
    tool.isMeta → true                              # D10: meta 工具始终可见
    AND NOT(tool.name in denyTools)                 # 工具名黑名单
    AND (
        activeTags is empty → true                  # 无筛选，全部可见
        OR any tag in activeTags in tool.tags       # 交集
    )
    AND NOT(any tag in denyTags in tool.tags)       # 标签黑名单
```

### 2.3 运行时动态切换

`AgentSession` 新增 `activeTags` 字段 + `resolveActiveTags(AgentModel)` 方法：

```java
public Set<String> resolveActiveTags(AgentModel model) {
    if (activeTags != null) return activeTags;      // session override
    if (model.getActiveTags() != null) return model.getActiveTags();
    return Collections.emptySet();                   // 全量可见
}
```

三级优先：session 运行时覆盖 → model 静态声明 → 空集（全量可见）。

### 2.4 筛选逻辑位置（D9 裁定）

**全部筛选逻辑内联在 `ReActAgentExecutor.buildToolDefinitions(AgentModel, AgentSession)` 中**。`IToolManager` 不新增任何方法（避免 `nop-ai-toolkit → nop-ai-agent` 反向依赖）。

`buildToolDefinitions` 流程：
1. 当 `_tools` 非空时，用 `loadTool(name)` 逐个加载声明工具（向后兼容现有 test stub）
2. 当 `_tools` 为空时，用 `listTools()` 获取全量工具
3. 合并 `listTools()` 中的 meta 工具（始终可见）
4. 对非 meta 工具应用 denyTools → activeTags → denyTags 过滤
5. 转为 `List<ChatToolDefinition>`

### 2.5 AgentSession 在 dispatch loop 的传播（D11 裁定）

`AgentToolExecuteContext` 新增 `AgentSession session` 字段。`ReActAgentExecutor` 在 dispatch loop 构造 context 时从 `sessionStore.get(sessionId)` 填充。

### 2.6 元工具 `set-active-tags`

一个内置 meta-tool（`meta="true"` 标记）：

```xml
<tool name="set-active-tags" meta="true">
    <schema>
        <set-active-tags>
            <tags><tag>!string</tag></tags>
        </set-active-tags>
    </schema>
    <description>运行时切换当前 agent 的工具可见性标签。仅影响当前 session。</description>
</tool>
```

- `meta="true"`：在响应给 LLM 的 tool list 中**始终可见**（不受 activeTags/denyTags/denyTools 过滤）
- 执行时从 `AgentToolExecuteContext.getSession()` 获取 session，写入 `session.activeTags`
- 仅影响当前 session，不持久化到 AgentModel

---

## 三、改动范围

| 改动 | 范围 | 向后兼容？ |
|------|------|-----------|
| `IToolDefinition` 新增接口 | `nop-ai-api` | ✅ 新增 |
| `ChatToolDefinition` 实现 `IToolDefinition` | `nop-ai-api` | ✅ getTags 返空集 |
| `AiToolModel` 实现 `IToolDefinition` | `nop-ai-toolkit` | ✅ getTags 委托 _tags |
| `tool.xdef` 新增 `<tags>csv-set</tags>` + `<meta>boolean</meta>` | `nop-xdefs` | ✅ 新增可选 |
| `agent.xdef` 新增 `activeTags`/`denyTags`/`denyTools` | `nop-xdefs` | ✅ 新增可选 |
| `AgentSession.activeTags` + `resolveActiveTags` | `nop-ai-agent` | ✅ 新增可选 |
| `AgentToolExecuteContext.session` 字段 | `nop-ai-agent` | ✅ 新增可选 |
| `ReActAgentExecutor.buildToolDefinitions(model, session)` 重写 | `nop-ai-agent` | ✅ _tools 白名单行为不变 |
| 新增 `set-active-tags` 元工具 | `nop-ai-agent` | ✅ 新增 |
| 现有 `tools: Set<String>` 行为 | 不变 | ✅ |

**不引入** `ToolGroup`（组+成员+工厂）。**不新增** `IToolManager` 方法。**不引入** `getToolsForAgent(AgentModel, AgentSession)` — 全部筛选内联在 `buildToolDefinitions`。

---

## 四、对比 AgentScope ToolGroup

| 维度 | AgentScope ToolGroup | Nop Tag System |
|------|---------------------|----------------|
| **定义模型** | 代码式 `ToolGroup(name, members)` | 声明式（XMeta 或 xdef 标签） |
| **运行时修改** | `addTool(...)` | `set-active-tags` meta-tool |
| **过滤粒度** | 组级（排他） | 标签级（多标签交/并） |
| **分组维度** | 一维（属于哪个组） | 多维（readonly + channel:webui + beta） |
| **可扩展性** | 需新增 ToolGroup subclass | 不用新增类，任意标签名 |
| **学习成本** | 中 | 低（K8s 标签语义类比） |
| **与 Nop 契合度** | 低 | 高（XMeta meta:ext: 前缀惯例自然延伸） |

---

## 五、验证

1. 工具标签在 `AiToolModel` 正确解析（`getTags()` 返回 xdef 声明值）
2. activeTags 筛选正确：空集合 = 全可见；非空 = 交集；无交集 = 不可见
3. denyTags 优先于 activeTags
4. denyTools 优先于 tags 判断
5. meta 工具始终可见（不受 activeTags/denyTags/denyTools 影响）
6. 运行时 `set-active-tags` 正确更新 `session.activeTags`
7. 下次 `buildToolDefinitions` 反射新标签状态
8. 向后兼容：未声明 activeTags 的 agent 行为不变；`_tools` 白名单行为不变

---

## References

- AgentScope `ToolGroupManager.java:78-95`（isActiveTool 算法）
- AgentScope `ToolGroup.java:38-60`（组定义方式）
- Nop XMeta `meta:` / `ext:` 前缀隔离惯例（标签复用同一模式）
- `ai-dev/analysis/agent-survey/2026-07-16-agentscope-vs-nop-ai-agent-deep-comparison.md` §8.2
- `ai-dev/plans/296-nop-ai-agent-middleware-and-tool-tag-system-implementation.md`
