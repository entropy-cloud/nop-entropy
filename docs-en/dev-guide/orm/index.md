# [Database Dialect](dialect.md)

## [DAO Encapsulation](dao.md)

For single entity operations such as insert, update, delete, we provide DAO encapsulation. It supports complex query conditions and pagination. When writing business code, we generally only need to use the IEntityDao interface, not the IOrmTemplate interface.

## [ORM Engine](orm.md)

Similar to a full JPA-based ORM implementation, it supports EQL object query language, including tenant filtering, cascading delete, logical delete, and field auto-encryption capabilities.

## [Transaction Management](transaction.md)

## [Multi Data Source](multi-datasource.md)

## [Data Change Log](data-change-log.md)

## [Many-to-Many Configuration](many-to-many.md)

## [SQL Management](sql-lib.md)

Similar to MyBatis's dynamic SQL management framework.

## [Add Extended Fields](ext-field.md)

Without modifying the database, we use a vertical table to store extended field names and values. It supports querying and sorting of extended fields. When using XPL template language, the usage of extended fields is completely consistent with that of regular fields, all using the entity.fld attribute syntax.

## [DQL Query Language](dql.md)

Similar to RuanQuan's DQL query mechanism, which simplifies BI system data retrieval. In Java, see [mdx-query.md](mdx-query.md).

## [Field Masking](field-masking.md)

When displaying credit card numbers or logging them, masking is required.
