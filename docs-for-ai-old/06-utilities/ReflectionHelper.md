# 反射工具类指南

## ClassHelper - 类工具类

### 概述

`ClassHelper` 提供了类加载、类型判断、包装类型/基本类型转换等核心功能。

包路径：`io.nop.commons.util.ClassHelper`

### 核心功能

#### 1. 类加载

#### forName()

安全加载类。

```java
// 加载类
Class<?> clazz = ClassHelper.forName("java.util.ArrayList");

// 使用指定ClassLoader加载
Class<?> clazz = ClassHelper.forName("com.example.MyClass", classLoader);

// 不抛出异常，加载失败返回null
Class<?> clazz = ClassHelper.safeForName("com.example.MyClass");
```

#### getDefaultClassLoader()

获取默认的ClassLoader。

```java
ClassLoader loader = ClassHelper.getDefaultClassLoader();
```

#### 2. 类型判断

#### isPrimitive()

判断是否为基本类型。

```java
boolean isInt = ClassHelper.isPrimitive(int.class);       // true
boolean isInteger = ClassHelper.isPrimitive(Integer.class);  // false
boolean isString = ClassHelper.isPrimitive(String.class);   // false
```

#### isPrimitiveOrWrapper()

判断是否为基本类型或包装类型。

```java
boolean isInt = ClassHelper.isPrimitiveOrWrapper(int.class);       // true
boolean isInteger = ClassHelper.isPrimitiveOrWrapper(Integer.class); // true
boolean isString = ClassHelper.isPrimitiveOrWrapper(String.class); // false
```

#### isPrimitiveWrapper()

判断是否为包装类型。

```java
boolean isInteger = ClassHelper.isPrimitiveWrapper(Integer.class); // true
boolean isInt = ClassHelper.isPrimitiveWrapper(int.class);       // false
```

#### isArray()

判断是否为数组类型。

```java
boolean isArray = ClassHelper.isArray(String[].class);      // true
boolean isNotArray = ClassHelper.isArray(String.class);    // false
```

#### isCollection()

判断是否为集合类型。

```java
boolean isList = ClassHelper.isCollection(List.class);        // true
boolean isSet = ClassHelper.isCollection(Set.class);          // true
boolean isNotCollection = ClassHelper.isCollection(String.class); // false
```

#### isMap()

判断是否为Map类型。

```java
boolean isMap = ClassHelper.isMap(Map.class);            // true
boolean isNotMap = ClassHelper.isMap(String.class);       // false
```

#### 3. 类型转换

#### getPrimitiveType()

获取包装类型对应的基本类型。

```java
Class<?> primitive = ClassHelper.getPrimitiveType(Integer.class);  // int.class
Class<?> primitive = ClassHelper.getPrimitiveType(Double.class);  // double.class
```

#### getWrapperType()

获取基本类型对应的包装类型。

```java
Class<?> wrapper = ClassHelper.getWrapperType(int.class);     // Integer.class
Class<?> wrapper = ClassHelper.getWrapperType(double.class); // Double.class
```

#### 4. 类实例化

#### newInstance()

通过反射创建实例。

```java
// 创建实例（使用无参构造函数）
MyClass instance = ClassHelper.newInstance(MyClass.class);

// 使用指定Constructor创建
Constructor<MyClass> ctor = MyClass.class.getConstructor(String.class);
MyClass instance = ClassHelper.newInstance(ctor, "param");
```

#### 5. 类名处理

#### getShortClassName()

获取短类名（不含包名）。

```java
String shortName = ClassHelper.getShortClassName("java.util.ArrayList"); // "ArrayList"
String shortName = ClassHelper.getShortClassName(ArrayList.class);       // "ArrayList"
```

#### getPackageName()

获取类所在的包名。

```java
String packageName = ClassHelper.getPackageName("java.util.ArrayList"); // "java.util"
String packageName = ClassHelper.getPackageName(ArrayList.class);       // "java.util"
```

---

## ReflectionHelper - 反射工具类

### 概述

`ReflectionHelper` 提供了反射操作的工具方法，包括方法调用、字段访问、注解处理等。

包路径：`io.nop.commons.util.ReflectionHelper`

### 核心功能

#### 1. 方法操作

#### makeAccessible()

设置方法或字段为可访问。

```java
// 使私有方法可访问
Method method = MyClass.class.getDeclaredMethod("privateMethod");
ReflectionHelper.makeAccessible(method);

// 使私有字段可访问
Field field = MyClass.class.getDeclaredField("privateField");
ReflectionHelper.makeAccessible(field);
```

#### invokeMethod()

通过反射调用方法。

```java
// 调用无参方法
Object result = ReflectionHelper.invokeMethod(object, "methodName");

// 调用带参方法
Object result = ReflectionHelper.invokeMethod(object, "methodName", arg1, arg2);

// 指定参数类型
Object result = ReflectionHelper.invokeMethod(
    object,
    "methodName",
    new Class[]{String.class, int.class},
    new Object[]{"test", 123}
);
```

