# Nop AI Agent DSL

## 1. 目标

本篇以 `agent.xdef` 为中心说明 Agent DSL。

文档只做两件事：

1. 指出 DSL 中有哪些节点和属性值得关注
2. 补充 `xdef` 本身看不出来的运行语义、默认行为和组合约束

本篇不重复解释 ReAct、LLM、Hook 的总体背景；这些内容放到语义文档中。

对应 schema：

- `/nop/schema/ai/agent.xdef`

## 2. DSL 入口

根节点为：

```xml
<agent name="!string" tagSet="csv-set">
    <description>...</description>
    <meta>...</meta>
    <chatOptions .../>
    <tools>...</tools>
    <availableSkills>...</availableSkills>
    <requiredSkills>...</requiredSkills>
    <permissions>...</permissions>
    <constraints .../>
    <prompt>...</prompt>
    <hooks>...</hooks>
</agent>
```

其中真正需要文档补语义的，是：

- `chatOptions`
- `tools`
- `availableSkills`
- `requiredSkills`
- `permissions`
- `constraints`
- `prompt`
- `hooks`

## 3. 顶层属性

### 3.1 `name`

- Agent 的唯一标识
- 应作为运行时查找、`call-agent` 调用和配置装配的主键

### 3.2 `tagSet`

- 用于分类、筛选或批量选择 agent
- 不直接参与 runtime 主循环语义
- 适合做管理和发现维度

## 4. 顶层子节点语义

### 4.1 `description`

- 面向人类阅读的说明
- 可用于 agent 列表展示或调试信息
- 不应作为主提示词的一部分自动注入，除非运行时显式这样设计

### 4.2 `meta`

- 保存扩展元数据
- 用于管理端、路由端或调试端附加信息
- `meta` 不应自动影响 Agent 执行，除非运行时显式消费某些约定字段

### 4.3 `chatOptions`

引用 `chat-options.xdef`。

支持的字段包括：

- `provider`
- `model`
- `seed`
- `temperature`
- `topP`
- `topK`
- `maxTokens`
- `contextLength`
- `stop`

语义补充：

- 这是 agent 级默认聊天参数，不是每次请求都不可覆盖的硬配置
- 运行时应允许 `AgentRequest.options` 覆盖这里的缺省值
- 推荐优先级：请求时显式选项 > agent DSL 中的 `chatOptions` > provider/model 默认值

### 4.4 `tools`

- 表示该 Agent 允许或倾向使用的工具集合
- 这里的值是工具名集合，不是完整工具定义
- 工具的 schema、description、examples 由对应 `.tool.xml` 提供

语义补充：

- `tools` 解决“这个 agent 能看见哪些工具”
- 它不替代权限系统
- 即使某个工具出现在 `tools` 中，仍应经过权限检查后才可执行

### 4.5 `availableSkills`

- 表示运行时可选择装配的技能集合
- 这些技能不一定会全部生效

推荐语义：

- 运行时在这些技能中按请求上下文判断是否激活
- 适合放可选能力，如 plan、compression、review 等

### 4.6 `requiredSkills`

- 表示本 Agent 启动时必须装配的技能集合
- 如果缺失，应视为配置错误或启动错误

推荐语义：

- `requiredSkills` 是硬依赖
- `availableSkills` 是候选依赖

### 4.7 `permissions`

- 这是 Agent 的声明式权限项
- 每项包含 `id/resource/action`

语义补充：

- 这里表达的是 Agent 能力边界
- 运行时仍需要将它映射到具体的工具权限、文件权限或子 agent 权限模型
- `permissions` 与外部权限配置文件可以并存，但必须定义谁是 source of truth

当前阶段建议：

- DSL 中的 `permissions` 作为 agent 内嵌声明
- `.nop/.permissions/agent-*.yml` 作为外部部署配置
- 若两者同时存在，外部配置优先

### 4.8 `constraints`

当前 schema 暴露的字段：

- `maxIterations`
- `toolTimeoutSeconds`
- `maxParallelTools`
- `tokenCompressionThreashold`

语义补充：

- `maxIterations`
  - 限制单次 ReAct 循环最大轮次
- `toolTimeoutSeconds`
  - 作为工具执行默认超时
- `maxParallelTools`
  - 限制同一轮 acting 的并行工具数
- `tokenCompressionThreashold`
  - 达到阈值时触发压缩检查

这些字段是 Agent DSL 中非常关键的 runtime 控制入口，不应在文档里被弱化成一般性建议。

### 4.9 `prompt`

- Agent 的系统提示词主体
- 使用 `prompt-syntax`

语义补充：

- `prompt` 是 Agent 行为模式的核心输入
- Skill、Plan、Compression 等能力可以在运行时对其做附加注入
- 但这些附加注入是在 `prompt` 之上增强，而不是替代 `prompt`

### 4.10 `hooks`

结构：

```xml
<hooks>
    <on id="..." event="...">xpl-fn:(event, agentRt)=>void</on>
</hooks>
```

这是 `agent.xdef` 中最关键的扩展点之一。

语义补充：

- `event` 使用事件模式匹配
- `body` 是运行时执行的 Hook 逻辑
- `id` 用于标识和调试，不直接影响执行顺序
- 顺序应由运行时的 Hook 排序策略决定

Hook 的具体事件和执行规则见 DSL 语义补充文档。

## 5. 最小可运行例子

```xml
<agent name="coder" tagSet="code,default">
    <description>General coding agent</description>

    <chatOptions provider="openai" model="gpt-4.1" temperature="0.2"/>

    <tools>read-file,write-file,patch-file,call-agent</tools>

    <availableSkills>plan,review,compression</availableSkills>

    <constraints maxIterations="10" toolTimeoutSeconds="300" maxParallelTools="5"
                 tokenCompressionThreashold="0.78"/>

    <prompt><![CDATA[
You are a coding agent.
Follow the task, use tools when necessary, and keep outputs precise.
    ]]></prompt>

    <hooks>
        <on id="plan-hook" event="before_reasoning">xpl-fn:(event,agentRt)=>null</on>
    </hooks>
</agent>
```

## 6. 推荐组合

### 6.1 简单 Agent

- `prompt`
- `tools`
- `chatOptions`
- `constraints`

### 6.2 带技能 Agent

- 上述字段
- `availableSkills`
- 或 `requiredSkills`

### 6.3 强约束 Agent

- 上述字段
- `permissions`
- `hooks`

## 7. 当前阶段不应写进 `agent.xdef` 的东西

下面这些概念如果当前 schema 里还没有，就不应在文档里伪装成“既有 DSL 字段”：

- `toolCallMode`
- `sessionStrategy`
- `call-agent.mode`
- `call-agent.context`

这些可以是运行时契约、工具契约或未来扩展，但不应误写成 `agent.xdef` 自身的一部分。

## 8. 本篇结论

Agent 设计首先应该以 `agent.xdef` 为中心。

设计文档最重要的任务不是重复 schema，而是解释：

- 每个 DSL 节点在 runtime 中的含义
- 默认行为和覆盖优先级
- 合法组合和不推荐组合
- 哪些能力属于 DSL，哪些能力只是运行时补充
