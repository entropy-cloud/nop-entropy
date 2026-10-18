# SQLPad 深度调研报告

> Status: resolved
> Date: 2026-10-18
> Scope: SQLPad 源码架构 / 在线 SQL 编辑与执行 / 连接与权限模型
> Conclusion: SQLPad 对 EQL 在线编辑器的最大可借鉴点是「batch/statement 执行模型 + 运行记录与结果数据分离存储 + 驱动能力声明式注册」这套小而完整的内核；它的编辑器补全、SQL 安全边界与多标签形态恰恰是 EQL 工具应当重做而非照搬的部分。

> 证据约定：本文所有 `path:line` 均相对于源码仓库 `/Users/abc/sources/sql-editors/sqlpad`，行号来自实际 `read`/`grep`。无法从源码确认的内容显式标注「未确认」。

## 1. 背景与调研问题

- Nop 平台计划为 EQL（`nop-persistence/nop-orm-eql`，标准 SQL + 实体属性导航自动 JOIN）做一个「在线编辑 + 测试」工具，目标形态接近在线版 DBeaver。
- SQLPad 是一个已停止活跃演进、但功能闭环的 Web SQL 编辑器，适合作为「小而完整」的参考实现。
- 本次要回答的问题：
  - Q1 一个在线 SQL 编辑器的最小前后端边界是什么？API 如何切分？
  - Q2 SQL 文本如何变成可执行单元，多语句/选中执行/取消如何处理？
  - Q3 连接配置、Schema 元数据、结果集分别如何建模、缓存、持久化？
  - Q4 多数据库差异被抽象在哪一层？哪些能力是声明式的？
  - Q5 权限模型如何覆盖「连接—查询—执行—结果」四类资源？
  - Q6 哪些实现可直接 adopt，哪些需 adapt 到 EQL 语义，哪些应 reject？
- 约束：只读源码，不构建、不安装、不联网；不修改 SQLPad 代码。

## 2. 仓库与产品概览

| 项 | 事实 | 证据 |
|---|---|---|
| 仓库状态 | 已归档（Archived），作者建议 fork 后重写；源码主体是 2016–2020 年代 JavaScript | `README.md:1`, `README.md:5`, `README.md:7` |
| 版本 | 7.5.7；v7 起不再遵循 semver，patch 版本也可能移除功能 | `server/package.json:3`, `README.md:16` |
| License | MIT | `LICENSE.md:1`, `server/package.json:5` |
| 部署形态 | 单进程 Node 服务（Express）+ 内置 SQLite 元数据库 + 前端静态资源；官方 Docker 镜像监听 3000，数据目录 `/var/lib/sqlpad` | `server/app.js:66`, `server/sequelize-db/index.js:28-41`, `README.md:18-22` |
| 后端技术栈 | Node ≥20、ESM、Express 4、Sequelize 6、Passport、pino、helmet | `server/package.json:7-9`, `server/package.json:52-101` |
| 前端技术栈 | React 18、TypeScript、Vite 4、Ace 编辑器、zustand、SWR、react-window、tau-charts | `client/package.json:21-51` |
| 元数据库 | `SQLPAD_BACKEND_DB_URI` 为空时回退到 `dbPath/sqlpad.sqlite`；也可指向 mssql/mysql/mariadb/postgres | `server/sequelize-db/index.js:28-41`, `server/lib/config/config-items.js:312-316` |
| 支持数据库 | Postgres、MySQL、SQL Server、ClickHouse、Crate、Vertica、Trino、Presto、SAP HANA、Cassandra、BigQuery、SQLite、TiDB、Pinot、Drill、Redshift、Athena，另可通过 ODBC 扩展 | `README.md:11`, `server/drivers/index.js:3-19`, `server/drivers/index.js:41-53` |
| 产品定位 | 写 SQL、执行、看结果、做简单可视化；不含调度、版本管理、EXPLAIN 面板 | `README.md:11`, `docs/src/pages/en/introduction.md:7` |

补充事实：

- 产品文档已迁移为 Astro 站点，内容在 `docs/src/pages/en/api-overview.md:1-6`，覆盖 API、认证、配置、连接模板、seed data、webhooks。
- 项目自述处于 maintenance mode，仅做安全与关键修复（`docs/src/pages/en/introduction.md:13`）。
- 代码注释中保留大量历史兼容逻辑（如 v5 连接字段迁移、v4 数据库不兼容退出），说明其架构演化痕迹较重（`server/models/connections.js:130-138`, `server/server.js:139-149`）。

## 3. 架构总览

### 3.1 前后端边界

- 后端是一个 Express app 工厂：`makeApp(config, models)` 注入配置与模型，路由按「预认证 / 需认证」两批挂载（`server/app.js:66`, `server/app.js:211-250`）。
- 前端是纯 SPA，所有静态资源由 `server/public` 提供；未命中的 API 返回 404，其余路径回退到 `index-template.html` 并注入 `baseUrl`（`server/app.js:255-302`）。
- 前后端只通过 JSON API 交互；除导出接口外，响应体即数据本身，错误使用 4xx/5xx + `{title, detail?}`（`docs/src/pages/en/api-overview.md:14-25`, `server/lib/response-utils.js:23-68`）。
- 认证支持 local、LDAP、Google、OIDC、SAML、auth proxy、HTTP Basic、JWT service token、disable-auth，统一由 Passport 策略 + sessionless 中间件组合（`server/app.js:203-228`, `server/auth-strategies/index.js:42-54`）。

### 3.2 API 设计

- 资源化风格：`/api/connections`、`/api/queries`、`/api/batches`、`/api/statements`、`/api/connection-accesses`、`/api/schema-info`、`/api/format-sql`。
- 列表分页用 `limit/offset` + `Link` 响应头（RFC 5988），而非响应体内包 envelope（`server/routes/queries.js:245-267`）。
- 特殊约定：
  - `POST /api/batches` 立即返回 batch 对象，真正执行在后台进行（`server/routes/batches.js:47-64`）。
  - 结果数据不在 batch/statement JSON 里，需再取 `/api/statements/:id/results`（`server/routes/statements.js:72-76`）。
  - 导出走非 `/api/` 路径 `/statement-results/:id.:format`，直接返回文件流（`server/routes/statement-results.js:31-35`, `server/routes/statement-results.js:126-130`）。
