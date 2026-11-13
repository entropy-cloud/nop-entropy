# 在线 API 层设计（nop-metadata-open）

**范围**：新增 Maven 子模块 `nop-metadata-open`（模型 + 执行 + 发布 + 页面单模块起步）；依赖 `nop-metadata-service`（复用联邦执行器与沙箱）与 `nop-auth`（权限接线）；不修改 `nop-metadata.orm.xml`（Protected Area）
**状态**：active
**灵感来源**：Palantir Ontology（Object + Link + Action + 发布切片），差异点 = 完全基于数据库模型（目录已登记的实体/字段/Join/指标），不引入独立计算层

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。实现编排见 `ai-dev/backlog/nop-metadata-online-api-roadmap.md`。

---

## 一、设计结论

1. **新建独立模块 `nop-metadata-open`，定位"在线 API 发布层"**：API 定义存数据库、发布即生效（免重启）、权限接 nop-auth；执行完全复用 nop-metadata 已验证的联邦执行器与 SQL 沙箱，不引入新的取数路径。与 nop-dyn 的分工：nop-dyn 面向平台自身库的动态 ORM 模型，本层面向联邦目录登记的一切数据（PHYSICAL / EXTERNAL / SQL_VIEW）+ 无实体服务函数。
2. **API 对象 = 发布切片 + 权限边界**（Ontology 的 project/API）：新增 `NopMetaApiObject` / `NopMetaApiFunction` / `NopMetaApiFunctionArg` / `NopMetaApiExposure` 四表。实体函数（`NopMetaEntityMethod`）与实体本身不复制，经暴露清单软引用（SSOT）。
3. **输出类型声明与实现分离是平台现成契约，本设计直接消费**：xbiz `<return>` 支持 `graphql:type` 命名类型与内嵌 `<schema>` 结构化声明，二者优先于实现类型（`BizModelToGraphQLDefinition.getType`，`nop-biz/impl/BizModelToGraphQLDefinition.java:328-348`）。运行时行数据以 `List<Map>` 承载，GraphQL 字段级选择对消费者完全可用。**因此不需要动态 ORM 实体**——首版类型化路径 = 发布时从实现派生输出字段并固化为声明。
4. **无实体服务函数的 implKind 谱系 = sql / catalog-query / composite**：三种受控原语，各自的沙箱与校验机制均为既有实现的复用或结构化约束；拒绝 `xpl`/`java`（任意代码执行面，沿 14 号设计既有裁定）；**首版只读**，`functionKind=mutation` 仅保留语义位，执行器显式拒绝。
5. **发布 = 原子快照交换**：publish 生成不可变发布快照（自包含 JSON）+ 登记权限资源 + 动态 bizObj 注册 + 缓存清除；任一步失败现网已发布版本原样保留；**运行时读快照，不读定义行**——草稿态编辑不泄漏到线上，契约是"发布那一刻的类型与行为"。
6. **权限不建平行系统**：定义面 = API 定义实体的 action-auth；运行面 = 发布时自动登记 `NopAuthResource` 功能点 + 函数行级 `roles` CSV 执行器复查（M3 同款双道）；管理界面复用 nop-auth-web 现有角色/授权页。
7. **动态注册机制复用 nop-dyn 验证过的链路**：生成薄壳 xbiz → 内存 VFS 层 → `bizObjectManager` 缓存清除。GraphQL schema 按请求从 BizObjectManager 实时解析（`GraphQLEngine.java:452`），新注册的 API 对象立即出现在 schema 与 introspection，无需重建。

## 二、背景与动机

nop-dyn 解决"平台自身库的模型在线定义与发布"（动态 ORM 实体 + 标准 CRUD）；nop-metadata 是联邦式元数据目录，已登记外部数据源、实体、字段、Join、指标、实体方法（M1-M4），但对外暴露停留在**泛化入口**形态（`queryData` / `invokeMethod` / `findEntityPage` 以 `metaEntityId` 为参数）：

