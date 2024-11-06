# NopORM与MyBatis的区别

## 声明式与命令式的区别

ORM的抽象模型是
实体就是数据本身，修改实体属性就意味着改数据，并不需要调用dao.update方法。因为MyBatis是一个半残废的ORM，导致需要通过动作来表达保存语义。调用两次save会导致执行两次数据库访问。ORM是声明式的，调用100次saveOrUpdate也只是标记实体需要被保存，实际只有session.flush的时候会更新一次数据库

MyBatis这种ORM对于封装是不利的。比如在methodA中修改了一些数据需要保存，在methodB里又修改了同样的数据需要保存，你单独调用methodA的时候应该执行一次save，
单独执行methodB的时候应该实际更新一次数据库。但是如果同时调用methodA和methodB，应该合并对数据库的修改，也只执行一次数据库更新。但是MyBatis做不到这一点，最终导致破坏这种保存语义的可复合性。

## 更新数据库时自动进行依赖排序

使用MyBatis在复杂场景下经常会出现数据库锁冲突的问题。比如一个线程先更新A，再更新B，而另一个线程先更新B再更新A。但是NopORM是在session.flush的时候检查所有当前内存中的实体有哪些被修改了，然后计算修改差量，根据差量生成数据库变更SQL，在计算的时候会对所有实体按照表依赖关系和主键大小进行排序，按照规定的顺序执行SQL。确保更新数据库的时候永远都是先更新A再更新B

## 减少数据库连接占用时间

NopORM内置了乐观锁支持，因此读取操作在事务之外执行也不会影响到数据库一致性。我们可以仅在OrmSession.flush的时候打开事务，不需要在整个读取阶段都持有数据库连接。

NopORM提供的ITransactionTemplate帮助类内部还提供了延迟获取连接的优化，仅当我们第一次实际用到数据库连接的时候才实际开启事务，在后台服务函数执行的过程中，我们可以先执行一些与数据库访问无关的操作，从而进一步减少对数据库连接的占用。

## JDBC批量优化

OrmSession.flush的时候会自动利用JDBC内置的batch机制来批量执行，从而极大的优化执行性能。

## 主键不允许修改

NopORM中一旦保存实体之后，就不允许修改实体的主键。在一般的关系数据库设计中，主键的变动一般也是极度不推荐的。

```javascript
var entity = dao.newEntity();
entity.setId("1");
entity.setName("test");
dao.saveEntity(entity);
entity.setId("33"); // 这种修改是不正确的，实际也不会起作用
dao.saveOrUpdateEntity("33");
```
