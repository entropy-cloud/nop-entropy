# Beekeeper Studio 深度调研报告

> Status: resolved
> Date: 2026-10-18
> Scope: Beekeeper Studio 源码 / SQL 编辑器交互 / 结果集 / 方言抽象
> Conclusion: 最大可借鉴点是「以语句为执行单元 + 以统一方言描述符驱动编辑器/执行/结果」的分层：CodeMirror 只负责解析与补全，查询以可取消的 `queryId` 句柄在独立执行进程中运行，结果集用虚拟表格承载；Nop 应保留这套分层，把执行进程换成服务端 EQL 网关，并补上 EXPLAIN/编译校验与多租户安全边界。

## 1. 背景与调研问题

- 目标产品：为 Nop 的 EQL（标准 SQL + 实体属性导航自动 JOIN）做「在线编辑 + 测试」工具，交互对标在线版 DBeaver：数据源/实体树、补全、执行、结果表格、历史、导出、EXPLAIN/校验、多标签。
- 约束：EQL 方言能力尽量配置化；Nop 前端为 React 19 + CodeMirror 6 的 flux 体系，后端 EQL 引擎在 `nop-persistence/nop-orm-eql`。
- Beekeeper Studio 是 Electron + Vue 2 的桌面 SQL 客户端，不是 Web 产品。本报告不把它当「可直接复用的实现」，而是提取三类可迁移设计：SQL 编辑器交互、结果集/元数据侧栏、方言与执行边界。
- 本报告回答五个问题：
  - Q1：桌面端如何组织编辑器、执行进程与 UI，避免渲染进程直接持有数据库连接？
  - Q2：SQL 补全、语句切分、多标签、执行选中语句分别做到什么程度？
  - Q3：结果集如何解决大行数、多结果、取消、错误定位、导出与编辑？
  - Q4：方言差异如何配置化，哪些能力被抽象成「方言描述符」？
  - Q5：哪些设计可以迁移到 Nop 的「浏览器 + 服务端执行」架构，哪些必须拒绝？

**方法与边界**：只读源码，不构建、不安装、不联网；以 HEAD `f7ff968`（2026-10-16）为准。所有行号来自实际 `read`/`grep`。无法确认的结论显式标注「未确认」。

## 2. 仓库与产品概览

| 项目 | 事实 | 证据 |
|---|---|---|
| 仓库形态 | Yarn workspaces：`apps/studio`（主应用）、`apps/ui-kit`（可复用组件库）、`apps/sqltools`（未使用） | `package.json:9-13`、`CLAUDE.md:11-14` |
| 版本 | `beekeeper-studio` 6.1.5 | `apps/studio/package.json:2-3` |
| 技术栈 | Vue 2.7 + Vuex + TypeScript；Electron 44.3.0；主进程 ESBuild、渲染进程 Vite | `apps/studio/package.json:154,208`、`CLAUDE.md:16-21` |
| SQL 编辑器 | CodeMirror 6 位于 `apps/ui-kit/lib/components/{text-editor,sql-text-editor}`；`codemirror@5` 仍作为旧依赖存在 | `apps/ui-kit/package.json:161-177`、`apps/studio/package.json:82` |
| 表格内核 | Tabulator 6（虚拟渲染、范围选择、剪贴板、下载） | `apps/studio/package.json:145`、`apps/studio/src/common/tabulator.ts:32-86` |
| 内部存储 | TypeORM + better-sqlite3，存连接、标签、历史、收藏、审计 | `apps/studio/src/common/appdb/Connection.ts:57-68` |
| 支持数据库 | README 表格 25 行（含 SAP HANA「Coming Soon」）；社区版与付费版按数据库分层 | `README.md:25-61` |

- 产品定位是「单一安装包 + 应用内升级」：社区功能 GPLv3，`src-commercial/**` 为商业 EULA。这个许可切分对「能否复制代码」有直接影响，见第 10、12 节。
- 支持数据库的能力差异不是靠 if/else 散落，而是集中在方言数据、客户端注册表、编辑器语言映射三处（见第 9 节）。
- 文档漂移实例：`CLAUDE.md:18` 写 Electron 31.7.3，实际 `apps/studio/package.json:208` 为 44.3.0。说明该仓库的架构文档不是强一致事实源，调研必须以源码为准。

## 3. 架构总览

### 3.1 三进程：main / renderer / utility

Beekeeper 不是「主进程 + 渲染进程」两段式，而是把数据库驱动放进第三个 Electron `utilityProcess`：

- 主进程 `src-commercial/entrypoints/main.ts` 负责窗口、菜单、更新、应用级 SQLite，并 fork `utility.js`；utility 退出且退出码非 0 时自动重启并重新分发端口。证据：`apps/studio/src-commercial/entrypoints/main.ts:45-94`。
- 每个窗口通过 `MessageChannelMain` 获得一对端口：`port1` 给 utility，`port2` 给 renderer，并分配 `sId` 作为会话隔离键；窗口关闭时向 utility 发送 `close` 清理状态。证据：`apps/studio/src-commercial/entrypoints/main.ts:242-253`。
- renderer 首次发请求时若尚无端口，会 `requestPorts` 触发主进程建链；拿到端口后 `UtilityConnection.setPort` 才真正 flush 消息队列。证据：`apps/studio/src/lib/utility/UtilityConnection.ts:39-55,110-139`、`apps/studio/src-commercial/entrypoints/renderer.ts:214-225`。
- utility 侧用 `parentPort` 收 `init/close`，按 `sId` 建 `State`；每个 `State` 持有 DB client、连接配置、`queries: Map<string, CancelableQuery>`、导出/导入任务等。证据：`apps/studio/src-commercial/entrypoints/utility.ts:127-146,194-205`、`apps/studio/src/handlers/handlerState.ts:16-34`。

### 3.2 通信方式：MessagePort 上的 RPC，而非每次 IPC

- renderer 通过 `Vue.prototype.$util.send(handlerName, args)` 发请求；`UtilityConnection` 用 `uuid` 维护 `replyHandlers`，把 `{id,name,args}` 投递到 `MessagePort`，按 `id` 收敛 reply/error。证据：`apps/studio/src/lib/utility/UtilityConnection.ts:110-139`。
- utility 侧有一张 `handlers` 总表，把连接、查询、导出、备份、插件、文件等 handler 合并；未知 handler 返回 error。证据：`apps/studio/src-commercial/entrypoints/utility.ts:81-102,148-192`。
- renderer 的数据库客户端 `ElectronUtilityConnectionClient` 实现 `IBasicDatabaseClient`，每个方法都是一次 `$util.send('conn/...')`，因此 UI 代码对「本地/远程」无感。证据：`apps/studio/src/lib/utility/ElectronUtilityConnectionClient.ts:12-115`。
- 预加载脚本用 `contextBridge` 暴露 `window.main`，并显式把外链交给主进程的 `safeOpenExternal` 校验，避免 preload 直接 `shell.openExternal`。证据：`apps/studio/src-commercial/entrypoints/preload.ts:30-42,72-78`。

