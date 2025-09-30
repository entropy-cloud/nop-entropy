
# EQL Object Query Language

EQL is the object query language used internally by NopORM. Its basic design idea is to extend SQL syntax with object property syntax like a.b.c and automatically convert object property associations into table joins.

For the complete grammar definition, see the [Eql.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/antlr/Eql.g4) grammar file.

## 1. Complex Property Associations

Using associated properties to automatically perform table join queries can greatly simplify the writing of standard SQL statements. For example, the User and SocialUser tables are associated via an intermediate table UserBind. Now we want to retrieve the User object by querying with the openId field on the SocialUser table.

```sql
select b.user from UserBind b where b.socialUser.openId = 123
```

## 2. Join Syntax Variants

EQL supports left join, right join, and full join between arbitrary tables, with syntax identical to standard SQL. This differs from Hibernate: Hibernate’s HQL query syntax only supports association queries between parent-child tables, where join conditions are inferred automatically from the mapping, and cannot be specified directly within HQL.

```sql
select o.dept.name, u.type
from NopAuthUser o left join MyEntity u on o.id = u.userId

select o.dept.name, u.type
from NopAuthUser o , MyEntity u
where o.id = u.userId

```

## 3. Pagination Statements

```sql
select o from NopAuthUser o limit 10 offset 0
```

## 4. FOR UPDATE Statement

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
insert NopAuthUser(name, status) values('a',1)
```

## 8. CASE Statement

## 9. CAST Statement

Type conversion

## 10. Supported Operators

* between
* like
* in
* is null, is not null
* \| : bitwise OR
* \& : bitwise AND
* \<\< : left shift
* `>>` : right shift
* % : modulo
* ^
*

<!-- SOURCE_MD5:03d3d48ea10d2da0f9c5532a15a6175b-->
