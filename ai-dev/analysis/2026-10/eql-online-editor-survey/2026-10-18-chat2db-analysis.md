# Chat2DB 深度调研报告

> Status: resolved
> Date: 2026-10-18
> Scope: Chat2DB 社区版源码架构 / SQL 编辑器 / 执行链路 / 多数据库与 AI 扩展
> Conclusion: 最大可借鉴点是「后端多阶段补全流水线 + 声明式方言能力配置 + 统一 candidate/trace 契约」这套组合——它把"编辑器智能"从散落在前端的关键字表，变成由元数据缓存驱动、每阶段可观测、每方言可替换的服务器能力；而 EQL 只需一份 ANTLR 语法即可覆盖全部方言，比 Chat2DB 为 MySQL 单方言写 64 个类更划算。

---

## 1 背景与调研问题

Nop 平台正在为 EQL（`nop-persistence/nop-orm-eql`，标准 SQL + 实体属性导航自动 JOIN）规划"在线编辑 + 测试"工具，形态类似在线版 DBeaver：数据源/实体树、编辑器补全、执行、结果表格、历史、导出、EXPLAIN/校验、多标签，方言能力尽量配置化。

EQL 与通用 SQL 客户端的关键差异决定调研视角：

- EQL 的"元数据"是 ORM 模型（实体/属性/关联），不是 JDBC 的 catalog/schema/table，补全需要的是**实体属性导航**而非纯文本表名匹配；
- EQL 背后只有 Nop ORM 一条执行栈，**不存在 36 种 JDBC 方言**，方言差异仅体现在生成的目标 SQL 与少量函数；
- 执行对象虽内部受控，仍需只读、超时、行数上限、权限裁剪等护栏。

因此不追求功能对照表，而回答五个架构问题：

1. 成熟 SQL 客户端的**编辑器补全**如何分层、如何被元数据驱动？
2. **多数据库差异**如何建模成配置而不是 if-else 蔓延？
3. **执行链路**（协议、分页、流式、取消、错误）有哪些坑已被踩过？
4. **结果集与查询管理**的边界在哪里（哪些该后端做、哪些该前端做）？
5. 哪些设计在 EQL 语境下应当**照搬 / 改造 / 拒绝**？

调研对象为本地完整 clone：`/Users/abc/sources/Chat2DB`，HEAD `7379139e`（2026-07-28）。全程只读源码，未构建、未联网。文中 `path:line` 均为仓库内相对路径的实际行号。

---

## 2 仓库与产品概览

### 2.1 仓库形态

单一 monorepo，前后端并列：

- `chat2db-community-client/`：React + Umi 4 前端（`chat2db-community-client/package.json:109`），版本 5.3.0（`package.json:4`）；
- `chat2db-community-server/`：Maven 多模块后端，9 个模块（`chat2db-community-server/pom.xml:17-25`）：`bom / domain / storage / start / tools / web / jcef / plugins / spi`；
- `docker/`、`jpackage/`、`script/`：三种分发形态的打包脚本；
- `frontend-donor.json:3-5` 记录前端是从 `Chat2DB-Enterprise` 仓库 `dcc81b58` 同步的 donor 副本——这解释了代码里大量 Pro 功能残留（`/api/v1/ai/embedding/*` 在社区版无实现）。

### 2.2 部署形态（四种，共用一套业务代码）

| 形态 | 前端载体 | 前后端通道 |
|---|---|---|
| Desktop | JCEF 内嵌浏览器 | `window.javaQuery` → `@JcefAction(method="client-command")` |
| Web / Docker | Thymeleaf 托管 SPA | HTTP REST（`/api/**`）+ 本地文件存储 |
| CLI | 无 | `/api/cli/v1/**` + Bearer token |
| MCP | 无 | `/api/mcp` + 桌面 MCP token |

分叉点在前端一处：`src/service/base.tsx:99-105`，`isDesktop` 为真走 `commandLineRequest`，否则走 `umi-request` HTTP。这直接决定了执行链路是流式还是一次性（§6.1）。

### 2.3 支持的数据库

`chat2db-community-server/chat2db-community-plugins/` 下有 **36 个**数据库插件目录，每个通过 `META-INF/services/ai.chat2db.spi.IPlugin` 注册；README 宣称 30+ 种（`README_CN.md:22`）。

### 2.4 License 与社区版边界

这是本次调研**最硬的约束**：

- 5.3.0 起不再是 Apache-2.0，而是"修改版 Apache 2.0 + 附加条款"（`LICENSE:3-4`），SPDX 标识 `LicenseRef-Chat2DB`（`LICENSE:12`）；
- 允许个人自用、组织**内部使用**、源码形式再分发；**禁止**对外提供 External Product/Service、托管交付、白标/OEM、Object 形式分发（`LICENSE:15-24`、`:50-64`）；
- "Community Core Capabilities" 覆盖数据库连接、元数据浏览、SQL 执行、数据编辑、导入导出、AI、CLI、MCP、HTTP API、插件、可复用模块（`LICENSE:117-122`）——几乎堵死"把 Chat2DB 当组件嵌入另一产品"的路径。

社区版边界在代码里也有痕迹：`src/community-stubs/` 只有 4 个文件（`Price.tsx` + `invitation/license/pricing`），说明商业化裁剪主要发生在构建期；而 AI 的 embedding/数据集合（`/api/v1/ai/embedding/**`，`src/service/aiDataCollection.ts:48-108`）在后端没有任何实现，属典型"前端保留、后端剥离"。

---

## 3 架构总览

### 3.1 后端分层

```
web (Controller/Aspect/Converter/DTO)
  → domain-api (service 接口 + model + DBConfig)
    → domain-core (service 实现、缓存、补全引擎)
      → spi (方言契约 + DefaultSQLExecutor + ConnectInfo/ConnectionPool)
        → plugins/* (每库一套实现)
storage (本地 JSON 文件持久化)   jcef (桌面端命令处理器)
```

