# 特性开关

在XDSL的任意节点上都可以通过特性表达式来实现编译期的开关选择

```
<root>
  <child feature:on="my.flag and !my.disabled-flag">
  </child>
</root>
```

* `feature:on`表示表达式为true时节点才存在，否则在加载的后处理操作中会被自动删除
* `feature:off`的语义表示表达式为false时节点才存在，语义与feature:on相反
* 节点上可以同时具有`feature:on`和`feature:off`设置，两个判断都通过才可以

## 特性表达式

1. 特性表达式支持复杂的and/or语法，简单的比较运算，比如`>=，=`等。
2. 可以通过!表示取反
3. 支持括号

## 虚拟节点

有时为了方便控制，我们可以加入一个虚拟节点。当特性开关不满足时，虚拟节点下的所有内容都会自动被删除。

```xml

<domain>
  <options>
    <x:div feature:on="my.a1">
      <option>1</option>
      <option>2</option>
    </x:div>
    <option>3</option>
  </options>
</domain>
```

当application.yaml中配置`my.a1=true`时，加载得到的XNode节点为

```xml

<domain>
  <options>
    <option>1</option>
    <option>2</option>
    <option>3</option>
  </options>
</domain>
```

## Meta配置变量

在根节点上配置 `feature:enable-meta-cfg`为true之后，会识别`@meta-cfg:`前缀，自动调换配置变量。例如:

```xml

<task feature:enable-meta-cfg="true">
  <step fetchSize="@meta-cfg:my.fetch-size|10">

  </step>
</task>
```

如果application.yaml中配置了`my.fetch-size`，则以配置的变量为准，否则fetchSize将被设置为缺省值10.
