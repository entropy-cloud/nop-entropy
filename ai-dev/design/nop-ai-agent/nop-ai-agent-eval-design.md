# Nop AI Agent Eval 系统设计

**日期**：2026-06-21
**范围**：`nop-ai-agent` 的 eval（评测/测试）子系统——声明式 YAML eval 描述格式、runner 语义、verdict 机制
**状态**：草案

**灵感来源**：Vercel `eve` 框架的 `defineEval` + `t` 上下文模型（命令式 TS），适配为 Nop 的 DSL-First 声明式风格

---

## 一、设计结论

1. **eval 是 agent 的端到端行为评测**：走真实 agent session（非 mock），驱动 ReAct 循环，断言 agent 行为（调了哪些工具、回复内容、HITL 是否正确），用 LLM-judge 处理模糊正确性。**不是单元测试**（不测函数返回值），是「测试 + 评测」混合体。
2. **采用声明式 YAML**（`*.eval.yaml`），不采用命令式脚本。理由：符合 DSL-First；可 Delta 定制；可 git diff；schema 可校验。任意逻辑用 XPL 表达式表达（声明式 + 可选表达式 = 分层 authoring）。
3. **`t` 上下文合一模型保留**：驱动（send/respond）和断言（calledTool/judge）描述在同一个有序 steps 序列里，不拆成 input/run/checks 字段（那是 eve 已拒绝的 legacy 形态）。
4. **gate/soft 二分 + 阈值**：确定性断言默认 gate（失败=fail），模糊断言（similarity、LLM-judge）默认 soft（仅记录，`--strict` 下带阈值才 fail）。
5. **与 AutoTest 互补不替代**：AutoTest 测 biz/service 方法（IoC 录制回放），eval 测 agent ReAct 行为（真实 session + LLM-judge）。

## 二、背景与动机

### 当前痛点

`nop-ai-agent` 已有完整的 DSL + 引擎 + 会话 + 工具体系（见 `00-vision.md` ~ `04-tool-invocation.md`），但**缺少标准化的 agent 行为评测能力**：

- 无法回答「这个 agent 改动后，行为有没有退化」
- 无法在 CI 里验证「agent 真能启动 + 真接受消息 + 真调对工具 + 真回复正确内容」
- 无法对 LLM 驱动的不确定行为做「确定性 gate + 模糊性 soft」的分层断言
- AutoTest 录制回放测的是方法层，不是 ReAct 循环行为层

### 为什么声明式而非命令式

eve 的 eval 是命令式 TS（`async test(t){ await t.send(...); t.calledTool(...) }`）。Nop 选择声明式 YAML 的理由：

- **DSL-First 约束**（`00-vision.md` 约束 #1）：eval 描述应是可校验的规格，不是脚本
- **可 Delta 定制**：同一 eval 在不同租户/环境跑不同门槛（soft 阈值、judge 模型），声明式可叠加覆盖，命令式不可
- **可 audit**：声明式可 git diff、可静态分析依赖关系
- **不牺牲表达力**：需要任意逻辑处（事件流谓词、复杂 matcher、值变换）用 XPL 表达式，不退化为纯 literal

## 三、参照系统的完整能力清单

> 以下能力清单来自对参照系统（Vercel eve）eval 源码的逐方法核实。本设计**必须覆盖每一项**，映射关系见 §四的能力映射表。

### 3.1 eval 定义字段
- `description?`、`judge?`（per-eval judge 模型）、`timeoutMs?`、`tags?`（`--tag` 过滤）、`metadata?`、`reporters?`（per-eval reporter）
- `test(t)` 必填
- **拒绝的 legacy key**：`id`/`name`（身份从路径派生）、`input`/`run`/`checks`/`scores`/`expected`/`thresholds`/`parseOutput`/`cases`/`requires`/`model`/`modelOptions`

### 3.2 驱动方法（session 级）
- `send(input)` —— 发一个 turn
- `sendFile(text, filePath, mediaType?)` —— 发附件（本地文件转 data URL）
- `respond(...responses)` / `respondAll(optionId)` —— 响应 HITL input request
- `expectInputRequests(filter?: {display?, toolName?})` —— 断言 park 并**返回**匹配的 InputRequest 列表（可读取 options/prompt 做进一步断言）
- `sleep(ms?)` —— 暂停（默认 1s，受 timeout signal 约束）
- `newSession()` —— 创建额外的独立 session（同一 target）
- `target.attachSession(sessionId, opts?: {startIndex?})` —— 附加到已有 session
- `target.dispatchSchedule(scheduleId)` —— 触发 dev-only schedule，返回 `{scheduleId, sessionIds}`
- `target.fetch(path, init?)` —— 认证 fetch（打任意 channel 路由）

