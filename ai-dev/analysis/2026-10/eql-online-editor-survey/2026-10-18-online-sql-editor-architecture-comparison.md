# 在线 SQL 编辑器架构横向对比与对 EQL 工具的取舍

> Status: resolved
> Date: 2026-10-18
> Scope: 7 个开源在线/桌面 SQL 编辑器（CloudBeaver、DbGate、Chat2DB、SQLPad、Beekeeper Studio、Querybook、Hue）的架构对比，以及对 Nop EQL 在线编辑器的采纳/改造/拒绝结论
> Conclusion: 七个实现共同收敛到同一骨架——**薄前端（编辑器只做装配/展示）+ 服务端语言与执行网关 + 数据化方言描述符 + 语句级执行状态机 + 元数据/结果集缓存与分页**；EQL 应直接采用该分层，把「编译校验 + 编译 SQL 预览 + EXPLAIN + 服务端强制护栏」做成多数开源实现缺失的差异化能力；所有实现只可借鉴架构，不可复制源码（GPL/自定义许可）。

## 1. 调研对象与方法

- 方法：每个项目在 `~/sources` 下只读源码（depth-1 clone），按统一 14 章模板产出独立报告；本文件只做横向对比与取舍，证据以各项目报告内的 `path:line` 为准。
- 统一关注点：架构边界、SQL 编辑器/补全、元数据与缓存、执行模型、结果集、查询管理、方言抽象、权限安全、测试。

| 项目 | HEAD | License | 形态/技术栈 | 一句话结论（详见报告） |
|---|---|---|---|---|
| [CloudBeaver](./2026-10-18-cloudbeaver-analysis.md) | 817a069 | Apache-2.0 | Web（Java 服务端 + TS/React/MobX 前端，OSGi 插件） | 把语言智能收敛为「每连接方言描述符 + 服务端语言服务 API」，前端只做 CodeMirror 装配与结果呈现 |
| [DbGate](./2026-10-18-dbgate-analysis.md) | 8e987b2 | GPL-3.0 | Web + Electron（Node 后端 + Svelte 前端） | 每标签页独立会话进程 + 结果集物化为可随机读数据集 + SSE 只通知、HTTP 取数 |
| [Chat2DB](./2026-10-18-chat2db-analysis.md) | 7379139e | 修改版 Apache（`LicenseRef-Chat2DB`） | Java 后端 + React（JCEF/Web/CLI/MCP 四种形态） | 后端多阶段补全流水线 + 声明式方言能力配置 + 统一 candidate 契约 |
| [SQLPad](./2026-10-18-sqlpad-analysis.md) | 57cc866 | MIT | Node + React（已归档） | batch/statement 执行模型 + 运行记录与结果数据分离 + 驱动能力声明式注册 |
| [Beekeeper Studio](./2026-10-18-beekeeper-studio-analysis.md) | f7ff968 | GPLv3 + 商业 EULA 双许可 | Electron + Vue（执行在独立 utility 进程） | 以语句为执行单元 + 统一方言描述符驱动编辑/执行/结果；查询以 `queryId` 句柄 execute/cancel |
| [Querybook](./2026-10-18-querybook-analysis.md) | 3f26532 | Apache-2.0 | Python/Flask + React（Celery/Redis/ES） | 执行记录闭环 + 异步任务 + 结果对象存储 + 权限/审计；方言能力应收敛为单一 Profile |
| [Hue](./2026-10-18-hue-analysis.md) | 65d9e48 | Apache-2.0 | Django + React/Vue 混合 | `SqlExecutable` 语句级执行状态机 + 客户端语句定位/选中执行 + 多引擎方言分层 |

## 2. 横向对比

### 2.1 架构边界与执行位置

