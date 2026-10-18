# Hue 深度调研报告

> Status: resolved
> Date: 2026-10-18
> Scope: Hue 源码 / Web SQL 编辑器 / 多引擎方言适配 / 权限与审计
> Conclusion: 对 EQL 在线编辑器的最大可借鉴点，是 Hue 以 `SqlExecutable` 为中心的「语句级执行状态机」与「客户端语句定位 + 选中执行」：它把多语句、轮询、取消、结果分页、每语句状态收敛为一个可复用内核；方言差异则被拆成「后端 connector 类 + 前端 dialect parser + 部分声明式属性」三层，而不是一套完整 DSL。

> 证据约定：本文所有 `path:line` 均相对于源码仓库 `/Users/abc/sources/sql-editors/hue`，行号来自实际 `read`/`grep`。无法从源码确认的内容显式标注「未确认」。

## 1. 背景与调研问题

- Nop 平台准备为 EQL 做「在线编辑 + 测试」工具，形态接近在线版 DBeaver：数据源/实体树、补全、执行、结果表格、历史、导出、EXPLAIN/校验、多标签。
- EQL 是「标准 SQL + 实体属性导航自动 JOIN」，与 Hive/Impala 的方言差异不同：它既有 SQL 方言面，又有 ORM 元模型面（实体、属性、关联、枚举、权限）。
- Hue 是 Cloudera 维护的成熟 Web SQL 工作台，长于「多引擎适配 + 编辑器 + 结果/历史/保存查询」，适合作为产品形态与执行链路的参照，而不适合作为 Nop 的技术栈参照。
- 本次要回答的问题：
  - Q1 Hue 的前后端边界如何切分？Notebook/查询/结果/历史各自如何建模？
  - Q2 SQL 编辑器如何做补全、多语句、选中执行、变量替换？
  - Q3 元数据树从哪里来、如何缓存、如何与执行引擎解耦？
  - Q4 查询执行如何提交、轮询、取消、取日志、取结果？结果是否落库？
  - Q5 多引擎/方言差异被抽象在哪几层？哪些是声明式的，哪些仍是硬编码？
  - Q6 权限、分享、审计覆盖到什么粒度？
  - Q7 对 Nop EQL 工具，哪些应 adopt / adapt / reject？

## 2. 仓库与产品概览

### 2.1 基本事实

- 仓库：`cloudera/hue`，本次 HEAD 为 `65d9e48`（2026-10-09），产品定位是 “SQL Assistant for Databases and Data Warehouses”（`README.md:19`）。
- License：Apache License 2.0（`LICENSE.txt:2-3`，`package.json:27`）。
- 版本：`package.json` 为 `6.0.1`（`package.json:3`），但仓库根 `VERSION` 仍写 `4.11.0`（`VERSION:20`），两者不一致，属于历史遗留/发版口径问题（未确认哪一个对外生效）。
- 后端：Django 4.2.30 + Django REST Framework 3.15.2 + Channels 4.2.2 + channels-redis 4.2.1（`desktop/core/generate_requirements.py:55-61`、`:75`）。
- 前端：React 18.2.0（`package.json:72`）、Ant Design 4.24.5（`package.json:37`）、Knockout 3.5.1（`package.json:59`）、Vue 3.5.43（`package.json:85`）、TypeScript 5.5.3（`package.json:160`）、Webpack 5.94.0（`package.json:164`）。
- 编辑器内核：仓库内自带 Ace 1.2.2（`desktop/core/src/desktop/js/ext/ace/ace.js:18677`），不是 npm 依赖。
- 产品能力：交互式 SQL 编辑、语法高亮与补全、Table Browser、多数据库连接器（`README.md:30-35`）。
- 部署形态：官方 Docker 镜像基于 Ubuntu 22.04 + Python 3.11（`tools/docker/hue/Dockerfile:3`、`:9`），安装后由 supervisor 启动（`tools/docker/hue/startup.sh:4`）；同时提供 ASGI 入口以支持 Channels（`desktop/core/src/desktop/asgi.py:7-9`）。

### 2.2 查询引擎版图

- `CONNECTOR_TYPES` 文件内共 43 个 `dialect` 条目，其中 33 个是 `category=editor` 的查询连接器，覆盖 Hive、Impala、Druid、ksqlDB、Flink SQL、SparkSQL、Phoenix、MySQL、PostgreSQL、Trino、Presto、DaskSQL、Elasticsearch SQL、Calcite、Athena、Redshift、Snowflake、BigQuery、Oracle、ClickHouse、Solr SQL、JDBC 通用库等（`desktop/core/src/desktop/lib/connectors/types.py:28`、`:33-767`）。
- 其余 10 个是 browsers（HDFS/YARN/S3/ADLS）、catalogs（HMS/Atlas/Navigator）、optimizers（Optimizer）、schedulers（Oozie/Celery），与 SQL 执行连接器分开声明（`desktop/core/src/desktop/lib/connectors/types.py:823-863`）。
- 关键判断：Hue 的「多引擎」不是靠一个统一 SQL 方言层，而是靠「每个 engine 一个 `Api` 子类 + 每个 dialect 一套 Jison 语法 + 一组能力属性」拼出来的。

### 2.3 技术栈判断

- 这是一个典型的「十年演进型」前端：Knockout 承载旧 Editor/Notebook，React 承载部分组件，Vue 3 承载 Editor v2 的新组件（`desktop/core/src/desktop/js/apps/editor/components/aceEditor/AceEditor.vue:78`）。
- 新 Editor 仍是「Vue 组件 + Knockout ViewModel + jQuery 插件」混跑：例如 `snippet.js` 仍用 Knockout observable 包装执行器（`desktop/core/src/desktop/js/apps/editor/snippet.js:37-47`），而 AceEditor/ResultTable 已是 Vue SFC（`AceEditor.vue:78`、`ResultTable.vue:71`）。
- 因此 Hue 的前端工程经验可借鉴「组件边界与执行内核」，但不应照搬其框架组合与构建体系。

## 3. 架构总览

### 3.1 前后端边界

