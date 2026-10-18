# DbGate 深度调研报告

> Status: resolved
> Date: 2026-10-18
> Scope: dbgate 源码架构 / SQL 编辑器 / 执行链路 / 多数据库方言抽象
> Conclusion: 对 EQL 在线编辑器最大的可借鉴点是「每个编辑标签页一个独立数据库会话 + 结果集物化为可随机读的 append-only 数据集 + 按用途拆分的方言语句切分/能力标志」这一整套查询工作台骨架；但 dbgate 的方言是「代码对象」而非「配置」，EQL 需要把同一组能力抽成数据化 descriptor 并补上 EXPLAIN、取消与流式分页。

## 1 背景与调研问题

- 目标产品：为 EQL 提供在线编辑与测试工具，交互对标在线版 DBeaver：数据源/实体树、补全、执行、结果表格、历史、导出、EXPLAIN/校验、多标签，且方言能力尽量配置化。
- 本次对象：`dbgate/dbgate`，HEAD `8e987b2`（v7.3.3-beta.13，2026-10-15），depth-1 clone，无历史、未安装依赖、未构建、未联网。
- 调研问题：
  1. 一个成熟 Web 数据库 IDE 如何划分前后端、连接/会话/结果集三层？
  2. SQL 编辑器内核、补全、格式化、多语句与多结果如何组织？
  3. 元数据如何获取、缓存与失效？
  4. 查询执行、流式返回、分页、取消/超时、事务与错误模型如何落地？
  5. 多数据库方言差异如何建模，哪些做法适合「EQL 配置化方言」，哪些应拒绝？
- 方法与证据约定：`dbgate:` 前缀表示相对 `/Users/abc/sources/sql-editors/dbgate` 的路径；`nop-entropy:` 表示本仓库根相对路径。所有行号来自本次实际 `read`/`grep`；依赖未安装导致无法读取的第三方实现一律标注「未确认」。
- 阅读范围：`README.md`、`CLAUDE.md`、`package.json`、`packages/{web,api,tools,datalib,types}`、`plugins/dbgate-plugin-*`、`docker/`、`app/`、`integration-tests/`、`e2e-tests/`、`.github/workflows/`。未逐行审计全部 1157 个源文件与全部 14 个数据库驱动后端。

## 2 仓库与产品概览

| 项 | 事实 | 证据 |
|---|---|---|
| 产品定位 | 跨平台 (no)SQL 数据库管理器；Web/Docker/Electron 三种运行形态 | `dbgate:README.md:9-11`、`dbgate:README.md:104-115` |
| 版本 | 7.3.3-beta.13，HEAD 8e987b2 | `dbgate:package.json:3`、本次 `git log` |
| 仓库形态 | Yarn workspaces：`packages/*`、`plugins/*`、`integration-tests` | `dbgate:package.json:4-9` |
| 技术栈 | 前端 Svelte 4 + Rolldown；后端 Node.js/Express；Electron 壳 | `dbgate:CLAUDE.md:67`、`dbgate:README.md:110-114` |
| 后端 API | `dbgate-api`，Express 控制器 + 子进程 | `dbgate:CLAUDE.md:66-83` |
| Web 前端 | `dbgate-web`，440 个 `.svelte`，Svelte stores | `dbgate:CLAUDE.md:67`、本次文件计数 |
| 共享库 | `dbgate-tools`（驱动基类/SQL dump/结构分析）、`dbgate-datalib`（网格/变更集）、`dbgate-sqltree`（SQL AST）、`dbgate-filterparser`、`dbgate-rest`、`dbgate-types` | `dbgate:CLAUDE.md:68-72` |
| 支持数据库 | MySQL、PostgreSQL、SQL Server、Oracle、MongoDB、Redis、SQLite、ClickHouse、Cassandra、DuckDB、Firebird 等；Redshift/CosmosDB/libSQL/Firestore 标为 Premium | `dbgate:README.md:23-41` |
| 社区版驱动插件 | 本仓 `plugins/` 下 15 个 plugin 包（含 CSV/Excel/XML/DBF 等文件格式） | `dbgate:plugins/` 目录 |
| License | GPL-3.0；原 MIT 代码在 `LICENSE-OLD` 中声明 | `dbgate:README.md:15`、`dbgate:LICENSE-OLD:1-10` |
| 社区/商业分层 | Community 仓 + `dbgate-pro` 覆盖同名文件后合并为 `dbgate-merged`；本仓 `isProApp()` 恒为 false | `dbgate:CLAUDE.md:122`、`dbgate:packages/api/src/utility/checkLicense.js:15-17` |
| 部署 | Docker（EXPOSE 3000、VOLUME `/root/.dbgate`）；Electron（`app/src/electron.js`）；npm `dbgate-serve` | `dbgate:docker/Dockerfile:19-22`、`dbgate:app/package.json:129`、`dbgate:packages/serve/package.json:12-13` |
| 规模 | 1157 个 `.js/.ts/.svelte` 源文件（排除 dist）；`packages` 6.7M、`plugins` 2.0M | 本次 `find`/`du` |

产品能力面远超「SQL 编辑器」：结构比较、ER 图、可视化查询设计器、数据导入导出、归档、图表、AI 助手、MCP/Codex 入口等（`dbgate:README.md:43-86`）。本报告只抽取与 EQL 在线编辑器相关的部分。

## 3 架构总览

### 3.1 模块划分

| 层 | 模块 | 职责 | 证据 |
|---|---|---|---|
| 前端壳 | `packages/web/src/tabs/` | 每个打开的编辑器/查看器是一个 tab；`QueryTab` 是 SQL 工作台 | `dbgate:CLAUDE.md:92`、`dbgate:packages/web/src/tabs/index.js:1-60` |
| 前端编辑器 | `packages/web/src/query/` | Ace 封装、补全、语句切分 worker、结果 tabs、消息面板 | `dbgate:packages/web/src/query/` |
| 前端数据网格 | `packages/web/src/datagrid/` + `packages/datalib` | 虚拟滚动、分页取数、过滤/排序、变更集 | `dbgate:packages/web/src/datagrid/JslDataGridCore.svelte:15-39` |
| 前端元数据 | `packages/web/src/utility/metadataLoaders.ts` | 声明式 loader + 缓存 + reloadTrigger | `dbgate:packages/web/src/utility/metadataLoaders.ts:16-42` |
| API 控制层 | `packages/api/src/controllers/` | Express 路由，`useController` 按 `_meta` 注册 | `dbgate:packages/api/src/utility/useController.js:24-72` |
| 连接/会话进程 | `packages/api/src/proc/` | fork 子进程持有真实数据库连接 | `dbgate:CLAUDE.md:83`、`dbgate:packages/api/src/controllers/sessions.js:189-213` |
| 结果集存储 | `packages/api/src/utility/JsonLinesDatastore.js` | JSONL 结果文件 + 过滤/排序/分页 | `dbgate:packages/api/src/utility/JsonLinesDatastore.js:246` |
| 驱动层 | `packages/tools/src/driverBase.ts` + `plugins/*` | 方言、SQL dump、连接、stream、元数据分析 | `dbgate:packages/tools/src/driverBase.ts:13-76` |

### 3.2 前后端边界

- 边界是「HTTP JSON + SSE 事件」，不是 GraphQL/RPC：控制器把方法注册为 `/<kebab-case>` POST 路由，异常统一转为 `{ apiErrorMessage }` 与 500（`dbgate:packages/api/src/utility/useController.js:27-30,72-91`）。
- Electron 复用同一批控制器，只是把路由换成 `ipcMain.handle`（`dbgate:packages/api/src/utility/useController.js:34-52`），因此业务逻辑不分叉。
- 前端 `apiCall` 统一附带 `x-dbgate-strmid`，用于把异步事件精确投递回发起请求的浏览器标签（`dbgate:packages/web/src/utility/api.ts:219`、`dbgate:packages/api/src/utility/socket.js:111-140`）。
- SQL 方言/元数据的一部分在前端：驱动插件的 **frontend bundle** 在浏览器里执行，提供 `dialect`、`getQuerySplitterOptions`、连接表单字段等；**backend bundle** 才负责真正连接（`dbgate:plugins/dbgate-plugin-mysql/src/frontend/index.js:1-6`、`dbgate:plugins/dbgate-plugin-mysql/package.json:21-24`）。这是「编辑器补全无需每次问后端方言」的关键设计，但也带来前后端重复定义的风险（见 §9）。

