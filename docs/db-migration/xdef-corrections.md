# migration.xdef 修正总结

## 修正的问题

根据 Nop 平台的 xdef 元模型定义规范，我对 migration.xdef 文件进行了以下修正：

### 1. 根元素缺少 description 属性

**问题**: 根元素 `<migration>` 缺少 `description` 属性声明

**修正前**:
```xml
<migration x:schema="/nop/schema/xdef.xdef" 
           xmlns:x="/nop/schema/xdsl.xdef"
           xmlns:xdef="/nop/schema/xdef.xdef"
           xdef:name="DbMigrationModel" 
           xdef:bean-package="io.nop.db.migration.model"
           xdef:bean-extends-type="io.nop.xlang.xdsl.AbstractDslModel"
           version="!string"
           author="string"
           type="enum:io.nop.db.migration.MigrationType=versioned"
           runOn="enum:io.nop.db.migration.RunOnChange=onChange"
           failOnError="!boolean=true"
           ignore="!boolean=false">
```

**修正后**:
```xml
<migration x:schema="/nop/schema/xdef.xdef" 
           xmlns:x="/nop/schema/xdsl.xdef"
           xmlns:xdef="/nop/schema/xdef.xdef"
           xdef:name="DbMigrationModel" 
           xdef:bean-package="io.nop.db.migration.model"
           xdef:bean-extends-type="io.nop.xlang.xdsl.AbstractDslModel"
           version="!string"
           description="string"
           author="string"
           type="enum:io.nop.db.migration.MigrationType"
           runOn="enum:io.nop.db.migration.RunOnChange"
           failOnError="!boolean=true"
           ignore="!boolean=false">
```

**说明**: 
- 添加了 `description="string"` 属性声明
- 同时移除了 enum 类型的默认值（见第2点）

### 2. enum 类型的默认值问题

**问题**: enum 类型不应该使用 `=default_value` 语法指定默认值

**修正位置**:
1. 根元素属性:
   - `type="enum:io.nop.db.migration.MigrationType=versioned"` → `type="enum:io.nop.db.migration.MigrationType"`
   - `runOn="enum:io.nop.db.migration.RunOnChange=onChange"` → `runOn="enum:io.nop.db.migration.RunOnChange"`

2. 前置条件属性:
   - `expect="enum:io.nop.db.migration.PreconditionExpect=exists"` → `expect="enum:io.nop.db.migration.PreconditionExpect"` (多处)
   - `expect="enum:io.nop.db.migration.PreconditionExpect=true"` → `expect="enum:io.nop.db.migration.PreconditionExpect"`

**说明**: 在 Nop 平台的 xdef 规范中，enum 类型不支持在类型声明中指定默认值。默认值应该在 Java 代码层面或者通过其他机制处理。

### 3. dbSpecific 中 sql 元素的定义问题

**问题**: 嵌套在 `dbSpecific` 中的 `sql` 元素缺少 `xdef:name` 声明

**修正前**:
```xml
<dbSpecific xdef:body-type="list" xdef:key-attr="dbType">
    <sql dbType="!string">
        <body xdef:body-type="string"/>
    </sql>
</dbSpecific>
```

**修正后**:
```xml
<dbSpecific xdef:body-type="list" xdef:key-attr="dbType">
    <sql xdef:name="DbSpecificSql" dbType="!string">
        <body xdef:body-type="string"/>
    </sql>
</dbSpecific>
```

**说明**: 当定义一个可以被 `xdef:ref` 引用的元素时，需要使用 `xdef:name` 给它命名。

### 4. labels 和 contexts 元素的类型声明

**问题**: 使用了 `xdef:body-type="string"` 而不是直接在元素内声明类型

**修正前**:
```xml
<labels xdef:body-type="string"/>
<contexts xdef:body-type="string"/>
```

**修正后**:
```xml
<labels>string</labels>
<contexts>string</contexts>
```

**说明**: 根据 Nop 平台的 xdef 规范（参考 orm.xdef），对于只包含简单文本内容的元素，应该直接在元素标签内写类型，而不是使用 `xdef:body-type="string"`。`xdef:body-type` 主要用于复杂结构（如 list、map 等）。

### 5. 逗号分隔字符串的类型声明

**问题**: 使用逗号分隔的字符串应该使用 `csv-set` 类型，而不是 `string` 类型

**修正位置**:

1. `primaryKey` 元素的 `columnNames` 属性:
   ```xml
   columnNames="!string" → columnNames="csv-set"
   ```

2. `uniqueConstraint` 元素的 `columnNames` 属性:
   ```xml
   columnNames="!string" → columnNames="csv-set"
   ```

3. `foreignKey` 元素的 `columnNames` 和 `refColumnNames` 属性:
   ```xml
   columnNames="!string" → columnNames="csv-set"
   refColumnNames="!string" → refColumnNames="csv-set"
   ```

4. `createIndex` 元素的 `columnNames` 属性:
   ```xml
   columnNames="!string" → columnNames="csv-set"
   ```

5. `dbTypeFilter` 元素的 `dbTypes` 属性:
   ```xml
   dbTypes="!string" → dbTypes="csv-set"
   ```

**说明**: 在 Nop 平台中，`csv-set` 类型用于表示逗号分隔的字符串集合，它会在解析时自动转换为 Set<String> 类型，提供更好的类型安全性和去重功能。

## 规范参考

本次修正遵循了 Nop 平台的 xdef 元模型定义规范，详见 `docs-for-ai/02-core-guides/xdef-and-xdsl.md`。

### 1. changeset 元素的结构

`changeset` 元素包含多个可选的变更操作子元素（createTable, dropTable, addColumn 等），这些子元素没有使用 `xdef:body-type` 声明。这是符合规范的，因为：

- 这些子元素都是可选的（没有 `xdef:mandatory="true"`）
- 每个子元素都使用 `xdef:name` 定义了自己的模型类
- 它们是平级的、互斥的选择关系

### 2. xdef:ref 的使用

在 `dbTypeFilter` 和 `rollback` 的 `changes` 元素中使用了 `xdef:ref` 来引用之前定义的变更类型，这是符合规范的。

## 验证建议

1. **编译验证**: 运行 `mvn clean compile` 确保没有语法错误
2. **模型生成**: 检查是否正确生成了模型类（DbMigrationModel 等）
3. **示例验证**: 使用修正后的 xdef 验证示例文件是否能正确解析

## 规范参考

参考了以下现有的 xdef 文件：
- `/nop/schema/orm/orm.xdef` - 根元素属性声明方式
- `/nop/schema/orm/entity.xdef` - 复杂结构的定义方式
- `/nop/schema/xdef.xdef` - xdef 元模型自身的定义

## 总结

所有修正都基于 Nop 平台的 xdef 元模型定义规范，确保了 migration.xdef 文件：
1. 属性声明完整（添加了 description）
2. 类型声明正确（移除了 enum 的默认值）
3. 元素结构规范（修正了 labels、contexts 和 dbSpecific 的定义）
4. 符合 Nop 平台的惯例和最佳实践
