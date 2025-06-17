Delta差量是一种"只描述变更"的数据格式，告诉系统如何修改原始数据，而不是提供完整的新数据。

1. 变更类型标记

- 合并内容：x:override="merge"（默认可省略）
- 替换节点：x:override="replace"
- 删除节点：x:override="remove"
- 插入位置：x:before="id" 或 x:after="id"

2. 节点定位规则

- 集合元素通过id、name或者x:id等唯一标识进行定位
- 非集合元素使用XML节点名来定位。

【示例】

```xml

<form>
  <layout>
    <text name="title" value="新标题"/>
    <input name="age"/>
    <input name="name" required="true"/>
  </layout>
  <cells>
    <text id="title" value="新标题"/>
  </cells>
</form>
```

Delta差量

```xml

<form>
  <layout>
    <input name="age" x:override="remove"/>

    <image name="avatar" x:before="name"/>

    <input name="name" label="名称"/>
  </layout>
</form>
```

合并后的结果

```xml

<form>
  <layout>
    <text name="title" value="新标题"/>
    <image name="avatar"/>
    <input name="name" label="名称" required="true"/>
  </layout>
  <cells>
    <text id="title" value="新标题"/>
  </cells>
</form>
```
