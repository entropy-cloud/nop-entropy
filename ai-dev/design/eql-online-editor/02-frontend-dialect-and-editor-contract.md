# 前端 Dialect Profile 与编辑器契约

适用范围：`nop-chaos-flux` 的 `packages/flux-code-editor`，以及消费 `code-editor` 的页面 schema。本文定义**前端可感知**的配置字段、运行时语义与兼容规则；EQL 专有内容（关键字、校验/执行 action）由页面配置与后端元数据注入，通用组件不含 EQL 字面量分支。

## 1. Dialect Profile

### 1.1 解析规则

`SQLEditorConfig.dialect` 接受「内置方言名」或「profile 名」；`SQLEditorConfig.dialectProfile` 接受内联 profile 对象。解析优先级：

1. 内联 `dialectProfile`（存在即使用）；
2. `dialect` 为已注册 profile 名 → 使用该 profile；
3. `dialect` 为内置方言名（`standard/mysql/postgresql/sqlite/mssql`）→ 使用内置 profile（行为与改造前等价）；
4. 都未提供 → `standard`；
5. 名称未注册或 profile 结构非法 → 降级 `standard` 并在控制台告警（不得阻断渲染）。

注册入口（前端公共契约）：`registerSqlDialectProfile(profile)` 注册/覆盖 profile，`getSqlDialectProfile(name)` 读取；注册是运行期能力，页面只需声明数据，不依赖注册顺序。

页面组装规则（EQL 场景）：`loadMeta.profile` 提供纯数据部分（关键字/类型/CodeMirror 方言/格式化/补全模式），页面把它作为 `sqlConfig.dialectProfile` 原样传入；`validation`/`execution` 由页面在 `sqlConfig` 顶层单独声明。**合并发生在组件内部**（不要求页面做对象展开）：组件按「profile 缺省 → `sqlConfig` 显式覆盖」计算有效 action；`loadMeta.profile` 不包含任何 action。

### 1.2 Profile 字段契约

| 字段 | 语义 | 缺省/约束 |
|---|---|---|
| `name` | profile 标识，用于诊断与调试 | 必填，非空字符串 |
| `keywords` | 补全与高亮关键字集合 | 空集合表示不提供关键字补全 |
| `types` | 类型名补全集合 | 可选 |
| `codeMirrorDialect` | CodeMirror `SQLDialect.define` 的 spec（关键字/内置标识符/类型/引号/注释/转义等） | 可选；缺省用 `standard` 的 spec |
| `format` | 格式化配置：`language`（内置语言名）或 `dialect`（可序列化方言描述，见 §4）、`keywordCase`、`tabWidth`、`indentStyle`、`logicalOperatorNewline` | 可选；缺省不提供格式化按钮 |
| `completion` | 补全模式：`table-alias` / `entity-path` / `none` | 缺省 `table-alias`（保持现状） |
| `validation` | 默认校验 action 与防抖 | 可选；页面 `sqlConfig.validation` 可覆盖 |
| `execution` | 默认执行 action 与结果路径 | 可选；页面 `sqlConfig.execution` 可覆盖 |

约束：

- profile 是纯数据，不得包含函数/闭包（页面 schema 需可 JSON 序列化）。
- profile 的 `validation`/`execution` 只作为缺省值，页面显式配置优先。
- `keywords`、`types` 可由页面从 `loadMeta` 返回值注入；前端不内置 EQL 常量。
- **v1 不提供 EQL 函数补全**：ORM/EQL 没有运行期函数注册表，若前端或页面手写函数清单会形成第二份事实源；函数补全列入 v2，待后端提供权威清单后再加。

## 2. EntitySchema（entity-path 补全数据）

### 2.1 结构与语义

`SQLEditorConfig.entities` 接受内联数组或 scope 引用（与 `tables` 同构的 source 解析机制：`{ source: 'scope', scopePath?, path? }`）。

| 字段 | 语义 | 约束 |
|---|---|---|
| `name` | EQL 实体名（大小写敏感，与 ORM 实体名一致） | 必填 |
| `label` | 展示名/中文名 | 可选，仅用于补全 detail 与树 |
| `querySpace` | 所属数据源 | 可选；用于多数据源过滤 |
| `properties` | 属性/关联列表 | 必填，可为空数组 |

