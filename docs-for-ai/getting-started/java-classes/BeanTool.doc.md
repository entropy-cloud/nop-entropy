# BeanTool 使用指南

## 概述

BeanTool是Nop平台提供的反射和Bean操作工具类，用于安全、高效地访问和操作Bean属性，替代直接反射调用。它通过缓存机制优化性能，提供类型安全检查和自动转换功能。

## 核心功能

### 1. Bean属性访问
- `getProperty(bean, propName)`：获取Bean属性值
- `setProperty(bean, propName, value)`：设置Bean属性值
- `makeProperty(bean, propName, constructor)`：获取或创建属性值，支持makeXX机制
- `hasProperty(bean, propName)`：检查属性是否存在
- `tryGetProperty(bean, propName)`：安全获取Bean属性值。如果属性不存在，则返回null

### 2. 复杂属性路径
- `getComplexProperty(bean, propPath)`：访问复杂属性路径（如"a.b.c"）
- `setComplexProperty(bean, propPath, value)`：设置复杂属性路径，支持makeXX机制
- `makeComplexProperty(bean, propPath, constructor)`：获取或创建复杂属性路径，支持makeXX机制
- `hasComplexProperty(bean, propPath)`：检查复杂属性路径是否存在

### 3. makeXX机制

Nop平台在标准的getXX/setXX方法基础上，补充了可选的makeXX机制，用于更便捷地创建和访问嵌套属性。

#### 什么是make

`make`方法是一种工厂方法约定，用于获取或创建属性对象。当调用`BeanTool.makeProperty(bean, "propName", constructor)`或`BeanTool.setComplexProperty(bean, "a.b.c", value)`时，系统会尝试：

1. **优先查找并调用对应的`makePropName()`方法**，如果存在则直接使用其返回值
2. **如果`makePropName()`方法不存在**，则回退到使用`getPropName()`方法获取
3. **如果`getPropName()`返回`null`且提供了`constructor`**（仅用于最后一级对象创建），则使用该构造函数创建新对象
4. **对于中间级对象**，如果获取到`null`，系统会抛出异常`nop.err.core.reflect.set-prop-fail`

**关键点**：`constructor`参数是一个`java.util.function.Supplier`，仅在最后一级属性为null时被调用。

#### 示例说明

```java
import io.nop.core.reflect.bean.BeanTool;
import java.util.function.Supplier;

// 当执行以下代码时
BeanTool.setComplexProperty(bean, "a.b.c", "value");

// 系统会尝试按以下顺序执行
// 1. 优先：bean.makeA().makeB().setC("value");
// 2. 备选：bean.makeA().getB().setC("value");

// 使用constructor参数的示例
Supplier<Address> addressSupplier = () -> new Address();
Address addr = (Address) BeanTool.makeProperty(user, "address", addressSupplier);
// 等价于：
// if (user.getAddress() == null) {
//     user.setAddress(new Address());
// }
// return user.getAddress();
```

#### 核心方法说明

- `makeProperty(bean, propName, constructor)`：获取或创建单个属性，支持makeXX机制
  - 优先调用makeXX()，如果不存在则使用getXX()
  - 如果getXX()返回null，且constructor不为null，则调用constructor.get()创建对象

- `makeComplexProperty(bean, propPath, constructor)`：获取或创建复杂属性路径
  - 自动处理嵌套属性的创建
  - **仅在最后一级属性**使用constructor创建对象
  - 中间级对象如果为null会抛出NopException，异常码是`nop.err.core.reflect.set-prop-fail`

- `setComplexProperty(bean, propPath, value)`：设置复杂属性值，支持makeXX机制
  - 自动处理中间级对象的创建
  - 支持通过makeXX()方法链式调用创建嵌套对象

