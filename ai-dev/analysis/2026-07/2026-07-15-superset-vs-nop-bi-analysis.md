# Apache Superset 功能设计分析及与 Nop 平台对标报告

> Status: open
> Date: 2026-07-15
> Scope: nop-report, nop-format/nop-chart-export, nop-format/nop-chart-echarts, nop-format/nop-tablesaw, nop-datav/nop-datav-chart, nop-dataset, nop-dyn, nop-auth, nop-job, nop-batch, nop-file, nop-integration, nop-persistence
> Conclusion: Nop 平台目前不是 BI 平台，但拥有构建类 Superset 功能所需的多个基础模块。EQL（Entity Query Language）是成熟的编译型实体查询语言（ANTLR4 语法→AST→转换→SQL 生成），可通过 Java API 在运行时执行。最大缺口在交互式可视化层（HTTP 端点和 Web UI 缺失），而非查询引擎本身。MXQuery 在本项目中不存在。

## Context

Apache Superset 是业界广泛使用的开源数据探索与可视化平台。本报告的目的是：

- 全面分析 Superset 的功能架构设计
- 逐项对标 Nop 平台的现有能力
- 识别差距、可复用模块和潜在扩展方向
- 为 Nop 平台是否/如何构建 BI 类能力提供决策参考

分析基于 `~/sources/superset` 和 `nop-entropy` master 分支（2026-07-15）的实际源码。

---

## Part 1: Superset 功能架构总览

### 1.1 核心架构分层

```
┌─────────────────────────────────────────────────┐
│                  Frontend (React)                │
│  Explore | Dashboard | SQL Lab | Admin          │
├─────────────────────────────────────────────────┤
│          REST API (Flask-AppBuilder)            │
│  /api/v1/chart/ | /dashboard/ | /dataset/ ...   │
├────────────────────┬────────────────────────────┤
│  CQRS Commands    │    DAO Layer                │
│  (superset/commands)  (superset/daos)           │
├────────────────────┴────────────────────────────┤
│  Query Pipeline                                 │
│  QueryContext → QueryObject → SQLGen → Execute  │
│  → Pandas PostProcessing → Serialize            │
├─────────────────────────────────────────────────┤
│  DB Engine Specs (75+ databases)                │
│  SupersetResultSet | Pandas Postprocessing      │
├─────────────────────────────────────────────────┤
│  Cache Layer (Redis/Memcached/Metastore)        │
│  Celery Async Tasks                             │
└─────────────────────────────────────────────────┘
```

### 1.2 核心模块清单

| 模块 | 职责 | 代码规模 |
|------|------|---------|
| `superset/viz.py` | 可视化后端（`BaseViz` + ~20 个子类） | ~3000 行 |
| `superset/common/query_context*.py` | 查询上下文编排 | ~900 行 |
| `superset/common/query_object*.py` | 查询对象模型（指标、维度、筛选器等） | ~500 行 |
| `superset/connectors/sqla/models.py` | 语义层数据集（`SqlaTable`、`TableColumn`、`SqlMetric`） | ~2300 行 |
| `superset/sql_lab.py` + `superset/sqllab/` | SQL Lab 查询执行与 API | ~1500 行 |
| `superset/sql/parse.py` | SQL 解析（基于 sqlglot） | ~2200 行 |
| `superset/security/manager.py` | 安全模型（RBAC + RLS + Subject API） | ~4900 行 |
| `superset/db_engine_specs/` | 数据库引擎抽象（~70 个引擎 spec） | ~70 个文件 |
| `superset/utils/pandas_postprocessing/` | 19 种 Pandas 后处理操作 | ~19 个文件 |
| `superset/reports/` | 警报与报告子系统 | ~400 行模型 + API |
| `superset/utils/cache_manager.py` | 多区域缓存管理 | ~300 行 |
| `superset/charts/data/api.py` | 图表数据 API + 异步查询 | ~800 行 |

### 1.3 关键设计模式

- **CQRS**: `BaseCommand`（validate → run 两阶段）+ `BaseDAO`
- **策略模式**: `BaseEngineSpec` ~50 个可覆盖方法
- **工厂模式**: `QueryObjectFactory`、`QueryContextFactory`
- **管道模式**: Pandas 后处理链（19 种操作可组合）
- **事件驱动**: Celery 异步任务 + WebSocket 推送
- **模板方法**: `BaseViz.get_data()` → 子类覆盖

