# EQL 在线编辑与测试工具 Roadmap

> Last updated: 2026-10-18
> 位置：按本仓 roadmap 惯例存放于 `ai-dev/backlog/`。
> 状态：roadmap 初版（M0 完成 1/3，其余阶段 todo）。本文件是执行计划的唯一状态源；设计结论沉淀到 ai-dev/design/eql-online-editor/，时效性证据见 `ai-dev/analysis/2026-10/eql-online-editor-survey/`。
> 数据口径：全部 `file:line` 于 2026-10-18 从三个仓库的当前工作树实测/只读调研得出；外部仓库行为不可验证处一律显式标注。外部项目调研基于 `~/sources` 下 depth-1 clone（HEAD 见各报告）。
> mission-driver 兼容：`## Work Item Status` 采用 `- 名称: \`status\`` 形态，状态集为 `todo / in_progress / done / blocked`；状态行尾部括注只允许一层括号。

> Sources:
> - `ai-dev/analysis/2026-10/eql-online-editor-survey/2026-10-18-flux-sql-editor-current-state-and-eql-gap.md`（现状与差距，owner：本 roadmap 的前端基线）
> - `ai-dev/analysis/2026-10/eql-online-editor-survey/2026-10-18-nop-entropy-eql-backend-capability-survey.md`（后端能力底账）
> - `ai-dev/analysis/2026-10/eql-online-editor-survey/2026-10-18-{cloudbeaver,dbgate,chat2db,sqlpad,beekeeper-studio,querybook,hue}-analysis.md`（7 份外部项目深度调研）
> - `ai-dev/analysis/2026-10/eql-online-editor-survey/2026-10-18-online-sql-editor-architecture-comparison.md`（横向对比与取舍）
> - `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`、`docs-for-ai/02-core-guides/api-and-graphql.md`、`docs-for-ai/02-core-guides/service-layer.md`
> - `docs-for-ai/04-reference/source-anchors.md`（EQL / DevDoc 锚点）

## Purpose

为 EQL（Nop 实体查询语言）交付一个 Web 在线编辑与测试工具，形态对标在线版 DBeaver：

1. **能查**：实体/属性树、可配置补全、语法校验、格式化、执行、结果表格、导出。
2. **能测**：EQL 片段快速验证（执行选中/整段）、错误行列定位、执行耗时、生成的 SQL 预览。
3. **可配置**：EQL 只是「一种 SQL-like 方言档案（Dialect Profile）」；方言关键字/函数/格式化/补全模式/校验/执行均由配置声明，不在通用组件内硬编码 EQL 分支。
4. **可交付**：前端能力经 `nop-chaos-flux → nop-chaos-next → nop-entropy/nop-web-site` 三仓同步链路交付；页面与后端 API 在 `nop-entropy` 内完成前后端联调与 E2E。

## Scope

- 前端：`nop-chaos-flux` 的 `packages/flux-code-editor` 方言化改造 + 控件级执行/校验/结果协议；`nop-chaos-flux` 现有 `resizable/tree/tabs/table/code-editor` 渲染器组合成的**工作台页面**。
- 后端：`nop-entropy` 中新增 EQL 控制台 API（元数据/校验/执行/可选 EXPLAIN），页面菜单与权限。
- 联调：`nop-chaos-next` 宿主打包、`nop-entropy` 页面 E2E 与三仓同步验证。
- 非目标（Non-Goals）：
  - 不做通用多数据库客户端（不接入 36 种 JDBC 方言，不做连接凭据托管、ER 图、数据编辑）。
  - 不做 AI/NL2SQL（可作 successor）。
  - 不做 DDL：EQL grammar 本身只接受 DML（`nop-persistence/nop-orm-eql/model/antlr/Eql.g4:27-50`），工具不绕过该边界。
  - 不重写 CodeMirror 语言服务，不引入 Monaco/Ace 替换现有编辑器内核。
  - 不在 v1 做后端持久化的查询历史/团队共享（v1 用浏览器本地存储；持久化列入 M5 可选增强）。

## 术语

| 术语 | 定义 |
|---|---|
| Dialect Profile | 描述一种 SQL-like 方言的配置包：关键字/函数/类型、CodeMirror 方言、格式化策略、补全模式、校验与执行默认值 |
| EntitySchema | 供补全/树使用的实体元数据：实体名、属性、类型、关联（to-one/to-many）、querySpace |
| Diagnostics | 后端返回的结构化诊断：`code/message/severity/line/column/endLine/endColumn` |
| 工作台页面 | 由 `main.page.yaml` + `main.flux.yaml` 提供、由后端 PageProvider 下发的 flux 页面 |
| 三仓链路 | flux 打 tgz → next 打包 dist → sync-site 同步到 entropy 的 `nop-web-site` |

## Current Baseline（2026-10-18 实测）

### 前端基线（可复用）

- `code-editor` 字段组件已具备：12 语言、SQL 模式、CodeMirror 6 方言（5 个内置）、表别名补全、`sql-formatter` 格式化、变量面板、snippet、执行 action + 结果预览、全屏/只读/diff/colorize。
  - 证据：`nop-chaos-flux:packages/flux-code-editor/src/types.ts:151-190`、`src/extensions/base.ts:144-219`、`src/extensions/sql/completion.ts:83-166`、`src/extensions/sql/format.ts:11-30`、`src/code-editor-renderer/use-sql-editor-state.ts:165-227`。
- 语言注册表已有运行期扩展点 `registerLanguage()`，无需新增 renderer type 即可扩语言/方言能力。
  - 证据：`nop-chaos-flux:packages/flux-code-editor/src/extensions/base.ts:77-89`。
- 依赖库已具备「自定义 SQL 方言」能力：CodeMirror `SQLDialect.define(spec)`（关键字/内置/类型/引号/注释等 spec）与 `sql-formatter` 的 `formatDialect(query,{dialect})`（自定义 `TokenizerOptions` + `formatOptions`）。
  - 证据：`nop-chaos-flux:node_modules/.pnpm/@codemirror+lang-sql@6.10.0/.../dist/index.d.ts:93-125`（`SQLDialect.define` / `SQLDialectSpec`）、`node_modules/.pnpm/sql-formatter@15.7.3/.../dist/esm/index.d.ts:1`（`formatDialect` 导出）与 `dialect.d.ts:5-16`（`DialectOptions`）。
- 页面侧已有手写 flux 页面范式：`*.page.yaml`（AMIS 入口）+ 同名 `*.flux.yaml`（flux 覆盖），flux 模式自动优先加载 `flux.yaml`。
  - 证据：`nop-entropy:nop-wf/nop-wf-web/src/main/resources/_vfs/nop/wf/designer/designer.page.yaml:1-4`、`designer.flux.yaml:1-20`；`docs-for-ai/02-core-guides/flux-rendering.md`「flux.yaml 页面文件」节。