### 3.3 一条查询的完整路径

1. `TabQueryEditor.submitQuery` 识别语句、处理事务与参数，调用 `connection.query(...)`。证据：`apps/studio/src/components/TabQueryEditor.vue:1705-1773`。
2. `ElectronUtilityConnectionClient.query` 发送 `conn/query`，拿到 utility 返回的 `id`，本地包装成 `{execute, cancel}`。证据：`apps/studio/src/lib/utility/ElectronUtilityConnectionClient.ts:89-100`。
3. utility 的 `conn/query` 真正创建 `CancelableQuery`，存入 `state(sId).queries`，返回 `uuid`。证据：`apps/studio/src-commercial/backend/handlers/connHandlers.ts:370-378`。
4. renderer 再发 `query/execute`；utility 取出 query、执行、删除句柄，并在返回前按 `maxResults` 截断。证据：`apps/studio/src/handlers/queryHandlers.ts:11-31`。
5. 取消时发 `query/cancel`，utility 调 `query.cancel()` 并删除句柄。证据：`apps/studio/src/handlers/queryHandlers.ts:33-42`。

这个「先创建句柄、再执行、可独立取消」的协议是 Beekeeper 最值得迁移的执行抽象：句柄 id 把「执行中」状态从 UI 生命周期里解耦出来，取消不依赖 renderer 还持有 Promise。

### 3.4 「前端直连 DB」vs「服务端执行」对比

严格说 Beekeeper 不是「浏览器前端直连 DB」，而是**本地独立进程直连 DB**：渲染进程不加载 `pg`/`mysql2` 等驱动，驱动与 socket 都在 utility。这个中间形态对 Nop 的参考价值比「纯前端直连」更高。

| 维度 | 本地 utility 直连（Beekeeper） | 服务端执行（Nop 目标） |
|---|---|---|
| 信任边界 | 单用户桌面；DB 凭据在用户机器上，renderer 可解密看到 | 多用户 Web；凭据必须只存在于服务端 |
| 网络可达性 | 用户机器必须能直达 DB | 浏览器只到 Nop 服务，DB 由服务端访问 |
| 驱动部署 | 原生驱动随安装包/动态依赖分发，按 OS/arch 区分 | 驱动在服务端 JVM/容器，版本统一 |
| 资源上限 | 本机内存/CPU，50k 行截断即可 | 必须做每用户/每语句配额、超时、并发上限 |
| 取消 | 进程内 `queryId` + 驱动取消（如 `pg_cancel_backend`） | 服务端 job id + 驱动取消/语句中断 |
| 审计 | 本地 SQLite，单机 | 服务端统一审计，可关联用户/租户/数据源 |
| 多租户 | 不适用 | 必须在数据源绑定、行级权限、EQL 编译期过滤上做 |
| 结果流式 | 游标读到本地文件（导出） | 游标 → HTTP 分块/SSE/临时文件下载 |

**结论**：Nop 不能走「浏览器直连」，但可以复用 Beekeeper 的「执行进程与 UI 解耦 + queryId 句柄 + 方言能力描述」三件事；把 utility 替换为服务端 EQL Gateway 即可。

## 4. SQL 编辑器实现

### 4.1 编辑器内核与扩展组合

- `TextEditor` 是 CodeMirror 6 的薄封装：`initialize` 创建 `EditorState/EditorView`，对外暴露 `setValue/getValue/setMarkers/setKeymap/execCommand` 等命令式 API。证据：`apps/ui-kit/lib/components/text-editor/TextEditor.ts:39-267`。
- `SqlTextEditor extends TextEditor`，只在 `getExtensions` 中追加 SQL 扩展，并提供 `setCompletionSource`、`setQueryIdentifierDialect`。证据：`apps/ui-kit/lib/components/sql-text-editor/SqlTextEditor.ts:20-65`。
- 基础扩展一次性组合：历史、折叠、括号匹配、多选、搜索、自动补全、矩形选择、缩进标记、只读、marker、行号、换行、vim/emacs keymap。证据：`apps/ui-kit/lib/components/text-editor/extensions/index.ts:108-166`。
- 支持 Vim/Emacs 两种键位方案，Vim 还支持 `.beekeeper.vimrc`、`:w/:q/:tabnew` 等 Ex 命令与相对行号。证据：`apps/ui-kit/lib/components/text-editor/extensions/keymap.ts:41-51`、`docs/user_guide/sql_editor/editor.md:156-210`。
- 格式化走 `sql-formatter`；格式化失败时静默回退原文，不破坏用户输入。证据：`apps/ui-kit/lib/components/sql-text-editor/SqlTextEditor.vue:135-157`。

### 4.2 补全：内置 schema 补全 + 自定义懒加载列

补全不是从零写的，而是在 `@codemirror/lang-sql` 的 vendor 副本上打补丁：

- `buildSchema` 把实体摊平成 `SQLNamespace`：默认 schema 的表用非限定名，带 schema 的表同时注册 `schema.table`；列作为 children。证据：`apps/ui-kit/lib/components/sql-text-editor/utils.ts:33-72`。
- `applyEntities` 在运行期通过 `StateEffect` 替换 schema，因此切连接/切库不需要重建编辑器。证据：`apps/ui-kit/lib/components/sql-text-editor/extensions/customSql.ts:46-55`。
- 内置补全找不到表时，`schemaCompletionFilter` 会调用 `loadColumnsFromQueryContext`：从光标前 5 行文本提取 `FROM/JOIN` 表名、结合语法树里的别名，逐表调用 `columnsGetter` 懒加载列，再合并进候选项。证据：`apps/ui-kit/lib/components/sql-text-editor/extensions/sqlContextComplete.ts:73-131,136-165,194-231`。
- 别名解析复用了 lang-sql 的 `getAliases`：沿 `Statement` 扫描 `FROM` 后的标识符与 `AS`，遇到 `where/group/order/...` 停止。证据：`apps/ui-kit/lib/components/sql-text-editor/getAliases.ts:26-67`。
- 触发时机很克制：只在输入空格且光标前是 `FROM|JOIN` 时 `startCompletion`；其他情况会 `closeCompletion`，避免每敲一个字符都弹窗。`.` 后的补全由内置 source 负责。证据：`apps/ui-kit/lib/components/sql-text-editor/extensions/sqlContextComplete.ts:39-71`、vendor `complete.ts:396-409`。
- 关键字补全不会在字符串、注释、已引用标识符里出现（`ifNotIn([...])`）。证据：vendor `complete.ts:467-485`。
- 列的 `boost: 10`，高于关键字/表，保证 `alias.` 后列优先。证据：`apps/ui-kit/lib/components/sql-text-editor/utils.ts:16-27`。