### 3.3 通信协议与事件

- 前端启动一条 `EventSource`：`GET /stream?strmid=...`（`dbgate:packages/web/src/utility/api.ts:83-84`、`dbgate:packages/api/src/main.js:161`）。
- 服务端通过 `socket.emitOwned(ownerKey, event, data)` 把会话事件只发给拥有它的标签页（`dbgate:packages/api/src/utility/socket.js:111-140`）。
- 查询期间的关键事件：`session-recordset-<sesid>`（有新结果集）、`session-info-<sesid>`（日志/错误）、`session-done-<sesid>`、`session-closed-<sesid>`、`session-changedb-<sesid>`（`dbgate:packages/api/src/controllers/sessions.js:82-150`、`dbgate:packages/web/src/tabs/QueryTab.svelte:296-312`）。
- 数据本身不走 SSE，而是写入服务器端 JSONL 文件，前端按 `jslid` 用 `jsldata/get-rows` 分页拉取（`dbgate:packages/api/src/controllers/jsldata.js:71-79`）。**事件只做「通知」，数据面单独取**，这是值得 EQL 直接采用的解耦。

### 3.4 进程模型

- 每个数据库（`conid + database`）在 API 侧 fork 一个 `databaseConnectionProcess`，结构分析与元数据请求都转发给它（`dbgate:packages/api/src/controllers/databaseConnections.js:240-262`）。
- 每个 SQL 编辑标签页可再 fork 一个 `sessionProcess`，持有独立连接/事务；`sessions/create` 完成权限校验后 fork 并发送 `connect` 消息（`dbgate:packages/api/src/controllers/sessions.js:182-236`）。
- 进程隔离的收益：驱动崩溃、长时间查询、连接级状态（事务、临时表、`USE db`）不会污染 API 主进程；代价是 IPC 复杂度、会话数无上限（见 §14）。

### 3.5 状态管理

- 前端全局状态是 Svelte writable stores；打开的 tab 列表用 `writableWithForage` 持久化到 IndexedDB（`dbgate:packages/web/src/stores.ts:83`、`dbgate:packages/web/src/stores.ts:173`）。
- 编辑器内容用 `useEditorData` 写入 localforage，输入 5 秒防抖持久化，`beforeunload` 时退回 localStorage 同步写（`dbgate:packages/web/src/query/useEditorData.ts:24-120`）。
- 服务端状态集中在控制器模块的 `opened` 数组 / `closed` 缓存（`dbgate:packages/api/src/controllers/databaseConnections.js:104-135`、`dbgate:packages/api/src/controllers/sessions.js:48`）。
- 结论：dbgate 没有引入 Redux/XState 类状态机，而是「stores + 缓存键 + 事件失效」；对 EQL 工作台足够，但多标签事务状态需要额外的显式模型。

### 3.6 扩展点

1. 数据库驱动：npm 包 `dbgate-plugin-*`，导出 `drivers`；后端 `requireEngineDriver` 按 `engine = short@package` 动态加载（`dbgate:packages/api/src/utility/requireEngineDriver.js:6-22`）。
2. 前端插件：API 读取 `dist/frontend.js` 文本，浏览器 `eval` 后注册到 `window.DBGATE_PACKAGES`（`dbgate:packages/web/src/plugins/PluginsProvider.svelte:10-40`、`dbgate:packages/api/src/controllers/plugins.js:16-28`）。
3. 文件格式/快速导出/主题：`fileFormats`、`quickExports`、`themes` 同样由插件贡献（`dbgate:packages/web/src/plugins/fileformats.ts:89-98`）。
4. 商业版：按路径覆盖同名文件，而不是运行时 hook（`dbgate:CLAUDE.md:122`）。
5. 脚本级配置：SQL 文件头部的 YAML front matter（`-- >>> ... -- <<<`），可指定 `connectionId/databaseName/autoExecute/chart-*`（`dbgate:packages/tools/src/stringTools.ts:891-918`、`dbgate:packages/web/src/tabs/QueryTab.svelte:656-661`）。

## 4 SQL 编辑器实现

### 4.1 编辑器内核

- 使用的是 **Ace**（`ace-builds ^1.44.0`），不是 CodeMirror：`AceEditor.svelte` 直接 `ace.edit()`，并注册 `mode-sql/mysql/pgsql/sqlserver` 等 mode（`dbgate:packages/web/src/query/AceEditor.svelte:65-90`、`:476`；`dbgate:packages/web/package.json:25`）。
- `SqlEditor.svelte` 只做三件事：engine→Ace mode 映射、包装 `AceEditor`、在 tab 可见且非只读时挂载补全（`dbgate:packages/web/src/query/SqlEditor.svelte:2-6,29,45-52`）。
- 方言 mode 映射是硬编码的 3 项：`mssql→sqlserver`、`mysql→mysql`、`postgres→pgsql`，其余落回通用 `sql`（`dbgate:packages/web/src/query/SqlEditor.svelte:2-6,29`）。非 SQL 引擎走 `driver.editorMode`（Mongo=`javascript`、Redis=`text`）（`dbgate:packages/web/src/tabs/QueryTab.svelte:802`、`dbgate:plugins/dbgate-plugin-mongo/src/frontend/drivers.js:54`）。
- 结论：编辑器内核本身不含任何数据库语义；语义全部来自「Ace mode + 补全数据 + 切分器」。

### 4.2 补全

- 使用 Ace `ext-language_tools` 的异步 completer，每次触发时并发拉取 `getDatabaseInfo`、`getSchemaList`、`getConnectionInfo`（`dbgate:packages/web/src/query/codeCompletion.ts:82-90`）。
- 候选项分四层：
  1. 17 个通用关键字，按设置转大小写（`dbgate:packages/web/src/query/codeCompletion.ts:9-27,95-107`）；
  2. schema 名（score 1000）；
  3. 当前默认 schema 下的 table/view/matview/function/procedure，可选自动追加表别名缩写（`dbgate:packages/web/src/query/codeCompletion.ts:33-81,92,185`）；
  4. 已识别 source 的列；带别名时插入 `alias.column`，score 1200（`dbgate:packages/web/src/query/codeCompletion.ts:187-204`）。
- 上下文识别只有两种：
  - 正则 `/([a-zA-Z0-9_]+)\.([a-zA-Z0-9_]*)?$/` 命中「表/别名.前缀」时，按 `analyseQuerySources` 得到的 source 找表/视图的列（单层，不支持 `a.b.c`）（`dbgate:packages/web/src/query/codeCompletion.ts:111-165`）。
  - 否则用最后一个关键字判断是否只补表（`FROM/JOIN/UPDATE/DELETE`）或只补过程（`EXEC/EXECUTE/CALL`）（`dbgate:packages/web/src/query/codeCompletion.ts:168-186`）。
- `analyseQuerySources` 是**空白分词 + 关键字前后看**的启发式解析，不是语法分析器；它只识别 FROM/JOIN/UPDATE/DELETE/INTO/INSERT 后的 source 与可选别名（`dbgate:packages/web/src/query/analyseQuerySources.ts:45-110`）。
- 自动弹出策略：输入字母或 `.` 时弹出；输入空格且行尾是 `from/join/update/call/exec/execute` 时弹出（`dbgate:packages/web/src/query/codeCompletion.ts:216-240`）。
- 评价：作为「单库、单层、表别名」补全足够；**没有关系展开、没有属性路径、没有类型信息**，无法直接满足 EQL `o.customer.address.city` 与 `_some` 集合算子。

### 4.3 格式化