#### getMethod()

获取方法。

```java
// 获取公开方法
Method method = ReflectionHelper.getMethod(MyClass.class, "methodName");

// 获取指定参数类型的方法
Method method = ReflectionHelper.getMethod(
    MyClass.class,
    "methodName",
    String.class,
    int.class
);

// 获取所有方法（包括私有）
Method[] methods = ReflectionHelper.getAllDeclaredMethods(MyClass.class);
```

#### isEqualsMethod() / isHashCodeMethod() / isToStringMethod()

判断是否为Object标准方法。

```java
boolean isEquals = ReflectionHelper.isEqualsMethod(method);
boolean isHashCode = ReflectionHelper.isHashCodeMethod(method);
boolean isToString = ReflectionHelper.isToStringMethod(method);
```

#### 2. 字段操作

#### getField()

获取字段。

```java
// 获取公开字段
Field field = ReflectionHelper.getField(MyClass.class, "fieldName");

// 获取字段（包括私有）
Field field = ReflectionHelper.getDeclaredField(MyClass.class, "fieldName");
```

#### setFieldValue() / getFieldValue()

设置和获取字段值。

```java
// 设置字段值
ReflectionHelper.setFieldValue(object, "fieldName", value);

// 获取字段值
Object value = ReflectionHelper.getFieldValue(object, "fieldName");

// 处理静态字段
ReflectionHelper.setFieldValue(MyClass.class, "staticFieldName", value);
Object value = ReflectionHelper.getFieldValue(MyClass.class, "staticFieldName");
```

#### 3. 构造函数操作

#### getConstructor()

获取构造函数。

```java
// 获取无参构造函数
Constructor<MyClass> ctor = ReflectionHelper.getConstructor(MyClass.class);

// 获取带参构造函数
Constructor<MyClass> ctor = ReflectionHelper.getConstructor(
    MyClass.class,
    String.class,
    int.class
);
```

#### 4. 注解操作

#### findAnnotation()

查找类或方法上的注解。

```java
// 查找类上的注解
MyAnnotation anno = ReflectionHelper.findAnnotation(
    MyClass.class,
    MyAnnotation.class
);

// 查找方法上的注解
Method method = MyClass.class.getMethod("myMethod");
MyAnnotation anno = ReflectionHelper.findAnnotation(
    method,
    MyAnnotation.class
);

// 查找字段上的注解
Field field = MyClass.class.getField("myField");
MyAnnotation anno = ReflectionHelper.findAnnotation(
    field,
    MyAnnotation.class
);
```

#### isAnnotationPresent()

判断注解是否存在。

```java
boolean hasAnnotation = ReflectionHelper.isAnnotationPresent(
    MyClass.class,
    MyAnnotation.class
);
```

#### 5. 方法参数名获取

#### getMethodParameterNames()

获取方法的参数名。

```java
Method method = MyClass.class.getMethod("myMethod", String.class, int.class);
String[] paramNames = ReflectionHelper.getMethodParameterNames(method);
// paramNames = ["str", "num"]
```

## 实用示例

### 示例1：动态调用方法

```java
public Object invokeByMethodName(Object target, String methodName, Object... args) {
    try {
        return ReflectionHelper.invokeMethod(target, methodName, args);
    } catch (Exception e) {
        throw new NopException(ERR_INVOKE_METHOD_FAIL)
            .param("method", methodName)
            .param("target", target.getClass().getName())
            .cause(e);
    }
}
```

### 示例2：获取所有字段值

```java
public Map<String, Object> getAllFieldValues(Object obj) {
    Map<String, Object> result = new HashMap<>();

    Class<?> clazz = obj.getClass();
    while (clazz != null && clazz != Object.class) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            ReflectionHelper.makeAccessible(field);
            try {
                Object value = ReflectionHelper.getFieldValue(obj, field.getName());
                result.put(field.getName(), value);
            } catch (Exception e) {
                log.warn("Failed to get field: {}", field.getName(), e);
            }
        }
        clazz = clazz.getSuperclass();
    }

    return result;
}
```

### 示例3：安全类型检查

```java
public void validateType(Object obj, Class<?> expectedType) {
    Class<?> actualType = obj.getClass();

    if (actualType != expectedType &&
        !expectedType.isAssignableFrom(actualType)) {
        throw new NopException(ERR_INVALID_TYPE)
            .param("expected", expectedType.getName())
            .param("actual", actualType.getName());
    }

    // 检查基本类型和包装类型
    if (ClassHelper.isPrimitive(expectedType) &&
        ClassHelper.getWrapperType(expectedType) == actualType) {
        // OK, 包装类型可以匹配基本类型
        return;
    }
}
```

### 示例4：动态创建实例

