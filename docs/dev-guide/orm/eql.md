# EQL对象查询语言

EQL是NopORM内部使用的对象查询语言，它的基本设计思想是在SQL语法的基础上补充a.b.c这样的对象属性语法，并自动将对象属性关联转换为表关联。

完整的语法定义，参见[Eql.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/antlr/Eql.g4)语法文件

## 1. 复杂属性关联

使用关联属性来自动实现表关联查询，可以极大简化一般SQL语句的编写。比如 User和SocialUser表通过中间表 UserBind表进行关联，
现在想通过SocialUser表上的openId字段来查询得到User对象。

```sql
select b.user from UserBind b where b.socialUser.openId = 123
```

## 2. 各种Join语法

EQL支持任意表之间的 left join, right join, full join语法，语法格式与普通SQL语言相同。这一点与Hibernate不同，Hibernate的HQL查询语法只支持
主子表之间的关联查询，关联条件根据主子表配置自动推定，而不能在HQL语法中直接指定关联条件。

```sql
select o.dept.name, u.type
from NopAuthUser o left join MyEntity u on o.id = u.userId

select o.dept.name, u.type
from NopAuthUser o , MyEntity u
where o.id = u.userId

```

## 3. 分页语句

```sql
select o from NopAuthUser o limit 10 offset 0
```

## 4. for update语句

```sql
select o from NopAuthUser o where id = 1 for update
```

## 5. 删除语句

```sql
delete from NopAuthUser o wher o.id = 3
```

## 6. 更新语句

```sql
update NopAuthUser o set o.name = 'a' where o.id = 2
```

## 7. 插入语句

```sql
insert NopAuthUser(name, status) values('a',1)
```

## 8. case语句

## 9. cast语句

类型转换

## 10. 支持运算符

* between
* like
* in
* is null, is not null
* \| : 比特或
* \& : 比特与
* \<\< : 左移位
* `>>` : 右移位
* % : 取余
* ^
*
