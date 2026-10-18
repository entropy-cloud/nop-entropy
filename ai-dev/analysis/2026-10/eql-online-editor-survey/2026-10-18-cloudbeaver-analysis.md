# CloudBeaver 深度调研报告

> Status: resolved
> Date: 2026-10-18
> Scope: cloudbeaver（DBeaver Web 版）源码架构 / SQL 编辑器 / 执行链路 / 方言抽象
> Conclusion: CloudBeaver 对 EQL 在线编辑器最大的可借鉴点是：把 SQL 语言智能（补全、分段、格式化、方言能力）全部收敛为「每连接一个服务端方言描述符 + 服务端语言服务 API」，前端只做 CodeMirror 动态方言装配与结果呈现；EQL 应照此模式做配置化 `EqlDialectProfile`，而不是把方言逻辑写进渲染器。

## 1. 背景与调研问题

- 目标产品是为 EQL 提供「在线编辑 + 测试」工具，交互对标在线版 DBeaver：数据源/实体树、补全、执行、结果表格、历史、导出、EXPLAIN/校验、多标签。
- Nop 侧已有基础：EQL 编译器/执行器在 `nop-entropy`，SQL 编辑器骨架在 `nop-chaos-flux` 的 `packages/flux-code-editor`；缺的是「工作台 + 服务端语言服务 + 可配置方言」。
- 本报告回答四个问题：① 方言/补全/校验如何分层；② 元数据与执行链路如何协议化；③ 结果集/历史/导出如何建模；④ 哪些适合 EQL 采用/改造/拒绝。只评架构，不做选型与工作项拆分。
- 调研对象为 depth-1 clone（无 git 历史），HEAD `817a069`，仓库根 `/Users/abc/sources/sql-editors/cloudbeaver`；本文所有 `path:line` 均相对该根目录。
- 约束：DBeaver core（`dbeaver`、`dbeaver-common`）是同级源码依赖，未包含在本 clone 中；凡涉及 DBeaver `SQLDialect` 内部实现的判断，本文标注「未确认」。
- 证据方法：只读 `README`/`AGENTS`/GraphQL schema/前端 TS/后端 Java，不构建、不联网。

## 2. 仓库与产品概览

### 2.1 产品形态

- CloudBeaver Community 是「Java Web 服务器 + TypeScript/React Web 前端」的单体 Web 应用，Apache 2.0（`README.md:5-7`）。
- 部署为单个 Java 进程，默认端口 8978；Docker Compose 只暴露 8978，并挂载 `logs`、`workspace`（`deploy/docker/cloudbeaver-ce/docker-compose.yml:1-10`）。
- 前端是纯静态产物：`product-default` 打包后复制到服务端 `web/` 目录（`deploy/build-frontend.sh:20-36`）；后端通过 `contentRoot` 指向它（`config/template/cloudbeaver-base.conf:13-16`）。
- 服务端是 Eclipse/OSGi + Tycho 产品：`web-server` 以 `eclipse-repository` 打包（`server/product/web-server/pom.xml:14`），聚合构建同时纳入 `dbeaver-common`、`datadam-api`、`dbeaver`（`server/product/aggregate/pom.xml:13-21`）。
- 运行时要求 Java 21（`server/bundles/io.cloudbeaver.server/META-INF/MANIFEST.MF:6`）；前端 Node 最低 22.15（`webapp/readme.md:7-10`）。

### 2.2 技术栈

- 前端：TypeScript ESM、React、MobX、项目自研 DI/module registry、GraphQL Code Generator、Vite、Yarn PnP、Vitest + Testing Library（`AGENTS.md:27`）。
- 前端工作区按 `core-*`（基础设施）、`plugin-*`（功能模块）、`product-*`（产品装配）分层，禁止 `core-*` 依赖 `plugin-*`（`AGENTS.md:18-20`、`AGENTS.md:56-58`）。
- 后端：OSGi bundle + Eclipse extension point，GraphQL 服务按 bundle 注册；每个服务自带 `schema/*.graphqls`（`server/bundles/io.cloudbeaver.model/plugin.xml:5-11`、`server/bundles/io.cloudbeaver.server/plugin.xml:19-35`）。
- 前后端契约由后端 schema 反向生成前端 SDK：codegen 的 schema glob 直接指向 `server/bundles/*/schema/**/*.graphqls`（`webapp/packages/core-sdk/codegen.ts:3-10`），生成物 `sdk.ts` 禁止手改（`AGENTS.md:64`）。

### 2.3 支持数据库

- `server/drivers` 下有 18 个驱动 bundle：ClickHouse、Databend、DB2、DB2/400、DuckDB、H2（3 个版本）、Jaybird(Firebird)、Kyuubi、libSQL、MariaDB、MySQL、Oracle、PostgreSQL、SQLite、SQL Server、Trino（`server/drivers/pom.xml:18-37`，目录清单同源）。
- 驱动 bundle 本身只负责把 JDBC jar 复制到 `deploy/drivers`（`server/drivers/pom.xml:50-65`）；真正的方言类、元数据读取、SQL 解析来自 DBeaver core（`project.deps`、`server/pom.xml:14-17`）。
- 因此 CloudBeaver 的「支持数据库」本质是「DBeaver 驱动 + 方言 + CloudBeaver 服务层适配」，不是 CloudBeaver 自己实现的方言体系。

### 2.4 版本与 License

- `README` 最新 changelog 为 26.2.2（2026-10-05），而服务端 bundle 版本为 `26.2.4.qualifier`、`web-server` 为 `26.2.4-SNAPSHOT`，说明 clone 处于下一版本开发期（`README.md:31`、`server/bundles/io.cloudbeaver.server/META-INF/MANIFEST.MF:6`、`server/product/web-server/pom.xml:12`）。
- 仓库根 `LICENSE` 为 Apache License 2.0；源码文件头统一声明 Apache 2.0（`webapp/packages/plugin-sql-editor/src/module.ts:1-9`）；CE 仓库只包含 Community 产品装配，EE 功能不在本仓库（`server/bundles/io.cloudbeaver.product.ce/plugin.xml:18-28`）。

## 3. 架构总览

### 3.1 模块/目录划分

| 层 | 位置 | 职责 |
|---|---|---|
| 产品装配（后端） | `server/product/*`、`server/features/*` | Tycho 产品、feature、OSGi 打包 |
| 服务 bundle（后端） | `server/bundles/io.cloudbeaver.*` | GraphQL 服务、会话、安全、元数据、SQL、RM、数据传输 |
| 驱动 bundle（后端） | `server/drivers/*` | 仅 JDBC 依赖分发 |
| 核心基础设施（前端） | `webapp/packages/core-*` | DI、资源缓存、连接、导航树、设置、UI 基座 |
| 功能插件（前端） | `webapp/packages/plugin-*` | SQL 编辑器、数据查看器、导出、执行计划、脚本管理 |
| 产品装配（前端） | `webapp/packages/product-*` | CE 插件集合与 Vite 入口 |

