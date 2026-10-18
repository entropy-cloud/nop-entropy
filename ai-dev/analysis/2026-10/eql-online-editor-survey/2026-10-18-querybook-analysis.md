# Querybook 深度调研报告

> Status: resolved
> Date: 2026-10-18
> Scope: Querybook 源码架构 / Notebook 式 SQL 编辑 / 执行队列与权限
> Conclusion: Querybook 对 EQL 在线编辑器的最大可借鉴点不是它的 CodeMirror 编辑器或 Python 技术栈，而是「QueryExecution/StatementExecution 执行记录 + Celery 异步执行 + 结果对象存储 + Socket.IO 状态推送 + 环境/执行两级权限与审计」这一套闭环；EQL 工具应借用该闭环，但把方言能力从散落的 feature flag 收敛为单一 Dialect Profile，并在服务端强制执行行数、超时与只读策略。

## 1. 背景与调研问题

- 目标产品是为 Nop EQL 提供「在线编辑 + 测试」工作台，交互对标在线版 DBeaver：数据源/实体树、补全、执行、结果表格、历史、导出、EXPLAIN/校验、多标签；EQL 方言能力尽量配置化。
- Querybook 是本轮调研中与「查询即文档 / 多单元格 notebook / 查询执行 / 结果复用 / 权限与审计」最接近的开源参照物之一；其价值不在单点编辑器，而在把一次查询从提交、排队、执行、结果落盘到权限审计串成完整生命周期。
- 本报告回答六个问题：
  1. Querybook 的前后端边界、异步任务与状态归属如何划分？
  2. Notebook/DataDoc 式多单元格模型如何组织，执行选中与结果复用怎么做？
  3. 一次查询从点击 Run 到结果表格出现，中间经过哪些可靠性与权限检查？
  4. 查询引擎/方言适配抽象是否足以支撑「配置化 EQL 方言」？
  5. 权限、行级安全、审计到什么粒度？
  6. 哪些能力应 adopt / adapt / reject 到 Nop EQL 工具？
- 方法：只读静态源码与文档，不构建、不安装、不联网；除特别标注外，证据路径相对 Querybook 仓库根目录（`/Users/abc/sources/sql-editors/querybook`），行号来自本次 `read`/`grep`/`sed` 实际输出；`ai-dev/...` 路径相对 nop-entropy 仓库根。
- 姊妹报告 `ai-dev/analysis/2026-10/eql-online-editor-survey/2026-10-18-flux-sql-editor-current-state-and-eql-gap.md:6` 已给出 Flux 侧差距；本报告只补充 Querybook 可借鉴/应规避的架构结论，不重复前端现状盘点。

## 2. 仓库与产品概览

| 维度 | 事实 | 证据 |
|---|---|---|
| 上游 / HEAD | pinterest/querybook，HEAD `3f26532`（2026-09-16） | 本次 `git log -1` |
| 版本 | 3.42.1 | `package.json:3` |
| License | Apache License 2.0 | `LICENSE:1` |
| 定位 | "Big Data IDE"，组织 analyses（富文本 + 查询 + 图表） | `README.md:9`、`README.md:14` |
| 代码规模 | Python 约 360 文件 / 3.66 万行；TS/TSX 约 815 文件 / 8.6 万行（含测试，`find`+`wc` 粗测） | 本次统计 |
| 后端栈 | Python 3.10、Flask、SQLAlchemy、Celery、Redis、Elasticsearch、Alembic | `Dockerfile:1`、`requirements/base.txt:24`、`requirements/base.txt:33`、`requirements/base.txt:15` |
| 前端栈 | React 17、Redux 4、TypeScript 5.6、CodeMirror 6（经 `@uiw/react-codemirror`）并保留 CodeMirror 5 兼容层 | `package.json:91`、`package.json:108`、`package.json:118`、`package.json:58` |
| 部署形态 | Web（uWSGI + gevent + websocket）、Celery Worker、Celery Beat Scheduler 三进程；Docker/Compose/Helm | `docs_website/docs/overview/architecture.mdx:9`、`querybook/scripts/runservice:30`、`Makefile:22` |
| 必需基础设施 | MySQL（或任意 SQLAlchemy 库）、Redis、Elasticsearch；结果存储可选 | `docs_website/docs/overview/architecture.mdx:20`、`querybook/config/querybook_default_config.yaml:62` |
| 查询引擎 | 通用 SQLAlchemy 执行器（约 28 个方言探测）+ 6 个专用执行器 + 插件执行器 | `querybook/server/lib/query_executor/executors/sqlalchemy.py:50`、`querybook/server/lib/query_executor/all_executors.py:13` |
| 结果存储 | `db` / `s3` / `gcs` / `file` 四种 Reader/Uploader，可插件扩展 | `querybook/server/lib/result_store/all_result_stores.py:5` |
| 元数据源 | Hive Metastore、Thrift HMS、MySQL、SQLAlchemy Inspector、AWS Glue，可插件扩展 | `querybook/server/lib/metastore/all_loaders.py:3` |
| 产品面 | DataDoc（query/text/chart/python 单元格）、Ad-hoc 查询、表文档/血缘/样例、搜索、定时执行、Peer Review、GitHub 版本、导出、AI 助手 | `README.md:14`、`querybook/webapp/const/datadoc.ts:8`、`querybook/server/datasources/github.py:94` |
| 插件机制 | 23 个插件目录（executor/metastore/result_store/auth/exporter/webpage 等） | 本次 `ls plugins`；`docs_website/docs/integrations/plugins.mdx:9` |

README 明示的查询引擎包括 Presto/Hive/Druid/Snowflake/BigQuery/MySQL/SQLite/PostgreSQL（`README.md:42-49`）；专用执行器覆盖 Hive/Presto/BigQuery/Snowflake/Trino/Redshift，其余通过通用 SQLAlchemy 方言接入（`querybook/server/lib/query_executor/all_executors.py:13`）。

关键判断：

- Querybook 是「重后端 + 厚前端 SPA」的单体应用，不是库；它的部署假设是至少一个关系库、一个 Redis、一个 ES，运维成本明显高于一个纯 JDBC 控制台。
- 它的产品核心是 DataDoc（文档）而非「查询标签页」；查询只是文档里的一种单元格类型，这决定了它的保存、协作、权限与历史模型。
- 技术栈对 Nop 没有复用价值，但领域模型、状态机、插件边界与权限树可迁移。

## 3. 架构总览

### 3.1 前后端边界

