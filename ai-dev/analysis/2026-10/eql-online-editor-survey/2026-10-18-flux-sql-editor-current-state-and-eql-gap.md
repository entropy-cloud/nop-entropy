# Flux SQL 编辑器现状与 EQL 在线编辑器差距分析

> Status: resolved
> Date: 2026-10-18
> Scope: `nop-chaos-flux` `packages/flux-code-editor` SQL 能力 / `nop-chaos-next` 宿主 / `nop-entropy` 三仓交付链路；对照目标「EQL 在线编辑与测试工具」
> Conclusion: Flux 已具备「字段级 SQL 编辑 + 配置化执行 + 结果预览」的骨架，但它是一个**字段组件**而不是查询工作台：EQL 方言、实体元数据补全、后端校验、结果集分页/导出、查询历史、工作台壳层、后端执行 API 全部缺失；改造路径应是「通用化 SQL-like 方言档案（profile）+ 新增工作台页面 + 新增后端 EQL 控制台 API」，而不是在 `flux-code-editor` 内硬编码 EQL。

## 1. 背景与调研问题

- 目标产品：为 EQL（Nop 的实体查询语言，标准 SQL + 实体属性导航自动 JOIN）提供在线编辑与测试工具，交互对标在线版 DBeaver：数据源/实体树、编辑器补全、执行、结果表格、历史、导出、校验、多标签。
- 约束：EQL 支持必须尽量配置化；前端实现位于 `nop-chaos-flux`，宿主为 `nop-chaos-next`，构建产物 sync 到 `nop-entropy`。
- 本报告回答：**现有 SQL 编辑器能复用多少、EQL 差距在哪、改造应落在哪一层**。架构选型与工作项拆分见 `ai-dev/backlog/eql-online-editor-roadmap.md`。

## 2. Flux SQL 编辑器现状（源码事实）

### 2.1 组件定位

- `code-editor` 是**字段级** renderer（`type: 'code-editor'`），12 种语言由 `language` 驱动，不为 SQL 单独注册 renderer type。见 `nop-chaos-flux:docs/components/code-editor/design.md`「组件定位」与「3. Flux 中的 renderer/type 定义」。
- 设计文档明确「SQL 执行结果预览属于组件内 feature state，不提升为新的 page/host 级协议」，并声明组件「不拥有平台级导入/bridge/session 协议」。见 `docs/components/code-editor/design.md` §7、§9。

### 2.2 已实现能力（可复用基线）

| 能力 | 实现位置 | 说明 |
|---|---|---|
| SQL 语言模式 | `packages/flux-code-editor/src/extensions/base.ts:144-153` | `languageRegistry` 注册 `sql`，按 `sqlDialect` 选择 CodeMirror 内置方言 |
| 5 种内置方言 | `packages/flux-code-editor/src/extensions/base.ts:201-219` | `standard/mysql/postgresql/sqlite/mssql` 写死在 switch 中；`SQLDialect` 联合类型见 `src/types.ts:173` |
| 表/别名补全 | `src/extensions/sql/completion.ts:107-166` | 用正则解析 `FROM`/`JOIN` 别名（`:83`、`:94`），单点 `.` 后按列前缀过滤（`:115-118`）；关键字表 `SQL_KEYWORDS` 在 `:4-81` |
| 格式化 | `src/extensions/sql/format.ts:11-30` | 依赖 `sql-formatter`，`SQLFormatConfig` 见 `src/types.ts:126-136` |
| 变量面板 | `src/code-editor-renderer/sql-editor-body.tsx` + `src/variable-panel.tsx`；解析见 `src/source-resolvers.ts:181-200` | 变量来自内联数组或 scope 路径 |
| snippet 面板 | `src/code-editor-renderer/snippet-panel.tsx` | 配置项 `snippets` |
| 执行（配置化 action） | `src/code-editor-renderer/use-sql-editor-state.ts:181-186` | `execution.executeAction` + `execution.params` 从 scope 取参 |
| 执行（硬编码兜底） | `src/code-editor-renderer/use-sql-editor-state.ts:188-197` | 无 `executeAction` 时 POST `/api/report/execSql`；该端点在本仓后端无实现（见 §2.4），且被 Flux 审计判为 P2 模块职责越界 |
| 结果面板 | `src/sql-result-panel.tsx:1-120`；状态机 `src/code-editor-renderer/use-sql-editor-state.ts:96-116` | 仅 `idle/loading/success/error` 四态，一次性渲染全部行 |
| 全屏、只读、diff、colorize | `src/code-editor-renderer.tsx`、`src/use-merge-view.ts` | 通用编辑器能力，与 EQL 无关但可直接复用 |
| 语言注册扩展点 | `src/extensions/base.ts:77-89` | `registerLanguage()` 运行期注册新语言/覆盖，为「不 hardcode EQL」提供已有缝隙 |

