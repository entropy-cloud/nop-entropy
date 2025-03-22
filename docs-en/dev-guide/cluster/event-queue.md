# Event Queue

## Database Event Table

### Broadcast

1. The topic prefix is `bro-`.
2. On startup, query events where `eventTime > currentTime - eventTimeOffset` and process them in order.

```sql
select o
from event o
where o.topic = 'test'
  and o.eventTime > ${startTime}
  and o.eventID > lastProcessEventID
order by o.eventID asc
```

### Cluster Partitioning

1. The topic prefix is `cluster-`.
2. Query all records where `processTime < currentTime`, then modify `processTime` to `currentTime + maxProcessSpan` so that other nodes do not process the same event.
3. After successful processing, set the event status to `processed` or delete the event.
