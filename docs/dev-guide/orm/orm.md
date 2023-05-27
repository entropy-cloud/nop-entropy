# 执行原理
NopORM是一个类似Hibernate的完整ORM引擎，因此它也是通过OrmSession来管理所有加载到内存中的实体对象。在同一个session中，只会按照id从数据库加载一次，然后得到的实体对象就会被缓存到session中。

````javascript
MyEntity entity = dao.getEntityById("3");
List<MyEntity> entities = dao.findAll();
assertTrue(entityes.contains(entity));
````

同一个id所对应的实体一定是唯一的对象。在上面的例子中，findAll从数据库中查找得到实体数据，返回给调用者之前会检查是否已经有同样id的实体已经存在，
如果session中已经存在，则会使用已经存在的实体对象替换从数据库加载得到的实体对象。

> 注意，即使数据库中的数据已经被修改，与session中的实体数据不一致，我们也只会使用session中的对象，会自动丢弃数据库返回的数据。这在某种意义上提升了
> 数据层的事务级别，相当于实现了按id的可重复读。

* 修改实体并不需要调用session.update(entity)或者dao.updateEntity(entity)，因为NopORM会自动追踪session中所有实体的修改情况，在session.flush的时候
会将修改自动同步到数据库中。
* 向集合对象中加入实体隐含的就表示该实体也要同步到数据库中，因此调用parent.getChildren().add(child)之后不需要再调用dao.saveEntity(child)来保存子实体。
* 从集合对象中删除实体会自动删除该实体，除非它的owner属性值被设置为null. 
````
parent.getChildren().remove(child);
// child.setParent(null); 
````
如果不调用child.setParent(null)，则从parent的children集合中删除child表示要从数据库中删除child。而如果设置了child的parent为null，则表示解除父实体和子实体的关联关系。

# 不支持自增主键
NopOrm引擎不支持MySQL数据库内置的自增主键特性。具体原因如下：
1. Oracle等数据库不支持自增主键。Nop平台的设计倾向于采用多种主流数据库兼容的方式，便于产品在多个数据库之间迁移。
2. 分布式数据库支持自增主键存在困难。
3. 分库分表的时候支持统一的自增主键存在困难。
4. NopOrm的实现要求实体在保存到数据库中之前就具有id，很多相关功能设计都依赖于实体具有id，因此不能等待实体插入数据库中再获取id。

在Nop平台中只要主键列标注seq标签，就会利用ISequenceGenerator接口进行自动生成，不需要使用数据库的自增机制。

nop-sys-dao模块提供了一个ISequenceGenerator的缺省实现，它使用nop_sys_sequence表记录数据序列的使用情况，缺省所有实体都共用一个default的sequence记录，可以为每个实体单独指定一个序列号。
另外也可以通过nop_sys_sequence表指定使用UUID等随机生成方式，或者使用数据库内置支持的sequence语句来返回顺序递增的id。