- 前端 145 个 workspace 目录，功能插件按「一个包一个 `module.ts`」注册服务与 bootstrap（`webapp/packages/plugin-sql-editor/src/module.ts:31-63`）。
- 后端并非 Spring：服务通过 OSGi extension point `io.cloudbeaver.service` 注册，绑定的类继承 `WebServiceBindingBase` 并声明 schema 文件（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebServiceBindingSQL.java:44-48`）。
- GraphQL endpoint 在启动时聚合所有 bundle 的 `schema/*.graphqls`，再按 service 绑定 data fetcher（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/server/graphql/GraphQLEndpoint.java:75-76`、`:98-112`）。

### 3.2 前后端边界与通信协议

- 业务 API：GraphQL over HTTP，路径 `/api/gql`（`webapp/packages/core-sdk/src/EnvironmentService.ts:13`）。
- 事件通道：WebSocket `/api/ws`；浏览器不支持或连接失败时自动降级为 `/api/events` 长轮询（`webapp/packages/core-sdk/src/EnvironmentService.ts:14`、`webapp/packages/core-root/src/TransportSubject.ts:49-54`、`:85-108`）。
- 长任务协议：mutation 只返回 `AsyncTaskInfo`（id/running/status/error/taskResult），真实结果用第二个 query 按 taskId 取（`server/bundles/io.cloudbeaver.server/schema/service.core.graphqls:88-106`、`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:577`）。
- 任务状态通过 WS 事件 `cb_session_task_info_updated` 推进；客户端也可以轮询兜底（`webapp/packages/core-root/src/AsyncTask/AsyncTaskInfoService.ts:51-76`、`:119-121`）。
- 取消是独立 mutation `asyncTaskCancel(id)`（`server/bundles/io.cloudbeaver.server/schema/service.core.graphqls:954`），服务端会取消 Job 并触发 `runAsyncTask` 的 cancel 回调（`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/model/session/WebSession.java:643-663`、`:690-700`）。

### 3.3 状态管理与缓存

- 前端状态以 MobX observable 为核心，服务通过构造函数注入并注册为单例/瞬时（`webapp/packages/plugin-sql-editor/src/module.ts:34-63`）。
- 数据访问统一建模为 `Resource` / `CachedResource` / `CachedMapResource` / `CachedTreeResource`：每个 key 带 `loaded/outdated/loading/includes/dependencies/exception` 元数据（`webapp/packages/core-resource/src/Resource/ICachedResourceMetadata.ts:10-20`）。
- 缓存支持 key 别名、列表 key、按资源依赖批量失效、预加载（`webapp/packages/core-resource/src/Resource/CachedResource.ts:210-296`）；导航树还用 offset page 元数据表达「已加载到第几页、是否还有下一页」（`webapp/packages/core-navigation-tree/src/NodesManager/NavTreeResource.ts:497-525`）。
- 这一层是 CloudBeaver 前端最值得复用的基础设施：组件不直接发请求，而是声明「我需要这个 key 的数据」，由资源统一做去重、过期、错误和依赖失效。

### 3.4 关键扩展点

| 扩展点 | 位置 | 用途 |
|---|---|---|
| 后端 GraphQL service | `io.cloudbeaver.service` extension（`server/bundles/io.cloudbeaver.model/plugin.xml:5`） | 新 API + 权限声明 |
| 前端 module registry | 各包 `src/module.ts`（`webapp/packages/plugin-sql-editor/src/module.ts:31`） | 注册 service/bootstrap |
| SQL 编辑器模式 | `SqlEditorModeService.tabsContainer`（`webapp/packages/plugin-sql-editor/src/SqlEditorModeService.ts:20-24`） | 同一编辑器挂多个面板（脚本、结果等） |
| 结果呈现 | `DataPresentationService.add()`（`webapp/packages/plugin-data-viewer/src/DataPresentationService.ts:124-129`） | 按 dataFormat 注册表格/GIS/文档视图 |
| 菜单/动作/快捷键 | `MenuService`/`ActionService`/`KeyBindingService`（`webapp/packages/plugin-sql-editor/src/MenuBootstrap.ts:85-230`） | 执行、格式化、导出、计划等命令 |
| 脚本数据源 | `SqlDataSourceService.register()`（`webapp/packages/plugin-sql-editor/src/SqlDataSource/SqlDataSourceService.ts:68-71`） | 浏览器存储 / 服务端文件 / 内存三种脚本承载 |

## 4. SQL 编辑器实现

### 4.1 包分层

- `plugin-codemirror6`：通用 CodeMirror 6 封装，含默认扩展、主题、搜索、补全、Hyperlink、React 组件（`webapp/packages/plugin-codemirror6/src/index.ts:26-44`）。
- `plugin-sql-editor`：SQL 编辑器领域模型，含 `SqlEditorModel`、`SQLParser`、执行上下文、结果标签、历史、权限（目录清单 `webapp/packages/plugin-sql-editor/src/`）。
- `plugin-sql-editor-codemirror`：SQL 专用 CodeMirror 扩展，含动态方言、活动查询高亮、执行行 gutter（`webapp/packages/plugin-sql-editor-codemirror/src/useSqlDialectExtension.ts:1-75`）。
- `plugin-sql-editor-new`：新的 CodeMirror 面板装配 + 补全源（`webapp/packages/plugin-sql-editor-new/src/SQLEditor/SQLCodeEditorPanel/SQLCodeEditorPanel.tsx:32-65`）。
- 编辑器本身通过 `SqlEditorModeService.tabsContainer.add({ key: 'sql-editor', ... })` 挂载，且只有 datasource 具备 `script` feature 时才显示（`webapp/packages/plugin-sql-editor-new/src/SQLEditor/SQLCodeEditorPanel/SQLCodeEditorPanelService.ts:19-26`）。

### 4.2 编辑器内核与扩展装配

- CodeMirror 默认扩展包括行号、括号匹配、折叠、搜索、高亮当前行、Tab 缩进、默认 keymap；`Mod-Enter` 被有意从默认 keymap 移除，交给 SQL 执行动作（`webapp/packages/plugin-codemirror6/src/useEditorDefaultExtensions.ts:30-42`）。
- 动态能力通过 `Compartment` + `extensions.set()` 装配：方言、补全、高亮各自一个 compartment，运行期可替换（`webapp/packages/plugin-sql-editor-new/src/SQLEditor/SQLCodeEditorPanel/SQLCodeEditorPanel.tsx:44-64`）。
- 方言模块按需懒加载：`PostgreSQLLoader`、`MySQLLoader`、`MariaSQLLoader`、`MSSQLLoader`、`SQLiteLoader`、`CassandraLoader`、`PLSQLLoader`、`StandardSQLLoader`（`webapp/packages/plugin-codemirror6/src/index.ts:37-44`）。
- 这一层没有 SQL 语义；SQL 语法树、语句切分、补全候选全部来自服务端（见 4.3/4.4）。

### 4.3 补全：客户端只做「候选渲染」，服务端做「语言智能」

- CodeMirror 侧用 `autocompletion({ override: [completionSource] })` 完全接管补全，关闭内置的 `filter`，由自己的 `getOptionsFromProposals` 做模糊匹配和排序（`webapp/packages/plugin-sql-editor-new/src/SQLEditor/useSqlDialectAutocompletion.ts:97-159`）。
- 每次补全调用 `data.getHintProposals(startPos, !context.explicit)`，并把 `hintsLimitIsMet` 作为「是否停止增量更新」的信号（`webapp/packages/plugin-sql-editor-new/src/SQLEditor/useSqlDialectAutocompletion.ts:111-145`）。
- `getHintProposals` 要求已有连接的 execution context，并且连接处于 connected 状态，否则直接返回空（`webapp/packages/plugin-sql-editor/src/SqlEditor/useSqlEditor.ts:152-177`）。
- 真正的请求是 GraphQL `sqlCompletionProposals(projectId, connectionId, contextId, query, position, maxResults, simpleMode)`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:367-376`；前端封装 `webapp/packages/plugin-sql-editor/src/SqlEditorService.ts:128-148`）。
- 服务端构造 `SQLCompletionRequest`，按用户偏好选择 DBeaver 默认补全引擎或新的 query completion analyzer，排序后截断到 `maxResults`（默认 200）（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/impl/WebServiceSQL.java:137-210`）。
- 返回的候选是**不透明提案**：`displayString/type/score/replacementString/replacementOffset/replacementLength/cursorPosition/icon/nodePath`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:23-35`）。
- 关键结论：CloudBeaver 的补全协议不是「把 schema 下发给前端，前端自己算」，而是「前端给 SQL 文本 + 光标位置，服务端返回替换片段」。因此前端不需要理解表、列、别名、关联；代价是每次补全都是一次网络往返，且强依赖连接可用。
- 对 EQL 的映射：EQL 的实体路径补全（`o.customer.address.city`、集合算子）比 SQL 别名补全更需要服务端 ORM 元数据；但**实体树**仍需要前端拿到结构化元数据（见 5.2）。推荐「实体树用结构化元数据 + 编辑器补全用服务端提案」的混合协议。

### 4.4 分段、多语句与多结果

- `SQLParser` 只保存 `{start,end}` 区间和对应文本，不做 SQL 解析；解析函数由外部注入（`webapp/packages/plugin-sql-editor/src/SQLParser.ts:31-80`）。
- `SqlEditorModel` 调服务端 `sqlParseScript` 拿整段脚本的语句区间，并对输入做 2 秒 debounce；光标处语句再用 `sqlParseQuery` 精确解析（`webapp/packages/plugin-sql-editor/src/SqlEditorModel/SqlEditorModel.ts:63-68`、`:92-124`；`webapp/packages/plugin-sql-editor/src/SqlEditorService.ts:107-124`）。
- 服务端 `sqlParseScript`/`sqlParseQuery` 使用 DBeaver `SQLScriptParser` + 方言规则，只返回 offset 区间，不返回语法错误（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/impl/WebServiceSQL.java:742-765`；schema `server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:422-435`）。
- 执行「当前语句」时，若光标有选区则用选区，否则用 `getResolvedSegment()` 解析出的当前语句；执行「整个脚本」时按 parser 的 `scripts` 顺序逐条执行，若有选区则裁剪到选区覆盖的语句（`webapp/packages/plugin-sql-editor/src/SqlEditor/useSqlEditor.ts:208-300`）。
- 脚本执行不是服务端一次多语句：前端逐条调用 `executeQueries`，每条一个 async task，聚合统计（执行数、耗时、更新行数），并允许在结果标签已存在时先确认清空（`webapp/packages/plugin-sql-editor/src/SqlResultTabs/SqlQueryService.ts:222-330`）。
- 单条 SQL 仍可能返回多个结果集：服务端 `dbStat.nextResults()` 循环，驱动支持多结果时最多 100 个，否则 1 个（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:82`、`:835-885`、`:953-958`）。
- 每个结果集在前端映射为一个 result group + 多个 result tab（表格/文档/统计），状态模型见 `ISqlEditorTabState` 的 `resultGroups/resultTabs/statisticsTabs/outputLogsTab`（`webapp/packages/plugin-sql-editor/src/ISqlEditorTabState.ts:50-69`）。

### 4.5 格式化

- 格式化是服务端能力：`sqlFormatQuery` → `SQLFormatUtils.formatSQL(dataSource, query, indent)`，按连接方言格式化（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/impl/WebServiceSQL.java:214-220`）。
- 前端只对当前 segment 做替换，保留光标位置，并在格式化期间置 `readonlyState=true` 防止并发编辑（`webapp/packages/plugin-sql-editor/src/SqlEditor/useSqlEditor.ts:180-200`）。
- 格式化失败时 `SqlDialectInfoService` 记录通知并原样返回输入（`webapp/packages/plugin-sql-editor/src/SqlDialectInfoService.ts:20-27`）。
- 这与 EQL 的差异：EQL 没有现成的 `sql-formatter` 方言档位；若沿用「服务端格式化」，可在 EQL 编译器 AST 上做 pretty-print，前端不需要引入第二个格式化器。

### 4.6 快捷键

- 执行当前语句：`mod+enter`（`webapp/packages/plugin-sql-editor/src/actions/bindings/KEY_BINDING_SQL_EDITOR_EXECUTE.ts:10-14`）。
- 在新标签执行：`mod+backslash` / `shift+mod+enter`（`webapp/packages/plugin-sql-editor/src/actions/bindings/KEY_BINDING_SQL_EDITOR_EXECUTE_NEW.ts:10-14`）。
- 执行整个脚本：`alt+x`（`webapp/packages/plugin-sql-editor/src/actions/bindings/KEY_BINDING_SQL_EDITOR_EXECUTE_SCRIPT.ts:10-14`）。
- 格式化：`shift+mod+f`（`webapp/packages/plugin-sql-editor/src/actions/bindings/KEY_BINDING_SQL_EDITOR_FORMAT.ts:10-14`）。
- 主动补全：Windows/Linux `Mod-Space`，mac `Mod-Shift-Space`；接受补全 `Tab` 或 `Enter`（`webapp/packages/plugin-codemirror6/src/createEditorAutocompletion.ts:12-27`）。
- 快捷键通过 `KeyBindingService` 与 action 绑定，CodeMirror keymap 与平台 keybinding 之间用 `mapCodemirrorShortcuts` 转换（`webapp/packages/plugin-sql-editor/src/actions/bindings/mapCodemirrorShortcuts.ts:8-23`）。

### 4.7 一个明确的能力缺口：没有语义校验/lint

- `plugin-codemirror6` 只「导出」了 `linter`/`lintGutter`，但全仓没有实际注册 lint source（`webapp/packages/plugin-codemirror6/src/index.ts:33`；`webapp/packages` 下 grep `linter(`/`setDiagnostics` 无业务命中）。
- 服务端 parse 接口只返回区间，不返回错误码/行列/严重级别（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/impl/WebServiceSQL.java:742-765`）。
- 因此 CloudBeaver 的「校验」实际依赖执行失败后的任务错误，而不是编辑器内联诊断。EQL 若要「执行前校验」，需要新增 `validateEql` 类 API 和 CodeMirror diagnostics 协议，这是 CloudBeaver 不能直接照搬的部分。

## 5. 元数据与连接管理

### 5.1 连接模型

- 连接是项目内的实体，包含 driverId、配置、状态、特性（features）；前端 `ConnectionInfoResource` 按 `{projectId, connectionId}` 缓存，并随项目/用户/会话失效（`webapp/packages/core-connections/src/ConnectionInfoResource.ts:89-160`）。
- 连接级安全特性由服务端计算：`restrictScriptExecute`、`restrictDataEdit`、`restrictDataImport`、`restrictMetadataEdit`、`readOnly` 等（`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/model/WebConnectionInfo.java:282-300`）。
- 每个 SQL 编辑器标签拥有独立的 SQL context（`contextId`），绑定 `projectId/connectionId/defaultCatalog/defaultSchema/autoCommit`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:39-52`）。
- `WebSQLContextInfo` 构造时校验 project 是否存在于当前 session，防止跨项目引用（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLContextInfo.java:87-92`）。

### 5.2 schema/实体树

- 导航树 API 以 node URI 为中心：`navNodeChildren(parentPath, offset, limit, onlyFolders)`、`navNodeInfo(nodePath)`、`navNodeParents`（`server/bundles/io.cloudbeaver.server/schema/service.navigator.graphqls:133-147`）。
- 节点带 `object`（DatabaseObjectInfo）、`features`、`editors`、`nodeDetails`、`filter` 等；列/表属性通过 `object.properties(filter)` 按需读取（`server/bundles/io.cloudbeaver.server/schema/service.navigator.graphqls:42`、`:95`）。
- 前端 `NavTreeResource` 以 parentPath 为 key 缓存子节点数组，并用 offset page 元数据记录分页；`loadNodeChildren` 直接调用 GraphQL `navNodeChildren`（`webapp/packages/core-navigation-tree/src/NodesManager/NavTreeResource.ts:497-525`、`:606-616`）。
- `NavNodeInfoResource` 以 node URI 为 key 缓存节点详情，并维护 parent 关系（`webapp/packages/core-navigation-tree/src/NodesManager/NavNodeInfoResource.ts:29-70`）。
- 树的懒加载/分页是「按父节点 + offset/limit」；不是一次性下发整库元数据。对 EQL 的实体树可照搬这一模型：querySpace/实体作为父节点，属性/关联按需展开。

### 5.3 补全依赖的元数据协议

- 补全只依赖三样东西：连接（connectionId）、SQL context（contextId）、当前 SQL 文本 + 光标（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:367-376`）。
- 服务端在内部用 DBeaver 的 `SQLCompletionContext` 读取当前连接/上下文/方言/元数据，并运行 analyzer；前端拿不到表/列列表（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/impl/WebServiceSQL.java:137-210`）。
- 这意味着 CloudBeaver 把「元数据获取」藏在连接与 context 后面：只要连接活着，补全就能拿到最新元数据；前端不需要维护 schema 版本。
- 对 EQL 的启示：如果 EQL 补全也走「服务端提案」，则不需要把整个 ORM 模型下发到浏览器，能避免大模型体积与版本不一致；但实体树仍需要一个轻量的结构化导航协议。

## 6. 查询执行链路

### 6.1 请求协议（从编辑器到结果）

1. 编辑器确保存在 execution context；没有则 `sqlContextCreate(projectId, connectionId, defaultCatalog, defaultSchema)`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:467-468`）。
2. 前端 `QueryDataSource.executeQuery` 调 `asyncSqlExecuteQuery`，参数含 `projectId/connectionId/contextId/query/resultId/filter/dataFormat/readLogs/isInteractive`（`webapp/packages/plugin-sql-editor/src/QueryDataSource.ts:271-295`）。
3. 服务端创建 async task 并立即返回 `AsyncTaskInfo`；任务内部执行 `WebSQLProcessor.processQuery`（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/impl/WebServiceSQL.java:597-615`）。
4. 客户端等任务结束后调 `asyncSqlExecuteResults(taskId)` 取 `SQLExecuteInfo`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:577`；`webapp/packages/plugin-sql-editor/src/QueryDataSource.ts:232-240`）。
5. 客户端把 `SQLQueryResults[]` 转成 `IDatabaseResultSet[]`，交给 `plugin-data-viewer` 的 `DatabaseDataModel` 渲染（`webapp/packages/plugin-sql-editor/src/QueryDataSource.ts:375-388`）。
6. 分页/排序/过滤再次走同一 mutation，带新的 `offset/limit/filter` 和上次的 `resultId`（`webapp/packages/plugin-sql-editor/src/QueryDataSource.ts:216-240`）。
7. 关闭结果调 `sqlResultClose` 释放服务端资源（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:508`）。

### 6.2 分页与「流式」

- 服务端一次请求返回一个 `SQLExecuteInfo`，里面是所有结果集和当前页的行，不是流式响应。
- 结果是「客户端分页」：`DataViewerSettingsService` 默认每页 200 行，最小 10、最大 5000（`webapp/packages/plugin-data-viewer/src/DataViewerSettingsService.ts:22-24`、`:102-107`）。
- 服务端 `WebSQLDataFilter` 默认 200 行、上限 100000 行（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLDataFilter.java:38-39`、`:51-61`）。
- 服务端按 `filter.offset/filter.limit` 读结果集，超过 limit 即停（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:909-924`）；有过滤条件时先用方言 `addFiltersToQuery` 重写 SQL（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:195-202`）。
- schema 明确写着「server always returns hasMoreData = false」，前端因此用「本页行数 < limit」推断 `loadedFully`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:163-164`；`webapp/packages/plugin-sql-editor/src/QueryDataSource.ts:375-388`）。
- 结论：这是「每次翻页重新执行/重读查询」的模型，不是游标/流式；优点是实现简单、无长连接状态，缺点是大结果集翻页成本高。EQL 有 `LongRangeBean`/`IDataSet`，可以做得更好（服务端游标或缓存结果集），但不应在 v1 过度设计。

### 6.3 取消与超时

- 取消：前端 `QueryDataSource.cancel()` 先取消本地 Task，再调 `AsyncTask.cancelAsync()` → `asyncTaskCancel`（`webapp/packages/plugin-sql-editor/src/QueryDataSource.ts:110-114`；`webapp/packages/core-root/src/AsyncTask/AsyncTask.ts:111-126`）。
- 服务端取消会调用 `CustomCancelableJob.cancelJob`、取消确认 future，并 `job.cancel()`（`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/model/session/WebSession.java:643-663`）。
- 执行过程中若用户拒绝危险语句确认，抛 `DBWebException("Query execution was cancelled by user")`（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:235-238`）。
- 超时：优先读连接 preference `sqlQueryTimeout`，否则读全局 resource quota；>0 时 `dbStat.setStatementTimeout(queryTimeout)`（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:276-288`）。
- 并发限制：每个 session 同时运行的 async task 数受 `sqlMaxRunningQueries` 限制，超出抛 `DBQuotaException`（`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/model/session/WebSession.java:709-713`）。
- 行数限制：结果集累计行数超过 `sqlResultSetRowsLimit` 抛异常（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLQueryDataReceiver.java:164-166`）。
- 配额常量集中在 `WebSQLConstants`：`sqlResultSetRowsLimit`、`sqlMaxRunningQueries`、`sqlQueryTimeout`、文本/二进制预览长度、RM 文件大小（`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/service/sql/WebSQLConstants.java:24-29`）。

### 6.4 事务与只读控制

- 每个 SQL context 有 `autoCommit`；前端 `ConnectionExecutionContext` 提供 `setAutoCommit/commit/rollback`，均走 async task（`webapp/packages/core-connections/src/ConnectionExecutionContext/ConnectionExecutionContext.ts:98-170`）。
- 服务端对应 `asyncSqlSetAutoCommit`、`asyncSqlCommitTransaction`、`asyncSqlRollbackTransaction`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:616-630`）。
- 非自动提交时，执行结束会发事务计数事件（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:318-321`），前端可显示事务状态。
- 只读控制是「多层」而非单一开关：连接 feature `readOnly`/`restrictDataEdit`、结果集 `readOnly`、前端 `DatabaseDataAccessMode.Readonly`、以及 `asyncUpdateResultsDataBatch` 的全局权限（`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/model/WebConnectionInfo.java:282-300`；`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:177-180`；`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:163-177`）。
- 注意：没有看到「SQL 解析层拒绝 DDL/DML」的逻辑；安全主要靠数据库账号权限、连接权限、全局权限与危险语句确认。EQL 语法本身只含 DML（参见 Flux 报告），但仍应在服务端做语句类型/只读校验。

### 6.5 错误模型

- GraphQL 层错误用 `ServerError`：`message/errorCode/errorType/stackTrace/causedBy`（`server/bundles/io.cloudbeaver.server/schema/service.core.graphqls:108-121`）。
- async task 失败时 `AsyncTaskInfo.error` 带 `ServerError`；前端 `AsyncTask` 在 `info.error` 时 reject 为 `ServerInternalError`（`webapp/packages/core-root/src/AsyncTask/AsyncTask.ts:138-146`）。
- 服务端 `runAsyncTask` 捕获 `InvocationTargetException`，把 target exception 写入 `asyncTask.setJobError`，同时写入 session 日志（`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/model/session/WebSession.java:720-728`）。
- 执行成功但无结果集时，`statusMessage` 为 `"No Data"`；有结果/更新时为 `"Executed"`（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:323-328`）。
- 前端 `QueryDataSource` 把 `statusMessage/duration/filterText/fullQuery/originalQuery` 放进 `requestInfo`，用于结果区状态栏和统计（`webapp/packages/plugin-sql-editor/src/QueryDataSource.ts:366-388`）。

## 7. 结果集与数据展示

### 7.1 结果集模型

- `SQLResultSet` 不是「二维数组」：列有 `position/name/label/icon/entityName/dataKind/typeName/fullTypeName/maxLength/scale/precision/required/autoGenerated/readOnly/readOnlyStatus/supportedOperations/description`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:108-134`）。
- 行是 `rowsWithMetaData`，每行 `{data, metaData}`，支持单元格级元数据（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:136-139`、`:156`）。
- 结果集还带 `singleEntity`、`hasRowIdentifier/rowIdentifierState/rowIdentifier`、`hasChildrenCollection`、`hasDynamicTrace`、`isSupportsDataFilter`、`readOnly/readOnlyStatus`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:158-180`）。
- 这些标志直接决定 UI 能力：能否编辑、能否过滤、能否加载 LOB、能否做动态 trace、能否从结果跳转到关联实体。

### 7.2 表格与呈现

- 表格基于内部包 `@dbeaver/react-data-grid`（`webapp/packages/plugin-data-grid/src/DataGrid.tsx:10-20`；依赖声明 `webapp/common-react/@dbeaver/react-data-grid/package.json:33-36`）。
- 呈现层是可注册的：`DataPresentationService` 按 `type` + `dataFormat` + `hidden` + `order` 选择组件，GIS 视图就是这样注册的（`webapp/packages/plugin-data-viewer/src/DataPresentationService.ts:73-90`、`:124-129`；`webapp/packages/plugin-gis-viewer/src/index.ts:5-10`）。
- 结果格式抽象为 `resultset/document/graph/timeseries`（`server/bundles/io.cloudbeaver.server/schema/service.core.graphqls:418-423`），前端按 dataFormat 注册 data action（当前可见的是 ResultSet/Document 两套，`webapp/packages/plugin-data-viewer/src/ResultSet/ResultSetDataSource.ts:68-80`）。
- 单元格值有独立的值面板（文本、布尔、图片、GIS 等），大值按配额截断，LOB 走单独 API（见 7.4）。

### 7.3 行编辑

- 编辑能力由 `asyncUpdateResultsDataBatch(resultsId, updatedRows, deletedRows, addedRows)` 提供，返回 async task，真正写回在服务端 `WebSQLDataUpdater` 中完成（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:511-530`；`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:382-445`）。
- 前端 `QueryDataSource.save` 从 `ResultSetEditAction`/`DocumentEditAction` 收集变更，上传 blob，再调 mutation；成功后把返回结果合并回本地结果集（`webapp/packages/plugin-sql-editor/src/QueryDataSource.ts:120-210`）。
- 能否编辑取决于：连接 `isDataEditable`、`DatabaseDataAccessMode`、结果集 `readOnly`、是否存在 row identifier（`webapp/packages/plugin-sql-editor/src/SqlResultTabs/SqlQueryService.ts:185-204`；`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:165-170`）。
- 对 EQL 控制台，v1 建议只读；如果未来要支持编辑，必须复用「row identifier + 单实体结果集」这套约束，否则无法生成安全的 UPDATE/DELETE。

### 7.4 导出与 LOB

- 导出是独立服务 `dataTransferExportDataFromResults`，异步任务产出 `dataFileId`，前端拼 `/data/{fileId}` 下载（`server/bundles/io.cloudbeaver.service.data.transfer/schema/service.data.transfer.graphqls:83-105`；`webapp/packages/plugin-data-export/src/DataExportProcessService.ts:50-100`）。
- 导出处理器由服务端 `dataTransferAvailableStreamProcessors` 返回（具体格式由流处理器插件决定），前端只负责选择参数（`server/bundles/io.cloudbeaver.service.data.transfer/schema/service.data.transfer.graphqls:82-83`）。
- 导出进度/状态是轮询 `getAsyncTaskInfo`，固定 1 秒间隔，而不是复用 WS 任务事件（`webapp/packages/plugin-data-export/src/ExportFromResultsProcess.ts:13`、`:91`）。这是一个可改进点。
- 大文本/二进制不直接塞进结果集：文本预览默认 4KB、二进制 255KB，完整值走 `sqlReadStringValue`/`sqlReadLobValue`（`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/service/sql/WebSQLConstants.java:31-32`；`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:557-570`）。

## 8. 查询管理

### 8.1 查询历史

- 没有独立的「执行历史」列表/API。全仓在 `webapp/packages` 中 grep `queryHistory|executionHistory|sqlHistory` 无命中。
- 存在的 `SqlDataSourceHistory` 是**编辑器撤销历史**：最多 30 条热历史，5 秒内的连续编辑会被压缩；导航时恢复文本和光标（`webapp/packages/plugin-sql-editor/src/SqlDataSource/SqlDataSourceHistory/SqlDataSourceHistory.ts:17-18`、`:38-63`）。
- 该历史随脚本数据源持久化到浏览器 IndexedDB（`webapp/packages/plugin-sql-editor/src/SqlDataSource/LocalStorage/LocalStorageSqlDataSourceBootstrap.ts:19-88`）。
- 对 EQL：这是明确的增量机会。EQL 控制台应把「执行历史」作为一等资源（服务端表 + 查询/收藏/重跑），而不是复用撤销历史。

### 8.2 保存的脚本与共享

- 脚本作为资源管理器（RM）中的文件保存，资源类型 `sql-script`（`webapp/packages/plugin-resource-manager-scripts/src/SCRIPTS_TYPE_ID.ts:1`）。
- 创建脚本时写入文本，并把默认 `projectId/connectionId/catalog/schema` 写进资源属性，下次打开自动恢复连接上下文（`webapp/packages/plugin-resource-manager-scripts/src/ResourceManagerScriptsService.ts:89-120`）。
- RM 本身提供项目内资源 CRUD、资源属性、项目级权限授予（`server/bundles/io.cloudbeaver.service.rm/schema/service.rm.graphqls:57-90`、`:127-133`）。
- 共享不是「分享链接」，而是「把项目/资源授权给 subject」；脚本跟随项目权限（`server/bundles/io.cloudbeaver.service.rm/schema/service.rm.graphqls:127-130`）。
- 自动保存由设置 `plugin.sql-editor.autoSave` 控制，默认 true；脚本数据源另有 `isAutoSaveEnabled`/`save()` 抽象（`webapp/packages/plugin-sql-editor/src/SqlEditorSettingsService.ts:52-57`；`webapp/packages/plugin-sql-editor/src/SqlDataSource/ISqlDataSource.ts:51-55`、`:84`）。

### 8.3 执行计划与多标签

- 执行计划是 async task：`asyncSqlExplainExecutionPlan` → `asyncSqlExplainExecutionPlanResult`，返回树形 `SQLExecutionPlan`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:580-590`、`:263-285`）。
- 服务端用 `DBCQueryPlanner` 适配器执行 explain，数据源不支持则抛错（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:652-690`）。
- 前端 action 是否可用由方言能力位 `supportsExplainExecutionPlan` 决定（`webapp/packages/plugin-sql-editor-execution-plan/src/SqlExecutionPlanMenuBootstrap.ts:61-65`、`:89`）。
- 多标签：每个 SQL 编辑器是 navigation tab，带独立 `ISqlEditorTabState` 和脚本数据源；tab handler 负责 restore/close/unload（`webapp/packages/plugin-sql-editor-navigation-tab/src/SqlEditorTabService.ts:82-115`）。结果区再按 result group 分 tab，支持关闭整组（`webapp/packages/plugin-sql-editor/src/SqlEditorGroupTabsBootstrap.ts:37-65`）。

## 9. 方言抽象机制

### 9.1 服务端：`SQLDialect` → `SQLDialectInfo` 投影

- DBeaver 的 `SQLDialect` 是按数据库实现的 Java 接口；CloudBeaver 不重新实现方言，而是把它投影成可序列化的 `SQLDialectInfo`（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLDialectInfo.java:39-90`）。
- 投影字段：`name`、`dataTypes`、`functions`、`reservedWords`、`quoteStrings`、`singleLineComments`、`multiLineComments`、`catalogSeparator`、`structSeparator`、`scriptDelimiter`、`supportsExplainExecutionPlan`（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:5-21`）。
- 能力位 `supportsExplainExecutionPlan` 不是配置，而是运行期适配探测：`GeneralUtils.adapt(dataSource, DBCQueryPlanner.class) != null`（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLDialectInfo.java:88-90`）。
- 其余方言差异藏在 `SQLDialect` 方法里：SQL 解析规则、`addFiltersToQuery`、脚本分隔符、标识符引用、格式化策略等；这些**没有**全部进入 GraphQL 描述符。
- 因此 CloudBeaver 的方言模型是「**代码实现 + 部分投影**」，不是「纯配置驱动」。这正是 EQL 要改进的地方。

### 9.2 前端：用描述符动态装配 CodeMirror 方言

- 前端按连接加载 `SqlDialectInfo`，按 `name` 选择内置 loader（PostgreSQL/MySQL/MariaSQL/SQLServer/SQLite/CQL/PLSQL/Standard），再用描述符覆盖关键字、内置函数、类型、注释、引号规则（`webapp/packages/plugin-sql-editor-codemirror/src/useSqlDialectExtension.ts:30-64`）。
- 具体是 CodeMirror 的 `SQLDialect.define({ keywords, builtin, types, hashComments, slashComments, doubleDollarQuotedStrings, doubleQuotedStrings })`（`webapp/packages/plugin-sql-editor-codemirror/src/useSqlDialectExtension.ts:34-44`）。
- 描述符变化通过 `Compartment` 热替换，不需要重建编辑器（`webapp/packages/plugin-sql-editor-codemirror/src/useSqlDialectExtension.ts:17-28`）。
- 方言还会影响「执行计划」等动作是否可用（`webapp/packages/plugin-sql-editor-execution-plan/src/SqlExecutionPlanMenuBootstrap.ts:61-65`）。
- 局限：`name` → loader 是一个 switch；新增数据库必须在代码里加分支，不能纯配置注册（`webapp/packages/plugin-sql-editor-codemirror/src/useSqlDialectExtension.ts:48-75`）。

### 9.3 能力差异如何建模

CloudBeaver 实际用了四种机制，而不是一个统一的 capability 模型：

| 机制 | 例子 | 特点 |
|---|---|---|
| 方言描述符字段 | `supportsExplainExecutionPlan`、`scriptDelimiter` | 可序列化、前端可直接用 |
| 方言接口方法 | `addFiltersToQuery`、`getDataTypes`、`getReservedWords` | 只能在服务端用，前端不可见 |
| 连接/数据源 feature | `restrictScriptExecute`、`readOnly`、`restrictDataEdit` | 权限与能力混合 |
| 数据源 feature 枚举 | `ESqlDataSourceFeatures.script/query/executable/setName/setProject` | 控制编辑器面板/动作是否显示（`webapp/packages/plugin-sql-editor/src/SqlDataSource/ESqlDataSourceFeatures.ts:9-15`） |

- 这种做法的优点是渐进：先有代码，再把需要跨进程的部分投影出来。
- 缺点是「能力」没有单一事实源：新增一个能力（比如 EQL 的 `supportsExplain`、`supportsValidate`、`supportsEntityNavigation`）需要同时改 schema、后端投影、前端类型、动作 gating，容易漏。
- 对 EQL 的建议：定义**一个** `EqlDialectProfile` 作为单一事实源，至少覆盖词法/格式（keywords/functions/types/comments/quote/separators/delimiter）、语言服务（completion mode、validate/format/explain 能力位）、执行（read-only/max rows/timeout/statement 分隔/多语句）。
- 前端只消费 profile；后端用同一份 profile 驱动 EQL 编译器/执行器，避免「前后端各写一份方言」。

### 9.4 与 EQL 的对比

| 维度 | CloudBeaver | EQL 目标 | 判断 |
|---|---|---|---|
| 方言来源 | DBeaver Java `SQLDialect` 实现 | Nop ORM/EQL 编译器 + 配置 | 不能照搬类层次，照搬「投影」思路 |
| 前端方言 | 描述符 + CodeMirror `SQLDialect.define` | 同一模式，profile 更完整 | 直接 adopt |
| 补全 | 服务端提案（不透明） | 实体路径，需要 ORM 元数据 | adapt：提案 + 实体树 |
| 校验 | 无 lint，只有执行错误 | 编译期校验 + 行列诊断 | 必须新增，CloudBeaver 无参考 |
| 格式化 | 服务端方言 formatter | EQL AST pretty-print | adapt |
| 能力位 | 分散在描述符/feature/枚举 | 统一 profile | 改进 |
| 多数据库 | 18 驱动，方言各异 | 单 EQL 引擎，多物理库 | reject 驱动管理复杂度 |

## 10. 权限、安全与多租户

### 10.1 声明式权限

- 每个 GraphQL action 用 `@WebAction` 声明：`requirePermissions`、`requireGlobalPermissions`、`authRequired`、`initializationRequired`、`configurationModeAllowed`（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/WebAction.java:30-43`）。
- 执行 SQL 的全局权限是 `permission.sql.script.execution`（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/DBWServiceSQL.java:117`；常量 `server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/DBWConstants.java:37`）。
- 结果集编辑的全局权限是 `permission.data-editor.editing`（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/DBWServiceSQL.java:163`、`:177`）。
- 权限检查在 `WebServiceBindingBase` 统一执行：session、license、`requirePermissions`、`requireGlobalPermissions`、项目权限、配置模式；admin 在 `WebSession.hasPermission` 中直接放行（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/WebServiceBindingBase.java:286-312`、`:327-370`；`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/model/session/WebSession.java:224-227`）。

### 10.2 连接级与前端双闸门

- 连接级：`DBPDataSourcePermission.PERMISSION_EXECUTE_SCRIPTS` 不满足时，连接 features 加 `restrictScriptExecute`（`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/model/WebConnectionInfo.java:282-288`）。
- 前端：`SqlEditorPermissionService.isScriptExecutionEnabled` 同时检查 `restrictScriptExecute` 未出现、且设置 `plugin.sql-editor.script.executionEnabled` 为真（`webapp/packages/plugin-sql-editor/src/SqlEditorPermissionService.ts:21-29`）。
- 结果是「服务端强制 + 前端提前禁用」的双层控制；前端禁用只是 UX，真正安全边界在服务端注解与连接权限。

### 10.3 多租户

- 所有 SQL API 都带 `projectId`；SQL context 绑定 project，构造时校验 project 在当前 session 中存在（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLContextInfo.java:87-92`）。
- 项目权限通过 RM 的 `hasProjectPermission` 检查；私有项目在未开启 custom connections 时直接拒绝（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/WebServiceBindingBase.java:290-302`）。
- 项目是资源/连接的隔离边界：连接、脚本、资源都在 project 下；项目可授权给 subject（`server/bundles/io.cloudbeaver.service.rm/schema/service.rm.graphqls:127-130`）。

### 10.4 危险语句确认

- 服务端对 UPDATE/DELETE 的危险性、DROP 语句做检测；命中后通过 WS 发确认事件，等待用户确认，超时/拒绝则取消执行（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:964-1030`）。
- 是否确认可由用户偏好 `CONFIRM_DANGER_SQL_KEY`/`CONFIRM_DROP_SQL_KEY` 控制，也支持 `SKIP_TASK_CONFIRMATIONS_ATTR` 跳过（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:969-990`）。
- AI 生成的 SQL 还会走单独的确认文案（`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:980-987`）。
- 对 EQL：EQL 语法只含 DML，但仍应对 UPDATE/DELETE 无 WHERE、跨 querySpace 大范围更新做确认；可复用「WS 确认事件 + 任务等待」的模式。

## 11. 测试与工程质量

### 11.1 前端

- Vitest + Testing Library + happy-dom；统计到 112 个 `*.test.ts(x)`（`webapp/packages` 下 find 计数）。
- 共享测试入口 `@cloudbeaver/tests-runner`：启动完整 `App`、注册真实 DI、提供 `renderInApp`，组件测试跑在应用上下文里（`webapp/packages/tests-runner/src/createApp.ts:22-45`）。
- GraphQL/WS 用 MSW mock：`core-root/src/__custom_mocks__/createGQLEndpoint.ts`、`createWebsocketEndpoint.ts`、`mockGraphQL.ts`（目录清单）。
- 测试与源码同目录；核心资源缓存有较完整的单测（`webapp/packages/core-resource/src/Resource/CachedMapResource.test.ts:1-80`）；但 `plugin-sql-editor` 没有测试文件，编辑器主要靠后端集成测试覆盖。

### 11.2 后端

- JUnit 5 测试套件启动嵌入式 CloudBeaver server，再通过 GraphQL 客户端打真实 API（`server/test/io.cloudbeaver.test.platform/src/io/cloudbeaver/test/platform/CEServerTestSuite.java:40-75`）。
- SQL 相关测试覆盖结果集生成、数据更新、row id、分组、外键导航、过滤约束（`server/test/io.cloudbeaver.test.platform/src/io/cloudbeaver/test/platform/CEServerTestSuite.java:67-73`）。
- async task 测试封装：30 秒超时、200ms 轮询、断言 `running=false`/`status=finished`/无 error（`server/test/io.cloudbeaver.test.platform/src/io/cloudbeaver/test/platform/util/GraphQLTestClientWrapper.java:38-39`、`:80-95`）。
- 测试基类 `CloudbeaverDBTest` 提供 H2 数据库、session、connection、SQL processor，测试可直接建表/插数（`server/test/io.cloudbeaver.test.platform/src/io/cloudbeaver/test/platform/sql/GenerateSQLResultSetTest.java:32-70`）。
- 另有一组 LSP 相关测试（`DBLTextDocumentServiceTest` 等），但实现类 `org.jkiss.dbeaver.model.lsp.*` 在 DBeaver core，不在本 clone；CloudBeaver 没有把 LSP 暴露成 GraphQL/WS 端点（未确认未来计划）。
- CI 侧前端先 `yarn bundle` 再 `yarn test`（失败阻断构建），后端用 JDK 21 + Tycho；未发现 Playwright/Selenium E2E（`deploy/build-frontend.sh:20-31`、`.github/workflows/backend-build.yml:20-40`）。

## 12. 对 Nop EQL 在线编辑器的启示

### 12.1 五条核心判断

1. **前端应该是「薄」的**：CloudBeaver 的编辑器不解析 SQL、不维护 schema、不计算补全，只做 CodeMirror 装配和结果呈现（`webapp/packages/plugin-sql-editor-new/src/SQLEditor/useSqlDialectAutocompletion.ts:111-159`、`server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/impl/WebServiceSQL.java:137-210`）。EQL 的实体路径、关联展开、校验规则都在 Java 侧，理应沿用这个边界。
2. **方言必须投影成描述符**：CloudBeaver 用 `SQLDialectInfo` 驱动 CodeMirror 的 `SQLDialect.define`（`webapp/packages/plugin-sql-editor-codemirror/src/useSqlDialectExtension.ts:34-44`）。EQL 应把描述符扩展为完整的 `EqlDialectProfile`，并让后端/前端共用同一份定义。
3. **执行必须是「任务 + 事件」**：长查询不能占 HTTP 连接；`AsyncTaskInfo` + WS 事件 + `asyncTaskCancel` 是成熟模式（`server/bundles/io.cloudbeaver.server/schema/service.core.graphqls:88-106`、`server/bundles/io.cloudbeaver.model/src/io/cloudbeaver/model/session/WebSession.java:643-663`）。
4. **结果集要带能力元数据**：列类型、row identifier、readOnly、supportedOperations 决定 UI 能做什么（`server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:108-180`）。EQL 结果集应至少带 entity/attribute/type/readOnly 四类信息。
5. **缓存要抽象成资源**：`CachedMapResource`/`CachedTreeResource` + offset page 元数据，能同时解决实体树懒加载、方言缓存、失效传播（`webapp/packages/core-resource/src/Resource/ICachedResourceMetadata.ts:10-20`、`webapp/packages/core-navigation-tree/src/NodesManager/NavTreeResource.ts:497-525`）。

### 12.2 Adopt（直接借鉴）

| # | 借鉴点 | 一句话理由 |
|---|---|---|
| A1 | 服务端语言服务 API（补全/分段/格式化） | EQL 语义与 ORM 元数据都在 Java，前端不应复制 |
| A2 | 方言描述符驱动 CodeMirror `SQLDialect.define` | 已验证可在不重建编辑器的前提下热替换方言 |
| A3 | 每编辑器独立 execution context（autoCommit/default schema） | 多标签、多数据源、事务状态天然隔离 |
| A4 | `AsyncTaskInfo` + WS 事件 + 取消/超时/并发配额 | 长查询与危险确认必须有可取消的任务通道 |
| A5 | 结果集列元数据 + row identifier + readOnly 能力位 | 决定表格、导出、编辑、跳转的能力边界 |
| A6 | 资源缓存抽象（key 元数据、依赖失效、offset page） | 实体树与元数据缓存不应每个组件各写一套 |
| A7 | 脚本作为资源文件 + 默认连接属性 | 保存/恢复/共享脚本的最小可用模型 |
| A8 | 能力位 gating（如 `supportsExplainExecutionPlan`） | 新增 EQL 能力时可渐进上线，不破坏旧连接 |

### 12.3 Adapt（改造后使用）

| # | 借鉴点 | 改造点 |
|---|---|---|
| D1 | 补全提案协议 | 增加实体/属性路径、关联类型、集合算子；`replacementOffset` 保留 |
| D2 | 前端 offset/limit 分页 | 对接 EQL `LongRangeBean`，服务端可缓存结果集，避免每次重执行 |
| D3 | 结果格式 `resultset/document/graph` | EQL v1 只需 table + JSON document；graph/timeseries 不做 |
| D4 | 服务端格式化 | 基于 EQL AST pretty-print，不引入 SQL formatter 方言表 |
| D5 | 保存脚本的 RM 模型 | 映射到 Nop 的资源/文件服务，去掉 RM 的 lock/history 复杂度 |
| D6 | 执行计划树 | 适配 EQL/数据库 EXPLAIN 输出，保留树 + cost/rows 展示 |
| D7 | 危险语句确认 | EQL 只含 DML，改为「无 WHERE 更新/删除 + 影响行数预估」确认 |
| D8 | 输出日志/统计标签 | 保留统计；执行历史另建服务端表，不复用撤销历史 |

### 12.4 Reject（不照搬）

| # | 不借鉴 | 一句话理由 |
|---|---|---|
| R1 | DBeaver `SQLDialect` 类层次与 18 驱动管理 | EQL 是单引擎多物理库，驱动/方言差异应由 ORM 方言层吸收 |
| R2 | OSGi + Tycho + extension point 注册 | Nop 用 IoC bean + BizModel，引入 OSGi 只会增加构建复杂度 |
| R3 | 客户端 SQL 解析/补全 | CloudBeaver 已证明服务端提案可行，EQL 更应如此 |
| R4 | 结果集行编辑（v1） | EQL 控制台首要目标是「查询/测试」，编辑会引入 row id/事务/权限复杂度 |
| R5 | 连接管理 UI（新建/驱动/网络/SSL） | Nop 数据源由平台配置，控制台只需选择已配置数据源/querySpace |
| R6 | 导出用 1 秒轮询 | 应复用统一 async task 事件，减少请求与状态分叉 |

## 13. 关键证据清单

| # | 证据（path:line） | 说明 |
|---|---|---|
| 1 | `README.md:5-7` | Java Web 服务器 + TS/React 前端，Apache 2.0 |
| 2 | `AGENTS.md:27` | React/MobX/DI/GraphQL Codegen/Vite/Vitest 技术栈 |
| 3 | `server/bundles/io.cloudbeaver.server/META-INF/MANIFEST.MF:6` | 运行时 JavaSE-21 |
| 4 | `server/bundles/io.cloudbeaver.model/plugin.xml:5-11` | OSGi 服务/feature/valueSerializer 扩展点 |
| 5 | `server/bundles/io.cloudbeaver.server/plugin.xml:19-35` | SQL 服务注册及其 5 个权限点 |
| 6 | `server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:5-21` | `SQLDialectInfo` 全字段 |
| 7 | `server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:23-35` | 补全提案协议（替换片段 + nodePath） |
| 8 | `server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:108-134` | 结果列类型/只读/操作元数据 |
| 9 | `server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:148-180` | 结果集能力位（row id/readOnly/hasMoreData） |
| 10 | `server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:367-435` | 补全/格式化/解析 API 的参数 |
| 11 | `server/bundles/io.cloudbeaver.server/schema/service.sql.graphqls:477-490` | 异步执行入口与 filter/dataFormat |
| 12 | `server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLDialectInfo.java:44-90` | 方言投影与 `DBCQueryPlanner` 能力探测 |
| 13 | `server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/impl/WebServiceSQL.java:137-210` | 服务端补全分析器与 200 上限 |
| 14 | `server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/impl/WebServiceSQL.java:742-765` | parse 只返回区间，不返回诊断 |
| 15 | `server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:276-288` | 查询超时设置 |
| 16 | `server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:835-885` | 多结果集循环与最多 100 个结果 |
| 17 | `server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLDataFilter.java:38-39` | 默认 200 行、上限 100000 行 |
| 18 | `webapp/packages/plugin-sql-editor-codemirror/src/useSqlDialectExtension.ts:34-44` | 描述符 → CodeMirror `SQLDialect.define` |
| 19 | `webapp/packages/plugin-sql-editor-new/src/SQLEditor/useSqlDialectAutocompletion.ts:111-159` | 客户端补全源与服务端提案消费 |
| 20 | `webapp/packages/plugin-sql-editor/src/SqlEditorModel/SqlEditorModel.ts:63-68` | 分段解析走服务端 + 2 秒 debounce |
| 21 | `webapp/packages/core-navigation-tree/src/NodesManager/NavTreeResource.ts:497-525` | 导航树 offset page 缓存 |
| 22 | `webapp/packages/plugin-sql-editor/src/QueryDataSource.ts:271-295` | 前端执行请求参数（offset/limit/filter） |
| 23 | `webapp/packages/plugin-sql-editor/src/SqlEditorPermissionService.ts:21-29` | 前端执行权限双闸门 |
| 24 | `server/bundles/io.cloudbeaver.server/src/io/cloudbeaver/service/sql/WebSQLProcessor.java:964-1030` | 危险 UPDATE/DELETE/DROP 确认 |

## 14. Open Questions

- [ ] DBeaver `SQLDialect` 的完整方法集与 EQL 需要的「实体路径语义」差距有多大？本 clone 看不到 DBeaver core 源码，需在 `dbeaver` 仓库补一次定向调研。
- [ ] EQL 补全应该是「服务端提案」还是「前端拿 ORM 元数据自算」？服务端提案实现简单、一致性好，但每次按键都有网络往返；前端自算需要下发实体/关联图，模型可能很大。
- [ ] EQL 控制台是否允许执行 UPDATE/DELETE/INSERT？如果允许，如何做「影响行数预估 + 危险确认 + 只读开关」？
- [ ] 多语句语义：EQL 编译器是否支持一次编译多条语句？如果不支持，是否在前端逐条执行（CloudBeaver 模式）？
- [ ] 查询历史放在服务端还是浏览器？服务端便于共享/审计，但涉及多租户与清理策略。
- [ ] 结果集分页：EQL 的 `LongRangeBean` 能否在服务端保持游标/缓存结果集，避免 CloudBeaver 的「每次翻页重执行」？
- [ ] 方言 profile 的归属：放在 `nop-orm-eql` 作为 Java 常量/配置，还是放在 `nop-chaos-flux` 作为前端配置？建议双端共享一份 schema，但需要确定生成方式。
- [ ] 是否需要 LSP 复用？CloudBeaver 有 `lsp4j` 测试但未暴露端点；Nop 若要做「编辑器无关的语言服务」，LSP 可能比自定义 GraphQL 更通用，但集成成本更高。
