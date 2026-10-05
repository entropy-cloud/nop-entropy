# ORM 模型设计规范

本页集中回答"ORM 模型应该怎么设计"——字段类型、主键策略、关系声明、字典设计等核心概念。

命名规范（表名、列名、实体名格式）见 `code-style.md`。开发流程（模型→生成→构建）见 `model-first-development.md`。

## 核心概念：stdSqlType 与 stdDataType 的分离

Nop ORM 的列类型系统有两个独立维度：

| 属性 | 含义 | 决定什么 |
|------|------|---------|
| `stdSqlType` | 数据库物理存储类型 | DDL 生成、JDBC 读写类型、数据库列类型 |
| `stdDataType` | Java 逻辑类型 | Entity 属性类型、GraphQL 类型、API 层类型 |

**两者独立配置，框架自动转换。**

- `stdSqlType` 是必填属性，决定数据库列的实际类型。
- `stdDataType` 是可选属性。未指定时，取 `stdSqlType` 的默认映射值。
- 当数据库物理类型与 Java 逻辑类型不一致时，通过 `stdDataType` 覆盖默认映射。

示例：数据库存 VARCHAR，Java 用 int

```xml
<column name="amount" code="VALUE2" stdSqlType="VARCHAR" precision="100" stdDataType="int"/>
```

框架会自动完成 String ↔ Integer 的双向转换。

> **强制规则：BIGINT 主键/外键必须显式声明 `stdDataType="string"`**。原因（JavaScript long 精度问题）与完整说明见「主键设计」节。

### stdSqlType 到 stdDataType 的默认映射

每个 `stdSqlType` 枚举值都有固定的 `stdDataType` 默认值：

| stdSqlType | 默认 stdDataType | Java 类型 |
|------------|-----------------|-----------|
| BOOLEAN | boolean | Boolean |
| TINYINT | byte | Byte |
| SMALLINT | short | Short |
| INTEGER | int | Integer |
| BIGINT | long | Long |
| DECIMAL / NUMERIC | decimal | BigDecimal |
| REAL | float | Float |
| FLOAT / DOUBLE | double | Double |
| CHAR / VARCHAR / JSON / CLOB | string | String |
| DATE | date | LocalDate |
| TIME | time | LocalTime |
| DATETIME | datetime | LocalDateTime |
| TIMESTAMP | timestamp | Timestamp |
| TIME_WITH_LOCAL_TIME_ZONE | time | LocalTime |
| TIMESTAMP_WITH_LOCAL_TIME_ZONE | timestamp | Timestamp |
| BINARY / VARBINARY / BLOB | bytes | ByteString |
| GEOMETRY | geometry | GeometryObject |

> 完整列表见 `StdSqlType.java` 和 `StdDataType.java`。

### stdSqlType 别名与 UUID（解析接受集扩展，WI9a）

`StdSqlType.fromStdName` 的键集（`stdNameMap`）除了全部枚举名外还接受以下**别名拼写**。别名只扩展"合法 `stdSqlType=` 取值集合"与 `cast` 目标类型的解析接受面，**不修改任何既有映射条目**（既有取值的解析/映射结果逐字不变）：

| 拼写 | 解析为 stdSqlType | 默认 stdDataType |
|------|------------------|-----------------|
| `INT` | INTEGER | int |
| `TEXT` | CLOB | string |
| `NUMBER` | DECIMAL | decimal |
| `BOOL` | BOOLEAN | boolean |
| `UUID` | UUID（枚举成员，非别名） | string |

- `NUMBER` 的映射目标是 `DECIMAL`（2026-10-04 G-3 增补裁决：std NUMERIC 无任何方言映射且其 native 名已被 DECIMAL 条目占用，裁定见平台开发过程文档 design 目录的 WI9a Phase 0 裁定记录）。
- `UUID` 是 2026-10-04 WI9a 新增的枚举成员（`stdDataType=string` 家族）。方言 DDL 映射：h2/postgresql/duckdb 原生 `UUID`、mssql `UNIQUEIDENTIFIER`；**无映射方言（mysql/oracle 等）生成 DDL/cast 时响亮报错**，不静默降级为 VARCHAR。
- EQL `cast(x as text/number/bool/uuid)` 全链可用（解析 → 方言映射渲染）；方言渲染文本按 `stdToNativeSqlType` 链路推导（如 h2 上 `number` 渲染 `NUMERIC`、oracle 上渲染 `NUMBER`），详见 `eql-and-database-compatibility.md` 的「CAST 目标类型」节。

### 时区类型（2026-10-05 WI9b）

`StdSqlType` 新增两个活枚举成员 `TIME_WITH_LOCAL_TIME_ZONE` / `TIMESTAMP_WITH_LOCAL_TIME_ZONE`（本地时区语义：无偏移存储、按会话/数据库时区渲染；成员形态 `Types.OTHER` + `StdDataType.TIME`/`TIMESTAMP`，`fromJdbcType`/`fromStdDataTYpe`/`fromJavaClass` 既有反查结果零翻转——static 块循环后显式重钉）。`stdSqlType=` 属性合法取值集随之扩为 **32 键**（`getNames()` 实测 30 → 32），orm.xml 列定义与 migration.xml 的 `type`/`newType` 属性即可合法引用它们。

- **方言 DDL 映射（支持面逐成员精确）**：`TIMESTAMP_WITH_LOCAL_TIME_ZONE` 唯一宿主 = **oracle**（原生 `TIMESTAMP WITH LOCAL TIME ZONE`，存储按数据库时区归一化、读取按会话时区；Oracle 23ai SQL Language Reference）。`TIME_WITH_LOCAL_TIME_ZONE` **全方言无宿主**（H2 2.x 实测不支持 LOCAL 形态建表、Oracle 无任何 TIME-only 类型），不落映射。**无映射方言生成 DDL/cast 时响亮报错**（`nop.err.dao.dialect.std-data-type-not-supported`），不静默降级。
- **EQL 拼写**：`cast(x as timestamp with local time zone)` / 下划线拼写 `timestamp_with_local_time_zone` 全链可用（oracle 渲染 `TIMESTAMP WITH LOCAL TIME ZONE`）；非 LOCAL 的 `time/timestamp with time zone` 拼写维持删除态。详见 `eql-and-database-compatibility.md` 的「LOCAL 时区拼写」小节。
- **迁移裁定（EC ②）：`No migration required`** —— 全仓 114 个 orm.xml 与资源对两个时区 std 类型 0 引用（2026-10-05 三口径实测），本次扩展纯增量、既有反查零翻转。**升级指引**：既有部署若以 `VARCHAR`/`TIMESTAMP` 近似存储时区数据，无需迁移；新部署在 oracle 上可用 `stdSqlType="TIMESTAMP_WITH_LOCAL_TIME_ZONE"` 获得原生 LOCAL 时区列，其余方言会生成期响亮报错（请先确认目标库有原生宿主）。
- 真实 Oracle 引擎建表往返 `[未取证]`（CI 真库仅 H2）；替代证据 = 方言模型渲染/反查断言（`TestEqlCastTimezoneOracleDialect`）+ H2 不可建一手探测。

### ORM 常用 stdDataType 速查

| stdDataType | Java 类型 | 典型用途 |
|-------------|----------|---------|
| boolean | Boolean | 布尔标记 |
| byte | Byte | 小整数、TINYINT |
| short | Short | SMALLINT |
| int | Integer | 整数字段、字典值 |
| long | Long | 大整数、BIGINT |
| float | Float | 单精度浮点 |
| double | Double | 双精度浮点 |
| decimal | BigDecimal | 金额、精确小数 |
| string | String | 文本、ID、外键 |
| date | LocalDate | 日期 |
| time | LocalTime | 时间 |
| datetime | LocalDateTime | 日期时间 |
| timestamp | Timestamp | 精确时间戳 |
| bytes | ByteString | 二进制数据 |

> 完整列表（含 file、files、point、geometry、map、list 等）见 `StdDataType.java`。

### 时间类型选择规则

`DATE`、`DATETIME`、`TIMESTAMP` 三个类型对应不同精度，选择规则如下：

| 类型 | 精度 | 适用场景 |
|------|------|---------|
| `DATE` | 到天 | 日历日期：生日、活动日、截止日期。只关心"哪天"，不关心时刻。 |
| `TIMESTAMP` | 到毫秒 | **默认选择**。所有业务时间点：过账时间、审批时间、创建时间、发送时间等。毫秒精度保证同一秒内的多条记录可正确排序（审计、批处理）。 |
| `DATETIME` | 到秒 | **一般不使用**。仅在业务确认秒精度足够且无排序需求时使用。 |

**规则：默认 `TIMESTAMP`；日历日期用 `DATE`；除非明确理由，不用 `DATETIME`。**

> **为什么不用 `DATETIME`？** DATETIME 在 H2 等数据库中只有秒精度，而 Java 实体（`LocalDateTime`）有纳秒精度。autotest 的 `@var:` 机制从 Java 实体收集值（纳秒），但 DB 读写会截断到秒，导致录制与校验不匹配。TIMESTAMP 在 DB 层也保持毫秒精度，避免此问题。

### 四层类型结构：type / stdDataType / stdDomain / domain

Nop 平台在多个层面描述字段的类型，按具体程度从高到低排列：