### 4.3 标识符引用与关键字大小写（可配置）

- 引用规则由方言 + 配置共同决定：`quoteIdentifiers: auto|always`，`quoteCharacter` 只有被方言识别时才生效，否则回退方言首选引号；名字里的闭引号会双写转义。证据：vendor `complete.ts:275-304`。
- 关键字大小写支持 `preserve|upper|lower`；`preserve` 的规则是「前缀含小写则小写，否则大写」，无前缀（Ctrl+Space）默认大写。证据：vendor `complete.ts:455-485`。
- 配置从 `default.config.ini` 的 `[ui.queryEditor.autocomplete]` 读取，并经 `TabQueryEditor` 的 computed 传入编辑器。证据：`apps/studio/default.config.ini:48-66`、`apps/studio/src/components/TabQueryEditor.vue:1045-1067`。
- 方言侧还区分 `caseInsensitiveIdentifiers`：MySQL/SQL Server/SQLite 不强制引用 MixedCase，PostgreSQL 家族必须引用。证据：`apps/ui-kit/lib/components/sql-text-editor/customDialects.ts:11-47`。

### 4.4 语句切分、当前语句高亮与三种运行模式

- 语句切分依赖 `sql-query-identifier`，按方言与参数类型识别；结果包含 `queries/selectedQuery/error`。证据：`apps/ui-kit/lib/components/sql-text-editor/extensions/querySelection.ts:89-102`。
- `querySelection` 是一个 `StateField`：文档或选区变化时重新切分，按光标/选区找到「当前语句」，并在语句数 ≥2 时给当前语句加 `cm-query-highlight` 装饰。证据：`apps/ui-kit/lib/components/sql-text-editor/extensions/querySelection.ts:140-200`。
- 当前语句通过 `onQuerySelectionChange` 回传 Vue，`TabQueryEditor` 保存 `individualQueries/currentlySelectedQuery/querySelectionError`。证据：`apps/studio/src/components/TabQueryEditor.vue:2003-2006`。
- 运行模式有三种：运行全部、运行当前语句、运行选中文本；`submitCurrentQuery` 优先选区，其次当前语句，最后兜底全量。证据：`apps/studio/src/components/TabQueryEditor.vue:1644-1675`。
- 「主运行」和「次运行」可交换：`primaryQueryAction` 默认 `submitTabQuery`，可配成 `submitCurrentQuery`；对应快捷键默认 `Ctrl/Cmd+Enter` 与 `F5`，次运行是 `Ctrl/Cmd+Shift+Enter`/`Shift+F5`。证据：`apps/studio/default.config.ini:39-46,357-361`、`apps/studio/src/components/TabQueryEditor.vue:958-972`。

### 4.5 多标签

- 所有标签页用 `v-show` 保留 DOM 与编辑器实例，只有 active 的显示；因此切标签不会丢 undo 栈/折叠状态/滚动位置。证据：`apps/studio/src/components/CoreTabs.vue:76-82`。
- 标签可拖拽排序；支持关闭当前、强制关闭、关闭其他、关闭右侧、全部关闭、复制标签、重开最近关闭。证据：`apps/studio/src/components/CoreTabs.vue:17-35,1038-1130`。
- 未保存标签关闭前弹确认；`closeAll/closeOther/closeToRight` 会先统计 `unsavedChanges` 再统一确认。证据：`apps/studio/src/components/CoreTabs.vue:1038-1052,1088-1129`。
- 标签状态持久化到本地 SQLite，按 `connectionId + workspaceId` 加载；位置、active、deletedAt 都会保存。证据：`apps/studio/src/store/modules/TabModule.ts:167-185,204-243`。
- 最近关闭的标签进入 `lastClosedTabs`，可从历史表恢复；未保存连接没有 DB id，则分配负数临时 id，避免与数据库 id 冲突。证据：`apps/studio/src/store/modules/TabModule.ts:10-21,104-108,190-202`。
- 标签快捷键：`Ctrl/Cmd+W` 关闭、`Ctrl/Cmd+Shift+T` 重开、`Ctrl+Tab` 切换、`Alt/Cmd+1..9` 定位。证据：`apps/studio/default.config.ini:326-343`。

### 4.6 编辑器侧的其他交互

- 错误 marker：`EditorMarker` 支持 `error/highlight/custom`，error 是红色下划线 + title tooltip；marker 会随文档变更映射位置。证据：`apps/ui-kit/lib/components/text-editor/extensions/markers.ts:1-40,60-140`。
- 编辑器侧只在 `dialect === 'postgresql'` 时把 `error.position` 转成行列 marker；未发现其他驱动提供同等的错误位置字段（BigQuery/Redshift 中的 `position` 是主键列序号，不是错误位置），其余数据库只显示消息。证据：`apps/studio/src/components/TabQueryEditor.vue:1079-1085`、`apps/studio/src/lib/db/clients/bigquery.ts:452-455`、`apps/studio/src/lib/db/clients/redshift.ts:178-181`。
- 查询参数：`:name`、`$1`、`?` 等由方言 `paramTypes` 配置；运行前若有参数会弹 modal 收集。证据：`apps/studio/default.config.ini:117-129`、`apps/studio/src/components/TabQueryEditor.vue:1751-1759`。
- Query Magic：输入 `table__column__format` 时基于表/列提供格式化补全（如 `users__email__link`），这是低代码风格的扩展点。证据：`apps/studio/src/lib/editor/extensions/queryMagicExtension.ts:1-13,60-90`。

## 5. 元数据 / schema 侧栏

- 侧栏分三个全局 tab：Tables、History、Saved/Favorites；Tables 内还有 Database 下拉与实体过滤。证据：`apps/studio/src/components/sidebar/CoreSidebar.vue:1-32`、`apps/studio/src/components/sidebar/core/TableList.vue:6-58`。
- 表列表按 schema 分组；只有一个 schema 时省略 folder 层。证据：`apps/studio/src/components/sidebar/core/table_list/VirtualTableList.vue:92-179`。
- 明确为大库优化：注释写「有的库有 1000+ 表，不优化会很慢」，因此用 `vue-virtual-scroll-list` 只渲染可视项，`keeps` 默认 30 并按视口高度动态调整。证据：`apps/studio/src/components/sidebar/core/table_list/VirtualTableList.vue:16-25,81-89,290-296`。
- 列是懒加载的：展开表或滚动到展开区域才 `updateTableColumns`，并避免并发重复加载。证据：`apps/studio/src/components/sidebar/core/table_list/VirtualTableList.vue:221-247,286-288`。
- 列项显示列名 + 数据类型 badge；右键可复制列名。证据：`apps/studio/src/components/sidebar/core/table_list/StatelessTableListItem.vue:70-88,192-205`。
- 支持固定（pin）、隐藏表/隐藏 schema、按表/视图/存储过程过滤、按名称过滤并统计隐藏数量；pin 的表单独一个可拖拽区域。证据：`apps/studio/src/components/sidebar/core/TableList.vue:66-110,365-376`。
- 元数据整体是「连接后一次性拉表清单 + 按需拉列」，刷新时只重拉表清单并刷新已展开/已 pin 的列。证据：`apps/studio/src/components/sidebar/core/TableList.vue:338-350`。