- 现有渲染器足以组合工作台：`resizable`、`tree`、`tabs`、`table`、`data-source`、`button`、`dialog` 等均已存在。
  - 证据：`nop-chaos-flux:packages/flux-renderers-layout/src/resizable-renderer.tsx`、`nop-chaos-flux:packages/flux-renderers-layout/src/schemas.ts`、`nop-chaos-flux:packages/flux-renderers-data/src/data-renderer-definitions.ts`。

### 前端差距（必须解决）

| 差距 | 证据 |
|---|---|
| 方言写死 5 值 switch，无法注册 EQL/自定义方言 | `nop-chaos-flux:packages/flux-code-editor/src/types.ts:173`、`src/extensions/base.ts:201-219` |
| 补全只支持一层 `alias.column`，无实体属性路径/关联/集合算子 | `src/extensions/sql/completion.ts:107-166` |
| `TableSchema` 扁平，无关联/嵌套属性/querySpace | `src/types.ts:175-190` |
| 无 SQL/EQL 校验/lint 接入点 | `src/types.ts:151-163` 无 validation 字段 |
| 执行默认兜底硬编码 `/api/report/execSql`（后端无实现，审计 P2） | `src/code-editor-renderer/use-sql-editor-state.ts:188-197`；`nop-chaos-flux:docs/analysis/2026-05-24-deep-audit-full/02-module-responsibility.md:439-460` |
| 结果面板无分页/列元数据/导出/错误定位 | `src/sql-result-panel.tsx:1-120` |

### 后端基线（可复用）