| 层 | 含义 | 示例 | 说明 |
|----|------|------|------|
| `type` | Java 泛型类型 | `String`、`List&lt;String&gt;` | 最具体。XMeta `<prop type="...">`、ORM `<alias>`/`<compute>` 上使用 |
| `stdDataType` | JSON/GraphQL 层次的简单类型 | `string`、`int`、`boolean`、`date` | `StdDataType` 枚举。决定 JSON 序列化方式和 GraphQL scalar 类型 |
| `stdDomain` | XDef 元模型中注册的标准业务语义类型 | `string`、`v-path`、`class-name`、`file`、`json`、`enum` | 绑定 `IStdDomainHandler`，可推导 `type` 和 `stdDataType`。xdef 元模型定义中使用 |
| `domain` | 业务模块自定义的域名称 | `boolFlag`、`userName`、`roleId` | 应用级命名，ORM 中可展开为 `stdDomain`/`stdDataType`/`stdSqlType` |

#### domain 的作用：类型模板复用

**`domain` 是类型模板**，用于定义可复用的列类型配置。当多个列需要相同类型配置时，可以定义一个 domain，然后在列上引用。

**domain 定义**：
```xml
<domains>
    <domain name="amount" stdSqlType="DECIMAL" precision="18" scale="2" stdDataType="decimal"/>
    <domain name="quantity" stdSqlType="DECIMAL" precision="18" scale="4" stdDataType="decimal"/>
    <domain name="boolFlag" stdSqlType="BOOLEAN" stdDataType="boolean"/>
</domains>
```

**使用 domain**：
```xml
<column name="totalAmount" domain="amount" .../>
<column name="quantity" domain="quantity" .../>
<column name="isActive" domain="boolFlag" .../>
```

**domain 解析行为**：

ORM 初始化时 `OrmModelInitializer.syncDomains()` 按以下规则把 domain 定义同步到 column 上（各属性冲突处理策略**不同**，需分别理解）：

| 属性 | 冲突处理（domain 与 column 同时设置时） |
|------|----------------------------------------|
| `stdDomain` / `stdSqlType` | domain 覆盖 column，冲突时打 `LOG.warn` 后以 domain 为准 |
| `stdDataType` | domain 覆盖 column，冲突时**直接抛 `ERR_ORM_MODEL_COL_DATA_TYPE_NOT_MATCH_DOMAIN_DEFINITION`**（不是静默覆盖） |
| `precision` / `scale` | **column 优先**：仅当 column 未设置（为 null）时才从 domain 填充 |

- 如果 domain 未找到，抛出 `ERR_ORM_MODEL_INVALID_COLUMN_DOMAIN` 异常
- 实践建议：设了 `domain` 后，不要在 column 上重复设 `stdDataType`（会抛异常）；若需覆盖长度，直接在 column 上设 `precision`/`scale`（column 优先）

**使用 domain 的好处**：
1. **类型复用**：定义一次，多处使用
2. **类型一致性**：确保相同 domain 的列类型一致
3. **维护方便**：修改 domain 定义，所有引用该 domain 的列自动更新
4. **UI 控件匹配**：前端可以根据 `domain` 或 `stdDomain` 自动选择合适的控件

#### ORM 与 XMeta 中的分布差异

**ORM `<column>`**：有 `stdSqlType`（物理）、`stdDataType`（逻辑）、`stdDomain`（语义触发）、`domain`（域名引用）。**没有 `type`**。`type` 仅出现在 `<alias>` 和 `<compute>` 上。

**XMeta `<prop>`**：有 `type`（Java 类型）、`stdDomain`（标准域）、`domain`（自定义域）。**不直接设 `stdDataType`**，类型信息由 `type` 或 `stdDomain` 推导。没有 `stdSqlType`。

> ORM `<column>` 上设的 `stdDomain` 会反映到生成的 XMeta 属性上。ORM 的 `domain` 解析后也会展开为 `stdDomain`/`stdDataType`，这些值最终都会映射到 XMeta 中。

#### domain 的解析行为差异

**ORM 中 `domain` 会被解析**：初始化时 `OrmModelInitializer.syncDomains()` 查找 `<domains>` 定义，将其 `stdDomain`/`stdSqlType`/`stdDataType`/`precision`/`scale` 复制到 column 上。如果 `domain` 指定但未找到定义，抛异常。

**XMeta 中 `domain` 不解析**：纯字符串，无内置类型解析逻辑，主要用于 UI 控件匹配。

#### 控件匹配链

前端根据 XMeta 属性查找渲染控件时按以下优先级匹配，逐级 fallback：

`control`（显式指定）→ `domain` → `stdDomain` → `stdDataType`

从 `control.xlib` 中查找 `{mode}-{type}` 标签：

1. 先看 `control="xxx"` 是否显式指定
2. 没指定则看 `domain` 是否有对应控件（如 `domain="int"` → `edit-int`）
3. `domain` 没匹配则看 `stdDomain`
4. `stdDomain` 也没匹配则以 `stdDataType` 兜底

常见映射：`string` → `input-text`、`int/long` → `input-text` + `isInt`、`double/decimal` → `input-number`、`enum` → `select`。

## 主键设计

### 方案 A：VARCHAR(36) UUID（推荐用于分布式场景）

```xml
<column name="id" code="ID" displayName="主键" mandatory="true" primary="true"
        propId="1" stdDataType="string" stdSqlType="VARCHAR" precision="36"
        tagSet="seq" ui:show="X"/>
```

关键要素：
- 字段名固定为 `id`
- `stdSqlType="VARCHAR"` `precision="36"`
- `stdDataType="string"`
- `tagSet="seq"` — 应用层生成 UUID

### 方案 B：BIGINT 自增（适用于单机或简单场景）

```xml
<column name="id" code="ID" displayName="主键" mandatory="true" primary="true"
        propId="1" stdDataType="string" stdSqlType="BIGINT"
        tagSet="seq-default" ui:show="X"/>
```

关键要素：
- `stdSqlType="BIGINT"` — 数据库列保持 bigint
- `stdDataType="string"` — **必须显式指定**，原因见下方「强制规则」
- `tagSet="seq-default"` — 使用平台默认序列号生成器（`ISequenceGenerator.generateLong(key, useDefault=true)`，底层含 snowflake 等；**不是数据库原生自增**，见下文「为什么不用数据库自增」）

> **强制规则：BIGINT 主键/外键必须显式声明 `stdDataType="string"`**
>
> JavaScript 的 Number 无法精确表示全部 64 位 long（安全精度约 2^53）。若依赖默认映射（BIGINT→`Long`），ID 经 GraphQL/JSON 序列化返回到前端后会**静默截断**，列表勾选、详情跳转、关联查询拿到的 ID 与数据库不一致。
>
> 显式 `stdDataType="string"` 后 Java 属性、GraphQL 类型、前端值全程为 String，且不影响任何平台机制：
> - 主键生成：`OrmEntityIdGenerator.genSeq` 按 **DB 类型**（`stdSqlType` 的 stdDataType）决定生成方式，BIGINT 仍走 `generateLong` 序列号引擎；
> - 类型转换：生成的 Entity setter 自动把 Long 序列值 `ConvertHelper.toString` 转为 String；
> - 关联 join、读写、查询均以 DB 层 bigint 语义执行，与 Java 类型无关。
>
> **落地实证注记（2026-08-23）**：方案 B 已在 nop-app-erp 参考应用 **19 个业务域全量落地**——全部主键/外键共 1662 列按本方案声明（Java 层 String / DB 层 BIGINT 保持，`tagSet="seq-default"` 序列生成不变），前端/GraphQL 全程 String 无精度截断。迁移工程证据链见 nop-app-erp 仓 `nop-app-erp/docs/backlog/id-string-migration-roadmap.md`（id-string-migration mission，M1-M4 全 done）。

### 选择建议

| 场景 | 推荐方案 | 理由 |
|------|----------|------|
| 分布式部署、多节点 | VARCHAR(36) + `tagSet="seq"` | UUID 全局唯一，无冲突 |
| 单机部署、简单应用 | BIGINT + `tagSet="seq-default"`（必须配 `stdDataType="string"`） | 性能更好，存储更省 |
| 需要跨系统合并数据 | VARCHAR(36) + `tagSet="seq"` | 字符串 ID 便于迁移 |
| 已有数据库表结构 | 按现有结构选择 | 兼容性优先 |

### `id` 属性名保留规则

**重要**：在 Nop 平台中，`id` 是系统保留的属性名，专用于主键。

**单主键实体**：
- 系统自动为每个实体创建一个 `id` 属性，指向主键列
- 如果数据库主键列名是 `id`，必须设置 `ext:allowIdAsColName="true"`，否则会被自动重命名为 `id_`

**复合主键实体**：
- 各主键列保持各自原始的属性名（如 `partitionId`、`sid`）
- 系统创建 `OrmCompositePk` 对象（运行时类型，`io.nop.orm.support.OrmCompositePk`）作为 `id` 属性
- 复合主键中不能有列名为 `id`，否则与自动生成的 `id` 属性冲突

**`ext:allowIdAsColName="true"` 开关**：

当数据库主键列名是 `id` 时，必须设置此开关，否则 Java 属性名会被自动重命名为 `id_`：

```xml
<orm ext:allowIdAsColName="true" ...>
    <entity name="..." tableName="...">
        <columns>
            <!-- 主键列名为 id，必须设置 allowIdAsColName 才能保持为 id -->
            <column name="id" code="ID" primary="true" .../>
        </columns>
    </entity>
</orm>
```