- 后端是 Django 单体：`desktop/core` 提供通用框架，`desktop/libs/notebook` 提供编辑器/查询领域逻辑，`apps/*` 提供具体应用（beeswax、hive、impala、rdbms 等）。
- 查询领域核心在 `desktop/libs/notebook/src/notebook/`：`api.py`（HTTP 入口）、`models.py`（Notebook/Document 包装）、`connectors/`（各引擎实现）、`sql_utils.py`（语句切分）、`consumer.py`（WebSocket）。
- 前端 Editor v2 的 API 常量集中在 `apps/editor/execution/api.ts`：`/api/v1/editor/execute/<dialect>`、`check_status`、`fetch_result_data`、`cancel_statement`、`get_logs`（`desktop/core/src/desktop/js/apps/editor/execution/api.ts:160-168`）。
- `/api/v1/editor/*` 由 DRF 风格的公开 API 包装（`desktop/core/src/desktop/api_public_urls_v1.py:48-59`），底层复用 `notebook/api.py` 的私有实现（`desktop/core/src/desktop/api_public.py:22-23`、`:31`）。

### 3.2 Notebook 数据模型

- 一个「查询」在 Hue 里不是独立表，而是一个 `Document2`，其 `data` 字段存 JSON；`snippets` 是标签页数组，每个 snippet 内含 `executor.executables`（`docs/designs/editor3.md:27`、`desktop/core/src/desktop/js/apps/editor/notebook.js:61-62`）。
- 每个 snippet 的标准字段包括 `status`、`id`、`statement_raw`、`statement`、`type`、`properties`、`database`、`result`、`variables`、`lastExecuted`（`desktop/libs/notebook/src/notebook/connectors/base.py:262-276`）。
- Editor v2 把「一条 SQL 语句」提升为 `SqlExecutable`：持有 `parsedStatement`、`handle`、`status`、`logs`、`result`、`previousExecutable/nextExecutable`（`desktop/core/src/desktop/js/apps/editor/execution/sqlExecutable.ts:113-130`）。
- `Executor` 负责维护 `executables` 列表，并在编辑内容变化时把旧的可执行对象标记为 lost、取消其 batch chain（`desktop/core/src/desktop/js/apps/editor/execution/executor.ts:59-101`）。

### 3.3 API 与 WebSocket

- 主链路是普通 HTTP POST，不是 WebSocket：提交执行、轮询状态、拉结果、拉日志、取消，全部是独立 REST API（`desktop/core/src/desktop/api_public_urls_v1.py:51-58`）。
- WebSocket 仅用于「结果流式推送」这一个可选增强：路由为 `ws/editor/results/<query_uuid>/`（`desktop/libs/notebook/src/notebook/routing.py:27-29`），consumer 只转发 `query_progress`/`query_result`（`desktop/libs/notebook/src/notebook/consumer.py:29-63`）。
- WebSocket 默认关闭：`WEBSOCKETS.ENABLED` 默认 `False`（`desktop/core/src/desktop/conf.py:2277-2286`），前端也据此决定是否走 WS（`desktop/core/src/desktop/js/apps/editor/execution/sqlExecutable.ts:364-372`）。
- 结论：Hue 的可借鉴点是「HTTP 轮询为主、WS 为可选增量」，而不是把执行链路设计成强依赖 WebSocket。

### 3.4 分层评价

- 领域层（notebook/connectors）与传输层（Django view / DRF wrapper）基本分离；但 connector 层同时承担「连接管理 + 执行 + 元数据 + 导出 + explain」，职责偏重。
- 前端执行内核（Executor/SqlExecutable/ExecutionResult）边界清晰，可直接作为 EQL 工具的设计参照。
- 旧 Editor（`apps/notebook/snippet.js`，2881 行）与新 Editor（`apps/editor/*`）并行存在，导致同一能力有两套实现，是主要技术债。

## 4. SQL 编辑器实现

### 4.1 编辑器内核

- Editor v2 使用 Ace，挂载点 `ace.edit(element)`（`desktop/core/src/desktop/js/apps/editor/components/aceEditor/AceEditor.vue:429`）。
- 默认开启基础补全与实时补全，可在用户本地存储中开关；默认 2 空格、软 Tab、显示行号、关闭打印边距（`AceEditor.vue:153-179`）。
- 快捷键包括 `Ctrl/Cmd-Enter` 执行当前语句、`Ctrl-S` 保存、`Ctrl-E` 新建、`Ctrl-Shift-P` 演示模式、`Ctrl-I/Ctrl-Shift-F` 格式化（`AceEditor.vue:253-340`）。
- 编辑器把「当前活动语句」作为一等公民：`AceLocationHandler` 监听光标/选区，计算 `activeStatement`、`precedingStatements`、`followingStatements`，并发布 `ACTIVE_STATEMENT_CHANGED_EVENT`（`desktop/core/src/desktop/js/apps/editor/components/aceEditor/AceLocationHandler.ts:632-737`）。
- 语法检查在 Worker 中异步执行，错误以 Ace marker 标注，并支持 “Did you mean …?” 提示（`AceLocationHandler.ts:334-365`、`:447-461`）。

### 4.2 补全来源

Hue 的补全不是单一路径，而是四类来源叠加：

1. **客户端 SQL 解析器**：按 dialect 懒加载 Jison 生成的 autocomplete parser，解析当前语句的语法上下文（`desktop/core/src/desktop/js/parse/sql/sqlParserRepository.ts:19-41`、`desktop/core/src/desktop/js/sql/sqlAutocompleter.js:78-82`）。
2. **语法关键字/函数/UDF**：由 parser 的 `suggestKeywords`、`suggestFunctions`、`suggestIdentifiers` 等字段驱动（`desktop/core/src/desktop/js/sql/autocompleteResults.js:500-526`）。
3. **元数据（库/表/列）**：由 `AutocompleteResults` 调用 `DataCatalogEntry` 逐层拉取，`loadDatabases`、`handleColumnReference` 等并行发起（`autocompleteResults.js:445-470`）。
4. **SQL 分析/优化器增强**：热门表、热门列、过滤/分组/关联列等来自 SQL analyzer（`autocompleteResults.js:450-500`；`dataCatalog.ts:434-500`）。

- 补全请求最终落到 `/api/v1/editor/autocomplete/...`、`/describe/...`、`/sample/...`（`desktop/core/src/desktop/js/catalog/api.ts:63-76`），后端再按 connector 转发到具体引擎（`desktop/libs/notebook/src/notebook/api.py:739-762`）。
- 关键设计：补全的「语法上下文」在浏览器算，「对象是否存在」在服务端算；两者通过 `parseResult` + `DataCatalog` 解耦。

### 4.3 多语句与选中执行