- 前端是 React SPA，静态资源由 Flask 托管；所有业务接口走统一前缀 `/ds`，由 `register()` 装饰器注册并统一做登录校验、参数注入、错误包装与 API 日志。`querybook/server/app/datasource.py:51`、`querybook/server/app/datasource.py:57`、`querybook/server/const/datasources.py:10`。
- 本次统计 `querybook/server/datasources/*.py` 共 253 个 `@register` 端点；最大的四个模块是 admin（42）、metastore（41）、datadoc（34）、query_execution（30）。这是典型的「按资源分模块的 RPC 风格 API」，不是 GraphQL。
- 前端用 `lib/datasource.ts` 统一封装 `/ds` baseURL，再按资源拆成 `resource/*.ts`（queryExecution、dataDoc、search 等），Redux 只做客户端缓存与 UI 状态。`querybook/webapp/lib/datasource.ts:44`、`querybook/webapp/resource/queryExecution.ts:1`。
- 实时通道独立于 REST：查询执行命名空间 `/query_execution`，DataDoc 协作命名空间 `/datadoc`；Socket.IO 通过 Redis `message_queue` 支持多 Web 实例。`querybook/server/app/flask_app.py:146`、`querybook/server/datasources_socketio/query_execution.py:15`、`querybook/webapp/lib/socketio-manager.ts:34`。

### 3.2 运行组件与状态归属

| 组件 | 职责 | 状态 |
|---|---|---|
| Flask Web | HTTP API、静态资源、Socket.IO 接入 | 无长任务状态 |
| Celery Worker | 执行查询、定时 DataDoc、ES 同步、样例查询、导出 | 任务状态在 Celery backend，业务状态在 MySQL |
| Celery Beat | 从数据库 `task_schedule` 读取 cron 并投递 | 调度定义在 MySQL |
| MySQL | DataDoc、单元格、执行记录、错误、日志、调度、审计 | 业务事实源 |
| Redis | Celery broker/backend、Socket.IO 跨实例、协作编辑缓存 | 可丢失的运行时状态 |
| Elasticsearch | DataDoc/表/查询/用户搜索与补全 | 最终一致的检索副本 |
| 对象存储/DB | 查询结果 CSV 与日志 | 结果事实源 |

- 状态机以数据库枚举为准：`INITIALIZED → (DELIVERED) → RUNNING → DONE/ERROR/CANCEL`，另有 `PENDING_REVIEW/REJECTED`；每条语句有独立的 `StatementExecutionStatus`（含 `UPLOADING`）。`querybook/server/const/query_execution.py:12`、`querybook/server/const/query_execution.py:23`。
- Celery 的 progress 只作为运行中百分比来源，不承载最终结果；最终结果路径、行数、错误写回 MySQL。`querybook/server/lib/query_executor/base_executor.py:308`、`querybook/server/models/query_execution.py:133`。

### 3.3 两条主链路

- 编辑链路：单元格输入 → 前端 500ms 防抖 → Socket.IO `update_data_cell` → 服务端权限校验 → 写 MySQL → 广播 `data_doc_updated`；ES 同步是 60s 防抖的异步任务。`querybook/webapp/components/DataDocQueryCell/DataDocQueryCell.tsx:71`、`querybook/server/datasources_socketio/datadoc.py:230`、`querybook/server/logic/datadoc_collab.py:43`、`querybook/server/tasks/sync_elasticsearch.py:6`。
- 执行链路：REST 创建执行记录 → 投递 Celery → Worker 拆分语句范围 → 逐语句执行 → 结果 CSV 上传 → Socket.IO 事件 → 前端完成后按需 HTTP 拉取结果。`querybook/server/datasources/query_execution.py:115`、`querybook/server/tasks/run_query.py:31`、`querybook/server/lib/query_executor/base_executor.py:315`、`querybook/server/datasources/query_execution.py:399`。

架构评价：

- 优点是把「长任务」彻底移出 Web 进程，Web 只做鉴权、编排和推送；执行器故障不会拖垮 API。
- 代价是运行时依赖多、结果一致性靠 DB + 对象存储 + 事件三者协作，排障面大。
- 对 EQL 的启示是「执行记录模型」可独立于 Querybook 技术栈复用；不必复制 Flask/Celery/ES 三件套。

## 4. SQL/Notebook 编辑器实现

### 4.1 编辑器内核

- 使用 CodeMirror 6（`@uiw/react-codemirror`），`CodeEditor` 统一封装主题、快捷键、搜索、事件与基础 setup；QueryEditor 在其上叠加 SQL 专属扩展。`querybook/webapp/components/CodeEditor/CodeEditor.tsx:1`、`querybook/webapp/components/QueryEditor/QueryEditor.tsx:13`。
- 仍保留 CodeMirror 5 的 keymap 兼容（`react-codemirror2`、`codemirror@5` 仍在依赖里），属于迁移期双栈，不是干净的新架构。`package.json:58`、`package.json:93`、`querybook/webapp/components/CodeEditor/CodeEditor.tsx:33`。
- 扩展管线清晰：代码分析（Web Worker）→ SQL parser → 自动补全 / lint / hover / 状态栏；QueryEditor 只做组合。`querybook/webapp/components/QueryEditor/QueryEditor.tsx:196`、`querybook/webapp/components/QueryEditor/QueryEditor.tsx:260`。

### 4.2 方言配置：三张互不一致的表

- CodeMirror 方言映射只有 9 个 key：`sql/mysql/mssql/postgresql/sqlite/presto/trino/sparksql/spark`；未知语言回落 `StandardSQL`，`hive`、`bigquery` 都不在这张表里。`querybook/webapp/lib/codemirror/codemirror-language.ts:97`、`querybook/webapp/lib/codemirror/codemirror-language.ts:109`。
- 前端词法/关键字配置是另一张表：`hive/presto/mysql/trino` 四套，`sparksql` 复用 `hive`；未知语言默认按 `presto` 处理。`querybook/webapp/lib/sql-helper/sql-setting.ts:16`、`querybook/webapp/lib/sql-helper/sql-setting.ts:39`、`querybook/webapp/lib/sql-helper/sql-setting.ts:91`、`querybook/webapp/lib/sql-helper/sql-setting.ts:93`。
- 后端 SQLGlot 映射是第三张表，有 12 个语言（含 bigquery/clickhouse/oracle/snowflake），且 `mssql→tsql`、`postgresql→postgres`、`sparksql→spark`。`querybook/server/const/sqlglot.py:2`。
- 结论：Querybook 的「方言能力」不是单一配置对象，而是前端高亮表、前端词法表、后端 SQLGlot 表、执行器 language 四处各自维护；新增一个引擎要改多处，且容易出现「能执行但不高亮/不能补全」的不一致。这正是 EQL 必须避免的。

### 4.3 补全与语义分析

