# Nop AI call-agent DSL

## 1. 目标

本篇以真实的 `call-agent.tool.xml` 为中心说明 `call-agent` DSL。

对应文件：

- `nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/call-agent.tool.xml`

本篇只解释当前已经存在的 DSL，不额外发明新的字段。

## 2. DSL 形态

`call-agent` 是一个普通工具定义，其 schema 形态如下：

```xml
<call-agent id="!int" explanation="!string" timeoutMs="int"
            agentName="!string" sessionId="string" skills="csv-set" inheritContext="boolean">
    <input>string</input>
    <input-files>
        <input-file path="!full-path" description="string"/>
    </input-files>
</call-agent>
```

这意味着：

- `call-agent` 首先是工具 DSL，不是单独的根级 agent schema
- 它应按工具调用链被执行、记录和返回结果

## 3. 字段语义

### 3.1 `id`

- 当前工具调用的唯一标识
- 用于与 `agent-result` 做结果关联

### 3.2 `explanation`

- 解释为什么要调用子 agent
- 用于调试、审计和模型自解释

### 3.3 `timeoutMs`

- 子 agent 本次调用的超时时间
- 若未指定，则由工具默认值或 Agent 约束提供缺省值

### 3.4 `agentName`

- 目标 Agent 配置名（即 `agent.xdef` 的 `name` 属性）
- 特殊值 `self` 表示创建当前 agent 的副本

### 3.5 `sessionId`

- 非空表示延续指定会话
- 为空表示新建会话

这是当前 DSL 中表达会话关系的主字段，不应被文档替换成另一个不存在于 schema 中的字段。

### 3.6 `skills`

- 本次子 agent 调用启用的技能集合
- 是调用级技能选择，不是 agent 全局默认技能集合

### 3.7 `inheritContext`

- 仅当 `agentName="self"` 时有效
- `true` 表示新 agent 继承当前会话上下文
- `false` 表示以全新上下文启动

### 3.8 `<input>`

- 传递给子 agent 的文本输入
- 可为空

### 3.9 `<input-files>`

- 传递给子 agent 的附加文件列表
- 适合把局部上下文文件显式交给子 agent

## 4. 返回结果

`call-agent.tool.xml` 的 examples 和 `call-tools-response.xdef` 已经说明，`call-agent` 返回的是：

- `agent-result`

其关键字段包括：

- `id`
- `status`
- `sessionId`
- `<output>`
- `<output-files>`

这意味着当前 DSL 层已经固定：

- `call-agent` 的结果是工具结果中的一种特殊形式
- 不是自定义 JSON 包，也不是绕过工具体系的旁路协议

## 5. 当前 DSL 能表达的会话语义

基于现有字段，当前 `call-agent` 可表达的典型场景有：

### 5.1 调用外部 agent 并新建会话

- `agentName="translator"`
- 不传 `sessionId`

### 5.2 调用外部 agent 并延续已有会话

- `agentName="translator"`
- 传入 `sessionId`

### 5.3 创建 `self` 副本且不继承上下文

- `agentName="self"`
- `inheritContext="false"`
- 不传 `sessionId`

### 5.4 创建 `self` 副本并继承上下文

- `agentName="self"`
- `inheritContext="true"`

## 6. 当前 DSL 还不能直接表达的内容

下面这些概念如果要设计，属于未来扩展，不应伪装成当前 DSL 字段：

- `sessionStrategy`
- `mode=sync|async|detached`
- `context` 结构化对象

如果未来需要支持这些能力，应先修改对应 `.tool.xml` 和 `tool.xdef` schema，再更新文档。

## 7. 与 Advisor Agent 的关系

Advisor Agent 不是独立 DSL，而是 `call-agent` 的典型使用场景。

例如：

- `retry-advisor`
- `compaction-advisor`
- `consistency-checker`

都应理解为：

- 被 `call-agent` 调用的普通 agent
- 返回 `agent-result`
- 输出中承载结构化决策结果

## 8. 与运行时的关系

运行时应补充的不是新的字段，而是现有字段的解释规则，例如：

- `sessionId` 为空时怎样创建新会话
- `inheritContext=true` 时复制哪些上下文
- `skills` 与目标 agent 自带 `availableSkills/requiredSkills` 如何合并

这些属于 runtime semantics，不应重新变成另一套 DSL。

## 9. 最小样例

### 9.1 新建 `self` 子 agent

```xml
<call-agent id="1" explanation="Spawn sub-agent" timeoutMs="30000"
            agentName="self" skills="log-analysis" inheritContext="false">
    <input><![CDATA[Analyze the attached log files.]]></input>
</call-agent>
```

### 9.2 延续已有 agent 会话

```xml
<call-agent id="2" explanation="Continue translator session" timeoutMs="10000"
            agentName="translator" sessionId="sess_xyz789" skills="technical">
    <input><![CDATA[Hello, world!]]></input>
</call-agent>
```

## 10. 本篇结论

`call-agent` 的正确入口应该是它自己的真实工具 DSL，而不是若干运行时文档里发明的新契约。

如果后续需要扩展 `context`、`mode`、更复杂的 session 策略，应先扩 schema，再写设计文档。
