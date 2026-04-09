# 枚举类和DTO编码规范

## 枚举类规范

### 基本要求

1. **使用 @StaticFactoryMethod 注解**
   - 在 fromValue 方法上添加 `@StaticFactoryMethod` 注解
   - 用于框架自动识别静态工厂方法

2. **使用 VALUE_MAP 缓存优化性能**
   - 定义一个静态的 Map 来缓存枚举实例
   - 在静态代码块中初始化缓存
   - fromValue 方法通过缓存查找实例，避免遍历 values()

### 参考示例

```java
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.commons.util.StringHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * 图表填充类型
 * 对应 OOXML 的填充类型和 POI 的 FillType
 * 用于指定 ChartFillModel 中的填充方式
 */
public enum ChartFillType {
    /**
     * 无填充
     * OOXML: noFill
     */
    NONE("none"),

    /**
     * 纯色填充
     * OOXML: solidFill
     */
    SOLID("solid"),

    /**
     * 渐变填充
     * OOXML: gradFill
     */
    GRADIENT("gradient"),

    /**
     * 图案填充
     * OOXML: pattFill
     */
    PATTERN("pattern"),

    /**
     * 图片填充
     * OOXML: blipFill
     */
    PICTURE("picture");

    private final String value;

    ChartFillType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String toString() {
        return value;
    }

    private static final Map<String, ChartFillType> VALUE_MAP = new HashMap<>();

    static {
        for (ChartFillType type : values()) {
            VALUE_MAP.put(type.value, type);
        }
    }

    @StaticFactoryMethod
    public static ChartFillType fromValue(String value) {
        if (StringHelper.isEmpty(value))
            return null;

        return VALUE_MAP.get(value);
    }

    /**
     * 是否需要颜色信息
     */
    public boolean requiresColor() {
        return this == SOLID || this == GRADIENT || this == PATTERN;
    }

    /**
     * 是否支持透明度
     */
    public boolean supportsOpacity() {
        return this != NONE;
    }
}
```

### xdef 枚举域与 toString() 约定

在 xdef 模型文件中使用 `enum:包名.枚举类名` 声明枚举属性时，框架通过 `EnumDictLoader` 构建字典，字典值取自枚举常量的 `toString()` 返回值（若无 `@Option` 注解覆盖）。

**因此，如果枚举的序列化文本与枚举名（`name()`）不一致，必须覆写 `toString()` 方法，否则 xdef 验证会报 `invalid-enum-value` 错误。**

```java
// ✅ 正确：覆写 toString() 返回协议文本
public enum OfficeHorizontalAlignment implements IOfficeEnumValue {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    private final String excelText;

    OfficeHorizontalAlignment(String text) {
        this.excelText = text;
    }

    @Override
    public String toString() {
        return excelText;  // 返回 "left" 而非 "LEFT"
    }
}

// ❌ 错误：未覆写 toString()，xdef 验证期望 "SINGLE" 但实际值为 "single"
public enum OfficeFontUnderline implements IOfficeEnumValue {
    SINGLE(1, "single");

    private final String excelText;

    OfficeFontUnderline(int value, String text) {
        this.excelText = text;
    }

    // 缺少 toString() 覆写 → toString() 返回 "SINGLE"(enum name)
    // 但序列化时写入 "single"(excelText) → xdef 验证失败
}
```

**关键规则**：
- `EnumDictLoader.buildOptions()` 使用 `fieldValue.toString()` 作为字典的 value
- `EnumStdDomainHandler.parseProp()` 通过 `dict.getOptionByValue(text)` 验证输入值
- 如果枚举有 `@Option` 注解，字典值由 `@Option.value()` 覆盖
- 如果枚举实现了 `IOfficeEnumValue` 且 `toString()` 返回协议文本（如 `"left"`、`"single"`），则 xdef 中写入的值也应使用同样的协议文本

### Office 枚举模式（IOfficeEnumValue）

对于需要与 Excel/Word 等文档格式互操作的枚举，实现 `IOfficeEnumValue` 接口：

```java
public enum OfficeHorizontalAlignment implements IOfficeEnumValue {
    LEFT("left"),
    CENTER("center");

    private final String excelText;
    private final String cssText;
    private final String wmlText;

    OfficeHorizontalAlignment(String text) {
        this.excelText = text;
        this.cssText = text.toLowerCase();
        this.wmlText = text.toLowerCase();
    }

    @Override
    public String toString() {
        return excelText;  // 必须：保证 xdef 字典值与序列化值一致
    }

    @Override
    public String getExcelText() { return excelText; }

    @Override
    public String getCssText() { return cssText; }

    @Override
    public String getWmlText() { return wmlText; }

    private static final OfficeEnumMap<OfficeHorizontalAlignment> MAP = new OfficeEnumMap<>(values());

    @StaticFactoryMethod
    public static OfficeHorizontalAlignment fromExcelText(String text) {
        return MAP.fromExcelText(text);
    }

    public static OfficeHorizontalAlignment fromCssText(String text) {
        return MAP.fromCssText(text);
    }

    public static OfficeHorizontalAlignment fromWmlText(String text) {
        return MAP.fromWmlText(text);
    }
}
```

