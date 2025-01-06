# 事件队列

## 数据库事件表

### 广播

1. topic以 `bro-`为前缀
2. 启动时查询 `eventTime > currentTime - eventTimeOffset` 的事件，并依次处理

```sql
select o
from event o
where o.topic = 'test'
  and o.eventTime > ${startTime}
  and o.eventID > lastProcessEventID
order by o.eventID asc
```

### 集群分工

1. topic以 `cluster-`为前缀
2. 查询所有 `processTime < currentTime`的记录，修改processTime为`currentTime + maxProcessSpan`，这样其他节点就不会处理同样的事件
3. 成功处理完毕后将事件状态设置为processed，或者删除事件