| 维度 | CloudBeaver | DbGate | Chat2DB | SQLPad | Beekeeper | Querybook | Hue |
|---|---|---|---|---|---|---|---|
| 执行位置 | 服务端 | 服务端子进程 | 服务端/JCEF 双栈 | 服务端 | Electron utility 进程 | Celery worker | 服务端 |
| 前端是否解析 SQL | 否（服务端提案） | 是（正则分词，仅补全） | 部分（本地 provider + 后端补全双路） | 是（Ace 关键字启发式） | 是（CM6 + 别名解析） | 是（CM6 + 服务端 lint） | 是（方言 parser） |
| 多标签/会话隔离 | 每编辑器 execution context | 每标签页独立 DB 会话 | consoleId + lane 锁 | 浏览器标签 | 每 tab 预留连接 | DataDoc 单元格 | Notebook/SavedQuery |
| 通信 | GraphQL + WS | REST + SSE | REST（Web 一次性 POST） | REST + 轮询 | MessagePort RPC | REST + Socket.IO | REST + 轮询/WS |

**结论**：EQL 的执行与语义都在 Java 侧，**没有理由把 SQL 解析/语义判断放前端**。前端做「装配 + 展示 + 轻量上下文（别名绑定/当前语句定位）」是各实现中唯一都不会出错的分工。

### 2.2 编辑器与补全

| 维度 | CloudBeaver | DbGate | Chat2DB | SQLPad | Beekeeper | Querybook | Hue |
|---|---|---|---|---|---|---|---|
| 编辑器内核 | CodeMirror | Ace | Ace（CanvasTable） | Ace | CodeMirror 6 | CodeMirror | Ace |
| 补全来源 | 服务端提案 | 正则 + 单层别名 | 后端 11 阶段流水线（ANTLR ATN caret） | 关键字启发式 | CM6 + `columnsGetter` 懒加载 | CM6 + 服务端 lint | 方言 parser + 元数据 |
| 格式化 | 服务端 | sql-formatter（不接方言） | 后端 sql-formatter | 服务端（无方言） | 惰性/无 | 无 | 引擎侧 |
| 语句定位/选中执行 | 服务端分段 + 客户端区间 | 有切分器（editor/stream/script） | 有切分器 | 有切分 | 当前语句高亮 + 三种运行模式 | 单元格 | 客户端语句定位 + 选中 |

**结论**：EQL 的补全应走 `entity-path`（实体/属性/关联/集合算子），并把「元数据下发」与「补全上下文计算」拆开；Chat2DB 的 `CodeCompletionCore`（ANTLR ATN caret）是唯一能实现「基于正式语法」补全的范式，但其为单方言写 64 个类的方式不可取——EQL 只有一份 grammar，方言差异只在生成 SQL 与函数集。

### 2.3 元数据与缓存

| 维度 | CloudBeaver | DbGate | Chat2DB | SQLPad | Beekeeper | Querybook | Hue |
|---|---|---|---|---|---|---|---|
| 元数据模型 | catalog/schema/table | structure 快照 | 数据源/库/schema/表/列 | database/schema/table/column | 表/列 + `columnsGetter` | 元数据服务 + ES | 数据源/库/表/列 |
| 缓存键 | 资源 + offset page | loader + reloadTrigger | (dataSourceId, db, schema, table) | 渲染后连接 uuidv5 | 本地 Vuex 表缓存 | ES（权限副本滞后） | 浏览器 + TTL |
| 失效 | 资源失效传播 | 细粒度 reloadTrigger | 配置变更整体失效 | 固定 TTL 1 天 | 手动/事件 | 手动/定时同步 | TTL |
| 大模型处理 | 懒加载 offset page | 懒加载 | 懒加载 | 全量 | 虚拟化树 + 懒加载 | 服务端 | 懒加载 |

**结论**：EQL 元数据必须是「数据源 → 实体 → 属性/关联」而非物理表；缓存键要含「模型版本 + querySpace」，失效由 ORM 模型变更事件驱动，TTL 只做兜底。大模型必须懒加载/按 querySpace 过滤。

### 2.4 执行模型（同步/异步、取消、超时、分页）