### 1.4 数据查询管道

```
Frontend POST /api/v1/chart/data
  → ChartDataRestApi
    → QueryContextProcessor.get_payload()
      → Cache check (data_cache)
        → datasource.get_query_result() → SQLAlchemy query
          → SupersetResultSet (DB-API wrapper)
            → Pandas post-processing (aggregate/pivot/rolling/...)
              → Serialize (JSON/CSV/XLSX)
                → Cache store → Response
```

### 1.5 SQL Lab 数据流

```
用户输入 SQL
  → POST /api/v1/sqllab/execute/
    → SQL validation (sqlglot parse)
      → Celery task SqlLabQuery
        → DB execute via SQLAlchemy
          → Query record tracking (status/progress/result)
            → Frontend polling or WebSocket push
              → Result grid / CSV export / Save as dataset
```

---

## Part 2: 逐项功能对标

### 2.1 SQL Lab（交互式 SQL 查询编辑器）

**Superset**: 基于 Monaco Editor 的 Web SQL 编辑器，支持语法高亮、自动补全、查询历史、多数据库执行、结果浏览/导出、保存为数据集。

**Nop 现状**: **查询引擎具备，HTTP 端点和 Web UI 缺失**

什么是 EQL——它是完整的编译型实体查询语言：

- **ANTLR4 语法** (`Eql.g4`)：完整的词法/语法定义，支持标准 SQL 全部子句（SELECT/FROM/WHERE/GROUP BY/HAVING/ORDER BY/子查询/窗口函数/CTE/UNION/INSERT/UPDATE/DELETE）
- **编译管线**：`EqlASTParser`(解析) → `EqlTransformVisitor`(实体解析/JOIN展开/租户过滤/逻辑删除过滤/数据权限注入) → `AstToSqlGenerator`(方言特定 SQL 生成)
- **核心差异**：`o.customer.name` 自动展开为 `LEFT JOIN`，无需手写 JOIN ON
- **三种查询类型**：`<eql>`(实体查询)、`<sql>`(原生 SQL)、`<query>`(结构化 DQL)

已具备的运行时执行能力：

- `IOrmSession.executeQuery(SQL)` — Java API，接受 EQL/SQL 字符串，在 `JdbcQueryExecutor` 中即时编译执行
- `SqlLibManager.invoke(sqlName)` — 调用预注册的 sql-lib 项
- `NopDynSql` — 通过 CRUD API 动态管理 EQL/SQL 定义，但**无执行端点**

**关键差距**：
- **无 HTTP 端点**：没有类似 `POST /api/sql/execute` 的接口接受原始 EQL/SQL 字符串并返回结果
- **无 Web UI**：没有交互式 SQL 编辑器（Monaco Editor 等）
- `NopDynSql` 存储定义但不暴露执行 API

**简单说**：EQL 引擎本身可以做 Superset SQL Lab 的后端工作，但缺了暴露层（HTTP API + 前端页面）。

**可复用基础**:
- `nop-orm-eql` 模块：ANTLR4 解析器 + `EqlCompiler` + `AstToSqlGenerator` → 可直接作为查询服务后端
- `JdbcQueryExecutor`：运行时即时编译执行 EQL → 无需预热即可接受任意 EQL 字符串
- `NopDynSql` 实体：可作为查询历史/保存查询的存储模型

### 2.2 Chart Builder（拖拽式可视化构建器）

**Superset**: 拖拽式界面，支持柱状图、折线图、散点图、饼图、地图等 60+ 可视化类型，可交互配置指标/维度/筛选器。

**Nop 现状**: **部分具备（模板驱动，无 GUI）**

- `nop-format/nop-chart-export`: 基于 JFreeChart 的图表渲染管线，支持 11 种图表类型：
  - `BarChartRenderer`, `LineChartRenderer`, `PieChartRenderer`, `ScatterChartRenderer`
  - `AreaChartRenderer`, `BubbleChartRenderer`, `RadarChartRenderer`
  - `HeatmapChartRenderer`, `ComboChartRenderer`, `DoughnutChartRenderer`, `TrendLineRenderer`
- `nop-excel` 的 `ChartType` 枚举定义了完整图表类型模型
- `nop-report`: 通过 Excel 模板（`.xpt.xlsx`）嵌入图表，运行时填充数据
- `nop-datav/nop-datav-chart`: **骨架模块**（仅 `pom.xml`，无 Java 源码）
- `nop-format/nop-chart-echarts`: **骨架模块**（仅 `pom.xml` + 依赖声明）

