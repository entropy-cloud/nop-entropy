# 分库分表

NopORM目前只支持按照shardProp的值确定分库以及分表，每次只允许访问一个分区，不支持自动对多个分区的数据进行汇总。

NopORM不会自动创建分区表。可以使用元编程，通过`x:extends`, `x:gen-extends`等机制从已有数据库表定义自动生成分区表定义，并使用DdlSqlCreator生成建表语句。

1. 在orm.xml中为entity配置shardProp，指定分区字段
2. 实现IShardSelector接口，并在beans.xml中配置一个bean， id为nopShardSelector

```java
public interface IShardSelector {
    /**
     * 根据查询对象名以及查询时指定的属性值来确定最终选择的shard
     *
     * @param entityName 实体名
     * @return
     */
    ShardSelection selectShard(String entityName, String shardProp, Object shardValue);
}

class ShardSelection {
  String querySpace;
  String shardName;
}
```

* querySpace用于指定访问哪个数据源。如果不为空，它会使用id=`nopDataSource_{querySpace}`这个bean所对应的数据源
* shardName对应于分区表的后缀。如果不为空，则实际访问的表是`{tableName}_{shardName}`
* 具体转换逻辑在GenSqlTransformer类中