- 分析在 Web Worker 中做，输入防抖 500ms，输出 `ICodeAnalysis`（编辑器行、lineage 引用、上下文）。`querybook/webapp/hooks/queryEditor/useCodeAnalysis.ts:19`、`querybook/webapp/lib/web-worker/index.ts:11`。
- 补全上下文分为 `column`、`table`、`keyword` 与 `column = value` 四种；表名补全走异步 `SearchTableResource.suggest(metastoreId, prefix)`，即 Elasticsearch 的 suggest 端点，而不是本地元数据树。`querybook/webapp/hooks/queryEditor/extensions/useAutoCompleteExtension.ts:54`、`querybook/webapp/lib/sql-helper/sql-parser.ts:234`、`querybook/webapp/lib/datasource.ts:44`。
- 列补全依赖前端自写的轻量 parser/lexer，能处理 `schema.table.column`、别名与 FROM/JOIN 引用，但不是完整 AST；对多层属性路径、集合导航没有模型。`querybook/webapp/lib/sql-helper/sql-parser.ts:155`、`querybook/webapp/lib/sql-helper/sql-parser.ts:202`。
- 关键字补全有最小长度 2 与「已输完整不提示」的启发式，说明它是交互优化而非语义补全。`querybook/webapp/lib/sql-helper/sql-parser.ts:202`。
- 对 EQL 的直接含义：现有实现只能支撑「表别名 → 列」一层补全，无法支撑 `o.customer.address.city` 或 `o.roles._some.userId` 的多级路径；必须替换补全数据模型，而不是改关键字表。

### 4.4 校验与 lint

- 本地 lint 有两类：上下文无关规则（按语言注册，例如 Hive 的 `count(distinct)` 性能提示）和上下文相关规则（表是否存在、表级 warning）。`querybook/webapp/lib/sql-helper/sql-context-free-linter.ts:3-12`、`querybook/webapp/lib/sql-helper/sql-context-sensitive-linter.ts:6-11`。
- 后端校验是可选能力：引擎的 `feature_params.validator` 指定 validator，`/query/validate/` 调用它；未配置则前端不显示后端 lint。`querybook/server/datasources/query_execution.py:744`、`querybook/webapp/components/AppAdmin/AdminQueryEngine.tsx:426`。
- 内置 validator 只有 Presto/Trino 的 `EXPLAIN (TYPE VALIDATE)`，把每条语句包一层 EXPLAIN 执行，再把引擎返回的行列号映射回原文。`querybook/server/lib/query_analysis/validation/validators/presto_explain_validator.py:14`、`querybook/server/lib/query_analysis/validation/validators/presto_explain_validator.py:67-68`。
- 前端对后端校验做 1s 防抖，结果转成 CodeMirror Diagnostic；这是「异步服务端语义校验 + 本地即时 lint」的双层结构，值得借鉴。`querybook/webapp/hooks/queryEditor/useLint.tsx:147`、`querybook/webapp/hooks/queryEditor/useLint.tsx:155`。

### 4.5 Notebook 模型与执行选中

- DataDoc 是容器，单元格有 `query/text/chart/python` 四种；查询单元格的 `meta` 保存 engine、limit、sample_rate、title、collapsed。`querybook/webapp/const/datadoc.ts:8`、`querybook/webapp/const/datadoc.ts:23`、`querybook/server/models/datadoc.py:106`。
- 单元格与执行是多对多历史：`DataCell.query_executions` 按 id 倒序，前端可切换同一单元格的历史执行。`querybook/server/models/datadoc.py:119`、`querybook/webapp/components/DataDocQueryExecutions/DataDocQueryExecutions.tsx:47`。
- 「执行选中」在前端完成：编辑器 `getSelection()` 有值则用选区，否则用整段；Run 按钮在选区态显示 "Run Selection"。`querybook/webapp/components/DataDocQueryCell/DataDocQueryCell.tsx:450`、`querybook/webapp/components/QueryRunButton/QueryRunButton.tsx:103`。
- 执行前会做三步客户端改写：模板变量渲染 → 表采样改写 → 自动加 LIMIT；若 SELECT 无 LIMIT 且引擎开启 row_limit，会弹确认框。`querybook/webapp/components/QueryComposer/RunQuery.tsx:32`、`querybook/webapp/components/QueryComposer/RunQuery.tsx:42`、`querybook/webapp/components/QueryComposer/RunQuery.tsx:90`。
- 关键风险：LIMIT 只在前端注入，服务端不校验/不补 LIMIT；Worker 会持续读取 cursor 并上传，直到 cursor 结束或存储写入失败才 break；服务端不按 LIMIT 截断。`querybook/webapp/components/QueryComposer/RunQuery.tsx:99`、`querybook/server/lib/query_executor/base_executor.py:332`。这意味着「行数保护」是 UX 约定，不是安全边界。
- EXPLAIN 也完全在前端拼接：`getQueryAsExplain` 直接给每条语句加 `EXPLAIN` 前缀，然后走普通执行；没有后端 plan 抽象或成本估算。`querybook/webapp/lib/sql-helper/sql-lexer.ts:618`、`querybook/webapp/components/DataDocQueryCell/DataDocQueryCell.tsx:590`。
- 「Run All」不是循环调用单元格执行，而是创建一个 scheduled DataDoc 运行，把查询单元格串成 Celery chain 顺序执行；这与交互式逐单元格执行是两套逻辑。`querybook/webapp/components/DataDocRightSidebar/DataDocRunAllButton.tsx:52`、`querybook/server/tasks/run_datadoc.py:73`。

### 4.6 结果复用

- 复用方式是「选历史执行」而不是「按查询指纹命中缓存」：每个单元格保留历史执行，用户通过 ExecutionPicker/StatementExecutionPicker 切换；分享 URL 可带 `cellId` 与 `executionId` 直达某次结果。`querybook/webapp/components/ExecutionPicker/QueryExecutionPicker.tsx:27`、`querybook/webapp/components/ExecutionPicker/StatementExecutionPicker.tsx:19`、`querybook/webapp/lib/data-doc/data-doc-utils.ts:31`。
- 全仓未发现按 SQL hash 复用结果的 cache 层（`grep query_hash/result_cache` 无相关命中，`reuse` 只命中连接复用/注释），因此 Querybook 的「结果复用」本质是历史留档，不是计算复用。对 EQL 可先做历史复用，缓存命中留到后续。

## 5. 元数据与数据源管理

- 领域树是 `Environment → QueryEngine → QueryMetastore → DataSchema → DataTable → DataColumn`；执行与 DataDoc 都挂在 Environment 下，权限沿树向上归约。`docs_website/docs/overview/sharing_and_security.mdx:9`、`docs_website/docs/overview/sharing_and_security.mdx:18`、`querybook/server/models/admin.py:95`、`querybook/server/models/metastore.py:123`。
- 管理面是 Admin UI + admin API：admin 模块共 42 个端点（本次统计 `@register` 数量，文件首端点在 `querybook/server/datasources/admin.py:27`；含环境、引擎、Metastore 的 CRUD）；引擎配置分 `executor_params`（连接串、用户名、超时等）与 `feature_params`（功能开关）。`querybook/server/models/admin.py:87`、`querybook/server/models/admin.py:91`、`querybook/server/datasources/admin.py:27`。
- 引擎的表单模板由执行器的 `EXECUTOR_TEMPLATE()` 提供，用 `lib/form` 的 `StructFormField` 描述；Admin UI 动态渲染，不写死字段。`querybook/server/lib/query_executor/base_executor.py:464`、`querybook/server/lib/query_executor/executor_template/templates.py:1`。
- 元数据加载是批量的：`BaseMetastoreLoader.sync_table` 等把 schema/table/column 写入 MySQL；`update_metastore` Celery task 触发 `load_metastore(id)`。`querybook/server/lib/metastore/base_metastore_loader.py:111`、`querybook/server/tasks/update_metastore.py:5`。
- 前端表树支持两层模式：未搜索时按 schema 展开（`SchemaTableView`），搜索时走 ES 分页结果（`InfinityScroll`）。`querybook/webapp/components/DataTableNavigator/DataTableNavigator.tsx:256`、`querybook/webapp/components/DataTableNavigator/DataTableNavigator.tsx:263`。
- 补全用 ES suggest（`/suggest/<metastoreId>/tables/`），详情/样例/血缘/统计来自 metastore 模块的 41 个端点（本次统计 `@register` 数量，文件首端点在 `querybook/server/datasources/metastore.py:30`）。`querybook/webapp/resource/search.ts:51`。
- schema 级读取有 4 小时 memoize 缓存；表/列/统计没有统一缓存层，更多依赖 Redux 与 ES。`querybook/server/lib/metastore/utils.py:105`。

