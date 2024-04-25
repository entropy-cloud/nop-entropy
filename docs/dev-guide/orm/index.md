# [数据库方言](dialect.md)

## [DAO封装](dao.md)

针对单个实体对象的增删改查封装，支持复杂查询条件，支持分页。一般编写业务代码时我们只需要使用IEntityDao接口，而无需使用IOrmTemplate接口

## [ORM引擎](orm.md)

类似JPA的完整的ORM引擎的实现，它支持EQL对象查询语言，支持租户过滤、级联删除、逻辑删除、字段自动加解密等功能。

## [事务管理](transaction.md)

## [多数据源](multi-db.md)

## [数据修改历史](data-change-log.md)

## [多对多配置](many-to-many.md)

## [SQL管理](sql-lib.md)

类似于MyBatis的动态SQL管理框架。

## [增加扩展字段](ext-field.md)

在不修改数据库的情况下，通过纵表来保存扩展字段名和扩展字段值，支持对扩展字段的查询和排序。在XPL模板语言中使用的时候，扩展字段和普通字段的使用方式完全一致，
都是entity.fld属性访问语法。

## [DQL查询语言](dql.md)

类似于润乾DQL查询语言的查询机制，可以简化BI系统的数据获取。在Java中的使用，参见[mdx-query.md](mdx-query.md)

## [字段掩码](field-masking.md)

信用卡号等敏感数据显示到界面上或者打印到日志中时需要进行掩码处理