- EQL 解析：`EqlASTParser.parseFromText`（`nop-entropy:nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/parse/EqlASTParser.java:13-26`）。
- EQL 编译：`EqlCompiler.compile(name, eql, context)`（`.../compile/EqlCompiler.java:56`）；上下文 `EqlCompileContext`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/compile/EqlCompileContext.java:19-70`）由 `ILoadedOrmModel.compileSql(...)` 统一构造（`.../factory/LoadedOrmModel.java:93-141`）。
- 任意 EQL 执行：`IOrmSession.executeQuery(SQL, LongRangeBean, Function<IDataSet,T>)`（`nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSession.java:245`），内部 `JdbcQueryExecutor.executeQuery` 编译后走 JDBC，返回带行列元数据的 `IDataSet`（`.../loader/JdbcQueryExecutor.java:66-128`）。
- 实体元数据：`IOrmModel.getEntityModels()` / `getEntityNames()` / `getEntityModel(name)`（`nop-persistence/nop-orm-model/src/main/java/io/nop/orm/model/IOrmModel.java:37-62`）；实体属性/关联由 `IEntityModel`/`IEntityPropModel` 提供。
- 安全边界：EQL grammar 只含 DML；`commit/rollback` 编译期显式拒绝（`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:66-73`）；租户/逻辑删除过滤由编译期注入（`docs-for-ai/04-reference/source-anchors.md:146`）。
- 开发期调试 API 已有宿主范式：`DevDoc`/`DevTool` 在 `nop.debug=true` 时注册，且全部 `@Auth(roles="admin")`（`nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml:134-142`；`nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevToolBizModel.java:20-42`；`docs-for-ai/02-core-guides/debugging-and-diagnostics.md:163-186`）。
- demo 应用默认 `nop.debug=true` 且 `nop.web.render-mode=flux`，可直接作为联调与 E2E 宿主（`nop-demo/nop-quarkus-demo/src/main/resources/application.yaml:12,66,73`）。

### 后端差距

| 差距 | 说明 |
|---|---|
| 无 EQL 控制台 API | 当前没有任何「接收 EQL 文本 → 校验/执行 → 返回列定义+行」的 Biz API |
| 无元数据下发 API | 前端补全需要的「实体/属性/关联/querySpace」没有面向编辑器的聚合 DTO |
| 无 EQL 结构化诊断协议 | `NopException` 可带 `SourceLocation`，但 REST/GraphQL 错误响应只有 `{status,code,msg,errors}`（`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/ApiResponse.java:33-41`），没有稳定的行列字段 |
| 取消/超时/行数上限未封装 | `IOrmSession.executeQuery` 支持 `LongRangeBean`，但控制台需要统一的 limit/timeout/cancel 策略与配置项 |

### 三仓交付基线

- flux 打包：`nop-chaos-next:scripts/import-flux-to-libs.sh`（在 flux 执行 `pnpm --filter @nop-chaos/ui build && pnpm pack:flux-bundle`，tgz 进 `nop-chaos-next/libs/`）。
- flux 刷新：`nop-chaos-next:scripts/repack-flux-and-refresh.sh`；一键刷新+构建：`nop-chaos-next:scripts/rebuild-flux-and-build.sh`（`pnpm rebuild:flux:build`）。
- 同步到 entropy：`nop-chaos-next:scripts/sync-site.sh` 把 `apps/main/dist` 拷到 `nop-entropy/nop-frontend-support/nop-web-site/src/main/resources/META-INF/resources/`（大文件 gzip，校验 `nop-shared-import-map`）。
- E2E：`nop-entropy-e2e/packages/nop-web-e2e` 自动以 flux 模式启动 `nop-demo/nop-quarkus-demo`（默认 8083，`playwright.config.ts` 的 webServer 参数含 `-Dnop.web.render-mode=flux`），现有 `extension-demo.spec.ts` 可作新 spec 范式。

## 关键决策（D1—D7）

| 决策 | 结论 | 理由 / 证据 |
|---|---|---|
| **D1 前端方言化方式** | 扩展「SQL-like Dialect Profile 注册表」，**不新增 renderer type、不在 `flux-code-editor` 内写 EQL 分支**。EQL 由 profile 配置声明（可在页面 schema 内联或按名引用）。 | 现有设计契约明确不新增平级 renderer（`nop-chaos-flux:docs/components/code-editor/design.md` §3）；CodeMirror `SQLDialect.define` + `sql-formatter formatDialect` 已支持自定义方言；`registerLanguage` 已有扩展缝。 |
| **D2 补全元数据模型** | 新增可配置的 `EntitySchema`（实体→属性→关联）与 `entity-path` 补全模式；`TableSchema` 保持向后兼容。元数据由后端聚合下发、按 scope 引用。 | EQL 的补全本质是实体属性导航与关联展开，扁平表列模型无法表达（差距表）；后端 `IOrmModel` 可直接产出。 |
| **D3 后端宿主** | 新建轻量 `nop-eql-console` 模块组（`nop-eql-console-api` / `-service` / `-web`，可选 `-app`），BizModel 名 `EqlConsole`；方法级 `@Auth(permissions="EqlConsole:read|write")`，Bean 注册加 `nop.debug=true` 条件（生产默认不可达）。**否决**放进 `nop-sys`（系统管理域与任意 EQL 安全边界混杂）、`nop-metadata`（元数据数据源与 ORM querySpace 两套机制混用）、`nop-report`（`nop-report-core` 不依赖 ORM，需破坏分层）、`nop-biz/dev`（无 web 页面归属）。 | 后端能力底账 §6.5 的依赖方向/领域边界/行列元数据/安全护栏对比；新模块无持久化实体、可先挂 demo app 联调；`DevDoc/DevTool` 的 `nop.debug` 门控 + `@Auth` 先例（`nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml:134-142`）。 |
| **D4 页面形态** | 手写 flux 页面（`main.flux.yaml`）+ AMIS 入口占位（`main.page.yaml`），不改 `nop-chaos-next` 内置路由；菜单位于 `nop-eql-console-web` 的 action-auth。 | 手写 flux 页面/自动回退已有先例（`designer.page.yaml`/`designer.flux.yaml`）；`nop-chaos-next` 内置页面向导只用于宿主级页面（`nop-chaos-next:apps/main/src/router/pageRegistry.tsx`）。 |
| **D5 执行契约** | 删除 `/api/report/execSql` 硬编码兜底；`execution.executeAction` 成为必填；执行结果统一为 `{columns, rows, rowCount, truncated, statementKind, querySpace, elapsedMs, compiledSql?}`；新增 `validation.action` 与 `onResult` 发布，页面级结果区可选消费。 | 审计已判兜底为 P2 越界（证据见上）；结果区需要页面级分页/导出而不能只依赖组件内部 state；字段对齐后端底账 §11 建议 API。 |
| **D6 安全模型** | 四层门控：部署级 `nop.debug=true`（默认关闭）+ `@Auth(permissions="EqlConsole:read|write")` + 只读默认（DML 需 `nop.eql-console.allow-write=true` 且每次显式确认）+ 执行护栏（`limit` 默认 1000/上限 10000、超时默认 30s、拒绝 `SELECT ... FOR UPDATE` 与 `@dump`、不暴露 `disableLogicalDelete`）。**必须**在 `SQL` 上显式 `.enableFilter(true)`：任意 EQL 默认 `enableFilter=false` 会绕过行级数据权限（后端底账 P0）。EQL 语法层已排除 DDL。 | 后端底账 §7（只读/数据权限/租户/逻辑删除/权限派生）；`DevDoc/DevTool` 门控先例；EQL grammar 只含 DML（`Eql.g4:20,32-52`）；错误处理要求业务失败抛 `NopException`（`docs-for-ai/02-core-guides/error-handling.md:213`）。 |
| **D7 交付链路** | 前端改动必须走 flux→next→entropy 同步链路；roadmap 的每个前端 WI 的完成判定包含「tgz 版本 + sync 日志 + entropy 页面实测」；不直接在 `nop-web-site` 手改产物。 | 三仓同步脚本事实（见基线）；`sync-site.sh` 有生产构建防呆（缺少 `nop-shared-import-map` 即失败）。 |

## 外部调研对架构的直接影响

> 详细证据与逐项目 adopt/adapt/reject 清单见 `ai-dev/analysis/2026-10/eql-online-editor-survey/2026-10-18-online-sql-editor-architecture-comparison.md` 及各项目报告；本节只记录「调研结论 → 决策/WI」的映射，不复制证据。

| 调研共识 | 来源项目 | 对 EQL 工具的结论 | 落点 |
|---|---|---|---|
| 执行以「语句」为最小单元，多语句是批（batch） | SQLPad、Hue、Beekeeper | v1 单语句/次执行（EQL 编译期本就拒绝多语句，`EqlCompiler.java:57-58`）；选中执行由前端切选区；batch 语义留待可选增强 | WI-09、WI-10、WI-28 |
| 执行与取消要服务端可达，不能只 abort HTTP | Chat2DB、DbGate、Querybook、Beekeeper | v1 同步 execute + `AbortController`；后端必须有查询超时兜底，取消能力按实测标注 | WI-15、Risks |
| 结果要「元数据与行数据分离」，并支持分页/截断 | SQLPad、DbGate、Querybook、Beekeeper | 执行返回 `columns/rows/truncated/elapsedMs`；`maxRows+1` 探测截断；页面结果区分页 | WI-09、WI-15、WI-21 |
| 补全应由元数据驱动、按作用域缓存，避免全量下发 | Chat2DB、DbGate、SQLPad、Beekeeper | 后端 `loadMeta` 按 querySpace 过滤 + 前端按 profile/scope 注入；缓存键含模型版本 | WI-06、WI-13 |
| 方言能力必须单一来源、数据化，不能前后端各存一份 | Chat2DB、DbGate、Querybook、Hue、Beekeeper | Dialect Profile 作为唯一来源；后端能力位（取消/EXPLAIN/只读）随 `loadMeta` 下发 | D1、D4、WI-04、WI-13 |
| 「运行当前语句/选中」与当前位置高亮是在线 SQL 编辑器的核心体验 | Beekeeper、Hue、DbGate | `Ctrl/Cmd+Enter` 整段、`Ctrl/Cmd+Shift+Enter` 选中；EQL 语句边界按 `;` 与空行切 | WI-10、WI-22 |
| 历史/保存查询应记录「执行快照」而非只存文本 | Querybook、Hue、DbGate | 历史项记录 EQL/编译 SQL/参数（脱敏）/耗时/行数/错误码；v1 本地、v2 可持久化 | WI-17 |
| EXPLAIN/编译校验是多数开源工具的空白，恰是 EQL 的差异化 | Chat2DB、SQLPad、Beekeeper、DbGate | 把 `validate` + 编译 SQL 预览作为 P0；EXPLAIN 作为可选 P1 | WI-14、WI-18 |
| License 与安全模型不可照搬 | Chat2DB、DbGate、Beekeeper、SQLPad | 只借鉴架构不复制源码；EQL 工具不托管凭据、不做多租户 DB 客户端；安全按 D6 三层门控 | D6、Risks |

## 目标架构

```text
┌──────────────────────────── nop-entropy（后端） ────────────────────────────┐
│ EqlConsoleBizModel  (nop-eql-console-service, nop.debug 门控, @Auth)      │
│   ├─ loadMeta(querySpace?)      → { querySpaces, entities[], dialectCaps }  │
│   ├─ validate(eql)              → { valid, diagnostics[], compiledSql, ... }│
│   ├─ execute(eql, params, ...)  → { columns[], rows[], elapsedMs, ... }     │
│   └─ explain(eql)  [可选]                                                  │
│ 复用：IOrmSessionFactory / ILoadedOrmModel.compileSql / IOrmSession         │
├────────────────────────── nop-eql-console-web（页面） ──────────────────────┤
│ _vfs/nop/eql-console/pages/EqlConsole/main.page.yaml   （AMIS 入口占位）    │
│ _vfs/nop/eql-console/pages/EqlConsole/main.flux.yaml   （手写 flux schema）│
│ _vfs/nop/eql-console/auth/nop-eql-console.action-auth.xml（菜单+权限资源） │
└───────────────────────────────────▲─────────────────────────────────────────┘
                                    │ REST /r/EqlConsole__*