评价：

- Querybook 的「数据源」是 QueryEngine（连接 + 执行器），「实体树」是 Metastore 的 schema/table/column；没有 ORM 实体/属性/关联这一层，因此不能直接表达 EQL 的实体导航图。
- 对 EQL，应把元数据模型从「表树」升级为「QuerySpace/实体 → 属性 → 关联 → 集合算子」，但可复用其「引擎配置 + 元数据加载器 + 搜索/补全 + 缓存」的分层。
- 管理面配置是自由 JSON + 少量前端开关，缺少强 schema 校验；EQL 的 Dialect Profile 应该做成有版本、可校验的配置契约，而不是任意 JSON。

## 6. 查询执行链路

### 6.1 从提交到执行

1. 前端 POST `/query_execution/`，参数含 query、engine_id、metadata、data_cell_id、peer_review_params、session_props。`querybook/server/datasources/query_execution.py:115`。
2. 服务端校验引擎权限，创建 `QueryExecution` 记录；有 peer review 则状态置 `PENDING_REVIEW`，否则 `INITIALIZED`。`querybook/server/datasources/query_execution.py:141`、`querybook/server/datasources/query_execution.py:144`。
3. 无 review 时 `run_query_task.apply_async(...)` 投递 Celery；投递失败会把执行置 ERROR 并写错误。`querybook/server/datasources/query_execution.py:106`、`querybook/server/datasources/query_execution.py:188`。
4. Worker 反查执行记录，若状态不是 `INITIALIZED` 则抛 `AlreadyExecutedException`（防 worker crash 后重复执行）；用 sqlparse 切分语句范围。`querybook/server/lib/query_executor/executor_factory.py:144`、`querybook/server/lib/query_executor/executor_factory.py:152`、`querybook/server/lib/query_analysis/statements.py:14`。
5. 执行前做 Metastore 表级 ACL 检查（allowlist/denylist，支持 `*` 与前缀通配），然后构造执行器。`querybook/server/lib/query_executor/executor_factory.py:161`、`querybook/server/lib/metastore/utils.py:38`。
6. Executor 逐语句 `run → poll → 完成 → 上传结果 → 下一条`；`poll` 间隔前 15 分钟 1s，之后 10s。`querybook/server/lib/query_executor/base_executor.py:548`、`querybook/server/lib/query_executor/base_executor.py:584-588`、`querybook/server/lib/query_executor/base_executor.py:604`。
7. 每条语句结束先把状态置 `UPLOADING`，再上传 CSV、日志，最后置 `DONE` 并广播 `statement_end`。`querybook/server/lib/query_executor/base_executor.py:194`。
8. 整个查询结束置 `DONE`，广播 `query_end`；异常则写 `QueryExecutionError`（错误类型 INTERNAL/ENGINE/SYNTAX）并置 ERROR。`querybook/server/lib/query_executor/base_executor.py:95`、`querybook/server/lib/query_executor/base_executor.py:264`。

### 6.2 取消、超时与失败恢复

- 取消是 DELETE `/query_execution/<id>/`：必须本人、引擎有权限、状态仍在可取消集合；Celery `PENDING/RECEIVED/RETRY` 用 `revoke(terminate=True)`，`STARTED` 用 `task.abort()`。`querybook/server/datasources/query_execution.py:216`、`querybook/server/datasources/query_execution.py:266`。
- 运行中的取消依赖执行器 `cancel()` 调 cursor `cancel()`；通用 SQLAlchemy cursor 的 `cancel()` 是空实现（注释 "Can't cancel (yet)"）。因此只有实现了原生 cancel 的执行器（如 Presto/Trino）才能真正取消。`querybook/server/lib/query_executor/base_executor.py:597`、`querybook/server/lib/query_executor/clients/sqlalchemy.py:49`。
- 超时只有全局 Celery 软超时 2 天（`task_soft_time_limit=172800`），没有按引擎、用户、查询类型的可配置超时。`querybook/server/app/flask_app.py:86`、`querybook/server/tasks/run_query.py:54`。
- 失败恢复靠 `acks_late=True` + `AlreadyExecutedException` + 最终状态对账（若停在 INITIALIZED/DELIVERED/RUNNING 则补写 ERROR），没有应用层重试策略（仅依赖 `acks_late` 的 broker 重投）。`querybook/server/tasks/run_query.py:29`、`querybook/server/tasks/run_query.py:125`。

### 6.3 队列与并发

- 执行队列就是 Celery 默认队列：`Celery(broker=REDIS_URL)`，没有 `task_routes`、按 engine 分队列、优先级或队列位置字段；全仓 `routing_key/priority` 只出现在 beat/调度相关代码。`querybook/server/app/flask_app.py:76`、`querybook/server/app/flask_app.py:82`、`querybook/server/scheduler/__init__.py:36`。
- 并发由 Worker 启动参数决定：生产 compose 用 `-c 150` 起 worker；Celery prefetch=1、每 child 只跑一个 task，避免一个 worker 预取大量长查询。`containers/docker-compose.prod.yml:26`、`querybook/server/app/flask_app.py:83`。
- 没有「排队位置/预计等待时间」API；前端只能显示 `INITIALIZED`、`DELIVERED`、`RUNNING` 等状态。`querybook/server/const/query_execution.py:12`、`querybook/webapp/const/queryStatus.ts:11`。
- 架构判断：Querybook 用「大并发 worker + 单队列」换简单；一旦某个慢引擎占满 worker，其他引擎会被拖累，也没有租户级配额。EQL 控制台若面向多数据源，应改为按数据源/QuerySpace 的逻辑队列 + 并发上限 + 公平调度。

### 6.4 结果存储与读取