## 6. 查询执行与结果集

### 6.1 结果缓冲、截断与分页

- 查询结果走「一次性 execute 返回数组」，不是流式返回 UI；返回后在 utility 侧按 `maxResults`（默认 50000）截断行，保留 `totalRowCount` 并置 `truncated=true`。证据：`apps/studio/src/handlers/queryHandlers.ts:20-30`、`apps/studio/default.config.ini:39-40`。
- 截断是内存保护，不是分页：UI 仍持有最多 5 万行；状态栏显示「当前行数 / 总行数（Truncated）」。证据：`apps/studio/src/components/editor/QueryEditorStatusBar.vue:32-46`。
- 任意 SQL 结果没有 server-side 分页；只有「表数据浏览」用 Tabulator 的分页 + `selectTop(offset, limit)`，`pageSize=100`，并多取 1 行判断是否有下一页。证据：`apps/studio/src/components/tableview/TableTable.vue:1933-1936,2012-2036`、`apps/studio/default.config.ini:68-70`。
- 这个取舍对桌面端合理（简单、可排序/过滤），但对 Web 多租户不够：Nop 需要在服务端做 limit/offset 或游标分页，不能把 5 万行 JSON 全量发给浏览器。

### 6.2 多结果与状态栏

- 一次执行可返回多个 `NgQueryResult`（多语句）；`results` 是数组，`selectedResult` 默认选最后一个非空结果。证据：`apps/studio/src/components/TabQueryEditor.vue:1791-1813`。
- 状态栏提供结果集下拉（Result 1/2/... 带行数）、总行数、影响行数、执行耗时；结果集 >1 时显示切换器。证据：`apps/studio/src/components/editor/QueryEditorStatusBar.vue:11-60`。

### 6.3 取消

- 运行中按 `Esc`（Vim 下普通模式）或 `Ctrl+Esc` 取消；新查询开始前会先取消旧查询。证据：`apps/studio/src/components/TabQueryEditor.vue:958-972,1708-1711,1407-1417`。
- 取消能力由各驱动实现：PostgreSQL 先查 `pg_backend_pid()`，取消时执行 `SELECT pg_cancel_backend(pid)` 并唤醒本地 cancelable；BigQuery 调 `job.cancel()`；SQLite 的取消是本地 Promise 竞争。证据：`apps/studio/src/lib/db/clients/postgresql.ts:791-850`、`apps/studio/src/lib/db/clients/bigquery.ts:295-306`、`apps/studio/src/lib/db/clients/sqlite.ts:294-322`。
- 客户端注册表用 `disabledFeatures: ['cancelQuery']` 标注不支持取消的库（SQLite、Cassandra、Redis、DynamoDB 等）。证据：`apps/studio/src/lib/db/clients/index.ts:107-122,225-252,299-328`。

### 6.4 错误定位与 EXPLAIN/校验

- 执行错误以 `Error` 对象回到 `TabQueryEditor.error`；只有 PostgreSQL 的 `position` 被转成编辑器 marker，其他方言没有行列定位。证据：`apps/studio/src/components/TabQueryEditor.vue:1079-1085`。
- 未发现专门的 EXPLAIN / 执行计划 UI；全仓 grep `explain` 只命中一处注释。唯一接近「预执行」的是 BigQuery 的 Dry Run：勾选后以 `dryRun` 创建 job，返回 `statementType/totalBytesBilled/totalBytesProcessed/估算 USD`。证据：`apps/studio/src/components/TabQueryEditor.vue:197-213,1766-1773`、`apps/studio/src/lib/db/clients/bigquery.ts:261-281,607-624`。
- 因此 EXPLAIN/校验是 Beekeeper 的空白区，也是 Nop EQL 的相对优势：EQL 有编译期 AST/错误位置，应把它做成「编辑时 lint + 执行前 validate + EXPLAIN 可视化」三件套，而不是等数据库报错。

### 6.5 导出与复制

- 结果表支持 Tabulator 原生下载与自定义 formatter：CSV/JSON/XLSX/Markdown；JSON 会重写以保留列名，XLSX 会把 null 变空串、对象 JSON 序列化。证据：`apps/studio/src/components/editor/ResultTable.vue:1005-1058`。
- 剪贴板支持 TSV/JSON/Markdown；TSV 用 PapaParse 并开启 `escapeFormulae: true`，防止 `=cmd` 类公式注入。证据：`apps/studio/src/components/editor/ResultTable.vue:1060-1090`。
- 大结果导出走流式游标：`Export.initExport` 调 `queryStream/selectTopStream` 拿 `BeeCursor`，`exportData` 按 chunk `read()` 循环写文件，而不是先全量入内存。证据：`apps/studio/src/lib/export/export.ts:126-158,171-200`。
- 各驱动用 cursor 实现 chunk：PostgreSQL 用 `pg-cursor`，`read(chunkSize)` 返回数组。证据：`apps/studio/src/lib/db/clients/postgresql/PsqlCursor.ts:34-70`。

### 6.6 事务与连接预留

- 自动/手动提交两种模式；手动模式会为当前 tab `reserveConnection`，`startTransaction/commit/rollback` 都带 `tabId`，保证同一标签的语句落在同一连接。证据：`apps/studio/src/components/TabQueryEditor.vue:1921-1969`、`apps/studio/src-commercial/backend/handlers/connHandlers.ts:624-642`。
- 运行前会识别 `BEGIN/COMMIT/ROLLBACK`，自动进入/退出事务模式；事务空闲超时会警告并自动回滚。证据：`apps/studio/src/components/TabQueryEditor.vue:1733-1745,1580-1603`。
- 池与预留上限可配：`maxConnections=8`、`maxReservedConnections=2`、`manualTransactionTimeout=600000`。证据：`apps/studio/default.config.ini:94-107`。

## 7. 数据编辑与表格交互