- 权限只有实体族粒度（全部实体共享 `NopMetaEntity:query|NopMetaEntity:queryData` 一个权限点），无法按 API/按函数授权；
- 无"发布"语义：`releaseModule` 只是模块状态翻转（`NopMetaModuleBizModel.java:502`，DRAFTING→RELEASED + 事件），不改变外部可访问面；
- 实体方法（M1）挂在实体上，**无实体的服务函数**（跨库聚合、参数化对账查询、多步编排）没有定义位置；
- 消费者看不到类型化 schema（`List<Map>` 返回被当作不透明 JSON）。

14 号设计（`14-entity-methods-design.md` §四）曾拒绝"动态生成 xbiz/bean 注册进平台 BizModelManager"，理由是"GraphQL schema 动态变化、权限面与缓存失效都需要平台级机制配合"。该平台级机制现已由 nop-dyn 验证存在（publish → generateForModule + reloadModel → `setDynamicBizModels` + VFS 内存层，`nop-dyn-service/.../codegen/DynCodeGen.java:394-405`），本设计承接其预留的"当目录实体需要被平台 GraphQL 引擎当作一等 bizObj 查询时，再评估该路径"触发条件——但**复用机制、不复用存储路径**（nop-dyn 的动态 ORM 面向平台库，外部数据源取数必须走 withConnection + 沙箱）。

### Ontology 概念映射

| Ontology | 本方案承载者 | 状态 |
|---|---|---|
| Object | `NopMetaEntity`（任意 entityKind） | 已有（目录） |
| Property（含派生） | `NopMetaEntityField`（storage/computed/alias） | 已有（M2） |
| Link | `NopMetaEntityJoin` / Relation | 已有 |
| 语义层 | Measure / Dimension / Filter | 已有 |
| Action（实体级） | `NopMetaEntityMethod` + `invokeMethod` | 已有（M1-M3） |
| **Action（服务级，无实体）** | `NopMetaApiFunction` | 本设计新增 |
| **Project / API（发布切片）** | `NopMetaApiObject` + `NopMetaApiExposure` | 本设计新增 |

### 平台机制锚点

| 平台机制 | 位置 | 本设计的消费方式 |
|---|---|---|
| xbiz `<return graphql:type="...">` 命名类型 / `<schema>` 内嵌结构化声明，优先于实现类型 | `nop-kernel/nop-xdefs/.../biz/xbiz.xdef:64-67`；`nop-biz/impl/BizModelToGraphQLDefinition.java:328-348`（getType：graphql:type → schema → generic type 三级） | 发布时从实现派生输出字段，生成 `<schema>` 声明；运行时 Map 承载 |
| 命名类型注册表 | `nop-graphql-core/.../schema/TypeRegistry.java:81-89` | 跨函数复用实体行类型（可选优化，首版每函数内联 schema） |
| GraphQL schema 按请求实时解析 + introspection 实时拼装 | `GraphQLEngine.java:452`；`BizObjectManager.java:55,355-389` | 发布/下线即时生效，无 schema 重建 |
| 动态 bizObj 注册 + 缓存清除（nop-dyn 验证链路） | `DynCodeGen.java:394-405`（VFS 内存层 + `setDynamicBizModels` + `removeCache`）；`NopDynModule.xbiz:20-35`（publish mutation 先例） | 本层发布动作的实现模板 |
| 输出列类型推断（LIMIT 0 + ResultSetMetaData） | `SqlViewFieldTypeInferrer`（plan 2026-07-18-0900-1 先例） | sql 类函数发布时推断输出字段类型 |
| SQL 沙箱双侧同源 | `MetaMethodSqlValidator`（M3，save-time `method-impl-invalid` + query-time `method-sql-unsafe`） | `sql` 步骤直接复用；publish-time 第三道重验 |
| 受限表达式求值 | `ExprFeatures.SIMPLE`（M2 计算属性先例：禁 import/new/语句/lambda） | composite 的 expression 步骤 |
| 行级 roles 检查 | `NopMetaEntityMethodBizModel`（M3，`method-role-denied`） | 函数行级 roles 同款 |
| action-auth 默认权限串 + DB 资源合并 | `ReflectionBizModelBuilder.java:364`；`SiteMapProviderImpl.java:116-130`（静态 XML + DB 动态合并） | 发布时登记 `NopAuthResource`，合并链自动生效 |
| 联邦取数分派 | `TableReferenceExecutor.java:62-133`（entity 走平台事务连接 / external、sql 走 `withConnection`） | 所有取数仍走此单点，本层不新增连接语义 |
| soft-FK 弱引用先例 | `NopMetaQualityResult.checkpointId`（无 DB FK、删除不级联、运行时解析） | 暴露清单/函数对实体、数据源的引用形态 |

