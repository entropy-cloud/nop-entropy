# 在标签库中直接执行SQL语句

```xml

<dao:FindPage xpl:lib='/nop/orm/xlib/dao.xlib' offset='0' limit='10'>
    select o from NopAuthUser o
    where o.id= ${id}
</dao:FindPage>
```

`/nop/orm/xlib/dao.xlib`提供了直接执行SQL语句的能力。在标签的body部分可以通过xpl标签动态生成SQL。

FindPage/FindFirst/FindAll标签的公共属性如下：

|名称|说明|
|---|---|
|sqlType|值为sql或者eql,指定执行SQL语言还是EQL语言的语句|
|rowType|将查询得到的行数据包装为Java对象所对应的Java类|
|querySpace|查询空间，对应于不同的数据库链接|
|timeout|SQL语句执行的超时时间|
|cacheName|缓存名称|
|cacheKey|缓存key|
|disableLogicalDelete|禁用逻辑删除条件|
