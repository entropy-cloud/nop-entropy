# Using QueryBean for Left Join Queries

The `QueryBean` now includes a `leftJoinProps` collection property. The specified properties will be converted into left join syntax.

```json
{
  "leftJoinProps": [
    "dept"
  ],
  "filter": {
    "$type": "eq",
    "name": "dept.name",
    "value": "a"
  }
}
```

When translated into EQL object queries, it becomes:

```sql
select o
from MyEntity o left join o.dept
where o.dept.name = 'a'
```

Parameters can be passed via URL in frontend queries like:

```
/r/NopAuthUser__findPage?query_leftJoinProps=dept,xxx
```

## Security Control

To prevent unexpected association conditions that could lead to attacks, `CrudBizModel` checks the `biz:allowedLeftJoinProps` collection.

By default, direct sending of association conditions from frontend to backend is not allowed, and the number of associations is limited to 3.