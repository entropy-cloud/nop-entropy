# EQL对象查询语言
EQL是NopORM内部使用的对象查询语言，它的基本设计思想是在SQL语法的基础上补充a.b.c这样的对象属性语法，并自动将对象属性关联转换为表关联。

完整的语法定义，参见[Eql.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-eql/model/antlr/Eql.g4)语法文件

# 1. 各种Join语法
EQL支持任意表之间的 left join, right join, full join语法，语法格式与普通SQL语言相同。这一点与Hibernate不同，Hibernate的HQL查询语法只支持
主子表之间的关联查询，关联条件根据主子表配置自动推定，而不能在HQL语法中直接指定关联条件。

````sql
select o.dept.name, u.type
from NopAuthUser o left join MyEntity u on o.id = u.userId

select o.dept.name, u.type
from NopAuthUser o , MyEntity u 
where o.id = u.userId

````

# 2. 分页语句

````sql
select o from NopAuthUser o limit 10 offset 0
````

# 3. for update语句

````
select o from NopAuthUser o where id = 1 for update
````

# 4. 删除语句
````
delete from NopAuthUser o wher o.id = 3
````

# 5. 更新语句

````
update NopAuthUser o set o.name = 'a' where o.id = 2
````

# 6. 插入语句

````
insert NopAuthUser(name, status) values('a',1)
````

# 7. case语句

# 8. cast语句
类型转换

# 8. 支持运算符

* between
* like
* in
* is null, is not null
* | : 比特或
* & : 比特与
* << : 左移位
* `>>` : 右移位
* % : 取余
* ^
* 