- `GET */api/app` 单独挂在最后且不带 `baseUrl`，用于返回 `currentUser` 与前端可见配置（`server/app.js:252-253`, `server/routes/app.js:25-52`）。

### 3.3 状态管理（前端）

- 全局编辑状态用 zustand 单 store：`editorSessions`（按 sessionId）、`batches`、`statements`、`schemaStates`、`queryResultFormat`（`client/src/stores/editor-store.ts:54-66`, `client/src/stores/editor-store.ts:101-129`）。
- 服务端数据用 SWR：`useBatch`、`useStatementResults`、`useQuery`、`useConnections`、`useConnectionSchema`，并依赖 SWR 的去重/缓存减少轮询请求（`client/src/utilities/api.ts:122-162`, `client/src/utilities/api.ts:193-219`）。
- 动作层是纯函数式模块，直接 `setState` 并调用 API；没有 Redux/Context 的 reducer 层（`client/src/stores/editor-actions.ts:33-52`）。
- 草稿文本额外写入 localforage，按 `queryText:${queryId}` 键持久化（`client/src/utilities/localQueryText.ts:3-19`, `client/src/stores/editor-actions.ts:719-724`）。
- 连接客户端心跳是模块级 `setInterval`，每 10s PUT 一次 keep-alive（`client/src/stores/editor-actions.ts:54-92`）。

### 3.4 分层评价

- 优点：API/模型/驱动/存储四层边界清晰，新增资源成本低；batch 与 statement 的拆解让「一次执行」和「单条 SQL」解耦。
- 局限：无 service 层，路由直接操作 models 与 `sequelizeDb`，业务规则散落在路由和 model（如 ACL 过滤在 `server/routes/queries.js:126-140`，权限装饰在 `server/lib/decorate-query-user-access.js:10-38`）。

## 4. SQL 编辑器实现

### 4.1 编辑器内核

- 使用 `react-ace` + Ace `mode-sql` + `theme-sqlserver`，不是 CodeMirror（`client/src/common/SqlEditor.tsx:3-8`, `client/src/common/SqlEditor.tsx:75-93`）。
- 选项：`enableBasicAutocompletion`、`enableLiveAutocompletion`、`showLineNumbers`、`tabSize: 2`，关闭 snippets 与 worker 之外的花哨能力（`client/src/common/SqlEditor.tsx:58-65`）。
- 默认不做语法诊断（无 lint/diagnostic 集成），仅在服务端做格式化与执行。
- 编辑器高度由 `react-measure` 驱动，支持 autoHeight 与固定 `maxLines` 两种布局（`client/src/common/SqlEditor.tsx:67-120`）。
- 字数换行由全局配置 `editorWordWrap` 控制，在 `useEffect` 中设置 session wrap mode（`client/src/common/SqlEditor.tsx:45`, `server/lib/config/config-items.js:217-221`）。

### 4.2 补全实现（核心可借鉴/需重做点）

- 补全是一个**全局单例 completer**，通过 `langTools.setCompleters([myCompleter])` 注册，而非绑定到某个编辑器实例（`client/src/utilities/updateCompletions.ts:386-390`）。
- 触发策略：开启 Ace live autocomplete；额外监听 `afterExec`，当输入 `.` 时手动 `startAutocomplete`，因为 Ace 会在 `.` 处重置 prefix（`client/src/common/SqlEditor.tsx:31-47`）。
- 上下文判定没有 SQL 解析器，而是关键字启发式：
  - 从光标向前按行回溯，找最近的 `from|join|select|where|group|having|on`（`client/src/utilities/updateCompletions.ts:171-180`, `client/src/utilities/updateCompletions.ts:193-222`）。
  - `from/join` 之后认为想要 TABLE，其余认为想要 COLUMN。
- 候选集构造：
  - 由 schema 树建立 schema/table/column 三级 `AceCompletion`，column 的 `meta` 显示 `dataType + description`（`client/src/utilities/updateCompletions.ts:87-135`）。
  - 用「所有表名/`schema.table`」拼成一个大正则，从当前编辑器全文里识别已引用的表（`client/src/utilities/updateCompletions.ts:166-169`, `client/src/utilities/updateCompletions.ts:251-272`）。
  - 点号前缀走 `columnDotMatches`：`schema.` 给表、`table.` 给列、`schema.table.` 给列（`client/src/utilities/updateCompletions.ts:314-343`）。
  - 无点号时按「已引用表优先」返回，`select` 场景额外补 schema/table 便于向下导航（`client/src/utilities/updateCompletions.ts:345-379`）。
- 局限（对 EQL 直接照搬会踩坑）：
  - 只理解 `schema.table.column` 三段式，不理解实体属性、别名、子查询、CTE、JOIN 关系、函数参数。
  - 大小写统一小写，依赖数据库侧大小写不敏感（`client/src/utilities/updateCompletions.ts:90-117`）。
  - 全局 completer 意味着多标签/多数据源场景下候选集会互相污染（见 4.5）。
  - 关键字集合硬编码，无法按方言配置。

### 4.3 格式化

- 格式化在服务端完成：`POST /api/format-sql` → `sqlFormatter.format(body.query)`，没有传 dialect/options（`server/routes/format-sql.js:12-23`）。
- 前端 `Shift+Enter` 触发，成功后同时写回 store 与 localforage（`client/src/utilities/use-shortcuts.ts:24-27`, `client/src/stores/editor-actions.ts:310-333`）。
- 因此格式化是「通用 SQL 风格」，不感知 EQL 扩展语法；EQL 若复用需替换为自有 formatter 或至少暴露 dialect 参数。

### 4.4 多语句与选中执行

- 编辑器本身不解析多语句；服务端在 batch 创建时用 `sql-limiter` 切分：
  - `selectedText || batchText` 作为输入（`server/models/batches.js:94`）。
  - `sqlLimiter.getStatements()` → `removeTerminator()` → 去空（`server/models/batches.js:102-108`）。
  - 切分失败不会 500，而是把 batch 标记 `error` 并把错误塞进每条 statement（`server/models/batches.js:99-129`）。
- 选中文本通过 Ace `onSelectionChange` 写入 `selectedText`，执行时随 batch 上送；服务端优先执行 `selectedText`，同时保留完整 `batchText` 作为「编辑器全量快照」（`client/src/stores/editor-actions.ts:769-772`, `client/src/stores/editor-actions.ts:422-433`, `server/sequelize-db/batches.js:46-56`）。
- 快捷键：`Ctrl/Cmd+Enter` 执行、`Ctrl/Cmd+S` 保存、`Shift+Enter` 格式化（`client/src/utilities/use-shortcuts.ts:16-27`）。