Controller 只做参数转换与 `DataResult/ListResult` 包装：`DbDmlController.java:50-56` 即 `dbWebConverter.dmlExecutionRequest(request)` → `dmlExecutionService.execute(...)` → `dbWebConverter.dto2response(...)`。业务全部下沉 domain。

### 3.2 通信协议

两套并存：

1. **HTTP REST**（Web/CLI/MCP）：41 个 Controller，按资源切分 `/api/rdb/dml`、`/api/rdb/table`、`/api/sql_parser`、`/api/connection`…（`DbSqlParserController.java:36`、`DbDmlController.java:30`）。
2. **JCEF IPC**（Desktop）：请求体 `{requestUrl, method:"client-command", message, uuid}`（`src/jcef/base.ts:1-7`），后端以 `@JcefAction(value="sql-execute", method="client-command")` 注册（`SqlExecuteHandler.java:13-20`）。

Web 形态也复用同一 IPC 语义：`ConsoleHelper` 通过**标准输入逐行读消息**（`ConsoleHelper.java:100-108`）实现行长协议，`WebJcefServerBridge` 把 `executeSql` 直接桥到 `SqlExecutionManager`（`WebJcefServerBridge.java:55-70`）。这是为"Java 进程被父进程托管"设计的，属历史包袱，不是可借鉴点。

### 3.3 状态管理

- 前端：zustand + `persist`，workspace 按 slice 拆分（`src/store/workspace/store.ts:7-28`），只持久化 `layout / workspaceTabList / activeConsoleId / splitLayout / recentlyClosedWorkspaceTabs`（`:40-60`），函数字段序列化时剔除（`utils/workspaceTabPersistence.ts:7-30`）。
- 后端：**无会话状态**。连接上下文放 `ThreadLocal<ConnectInfo>`（`Chat2DBContext.java:27`），由 `ConnectionInfoAspect` 在请求入口 `putContext`/`removeContext`；进入执行线程池需手工搬运（`SqlExecutionJob.java:96-104`）。

### 3.4 扩展点

| 扩展点 | 位置 | 粒度 |
|---|---|---|
| `IPlugin` | `spi/IPlugin.java:11-130` | 数据库整体（连接/元数据/DDL/账号/存储过程） |
| `ISqlSyntaxPlugin` | `MysqlSyntaxPlugin` 实现 | 语法解析 + 补全 provider |
| `ISqlCompletionProvider` / `ISqlCompletionDialect` | `spi/ISqlCompletionProvider.java:10-25` | 补全实现 |
| `ISQLDialect` | `spi/ISQLDialect.java:12-228` | 词法/块结构关键字集合 |
| `ISqlBuilder`（identifier/dql/dml/ddl 四段） | `spi/ISqlBuilder.java:10-32` | SQL 生成 |
| `IDbMetaData` | `spi/IDbMetaData.java:37-100` | 元数据读取 |
| `IValueProcessor` / `ISQLIdentifierProcessor` | `IPlugin.java:47-64` | 值渲染 / 标识符引用 |

`ISqlBuilder` 用"四段 builder + 默认抛 `UnsupportedOperationException`"表达能力缺失（`:16-30`），比返回 null 更早暴露问题。

---

## 4 SQL 编辑器实现

### 4.1 编辑器内核

Monaco Editor 0.52（`package.json:98`），组件树在 `src/components/SQLEditor/`：`editor/MonacoEditor`（受控封装、diff 视图）、`editor/SQLEditor`、`editor/SQLEditorWithOperation`（工具栏/执行/格式化外壳）、`core/initializeEditor.ts`（统一注册 completion/hover/snippets/format/theme，`:25`）、`components/OperationLine`、`ParameterHintWidget.ts`、`MonacoEditorErrorTips`。

### 4.2 补全：两代实现并存

这是全仓最有价值的部分。

**第一代（前端本地）**：`core/completionProviderManager.ts` 用多个 `registerCompletionItemProvider('sql', ...)` 分别提供内置关键字（`:1567`）、内置函数（`:1639`）、database（`:1675`）、schema（`:1713`），外加 `core/registerSnippets.ts` 的片段补全。触发字符固定 9 个：`[' ', '.', ',', '(', ')', '[', ']', '{', '}']`（`:47`）。

**第二代（后端驱动）**：同文件另有 `cachedTipsProvider`（`:1358`）与 `tipsProvider`（`:1387`），异步调 `queryTips`（`service/sqlParser.ts:67-110`，POST `/api/sql_parser/context/tip`）。请求支持"全文 + cursor 偏移"或"`beforeSql`/`afterSql` 窗口"两种形态（`FetchTipsParams`，`:86-99`），后者避免大 SQL 全量上传；响应带 `replaceStart/replaceEnd`，前端据此做**精确替换区间**而非补全当前单词。

切换由能力表控制：`isBackendCompletionDatabaseType`（`core/sqlCompletionModelMode.ts:16-18`），而 `databaseCapabilities.backendCompletionSupported` 只有 `[MYSQL]`（`src/constants/databaseCapabilities.ts:40`）。**36 个数据库里只有 MySQL 走新补全**，其余仍走本地关键字 + 通用引擎。这是本报告最重要的"现实校准"：新架构并非全量落地，而是单方言试点。

### 4.3 后端补全流水线（11 阶段）

`SqlCompletionPipeline.complete` 串起 11 个可替换组件，每步写 trace（`spi/parser/completion/SqlCompletionPipeline.java:20-52`）：

```
request → input(清洗) → statementWindow(定位语句) → cursor(游标上下文)
→ dummySql(占位 SQL) → localContext(局部作用域) → c3(语法候选)
→ ruleEvidence(规则证据) → slots(槽位分类) → intents(意图解析)
→ candidatePlan(候选编排) → result(展示处理)
```

组件契约是 11 字段 record，并用 `Objects.requireNonNull` 强制全实现（`SqlCompletionDialectComponents.java:17-42`）；流水线状态不可变，逐步 `withXxx` 前进（`SqlCompletionPipelineState.java:1-127`）。