### 3.3 读取属性
- `reply` —— 最后一条 assistant 消息（string | null）
- `sessionId`、`events`（完整事件流）、`pendingInputRequests`、`state`（可序列化恢复游标）
- `target.capabilities.devRoutes` —— 是否 dev 目标
- `signal` —— eval 超时 AbortSignal

### 3.4 Run-level 断言（**lazy 求值**：记录后在 run 结束时对最终 run 求值；默认 gate）
- `completed()` / `didNotFail()` / `waiting()`
- `messageIncludes(token: string | RegExp)`
- `calledTool(name, options?: {input?, output?, isError?, times?})`
- `loadedSkill(skill, options?: {output?, isError?, times?})` —— `calledTool("load_skill", {input:{skill}, ...})` 的语法糖
- `notCalledTool(name)` / `usedNoTools()` / `maxToolCalls(max)` / `toolOrder(names)`
- `calledSubagent(name, options?: {remoteUrl?, output?})`
- `noFailedActions()`
- `event(predicate, label)` —— 自定义事件流谓词（函数）
- `outputEquals(value)` —— 严格结构相等
- `outputMatches(schema)` —— Standard Schema 校验

### 3.5 值断言
- `check(value, assertion)` —— 对任意值应用 expect builder

### 3.6 expect builders（`check` 用）
- `includes(substring)` —— gate 默认
- `equals(expected)` —— gate 默认，deepEquals
- `matches(schema)` —— gate 默认
- `similarity(expected: string)` —— **soft 默认**，Levenshtein 归一化相似度

### 3.7 LLM-judge（`t.judge.autoevals.*`，默认 soft）
- `factuality(expected, opts?)` / `summarizes(expected, opts?)` / `closedQA(criteria, opts?)` / `sql(expected, opts?)`
- `JudgeOpts`: `{on?, model?, modelOptions?}` —— `on` 指定评哪个值（默认 `t.reply`）

### 3.8 AssertionHandle 链式（所有断言返回）
- `gate(threshold?)` —— 提升为 gate
- `soft(threshold?)` —— 降级为 soft
- `atLeast(threshold)` —— soft + 阈值

### 3.9 matcher 三态（calledTool/calledSubagent/loadedSkill 的 options）
- **literal**：对象 partial-deep 匹配（matcher 的每个 key 必须匹配，递归），数组元素匹配，原始 `Object.is`
- **RegExp**：测字符串，或非字符串的 JSON 序列化
- **function**：返回 boolean 作为裁决，或返回期望值再按 literal 比较

### 3.10 InputRequest / InputResponse 结构
- `InputRequest`: `{action, allowFreeform?, display?: "confirmation"|"select"|"text", options?: InputOption[], prompt, requestId}`
- `InputOption`: `{description?, id, label, style?: "primary"|"danger"|"default"}`
- `InputResponse`: `{optionId?, requestId, text?}`
- **approval** = `display:"confirmation"` + options 含 `approve`/`deny`

### 3.11 config（`evals.config.yaml`，run-wide 默认）
- `judge?`（默认 judge 模型）、`reporters?`、`maxConcurrency?`（默认 8）、`timeoutMs?`

### 3.12 verdict 三态
- `passed` —— 无错误，所有 gate 持住，所有 soft 阈值满足
- `failed` —— gate 失败 或 执行错误（timeout/transport/thrown）
- `scored` —— 所有 gate 持住，但有 soft 低于阈值

### 3.13 身份派生与数据集扇出
- 单 eval：`evals/<path>.eval.yaml` → id = `<path>`（如 `weather/brooklyn`）
- 数据集扇出：一个文件含多个 case → id = `<file-id>/<zero-padded-index>`（如 `weather/0000`）

### 3.14 turn 级 bespoke 前置条件
- `EveEvalTurn.expectOk()` —— 断言该 turn 成功（失败则抛出，中止 run）

## 四、核心设计

### 4.1 文件组织与身份派生

- **位置**：`evals/` 目录（与 `agent/` 平级，VFS 管理）
- **单 eval 文件**：`evals/<path>.eval.yaml` → id = `<path>`
- **config 文件**：`evals.config.yaml`（位于 `evals/` 根目录）（每个 `evals/` 根目录一个，run-wide 默认）
- **身份**：VFS 路径派生，**禁写 `id`/`name`**（与 agent/tool DSL 一致）
- **Delta 定制**：eval 文件可被 Delta 路径叠加覆盖（如改 soft 阈值、换 judge 模型）

