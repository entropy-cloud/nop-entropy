# Field Masking

For security reasons, certain sensitive user information must not be printed to log files. When returned to the front end for display, it also needs to be masked: it should not show the full content, only the first few and last few characters, etc., such as credit card numbers and users’ phone numbers.

## Add the masked tag to columns in the Excel model

Masked tags annotated in the data model will be generated into the app.orm.xml model file. When the ORM engine prints SQL statements, all fields with the masked tag are shown as \*\*\*XX.

> nop.core.default-masking-keep-chars controls how many trailing characters are shown under the default masking; the default is 2

## Configure ui:maskPattern for the prop in meta to control the number of leading and trailing characters displayed

```xml
<prop name="email" ui:maskPattern="3*4">
    
</prop>
```

`ui:maskPattern="3*4"` means keep the first 3 and the last 4 characters, replacing the others with \*.

## Programmatic control

* In Java code, you can call StringHelper.maskPattern(text,"3\*4") to perform masking.
* In sql-lib, you can use `${masked(cardNo)}` to indicate an SQL parameter that needs masking. The masked function will wrap the value into the MaskedValue type; with this in place, when the framework prints it to the log file, it will automatically be masked

<!-- SOURCE_MD5:1e86eeefe36f9c33131e20bf65ce05f7-->