### 4.5 多标签

- **没有应用内多标签栏**。「New」是一个指向 `/queries/new` 的链接；按住 Ctrl/Cmd/Shift 点击时交给浏览器开新标签页（`client/src/app-header/ToolbarNewQueryButton.tsx:10-30`）。
- 编辑状态的数据结构支持多 session（`editorSessions: Record<string, EditorSession>`），但仓库内除 store 自身外没有任何代码创建第二个 session，路由始终操作 `focusedSessionId`（`client/src/stores/editor-store.ts:54-66`, `client/src/stores/editor-store.ts:115-129`）。
- 结论：SQLPad 的「多标签」= 浏览器标签 + 每标签一个 SPA 实例；不是 Nop 目标形态的 in-app tabs。是否曾支持多 session 属历史残留，未确认。

## 5. 连接与元数据管理

### 5.1 连接配置模型

- 连接分为「数据库存储」与「环境变量/配置文件」两种来源，`findAll` 合并后按名称排序（`server/models/connections.js:51-75`）。
- 连接对象结构：`id/name/description/driver/multiStatementTransactionEnabled/idleTimeoutSeconds` + `data`（驱动专属字段的加密 JSON）（`server/models/connections.js:107-148`）。
- 兼容旧格式：无 `data` 时把顶层未知字段视为驱动字段；读取时再把 `data` 展开到顶层供驱动使用（`server/models/connections.js:34-40`, `server/models/connections.js:130-138`）。
- 驱动专属字段由驱动导出的 `fields` 数组描述（`key/formType/label/description`），前端据此动态渲染表单（`server/drivers/postgres/index.js:199-211`, `client/src/connections/ConnectionForm.tsx:195-293`）。
- 连接保存前用驱动 `fields` 做白名单清洗与布尔归一化，未知字段被丢弃（`server/lib/validate-connection.js:57-94`）。
- 连接测试接口 `POST /api/test-connection` 仅 admin 可用（`server/routes/test-connection.js:13-30`）。

### 5.2 凭据加密与用户模板

- `data` 字段用 `cryptr` 对称加密后入库，密钥来自 `SQLPAD_PASSPHRASE`（`server/models/connections.js:14`, `server/models/connections.js:142-145`）。
- 默认 passphrase 是弱默认值，生产必须覆盖（`server/lib/config/config-items.js:87-91`）。
- 连接字段支持 mustache 模板，用登录用户渲染，例如 `{{user.data.someKey}}`，从而实现「同一连接、不同用户不同 DB 账号」（`server/lib/render-connection.js:8-33`）。
- 该渲染发生在 `ConnectionClient` 构造时，因此 schema 缓存 key 必须基于「渲染后的连接」而非 connectionId（`server/lib/connection-client.js:21-24`, `server/lib/connection-client.js:439-459`）。

### 5.3 Schema 抓取

- 每个驱动实现 `getSchema(connection)`，内部执行各库自己的元数据 SQL；Postgres 从 `pg_catalog` 读 schema/table/column/comment（`server/drivers/postgres/index.js:139-160`）。
- 驱动返回统一层级：`{schemas:[{name,description,tables:[{name,description,columns:[{name,description,dataType}]}]}]}`，无 schema 概念时用 `{tables:[...]}`（`server/drivers/utils.js:19-44`）。
- 归一化逻辑兼容大小写不一致（HANA 全大写）与 `schema_name`/`table_schema` 两种字段名（`server/drivers/utils.js:56-75`）。
- 抓取 schema 时临时把 `maxRows` 提到 1,000,000，避免元数据被截断（`server/lib/connection-client.js:422-436`）。

### 5.4 元数据缓存

- 两套接口并存：
  - 旧：`GET /api/schema-info/:connectionId`，把层级结构再压平成 `{schema: {table: [columns]}}` 后缓存（`server/routes/schema-info.js:40-82`）。
  - 新：`GET /api/connections/:connectionId/schema`，直接返回层级结构并加 gzip（`server/routes/connection-schema.js:24-44`, `server/routes/connection-schema.js:47-55`）。
- 缓存 key：`uuidv5(sorted(renderedConnection key:value 串))`，加 `schemacache:` / `schemacache2:` 前缀做格式版本（`server/lib/connection-client.js:445-459`）。
- 缓存落库在通用 `Cache` 表，TTL 固定 1 天；`?reload=true` 跳过读缓存（`server/models/schema-info.js:34-52`, `server/routes/schema-info.js:15`, `server/routes/schema-info.js:28`）。
- 没有 DDL 变更感知或主动失效机制，只能等 TTL 或用户手动刷新。

### 5.5 Schema 树 UI

- 前端把树形结构压平成缩进列表，再用 `react-window` 虚拟滚动渲染（`client/src/schema/getSchemaList.ts:14-60`, `client/src/schema/SchemaSidebar.tsx:104-105`）。
- 支持按 schema/table/column 名做正则搜索，命中 schema 时展开整棵子树（`client/src/schema/searchSchemaInfo.ts:3-50`）。
- schema 数量 ≤5 时自动展开；超过则不展开，避免渲染爆炸（`client/src/stores/editor-actions.ts:825-838`）。
- 排序用 `Intl.Collator` 对 schema/table 排序，刻意不排序列（保持建表顺序）（`client/src/stores/editor-actions.ts:840-853`）。

## 6. 查询执行链路

### 6.1 数据模型

- `batches`：一次执行请求，存 `queryId`（可空）、`connectionId`、`connectionClientId`、`batchText`、`selectedText`、`chart`、`userId`、`status/startTime/stopTime/durationMs`（`server/sequelize-db/batches.js:4-78`）。
- `statements`：切分后的单条 SQL，存 `sequence`、`statementText`、`status(queued|started|finished|error|cancelled)`、`columns`、`rowCount`、`resultsPath`、`incomplete`、`error`、`executionId`（`server/sequelize-db/statements.js:4-74`）。
- 状态机由 model 方法推进：`updateStarted` / `updateFinished` / `updateErrored` / `updateCancelled` / `updateExecutionId`（`server/models/statements.js:123-160`, `server/models/statements.js:312-318`）。

### 6.2 API 与后台执行

