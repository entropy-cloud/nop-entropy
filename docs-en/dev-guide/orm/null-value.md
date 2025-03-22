# Handling Null Values

Different databases handle empty string logic inconsistently. Oracle Database does not support empty strings, so when a field value is set to an empty string, it is actually stored as `null` in the database.

NopORM ensures portability across different databases by implementing special identification and handling for empty strings. This is handled by the `DialectImpl.getDataParameterBinder` method's implementation.

When a field value is set to an empty string, it will be automatically converted to `null` when stored in the database. This behavior can be disabled by setting `nop.orm.auto_convert_empty_string_to_null = false`.
