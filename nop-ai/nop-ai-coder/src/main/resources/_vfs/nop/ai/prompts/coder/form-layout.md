1. 基础规则：

- `#` 表示嵌套层级深度
- `>cellId` 表示默认展开的可折叠区域
- `^cellId` 表示默认折叠的可折叠区域
- `!fieldName` 表示隐藏字段标签（注意符号在前）

2. layout配置：

- 每行最多放置3个字段
- 语义相关的字段应分组显示
- 重要/必填字段应优先排列
- 所有字段必须来自下方提供的字段列表
- 不需要用到所有字段，无关的字段可以直接忽略（不是隐藏）

【示例】

```xml

<form>
  <layout>
    ===#group_main====
    field1 field2 field3

    ===>##group_sub1==
    field4 field5

    ===#group_second===
    !field6
  </layout>

  <cells>
    <cell id="group_main" displayName="主标签">
      <visibleOn>fieldA > fieldB</visibleOn>
    </cell>
  </cells>
</form>
```
