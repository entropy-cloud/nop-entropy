# 新增字典和常量

## 适用场景

- 需要为字段增加枚举 / 字典值。
- 需要补充业务常量。

## AI 决策提示

- 数据库字段枚举优先定义在 ORM 的 `dicts` 中。
- 业务常量放在手写常量类或接口中。
- 不要在 Entity 里手写数据库字典常量。

## 字典值类型选择（重要）

**推荐 `valueType="string"`**，使用语义完整的英文单词作为字典值：

| 维度 | `valueType="int"` | `valueType="string"` |
|------|-------------------|----------------------|
| AI 可读性 | ❌ `status == 2` 无意义 | ✅ `status == "APPROVED"` 自解释 |
| AI 代码生成 | ❌ 需查字典才能生成正确逻辑 | ✅ 直接生成可读代码 |
| SQL 可读性 | ⚠️ `WHERE status = 2` 需注释 | ✅ `WHERE status = 'APPROVED'` |
| 重构友好 | ❌ 插入新值需重新排序 | ✅ 插入新值不影响已有数据 |

**字典值命名规范**：
- 全大写，下划线分隔
- 语义完整的英文单词
- 长度 3-12 字符，不需要固定长度
- 示例：`DRAFT` `APPROVED` `CANCELLED`

## 最小闭环

### 1. 在 ORM 中定义 dict

```xml
<dicts>
    <dict name="order/status" valueType="string">
        <option code="DRAFT" value="DRAFT" label="草稿"/>
        <option code="SUBMITTED" value="SUBMITTED" label="已提交"/>
        <option code="APPROVED" value="APPROVED" label="已审核"/>
        <option code="CANCELLED" value="CANCELLED" label="已作废"/>
    </dict>
</dicts>
```

### 2. 在字段上挂字典

```xml
<column name="status" ext:dict="order/status" stdSqlType="VARCHAR" precision="20" stdDataType="string"/>
```

### 3. 重新生成并使用生成常量

```java
if (_AppErpDaoConstants.ORDER_STATUS_APPROVED.equals(order.getStatus())) {
    // 处理已审核状态
}
```

### 4. 业务常量单独定义

```java
public interface OrderConstants {
    BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("88");
}
```

## 常见字典值参考

| 类别 | 推荐值 |
|------|--------|
| 状态（生命周期） | `DRAFT` `SUBMITTED` `APPROVED` `REJECTED` `CANCELLED` `COMPLETED` `CLOSED` `REVERSED` `VOIDED` |
| 财务状态 | `UNPOSTED` `POSTED` `REVERSED` |
| 收付状态 | `UNPAID` `PARTIAL` `PAID` `UNRECEIVED` `RECEIVED` |
| 方向 | `DEBIT` `CREDIT` |
| 类型 | `PURCHASE` `SALES` `TRANSFER` `ADJUSTMENT` `PRODUCTION` |

## 常见坑

1. 在 Entity 中硬写数据库枚举常量。
2. 把业务常量和字段枚举混在一起。
3. 修改 ORM 后不重新生成。
4. 使用 `valueType="int"` 而非 `valueType="string"`（不推荐）。

## 相关文档

- `./change-model-and-regenerate.md`
- `../02-core-guides/model-first-development.md`
- `../01-repo-map/domain-module-pattern.md`
- `../02-core-guides/orm-model-design.md` §字典设计