- `POST /api/batches`：创建 batch + statements，立即返回；若 batch 非 error，则异步启动执行，不等待完成（`server/routes/batches.js:28-64`）。
- 对 `connection.isAsynchronous` 的驱动，先 `await executeCancellableBatch()` 拿到 executionId 并持久化，再启动正式执行，以便后续可取消（`server/routes/batches.js:49-58`, `server/lib/execute-batch.js:113-162`）。
- 前端创建 batch 后每 500ms 轮询 `GET /api/batches/:id`，直到 `finished|error|cancelled`（`client/src/stores/editor-actions.ts:458-480`）。
- 轮询而非 SSE/WebSocket；注释也承认「future event mechanism」（`server/routes/batches.js:47-48`）。

### 6.3 顺序执行与失败语义

- 同一 batch 内 statements 串行执行，复用同一个 `ConnectionClient`（若提供 `connectionClientId`，则跨请求复用底层连接）（`server/lib/execute-batch.js:17-37`, `server/lib/execute-batch.js:45-59`）。
- 任一条失败：当前 statement 记 `error`，batch 记 `error`，剩余 `queued` 全部改为 `cancelled`，不再执行（`server/lib/execute-batch.js:61-87`）。
- 所有语句成功才把 batch 置 `finished`（`server/lib/execute-batch.js:89-95`）。
- 结果元数据（columns/rowCount/incomplete）写 statements 表，行数据写结果存储（`server/models/statements.js:216-226`）。

### 6.4 结果存储抽象

- `queryResultStore` 支持 `file | memory | database | redis`，默认 `file`（`server/lib/config/config-items.js:227-231`）。
- file：`dbPath/results/<id 前3位>/<id>.json`，内容为「行数组的数组」，按列名投影（`server/models/statements.js:170-181`）。
- redis：按 `queryHistoryRetentionTimeInDays` 设 TTL，非法/0 时回退 1 小时（`server/models/statements.js:183-196`）。
- database：写入 `Cache` 表并带 `expiryDate`（`server/models/statements.js:198-209`）。
- memory：LRU，max 1000，TTL 1 小时（`server/models/statements.js:37-39`）。
- 过期清理：启动后每 30–90 分钟随机执行一次 `removeOldEntries()`（`server/server.js:90-91`, `server/server.js:182-186`），默认保留 30 天（`server/lib/config/config-items.js:287-291`）。

### 6.5 结果读取、流式与分页

- `GET /api/statements/:id/results` 一次性返回全部行；只有 `status === 'finished'` 才可读（`server/routes/statements.js:47-76`）。
- 没有服务端分页、游标或流式响应；结果先整体进内存再 `JSON.stringify`/读取整个文件（`server/models/statements.js:229-294`）。
- 行数上限在驱动侧实现：`sqlLimiter.limit(query, ['limit','fetch'], maxRows+1)`，多取 1 行用于判断是否截断（`server/drivers/postgres/index.js:95-118`, `server/drivers/clickhouse/index.js:37-76`）。
- 截断时 `incomplete=true`，前端显示「Incomplete」提示并建议减少列数或调大 max rows（`server/lib/connection-client.js:249`, `server/models/statements.js:223`, `client/src/common/IncompleteDataNotification.tsx:4-12`）。
- `maxRows` 来自全局 `queryResultMaxRows`（默认 10000），驱动可被连接字段 `maxrows_override` 覆盖（`server/lib/config/config-items.js:222-226`, `server/models/connections.js:17-23`, `server/drivers/mysql/index.js:151-159`）。

### 6.6 取消与超时

- 取消：`PUT /api/batches/:batchId/cancel`，仅当连接 `isAsynchronous` 才允许；否则返回「driver does not support cancellation」（`server/routes/batches.js:186-195`）。
- 取消链路：遍历 statements 取 `executionId` → `connectionClient.cancelQuery(executionId)` → 状态置 `cancelled`（`server/lib/cancel-batch.js:45-87`）。
- 事实上只有 Athena 驱动声明 `asynchronous = true`，其余驱动（含 Postgres/MySQL/SQL Server）都不能取消正在执行的 SQL（`server/drivers/athena/index.js:10`, `server/drivers/athena/index.js:291`）。
- 超时：
  - HTTP 层 `server.setTimeout(timeoutSeconds * 1000)`，默认 300s（`server/server.js:88`, `server/server.js:239`, `server/lib/config/config-items.js:47-51`）。
  - Postgres 连接字段 `queryTimeout` 映射到 `pg` 的 `query_timeout`（`server/drivers/postgres/index.js:35-38`）。
  - BigQuery 在驱动内部轮询并在接近 `timeoutSeconds` 时主动抛错（`server/drivers/bigquery/index.js:46`, `server/drivers/bigquery/index.js:77-124`）。
  - 没有统一的「每条 statement 超时」服务端强制机制，只有各驱动/HTTP 层各自处理。

### 6.7 错误模型

- 执行错误只保留 `error.message` 映射为 `{title}` 写入 statement，并同样写入 batch 错误（`server/lib/execute-batch.js:61-81`）。
- HTTP 错误用 `ResponseUtils` 统一为 `{title}` + 400/401/403/404；未预期错误由 `/api/` error handler 记日志并返回 500 `{title:'Internal Server Error'}`（`server/lib/response-utils.js:23-68`, `server/app.js:262-276`）。
- 驱动错误与配置错误未区分，代码中多处 TODO 承认这一点（`server/routes/connection-clients.js:88-89`, `server/routes/test-connection.js:20-21`）。
- 缺陷：没有错误位置/错误码/可恢复动作，前端只能原样展示标题。

## 7. 结果集展示与导出

### 7.1 表格渲染

- 表头与表体是两个同步滚动的 `VariableSizeGrid`，按行列虚拟化；只渲染可视单元格（`client/src/common/QueryResultDataTable.tsx:532-556`）。
- 列宽初始值由「列名长度 / maxLineLength」估算，夹在 100–350px；单列时占满宽度（`client/src/common/QueryResultDataTable.tsx:190-220`）。
- 单元格渲染规则：`null → <em>null</em>`、布尔转字符串、`datetime` 去掉 `T/Z`、`date` 截前 10 位、对象 `JSON.stringify(…, 2)`、URL 与 Markdown 链接可点击（`client/src/common/QueryResultDataTable.tsx:30-63`）。
- 支持两种展示模式：`column`（普通表格）与 `fullColumns`（纵向 key/value），可在结果工具栏切换（`client/src/queryEditor/QueryResultHeader.tsx:79-107`, `client/src/types.ts:295`）。
- 列类型不是来自数据库元数据，而是前端 `getColumns` 扫描行数据推断（date/datetime/number/string/boolean/object），并计算 min/max/maxLineLength（`server/lib/get-columns.js:33-181`）。
  - 这也是服务端 columns 元数据的来源（`server/lib/connection-client.js:242`）。
  - 代码注释明确承认「理想情况应由 DB schema 提供，但多数驱动没有」（`server/lib/get-columns.js:55-57`）。