- 直接 `sqlFormatter.format(editor.getValue())`，没有传 language/options（`dbgate:packages/web/src/tabs/QueryTab.svelte:557-561`），依赖 `sql-formatter ^3.1.0`（`dbgate:packages/web/package.json:53`）。
- 因此格式化与 `driver.dialect` 无关：MySQL 反引号、MSSQL 方括号、PG `::` 等差异不会被格式化器感知。这是 dbgate 的明显短板，也是 EQL 可以直接改进的点。
- 其他位置（对象 DDL、脚本模板）也复用同一 formatter（`dbgate:packages/web/src/utility/applyScriptTemplate.ts:19,75-84`）。

### 4.4 多语句、多结果与「执行当前」

- 语句切分在 Web Worker 中完成：`QueryParserWorker.js` 调 `splitQuery(text, options)` 并 `postMessage` 结果；Worker 创建失败时退化为同步 fallback（`dbgate:packages/web/src/query/QueryParserWorker.js:1-8`、`dbgate:packages/web/src/query/queryParserWorkerFallback.js:1-5`、`dbgate:packages/web/src/query/AceEditor.svelte:311,392-405`）。
- 切分选项来自驱动：`driver.getQuerySplitterOptions('editor')`，再叠加「按空行切分」设置（`dbgate:packages/web/src/tabs/QueryTab.svelte:775-778`、`dbgate:packages/web/src/tabs/QueryTab.svelte:393-397`）。
- 切分结果带 `trimStart/trimEnd/start/end`，编辑器用它做两件事：
  1. gutter 上为每个语句起点画「可执行」标记（`dbgate:packages/web/src/query/AceEditor.svelte:357-380`）；
  2. 光标落在哪个区间，就把它作为 `currentPart`（`dbgate:packages/web/src/query/AceEditor.svelte:415-455`）。
- 点击 gutter 的语句标记可直接执行该片段（`dbgate:packages/web/src/query/AceEditor.svelte:546-560`）。
- `getCurrentCommandText()` 的优先级：currentPart → 选中文本 → 当前行（可由设置关闭）→ 全文档（`dbgate:packages/web/src/query/AceEditor.svelte:204-230`）。
- 执行入口：`execute()` 执行选中或全文；`executeCurrent()` 执行当前语句；工具栏拆分按钮同时提供两者（`dbgate:packages/web/src/tabs/QueryTab.svelte:505-514,904-907`）。
- 多结果由服务端逐 recordset 建一个 `jslid`，前端每收到一个 `session-recordset` 就追加一个结果 tab；也支持「所有结果放在一个 tab 内多网格」（`dbgate:packages/web/src/query/ResultTabs.svelte:65-78,108-140`、`dbgate:packages/web/src/query/AllResultsTab.svelte:1-30`）。

### 4.5 参数与脚本级配置

- 支持 5 种参数风格（`?`、`@`、`:`、`$`、`#`）：先用 splitter 的 `extractQueryParameters` 找参数，弹模态框收集值，再用 `replaceQueryParameters` 替换后执行；参数值存 localStorage（`dbgate:packages/web/src/tabs/QueryTab.svelte:174-183,383-424`）。
- front matter 是 dbgate 很有特色的「脚本即配置」：YAML 写在 SQL 注释块里，可控制 `autoExecute`、`connectionId/databaseName`、`splitterInitialValue`、`chart-N/selected-chart`（`dbgate:packages/tools/src/stringTools.ts:891-918`、`dbgate:packages/web/src/tabs/QueryTab.svelte:656-661,706-716`）。
- 安全注意：带执行授权的团队文件在真正执行前会把 front matter 块「抹成空行」而不是删除，以保证行号一致（`dbgate:packages/api/src/controllers/sessions.js:27-33`）。这说明「注释里的配置」会参与权限判定，EQL 若采用类似机制必须把配置解析纳入安全边界。

### 4.6 诊断

- 服务端返回的 `info` 消息带 `severity` 与相对行号，`StreamHandler.info` 会把相对行号加上语句起始行（`dbgate:packages/api/src/utility/handleQueryStream.js:354-362`）。
- 前端把语句标记与错误行合并成 Ace annotations（`dbgate:packages/web/src/query/AceEditor.svelte:353-380`），消息面板点击可 `gotoLine`（`dbgate:packages/web/src/tabs/QueryTab.svelte:616-621`）。
- **没有编辑期 lint / 语义校验**：错误只能等执行后由数据库返回。`Explain` 按钮的语义是「AI 解释错误」，不是 `EXPLAIN` 执行计划（`dbgate:packages/web/src/query/MessageViewRow.svelte:54-61`、`dbgate:packages/web/src/tabs/QueryTab.svelte:670-678`）。

## 5 元数据与连接管理

### 5.1 连接管理

- 连接 CRUD 在 `connections` 控制器；保存时加密（`encryptConnection`），读取列表时按权限决定 mask 或加密返回（`dbgate:packages/api/src/controllers/connections.js:376-380,499`）。
- 连接可由环境变量声明：`CONNECTIONS=a,b` + `ENGINE_a`/`SERVER_a`/`USER_a`/`PASSWORD_a`/`READONLY_a`/`ALLOWED_DATABASES_a` 等（`dbgate:packages/api/src/utility/envtools.js:8-65`）。这是「部署即配置」的典型做法，对 EQL 的数据源预置很有参考价值。
- 连接表单字段由驱动决定：`showConnectionField(field, values)` 返回布尔，前端据此显隐字段（`dbgate:packages/types/engines.d.ts:297-303`、`dbgate:plugins/dbgate-plugin-mssql/src/frontend/driver.js:160-176`）。

### 5.2 元数据 API

- 核心是「整库结构快照 + 轻量 schema 列表」：
  - `POST /database-connections/structure` 返回该 database 的完整 `DatabaseInfo`（tables/views/columns/keys/...）（`dbgate:packages/api/src/controllers/databaseConnections.js:766-847`）。
  - `POST /database-connections/schema-list` 只返回 schema 列表，转给驱动的 `listSchemas`（`dbgate:packages/api/src/controllers/databaseConnections.js:475-479`、`dbgate:packages/api/src/proc/databaseConnectionProcess.js:337`）。
  - `POST /metadata/table-info`、`/metadata/sql-object-info` 直接从内存快照里查，不再访问数据库（`dbgate:packages/api/src/controllers/metadata.js:30-48`）。
- 结构快照由子进程分析：`driver.analyseFull` 或 `analyseIncremental`（`dbgate:packages/api/src/proc/databaseConnectionProcess.js:96-115`）；驱动通过 `supportsIncrementalAnalysis` 声明是否支持增量（`dbgate:packages/types/engines.d.ts:262`、`dbgate:plugins/dbgate-plugin-postgres/src/frontend/drivers.js:131`）。
- 连接关闭后结构不立即丢弃，而是放到 `closed` 缓存，下次打开复用（`dbgate:packages/api/src/controllers/databaseConnections.js:258-266`）。

### 5.3 缓存与失效

- 前端把每个 loader 声明成 `{url, params, reloadTrigger, transform}`（`dbgate:packages/web/src/utility/metadataLoaders.ts:16-42`）。
- 缓存键是 `stableStringify({url, ...params})`，并把 key 注册到 `reloadTrigger` 下（`dbgate:packages/web/src/utility/metadataLoaders.ts:246-263`、`dbgate:packages/web/src/utility/cache.ts:73-88`）。
- 服务端在结构/状态/版本变化时 `socket.emitChanged('database-structure-changed', {conid, database})`（`dbgate:packages/api/src/controllers/databaseConnections.js:119-135`），SSE 的 `changed-cache` 事件在前端触发对应 key 的清理与重载（`dbgate:packages/web/src/utility/cache.ts:168`、`dbgate:packages/api/src/utility/socket.js:95`）。
- 评价：这是「声明式 loader + 细粒度失效」的干净实现；EQL 的实体元数据应按 `querySpace/entity` 建立类似 trigger，而不是整库失效。

### 5.4 schema 树

