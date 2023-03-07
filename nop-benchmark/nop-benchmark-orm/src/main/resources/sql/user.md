userSelect
===

```sql
select * from sys_user where 1=1
-- @ if(isNotEmpty(id)){
and id=#{id}
-- @ }
```

queryPage
===

```sql
select #{page()} from sys_user where code=#{code}
```