- 语句切分有两套：前端用 `sqlStatementsParser`（Jison）切分（`desktop/core/src/desktop/js/parse/utils.ts:19-27`），后端用 `split_statements` 按分号/引号/注释切分（`desktop/libs/notebook/src/notebook/sql_utils.py:94-160`）。
- 选区非空时，`AceLocationHandler` 把选区覆盖到的语句全部放入 `selectedStatements`；选区为空时只执行光标所在语句（`AceLocationHandler.ts:667-722`）。
- `Executor.update` 把 `selectedStatements` 同步为 `SqlExecutable` 列表，并为它们建立 `previousExecutable/nextExecutable` 链（`desktop/core/src/desktop/js/apps/editor/execution/executor.ts:71-99`、`desktop/core/src/desktop/js/apps/editor/execution/utils.ts:29-98`）。
- 批量执行是「链式串行」：当前语句进入 `available` 后，自动触发 `nextExecutable.execute()`（`sqlExecutable.ts:341-353`）。
- 每条语句在编辑器 gutter 上有独立状态标记（active/completed/executing/failed），失败时把第一条错误挂到对应行（`desktop/core/src/desktop/js/apps/editor/components/aceEditor/AceGutterHandler.ts:30-34`、`:75-105`）。

### 4.4 变量替换

- 变量语法为 `${name}`（也兼容旧 `$name`），变量定义包含 `name`、`type`、`value`、`meta.placeholder`、`sample` 等（`desktop/core/src/desktop/js/apps/editor/components/variableSubstitution/types.ts:21-39`）。
- 执行前在客户端做替换：`SqlExecutable.getStatement()` 调用 `substituteVariables`，把 `${var}` 替换为变量值或占位符（`sqlExecutable.ts:483-485`、`:92-110`）。
- 变量类型支持 text/select/date/datetime-local/number/checkbox（`variableSubstitution/types.ts:30`），变量候选项可来自元数据 sample（`variableSubstitution/types.ts:38-39`）。
- 设计要点：变量替换发生在「发送给后端之前」，后端只看到最终 SQL；这与 Hue 旧版 `Notebook.statement_with_variables` 的服务端替换并存（`desktop/libs/notebook/src/notebook/connectors/base.py:127-160`）。

### 4.5 格式化、预测与语法辅助

- 格式化走 `/notebook/api/format`，后端直接用 `sqlparse.format(..., reindent=True, keyword_case='upper')`（`desktop/libs/notebook/src/notebook/api.py:798-806`）。
- 支持选中文本格式化，也支持整段格式化（`AceEditor.vue:304-330`）。
- 支持基于 SQL analyzer 的「行内预测」typeahead（`acePredict.ts:35-78`），属于可选增强，不是核心链路。

## 5. 元数据与数据源管理

### 5.1 统一目录树抽象

- 前端用 `DataCatalogEntry` 表示任意层级节点：connector → namespace/compute → database → table → column（`desktop/core/src/desktop/js/catalog/DataCatalogEntry.ts:588` 起）。
- 子节点按需加载：`getChildren()` 逐层展开，不在初始化时拉全库（`DataCatalogEntry.ts:588`、`dataCatalog.ts:397-445`）。
- 目录树支持 `getComment`、`getPartitions`、`getSample`、`getAnalysis` 等能力，按节点类型渐进增强（`DataCatalogEntry.ts:1040-1062`、`:1604`、`:1661`）。

### 5.2 元数据 API

- 后端元数据接口与执行接口共用 connector：`/api/v1/editor/autocomplete/`、`/describe/`、`/sample/` 都由 `get_api(request, snippet)` 选择具体 `Api` 实现（`desktop/libs/notebook/src/notebook/api.py:739-783`、`:1027-1045`）。
- 元数据默认实现有两层：`Api.describe_table` 的兜底逻辑复用 `autocomplete` 返回的 `extended_columns`（`desktop/libs/notebook/src/notebook/connectors/base.py:675-680`）；HS2 则覆写为直接读取表元数据（列、分区键、主键、属性、统计）（`desktop/libs/notebook/src/notebook/connectors/hiveserver2.py:939-955`）。`get_sample_data` 走 `_get_sample_data`（`hiveserver2.py:572-575`）。
- Catalog 类连接器（HMS/Atlas/Navigator）通过 `/metadata/api/catalog/*` 提供搜索、标签、实体查询（`desktop/libs/metadata/src/metadata/urls.py:34-44`），但 SQL 编辑器的默认树仍以「当前 connector 的库/表」为主。

### 5.3 元数据缓存

- 前端用 `localforage` 建浏览器侧缓存，按 namespace + compute + path 生成 cache key（`dataCatalog.ts:143-176`、`:277-280`）。
- 缓存有 TTL，默认来自 `window.CACHEABLE_TTL`；过期后重新拉取（`dataCatalog.ts:170-176`、`:357-368`）。
- 缓存的粒度是「目录项元数据」，不缓存结果行；执行结果的缓存/生命周期由引擎侧 handle 决定。

### 5.4 评价

- 优点：`DataCatalogEntry` 把「元数据源差异」收敛为树节点接口，UI 只依赖 `getChildren`，适合 EQL 的「数据源 → 实体 → 属性」树。
- 局限：Hue 没有独立的元数据服务，元数据质量取决于底层引擎/HMS；没有 EQL 那种「实体属性 + 关联路径」的语义层。
- 对 EQL 的启示：树节点应显式区分 `DataSource / Entity / Property / Relation`，并把「导航路径」作为节点身份的一部分，而不是只存 `database.table.column`。

## 6. 查询执行链路

### 6.1 提交

- 入口：`POST /api/v1/editor/execute/<dialect>`（`api.ts:165`、`:220`）。
- 前端把 `executable.toJson()` 作为 `executable` 字段，连同 notebook/snippet 上下文一起 POST（`apps/editor/execution/api.ts:220-230`）。
- 后端 `_execute_notebook` 解析 notebook/snippet，选择 `get_api(request, snippet)`，调用 `interpreter.execute(notebook, snippet)`（`desktop/libs/notebook/src/notebook/api.py:123-160`）。
- 返回 `handle`：至少包含 `guid`、`secret`、`session_id`、`session_type`、`has_result_set`、`statement_id`、`statements_count`（`desktop/libs/notebook/src/notebook/connectors/hiveserver2.py:318-359`）。
- 同步型 connector 可在 handle 中直接带 `result`，后端会把它 pop 出来直接返回，不写入历史（`notebook/api.py:153-157`、`:201-204`）。

### 6.2 轮询