- 前端树不是后端返回的嵌套树，而是把 `DatabaseInfo` 的扁平列表按 `objectTypeField` 合并、排序、过滤后渲染（`dbgate:packages/web/src/widgets/SqlObjectList.svelte:70-114`）。
- schema 选择/默认 schema 由 `findDefaultSchema` 决定：优先存储里记录的 schema，其次 `schema.isDefault`，其次 `dialect.defaultSchemaName`，最后取第一个（`dbgate:packages/tools/src/schemaInfoTools.ts:5-21`、`dbgate:packages/web/src/widgets/SchemaSelector.svelte:36,101`）。
- 多 schema 有两种建模：`connection.useSeparateSchemas` 时 database 名用 `db::schema` 复合名；否则 schema 只作为对象属性（`dbgate:packages/tools/src/schemaInfoTools.ts:38-56`、`dbgate:packages/web/src/utility/common.ts:133-145`）。

## 6 查询执行链路

### 6.1 端到端顺序

1. `executeCore` 先解析参数（若有则弹框），再进入 `executeCoreWithParams`（`dbgate:packages/web/src/tabs/QueryTab.svelte:401-424`）。
2. 首次执行时 `POST /sessions/create`，服务端校验连接权限后 fork `sessionProcess`（`dbgate:packages/web/src/tabs/QueryTab.svelte:453-461`、`dbgate:packages/api/src/controllers/sessions.js:182-236`）。
3. `POST /sessions/execute-query` 传 `sql/autoCommit/limitRows/frontMatter/autoDetectCharts`（`dbgate:packages/web/src/tabs/QueryTab.svelte:466-473`）。
4. 服务端做 `dbops/query` + 数据库 `run_script` 角色校验、写审计日志，然后把 SQL 通过 IPC 发给子进程（`dbgate:packages/api/src/controllers/sessions.js:242-315`）。
5. 子进程用 `driver.getQuerySplitterOptions('stream')` 切分，逐条 `driver.stream(...)`（`dbgate:packages/api/src/proc/sessionProcess.js:157-210`）。
6. `StreamHandler.recordset()` 为每个结果集创建一个 `QueryStreamTableWriter`，写 JSONL 头行（列元数据）与数据行（`dbgate:packages/api/src/utility/handleQueryStream.js:207-259`、`:14-38`）。
7. 每写一批/结束时更新 `.stats` 旁文件并通知前端（`dbgate:packages/api/src/utility/handleQueryStream.js:91-103`；子进程 `stats` 消息经 `sessions.js:121-123` 转到 `jsldata.notifyChangedStats`，见 `dbgate:packages/api/src/controllers/jsldata.js:168-171`）。
8. 前端 `ResultTabs` 监听 `session-recordset-<sesid>` 追加结果 tab，网格通过 `jsldata/get-info`、`get-stats`、`get-rows` 拉取（`dbgate:packages/web/src/query/ResultTabs.svelte:182`、`dbgate:packages/web/src/datagrid/JslDataGridCore.svelte:15-39`）。

### 6.2 流式与分页

- 真正的「流式」发生在服务端：结果行随到随写盘，不驻留内存（`dbgate:packages/api/src/utility/handleQueryStream.js:46-84`）。
- 前端网格的分页是「对已物化结果集的随机读」：`getRows(offset, limit, filters, sort)`（`dbgate:packages/api/src/controllers/jsldata.js:71-79`）。
- 过滤/排序也在服务端对 JSONL 做：当前 filter/sort 变化时重建 reader；排序需要时生成外部排序文件（`dbgate:packages/api/src/utility/JsonLinesDatastore.js:170-230,246-254`）。
- 行数上限是「执行期硬切断」：`limitRows` 达到后发 error info、标记 canceled、关闭 writer 后 **`process.exit(0)`**（`dbgate:packages/api/src/utility/handleQueryStream.js:280-310`）。这会把整个会话进程杀掉，属于粗暴但有效的保护。

### 6.3 取消与超时

- 取消 = 杀会话进程：`POST /sessions/kill` → `subprocess.kill()`，前端把 `sessionId` 置空（`dbgate:packages/api/src/controllers/sessions.js:434-441`、`dbgate:packages/web/src/tabs/QueryTab.svelte:516-521`）。
- 代码里保留了真正的 `cancel` 通道注释（`// cancel: handleCancel`），但未启用（`dbgate:packages/api/src/proc/sessionProcess.js:337`、`dbgate:packages/api/src/utility/handleQueryStream.js:160-164`）。
- 没有「单条查询超时」参数：`executeQuery` 只传 `autoCommit/autoDetectCharts/limitRows/frontMatter`（`dbgate:packages/api/src/controllers/sessions.js:297-305`）。驱动的 `commandTimeout` 仅用于 `driver.query`（导入/导出/DDL），不在 stream 路径（`dbgate:plugins/dbgate-plugin-postgres/src/backend/drivers.js:564-573`）。
- 会话生命周期：前端每 15 秒 ping（`dbgate:packages/web/src/tabs/QueryTab.svelte:274-282`）；服务端 2 分钟无 ping 或 15 分钟无活动即关闭连接并退出进程（`dbgate:packages/api/src/proc/sessionProcess.js:21,350-378`）。

### 6.4 只读、事务与隔离级别

- 只读在服务端兜底：`allowExecuteCustomScript` 中，若驱动声明 `readOnlySessions` 则允许（驱动会开只读会话），否则 `connection.isReadOnly` 为真就拒绝（`dbgate:packages/api/src/utility/handleQueryStream.js:416-427`）。
- 事务模型由驱动标志区分：
  - `supportsTransactions` + 非 `implicitTransactions`：需要显式 `BEGIN`，前端提供 Begin/Commit/Rollback 按钮（`dbgate:packages/web/src/tabs/QueryTab.svelte:583-601`）。
  - `implicitTransactions`（如 Oracle）：第一条 SQL 即开事务，前端只提供 autocommit 切换（`dbgate:packages/web/src/tabs/QueryTab.svelte:587-595`、`dbgate:plugins/dbgate-plugin-oracle/src/frontend/driver.js:107`）。
- 事务控制走 `sessions/execute-control-command` → `driver.createDumper().beginTransaction()/commitTransaction()/rollbackTransaction()`（`dbgate:packages/api/src/controllers/sessions.js:322-337`、`dbgate:packages/api/src/proc/sessionProcess.js:120-150`）。
- 隔离级别由驱动声明 `isolationLevels` 与 `setTransactionIsolationLevel`，前端按列表渲染（`dbgate:packages/web/src/tabs/QueryTab.svelte:1010-1050`、`dbgate:packages/api/src/proc/sessionProcess.js:78-114`）。

### 6.5 错误模型

- 传输层：HTTP 500 + `{ apiErrorMessage }`；业务错误（如缺凭据）用 `{ missingCredentials, detail }` 让前端弹登录框（`dbgate:packages/api/src/utility/useController.js:83-90`、`dbgate:packages/web/src/utility/api.ts:112-165`）。
- 执行层：错误被转成 `msgtype: 'info'` 的 `severity: 'error'` 消息，并置 `canceled = true`，因此后续语句不再执行，但调用方仍会收到 `done`（`dbgate:packages/api/src/utility/handleQueryStream.js:323-362`、`dbgate:packages/api/src/proc/sessionProcess.js:380-400`）。
- 日志/消息使用 `DBGM-NNNNN` 风格错误码（新代码要求统一 `DBGM-00000`），但错误码不是结构化字段，只是消息文本的一部分（`dbgate:CLAUDE.md:107`、`dbgate:AGENTS.md:5`）。
- 对 EQL 的启示：`code + severity + line/column + params` 的结构化诊断应显式建模，不要复用「字符串里带码」的做法。

## 7 结果集展示

