# 实体方法与计算属性设计

**范围**：`nop-metadata`（`nop-metadata/model/nop-metadata.orm.xml`、`nop-metadata-meta` xmeta/dict、`nop-metadata-service` BizModel/执行器）；平台机制对照 `nop-biz`（CrudBizModel）、`nop-xlang`（xmeta/xbiz 模型）
**状态**：active
**灵感来源**：平台 xmeta/xbiz 元模型（schema 与行为分离）与 nop-metadata 联邦查询执行层

> 本档为规范性文档：只描述当前有效设计；变更历史见 git 与 AI 开发记录区的时效性文档。

---

## 一、设计结论

1. **方法注册走单表 + 判别列**：新增 `NopMetaEntityMethod`（`nop_meta_entity_method`）一张表承载静态入口（query/mutation）与实体级函数（loader）两类方法，以 `methodKind` 判别列区分；方法参数落独立子表 `NopMetaMethodArg`（`nop_meta_method_arg`）。不建"静态方法表 + 实体方法表"两张表。
2. **标准 CRUD 不落方法行，用实体标签声明**：`NopMetaEntity.tagSet` 引入保留标签 `std-crud` / `std-crud-ro`。标签是能力声明：PHYSICAL 实体声明平台标准 CRUD 已由 bizObj 提供；SQL_VIEW/EXTERNAL 实体声明目录合成只读查询入口。不逐实体存储 findPage/get 等标准方法行。
3. **计算属性与别名扩展 `NopMetaEntityField`，不建子表**：新增 `fieldKind`（storage/computed/alias）判别列 + `computeExpr` 表达式列 + `mapToProp` 别名目标列。计算属性、别名与普通字段同表，因为它们必须参与 selection/过滤/排序/导出等一切字段管线。
4. **方法执行走泛化入口 + entityKind 分派**：`NopMetaEntity__invokeMethod(metaEntityId, methodName, args)` 单一入口；PHYSICAL 经 EQL，SQL_VIEW/EXTERNAL 经绑定数据源连接执行。不做动态 bizObj/xbean 注册。
5. **方法实现首版仅支持参数化只读 SQL**（`implKind=sql`），save-time 与 query-time 双侧同源沙箱校验；不支持 XPL/Java-bean 实现。
6. **SSOT 标注**：方法行与字段行带 `sourceKind`（imported/custom），快照导入通道独占 `imported` 写入，目录侧手写被守卫拒绝——目录快照与目录自定义永不混淆。
7. **执行面横切能力的裁剪**：方法行建模 `roles` 行级角色限制（执行器实现检查）；不建模行级缓存/事务/补偿——目录方法是只读查询语义，事务与缓存失效一致性属平台 bizObj 层能力，目录不重建。

## 二、背景与动机

`NopMetaEntity` 是联邦式元数据目录的统一逻辑表（entityKind：PHYSICAL/SQL_VIEW/EXTERNAL），已登记字段、关系、唯一键、索引、JOIN、维度、指标、过滤器等结构事实。目录缺少三个能力：

- **方法能力**：登记实体无法声明可执行的查询函数（如"按层级查下属"这类带参查询），外部表/SQL 视图更没有可挂自定义查询入口的位置。
- **派生字段能力**：目录只有存储字段（`NopMetaEntityField` 全部为存储列语义），无法声明计算列与别名，前端/AI 消费方只能看到物理列。
- **标准查询面声明**：外部表/SQL 视图没有声明"可分页查询"的机制，消费方无法从目录判断一个实体能以什么标准方式被查询。

### 平台机制锚点

平台对这三个问题在 xmeta/xbiz 层已有成熟形态，本设计是其在**目录层（数据化元数据）**的映射，不重复其运行时机制：

