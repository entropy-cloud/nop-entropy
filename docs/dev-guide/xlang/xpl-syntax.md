# **XPL 模板标签规范说明**

在markdown中，可以用xpl-syntax来简洁的表示XPL标签的语法结构, 例如:

```xpl-syntax
<!--
@bizObjName 目标业务对象名称
@limit 最大返回记录数(可选)
-->
<bo:DoFindList bizObjName="entity-name" limit="int">
  <!-- @filter 查询条件 -->
  <filter>
    <!--
    @name 字段名称
    @value 字段值(表达式)
    @op 操作符(默认为eq)
    -->
    <condition name="field" value="t-expr" op="eq|gt|lt..."/>
  </filter>

  <!-- @orderBy 排序规则 -->
  <orderBy>
    <!-- @name 排序字段 @desc 是否降序 -->
    <field name="field-name" desc="boolean"/>
  </orderBy>
</bo:DoFindList>
```

## **1. 语法格式**
- 使用 **`xpl-syntax`** 作为根标记，描述 XPL 模板标签的语法规则。
- 每个标签的**参数说明**以 `<!-- -->` 注释形式紧邻标签上方，格式为：
  ```xpl-syntax
  <!--
    @参数名1 参数说明（可选附加约束）
    @参数名2 参数说明
    ...
  -->
  <标签名 属性1="值" 属性2="值"/>
  ```
- **子标签**（如 `<filter>`）的注释独立一行，缩进与标签层级对齐。

---

## **2. 注释规则**
- **参数说明**：使用 `@参数名` 开头，后接简洁描述（如类型、行为约束）。
  - 示例：`@bizObjName 实体名称（如User）`
  - 可选参数标注 `(可选)`，如 `@limit 返回数量限制（可选）`。
- **子标签说明**：在子标签上方单独注释，说明其作用。
  - 示例：`<!-- @filter 过滤条件 -->`
