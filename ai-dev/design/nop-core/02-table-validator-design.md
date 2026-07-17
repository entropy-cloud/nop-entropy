# Table Validator 设计

> Status: draft
> Scope: nop-xdefs, nop-core, nop-utils/nop-table-validator, nop-format/nop-tablesaw

## 设计原则

1. **三层验证**：row-level / column-stat-level / table-level，scope 不同但错误模型统一
2. **泛型接口 + Adaptor 模式**：`ITableValidator<T>` 泛型接口，`ITableDataAdaptor<T>` 桥接任意行类型
3. **参考 ValidatorModel 编译模式**：`TableValidatorModel` + `ITableDataAdaptor<T>` → `ModelBasedTableValidator<T>`
4. **复用 filter-bean**：所有层的条件表达式复用现有的 filter-bean 机制；stat/table shortcut 直接映射到 filter-bean 元素
5. **兼容 validator.xdef**：row-level 直接嵌入标准 `validator.xdef`
6. **错误模型一致**：复用 validator.xdef 的 errorCode/severity/errorDescription/errorParams 体系

## 三层验证

| 层级 | 验证时机 | 验证对象 | Scope 变量 |
|------|---------|---------|-----------|
| row | 每行输入时 | 当前行的各列值 | 列名直接绑定列值 |
| column-stat | 所有行输入完成后 | 列的统计量 | `stat`(StatResult), `_table`(TableMeta) |
| table | 所有行输入完成后 | 表整体属性 | `_table`(TableMeta) |

## 模块划分

| 部分 | 模块 | 内容 |
|------|------|------|
| xdef 定义 | `nop-xdefs` | `table-validator.xdef` |
| 模型 bean | `nop-core` | `TableValidatorModel` / `TableStatCheckModel` / `TableGlobalCheckModel` / `TableColumnMeta` |
| register-model | `nop-core` | `table-validator.register-model.xml` |
| 接口 + 核心运行时 | `nop-utils/nop-table-validator` | `ITableValidator<T>` / `ITableDataAdaptor<T>` / `ModelBasedTableValidator<T>` / `StatResult` / `TableMeta` |
| Tablesaw 适配器 | `nop-format/nop-tablesaw` | `TablesawTableDataAdaptor` |

`nop-table-validator` 依赖 `nop-core`（无其他外部依赖）。`nop-tablesaw` 依赖 `nop-table-validator`。

## xdef

```xml
<table-validator x:schema="/nop/schema/xdef.xdef"
                 xmlns:x="/nop/schema/xdsl.xdef"
                 xmlns:xdef="/nop/schema/xdef.xdef"
                 xdef:name="TableValidatorModel"
                 xdef:bean-package="io.nop.core.model.table.validator">

    <description>string</description>

    <!-- 列元数据（可选） -->
    <columns xdef:body-type="list" xdef:key-attr="name">
        <column name="!string" displayName="string" type="generic-type"
                domain="string" mandatory="!boolean=false"
                xdef:name="TableColumnMeta">
            <schema xdef:ref="/nop/schema/schema/schema.xdef"/>
        </column>
    </columns>

    <!-- 逐行验证 -->
    <rowValidators xdef:body-type="list" xdef:key-attr="id">
        <validator id="!string" xdef:ref="/nop/schema/validator.xdef"/>
    </rowValidators>

    <!-- 列统计验证 -->
    <statChecks xdef:body-type="list" xdef:key-attr="id">
        <check id="!string" column="!string" errorCode="!string"
               severity="!int=0" errorDescription="string"
               xdef:name="TableStatCheckModel">
            <statExpr>xpl-fn:(stat,table)=>any</statExpr>
            <between min="double" max="double"/>
            <ge value="double"/>
            <le value="double"/>
            <gt value="double"/>
            <lt value="double"/>
            <condition>filter-bean</condition>
            <errorParams xdef:body-type="map">
                <param name="!string" value="string"/>
            </errorParams>
        </check>
    </statChecks>

    <!-- 全表验证 -->
    <tableChecks xdef:body-type="list" xdef:key-attr="id">
        <check id="!string" errorCode="!string" severity="!int=0"
               errorDescription="string"
               xdef:name="TableGlobalCheckModel">
            <rowCount min="int" max="int"/>
            <columnCount min="int" max="int"/>
            <condition>filter-bean</condition>
            <errorParams xdef:body-type="map">
                <param name="!string" value="string"/>
            </errorParams>
        </check>
    </tableChecks>

    <beforeValidate>xpl</beforeValidate>
    <afterValidate>xpl</afterValidate>
</table-validator>
```

