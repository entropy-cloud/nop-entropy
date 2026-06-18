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
| 逻辑删除字段 | `delVersion`（BIGINT，默认推荐，详见 `logical-deletion.md`）；或 `deleted`（BOOLEAN，仅用于已给定数据模型不便修改的兼容场景） |
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

## 相关文档

- `code-style.md` — ORM 命名规范（表名、列名、实体名格式）
- `model-first-development.md` — 模型优先开发流程、代码生成链路、VARCHAR precision 自动选择
- `../03-runbooks/create-new-entity.md` — 新建实体 runbook
- `../03-runbooks/add-field-and-validation.md` — 新增字段 runbook
- `../03-runbooks/add-dict-and-constants.md` — 新增字典 runbook
