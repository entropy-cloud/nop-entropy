# 逻辑删除

本文说明 Nop 平台的逻辑删除机制：ORM 模型如何配置、删除/查询时框架自动做什么、如何恢复已删除数据。

## 推荐方案：单个 delVersion 字段

逻辑删除只需要一个 `delVersion` (BIGINT) 字段，将 `deleteFlagProp` 和 `deleteVersionProp` 同时指向它：

- 值为 `0`：未删除
- 值为非零时间戳（`CoreMetrics.currentTimeMillis()`）：已删除

框架通过 `!= 0` 判断删除状态，一个字段同时承担"是否已删除"和"删除版本号"两个职责。

`delVersion` 解决了唯一键冲突问题：逻辑删除不物理移除行，如果表有 `UNIQUE(name)` 约束，删除 `name='A'` 后再新建 `name='A'` 会违反唯一约束。`delVersion` 每次删除写入不同的时间戳值，将唯一约束扩展为 `UNIQUE(name, delVersion)` 后即可安全地反复删除和重建。

### ORM 模型配置

```xml
<entity name="io.nop.app.entity.SomeEntity"
        tableName="some_table"
        useLogicalDelete="true"
        deleteFlagProp="delVersion"
        deleteVersionProp="delVersion">
    <columns>
        <column name="delVersion" code="DEL_VERSION" domain="delVersion"
                stdSqlType="BIGINT" stdDataType="long" mandatory="true"/>
    </columns>
</entity>
```

唯一索引应包含 `delVersion`：

```sql
UNIQUE INDEX uk_name (name, delVersion)
```

### 配置属性说明

| 属性 | 说明 |
|------|------|
| `useLogicalDelete` | 设为 `"true"` 启用逻辑删除。`dao().deleteEntity()` 自动转为 UPDATE |
| `deleteFlagProp` | 指向用于判断删除状态的列。框架通过 `!= 0` 判断已删除 |
| `deleteVersionProp` | 指向删除版本列。删除时写入 `CoreMetrics.currentTimeMillis()`。**与 `deleteFlagProp` 指向同一字段** |

### 内置 domain

`default.orm.xml` 提供内置 domain：

```xml
<domain name="delVersion" stdSqlType="BIGINT"/>
```

列声明 `domain="delVersion"` 后，ORM 导入器自动设置 `deleteVersionProp` 和 `useLogicalDelete=true`。应用层仍需显式设置 `deleteFlagProp="delVersion"` 将两者指向同一字段。

## 框架自动行为

配置 `useLogicalDelete="true"` 后，以下行为由框架自动完成，业务代码不需要手工设置这些字段的值。

### 1. 保存时初始化

`LogicalDeleteHelper.onSave()` 在新建实体时自动将 `delVersion` 设为 `0`。

源码锚点：`LogicalDeleteHelper.java:28`

### 2. 删除时转为 UPDATE

`EntityPersisterImpl.delete()` 检测到 `useLogicalDelete` 后，不执行 SQL DELETE，而是调用 `LogicalDeleteHelper.onDelete()`，直接设 `delVersion = CoreMetrics.currentTimeMillis()`，然后转为 `UPDATE` 语句。

源码锚点：`EntityPersisterImpl.java:316`、`LogicalDeleteHelper.java:50`

### 3. 查询时自动过滤

框架在以下场景自动追加 `delVersion = 0` 条件：

- **集合加载 SQL**（一对多、多对多关联查询）：`GenSqlHelper.genCollectionFilterEx()` 自动追加。源码锚点：`GenSqlHelper.java:467`
- **Example 查询**（`findAllByExample`、`findPageByExample` 等）：`addDeleteFlagToExample()` 自动设置。源码锚点：`EntityPersisterImpl.java:754`

> 自动过滤在 ORM 引擎层执行，对业务代码透明。

### 4. Entity 状态判断

`IOrmEntity.orm_logicalDeleted()` 返回 `true` 表示该实体已被逻辑删除。内部通过 `deleteFlagProp` 指向的字段值判断，非零即已删除。

源码锚点：`OrmEntity.java:755`

## CrudBizModel 内置操作

`CrudBizModel<T>` 提供了一组管理逻辑删除数据的内置方法。这些方法受 `biz:allowGetDeleted` 开关控制。

### 开关配置

在 xmeta 中设置 `biz:allowGetDeleted="true"` 开放已删除数据的管理能力：

```xml
<meta x:schema="/nop/schema/xmeta.xdef" xmlns:biz="biz" biz:allowGetDeleted="true">
    ...
</meta>
```

