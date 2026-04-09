# 核心错误码参考

本页是平台内置错误码类别的参考页，不定义 BizModel 默认写法。

如果你在写业务代码，请优先看：

- `./exception-handling.md`
- `./error-codes.md`
- `../12-tasks/error-codes-and-nop-exception.md`

---

## 一、常见错误码来源

| 类别 | 参考位置 |
|------|---------|
| API 通用错误 | `io.nop.api.core.ApiErrors` |
| DAO 通用错误 | `io.nop.dao.DaoErrors` |
| 通用基础错误 | `io.nop.commons.CommonErrors` |

---

## 二、使用原则

1. 优先复用已有错误码
2. 业务域特有错误放到模块自己的 `*Errors` 接口
3. 通过 `NopException + .param(...)` 传递上下文
4. 不要在错误码示例里混入不安全的数据访问模板

---

## 三、示例

```java
throw new NopException(ApiErrors.ERR_CHECK_INVALID_ARGUMENT)
    .param("paramName", "userId")
    .param("paramValue", userId);
```

```java
throw new NopException(DaoErrors.ERR_DAO_UNKNOWN_ENTITY)
    .param("entityName", "Order")
    .param("entityId", orderId);
```

---

## 四、相关文档

- `./exception-handling.md`
- `./error-codes.md`
- `../12-tasks/error-codes-and-nop-exception.md`