| 维度 | CloudBeaver | DbGate | Chat2DB | SQLPad | Beekeeper | Querybook | Hue |
|---|---|---|---|---|---|---|---|
| 同步/异步 | async task + WS | 会话进程 + SSE 通知 | Web 一次性 POST | batch 立即返回 + 轮询 | 同步 execute（可取消） | Celery 异步 | execute + 轮询 |
| 取消 | `asyncTaskCancel` | kill 会话进程 | `Statement.cancel` + `Future.cancel`（Web 端 abort 不达服务端） | 驱动能力声明 | `queryId` cancel | revoke/abort + owner 校验 | cancel API |
| 超时 | 查询超时设置 | 无查询级超时 | 无 `setQueryTimeout` | 有限 | 无 | Celery soft timeout | 有 |
| 分页 | offset/limit + 结果集配额 | offset/limit 随机读 | `setMaxRows` + 游标跳过（深翻页 O(offset)） | 一次性全量 | 一次性 + 5 万行截断 | 结果读取窗口 | 100 行/滚动拉取 |
| 多语句 | 服务端分段 | 按用途 splitter | 有切分器 | batch/statement | 有切分 | 单语句 + 单元格 | 语句级状态机 |

**结论**：EQL 编译期本身只接受单语句（`EqlCompiler.java:57-58`），v1 应采用「同步执行 + 服务端超时兜底 + `LongRangeBean` 分页 + `maxRows+1` 截断」；异步任务/结果物化留到 v2（当结果集或耗时证明必要时）。

### 2.5 结果集、导出与查询管理

- 7 个实现一致把结果拆为「列元数据 + 行数据 + 统计」；差异在行数据是否物化（CloudBeaver/DbGate/Querybook 物化可随机读，SQLPad 可插拔存储，Beekeeper/Chat2DB 前端虚拟表格）。
- 导出一致采用「从已存结果转换」而非重跑（SQLPad/DbGate/Hue/Chat2DB），避免「看到的结果 ≠ 导出的结果」。
- 历史/保存普遍分离：历史=执行记录（可重跑），保存查询=文档（可共享/版本）；Querybook 用 `QueryExecution/StatementExecution` 闭环，Hue 用 UUID 关联，DbGate/Chat2DB 用 append-only 历史。
- 权限/审计：Querybook/Hue/DbGate 在服务端做执行级权限与审计；SQLPad/Chat2DB/Beekeeper 的安全边界较弱（SQLPad 无 SQL 校验/只读强制，Chat2DB 无查询超时，Beekeeper 桌面单用户信任模型）。

**结论**：EQL v1 用「执行快照」历史（EQL/编译 SQL/参数脱敏/耗时/行数/错误码），导出「从当前结果集转换」；服务端必须强制行数/超时/只读，不得把安全交给前端。

### 2.6 方言抽象机制（本调研的核心问题）

| 实现 | 方言抽象方式 | 配置化程度 | 主要问题 |
|---|---|---|---|
| CloudBeaver | `SQLDialectInfo` 服务端描述符 → CodeMirror `SQLDialect.define`；能力位由 `DBCQueryPlanner` 探测 | **高**（描述符驱动） | 依赖 DBeaver 类层次与 OSGi |
| DbGate | Driver 能力布尔 + Dialect 数据对象 + 按用途 splitter + 版本覆盖 | 中（数据对象但前端/后端各一份） | 「代码即方言」、易漂移 |
| Chat2DB | `DBConfig` JSON 模板 + Java 覆写 + 前端 TS 能力矩阵；36 个插件 ServiceLoader | 中（有配置但未充分落地） | 前后端两份能力矩阵（SSOT 反面案例） |
| SQLPad | 驱动模块声明 `fields/Client/asynchronous`，加载时 validate | 中（能力声明式） | 无方言语法差异建模 |
| Beekeeper | 单一 `DialectData` + langId 映射 + 标识符方言 + CLIENTS | 中 | 能力散在 4 张表 |
| Querybook | Query Engine 插件 + feature flags | 低 | 方言能力与业务 flag 混杂 |
| Hue | 后端 connector 类 + 前端 dialect parser + 部分声明式 `dialect_properties` | 中低 | `dialect_properties` 声明后未消费 |

**结论（对 EQL 最重要）**：只有 CloudBeaver 做到了「**服务端单一描述符 → 前端编辑器可直接消费**」。EQL 应把方言抽象成单一数据化 `EqlDialectProfile`，由后端产出/前端消费，覆盖：关键字、函数、引号/大小写、补全模式、格式化策略、分页语法、EXPLAIN/取消/多语句能力位；**不要**前后端各存一份，也不要「代码即方言」。

### 2.7 安全、权限与测试