## 三、核心设计

### 3.1 数据模型（四表，独立 `nop-metadata-open.orm.xml`）

**`NopMetaApiObject`**（表 `nop_meta_api_object`）——发布单元：

| 列 | 语义 |
|---|---|
| `apiObjectId` | 主键（seq） |
| `apiObjectName` | 全局唯一（UK），小驼峰标识符；发布时校验与平台已注册 bizObj 名不冲突，且不得落在平台保留命名空间（`Nop*` 等前缀拒绝） |
| `displayName` / `description` | 展示与文档 |
| `status` | dict `meta/api-status`：`draft` / `released` / `offline`（下线） |
| `publishedVersion` | 发布版本号（每次 publish +1） |
| `publishedSnapshot` | 不可变发布快照（mediumtext，自包含 JSON：成员定义 + 派生输出类型 + 权限点清单）；**运行时唯一事实源** |
| `roles` | API 级行角色限制（CSV，空 = 不限制） |
| `sourceKind` + 审计五件套 + `extConfig` | 沿目录惯例（`extConfig` 预留限流/缓存等横切配置位） |

**`NopMetaApiFunction`**（表 `nop_meta_api_function`）——无实体服务函数：

| 列 | 语义 |
|---|---|
| `functionId` / `apiObjectId` | 主键；归属 API 对象（FK，cascadeDelete） |
| `functionName` | 对象内唯一（UK `(apiObjectId, functionName)`）；小驼峰；不得与平台标准 action 名冲突（复用 `RESERVED_METHOD_NAMES` 名单） |
| `functionKind` | dict `meta/api-function-kind`：`query` / `mutation`（首版执行器对 mutation 显式拒绝，仅保留语义位） |
| `implKind` | dict `meta/api-impl-kind`：`sql` / `catalog-query` / `composite` |
| `implSource` | `sql`：带 `?` 占位符的单条 SELECT；`composite`：步骤编排 JSON；`catalog-query` 不用此列（结构化列承载） |
| `dataSourceId` | `sql` 类必填，软引用 `NopMetaDataSource`（须 ACTIVE）；`catalog-query`/`composite` 为空 |
| `querySpec` | `catalog-query` 的结构化定义（TreeBean + joinId + measures/dimensions 选择，JSON） |
| `returnType` | 文档性契约（generic-type 字符串，沿 M1 惯例） |
| `outputFields` | 显式输出字段声明（JSON，可选）；发布时优先于自动推断 |
| `roles` / `disabled` / `sourceKind` / 审计五件套 / `extConfig` | 同上惯例 |

**`NopMetaApiFunctionArg`**（表 `nop_meta_api_function_arg`）：与 `NopMetaMethodArg` 同构（`argName` 方法内 UK、`argType`、`mandatory`、`defaultValue`、`displayName`）。

**参数定义形态：行式 SSOT + 多视图转换**。参数以行为唯一事实源（每参数独立定义、各带元数据）；平台原生同构先例为 xbiz `BizActionArgModel`（`xbiz.xdef:56-62`：name/mandatory/displayName/type/graphql:type/description + 每参数 `<schema>`）。不采用"整体 `{}` GraphQL 文本"作为存储形态——解析成本不构成障碍（Nop 自有 parser：`GraphQLDocumentParser` + ANTLR 语法 `nop-service-framework/nop-graphql/nop-graphql-core/model/antlr/GraphqlSDL.g4`，AST 含 `GraphQLInputDefinition`/`GraphQLDirective`，`GraphQLDocumentHelper.parseObjectDefinitions` 已可解析对象定义，需要新解析入口时扩展 parser）；行式 SSOT 的真实依据是：argName 唯一性由 DB UK 承载（M1 先例）、行级审计 diff 与管理页表格编辑天然适配、跨函数参数查询可 SQL 直达、与 `NopMetaMethodArg` 保持同构以复用校验/执行代码。