| 平台机制 | 位置 | 目录层映射 |
|---|---|---|
| 标准 CRUD action 由 `CrudBizModel` 提供，GraphQL 按 `BizObjName__actionName` 自动暴露 | `nop-service-framework/nop-biz/src/main/java/io/nop/biz/crud/CrudBizModel.java:344` | 标签声明（设计结论 2），不复制定义 |
| `<disabledActions>`/`<inheritActions>` 裁剪标准 action 面 | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/biz/xbiz.xdef:16-20` | 保留标签只做"开"声明；细粒度裁剪沿 extConfig 预留 |
| 实体级带参函数 = xbiz `<loaders>` | 同上 `:82-98` | `methodKind=loader` |
| 静态入口 = xbiz `<actions>`（query/mutation） | 同上 `:22-80` | `methodKind=static-query/static-mutation` |
| 计算属性 = 普通 prop + `<getter>`/`<setter>`，别名 = `mapToProp`，不设平行集合 | `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/schema/obj-schema.xdef:34,145-149,167-177` | fieldKind 判别列（设计结论 3） |
| 外部 SQL 沙箱（blocklist + SELECT-only + 参数绑定） | `nop-metadata-service` `MetaQualityRuleExecutor`（custom_sql）、`ExpressionMeasureValidator`（表达式黑名单） | 方法 SQL 校验同源复用（见 §3.6） |

## 三、核心设计

### 3.1 方法注册表

**`NopMetaEntityMethod`**（表 `nop_meta_entity_method`），列语义（UK `(metaEntityId, methodName)`，首版不含 `isDelta` 维度——isDelta 双存储仅在快照导入通道激活，该通道落地时需随其写路径重新裁定 UK）：

| 列 | 语义 |
|---|---|
| `methodId` | 主键（seq） |
| `metaEntityId` | 归属实体（FK，mandatory） |
| `methodName` | 方法名，实体内唯一（UK `(metaEntityId, methodName)`）；小驼峰标识符，不得与平台标准 action 名（findPage/findList/findFirst/findCount/findConnection/get/batchGet/save/update/delete/saveUnique 等）冲突 |
| `methodKind` | 判别列，dict `meta/method-kind`：`static-query`（顶层只读入口）/ `static-mutation`（顶层入口，业务副作用由外部过程承担，实现体仍为只读 SELECT）/ `loader`（实体级带参字段） |
| `sourceKind` | dict `meta/method-source`：`imported`（快照导入，仅快照通道可写）/ `custom`（目录自定义，缺省） |
| `implKind` | dict `meta/method-impl-kind`：首版仅 `sql` |
| `implSource` | 实现体：带 `?` 占位符的单条 SELECT 语句（mediumtext，对齐 `sourceSql` 列先例） |
| `returnType` | 返回类型（generic-type 字符串，mandatory） |
| `roles` | 行级角色限制（CSV，空 = 不限制） |
| `disabled` | 禁用标志 |
| `isDelta` + 审计五件套（version/createdBy/createTime/updatedBy/updateTime）+ `remark` + `extConfig` | 沿目录子表既有惯例（isDelta 支持 full/delta 双存储；extConfig 预留后续横切配置） |

**`NopMetaMethodArg`**（表 `nop_meta_method_arg`）：`argId` 主键、`methodId` FK、`argName`（方法内 UK）、`argType`（generic-type）、`mandatory`、`defaultValue`、`displayName`、审计五件套。

**NopMetaEntity 聚合**：`_NopMetaEntity.xmeta` 增加 `methods` to-many 关联（沿 `entityFields` 等既有子表关联形态）。

### 3.2 计算属性与别名（NopMetaEntityField 扩展）

| 列 | 语义 |
|---|---|
| `fieldKind` | 判别列，dict `meta/field-kind`：`storage`（物理列，缺省）/ `computed`（表达式计算列）/ `alias`（同实体字段别名） |
| `computeExpr` | `fieldKind=computed` 时 mandatory：**XLang 表达式**（平台表达式引擎求值，与 xmeta prop `<transformOut>`/`<getter>` 同一表达式族），求值作用域为返回行字段（Map）；`storage/alias` 时必须为空 |
| `mapToProp` | `fieldKind=alias` 时 mandatory：目标字段名，必须为同实体的 storage 字段（不存在、自引用或指向非 storage 字段均拒绝，统一 `field-alias-target-unknown`）；`storage/computed` 时必须为空 |

行为语义：

- 计算列求值 = 平台 XLang 表达式机制（不新造表达式引擎）：经 `XLang.newCompileTool()` 解析（`IXLangExprParser.parseSimpleExpr`）、`EvalScopeImpl(rowMap)` 行作用域、`XLang.execute(executable, EvalRuntime)` 求值；以 `ExprFeatures.SIMPLE` 特性集编译，编译期禁用 import/new/语句（无任意类访问）。save-time 校验 = 表达式可解析 + 标识符白名单（引用的名称必须存在于实体字段集，做法对齐 `ExpressionMeasureValidator` 的标识符白名单）。query-time 求值失败显式报错（`field-computed-eval-failed`），不静默置 null。
- 计算属性与别名**只改变查询输出投影**：`queryData` 等查询入口的返回行中，`fieldKind=computed/alias` 的字段按其定义解析后与存储字段同形出现；不下推 SQL（XLang 表达式无 SQL 下推语义，行上求值边界清晰）。SQL 下推属不同表达式语言的选择，留待有明确需求时另裁定。
- 存量行为零变化：既有行 `fieldKind` 缺省 `storage`，查询路径对 storage 字段走原有逻辑。
- 计算属性不提供 setter/写路径：目录字段是查询投影语义，与 xmeta prop 的 `<getter>`（实体对象层）不同层。

### 3.3 标准 CRUD 标签

`NopMetaEntity.tagSet`（列已存在，无 DDL）引入两个保留标签，语义由实体可执行面决定：

| 标签 | 适用 entityKind | 声明的目录查询面 |
|---|---|---|
| `std-crud` | PHYSICAL | 平台标准 CRUD（findPage/findList/get/batchGet/save/update/delete）已由该实体的 bizObj 提供，目录消费方按标准方式查询 |
| `std-crud-ro` | SQL_VIEW / EXTERNAL | 目录合成只读查询入口（分页/列表/单条），执行走 `queryData` 同源取数路径 |

守卫规则（save-time，无静默改写）：

- SQL_VIEW/EXTERNAL 实体写 `std-crud` → 显式拒绝（只允许 `std-crud-ro`）；PHYSICAL 写 `std-crud-ro` → 显式拒绝（PHYSICAL 的只读声明无目录合成意义，写 `std-crud`）。
- 保留标签集合登记进 dict `meta/entity-tag`，与自由业务标签共存。
- 目录合成的只读入口挂在 `NopMetaEntity` bizObj 上：`NopMetaEntity__findEntityPage(metaEntityId, query)` / `findEntityList` / `getEntity`（命名避开 `NopMetaEntity` 自身的标准 `__findPage` 等 action）。目标实体无需自己的 bizObj。实体未带保留标签时，合成入口显式拒绝（`entity-std-crud-not-declared`）——标签是合成入口的启用开关，不是纯文档性声明。三个入口的返回结构与 `queryData` 同源（`QueryEntityDataResultDTO`，`findEntityPage` 含分页信息），不另造 DTO 族。
- 目录只合成只读面；SQL_VIEW/EXTERNAL 无写路径。

### 3.4 方法执行模型

单一泛化入口 `NopMetaEntity__invokeMethod(metaEntityId, methodName, args)`（GraphQL query，args 为参数名 → 值的 JSON 映射）。执行序：

```
1. requireEntity(metaEntityId)
2. 加载 method 行：不存在 → method-not-found；disabled → method-disabled
3. args 校验：缺失 mandatory 参数 → method-arg-missing；未知参数名 → method-arg-unknown
4. 行级权限：roles 非空且当前用户不含任一角色 → method-role-denied
   （无登录态的内部调用放行，沿凭证管理 admin 判定先例；GraphQL 入口另有 action-auth 兜底）