| 能力 | 实现 | 证据 |
|---|---|---|
| 结果存储 | 每结果集一个 `.jsonl`，首行是 `{columns, __isStreamHeader}` 头；同目录 `.stats` 存 rowCount/changeIndex/isFinished | `dbgate:packages/api/src/utility/handleQueryStream.js:23-38,91-103` |
| 网格取数 | 虚拟网格按可见区域请求 `offset/limit`，行数来自 `get-stats` | `dbgate:packages/web/src/datagrid/JslDataGridCore.svelte:15-39` |
| 过滤/排序 | 服务端对 JSONL 逐行 `evaluateCondition`；排序生成外部排序文件并缓存 | `dbgate:packages/api/src/utility/JsonLinesDatastore.js:118-125,170-230` |
| 结果集缓存 | `DatastoreCache` LRU：最多 32 个、空闲 5 分钟关闭 | `dbgate:packages/api/src/utility/DatastoreCache.js:2-7,69-80` |
| 导出 | 网格注册 quick export；底层可按 `jsldata/stream-rows` 以 NDJSON 拉全量，再交给 CSV/Excel/JSON writer 插件 | `dbgate:packages/web/src/datagrid/JslDataGridCore.svelte:138-185`、`dbgate:packages/api/src/controllers/jsldata.js:87-139`、`dbgate:packages/web/src/utility/fetchAll.ts:94` |
| 行编辑 | 结果集可编辑是 Pro 能力；只允许 UPDATE，且要求目标表、changed fields、完整行条件 | `dbgate:packages/web/src/query/ResultTabs.svelte:56`、`dbgate:packages/api/src/proc/sessionProcess.js:212-260` |
| 列可编辑性推导 | 有 `enrichQueryResultColumns`：解析 SQL/视图 DDL，把结果列映射回源表列与主键 | `dbgate:packages/api/src/utility/queryResultMetadata.js:1-80`、`dbgate:packages/api/src/utility/handleQueryStream.js:365-379` |
| 图表 | Pro 能力；`ChartProcessor` 在写行时增量聚合，结束后发 `charts` 事件 | `dbgate:packages/api/src/utility/handleQueryStream.js:105-145` |
| 结果变更集 | `createChangeSet` + undo reducer，保存前生成 SQL 预览并二次确认 | `dbgate:packages/web/src/query/ResultTabs.svelte:65-76`、`dbgate:packages/web/src/tabs/QueryTab.svelte:366-380` |

评价：结果集「物化 + 旁文件统计 + 随机分页 + 服务端过滤排序」是 dbgate 最值得借鉴的工程决策，因为它把「数据库游标生命周期」从 UI 中彻底解耦；但落盘 JSONL 在多人 SaaS 场景需要配额、加密与 TTL（见 §12）。

## 8 查询管理

- 历史：`POST /query-history/write` 追加一行 JSON 到 `datadir/query-history.jsonl`；`read` 用 `readFileReverse` 从文件尾部反向读，支持 `skip/limit/filter`（`dbgate:packages/api/src/controllers/queryHistory.js:46-67`、`dbgate:packages/api/src/utility/readFileReverse.js:1-20`）。
- 历史 UI 只取最近 100 条，防抖搜索，点击后用历史 SQL 新开 QueryTab；从不上传 SQL 内容做分析（`dbgate:packages/web/src/widgets/QueryHistoryList.svelte:19-72`）。
- 保存脚本：以文件形式存于 `files/sql/`，通过 `files/save` 写入；打开时读取 front matter 里的 `connectionId/databaseName` 自动切换连接（`dbgate:packages/api/src/controllers/files.js:197-235`、`dbgate:packages/web/src/appobj/SavedFileAppObject.svelte:25-38`）。
- 应用内查询：app JSON 的 `files[].type == 'query'` 也是保存脚本的一种形态，元数据由 `apps/get-all-apps` 提供（`dbgate:packages/api/src/controllers/apps.js:15-60`）。
- **EXPLAIN/执行计划：本仓未发现专用 UI 或 API**。全仓 `explain` 命中主要是「AI 解释错误」按钮与个别驱动的只读语句白名单（`dbgate:packages/web/src/query/MessageViewRow.svelte:54-61`、`dbgate:plugins/dbgate-plugin-sqlite/src/backend/clients/CloudflareD1Client.js:43`）。这是 EQL 工具必须补齐的空白。

## 9 方言抽象机制（重点）

### 9.1 机制：Driver 对象 + Dialect 对象 + 能力标志

- 每个数据库驱动是一个普通 JS 对象，继承 `driverBase`（`dbgate:packages/tools/src/driverBase.ts:72-267`），核心组成：
  - 身份：`engine`、`title`、`icon`、`defaultPort`；
  - 语言族：`databaseEngineTypes: ['sql'|'document'|'keyvalue'|'rest'|'graphql']`；
  - 能力：`supportsTransactions`、`implicitTransactions`、`readOnlySessions`、`supportsEditableQueryResults`、`supportsIncrementalAnalysis`、`isolationLevels`、`singleConnectionOnly` 等；
  - 行为：`connect/query/stream/readQuery/analyseFull/listDatabases/...`；
  - 方言：`dialect: SqlDialect` 与 `dumperClass`；
  - 编辑器/切分：`editorMode`、`getQuerySplitterOptions(usage)`。
  （类型定义见 `dbgate:packages/types/engines.d.ts:240-383`，`singleConnectionOnly` 在 `:470`。）
- `dialect` 是纯数据 + 少量函数：
  - 分页/限制语法：`limitSelect/rangeSelect/topRecords/offsetFetchRangeSyntax/offsetNotSupported`；
  - 标识符与转义：`quoteIdentifier(s)`、`stringEscapeChar`；
  - 默认 schema、多 schema、类型列表：`defaultSchemaName/multipleSchema/predefinedDataTypes`；
  - 结构变更能力：`createColumn/dropColumn/changeColumn/createIndex/...`；
  - 特殊表达式：`createColumnViewExpression(...)`。
  （`dbgate:packages/types/dialect.d.ts:3-96`，默认值见 `dbgate:packages/tools/src/driverBase.ts:13-30`。）
- 以 PostgreSQL 为例：`rangeSelect/ilike/defaultSchemaName:'public'/multipleSchema` + 一整套 DDL 能力 + `createColumnViewExpression`（空间类型转 `ST_AsText`、uuid 转 `::text`），见 `dbgate:plugins/dbgate-plugin-postgres/src/frontend/drivers.js:13-127`。
- 以 MySQL 为例：`stringEscapeChar:'\\'`、反引号引用、`anonymousPrimaryKey:true`、`columnProperties` 等，见 `dbgate:plugins/dbgate-plugin-mysql/src/frontend/drivers.js:23-120`。
- 能力还会随服务器版本变化：MSSQL 用 `dialectByVersion(version)` 在旧版本关闭 `rangeSelect/offsetFetchRangeSyntax`（`dbgate:plugins/dbgate-plugin-mssql/src/frontend/driver.js:149-158`）。

### 9.2 注册与配置

- 后端：`requireEngineDriver(connection)` 从 `engine` 字符串拆出包名，`requirePlugin(packageName)` 加载 npm 包，再按完整 engine 找 driver（`dbgate:packages/api/src/utility/requireEngineDriver.js:6-22`）。
- 前端：插件 frontend bundle 由 API 以文本返回，浏览器 `eval` 成模块，挂到 `window.DBGATE_PACKAGES`；`buildDrivers` 汇总所有插件导出的 `drivers`（`dbgate:packages/web/src/plugins/PluginsProvider.svelte:10-50`）。
- 插件包通过 package.json 的 `keywords: ["dbgateplugin","dbgatebuiltin"]` 被发现；`plugins` 控制器负责安装/卸载/列出（`dbgate:packages/api/src/controllers/plugins.js:69-140`）。
- 配置面：连接级字段（server/port/user/password/ssl/ssh/readonly/allowedDatabases）由 `showConnectionField` 与 `beforeConnectionSave` 控制；没有「用户可编辑的方言配置」。**方言是代码，不是配置。**

### 9.3 能力差异如何被消费