┌──────────────────────── nop-chaos-next（宿主） ─────────────────────────────┐
│ flux bundle（@nop-chaos/flux，由 nop-chaos-flux 打包）                       │
│   └─ flux-code-editor（Dialect Profile / EntitySchema / validation / exec）  │
└───────────────────────────────────▲─────────────────────────────────────────┘
                                    │ workspace 源码 / tgz
┌──────────────────────── nop-chaos-flux（组件仓库） ─────────────────────────┐
│ packages/flux-code-editor  ← 本 roadmap 主要前端改动面                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 配置协议草案（roadmap 的核心「配置化」约束）

前端 `SQLEditorConfig` 增量（不破坏现有字段；EQL 不写进通用包的字面量分支）：

```ts
export interface SqlDialectProfile {
  name: string;                       // 'eql' | 'mysql' | ...
  keywords?: string[];                // 补全/高亮关键字
  functions?: FuncItem[];             // 函数补全（名称/签名/说明）
  types?: string[];
  codeMirrorDialect?: SQLDialectSpec; // 传给 SQLDialect.define
  format?: {
    language?: string;                // sql-formatter 内置语言
    formatDialect?: DialectOptions;   // 自定义 dialect（EQL 用）
    keywordCase?: 'upper' | 'lower' | 'preserve';
    tabWidth?: number;
  };
  completion?: {
    mode: 'table-alias' | 'entity-path' | 'none';
    // entity-path: 绑定 FROM/JOIN 别名 → EntitySchema，支持多级属性/to-many/_some/_all
  };
  defaultSnippets?: CodeSnippetTemplate[];
}

export interface EntityPropertySchema {
  name: string;
  type: string;
  kind: 'column' | 'to-one' | 'to-many' | 'component';
  refEntity?: string;
  properties?: EntityPropertySchema[]; // component / 已内联的 to-one
  description?: string;
}

export interface EntitySchema {
  name: string;
  label?: string;
  querySpace?: string;
  properties: EntityPropertySchema[];
}

// SQLEditorConfig 增量
dialect?: SQLDialect | string;                 // string → 按名取 profile
dialectProfile?: SqlDialectProfile;            // 内联 profile
entities?: EntitySchema[] | EntitySchemaSourceRef;
validation?: {
  enabled: boolean;
  action?: ActionSchema;                       // 入参 {eql}；返回 Diagnostics DTO
  debounceMs?: number;
};
execution?: SQLExecutionConfig & {
  publishResult?: boolean;                     // 触发 onResult 供页面级结果区消费
  request?: { limitField?: string; offsetField?: string; };
};
onResult?: ActionSchema | ActionSchema[];
```

诊断协议（前后端共用）：

```ts
export interface SqlDiagnostic {
  code: string;            // nop.err.eql.* 或控制台错误码
  message: string;
  severity: 'error' | 'warning' | 'info';
  line: number;            // 1-based
  column: number;          // 1-based
  endLine?: number;
  endColumn?: number;
}
```

后端 API 契约（BizModel `EqlConsole`，模块 `nop-eql-console-service`）：

| 方法 | 入参 | 出参（成功） | 说明 |
|---|---|---|---|
| `loadMeta` | `querySpace?` | `{ querySpaces[], entities:[EntitySchema], dialectCapabilities }` | 实体/属性/关联/方言能力；大模型可分页/按 querySpace 过滤 |
| `validate` | `eql`, `params?` | `{ valid, diagnostics:[SqlDiagnostic], statementKind?, compiledSql?, readEntities[], writeEntity? }` | 只 parse+compile，不执行；`compiledSql` 供「EQL → SQL 预览」 |
| `execute` | `eql`, `params?`, `querySpace?`, `offset?`, `limit?`, `timeoutSec?` | `{ columns:[{name,type,label?}], rows:object[], statementKind, elapsedMs, total?, truncated }` | 只读默认；DML 受配置+确认门控；`limit` 缺省/上限由配置决定 |
| `explain` | `eql`, `params?` | `{ planText?, statementKind, diagnostics? }` | 可选（M5）；不同方言支持度不同，未支持时显式失败 |
| `loadHistory` / `saveHistory` | 见 WI-17 | — | v1 前端 localStorage，后端接口仅预留 |

## Milestones

| 里程碑 | 目标 | Exit Criteria |
|---|---|---|
| **M0 基线与协议冻结** | 调研闭环、决策与协议定稿、测试基线 | 7 份外部调研 + 1 份对比 + 1 份现状差距 + 1 份后端能力底账落地；D1—D7 写入 design 文档；协议 DTO/类型有前端 fixture 与后端 DTO 的单一来源 |
| **M1 前端方言化** | flux 支持可注册 Dialect Profile 与 EQL 全套编辑能力 | `flux-code-editor` 单包 test/typecheck 全绿；EQL profile 可仅靠 schema 配置启用（关键字/补全/格式化/校验/执行）；现有 5 方言与 12 语言行为零回归 |
| **M2 后端 API** | EQL 控制台 API 可用且有安全护栏 | `loadMeta/validate/execute` 在 H2 demo 上 curl 通过；错误场景返回结构化 diagnostics；`nop.debug=false` 时 Bean 不注册；非 admin 403 |
| **M3 工作台页面** | 页面可用（树/编辑器/结果/工具栏） | `main.flux.yaml` 在 demo 应用渲染；实体树可点选插入；执行结果可见；校验错误能定位到行列 |
| **M4 三仓联调与 E2E** | 同步链路 + 真实浏览器 E2E | flux→next→entropy 同步后，`nop-web-e2e` 新 spec 全绿（打开页面→选实体→补全→执行→结果→错误→只读校验）；sync 日志与 tgz 版本记录在案 |
| **M5 收口与可选增强** | 独立审计、文档、后续增强裁定 | closure audit 通过；owner doc 更新；roadmap 状态与日志一致；可选增强（历史持久化/多 querySpace/EXPLAIN 可视化）显式标记 deferred 或拆新 roadmap |