- Worker 把语句结果写成 CSV：第一行是列名，之后逐行 `cursor.get_rows_iter()`；通过 `GenericUploader` 按 `RESULT_STORE_TYPE` 路由到 db/s3/gcs/file。`querybook/server/lib/query_executor/base_executor.py:315`、`querybook/server/lib/query_executor/base_executor.py:325`、`querybook/server/lib/result_store/__init__.py:8`。
- 结果读取是 HTTP：`GET /statement_execution/<id>/result/?limit=N`，默认 1000 行，最大 50000；返回 `string[][]`，第一行仍是列名。`querybook/server/datasources/query_execution.py:399`、`querybook/config/query_result_limit.yaml:1`。
- 下载走 `/result/download/`：先校验执行权限，再对 S3 生成预签名 URL 重定向，否则回读文件流式返回 CSV。`querybook/server/datasources/query_execution.py:360`。
- 结果路径、行数、是否有日志都持久化在 `StatementExecution` 上，前端据此决定是否加载。`querybook/server/models/query_execution.py:133`、`querybook/server/models/query_execution.py:134`。
- 关键限制：结果只有首行列名，没有列类型等元数据，全部按字符串 CSV 传输；前端只能靠采样推断类型。`querybook/server/datasources/query_execution.py:440`、`querybook/webapp/lib/query-result/detector.ts:13`。

## 7. 结果集展示与可视化

- 结果表格在数据加载后由 `StatementResult` 渲染，支持分页、全屏、列筛选、排序、列统计、复制；`paginate` 与 `isPreview` 由是否已取全量决定。`querybook/webapp/components/DataDocStatementExecution/StatementResult.tsx:159`、`querybook/webapp/components/StatementResultTable/StatementResultTable.tsx:1`。
- 分页不是服务端游标，而是「提高 limit 重新取更多行」：`resultLimit` 从 1000 → 5000 → 10000 → 50000；当 `result_row_count` 大于当前 limit 时显示 Fetch More。`querybook/webapp/components/DataDocStatementExecution/StatementResult.tsx:200`、`querybook/config/query_result_limit.yaml:2`。
- 列类型由前端 detector 采样推断（string/json/url/boolean/number/id），并允许部署注入 `CUSTOM_COLUMN_DETECTORS`。`querybook/webapp/lib/query-result/detector.ts:13`、`querybook/webapp/lib/query-result/detector.ts:72`。
- 列统计（sum/average/median 等）在前端用 `bignumber.js` 计算，不依赖后端聚合。`querybook/webapp/lib/query-result/analyzer.ts:13`。
- 图表是独立单元格类型，基于 Chart.js，支持从查询单元格选源、聚合与坐标轴配置；不是结果表格的内嵌图表。`querybook/webapp/const/datadoc.ts:53`、`querybook/webapp/lib/chart/chart-data-transformation.ts:272`。
- 导出分两层：结果 CSV 下载是内置端点；Google Sheets/Python/R 等由 exporter 插件异步执行。`querybook/server/datasources/query_execution.py:360`、`querybook/server/lib/export/all_exporters.py:4`。
- 错误/日志展示：语句日志与元信息折叠展示，语法错误可映射到行列并显示建议。`querybook/webapp/components/DataDocStatementExecution/DataDocStatementExecution.tsx:186`、`querybook/webapp/components/DataDocStatementExecution/StatementLog.tsx:1`。

评价：

- 结果体验的强项是「历史执行可切换 + 多视图 + 导出」，弱项是类型系统与大数据量分页：所有值都是字符串，分页靠重复拉取 CSV 前缀。
- EQL 有 `IDataSet` 行列元数据，应该在执行结果契约里直接返回列名、类型、主键/实体来源，避免重走 Querybook 的前端类型猜测。

## 8. 查询管理与协作

- 保存：DataDoc 与单元格自动保存，无「保存按钮」强约束；查询文本 500ms 防抖后经 Socket.IO 提交，单元格插入/移动/删除也是 socket 事件。`querybook/webapp/components/DataDocQueryCell/DataDocQueryCell.tsx:71`、`querybook/server/datasources_socketio/datadoc.py:230`。
- 协作：服务端把更新广播到 DataDoc room，并维护在线用户与光标（Redis 缓存）；前端有 viewers badge、editors 列表与 access request。`querybook/server/logic/datadoc_collab.py:43`、`querybook/server/datasources_socketio/datadoc.py:146`。
- 分享：DataDoc 有 `public/archived/owner_uid`；分享 URL 可定位到 cell 与 execution；执行还有独立的 viewer 授权与 access request。`querybook/server/models/datadoc.py:37`、`querybook/webapp/lib/data-doc/data-doc-utils.ts:31`、`querybook/server/logic/query_execution_permission.py:26`。
- 搜索：DataDoc/查询/表/用户/Board 各有 ES 查询；查询搜索按 `author_uid/readable_user_ids/public` 过滤，DataDoc 同理。`querybook/server/lib/elasticsearch/search_query.py:13`、`querybook/server/lib/elasticsearch/search_datadoc.py:10`。
- 版本：不是内建版本表，而是可选 GitHub 集成：手动 commit DataDoc 为 Markdown（YAML front matter + 单元格 HTML 注释元数据），支持 versions/compare/restore。`querybook/server/datasources/github.py:94`、`querybook/server/datasources/github.py:107`、`querybook/server/lib/github/serializers.py:20`。
- 片段与模板：QuerySnippet 支持 public/golden；模板变量用 Jinja2 Sandbox 渲染，变量类型限 boolean/number/string。`querybook/server/models/datadoc.py:198`、`querybook/server/lib/query_analysis/templating.py:243`、`querybook/webapp/const/datadoc.ts:74-78`。
- 定时：`TaskSchedule` 存 cron/enabled/args，`DatabaseScheduler` 读库调度；定时 DataDoc 把多个 query cell 串成 Celery chain，前一条成功才跑下一条，可配置通知与导出。`querybook/server/models/schedule.py:45`、`querybook/server/tasks/run_datadoc.py:73`。

评价：

- Querybook 的「查询即文档」做得很完整：保存、协作、分享、搜索、版本、定时是同一套 DataDoc 模型自然长出来的。
- 但它没有「多标签查询工作台」：一个 DataDoc 是一页，只有语句级选择器，没有多文档/多查询 tab；EQL 若要求多标签，需要自建 tab/session 模型，不能照搬 DataDoc 页面结构。
- GitHub 版本方案适合「文档型」产品，不适合作为查询历史/审计的唯一版本源；EQL 应把版本放在平台侧。

## 9. 方言/引擎适配抽象

