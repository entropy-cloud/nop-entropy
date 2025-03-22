# EQL Object Query Language

EQL is the object query language used internally by NopORM. Its basic design philosophy is to extend SQL syntax with a.b.c-like object property syntax and automatically convert object properties to table relationships.

For complete syntax definitions, refer to the [Eql.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/antlr/Eql.g4) syntax file.

## 1. Complex Property Associations

Using associative properties allows for automatic table relationship queries and significantly simplifies SQL statement writing. For example, to query a User object through the SocialUser table via the UserBind table:

```sql
select b.user from UserBind b where b.socialUser.openId = 123
```

## 2. Various Join Syntaxes

EQL supports arbitrary table joins including `left join`, `right join`, and `full join`. The syntax format is similar to standard SQL, unlike Hibernate's HQL, which only supports main table associations and does not allow explicit join conditions.

Example of left join:

```sql
select o.dept.name, u.type
from NopAuthUser o left join MyEntity u on o.id = u.userId
```

Example with where clause for multiple tables:

```sql
select o.dept.name, u.type
from NopAuthUser o , MyEntity u
where o.id = u.userId
```

## 3. Pagination Statements

```sql
select o from NopAuthUser o limit 10 offset 0
```

## 4. For Update Statements

```sql
select o from NopAuthUser o where id = 1 for update
```

## 5. Delete Statements

```sql
delete from NopAuthUser o where o.id = 3
```

## 6. Update Statements

```sql
update NopAuthUser o set o.name = 'a' where o.id = 2
```

## 7. Insert Statements

```sql
insert NopAuthUser(name, status) values('a', 1)
```

## 8. Case Statement

## 9. Cast Statement

Type conversion.

## 10. Supported Operators

- `between`
- `like`
- `in`
- `is null`, `is not null`
- `\|` (bitwise OR)
- `\&` (bitwise AND)
- `\<<` (left shift)
- `\>>` (right shift)
- `%` (modulo)
- `^` (xor)
