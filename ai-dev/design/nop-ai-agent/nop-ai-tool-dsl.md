# Nop AI Tool DSL

## 1. 目标

本篇说明 AI 工具的 DSL 形态，覆盖：

- `tool.xdef`
- `tool-call.xdef`
- `call-tools.xdef`
- `call-tools-response.xdef`
- `call-agent.tool.xml`

重点不是重复 schema，而是说明这些 DSL 在工具调用链里分别承担什么角色。

## 2. `tool.xdef`

对应根节点：

```xml
<tool name="!string">
    <schema>...</schema>
    <response-schema>...</response-schema>
    <description>...</description>
    <examples>...</examples>
</tool>
```

### 2.1 `name`

- 工具唯一名称
- 运行时用它做发现、调用、权限控制和错误提示

### 2.2 `schema`

- 描述工具调用的 XML 结构
- 这是工具 DSL 的核心

语义补充：

- `schema` 不是展示用文档，而是 prompt 注入、XML 调用解析、参数验证的重要依据

### 2.3 `response-schema`

- 可选的返回格式定义
- 适合对结构化结果做进一步约束

### 2.4 `description`

- 给模型看的工具说明
- 也是人类理解工具意图的入口

### 2.5 `examples`

- 包含调用示例和响应示例
- 对 XML tool calling 尤其重要

## 3. `tool-call.xdef`

对应单个工具调用结构。

关键字段：

- `id`
- `explanation`
- `timeoutMs`
- `<input>`
- `<input-files>`
- 未知 tag 负载

### 3.1 `id`

- 工具调用唯一标识
- 用于结果关联和事件追踪

### 3.2 `explanation`

- 模型解释“为什么要调用这个工具”
- 适合展示、调试和审计

### 3.3 `timeoutMs`

- 当前调用级超时
- 若未指定，则退回到 Agent 或工具默认超时

### 3.4 `<input>`

- 适合承载原始文本输入
- 不是所有工具都必须依赖它

### 3.5 `<input-files>`

- 表示本次工具调用依赖的输入文件集合
- 适合让工具显式声明上下文文件依赖

### 3.6 未知 tag 负载

- 允许不同工具用自身 XML 结构表达参数
- 这是 Nop 工具 DSL 的核心灵活性来源

## 4. `call-tools.xdef`

对应一批工具调用：

```xml
<call-tools paralllel="boolean=true" maxConcurrency="int">
    <read-file .../>
    <search-files .../>
</call-tools>
```

语义补充：

- 它表达的是“一轮 acting 中的一批工具调用”
- `paralllel` 表达是否允许并行
- `maxConcurrency` 限制这一批调用的最大并发数

注意：schema 中属性名当前是 `paralllel`（拼写错误），文档应忠实反映现状。**修正计划**：在 xdef schema 中将属性名修正为 `parallel`，同时保留 `paralllel` 作为 deprecated alias（XDSL 兼容性），下个 breaking version 移除 alias。

## 5. `call-tools-response.xdef`

表示工具调用结果集合。

关键元素：

- `tool-result`
- `agent-result`
- `AiToolCallResult.status`
- `<output>`
- `<error>`
- `<output-files>`

### 5.1 状态

当前 schema 支持：

- `success`
- `failure`
- `timeout`
- `processing`

### 5.2 `tool-result`

- 普通工具调用结果

### 5.3 `agent-result`

- `call-agent` 这种特殊工具的结果
- 额外带 `sessionId`

这意味着 `call-agent` 在 DSL 层已经被视为“返回 agent 结果的特殊工具调用”。

## 6. `call-agent.tool.xml`

`call-agent` 是当前 AI 工具体系里最重要的特殊工具之一。

它不是根级 schema，而是一个基于 `tool.xdef` 定义出来的具体工具。

它的详细字段语义单独见：

- `nop-ai-call-agent-dsl.md`

## 7. 最小例子

### 7.1 工具定义

```xml
<tool name="read-file">
    <schema>
        <read-file id="!int" explanation="!string" path="!full-path"/>
    </schema>
    <description>Read a file from workspace</description>
</tool>
```

### 7.2 工具调用

```xml
<call-tools paralllel="true" maxConcurrency="2">
    <read-file id="1" explanation="Read README" path="README.md"/>
    <read-file id="2" explanation="Read schema" path="agent.xdef"/>
</call-tools>
```

## 8. 工具执行上下文

### 8.1 上下文可见性

工具执行时，引擎提供有限的上下文信息。工具不应该能访问 Agent 的全部内部状态。

上下文可见性的详细定义见 `nop-ai-agent-context-model.md` §4。

### 8.2 与 Toolkit 的关系

nop-ai-toolkit 已有 Toolkit 抽象。工具执行上下文应在 Toolkit 层注入，工具实现通过 Toolkit 提供的上下文接口访问环境信息。

**Phase 1 实现**：工具通过 Toolkit 上下文获取工作目录、sessionId、环境变量等基本信息。

**Phase 2 扩展**：增加文件锁注册、资源声明等协同信息。

## 9. 文档应补充而不是重复的部分

围绕工具 DSL，文档最应该补的不是 schema 本身，而是：

- 单个调用和批次调用的关系
- `call-agent` 为什么在结果层是 `agent-result`
- `description/examples` 对 XML tool calling 的作用
- `timeoutMs`、`maxConcurrency` 这类字段的运行时解释

## 10. 本篇结论

AI 工具设计的第一主角同样是 DSL，而不是 Java 执行器类。

先把 `tool.xdef`、`tool-call.xdef`、`call-tools.xdef`、`call-tools-response.xdef` 讲清楚，才能正确讨论工具执行器、并发策略和错误处理。