- 前端 `SqlExecutable.checkStatus()` 先立即查一次，之后 `setTimeout` 递归：前 45 次每 1s，之后每 5s（`desktop/core/src/desktop/js/apps/editor/execution/sqlExecutable.ts:311-382`）。
- 状态机：`ready → running/starting/waiting → available | success | failed | canceled | expired`，`available` 表示结果可取（`sqlExecutable.ts:49-62`、`:334-395`）。
- 后端 `check_status` 调用 connector 的 `check_status`，并把最新状态回写到 `Document2` 的 snippet（`notebook/api.py:253-297`）；HS2 把 Thrift 的 operationState 映射为 `running/available`（`hiveserver2.py:361-386`）。
- 轮询请求本身也注册为 cancellable，取消时清掉定时器（`sqlExecutable.ts:319-327`）。

### 6.3 结果拉取

- `fetch_result_data` 默认 `rows=100`，支持 `startOver`（`notebook/api.py:301-336`）。
- 前端结果表滚动到底部时再拉 100 行（`ResultTable.vue:203-213`、`executionResult.ts:151-160`）。
- 结果元数据来自 `fetch_result` 的 `meta`（name/type/comment），前端按数值/日期/复杂类型分类渲染（`executionResult.ts:168-195`）。
- 后端对表格结果做 HTML escape，避免结果单元格注入（`notebook/api.py:331-335`、`notebook/models.py:47-75`）。

### 6.4 取消与关闭

- 取消入口 `POST /api/v1/editor/cancel_statement`（`api.ts:160`）；后端 `cancel_statement` 调用 connector 的 `cancel`（`notebook/api.py:393-416`）。
- HS2 通过 `db.cancel_operation(handle)` 取消（`hiveserver2.py:442-448`）。
- 前端 `SqlExecutable.cancel()` 先把状态置为 `canceling`，再依次取消所有 `cancellables`，最后置为 `canceled`（`sqlExecutable.ts:499-516`）。
- `close_statement` 用于释放游标/查询句柄（`notebook/api.py:703-736`、`hiveserver2.py:458-470`）。
- 注意：取消是「尽力而为」，不是事务回滚；已执行的 DDL/DML 不会因取消而撤销（源码未提供补偿机制，属引擎语义）。

### 6.5 日志与进度

- 日志入口 `POST /api/v1/editor/get_logs`，支持 `from`、`size`、`full_log`（`notebook/api.py:419-463`）。
- HS2 通过 `db.get_log(handle)` 拉取执行日志（`hiveserver2.py:450-456`）。
- 前端 `ExecutionAnalysisPanel` 展示 errors、jobs、logs 三块；jobs 来自日志解析出的 YARN/作业链接（`ExecutionAnalysisPanel.vue:18-50`）。
- 进度条是启发式的：connector 的 `progress()` 默认返回 50（`desktop/libs/notebook/src/notebook/connectors/base.py:590-591`），前端在 `available` 时置 100（`sqlExecutable.ts:341-346`）。
- 结论：Hue 的进度不是精确进度，而是「状态 + 日志 + 作业链接」；EQL 若要精确进度，需要引擎侧提供可轮询的 metrics。

### 6.6 超时

- Hue 没有统一的「每查询超时」配置；超时主要由引擎侧参数控制。
- Impala 通过 `QUERY_TIMEOUT_S` 注入会话配置，超时由 Impala 取消查询（`apps/impala/src/impala/conf.py:105-108`、`apps/beeswax/src/beeswax/server/hive_server2_lib.py:1072-1090`）。
- Thrift 连接层有 `SERVER_CONN_TIMEOUT`，默认 120 秒（`apps/beeswax/src/beeswax/conf.py:156-160`），但这不是查询执行超时。
- 对 EQL 的启示：需要区分 connect timeout / statement timeout / idle timeout，并在工具层给出可配置项，而不是依赖某个引擎参数。

### 6.7 结果存储

- 默认链路不在 Hue 服务端持久化结果行：异步执行只保存 `handle`，结果行通过 `fetch_result` 从引擎游标分页读取（`notebook/api.py:158-180`、`hiveserver2.py:388-410`）。
- 历史记录保存的是 notebook JSON（含 handle、语句、元数据），不含结果行；因此历史详情页通常只能重跑，不能回看旧结果（`notebook/api.py:175-180`）。
- 结果的可重取性依赖引擎是否保留查询句柄；HS2 在句柄过期时返回 `QueryExpired`，前端显示 “Results have expired”（`ResultTable.vue:36-38`、`hiveserver2.py:398-407`）。
- 设计文档曾讨论「自动 LIMIT 5000 / 结果缓存 / Parquet」但未落地（`docs/designs/query_result_handling.md:2-5`），可视为未来方向而非现状。

## 7. 结果集展示与导出

### 7.1 表格

- Editor v2 用 `HueTable` 渲染结果，支持 sticky header、sticky first column（`ResultTable.vue:20-27`）。
- 空态区分 executing / success / expired / failed / empty / waiting-for-stream（`ResultTable.vue:29-49`）。
- 旧结果网格（Knockout）仍提供列显隐、列过滤、图表、下载等能力（`desktop/core/src/desktop/js/apps/editor/components/resultGrid/ko.resultGrid.js:44-58`、`:90-146`）。
- 结果列类型会被归一化（去掉 `_type` 后缀、转小写），并按数值/日期/字符串/复杂类型分类（`executionResult.ts:168-195`）。

### 7.2 导出

- CSV/Excel 下载走 `/notebook/download` 表单 POST，前端弹「正在准备下载」模态框（`ko.resultDownloadModal.js:119-132`、`ko.resultDownloadActions.js:44-60`）。
- 后端 `download` 视图调用 connector 的 `download()`，最终由 `export_csvxls` 生成流式响应（`desktop/libs/notebook/src/notebook/views.py:326-357`、`connectors/base.py:570-580`）。
- 导出有行数/字节数上限：`DOWNLOAD_ROW_LIMIT`、`DOWNLOAD_BYTES_LIMIT`，负数表示不限制（`apps/beeswax/src/beeswax/conf.py:218-232`）。
- 除文件下载外，还支持导出到 HDFS、Hive 表、HDFS 目录、Search Index、Dashboard（`notebook/api.py:810-900`）。
- 安全注意：导出会写 `request.audit`（`notebook/views.py:354-357`、`notebook/api.py:834-838`）。

### 7.3 评价

