# 乐观锁

## 启用乐观锁

在Excel模型中，为字段指定【数据域】为version，则会设置实体的versionProp配置，它的类型应该是INTEGER或者BIGINT。

更新数据库时会自动加入version条件，并自动对version+1。

如果更新了多条记录，则EntityPersisterImpl中将会抛出`nop.err.orm.update-entity-multiple-rows`异常.
如果更新失败，更新条目数为0，则会抛出`nop.err.orm.update-entity-not-found`异常。

## 禁用乐观锁

`entity.orm_disableVersionCheckError(true)`可以禁用指定实体的乐观锁更新检查，按照versionProp更新失败时不会抛出异常，但是会导致实体被设置为readonly。