## Work Item Status

- WI-01 外部项目调研与现状差距分析: `done`
- WI-02 设计文档与协议冻结: `todo`
- WI-03 测试基线与 fixture 设计: `todo`
- WI-04 DialectProfile 类型与注册表: `todo`
- WI-05 可配置 CodeMirror 方言: `todo`
- WI-06 entity-path 补全: `todo`
- WI-07 可配置格式化: `todo`
- WI-08 校验协议与 lint 诊断映射: `todo`
- WI-09 执行结果协议增强与 fallback 移除: `todo`
- WI-10 键盘执行与 snippet/快捷键: `todo`
- WI-11 前端单测与零回归: `todo`
- WI-12 后端宿主与安全骨架: `todo`
- WI-13 元数据 API: `todo`
- WI-14 validate API: `todo`
- WI-15 execute API: `todo`
- WI-16 后端集成测试: `todo`
- WI-17 历史与收藏: `todo`
- WI-18 EXPLAIN 可选能力: `todo`
- WI-19 页面与菜单骨架: `todo`
- WI-20 编辑器装配与元数据接线: `todo`
- WI-21 结果区与分页导出: `todo`
- WI-22 工作台交互完善: `todo`
- WI-23 三仓构建同步链路: `todo`
- WI-24 demo 前后端联调: `todo`
- WI-25 Playwright E2E: `todo`
- WI-26 文档与日志: `todo`
- WI-27 独立 closure audit: `todo`
- WI-28 可选增强裁定: `todo`

## Work Item 详情

### M0 基线与协议

**WI-01 外部项目调研与现状差距分析** `done`
- 交付：`ai-dev/analysis/2026-10/eql-online-editor-survey/` 下 7 份外部项目报告、对比综合、Flux 现状与 EQL 差距、后端能力底账。
- 证据：本 roadmap Sources 清单。

**WI-02 设计文档与协议冻结** `todo`
- 目标：把 D1—D7 与「配置协议草案」「后端 API 契约」落成规范设计文档 ai-dev/design/eql-online-editor/design.md，作为唯一事实源；roadmap 只引用不复制。
- 内容：DialectProfile/EntitySchema/SqlDiagnostic/执行结果 DTO 的字段级定义；EQL profile 的配置样例；错误码与权限点命名；安全门控矩阵。
- 验收：设计文档经 1 轮独立架构审查（只收 Blocker/Major）；roadmap 中协议草案改为链接；前端类型与后端 DTO 字段逐一对照表（手工 review 记录）。
- 依赖：WI-01。

**WI-03 测试基线与 fixture 设计** `todo`
- 目标：为前端（vitest + @testing-library/react）与后端（JUnit5 + Nop AutoTest）准备共用 fixture：EQL 样本集（合法/语法错误/语义错误/超限/只读违规）、EntitySchema 样例、诊断样例。
- 验收：fixture 文件落在各自测试资源目录并被至少一个测试引用；E2E 页面元素 marker（`data-slot`/`data-testid`）命名方案随设计文档冻结。
- 依赖：WI-02。

### M1 前端方言化（nop-chaos-flux）

**WI-04 DialectProfile 类型与注册表** `todo`
- 改动面：`nop-chaos-flux:packages/flux-code-editor/src/types.ts`、`nop-chaos-flux:packages/flux-code-editor/src/extensions/base.ts`、`nop-chaos-flux:packages/flux-code-editor/src/index.ts`。
- 内容：`SqlDialectProfile` 类型；`registerSqlDialectProfile/getSqlDialectProfile`；`SQLEditorConfig.dialect` 支持 `string`；内置 5 方言改为 profile 注册（行为逐字节等价）；未注册 profile 的降级策略（warn + standard）。
- 验收：现有单测全绿；新增注册表单测（覆盖注册/覆盖/未注册/内联 profile）。
- 依赖：WI-02。

**WI-05 可配置 CodeMirror 方言** `todo`
- 内容：`createSQLDialectExtension` 支持 `SQLDialect.define(profile.codeMirrorDialect)`；关键字/函数/类型从 profile 注入；大小写策略与 `uppercaseKeywords` 生效。
- 验收：EQL profile fixture 下 `SELECT/FROM/WHERE` 与 EQL 关键字（如 `_some/_all`、`LATERAL`、`TUMBLE` 等以 grammar 实测清单为准）高亮正确；非 EQL 方言不变。
- 依赖：WI-04。

**WI-06 entity-path 补全** `todo`
- 内容：解析 FROM/JOIN 绑定实体别名（支持 `AS`/裸别名/逗号连接）；`alias.` 补属性；`alias.prop.` 沿 to-one/component 递归；`alias.coll._some.` / `._all.` 补目标实体属性；顶层补实体名/关键字/函数；支持从 scope 解析 `EntitySchema` 与后端 action 异步加载（复用 source 解析机制）。
- 验收：单测覆盖多级路径、集合算子、未知别名降级、大 schema 性能（10k 属性 < 100ms 过滤）。
- 依赖：WI-04；EntitySchema 契约（WI-02）。

**WI-07 可配置格式化** `todo`
- 内容：`format.formatDialect` 注入 `formatDialect`；`keywordCase/tabWidth/indentStyle/logicalOperatorNewline` 透传；EQL 关键字大小写策略与实体名保留大小写。
- 验收：EQL 语句格式化回归样例（select/join/子查询/窗口/`_some`）稳定；幂等（格式化两次结果一致）。
- 依赖：WI-04。

**WI-08 校验协议与 lint 诊断映射** `todo`
- 内容：`SQLEditorConfig.validation` + 执行前校验；异步 `dispatch(validation.action, {eql})`；将 `SqlDiagnostic(line,column)` 转为 CM `Diagnostic(from,to)`（CRLF/Unicode 偏移正确）；防抖与请求取消（复用 AbortController/requestId 模式）。
- 验收：单测覆盖 1-based→offset、越界 clamp、多诊断、请求竞态；无 validation 配置时零行为变化。
- 依赖：WI-04。

**WI-09 执行结果协议增强与 fallback 移除** `todo`
- 内容：删除 `/api/report/execSql` 分支，`execution.executeAction` 必填（缺失时工具栏不显示执行/或显式报错）；`SQLResultState` 增加 `columns/elapsedMs/statementKind/total/truncated`；`publishResult` + `onResult` 事件；错误展示 code/msg 与可选行列定位；支持 `limit/offset` 请求参数映射。
- 验收：单测覆盖成功/失败/取消/竞态/结果映射；全仓 grep 无 `/api/report/execSql`；`nop-chaos-flux:docs/components/code-editor/design.md` 同步更新。
- 依赖：WI-04、WI-02。