- 执行器契约有 4 个必选：`_get_client`、`EXECUTOR_LANGUAGE`（字符串或列表）、`EXECUTOR_NAME`、`EXECUTOR_TEMPLATE`；可选 `SINGLE_QUERY_QUERY_ENGINE`、`LOGGER_CLASS`、`match`。`querybook/server/lib/query_executor/base_executor.py:433`、`querybook/server/lib/query_executor/base_executor.py:452`、`querybook/server/lib/query_executor/base_executor.py:469`。
- 注册是静态列表 + 插件：内置 6 个专用执行器 + 通用 SQLAlchemy，插件通过 `executor_plugin.ALL_PLUGIN_EXECUTORS` 追加；按 `(language, executor_name)` 匹配。`querybook/server/lib/query_executor/all_executors.py:13`、`querybook/server/lib/query_executor/executors/sqlalchemy.py:50`。
- Client/Cursor 契约是「连接 + run/poll/cancel/get_columns/get_n_rows + 可选 tracking_url/percent_complete/logs」；这是把异构引擎统一成轮询模型的关键抽象。`querybook/server/lib/query_executor/base_client.py:6`、`querybook/server/lib/query_executor/base_client.py:64-92`。
- 引擎能力配置只有 `feature_params` 的 5 个前端开关：status_checker、upload_exporter、validator、row_limit、peer_review；没有「是否支持取消/进度/日志/EXPLAIN/事务/多语句」的结构化能力声明。`querybook/webapp/components/AppAdmin/AdminQueryEngine.tsx:409`、`querybook/webapp/components/AppAdmin/AdminQueryEngine.tsx:439`、`querybook/webapp/components/AppAdmin/AdminQueryEngine.tsx:444`。
- 方言能力分散在四层：CodeMirror 方言、前端 lexer 关键字、后端 SQLGlot 方言、执行器 language；四者没有共享的 profile。`querybook/webapp/lib/codemirror/codemirror-language.ts:97`、`querybook/server/const/sqlglot.py:2`、`querybook/server/lib/query_executor/executors/sqlalchemy.py:50`。
- 转换/格式化/限行由 SQLGlot 承担（`format_query`、`transform_to_limited_query`、`transform_to_sampled_query`），transpiler 也是插件；但它们是「按语言字符串查表」，不是按引擎能力协商。`querybook/server/lib/query_analysis/transform.py:10`、`querybook/server/lib/query_analysis/transpilation/base_query_transpiler.py:14`。

评价：

- Querybook 的插件边界（executor/client/result store/metastore/validator）值得借鉴，尤其「执行器只负责连接与游标，编排与状态在基类」的分层。
- 但它的「方言能力」不是一等公民：新增引擎要同时改前端两张表、后端 SQLGlot 表、执行器与 Admin 表单。EQL 需要反过来：先定义 Dialect Profile，再让编辑器、补全、校验、执行、结果渲染都消费同一份 profile。

## 10. 权限与安全

### 10.1 身份与入口

- 认证后端可配置：password、OAuth（Google/Okta/GitHub/Auth0/Azure）、LDAP；API 可用 `api-access-token` 头以用户身份调用。`querybook/config/querybook_default_config.yaml:35`、`querybook/server/app/auth/utils.py:67`。
- 全局限流 60 req/min，仅生产启用；查询引擎状态接口额外 30/min。`querybook/server/app/flask_app.py:125`、`querybook/server/app/flask_app.py:128`、`querybook/server/datasources/query_engine.py:24`。

### 10.2 权限树

- 权限根是 Environment：`public`（所有登录用户）、`hidden`（无权时不可见）、`shareable`（环境内 DataDoc/查询默认公开）。`querybook/server/models/environment.py:26`、`querybook/server/models/environment.py:35`。
- DataDoc 权限：owner 或 public 可读，写权限来自 `DataDocEditor` 的 read/write 记录。`querybook/server/logic/datadoc_permission.py:27`、`querybook/server/logic/datadoc_permission.py:43`。
- 执行权限：先按执行所属环境判断，`shareable` 环境放行；否则要求 owner、所属 DataDoc 可读、或显式 viewer。`querybook/server/app/auth/permission.py:322`、`querybook/server/logic/query_execution_permission.py:9`。
- 结果/日志接口在读取前再次校验执行权限；下载同样先校验再重定向。`querybook/server/datasources/query_execution.py:374`、`querybook/server/datasources/query_execution.py:435`。

### 10.3 表级 ACL 与行级安全

- 执行前用 `process_query` 抽取表名，再按 Metastore 的 `acl_control` 做 allowlist/denylist；支持 `schema.*` 与前缀通配。`querybook/server/lib/query_executor/executor_factory.py:165`、`querybook/server/lib/metastore/utils.py:43`。
- 这是「表级、执行前」检查：不做列级脱敏、不做行级过滤、不重写 SQL、不校验函数/操作符；如果 metastore 未配置 ACL，则直接跳过。`querybook/server/lib/query_executor/executor_factory.py:169`。
- 全仓未发现 row-level security / RLS / 行级策略实现（本次 `grep` 未命中相关关键字）；权限模型止于 Environment/DataDoc/QueryExecution/Metastore 表。对 EQL 而言，Nop 的租户、逻辑删除、行级权限必须在 EQL 编译期注入，不能依赖 Querybook 式表级 ACL。

### 10.4 审计与评审

- 事件日志：`event_log` 记录 uid/event_type/event_data，`register()` 默认对大多数非 admin API 记录请求参数（查询文本等大字段会被截断，但可能仍保留片段）；可替换为 console/db/null logger。`querybook/server/models/event_log.py:11`、`querybook/server/app/datasource.py:78`、`querybook/server/lib/event_logger/loggers/db_event_logger.py:1`。
- 管理审计：`admin_audit_log` 记录 item_type/item_id/op/uid/log，通过装饰器包在 admin API 上。`querybook/server/models/admin.py:216`、`querybook/server/datasources/admin_audit_log.py:22`。
- Peer Review：可按引擎开关；提交时进入 `PENDING_REVIEW`，reviewer approve 后才真正投递执行，reject 则终止。`querybook/server/datasources/query_execution.py:144`、`querybook/server/datasources/query_execution.py:779`、`querybook/server/lib/query_review/base_query_review_handler.py:57`。
- 危险语句：前端仅在 `DROP` 时弹确认，服务端不限制语句类型；执行器对任意 SQL 调 `cursor.run`。`querybook/webapp/components/QueryComposer/RunQuery.tsx:163`、`querybook/server/lib/query_executor/base_executor.py:652`。
- 审计缺口：事件日志是普通表，可被有库权限的人修改；没有签名/链式哈希/保留策略；查询文本作为 API 参数直接落日志，敏感信息可能二次扩散。`querybook/server/models/event_log.py:11`。

## 11. 测试与工程质量