- 服务端强制是共识：执行前鉴权、执行中护栏（行数/超时/只读）、执行后审计（Querybook/Hue/DbGate 最完整）。
- 桌面型（Beekeeper/Chat2DB Desktop）依赖单用户本机信任边界，不适合直接复制到 Web 多租户。
- 测试上值得借鉴：DbGate 的权限/会话测试密度、Beekeeper 的「方言约定矩阵」测试、Querybook 的异步执行测试、Chat2DB 的补全测试（30 个）。

## 3. 共性结论（10 条）

1. **薄前端**：编辑器不解析语义、不维护 schema、不计算权威补全；前端只做装配、上下文计算与展示。（CloudBeaver 最纯粹；也是 EQL 唯一合理分工）
2. **方言 = 数据描述符，不是代码分支**：所有项目都在朝数据化演进，但只有 CloudBeaver 做到前后端共用一份。
3. **执行以语句为最小单元**：多语句是 batch/脚本；状态、耗时、错误按语句记录。
4. **执行必须可取消/超时/限行**：弱实现（SQLPad/Beekeeper/Chat2DB）是反面教材。
5. **结果 = 列元数据 + 行 + 统计**，行数据应可分页/物化/截断，导出从已存结果转换。
6. **元数据按作用域缓存并显式失效**：模型版本/连接是缓存键的一部分；TTL 只兜底。
7. **历史与保存查询分离**：历史=执行快照，保存=文档/版本。
8. **补全上下文需要语句定位**：当前语句/选区/别名绑定是「像不像 DBeaver」的关键。
9. **EXPLAIN/编译校验普遍缺失**：7 个实现中只有 Chat2DB/DbGate 有执行计划相关能力，且多为薄封装——这是 EQL 的差异化机会。
10. **License 与安全模型不可照搬**：GPL/自定义许可项目只能借鉴架构；凭据绝不下发浏览器。

## 4. 争议点与裁定

| 争议 | 可选方案 | 裁定 | 理由 |
|---|---|---|---|
| 补全在前端还是服务端 | A 服务端提案（CloudBeaver）；B 前端下发元数据自算（Beekeeper/DBGate 改良）；C 混合：前端装配 + 服务端 validate | **B+C**：v1 前端用 `EntitySchema` 做 entity-path 补全（零往返、可离线），服务端只做 `validate`；若模型过大再评估服务端提案 | EQL 元数据由 `IOrmModel` 一次性可得且可版本化；每键往返会引入延迟与状态同步问题；Chat2DB 的 650 行 ATN 补全内核在 EQL 的单 grammar 场景下性价比低 |
| 执行同步还是异步 | A 同步 execute + 超时；B 异步 job + 事件（CloudBeaver/Querybook/DbGate） | **v1 A，v2 视需求 B** | EQL 查询通常为 ORM 查询，v1 的 `LongRangeBean` + 超时已够；异步需要任务表/事件通道/取消协议，成本高 |
| 结果是否物化 | A 一次返回 rows + limit；B 服务端物化可随机读 | **v1 A，v2 评估 B** | v1 用 `maxRows+1` 截断 + 分页；物化引入存储/清理/多实例问题 |
| 多标签是否独立会话 | A 每 tab 独立 DB 会话（DbGate/Beekeeper）；B 前端 tab + 共享服务端上下文 | **v1 B** | EQL 编译期拒绝 commit/rollback，v1 无事务状态需隔离；tab 状态（文本/结果/历史）由前端 scope 维护 |
| 历史存储位置 | A 浏览器本地；B 服务端 DB/共享 | **v1 A，v2 B（可选）** | v1 不引入新 ORM 模型与迁移；团队共享需求出现后再走 plan-first 加表 |
| 是否提供 DML | A 完全只读；B 只读默认 + 显式写开关（CloudBeaver/Chat2DB 危险确认） | **B**：默认只读，`allow-write` + 每次确认 + `@Auth` 三重门控；DDL 由 grammar 结构性排除 | EQL 的定位是「测试工具」，允许 DML 才能测试 insert/update/delete 语义；但必须强门控 |

## 5. 对 EQL 的采纳清单（映射 roadmap）

### Adopt（直接采纳）

