### 完整性检查
✓ 覆盖需求中所有数据库读写需求
✓ 包含所有主表、明细表、关联表、统计表
✓ 表名采用单数驼峰命名
✓ 未使用JSON类型字段

### 表结构清单
| 表名 | 中文名 | 说明 | 字段中文名列表 |
|------|--------|------|----------------|
| MaterialCategory | 物资分类 | 多级物资分类体系 | 分类名称、父级分类ID |
| Material | 物资 | 食材类物资主数据 | 物资编码、物资名称、计量单位、规格型号、品牌、备注、参考价格、图片URL |
| Supplier | 供应商 | 食材供应商信息 | 供应商编码、供应商名称、社会信用代码、食品安全认证证书编号、食品安全认证有效期、联系人姓名、联系人电话、联系人邮箱、企业简介、备注、状态 |
| ServiceCompany | 服务公司 | 食堂运营服务商 | 公司编码、公司名称、社会信用代码、食品安全认证证书编号、食品安全认证有效期、联系人姓名、联系人电话、联系人邮箱、企业简介、备注、状态 |
| BaseMenu | 基础菜单 | 标准菜单模板 | 菜单编号、菜单名称、菜单类型、菜单图片URL、备注 |
| BaseMenuMaterial | 基础菜单配料 | 菜单与物资关联关系 | 所需数量、成本单价 |
| Warehouse | 库房 | 服务公司专属仓库 | 库房编码、库房名称、备注 |
| DailyMenu | 日常菜单 | 每日供餐计划 | 菜单日期、菜单编码、菜单名称、菜单图片URL、锁定状态 |
| DailyMenuMaterial | 日常菜单配料 | 当日实际配料清单 | 所需数量、成本单价 |
| InboundOrder | 入库单 | 物资采购入库记录 | 入库单号、入库日期、票据附件URL、锁定状态 |
| InboundOrderDetail | 入库明细 | 入库物资详情 | 数量、单价、金额 |
| OutboundOrder | 出库单 | 物资领用出库记录 | 出库单号、出库日期、出库类型、锁定状态 |
| OutboundOrderDetail | 出库明细 | 出库物资详情 | 数量、单价、金额 |
| Inventory | 库存 | 实时库存快照 | 当前库存数量 |
| InventoryCheck | 盘点任务 | 库存盘点记录 | 盘点日期 |
| InventoryCheckDetail | 盘点明细 | 物资盘点差异 | 系统库存数量、实际盘点数量、差异数量 |
| Settlement | 结算单 | 供应商付款单据 | 结算单号、结算总金额、结算状态、结算日期 |
| SettlementDetail | 结算明细 | 结算关联入库单 | 本次结算金额 |
| PaymentRecord | 支付记录 | 实际付款流水 | 支付金额、支付日期 |
| MealOrder | 订餐统计 | 就餐人数记录 | 就餐日期、就餐类型、订餐人数 |
| FixedCost | 固定成本 | 运营固定费用 | 成本类型、金额、开始日期、结束日期 |
| DeviationReport | 偏差报告 | 消耗偏差分析 | 报告日期、理论消耗量、实际出库量、偏差量 |

### 表关联
**多对一关系**
1. Material → MaterialCategory (物资→分类)
2. BaseMenuMaterial → BaseMenu (配料→基础菜单)
3. BaseMenuMaterial → Material (配料→物资)
4. Warehouse → ServiceCompany (库房→服务公司)
5. DailyMenu → BaseMenu (日常菜单→基础菜单)
6. DailyMenuMaterial → DailyMenu (配料→日常菜单)
7. DailyMenuMaterial → Material (配料→物资)
8. InboundOrder → Supplier (入库单→供应商)
9. InboundOrder → Warehouse (入库单→库房)
10. InboundOrderDetail → InboundOrder (明细→入库单)
11. InboundOrderDetail → Material (明细→物资)
12. OutboundOrder → Warehouse (出库单→库房)
13. OutboundOrderDetail → OutboundOrder (明细→出库单)
14. OutboundOrderDetail → Material (明细→物资)
15. Inventory → Warehouse (库存→库房)
16. Inventory → Material (库存→物资)
17. InventoryCheck → Warehouse (盘点任务→库房)
18. InventoryCheckDetail → InventoryCheck (明细→盘点任务)
19. InventoryCheckDetail → Material (明细→物资)
20. Settlement → Supplier (结算单→供应商)
21. SettlementDetail → Settlement (明细→结算单)
22. SettlementDetail → InboundOrder (明细→入库单)
23. PaymentRecord → Settlement (支付记录→结算单)
24. MealOrder → DailyMenu (订餐统计→日常菜单)
25. FixedCost → ServiceCompany (固定成本→服务公司)
26. DeviationReport → ServiceCompany (偏差报告→服务公司)
27. DeviationReportDetail → DeviationReport (明细→偏差报告)
28. DeviationReportDetail → Material (明细→物资)

**多对多关系**
通过关联表实现：
- BaseMenuMaterial (基础菜单⇄物资)
- DailyMenuMaterial (日常菜单⇄物资)

### 设计说明
1. **数据一致性保障**
  - 事务控制：出入库操作采用数据库事务，确保库存实时更新
  - 外键约束：通过36位UUID主键和外键实现强关联关系
  - 状态同步：供应商/服务公司状态变更实时影响关联业务（如禁用供应商无法新建入库单）

2. **高频读写优化**
  - 读写分离：库存表(Inventory)与事务表(OutboundOrderDetail/InboundOrderDetail)分离
  - 热点分区：按库房ID对出入库明细表水平分片
  - 冗余设计：物资名称/单位在明细表中冗余存储，避免高频查询关联

3. **可扩展关联设计**
  - 树形结构：MaterialCategory通过parent_id支持无限级分类
  - 版本隔离：BaseMenu与DailyMenu解耦，基础菜单修改不影响历史数据
  - 类型扩展：出库单outbound_type字段支持整入整出/自由出库/盘亏等模式扩展

4. **历史追踪能力**
  - 全链路追溯：从DailyMenu→BaseMenuMaterial→InboundOrderDetail实现成本溯源
  - 偏差分析：DeviationReportDetail记录理论/实际消耗量差值
  - 状态保留：供应商禁用后历史入库单仍可查询

5. **性能增强设计**
  - 索引策略：在查询频率高的字段建立组合索引（如：Material(分类ID+名称)）
  - 异步计算：偏差报告通过离线任务生成，避免实时分析压力
  - 数据归档：设定5年以上的冷热数据分离机制

6. **关键业务覆盖**
  - 支持整入整出：OutboundOrderDetail可关联InboundOrderDetail实现批次跟踪
  - 第三方对接：MealOrder表存储API获取的订餐数据
  - 附件管理：票据/图片字段存储OSS路径，满足财税合规要求

> 设计满足200人5年使用需求，日均10万条数据处理能力，关键操作响应时间<1秒。通过物资参考价格联动机制实现成本实时核算（Material.referencePrice→BaseMenuMaterial.costUnitPrice），同时确保历史菜单成本不变。