- 「编辑结果」不是直接改 SQL，而是先由 `getResultEditData` 把结果列映射回表/列：用 `sql-query-identifier` 识别单条 `LISTING` 查询，展开通配符，匹配字段来源；无法映射、无关联表、缺主键都会给 `readOnlyReason`。证据：`apps/studio/src/lib/db/clients/BasicDatabaseClient.ts:197-296`。
- 只有 `!isPK && !generated` 的列可编辑；主键列本身不可编辑，但用于定位更新。证据：`apps/studio/src/lib/db/clients/BasicDatabaseClient.ts:284-293`。
- 表格按数据类型切换编辑器：text/json/jsonb 用 textarea，bool 用 list，enum 用 list，其余用 Vue 输入组件；表头 tooltip 显示列名、类型、只读原因。证据：`apps/studio/src/components/editor/ResultTable.vue:505-628`。
- 编辑先进入 pending changes，不立即发 SQL；支持 range 复制/粘贴、批量置 NULL、undo/redo，最后统一 Apply。证据：`apps/studio/src/components/editor/ResultTable.vue:835-845`、`apps/studio/src/common/tabulator.ts:47-60`。
- Apply 前若有手动事务且未开始，会先 `manualBegin`；保存失败会把错误标到具体单元格，并提供「Copy to SQL」把变更导出成 SQL 新标签。证据：`apps/studio/src/components/TabQueryEditor.vue:1449-1478`、`apps/studio/src/components/editor/ResultTable.vue:880-905`。
- 局限：编辑能力绑定「单表 + 主键」；join/聚合/表达式结果基本不可编辑，且未见版本列/乐观锁校验（未确认，代码只按主键定位）。对 EQL 的实体导航查询，这套模型不够用，应转为「实体 + 主键 + 版本」的实体编辑，而不是结果集反推。

## 8. 查询历史与保存

Beekeeper 有两套「历史」，容易混淆：

1. **运行历史**：每次执行后把 `{text, excerpt(250), numberOfRecords, queryId, connectionId}` 写入 `usedQueries`；连续相同文本会更新同一条而不是新增。证据：`apps/studio/src/components/TabQueryEditor.vue:1815-1831`。
2. **编辑历史（审计）**：保存过的查询每次改标题/正文都会写 `query_audit`，可 diff、可恢复；表由 SQLite trigger 维护，且 trigger 禁止更新审计行（append-only）。证据：`apps/studio/src/common/appdb/models/QueryAudit.ts:10-95`、`apps/studio/src/migration/20260526_create_query_audits.js:1-60`、`apps/studio/src/migration/20260914_fix_query_audit_append_only_trigger.js:1-18`。

- 运行历史默认只显示当前连接，可勾选「Show all」；每条显示摘要、结果数、相对时间。证据：`apps/studio/src/components/sidebar/core/HistoryList.vue:36-40,100-109`、`docs/user_guide/sql_editor/editor.md:150-154`。
- 历史读取默认 `take: 100, order by updatedAt DESC`，字段只取 `text/database/status/numberOfRecords/workspaceId/excerpt`。证据：`apps/studio/src/store/modules/data/used_query/UtilityUsedQueryModule.ts:5-18`。
- 保存查询要求 title + text，保存后把 `queryId` 绑到 tab；支持查询文件夹，保存/列表在本地与云端两套 store 间切换，并有远端删除/远端变更提示。证据：`apps/studio/src/components/TabQueryEditor.vue:1525-1564,33-55`、`apps/studio/src/store/modules/data/query/CloudQueryModule.ts:10-34`、`apps/studio/src/store/modules/data/query/UtilityQueryModule.ts:7-20`。
- 编辑历史 UI 支持「当前未保存版本」、按时间分组、显示修改人（云端）、diff 高亮、恢复某版本；恢复前若远端有变更会禁用。证据：`apps/studio/src/components/editor/QueryEditHistory.vue:31-135`。
- 这套「运行历史 + 版本审计 + 标签持久化」三层分离很值得借鉴：运行历史回答「我跑过什么」，版本审计回答「这条收藏查询怎么变的」，标签回答「我现在开着什么」。

## 9. 数据库方言适配

### 9.1 三张映射表，而不是一个描述符

Beekeeper 的方言能力分散在四处，阅读时必须一起看：

| 层 | 作用 | 证据 |
|---|---|---|
| `shared/lib/dialects/*.ts` | `DialectData`：类型、引号、转义、分页风格、默认 schema、编辑器 mode、disabledFeatures | `apps/studio/src/shared/lib/dialects/models.ts:143-220`、`apps/studio/src/shared/lib/dialects/postgresql.ts:39-66` |
| `langIdToDialect` | `text/x-*` → CodeMirror `SQLDialect` | `apps/ui-kit/lib/components/sql-text-editor/customDialects.ts:86-100` |
| `findSqlQueryIdentifierDialect` | connectionType → `sql-query-identifier` 方言（用于切句/参数） | `apps/studio/src/lib/editor/CodeMirrorPlugins.ts:36-51` |
| `CLIENTS` / `findClient` | 连接表单能力、默认端口、`disabledFeatures` | `apps/studio/src/lib/db/clients/index.ts:7-46` |

- 这带来灵活性，也带来一致性风险：新增一个数据库要在 4 处登记，任何一处漏掉都会出现「能连但补全错」「能切句但不能取消」等半残状态。Nop 应把它收敛成**一个方言描述符**（见 9.3）。
- `dialectFor` 负责别名归一：`cockroachdb→postgresql`、`mariadb/tidb→mysql`、`libsql→sqlite`、`mssql→sqlserver`、`bedrock→sqlite`。证据：`apps/studio/src/shared/lib/dialects/models.ts:31-53`。
- 社区/付费数据库清单也在此文件：`communityDialects`、`ultimateDialects`。证据：`apps/studio/src/shared/lib/dialects/models.ts:6-8`。

### 9.2 能力差异如何表达

- `DialectData` 的字段覆盖：`columnTypes`、`wrapIdentifier/unwrapIdentifier`、`escapeString/wrapLiteral`、`textEditorMode`、`defaultSchema`、`usesOffsetPagination`、`requireDataset`、`disallowedSortColumns`、`sqlLabel`、`disabledFeatures`。证据：`apps/studio/src/shared/lib/dialects/models.ts:143-220`。
- `disabledFeatures` 是嵌套对象：`manualCommit`、`resultEditing`、`rawFilters`、`builderFilters`、`shell`、`queryEditor`、`informationSchema.extra`、`tableTable`、`indexes`、`alter.*`、`multipleDatabases`。证据：`apps/studio/src/shared/lib/dialects/models.ts:161-220`。
- 例：PostgreSQL 禁用 shell、禁用 `alter.reorderColumn`；SQLite 禁用 manualCommit/shell/schema/comments/compositeKeys/多项 alter/多库。证据：`apps/studio/src/shared/lib/dialects/postgresql.ts:57-66`、`apps/studio/src/shared/lib/dialects/sqlite.ts:49-77`。
- 编辑器方言按 `languageId` 选：`text/x-pgsql→PostgreSQL`、`text/x-mysql→MySQL`、`text/x-mssql→SQLServer`、`text/x-sqlite→SQLite`、`text/x-partiql→PartiQL`、`text/x-ggsql→GreengageSQL`。证据：`apps/ui-kit/lib/components/sql-text-editor/customDialects.ts:91-100`。
- 自定义方言是「在标准 spec 上加关键字/引号/大小写策略」：Greengage 加关键字，MySQL/SQLite/SQL Server 打开 `caseInsensitiveIdentifiers`，DynamoDB 用自写 PartiQL keywords/types。证据：`apps/ui-kit/lib/components/sql-text-editor/customDialects.ts:17-84`。
- 参数类型也按方言配置：`[db.postgres.paramTypes] positional=false, numbered[]='$'`；默认支持 `?/:name/@name/$name`。证据：`apps/studio/default.config.ini:117-129,180-185`。