## Java API

### 接口定义（nop-utils/nop-table-validator, 包 `io.nop.table.validator`）

```java
public interface ITableValidator<T> {
    List<ErrorBean> validate(T data);
}

public interface ITableDataAdaptor<T> {
    int getRowCount(T data);
    List<String> getColumnNames(T data);
    Object getCellValue(T data, int rowIndex, String columnName);
}
```

### 编译方式

参考 `ValidatorModel` → `ModelBasedValidator` 的模式：

```java
TableValidatorModel model = (TableValidatorModel) ResourceComponentManager
    .loadComponentModel("/path/to/validator.table-validator.xml");

ITableDataAdaptor<MyRowType> adaptor = new MyRowTypeAdaptor(...);
ITableValidator<MyRowType> validator = new ModelBasedTableValidator<>(model, adaptor);

List<ErrorBean> errors = validator.validate(myData);
```

### 内部执行流程

`ModelBasedTableValidator.validate(T data)` 内部执行：

```
1. 从 adaptor 获取 columnNames 和 rowCount
2. 初始化 per-column 统计累加器
3. 遍历行:
   beginRow → adaptor.getCellValue(data, rowIndex, colName) → setValue → endRow
   endRow 时:
     - 构建 rowScope → 执行 rowValidators (ModelBasedValidator)
     - 更新统计累加器
4. 计算最终统计量 → StatResult[]
5. 执行 statChecks (shortcut→filter-bean / condition)
6. 执行 tableChecks (shortcut→filter-bean / condition)
7. 返回 List<ErrorBean>
```

### Tablesaw 适配器（nop-tablesaw, 包 `io.nop.tablesaw.dataflow.validation`）

```java
public class TablesawTableDataAdaptor implements ITableDataAdaptor<Table> {
    @Override
    public int getRowCount(Table table) { return table.rowCount(); }

    @Override
    public List<String> getColumnNames(Table table) {
        return table.columns().stream().map(Column::name).toList();
    }

    @Override
    public Object getCellValue(Table table, int rowIndex, String columnName) {
        return table.row(rowIndex).getObject(columnName);
    }
}

// 使用:
ITableValidator<Table> validator = new ModelBasedTableValidator<>(model, new TablesawTableDataAdaptor());
List<ErrorBean> errors = validator.validate(table);
```

### Push-based 引擎

`ModelBasedTableValidator.validate()` 内部使用 push-based 引擎。该引擎也可单独使用：

```java
TableValidatorEngine engine = new TableValidatorEngine(model);
engine.beginTable(columnsMeta);

for each row:
    engine.beginRow();
    engine.setValue(col, val);
    engine.endRow();

List<ErrorBean> errors = engine.endTable();
```

`TableValidatorEngine` 是 `nop-table-validator` 的内部实现类，包可见或非公开。

## Scope 变量

### Row-level scope

每行的列值构建 `Map<String, Object>`，包装为 `BeanVariableScope`。filter-bean 中直接按列名引用。

### Stat-level scope

| 变量 | 类型 | 属性 |
|------|------|------|
| `stat` | `StatResult` | `type, size, missing, distinctCount, min, max, mean, stdDev`; `value` = 默认主值 |
| `_table` | `TableMeta` | `rowCount, columnCount` |

### Table-level scope

| 变量 | 类型 | 属性 |
|------|------|------|
| `_table` | `TableMeta` | `rowCount, columnCount` |

## Shortcut → Filter-bean 映射

Shortcut 在编译期展开为 filter-bean。展开时自动添加 `name="stat.value"`（stat-level）或 `name="_table.rowCount"`（table-level）。