### 2.3 配置面现状（`SQLEditorConfig`）

定义在 `nop-chaos-flux:packages/flux-code-editor/src/types.ts:151-163`：

| 字段 | 现状 | EQL 需要但缺失 |
|---|---|---|
| `tables` | `TableSchema[]`（name/alias/description/columns），`:175-190` | 无实体关联树、无嵌套属性、无 `_some/_all` 集合算子、无主键/查询空间信息 |
| `dialect` | 固定 5 值联合，`:173` | 无法注册 `eql` 或任意自定义方言 |
| `keywords`/`uppercaseKeywords` | 类型里有字段但 completion 未消费 | 关键字集合不可配置 |
| `format` | `sql-formatter` 语言固定映射，`format.ts:5-11` | EQL 无 formatter 语言档位；无法注入自定义格式化策略 |
| `execution` | action/resultPath/params/showPreview，`:143-149` | 无分页、最大行数、多语句、取消策略、结果列元数据约定 |
| `snippets` | 已有 | 可复用（EQL 片段库） |
| `variablePanel` | 已有 | 可复用 |
| 校验 | 无 | 无 SQL lint / 无后端 parse 校验接入点 |
| 补全数据源 | 仅 `tables` 内联或 scope 路径，`src/source-resolvers.ts:120-178` | 无「从后端拉实体元数据」的异步加载协议 |

### 2.4 两个关键断点（roadmap 必须解决）

1. **默认执行端点是死代码**：`/api/report/execSql` 在 `nop-entropy` 后端无对应实现（全仓 grep `execSql`/`executeSql` 仅命中测试与无关类）；该 fallback 的存在违反组件「不拥有 host transport」的定位，`nop-chaos-flux:docs/analysis/2026-05-24-deep-audit-full/02-module-responsibility.md:439-460` 已记为 P2。
2. **补全是「表别名」模型，不是「实体属性路径」模型**：`src/extensions/sql/completion.ts:115-118` 只识别一层 `alias.partial`，而 EQL 的核心体验是 `o.customer.address.city` 多级路径与 `o.roles._some.userId` 集合算子；`TableSchema.columns` 是扁平列表（`src/types.ts:182-190`），无法表达。

## 3. 与 EQL 的逐项差距

| 维度 | 现状 | EQL 目标 | 差距等级 |
|---|---|---|---|
| 语言/方言 | 5 个硬编码 SQL 方言 | EQL 作为可注册方言档案（关键字/格式化/补全模式/校验 action） | P0 |
| 补全-表 | 正则解析 FROM/JOIN + 扁平列 | 实体名 + 属性路径 + 关联展开 + 集合算子 + 函数 | P0 |
| 补全-元数据 | 前端内联/scope 数组 | 后端 ORM 元数据 API（实体/属性/类型/关联/querySpace） | P0 |
| 校验 | 无 | 调后端 `parse/compile`，返回行列定位，CodeMirror lint 展示 | P0 |
| 格式化 | sql-formatter 标准方言 | EQL 关键字集上色/换行；实体名大小写保留 | P1 |
| 执行 | 单 action + 无分页 | 分页/limit/超时/取消/多语句/只读开关 | P0 |
| 结果集 | 全量渲染表格 | 分页、列元数据、类型渲染、复制/导出 CSV、错误行列定位 | P1 |
| 查询管理 | 无 | 历史、收藏、多标签、分享（可分期） | P1/P2 |
| 工作台壳层 | 字段组件 | 页面级布局：实体树 + 编辑器 + 结果 + 工具栏 | P0 |
| 后端 API | 无 | EQL 校验/执行/元数据/权限 | P0 |
| 安全 | 无 EQL 相关 | 权限、只读、行数/超时上限、危险语句拒绝 | P0 |