**WI-10 键盘执行与 snippet/快捷键** `todo`
- 内容：`Ctrl/Cmd+Enter` 执行整段、`Ctrl/Cmd+Shift+Enter` 执行选中（选中为空则整段）；EQL snippet（select/join 模板）；与现有 search/indent 快捷键不冲突。
- 验收：键盘事件单测；E2E 可触发。
- 依赖：WI-04。

**WI-11 前端单测与零回归** `todo`
- 内容：新增/更新 `packages/flux-code-editor` 单测与集成测试；验证 12 语言、5 方言、diff/colorize/fullscreen/变量面板无回归；必要时更新 `nop-chaos-flux:docs/components/code-editor/design.md` 决策表。
- 验收：`pnpm --filter @nop-chaos/flux-code-editor test`、`typecheck` 全绿；覆盖率不下降；改动清单记录在 flux 侧 plan/log。
- 依赖：WI-04—WI-10。

### M2 后端 API（nop-entropy）

**WI-12 后端宿主与安全骨架** `todo`
- 改动面：新建 `nop-eql-console/nop-eql-console-api`、`nop-eql-console-service`、`nop-eql-console-web`（各自 pom + 父 pom `nop-eql-console`），根 `pom.xml` `<modules>` 增加一项；`nop-demo/nop-quarkus-demo` 增加 service/web 依赖（e2e 宿主）。
- 内容：`@BizModel("EqlConsole")`；方法级 `@Auth(permissions="EqlConsole:read")` / `@Auth(permissions="EqlConsole:write")`；Service Bean 注册加 `<ioc:condition><if-property name="nop.debug"/></ioc:condition>`；配置项 `nop.eql-console.max-rows`（默认 1000/上限 10000）、`nop.eql-console.timeout-seconds`（默认 30）、`nop.eql-console.allow-write=false`；错误码族 `nop.err.eql-console.*`；Web 模块提供 action-auth 资源（`EqlConsole:read/write`）。
- 验收：新模块 `./mvnw install -pl nop-eql-console/nop-eql-console-service -am -DskipTests` 通过；`nop.debug=false` 启动时 Bean 不存在（启动日志/Bean 断言测试）；授权用户可调用、无权限 403；配置项注入有单测。
- 依赖：WI-02。

**WI-13 元数据 API** `todo`
- 内容：`loadMeta(querySpace?)`：querySpace 列表、实体（名称/标签/querySpace/属性/关联/类型）、EQL 函数清单（如有注册表）、方言能力位（从 `IDialect`/`dialect.xdef` 读取）与元数据版本号（供前端缓存失效）。大模型支持按 querySpace 过滤与紧凑 DTO（避免 10k+ 属性一次性 JSON 膨胀）。**DTO 为白名单投影**：不返回物理表名、Java 类名、SQL 片段、内部路径（后端底账 §4.3/§7）。
- 验收：H2 demo 上返回实体数与 `IOrmModel.getEntityModels()` 一致（抽样）；DTO 字段与前端 `EntitySchema` 对齐测试；1000 实体内存/耗时记录；重复请求带 `If-None-Match`/版本号时可命中缓存（可选）。
- 依赖：WI-12。

**WI-14 validate API** `todo`
- 内容：`validate(eql)` = `IOrmSessionFactory.compileSql(name, eql, false)`（内部 `EqlCompiler`；校验用 3 参即可；执行时须改为 7 参 `enableFilter=true`）；成功返回 `compiledSql`（`ICompiledSql.getSql().getText()`）、`statementKind`、`readEntities`、`writeEntity`、`querySpace`；失败把 `NopException`（含 `SourceLocation`）归一化为 `SqlDiagnostic[]`（语法错误用 ANTLR token 行列；语义错误尽力取 AST source loc，无 loc 时给 line=1/column=1 并在 message 标注）。**错误响应必须走自定义 Error DTO**：REST `ApiResponse` 会丢弃 `sourceLocation`（后端底账 P0-6）。
- 验收：语法错误/未知实体/未知属性/未加别名/多语句/commit 语句六类样本的 diagnostics 行列符合预期（与前端 offset 映射联合测试）。
- 依赖：WI-13。

**WI-15 execute API** `todo`
- 内容：`execute(eql, params?, querySpace?, offset?, limit?, timeoutSec?)`：编译一次 → 只读策略检查（`statementKind != SELECT` 时需 `EqlConsole:write` + `allow-write=true`，否则报错；拒绝 `hasForUpdate()` 与 `@dump` 装饰器）→ 编译期 `enableFilter=true`（7 参 `compileSql`）→ `IQueryExecutor.executeQuerySql(...)`（SELECT，`LongRangeBean(offset,limit)`；参数由执行器内部 `buildParams` 构造，见后端底账 §3.2/§3.3）或 `executeUpdate`（DML，`@BizMutation` 自动事务）→ 行列元数据取 `ICompiledSql.getDataSetMeta()`；`limit` 由配置截断并置 `truncated=true`（`maxRows+1` 探测）；超时用 `SQL.timeout` + JDBC 配置兜底；取消能力按后端底账 P2（`executeQuery` 无 `ICancelToken`）标注降级或改用 `executeStatement`。
- 验收：SELECT 返回列名/类型/行数与 JDBC 一致；DML 在只读模式被拒；`enableFilter` 生效（有数据权限的测试用户只能看到授权行）；超限被截断且标记；参数化样本不改变语义；取消/超时在可测范围内验证（不可验证时写明降级方案）。
- 依赖：WI-14。

**WI-16 后端集成测试** `todo`
- 内容：JUnit5 + Nop AutoTest：`loadMeta` 最小断言、`validate` 六类样本、`execute` SELECT/DML/limit/timeout、权限（admin/非 admin/未登录）与 `nop.debug` 门控。
- 验收：`./mvnw test -pl nop-eql-console/nop-eql-console-service -am` 通过；测试不依赖外部数据库（H2 dev profile）。
- 依赖：WI-12—WI-15。

**WI-17 历史与收藏** `todo`
- 内容：v1 前端 localStorage（按 querySpace/实体隔离，容量/清理策略、隐私）；历史项记录执行快照：EQL 文本、编译后 SQL（可选）、参数（默认脱敏）、statementKind、耗时、行数、错误码、时间；后端接口仅预留（`loadHistory/saveHistory` 不实现或返回空）；v2（deferred）若需团队共享，新增 ORM 模型走 plan-first。
- 验收：前端历史/收藏单测；刷新页面后恢复；不写入敏感信息（参数值脱敏开关）。
- 依赖：WI-09、WI-15。