- Hue 的结果区是「表格 + 图表 + 下载 + 日志」四件套，产品完整度高。
- 结果分页是「按需拉取 + 无限滚动」，不是一次性全量；这对 EQL 的大结果集是正确方向。
- 结果不落库简化了架构，但也牺牲了「历史结果回看」；EQL 工具若需要回看，必须自建结果缓存或快照。

## 8. 查询管理（history / saved query / share / explain）

### 8.1 History

- 每次执行会创建/更新一个 `Document2`，`is_history=True`；在 connectors 模式下类型为 `query-<dialect>`，否则沿用 snippet type（`notebook/api.py:531-572`）。
- 历史保存完整 notebook JSON（含语句、handle、变量、状态），不含结果行（`notebook/api.py:561-572`）。
- `get_history` 支持按 `doc_type`、`connector_id`、文本、分页（默认 50，最大 100）查询（`notebook/api.py:593-643`）。
- 前端 `QueryHistoryTable` 支持搜索、清空、导出、导入；导出通过 `/desktop/api2/doc/export?history=true` 完成（`QueryHistoryTable.vue:36-60`、`:226-240`）。
- 历史是「按用户」的：查询会过滤 `user=request.user`（`notebook/api.py:603-606`、`desktop/core/src/desktop/models.py:1048-1055`）。

### 8.2 Saved Query

- 保存入口是 `POST /notebook/api/notebook/save`（不在 `/api/v1/editor` 前缀下），底层 `_save_notebook` 维护 `Document2` 与历史依赖（`notebook/api.py:509-525`、`:465-508`；`desktop/libs/notebook/src/notebook/urls.py:60-62`）。
- 保存查询与历史共享同一套文档模型：历史通过 `dependencies` 指向父级保存查询（`notebook/api.py:550-558`）。
- 打开/关闭分别走 `open_notebook`/`close_notebook`（`notebook/api.py:667-701`）。
- 分享是文档级 ACL：`Document2.share`、`DocumentPermission` 只支持 read/write 两种权限（`desktop/core/src/desktop/models.py:914-927`、`:1498-1517`），前端通过 `doc.show.share.modal` 打开分享弹窗（`EditorViewModel.js:446`）。

### 8.3 Explain

- 后端有 `POST /notebook/api/explain`，调用 connector 的 `explain()`（`notebook/api.py:785-795`）。
- HS2 的 `explain()` 执行 `db.explain(query)` 并返回文本计划（`hiveserver2.py:580-607`）。
- 但新 Editor v2 的执行分析面板只展示 errors/jobs/logs，没有调用 explain（`ExecutionAnalysisPanel.vue:18-50`）；EXPLAIN 仍主要挂在旧 Notebook 的 `snippet.explain()` 上（`apps/notebook/snippet.js:2037-2064`）。
- 结论：Hue 的 EXPLAIN 是「引擎侧文本计划」，不是统一的执行计划可视化；新编辑器没有把 EXPLAIN 做成一级入口，这是 Hue 的产品缺口，也是 EQL 工具应当补上的差异点。

### 8.4 格式化

- 格式化 API 独立于执行，使用 `sqlparse`，只做 reindent + keyword 大写（`notebook/api.py:798-806`）。
- 这意味着格式化不理解 EQL 的实体属性导航；EQL 若要格式化，需要自己的 AST/格式化器或至少支持 `entity.property` 路径不被拆坏。

## 9. 多引擎/方言适配抽象

### 9.1 后端：connector 类 + interface 路由

- `get_api(request, snippet)` 先根据 connector 配置解析出 `interface`，再用 if/elif 手工路由到具体 `Api` 子类（`desktop/libs/notebook/src/notebook/connectors/base.py:418-520`）。
- 路由分支包括 `hiveserver2/hms`、`oozie`、`livy`、`livy-batch`、`text/markdown`、`rdbms`、`jdbc`、`sqlflow`、`teradata`、`athena`、`presto`、`sqlalchemy`、`solr`、`hbase`、`ksql`、`flink` 等（`base.py:455-520`）。
- JDBC 内部又按 URL/driver 再分叉：Teradata、Athena、Presto、ClickHouse、Vertica、Kyuubi（`base.py:472-490`）。
- 每个 `Api` 子类实现同一组钩子：`execute`、`check_status`、`fetch_result`、`cancel`、`get_log`、`explain`、`describe`、`autocomplete`、`export_*`（`base.py:543-700`）。
- 优点：引擎差异被封装在类里，上层 API 稳定。
- 缺点：新增引擎要改 `get_api` 的 if/elif，属于开闭原则的反例；能力差异没有统一协议。

### 9.2 前端：dialect parser 注册表

- `sqlParserRepository` 维护 `AUTOCOMPLETE_MODULES` 和 `SYNTAX_MODULES`，每个 dialect 一个动态 import，未命中时回退到 `generic`（`desktop/core/src/desktop/js/parse/sql/sqlParserRepository.ts:19-41`、`:59`）。
- 支持的 dialect：calcite、dasksql、flink、generic、hive、impala、ksql、phoenix、presto、sparksql（`sqlParserRepository.ts:19-41`）。
- 每个 dialect 的语法由 `jison/structure.json` 组合：lexer + 一组 `.jison` 片段（`desktop/core/src/desktop/js/parse/sql/hive/jison/structure.json:1-60`、`tools/jison/parserDefinitions.js:113-133`）。
- 生成器会扫描 `parse/sql/*/jison/structure.json`，自动生成 `*AutocompleteParser` 与 `*SyntaxParser`（`tools/jison/parserDefinitions.js:132-180`）。
- 这是 Hue 方言适配中最「配置化」的部分：新增方言主要是写 grammar 片段 + structure.json，而不是改 TypeScript 路由。

### 9.3 声明式属性：有，但消费不足

- Connector 类型上声明了 `dialect_properties`：`is_sql`、`sql_identifier_quote`、`sql_identifier_comment_single`、`has_catalog`、`has_database`、`has_table`、`has_live_queries`、`has_optimizer_risks`、`has_optimizer_values`、`has_auto_limit`、`has_reference_language`、`has_reference_functions`、`has_use_statement`（`desktop/core/src/desktop/lib/connectors/types.py:40-58`）。
- 这些属性会被序列化到前端 interpreter 配置（`desktop/libs/notebook/src/notebook/conf.py:128-146`）。
- 但前端实际消费非常有限：只检索到 `sql_identifier_quote`（`desktop/core/src/desktop/js/sql/sqlUtils.ts:271`）和 `has_optimizer_risks`（`desktop/core/src/desktop/js/ko/components/assist/ko.assistEditorContextPanel.js:209-212`）两处。
- 后端也基本只声明、不消费这些能力位（`connectors/types.py` 中大量字段，未在 `notebook/` 逻辑中检索到对应分支）。
- 结论：Hue 的「方言能力声明」更接近元数据展示，而不是真正的 capability gating；这是 EQL 可以做得更好的地方。