- **类型语法**：`argType` 直接采用 GraphQL 类型语法子集（平台内建标量 + `[]` 列表 + `!` 非空修饰）；文本形态中的 `!` 与行上 `mandatory` 列双向映射，行上 `mandatory` 是唯一事实源。执行器按 `argType` 对 JSON 入参做标量强转（泛化 `invokeApi` 入口的兜底校验）。
- **复杂输入**：单参数本身是复杂输入对象时，该参数行内声明其结构化 schema（对齐 xbiz arg `<schema>` + `graphql:inputType` 形态），不把嵌套结构拆成多层表。
- **视图与转换（有方向性）**：行 → GraphQL SDL 片段视图为无损生成（`!` ↔ mandatory、`= default` ↔ defaultValue、`"""doc"""` ↔ description）；SDL → 行经 `GraphQLDocumentParser` 回解析（解析入口不足时扩展框架 parser，本模块禁止手写 GraphQL 解析），转换有损：SDL 不可表达的元数据（displayName 等）保持行上现值不变、不静默清空；SDL 中未知 directive 首版显式拒绝（不静默丢弃），携带元数据的 directive 词汇表（如 `@meta(displayName:...)`）为可选增强。行视图是全量元数据编辑面，SDL 片段视图是专家快速建参/粘贴面。
- **对外暴露形态与存储解耦**：消费者看到的参数形态由发布薄壳决定——v1 平铺为 GraphQL field arguments（引擎默认形态）；参数多、需分组或跨函数复用时薄壳生成命名 input type（`SalesQueryInput!` 形态，预留语义位，复用声明经 `extConfig` 登记）。存储形态不随对外形态变化。

**`NopMetaApiExposure`**（表 `nop_meta_api_exposure`）——实体暴露清单：

| 列 | 语义 |
|---|---|
| `exposureId` / `apiObjectId` | 主键；归属 API 对象（cascadeDelete） |
| `metaEntityId` | 软引用目录实体；**实体本身不复制**（SSOT） |
| `exposeKind` | dict `meta/api-expose-kind`：`std-crud-ro`（合成只读查询入口）/ `data`（queryData/聚合）/ `methods`（该实体已注册方法经 API 面暴露） |
| `alias` | 该实体在 API 面的别名（可选，避免实体名冲突） |

**与 `releaseModule` 的关系**：`NopMetaModule.releaseModule` 保持既有状态语义不变（目录治理生命周期）；本层的 publish/unpublish 是 API 面的独立动作，挂在 `NopMetaApiObject` bizObj 上。二者不互相驱动。

### 3.2 无实体函数的 implKind 谱系

| implKind | 语义 | 校验与执行机制 |
|---|---|---|
| `sql` | 对绑定数据源的单条参数化 SELECT | save-time + publish-time + query-time 三道 `MetaMethodSqlValidator` 同源沙箱（M3 双侧同源 + publish 第三道）；绑定数据源经 `withConnection` + 既有 SSRF 防御链；参数绑定顺序契约沿 M3（声明参数按 `argName` 字典序对应 `?` 占位符） |
| `catalog-query` | 保存的结构化查询：目录实体上的过滤（TreeBean）+ 可选 joinId / measures / dimensions，参数按名绑定进 filter 占位 | 无 SQL 文本，结构化定义由既有 `queryJoinData` / `queryAggregation` 执行器校验执行；跨实体跨库能力继承目录 Join/聚合的既有裁定（同库下推、跨库内存合并、limit 契约 AR-09） |
| `composite` | 有序步骤编排：每步 ∈ {sql, catalog-query, expression}，步骤输出命名变量，末步返回 | 每步独立过各自沙箱/校验；expression 步用 `ExprFeatures.SIMPLE`（M2 特性集）；发布时校验步骤 DAG 无环、变量引用存在、末步类型已知；内存边界沿跨库合并既有上限配置 |
| `xpl` / `java` | 任意代码 | **拒绝**（14 号设计既有裁定的延伸：任意代码执行面不进入数据驱动的 API 模型；表达力缺口优先扩受控原语） |
| 写外部库 | — | **首版不做**：`functionKind=mutation` 执行器显式拒绝（`api-mutation-unsupported`）；有副作用的平台动作（对账、质量执行等）已由各自 BizModel action 承载，不在本面重复 |

### 3.3 输出类型声明：声明与实现分离

