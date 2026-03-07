# xdef 修正说明

# 正确的 xdef 用法

根据 Nop 平台的 xdef 规范，修正了 migration.xdef 文件中的语法错误。

## 关键原则

1. **简单文本内容**: 使用 `<element>type</element>` 格式
2. **xdef:value**: 仅用于**同时具有属性和文本内容**的复杂情况
3. **xdef:body-type**: 仅用于复杂结构（list/map/union）

## 修正内容

### 1. customCondition 元素
```xml
<!-- ❌ 错误 -->
<expression xdef:value="xpl"/>

<!-- ✅ 正确 -->
<customCondition xdef:name="CustomConditionPrecondition"
                 expect="enum:io.nop.db.migration.PreconditionExpect">
    <expression>xpl</expression>
</customCondition>
```

### 2. selectSql 元素
```xml
<!-- ❌ 错误 -->
<selectSql xdef:value="string"/>

<!-- ✅ 正确 -->
<selectSql>string</selectSql>
```

### 3. sql/body 元素
```xml
<!-- ❌ 错误 -->
<body xdef:value="string"/>

<!-- ✅ 正确 -->
<body>string</body>
```

### 4. where 元素（update/delete）
```xml
<!-- ❌ 错误 -->
<where xdef:value="string"/>

<!-- ✅ 正确 -->
<where>string</where>
```

### 5. implementation 元素
```xml
<!-- ❌ 错误 -->
<implementation xdef:value="xpl"/>

<!-- ✅ 正确 -->
<implementation>xpl</implementation>
```

## 为什么使用 `<element>type</element>` 而不是 `xdef:value="type"`？

根据 Nop 平台的 xdef 规范和现有代码库的实践：

1. **简洁性**: `<element>type</element>` 更简洁直观
2. **一致性**: 与 Nop 平台现有 xdef 文件保持一致
3. **语义清晰**: 明确表示该元素只包含简单文本内容

`xdef:value` 主要用于以下特殊情况：
- 元素同时具有属性和文本内容
- 需要明确区分属性和内容
- 在 union 类型的复杂定义中

## 参考

- `docs-for-ai/05-xlang/xdef-core.md` 第 292-334 行
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/*.xdef` 中的实践
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef` 中的实践
