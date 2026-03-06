# Record Mapping 配置指南

## 核心概念

Record Mapping 是 Nop 平台的对象映射框架，用于定义不同数据结构之间的映射关系。详细配置结构请参考：

- **Schema 文件**: `/nop/schema/record/record-mapping.xdef`
- **模型类**: `io.nop.record_mapping.model.RecordMappingConfig`

## 映射执行流程

```
1. 遍历 <fields> 中的精确映射规则
2. 遍历 <patternFields> 中的模式匹配规则（处理剩余未映射字段）
3. 对每个字段：获取源值 → 条件检查 → 值转换 → 设置目标值
```

**优先级**: `<fields>` 精确映射 > `<patternFields>` 模式映射（按定义顺序，先定义先匹配）

## 映射名称解析

### 映射名称与文件路径的对应关系

在代码中获取映射规则时，映射名称会被转换为文件路径：

- **映射名称格式**: `<包路径>.<映射名>`
  - 示例: `test.demo.Type1_to_Type2` 实际路径 `_vfs/nop/record/mapping/test/demo.record-mappings.xml`

```java
   IRecordMapping mapping = manager.getRecordMapping("test.demo.Type1_to_Type2");
```

**XML 文件中的映射名**：保持简单名称，无需添加包路径前缀
```xml
<mapping name="Type1_to_Type2">...</mapping>
```

## 易错点

### 1. XPL 函数格式

**问题**: `computeExpr`、`valueExpr`、`when` 等必须使用 `xpl-fn` 格式

```xml
<computeExpr><![CDATA[ source.amount < 1000]]></computeExpr>
```

**关键点**:
- 表达式如果包含xml中的特殊字符，则需要用 `CDATA` 包裹
- `xpl-fn:(source,target,ctx)=>any`表示代码中可以直接使用source/target/ctx三个参数，返回类型为any


## 常见场景示例

### 场景1: 简单字段映射

```xml
<mapping name="SimpleMapping">
    <fields>
        <field name="id" from="ID"/>
        <field name="name" from="NAME" mandatory="true"/>
        <field name="status" from="STATUS" defaultValue="ACTIVE"/>
    </fields>
</mapping>
```

### 场景2: 前缀转嵌套对象

```xml
<mapping name="FlatToNested">
    <patternFields>
        <patternField id="user_props" fromPattern="user_{prop}" to="user.${prop}"/>
        <patternField id="addr_props" fromPattern="addr_{prop}" to="address.${prop}"/>
    </patternFields>
</mapping>
```

**输入**: `{"user_name": "John", "user_age": 20, "addr_city": "Shanghai"}`

**输出**: `{"user": {"name": "John", "age": 20}, "address": {"city": "Shanghai"}}`

### 场景3: 条件过滤

```xml
<mapping name="FilterMapping">
    <fields>
        <field name="email">
            <when>source.emailValid</when>
        </field>
    </fields>

    <patternFields>
        <!-- 忽略 internal_ 开头的字段 -->
        <patternField id="ignore_internal" fromPattern="internal_{rest}" ignore="true"/>
    </patternFields>
</mapping>
```

### 场景4: 嵌套对象和列表映射

```xml
<definitions>
    <!-- 地址映射 -->
    <mapping name="AddressMapping">
        <fields>
            <field name="city" from="CITY"/>
            <field name="street" from="STREET"/>
        </fields>
    </mapping>

    <!-- 订单项映射 -->
    <mapping name="OrderItemMapping">
        <fields>
            <field name="productId" from="PRODUCT_ID"/>
            <field name="quantity" from="QTY" type="Integer"/>
            <field name="price" from="PRICE" type="BigDecimal"/>
        </fields>
    </mapping>

    <!-- 订单映射 -->
    <mapping name="OrderMapping">
        <fields>
            <field name="id" from="ORDER_ID" mandatory="true"/>
            <field name="address" from="SHIP_ADDR" mapping="AddressMapping"/>
            <field name="items" from="ITEMS" itemMapping="OrderItemMapping" type="List&lt;OrderItem>"/>
        </fields>
    </mapping>
</definitions>
```

### 场景5: 使用 computeExpr 计算衍生值

`computeExpr` 有两种使用方式：

#### 5.1 在 `<field>` 中使用

直接计算字段值，不从 source 获取：

```xml
<mapping name="WithComputeExpr">
    <fields>
        <field name="amount" from="AMOUNT"/>
        <field name="price" from="PRICE"/>

        <!-- 使用 computeExpr 计算总价 -->
        <field name="totalPrice">
            <computeExpr>source.amount * source.price</computeExpr>
        </field>
    </fields>
</mapping>
```

### 场景6: 值转换

使用 `valueExpr` 对字段值进行转换：

```xml
<mapping name="ValueTransform">
    <fields>
        <field name="amount" from="AMOUNT">
            <valueExpr>value*100</valueExpr>
        </field>
        <field name="status" from="STATUS">
            <valueExpr>value.toUpperCase()</valueExpr>
        </field>
    </fields>
</mapping>
```

### 场景7: 映射前后钩子

在映射开始和结束时执行自定义逻辑：

```xml
<mapping name="WithHooks">
    <beforeMapping><![CDATA[
        target.createdBy = 'system';
        target.createdTime = now();
    ]]></beforeMapping>

    <fields>
        <field name="id" from="ID"/>
        <field name="name" from="NAME"/>
    </fields>

    <afterMapping><![CDATA[
        target.version = 1;
    ]]></afterMapping>
</mapping>
```

### 场景8: 集合元素过滤

使用 `itemFilterExpr` 过滤列表中的元素：

```xml
<mapping name="FilterItems">
    <fields>
        <field name="activeItems" from="items" itemMapping="ItemMapping">
            <itemFilterExpr>item.status == 'ACTIVE'</itemFilterExpr>
        </field>
    </fields>
</mapping>
```

### 场景9: 字段间依赖（varName）

使用 `varName` 将中间结果存储到上下文，供后续字段使用，**但不会设置到目标对象上**：

```xml

<mapping name="FieldDependency">
  <fields>
    <!-- 存储 basePrice 到上下文，不设置到 target -->
    <field name="tempBasePrice" from="price" varName="basePrice"/>

    <!-- 从上下文读取 basePrice，计算 discount -->
    <field name="discount">
      <computeExpr>basePrice * 0.9</computeExpr>
    </field>

    <!-- 从上下文读取 basePrice 和 target，计算 finalPrice -->
    <field name="finalPrice">
      <computeExpr>basePrice - target.discount</computeExpr>
    </field>
</mapping>
```

**关键点**：
1. 设置 `varName` 后，值只存储到上下文中，**不会设置到 target 对象**
2. 后续表达式中可以直接使用变量名访问（如 `basePrice`）
3. 适用于临时计算或条件判断

## 相关文档

- [数据处理和任务编排](data-processing.md)
- [XDef 核心概念](../05-xlang/xdef-core.md)
- [XPL 表达式语言](../05-xlang/xpl.md)