类型化路径**不依赖动态 ORM**，分两层：

1. **派生**：publish 时对每个函数/暴露入口派生输出字段——`sql` 复用 `SqlViewFieldTypeInferrer` 思路（LIMIT 0 + ResultSetMetaData，对绑定数据源实测推断）；`catalog-query` 从 measures/dimensions 选择结构化派生；`composite` 取末步类型。定义侧 `outputFields` 显式声明优先于推断。
2. **固化**：派生结果写入发布快照，生成薄壳 xbiz 的 `<return><schema>` 声明（`graphql:type` 命名类型留作跨函数复用同一实体行类型的后续优化）。运行时执行器返回 `List<Map>`，GraphQL 引擎按声明类型做字段解析与字段级选择（`BizModelToGraphQLDefinition.getType` 的声明优先语义 + graphql-java PropertyDataFetcher 的 Map 取值）。

**类型契约的时效语义**：消费者看到的字段与类型 = 发布那一刻的快照，草稿态的 SQL/定义修改不影响线上类型；重新 publish 才换约。推断失败的函数显式发布失败（不静默降级为无类型），除非定义侧显式声明了 `outputFields`。

### 3.4 发布状态机

```
publish(apiObjectName):
  1. 校验：定义完整性（必填、引用存在且 ACTIVE）；全部 sql/composite 步骤沙箱重验；
     composite 步骤 DAG 无环 + 变量引用闭合；输出类型派生（3.3）；
     apiObjectName 与已注册 bizObj 无冲突
  2. 组装不可变快照 JSON（成员定义 + 派生类型 + 权限点清单 + 版本号）
  3. 登记权限资源（3.6）
  4. 生成薄壳 xbiz → 内存 VFS → bizObjectManager 缓存清除
  5. 单行事务：publishedSnapshot 覆盖 + publishedVersion+1 + status=released
     + NopMetaModelChangedEvent 事件（changeSource=api-publish）
  失败路径：任一步失败，已发布版本原样保留（步骤 1-4 均为无副作用准备/生成，
  步骤 5 原子提交）；发布持 per-API 锁（沿 checkpoint 运行标记先例），
  并发发布第二次 fail-fast

unpublish(apiObjectName):
  移除薄壳注册 + 缓存清除 + 权限资源停用 + status=offline；快照保留（审计）
```

### 3.5 暴露形态与命名契约

- **薄壳委托**：生成的 xbiz 每个函数一个入口，函数体为单行受控委托 `MetaApiInvoker.invoke(apiObjectName, functionName, args)`——薄壳无用户逻辑；用户逻辑只在 DB 定义行，运行时经快照 + 执行器复查（args 校验、roles、沙箱三道水位中的 query-time 道防线在执行器内）。
- **泛化兜底入口**：`NopMetaApiObject__invokeApi(apiName, functionName, args)` 恒可用（调试面 + 未发布薄壳场景的降级面），执行同一执行器。
- **GraphQL 命名**：bizObj 名 = `apiObjectName` 本身（`salesApi__regionSales` 形态），发布时查重；暴露入口命名 `<alias>__findEntityPage` 等（避开平台标准 action 名，沿 M4 合成入口命名先例）。

### 3.6 权限

| 面 | 机制 |
|---|---|
| 定义面（谁能建/改/发布 API） | `NopMetaApiObject` 等定义实体的 action-auth（平台既有链路，部署开启 `nop.auth.enable-action-auth`） |
| 运行面（谁能调已发布 API） | publish 时按函数/暴露入口自动登记 `NopAuthResource` 功能点（权限串 = `apiObjectName:query\|apiObjectName__functionName` 形态）；unpublish 停用；nop-auth 静态 XML + DB 资源合并链自动生效 |
| 执行面（defense-in-depth） | 函数行级 `roles` CSV 执行器复查（`api-role-denied`，M3 同款：无登录态内部调用放行，GraphQL 入口由 action-auth 兜底） |
| 管理界面 | 复用 nop-auth-web 角色管理 / assign-auth / 资源管理页；本模块仅提供按 API 对象过滤的只读视图（后续按需） |

不为 metadata 单独建权限模型或权限表——那会制造第二事实源与平行校验链。

### 3.7 安全契约