### 4.2 YAML 格式总览

```yaml
# evals/weather/call-get-weather.eval.yaml
description: 天气工具被正确调用并回复
target:
  agent: weather-agent          # 必填，被测 agent 的 .agent.xml name
  # model: openai/gpt-5         # 可选，覆盖 agent 默认模型（仅 eval 运行时）
  # sessionId: <existing>       # 可选，附加到已有 session 而非新建

judge:                          # 可选，per-eval judge 模型（覆盖 config）
  model: openai/gpt-5
timeoutMs: 120000
tags: [smoke, weather]          # 用于 --tag 过滤
metadata: { owner: team-a }

steps:
  - id: ask
    action: send
    input: "Brooklyn 天气如何？"
    assertions:                 # step 级断言（该 step 完成后对累积状态求值）
      - kind: calledTool
        name: get_weather
      - kind: messageIncludes
        token: Sunny
      - kind: completed

assertions:                     # run 级断言（整个 run 结束后求值）
  - kind: judge
    judge: closedQA
    criteria: "应报告温度和天气状况"
    on: reply                   # 引用最终 reply
    severity: soft
    atLeast: 0.5
```

### 4.3 steps —— 有序驱动序列

每个 step = 一个 `action` + action 特定字段 + 该 step 完成后求值的 `assertions`。

| `action` | 语义 | 必填字段 | 对应参照能力 |
|---|---|---|---|
| `send` | 发一个 turn | `input`（string 或 `{message, clientContext?, outputSchema?}`） | `t.send` |
| `sendFile` | 发文本 + 本地文件附件 | `text`, `file`, `mediaType?` | `t.sendFile` |
| `expectInput` | 断言 park 并捕获 InputRequest | `match?`（`{display?, toolName?}`） | `t.expectInputRequests` |
| `respond` | 响应 HITL | `responses`（list of `{optionId?, requestId?, text?}`）或 `respondAll: <optionId>` | `t.respond`/`respondAll` |
| `sleep` | 暂停 | `ms?`（默认 1000） | `t.sleep` |
| `newSession` | 起新独立 session | — | `t.newSession` |
| `attachSession` | 附加已有 session | `sessionId`, `startIndex?` | `t.target.attachSession` |
| `dispatchSchedule` | 触发 dev-only schedule | `scheduleId` | `t.target.dispatchSchedule` |
| `fetch` | 打任意 channel 路由 | `path`, `method?`, `body?` | `t.target.fetch` |

**`expectInput` + `respond` 合并**：实际 HITL 流程是「先 expect 再 respond」。YAML 允许在一个 step 里同时声明 `match`（expect 条件）和 `respond`/`respondAll`（响应），表达「等到这个 input request 出现，然后响应它」。

**step 的 `assertions` 求值时机**：该 step 完成后立即对「从 run 开始到该 step 完成」的累积状态求值（区别于 §4.4 的 run 级 lazy 求值）。这让「第三步后应该已调过 get_weather」可精确表达。

### 4.4 assertions —— 声明式断言 + severity

每个断言：`kind` + 类型特定字段 + 可选 `severity`（`gate`|`soft`）+ 可选 `atLeast`（阈值）+ 可选 `match`（值谓词）。

**完整 kind 清单**（对照 §三，**无遗漏**）：

| `kind` | 默认 severity | 关键字段 | 对应参照能力 |
|---|---|---|---|
| `completed` | gate | — | `t.completed` |
| `didNotFail` | gate | — | `t.didNotFail` |
| `waiting` | gate | — | `t.waiting` |
| `messageIncludes` | gate | `token`（string 或 `/regex/`） | `t.messageIncludes` |
| `calledTool` | gate | `name`, `match?`（`{input?, output?, isError?, times?}`） | `t.calledTool` |
| `loadedSkill` | gate | `skill`, `match?`（`{output?, isError?, times?}`） | `t.loadedSkill` |
| `notCalledTool` | gate | `name` | `t.notCalledTool` |
| `usedNoTools` | gate | — | `t.usedNoTools` |
| `maxToolCalls` | gate | `max` | `t.maxToolCalls` |
| `toolOrder` | gate | `names` | `t.toolOrder` |
| `calledSubagent` | gate | `name`, `match?`（`{remoteUrl?, output?}`） | `t.calledSubagent` |
| `noFailedActions` | gate | — | `t.noFailedActions` |
| `event` | gate | `type?`, `match?`, `xpl?`（见 §4.6） | `t.event` |
| `outputEquals` | gate | `value` | `t.outputEquals` |
| `outputMatches` | gate | `schema`（见 §4.7） | `t.outputMatches` |
| `check` | gate | `value`（值引用，见 §4.5）, `assertion`（includes/equals/matches/similarity） | `t.check` |
| `judge` | **soft** | `judge`（factuality/summarizes/closedQA/sql）, `expected?`/`criteria?`, `on?`, `atLeast?` | `t.judge.autoevals.*` |