`EntityPropertySchema`：

| 字段 | 语义 | 约束 |
|---|---|---|
| `name` | 属性名 | 必填 |
| `type` | 展示类型（列类型或目标实体名） | 必填，仅展示 |
| `kind` | `column` / `to-one` / `to-many` / `component` | 必填 |
| `refEntity` | 关联目标实体名（`to-one`/`to-many`） | 关联属性必填 |
| `properties` | 嵌套属性（`component`，或已内联的 `to-one`） | 可选 |
| `description` | 说明 | 可选 |

### 2.2 数据来源与生命周期

- 唯一生产来源：后端 `EqlConsole.loadMeta`（见 `03-eql-console-api-and-security.md`）。
- 页面通过 `data-source` 加载后注入 `sqlConfig.entities`；加载完成前编辑器可渲染但处于禁用态（不可执行/校验），entity-path 只提供关键字候选；加载失败时页面显示可重试错误，编辑器保持禁用，不进入半配置状态。
- 前端可按 `querySpace` 过滤候选；不在前端缓存跨页面数据。
- 元数据版本变化（后端返回版本号）时整页重载元数据；不做增量失效（v1）。

## 3. entity-path 补全

### 3.1 上下文识别

从光标前文本识别语句结构与别名绑定（不要求完整 SQL 解析，但必须满足以下规则）：

1. 扫描 `FROM` / `JOIN` / 逗号连接的表源，识别 `实体名 [AS] 别名`；无别名时以实体名本身作为别名。
2. 同一语句内后出现的绑定覆盖先出现的同名别名；未知别名不报错，降级为无候选。
3. 光标位于 `alias.` 之后：候选为该实体（或关联目标实体）的属性。
4. 光标位于 `alias.prop.` 之后：`prop` 为 `to-one`/`component` 时递归进入其属性；为 `to-many` 时仅补 `_some`/`_all`。
5. 光标位于 `alias.coll._some.` / `._all.` 之后：候选为 `refEntity` 的属性。
6. 语句顶层（非点路径）：候选为实体名 + 关键字 + 已绑定别名；v1 不提供函数候选。

### 3.2 候选契约

候选必须携带：`label`（插入文本）、`type`（`keyword/type/entity/property/collection-operator`）、`detail`（类型/实体名）、`info`（说明，可选）、`sortRank`（关键字 < 实体 < 属性，或按 profile 定义）。v1 不提供函数候选。

- 插入文本：实体/属性名按原名插入（不自动加引号）；关键字按 profile 的大小写策略插入。
- 选中实体名时插入文本为实体名；页面可额外提供「插入完整 SELECT 模板」动作（不属于补全本身）。
- 若 `partial` 已包含点号（如 `o.cus`），替换范围必须从 partial 起点开始，避免重复插入。

### 3.3 性能约束

- 单次候选计算只允许遍历当前 `querySpace` 的实体与当前路径的属性；不遍历全部实体间关系图。
- 10k 属性规模下，单次按键候选计算目标 < 50ms（不含渲染）；超出时允许截断候选并在 detail 中提示。

## 4. 格式化

- 配置来源：profile 的 `format`，或 `sqlConfig.format` 覆盖。
- 支持两种模式：内置语言模式（`sql/mysql/postgresql/...`）与自定义 formatter 方言描述（供 EQL 使用）。
- `format.dialect` 结构（字段名冻结）：`reservedKeywords`、`reservedClauses`、`reservedSetOperations`、`reservedJoins`、`reservedFunctionNames`、`reservedDataTypes` 为字符串数组；`stringTypes`、`identTypes`、`paramTypes` 为 sql-formatter 的引号/参数描述对象数组（`{quote, prefixes?}` 或 `{regex}`），不是字符串数组；`lineCommentTypes`、`operators`、`propertyAccessOperators` 为字符串数组。缺省回退标准 SQL 配置。
- 前端把 `format.dialect` 映射为 `sql-formatter` 的 dialect 配置；**不接受**函数、正则回调或 `postProcess` 钩子（保证页面 schema 可序列化、可校验）。映射规则变化视为契约变更，必须更新本节。
- 契约：格式化只改文本，不改语义；连续格式化两次结果一致（幂等）；失败时保留原文并在工具栏提示，不得清空编辑器。
- EQL 场景下实体名/属性名大小写必须保持原样，仅关键字按配置大小写。