### 9.3 对 Nop EQL 的方言描述符建议

Beekeeper 的字段可以映射成 EQL 的「方言档案（Dialect Profile）」：

| 描述符字段 | 用途 | Beekeeper 对应 |
|---|---|---|
| `keywords/builtins/types` | 高亮、补全 | `customDialects.ts:17-84` |
| `identifierQuotes` / `caseInsensitiveIdentifiers` | 补全时是否引用、如何引用 | vendor `complete.ts:283-304` |
| `splitterDialect` / `paramTypes` | 语句切分、参数识别 | `findSqlQueryIdentifierDialect`、`default.config.ini:117-129` |
| `editorLanguageId` | CM6 语言扩展 | `langIdToDialect` |
| `formatter` | 格式化策略 | `FormatterDialect`、`formatOptionsFor` |
| `paginationStyle` | offset/limit/游标 | `usesOffsetPagination` |
| `cancelSupported` | 是否显示/启用取消 | `CLIENTS.disabledFeatures` |
| `readOnlyStatements` | 只读模式允许的语句类型 | `isAllowedReadOnlyQuery` |
| `resultEditing` | 是否支持结果编辑 | `disabledFeatures.resultEditing` |
| `explain` | EXPLAIN 模板/能力 | 无（Nop 新增） |
| `completionMode` | table-alias / entity-path / none | 无（Nop 新增，EQL 关键） |

关键差异：EQL 的补全模式不是 `alias.column`，而是 `entity.property.association.property` 与 `_some/_all` 集合算子；Beekeeper 的 `getAliases + columnsGetter` 只能作为「懒加载 + 上下文提取」的参考，不能直接复用补全语义。

## 10. 安全边界

- 官方安全模型是「桌面单用户」：凭据只存本地、SQL 直接从本机发到数据库、结果不经过厂商服务器；云服务可选，云凭据在本地加密。证据：`docs/user_guide/security.md:7-20`。
- Electron 基础加固只做了一半：`nodeIntegration:false`、`contextIsolation:true`，但 `sandbox:false`，且显式 `remoteMain.enable(webContents)` 启用 `@electron/remote`。这会扩大渲染进程被 XSS 后的攻击面。证据：`apps/studio/src/background/WindowBuilder.ts:52-58,75`。
- 外链与导航有防护：`will-navigate` 阻止非 app URL，`setWindowOpenHandler` + `safeOpenExternal` 校验协议。证据：`apps/studio/src/background/WindowBuilder.ts:79-96`、`apps/studio/src-commercial/entrypoints/preload.ts:72-78`。
- 连接密码、SSH 密码、云 token 在 SQLite 中用 `EncryptTransformer` 加密；密钥首次生成后写到 `userDirectory/.key`，用固定默认 key 包裹。证据：`apps/studio/src/common/appdb/models/saved_connection.ts:345-357`、`apps/studio/src/common/encryption_key.ts:14-38`、`apps/studio/src/common/appdb/transformers/Transformers.ts:10-24`。
- 关键事实：加密只保护「静态存储」。renderer 为了发 `conn/create` 仍会拿到解密后的 config，再经 MessagePort 传给 utility；因此桌面端「renderer 不接触密码」并不成立。证据：`apps/studio/src-commercial/backend/handlers/connHandlers.ts:137-185`。
- 只读模式在执行边界强制：`driverExecuteSingle/Multiple` 先 `safelyIdentify` 语句，再 `isAllowedReadOnlyQuery`，只允许 `LISTING/INFORMATION`；但内部保留 `options.overrideReadonly` 逃生门。证据：`apps/studio/src/lib/db/clients/BasicDatabaseClient.ts:639-661,692-701`、`apps/studio/src/lib/db/clients/utils.ts:376-381`。
- 这条「按语句分类判只读」的路径对 Nop 只能作为**第二道防线**：`sql-query-identifier` 是启发式解析，不能替代 EQL 编译器的 AST 级校验与数据权限。
- 其他值得借鉴的细节：复制为 TSV 时开启 `escapeFormulae` 防公式注入；SQLite 运行时扩展默认关闭（加载即执行任意 native code）；支持 PIN 锁、空闲/锁屏/休眠自动断开。证据：`apps/studio/src/components/editor/ResultTable.vue:1084-1088`、`apps/studio/default.config.ini:18-21`、`apps/studio/src/backend/lib/security.ts:30-67`。

## 11. 测试与工程质量

- 测试规模：`tests/vitest/unit` 126 个文件、`tests/vitest/integration` 22 个、`e2e` 44 个文件（含 page object）、`apps/ui-kit/tests` 13 个。证据：目录统计（`apps/studio/tests`、`apps/ui-kit/tests`）。
- 编辑器测试是「契约级」的，不只是快照：
  - `ui-kit/tests/unit/sql-text-editor/completions.spec.ts` 从上游 lang-sql 测试 fork，覆盖表/列/带引号/schema 限定补全。
  - `conventions.spec.ts` 把「每个数据库的引用与大小写约定」写成 assumption matrix，tests-first 地锁住行为。证据：`apps/ui-kit/tests/unit/sql-text-editor/conventions.spec.ts:1-45`。
  - `querySelection.spec.ts`、`SqlTextEditor.spec.ts` 覆盖切句与编辑器 API。
- CI 强制新测试用 Vitest：PR 里新增 Jest spec 会被 `bin/check-for-new-jest-tests.sh` 拒绝；同时跑 lint、unit、分片 integration、Playwright e2e。证据：`.github/workflows/studio-test.yml:76-89,119-147,211`、`.github/workflows/e2e-tests.yml:70-127`。
- 测试运行方式有历史包袱：unit 用 Electron 以 Node 模式跑 Vitest（为了 native module ABI），`vitest.shared.mjs` 里解释 `pool: 'forks'` 与 `fileParallelism:false`。证据：`apps/studio/vitest.shared.mjs:56-67`、`apps/studio/package.json:18`。
- 仍有旧 CodeMirror 5 测试（`tests/vitest/unit/codemirror/completions.spec.js`）与 CM6 测试并存，说明迁移未完全收尾；这一点不应照搬到 Nop。

## 12. 对 Nop EQL 在线编辑器的启示

### 12.1 目标形态