**severity 与阈值语义**：
- `severity: gate` —— 失败 → verdict `failed`
- `severity: soft` 无 `atLeast` —— 仅记录，永不 fail
- `severity: soft` + `atLeast: N` —— score < N 时，普通模式仅记录，`--strict` 模式 → verdict `scored`（gate 全过前提下）
- 链式覆盖：`atLeast` 隐含 `soft`（与参照的 `atLeast` → soft 一致）

### 4.5 值引用（`on` / `check.value` / `event` 取值）

参照系统的 `check(value, ...)` 和 `judge.opts.on` 接受任意值。声明式用**值引用**表达：

| 引用语法 | 含义 | 对应参照 |
|---|---|---|
| `reply` | 最终 assistant 消息 | `t.reply` |
| `lastTurn` | 最后一个 turn 的消息 | turn.message |
| `{ step: <id> }` | 指定 step 的消息 | 某 step 后的 reply |
| `{ tool: <name>, field: output }` | 某工具调用的 output | toolCall.output |
| `{ event: action.result, tool: <name> }` | 事件流中的值 | event data |
| `{ xpl: <expression> }` | XPL 表达式求值（任意变换） | function |

### 4.6 matcher（`calledTool`/`calledSubagent`/`loadedSkill`/`event` 的 `match`）

参照的 matcher 三态（literal/RegExp/function）映射：

```yaml
match:
  isError: false
  times: 1
  input:                          # literal → partial-deep 匹配
    city: Brooklyn
  output:
    regex: "Sun.*"                # RegExp → /Sun.*/ 测字符串或 JSON 序列化
    # 或 partial: { condition: Sunny }   # literal partial-deep
    # 或 xpl: "value.temp > 60"          # function → XPL 表达式（返回 boolean 或期望值）
```

matcher 字段三种形态（互斥）：
- `partial: <literal>` —— 对象 partial-deep，数组元素，原始 `Object.is`（对应参照 literal）
- `regex: <pattern>` —— 正则测字符串或 JSON 序列化（对应参照 RegExp）
- `xpl: <expression>` —— XPL 表达式，返回 boolean 裁决或期望值（对应参照 function）

### 4.7 schema 表达（`outputMatches`）

参照用 Standard Schema（Zod）。Nop 用**已有的 schema DSL**（`meta.xdef` 的 column schema 或 JSON Schema）：

```yaml
- kind: outputMatches
  schema:
    type: object
    properties:
      temperatureF: { type: number }
      condition: { type: string }
    required: [temperatureF, condition]
```

### 4.8 judge（LLM-as-judge）

```yaml
- kind: judge
  judge: factuality | summarizes | closedQA | sql
  expected: "..."          # factuality/summarizes/sql 必填
  criteria: "..."          # closedQA 必填
  on: reply                # 值引用，默认 reply
  severity: soft           # 默认 soft
  atLeast: 0.5             # 阈值
```

**judge 模型解析优先级**（innermost-wins，与参照一致）：per-assertion 无 → per-eval `judge` → `evals.config.yaml` 的 `judge` → 缺凭证则**可见 skip**（不报错，与参照一致）。

**约束**：judge 模型**只用于打分，永不改被测 agent**（与参照一致）。

### 4.9 config（`evals.config.yaml`（位于 `evals/` 根目录））

```yaml
judge: { model: openai/gpt-5 }
reporters: [...]            # run-wide reporter
maxConcurrency: 8           # 默认 8
timeoutMs: 120000           # 默认 per-eval 超时
```

### 4.10 数据集扇出

参照用「文件 default-export 数组」。YAML 用顶层 `cases`：

```yaml
description: 多城市天气
target: { agent: weather-agent }
cases:
  - id: brooklyn            # 最终 id = weather/cities/brooklyn
    input: "Brooklyn 天气"
  - id: queens
    input: "Queens 天气"
steps:                       # 共享 step 模板，input 用 ${case.input} 插值
  - action: send
    input: ${case.input}
    assertions:
      - { kind: calledTool, name: get_weather }
```