## 5. 校验（validation）

### 5.1 配置

| 字段 | 语义 | 约束 |
|---|---|---|
| `enabled` | 是否启用 | 必填 |
| `action` | 校验动作（Ajax action 或 Flux 动作） | `enabled=true` 时必填 |
| `debounceMs` | 编辑防抖 | 缺省 300ms |
| `showOnEdit` | 是否编辑时触发；关闭时仅执行前校验 | 缺省 true |

请求载荷固定：`{ eql }`（后台从 context 补充用户/数据源信息）。

### 5.2 响应与展示

校验返回 `EqlValidateResult` 信封（字段见 `03-eql-console-api-and-security.md` §2.1/§4）；前端职责：

1. 从 `EqlValidateResult.diagnostics` 取诊断（`ok=true` 时为空），将 1-based `line/column` 转换为 CodeMirror 文档 offset。
2. 超过编辑器末尾或列号越界时 clamp 到行尾/文末，不抛错。
3. 无位置信息的诊断映射到整条语句或文档起点，并在 message 前加「(无位置)」标记。
4. 严重级别映射：`error` → CM error，`warning` → CM warning，`info` → CM info。
5. 同一请求序列使用 request-id/AbortController 丢弃过期响应，避免「后发先至」覆盖。

### 5.3 位置换算规则（契约）

- API 行列：`line` 从 1 开始；`column` 从 1 开始，按 **Unicode code point** 计数（与 ANTLR `charPositionInLine` +1 对齐）。
- 前端换算：取第 `line` 行的文本，按 code point 逐字符推进 `column-1` 个位置，得到 UTF-16 offset；代理对（surrogate pair）按 2 个 UTF-16 单元计。
- `endLine/endColumn` 缺省时诊断范围为单字符；越界时 clamp。

## 6. 执行（execution）

### 6.1 配置

| 字段 | 语义 | 约束 |
|---|---|---|
| `enabled` | 是否显示执行按钮 | 必填 |
| `executeAction` | 只读/通用执行动作（后端 `execute`） | 必填；缺少时执行不可用 |
| `executeUpdateAction` | 受控写执行动作（后端 `executeUpdate`） | 可选；仅当 `loadMeta.config.allowWrite=true` 且页面提供时显示写模式 |
| `writeMode` | 写模式交互：`toggle`（默认，工具栏切换+二次确认）或 `always`（仅对可信环境） | 可选 |
| `resultPath` | 响应中结果数据的路径 | 可选；缺省按标准信封解析 |
| `params` | scope → 请求参数的映射（键 → scope 路径），作为“命名参数”的配置形态 | 可选 |
| `paramOrder` | `params` 映射键到 EQL `?` 顺序的排列 | 配置了 `params` 时**必填**；缺失直接报配置错误，不得按字典序静默绑定 |
| `paramNames` | 参数展示名（与 `paramOrder` 对齐），用于参数面板/历史展示 | 可选；仅展示，不参与绑定 |
| `publishResult` | 是否发布 `onResult` 事件 | 可选，缺省 false |
| `limit` / `offset` | 请求参数缺省值 | 可选；页面可覆盖 |
| `readOnly` | 仅前端提示，不构成安全边界 | 可选 |

执行分派契约：只读模式调用 `executeAction`；写模式调用 `executeUpdateAction`。两者都未提供 `executeUpdateAction` 时不得展示写模式。请求参数 `params` 必须按 `paramOrder` 组装为**有序数组**（EQL 只支持 `?` 位置参数）。

能力位消费：写模式只由 `loadMeta.config.allowWrite` 单一来源决定；`loadMeta.capabilities` 只承载未来入口（`explain`/`cancel`/`multiStatement`），v1 恒为 false，前端不得为 false 的能力渲染入口。