**WI-18 EXPLAIN 可选能力** `todo`
- 内容：按方言能力决定是否提供 EXPLAIN（`IDialect` 支持度需实测 H2/MySQL/PostgreSQL）；不支持的方言显式报错；结果以文本面板展示。
- 验收：H2 上 EXPLAIN 返回可读计划；不支持的方言错误码明确；未支持时不展示按钮。
- 依赖：WI-15。

### M3 工作台页面

**WI-19 页面与菜单骨架** `todo`
- 改动面：nop-eql-console/nop-eql-console-web/src/main/resources/_vfs/nop/eql-console/pages/EqlConsole/（`main.page.yaml` + `main.flux.yaml`）、`_vfs/nop/eql-console` 下的 action-auth 与 i18n 资源。
- 内容：`resizable` 左右布局（实体树 + 主编辑区）；工具栏（querySpace 选择/执行/格式化/校验/只读切换/清空）；`tabs` 多标签；权限资源 `EqlConsole:read` / `:write`；菜单默认在 `nop.debug` 下可见（页面 URL 不存在时 404 而非崩溃）。
- 验收：demo 应用 8083 打开页面渲染成功；菜单出现且权限资源可授权；未 debug 环境下菜单/页面不可达。
- 依赖：WI-12、WI-02、WI-11。

**WI-20 编辑器装配与元数据接线** `todo`
- 内容：页面 `data-source` 加载 `loadMeta` → 注入 `sqlConfig.entities`；`sqlConfig.dialect='eql'`（profile 由配置声明）；`validation.action`/`execution.executeAction` 指向 `EqlConsole__validate/execute`；实体树点击把实体名/属性路径插入光标（使用 code-editor 句柄或 onChange action）。
- 验收：补全能列出实体与属性；点击实体插入 `SELECT ... FROM Entity e`；校验错误在编辑器内标红；执行结果出现。
- 依赖：WI-13—WI-15、WI-19。

**WI-21 结果区与分页导出** `todo`
- 内容：v1 复用 code-editor 内置结果预览（快速可用）；v2 页面级结果区（`onResult` 发布 + `table` 渲染 + 分页 + CSV 导出 + 消息 tab + SQL 预览 tab）。
- 验收：v1 在 M3 内可用；v2 分页切换不重跑全量、导出列名正确、错误信息可见；大结果（>1000 行）不卡死。
- 依赖：WI-20、WI-09。

**WI-22 工作台交互完善** `todo`
- 内容：多标签状态保持、历史/收藏面板、执行中取消、只读模式提示、错误行点击跳转、快捷键帮助、空态与加载态、i18n（中/英）。
- 验收：E2E 覆盖关键路径；无 console error；a11y 基本检查（按钮 aria/title）。
- 依赖：WI-20、WI-21。

### M4 联调与 E2E

**WI-23 三仓构建同步链路** `todo`
- 内容：执行并记录：flux 单包 test/typecheck/build → `nop-chaos-next` `pnpm rebuild:flux:build` → `bash scripts/sync-site.sh` → `nop-entropy` 重新构建 `nop-web-site` 资源；记录 tgz 版本/commit/时间到 flux sync log 与 `ai-dev/logs/`；验证 `nop-shared-import-map` 存在。
- 验收：同步后 demo 应用加载新页面与新版 code-editor；sync 脚本 exit 0；产物可回滚（记录前一版 tgz/commit）。
- 依赖：WI-11、WI-19—WI-22。

**WI-24 demo 前后端联调** `todo`
- 内容：`mvn quarkus:dev -Dquarkus.profile=dev -Dnop.web.render-mode=flux` 启动 8083；curl 验证 `loadMeta/validate/execute` 三类请求；浏览器手工验收页面全流程；记录响应样例与耗时。
- 验收：三类 API 返回符合契约；页面手工验收清单全过；问题记录在 `ai-dev/testing/` 或直接修复。
- 依赖：WI-23。

**WI-25 Playwright E2E** `todo`
- 内容：nop-entropy-e2e/packages/nop-web-e2e/tests/eql-console.spec.ts：打开页面 → 展开实体树 → 补全/插入 → 执行合法 EQL → 断言结果行 → 执行错误 EQL → 断言诊断定位 → 只读模式下执行 DML 被拒。
- 验收：`cd nop-entropy-e2e && pnpm test:web` 通过（串行、复用现有 webServer 配置）；失败截图/trace 可定位。
- 依赖：WI-24。

**WI-26 文档与日志** `todo`
- 内容：`docs-for-ai/` 增加 EQL 控制台 owner doc（使用入口/配置项/权限/安全边界/排障）；`ai-dev/logs/` 记录交付；flux 侧同步更新 `nop-chaos-flux:docs/components/code-editor/design.md` 与 plan/log；运行 doc link checker。
- 验收：`node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`docs-for-ai/INDEX.md` 路由更新；日志与 roadmap 状态一致。
- 依赖：WI-25。

### M5 收口

**WI-27 独立 closure audit** `todo`
- 内容：由独立 subagent（非实现者）按 exit criteria 对照 live code 审计；只返回 Blocker/Major + 证据；修复后复审一轮。
- 验收：零 Blocker/零 Major；roadmap `Work Item Status`、Milestones、`ai-dev/logs/` 三处状态一致。
- 依赖：WI-26。

**WI-28 可选增强裁定** `todo`
- 内容：EXPLAIN 可视化、保存查询持久化/共享、多 querySpace 切换、NL2EQL、结果可视化；逐项给出「进 backlog / 新 roadmap / 明确拒绝」裁定。
- 验收：裁定记录在 roadmap 末尾与 `ai-dev/backlog/`。
- 依赖：WI-27。

## 依赖图

```text
WI-01 → WI-02 → WI-03
WI-02 → WI-04 → {WI-05, WI-06, WI-07, WI-08, WI-09} → WI-10 → WI-11
WI-02 → WI-12 → WI-13 → WI-14 → WI-15 → WI-16
WI-09, WI-15 → WI-17
WI-15 → WI-18
WI-11, WI-12 → WI-19 → WI-20 → WI-21 → WI-22
WI-11, WI-19..WI-22 → WI-23 → WI-24 → WI-25 → WI-26 → WI-27 → WI-28
```

## Verification Plan

> 全部命令以 2026-10-18 工作树为准；执行前先确认端口/工作树未被占用。任何一项无法执行必须记录为「未验证」并在 WI 中显式披露，不得静默收口。

### 前端（nop-chaos-flux）

```bash
cd ~/app/nop-chaos-flux
pnpm --filter @nop-chaos/flux-code-editor test
pnpm --filter @nop-chaos/flux-code-editor typecheck
pnpm --filter @nop-chaos/flux-code-editor lint
# EQL profile/补全/校验相关 e2e（若在 flux 侧补充）
pnpm exec playwright test tests/e2e/code-editor.spec.ts
```

### 后端（nop-entropy）

```bash
cd ~/app/nop-entropy-wt/nop-entropy-master
./mvnw test -pl nop-eql-console/nop-eql-console-service -am
# H2 dev 启动（联调用）
cd nop-demo/nop-quarkus-demo
../../mvnw quarkus:dev -Dquarkus.profile=dev -Dquarkus.http.port=8083 -Dnop.web.render-mode=flux
```

```bash
# 契约冒烟（按设计文档最终字段为准）
curl -s -X POST 'http://localhost:8083/r/EqlConsole__loadMeta' -H 'Content-Type: application/json' -d '{}'
curl -s -X POST 'http://localhost:8083/r/EqlConsole__validate' -H 'Content-Type: application/json' \
  -d '{"eql":"select o.id from NopAuthUser o where o.userName = :userName"}'