`c3` 阶段用 ANTLR ATN 的 caret 候选算法：`CodeCompletionCore.java`（650 行，`:10-57`）从 parser 的 ATN 逐状态推导"光标处可接哪些 token/rule"，按 `preferredRules` 过滤。这是 antlr4-c3 思路的自研移植，**与 EQL 的 ANTLR 语法天然契合**。

方言实现密度值得单独记一笔：MySQL 的 `completion/` 目录有 **64 个 Java 文件**，分包 `analysis / c3 / catalog / context / dummy / evidence / hint / intent / locate / plan / presentation / provider / resolver / slot / util / value`，装配点集中在 `MysqlSqlCompletionDialect.java:25-45`——一行一个组件，可读性极好，代价是单方言 64 个类。

### 4.4 元数据如何进入补全

`SqlCompletionMetadataProviderAdapter` 把 `IDbMetaData` 适配成候选源，按类型 switch（`:53-66`）：`DATABASE/SCHEMA/TABLE/VIEW/COLUMN/FUNCTION/PROCEDURE/TRIGGER/PARAMETER`。关键在缓存：表和列走 `MemoryCacheManage.computeIfAbsent`，key 是 `(dataSourceId, databaseName, schemaName[, tableName])`（`:97-99`、`:121-123`）。这把"每敲一键打一遍 JDBC 元数据"降到"每作用域一次"，是补全低延迟的根本原因。

候选 `SqlCompletionCandidate` 携带 `label / insertText / detail / comment / sortRank / objectType / datasourceName`；表注释、列注释直接进 `comment`（`SqlCompletionConverter.java:92`、`:125`、`:158`）。响应含 `editorHints`（`SqlCompletionResponse.java:16`）驱动编辑器辅助提示，并有 `status`（SUCCESS/EMPTY/UNSUPPORTED/REJECTED）与 `reasonCode`（`:16-58`）——**能力缺失是一等公民**，不是空数组。

### 4.5 格式化

实际格式化在后端：`DbSqlServiceImpl.format` 按 dbType 选 sql-formatter 的 Dialect（MySQL/PostgreSql/PlSql/TSql/Db2/MariaDb，`:44-75`），并对 PostgreSQL 纯 INSERT 脚本用特殊 `FormatConfig`（`:37-40`、`:78-81`）；前端 `helper/utils.ts:16-31` 调 `/api/sql/format`。

需要指出：`core/registerSQLFormat.ts:8-19` 是**未实现的桩**——返回 `'// Formatted code\n' + model.getValue()`。真正格式化走按钮/快捷键，不走 Monaco 的 Format Document。这是应被 EQL 避免的"两套入口不一致"。

### 4.6 多语句 / 多结果

- 后端按方言切分：`SqlSplitProcessor` 支持自定义 delimiter（默认 `;`，`:26-30`），遇 `DELIMITER xxx` 动态切换（`:171-190`），Oracle 分支单独处理（`:354+`）；块结构识别靠 `ISQLDialect` 关键字集合（§9）。
- 结果身份是三元组 `(statementSequence, resultSetId, streamResultId)`：每语句递增 `statementSequence`（`DefaultSQLExecutor.java:665-676`），每条结果带 `resultSetId`，流式再叠加 `streamResultId`（`:1148-1160`）。
- 前端按三元组构造 `resultKey` 并维护结果列表（`SQLExecute/index.tsx:398-450`），结果展示为独立 tab，支持"保留历史 / 重置会话"两种模式（`service/sqlExecutionBatch.ts:20-45`）。

---

## 5 元数据与连接管理

### 5.1 元数据接口

`IDbMetaData` 是 30+ 方法的宽接口（`spi/IDbMetaData.java:37-100`）：`databases/schemas/tables/views/columns/indexes/functions/procedures/triggers/types/tableDDL`，外加分页变体 `TablesPageResponse tables(TablesPageRequest)`。宽接口的代价是每插件都要实现大半，收益是调用方无需类型判断。

对外暴露为 `/api/rdb/table/list`、`/api/rdb/ddl/column_list`、`/api/rdb/ddl/database_schema_list` 等（`src/service/sql.ts:68-233`），以及聚合端点 `/api/sql_parser/get_keywords` 一次返回 databases/schemas/tables/views/functions/procedures（`SqlKeyword.java:19-24`）——前端初始化补全上下文只需一次请求。

### 5.2 连接池

自研极简池，**每连接 key 最多 2 条连接**（`ConnectionPool.java:20`），三级校验：`isValid(2s)` → 失败降级探针 SQL（Oracle 用 `SELECT 1 FROM DUAL`，`:24-27`、`:163-173`），30 秒内复用过的连接跳过校验（`:22`）；每分钟一次清理线程（`:38-46`），并用 `CONNECTION_GENERATIONS` 做"配置变更后整体失效"（`:210-222`）。

对 EQL 的启示：连接池不是重点（Nop ORM 已有），但**"配置变更必须让缓存/连接同时失效"**必须显式建模，否则改数据源后补全拿到旧 metadata。

### 5.3 凭据

数据源密码与 AI API Key 用 AES-256-GCM 加密落盘（`AesGcmUtil.java:25-32`），每次随机 12 字节 nonce、16 字节 tag，并用不同 AAD 区分用途（`chat2db-community-datasource-password` / `chat2db-community-ai-model-api-key`，`:32-35`），两类密文不能互相顶替；密钥来自环境变量/文件/property（`:17-20`），Web 模式缺密钥直接启动失败。

---

## 6 查询执行链路

### 6.1 两套语义完全不同的执行路径

| | Desktop | Web |
|---|---|---|
| 入口 | `sql-execute`（JCEF）→ `SqlExecutionManager.start` | `POST /api/rdb/dml/execute` |
| 返回 | 立即返回 `executionId`，结果靠事件推送 | 一次性返回 `List<ExecuteResultResponse>` |
| 前端 | `startSqlExecution` + `onSqlExecutionEvent`（`service/sqlExecutionStream.ts:85-121`） | `executeSqlServer.executeSql`（`hooks/useSqlExecutor.ts:196-207`） |
| 取消 | 服务端 `Statement.cancel()` | **仅前端 abort HTTP** |