**推荐做法**：
- 单主键实体：主键列使用 `name="id"`，并设置 `ext:allowIdAsColName="true"`
- 避免在非主键列使用 `name="id"`
- 复合主键各列使用有意义的名称（如 `partitionId`、`sid`），避免使用 `id`

### 为什么不用数据库自增

1. **分布式兼容**：多节点部署时数据库自增主键会冲突。
2. **跨数据库**：不是所有数据库都支持自增（Nop 放弃了数据库自增主键支持）。
3. **合并迁移**：字符串 ID 便于跨系统合并数据、数据迁移、离线同步。
4. **全局统一**：`tagSet="seq"` 告诉 ORM 引擎在 `save()` 时自动调用 `SequenceGenerator` 生成 UUID。

主键生成器使用 `StringHelper.generateUUID()`。如果实体已设置主键值，以用户设置的值为准。

## 外键与关系设计

### 外键字段

外键列与主键使用相同的类型配置（VARCHAR(36) + stdDataType="string"）：

```xml
<column name="orderId" code="ORDER_ID" stdDataType="string" stdSqlType="VARCHAR" precision="36"/>
```

关系通过 `orm:ref-*` 属性在子表端声明，而不是在外键列上直接声明 `foreign key`。

### to-one 关系

```xml
<to-one name="user" refEntityName="io.nop.auth.dao.entity.NopAuthUser" tagSet="pub">
    <join on="userId" />
</to-one>
```

- `tagSet="pub"` — 公开可访问
- `refEntityName` 使用全限定名

### to-many 关系

**仅限同模块引用**：`<to-many>` 的 `refEntityName` 必须指向**同一模块/域**内的实体（相同 `biz:moduleId`）。跨模块/域的主子表关系必须使用**弱指针**（字符串类型 + `billType`/`billCode` 元组），而非 `<to-many>`。

```xml
<to-many name="lines" refEntityName="app.erp.pur.dao.entity.ErpPurOrderLine"
         tagSet="pub,cascade-delete,insertable,updatable">
    <join><on leftProp="id" rightProp="orderId"/></join>
</to-many>
```

- `tagSet="pub,cascade-delete,insertable,updatable"` — 标准配置（pub:公开, cascade-delete:级联删除, insertable/updatable:允许增改子表）
- `<join>` 使用**展开式** `<join><on leftProp="" rightProp=""/></join>`（精确指定两端字段），而非简写 `<join on="fieldName" />`（隐式假定字段名与 `{parentName}Id` 模式一致）
- 所有 `<to-many>` 应显式声明 `ext:estRows` 属性，标注预估行数（见下文性能清单）

> **AI 生成规范**：当前 AI 辅助生成时代，**显式声明每一对 `<to-one>` / `<to-many>`**（两侧都写），不再依赖平台自动推定。
> 
> 在此模式下，**所有 `ref-*` 属性均不需要**：
> - `refPropName` — AI 同时看到 `<to-one name="order">` 和 `<to-many name="lines">`，无需额外告知反向属性名。`OrmModelInitializer` 运行时会自动匹配。
> - `refDisplayName` / `ref-i18n-en:displayName` — 反向显示的标签名，仅在平台自动生成 `<to-many>` 时才有意义。显式声明时，显示名已在 `<to-many displayName="...">` 上。
> 
> 仅在平台自动推定反向关系（人工手写，仅写一端）的遗留模式中需要 `ref-*`。AI 维护的项目中，这些属性是噪音。

### 关系声明规范

| 关系类型 | tagSet | 声明位置 | 域约束 |
|---------|--------|---------|--------|
| to-one | `tagSet="pub"` | 从表端（持有 FK 的实体） | 允许跨模块引用（需 `notGenCode` 外部实体声明 + Maven 依赖） |
| to-many | `tagSet="pub,cascade-delete,insertable,updatable"` | 主表端（被 FK 指向的实体） | **禁止跨模块**（必须同域，`refEntityName` 的 `biz:moduleId` 与父实体一致） |

### 列的 tagSet 标签体系

`tagSet` 是逗号分隔的标签列表，控制列的行为。除了 `seq-default`（主键策略）、`not-query`（不可查询）等标签外，还有以下关键标签：

| 标签 | 作用位置 | 效果 |
|------|---------|------|
| `code` | column | 标记为业务编码列，自动集成 `@CodeRule` 编号生成 |
| `disp` | column | **标记为显示列**——代码生成时以此列的 `name` 作为实体的 `displayProp`。被 `<to-one>` 引用时，代码生成器会据此自动创建 `{relation.name}.{dispCol.name}` 路径属性（见下方"显示名解析"） |
| `parent` | column | 树形结构的父 ID 列 |
| `sort` / `sort-desc` | column | 列表默认排序，`sort-desc` 表示降序 |
| `del` | column | 逻辑删除标志列 |
| `audit` | column | 记录字段级变更日志到 `NopSysChangeLog` |

例如：
```xml
<column name="name" code="NAME" stdSqlType="VARCHAR" precision="200" tagSet="disp"/>
```

## 字典设计

### dict 定义

```xml
<dicts>
    <dict name="order/status" valueType="string">
        <option code="DRAFT" value="DRAFT" label="草稿"/>
        <option code="SUBMITTED" value="SUBMITTED" label="已提交"/>
        <option code="APPROVED" value="APPROVED" label="已审核"/>
        <option code="CANCELLED" value="CANCELLED" label="已作废"/>
    </dict>
</dicts>
```

### 规范

1. **dict name 格式**：`{模块简称}/{kebab-case名称}`（如 `order/status`、`job/schedule-status`）。
2. **dict option code**：UPPER_SNAKE_CASE（如 `RUNNING`、`AUTO_CANCEL`）。**`code` 用于 Java 常量后缀**。
3. **dict option value**：**推荐使用字符串类型**（`valueType="string"`），值为语义完整的英文单词（如 `"DRAFT"` `"APPROVED"`）。
4. **dict option label**：显示文本，支持 i18n。
5. **字段引用**：`ext:dict="{模块简称}/{kebab-case名称}"`。
6. **禁止**：boolean 型字段不设 dict。

> dict name 斜杠后的部分**必须用 kebab-case**，不能用 snake_case。

### 字典值类型选择（重要）

**推荐 `valueType="string"`**，原因：

| 维度 | `valueType="int"` | `valueType="string"` |
|------|-------------------|----------------------|
| AI 可读性 | ❌ `status == 2` 无意义 | ✅ `status == "APPROVED"` 自解释 |
| AI 代码生成 | ❌ 需查字典才能生成正确逻辑 | ✅ 直接生成可读代码 |
| SQL 可读性 | ⚠️ `WHERE status = 2` 需注释 | ✅ `WHERE status = 'APPROVED'` |
| 重构友好 | ❌ 插入新值需重新排序 | ✅ 插入新值不影响已有数据 |
| 多语言 | ❌ 值无语义 | ✅ 值本身是语义编码 |
| 跨系统集成 | ❌ 第三方需对照字典 | ✅ 语义编码是行业标准 |

**字典值命名规范**：
- 全大写，下划线分隔
- 语义完整的英文单词
- 长度 3-12 字符，不需要固定长度
- 示例：`DRAFT` `SUBMITTED` `APPROVED` `REJECTED` `CANCELLED` `COMPLETED` `CLOSED`

**常见字典值参考**：

| 类别 | 推荐值 |
|------|--------|
| 状态（生命周期） | `DRAFT` `SUBMITTED` `APPROVED` `REJECTED` `CANCELLED` `COMPLETED` `CLOSED` `REVERSED` `VOIDED` |
| 财务状态 | `UNPOSTED` `POSTED` `REVERSED` |
| 收付状态 | `UNPAID` `PARTIAL` `PAID` `UNRECEIVED` `RECEIVED` |
| 方向 | `DEBIT` `CREDIT` |
| 类型 | `PURCHASE` `SALES` `TRANSFER` `ADJUSTMENT` `PRODUCTION` |
| 期间状态 | `NEVER_OPENED` `OPEN` `CLOSING` `CLOSED` |

### code 与 value 的区别

| 属性 | 用途 | 示例 |
|------|------|------|
| `code` | **Java 常量后缀**，与字典名前缀组合生成常量 | `DRAFT`、`APPROVED` |
| `value` | 数据库存储的实际值，常量的值 | `"DRAFT"`、`"APPROVED"` |
| `label` | 显示文本，支持 i18n | "草稿"、"已审核" |

**生成的 Java 常量**（源码依据：`_{moduleClassPrefix}DaoConstants.java.xgen`）：

```java
// 自动生成的常量接口
public interface _AppErpDaoConstants {
    // String 类型字典（推荐）
    String ORDER_STATUS_DRAFT = "DRAFT";           // 草稿
    String ORDER_STATUS_SUBMITTED = "SUBMITTED";   // 已提交
    String ORDER_STATUS_APPROVED = "APPROVED";     // 已审核
    String ORDER_STATUS_CANCELLED = "CANCELLED";   // 已作废
}
```

**常量命名规则**：
- 前缀：字典名斜杠后的部分，转大写下划线（如 `order/status` → `ORDER_STATUS`）
- 后缀：`code` 的值（如 `DRAFT`）
- 完整常量名：`{前缀}_{后缀}`（如 `ORDER_STATUS_DRAFT`）

**代码中使用**：
```java
// 通过常量引用字典值
if ("APPROVED".equals(order.getStatus())) {
    // 处理已审核状态
}

// 或使用生成的常量
if (_AppErpDaoConstants.ORDER_STATUS_APPROVED.equals(order.getStatus())) {
    // 处理已审核状态
}
```

