# Database and Table Sharding

NopORM currently only supports determining the database and table shard based on the value of shardProp. Each operation is allowed to access only one shard; automatic aggregation across multiple shards is not supported.

NopORM does not automatically create sharded tables. You can use metaprogramming to automatically generate sharded table definitions from existing database table definitions via mechanisms such as `x:extends` and `x:gen-extends`, and use DdlSqlCreator to generate the DDL statements for table creation.

1. In orm.xml, configure shardProp for the entity to specify the sharding field
2. Implement the IShardSelector interface, and configure a bean with id nopShardSelector in beans.xml

```java
public interface IShardSelector {
    /**
     * Determine the target shard based on the entity name and the property value specified at query time.
     *
     * @param entityName Entity name
     * @return
     */
    ShardSelection selectShard(String entityName, String shardProp, Object shardValue);
}

class ShardSelection {
  String querySpace;
  String shardName;
}
```

* querySpace is used to specify which data source to access. If it is not empty, it will use the data source corresponding to the bean with id=`nopDataSource_{querySpace}`
* shardName corresponds to the suffix of the sharded table. If it is not empty, the actual table accessed is `{tableName}_{shardName}`
* The concrete transformation logic resides in the GenSqlTransformer class

<!-- SOURCE_MD5:d989087efd2319b41f9161aeefecc50d-->