- 后端测试：`pytest`，`testpaths = querybook`；本次统计 48 个 `test_*.py`，覆盖 permission、query executor base、SQL 解析/限行/模板、metastore loader、export、ES 等。`setup.cfg:41`、本次 `find querybook/tests -name 'test_*.py' | wc -l`。
- 前端测试：Jest，rootDir 为 `querybook/webapp`；本次统计 18 个 `*.test.ts(x)`，集中在 SQL helper、chart、data-doc、utils，没有组件级/页面级 E2E。`jest.config.js:14`、本次 `find querybook/webapp/__tests__ \( -name '*.test.ts' -o -name '*.test.tsx' \) | wc -l`。
- CI：PythonTests 跑 `run_test --python`，NodeTests 跑 `run_test --node`，另有 lint workflow 跑 pre-commit；没有 Playwright/Cypress/Selenium 依赖。`.github/workflows/pythontests.yml:42`、`.github/workflows/nodetests.yml:38`、`.github/workflows/lint.yml:1`。
- 代码风格：black + flake8 + prettier 由 pre-commit 强制；flake8 行宽 88，忽略 E501 等。`.pre-commit-config.yaml:12`、`.pre-commit-config.yaml:26`、`setup.cfg:1`。
- 类型检查：`tsc --noEmit` 脚本存在，但 `noImplicitAny: false`、无 `strict`，类型安全是渐进式而非强约束。`package.json:13`、`tsconfig.json:4`。
- 数据库迁移：Alembic，49 个版本文件，说明 schema 演进有历史包袱但也有可追踪路径。本次 `ls querybook/migrations/versions | wc -l`。
- 结构：`datasources`（HTTP/权限/参数）→ `logic`（事务/领域）→ `models` 分层清楚；执行器/结果存储/Metastore 都通过 `import_helper` 做「内置 + 插件」注册。`querybook/server/lib/utils/import_helper.py:1`。
- 风险：部分文件过大（`query_execution.py` 798 行、`DataDocQueryCell.tsx` 1220 行、`StatementResult.tsx` 537 行），逻辑与展示耦合；admin API 42 个端点集中在一个模块，测试以单元为主，缺少执行链路的集成测试。`querybook/server/datasources/query_execution.py:1`、`querybook/webapp/components/DataDocQueryCell/DataDocQueryCell.tsx:1`。

## 12. 对 Nop EQL 在线编辑器的启示

### 12.1 Adopt（直接借鉴）

| # | 借鉴点 | Querybook 证据 | 对 EQL 的落法 |
|---|---|---|---|
| A1 | 查询执行记录模型：QueryExecution + StatementExecution + Error + Metadata | `querybook/server/models/query_execution.py:21`、`querybook/server/models/query_execution.py:114` | EQL 控制台建 `EqlExecution`/`EqlStatement`，保存原始 EQL、编译后 SQL、参数、数据源、行列数、耗时、错误码 |
| A2 | 异步执行 + 事件推送：提交即返回，Worker 推状态 | `querybook/server/datasources/query_execution.py:106`、`querybook/server/lib/query_executor/base_executor.py:75` | 用 Nop Job/Task 异步执行，前端订阅执行事件；Web 进程不阻塞 |
| A3 | 结果对象存储 + 读取窗口 | `querybook/server/lib/result_store/__init__.py:8`、`querybook/config/query_result_limit.yaml:1` | 结果落对象存储/临时表，按页读取；不要一次性把全部行塞进 GraphQL/REST |
| A4 | 单元格执行历史 + 切换复用 | `querybook/server/models/datadoc.py:119`、`querybook/webapp/components/ExecutionPicker/QueryExecutionPicker.tsx:27` | 每个 tab/查询保留执行历史，可回看任意一次结果；先做历史复用，后做 hash 缓存 |
| A5 | 引擎/执行器插件注册 | `querybook/server/lib/query_executor/all_executors.py:13`、`querybook/server/lib/query_executor/base_executor.py:478` | EQL 方言/数据源适配器按 `(dialect, executor)` 注册，内置 + 扩展 |
| A6 | 权限树 + 执行级权限 | `querybook/server/models/environment.py:26`、`querybook/server/app/auth/permission.py:322` | 数据源/QuerySpace → 执行 → 结果三层鉴权；结果下载单独校验 |
| A7 | 双层 lint：本地即时 + 服务端语义 | `querybook/webapp/hooks/queryEditor/useLint.tsx:147`、`querybook/server/datasources/query_execution.py:744` | CodeMirror 本地关键字/语法提示 + 后端 EQL parse/compile 诊断（行列定位） |
| A8 | 嵌入协议：postMessage 的 SET/SEND/SUBMIT | `querybook/webapp/components/EmbeddedQueryPage/EmbeddedQueryPage.tsx:44`、`docs_website/docs/integrations/embedded_iframe.mdx:12` | 若 EQL 编辑器要嵌到 flux 页面/其他系统，可定义同类最小消息协议，而不是暴露内部 API |

### 12.2 Adapt（改造后使用）

| # | Querybook 做法 | 为什么不能照搬 | EQL 应怎么改 |
|---|---|---|---|
| B1 | DataDoc 多单元格 notebook | Querybook 是文档模型，没有多标签；单元格间无变量/结果依赖表达 | 采用「工作台 + 多 tab」，每个 tab 一个查询草稿；notebook 可作为可选视图，不作为唯一模型 |
| B2 | 前端 LIMIT 注入 | 只是 UX，服务端不强制；大结果仍会全量落盘 | 服务端按数据源配置强制 maxRows/maxBytes/timeout，超限截断并标注 |
| B3 | Celery 单队列 + 大并发 worker | 无按引擎隔离/公平调度/队列位置 | 按数据源/QuerySpace 建逻辑队列，设并发上限、排队位置、取消与超时 |
| B4 | 结果 CSV + 前端类型猜测 | EQL 有 IDataSet 行列元数据，猜测会丢类型/精度 | 结果契约直接返回列名、类型、可空、实体/属性来源；CSV 仅作导出格式 |
| B5 | feature_params 五个开关 | 不是结构化能力声明，前端/后端各自解释 | 定义 Dialect Profile：关键字、补全模式、格式化、校验器、执行器、结果渲染、能力位（cancel/explain/transaction） |
| B6 | GitHub 手动 commit 做版本 | 依赖外部仓库、手动、与执行历史脱节 | 平台内建 revision/变更日志；GitHub 仅作可选导出 |
| B7 | Jinja2 模板变量 | 与 EQL 参数体系、Nop 表达式不匹配，且有沙箱/转义成本 | 用 EQL 参数/marker + Nop 表达式；模板仅作可选语法糖 |
| B8 | 前端 EXPLAIN 前缀 | 不是真 plan，无法做成本/索引/权限预检 | 调 EQL 编译器的 explain/validate API，返回计划树与诊断，不伪造 SQL |
| B9 | ES 作为补全/搜索唯一来源 | 权限副本（readable_user_ids）会滞后；多一套运维 | 元数据走 Nop ORM/元数据服务；搜索可选，权限以 DB 实时为准 |

### 12.3 Reject（明确不采用）

