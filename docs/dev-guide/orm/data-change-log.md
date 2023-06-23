# 实体修改状态跟踪

所有实体都从OrmEntity继承，在OrmEntity上提供了修改状态跟踪机制

1. orm_propDirty(propId) 可以判断某个字段是否已经被修改
2. orm_propOldValue(propId) 返回修改前的值。如果没有被修改，则返回当前的值
3. orm_propValue(propId) 返回字段当前的值
4. orm_dirtyOldValues()和orm_dirtyNewValues() 返回所有被修改的字段的修改前和修改后的值，返回的Map的key为属性名

# 修改监听器

实现IOrmInterceptor接口，可以监听preSave/preUpdate/preDelete等事件，在其中记录修改日志。nop-sys模块提供了一个缺省的修改日志表，以及缺省实现

NopORM启动的时候会查找在IoC容器中查找所有IOrmInterceptor接口的实现类，