**要点**：
- 实现 `IOfficeEnumValue` 接口，提供 `getExcelText()`/`getCssText()`/`getWmlText()`
- **必须覆写 `toString()` 返回 `excelText`**，确保 xdef 枚举域验证通过
- 使用 `OfficeEnumMap` 管理文本到枚举的反向映射
- `fromExcelText`/`fromCssText`/`fromWmlText` 提供从不同格式文本反解析的能力
- `fromExcelText` 上标注 `@StaticFactoryMethod`，用于框架自动识别

### @Option 注解

使用 `@Option` 注解可以为枚举常量自定义字典值，覆盖 `toString()` 的返回值：

```java
public enum ChartFillType {
    @Option("none")
    NONE,

    @Option("solid")
    SOLID;

    // EnumDictLoader 会使用 @Option.value() 作为字典值，
    // 无需覆写 toString()
}
```

### 最佳实践

1. **命名规范**
   - 缓存 Map 统一命名为 `VALUE_MAP`
   - 简单枚举的工厂方法统一为 `fromValue`
   - Office 枚举使用 `fromExcelText`/`fromCssText`/`fromWmlText`
   - 获取值的方法根据情况命名为 `value()` 或 `getValue()`

2. **toString() 覆写**
   - 如果枚举的文本表示与 `name()` 不同（如小写文本），**必须**覆写 `toString()`
   - 或者使用 `@Option` 注解指定字典值
   - 否则 xdef 枚举域验证会失败

3. **空值处理**
   - fromValue 方法对空值返回 null（可选）
   - 使用 `StringHelper.isEmpty()` 进行空值判断

4. **注释规范**
   - 为每个枚举值添加 JavaDoc 注释
   - 注释说明枚举值的含义和对应的协议值

## DTO 编码规范

### 基本要求

1. **使用 @DataBean 注解**
   - 在类上添加 `@DataBean` 注解
   - 标识这是一个数据传输对象

2. **属性访问方式**
   - **简单对象（2-3个参数）**：可以使用 final 属性
   - **复杂对象**：需要生成 get/set 方法

3. **使用 @JsonProperty 注解**
   - 对于使用 final 属性的简单对象，构造函数参数需要添加 `@JsonProperty` 注解
   - 确保序列化/反序列化正确工作

### 简单 DTO 示例（final 属性）

```java
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.exceptions.NopException;

/**
 * 单元格位置
 */
@DataBean
public class CellPosition implements Serializable {
    private final int rowIndex;
    private final int colIndex;

    public CellPosition(@JsonProperty("rowIndex") int rowIndex,
                       @JsonProperty("colIndex") int colIndex) {
        if (rowIndex < 0 && colIndex < 0) {
            throw new NopException(ERR_TABLE_INVALID_CELL_POSITION)
                .param(ARG_ROW_INDEX, rowIndex)
                .param(ARG_COL_INDEX, colIndex);
        }

        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
    }

    @StaticFactoryMethod
    public static CellPosition of(int rowIndex, int colIndex) {
        return new CellPosition(rowIndex, colIndex);
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }
}
```

### 复杂 DTO 示例（get/set 方法）

```java
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.beans.ExtensibleBean;

/**
 * 登录请求
 */
@DataBean
public class LoginRequest extends ExtensibleBean {
    private static final long serialVersionUID = -8865201415621170212L;

    private String principalId;
    private String principalSecret;
    private String verifyCode;
    private String verifySecret;
    private boolean rememberMe;
    private String deviceId;
    private String deviceType;
    private String deviceOs;

    @PropMeta(propId = 1)
    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    @PropMeta(propId = 2)
    public String getPrincipalSecret() {
        return principalSecret;
    }

    public void setPrincipalSecret(String principalSecret) {
        this.principalSecret = principalSecret;
    }

    @PropMeta(propId = 3)
    public String getVerifyCode() {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
    }

    @PropMeta(propId = 4)
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    @PropMeta(propId = 5)
    public boolean isRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
}
```

### 最佳实践

1. **继承规范**
   - 复杂 DTO 通常继承 `ExtensibleBean` 以支持扩展属性
   - 简单 DTO 不需要继承

2. **注解使用**
   - 使用 `@PropMeta(propId = N)` 为属性分配唯一序号
   - 使用 `@JsonInclude` 控制序列化行为（如 NON_NULL、NON_EMPTY）
   - 使用 `@JsonIgnore` 排除不需要序列化的属性

3. **命名规范**
   - Boolean 类型属性使用 `is` 前缀（如 `isRememberMe`）
   - 其他属性使用 `get` 前缀（如 `getPrincipalId`）
   - Setter 方法统一使用 `set` 前缀

4. **序列化控制**
   - 可选属性使用 `@JsonInclude(JsonInclude.Include.NON_NULL)`
   - 集合/数组属性使用 `@JsonInclude(JsonInclude.Include.NON_EMPTY)`

## 总结

### 枚举类关键点
- ✅ 使用 @StaticFactoryMethod 注解
- ✅ 定义 VALUE_MAP 缓存
- ✅ fromValue 通过缓存查找
- ✅ 适当的空值处理
- ✅ 如果序列化文本与 name() 不同，必须覆写 toString() 或使用 @Option 注解

### DTO 关键点
- ✅ 使用 @DataBean 注解
- ✅ 简单对象使用 final 属性 + @JsonProperty
- ✅ 复杂对象使用 get/set 方法
- ✅ 继承 ExtensibleBean 支持扩展
- ✅ 使用 @PropMeta 分配序号
