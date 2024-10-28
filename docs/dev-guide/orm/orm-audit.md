# 操作审计

## createdBy
在Excel中如果设置了数据域domain为`createdBy/updatedBy`，则新增、修改实体时会记录当前用户名，如果domain为`createTime/updateTime`则会记录修改时间。

createdBy会存放`IContext.getUserRefNo()`返回的值，缺省对应于userName。如果希望存放userId,需要配置`nop.auth.use-user-id-for-audit-fields`为true。