`isAllowGetDeleted()` 读取此属性，默认为 `false`。未启用时调用 `deleted_get` / `deleted_findPage` / `recoverDeleted` 会抛出 `ERR_BIZ_NOT_ALLOW_GET_DELETED` 异常。

源码锚点：`CrudBizModel.java:1004`

### 内置方法

| 方法 | 类型 | 说明 |
|------|------|------|
| `deleted_get(id, ignoreUnknown)` | `@BizQuery` | 获取已逻辑删除的单条记录。内部设置 `includeLogicalDeleted=true` |
| `deleted_findPage(query)` | `@BizQuery` | 分页查询已逻辑删除记录。内部设 `query.disableLogicalDelete=true` 并追加 `deleteFlagProp > 0` 过滤 |
| `recoverDeleted(id)` | `@BizMutation` | 恢复已删除记录。设 `deleteFlagProp=0`、`deleteVersionProp=0`，然后 `updateEntity` |

源码锚点：`CrudBizModel.java:989`、`CrudBizModel.java:1376`、`CrudBizModel.java:1398`

### 普通方法中的逻辑删除防护

- `get()` / `requireEntity()` 默认过滤已删除记录。如果实体已被逻辑删除，抛出 `UnknownEntityException(deleted=true)`。
- `batchGet()` 同样过滤已删除记录。
- `findPage()` / `findList()` 通过 ORM 引擎自动过滤，业务代码无需关心。

## 绕过逻辑删除过滤

以下场景可能需要绕过自动过滤：

| 场景 | 方法 |
|------|------|
| QueryBean 查询需要包含已删除数据 | `query.setDisableLogicalDelete(true)` |
| 单个实体临时绕过 | `entity.orm_disableLogicalDelete(true)` |
| SQL 查询中显式控制 | `compileSql(..., disableLogicalDelete=true)` |
| `@SqlLibMapper` SQL 中 | `<sql>` 的 `disableLogicalDelete="true"` 属性 |

> 绕过逻辑删除过滤属于底层操作。在 BizModel 中优先使用 `deleted_get` / `deleted_findPage` 等内置方法，而非直接绕过。

## findLogicalDeleted 机制

`CrudBizModel.findLogicalDeleted()` 用于保存场景：如果数据库中已存在一条逻辑删除的记录且主键相同，会返回该记录供复用（覆盖），而不是抛出唯一键冲突。

源码锚点：`CrudBizModel.java:702`

## 删除前后的扩展点

通过 override CrudBizModel 的 hook 方法控制删除行为：

| 扩展点 | 适用场景 |
|--------|---------|
| `defaultPrepareDelete(entity, context)` | 删除前做引用检查之外的补充校验 |
| `afterEntityChange(...)` | 保存、更新、删除后追加统一后处理 |

详见 `03-runbooks/extend-crud-with-hooks.md`。

## 与 I*Biz 管道的关系

`I*Biz.get()` / `I*Biz.requireEntity()` 走完整管道，包含逻辑删除检查。`daoProvider().daoFor()` 绕过全部管道（包括逻辑删除）。在 BizModel 中默认永远使用 `I*Biz`。

详见 `04-reference/safe-api-reference.md`。

## 常见错误

| 错误 | 正确做法 |
|------|---------|
| 有业务唯一键但未配 `delVersion`，删除后重建同名对象报唯一约束冲突 | 配置 `deleteVersionProp` 并将唯一索引调整为包含 `delVersion` |
| 手动 `dao().deleteEntity()` 后期望物理删除但实际是 UPDATE | 检查实体是否启用了 `useLogicalDelete`。需要物理删除时用 `entity.orm_disableLogicalDelete(true)` |
| 在 BizModel 中用 `daoProvider().daoFor()` 绕过管道查询，漏掉了逻辑删除过滤 | 使用 `I*Biz` 接口，或明确知道需要绕过时加注释说明 |
| `deleted_findPage` 返回 403 或异常 | 检查 xmeta 是否设置了 `biz:allowGetDeleted="true"` |
| 删除标记字段用 `boolean` / `stdSqlType="BOOLEAN"` | 用 `BIGINT`，框架通过 `!= 0` 判断，BOOLEAN 类型在某些数据库不可靠 |

## 相关文档

- `orm-model-design.md` — ORM 模型设计规范（字段类型、主键策略、关系设计）
- `service-layer.md` — 服务层与 BizModel 默认模式
- `safe-api-reference.md` — CrudBizModel 安全 API（I\*Biz 管道说明）
- `extend-crud-with-hooks.md` — 扩展 CRUD 钩子的 runbook
- `code-style.md` — ORM 命名规范
