# 新增字典和常量

## 适用场景

- 你要为字段增加枚举/字典值
- 你要补充业务常量

## AI 决策提示

- ✅ 数据库字段枚举优先定义在 ORM `dicts` 中
- ✅ 业务常量单独放常量类
- ❌ 不要在 Entity 里手写数据库字典常量

## 最小闭环

### 1. 在 ORM 中定义 dict

```xml
<dicts>
    <dict name="order/status" valueType="int">
        <option code="PENDING" value="101"/>
        <option code="CANCELLED" value="102"/>
        <option code="PAID" value="201"/>
    </dict>
</dicts>
```

### 2. 在字段上挂字典

```xml
<column name="orderStatus" ext:dict="order/status"/>
```

### 3. 重新生成后使用生成常量

```java
if (order.getOrderStatus() == ORDER_STATUS_PAID) {
    // ...
}
```

### 4. 业务常量单独定义

```java
public interface OrderConstants {
    BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("88");
}
```

## 常见坑

- ❌ 在 Entity 中硬写数据库枚举常量
- ❌ 把业务常量和字段枚举混在一起
- ❌ 修改 ORM 后不重新生成

## 相关文档

- `03-development-guide/bizmodel-guide.md`
- `12-tasks/change-model-and-regenerate.md`
