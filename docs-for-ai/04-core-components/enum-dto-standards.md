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

### 最佳实践

1. **命名规范**
   - 缓存 Map 统一命名为 `VALUE_MAP`
   - 方法名统一为 `fromValue`
   - 获取值的方法根据情况命名为 `value()` 或 `getValue()`

2. **空值处理**
   - fromValue 方法对空值返回 null（可选）
   - 使用 `StringHelper.isEmpty()` 进行空值判断

3. **注释规范**
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

### DTO 关键点
- ✅ 使用 @DataBean 注解
- ✅ 简单对象使用 final 属性 + @JsonProperty
- ✅ 复杂对象使用 get/set 方法
- ✅ 继承 ExtensibleBean 支持扩展
- ✅ 使用 @PropMeta 分配序号