| # | 不采用 | 理由 | 证据 |
|---|---|---|---|
| R1 | Python/Flask/Celery/Redis/ES 技术栈 | 与 Nop Java/GraphQL/Job 体系重复，运维面大 | `docs_website/docs/overview/architecture.mdx:20` |
| R2 | CodeMirror 5/6 双栈与 `react-codemirror2` | 历史迁移包袱，Flux 已是 CM6 | `package.json:58`、`package.json:93` |
| R3 | 通用 SQLAlchemy 执行器作为 EQL 执行通道 | 阻塞式执行、cancel 空实现、无进度/日志 | `querybook/server/lib/query_executor/clients/sqlalchemy.py:46`、`querybook/server/lib/query_executor/clients/sqlalchemy.py:49` |
| R4 | 表级 allow/deny 作为唯一安全边界 | 无行级/列级控制，无法满足 Nop 租户与数据权限 | `querybook/server/lib/metastore/utils.py:43` |
| R5 | 前端 DROP 确认作为危险语句防护 | 服务端不限制语句类型，绕过前端即可执行 | `querybook/webapp/components/QueryComposer/RunQuery.tsx:163`、`querybook/server/lib/query_executor/base_executor.py:652` |
| R6 | 以 DataDoc 文档页替代查询工作台 | 无 tab、无 session、跨文档复用弱 | `querybook/server/models/datadoc.py:25` |

### 12.4 建议的落地顺序（与 `ai-dev/backlog/eql-online-editor-roadmap.md` 对齐）

1. 先定契约：`EqlDialectProfile` + `EqlExecutionResult`（列元数据、行数、耗时、诊断）+ 执行状态机；不要先写编辑器。
2. 再做后端：`/eql/metadata`、`/eql/validate`、`/eql/execute`、`/eql/executions/{id}`、`/eql/executions/{id}/result`、`/eql/executions/{id}/cancel`，全部走 Nop 权限与审计。
3. 然后前端：复用 Flux `code-editor` 的 CM6 能力，补 entity-path 补全与 profile 驱动的关键字/格式化；结果面板按列元数据渲染，支持分页/导出。
4. 最后做工作台：多 tab、历史、收藏、分享；notebook 作为增强视图，而不是第一版必须。
5. 方言配置只保留一份 profile，由前端/后端/元数据/校验共同消费；新增 EQL 方言或数据源只改 profile + adapter，不改 UI 分支。

## 13. 关键证据清单

| # | 证据（相对 Querybook 仓库根） | 说明 |
|---|---|---|
| 1 | `querybook/server/app/flask_app.py:78-79` | Celery broker/backend 均为 Redis |
| 2 | `querybook/server/app/flask_app.py:83-92` | prefetch=1、soft timeout=172800s、visibility timeout |
| 3 | `querybook/server/app/flask_app.py:146-150` | Socket.IO 使用 Redis message_queue |
| 4 | `querybook/server/app/datasource.py:51-57` | `/ds` API 注册与统一鉴权入口 |
| 5 | `querybook/server/datasources/query_execution.py:115` | 创建执行、权限校验、投递任务 |
| 6 | `querybook/server/datasources/query_execution.py:216` | 取消执行：owner 校验、revoke/abort |
| 7 | `querybook/server/datasources/query_execution.py:399` | 结果读取：limit 上限、权限校验、CSV |
| 8 | `querybook/server/datasources/query_execution.py:744` | 服务端校验入口，依赖 feature_params.validator |
| 9 | `querybook/server/tasks/run_query.py:23` | AbortableTask + acks_late + 软超时处理 |
| 10 | `querybook/server/tasks/run_query.py:90` | 执行循环：abort → poll → sleep |
| 11 | `querybook/server/lib/query_executor/base_executor.py:315` | 结果逐行写 CSV 并上传 |
| 12 | `querybook/server/lib/query_executor/base_executor.py:548-602` | 执行器 start/poll/cancel 生命周期 |
| 13 | `querybook/server/lib/query_executor/clients/sqlalchemy.py:49` | 通用 SQLAlchemy 不支持 cancel |
| 14 | `querybook/server/lib/query_analysis/statements.py:14` | sqlparse 语句切分 |
| 15 | `querybook/server/lib/query_executor/executor_factory.py:161-183` | 执行前 Metastore 表级 ACL |
| 16 | `querybook/server/lib/metastore/utils.py:43-47` | allowlist/denylist 表级判定 |
| 17 | `querybook/server/models/environment.py:26-39` | public/hidden/shareable 三态 |
| 18 | `querybook/server/app/auth/permission.py:323-335` | shareable 环境或执行级授权 |
| 19 | `querybook/server/logic/query_execution_permission.py:9` | owner/DataDoc/viewer 三条访问路径 |
| 20 | `querybook/server/models/query_execution.py:133-134` | 结果行数/路径持久化 |
| 21 | `querybook/webapp/lib/codemirror/codemirror-language.ts:97-110` | 9 个硬编码 CodeMirror 方言 |
| 22 | `querybook/server/const/sqlglot.py:2-14` | 12 个后端 SQLGlot 方言 |
| 23 | `querybook/webapp/hooks/queryEditor/useCodeAnalysis.ts:19` | Web Worker 分析 + 500ms 防抖 |
| 24 | `querybook/webapp/components/DataDocQueryCell/DataDocQueryCell.tsx:450` | 选区优先的执行输入 |
| 25 | `querybook/webapp/lib/sql-helper/sql-lexer.ts:618-624` | EXPLAIN 由前端拼接 |

## 14. Open Questions

- [ ] Querybook 的单队列 Celery 在生产规模下的实际排队/超时表现如何？仓库没有压测或队列指标，需运行态数据才能判断是否需要按数据源分队列。
- [ ] 结果 CSV 全量落盘的存储成本与 50k 行前端窗口是否满足 EQL 宽表场景？需要按 EQL 典型结果宽度做基准。
- [ ] Nop EQL 的 `IDataSet` 列元数据如何映射到前端表格与导出？是否需要定义跨前后端的列类型协议（含主键/实体来源/脱敏标记）？
- [ ] EQL 的「实体树」与 Querybook 的 schema/table 树差异较大：是按 QuerySpace 懒加载实体，还是一次性下发 ORM 模型？需要评估大模型体积。
- [ ] EQL 校验应复用编译器 `parse/compile` 的哪一层，才能返回稳定的行列定位与错误码？是否需要在编译前做只读/危险语句静态检查？
- [ ] 多语句/事务语义：EQL 当前编译期拒绝 commit/rollback（`ai-dev/analysis/2026-10/eql-online-editor-survey/2026-10-18-flux-sql-editor-current-state-and-eql-gap.md:78`），控制台是否允许一个请求多条语句？若允许，结果如何按 statement 分组？
- [ ] 多标签工作台的会话状态放前端（Redux/localStorage）还是后端（草稿表）？是否需要跨设备恢复？
- [ ] 历史与审计的保留策略：查询文本、结果、错误日志分别保留多久？是否需要可配置的脱敏/截断？
- [ ] 行级/列级权限由 EQL 编译期注入还是执行期改写 SQL？两种方案对缓存、EXPLAIN、审计的影响不同。
- [ ] 是否保留 notebook（单元格 + 富文本 + 图表）作为 EQL 工作台的一种视图，还是第一版只做「查询 tab + 结果」？这会影响数据模型与前端壳层。
