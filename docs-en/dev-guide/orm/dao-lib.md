# The SQL statement is executed directly in the tag library.

```xml
<dao:FindPage xpl:lib='/nop/orm/xlib/dao.xlib' offset='0' limit='10'>
    select o from NopAuthUser o
    where o.id= ${id}
</dao:FindPage>
```

The `/nop/orm/xlib/dao.xlib` library provides the ability to execute SQL statements directly. In the body of the tag, the xpl tag dynamically generates SQL.

The common attributes of the FindPage/FindFirst/FindAll tags are as follows:

| Property | Description |
|---------|-------------|
| sqlType | The value can be "sql" or "eql", specifying whether to execute SQL or EQL statements |
| rowType | The type of rows returned, corresponding to Java objects |
| querySpace | The query space, corresponding to different database connections |
| timeout | The timeout for executing SQL statements |
| cacheName | Cache name |
| cacheKey | Cache key |
| disableLogicalDelete | Disable the logical delete condition |