### 4. Bean复制与转换
- `copyBean(src, dest, targetType, deep)`：复制Bean属性，自动执行类型转换。targetType可以是Class或者泛型Type。
- `copyProperties(src, dest)`：相当于`copyBean(src, dest, dest.getClass(), false)`
- `pluckSelected(bean, fieldSelection)`：类似GraphQL的字段选择，从Bean对象上获取指定字段所构成的JSON对象
- `buildBean(src, targetType)`：根据源对象创建指定类型的目标对象，深度嵌套处理。
- `castBean(src, targetType)`：将对象转换为指定类型，如果类型相同则直接返回，否则会复制新建目标对象，然后递归cast。

#### buildBean vs castBean 对比
| 方法 | 行为 | 使用场景 |
|------|------|----------|
| `buildBean` | 总是创建新对象，深度复制所有属性 | 需要完全独立的副本 |
| `castBean` | 类型相同则返回原对象，否则创建新对象 | 类型安全转换，优化性能 |

## 示例代码

```java
import io.nop.core.reflect.bean.BeanTool;

// Bean定义
class User {
    private String name;
    private Address address;

    // 省略getter/setter方法

    // makeXX方法示例
    public Address makeAddress() {
        if (address == null) {
            address = new Address();
        }
        return address;
    }
}

class Address {
    private String city;
    // 省略getter/setter方法
}

// 1. 基本属性访问
User user = new User();
user.setName("test");
String name = (String) BeanTool.getProperty(user, "name"); // "test"
BeanTool.setProperty(user, "name", "newName"); // user.name = "newName"

// 2. 复杂属性路径访问
Address addr = new Address();
addr.setCity("Beijing");
user.setAddress(addr);
String city = (String) BeanTool.getComplexProperty(user, "address.city"); // "Beijing"

// 3. makeXX机制使用
// 设置嵌套属性，自动调用makeAddress()创建Address对象
BeanTool.setComplexProperty(user, "address.city", "Shanghai");

BeanTool.tryGetComplexProperty(user,"address.invalidProp"); // null，不会抛出异常

// 4. 使用constructor参数
Supplier<Address> addrSupplier = () -> {
    Address a = new Address();
    a.setCity("Guangzhou");
    return a;
};
Address createdAddr = (Address) BeanTool.makeProperty(user, "address", addrSupplier);

// 5. Bean复制
Map<String, Object> map = new HashMap<>();
map.put("name", "John");
map.put("address", Collections.singletonMap("city", "Shenzhen"));

User user2 = new User();
BeanTool.copyBean(map, user2, user2.getClass(), true); // 深度复制所有属性
BeanTool.copyProperties(user, user2); // 浅复制属性

// 6. Bean转换
User user3 = (User) BeanTool.buildBean(map, User.class); // 总是创建新对象
User user4 = (User) BeanTool.castBean(map, User.class);  // 类型转换
User user5 = (User) BeanTool.castBean(user, User.class); // 类型相同，返回原对象

// 7. 错误处理示例
try {
    BeanTool.setComplexProperty(user, "nonexistent.property", "value");
} catch (Exception e) {
    // 会抛出NopException: nop.err.core.reflect.set-prop-fail
    System.out.println("设置属性失败: " + e.getMessage());
}

// 8. 安全访问
Object result = BeanTool.tryGetProperty(user, "nonexistent");
System.out.println(result); // null，不会抛出异常
```

## 性能说明

BeanTool通过缓存BeanModel反射模型来优化性能，优于Spring的BeanWrapperImpl调用。

## 最佳实践

1. **优先使用**：所有Bean操作优先使用BeanTool，替代直接反射调用
2. **复杂路径**：使用`getComplexProperty`/`setComplexProperty`访问嵌套属性
3. **类型安全**：利用`castBean`进行类型安全转换
4. **错误处理**：对可能不存在的属性访问使用`tryGetProperty`

## 注意事项

1. **线程安全**：所有方法都是线程安全的
2. **异常处理**：转换失败会抛出NopException，包含详细的错误信息
3. **空值处理**：中间级对象为null时，`setComplexProperty`会抛出异常
4. **属性不存在**：`getProperty`在属性不存在时抛出异常，`tryGetProperty`返回null
5. **makeXX方法可选**：不是必须实现makeXX方法，但有此方法时会优先使用
6. **循环引用**：深度复制时会自动处理循环引用问题

