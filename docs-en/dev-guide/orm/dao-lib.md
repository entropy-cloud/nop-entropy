# Execute SQL Statements Directly in the Tag Library

```xml

<dao:FindPage xpl:lib='/nop/orm/xlib/dao.xlib' offset='0' limit='10'>
    select o from NopAuthUser o
    where o.id= ${id}
</dao:FindPage>
```

`/nop/orm/xlib/dao.xlib` provides the capability to execute SQL statements directly. In the body of the tag, you can dynamically generate SQL via xpl tags.

The common attributes of the FindPage/FindFirst/FindAll tags are as follows:

|Name|Description|
|---|---|
|sqlType|Value is sql or eql; specifies whether to execute statements in SQL or EQL|
|rowType|The Java class used to wrap each row of the query result as a Java object|
|querySpace|Query space, corresponding to different database connections|
|timeout|Timeout for SQL statement execution|
|cacheName|Cache name|
|cacheKey|Cache key|
|disableLogicalDelete|Disables the logical-delete condition|

<!-- SOURCE_MD5:111c14fe7e17e2f7463d39a40464e5e0-->