- 与 Nop 现状的逐项差距（Flux 方言硬编码、补全只有 `alias.column`、结果面板无分页/导出）见 [Flux SQL 编辑器现状与 EQL 差距](./2026-10-18-flux-sql-editor-current-state-and-eql-gap.md)；本节只给由 Beekeeper 反推的取舍。

浏览器（flux-code-editor/CM6）只做「编辑 + 补全 + 诊断展示」；Nop 后端新增 EQL Console API，负责元数据、编译校验、执行、取消、分页、导出、历史。执行模型建议：

1. `POST /eql/query` 创建 job，返回 `queryId`（可带客户端生成的 idempotency key）。
2. `POST /eql/query/{id}/execute` 或 SSE `GET /eql/query/{id}/stream` 执行并推送列元数据/行块/结束状态。
3. `POST /eql/query/{id}/cancel` 取消。
4. `POST /eql/validate` 只编译不执行，返回 `{line,column,code,message}`。
5. `POST /eql/explain` 返回执行计划树/文本。

### 12.2 Adopt（直接吸收的设计）

| # | 设计 | 为什么适合 Nop | 证据 |
|---|---|---|---|
| A1 | CM6 薄封装 + 扩展组合，编辑器作为独立包 | flux 已有 CM6 基础，可把 SQL 扩展做成可组合 profile | `apps/ui-kit/lib/components/sql-text-editor/SqlTextEditor.ts:20-65` |
| A2 | 语句切分 + 当前语句高亮 + 运行全部/当前/选区 | EQL 同样支持多语句脚本；当前语句高亮显著降低误执行 | `querySelection.ts:140-200`、`TabQueryEditor.vue:1644-1675` |
| A3 | 懒加载列补全（`columnsGetter`）与别名解析 | 实体多、列多，不能一次全量下发；别名解析可复用到 EQL 表别名 | `sqlContextComplete.ts:73-131`、`getAliases.ts:32-67` |
| A4 | 方言描述符驱动引号/大小写/关键字 | EQL 需要可配置关键字、引用、大小写，避免硬编码 | `customDialects.ts:17-100`、vendor `complete.ts:283-304` |
| A5 | `queryId` + `execute/cancel` 执行句柄 | Web 端同样需要「执行中可取消」，且要跨请求保持 | `queryHandlers.ts:11-42`、`ElectronUtilityConnectionClient.ts:89-100` |
| A6 | 结果集多结果切换 + 行列元数据 + 复制/导出 | EQL 多语句、列类型展示、CSV/JSON 复制是刚需 | `QueryEditorStatusBar.vue:11-60`、`ResultTable.vue:1005-1090` |
| A7 | 虚拟化实体树 + 懒加载列 + pin/隐藏/过滤 | EQL 实体树可能上千节点，必须虚拟化 | `VirtualTableList.vue:16-25,221-247` |
| A8 | 标签持久化 + 重开最近关闭 + 未保存确认 | 工作台体验基线，直接决定「像不像 DBeaver」 | `TabModule.ts:167-243`、`CoreTabs.vue:1038-1052` |
| A9 | 运行历史与版本审计分离 | 运行历史用于重跑；版本审计用于收藏查询的 diff/恢复 | `TabQueryEditor.vue:1815-1831`、`QueryAudit.ts:10-95` |
| A10 | 只读模式在执行边界按语句类型拦截 | 可作为 Nop 的第二道防线，配合 EQL AST 校验 | `BasicDatabaseClient.ts:639-661` |
| A11 | 测试先行锁住「方言约定矩阵」 | EQL 方言配置多，必须有 convention tests 防回归 | `conventions.spec.ts:1-45` |

### 12.3 Adapt（需要改造后使用）

| # | Beekeeper 做法 | Nop 改造 |
|---|---|---|
| D1 | MessagePort + utility 进程 | 改为 HTTP/WebSocket/SSE + 服务端 job；`queryId` 语义保留，状态存服务端（Redis/DB） |
| D2 | `columnsGetter` 从本地 Vuex 表缓存取列 | 改为 EQL 元数据服务：按 querySpace/实体懒加载，返回属性类型、关联、主键、可空、枚举 |
| D3 | 5 万行截断 + 前端全量持有 | 服务端强制 `maxRows` + 分页/游标；导出走独立流式接口，不占浏览器内存 |
| D4 | 每 tab reserve 一条 DB 连接 + 本地事务 | 服务端会话/连接池绑定；EQL 编译期已拒绝 commit/rollback，事务语义需单独设计 |
| D5 | SQLite trigger 维护 append-only 审计 | Nop 历史/审计表 + 版本号；用应用层或数据库约束保证 append-only |
| D6 | 运行历史按文本去重、excerpt 250 | 按规范化 EQL + 数据源 + 用户去重；excerpt 可更长，另存 AST hash |
| D7 | 结果集反推表/主键做内联编辑 | 仅在「单实体、含主键、无聚合」时开放；用实体 id + version 做乐观锁 |
| D8 | `default.config.ini` 作为全局配置 | 改为 Nop 配置模型：平台默认 + 数据源级 + 用户级覆盖；方言能力随连接返回 |
| D9 | 错误位置只支持 PostgreSQL `position` | 用 EQL parser/compiler 的行列信息，统一所有方言 |
| D10 | 桌面文件导出 | 服务端生成临时文件 + 签名下载 URL；保留 CSV/JSON/XLSX/Markdown |

### 12.4 Reject（不应照搬）

| # | 做法 | 拒绝理由 |
|---|---|---|
| R1 | Electron 桌面壳、`sandbox:false`、`@electron/remote` | Nop 是 Web 多租户；这些设置扩大攻击面，且与交付形态无关 |
| R2 | renderer 持有解密后的 DB 凭据 | 浏览器绝不应接触数据源密码；凭据只进服务端密钥管理 |
| R3 | 直接复制 GPLv3/商业源码 | 社区版 GPLv3、`src-commercial` 为商业 EULA；只能做概念借鉴与独立实现，需法务确认 |
| R4 | 用 `sql-query-identifier` 之类启发式解析做安全判定 | 只读/权限必须由 EQL AST + 数据权限决定；启发式只能当 UX 提示 |
| R5 | 依赖数据库返回的错误位置做编辑器定位 | 只有 PostgreSQL 有 `position`；EQL 应自行产出诊断 |
| R6 | 对任意 SQL 无条件执行、无行数/超时/并发上限 | 桌面单用户可接受，Web 多租户不可接受 |
| R7 | 结果集内联编辑依赖「单表 + 主键」反推 | EQL 的 join/导航查询大量存在，反推模型不成立 |
| R8 | 把「取消」当成所有驱动都支持 | 注册表明确有库不支持；UI 必须按能力显示，服务端要有兜底超时 |

### 12.5 落地优先级（建议）