无 `cases` 时，整文件是一个 case，id = 文件路径。有 `cases` 时，每个 case 共享 `steps`/`assertions`/`target`，id = `<file-id>/<case.id>`（对应参照的 `<file-id>/<zero-padded-index>`，但用语义化 id 而非数字索引——**改进点**：声明式允许命名 id）。

### 4.11 verdict 三态（与参照一致）

```
执行错误（timeout/transport/thrown） → failed
任一 gate 断言失败                     → failed
所有 gate 持住，有 soft < atLeast      → scored（--strict 下算 fail）
所有 gate 持住，soft 全过              → passed
```

### 4.12 退出码与 CLI（提议）

```bash
nop-ai-agent eval                    # 跑所有发现的 eval
nop-ai-agent eval weather            # 按 id 前缀过滤
nop-ai-agent eval --tag smoke        # 按 tag 过滤
nop-ai-agent eval --strict           # soft 阈值不达也 fail
nop-ai-agent eval --target <url>     # 指向远程 agent 实例
nop-ai-agent eval --max-concurrency 4
nop-ai-agent eval --timeout 120000
nop-ai-agent eval --list
```

退出码 0 = 所有 gate 通过（`--strict` 下还要求 soft 达阈值）。

## 五、能力映射核对表（证明无遗漏）

> 逐一对照 §三的参照能力，确认每一项都有 YAML 表达。

| 参照能力 | YAML 表达 | 章节 |
|---|---|---|
| `description` / `judge` / `timeoutMs` / `tags` / `metadata` / `reporters` | 顶层同名字段 | §4.2 |
| `test(t)` | `steps` + `assertions` | §4.3-4.4 |
| 拒绝 legacy key（id/name/input/run/...） | 禁写（schema 校验拒绝） | §4.1 |
| `t.send` / `sendFile` / `respond` / `respondAll` / `expectInputRequests` | `action: send/sendFile/respond/expectInput` | §4.3 |
| `t.sleep` / `newSession` / `attachSession` / `dispatchSchedule` / `fetch` | 对应 action | §4.3 |
| `t.reply` / `sessionId` / `events` / `pendingInputRequests` / `state` / `signal` | 值引用 + runner 内部 | §4.5 |
| `completed/didNotFail/waiting` | `kind: completed/didNotFail/waiting` | §4.4 |
| `messageIncludes` | `kind: messageIncludes`（token 支持 regex） | §4.4 |
| `calledTool` + match options（input/output/isError/times） | `kind: calledTool` + `match` | §4.4-4.6 |
| `loadedSkill` | `kind: loadedSkill` | §4.4 |
| `notCalledTool/usedNoTools/maxToolCalls/toolOrder` | 对应 kind | §4.4 |
| `calledSubagent` + match（remoteUrl/output） | `kind: calledSubagent` + `match` | §4.4-4.6 |
| `noFailedActions` | `kind: noFailedActions` | §4.4 |
| `event(predicate, label)` | `kind: event` + `xpl`（XPL 替代函数） | §4.4-4.6 |
| `outputEquals` / `outputMatches` | 对应 kind | §4.4-4.7 |
| `check(value, assertion)` | `kind: check` + 值引用 | §4.4-4.5 |
| `includes/equals/matches/similarity` | `check.assertion` 字段 | §4.4 |
| `t.judge.autoevals.*` | `kind: judge` | §4.8 |
| `gate()/soft()/atLeast()` | `severity` + `atLeast` 字段 | §4.4 |
| matcher literal/RegExp/function | `partial`/`regex`/`xpl` | §4.6 |
| InputRequest 结构（display/options/...） | `expectInput.match` 读取 + `respond.responses` | §4.3 |
| config（judge/reporters/maxConcurrency/timeoutMs） | `evals.config.yaml` | §4.9 |
| verdict passed/failed/scored | 同 | §4.11 |
| 身份路径派生 | VFS 路径 | §4.1 |
| 数据集扇出 `<file-id>/<index>` | `cases` + `<file-id>/<case.id>` | §4.10 |
| `EveEvalTurn.expectOk()` | step 级 `expectOk: true`（bespoke 前置，失败中止） | §4.3 |
| `t.log(message)` | step 级 `log: <message>`（结构化日志） | §4.3 |

**结论**：§三的 14 个能力分组、40+ 个具体方法/字段，全部有对应 YAML 表达。

