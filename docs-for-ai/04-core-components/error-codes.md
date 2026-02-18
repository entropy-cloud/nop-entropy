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

---

## Processor 中的错误码定义

Processor 中定义错误码与普通 Errors 接口类略有不同，因为 Processor 不需要接口类。

### 方式一：直接在 Processor 类内定义（推荐）

```java
public class LitemallOrderSubmitProcessor {

    // ===== 错误码定义（private static final）=====
    private static final ErrorCode ERR_ADDRESS_REQUIRED =
            ErrorCode.define("nop.err.mall.order.address-required", "请选择收货地址");

    private static final ErrorCode ERR_ADDRESS_NOT_OWNER =
            ErrorCode.define("nop.err.mall.order.address-not-owner", "收货地址不属于当前用户");

    private static final ErrorCode ERR_CART_EMPTY =
            ErrorCode.define("nop.err.mall.order.cart-empty", "购物车中没有商品");

    private static final ErrorCode ERR_STOCK_INSUFFICIENT =
            ErrorCode.define("nop.err.mall.order.stock-insufficient", "商品[{goodsName}]库存不足，可用库存:[{available}]");

    // ===== 使用 =====
    protected LitemallAddress validateAddress(String addressId, String userId, IServiceContext context) {
        if (StringHelper.isEmpty(addressId)) {
            throw new NopException(ERR_ADDRESS_REQUIRED);
        }
        LitemallAddress address = addressBiz.requireEntity(addressId, "read", context);
        if (!userId.equals(address.getUserId())) {
            throw new NopException(ERR_ADDRESS_NOT_OWNER).param("addressId", addressId);
        }
        return address;
    }

    protected void validateStock(List<LitemallCart> carts, IServiceContext context) {
        for (LitemallCart cart : carts) {
            LitemallGoodsProduct product = productBiz.get(cart.getProductId(), true, context);
            if (product == null) {
                throw new NopException(ERR_PRODUCT_NOT_FOUND)
                        .param("productId", cart.getProductId());
            }
            int stock = product.getNumber() != null ? product.getNumber() : 0;
            int quantity = cart.getNumber() != null ? cart.getNumber() : 0;
            if (stock < quantity) {
                throw new NopException(ERR_STOCK_INSUFFICIENT)
                        .param("goodsName", cart.getGoodsName())
                        .param("available", stock);
            }
        }
    }
}
```

### 方式二：提取到模块级 Errors 接口

如果错误码需要在多个 Processor 间共享，可以定义在模块级 Errors 接口中：

```java
// app-mall-service/src/main/java/app/mall/service/AppMallErrors.java
public interface AppMallErrors {
    // 复用的错误码
    ErrorCode ERR_ENTITY_NOT_FOUND =
            ErrorCode.define("nop.err.mall.entity-not-found", "实体[{entityType}]未找到:[{entityId}]");
    
    ErrorCode ERR_PERMISSION_DENIED =
            ErrorCode.define("nop.err.mall.permission-denied", "无权限访问[{resource}]");
}

// Processor 中使用
public class LitemallOrderSubmitProcessor {
    protected void checkPermission(String userId, IServiceContext context) {
        if (!hasPermission(userId)) {
            throw new NopException(AppMallErrors.ERR_PERMISSION_DENIED)
                    .param("resource", "order");
        }
    }
}
```

### ⚠️ 常见错误

```java
// ❌ 错误：使用 NopException 而不是 ErrorCode 作为常量类型
private static final NopException ERR_ADDRESS_REQUIRED =
    new NopException(ErrorCode.define("nop.err.mall.order.address-required", "..."));

// ✅ 正确：使用 ErrorCode 作为常量类型
private static final ErrorCode ERR_ADDRESS_REQUIRED =
    ErrorCode.define("nop.err.mall.order.address-required", "请选择收货地址");

// 使用时
throw new NopException(ERR_ADDRESS_REQUIRED);
```

---

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