### 7.2 复制与导出

- 复制：把结果拼成 ASCII 表格或纵向块写入剪贴板（`client/src/queryEditor/QueryResultHeader.tsx:141-200`）。
- 导出格式固定三种：`csv/json/xlsx`（`server/routes/statement-results.js:11`）。
- 导出实现从**已存储的结果**读取，不重新执行 SQL（`server/routes/statement-results.js:66`）。
- CSV 用 papaparse，XLSX 用 node-xlsx，JSON 输出「列名→值」对象数组（`server/routes/statement-results.js:93-120`）。
- 导出值会按列类型做日期/对象格式化（`server/routes/statement-results.js:13-29`, `server/routes/statement-results.js:86-91`）。
- 权限：需登录 + batch 属于当前用户 + 全局 `allowCsvDownload` 开关（`server/routes/statement-results.js:46-64`）。
- 文件名用 `sanitize-filename` 清洗并 URI 编码（`server/routes/statement-results.js:69-77`）。
- 图表可导出 PNG，但走前端 canvas（`ExportButton` 的 `onSaveImageClick`），不在服务端（`client/src/common/ExportButton.tsx:39-45`）。

## 8. 查询管理（历史、保存、共享、可视化）

### 8.1 保存的 Query

- `queries` 表存 `name/query_text/connection_id/chart/created_by`；`tags` 与 `acl` 分表存储，创建/更新时在事务内重建（`server/models/queries.js:121-217`, `server/models/queries.js:43-47`）。
- 列表接口手写 SQL 做多条件过滤：connectionId、createdBy、ownedByUser、tags、search、ACL（`server/routes/queries.js:62-198`）。
- 排序字段白名单只允许 `name` 与 `updatedAt`，避免注入（`server/routes/queries.js:166-174`）。
- 权限装饰：`canRead/canWrite/canDelete` 计算后随对象返回，前端据此禁用按钮（`server/lib/decorate-query-user-access.js:10-38`, `client/src/stores/editor-actions.ts:369-374`）。
- 没有版本历史/回滚；`handleCloneClick` 只是复制当前编辑态并跳 `/queries/new`（`client/src/stores/editor-actions.ts:674-689`）。

### 8.2 执行历史

- 历史不是独立表，而是 `vw_query_history` 视图：`batches LEFT JOIN users/connections` + 子查询聚合 statements 的 rowCount/incomplete（`server/migrations/04-00400-query-history-to-batch.js:21-57`）。
- `GET /api/query-history` 支持 `filter` 参数转 Sequelize 条件，非 admin 强制追加 `userId` 过滤；无身份的 noauth 编辑器直接返回空（`server/routes/query-history.js:13-32`, `server/lib/url-filter-to-db-filter.js:8`）。
- 按 queryId 取历史 batch 时默认 limit 40、按 createdAt 升序返回（`server/models/batches.js:52-84`）。
- 恢复历史：把 batchText/chart/connectionId 回填编辑器，并尝试加载对应 batch/statements；结果已被清理时只恢复 SQL（`client/src/stores/editor-actions.ts:533-560`）。
- 清理：按 `queryHistoryRetentionTimeInDays` 删除过期 statements 及其结果数据（`server/models/statements.js:296-310`）。

### 8.3 共享

- Query ACL 记录 `query_id + (user_id | group_id) + write`，并有 user/group 两类唯一索引（`server/migrations/04-00100-query-acl-schema.js:30-94`）。
- 判定规则：admin 或作者拥有全部权限；否则 ACL 中任一条匹配即 `canRead`，匹配且 `write=true` 才 `canWrite`（`server/lib/decorate-query-user-access.js:16-35`）。
- UI 只暴露「Everyone」组与具体用户两种选择，没有任意 group 管理界面（`client/src/queryEditor/ACLInput.tsx:19-43`）。
- 无公开分享链接/只读 token；共享完全依赖登录用户身份。

### 8.4 可视化

- 支持 6 种图表：line、bar、verticalbar、bubble、stacked-bar-vertical、stacked-bar-horizontal（`client/src/utilities/chartDefinitions.ts:3-235`）。
- 图表配置（`chartType` + `fields`）随 query 保存，并在执行时快照进 batch，保证历史结果可用当时的配置复现（`server/sequelize-db/batches.js:57-60`, `client/src/stores/editor-actions.ts:429-432`）。
- 无定时执行/告警/订阅；全仓库只有连接客户端清理与结果清理两类定时器（`server/lib/connection-client.js:93-129`, `server/server.js:182-186`）。

## 9. 多数据库方言抽象

### 9.1 驱动契约

- 驱动注册表是静态对象，键必须等于驱动导出的 `id`（`server/drivers/index.js:21-39`, `server/drivers/validate.js:29-40`）。
- 必需成员：`id`、`name`、`fields`、`getSchema`、`runQuery`、`testConnection`；可选：`Client`、`asynchronous`、`startQueryExecution`、`cancelQuery`（`server/drivers/validate.js:42-49`）。
- 模块加载时对全部驱动跑一遍 validate，缺失成员直接抛错；ODBC 因需编译而动态加载，失败只记日志不阻塞（`server/drivers/index.js:41-59`）。
- `Client` 类代表「可持久的数据库连接」；目前只有 mysql/mysql2/postgres/redshift/sqlite 实现（`server/drivers/postgres/index.js:12`、`server/drivers/mysql/index.js:51`、`server/drivers/mysql2/index.js:49`、`server/drivers/redshift/index.js:10`、`server/drivers/sqlite/index.js:21`）。
- `asynchronous` 代表「支持异步执行 + 可取消」，目前只有 Athena（`server/drivers/athena/index.js:10`, `server/drivers/athena/index.js:291-297`）。

### 9.2 能力如何暴露给前端