**差距**: 无拖拽式图表构建 GUI，无可视化配置界面。图表通过 Excel 模板或 Java 代码定义。

**可复用基础**:
- `nop-chart-export` 的 JFreeChart 渲染器可直接复用
- `nop-excel` 的 chart 对象模型可作为前后端通用的图表描述格式
- `nop-chart-echarts` 骨架模块可作为 ECharts 前端渲染的切入点

### 2.3 Dashboard（组合式仪表盘）

**Superset**: 可拖拽布局的多图表组合视图，支持跨图表筛选、Tabs、临时过滤器、下钻。

**Nop 现状**: **不具备**

- `nop-report` 生成的是文档式报表输出（Excel/PDF/HTML），非交互式仪表盘
- AMIS 前端框架可构建管理页面（`.page.yaml`），但这不是 BI 仪表盘系统
- `nop-code` 模块有 `dashboard/main.page.yaml` 路径引用，但用于代码分析指标展示，非通用 BI

**差距**: 无仪表盘系统、无交叉筛选、无布局编辑器。

### 2.4 DQL 结构化维度查询

**Superset**: SQL Lab 允许用户写 SQL 查询，Explore 提供拖拽式指标/维度配置。

**Nop 现状**: **具备但无 Web UI**

DQL（Dimensional Query Language）在 Nop 中不是文本语言，而是**结构化查询模型** `QueryBean` + `MdxQueryExecutor` 执行引擎：

- **三层查询体系**：`<sql>`(原生SQL) → `<eql>`(实体EQL) → `<query>`(DQL/QueryBean)，抽象层级递增
- **QueryBean 模型**：`sourceName`(主实体) + `fields`(字段列表，支持 `owner`+`aggFunc`) + `filter`(条件树) + `dimFields`(维度对齐字段)
- **主子表维度对齐**：`MdxQuerySplitter` 按 `owner` 属性拆分字段 → `MdxQueryExecutor` 分别执行 → **内存 Hash Join 合并**
  - 主表先分页，子表仅查当前页的维度值（`IN` 过滤），避免全表扫描
- **三种使用方式**：
  - **Java API**：`IOrmTemplate.findListByQuery(QueryBean)` / `findFirstByQuery()`
  - **sql-lib.xml `<query>` 标签**：XML 声明的结构化查询
  - **GraphQL/REST**：通过 `CrudBizModel` 的 `findPage`/`findList` 接受 `QueryBean` 参数
- **FilterBeans 条件树**：`eq`/`gt`/`in`/`contains`/`and`/`or`/`sql()` 等组合

**与 Superset Explore 对比**：
- Superset Explore 有拖拽 GUI → 生成 `QueryObject`（含指标、维度、筛选器）
- Nop DQL 有结构化 `QueryBean`（含 fields/owner/aggFunc/filter）→ 通过 `CrudBizModel` 接受 GraphQL 参数
- 缺的是**前端拖拽界面**，后端查询模型的能力（多表维度对齐 + 内存 Hash Join）已超越 Superset 的纯 SQL 模式

### 2.5 Dataset Management（数据集管理）

**Superset**: 物理表/虚拟 SQL 数据集，支持计算列、度量（SQL 聚合表达式）、列类型检测、数据集刷新。

**Nop 现状**: **强部分具备**

- `nop-report` 的 ORM 模型定义了一套完整的数据集管理：
  - `NopReportDataset`（`dsType` = SQL/file, `dsText` = SQL 查询, `dsMeta` = 元数据 schema）
  - `NopReportSubDataset`（子数据集 + `joinFields` 关联）
  - `NopReportDatasetRef`（多对多关联报表和数据集）
  - `NopReportDatasource`（数据源配置）
  - `NopReportDatasourceAuth` + `NopReportDefinitionAuth`（权限控制）
- `nop-kernel/nop-dataset`: `IDataSet`/`IDataRow`/`IDataSetMeta` 统一的行列抽象 API
- `nop-format/nop-tablesaw`: Tablesaw 集成，支持 XLSX 解析为 DataFrame

**差距**: 无"计算列"(computed column) 和"度量"(metric) 作为一等抽象。但 SQL 可嵌入 dsText。

### 2.5 Semantic Layer（语义层）

**Superset**: 业务指标定义（SQL 表达式 + 描述 + 认证状态），维度层次结构，计算列。