事件模型完整：`started / statementStarted / resultStarted / rows / updateCount / message / resultFinished / statementFinished / finished / failed / cancelled`，每条带 `executionId / eventSequence / statementSequence / resultKey`（`service/sqlExecutionStream.ts:11-29`）；`rows` 是真正的行级流式——服务端每攒满 200 行 flush 一次，末尾再 flush 残批（`DefaultSQLExecutor.java:1224`、`:1259-1265`）。

Web 侧没有等价物：`isDesktop` 为假时直接 `executeSQL(executeSqlParams).then(...)`（`SQLExecute/index.tsx:676`、`:693`），取消是 `abortRequest()`（`hooks/useSqlExecutor.ts:223-235`）——**客户端断开不会终止服务端语句**。EQL 工具不应复制。

### 6.2 执行器与调度

`SqlExecutionManager` 三层线程池：执行池 4/16、队列 64（`:37-44`）；取消池 1/4（`:45-51`）；单线程 `outputPoller` 每 200ms 轮询所有 job 待发消息（`:56`、`:118-122`）。

并发控制用 **lane 锁**：`laneExecutions.putIfAbsent(laneId, executionId)`，laneId 优先 `consoleId`，否则退化为 `datasource:db:schema`（`WebJcefServerBridge.java:100-107`）；同一编辑器同时只允许一个执行，冲突抛 `sqlExecution.alreadyRunningInEditor`（`SqlExecutionManager.java:82-87`）。比全局互斥精细，比完全放开安全。

`SqlExecutionJob` 持 `volatile Statement currentStatement`，取消时先 `statement.cancel()` 再 `future.cancel(true)`（`:143-158`）——两个动作都做，是对 JDBC 驱动取消语义不统一的务实妥协。

### 6.3 分页：JDBC 游标跳过，不改写 SQL

`pageSize` 默认 `MAX_PAGE_SIZE`，`offset=(pageNo-1)*pageSize`（`DefaultSQLExecutor.java:837-839`）；对 select 设 `stmt.setMaxRows(offset+count)`（`:998-1006`），取值用 `while(rs.next())` + `if (rowNumber++ < offset) continue;`（`:322-358`）。

优点：对任意 SQL 都正确，不需解析改写，不破坏用户语义。缺点：**深翻页 O(offset)**；`hasNextPage` 是"本页行数 >= pageSize"的猜测（`setPageInfo`，`:1394-1406`），`fuzzyTotal` 因此得名（`:1405`）。

EQL 应当改造：有 AST 就能把 `limit/offset` 下推进生成的 SQL。Chat2DB 其实有 `ISqlBuilder.dql().buildPageLimit(PageLimitRequest)`（`sql/builder/IDqlSqlBuilder.java:10`、`MysqlSqlBuilder.java:234`），但**执行主链路没用它**——典型的"有能力、没接线"。

### 6.4 超时、只读与事务

- 全仓未发现 `setQueryTimeout`，也未发现 `Connection.setReadOnly` 调用（grep 命中的 `READ_ONLY` 均属 Oracle 视图选项、ResultSet 并发模式等无关场景）。超时只能靠 `cancel` 兜底。
- `setAutoCommit(false)/commit/rollback` 只出现在 `DefaultSQLExecutor.java:1495-1525` 的"取整表记录"路径，且测试明确要求**不得提交/回滚调用方管理的事务**（`DefaultSQLExecutorTransactionTest.java:31-71`）。普通脚本执行是自动提交语义，多语句之间**没有事务包裹**。

结论：Chat2DB 把事务/只读/超时完全交给数据库和用户 SQL。对 EQL 工具不可接受，必须显式建模。

### 6.5 错误模型与 EXPLAIN

错误不穿透异常，而是**结果内嵌**：`ExecuteResponse.success=false` + `message`，配合 `SqlExecutionMessage{level, message, errorCode, sqlState, source}`（`service/sqlExecutionStream.ts:66-72`）；语句级错误可继续执行，由 `errorContinue` 控制（`DefaultSQLExecutor.java:678-680`、`:726-728`）；HTTP 层再包 `DataResult/ListResult` 信封。这个"批量执行单条失败不中断 + 保留 SQLState"的设计，对 EQL 多语句测试面板很合适。

EXPLAIN 只是一个请求布尔位（`SqlExecuteRequest.java:76`），执行前把每条语句改写成方言 explain 形式：`Chat2DBContext.getSqlBuilder().dql().buildExplain(sql)`（`:820-826`）。默认实现 `"EXPLAIN " + sql`（`DefaultSqlBuilder.java:1027-1030`），SQL Server 覆写为 `SET SHOWPLAN_XML ON ...`（`SqlServerSqlBuilder.java:381`）。**EXPLAIN 复用普通查询的执行/结果链路**，不需要单独展示组件。

---

## 7 结果集展示

### 7.1 虚拟化表格

`ResultSetTable` 仅 221 行（`blocks/SearchResult/components/ResultSetTable/index.tsx`），渲染完全交给 `@visactor/vtable`（`:3` 引 `CanvasTable`，`blocks/CanvasTable/index.tsx:12` 引 `VTable`）。Canvas 渲染天然支持万行级数据、冻结列、单元格编辑器与全文搜索（`package.json:71-73`）。自带列筛选排序（`hooks/useFilterAndSort`）、表头 tooltip、单元格右键菜单（复制为 SQL/INSERT/Markdown、粘贴、置 NULL）、选中聚合、行详情。

### 7.2 行编辑

可编辑性由后端判定：`SqlUtils.buildCanEditResult(originalSql, dbType, executeResult)`（`DefaultSQLExecutor.java:894`、`:1189`），前端据此开放编辑。编辑不直接改表，而是先生成 SQL 再执行：`POST /api/rdb/dml/get_update_sql` 反推 UPDATE，`execute_update` 执行；复制/粘贴/批量改值同样生成 SQL（`copy_update_sql`、`copy_in_values_sql`，`DbDmlController.java:105-140`）。"所有变更都归结为可见 SQL"是可审计性的关键。