- **P0**：EQL 方言描述符 + 后端 validate/execute/cancel 协议 + 实体树元数据 API + CM6 补全（实体路径）。
- **P1**：结果集分页/列元数据/复制导出 + 运行历史 + 多标签 + EXPLAIN 文本视图。
- **P2**：查询收藏/版本审计、内联编辑（单实体 + 乐观锁）、执行计划可视化、多数据源对比。

## 13. 关键证据清单

1. `apps/studio/package.json:208` — Electron 44.3.0，不是 `CLAUDE.md:18` 所写的 31.7.3。
2. `apps/studio/src-commercial/entrypoints/main.ts:58-66` — 主进程 fork `utility.js` 承载数据库能力。
3. `apps/studio/src-commercial/entrypoints/main.ts:242-253` — 每窗口一对 `MessageChannelMain` 端口 + `sId` 会话隔离。
4. `apps/studio/src/lib/utility/UtilityConnection.ts:110-139` — MessagePort 上的 request/reply RPC。
5. `apps/studio/src-commercial/entrypoints/utility.ts:81-102` — 统一 handler 注册表，连接/查询/导出均在此。
6. `apps/studio/src-commercial/backend/handlers/connHandlers.ts:370-378` — `conn/query` 创建并缓存 `CancelableQuery`，返回 `queryId`。
7. `apps/studio/src/handlers/queryHandlers.ts:20-30` — 执行后按 `maxResults` 截断并保留 `totalRowCount`。
8. `apps/studio/src/handlers/queryHandlers.ts:33-42` — `query/cancel` 通过 `queryId` 取消并清理。
9. `apps/ui-kit/lib/components/sql-text-editor/SqlTextEditor.ts:20-65` — SQL 编辑器是 TextEditor 的子类，只追加 SQL 扩展。
10. `apps/ui-kit/lib/components/sql-text-editor/extensions/sqlContextComplete.ts:60-66` — 仅在 `FROM/JOIN` 后空格触发补全。
11. `apps/ui-kit/lib/components/sql-text-editor/extensions/sqlContextComplete.ts:73-131` — 内置补全失败时按上下文懒加载列。
12. `apps/ui-kit/lib/components/sql-text-editor/extensions/querySelection.ts:140-200` — 语句切分/当前语句/高亮的状态字段。
13. `apps/ui-kit/lib/components/sql-text-editor/customDialects.ts:91-100` — `languageId` → CodeMirror 方言的唯一映射。
14. `apps/ui-kit/lib/components/sql-text-editor/extensions/vendor/@codemirror/lang-sql/src/complete.ts:283-304` — 标识符引用与转义规则。
15. `apps/ui-kit/lib/components/sql-text-editor/extensions/vendor/@codemirror/lang-sql/src/complete.ts:467-485` — 关键字大小写 `preserve/upper/lower`。
16. `apps/studio/src/components/TabQueryEditor.vue:1644-1675` — 选区 → 当前语句 → 全量的运行回退。
17. `apps/studio/src/components/CoreTabs.vue:76-82` — 标签用 `v-show` 保活编辑器状态。
18. `apps/studio/src/store/modules/TabModule.ts:167-243` — 标签按连接/工作区持久化、重开、临时 id。
19. `apps/studio/src/components/sidebar/core/table_list/VirtualTableList.vue:16-25,221-247` — 大库虚拟列表 + 展开懒加载列。
20. `apps/studio/src/components/editor/ResultTable.vue:1005-1058` — CSV/JSON/XLSX/Markdown 导出。
21. `apps/studio/src/components/editor/ResultTable.vue:1060-1090` — 剪贴板 TSV/JSON/MD，`escapeFormulae` 防公式注入。
22. `apps/studio/src/lib/export/export.ts:126-158,171-200` — 导出用 `BeeCursor` 分块读，不占全量内存。
23. `apps/studio/src/lib/db/clients/postgresql.ts:791-850` — 取消通过 `pg_cancel_backend(pid)`。
24. `apps/studio/src/shared/lib/dialects/models.ts:143-220` — `DialectData` 能力字段与 `disabledFeatures`。
25. `apps/studio/src/shared/lib/dialects/sqlite.ts:49-77` — 用 `disabledFeatures` 表达 SQLite 能力缺失。
26. `apps/studio/src/lib/db/clients/BasicDatabaseClient.ts:197-296` — 结果列 → 表/主键/可编辑性的映射。
27. `apps/studio/src/lib/db/clients/BasicDatabaseClient.ts:639-661` — 只读模式在执行入口按语句类型拦截。
28. `apps/studio/src/common/appdb/models/QueryAudit.ts:10-95` — 收藏查询的版本审计与恢复。
29. `apps/studio/src/migration/20260526_create_query_audits.js:1-60` — SQLite trigger 维护 append-only 审计。
30. `apps/studio/src/background/WindowBuilder.ts:52-58,75` — `contextIsolation:true` 但 `sandbox:false` + remote。
31. `apps/studio/src/common/encryption_key.ts:14-38` — 本地密钥文件与默认包裹 key。
32. `apps/ui-kit/tests/unit/sql-text-editor/conventions.spec.ts:1-45` — 方言引用/大小写约定矩阵，tests-first。
33. `apps/studio/default.config.ini:39-70` — 5 万行上限、自动补全配置、表浏览 pageSize=100。
34. `apps/studio/default.config.ini:357-361` — 主/次运行与取消相关快捷键。
35. `apps/studio/src/lib/db/clients/bigquery.ts:261-281,607-624` — 唯一近似 EXPLAIN 的 Dry Run 成本估算。

## 14. Open Questions

- [ ] Beekeeper 的 SQL 结果是否在 5 万行截断前做过「先 count 再取」？当前只看到 `totalRowCount` 回填，未见独立 count 查询；大结果集的耗时归因未确认。
- [ ] `disabledFeatures: ['cancelQuery']` 在 UI 层是否真正消费？grep 只看到定义，未看到读取点；需确认是否存在「UI 显示取消但驱动不支持」的错配。
- [ ] 查询结果的 `NgQueryResult.totalRowCount` 对各驱动是否都准确？SQLite/MySQL 的 rowCount 语义可能不同，未逐一验证。
- [ ] 云端工作区的查询审计/权限模型未深入：`src-commercial` 的 Cloud 控制器只做了粗读，无法确认多用户冲突合并的完整策略。
- [ ] Beekeeper 的 GPLv3 与 commercial 双许可对「参考其交互设计并在 Nop 独立实现」的边界，需要法务给出结论；本报告只做技术分析。
- [ ] 若 Nop 采用服务端 job + SSE，EQL 编译期的参数绑定/租户过滤/逻辑删除注入应在哪一层固化（Console API vs `IOrmSession`），需与后端能力调研合并裁定。
- [ ] EXPLAIN 在 EQL 侧应复用 ORM 的哪种计划表示（SQL 文本 / 执行计划树 / 索引命中），当前未确认已有统一抽象。