**Nop 现状**: **不具备**

- 无集中式指标管理
- `nop-dyn` 的动态实体元数据（`NopDynEntityMeta`、`NopDynPropMeta`）是表单/字段层面，非 BI 分析层面
- `nop-report` 的 dataset 中可嵌入 SQL，但无"指标"一级抽象

### 2.6 DB Engine Abstraction（数据库引擎抽象）

**Superset**: `BaseEngineSpec` + ~70 个数据库引擎 spec，覆盖 MySQL、PostgreSQL、Presto、Snowflake、BigQuery、Druid、ClickHouse 等。

**Nop 现状**: **具备但范围窄**

- Nop ORM 的 `.dialect.xml` 方言模型：声明式配置 SQL 方言差异
  - `useAsInFrom`, `supportNullsFirst`, `supportILike`, `supportSequence` 等特性标志
  - 自动 VARCHAR→CLOB 升级、空串→NULL 转换（Oracle 兼容）
- 当前支持：MySQL, Oracle, PostgreSQL
- **EQL 编译管线**内置方言适配：`AstToSqlGenerator` 根据目标方言调整 SQL 输出

**差距**: 仅 3 种关系型数据库。无分析型数据库（ClickHouse, Druid, Snowflake）和无 SQL 引擎适配。EQL 的 dialect 模型是声明式的（`.dialect.xml` 特性标志），不像 Superset 的 `BaseEngineSpec` 可以用 Java 代码自由覆盖 ~50 个行为点。

### 2.7 Security/Permissions（权限模型）

**Superset**: RBAC + Row-Level Security（RLS）+ Subject API（对象级权限）+ Guest Token（嵌入）。

**Nop 现状**: **强具备**

- `nop-auth` 完整 RBAC：User/Role/Resource/Site/Dept/Group/Position
- 声明式数据权限：`data-auth.xml` 文件 + XPL 表达式
  - 例：`<eq name="tenantId" value="${$context.tenantId}"/>`
  - ORM 级注入：`DataAuthEntityFilterProvider`
  - Entity 级检查：`checkDataAuth`
  - 内置角色：`admin`（跳过所有检查）, `nop-admin`, `user`
- 报表级权限：`NopReportDefinitionAuth`, `NopReportDatasourceAuth`

**优势**: Nop 的权限模型比 Superset 更精细（org 结构、多租户、实体级数据权限）。

### 2.8 Explore View（交互式探索）

**Superset**: 选择一个数据集 → 选图表类型 → 拖入维度/指标 → 配置筛选器 → 实时预览。

**Nop 现状**: **不具备**

- CRUD BizModel 的 `QueryBean`/`findPage` 提供声明式查询，但目的是管理后台 CRUD
- 无"选择一个数据集，交互式探索"的概念

### 2.9 Annotation Layers（注释层）

**Superset**: 时间序列图上叠加事件标记线。

**Nop 现状**: **不具备**

- 无注释系统，`ChartType`/chart 模型中无 annotation 关联

### 2.10 Alerts & Reports（警报与报告）

**Superset**: 基于 CRON 调度报表生成，通过 Email/Slack/Webhook 发送，可配置告警条件。

**Nop 现状**: **部分具备（基础设施已有，缺集成 UI）**

- `nop-job`: 完整 CRON 调度引擎（调度器 + 执行器，支持 fire-now、suspend/resume）
- `nop-integration`: 邮件发送（`IEmailSender`，支持 JavaMail 和腾讯云）
- `nop-report`: 报表生成引擎
- `nop-batch`: 批量处理框架

**差距**: 无用户可配置的"定时发送报表"UI、无告警条件配置、无 Slack 集成。

### 2.11 CSV/Excel Upload（文件导入为数据集）

**Superset**: 上传 CSV/Excel → 自动类型检测 → 成为可用数据集 → 可在 Explore 中使用。

**Nop 现状**: **部分具备（基础设施已有，缺集成）**

- `nop-file`: 文件上传/下载 API（`POST /f/upload`, `GET /f/download/{fileId}`）
- `nop-format/nop-tablesaw`: XLSX 解析为 Tablesaw Table
- `nop-report` 的 dataset 支持 `dsType="file"` 类型

**差距**: 无"上传 → 自动类型检测 → 立即可视化"的端到端 UI 工作流。

### 2.12 Caching Layer（缓存层）