### 7.3 导出与大字段

前端只选参数：`exportType ∈ {EXCEL, CSV, INSERT}` × `exportSize ∈ {ALL, CURRENT_PAGE}`（`ExportBar/index.tsx:29-40`）。Web 走 `downloadFile('/api/rdb/dml/export', params)`，Desktop 走 `exportResultTable` 再 `revealInExplorer`。后端把 `OutputStream` 交给 `IDbDmlExportService.export`（`DbDmlExportController.java:43-52`），即**流式写出、不落中间大对象**；`DbDmlExportPlan` 含文件名与类型。这个"plan → openOutputStream → write"三段式比"生成临时文件再下载"更省内存，也能同时服务本地文件与 HTTP 响应。

大字段在读取时即决定截断：`JDBCDataValue(rs, meta, i, limitRowSize)`（`DefaultSQLExecutor.java:347`），大值经 `IDbLargeValueTokenService` 发 token，前端再用 `/api/rdb/cell/value`、`/api/rdb/cell/download_path` 分块拉取（`DbCellValueController.java:38-64`）。

---

## 8 查询管理与 AI 能力

### 8.1 历史与收藏

- 执行历史：每次执行异步落 `OperationLog`（`IOpsSqlOperationLogService.recordResultsAsync`，`:17`），上限 **1000** 条（`OperationLogStorage.java:14`），接口 `/api/operation/log`；
- 收藏/控制台：`ConsoleStorage` 上限 **10000**（`ConsoleStorage.java:13`），接口 `/api/operation/saved`；
- 持久化形态出乎意料地朴素：`SmallDataStorage`/`LargeDataStorage` 用 **JSON Lines 文件** + `ConcurrentSkipListMap` 全量载入内存（`SmallDataStorage.java:23-46`、`LargeDataStorage.java:22-49`），路径 `{base}/storage/{name}/{name}.json`。

单用户本地场景够用，但不可借鉴到多人/大数据量场景。

### 8.2 AI：工具调用 + 流式 SSE

`AiChatController` 用 Spring AI 的 `ChatClient` + `SseEmitter`（`AiChatController.java:70-75`），工具以注解暴露（`AiToolAdapter.java`）：`list_all_datasources`、`list_all_databases/schemas/tables`（输出带 `[SYSTEM]` 标记与注释，`AiToolServiceImpl.java:155-186`）、`get_tables_schema`（**DDL 优先**，取不到再退回结构化列，`:259-286`、`:344`）、`execute_sql`（`pageSize` 默认 200、上限 500，`AiToolAdapter.java:67-84`）。

### 8.3 表/字段"语义"是怎么喂给模型的

社区版**没有语义层**：`AiBusinessContextServiceImpl.buildStructuredContext` 直接 `return null`（`:13-16`），embedding/数据集合端点在后端不存在。真正的语义通道只有两条：

1. **数据库原生注释**——表/列注释拼进元数据文本（`AiToolServiceImpl.java:355-357`）与补全候选的 `comment`（`SqlCompletionConverter.java:158`）；
2. **方言级 `buildAITableSchema`**——把 `Table` 渲染成该方言的 `CREATE TABLE` DDL 给模型（`MysqlSqlBuilder.java:135-138`）。DDL 比"列名+类型"表信息密度高得多，是低成本的 schema 序列化方案。

NL2SQL 用独立系统提示词，强制"只输出一条可执行 SQL、不要 markdown、不要解释"（`AiChatStreamAdapter.java:145-170`），并按是否有工具上下文拼装不同 prompt 段（`:491-499`）。

**对 EQL 的映射**：EQL 的语义天然强于注释——实体显示名、属性显示名、字典、关联、`xmeta` 业务描述都是结构化元数据。EQL 版可把 `buildAITableSchema` 升级为 `buildEntitySchema`：实体 → 带属性/关联/中文名的类 DDL 文本，同时供 AI 与补全 `detail` 复用。

---

## 9 数据库方言抽象机制（重点）

Chat2DB 把方言差异切成三层，层次清晰但存在重复。

### 第 1 层：JSON 声明式能力（配置化）

每插件一个 `xxx.json` → 反序列化为 `DBConfig`（`MysqlPlugin.java:16-24`）。`DBConfig` 字段（`domain/api/config/DBConfig.java:47-66`）：

| 字段 | 用途 | 是否被消费 |
|---|---|---|
| `dbType` / `name` | 注册键与显示名 | 是（`Chat2DBContext.java:37-46`） |
| `supportDatabase` / `supportSchema` | 是否三级命名空间 | 是（`alignConnectionContext`、补全引擎） |
| `driverConfigList` | 默认/可选 JDBC 驱动、下载地址、扩展参数 | 是 |
| `sqlMap` | 方言 SQL 模板（`SQL_TABLE_DDL` / `SQL_VIEW_DDL` / `SQL_CHANGE_DATABASE`…） | 是 |
| `columnTypes` / `indexTypes` | 类型/索引能力清单 | 是（`GenericMetaData.java:44-55`） |
| `preserveScriptBatchExecution` | 是否保留整段脚本 | 是 |
| `systemDatabases` / `systemSchemas` | 系统对象过滤 | 是 |

`sqlMap` 是最漂亮的配置化设计：模板用 `{database}/{schema}/{table}` 占位，`DBConfig.getTableDdl` 做字符串替换（`:198-211`）；`GenericMetaData.tableDDL` 拿模板查库、取指定结果列，**失败再退回 JDBC `getMetaData()`**（`GenericMetaData.java:26-39`）。这样一个"通用方言"插件不用写 Java 就能支持新数据源的 DDL 读取。配置示例见 `generic.json:18-23`（DuckDB）与 `:215-219`（TDengine）。

但绝大多数方言的 JSON 只有 5 个 key（`pg.json` 仅 `dbType/supportDatabase/supportSchema/driverConfigList/name`），`columnTypes` 基本为空——**配置化能力存在，但没被充分使用**，真正的差异仍沉在 Java 里。

