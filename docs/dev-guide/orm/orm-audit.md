# 操作审计

## createdBy

在Excel中如果设置了数据域domain为`createdBy/updatedBy`，则新增、修改实体时会记录当前用户名，如果domain为`createTime/updateTime`则会记录修改时间。

createdBy会存放`IContext.getUserRefNo()`
返回的值，缺省对应于userName。如果希望存放userId,需要配置`nop.auth.use-user-id-for-audit-fields`为true。

如果上下文中没有用户信息，缺省会使用SYS作为用户名，可以通过`nop.orm.sys-user-name`配置。

## 字段变更记录

在实体模型中增加标签 audit会启用字段变更记录，记录在NopSysChangeLog表中，会记录 实体-ID-属性-旧值-新值。

* 通过`nop.orm.audit.enabled=false`来禁用这个特性，缺省为true
* 删除会被记录为`prop=deleted`,新值为1
* 新增缺省情况下不会记录，除非为实体增加标签`audit-save`