**Superset**: 多区域缓存（Data/Thumbnail/Metrics/FilterState），基于 Redis/Memcached/Metastore，支持缓存预热。

**Nop 现状**: **部分具备（ORM/资源级，非分析查询级）**

- `DataSetCacheHelper` (`nop-dataset`): 数据集结果缓存
- ORM Session Cache: `TenantOrmSessionEntityCache`
- `ResourceComponentManager`: 组件模型缓存
- `IResourceLoadingCache`: 配置缓存（site-map, data-auth）

**差距**: 无可配置的分析查询缓存、无缓存预热、无按数据源设置缓存超时的功能。

### 2.13 REST API

**Superset**: 基于 Flask-AppBuilder 的 `ModelRestApi`，每个资源有完整 CRUD。

**Nop 现状**: **更强（GraphQL + REST 双 API）**

- 三大端点：`/graphql`（GraphQL）、`/r/{operationName}`（REST）、`/p/{operationName}`（内容感知）
- 每个有 `BizModel` 的实体自动暴露：`findPage`, `findList`, `get`, `save`, `update`, `delete` 等
- `.api.xml` 模型 + 代码生成：可生成类型化 RPC 接口

### 2.14 Internationalization（国际化）

**Superset**: Babel `.po`/`.mo` 文件，30+ 语言。

**Nop 现状**: **强具备**

- `NopSysI18n` 实体提供 locale-based 翻译查询
- ORM 级 i18n：`i18n-en:displayName`, `i18n-zh:displayName` 注解
- AMIS 前端支持语言切换
- 代码生成时自动产出资源包

---

## Part 3: 对标汇总

| Superset 功能 | Nop 状态 | 涉及模块 | 差距等级 |
|---|---|---|---|
| SQL Lab | ❌ 不直接具备 | `nop-persistence`(可做后端) | 🔴 大 |
| Chart Builder GUI | ❌ 无 GUI，有渲染引擎 | `nop-chart-export`, `nop-datav-chart`(骨架) | 🔴 大 |
| Dashboard | ❌ 不直接具备 | — | 🔴 大 |
| Dataset Management | 🟡 强部分具备 | `nop-report` ORM 模型 | 🟡 中 |
| Semantic Layer | ❌ 不直接具备 | — | 🔴 大 |
| DB Engine Abstraction | 🟢 具备(窄范围) | `nop-persistence` dialect | 🟡 需扩展 |
| RBAC + RLS | 🟢 强具备 | `nop-auth` | ✅ |
| Explore View | ❌ 不直接具备 | — | 🔴 大 |
| Annotation Layers | ❌ 不直接具备 | — | 🔴 大 |
| Alerts & Reports | 🟡 基础设施具备 | `nop-job` + `nop-integration` + `nop-report` | 🟡 中 |
| CSV/Excel Upload | 🟡 基础设施具备 | `nop-file` + `nop-tablesaw` + `nop-report` | 🟡 中 |
| Cache(分析查询) | 🟡 ORM/资源级 | `nop-dataset`, `nop-persistence` | 🟡 中 |
| REST/GraphQL API | 🟢 更强(GraphQL+REST) | `nop-service-framework` | ✅ |
| i18n | 🟢 强具备 | `nop-sys` | ✅ |

**图例**: 🟢=强, 🟡=中(需集成), 🔴=缺失/大缺口

---

## Part 4: Nop 的特色优势

1. **GraphQL API**: Superset 只有 REST，Nop 同时提供 GraphQL + REST，前端数据查询更灵活
2. **Delta 定制**: Nop 的 Delta 层可无 fork 地深度定制平台行为
3. **代码生成**: 从 ORM 模型到完整 CRUD API + AMIS 页面的一条龙代码生成
4. **声明式数据权限**: `data-auth.xml` 比 Superset 的 RLS 更灵活（XPL 表达式 + 多租户）
5. **Excel 模板报表**: `nop-report` 的 Excel 模板驱动报表设计，像素级排版，适合中国式复杂报表
6. **EQL 编译型实体查询语言**: 完整 ANTLR4 编译管线（解析→AST 转换→方言 SQL 生成），`o.customer.name` 自动展开 JOIN，内置租户/逻辑删除/数据权限过滤注入。可通过 `IOrmSession.executeQuery(SQL)` 在运行时即时编译执行——不仅是查询语法，是完整的查询语言实现
7. **DQL 结构化维度查询**: `QueryBean` + `MdxQueryExecutor` 提供主子表维度对齐能力，支持 `owner` 属性和 `aggFunc` 聚合，内存 Hash Join 合并。可通过 `IOrmTemplate.findListByQuery(QueryBean)` 编程使用，或通过 `CrudBizModel` 的 GraphQL 接口调用