**常量类型**：
- `valueType="string"`：生成 `String` 常量（推荐）
- `valueType="int"`：生成 `int` 常量（不推荐，仅用于兼容已有 INTEGER 列）

**实战警示**：
- **option code 禁止带中横线**：`code="to-one"` 会生成 Java 标识符 `RELATION_TYPE_to-one`，编译器无法通过。必须用 UPPER_SNAKE_CASE（`TO_ONE`），带横线的值放在 `value` 属性上。
- **生成的常量在 `-core` 模块的 `_*CoreConstants.java`**：该模块必须有 `pom.xml` 且在父 `pom.xml` 的 `<modules>` 中；引用常量的模块必须添加 `-core` 依赖。
- **Java 代码禁止硬编码字典值**：必须用生成的常量（如 `_NopMetadataCoreConstants.MODULE_STATUS_DRAFTING`），不用手写 `"DRAFTING"`。

### 状态机 dict 禁止死状态

**每个状态值必须有 ≥1 个可达写入路径**（grep `setStatus` 等 writer）。声明了但运行时永不出现的状态值 = 死状态：驱动 UI 下拉/筛选时会误导用户，owner doc 里的进出迁移图也失真。

- **检测**：对每个 dict option value `grep "set<Field>("`（或 `set<Field>Code(` 常量）找到全部写入点，逐一核对可达性；无 writer 的值按"删 / 实现迁移 / 显式 Deferred 登记"三选一裁决，禁止沉默保留。
- **预防（新建 dict 时）**：每加一个状态值先回答"谁会写这个值、什么路径到达"，答不出就不加。
- 与 `03-runbooks/add-dict-and-constants.md` 配合使用。

## 通用字段（框架自动管理）

以下字段需要在 ORM 模型中声明，框架在运行时自动维护其值，业务代码不需要手工读写：

| 字段 | 说明 |
|------|------|
| 逻辑删除字段 | `delVersion`（BIGINT，默认推荐，详见 `logical-deletion.md`）；或 `deleted`（BOOLEAN，仅用于已给定数据模型不便修改的兼容场景） |
| 创建时间 | `CREATE_TIME` |
| 更新时间 | `UPDATE_TIME` |
| 创建人 | `CREATED_BY` |
| 乐观锁版本号 | 自动管理 |

## 字段类型选择指南

### 金额与精确数值：使用 BigDecimal

**必须使用 BigDecimal 的场景**：
- 金额（价格、成本、费用、税额、折扣）
- 数量（库存数量、采购数量）
- 汇率、利率
- 任何需要精确计算的数值

**不要使用 double/float 的场景**：
- 金额计算（浮点数有精度丢失问题）
- 库存数量（必须精确）
- 统计汇总

**financial 字段必须使用 domain 标注**，便于程序识别业务语义：

| domain | 业务含义 | precision | scale | 示例 |
|--------|----------|-----------|-------|------|
| `amount` | 金额 | 18 | 2 | `<column name="amount" domain="amount" .../>` |
| `price` | 单价 | 18 | 4 | `<column name="unitPrice" domain="price" .../>` |
| `quantity` | 数量 | 18 | 4 | `<column name="quantity" domain="quantity" .../>` |
| `exchangeRate` | 汇率 | 10 | 6 | `<column name="exchangeRate" domain="exchangeRate" .../>` |
| `interestRate` | 利率 | 5 | 4 | `<column name="rate" domain="interestRate" .../>` |
| `taxRate` | 税率 | 5 | 2 | `<column name="taxRate" domain="taxRate" .../>` |

**ORM 配置示例**：
```xml
<!-- 金额字段：使用 domain="amount" 标注业务语义 -->
<column name="amount" code="AMOUNT" domain="amount" stdSqlType="DECIMAL" precision="18" scale="2" stdDataType="decimal"/>

<!-- 汇率字段：使用 domain="exchangeRate" 标注业务语义 -->
<column name="exchangeRate" code="EXCHANGE_RATE" domain="exchangeRate" stdSqlType="DECIMAL" precision="10" scale="6" stdDataType="decimal"/>

<!-- 数量字段：使用 domain="quantity" 标注业务语义 -->
<column name="quantity" code="QUANTITY" domain="quantity" stdSqlType="DECIMAL" precision="18" scale="4" stdDataType="decimal"/>
```

### VARCHAR 长度与字符编码

`precision` 的语义取决于具体 dialect：Nop 把 `precision` 传给 dialect 生成列声明的长度。在 MySQL 中 `VARCHAR(n)` 的 `n` 是**字符数**（character），不是字节数；其他数据库/编码可能不同。

> 下表的中文存储估算按"每字符约 3 字节"的保守口径给出，用于粗略规划。MySQL 下 VARCHAR 长度按字符数计算，实际可容纳的字符数通常比下表估算更多。

**中文存储计算**：
- UTF-8 编码：1 个中文字符 = 3 字节
- 如果需要存储 10 个中文字符，precision 至少设为 30

**常见字段长度建议**：

| 字段类型 | precision | 可存储内容 |
|----------|-----------|-----------|
| 编码/单号 | 36 | UUID 或业务单号 |
| 名称（短） | 100 | 约 33 个中文字符 |
| 名称（中） | 200 | 约 66 个中文字符 |
| 名称（长） | 500 | 约 166 个中文字符 |
| 地址 | 500 | 约 166 个中文字符 |
| 备注 | 1000 | 约 333 个中文字符 |
| 长文本 | 2000+ | 使用 TEXT 类型 |

**MySQL 类型自动转换**（阈值由 `mysql.dialect.xml` 的 `sqlDataType` 配置决定，不同 dialect 可能不同）：

- precision ≤ 16383：VARCHAR
- 16383 < precision ≤ 65535：TEXT
- 65535 < precision ≤ 16777215：MEDIUMTEXT
- precision > 16777215：LONGTEXT（stdSqlType=CLOB）

### 日期时间类型选择

| stdSqlType | Java 类型 | 用途 | 示例 |
|------------|----------|------|------|
| DATE | LocalDate | 只需要日期，不需要时间 | 出生日期、到期日期、会计期间起止日 |
| DATETIME | LocalDateTime | 日期 + 时间（不带时区） | 创建时间、审核时间、业务发生时间 |
| TIMESTAMP | Timestamp | 精确时间戳（带时区） | 系统日志、审计追踪、分布式系统时间同步 |

**选择建议**：

| 场景 | 推荐类型 | 理由 |
|------|----------|------|
| 出生日期 | DATE | 只需要日期 |
| 会计期间 | DATE | 期间起止只到日期 |
| 单据创建时间 | DATETIME | 需要精确到秒，业务不涉及时区 |
| 审核时间 | DATETIME | 同上 |
| 系统日志 | TIMESTAMP | 需要精确时间戳 |
| 跨时区应用 | TIMESTAMP | 自动处理时区转换 |

**ORM 配置示例**：
```xml
<!-- 只需要日期 -->
<column name="birthDate" code="BIRTH_DATE" stdSqlType="DATE" stdDataType="date"/>

<!-- 日期 + 时间 -->
<column name="createTime" code="CREATE_TIME" stdSqlType="DATETIME" stdDataType="datetime"/>

<!-- 精确时间戳 -->
<column name="logTime" code="LOG_TIME" stdSqlType="TIMESTAMP" stdDataType="timestamp"/>
```

### 字段设计注意事项

1. **避免使用 tinyint / short 类型，统一使用 int**。存储空间已不是瓶颈，使用 `short` 会导致 Java 常量赋值时必须 `(short)` 强制转型，增加噪音且容易出错。布尔标记字段直接用 `stdDataType="boolean"` + `stdSqlType="BOOLEAN"`（或通过 domain 使用 TINYINT 映射到 boolean）。
2. **precision 必须按实际数据大小设置**。框架会根据 dialect 和 precision 自动选择 VARCHAR → TEXT → MEDIUMTEXT → LONGTEXT。过大的 precision 会导致 MySQL 用 TEXT 而非 VARCHAR，影响索引和查询性能。详见 `model-first-development.md`。
3. **`stdDomain="json"` 或 `tagSet="json"`** 会自动生成 `JsonOrmComponent`，允许在 Java 代码中直接操作 JSON 对象。详见 `model-first-development.md`。
4. **`stdDomain="file"` / `"file-list"`** 会自动生成文件附件组件。详见 `model-first-development.md`。
5. **`tagSet="mapper"`（实体级）** 会自动生成 sql-lib mapper 三件套：`{ShortName}Mapper.java` 接口（retention 文件）、`{ShortName}.sql-lib.xml` 骨架（retention 文件）、`_dao.beans.xml` 中的 `SqlLibProxyFactoryBean` 注册（每次 codegen 重新生成）。详见下方"Sql-Lib Mapper 自动生成"。
6. **`notGenCode="true"`** 标记的字段不会生成 Java get/set 方法，始终作为动态属性存取。

## Sql-Lib Mapper 自动生成（`tagSet="mapper"`）

当实体需要在 `@SqlLibMapper` 中定义原子 SQL 查询（如聚合、批量更新、条件扣减）时，**不要手写 mapper 接口和 bean 注册**——在 ORM 模型的实体上添加 `tagSet="mapper"`，codegen 会自动生成三件套：