## 六、拒绝了什么

| 方案 | 拒绝理由 |
|---|---|
| **命令式脚本（TS/JS test 函数）** | 违反 DSL-First；不可 Delta 定制；不可静态分析。参照系统用命令式是因为 TS 生态，Nop 有 XLang 生态应用 DSL |
| **JSON 而非 YAML** | YAML 支持多行字符串（prompt/criteria）、注释、更可读的 steps 序列；JSON 无注释不利于 eval 这种含大量说明文本的格式 |
| **input/run/checks/scores 字段分离**（参照的 legacy 形态） | 参照系统已明确拒绝：导致驱动和断言割裂、冗余。本设计沿用「steps 内 assertion 内联」 |
| **纯 literal matcher（不支持 XPL）** | 会丧失 `event(predicate)` 和复杂 output 断言的表达力。XPL 是 Nop 已有能力，引入它不增加新依赖 |
| **每个 case 一个文件**（而非 `cases` 扇出） | 数据集场景（如多城市天气）会产出大量重复文件。`cases` 共享 step 模板更 DRY |
| **数字索引 id**（参照的 `<file-id>/0000`） | 声明式允许命名 case id（`brooklyn` 而非 `0000`），更可读、更稳定 |
| **per-eval `requires`（声明外部依赖）** | 参照已拒绝：dev-only 能力从 live target 探测（`target.capabilities.devRoutes`），不声明 |

## 七、与已有设计的关系

| 本设计 | 相关文档 | 关系 |
|---|---|---|
| target.agent → 加载 `.agent.xml` | `nop-ai-agent-dsl.md` | eval 通过 VFS 加载被测 agent 配置 |
| target.sessionId / attachSession | `nop-ai-agent-session-engine.md`、`nop-ai-agent-session-and-storage.md` | eval 复用 session 引擎，可附加已有 session |
| calledTool / toolOrder / loadedSkill | `04-tool-invocation.md`、`skill-system-design.md` | 断言对象是工具调用/skill 加载事实 |
| dispatchSchedule | `nop-ai-agent-task-flow-integration.md` | 触发 schedule 复用 task-flow |
| judge 模型路由 | `nop-ai-agent-llm-layer.md` | judge 走同一 LLM 层（`IChatService`/`ILlmDialect`） |
| target.fetch（channel 路由） | `nop-ai-agent-channel-connector.md` | 打 channel 路由复用连接器 |
| 与 AutoTest | AutoTest（IoC 录制回放） | **互补**：AutoTest 测方法层，eval 测 ReAct 行为层 |
| VFS 管理 eval 文件 | `01-architecture-baseline.md` §七 | eval 文件、config、fixture agent 都经 VFS |

## 八、待定决策（需人决策）

1. **judge 缺凭证的 skip 可见性**：提议「可见 skip 不报错」（与参照一致），需确认。
2. **`cases` 的 step 插值语法**：提议 `${case.input}` 风格的 `${}` 插值（与 Nop 模板约定一致），还是用 XPL `${...}`？需确认是否复用现有模板引擎。
3. **step 级断言 vs run 级断言的求值语义**：本设计让 step 级断言对「该 step 为止的累积状态」求值（比参照的「全部 lazy 对最终 run 求值」更精细）。需确认是否接受这个语义增强，还是严格对齐参照（全部 lazy）。
4. **eval 是否支持 fork 场景**：参照的 `newSession` 是独立 session。Nop 的 `call-agent` fork/exec（`nop-ai-agent-context-model.md` §5）是否需要 eval 支持「断言子 agent 行为」？`calledSubagent` 已覆盖调用断言，但子 agent 内部行为断言是否需要？
5. **reporter 清单**：首版支持哪些 reporter（控制台 / JUnit XML / 其他）？参照支持 Braintrust，Nop 是否需要对应集成？
6. **远程 target 鉴权**：`--target <url>` 指向远程 agent 实例时，鉴权如何提供（复用 channel auth？）？

---

## 与其他文档的关系

- `00-vision.md` —— 本设计遵循的 DSL-First、配置/执行/状态分离约束
- `01-architecture-baseline.md` —— VFS 存储模型、session 模型
- `nop-ai-agent-dsl.md` / `04-tool-invocation.md` —— 被测对象（agent 配置、工具调用）
- `nop-ai-agent-session-engine.md` / `nop-ai-agent-session-and-storage.md` —— session 生命周期（eval 复用）
- `nop-ai-agent-llm-layer.md` —— judge 模型路由
