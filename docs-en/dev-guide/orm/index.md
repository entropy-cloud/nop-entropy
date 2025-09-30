# [Database Dialects](dialect.md)

## [DAO Encapsulation](dao.md)

Encapsulation for CRUD operations on a single entity, supporting complex query conditions and pagination. In general business code, we only need to use the IEntityDao interface, without the need to use the IOrmTemplate interface.

## [ORM Engine](orm.md)

A full ORM engine implementation similar to JPA; it supports the EQL object query language, tenant filtering, cascading deletes, logical deletes, and automatic field encryption/decryption.

## [Transaction Management](transaction.md)

## [Multiple Data Sources](multi-datasource.md)

## [Data Change History](data-change-log.md)

## [Many-to-Many Configuration](many-to-many.md)

## [SQL Management](sql-lib.md)

A dynamic SQL management framework similar to MyBatis.

## [Adding Extension Fields](ext-field.md)

Without modifying the database, extension field names and values are stored via a vertical table, supporting queries and sorting on extension fields. When used in the XPL template language, extension fields are used in exactly the same way as regular fields, both following the entity.fld property access syntax.

## [DQL Query Language](dql.md)

A query mechanism similar to the Runqian DQL query language that can simplify data retrieval for BI systems. For usage in Java, see [mdx-query.md](mdx-query.md)

## [Field Masking](field-masking.md)

Sensitive data such as credit card numbers needs to be masked when displayed on the UI or printed in logs.
<!-- SOURCE_MD5:086d16c9abae1b0e051f7954492544c9-->
