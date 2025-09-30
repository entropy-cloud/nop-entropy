# Handling Null Values

Different databases handle empty strings inconsistently. Oracle Database does not support empty strings; when a field value is set to an empty string, what is actually stored in the database is a null value.

To ensure portability across different databases, NopORM performs special detection and handling for empty strings. For details, see the implementation of the DialectImpl.getDataParameterBinder function.

If a field value is set to an empty string, it will be automatically converted to null when saved to the database. You can disable this behavior by setting nop.orm.auto\_convert\_empty\_string\_to\_null.
<!-- SOURCE_MD5:b27d94bcc121fd98bbbf037eeb1e3610-->
