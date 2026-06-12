# 快速问题诊断清单

> 按症状索引常见根因。如果某个症状不符合表中模式，说明可能是新问题，需要回到 systematic debugging 流程从头追溯。

## EQL / SQL-lib

| 症状 | 最可能根因 | 验证方法 |
|------|-----------|---------|
| `nop.err.eql.unknown-column-name` | `<eql>` 中用了数据库列名（如 `update_time`）而非实体属性名（如 `updateTime`） | 检查对应实体 ORM XML 中的 `name`（驼峰属性名） |
| `nop.err.eql.unknown-table-name` | `<eql>` 中用了表名而非实体名 | 实体名 = `app.mall.dao.entity.XXX` 的简名（或用全限定名） |
| `<sql>` 查询结果为空/错列 | `<sql>` 中列名大小写与数据库实际列名不匹配 | PG 缺省折叠为小写；检查 DDL 是否加引号 |

**规则速记：** `<eql>` 用实体属性名（驼峰）；`<sql>` 用数据库列名（DDL 实际大小写）。

## 容器启动 / Bean 创建

| 症状 | 最可能根因 | 验证方法 |
|------|-----------|---------|
| `nop.err.biz.object-not-support-action` action=`add`/`save`/etc | IBiz 接口方法名与 BizModel 中 `@BizMutation`/`@BizQuery` 方法名不一致，或 XBiz 生成的动作名与接口方法名不匹配 | 对比 IBiz 接口方法 |


## 测试 / 数据库

| 症状 | 最可能根因 | 验证方法 |
|------|-----------|---------|
| `convert-to-type-fail: targetType=Integer value=xxx` | 实体字段类型与数据库列类型不匹配，如 `userId` 为 `STRING` 但列定义 `INTEGER` | 检查 ORM XML 中 `stdSqlType` 和 `stdDataType`；测试用纯数字 userId |
| `data-integrity-violation: NULL not allowed for column "XXX"` | 实体字段未设值但列 `mandatory=true` | 检查 ORM XML 确认必填列；确认 Java setter 是否被调用 |
| 测试中 `getEntityById` 返回非 null 但应已删除 | ORM 一级缓存中仍持有实体 | 使用 `daoProvider().daoFor().findAll()` 或 `orm().flushSession()` 后重新查询 |

## 通用

| 症状 | 最可能根因 | 验证方法 |
|------|-----------|---------|
| 某个问题连续 3 次修复失败 | 架构模式选型错误，不是局部 bug | 退一步审查设计文档和 owner doc |
| 测试间歇性失败 | 未隔离测试数据，或 `@BeforeEach` 未清理 | 确认 `@NopTestConfig(localDb=true)` 每次重置 schema |
