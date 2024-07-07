# 逻辑删除

## 1. 数据模型配置
在Excel模型中指定domain为delFlag和delVersion的字段，分别为Boolean类型和Long类型。

逻辑删除的时候delFlag设置为1，且delVersion设置为当前时间戳。正常存在时，这两个值都设置为0.

deleteVersionProp用于避免逻辑删除时唯一键依然存在导致与正常实体的唯一键冲突。

也就是说假设实体上有一个属性name，要求唯一，那么在启用逻辑删除的时候，需要把name和deleteVersion这两个字段建立成联合唯一键。

新建的时候deleteVersion为0，而当逻辑删除的时候，deleteVersion设置为删除时刻的时间戳。这样以后仍然可以插入指定name的新记录，不会与已经逻辑删除的记录发生冲突。

## 2. EQL查询
缺省情况下EQL查询和关联集合查询都会考虑到逻辑删除条件，只查询`delFlag=0`的记录。

QueryBean和SQL对象都支持disableLogicalDelete设置，从而禁用逻辑删除的过滤条件。
