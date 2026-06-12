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

### domain / stdDomain / stdDataType 三层关系

1. **stdDataType**：最基础的类型系统，决定 Java 类型和 GraphQL 类型。
2. **stdDomain**：在 stdDataType 之上增加语义约束（如 `file` → 文件附件、`json` → JSON 组件）。
3. **domain**：应用模块自定义的域，可复用 stdDomain 或定义新的。domain 全局唯一，优先复用已有 domain。

控件匹配链（前端渲染）：`control` → `domain` → `stdDomain` → `stdDataType`，从 `control.xlib` 中查找匹配的渲染控件。

## 主键设计

### 规范

所有实体主键遵循以下固定配置：

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

```xml
<to-many name="items" refEntityName="io.nop.app.dao.entity.NopAppOrderItem"
         tagSet="pub,cascade-delete,insertable,updatable">
    <join on="orderId" />
</to-many>
```

- `tagSet="pub,cascade-delete,insertable,updatable"` — 标准配置
- 通过 `orm:ref-prop` 可在子表端声明主表的反向集合属性

### 关系声明规范

| 关系类型 | tagSet | 声明位置 |
|---------|--------|---------|
| to-one | `tagSet="pub"` | 主表端 |
| to-many | `tagSet="pub,cascade-delete,insertable,updatable"` | 主表端 |

## 字典设计

### dict 定义

```xml
<dicts>
    <dict name="order/status" valueType="int">
        <option code="PENDING" value="101"/>
        <option code="CANCELLED" value="102"/>
        <option code="PAID" value="201"/>
    </dict>
</dicts>
```

### 规范

1. **dict name 格式**：`{模块简称}/{kebab-case名称}`（如 `order/status`、`job/schedule-status`）。
2. **dict option code**：UPPER_SNAKE_CASE（如 `RUNNING`、`AUTO_CANCEL`）。
3. **dict option value**：整数 10/20/30 递增，统一使用 `int` 类型。
4. **字段引用**：`ext:dict="{模块简称}/{kebab-case名称}"`。
5. **禁止**：boolean 型字段不设 dict。

> dict name 斜杠后的部分**必须用 kebab-case**，不能用 snake_case。

## 通用字段（框架自动管理）

以下字段需要在 ORM 模型中声明，框架在运行时自动维护其值，业务代码不需要手工读写：

| 字段 | 说明 |
|------|------|
| 逻辑删除标记 | `delFlag`（详见 `logical-deletion.md`） |
| 逻辑删除版本 | `delVersion`（推荐必配，处理唯一键冲突，详见 `logical-deletion.md`） |
| 创建时间 | `CREATE_TIME` |
| 更新时间 | `UPDATE_TIME` |
| 创建人 | `CREATED_BY` |
| 乐观锁版本号 | 自动管理 |

## 字段设计注意事项

1. **避免使用 tinyint / short 类型，统一使用 int**。存储空间已不是瓶颈，使用 `short` 会导致 Java 常量赋值时必须 `(short)` 强制转型，增加噪音且容易出错。布尔标记字段直接用 `stdDataType="boolean"` + `stdSqlType="BOOLEAN"`（或通过 domain 使用 TINYINT 映射到 boolean）。
2. **precision 必须按实际数据大小设置**。框架会根据 dialect 和 precision 自动选择 VARCHAR → TEXT → MEDIUMTEXT → LONGTEXT。过大的 precision 会导致 MySQL 用 TEXT 而非 VARCHAR，影响索引和查询性能。详见 `model-first-development.md`。
3. **`stdDomain="json"` 或 `tagSet="json"`** 会自动生成 `JsonOrmComponent`，允许在 Java 代码中直接操作 JSON 对象。详见 `model-first-development.md`。
4. **`stdDomain="file"` / `"file-list"`** 会自动生成文件附件组件。详见 `model-first-development.md`。
5. **`notGenCode="true"`** 标记的字段不会生成 Java get/set 方法，始终作为动态属性存取。

## 相关文档

- `code-style.md` — ORM 命名规范（表名、列名、实体名格式）
- `model-first-development.md` — 模型优先开发流程、代码生成链路、VARCHAR precision 自动选择
- `../03-runbooks/create-new-entity.md` — 新建实体 runbook
- `../03-runbooks/add-field-and-validation.md` — 新增字段 runbook
- `../03-runbooks/add-dict-and-constants.md` — 新增字典 runbook
