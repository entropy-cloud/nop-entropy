# XDef模型设计指南

## 概述

XDef是Nop平台的元模型定义语言，用于定义各种领域特定语言(DSL)的语法和结构。每个XDef模型都定义了一个DSL语言，系统会自动生成解析器、验证器、IDE支持等。

## 核心概念

### 1. XDef模型
- 定义：描述DSL语法结构的元模型
- 位置：`_vfs`目录下，如`/nop/schema/orm.xdef`
- 内容：类型定义、属性定义、约束规则等

### 2. DSL生成
- 自动生成DSL解析器
- 自动生成DSL验证器
- 自动生成IDE支持（语法提示、跳转、调试）
- 自动生成文档

### 3. XDef语法
- 基于XML的语法
- 支持类型系统
- 支持约束规则
- 支持继承和扩展

## 设计流程

### 1. 设计XDef模型
创建XML文件定义DSL语法结构

### 2. 注册XDef模型
将XDef模型注册到系统中

### 3. 生成DSL支持
系统自动生成解析器、验证器等

### 4. 使用DSL
使用定义的DSL编写业务逻辑

## 关键语法理解：XDef与XML的同构关系

### XDef的本质
XDef模型应该与最终生成的XML结构同构，只是用类型信息替换具体的值。这是理解XDef语法的关键。

### 正确语法模式

#### 1. 简单属性定义（使用 Attribute）
```xml
<!-- XDef定义 -->
<user name="string" age="integer" active="boolean">
    <description>text</description>
</user>

<!-- 最终生成的XML -->
<user name="张三" age="25" active="true">
    <description>用户描述</description>
</user>
```

#### 2. 属性类型定义（正确）
```xml
<!-- XDef定义 -->
<user id="!string" name="string" email="string"/>

<!-- 最终生成的XML -->
<user id="123" name="张三" email="zhang@example.com"/>
```

### 常见错误语法（避免使用）

#### ❌ 错误：使用type属性定义类型
```xml
<user>
    <name type="string"/>
    <age type="integer"/>
</user>
```

#### ❌ 错误：使用xdef:value
```xml
<user>
    <name xdef:value="string"/>
    <age xdef:value="integer"/>
</user>
```

### 参考示例文件
- **正确示例**：[simple-model.xdef](../../../examples/xdefs/simple-model.xdef)
- **学习路径**：[xdefs README](../../../examples/xdefs/README.md)

## 设计注意事项

### 1. 模型结构设计
- **模块化**：将复杂模型拆分为多个模块
- **层次化**：合理设计模型的层次结构
- **复用**：使用继承和引用复用模型定义

### 2. 类型定义
- **基本类型**：使用内置的基本类型
- **复杂类型**：定义结构化的复杂类型
- **枚举类型**：使用枚举类型限制取值范围
- **集合类型**：使用数组和列表类型

### 3. 属性设计
- **名称**：使用有意义的属性名
- **类型**：选择合适的数据类型
- **约束**：添加必要的约束规则
- **描述**：添加清晰的属性描述

### 4. 约束规则
- **必填性**：使用`required="true"`标记必填属性
- **唯一性**：使用`unique="true"`标记唯一属性
- **取值范围**：使用`min`、`max`、`pattern`等限制取值
- **引用关系**：使用`ref`定义引用关系

## 示例XDef模型

```xml
<xdef:definitions xmlns:xdef="http://nop-xlang.github.io/schema/xdef.xdef">
  <!-- 基本类型定义 -->
  <xdef:type name="OrderStatus" xdef:enum="true">
    <xdef:item name="PENDING" value="pending" description="待处理" />
    <xdef:item name="PAID" value="paid" description="已支付" />
    <xdef:item name="SHIPPED" value="shipped" description="已发货" />
    <xdef:item name="COMPLETED" value="completed" description="已完成" />
  </xdef:type>
  
  <!-- 复杂类型定义 -->
  <xdef:type name="Address">
    <xdef:prop name="city" type="string" required="true" description="城市" />
    <xdef:prop name="street" type="string" required="true" description="街道" />
    <xdef:prop name="zipCode" type="string" pattern="\d{6}" description="邮政编码" />
  </xdef:type>
  
  <!-- 主实体定义 -->
  <xdef:type name="Order">
    <xdef:prop name="id" type="string" primary="true" description="订单ID" />
    <xdef:prop name="userId" type="string" required="true" description="用户ID" />
    <xdef:prop name="amount" type="decimal" min="0" required="true" description="订单金额" />
    <xdef:prop name="status" type="OrderStatus" default="PENDING" description="订单状态" />
    <xdef:prop name="address" type="Address" required="true" description="配送地址" />
    <xdef:prop name="items" type="OrderItem[]" required="true" minItems="1" description="订单商品" />
  </xdef:type>
  
  <!-- 子实体定义 -->
  <xdef:type name="OrderItem">
    <xdef:prop name="productId" type="string" required="true" description="商品ID" />
    <xdef:prop name="quantity" type="int" min="1" required="true" description="数量" />
    <xdef:prop name="price" type="decimal" min="0" required="true" description="单价" />
  </xdef:type>
  
  <!-- 根元素定义 -->
  <xdef:root name="Orders">
    <xdef:prop name="orders" type="Order[]" required="true" description="订单列表" />
  </xdef:root>
</xdef:definitions>
```

## 自动生成的结果

### 1. DSL解析器
- 自动生成DSL模型的解析器
- 支持XML和JSON格式
- 支持差量化合并

### 2. DSL验证器
- 自动验证DSL文档的合法性
- 验证必填项、数据类型、约束规则等
- 生成详细的错误信息

### 3. IDE支持
- 语法提示和自动补全
- 跳转到定义和引用
- 断点调试支持
- 错误高亮显示

### 4. 文档生成
- 自动生成DSL文档
- 包含类型定义、属性说明、约束规则等
- 支持多种格式（HTML、Markdown等）

### 5. 代码生成
- 可以基于DSL模型生成业务代码
- 支持Java、GraphQL、前台页面等
- 支持定制生成模板

## 最佳实践

1. **清晰的命名**：使用清晰、有意义的名称
2. **完整的描述**：为所有类型和属性添加描述
3. **合理的约束**：添加必要的约束规则
4. **模块化设计**：将复杂模型拆分为多个模块
5. **复用设计**：使用继承和引用复用模型定义
6. **测试验证**：对XDef模型进行充分测试

## 注意事项

- XDef模型是DSL的基础，应仔细设计
- 避免过于复杂的模型结构
- 考虑DSL的易用性和可读性
- 遵循平台的命名规范
- 定期维护和更新XDef模型

## 常见问题

### 1. 模型冲突
- **问题**：不同模块的XDef模型冲突
- **解决**：使用命名空间和模块名区分

### 2. 模型过于复杂
- **问题**：XDef模型过于复杂难以维护
- **解决**：拆分模型，提高模块化程度

### 3. 性能问题
- **问题**：复杂XDef模型导致解析性能下降
- **解决**：优化模型结构，减少不必要的约束

### 4. 版本兼容性
- **问题**：XDef模型变更导致兼容性问题
- **解决**：设计向前兼容的模型变更策略

## 扩展功能

### 1. 自定义验证器
- 添加自定义的验证逻辑
- 实现复杂的业务规则验证

### 2. 自定义生成器
- 扩展代码生成器
- 支持生成更多类型的代码

### 3. 可视化设计器
- 为XDef模型提供可视化设计器
- 支持拖拽式设计DSL模型

### 4. 动态扩展
- 支持运行时扩展XDef模型
- 支持动态添加属性和类型