| Shortcut | 展开为 filter-bean |
|----------|--------------------|
| `<between min="0" max="150"/>` | `<between name="stat.value" min="0" max="150"/>` |
| `<ge value="0"/>` | `<ge name="stat.value" value="0"/>` |
| `<le value="150"/>` | `<le name="stat.value" value="150"/>` |
| `<gt value="0"/>` | `<gt name="stat.value" value="0"/>` |
| `<lt value="150"/>` | `<lt name="stat.value" value="150"/>` |
| `<rowCount min="1"/>` | `<ge name="_table.rowCount" value="1"/>` |
| `<rowCount max="1000"/>` | `<le name="_table.rowCount" value="1000"/>` |
| `<rowCount min="1" max="1000"/>` | `<and><ge name="_table.rowCount" value="1"/><le name="_table.rowCount" value="1000"/></and>` |
| `<columnCount min="1"/>` | `<ge name="_table.columnCount" value="1"/>` |

## StatResult

| 属性 | 类型 | 计算方式 |
|------|------|---------|
| `columnName` | String | 列名 |
| `type` | String | 由 column meta 或值推断 |
| `size` | int | rowCount |
| `missing` | int | null 值计数 |
| `distinctCount` | int | distinct 值集合大小 |
| `min` | Double | 数值列最小值（非数值列或空列=null） |
| `max` | Double | 数值列最大值（非数值列或空列=null） |
| `mean` | Double | 数值列平均值（非数值列或空列=null） |
| `stdDev` | Double | 数值列标准差（非数值列或空列=null）；`sqrt(sumSq/count - mean²)` |
| `value` | Object | 默认主值：数值列=mean（空列=null），非数值列=size |

## TableMeta

| 属性 | 类型 |
|------|------|
| `rowCount` | int |
| `columnCount` | int |

## 错误输出

```java
// row-level
error.param("rowIndex", rowIndex);

// stat-level
error.param("column", columnName);
error.param("statValue", statValue);

// table-level
error.param("rowCount", rowCount);
```

## Register Model

```xml
<model x:schema="/nop/schema/register-model.xdef" xmlns:x="/nop/schema/xdsl.xdef"
       name="table-validator">
    <loaders>
        <xdsl-loader fileType="table-validator.xml" schemaPath="/nop/schema/table-validator.xdef"/>
    </loaders>
</model>
```

路径：`nop-kernel/nop-core/src/main/resources/_vfs/nop/core/registry/table-validator.register-model.xml`

## 累加器

在 `TableValidatorEngine` 内跟踪 per-column `count/nullCount/sum/sumOfSquares/min/max/distinctSet`。

空列保护：`count=0` 时 `mean/stdDev/min/max = null`，filter-bean 遇到 null 值条件评估为 `INVALID`（不触发错误，不误报通过）。

行号注入：使用包装 `IValidationErrorCollector`，在 `endRow()` 时拦截 `addError` 并注入 `param("rowIndex", rowIndex)`。

## 落地顺序

1. `nop-xdefs`: `table-validator.xdef` 定义
2. `nop-core`: bean 模型生成（`mvn compile`）
3. `nop-core`: register-model.xml
4. `nop-utils/nop-table-validator`: 模块 pom.xml
5. `nop-utils/nop-table-validator`: `ITableValidator<T>` / `ITableDataAdaptor<T>` / `StatResult` / `TableMeta`
6. `nop-utils/nop-table-validator`: `TableValidatorEngine`（push-based 内部引擎）
7. `nop-utils/nop-table-validator`: `ModelBasedTableValidator<T>`（编译验证器）
8. `nop-utils/nop-table-validator`: 单元测试
9. `nop-tablesaw`: `TablesawTableDataAdaptor` + pom.xml 新增依赖
10. `nop-tablesaw`: 集成测试

## 与现有基础设施的关系

| 基础设施 | 关系 |
|---------|------|
| `validator.xdef` | row-level 直接 xdef:ref 复用 |
| `ModelBasedValidator` | row-level 验证委托 |
| `filter-bean` (`filter.xdef`) | 所有层的条件表达式机制；shortcut 映射到 `ge/le/gt/lt/between` |
| `ValidatorModel` → `ModelBasedValidator` | 编译模式参考 |
| `ITableDataAdaptor<T>` | 桥接任意行类型 |
| `DataSetToTableTransformer` | Tablesaw 适配器可复用 |
| `SchemaBasedValidator` | column schema 级验证（通过 `<schema>` 引用） |
