# Coding Rules

In business systems, it's common to generate business codes automatically according to certain coding rules, such as generating order numbers or card numbers.

## Built-In CodeRule

1. Add a `code` tag to fields in the model
2. During code generation, the system will automatically add a `biz:codeRule="objectName@attributeName"` configuration to the field's metadata. This can be overridden in the derived metadata.
3. Use the `nop-sys-dao` module to add corresponding records to the `nop_sys_code_rule` table and configure the coding template
4. When saving or modifying an entity, it will trigger `autoExpr` in the metadata based on the `biz:codeRule` configuration

## Built-In CodeTemplate

The `ICodeRule` interface supports defining a set of variable patterns for encoding templates, then parsing the coding template and replacing variables to generate encoded strings.

The coding template uses the format `{@type:options}` to represent variables. For example, `D{@year}{@month}{@seq:5}` represents:

- Prefix: D
- 4-digit year
- 2-digit month
- 5-digit incremental sequence number

If the maximum value is reached, it will roll over.

| Field | Pattern | Description |
|-------|---------|-------------|
| Year | `{@year}` | Year |
| Month | `{@month}` | Month (fixed 2 digits) |
| DayOfMonth | `{@dayOfMonth}` | Day of month (1-31) |
| Hour | `{@hour}` | Hour (fixed 2 digits) |
| Minute | `{@minute}` | Minute (fixed 2 digits) |
| Second | `{@second}` | Second (fixed 2 digits) |
| RandNumber | `{@randNumber:3}` | Random number generated based on options, e.g., 3 digits |
| Seq | `{@seq:3}` | Incrementing sequence number, fixed to 3 digits |
| Prop | `{@prop:entity.type,3}` | Represents a variable value from the context object's property. The length can be specified in the options.

## Example
When configuring `NopSysCodeRule` with the coding pattern `{@type:options}` as `D{@year}{@month}{@seq:5}`, it may generate a string like "D20240912345", where "09" corresponds to September and "12345" is a 5-digit incremental sequence number.

For the `NopSysCodeRule` object's `seqName` field, configure `{@seq:5}` as the pattern for the sequence name, which refers to the configuration in `NopSysSequence`.

## Register Extended Variables

Extended coding rule variables can be defined in `beans.xml`. The bean's name should follow the format `nopCodeRuleVariable_{xxx}`.