| 生成物 | 路径 | 是否 retention | 说明 |
|--------|------|---------------|------|
| Mapper 接口 | `{basePackage}.dao.mapper.{ShortName}Mapper.java` | 是（不覆盖） | 空接口，带 `@SqlLibMapper` 注解，开发者在其上添加方法 |
| sql-lib 骨架 | `_vfs/{moduleId}/sql/{ShortName}.sql-lib.xml` | 是（不覆盖） | 空 `<sqls>` 容器，开发者在其上添加 `<eql>`/`<sql>` |
| Bean 注册 | `_vfs/{moduleId}/beans/_dao.beans.xml` | 否（每次重新生成） | `SqlLibProxyFactoryBean` 自动注册，bean id = `{basePackage}.dao.mapper.{ShortName}Mapper` |

### 操作步骤

1. 在 `model/*.orm.xml` 的实体上添加 `tagSet="mapper"`：
   ```xml
   <entity name="io.nop.app.entity.NopJobTask"
           tableName="nop_job_task"
           tagSet="mapper"
           ...>
   ```
2. 运行 `mvn install -pl {app}-dao -am`，codegen 自动生成三件套
3. 在生成的 `{ShortName}Mapper.java` 中添加查询方法（参数用 `@Name("xxx")` 标注）
4. 在生成的 `{ShortName}.sql-lib.xml` 中添加对应的 `<eql>` 或 `<sql>` 查询
5. 在 store / BizModel 中通过 `@Inject` 注入 mapper 并调用

### 为什么不用手写

手写 mapper 接口和手动在 `_dao.beans.xml` 注册 `SqlLibProxyFactoryBean` 是**错误做法**——`_dao.beans.xml` 是 codegen 生成物，每次 `mvn install` 都会重新生成，手写的注册会被覆盖。必须通过 `tagSet="mapper"` 让 codegen 纳入自动管理。

## 启动时数据库初始化

Nop 平台提供两个启动时初始化机制，通过配置开关激活，均在 `orm-defaults.beans.xml` 中注册为条件 bean。

### 自动建表：DataBaseSchemaInitializer

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `nop.orm.init-database-schema` | Boolean | `false` | 启动时根据 ORM 模型自动执行 DDL 建表（表不存在时才创建） |

### 自动初始化数据：DataInitInitializer

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `nop.orm.init-database-data` | Boolean | `false` | 启动时从 CSV/SQL 文件初始化数据 |
| `nop.orm.init-database-data-location` | String | `/_init-data/` | 初始化数据文件的 VFS 目录路径 |

`DataInitInitializer` 在 `DataBaseSchemaInitializer` 之后执行，按以下顺序处理：

1. **CSV 数据插入**：按 ORM 模型的拓扑序遍历所有实体，对每个实体检查 `{location}/{tableName}.csv` 是否存在。若存在，解析 CSV 并通过 ORM `dao.saveEntity()` 插入。
   - CSV 列名按实体列的 `code` 匹配（大写的数据库列名，如 `COLLEGE_ID`）
   - 列名不匹配时抛出 `NopException`（不静默忽略）
   - 数据通过 ORM 插入，自动设置租户、创建人等框架管理字段

2. **SQL 文件执行**：扫描 `{location}/` 下所有 `*.sql` 文件，按文件名排序后在事务中依次执行。
   - SQL 为原生 JDBC 语句，不经过 ORM
   - **需要注意**：如果实体启用了多租户（`tenantCol`），SQL 中必须手动包含租户列（如 `TENANT_ID`），否则数据不会在租户上下文中可见

### 使用示例

在 `application.yaml` 中配置：

```yaml
nop:
  orm:
    init-database-schema: true   # 自动建表
    init-database-data: true     # 自动初始化数据
    init-database-data-location: /_init-data/
```

在 `_vfs/_init-data/` 目录下放置数据文件：

```
_vfs/_init-data/
├── sims_college.csv      # 表名.csv，按拓扑序插入
├── nop_user.csv
├── 01-init-dict.sql      # 按文件名排序执行
└── 02-init-sequence.sql
```

## 列域设计：`stdDomain` 与 `domain`

列的类型和语义通过 `stdDomain`（标准域）和 `domain`（简写域）控制。两者的区别和使用场景：

> **生成业务编码须对齐目标列 domain 的 precision**：`buildCode` 拼接（如"长类型前缀 + 长单号"）可能超过目标列 `precision` → DB 报 22001 截断。应用层生成编码时先算长度、做长度守护/截断，不要依赖 DB 报错兜底；放宽 domain 的 precision 是跨表侵入面，需评估所有引用该 domain 的列。


### 判别原则：语义角色决定建模

"人"相关列分三类语义角色，**先判断角色，再选类型**。最常见的错误是不分角色、把"业务操作人"误建成员工引用或被动审计列。

| 语义角色 | 含义 | 主体 | 正确建模 |
|---------|------|------|---------|
| **操作人/责任人/归属** | 谁执行/负责/拥有此操作（ownerId, approverId, operatorId, assignedToId, teamLeaderId, pickerId, handlerId, requesterId, managerId, approvedBy, postedBy, closedBy） | 登录**系统用户**（认证主体） | `stdDomain="userId"` VARCHAR(36)，无 to-one |
| **被动审计** | 框架自动填充的纯合规轨迹（createdBy, updatedBy） | 登录用户 | `domain="createdBy"` VARCHAR(50) 登录名 |
| **HR 主体实体** | 员工记录本身是业务数据（用工合同、薪资、考勤、休假、绩效、部门归属） | 业务员工 | `<to-one>` → ErpMdEmployee（BIGINT） |

判别问题：**"该列记录的是认证主体的动作/归属，还是引用一个独立于登录的 HR 资源？"** 前者一律 `stdDomain="userId"`；后者才用员工 to-one。

### 操作人/责任人/归属列：`stdDomain="userId"`

引用系统用户的 FK 列——所有"谁执行/负责/拥有"语义的字段，无论命名是 `xxxId`（ownerId, approverId, operatorId, assignedToId, teamLeaderId, pickerId, handlerId, requesterId, managerId）还是 `xxxBy`（approvedBy, postedBy, closedBy）：

```xml
<column name="ownerId" stdDomain="userId" stdDataType="string" stdSqlType="VARCHAR" precision="36"/>
```

- 存储 **用户 UUID**（NopAuthUser 的主键是 `userId`，类型为 String UUID）
- **必须为 `VARCHAR(36)`**，不是 `BIGINT`
- **不需要 `<to-one>` 关联**到 NopAuthUser——平台通过 `stdDomain="userId"` 自动处理用户解析（展示名、过滤）
- **业务操作人字段（pickerId/handlerId/operatorId/…）也用 `userId`，不要建员工引用**：执行操作的是登录用户，不是"员工记录"。需要员工 HR 属性（部门、岗位）时在读时 user→employee 解析，而非在操作单据上反规范化员工 FK
- 命名约定：用 `xxxId`（如 `approverId`/`pickerId`）；若历史沿用 `xxxBy`（如 `approvedBy`），存的是用户 UUID 时也应标 `stdDomain="userId"`，避免"名称列存 ID"的语义错配

> **为什么操作人不建员工引用**：(1) 写入时捕获的是 `IUserContext.getUserId()`，员工引用需额外 user→employee 解析，对系统账号/外部审计员/无员工档案的管理员会失败；(2) 耦合员工实体生命周期，员工记录变更会污染历史事实；(3) `stdDomain="userId"` 已免费提供展示解析。员工引用仅留给"员工记录本身是业务主体"的 HR 实体。

### 被动审计列：`domain="createdBy"` 模式

**仅限**框架自动填充的纯合规轨迹字段（`createdBy`、`updatedBy`），存储用户登录名：

```xml
<column name="createdBy" domain="createdBy" stdDataType="string" stdSqlType="VARCHAR" precision="50"/>
```

- 存储 **用户登录名**（字符串），不是用户 UUID
- 长度 `VARCHAR(50)`，不是 `VARCHAR(36)`
- **不要加 `stdDomain="userId"`**——这是被动轨迹，不参与过滤/统计
- 遵循 Nop 内置 `domain="createdBy"` 的 `VARCHAR(50)` 惯例

> **`postedBy`/`approvedBy`/`closedBy` 不属于此类**。它们是业务动作责任字段（需按审核人过滤、统计、显示姓名），应建为 `stdDomain="userId"`（见上节）。旧版文档曾将它们并入 `createdBy`，该归类过粗，已修正。

### 员工引用列：仅 HR 主体实体

引用 ErpMdEmployee 的 FK 列——**仅当员工记录本身是业务数据时**（用工合同、薪资、考勤、休假、绩效、部门归属等 HR 实体）：

```xml
<column name="employeeId" stdDataType="string" stdSqlType="BIGINT"/>
<to-one name="employee" refEntityName="app.erp.md.dao.entity.ErpMdEmployee">
    <join><on leftProp="employeeId" rightProp="id"/></join>
</to-one>
```

- 员工主键是 `id`（BIGINT，Java 类型为 String，见「主键设计」强制规则）
- **不要加 `stdDomain="userId"`**
- 需要 `<to-one>` 关联到 `ErpMdEmployee`（主数据，本模块或跨模块均可）
- **操作人字段（pickerId/handlerId/operatorId/…）不要用员工引用**——见上节"操作人/责任人/归属列"

> **前置分派的边界场景**：若业务确需"把工作分派给某个尚未开通账号的 HR 人员"，该分派字段可建员工引用——但这是"分派目标"，与"谁实际执行"是两个字段，后者仍为 `userId`。若选择员工引用，ErpMdEmployee 必须携带 `userId` 链接，否则无法从登录会话解析到员工。