- `GET /api/drivers` 返回 `{id,name,fields,supportsConnectionClient}`；`fields` 直接驱动连接表单（`server/routes/drivers.js:11-24`）。
- 连接对象在读取时被装饰：`supportsConnectionClient`、`isAsynchronous`、`maxRows`，以及把 `data` 摊平供旧驱动使用（`server/models/connections.js:17-41`）。
- 前端据此决定：是否显示「连接客户端」按钮、是否允许取消（`client/src/queryEditor/ToolbarConnectionClientButton.tsx:45`, `client/src/stores/editor-actions.ts:233-252`）。

### 9.3 方言差异落在哪

- **Schema SQL**：每个驱动自带 `SCHEMA_SQL` 或等价查询（`server/drivers/postgres/index.js:139-160`, `server/drivers/sqlserver/index.js:8-23`）。
- **行数限制语法**：驱动各自声明 `sqlLimiter.limit(query, ['limit'] | ['limit','fetch'], n)`（`server/drivers/postgres/index.js:102-106`, `server/drivers/athena/index.js:125-134`）。
- **类型/日期处理**：驱动返回 JS 值，统一由 `getColumns` 与渲染层推断；各驱动对 BigInt/日期/对象的处理不完全一致（`server/app.js:51-60`, `server/lib/get-columns.js:58-82`）。
- **超时/SSL/代理/预备语句**：均为驱动私有字段（Postgres 的 `postgresSsl/postgresKey/postgresCert/preQueryStatements` 等，`server/drivers/postgres/index.js:25-75`）。
- **没有方言描述符**：编辑器模式、补全关键字、格式化方言、分页语法都没有集中配置，只散落在驱动实现里。
- 对 EQL 的直接启发：可以把「方言能力」抽成类似 `fields` 的声明式 descriptor（关键字集、标识符引用符、分页语法、是否支持 EXPLAIN、函数表），让编辑器/补全/格式化/执行共用同一份配置。

## 10. 权限与安全

### 10.1 用户与角色

- 角色限定为 `admin | editor | viewer`（`server/sequelize-db/users.js:28-35`）。
- 但代码中几乎所有判断只区分 `role === 'admin'` 与「非 admin」；`viewer` 没有任何额外限制逻辑，判权行为上等同 editor（是否存在前端隐藏未确认）（`server/lib/decorate-query-user-access.js:16`, `server/middleware/must-have-connection-access.js:10`, `server/routes/query-history.js:25`）。
- 认证方式多且可组合：本地密码、LDAP、Google、OIDC、SAML、auth proxy、Basic、JWT service token、disable-auth（`server/app.js:211-228`, `server/auth-strategies/index.js:42-54`）。
- 用户列表 `GET /api/users` 只要求登录，任何用户都能读到全量用户邮箱/角色，用于共享选择器（`server/routes/users.js:148`, `client/src/utilities/api.ts:229-231`）。

### 10.2 连接访问控制

- `ConnectionAccesses` 记录 `connectionId + userId + expiryDate + duration`，通过两个哨兵值支持「所有人」「所有连接」：`__EVERYONE__`、`__EVERY_CONNECTION__`（`server/lib/consts.js:1-7`, `server/models/connection-accesses.js:62-86`）。
- 查询时用 `expiryDate > now` 判断有效；`duration<=0` 视为永久（到期日 2099）（`server/models/connection-accesses.js:22-27`, `server/models/connection-accesses.js:82`）。
- admin 绕过所有连接检查（`server/middleware/must-have-connection-access.js:10-12`）。
- `allowConnectionAccessToEveryone` 默认 true，启动时自动创建「所有连接 × 所有人」的永久 access（`server/lib/config/config-items.js:282-286`, `server/lib/ensure-connection-access.js:10-29`）。
- 连接列表按 access 过滤，非 admin 只看到可访问连接；响应统一清空 password（`server/routes/connections.js:24-43`, `server/routes/connections.js:9-15`）。

### 10.3 查询与执行归属

- Query 级：ACL（见 8.3），删除/读取/更新都先 `decorateQueryUserAccess` 再判权（`server/routes/queries.js:16-29`, `server/routes/queries.js:278-292`, `server/routes/queries.js:333-345`）。
- Batch 级：只允许创建者读取/取消；比较时把 userId 转字符串以兼容类型差异（`server/routes/batches.js:113-131`, `server/routes/batches.js:176-208`）。
- Statement 级：先查 statement，再回查 batch 归属；结果接口额外要求 `status === 'finished'`（`server/routes/statements.js:14-32`, `server/routes/statements.js:47-70`）。
- 导出级：batch 归属 + `allowCsvDownload`（`server/routes/statement-results.js:46-64`）。
- ConnectionClient 级：get/keep-alive/disconnect 校验 owner（disconnect 额外允许 admin），create 仅要求登录（`server/routes/connection-clients.js:49-54`, `server/routes/connection-clients.js:75-113`, `server/routes/connection-clients.js:132-137`, `server/routes/connection-clients.js:175-180`）。

### 10.4 SQL 安全边界

- SQLPad 的定位是「把用户 SQL 原样交给数据库执行」，没有 SQL 白名单、只读强制或语句类型校验；`sql-limiter` 只用于切分与注入 LIMIT（`server/models/batches.js:102-108`, `server/drivers/postgres/index.js:102-106`）。
- 唯一接近只读的是 SQLite 驱动的连接字段 `readonly`（`server/drivers/sqlite/index.js:15-41`）。
- 因此安全性依赖「数据库账号本身的权限」+ 连接访问控制；应用层不阻止 DROP/UPDATE。
- 服务端未发现 CSRF token 机制（源码中无 `csrf` 相关实现）；依赖 `SameSite=strict` 默认值与 `cookieSecure` 配置（`server/lib/config/config-items.js:37-41`, `server/app.js:140-152`）。
- helmet 默认策略启用了大部分头，但显式关闭 frameguard 以支持 iframe 嵌入（`server/app.js:99-106`）。

### 10.5 安全层面的可议点（事实陈述）

- `GET /api/connection-accesses` 与 `/:id` 仅要求登录，普通用户可枚举所有连接授权关系（`server/routes/connection-accesses.js:27-49`）；写操作才是 admin（`server/routes/connection-accesses.js:102-125`）。
- `POST /api/connection-clients` 未复用 `mustHaveConnectionAccess`，理论上登录用户可对任意 connectionId 发起连接（`server/routes/connection-clients.js:109-113`）；是否有网关层兜底未确认。
- 用户列表对所有登录用户开放，泄露组织内邮箱（`server/routes/users.js:148`）。
- 凭据加密是单密钥对称加密，密钥默认值弱且无轮换机制（`server/models/connections.js:14`, `server/lib/config/config-items.js:87-91`）。

