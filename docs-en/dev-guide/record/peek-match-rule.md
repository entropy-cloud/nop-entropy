# PeekMatchRule DSL Syntax Documentation

## 1. Overview

PeekMatchRule is a domain-specific language (DSL) used to define pattern matching rules for binary data. This language allows users to describe complex data matching conditions with simple syntax and return corresponding type identifiers.

## 2. Basic Syntax Structure

### 2.1 Basic Matching Rule

```
GET(<offset>,<length>) <operator> <value> => <result type>
```

Examples:

```
GET(0,4) == 'HEAD' => FILE_HEADER
GET(16,8) == 0x89504E470D0A1A0A => PNG_FILE
```

### 2.2 Complete Syntax Elements

| Element   | Description                          | Example                   |
|-----------|--------------------------------------|---------------------------|
| `GET`     | Keyword, retrieves a specified part from the data | `GET(0,4)`                |
| `offset`  | Start position in the data (bytes)   | `0` indicates from the beginning |
| `length`  | Length of the data to match (bytes)  | `4` indicates 4 bytes     |
| `operator`| Comparison operator                   | `==` (only equality currently supported) |
| `value`   | Value to match, can be a string or hexadecimal | `'HEAD'` or `0x89ABCDEF` |
| `=>`      | Result mapping operator               |                           |
| `result type` | Type identifier returned upon a successful match | `FILE_HEADER`        |

## 3. Composite Condition Syntax

### 3.1 Logical Operators

| Operator | Description | Precedence |
|---------|-------------|------------|
| `and`   | Logical AND | High       |
| `or`    | Logical OR  | Low        |
| `!`     | Logical NOT | Highest    |

### 3.2 Composite Condition Examples

```
GET(0,4) == 'HEAD' and GET(4,4) == 0x01020304 => TYPE_A
GET(0,2) == 0xFFD8 or GET(0,4) == 0x89504E47 => IMAGE_FILE
!(GET(0,8) == 'TRASHBIN') and GET(8,4) == 0x00000001 => VALID_FILE
```

### 3.3 Grouping Conditions

Use parentheses `()` to clarify operator precedence:

```
(GET(0,2) == 'AB' or GET(0,2) == 'CD') and GET(2,2) == 'EF' => TYPE_SPECIAL
```

## 4. Multiple Rule Definitions

Multiple rules can be separated by newline characters:

```
GET(0,8) == 'FILE_HDR' => HEADER
GET(8,8) == 'DATA_BLK' => DATA_BLOCK
GET(16,8) == 'FILE_TRL' => TRAILER
```

## 5. Value Types

### 5.1 String Values

- Enclosed in single quotes `'`
- Case-sensitive
- Examples: `'HEADER'`, `'File'`

### 5.2 Hexadecimal Values

- Prefixed with `0x` or `0X`
- Must consist of an even number of hexadecimal characters
- Examples: `0x89ABCDEF`, `0XFFD8`

## 6. Usage Examples

### 6.1 File Type Identification

```
GET(0,4) == '%PDF' => PDF_DOCUMENT
GET(0,4) == 0x89504E47 => PNG_IMAGE
GET(0,2) == 0xFFD8 => JPEG_IMAGE
GET(0,8) == 'SQLite f' => SQLITE_DB
```

### 6.2 Protocol Message Identification

```
GET(0,1) == 0x01 and GET(1,2) == 0x0001 => HELLO_MSG
GET(0,1) == 0x02 and GET(1,4) == 0x0000000C => DATA_MSG
GET(0,1) == 0xFF => ERROR_MSG
```

### 6.3 Complex Condition Matching

```
(GET(0,4) == 'RIFF' and GET(8,4) == 'WAVE') => WAV_FILE
(GET(0,4) == 'RIFF' and GET(8,4) == 'AVI ') => AVI_FILE
!(GET(0,3) == 0xEFBBBF) and GET(0,4) == '<?xm' => XML_FILE
```

## 7. Default Rule (Wildcard)

Use `*` as a wildcard to define a default matching rule, typically placed at the end:

```
GET(0,8) == 'FILE_HDR' => HEADER
GET(8,8) == 'DATA_BLK' => DATA_BLOCK
* => UNKNOWN_TYPE
```

When none of the preceding rules match, the wildcard rule is automatically matched and `UNKNOWN_TYPE` is returned.

## 8. Notes

1. Offsets and lengths must be positive integers
2. String comparison is exact and case-sensitive
3. Hexadecimal values must represent complete bytes (an even number of hexadecimal characters)
4. Rules are evaluated in the order they are defined; the first successful match is returned
5. Use parentheses to clarify the evaluation order in complex conditions

## 9. Best Practices

1. Place the most likely matches first
2. For data with fixed formats, match magic numbers first
3. Complex matching conditions can be broken down into multiple simple rules
4. Use comments to explain the purpose of complex rules (although the DSL itself does not support comments, you can add descriptions at the top of the rules file)
<!-- SOURCE_MD5:69f89beb9f05e9e9d658676c17cf7d3d-->