- SQL 编辑器只在 `driver.databaseEngineTypes.includes('sql')` 时渲染（`dbgate:packages/web/src/tabs/QueryTab.svelte:770`）；非 SQL 用 `editorMode`。
- 事务按钮按 `supportsTransactions/implicitTransactions` 显隐（`dbgate:packages/web/src/tabs/QueryTab.svelte:583-601`）。
- 行数限制按钮在 `singleConnectionOnly` 时隐藏（`dbgate:packages/web/src/tabs/QueryTab.svelte:911`）。
- 结果编辑按 `supportsEditableQueryResults` + engine type 双重判断（`dbgate:packages/api/src/proc/sessionProcess.js:212-216`）。
- 切分器按用途给不同选项：`editor` 模式通常 `ignoreComments + preventSingleLineSplit`，`stream/script` 用原生选项，`import` 可开 `copyFromStdin`（`dbgate:plugins/dbgate-plugin-postgres/src/frontend/drivers.js:137-145`、`dbgate:plugins/dbgate-plugin-mssql/src/frontend/driver.js:177-180`）。
- 前端连接表单、对象树菜单、导入导出也都读同一组能力标志（如 `dbgate:packages/web/src/appobj/DatabaseAppObject.svelte:460-520`）。

### 9.4 局限

1. **不可运行期配置**：新增/微调方言必须改代码、重新打包 npm 插件；无法由部署方用一份 JSON 声明「EQL 方言」。
2. **前后端重复**：同一 `getQuerySplitterOptions/dialect` 在 frontend 与 backend 各写一份（如 `dbgate:plugins/dbgate-plugin-mysql/src/frontend/drivers.js:199` 与 `dbgate:plugins/dbgate-plugin-mysql/src/backend/drivers.js` 各自依赖 splitter），存在漂移风险。
3. **能力标志是布尔散点**：没有统一的 capability schema、版本协商或「不支持时的降级策略」；调用点各自 `if`。
4. **切分器是外部 npm 包**：`dbgate-query-splitter` 不在本仓、未安装，无法确认其错误定位/方言边界实现（见 §14）。
5. **前端 `eval` 插件**：灵活但等于信任 npm 包在浏览器执行任意代码，不适合作为 EQL 的扩展模型。
6. **格式化不接方言**：见 §4.3。

### 9.5 对 EQL「配置化方言」的映射

| dbgate 概念 | EQL 可映射为 | 建议 |
|---|---|---|
| `engine` + `databaseEngineTypes` | `dialectProfileId` + `languageFamily` | 保留，作为 profile 主键 |
| `dialect.*` 布尔/函数 | 数据化 `DialectDescriptor`：标识符引用、字符串转义、分页语法、类型名、函数/关键字、NULL/布尔字面量 | 采用，但默认值放数据文件，行为钩子才用代码 |
| `getQuerySplitterOptions(usage)` | `splitter.editor/execute/script` 三档配置 | 采用「按用途分档」，EQL 编辑器切分与执行切分必须不同 |
| `supportsTransactions/implicitTransactions` | `transactionMode: none / explicit / implicit` | 用枚举替代两个布尔 |
| `readOnlySessions` | `readOnlyMode: session / statement / none` | 明确语义，避免 dbgate 式反直觉命名 |
| `isolationLevels` | 能力列表 + 默认值 | 采用 |
| `editorMode` | `editorSyntax`（eql/sql/json） | 采用 |
| `predefinedDataTypes` | 类型白名单/提示 | 采用，但应从 ORM 元数据生成 |
| `getNewObjectTemplates` | snippet 库 | 采用 |
| `dialectByVersion` | `serverVersionRange → overrides` | 采用「版本覆盖层」 |
| 插件 npm 包 | Nop 模块内的 `*.dialect.json` + SPI | 拒绝 npm/eval 模型，改为服务端注册 + 前端下发 descriptor |

## 10 权限与安全

- 认证：`authProvider` 支持匿名、Basic、OAuth、AD 等；登录后发 JWT（`dbgate:packages/api/src/auth/authProvider.js:11-100`、`dbgate:packages/api/src/auth/authProvider.js:103`）。匿名模式下所有人可登录，权限由 `PERMISSIONS` 环境变量控制（`dbgate:packages/api/src/auth/authProvider.js:39-44`）。
- 权限语言：逗号/分号/空格分隔的字符串，`~` 前缀表示 deny；按出现顺序分层，后者覆盖前者；支持 `*` 通配（`dbgate:packages/tools/src/testPermission.ts:19-81`）。预置角色 `superadmin/logged-user/anonymous-user` 只是权限串模板（`dbgate:packages/tools/src/testPermission.ts:107-140`）。
- 三层校验：
  1. 标准权限：`dbops/query`、`dbops/export`、`all-connections` 等；
  2. 连接权限：`connections/<conid>`；
  3. 数据库角色：`read_content` / `run_script` / `update_only` / `create_update_delete`。
  （`dbgate:packages/api/src/utility/hasPermission.js:17-60,326-350`）
- 执行入口全部在服务端校验：`sessions/create` 校验连接权限，`executeQuery` 校验 `dbops/query` + `run_script`，`executeReader` 对 SQL 只需 `read_content`、对文档库脚本需 `run_script`（`dbgate:packages/api/src/controllers/sessions.js:242-272,356-383`）。
- 结构接口在多用户存储模式下会按表级权限过滤对象（deny 的不返回），避免前端看到无权对象（`dbgate:packages/api/src/controllers/databaseConnections.js:795-830`）。
- 审计：每次结构读取、查询执行都写 `sendToAuditLog`，包含 `conid/database/action/severity/detail`（`dbgate:packages/api/src/controllers/sessions.js:274-286`、`dbgate:packages/api/src/controllers/databaseConnections.js:783-793`）。
- 路径安全：导出路径必须落在托管目录，且用 realpath + 目录 fd 校验防符号链接逃逸（`dbgate:packages/api/src/utility/security.js:66-260`）；结果集 `jslid` 也统一在 `getJslFileName` 里做目录约束（`dbgate:packages/api/src/utility/getJslFileName.js:1-337`）。
- SSE 安全：`/stream` 本身不鉴权，但事件按 `strmid` owner 投递，避免串流；这是「通知面不泄露数据」的设计（`dbgate:packages/api/src/utility/socket.js:4-12,140`）。
- 只读连接：服务端 `allowExecuteCustomScript` 兜底，前端 `hasConnection()` 也据此隐藏执行入口（`dbgate:packages/web/src/tabs/QueryTab.svelte:343-346`）。
- 风险点：前端插件 `eval`、npm 插件供应链、`DBGM` 错误信息可能含 SQL 片段（审计日志 `detail: sql` 本身是设计选择）。

## 11 测试与工程质量

| 维度 | 事实 | 证据 |
|---|---|---|
| 单元测试 | 31 个 `*.test.js/ts`：api 20 个、tools 4 个、datalib 3 个、filterparser 1 个、rest 3 个 | 本次 `find` 计数 |
| 权限回归 | `sessions.test.js` 覆盖连接权限、`dbops/query`、`run_script`、team-file token 伪造/重放/解绑、front matter 隐藏 SQL、SSE owner 隔离 | `dbgate:packages/api/src/controllers/sessions.test.js:54-370` |
| 集成测试 | `integration-tests/__tests__` 13 个 spec，Docker Compose 起多数据库，Jest 直连驱动测试 query/DDL/import-export | `dbgate:integration-tests/__tests__/query.spec.js:1-60`、`dbgate:integration-tests/package.json:7-13` |
| E2E | 11 个 Cypress spec，`data-testid` 约定 `ComponentName_identifier`；多数据库矩阵（MySQL/PG/MSSQL/Oracle/SQLite/Mongo） | `dbgate:e2e-tests/cypress/e2e/multi-sql.cy.js:37-65`、`dbgate:CLAUDE.md:109` |
| CI | `run-tests.yaml` 先 checkout `dbgate-pro`、用 `diflow fsmerge` 合并，再跑 integration + filterparser + datalib + tools + api 测试 | `dbgate:.github/workflows/run-tests.yaml:24-74` |
| 类型/格式 | `yarn ts`（api+web）、`svelte-check`、prettier（单引号/2 空格/120 列） | `dbgate:CLAUDE.md:36-37,107-110`、`dbgate:packages/web/package.json:8` |
| 生成物治理 | `storageModel.js` 由 YAML 生成，禁止手改；`_` 前缀文件视为生成物 | `dbgate:AGENTS.md:9`、`dbgate:package.json:53` |
| i18n | `translations/{en,zh,de,...}.json`，`_t/__t` 包装；CLI 可 extract/check | `dbgate:packages/web/src/translations.ts:96-129`、`dbgate:package.json:78-82` |
| 日志 | pino（`pinomin`），结构化字段 + `DBGM` 码 | `dbgate:CLAUDE.md:111`、`dbgate:packages/api/src/utility/handleQueryStream.js:9` |