## 11. 测试与工程质量

- 后端测试：mocha + supertest，`server/test` 下 39 个测试文件；`test/api` 约 2840 行，覆盖 batches、queries、connections、schema、drivers、users、service tokens、webhooks、auth、migrations（`server/package.json:19`, `server/test/api/batches.js:20-39`）。
- 测试基座 `TestUtils` 会真实启动 Express app + Sequelize，默认内存 SQLite、内存 session/结果存储，可注入配置覆盖（`server/test/utils.js:28-58`）。
- batch 测试通过轮询 + 25ms 间隔等待完成，验证多语句、结果、取消、清理等路径（`server/test/api/batches.js:32-39`, `server/test/api/batches.js:65-80`）。
- 结果存储四后端有专门测试，逐一验证 file/memory/database/redis（`server/test/api/query-result-stores.js:12-40`）。
- CI：Node 20.x/22.x × backend `''/mssql/mysql/mariadb/postgres` 矩阵，15 分钟超时（`.github/workflows/test.yml:16-46`）。
- Lint：eslint + prettier，pre-commit 由 husky + lint-staged 触发（`.github/workflows/lint.yml:1-20`, `package.json:12-22`）。
- 前端没有单元/组件/E2E 测试（`client/src` 下无 `*.test.*` / `*.spec.*`，未发现 Playwright/Cypress 配置）。
- 文档以 Astro 站点形式维护，API 文档与实现有一定同步，但无 schema 校验/生成（`docs/src/pages/en/api-overview.md:1-6`）。
- 工程负债：项目已归档、依赖偏旧、无 TypeScript 后端类型约束（JSDoc + `// @ts-check` 风格有限）。

## 12. 对 Nop EQL 在线编辑器的启示

### 12.1 Adopt（可直接借鉴）

| # | 借鉴点 | 具体做法 | 证据 |
|---|---|---|---|
| A1 | batch/statement 双层执行模型 | 一次「运行」= batch，切分后的每条 SQL = statement；状态、耗时、行数、错误按 statement 记录 | `server/sequelize-db/batches.js:4-78`, `server/sequelize-db/statements.js:4-74` |
| A2 | 立即返回 + 轮询 | 创建 batch 即返回 id，前端 500ms 轮询至终态；实现简单、易接入任务化后端 | `server/routes/batches.js:47-64`, `client/src/stores/editor-actions.ts:458-480` |
| A3 | 运行记录与结果数据分离 | 元数据入库，行数据进 file/redis/db/memory 可插拔存储，按保留期清理 | `server/models/statements.js:162-227`, `server/models/statements.js:296-310` |
| A4 | 驱动能力声明 | `fields` 描述连接表单，`Client/asynchronous` 描述能力，模块加载时统一 validate | `server/drivers/validate.js:29-49`, `server/routes/drivers.js:11-24` |
| A5 | Schema 缓存 key = 渲染后连接哈希 | 支持「同连接不同用户不同账号」场景下的正确缓存隔离 | `server/lib/connection-client.js:439-459` |
| A6 | maxRows+1 探测截断 | 统一多取一行判断 `incomplete`，前端明确提示 | `server/drivers/postgres/index.js:95-118`, `client/src/common/IncompleteDataNotification.tsx:4-12` |
| A7 | 过期时间 + 哨兵的访问控制 | `ConnectionAccess(user|everyone, connection|all, expiry)` 模型简单可审计 | `server/models/connection-accesses.js:62-86`, `server/lib/consts.js:1-7` |
| A8 | 从已存结果导出 | 导出不重跑 SQL，保证「看到什么就导出什么」 | `server/routes/statement-results.js:66`, `server/routes/statement-results.js:93-120` |

### 12.2 Adapt（需改造后借鉴）

| # | SQLPad 做法 | EQL 场景的改造 |
|---|---|---|
| D1 | Ace 全局 completer + 关键字启发式 | 换成基于 EQL AST/元模型的补全：实体、属性、关联属性、别名、函数、参数；completer 必须 per-editor/per-datasource，不能全局单例（对照 `client/src/utilities/updateCompletions.ts:386-390`） |
| D2 | Schema 树 = database/schema/table/column | EQL 树应是「数据源 → 实体 → 属性/关联」，并叠加 `xmeta` 展示名、字典、权限；不能假设物理表存在（对照 `client/src/schema/getSchemaList.ts:21-60`） |
| D3 | 元数据缓存固定 1 天 TTL | 改为「模型版本/元数据版本」驱动的显式失效；TTL 只做兜底（对照 `server/models/schema-info.js:34-52`） |
| D4 | 服务端 sql-formatter 无方言 | EQL formatter 需配置化：关键字大小写、缩进、标识符引用、函数集、EQL 扩展语法（对照 `server/routes/format-sql.js:12-23`） |
| D5 | 不校验 SQL，直接执行 | 执行前增加 EQL parse/validate，返回结构化错误（行列号、错误码、建议）；可选 EXPLAIN 面板 |
| D6 | 权限到 connection + query ACL | EQL 需叠加「数据源访问 + 实体/字段权限 + 行过滤 + 操作类型（查询/更新）」，并复用 Nop 的认证上下文 |
| D7 | 多标签 = 浏览器标签 | 实现真正的 in-app tabs：每 tab 独立 queryText/连接/结果/历史，切换不丢状态（对照 `client/src/app-header/ToolbarNewQueryButton.tsx:10-30`） |
| D8 | 结果整包返回 + 前端虚拟滚动 | 增加服务端分页/游标/流式；至少把「总行数」和「当前页」分离，避免大结果全量进内存 |
| D9 | 错误只有 `{title}` | 使用 Nop `ErrorCode` + 结构化 detail（SQL 片段、位置、驱动原始码） |
| D10 | 连接表单由 `fields` 动态生成 | 直接复用该模式做「EQL 方言能力 descriptor」，但字段需支持分组/默认值/校验/敏感标记 |

### 12.3 Reject（不建议照搬）