## 4. EQL 侧已有可复用后端能力（初勘，细节见后端能力调研报告）

- EQL 语法入口：`nop-entropy:nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTParser.java:13-26`；编译入口 `EqlCompiler.compile(name, eql, context)` 在 `.../compile/EqlCompiler.java:56`，编译上下文实现为 `EqlCompileContext`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/compile/EqlCompileContext.java:19`），由 `ILoadedOrmModel.compileSql(...)` 统一构造（`nop-persistence/nop-orm/src/main/java/io/nop/orm/factory/LoadedOrmModel.java:93-141`）。
- 任意 EQL 文本执行已有通路：`IOrmSession.executeQuery(SQL, LongRangeBean, Function<IDataSet,T>)`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSession.java:245`），`JdbcQueryExecutor.executeQuery` 内部即 `env.compileSql(...)` 后走 JDBC（`nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:66-96`），并携带 `IDataSet` 行列元数据与 `LongRangeBean` 分页。
- 安全边界已有基础：EQL grammar 只含 DML（`nop-persistence/nop-orm-eql/model/antlr/Eql.g4:27-50` 的 `sqlDmlStatement` 仅 select/insert/upsert/update/delete），`commit/rollback` 在编译期显式拒绝（`EqlCompiler.java:73-82`）；租户/逻辑删除过滤由编译期注入（`docs-for-ai/04-reference/source-anchors.md:146`）。
- 元数据：`nop-metadata` 已有 SQL 字段预览 `NopMetaEntityBizModel.previewSqlFields`（`nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaEntityBizModel.java:269-276`）与数据查询 `queryData`（`:299-322`），但面向「元数据实体」而非 EQL 控制台，可作为参考而非直接宿主。
- 执行计划的参数绑定：`IOrmSession.executeQuery` 的 `SQL` 支持 marker 值；控制台应默认强制参数化/限制字面量类型，避免拼接。

## 5. 三仓交付链路（sync）

- 前端主链路（已有脚本，事实）：
  1. `nop-chaos-next:scripts/import-flux-to-libs.sh`：在 `nop-chaos-flux` 执行 `pnpm --filter @nop-chaos/ui build && pnpm pack:flux-bundle`，把 `dist-packages/nop-chaos-flux-0.1.0.tgz` 拷到 `nop-chaos-next/libs/`。
  2. `nop-chaos-next:scripts/repack-flux-and-refresh.sh`：打 tgz → 同步 `ui/theme-tokens/tailwind-preset` 源码基线 → 清 pnpm store → 重新 `pnpm add file:...`。
  3. `nop-chaos-next:scripts/rebuild-flux-and-build.sh`：上述刷新后 `pnpm build`（`apps/main/dist`）。
  4. `nop-chaos-next:scripts/sync-site.sh`：把 `apps/main/dist` 同步到 `nop-entropy/nop-frontend-support/nop-web-site/src/main/resources/META-INF/resources/`，>3KB 的 js/css gzip（原文件删除），并用 `nop-shared-import-map` 标记校验「生产构建」。
- 后端页面链路：页面以 `_vfs/**/pages/*.page.yaml` 提供，菜单在 `_vfs/**/auth/*.action-auth.xml` 声明（参考 `nop-code/nop-code-web/src/main/resources/_vfs/nop/code/auth/nop-code.action-auth.xml` 的 `resourceType="SUBM" url="/nop/code/pages/..."` 写法）。`nop-chaos-next` 宿主按 siteMap + 页面 URL 渲染 flux schema（`nop-chaos-next:apps/main/src/flux/providers.ts`、`apps/main/src/router/`）。
- 结论：EQL 控制台页面应做成 **`nop-entropy` 侧 `_vfs` 页面 + action-auth 菜单**，前端能力通过 flux bundle 升级交付；这样不需要在 `nop-chaos-next` 增加内置路由（避免三仓各自维护页面）。

## 6. 结论与改造方向（roadmap 输入）

1. **不新增 `eql-editor` renderer type**，也不在 `flux-code-editor` 内写 EQL 分支；把现有的「SQL 方言」抽象升级为**可注册的 SQL-like 方言档案（Dialect Profile）**：
   - profile 字段：关键字/函数、CodeMirror 方言工厂、格式化策略、补全模式（`table-alias` / `entity-path` / `none`）、元数据来源、校验 action 默认值、执行默认值。
   - EQL 是其中一个 profile（可由配置声明 + 少量通用 `entity-path` 补全代码支撑），满足「尽量配置化」。