### 第 2 层：Java 插件实现

`IPlugin` 把方言拆成 7 个可覆写组件（`IPlugin.java:11-130`），都有默认实现，插件只覆写需要变的。MySQL 插件包结构即最佳实践：`plugin/mysql/{account, builder, completion, config, constant, converter, enums, identifier, model, parser, value}`。`MysqlSqlBuilder extends DefaultSqlBuilder`，只覆写 `quoteIdentifier`、`buildCreateTable`、`buildAlterTable`、`buildPageLimit` 等十余个方法——**模板方法 + 少量覆写**的正确用法。

### 第 3 层：前端能力矩阵（重复建模）

`src/constants/databaseCapabilities.ts` 用 TS 常量表描述前端可见能力：`routineOperationSupported`、`importExportUnsupported`、`createDatabaseCharsetSupported`、`openTableIdentifierQuote`、`backendCompletionSupported` 等 20 余项（`:10-80`）。

问题很明显：**同一事实存在三处**——后端 JSON（`supportSchema`）、后端 Java（`getSQLIdentifierProcessor`）、前端 TS（`sqlCompletionIdentifierQuote`），三者无生成关系，靠人工同步。这是典型 SSOT 违背，EQL 工具必须避免。

### 第 4 层：注册与发现

`Chat2DBContext` 静态块 `ServiceLoader.load(IPlugin.class)` 全量加载，按 `dbType` 放入 `ConcurrentHashMap`（`:33-46`）；`dbType` 为空则遍历 `getDBConfigList()`（一个插件对应多库，如 generic 同时提供 DuckDB/Elasticsearch/TDengine）。未注册类型抛异常时**把已注册列表打进消息**（`:52-57`），排查体验很好。

---

## 10 权限与安全

安全模型明确且克制（`SECURITY.md:23-38`）：**单用户、本机优先**，OS 用户即信任边界，不提供账号/租户隔离/多用户授权；HTTP 服务必须绑 `127.0.0.1` 或 `::1`，LAN/公网暴露不在支持范围；自定义 JDBC Driver 视为"安装可执行代码"，属受信操作（`:48-54`）；导入的配置/SQL/数据库内容/AI 响应均视为**不可信数据**（`:56-63`）。

工程上有三处真实护栏：CLI 运行时 Bearer token 校验（`CliRuntimeHttpFilter.java:52-60`）、MCP 桌面 token 校验（`McpAuthInterceptor.java:43-58`）、凭据加密（§5.3）。

**但 SQL 执行层面几乎没有护栏**：没有语句白名单、没有只读连接、没有 `setQueryTimeout`、没有行数硬上限（只有分页默认值）。`valid_select` 只用于判断结果是否可编辑（`DbSqlServiceImpl.java:83-91`，Druid 解析 + `instanceof SQLSelectStatement`），**不是安全闸门**；DDL 执行只是独立路由 `execute_ddl`（`DbDmlController.java:138-141`），无服务端二次确认。

结论：Chat2DB 把安全责任完全交给"本机单用户"前提。EQL 工具若面向团队/多数据源，必须补上语句类型白名单、只读开关、超时、行数上限、结果脱敏。

---

## 11 测试与工程质量

### 11.1 后端

- `src/test` 下 **134 个 Java 测试类**；
- SPI 的 5 个执行器测试正好覆盖最易回归点：`DefaultSQLExecutorTransactionTest`（调用方事务不被提交/回滚、rollback 失败时的 suppressed 异常与 abort/close 顺序）、`LargeCellTest`、`MultilineStringTest`、`ExportTest`、`ExecutionMetricsTest`；
- 补全相关测试 **30 个**，其中 MySQL 插件 20 个：`ScenarioCoverageTest`、`DdlScenarioCoverageTest`、`StatementProgressionTest`（语句推进）、`RuleRangeTest`、`AdhocProbeTest`，以及 `resolver/` 下局部作用域/结果集/关系作用域 3 个解析器测试——**补全被当作有明确输入输出契约的纯函数来测**，这是它能演进到 64 个类还不崩的原因；
- 方言 SQL 生成测试精确到字符串，如断言 `SET SHOWPLAN_XML ON ... GO ... SET SHOWPLAN_XML OFF;`（`SqlServerSqlBuilderTest.java:18`）。

### 11.2 前端与静态质量

没有 e2e/Jest，而是 **24 个 `tsx` 纯函数测试脚本**（`package.json:31-54`，另有 1 个 `test:i18n` 校验脚本），每个用 `node:assert` 断言纯函数：`activeTabLocator`、`treeInteraction`、`resultTabPreferences`、`sqlExecutionRequestTracker`、`sqlExecutionStream`、`handleCopyAsMarkdown`…其中 4 个关键脚本被纳入 `prebuild:web:community` 强制跑（`:16`）。这是"把可测逻辑从 React 组件抽成纯函数"的轻量策略——`sqlExecutionRequestTracker.ts:1-100` 就是纯状态机，测起来零成本。

静态质量：ESLint + Stylelint 均 `--max-warnings=0`（`:42-44`）、i18n 校验脚本（`:45`）、Controller Javadoc 统一写出 `Endpoint: {@code POST /api/...}`（`DbDmlController.java:48`）。

缺口：无端到端测试，Desktop/Web 两条执行链路的行为差异（§6.1）零覆盖；Web 取消语义的缺陷只能靠人工发现；`trace` 有数据结构但未见断言 trace 的测试。

---

## 12 对 Nop EQL 在线编辑器的启示

### 12.1 Adopt（直接采纳）

