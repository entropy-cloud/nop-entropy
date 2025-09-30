# Event Queue

## Database Event Table

### Broadcast

1. The topic is prefixed with `bro-`.
2. On startup, query events with `eventTime > currentTime - eventTimeOffset`, and process them in sequence.

```sql
select o
from event o
where o.topic = 'test'
  and o.eventTime > ${startTime}
  and o.eventID > lastProcessEventID
order by o.eventID asc
```

### Cluster Work Allocation

1. The topic is prefixed with `cluster-`.
2. Query all records with `processTime < currentTime`, and update processTime to `currentTime + maxProcessSpan`, so that other nodes will not process the same event.
3. After successful processing, set the event status to processed, or delete the event.
<!-- SOURCE_MD5:85d2f32bbd3befa893ae287059bdbf04-->