curl -s -X POST 'http://localhost:8083/r/EqlConsole__execute' -H 'Content-Type: application/json' \
  -d '{"eql":"select o.id, o.userName from NopAuthUser o","limit":20}'
```

### 三仓同步

```bash
cd ~/app/nop-chaos-next
pnpm rebuild:flux:build          # = repack-flux-and-refresh.sh + pnpm build
bash scripts/sync-site.sh        # dist → nop-entropy/nop-frontend-support/nop-web-site/.../resources
cd ~/app/nop-entropy-wt/nop-entropy-master
./mvnw install -pl nop-frontend-support/nop-web-site -am -DskipTests
```

### E2E

```bash
cd ~/app/nop-entropy-wt/nop-entropy-master/nop-entropy-e2e
pnpm test:web                    # 串行；自动起 quarkus:dev（flux 模式）+ Playwright
```

### 文档

```bash
cd ~/app/nop-entropy-wt/nop-entropy-master
node ai-dev/tools/check-doc-links.mjs --strict
```

## Risks

| 风险 | 影响 | 缓解 |
|---|---|---|
| 后端 EQL 语义错误缺少 `SourceLocation` | 诊断无法精确定位行列 | WI-14 归一化 + 无 loc 时显式降级；关键错误码补 loc 作为后端增强（plan-first） |
| 大模型元数据体积（10k+ 属性） | 页面首载慢/内存大 | 按 querySpace 过滤 + 紧凑数组 DTO + 懒加载；WI-13 记录耗时 |
| 三仓同步链路脆弱（tgz/pnpm store/生产构建防呆） | 前端改动无法进入联调 | WI-23 固定命令与版本记录；每次改动前先跑通空链路 |
| 写操作安全 | 生产误开导致数据损坏 | 四层门控（`nop.debug`+`@Auth`+只读默认+`allow-write`）+ E2E 负面用例 |
| 行级数据权限被绕过 | 越权看到未授权数据 | 后端强制 `SQL.enableFilter(true)`；WI-15 用带数据权限的测试用户做负面用例 |
| 新模块发布面扩大 | 根 pom/demo 依赖/构建链变更引入回归 | WI-12 只加最小依赖；`./mvnw install -pl ... -am -DskipTests` 与 demo 启动作为门槛；不进生产 app 默认依赖 |
| `code-editor` 结果区仍是组件内 feature state | 工作台分页/导出体验受限 | WI-09 `onResult` 发布 + WI-21 页面级结果区；保留 v1 内置结果作为回退 |
| sql-formatter 自定义方言与 EQL 关键字差异 | 格式化错行/破坏语句 | WI-07 用幂等样例回归；无法保证时降级为「不格式化」并提示 |
| flux 单包改动影响 12 语言/5 方言 | 回归面大 | WI-11 全量单测与集成测试；profile 默认值保持原行为 |
| 查询取消能力不足 | 长查询只能等超时 | 后端底账 P2：`executeQuery` 无 `ICancelToken`；WI-15 实测，必要时改用 `executeStatement` 或显式降级并在 UI 标注 |

## Open Questions

- [ ] `nop.eql-console` 配置项是否应支持按应用覆盖（`nop.debug` 之外）？由 WI-12 设计时裁定。
- [ ] `nop-eql-console-app`（独立 Quarkus 宿主）是否在 v1 交付，还是只挂 `nop-demo/nop-quarkus-demo` 联调？由 WI-23 联调前裁定。
- [ ] 后端 `loadMeta` 一次全量 vs 按需分页，需用 demo 模型实测决定（WI-13）。
- [ ] 执行取消的端到端能力：现有 `ICancelToken`/JDBC cancel 是否可用于 Web 请求中断，需在 WI-15 实测；不可用则显式降级为「前端取消 + 后端查询超时」。
- [ ] 历史/收藏是否需要在 v1 就做后端持久化（团队共享），还是保持 localStorage 到 M5 再裁定。
- [ ] EQL 函数补全清单是否需要暴露方言注册表/函数元数据（WI-13 数据源未定）。

## References

- 调研报告目录：`ai-dev/analysis/2026-10/eql-online-editor-survey/`
- Flux SQL 编辑器设计：`nop-chaos-flux:docs/components/code-editor/design.md`
- Flux SQL 增强历史计划：`nop-chaos-flux:docs/archive/plans/22-code-editor-sql-enhancement-plan.md`
- EQL 语法与兼容性：`docs-for-ai/02-core-guides/eql-and-database-compatibility.md`
- EQL 编译/执行源码：`nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java:56`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java:66-128`、`nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSession.java:245`
- DevDoc/DevTool 调试宿主范式：`nop-service-framework/nop-biz/src/main/java/io/nop/biz/dev/DevToolBizModel.java:20-42`、`nop-service-framework/nop-biz/src/main/resources/_vfs/nop/biz/beans/biz-defaults.beans.xml:134-142`
- 手写 flux 页面范式：`nop-wf/nop-wf-web/src/main/resources/_vfs/nop/wf/designer/designer.flux.yaml`
- 三仓脚本：`nop-chaos-next:scripts/import-flux-to-libs.sh`、`nop-chaos-next:scripts/repack-flux-and-refresh.sh`、`nop-chaos-next:scripts/rebuild-flux-and-build.sh`、`nop-chaos-next:scripts/sync-site.sh`
- E2E 基础设施：`nop-entropy-e2e/README.md`、`nop-entropy-e2e/packages/nop-web-e2e/playwright.config.ts`