### 总结对照

| 场景 | 类型 | 域 | 关联 |
|------|------|-----|------|
| 操作人/责任人/归属（ownerId, approverId, operatorId, assignedToId, teamLeaderId, pickerId, handlerId, requesterId, managerId, approvedBy, postedBy, closedBy） | `VARCHAR(36)` | `stdDomain="userId"` | 无 to-one |
| 被动审计（createdBy, updatedBy） | `VARCHAR(50)` | `domain="createdBy"`（惯例） | 无 to-one |
| HR 主体实体员工引用（合同/薪资/考勤/绩效上的 employeeId） | `BIGINT` + `stdDataType="string"` | 无 | `<to-one>` → ErpMdEmployee |
| 一般业务 FK | `BIGINT` + `stdDataType="string"` | 无 | `<to-one>` → 业务实体 |

:::tip
用户 ID 在 Nop 平台中是 String UUID（如 `nop_auth_user.userId`），不是 Long。因此引用用户的 FK 列必须用 `VARCHAR(36)`。如果在 ORM 中错误声明为 `BIGINT`，编译生成的 Java 代码会因类型不匹配（`getId()` vs `getUserId()`）而失败。

**最易踩的坑**：把"业务操作人"（pickerId/handlerId/operatorId/approvedBy）误建为员工引用或被动审计列。记住——凡是"谁执行/负责/拥有此操作"，一律 `stdDomain="userId"`。
:::

## 关系声明：`<to-one>` 与 `<to-many>`

关系声明定义实体之间的关联，支持两种形式。

### 基本模式

```xml
<to-one name="order" refEntityName="app.erp.pur.dao.entity.ErpPurOrder">
    <join><on leftProp="orderId" rightProp="id"/></join>
</to-one>

<to-many name="lines" refEntityName="app.erp.pur.dao.entity.ErpPurOrderLine">
    <join><on leftProp="id" rightProp="orderId"/></join>
</to-many>
```

- `<to-one>` 定义子→父的引用（外键列在子表）
- `<to-many>` 定义父→子的集合（外键列在子表）
- `<join>` 中的 `leftProp` 是当前实体的列，`rightProp` 是被引用实体的列
- 标准约定：子表的 FK 列名为 `xxxId`（如 `orderId`），对应的 to-one `name` 为 `xxx`（如 `order`）

### 运行时语义：关联的透明懒加载

声明 `<to-one>`/`<to-many>` 后，codegen 生成类型化 getter（如 `task.getJobFire()`、`order.getOrderGoods()`）。**访问这些 getter 即透明触发懒加载，与 JPA 一致**：

- **触发时机**：getter 首次被调用时，ORM 引擎按外键值发起一次查询加载关联对象，无需手动查数据库。
- **前置条件**：实体必须 **orm-attached**（经 `dao`/`session` 从数据库加载，持有 `IOrmEntityEnhancer`）。在打开的 session 内（如 `@SingleSession` 方法、BizModel 请求线程）访问才有效。
- **缓存复用**：关联对象一旦在当前 session 加载过，再次访问 getter 复用同一实例，**不会重复查询**。
- **detached 实体不可用**：直接 `new NopXxx()` 构造的实体未 attach，访问关联 getter 会抛 `requireEnhancer`。**根因是实体不该 `new`**——应通过 `dao.newEntity()` / `xxxBiz.newEntity()` 创建（经 DAO 会 `orm_attach` enhancer + 注入 entityModel，懒加载、组件绑定等机制才能工作）。
  - **测试也应走** `daoProvider.daoFor(NopXxx.class).newEntity()`（继承 `JunitBaseTestCase` 即可注入 `IDaoProvider`），而非 `new NopXxx()`。仅在确实拿不到 DAO 的纯单元场景，才用 `entity.setJobFire(fire)` 手动链接关系作为权宜之计。
  - **生产代码**中调用方已持有被关联实体时，可把它作为**方法参数显式传入**（如 `getEffectiveParams(fire)`），避免引入对懒加载的隐式依赖。

> **判别要点**：调用方已持有被关联实体（如 `fire` 已作为参数传入）时，**直接传参**比走关联 getter 懒加载更稳、更省——关联懒加载是为"只有一端引用"的场景设计的。

### `ref-*` 属性：AI 时代不需要

`refPropName`、`refDisplayName`、`ref-i18n-en:displayName` 都是平台在**只有一端关系声明时**自动推定反向关联的辅助属性：

| 属性 | 旧用途（仅写一端） | AI 维护（两端都写） |
|------|-------------------|-------------------|
| `refPropName` | 指定反向集合属性名（如 `<to-one refPropName="children">`） | 不需要 — AI 直接声明 `<to-many name="children">` |
| `refDisplayName` | 为自动生成的 `<to-many>` 提供中文显示名 | 不需要 — 已在 `<to-many displayName="...">` 上 |
| `ref-i18n-en:displayName` | 同上，英文版 | 不需要 — 已在 `<to-many i18n-en:displayName="...">` 上 |

**不要在 `refPropName` 上浪费时间。** `OrmModelInitializer` 会在启动时自动匹配：找到被引用实体上 `leftProp`/`rightProp` 对应的 `<to-one>`，取其 `name` 作为反向属性名。显式写 `refPropName` 只是重复了运行时已能自动推导的信息。

### 显示名解析：`{relation}.{displayProp}` 自动路径属性

`<to-one tagSet="pub">` 配合被引用实体的 `tagSet="disp"` 列，代码生成器会在 xmeta 中自动创建**路径属性**：

```
被引用实体 ErpMdPartner 的 name 列有 tagSet="disp"
    ↓
引用方 xmeta 自动生成:
  <prop name="partner.name" internal="true" queryable="true" sortable="true" lazy="true">
      <schema type="String"/>
  </prop>
```

**触发条件**（xmeta 代码生成器 `_{shortName}.xmeta.xgen` 第 117 行）：
1. `<to-one>` 上有 `tagSet="pub"`
2. 被引用实体有 `tagSet="disp"` 列的列（通过 `IEntityModel.getDisplayPropModel()` 查找）
3. 两者同时满足 → 自动生成 `<prop name="{relation}.{dispColName}">`

同时，`_{shortName}.xmeta.xgen` 第 106 行生成 `ext:joinRightDisplayProp`：
```xgen
ext:joinRightDisplayProp="${rel.singleColumnJoin?.rightDisplayPropModel?.name}"
```
这就是 `{relation}.{displayProp}` 路径属性的名称（如 `"partner.name"`）。

### 前端自动渲染

有了上述 xmeta 配置，**不需要手动修改 view.xml**。`control.xlib:view-relation` 标签（976-997 行）在 AMIS 页面渲染时自动处理 FK 列：

```javascript
const relProp = XuiHelper.getRelationProp(propMeta, objMeta);
const labelProp = propMeta['graphql:labelProp'] || relProp['ext:joinRightDisplayProp'];
if(labelProp){
    return {
       type: 'static',
       name: propMeta['graphql:labelProp'] || relProp.name + '.' + relProp['ext:joinRightDisplayProp']
    }
}
```

**完整管线**：

```
view.xml 列: id="partnerId"            ← AMIS grid 列定义，无需改为 partnerName
    ↓
control.xlib view-relation 标签        ← 读取 ext:relation → ext:joinRightDisplayProp
    ↓                                   → 将列 name 从 "partnerId" 改为 "partner.name"
AMIS 列: name="partner.name"           ← 自动映射，显示 "五金件" 而非原始 ID
```

**同时**，`XuiViewAnalyzer.addRelationDispProp`（`XuiViewAnalyzer.java:381-395`）在构建 GraphQL selection 时自动将 `partner.name` 加入请求字段。后端返回 `{"partner.name": "五金件"}`，AMIS 直接渲染。

**要点**：view.xml 中 FK 列保持原始列名 `partnerId`，无需改为 `partner.name` 或 `partnerName`。所有转换由 `control.xlib:view-relation` 在页面生成时自动完成。

**运行时行为**：路径属性是懒加载的（`lazy="true"`），首次访问时通过 ORM 透明懒加载获取。对于列表批量场景，应通过 `orm().batchLoadProps(rows, "partner")` 预加载关联对象，避免 N+1。

### 跨模块引用

跨模块引用（`notGenCode="true"` 实体桩）同样支持此机制。代码生成器见到的是**本模块的桩定义**，只要桩的显示列也标注 `tagSet="disp"`，`ext:joinRightDisplayProp` 就能正常生成：

```xml
<!-- module-purchase 中的桩 -->
<entity name="app.erp.md.dao.entity.ErpMdPartner" notGenCode="true" ...>
    <columns>
        <column name="name" code="NAME" stdSqlType="VARCHAR" precision="200" tagSet="disp"/>
    </columns>
</entity>
```

代码生成器通过桩的 `getDisplayPropModel()` 找到 `tagSet="disp"` 的列 → `rightDisplayPropModel` 非空 → 生成 `ext:joinRightDisplayProp="name"` → `view-relation` 标签正常处理。**同模块和跨模块的行为完全一致。**

实践中，每个引用主数据实体的模块都需要在桩的 `name`（或 `code`）列上加 `tagSet="disp"`。这是重复的手工步骤，但一次配置永久自动，远优于手动维护 `@BizLoader` 方法。对于大量跨模块引用场景的方案选择，见 `cross-module-entity-reference.md` 中的"显示名解析策略对比"。

