# ErrorCode定义规范

## 概述

Nop平台使用统一的ErrorCode机制管理错误信息，避免创建自定义异常类。所有错误通过NopException抛出，使用ErrorCode定义错误信息。

## 核心类

### NopException
- 统一异常类，继承自RuntimeException
- 支持错误码、参数
- 使用方式：`throw new NopException(errorCode).param("arg", value);`

### ErrorCode
- 定义标准化错误码
- 包含：错误码字符串、描述模板、参数名数组
- 创建方式：`ErrorCode.define(errorCode, description, argNameA, argNameB, ...);`

## 定义规范

### 1. 错误码格式
```
nop.err.[模块].[子模块/功能].[具体错误]
```

**示例**：`nop.err.excel.import.unknown-sheet`


### 2. 错误描述模板
- 使用`[{参数名}]`作为占位符
- 简明扼要，清晰表达错误原因

**示例**：
- `"无效字段:[{fieldName}]，允许:[{allowedFields}]""`

### 3. 常量命名
- 参数名：`ARG_[参数名]`，如`ARG_SHEET_NAME`
- 错误码：`ERR_[描述性名称]`，如`ERR_IMPORT_UNKNOWN_SHEET`

## 异常包装处理

Nop平台不使用CheckedException， 对于抛出IOException之类的CheckedException的函数调用，一般需要捕获异常，并包装为NopException。

```java
try {
    output.write(xxx);
} catch (IOException e) {
    throw NopException.adapt(e);
}
```

如果需要额外传递详细的错误信息，则可以捕获异常作为cause

示例：

```java
try {
    someIOOperation();
} catch (IOException e) {
    throw new NopException(ERR_XXX, e)
            .param("xxx", xxx);
}
```

## 示例代码

### 定义ErrorCode接口类，用于集中存放错误码
```java
public interface ExcelErrors {
    // 参数名定义
    String ARG_SHEET_NAME = "sheetName";
    String ARG_ALLOWED_NAMES = "allowedNames";
    
    // 错误码定义
    ErrorCode ERR_IMPORT_UNKNOWN_SHEET = ErrorCode.define(
            "nop.err.excel.import.unknown-sheet",
            "未定义的Excel表格:[{sheetName}],允许的名称为[{allowedNames}]",
            ARG_SHEET_NAME, ARG_ALLOWED_NAMES);
}
```

### 使用ErrorCode
```java
// 抛出异常
throw new NopException(ExcelErrors.ERR_IMPORT_UNKNOWN_SHEET)
        .param(ExcelErrors.ARG_SHEET_NAME, sheetName)
        .param(ExcelErrors.ARG_ALLOWED_NAMES, allowedNames);
```

控制选项：

```java
throw new NopException(errorCode)
    .loc(sourceLocation)    // 记录错误发生位置
    .param("arg1", value1)        // 添加参数
    .param("arg2", value2)
    .httpStatus(400)             // 设置HTTP状态码
    .bizFatal(true)              // 标记为业务致命错误, 不允许TCC分布式事务重试
    .notRollback(false);         // 控制事务回滚。缺省情况下ITransctionTemplate的模板方法捕获异常时会自动回滚事务。
```

## 国际化
i18n文件：`_vfs/i18n/{locale}/xxx.i18n.yaml`
```yaml
nop.err.excel.import.unknown-sheet: "未定义表格:[{sheetName}]"
```

## 最佳实践
1. 按模块定义Errors接口类
2. 所有错误码采用固定前缀，Nop平台内置错误码以`nop.err.`开头
3. 相同参数使用统一名称
4. 避免创建自定义异常类
5. 错误描述便于国际化翻译

## 注意事项
- 错误码保持唯一性
- 描述提供足够上下文
- 定期清理废弃错误码
- 异常构造避免重计算