---

## Part 5: 如果要在 Nop 上构建 BI 能力

### 推荐切入点

基于现有骨架模块，建议以下建设顺序：

#### Phase 1: 补齐 nop-chart-echarts + nop-datav-chart（中优先）

- `nop-chart-echarts` 已声明了 pom.xml 但无源码：实现前端 ECharts 渲染
- `nop-datav-chart` 已声明了 pom.xml 但无源码：实现图表配置模型 + API
- `nop-excel` 的 `ChartType`/`ExcelChartModel` 可作为图表描述的元模型基础
- `nop-chart-export` 的 JFreeChart 渲染器可作为服务端导出后端

#### Phase 2: 数据集与查询服务（高优先）

- `nop-report` 的 dataset 系统已相当完整，可抽取为通用数据集服务
- 暴露 `SqlLabBizModel` (或 `EqlRunBizModel`)：`POST /r/SqlLab__runSql` 接受 `{sqlKind, source, params}` 输入，使用 `IOrmSession.executeQuery(SQL)` 即时编译执行 EQL/SQL，返回通用数据集结果
- 暴露 `DatasetRestApi` + `QueryContext` API，对齐 Superset 的 `/api/v1/chart/data` 模式
- 实现 Pandas-style 的数据后处理链（聚合/透视/排序/滚动/差分等）

#### Phase 3: 构建前端可视化

- 基于 AMIS 或独立 React 组件构建图表配置界面
- 仪表盘布局：复用 AMIS 的 Grid/Container 组件，或引入 react-grid-layout
- SQL Lab：集成 Monaco Editor + 后端 `SqlLabBizModel`(用 `IOrmSession.executeQuery()` 执行任意 EQL/SQL) + `NopDynSql`(保存查询)

#### Phase 4: 补全集成

- 数据集上传工作流：`nop-file` + `nop-tablesaw` + dataset API
- 定时报表：`nop-job` + `nop-report` + `nop-integration` 集成
- 分析查询缓存：扩展 `DataSetCacheHelper` 支持按数据源配置

---

## Conclusion

Nop 平台目前**不是 BI 平台**，但拥有构建类 Superset 功能所需的多个基础模块。最大缺口在**交互式可视化层**（SQL Lab、Chart Builder、Dashboard 均缺失）。最强的可复用资产是：

1. `nop-report` 的数据集管理系统
2. `nop-chart-export` 的 JFreeChart 渲染管线
3. `nop-auth` 的 RBAC + RLS 权限模型
4. `nop-excel` 的图表对象模型
5. `nop-job` + `nop-integration` 的调度/通知基础设施

`nop-datav-chart` 和 `nop-chart-echarts` 两个骨骼模块的存在表明有过相关的规划意图，但目前未实现。

---

## Open Questions

- [ ] Nop 平台的 BI 能力定位是什么？是构建通用 BI 平台，还是仅满足报表场景？
- [ ] 是否有前端团队资源来实现 AMIS/React 可视化组件？
- [ ] `nop-report` 的 dataset 系统是否需要独立为 `nop-dataset-api` 模块以服务 BI 场景？
- [ ] nop-chart-echarts 的定位是服务端渲染还是前端渲染？或两者兼有？
- [ ] 是否需要参考 Superset 的 `BaseEngineSpec` 模式扩展 Nop 的 dialect 系统以支持分析型数据库？
- [ ] MXQuery 在本项目中不存在。可能指其他项目或早期草案，需澄清。

---

## 补充说明：EQL 编译管线详解

本报告初稿对 EQL 的描述不够准确。以下为修正后的完整描述：

### EQL 不是"查询语法"，是"编译型查询语言"

```
EQL 文本 (如: SELECT o.id, o.customer.name FROM NopAppOrder o)
  → EqlASTParser (ANTLR4) → SqlProgram (AST)
  → EqlTransformVisitor (实体名→表名解析, o.customer.name→LEFT JOIN展开
     租户过滤注入, 逻辑删除过滤, 数据权限过滤)
  → AstToSqlGenerator (方言适配→Dialect-specific SQL)
  → ICompiledSql (原生 SQL + 参数标记 + 列元数据)
  → JdbcQueryExecutor (参数绑定 + JDBC执行 + 结果映射)
```

