# Coding Rules

Business systems often need to automatically generate business codes according to certain coding rules, such as automatically generating order numbers, card numbers, etc.

## Built-in CodeRule

1. Add a code tag to the field in the model.
2. During code generation, a configuration like biz:codeRule="ObjectName@PropertyName" will be automatically added to the field in meta. This code rule name can be overridden in derived meta.
3. Include the nop-sys-dao module, add the corresponding record in the nop_sys_code_rule table, and configure the coding template.
4. When saving or modifying an entity, meta's autoExpr will be triggered (automatically generated according to the biz:codeRule configuration).

## Coding Template

The ICodeRule interface supports defining a set of variable patterns, then parsing the coding template and replacing the variables to generate the code string.

Variables in the coding template are represented in the form {@type:options}, for example D{@year}{@month}{@seq:5} means:

* Prefix is D
* 4-digit year
* 2-digit month
* A 5-digit sequentially increasing sequence number; when it reaches the maximum, it wraps around

|name|pattern|description|
|---|---|---|
|year|{@year}|Year|
|month|{@month}|Month, fixed two digits|
|dayOfMonth|{@dayOfMonth}|Day of month, 1 to 31|
|hour|{@hour}|Hour, fixed two digits|
|minute|{@minute}|Minute, fixed two digits|
|second|{@second}|Second, fixed two digits|
|randNumber|{@randNumber:3}|Random number; declare the number of digits via options|
|seq|{@seq:3}|Sequential counter, fixed 3 digits|
|prop|{@prop:entity.type,3}|Fetch the variable value from a property of the context object; an optional length field can constrain the returned string length|

## Examples
If you set NopSysCodeRule’s 【Code Pattern codePattern】 to `D{@year}{@month}{@seq:5}`, it may generate D20240912345, where 09 corresponds to September, and 12345 is a sequentially generated serial number with a length of 5.

In the NopSysCodeRule object’s 【Sequence Name seqName】 field, configure the name of the sequence object used by the `{@seq:5}` pattern; it corresponds to the configuration item in NopSysSequence.

## Register Extension Variables

You can define extended coding rule variables in beans.xml. The bean name convention is nopCodeRuleVariable_xxx
<!-- SOURCE_MD5:993678306350dd03222d237c6d96950f-->
