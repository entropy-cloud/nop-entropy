# Field Masking

Due to security considerations, some sensitive user information should not be printed to the log file. When returning data to the frontend, such information also requires masking, as it cannot display all content. Only the first few digits or the last few digits can be shown, depending on the case.
For example, credit card numbers and phone numbers.

## Adding Masked Tags in Excel Models

In the data model, masked tags are annotated, which will be generated into the app.orm.xml model file. When the ORM engine prints SQL statements, all fields with masked tags are displayed as ***XX instead of their actual values.

> nop.core.default-masking-keep-chars can be used to control the number of characters displayed in the default masking scenario, with a default value of 2.

## Configuring Masked Fields in Meta

By adding `ui:maskPattern` to the property in meta:
```xml
<prop name="email" ui:maskPattern="3*4">
    
</prop>
```

`ui:maskPattern="3*4"` indicates that the first 3 digits and the last 4 characters should be shown, with other characters replaced by *.

## Program Control

* In Java code, masking can be performed using `StringHelper.maskPattern(text, "3*4")`.
* In sql-lib, masking can be represented as `${masked(cardNo)}`. The masked function will wrap the value in a MaskedValue object, and when the framework prints it to the log file, it will automatically apply the masking.