## 跨表属性：`<alias>` 与扩展属性表（`kvTable`）

实体需要把属性放在主表以外的物理表时，**用 alias，不要用 JPA 的二级表**（Nop 不支持 `@SecondaryTable` 那种"按列把实体摊到多表、运行期决定字段来源"的形态）。

### 三类 alias

| 形态 | 模型声明 | 用途 |
|------|---------|------|
| **关联表列投影** | `<alias name="collegeName" propPath="simsCollege.collegeName" type="String"/>` | 把 to-one 关联表上的列投影成本地属性 |
| **纵表扩展属性投影** | 扩展表实体声明 `kvTable="true"`；本实体 `<alias name="extFldA" propPath="ext.fldA.string" type="String"/>` | 动态扩展属性存独立扩展表 |
| **组件内路径投影** | `<alias name="examScoreNormalized" propPath="examScoreDecimal.normalizedValue" type="BigDecimal"/>` | 组件字段投影为顶层属性 |

**alias 是可读可写的属性路径投影**，不是只读视图。生成代码的 getter/setter 落到 `internalGetAliasValue/internalSetAliasValue`，后者对整条 `propPath` 做 `BeanTool.getComplexProperty/setComplexProperty`；写入沿路径进行——关联投影写的是关联实体、组件路径写的是组件字段、纵表路径写的是扩展表行——并由该路径自身的持久化机制落库。

`<alias>` 的 `propPath` 是属性路径（可含 `.` 下钻），`type` 是 Java 泛型类型（`<column>` 上没有 `type`，这是与列模型的关键区别）。alias 不是列：`getColumns()` 为空、`hasLazyLoadColumn()` 为 false、不参与 INSERT/UPDATE 的列清单。因此排序/过滤走 alias 时是按属性路径定位的，别把它当成本地列写 SQL 片段。

### 纵表扩展属性表（`kvTable`）

```xml
<!-- 全局通用扩展表：主键 (entityName, entityId, fieldName) -->
<entity name="io.nop.app.SimsExtField" tableName="SIMS_EXT_FIELD" kvTable="true" useTenant="true">
    <columns>
        <column name="entityName" code="ENTITY_NAME" primary="true" propId="1" .../>
        <column name="entityId"   code="ENTITY_ID"   primary="true" propId="2" .../>
        <column name="fieldName"  code="FIELD_NAME"  primary="true" propId="3" .../>
        <column name="fieldType"  code="FIELD_TYPE"  propId="4" .../>   <!-- StdDataType.ordinal -->
        <column name="decimalValue"  code="DECIMAL_VALUE"  propId="5" .../>
        <column name="dateValue"     code="DATE_VALUE"     propId="6" .../>
        <column name="timestampValue" code="TIMESTAMP_VALUE" propId="7" .../>
        <column name="stringValue"  code="STRING_VALUE"  propId="8" .../>
    </columns>
</entity>
```

- `kvTable="true"` 的实体实现 `IOrmKeyValueTable`（"支持纵表存储"），按 `fieldType` 分列存放不同数据类型，主键三元组定位一行。
- 主实体侧通过 `<to-many>` 关联它，得到 `entity.getExt()`，元素是 `IOrmKeyValueTable`；`prop_get(fieldName)` / `prop_set(fieldName, value)` 按字段名读写。
- 实体类上有 `ext` 集合属性与 `getExtFldA()` 之类的**类型化快捷属性**（由 alias 生成）。`TestExtFields` 覆盖了完整用法：`BeanTool.setComplexProperty(entity, "ext.测试参数2.double", 2.0)` 写、`entity.getExtFldA()` 读、`dao.batchLoadProps(list, "ext")` 批量预取、以扩展字段值作为关联条件。
- 该机制在 `nop-sys`、`nop-wf`、`nop-dyn` 均已使用（见各模块 `_delta/.../app.orm.xml`），是给平台实体加动态字段的既定路径。
- **注意**：`TestExtFields` 演示的"模型中定义但未生成 get/set 的字段"（`entity.prop_set("extField", 5)`）
  用的是**已生成并签入的** 实体基类 `io.nop.app._SimsExam`（位于 nop-orm 测试树的 `io/nop/app/_gen/` 目录下；该测试类头注释也写明"修改了 app.orm.xml 需重新运行 OrmCodeGen"）。
  也就是说**实体类本身仍然是必需的**，只是个别字段不需要访问器。
  若要连实体类一并免掉，见下方「免代码生成：动态实体 `DynamicOrmEntity`」。

### 在 EQL 中使用 alias

alias 名在 EQL 里就是普通列名，**出现在任何列位置都可以**：SELECT 投影、WHERE、GROUP BY、ORDER BY，也可参与算术表达式（`o.extFldB*2 + 3`）。

```sql
select o, o.extFldA, o.extFldB*2 + 3
  from io.nop.app.SimsExam o
 where o.extFldB = 1 and o.extFldA = 'x'
 order by o.extFldA
```

编译器解析列名时先查真实字段，查不到就回落到 alias 的 `propPath`（`EqlTransformVisitor.resolveColName` 的 `getAliasPropPath` 分支），再交给与手写属性路径**完全相同**的 `resolvePropPath` 隐式 JOIN 逻辑。所以：

- `select o.collegeName`（alias）与 `select o.simsCollege.collegeName`（显式路径）编译产物等价，无需二选一。
- ORDER BY 中引用可空 to-one 的 alias 会被隐式转成 LEFT JOIN（与其他属性路径同一启发式），排序不会因 NULL 被过滤掉。
- alias 只认 `<alias>` 声明的属性（`OrmEntityModel.getAliasPropPath` 仅接受 `IEntityAliasModel`）；`<compute>` 属性没有对应列，不能当列名用在 EQL 里。
- 别把 alias 当本地列拼裸 SQL——它背后可能是 JOIN 或另一张表，跨表语义只有 EQL / DAO 层会正确处理。

## 可检索加密：`enc` 列的辅助查询列（assisted / like）

**何时需要**：列已标记 `enc`（透明加密存储），但仍需按该列做等值或模糊查询。随机 IV 加密的密文不可检索，需要额外的确定性辅助列承载查询。

**声明语法**（扩展属性 + 角色标签；该特性的设计契约与被拒替代方案由平台设计文档承载，本节只写使用契约）：

```xml
<!-- 主列：tagSet 含 enc，ext 属性指向辅助列 -->
<column name="email" tagSet="enc"
        ext:enc-assisted="emailAssisted" ext:enc-like="emailLike"
        stdSqlType="VARCHAR" precision="256"/>
<!-- 辅助列：普通模型列（参与建表与 diff 升级），角色由标签标记 -->
<column name="emailAssisted" tagSet="enc-assisted" stdSqlType="VARCHAR" precision="64"/>
<column name="emailLike" tagSet="enc-like" stdSqlType="VARCHAR" precision="2048"/>
```

- `emailAssisted` 存 `HMAC-SHA256` 十六进制摘要（定长 64，密钥 `nop.crypt.enc-assisted-key`，空则回落 `nop.crypt.default-enc-key`），承载等值/IN/不等值检索。
- `emailLike` 存逐字符定宽摘要拼接（宽度 `nop.orm.enc-like-digest-width` 缺省 8），承载 `LIKE / NOT LIKE / ILIKE` 检索；列长与明文长度成正比（已知泄露面，不接受则只用 assisted）。
- 两个辅助列都可以只声明其一；辅助列值由框架在保存时自动派生，业务代码对辅助列赋值会报错。

**模型加载期校验**（配置错误启动即失败，不推迟到首次查询）：

| 校验 | 错误码 |
|---|---|
| 声明 ext 引用的列未标记 `enc` | `nop.err.orm.model.enc-assisted-not-enc` |
| ext 引用指向不存在的列 | `nop.err.orm.model.enc-helper-unknown-column` |
| 辅助列缺少对应角色标签（或标签与引用类型不符） | `nop.err.orm.model.enc-helper-tag-mismatch` |
| 辅助列为主键列 / 非 VARCHAR | `nop.err.orm.model.enc-helper-primary-key` / `enc-helper-not-varchar` |
| 辅助列再声明辅助引用（含自引用） | `nop.err.orm.model.enc-helper-chained` |
| 角色标签孤立（未被任何主列引用） | `nop.err.orm.model.enc-helper-tag-orphan` |
| 同一辅助列被多个主列引用 | `nop.err.orm.model.enc-helper-duplicate` |

**查询语义**：主列上的 `= / IN / != / NOT IN` 自动改写到 assisted 列，`LIKE / NOT LIKE / ILIKE` 自动改写到 like 列（`%`/`_` 通配语义保持；ILIKE 两侧小写规范化后摘要，不依赖方言 ILIKE 函数）。范围谓词（`< > BETWEEN`）、`ORDER BY / GROUP BY / JOIN ON` 涉及加密主列、含 `escape` 的 LIKE、正则字符类均显式报错（`nop.err.orm.enc-column-not-searchable`），不做静默全表解密。

改写覆盖两条产出路径：EQL/QueryBean（编译期 AST 改写，参数经执行期摘要，详见 `eql-and-database-compatibility.md`）与 **GenSqlHelper 直拼路径**——`findAllByExample / findFirstByExample / findPageByExample / countByExample / deleteByExample / updateByExample` 的逐列等值 WHERE，以及模型 `<filters>` 固定 filter（load/lock/delete/collection load）。该路径过滤值在 SQL 构建期已知，直接摘要后绑定；只声明 like 辅助列的加密列做等值过滤时显式报错（`eq-needs-enc-assisted-helper`）。声明了辅助列的加密主列出现在按主键比对（`genIdEq`）或 `useRevision` 实体 findLatest 的全列比对中同样显式报错（随机 IV 密文比对恒不命中，摘要无法表达这些比对）；example 查询的 `orderBy` 触及加密主列亦显式报错。

