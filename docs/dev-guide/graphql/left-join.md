# 通过QueryBean实现左连接查询

QueryBean中增加了leftJoinProps集合属性，这里指定的属性会转换为左连接语法。

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

翻译成EQL对象查询时会变为

```sql
select o
from MyEntity o left join o.dept
where o.dept.name = 'a'
```

前台查询时可以通过url传递参数

```
/r/NopAuthUser__findPage?query_leftJoinProps=dept,xxx
```

## 安全性控制

为了避免前台发送预料之外的关联条件导致出现攻击，CrudBizModel中会检查leftJoinProps中的属性已经在meta的`biz:allowedLeftJoinProps`
集合中定义。

缺省情况下不允许前端直接发送关联条件到后台，并且会限制关联个数不超过3。