### 9.4 与 EQL 配置化方言的对比

| 维度 | Hue | EQL 工具建议 |
|---|---|---|
| 方言语法 | 每 dialect 一套 Jison grammar，编译期生成 | 一套 EQL 核心语法 + 可配置扩展（关键字/函数/标识符引用/注释） |
| 补全上下文 | 客户端 parser 计算 | 客户端 parser + 服务端实体元模型校验 |
| 元数据 | connector 直接访问引擎 | Nop ORM 元模型 + 数据源元数据 |
| 执行 | 每引擎一个 `Api` 类 | 统一 `EqlExecutor` + `DataSource` SPI |
| 能力声明 | `dialect_properties`，消费不足 | 显式 `DialectCapabilities`，前后端共用并强约束 |
| 结果类型 | 引擎 meta 字符串 | ORM 属性类型 + JDBC 类型双向映射 |

- 对 EQL，最值得借鉴的是「parser 按 dialect 懒加载 + generic 回退」；最不值得照搬的是「每个引擎一个 Python 类 + if/elif 路由」。

## 10. 权限、安全与审计

### 10.1 应用与文档权限

- 进入 Editor 需要 `check_editor_access_permission`，按 editor type 检查 Hue 应用权限（`desktop/libs/notebook/src/notebook/decorators.py:50-72`）。
- 文档读权限：`check_document_access_permission` 按 notebook id/uuid 校验 `Document2.can_read`（`decorators.py:74-93`）。
- 文档写权限：`check_document_modify_permission` 校验 `can_write`（`decorators.py:95-111`）。
- `can_read` 允许 admin/owner/被分享者；`can_write` 允许 admin/owner/被分享组/被分享用户（`desktop/core/src/desktop/models.py:719-736`）。
- 共享模型只有 read/write 两级，没有列级/行级/数据源级权限（`models.py:914-927`、`:853-857`）。

### 10.2 数据源身份

- 执行时使用「当前登录用户」建立引擎连接：HS2 通过 `dbms.get(self.user, ...)` 获取 per-user 连接（`hiveserver2.py:789-808`）。
- JDBC 支持 `impersonation_property`，把登录用户传给驱动（`desktop/libs/notebook/src/notebook/connectors/jdbc.py:60-68`）。
- SQLAlchemy 支持 `has_impersonation` 配置（`sql_alchemy.py:164-194`）。
- 会话按 type 复用：`SessionManager` 为每个 connector type 缓存一个 session promise，支持认证弹窗和重启（`sessionManager.ts:26-108`）。
- 安全含义：Hue 本身不代理数据权限，权限最终由底层引擎的用户身份决定；这对 EQL 是重要参照——EQL 工具应复用 Nop 的认证/授权，而不是复制 Hue 的文档 ACL。

### 10.3 审计

- 审计中间件 `AuditLoggingMiddleware` 在请求设置了 `request.audit` 时写 JSON 审计日志，字段含 username、impersonator、ip、operation、operationText、allowed、service、url（`desktop/core/src/desktop/middleware.py:518-575`）。
- 审计默认关闭：`AUDIT_EVENT_LOG_DIR` 为空即禁用（`desktop/core/src/desktop/conf.py:1887-1891`）。
- 明确写审计的操作：登录、下载结果、导出到 HDFS/Hive 表/目录（`middleware.py:613`、`:741`；`notebook/views.py:354-357`；`notebook/api.py:834-838`、`:860-863`、`:890-893`）。
- 未发现 `execute` 路径显式设置 `request.audit`；查询执行的可追溯性主要依赖 query history（`Document2`）而非审计日志。这是 Hue 审计覆盖的缺口。

### 10.4 输出安全

- 结果单元格在服务端 HTML escape，避免把查询结果当 HTML 注入（`desktop/libs/notebook/src/notebook/models.py:47-75`、`notebook/api.py:331-335`）。
- 前端对分享/文档内容使用 `deXSS` 等工具（`apps/editor/snippet.js:39`）。
- 但 SQL 本身是「按设计执行任意语句」，安全边界不在 SQL 解析，而在「谁有权连哪个数据源 + 引擎侧权限」。EQL 工具应明确这一点，避免把 SQL 黑名单当成安全机制。

## 11. 测试与工程质量

### 11.1 测试

- 前端用 Jest 29 + `@vue/vue3-jest` + `babel-jest`，测试匹配 `desktop/core/src/desktop/js/**/*.test.(js|jsx|ts|tsx)`（`jest.config.js:1-26`）。
- 仓库内 `.test.*` / `*_tests.py` 文件约 320 个（按 `find` 统计），覆盖 parser、Vue 组件、Knockout 组件、后端 connector/API。
- 后端用 pytest + Django settings，默认排除 `integration` marker，支持 `requires_hadoop`（`pyproject.toml:3-17`）。
- CI：Python 3.9/3.10/3.11 矩阵，跑 `make apps test_prep` + `pytest`（`.github/workflows/commitflow-py3.yml:10-12`、`:57-64`）；前端跑 prettier、eslint、stylelint、jest（`.github/workflows/commitflow-frontend.yml:39-53`）。
- Python lint 用 ruff（`.github/workflows/commitflow-py3.yml:70-72`、`pyproject.toml:79-96`）。

### 11.2 工程质量判断

- 优点：parser 有独立生成器与大量 fixture 测试；connector 有单测；Vue 组件有 snapshot/DOM 测试；CI 覆盖 lint + 单测。
- 债务：
  - 新旧两套 Editor 并存（`apps/notebook/snippet.js` 2881 行 vs `apps/editor/*`），行为容易分叉。
  - 前端同时存在 Knockout、React、Vue 三种范式，状态同步靠 `huePubSub` 事件（`AceEditor.vue:49`、`snippet.js:816`）。
  - Connector 的 if/elif 路由和能力位声明缺乏强约束，新增引擎成本高。
  - 版本号双源（`package.json:3` vs `VERSION:20`），发版口径不清晰。

## 12. 对 Nop EQL 在线编辑器的启示

### 12.1 Adopt（可直接借鉴）