| # | 不建议照搬 | 原因 |
|---|---|---|
| R1 | 全局 Ace completer 单例 | 多数据源/多 tab 会互相污染，且无法按方言隔离（`client/src/utilities/updateCompletions.ts:386-390`） |
| R2 | 「任意 SQL 直接执行」的安全模型 | 目标产品面向 EQL 测试，应区分只读/写操作并提供校验与 EXPLAIN；不能只靠 DB 账号兜底（`server/models/batches.js:102-108`） |
| R3 | 结果全量 JSON 落文件/内存 | 大结果会打爆内存与磁盘；EQL 查询可能返回宽行/大结果集（`server/models/statements.js:170-181`） |
| R4 | 用浏览器标签充当多标签 | 无法共享连接、无法做 tab 级状态恢复与对比，不符合「在线版 DBeaver」预期 |
| R5 | 单 passphrase 对称加密 + 弱默认值 | 应使用平台级密钥管理/凭据加密方案，支持轮换与最小暴露（`server/models/connections.js:14`, `server/lib/config/config-items.js:87-91`） |
| R6 | 把 React 18 + Ace + Vite 4 老栈作为基座 | 项目已归档，前端无测试；Nop 已有 `flux-code-editor`/CodeMirror 6，应复用（`README.md:1-7`） |
| R7 | 开放的用户/授权列表读接口 | 普通用户可枚举用户与连接授权，属于信息暴露（`server/routes/users.js:148`, `server/routes/connection-accesses.js:27-49`） |
| R8 | 无版本管理的「保存 query」 | 目标产品需要历史/回滚/对比；SQLPad 的 clone 只是复制文本（`client/src/stores/editor-actions.ts:674-689`） |

### 12.4 建议的 EQL 编辑器最小内核映射

- 执行模型：`EqlExecution`（≈batch）+ `EqlStatement`（≈statement），状态机沿用 `queued/started/finished/error/cancelled`。
- 元数据：`DataSource → Entity → Property/Relation`，缓存 key = `datasource + modelVersion + userContext`。
- 方言：一份 `EqlDialect` descriptor 同时驱动 CodeMirror 补全、formatter、分页语法、EXPLAIN 能力、错误映射。
- 结果：元数据（columns/rowCount/truncated/elapsed）与行数据分离；行数据支持内存/文件/对象存储，并带显式过期。
- 权限：数据源访问 + 实体/字段级 + 操作类型；执行前做 EQL validate，执行中记录审计（谁、什么 SQL、影响行数）。
- UI：in-app tabs + 每 tab 独立 session；结果表格复用虚拟滚动，但数据源改为分页/流式。

## 13. 关键证据清单
1. `README.md:1` — 项目已归档，作者建议 fork 重写。
2. `README.md:11` — 支持的数据库清单与 ODBC 扩展。
3. `server/app.js:211-250` — 预认证路由 / 需认证路由两批挂载。
4. `server/routes/batches.js:47-64` — batch 创建后异步执行、立即返回。
5. `server/models/batches.js:94-129` — `selectedText || batchText` 经 sql-limiter 切分为 statements；解析失败降级为 error。
6. `server/lib/execute-batch.js:45-95` — statements 串行执行，首个错误终止并取消剩余。
7. `server/lib/execute-batch.js:113-162` — 异步驱动先取 executionId 并持久化以支持取消。
8. `server/models/statements.js:162-227` — 结果按 file/redis/database/memory 四种后端写入。
9. `server/routes/statements.js:47-76` — 结果接口一次性返回全部行，无分页。
10. `server/routes/statement-results.js:11`, `server/routes/statement-results.js:93-120` — 导出仅 csv/json/xlsx，且从已存结果转换。
11. `server/lib/connection-client.js:439-459` — schema 缓存 key 为渲染后连接的 uuidv5。
12. `server/models/schema-info.js:34-52` — schema 缓存固定 1 天过期。
13. `server/drivers/validate.js:42-49` — 驱动必需/可选能力校验。
14. `server/drivers/postgres/index.js:95-118` — maxRows+1 的 LIMIT 注入与 incomplete 判定。
15. `client/src/utilities/updateCompletions.ts:386-390` — 补全注册为全局 Ace completer。
16. `client/src/utilities/updateCompletions.ts:193-222` — 用最近关键字猜测 TABLE/COLUMN 上下文。
17. `server/routes/format-sql.js:12-23` — 服务端格式化未传 dialect。
18. `client/src/stores/editor-store.ts:54-66` — editorSessions 数据结构支持多 session。
19. `client/src/app-header/ToolbarNewQueryButton.tsx:10-30` — 「New」实为链接，靠浏览器标签实现多开。
20. `server/lib/decorate-query-user-access.js:10-38` — query ACL 的 canRead/canWrite/canDelete 判定。
21. `server/models/connection-accesses.js:62-86` — 连接访问的哨兵 + 过期判定。
22. `server/routes/connection-clients.js:109-113` — 创建 connection client 仅要求登录（未校验连接访问）。
23. `server/routes/connection-accesses.js:27-49` — 授权列表读接口仅要求登录。
24. `server/sequelize-db/users.js:28-35` — 角色仅 admin/editor/viewer，viewer 无额外限制。

## 14. Open Questions

- [ ] `viewer` 角色的预期语义是什么？当前代码没有任何 viewer 专属限制，是历史遗留还是依赖前端隐藏？
- [ ] Schema 缓存 1 天 TTL 之外，是否有外部工具/运维手段触发失效？DDL 变更后用户看到旧元数据是否被接受？
- [ ] `connectionClientId` 是 per-user 资源，但 batch 创建时是否可能被他人复用？当前只有 create 无访问校验，实际部署是否有反向代理/网关补足？
- [ ] `asynchronous` 只有 Athena 一个实现，取消能力是否值得在 EQL 工具中作为通用能力投入？还是先做「查询超时 + 连接级 kill」？
- [ ] Query ACL 的 `group_id` 除 `__EVERYONE__` 外是否还有其他 producer？UI 未暴露任意组管理。
- [ ] 结果存储为 `database` 时写入通用 `Cache` 表，是否与 schema 缓存共用清理策略？当前只按 `expiryDate` 字段，未见统一清理任务。
- [ ] 前端无组件测试、无 E2E；若参考其交互，是否需要先补一套可回归的 UI 测试基线？
- [ ] SQLPad 已归档，若借鉴其安全模型，是否需要自行承担后续 CVE 跟踪（尤其 Express 4/Sequelize 6/Ace 老依赖）？