2. **扩展补全数据模型**：`TableSchema` 向后兼容地增加关联/嵌套属性描述（或新增 `EntitySchema`，由 profile 指定）；补全实现支持多级路径与集合算子。
3. **把执行/校验 transport 显式化**：删除硬编码 `/api/report/execSql` fallback，`execution.executeAction` 必填；新增 `validation.action`（后端 parse/compile）与结构化诊断协议（line/column/message/severity）。
4. **结果集增强**：`SQLResultState` 增加列元数据、分页/limit、总行数、耗时、可复制/导出；结果面板支持错误定位跳转。
5. **工作台壳层**：新页面用 flux 现有 `resizable`/`tree`/`tabs`/`table`/`code-editor` 组合，不新增宿主级协议；复杂交互再评估专用 renderer。
6. **后端**：新增 EQL 控制台 API（元数据/校验/执行/历史/保存），宿主模块与依赖方向见后端能力调研报告与 roadmap 决策。

## 7. 关键证据清单

| # | 证据 | 说明 |
|---|---|---|
| 1 | `nop-chaos-flux:packages/flux-code-editor/src/types.ts:173` | `SQLDialect` 固定 5 值，无法注册 EQL |
| 2 | `nop-chaos-flux:packages/flux-code-editor/src/types.ts:151-163` | `SQLEditorConfig` 当前完整配置面 |
| 3 | `nop-chaos-flux:packages/flux-code-editor/src/extensions/base.ts:201-219` | 方言扩展写死 switch |
| 4 | `nop-chaos-flux:packages/flux-code-editor/src/extensions/sql/completion.ts:115-118` | 只支持一层 `alias.col` 补全 |
| 5 | `nop-chaos-flux:packages/flux-code-editor/src/code-editor-renderer/use-sql-editor-state.ts:188-197` | 硬编码 `/api/report/execSql` 兜底 |
| 6 | `nop-chaos-flux:docs/analysis/2026-05-24-deep-audit-full/02-module-responsibility.md:439-460` | 该兜底被审计判为 P2 模块职责越界 |
| 7 | `nop-chaos-flux:packages/flux-code-editor/src/sql-result-panel.tsx:1-120` | 结果面板无分页/导出/列元数据 |
| 8 | `nop-chaos-flux:packages/flux-code-editor/src/extensions/base.ts:77-89` | 已有 `registerLanguage` 扩展点 |
| 9 | `nop-entropy:nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:56` | EQL 编译入口 |
| 10 | `nop-entropy:nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSession.java:245` | 任意 EQL 文本+分页执行入口 |
| 11 | `nop-entropy:nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:66-96` | 编译→执行→结果集元数据链路 |
| 12 | `nop-entropy:nop-persistence/nop-orm-eql/model/antlr/Eql.g4:27-50` | EQL 只接受 DML，DDL 不可达 |
| 13 | `nop-entropy:nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaEntityBizModel.java:269-276` | 已有 SQL 字段预览 API（可参考） |
| 14 | `nop-chaos-next:scripts/import-flux-to-libs.sh` / `repack-flux-and-refresh.sh` / `rebuild-flux-and-build.sh` / `sync-site.sh` | 三仓构建与同步链路 |
| 15 | `nop-entropy:nop-code/nop-code-web/src/main/resources/_vfs/nop/code/auth/nop-code.action-auth.xml` | 后端页面 + 菜单注册范式 |

## 8. Open Questions

- [ ] EQL 控制台的后端宿主模块选择（新建 `nop-eql-console` vs 扩 `nop-metadata` vs 扩 `nop-sys`），待后端能力调研报告对比后裁定。
- [ ] EQL 补全元数据协议：一次性全量下发 ORM 模型，还是按需按 querySpace/实体懒加载？需评估大模型体积。
- [ ] 「平台开发工具」是否可进入生产环境：默认关闭开关与权限点如何设计。
- [ ] 多语句/事务语义：EQL 编译期拒绝 commit/rollback，控制台是否允许同一请求多语句，需明确。