1. **语句级执行状态机**：以 `SqlExecutable` 为单位管理 `parsedStatement/handle/status/logs/result`，每个语句独立轮询、取消、重试；EQL 可直接映射为「一个 EQL 语句 = 一个 Executable」。
2. **客户端语句定位 + 选中执行**：用 parser 产出语句位置区间，光标/选区决定 active/selected statements；这是「在线 DBeaver」体验的核心，且与编辑器内核解耦。
3. **HTTP 轮询为主、WS 可选**：`execute → check_status → fetch_result_data → cancel/get_logs` 的 API 切分简单可靠；EQL 第一阶段不必引入 WebSocket。
4. **结果分页 + 无限滚动**：默认 100 行、滚动拉取，避免一次性把大结果集塞进浏览器。
5. **dialect parser 懒加载 + generic 回退**：EQL 可有 `EqlDialect` 注册表，按数据源方言动态加载补全/校验规则，未配置时回退到通用 SQL。
6. **统一目录树抽象**：`DataCatalogEntry.getChildren()` 的懒加载模式适合「数据源 → 实体 → 属性 → 关联」树；缓存放浏览器侧并设 TTL。
7. **可嵌入的 Scratchpad**：Hue 把编辑器+执行+结果封装为 `SqlScratchpad` Web Component（`SqlScratchpad.vue:20-70`、`webComponents/SqlScratchpad.ts:19-25`），Nop 可复用同一思路做「可嵌入 EQL 控制台」。
8. **历史与保存查询分离**：历史是执行记录，保存查询是文档；两者用 UUID 关联，便于「从历史另存为查询」。

### 12.2 Adapt（需改造后使用）

1. **方言能力声明**：Hue 的 `dialect_properties` 只声明不消费；EQL 应定义 `DialectCapabilities` 并由前后端共同消费（是否支持 EXPLAIN、是否支持取消、是否支持多语句、是否支持 `USE`、默认 LIMIT、标识符引用符等）。
2. **后端 connector 路由**：不要复制 if/elif；用 `DataSource` + `EqlDialect` SPI 注册，新增数据源只加配置/插件。
3. **多语句执行语义**：Hue 的 batch chain 是「前一条成功才执行下一条」，且 continuation 依赖 statement hash（`sql_utils.py:37-75`）；EQL 应显式定义「遇错继续/遇错停止/事务包裹」三种策略。
4. **EXPLAIN/校验**：Hue 的 EXPLAIN 是旧编辑器里的引擎文本计划；EQL 应把「语法校验 + 语义校验（实体/属性是否存在）+ EXPLAIN」做成一级能力，并在结果区旁展示。
5. **权限模型**：Hue 的文档 read/write ACL 不足以覆盖 EQL 的数据源/实体权限；应复用 Nop 的认证、角色、数据权限，并在执行前做「数据源可访问 + 实体可读」校验。
6. **审计**：Hue 只审计导出/登录，不审计执行；EQL 应记录「谁、何时、对哪个数据源、执行了什么 EQL、耗时、行数、是否失败」，但要注意 SQL 文本脱敏与审计日志容量。
7. **结果导出**：Hue 的 CSV/XLS 流式导出可借鉴，但 EQL 需要补充 JSON/NDJSON 以及「导出当前结果集 vs 重跑导出」的语义区分。

### 12.3 Reject（不应照搬）

1. **Django + Knockout + React + Vue 的混合栈**：Nop 已有 React 19 + CodeMirror 6 + flux，无需引入第二套编辑器框架。
2. **Cloudera cuix 本地 tarball 依赖**：`package.json` 直接引用 `file:.../cuix-13.4.1.patched.tgz`（`package.json:34`、`:45`），不是纯开源 npm 依赖，复用会引入许可/供应链风险。
3. **数十个 connector 的「引擎动物园」**：EQL 只需 EQL 方言 + 数据源，不需要 Hive/Impala/Presto 的全量适配。
4. **HDFS/Hive 表导出、Job Browser、Oozie 等大数据生态耦合**：这些是 Hue 的领域包袱，不是 EQL 工具的核心。
5. **把整棵 notebook JSON 塞进 Document2 的存储方式**：历史/保存查询会越来越重；EQL 应用独立的 `query` / `query_execution` / `query_result` 表。
6. **旧版 `apps/notebook/snippet.js`（2881 行）式的 God Object**：执行、变量、UI、历史、分享混在一个文件，不可维护。

### 12.4 给 EQL 的方言配置建议（衍生设计，不在本次源码范围内）

- `EqlDialect` 描述：`name`、`identifierQuote`、`commentSyntax`、`keywords`、`functions`、`typeMapping`、`supportsExplain`、`supportsCancel`、`supportsMultiStatement`、`supportsUseDatabase`、`defaultLimit`、`maxRows`。
- `EqlCompletionProvider` 链：关键字 → 函数 → 实体 → 属性 → 关联路径 → 枚举值 → 样本值。
- `EqlValidator`：语法校验（parser）+ 语义校验（ORM 元模型：实体/属性/关联是否存在、类型是否匹配）+ 权限校验（数据源/实体可读）。
- `EqlExecutor`：统一 `execute/checkStatus/fetchPage/cancel/explain`，底层按数据源类型选择 JDBC 或其他实现。
- 这样可以把 Hue 的「parser 注册表 + connector 类」重构成「dialect 配置 + 少量 SPI」，既保留多方言能力，又避免 if/elif 和声明不消费的问题。

## 13. 关键证据清单

