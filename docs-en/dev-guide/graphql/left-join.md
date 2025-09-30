# Implement Left Join Queries via QueryBean

A leftJoinProps collection property has been added to QueryBean; properties specified here will be translated into left join syntax.

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

When translated into an EQL object query, it becomes:

```sql
select o
from MyEntity o left join o.dept
where o.dept.name = 'a'
```

When querying from the frontend, you can pass parameters via URL:

```
/r/NopAuthUser__findPage?query_leftJoinProps=dept,xxx
```

## Security Control

To prevent attacks caused by the frontend sending unexpected join conditions, CrudBizModel will verify that the properties in leftJoinProps are already defined in the meta's `biz:allowedLeftJoinProps` collection.

By default, the frontend is not allowed to directly send join conditions to the backend, and the number of joins is limited to no more than 3.

<!-- SOURCE_MD5:a015e0d433fed636690f3c8601880b36-->
