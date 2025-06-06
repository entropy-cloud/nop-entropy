1. 设计范围不包含User、Role, Permission、页面资源等通用公共表
2. 所有字段命名需严格避免与 SQL 关键字冲突
3. 主键名固定使用id, 类型为VARCHAR，长度36, 不具有业务含义
4. stdSqlType不能为空，允许的值：VARCHAR, CHAR, DATE, TIME, DATETIME,TIMESTAMP, INTEGER,BIGINT,DECIMAL,BOOLEAN,VARBINARY
5. `orm:ref-prop`是主表上引用子表的集合属性，用于反向关联子表。 数据字典表引用场景无需设置此属性，该属性主要用于支持主表记录创建时级联提交子表记录集合
6. 当存在`orm:ref-prop`属性的时候，需要同时设置`orm:ref-prop-display-name`和`orm:ref-table`
7. std-domain的可选范围image|file|fileList|imageList。图片字段、图片地址字段对应于image， 附件字段对应于file,附件列表字段对应于fileList。
8. ext:dict指定字段值的可选范围由字典定义。字典的名称必须在dicts集合中。status等字段应该指定ext:dict。
9. 不需要为表增加`创建时间`，`修改人`等审计字段，也不要增加`乐观锁版本号`等辅助字段，Nop平台会整体添加
10. biz:type的可选值有entity|entity-detail|txn|txn-detail|report|report-detail|config|config-detail，区分表格是业务实体，还是用于记录交易记录、报表数据还是配置数据，并且区分是主表还是明细表。比如CreditCard是entity, 而Order是txn, OrderDetail是txn-detail
11. 假定系统有100个常用用户，使用1年时间，db:estimatedRowCount用于估计此表中累积的数据行数
12. 字典命名需遵循`业务域_用途`格式（如`user_status`）
13. 所有表都采用逻辑删除，固定使用BOOL类型的IS_DELETED字段

**再次强调**: Nop平台内置了乐观锁，逻辑删除，操作日志等功能，因此**不需要**在业务表中增加版本号、IS_DELETED、CREATE_TIME等字段。即使需求中明确提到这些字段也不需要添加！！

**关键约束**:
- 所有 `<column>` 必须对应数据库原始字段（数值/文本/日期等基础类型）
- 禁止将子表关联属性（如集合引用）定义为 `<column>`
- 外键关系仅通过 `orm:ref-*` 属性在子表字段上声明