1. `README.md:19`、`:30-35` — 产品定位、交互式 SQL 编辑/补全/Table Browser/多连接器。
2. `package.json:3`、`:27`、`:37`、`:59`、`:72`、`:85`、`:160`、`:164` — 版本、License、React/Antd/Knockout/Vue/TS/Webpack。
3. `VERSION:20` — 仓库版本仍为 4.11.0，与 package.json 不一致。
4. `desktop/core/generate_requirements.py:55-61`、`:75` — Django 4.2.30、Channels 4.2.2、DRF 3.15.2。
5. `tools/docker/hue/Dockerfile:3`、`:9`；`tools/docker/hue/startup.sh:4` — Docker + Python 3.11 + supervisor。
6. `desktop/core/src/desktop/asgi.py:7-9`；`desktop/core/src/desktop/conf.py:2277-2286`；`desktop/libs/notebook/src/notebook/routing.py:27-29` — ASGI/Channels 存在，但 WebSocket 默认关闭。
7. `desktop/core/src/desktop/lib/connectors/types.py:28`、`:40-58`、`:823-863` — 43 个 dialect（33 editor + 10 其他）与 `dialect_properties` 能力位。
8. `desktop/core/src/desktop/js/parse/sql/sqlParserRepository.ts:19-41`、`:59`；`tools/jison/parserDefinitions.js:113-133` — 按 dialect 懒加载 parser、generic 回退、`structure.json` 生成。
9. `desktop/core/src/desktop/js/apps/editor/components/aceEditor/AceEditor.vue:153-179` — Ace 补全与编辑选项。
10. `desktop/core/src/desktop/js/apps/editor/components/aceEditor/AceLocationHandler.ts:632-737` — 活动语句/选区计算。
11. `desktop/core/src/desktop/js/apps/editor/execution/sqlExecutable.ts:49-62`、`:247-300`、`:311-395`、`:499-516` — 状态枚举、执行、轮询、取消。
12. `desktop/core/src/desktop/js/apps/editor/execution/api.ts:160-168` — 执行链路 REST 端点。
13. `desktop/libs/notebook/src/notebook/api.py:123-210`、`:253-297` — `_execute_notebook`、handle/结果处理、状态回写。
14. `desktop/libs/notebook/src/notebook/api.py:301-336` — fetch_result_data 默认 100 行、HTML escape。
15. `desktop/libs/notebook/src/notebook/connectors/base.py:418-520` — connector interface 路由。
16. `desktop/libs/notebook/src/notebook/connectors/hiveserver2.py:318-359`、`:361-386`、`:442-456`、`:580-607` — HS2 执行、状态、取消、日志、explain。
17. `desktop/libs/notebook/src/notebook/sql_utils.py:37-75`、`:94-160` — 多语句切分与 statement hash continuation。
18. `desktop/libs/notebook/src/notebook/api.py:531-572`、`:593-643` — 历史文档化与分页。
19. `desktop/core/src/desktop/models.py:719-736`、`:914-927`、`:1498-1517` — Document2 read/write ACL 与分享。
20. `desktop/core/src/desktop/middleware.py:518-575`、`desktop/core/src/desktop/conf.py:1887-1891` — 审计中间件与默认关闭。
21. `desktop/core/src/desktop/js/catalog/dataCatalog.ts:170-176`、`:277-280`、`:357-368` — localforage 元数据缓存与 TTL。
22. `desktop/core/src/desktop/js/apps/editor/components/result/ResultTable.vue:203-213` — 滚动加载 100 行。
23. `apps/beeswax/src/beeswax/conf.py:218-232`、`apps/impala/src/impala/conf.py:105-108` — 导出限制与引擎侧超时。
24. `desktop/core/src/desktop/js/apps/editor/components/sqlScratchpad/SqlScratchpad.vue:20-70` — 可嵌入 Scratchpad 组件。
25. `jest.config.js:1-26`、`pyproject.toml:3-17`、`.github/workflows/commitflow-frontend.yml:39-53` — 测试与 CI。

## 14. Open Questions

- [ ] Editor v2 是否已完全替代旧 Notebook Editor？旧 Editor 的 EXPLAIN、图表、分享、Schedules 等能力是否会迁移？源码中两套实现并存，未找到明确的弃用声明。
- [ ] Hue 是否有「服务端结果缓存」的可选实现？当前只见 handle + 引擎游标，未发现 Hue 自建结果存储；`docs/designs/query_result_handling.md:2-5` 只是设计设想。
- [ ] `dialect_properties` 中大量能力位（`has_auto_limit`、`has_use_statement`、`has_reference_language` 等）的真实消费方是谁？本次 grep 未在前端/后端逻辑中找到对应分支，可能是为未来或外部插件预留。
- [ ] 多语句 batch 执行在中途失败时的语义：后续语句是全部停止还是继续？`sqlExecutable.ts:341-353` 只看到成功后才触发下一条，失败路径未显式处理后续链。
- [ ] WebSocket 结果流式在生产环境是否默认启用？`WEBSOCKETS.ENABLED` 默认 False，且依赖 Redis channel layer，实际部署是否开启未确认。
- [ ] `package.json:3` 的 6.0.1 与 `VERSION:20` 的 4.11.0 哪个是真实发版号？可能涉及 Cloudera 内部版本与社区版本的双轨。
- [ ] `cuix` 本地 tarball 的许可证与可再分发性：`package.json:34`、`:45` 引用仓库内 `.tgz`，未在源码中看到独立许可证说明。
- [ ] EQL 工具是否需要「结果快照」能力？如果需要，Hue 的「不落结果」模式不可直接复用，需要设计结果表/对象存储与 TTL。
- [ ] EQL 的实体属性导航补全能否复用 Hue 的「客户端 parser + 服务端元数据」两段式？EQL 的路径补全需要 ORM 元模型，可能必须在服务端完成语义校验。
- [ ] Hue 的 EXPLAIN 只返回文本计划，EQL 是否需要结构化的执行计划（算子、代价、下推信息）？这会影响后端 SPI 设计。

## 15. 结论

- Hue 对 EQL 在线编辑器的最大价值，不是它的 Django 实现，也不是它的数十个连接器，而是三件事：
  1. **把 SQL 文本切成可执行语句，并用 `SqlExecutable` 状态机管理每条语句的生命周期**；
  2. **把「光标/选区 → 活动语句」做成编辑器的独立能力**，从而自然支持「执行选中」「批量执行」「按语句展示状态」；
  3. **把方言差异拆成 parser 注册表 + connector 实现 + 能力声明三层**，其中 parser 注册表最接近「配置化方言」的目标。
- 对 Nop 的落地建议：以 flux-code-editor 的 CodeMirror 6 为编辑器内核，复用 Hue 的语句定位/执行状态机/HTTP 轮询/结果分页模式；后端以 Nop ORM 元模型为语义层，定义 `EqlDialect` + `DataSource` SPI；权限与审计直接复用 Nop 的认证授权体系；不要引入 Hue 的 Django、Knockout、Vue 混合栈与 Cloudera 专有依赖。
- 与同目录的 [Flux SQL 编辑器现状报告](2026-10-18-flux-sql-editor-current-state-and-eql-gap.md)、[SQLPad 深度调研报告](2026-10-18-sqlpad-analysis.md) 结合阅读：Flux 报告回答「前端已有什么」，SQLPad 报告回答「小内核如何做」，本报告回答「成熟产品如何做多引擎与执行链路」。