1. **多阶段补全流水线 + trace**。EQL 已有 ANTLR 语法（`nop-persistence/nop-orm-eql/model/antlr/Eql.g4`），可直接复用 `CodeCompletionCore` 的 ATN 候选思路，把补全拆成"定位语句 → 分析游标 → ATN 候选 → 实体属性解析 → 候选编排 → 展示"，每阶段可独立测试。这是本次调研**最高价值**的借鉴。
2. **统一 candidate 契约**：`label / insertText / detail / comment / sortRank / type / replaceStart / replaceEnd` + `status/reasonCode` + `editorHints`。把"能力不支持"表达为 `UNSUPPORTED + reasonCode`，前端可据此降级，而不是拿到空数组猜。
3. **元数据缓存按作用域键** `(dataSourceId, entityName)` / `(dataSourceId, ormModelVersion)` + `computeIfAbsent`，配置变更时整体失效。
4. **声明式方言能力表**：`DBConfig` 的 `supportDatabase/supportSchema/sqlMap/columnTypes` + 模板替换 + 失败回退 JDBC，正是"EQL 方言能力尽量配置化"的现成范式。EQL 版建议 `eql-dialect/{dbType}.json` 描述 `limitSyntax`、`identifierQuote`、`explainPrefix`、`functionWhitelist`、`supportsCTE`，Java 侧只留无法声明的部分。
5. **EXPLAIN 复用普通执行链路**：一个布尔位 + 方言 `buildExplain(sql)`，结果走同一套表格渲染。
6. **结果三元组身份模型** `(statementSequence, resultSetId, streamResultId)` + `mergeResultStarted/mergeRows/mergeResultFinished` 增量合并思路。
7. **服务端流式导出** `plan → openOutputStream → write`，天然支持"导出全部结果"而不占内存。
8. **错误内嵌 + `errorContinue`**：批量执行单条失败不中断，保留 `errorCode/sqlState`。

### 12.2 Adapt（改造后采纳）

1. **传输层**：不要复制"Desktop IPC + Web 一次性 POST"双栈。统一 **SSE 或 WebSocket 单栈**，服务端持 `executionId`，断开/取消必须能 `cancel` 到服务端；保留 **lane 锁**（同一编辑器同时一个执行）。
2. **分页**：不要 JDBC 游标跳过。EQL 有 AST，应把 `limit/offset` 下推进生成的 SQL（Chat2DB 的 `buildPageLimit` 未接线正好反证"有能力不接线"的风险）；总数单独查询，而非 `fuzzyTotal` 猜测。
3. **语义层**：把"注释 + DDL 拼提示词"升级为 **EQL 实体元数据 → 结构化 schema 文本**（实体名/显示名/属性类型/关联/字典），一份序列化同时供补全 `detail`、AI 提示词、文档导出。
4. **多标签**：借鉴 tab 拆分模型（`copy`/`move` 两种语义 + dnd-kit 拖拽，`WorkspaceTabs/index.tsx:6`、`:1251-1291`）与"持久化剔除函数字段"（`workspaceTabPersistence.ts:7-30`），但可简化——EQL 工具不需要双 pane 分屏。
5. **AI 工具面**：把 `list_all_tables/get_tables_schema/execute_sql` 换成 EQL 原生工具 `describe_entity`、`list_entities`、`validate_eql`、`explain_eql`、`execute_eql(pageSize)`。校验/解释类工具是 EQL 相对裸 SQL 的独有优势。
6. **行编辑**：保留"先生成语句再执行"的可审计模式，但生成 EQL 而非物理 SQL，并强制走 ORM 的权限/审计钩子。

### 12.3 Reject（明确拒绝）

1. **License**：`LicenseRef-Chat2DB` 禁止嵌入对外产品、禁止 Object 形式分发、禁止白标（`LICENSE:50-64`）。**不能复制任何源码**，只能借鉴架构思想；引用片段须先法务评估。
2. **前后端各存一份能力矩阵**：`databaseCapabilities.ts` 与 `DBConfig` 的重复是长期腐化源。EQL 必须单一来源（建议后端 JSON 为准，构建期生成 TS 常量）。
3. **单方言 64 个类的补全实现**：EQL 只有一份语法，方言差异只在"生成的 SQL 与函数集"，不应按数据库复制整套补全包。
4. **无只读/超时/行数上限的执行器**：EQL 工具必须默认只读、默认超时、默认行数上限，且服务端强制。
5. **全局可变静态注册表 + ThreadLocal 连接上下文**（`Chat2DBContext.PLUGIN_MAP`、`CONNECT_INFO_THREAD_LOCAL`）：与 Nop IoC 显式装配冲突，线程池里极易串上下文（`SqlExecutionJob` 手工搬运即为证据）。应用 `@Inject` 的 registry bean + 显式 `ExecutionContext` 参数。
6. **JSON Lines 本地存储**：Nop 有 ORM，历史/收藏应落库，而非启动全量载入内存。
7. **未实现的 `registerSQLFormat` 桩**：要么接 `DocumentFormattingEditProvider` 调后端，要么不注册。
8. **PC 端 IPC 行长协议**（`ConsoleHelper` 从 stdin 逐行读）：属打包形态历史产物，与 EQL 工具无关。

---

## 13 关键证据清单

