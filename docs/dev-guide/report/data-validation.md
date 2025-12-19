# Excel数据验证

ExcelDataValidationHelper对外暴露的主要函数是`newDataValidation`，根据ISchema和IObjPropMeta创建Excel数据验证规则。

## 基本用法

```java
// 通过IObjPropMeta创建验证
ExcelDataValidation validation = ExcelDataValidationHelper.newDataValidation(propMeta, "A1:B10");

// 直接通过schema创建验证  
ExcelDataValidation validation = ExcelDataValidationHelper.newDataValidation(schema, mandatory, fieldName, "A1:B10");

// 添加到工作表
sheet.addDataValidation(validation);
```

ExcelSheet和ExpandedSheet都实现了`addDataValidation`方法，支持添加数据验证规则。

## 从Schema转换的验证规则

ExcelDataValidationHelper根据Schema定义自动转换验证规则：

- **整数类型**（INT/LONG/SHORT/BYTE）：设置整数范围验证，支持最小值、最大值约束
- **小数类型**（FLOAT/DOUBLE/DECIMAL）：设置小数精度验证，支持精度和小数位控制  
- **日期类型**（DATE）：转换为日期格式验证
- **日期时间类型**（DATETIME/TIMESTAMP）：转换为日期时间格式验证
- **时间类型**（TIME）：转换为时间格式验证
- **字符串类型**（STRING/CHAR）：设置最大长度验证，支持正则表达式
- **字典类型**：转换为下拉列表验证，支持单选和多选
- **布尔类型**（BOOLEAN）：转换为是/否选项列表

## Excel Validation支持的内容

Excel原生验证支持：
- **类型**：整数、小数、日期、时间、文本长度、列表、自定义公式
- **操作符**：介于、不介于、等于、不等于、大于、小于、大于等于、小于等于
- **错误样式**：停止、警告、信息
- **提示信息**：输入提示、错误提示标题和内容

## 特殊处理

ExcelDataValidationHelper做了以下特殊处理：

1. **字典验证**：优先处理字典类型，自动获取DictBean的选项值，使用`setListOptions`设置下拉列表
2. **自定义公式**：支持通过`excel:validationFormula`属性设置自定义验证公式
3. **错误消息国际化**：使用ErrorMessageManager获取国际化错误消息，支持参数替换
4. **必填字段**：根据mandatory属性设置`allowBlank`
5. **类型优先级**：字典验证 > 自定义公式 > 数据类型验证
6. **错误处理**：统一设置错误样式为STOP，显示错误和输入提示
7. **默认值处理**：未指定类型时默认使用文本验证