# 执行原理

NopORM是一个类似Hibernate的完整ORM引擎，因此它也是通过OrmSession来管理所有加载到内存中的实体对象。在同一个session中，只会按照id从数据库加载一次，然后得到的实体对象就会被缓存到session中。

```javascript
MyEntity entity = dao.getEntityById("3");
List<MyEntity> entities = dao.findAll();
assertTrue(entityes.contains(entity));
```

同一个id所对应的实体一定是唯一的对象。在上面的例子中，findAll从数据库中查找得到实体数据，返回给调用者之前会检查是否已经有同样id的实体已经存在，
如果session中已经存在，则会使用已经存在的实体对象替换从数据库加载得到的实体对象。

> 注意，即使数据库中的数据已经被修改，与session中的实体数据不一致，我们也只会使用session中的对象，会自动丢弃数据库返回的数据。这在某种意义上提升了
> 数据层的事务级别，相当于实现了按id的可重复读。

* 修改实体并不需要调用session.update(entity)或者dao.updateEntity(entity)，因为NopORM会自动追踪session中所有实体的修改情况，在session.flush的时候
  会将修改自动同步到数据库中。
* 向集合对象中加入实体隐含的就表示该实体也要同步到数据库中，因此调用parent.getChildren().add(child)之后不需要再调用dao.saveEntity(child)来保存子实体。
* 从集合对象中删除实体会自动删除该实体，除非它的owner属性值被设置为null.

```
parent.getChildren().remove(child);
// child.setParent(null); 
```

如果不调用child.setParent(null)，则从parent的children集合中删除child表示要从数据库中删除child。而如果设置了child的parent为null，则表示解除父实体和子实体的关联关系。

## 不支持自增主键

NopOrm引擎不支持MySQL数据库内置的自增主键特性。具体原因如下：

1. Oracle等数据库不支持自增主键。Nop平台的设计倾向于采用多种主流数据库兼容的方式，便于产品在多个数据库之间迁移。
2. 分布式数据库支持自增主键存在困难。
3. 分库分表的时候支持统一的自增主键存在困难。
4. NopOrm的实现要求实体在保存到数据库中之前就具有id，很多相关功能设计都依赖于实体具有id，因此不能等待实体插入数据库中再获取id。

在Nop平台中只要主键列标注seq标签，就会利用ISequenceGenerator接口进行自动生成，不需要使用数据库的自增机制。

nop-sys-dao模块提供了一个ISequenceGenerator的缺省实现，它使用nop\_sys\_sequence表记录数据序列的使用情况，缺省所有实体都共用一个default的sequence记录，可以为每个实体单独指定一个序列号。
另外也可以通过nop\_sys\_sequence表指定使用UUID等随机生成方式，或者使用数据库内置支持的sequence语句来返回顺序递增的id。

## ORM内置功能

NopORM内置了很多业务常用的功能:

1. 字段加密，保存到数据库中时自动加密，读取到内存中时自动解密
2. 字段掩码：类似卡号等数据通过GraphQL返回时自动用\*号替换大部分内容，仅保留最后几位。
3. 逻辑删除：删除操作仅对应于设置deleted标识。此机制由ORM底层实现，EQL对象查询语法中也会自动增加过滤条件
4. MakerChecker审核机制：修改操作提交的时候会触发审核流程，审核成功后才会真的修改（此机制尚未完全实现，已经内置了接口）
5. 扩展字段： 无需加表就可以为实体增加扩展字段，可以对扩展字段进行排序和过滤，扩展字段在编程时的使用基本与原生字段相同，只是保存到纵表中，EQL查询语言自动进行横纵变换
6. 分库分表，多租户过滤
7. 自动记录修改人，修改时间
8. 通过trigger模型允许用户在应用层实现类似数据库trigger的机制，每个实体保存的时候都会触发trigger操作。
9. 通过Component机制自动实现JSON字段解析，允许用户利用类似的机制将多个字段封装为Component对象，提供自定义的增强方法。