| # | 结论 | 证据 |
|---|---|---|
| 1 | 5.3.0 起为修改版 Apache，禁止对外产品化复用 | `LICENSE:3-4`、`LICENSE:22-80`、`LICENSE:118-124` |
| 2 | 前端为 Enterprise 仓库 donor 副本，Pro 功能有残留 | `chat2db-community-client/frontend-donor.json:3-5`、`src/service/aiDataCollection.ts:48-108` |
| 3 | 36 个数据库插件，ServiceLoader 注册，未注册类型报错带全量列表 | `chat2db-community-plugins/*/src/main/resources/META-INF/services/ai.chat2db.spi.IPlugin`、`spi/sql/Chat2DBContext.java:33-57` |
| 4 | 补全流水线 11 阶段，每步写 trace | `spi/parser/completion/SqlCompletionPipeline.java:20-52` |
| 5 | 11 个组件以 record + requireNonNull 强约束，状态不可变 | `spi/parser/completion/SqlCompletionDialectComponents.java:17-42`、`SqlCompletionPipelineState.java:1-127` |
| 6 | ANTLR ATN caret 候选算法（650 行），按 preferredRules 过滤 | `spi/parser/completion/CodeCompletionCore.java:10-57` |
| 7 | MySQL 单方言补全 64 个类，逐个组件装配 | `plugins/.../mysql/completion/MysqlSqlCompletionDialect.java:25-45` |
| 8 | 仅 MySQL 走后端补全，其余走通用引擎 | `domain/core/impl/db/DbSqlCompletionServiceImpl.java:53-56`、`src/constants/databaseCapabilities.ts:40` |
| 9 | 元数据缓存键 = 数据源+库+schema(+表) | `domain/core/completion/SqlCompletionMetadataProviderAdapter.java:97-99`、`:121-123` |
| 10 | 表/列注释进入补全候选 `comment` | `domain/core/converter/SqlCompletionConverter.java:92`、`:125`、`:158` |
| 11 | 前端 9 个补全触发字符 + 本地/后端双 provider | `src/components/SQLEditor/core/completionProviderManager.ts:47`、`:1358`、`:1387` |
| 12 | 格式化实为后端 sql-formatter，前端另有一个假实现 | `domain/core/impl/db/DbSqlServiceImpl.java:44-75`、`src/components/SQLEditor/core/registerSQLFormat.ts:8-19` |
| 13 | 语句切分支持 DELIMITER 动态切换与 Oracle 分支 | `spi/util/SqlSplitProcessor.java:26-30`、`:171-190`、`:354+` |
| 14 | Desktop 流式事件 vs Web 一次性返回；Web 取消只 abort HTTP | `src/service/sqlExecutionStream.ts:11-29`、`src/hooks/useSqlExecutor.ts:196-235` |
| 15 | lane 锁按 consoleId 限流，冲突即拒绝 | `web/api/adapter/db/execution/SqlExecutionManager.java:82-87`、`web/.../WebJcefServerBridge.java:100-107` |
| 16 | 取消同时 `Statement.cancel` 与 `Future.cancel` | `web/api/adapter/db/execution/SqlExecutionJob.java:143-158` |
| 17 | 分页靠 `setMaxRows` + 游标跳过，`hasNextPage` 是猜测 | `spi/DefaultSQLExecutor.java:998-1006`、`:322-358`、`:1394-1406` |
| 18 | 全仓无 `setQueryTimeout` / `setReadOnly`（负向发现） | `spi/DefaultSQLExecutor.java`（见 §6.4） |
| 19 | EXPLAIN 走方言 buildExplain + 普通结果链路 | `spi/DefaultSQLExecutor.java:820-826`、`spi/DefaultSqlBuilder.java:1027-1030` |
| 20 | 结果集用 vtable Canvas 虚拟表格 | `src/blocks/CanvasTable/index.tsx:12`、`package.json:71-73` |
| 21 | 导出为 plan → OutputStream 流式写出 | `web/api/controller/DbDmlExportController.java:43-52`、`src/blocks/SearchResult/components/ExportBar/index.tsx:29-40` |
| 22 | 方言能力三层建模：JSON 模板 + Java 覆写 + 前端 TS | `domain/api/config/DBConfig.java:47-66`、`:198-211`、`plugins/.../generic/GenericMetaData.java:26-39`、`.../generic.json:18-23` |
| 23 | 凭据 AES-256-GCM + 双 AAD 隔离；单用户本机信任边界 | `tools/security/AesGcmUtil.java:25-35`、`SECURITY.md:23-63` |
| 24 | AI 工具面（列表/DDL 优先/执行）与 NL2SQL 严格输出；社区版无语义层 | `web/api/adapter/ai/AiToolAdapter.java:31-84`、`AiChatStreamAdapter.java:145-170`、`domain/core/impl/ai/AiBusinessContextServiceImpl.java:13-16` |
| 25 | 测试规模：后端 134 个测试类、补全 30 个；前端 24 个 tsx 纯函数脚本 | `chat2db-community-server/**/src/test/**/*.java`、`chat2db-community-client/package.json:16`、`:30-54` |

---

## 14 Open Questions

1. **`SqlCompletionPipelineState` 的 trace 是否有生产用途？** 目前只看到构建与挂载（`SqlCompletionPipeline.java:22-51`），未发现前端消费或落盘。若仅供调试，EQL 版可简化为按需 debug 字段。
2. **`DBConfig.columnTypes` / `indexTypes` 的实际覆盖度**：仅 generic 插件的 DuckDB 段填了完整类型清单（`generic.json:31+`），其余方言为空并回退 JDBC。是"配置化未落地"还是"有意只给无 JDBC 元数据的库用"？未确认。
3. **`sqlMap` 的完整键集合**：已确认消费 `SQL_TABLE_DDL`、`SQL_TABLE_DDL_RESULT`、`SQL_VIEW_DDL`、`SQL_VIEW_DDL_RESULT`、`SQL_CHANGE_DATABASE`（`DBConfig.java:193-241`、`DBConfigConstants.java:5-7`），是否还有其它键硬编码在别处，未穷举。
4. **Web 端取消的真实影响**：abort HTTP 后服务端 job 仍在跑，是否有兜底清理？`SqlExecutionJob` 未体现该逻辑，未确认。
5. **事件推送是否有背压？** `ConsoleSqlExecutionSink.send` 直接 `ConsoleOutboundRegistry.send(JSON)`（`:26-47`），无本地队列；而 `SqlExecutionManager` 另有 200ms 一次的 `outputPoller`。两者职责如何划分、前端消费慢时事件是否会在注册表里堆积，未追清。
6. **`fuzzyTotal` 的算法**：`calculateFuzzyTotal` 未细读，无法判断是否做了"多取一行判断 hasNext"的优化。
7. **社区版裁剪清单是否完整**：前端仍调用大量 `/api/v1/ai/embedding/**`，后端无对应 Controller。是构建期剔除还是运行期 404，未确认。
8. **License 对"内部工具"的边界**：Nop 的 EQL 编辑器若是内部研发工具，落在 Internal Use（`LICENSE:110-112`）；若要随 Nop 平台分发给 Nop 使用者，则可能触发 External Product 条款——需法务判断，本报告不作结论。