**禁止**任何硬编码执行端点（包括历史遗留 `/api/report/execSql`）；后端端点必须由页面显式配置。组件向执行 action 注入的请求字段名为 `eql`（不是历史字段 `sql`）；该改名是 breaking change，迁移见 §7。

### 6.2 请求/响应信封

请求：`{ eql, params?: any[], querySpace?, offset?, limit? }`。`params` 为按 `?` 出现顺序的有序数组。

响应信封（成功与用户输入失败统一）：

```text
{
  ok: boolean,
  diagnostics?: SqlDiagnostic[],
  columns?: [{ name, label?, type?, stdSqlType? }],
  rows?: object[],
  rowCount?: number,
  truncated?: boolean,
  statementKind?: string,
  querySpace?: string,
  elapsedMs?: number,
  compiledSql?: string
}
```

- `ok=true` 时只读执行必含 `columns/rows/rowCount/statementKind/querySpace/elapsedMs`；写执行（`executeUpdateAction`）必含 `affectedRows/statementKind/querySpace/elapsedMs`，不含 `columns/rows`；`ok=false` 时 `diagnostics` 必填，其余可省略。
- 前端按 `columns` 顺序渲染，不依赖对象键顺序。
- `columns[].name` 必须唯一（列名重复时后端返回诊断拒绝，不返回行）；`rows` 为对象数组，键为列名。
- 系统/权限/数据库执行错误**不放进信封**：后端按 `03` §4 抛 `NopException`，前端由 action 错误路径进入 error 状态并展示 code/message。
- 请求取消：前端 abort 后立即恢复按钮可用；v1 后端不承诺服务端取消（`capabilities.cancel=false`），UI 必须提示「已请求取消，服务端可能仍在执行」。

### 6.3 页面级结果区

- `onResult` 是 `CodeEditorSchema` 的**顶层事件字段**（与 `onChange` 同级，类型 `ActionSchema | ActionSchema[]`），不是 `sqlConfig` 内字段；仅在 `publishResult=true` 时触发，载荷为上述统一信封，并以 `action: execute | executeUpdate` 区分来源。系统/权限错误走 action 错误路径，不触发成功载荷。
- 页面可用 `table` 等 renderer 渲染结果区，并实现分页、导出、消息/计划标签；未设置时沿用组件内结果面板。
- 组件内结果面板仍是默认能力，但不得成为页面级分页/导出的唯一实现。

## 7. 其他契约

- **参数与注释（v1 边界）**：EQL 文本只支持 `?` 位置参数；命名参数仅存在于控制台配置（`params`+`paramOrder`），请求发送有序数组。注释（`--`/`/* */`）只被解析器容忍，**不承载语义、不进入生成 SQL**；控制信息用 decorator，不用注释。
- **兼容范围（明确例外）**：未配置任何新字段时，现有行为不变；以下为破坏性变更，发布说明必须标注：(1) 删除历史硬编码执行端点 `/api/report/execSql`，依赖 fallback 的页面必须显式配置 `execution.executeAction`；(2) 执行请求字段由 `sql` 改为 `eql`、`params` 由对象改为有序数组，页面与 bundle 必须同快照发布。

- 快捷键：`Ctrl/Cmd+Enter` 执行整段；`Ctrl/Cmd+Shift+Enter` 执行选区（选区为空时整段）。不得与搜索/缩进快捷键冲突。
- snippet 与变量面板沿用现有字段；EQL 片段由页面注入，不在通用包内预置。
- 组件句柄新增 `component:insertText`（在光标处插入文本并聚焦）与 `component:replaceSelection`（替换当前选区）；用于实体树/属性点击插入。插入行为必须触发 `onChange`。
- 语言注册：新增 profile 不得要求新增 `EditorLanguage` 联合成员；继续以 `language: 'sql'` + dialect profile 表达 SQL-like 方言。
- 回归要求：现有 13 语言、5 个 SQL 方言、diff/colorize/fullscreen/变量面板行为不变；新增行为必须在未配置新字段时完全不生效。
