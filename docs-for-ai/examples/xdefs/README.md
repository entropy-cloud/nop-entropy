# XDef 示例目录

本目录包含Nop平台XDef元模型定义语言的示例文件，用于帮助开发者理解和学习正确的XDef语法。

## 目录结构

```
xdefs/
├── README.md                    # 本说明文档
└── simple-model.xdef           # 简单模型示例
```

## 示例文件说明

### simple-model.xdef
- **目的**：展示XDef基础语法和正确的属性定义方式
- **内容**：包含基础类型、约束条件、列表、对象等语法
- **适用场景**：初学者学习XDef基础语法和属性定义模式

## 学习路径

1. **初学者**：从 `simple-model.xdef` 开始，了解基础语法和属性定义模式
2. **进阶学习**：参考Nop平台的实际XDef文件，学习更复杂的用法
3. **实际应用**：在实际项目中应用学到的语法模式

## 语法要点

### 基础语法（使用属性定义）
```xml
<!-- 字符串属性 -->
<name type="string" comment="名称"/>

<!-- 数值属性 -->
<age type="integer" comment="年龄"/>

<!-- 布尔属性 -->
<active type="boolean" default-value="true" comment="是否激活"/>
```

### 列表语法
```xml
<!-- 字符串列表 -->
<tags xdef:body-type="list" comment="标签列表">
    <tag type="string"/>
</tags>
```

### 对象语法
```xml
<!-- 对象属性 -->
<contact-info type="object" comment="联系信息">
    <email type="string" comment="邮箱"/>
    <phone type="string" comment="电话"/>
</contact-info>
```

## 常见错误

### 错误写法
```xml
<!-- 错误：使用子节点定义类型 -->
<name>string</name>

<!-- 错误：使用xdef:value -->
<name xdef:value="string"/>
```

### 正确写法
```xml
<!-- 正确：使用属性定义类型 -->
<name type="string" comment="名称"/>

<!-- 正确：使用xdef:ref引用 -->
<api xdef:ref="api.xdef" comment="API配置"/>
```

## 相关文档

- [XDef核心概念](../../getting-started/xlang/xdef-core-concepts.md)

## 贡献指南

欢迎提交更多XDef示例文件，请确保：
1. 语法正确，符合Nop平台规范
2. 包含清晰的注释说明
3. 提供实际应用场景
4. 包含错误vs正确的对比示例