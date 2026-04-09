# XDef 与 XDSL 规则

本页只保留当前仓库里最值得 AI 记住的 XDef / XDSL 规则。

## 所有 XDSL 文件的基本格式

根节点必须同时包含：

```xml
<your-tag x:schema="/nop/schema/your-xdef.xdef"
          xmlns:x="/nop/schema/xdsl.xdef">
    ...
</your-tag>
```

## XDef 的核心理解

XDef 的目标不是再造一层与最终 DSL 完全不同的 schema，而是让模型结构与最终 XML 基本同构，只是把具体值替换为类型声明。

## 最适合 AI 记住的几条规则

### 1. 简单标量优先写成属性

推荐：

```xml
<user id="!string" name="string" age="int">
    <description>string</description>
</user>
```

### 2. 长文本或复杂结构优先写成子节点

典型如：

- `description`
- 嵌套对象
- 集合结构

### 3. 列表结构使用 `xdef:body-type` 与 `xdef:key-attr`

```xml
<services xdef:body-type="list" xdef:key-attr="name">
    <service name="!var-name" className="!class-name"/>
</services>
```

### 4. 复用结构使用 `xdef:name` / `xdef:ref`

- `xdef:name` 给结构命名
- `xdef:ref` 在同文件或外部文件中复用结构

### 5. 逗号分隔字符串集合优先用 `csv-set`

不要把这类值继续写成普通 `string`。

### 6. 简单文本元素推荐直接写类型内容

推荐：

```xml
<description>string</description>
```

而不是为了简单文本额外使用 `xdef:body-type="string"`。

### 7. Java 枚举作为 XDef 域时，要让文本值对得上

如果 `.xdef` 中引用的是 `enum:全限定类名`，要优先保证枚举的可读文本与 XDef 中实际使用的值一致。

最常见的两种做法：

1. 给枚举项加 `@Option("text")`。
2. 如果文本值和 `name()` 不一致，覆写 `toString()` 返回协议值。

仓库里的很多枚举还会提供 `@StaticFactoryMethod` 的解析入口；如果周边代码已经这样写，继续沿用该风格。

## Delta / XDSL 的关键语法

### 继承

```xml
x:extends="super"
```

### 覆盖方式

- `x:override="replace"`
- `x:override="merge"`
- `x:override="remove"`

## 什么时候你应该先想起本页

1. 需要新增或修正 `.xdef` 文件。
2. 需要解释 `x:schema`、`x:extends`、`x:override`。
3. 需要设计一个新 DSL 或修正旧 DSL 结构。
4. 需要判断一个 XML DSL 文件为什么不符合仓库惯例。

## 相关文档

- `./delta-customization.md`
- `../04-reference/source-anchors.md`