质量评价：**安全与权限的测试密度明显高于 UI**，尤其 sessions 的 token 边界测试值得学习；但 UI 侧主要靠 E2E，缺少组件级测试；方言矩阵靠 integration 覆盖主流 6 种数据库，未覆盖全部 17 种。

## 12 对 Nop EQL 在线编辑器的启示

### 12.1 Adopt（直接采用其架构决策）

| # | 做法 | dbgate 证据 | 对 EQL 的落地 |
|---|---|---|---|
| A1 | 每个编辑标签页一个独立数据库会话（连接/事务/临时状态隔离） | `dbgate:packages/api/src/controllers/sessions.js:182-236` | 后端 `EqlConsoleSession` 注册表：`tabId → {ormSession, tx, lastAccess}`；禁止跨 tab 共享事务 |
| A2 | 结果集物化为 append-only 数据集 + stats 旁文件 + 分页随机读 | `dbgate:packages/api/src/utility/handleQueryStream.js:14-145`、`dbgate:packages/api/src/controllers/jsldata.js:71-152` | 用 JDBC `ResultSet` 流式写入受控临时存储（内存 ring + 临时文件），前端按 `{resultId, offset, limit}` 取数 |
| A3 | 事件面与数据面分离（SSE 通知，HTTP 取数） | `dbgate:packages/web/src/utility/api.ts:83-84`、`dbgate:packages/api/src/controllers/jsldata.js:91-139` | EQL 执行返回 `queryId`，SSE 推 `result-set/columns/stats/done`，REST 拉页 |
| A4 | 元数据 loader 声明式化 + reloadTrigger 细粒度失效 | `dbgate:packages/web/src/utility/metadataLoaders.ts:16-42,246-263`、`dbgate:packages/web/src/utility/cache.ts:168` | 实体树按 `querySpace` 建 loader；模型变更发 `entity-metadata-changed` |
| A5 | 驱动能力标志 + 按用途切分（editor/stream/script） | `dbgate:packages/types/engines.d.ts:240-383`、`dbgate:plugins/dbgate-plugin-postgres/src/frontend/drivers.js:137-145` | `DialectDescriptor.splitter.{editor,execute,script}`；EQL 编辑器按 `;` 与空行切，执行按编译单元切 |
| A6 | 脚本 front matter 承载连接/执行选项 | `dbgate:packages/tools/src/stringTools.ts:891-918` | EQL 脚本头 `-- >>> datasource: ..., maxRows: ..., timeoutMs: ... -- <<<`；解析纳入权限/校验 |
| A7 | 错误行号相对语句起点，前端可跳转 | `dbgate:packages/api/src/utility/handleQueryStream.js:354-362` | 编译/执行错误统一 `{line,column,offset,code,severity}`，前端 CodeMirror lint 标注 |
| A8 | 查询历史 append-only + 反向读 | `dbgate:packages/api/src/controllers/queryHistory.js:46-67` | 历史表按 `user/querySpace/time` 索引，分页读，不加载全量 |
| A9 | 服务端强制权限 + 审计 + 路径约束 | `dbgate:packages/api/src/controllers/sessions.js:242-286`、`dbgate:packages/api/src/utility/security.js:66-260` | EQL 控制台 API 入口统一 `@Auth` + 数据权限 + 审计；结果文件路径由服务端生成 |
| A10 | 编辑器/E2E 的 `data-testid` 命名与多引擎矩阵测试 | `dbgate:CLAUDE.md:109`、`dbgate:e2e-tests/cypress/e2e/multi-sql.cy.js:37-65` | EQL 用 Playwright，按 H2/MySQL/PG 矩阵跑同一套工作台用例 |

### 12.2 Adapt（借鉴思路但必须改造）

| # | dbgate 做法 | 为什么不能照搬 | EQL 改造 |
|---|---|---|---|
| B1 | 结果落 JSONL 到服务器磁盘 | 多租户/云部署的配额、加密、清理、跨实例共享都成问题 | 可插拔 `ResultStore`：小结果内存、大结果临时文件（TTL+配额）、可选对象存储 |
| B2 | 取消 = `subprocess.kill()` | 会丢失会话事务/临时状态，用户体验差 | `Statement.cancel()` + 查询级 timeout；会话进程保留，必要时重建连接 |
| B3 | 行数超限直接 `process.exit(0)` | 同样杀会话，且无法返回部分结果的元信息 | 达到上限时停止拉取、标记 `truncated=true`、保留结果集，不杀会话 |
| B4 | 方言 = 前端/后端各一份 JS 代码 | 无法配置化、易漂移 | 单一后端 `DialectProfile` 数据源，构建期生成前端 descriptor（JSON），禁止前端手写第二份 |
| B5 | 整库 `structure` 快照 | EQL 模型可能很大，且实体树应按 querySpace 懒加载 | `entity/list?querySpace=&cursor=` 增量返回；结构快照加 `version/etag` |
| B6 | 补全用正则分词解析 SQL | EQL 有正式 parser，正则无法处理嵌套/CTE/集合算子 | 用 EQL AST/编译器的错误恢复信息做补全上下文；至少复用 parser 的 token 流 |
| B7 | 格式化与方言无关 | EQL 需要保留实体名大小写、属性路径、`_some` 算子 | `DialectProfile.formatter` 配置关键字/换行/引用规则；无法配置时降级为「只做缩进」 |
| B8 | 前端 `eval` npm 插件 | 浏览器执行任意第三方代码，供应链风险高 | 方言 descriptor 由后端下发；前端不允许动态 eval |
| B9 | Ace 编辑器 | Nop 前端已有 CodeMirror 6 与 SQL 扩展 | 复用 flux-code-editor 的 CodeMirror 6，不引入 Ace |
| B10 | 历史只存本地 JSONL | 多用户/多实例不可共享 | 存 DB 表，按用户与数据源隔离，支持保留策略 |

### 12.3 Reject（明确不采用）

| # | 做法 | 理由 |
|---|---|---|
| C1 | 直接复制 dbgate 源码/驱动实现 | GPL-3.0（`dbgate:README.md:15`），与 Nop 的许可/分发模型存在冲突风险；只可学习架构并 clean-room 实现 |
| C2 | 「方言即 npm 插件 + 代码对象」 | 不满足 EQL「尽量配置化」；且引入 npm 供应链与版本矩阵负担 |
| C3 | 把核心能力放在 Pro 开关后（结果编辑、图表） | dbgate 用 `isProApp()` 门控（`dbgate:packages/web/src/query/ResultTabs.svelte:56`）；EQL 工具的核心能力应默认可用，而非商业分层 |
| C4 | 无 EXPLAIN/执行计划 | dbgate 缺失（§8）；EQL 的卖点恰是「可解释的 ORM 查询」，必须提供 EXPLAIN/编译 SQL 预览 |
| C5 | 无查询级超时、无优雅取消 | 见 §6.3；生产工具不可接受 |
| C6 | 全库结构一次性加载后再渲染树 | 大模型/多 querySpace 下首屏不可接受 |
| C7 | 用「注释里的 front matter」承载安全敏感配置却不做安全解析 | dbgate 需要专门 blank 掉 front matter 才能安全执行（`dbgate:packages/api/src/controllers/sessions.js:27-33`）；EQL 应把脚本配置放独立元数据字段或严格解析白名单 |

### 12.4 与 EQL 现状的对照

