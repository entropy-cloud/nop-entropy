# 逻辑删除

## 1. 数据模型配置
在Excel模型中指定domain为delFlag和delVersion的字段，分别为Boolean类型和Long类型。

逻辑删除的时候delFlag设置为1，且delVersion设置为当前时间戳。正常存在时，这两个值都设置为0.

deleteVersionProp用于避免逻辑删除时唯一键依然存在导致与正常实体的唯一键冲突。

也就是说假设实体上有一个属性name，要求唯一，那么在启用逻辑删除的时候，需要把name和deleteVersion这两个字段建立成联合唯一键。

新建的时候deleteVersion为0，而当逻辑删除的时候，deleteVersion设置为删除时刻的时间戳。这样以后仍然可以插入指定name的新记录，不会与已经逻辑删除的记录发生冲突。

## deleteVersionProp
如果设置了deleteVersionProp，但是没有设置deleteFlagProp，则会自动将deleteFlagProp设置为deleteVersionProp。这样的话，初始设置deleteVersion=0，删除的时候设置deleteVersion为时间戳。

## 2. EQL查询
缺省情况下EQL查询和关联集合查询都会考虑到逻辑删除条件，只查询`delFlag=0`的记录。

QueryBean和SQL对象都支持disableLogicalDelete设置，从而禁用逻辑删除的过滤条件。

IOrmEntity上也具有`orm_disableLogicalDelete()`方法，则可以真正执行物理删除，否则`session.delete(entity)`实际只会设置entity的deleteFlag属性。


## 权限控制

缺省情况下已经标记为逻辑删除的记录无法通过CrudBizModel的get/batchGet/findPage等方法读取到。

CrudBizModel提供了专用的deleted_get/deleted_findPage/recoverDeleted等方法用于管理已经被逻辑删除的记录。
这几个方法缺省情况下处于禁用状态，如果访问会抛出nop.err.biz.not-allow-get-deleted异常消息。

在meta文件的根节点上配置biz:allowGetDeleted=true会放开限制，此时原则上应该通过操作权限机制进行访问限制，确保只有某些角色才能调用这些方法。