1. 服务端语言/执行网关边界（CloudBeaver）→ WI-12/WI-14/WI-15。
2. 单一数据化方言描述符（CloudBeaver/Beekeeper/Querybook 共识）→ D1、WI-04、WI-05、WI-07。
3. 语句级执行状态与「运行当前语句/选区」（SQLPad/Hue/Beekeeper）→ WI-10、WI-22。
4. 结果列元数据 + 截断/分页 + 从已存结果导出（SQLPad/DbGate/CloudBeaver）→ WI-09、WI-15、WI-21。
5. 元数据按作用域缓存 + 显式失效（Chat2DB/DbGate/SQLPad）→ WI-06、WI-13。
6. 执行快照历史（Querybook/Hue）→ WI-17。
7. 服务端强制护护栏 + 执行审计（Querybook/Hue/DbGate）→ D6、WI-12、WI-15。

### Adapt（改造后使用）

1. 服务端补全提案 → 改为「前端 entity-path 补全 + 服务端 validate/metadata」。
2. 异步任务 + 事件 → v2 再评估；v1 同步 + 超时。
3. 结果物化/随机读 → v2 可选 `ResultStore`。
4. 多标签独立 DB 会话 → 前端 tab 状态 + 服务端无事务绑定。
5. 脚本 front matter（DbGate）→ 改为页面 schema 的显式配置，不做注释解析。
6. Querybook DataDoc notebook → 作为可选视图，不作唯一模型。
7. Hue `dialect_properties` → 升级为前后端共同消费的能力位。

### Reject（不采用）

1. 复制源码（GPL/自定义许可：DbGate、Beekeeper、Chat2DB）。
2. 前端解析 SQL 做权威语义或安全判定（所有实现的共同教训）。
3. 前后端各存一份能力矩阵（Chat2DB 反面案例）。
4. 桌面单用户信任模型（Beekeeper/Chat2DB Desktop）与凭据下发浏览器。
5. 把多标签做成浏览器标签（SQLPad）或 DataDoc 唯一模型（Querybook）。
6. 无超时/无限行/无只读强制（SQLPad/Beekeeper/Chat2DB 的执行器）。
7. 引入第二套编辑器/前端框架（Hue 混合栈、Chat2DB Ace、Querybook CM5 包袱）。

## 6. 分期建议

| 阶段 | 范围 | 对应里程碑 |
|---|---|---|
| v1 | 单语句编辑/校验/执行/结果 + 实体树 + 配置化 EQL profile + 安全门控 | M1—M3 |
| v1.5 | 多标签状态、历史/收藏、分页/导出、错误定位、EXPLAIN（按方言） | M3—M4 |
| v2（deferred） | 异步执行/结果物化、服务端保存查询与共享、多 querySpace 切换、NL2EQL/可视化 | WI-28 裁定 |

## 7. 局限与未验证

- 全部外部仓库为 depth-1 clone，未构建、未运行；所有行为结论来自静态源码阅读，运行时行为（如取消是否真正到达驱动）以各报告标注为准。
- CloudBeaver 的 DBeaver core 方言类层次不在本 clone 中，相关判断基于 Web 侧描述符与服务端封装（报告 §14 已列 Open Question）。
- License 结论仅供技术选型，最终合规需法务确认。
- 各项目报告的 `path:line` 已在各自报告中校验；本文件不重复引用原始行号。

## 8. References

- 独立报告：[CloudBeaver](./2026-10-18-cloudbeaver-analysis.md)、[DbGate](./2026-10-18-dbgate-analysis.md)、[Chat2DB](./2026-10-18-chat2db-analysis.md)、[SQLPad](./2026-10-18-sqlpad-analysis.md)、[Beekeeper Studio](./2026-10-18-beekeeper-studio-analysis.md)、[Querybook](./2026-10-18-querybook-analysis.md)、[Hue](./2026-10-18-hue-analysis.md)
- 现状与差距：[Flux SQL 编辑器现状与 EQL 差距](./2026-10-18-flux-sql-editor-current-state-and-eql-gap.md)
- 后端能力：[Nop Entropy EQL 后端能力调研](./2026-10-18-nop-entropy-eql-backend-capability-survey.md)
- Roadmap：[EQL 在线编辑与测试工具 Roadmap](../../backlog/eql-online-editor-roadmap.md)
