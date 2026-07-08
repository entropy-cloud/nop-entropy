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
| BINARY / VARBINARY / BLOB | bytes | ByteString |
| GEOMETRY | geometry | GeometryObject |

> 完整列表见 `StdSqlType.java` 和 `StdDataType.java`。

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
- ORM 初始化时，`OrmModelInitializer.syncDomains()` 会将 domain 的 `stdDomain`/`stdSqlType`/`stdDataType`/`precision`/`scale` 复制到 column 上
- 如果 column 也设置了这些属性，以 domain 的设置为准
- 如果 domain 未找到，抛出 `ERR_ORM_MODEL_INVALID_COLUMN_DOMAIN` 异常

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
        propId="1" stdDataType="long" stdSqlType="BIGINT"
        tagSet="seq-default" ui:show="X"/>
```

关键要素：
- `stdSqlType="BIGINT"`
- `stdDataType="long"`
- `tagSet="seq-default"` — 使用数据库自增序列

### 选择建议

| 场景 | 推荐方案 | 理由 |
|------|----------|------|
| 分布式部署、多节点 | VARCHAR(36) + `tagSet="seq"` | UUID 全局唯一，无冲突 |
| 单机部署、简单应用 | BIGINT + `tagSet="seq-default"` | 性能更好，存储更省 |
| 需要跨系统合并数据 | VARCHAR(36) + `tagSet="seq"` | 字符串 ID 便于迁移 |
| 已有数据库表结构 | 按现有结构选择 | 兼容性优先 |

### `id` 属性名保留规则

**重要**：在 Nop 平台中，`id` 是系统保留的属性名，专用于主键。

**单主键实体**：
- 系统自动为每个实体创建一个 `id` 属性，指向主键列
- 如果数据库主键列名是 `id`，必须设置 `ext:allowIdAsColName="true"`，否则会被自动重命名为 `id_`

**复合主键实体**：
- 各主键列保持各自原始的属性名（如 `partitionId`、`sid`）
- 系统创建 `OrmCompositePKModel` 对象作为 `id` 属性
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

### VARCHAR 长度：是字节数不是字符数

**重要**：VARCHAR 的 `precision` 是**字节数**，不是字符数。

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

**MySQL 类型自动转换**：
- precision ≤ 255：VARCHAR
- 255 < precision ≤ 65535：TEXT
- precision > 65535：MEDIUMTEXT / LONGTEXT

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
<column name="employeeId" stdDataType="long" stdSqlType="BIGINT"/>
<to-one name="employee" refEntityName="app.erp.md.dao.entity.ErpMdEmployee">
    <join><on leftProp="employeeId" rightProp="id"/></join>
</to-one>
```

- 员工主键是 `id`（BIGINT），不是 String UUID
- **不要加 `stdDomain="userId"`**
- 需要 `<to-one>` 关联到 `ErpMdEmployee`（主数据，本模块或跨模块均可）
- **操作人字段（pickerId/handlerId/operatorId/…）不要用员工引用**——见上节"操作人/责任人/归属列"

> **前置分派的边界场景**：若业务确需"把工作分派给某个尚未开通账号的 HR 人员"，该分派字段可建员工引用——但这是"分派目标"，与"谁实际执行"是两个字段，后者仍为 `userId`。若选择员工引用，ErpMdEmployee 必须携带 `userId` 链接，否则无法从登录会话解析到员工。

### 总结对照

| 场景 | 类型 | 域 | 关联 |
|------|------|-----|------|
| 操作人/责任人/归属（ownerId, approverId, operatorId, assignedToId, teamLeaderId, pickerId, handlerId, requesterId, managerId, approvedBy, postedBy, closedBy） | `VARCHAR(36)` | `stdDomain="userId"` | 无 to-one |
| 被动审计（createdBy, updatedBy） | `VARCHAR(50)` | `domain="createdBy"`（惯例） | 无 to-one |
| HR 主体实体员工引用（合同/薪资/考勤/绩效上的 employeeId） | `BIGINT` | 无 | `<to-one>` → ErpMdEmployee |
| 一般业务 FK | `BIGINT` | 无 | `<to-one>` → 业务实体 |

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

### `ref-*` 属性：AI 时代不需要

`refPropName`、`refDisplayName`、`ref-i18n-en:displayName` 都是平台在**只有一端关系声明时**自动推定反向关联的辅助属性：

| 属性 | 旧用途（仅写一端） | AI 维护（两端都写） |
|------|-------------------|-------------------|
| `refPropName` | 指定反向集合属性名（如 `<to-one refPropName="children">`） | 不需要 — AI 直接声明 `<to-many name="children">` |
| `refDisplayName` | 为自动生成的 `<to-many>` 提供中文显示名 | 不需要 — 已在 `<to-many displayName="...">` 上 |
| `ref-i18n-en:displayName` | 同上，英文版 | 不需要 — 已在 `<to-many i18n-en:displayName="...">` 上 |

**不要在 `refPropName` 上浪费时间。** `OrmModelInitializer` 会在启动时自动匹配：找到被引用实体上 `leftProp`/`rightProp` 对应的 `<to-one>`，取其 `name` 作为反向属性名。显式写 `refPropName` 只是重复了运行时已能自动推导的信息。

## 相关文档

- `code-style.md` — ORM 命名规范（表名、列名、实体名格式）
- `model-first-development.md` — 模型优先开发流程、代码生成链路、VARCHAR precision 自动选择
- `../03-runbooks/create-new-entity.md` — 新建实体 runbook
- `../03-runbooks/add-field-and-validation.md` — 新增字段 runbook
- `../03-runbooks/add-dict-and-constants.md` — 新增字典 runbook
- `./api-and-graphql.md` §字典字段的自动 _label 字段 — 字典字段的 _label 在 GraphQL/REST 响应中的格式与 selection 写法