### 运行时执行能力

`IOrmSession.executeQuery(SQL)` / `JdbcQueryExecutor.executeQuery()` 在每次调用时执行完整编译管线。这不是预编译——EQL 引擎可以接受任意 EQL 字符串并即时编译执行。

### 已存在 vs 缺失

| 能力 | 状态 |
|------|------|
| EQL 语言定义 (ANTLR4 语法) | ✅ 就绪 |
| EQL 编译管线 (parse → transform → generate) | ✅ 就绪 |
| EQL 运行时编译执行 (Java API) | ✅ `IOrmSession.executeQuery(SQL)` |
| 方言适配 | ✅ `.dialect.xml` + `AstToSqlGenerator` |
| 接受原始 EQL/SQL 的 HTTP 端点 | ❌ 不存在 |
| Web 交互式 SQL 编辑器 UI | ❌ 不存在 |
| 查询结果流式导出 (CSV/Excel) | ❌ 不存在 |
| NopDynSql 执行 API | ❌ 仅 CRUD 管理定义，不能执行 |

### MXQuery

经全库搜索（所有 `.java`/`.xml`/`.md` 文件），MXQuery 在本项目中不存在。---

## References

### 代码路径
- `nop-report/`: `/Users/abc/app/nop-entropy-wt/nop-entropy-master/nop-report/`
- `nop-report ORM 模型`: `nop-report/model/nop-report.orm.xml`
- `nop-chart-export renderers`: `nop-format/nop-chart-export/src/main/java/io/nop/chart/export/renderer/`
- `nop-excel chart model`: `nop-format/nop-excel/src/main/java/io/nop/excel/chart/`
- `nop-chart-echarts`: `nop-format/nop-chart-echarts/`
- `nop-datav-chart`: `nop-datav/nop-datav-chart/`
- `nop-tablesaw`: `nop-format/nop-tablesaw/`
- `nop-dataset`: `nop-kernel/nop-dataset/`
- `Eql.g4` ANTLR4 语法: `nop-persistence/nop-orm-eql/model/antlr/Eql.g4`
- `EqlCompiler` (编译管线): `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlCompiler.java`
- `EqlTransformVisitor` (JOIN展开/过滤注入): `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/compile/EqlTransformVisitor.java`
- `AstToSqlGenerator` (SQL生成): `nop-persistence/nop-orm-eql/src/main/java/io/nop/orm/eql/sql/AstToSqlGenerator.java`
- `JdbcQueryExecutor` (运行时执行): `nop-persistence/nop-orm/src/main/java/io/nop/orm/loader/JdbcQueryExecutor.java`
- `IOrmSession.executeQuery()`: `nop-persistence/nop-orm/src/main/java/io/nop/orm/IOrmSession.java`
- `NopDynSql` (动态 SQL 定义): `nop-dyn/nop-dyn-dao/src/main/java/io/nop/dyn/dao/entity/NopDynSql.java`
- `SqlLibManager`: `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/SqlLibManager.java`
- `QueryBean` (结构化查询模型): `nop-kernel/nop-api-core/src/main/java/io/nop/api/core/beans/query/QueryBean.java`
- `MdxQueryExecutor` (维度对齐执行引擎): `nop-persistence/nop-orm/src/main/java/io/nop/orm/mdx/MdxQueryExecutor.java`
- `MdxQuerySplitter` (主子表拆分): `nop-persistence/nop-orm/src/main/java/io/nop/orm/mdx/MdxQuerySplitter.java`
- `QuerySqlItemModel` (`<query>` 标签): `nop-persistence/nop-orm/src/main/java/io/nop/orm/sql_lib/QuerySqlItemModel.java`
- `DaoQueryHelper` (QueryBean→EQL): `nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/DaoQueryHelper.java`
- DQL 使用指南: `docs-for-ai/02-core-guides/dql-query.md`
- Superset 源码: `~/sources/superset/superset/`

### Nop 文档
- `docs-for-ai/02-core-guides/eql-and-database-compatibility.md`
- `docs-for-ai/02-core-guides/api-and-graphql.md`
- `docs-for-ai/02-core-guides/auth-and-permissions.md`
- `docs-for-ai/01-repo-map/module-groups.md`
- `docs-for-ai/03-modules/nop-report.md`
