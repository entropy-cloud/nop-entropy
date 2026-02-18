# 示例代码

本目录包含实际的业务代码示例，用于参考和学习 Nop 平台的开发模式。

## BizModel 示例

| 文件 | 说明 | 关键模式 |
|------|------|---------|
| [LitemallCouponBizModel.java](./LitemallCouponBizModel.java) | 优惠券业务模型 | 复杂业务验证、跨聚合调用、Entity 只读方法 |
| [LitemallAftersaleBizModel.java](./LitemallAftersaleBizModel.java) | 售后业务模型 | 批量操作、外部服务调用、状态流转 |

## SQL-Lib 示例

| 文件 | 说明 |
|------|------|
| [LitemallGoodsProduct.sql-lib.xml](./LitemallGoodsProduct.sql-lib.xml) | SQL-Lib 库存更新示例 |

## XDef 示例

参见 [xdefs/](./xdefs/) 目录。

## Processor + Step 示例

Processor 和 Step 的完整示例参见 [bizmodel-guide.md](../03-development-guide/bizmodel-guide.md#复杂逻辑拆分processor--step)，包含：
- `LitemallOrderSubmitProcessor` - 订单提交 Processor
- `InventoryDeductStep` - 库存扣减 Step
- beans.xml 配置示例

## 相关文档

- [BizModel 编写指南](../03-development-guide/bizmodel-guide.md)
- [CRUD 开发指南](../03-development-guide/crud-development.md)
- [ORM SQLLib](../03-development-guide/orm-sqllib.md)
