# 1. filter:sql标签的执行结果是生成一个XNode

```xpl
<c:unit xpl:outputMode="node">
<filter:sql xpl:lib="/nop/core/xlib/filter.xlib">
  o.id in (select t.task.id from MyTask t where t.userId = ${$context.userId || '1'}) 
</filter:sql>
</c:unit>
```

* outputMode: node
* output

````

<_>
    <sql value="SQL[text=o.id in (select t.task.id from MyTask t where t.userId = ?) 
]"/>
</_>
````