**限制（重要）**：`updateByExample` 等 bulk UPDATE 直接 SET 加密列时，框架**不**维护辅助列（既定裁定：归业务层校验）——加密列的批量变更请走逐实体保存，或在业务层同步维护辅助列。未声明辅助列的 `enc` 列维持纯加密存储行为（等值查询检索不到，属预期）。固定 filter（`<filters>`）是分区语义：保存时 persister 会把 filter 列强制写为 filter 值（`bindFilter`），filter 指向加密主列时所有经 ORM 保存的行都满足 filter。

**存量数据回填**（辅助列是后加声明、存量行辅助列为空时使用）：`EncAssistedBackfiller`（`io.nop.orm.enc`，构造注入 `IOrmSessionFactory`）提供 `backfillAll()` 与 `backfillEntity(entityName)`：按主键排序分批读 id+主列密文 → 解密 → 重算辅助列 → 仅批量更新辅助列。解密与读路径语义一致（`@enc:` 前缀剥离后经 `ITextCipher.decrypt` 识别 v1/legacy 格式；未带前缀的存量值按明文处理）。幂等（重复执行重算覆盖，结果一致）；主列为 null 的行跳过；自定义密码器时须与 `DefaultOrmColumnBinderEnhancer` 的密码器一致。分批大小配置 `nop.orm.enc-backfill-batch-size`（缺省 500）。辅助列声明加入已有模型后，既有 auto-upgrade（`nop.orm.db-differ.auto-upgrade-database`，需引入 `nop-dbtool-core`）会自动对存量库 `ALTER TABLE ADD COLUMN`，再执行回填即可完成存量迁移。

## 免代码生成：动态实体 `DynamicOrmEntity`

**何时需要**：想在 ORM 模型里声明实体（能写 EQL、能建表、能 CRUD），但**不生成** Java 实体类。

**怎么做**：在 `<entity>` 上指定 `className="io.nop.orm.support.DynamicOrmEntity"`。

```xml
<!-- app.orm.xml -->
<entity name="test.entity.SimsRevision" tableName="TEST_SIMS_REVISION" useRevision="true"
        className="io.nop.orm.support.DynamicOrmEntity">
    <columns>
        <column name="id" code="ID" stdSqlType="BIGINT" primary="true" propId="1"/>
        <column name="extField" code="EXT_FIELD" stdSqlType="INTEGER" propId="2"/>
    </columns>
</entity>
```

这样**不需要跑 OrmCodeGen**，也不需要任何 retention 子类：表由 `createTables()` 自动建出，
实体实例由 `DynamicOrmEntity` 直接构造。

**为什么必须显式写 `className`**：`SessionFactoryImpl.getEntityClassModel`
（`SessionFactoryImpl.java:412-423`）在**模型加载期**就对**每个**实体解析实体类，
且 `EntityPersisterImpl.init`（`EntityPersisterImpl.java:100`）是**eager** 调用——
即便该实体从未被查询，类不存在也会让整个 `SessionFactory` 初始化失败。
解析规则是：`className` 为空则**回退用实体 `name` 当全限定类名**去 `loadClassModel`，
于是 `ClassNotFoundException` 从 `PersistEnvBuilder.java:124-127` 抛出，
堆栈里带一行 `createEntityPersistDriver:{entityName}` 看似是别的问题，实为实体类缺失。

**动态字段怎么存的**：`DynamicOrmEntity` 内部有 `private MapOfInt<Object> values`
（`DynamicOrmEntity.java:36`，按 **propId** 索引的 int-keyed map，不是按字段名的 `Map<String,Object>`）。
模型里声明了 `propId` 但类上没有对应 getter/setter 的列，经
`IPropGetMissingHook` / `IPropSetMissingHook` 落到这里，存取用：

```java
entity.prop_get("extField");
entity.prop_set("extField", 5);
// 或
BeanTool.setProperty(entity, "extField", 5);
```

注意 `OrmEntity` 自己**没有**这个 Map——它只有一个 `_t`（`OrmEntity.java:93`），
javadoc 明确写"保存一些**临时**属性，实现类似缓存的作用"，且 `orm_unload()` / `orm_reset()` 会清空，
**不参与持久化**。别把 `_t` 当扩展字段存储用。

**三种免生成方式的区别**：

| 方式 | 写法 | 粒度 | 说明 |
|---|---|---|---|
| 动态实体 | `<entity className="io.nop.orm.support.DynamicOrmEntity">` | 单个实体 | 推荐；测试与只读投影实体常用 |
| 整模动态 | `<orm orm:forceDynamicEntity="true">` | 整个模型 | codegen 时把所有实体 `className` 改写为 `DynamicOrmEntity`（`kv-table` 标签的改为 `DynamicOrmKeyValueTable`，见 `orm-gen.xlib:64-73`）；**仅 codegen 期属性**，`orm.xdef` 里没有声明 |
| 动态实体名单 | `SessionFactoryImpl.setDynamicEntityNames(names)` | 按名字集合 | 代码级开关；**全仓库无调用方**，属未验证 API，勿优先使用 |

**与 `notGenCode="true"` 的区别（易混）**：两者都"运行时有效但不生成代码"，但层次不同——
`notGenCode` 是**字段/关系/alias 级**（该字段不生成 get/set，落到动态属性）；
`className=DynamicOrmEntity` 是**实体级**（整个实体类都不存在）。
`notGenCode` 仍然要求实体类存在，只是那个类缺某些字段的访问器。

**已知限制**：`DynamicOrmEntity` 需要**公开无参构造**（`SessionFactoryImpl.java:405` 调用
`beanModel.newInstance()`）；`kvTable="true"` 的实体要动态化必须用
`DynamicOrmKeyValueTable`，直接写 `DynamicOrmEntity` 不满足 `IOrmKeyValueTable` 约束。

## 明确不支持的映射特性（设计裁定）

以下能力在 Nop ORM 中是**设计层面明确不实现**的，不是待办项也不是缺陷。看到需求时不要按 JPA 直觉建模：

| 特性 | 裁定 | 正确做法 |
|------|------|---------|
| **对象继承映射**（JOINED / TABLE_PER_CLASS / 单表判别符） | 不支持，且不应该实现 | 拆为独立实体，用 `<to-one>` 关联；共享属性用 `<component>` 或抽为独立实体 |
| **二级表**（一个实体映射多张物理表） | 不支持 | 拆为独立实体互相 `<to-one>`；只读聚合用视图实体（`noPrimaryKey`） |
| **多对多连接表自动持久化** | 不支持 | 显式声明连接实体（带两个外键的普通实体），由平台按普通实体能力处理 |
| **Savepoint** | 不支持 | 用补偿逻辑或业务级幂等替代 |
| **ORM 层 PreparedStatement 缓冲** | 不做 | 由 DataSource / 连接池层解决 |
| **XA / 2PC** | 不支持 | 跨数据源按 best-effort 顺序提交；关键流程用 TCC / 对账 |

**为什么继承不被支持**：Nop 实体是 codegen 产物，形态在编译期完全确定，运行期不做字节码增强、不建代理。继承映射需要在运行期决定实体真实类型并延迟实例化，而 L1 实体缓存的身份键是 `(entityName, id)`——同一行数据对应多个候选实体类时键无法唯一定位已加载实例，代理替换与批量加载语义一并失效。

**为什么 m2m 必须在实体层处理**：关联表本质是"带两个外键的普通表"，在 Nop 中它就是普通实体。显式建模后字典、租户隔离、逻辑删除、审计字段、数据权限、审批流、GraphQL CRUD、前端关联渲染全部可直接复用；隐式 m2m 表则完全拿不到这些能力。

**集合变更语义**：`to-many` 集合中新增/删除元素时，持久化由**子实体自身的保存/删除**表达。父实体 flush 不产生连接表 INSERT/DELETE——集合成员的新增走 `bindOwnerProps`（按 `<join>` 声明把外键列写入子实体）后保存子实体，移除走 orphan 判定后删除子实体。因此**子实体没有反向外键属性（`refProp`）也能正常持久化**，外键由 `<join>` 的 `leftProp/rightProp` 决定。

真正会"改了集合但无 SQL"的形态只有一个：`<to-many>` 的 `<join>` 为空集（既无 `leftProp` 也无 `rightProp`），此时无从确定写入哪个外键列，框架不做任何事且不报错。这类结构必须补全 `<join>`，或建模为显式连接实体自己管理写入。

以上裁定为框架设计层面的确定性决策，变更需先修改设计决策本身，不可按 JPA 直觉直接引入。

## 相关文档

- `code-style.md` — ORM 命名规范（表名、列名、实体名格式）
- `model-first-development.md` — 模型优先开发流程、代码生成链路、VARCHAR precision 自动选择
- `../03-runbooks/create-new-entity.md` — 新建实体 runbook
- `../03-runbooks/add-field-and-validation.md` — 新增字段 runbook
- `../03-runbooks/add-dict-and-constants.md` — 新增字典 runbook
- `./api-and-graphql.md` §字典字段的自动 _label 字段 — 字典字段的 _label 在 GraphQL/REST 响应中的格式与 selection 写法