5. implKind 分派：sql → §3.5 校验后取数
6. entityKind 分派取数：
   - PHYSICAL      → EQL/ORM（平台库连接）
   - SQL_VIEW/EXTERNAL → 实体绑定数据源 withConnection（沿联邦查询取数先例，方法执行仅单表，不做 JOIN）
7. 返回强类型 DTO：methodId/methodName/columns/items(row Map 列表)/rowCount
   （items 为 List<Map> 沿 queryData 既有裁定：行结构动态，无法预声明固定 DTO）
```

- `static-query`/`loader`/`static-mutation` 三种 methodKind 共用同一执行序，且首版经同一入口 `invokeMethod` 执行、运行时行为完全一致：`methodKind` 首版是**文档性分类**（供目录消费方区分方法语义）+ 为未来按 kind 拆分 GraphQL 入口/权限串保留的语义位，不产生首版可观察的行为差异。`static-mutation` 用于表达业务上由外部 SQL 过程/触发器承担副作用的场景，其实现体仍必须是只读 SELECT。
- 返回行结构由实现 SQL 决定，`returnType` 声明为消费方文档性契约，执行器不做强类型装配。

### 3.5 方法 SQL 沙箱（安全核心）

存储型 SQL 是本设计最大的新攻击面，沿模块既有沙箱水位（custom_sql blocklist、ExpressionMeasureValidator 双侧同源）：

- **save-time 校验**（拒绝码 `method-impl-invalid`）：单语句 SELECT-only；token 精确匹配黑名单（水位不低于 custom_sql 现状：DDL/DML/文件族/PG 危险函数/`INTO`/`WITH` 等族）；`?` 占位符参数绑定，禁止字面量拼接参数。
- **query-time 重新校验**（拒绝码 `method-sql-unsafe`，defense-in-depth）：执行前以同一校验器重验 `implSource`，防止 save 后模型/校验器水位变化产生漂移，也兜底校验器落地前已存在的存量行。
- **错误面不泄漏 SQL 全文**：执行错误 param 只携带 `methodId`/`methodName` 与错误类别，不回显 SQL 与参数值（沿事件快照脱敏与 sqlHash 先例）。
- **连接面封闭**：SQL 只在实体绑定连接上执行，无 JDBC URL 注入面；F2/F5/F7 的 URL 校验链不因本设计新增绕过路径。
- **实现语言不支持 XPL/Java-bean**：任意代码执行面不进入目录数据模型（见 §四）。

### 3.6 校验与错误码

save 守卫集中在 `NopMetaEntityMethodBizModel.save` 与 `NopMetaEntityBizModel`（标签守卫、字段 kind 守卫在对应 BizModel）：

- 方法：`method-name-reserved`（与标准 action 名冲突）、`method-name-invalid`（非小驼峰标识符）、`method-impl-invalid`（save-time SQL 沙箱拒绝）、`method-sql-unsafe`（query-time 重验拒绝）、`method-source-imported-forbidden`（手写 imported）、`method-arg-duplicate` 等。
- 字段：`field-computed-expr-required`、`field-storage-expr-forbidden`、`field-alias-target-unknown`（目标不存在/自引用/非 storage 统一此码）、`field-computed-eval-failed`（query-time 求值失败）等。
- 标签：`entity-tag-std-crud-physical-only`、`entity-tag-std-crud-ro-entity-kind` 等。
- 执行：`method-not-found`、`method-disabled`、`method-arg-missing`、`method-arg-unknown`、`method-role-denied`、`method-sql-unsafe`。
- 全部归入 `NopMetadataErrors` 聚合的新 `MethodErrors`/`FieldKindErrors` 分组文件，命名前缀 `nop.err.metadata.*`，占位符与 ARG_* 声明面一致性受既有不变式门禁约束。

### 3.7 API 契约（I*Biz 接口）

- 新实体各配 `INopMetaEntityMethodBiz` / `INopMetaMethodArgBiz` 接口（驻 `nop-metadata-dao` `io.nop.metadata.biz`，沿既有 I*Biz 惯例与 `TestNopMetaBizInterfaceCompleteness` 程序化守卫）。
- `INopMetaEntityBiz` 增补 `invokeMethod` / `findEntityPage` / `findEntityList` / `getEntity` 声明。
- 事件：不新增事件类型与快照契约，沿 `MetaModelChangedEventPublisher` 既有覆盖面。

## 四、拒绝了什么

| 被拒方案 | 理由 |
|---|---|
| 静态方法表 + 实体方法表两张表 | 两类方法 90% 属性同构（name/args/return/impl/roles/disabled）；分表导致每次增加横切能力改两处、跨表聚合"实体全部能力"需要 union。差异只在绑定方式，一个 `methodKind` 判别列足够。平台 xbiz 分 `<actions>`/`<loaders>` 是 XML 模型层的结构选择，DB 落表时判别列成本更低 |
| 标准方法（findPage/get 等）逐实体落方法行 | 标准 CRUD 是 `CrudBizModel` 自动暴露的框架能力，逐实体存行制造第二事实源（平台模型一变目录即过时），且产生实体数 × 标准方法数的噪音行。用标签声明 + 目录合成查询面 |
| 为计算属性/别名新建子表 | 它们必须参与 selection/过滤/排序/导出等一切字段管线；单独建表迫使所有字段遍历代码 union 两表。平台 xmeta 同题的答案是 prop 内判别（kind/getter/mapToProp），非平行集合 |
| 动态生成 xbiz/bean 注册进平台 BizModelManager | GraphQL schema 动态变化、权限面与缓存失效都需要平台级机制配合，工程量大且超出目录职责；首版以泛化入口 `invokeMethod` 承载（沿 `queryData` 泛化入口先例）。当目录实体需要被平台 GraphQL 引擎当作一等 bizObj 查询时，再评估该路径 |
| `implKind=xpl/java-bean` | XPL/Java 实现等价于把任意代码执行面放进目录数据模型；SQL + 沙箱已覆盖"带参只读查询"主需求，表达力缺口出现时优先扩展受控 DSL（如表达式列），不开放通用代码通道 |
| 方法行级缓存/事务/补偿建模 | 目录方法是只读查询；缓存失效一致性与事务编排是平台 bizObj 层能力，目录重建必然产生语义分叉。`extConfig` 预留字段位，需求真实出现时再裁定 |
| `std-crud` 在 EXTERNAL/SQL_VIEW 上自动降级为 `std-crud-ro` | 静默改写违反"无静默跳过"原则；显式拒绝 + 错误码让调用方写对标签 |

## 五、与已有设计的关系

- `01-architecture-baseline.md` §2.5 逻辑表 / §七 拒绝清单：本设计沿用"所有查询走现有 ORM/连接层、不引入新 Driver 抽象"的既定拒绝；方法取数完全复用联邦查询执行层。
- `05-metadata-import.md` / `13-entity-unification.md`：`sourceKind=imported` 为快照导入通道（`importOrmModel` / xbiz 快照）预留 SSOT 标注；快照方法/字段导入本身不在本设计范围内，本设计只锁定其写路径契约。
- `docs-for-ai/03-modules/nop-metadata.md`：实现落地后按其章节结构补"实体方法与计算属性"能力契约。
- 平台机制（xmeta/xbiz）的权威定义在 `nop-kernel/nop-xdefs` 的 xdef 与 `nop-service-framework/nop-biz` 源码，本设计只在目录层消费其形态，不修改平台元模型。