```java
public <T> T createInstance(String className, Class<T> expectedType) {
    try {
        Class<?> clazz = ClassHelper.forName(className);

        if (!expectedType.isAssignableFrom(clazz)) {
            throw new NopException(ERR_CLASS_NOT_EXPECTED_TYPE)
                .param("className", className)
                .param("expectedType", expectedType.getName());
        }

        return (T) ClassHelper.newInstance(clazz);
    } catch (Exception e) {
        throw new NopException(ERR_CREATE_INSTANCE_FAIL)
            .param("className", className)
            .cause(e);
    }
}

// 使用
User user = createInstance("com.example.UserImpl", User.class);
```

### 示例5：查找带特定注解的方法

```java
public List<Method> findAnnotatedMethods(
    Class<?> clazz,
    Class<? extends Annotation> annotationClass) {

    List<Method> result = new ArrayList<>();

    for (Method method : clazz.getDeclaredMethods()) {
        if (ReflectionHelper.findAnnotation(method, annotationClass) != null) {
            ReflectionHelper.makeAccessible(method);
            result.add(method);
        }
    }

    return result;
}

// 使用：查找所有带@BizQuery注解的方法
List<Method> queryMethods = findAnnotatedMethods(
    MyService.class,
    BizQuery.class
);
```

## 性能建议

### 1. 缓存反射结果

反射操作开销较大，应缓存反射结果。

```java
// ✅ 推荐：缓存Method对象
private static final Method CACHE_METHOD;

static {
    try {
        CACHE_METHOD = MyClass.class.getMethod("methodName");
        ReflectionHelper.makeAccessible(CACHE_METHOD);
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}

// 使用缓存的方法
Object result = ReflectionHelper.invokeMethod(target, CACHE_METHOD);

// ❌ 不推荐：每次都查找方法
Object result = ReflectionHelper.invokeMethod(target, "methodName");
```

### 2. 优先使用编译时类型检查

在可以使用编译时检查的情况下，避免使用反射。

```java
// ✅ 推荐：直接调用
myObject.myMethod();

// ❌ 不推荐：反射调用
ReflectionHelper.invokeMethod(myObject, "myMethod");
```

### 3. 反射异常处理

反射操作可能抛出多种异常，应统一处理。

```java
public Object safeInvoke(Object target, String methodName, Object... args) {
    try {
        return ReflectionHelper.invokeMethod(target, methodName, args);
    } catch (NoSuchMethodException e) {
        throw new NopException(ERR_METHOD_NOT_FOUND)
            .param("method", methodName)
            .cause(e);
    } catch (IllegalAccessException e) {
        throw new NopException(ERR_METHOD_NOT_ACCESSIBLE)
            .param("method", methodName)
            .cause(e);
    } catch (InvocationTargetException e) {
        throw new NopException(ERR_METHOD_INVOCATION_FAILED)
            .param("method", methodName)
            .cause(e.getTargetException());
    }
}
```

## 常见问题

### Q1: ClassHelper.forName() 与 Class.forName() 有什么区别？

`ClassHelper.forName()` 提供了更好的错误处理和ClassLoader管理：

```java
// ❌ Class.forName() 可能抛出ClassNotFoundException
try {
    Class<?> clazz = Class.forName("com.example.MyClass");
} catch (ClassNotFoundException e) {
    // 处理异常
}

// ✅ ClassHelper.safeForName() 返回null
Class<?> clazz = ClassHelper.safeForName("com.example.MyClass");
if (clazz == null) {
    // 处理类不存在
}
```

### Q2: 如何调用私有方法？

```java
MyClass obj = new MyClass();

// 获取私有方法
Method method = MyClass.class.getDeclaredMethod("privateMethod");

// 设置可访问
ReflectionHelper.makeAccessible(method);

// 调用方法
Object result = method.invoke(obj);
```

### Q3: 如何获取父类的所有字段？

```java
public List<Field> getAllFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();

    while (clazz != null && clazz != Object.class) {
        for (Field field : clazz.getDeclaredFields()) {
            fields.add(field);
        }
        clazz = clazz.getSuperclass();
    }

    return fields;
}
```

### Q4: 如何判断一个类型是否为数字类型？

```java
public boolean isNumericType(Class<?> type) {
    if (ClassHelper.isPrimitive(type)) {
        return type == int.class || type == long.class ||
               type == double.class || type == float.class ||
               type == short.class || type == byte.class;
    } else if (ClassHelper.isPrimitiveWrapper(type)) {
        return type == Integer.class || type == Long.class ||
               type == Double.class || type == Float.class ||
               type == Short.class || type == Byte.class ||
               type == BigDecimal.class || type == BigInteger.class;
    }
    return false;
}
```

## 相关文档

- [BeanTool](./BeanTool.md)
- [CollectionHelper](./CollectionHelper.md)
- [ConvertHelper](./ConvertHelper.md)