- EQL 已有正式编译入口 `EqlCompiler.compile(name, eql, context)`（`nop-entropy:nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:56`），且编译期拒绝多语句与事务语句（`:58-72`），这比 dbgate 的「切分后逐条丢给数据库」更适合做**校验/EXPLAIN 先于执行**。
- 任意 EQL 文本执行已有通路：`IOrmSession.executeQuery(SQL, LongRangeBean, Function<IDataSet,T>)`（`nop-entropy:nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSession.java:245`），天然支持分页与结果集元数据；列来源可由 ORM 模型直接提供，不必复用 dbgate 的 SQL 反解启发式。
- 方言能力：EQL 语法的 DML 集合与事务拒绝规则见 `nop-entropy:nop-persistence/nop-orm-eql/model/antlr/Eql.g4:29-54`；因此 EQL 控制台的「方言配置」重点不是 DDL 差异，而是：标识符引用/大小写、分页语法、函数与运算符白名单、类型映射、错误文案。
- 前端现状与差距（SQL 方言硬编码 5 种、补全仅一层 `alias.col`、无后端校验 action、结果面板无分页）已在同目录前序报告 `2026-10-18-flux-sql-editor-current-state-and-eql-gap.md` 中逐条给出证据；本报告不重复。

## 13 关键证据清单

| # | 证据（path:line） | 支撑结论 |
|---|---|---|
| 1 | `dbgate:package.json:4-9` | Yarn workspaces 单仓多包 |
| 2 | `dbgate:README.md:23-41` | 支持 17 种数据库，Premium 分层 |
| 3 | `dbgate:README.md:15` + `dbgate:LICENSE-OLD:1-10` | GPL-3.0，原 MIT 代码声明 |
| 4 | `dbgate:CLAUDE.md:83` | 数据库连接在子进程，隔离崩溃与长任务 |
| 5 | `dbgate:packages/api/src/controllers/sessions.js:182-236` | 每会话 fork 进程 + 权限校验 + SSE owner |
| 6 | `dbgate:packages/web/src/utility/api.ts:83-84` | 前端 EventSource `/stream?strmid=` |
| 7 | `dbgate:packages/api/src/utility/useController.js:24-91` | `_meta` 驱动 REST 注册与统一错误返回 |
| 8 | `dbgate:packages/web/src/query/SqlEditor.svelte:2-6,29` | 编辑器仅 3 个硬编码 engine→Ace mode 映射 |
| 9 | `dbgate:packages/web/src/query/codeCompletion.ts:82-204` | 异步补全：关键字/schema/表/列，单层别名 |
| 10 | `dbgate:packages/web/src/query/analyseQuerySources.ts:45-110` | FROM/JOIN 启发式 source 解析，非 AST |
| 11 | `dbgate:packages/web/src/tabs/QueryTab.svelte:557-561` | 格式化不接方言 |
| 12 | `dbgate:packages/web/src/query/QueryParserWorker.js:1-8` | 多语句切分在 Web Worker |
| 13 | `dbgate:packages/web/src/tabs/QueryTab.svelte:401-475` | 参数解析→建会话→执行→写历史 |
| 14 | `dbgate:packages/api/src/proc/sessionProcess.js:157-210` | 服务端再次切分并逐条 stream |
| 15 | `dbgate:packages/api/src/utility/handleQueryStream.js:23-103` | 结果集 JSONL 头行 + stats 旁文件 |
| 16 | `dbgate:packages/api/src/utility/handleQueryStream.js:280-310` | 行数超限杀进程 |
| 17 | `dbgate:packages/api/src/controllers/jsldata.js:71-152` | 分页取数、NDJSON 流式导出、stats |
| 18 | `dbgate:packages/api/src/utility/JsonLinesDatastore.js:170-254` | 结果集服务端过滤/外部排序/分页 |
| 19 | `dbgate:packages/api/src/utility/DatastoreCache.js:2-80` | 结果集 LRU + 空闲 5 分钟回收 |
| 20 | `dbgate:packages/types/engines.d.ts:240-383,470` | Driver 能力标志全集 |
| 21 | `dbgate:packages/types/dialect.d.ts:3-96` | Dialect 数据面：分页/引用/类型/DDL 能力 |
| 22 | `dbgate:plugins/dbgate-plugin-postgres/src/frontend/drivers.js:137-146` | 按 usage 返回不同 splitter options；readOnlySessions |
| 23 | `dbgate:plugins/dbgate-plugin-mssql/src/frontend/driver.js:149-158` | 按服务器版本覆盖 dialect |
| 24 | `dbgate:packages/web/src/utility/metadataLoaders.ts:16-42,246-263` | loader + reloadTrigger + 缓存键 |
| 25 | `dbgate:packages/api/src/controllers/databaseConnections.js:119-135,766-847` | 结构快照 + SSE 失效事件 |
| 26 | `dbgate:packages/api/src/controllers/sessions.js:242-286` | 查询前 `dbops/query` + `run_script` + 审计 |
| 27 | `dbgate:packages/api/src/utility/security.js:66-260` | 导出路径 realpath/目录 fd 安全 |
| 28 | `dbgate:packages/api/src/controllers/queryHistory.js:46-67` | 历史 JSONL 追加 + 反向分页读 |
| 29 | `dbgate:packages/web/src/query/MessageViewRow.svelte:54-61` | 「Explain」是 AI 解释错误，不是执行计划 |
| 30 | `dbgate:.github/workflows/run-tests.yaml:24-74` + `dbgate:integration-tests/__tests__` | 合并 Pro 后跑多库集成测试 |
| 31 | `nop-entropy:nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:56-72` | EQL 编译入口，拒绝多语句/事务语句 |
| 32 | `nop-entropy:nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSession.java:245` | 任意 EQL + 分页 + 结果集回调 |
| 33 | `nop-entropy:nop-persistence/nop-orm-eql/model/antlr/Eql.g4:29-54` | EQL 语法含 DML 与事务语句节点（编译器拒绝事务） |

## 14 Open Questions

1. **切分器行为未确认**：`dbgate-query-splitter` 未安装、不在本仓，无法确认其错误定位、`GO`/`DELIMITER`/`$$` 边界与方言差异的实现质量；EQL 若自研切分器，需要独立的边界用例集。
2. **结果集过滤语义**：`evaluateCondition`（来自 `dbgate-sqltree`）对 JSONL 行的类型比较、NULL、大小写规则未验证；EQL 结果过滤应复用 EQL 表达式语义还是定义独立过滤 DSL，需要决策。
3. **会话数上限**：未发现对 `opened` 会话/连接进程数量的硬限制或配额；EQL 多用户场景必须加池化与上限。
4. **社区版结果编辑是否死代码**：`supportsEditableQueryResults` 与 `enrichQueryResultColumns` 在 Community 后端存在，但 UI 由 `isProApp()` 关闭（`ResultTabs.svelte:56`）；需确认是否有非 UI 调用路径。
5. **多用户模式下结构权限**：表级过滤只在 `process.env.STORAGE_DATABASE` 时生效（`databaseConnections.js:795`），单机/匿名部署下无表级隔离；EQL 的权限模型必须以 Nop 的 `nop-auth` 为准，不沿用该条件分支。
6. **`readOnlySessions` 语义**：名为 readOnly，实为「驱动可创建只读会话因而允许执行脚本」；EQL 需用更明确的枚举，避免同类误读。
7. **前端插件 `eval` 的 CSP 兼容性**：`eval` 在严格 CSP 下不可用；dbgate 通过 API 返回脚本文本规避构建期打包，但 EQL 若走内容安全策略需改为静态模块注册。
8. **`limitRows` 杀进程的副作用**：`process.exit(0)` 会丢失未提交事务且前端只看到 error info；EQL 应改为「截断 + 明确标记」，需确认前端协议改动成本。
9. **EXPLAIN 形态未定**：EQL 可直接输出编译后的 SQL（`ICompiledSql`）与执行计划；是展示「编译 SQL」还是「数据库 EXPLAIN」，或两者并列，需要产品决策。
10. **方言 descriptor 的边界**：哪些能力必须代码钩子（如 `quoteIdentifier` 的边界字符、`LIMIT` 语法变体），哪些可纯数据；建议先做 3 个方言（MySQL/PG/H2）的 spike 再定 schema。