- **沙箱水位不降低**：sql/composite-sql 步骤的沙箱实现与 M3 同源单点（不复制黑名单）；publish-time 重验使"save 后校验器水位提升"能阻断旧定义发布。
- **错误面不泄漏**：执行失败错误 param 只带 apiObjectName/functionName/错误类别，不回显 SQL、表名、参数值（沿 M3 契约）；日志只记 sqlHash。
- **连接面封闭**：sql 步骤只能在函数绑定的 ACTIVE 数据源连接上执行，无 JDBC URL 注入面。
- **无写路径**：首版全只读；对平台库 PHYSICAL 实体的写继续走其平台 bizObj 标准 CRUD（经暴露清单声明 + 权限点控制），本层不新增写通道。
- **审计**：发布/下线/调用失败事件沿 `MetaModelChangedEventPublisher` 既有机制；调用成功审计（谁在何时调了什么）属网关横切能力，`extConfig` 预留位，首版不做。

### 3.8 模块结构与依赖

- 单 Maven 子模块 `nop-metadata-open`（模型 + dao + service + 页面一体，规模大了再拆），依赖 `nop-metadata-service`（执行器、沙箱、解析器）与 `nop-auth-api`（权限资源登记接口）。
- 自带 `nop-metadata-open.orm.xml`；对 `nop_meta_entity` / `nop_meta_data_source` / `nop_meta_entity_method` 一律软引用（无 DB 外键、删除不级联、运行时 requireXxx 解析），**不触碰 `nop-metadata.orm.xml`**。
- 租户化（沿 nop-dyn tenant BizModel provider 形态）为 follow-up，首版不做。

## 四、拒绝了什么

| 被拒方案 | 理由 |
|---|---|
| 扩展 nop-dyn 支持外部数据源 | 其执行路径绑定平台 ORM/querySpace；外部源必须走 withConnection + 沙箱，硬改会把两套安全模型搅在一起；机制复用即可，无需同模块 |
| 动态 ORM 实体作为首版类型化路径（外部源 querySpace 化） | 输出类型可经 `graphql:type`/`<schema>` 声明并与 Map 运行时解耦（平台现成契约），动态 ORM 的工程量与安全模型迁移不必要；高频常驻访问场景未来另按 querySpace 注册立项 |
| `NopMetaEntityMethod.entityId` 改可空以容纳无实体函数 | 污染实体聚合语义与 cascadeDelete；无实体函数是 API 对象的成员，不是实体的成员 |
| 单表多态函数表（ownerType + 两个可空 FK） | UK/级联/校验全要写 NULL 豁免逻辑；平行同构表 + 执行器代码复用更廉价（M1 单表判别列裁定的适用边界是"同一聚合内的两类方法"，跨聚合不适用） |
| `implKind=xpl/java`、webhook 出站调用 | 任意代码执行面 / SSRF 新面；只读 + 受控原语组合已覆盖主需求 |
| 首版支持写外部库 | 事务/补偿/幂等语义完全不同，最大风险面；mutation 留语义位 |
| API 定义多版本并存（v1/v2 同时在线） | 首版单活跃版本（快照 + 版本号）；多版本路由属网关能力，需求出现时再立项 |
| 平行权限系统 / 独立权限配置界面 | nop-auth 校验链 + 管理界面现成；平行系统 = 第二事实源 + 重复校验链 |

## 五、与已有设计的关系

- `14-entity-methods-design.md`：本设计承接其 §四 被拒方案"动态生成 xbiz/bean 注册"的再评估触发条件；实体方法与 API 函数保持两个聚合，执行器与沙箱代码复用、数据模型不合并。
- `01-architecture-baseline.md` §七 拒绝清单："所有查询走现有 ORM/连接层、不引入新 Driver 抽象"——本层所有取数仍经 `TableReferenceExecutor` 单点，无新增连接语义。
- `10-event-model.md`：发布/下线复用 `NopMetaModelChangedEvent`（changeSource 扩展 `api-publish`/`api-offline`），不新增事件类型。
- `13-entity-unification.md`：暴露清单引用的是归一后的 `NopMetaEntity`（任意 entityKind）。
- 实现编排：`ai-dev/backlog/nop-metadata-online-api-roadmap.md`。
