/** 基础菜单信息 */
class BaseMenu{
Long id; //ID
String name; //菜单名称
String code; //菜单编码
String type; //菜单类型
String imageUrl; //图片URL
java.math.BigDecimal standardCost; //标准成本
String description; //描述

@JoinToMany(leftProp="id",rightProp="menuId")
Set<MenuMaterial> materials; //配料列表

@JoinToMany(leftProp="id",rightProp="baseMenuId")
Set<DailyMenu> dailyMenus; //每日菜单引用

}

/** 每日实际供应的菜单 */
class DailyMenu{
Long id; //ID
Long companyId; //服务公司ID
java.time.LocalDate menuDate; //菜单日期
Long baseMenuId; //基础菜单ID
Integer plannedDiners; //预计用餐人数
Integer actualDiners; //实际用餐人数
Boolean isLocked; //是否锁定
String remark; //备注

@JoinToOne(leftProp="companyId",rightProp="id")
ServiceCompany company;

@JoinToOne(leftProp="baseMenuId",rightProp="id")
BaseMenu baseMenu;

@JoinToMany(leftProp="id",rightProp="dailyMenuId")
Set<StockOperation> operations; //出库记录

}

/** 物资在各库房的实时库存 */
class Inventory{
Long id; //ID
Long warehouseId; //库房ID
Long materialId; //物资ID
java.math.BigDecimal quantity; //数量
String unit; //单位
java.sql.Timestamp lastUpdateTime; //最后更新时间

@JoinToOne(leftProp="warehouseId",rightProp="id")
Warehouse warehouse;

@JoinToOne(leftProp="materialId",rightProp="id")
Material material;

}

/** 库存盘点记录 */
class InventoryCheck{
Long id; //ID
String checkNo; //盘点单号
Long warehouseId; //库房ID
java.time.LocalDate checkDate; //盘点日期
String operator; //操作人
String status; //状态 Options: DRAFT[草稿],CONFIRMED[已确认],ADJUSTED[已调整],
String remark; //备注

@JoinToOne(leftProp="warehouseId",rightProp="id")
Warehouse warehouse;

@JoinToMany(leftProp="id",rightProp="checkId")
Set<InventoryCheckDetail> details; //盘点明细

}

/** 盘点单的物资明细 */
class InventoryCheckDetail{
Long id; //ID
Long checkId; //盘点单ID
Long materialId; //物资ID
java.math.BigDecimal systemQuantity; //系统数量
java.math.BigDecimal actualQuantity; //实际数量
java.math.BigDecimal difference; //差异数量
String unit; //单位
java.math.BigDecimal unitPrice; //单价
java.math.BigDecimal differenceAmount; //差异金额
String remark; //备注

@JoinToOne(leftProp="checkId",rightProp="id")
InventoryCheck check;

@JoinToOne(leftProp="materialId",rightProp="id")
Material material;

}

/** 食堂物资基本信息 */
class Material{
Long id; //ID
String code; //物资编码
String name; //物资名称
Long categoryId; //分类ID
String specification; //规格
String unit; //单位
java.math.BigDecimal unitPrice; //单价
Integer minStock; //最低库存
Integer maxStock; //最高库存
String imageUrl; //图片URL
String status; //状态 Options: ENABLED[启用],DISABLED[禁用],
String description; //描述

@JoinToOne(leftProp="categoryId",rightProp="id")
MaterialCategory category;

@JoinToMany(leftProp="id",rightProp="materialId")
Set<MenuMaterial> menus; //关联菜单

@JoinToMany(leftProp="id",rightProp="materialId")
Set<Inventory> inventories; //库存分布

@JoinToMany(leftProp="id",rightProp="materialId")
Set<StockOperationDetail> operationDetails; //操作记录

@JoinToMany(leftProp="id",rightProp="materialId")
Set<InventoryCheckDetail> checkDetails; //盘点记录

@JoinToMany(leftProp="id",rightProp="materialId")
Set<SettlementDetail> settlementDetails; //结算记录

}

/** 物资的多级分类信息 */
class MaterialCategory{
Long id; //ID
String name; //分类名称
String code; //分类编码
Long parentId; //父分类ID
Integer level; //分类层级
Integer sortOrder; //排序序号

@JoinToOne(leftProp="parentId",rightProp="id")
MaterialCategory parent;

@JoinToMany(leftProp="id",rightProp="parentId")
Set<MaterialCategory> children; //子分类

@JoinToMany(leftProp="id",rightProp="categoryId")
Set<Material> materials; //物资列表

}

/** 基础菜单与物资的关联关系 */
class MenuMaterial{
Long id; //ID
Long menuId; //菜单ID
Long materialId; //物资ID
java.math.BigDecimal quantity; //数量
String unit; //单位
String remark; //备注

@JoinToOne(leftProp="menuId",rightProp="id")
BaseMenu menu;

@JoinToOne(leftProp="materialId",rightProp="id")
Material material;

}

/** 食堂日常经营数据 */
class OperationData{
Long id; //ID
Long companyId; //服务公司ID
java.time.LocalDate recordDate; //记录日期
java.math.BigDecimal fixedCost; //固定成本
java.math.BigDecimal laborCost; //人工成本
java.math.BigDecimal otherCost; //其他成本
java.math.BigDecimal totalCost; //总成本
java.math.BigDecimal income; //收入
java.math.BigDecimal profit; //利润
Boolean isLocked; //是否锁定
String operator; //操作人
String remark; //备注

@JoinToOne(leftProp="companyId",rightProp="id")
ServiceCompany company;

}

/** 向供应商付款的记录 */
class PaymentRecord{
Long id; //ID
String paymentNo; //付款单号
Long settlementId; //结算单ID
java.math.BigDecimal paymentAmount; //付款金额
java.time.LocalDate paymentDate; //付款日期
String paymentMethod; //付款方式
String operator; //操作人
String remark; //备注
String attachment; //附件

@JoinToOne(leftProp="settlementId",rightProp="id")
SupplierSettlement settlement;

}

/** 食堂经营服务公司信息 */
class ServiceCompany{
Long id; //ID
String name; //公司名称
String code; //公司编码
String contactPerson; //联系人
String contactPhone; //联系电话
String address; //地址
String status; //状态 Options: ENABLED[启用],DISABLED[禁用],
String remark; //备注

@JoinToMany(leftProp="id",rightProp="companyId")
Set<Warehouse> warehouses; //库房列表

@JoinToMany(leftProp="id",rightProp="companyId")
Set<DailyMenu> dailyMenus; //每日菜单列表

@JoinToMany(leftProp="id",rightProp="companyId")
Set<SupplierSettlement> supplierSettlements; //供应商结算

@JoinToMany(leftProp="id",rightProp="companyId")
Set<OperationData> operationDatas; //经营数据

}

/** 结算单的物资明细 */
class SettlementDetail{
Long id; //ID
Long settlementId; //结算单ID
Long operationId; //操作ID
Long materialId; //物资ID
java.math.BigDecimal quantity; //数量
String unit; //单位
java.math.BigDecimal unitPrice; //单价
java.math.BigDecimal amount; //金额
String remark; //备注

@JoinToOne(leftProp="settlementId",rightProp="id")
SupplierSettlement settlement;

@JoinToOne(leftProp="operationId",rightProp="id")
StockOperation operation;

@JoinToOne(leftProp="materialId",rightProp="id")
Material material;

}

/** 物资的出入库操作记录 */
class StockOperation{
Long id; //ID
String operationNo; //操作单号
Long warehouseId; //库房ID
String operationType; //操作类型 Options: IN[入库],OUT[出库],ADJUST[调整],
java.time.LocalDate operationDate; //操作日期
String operator; //操作人
Long supplierId; //供应商ID
Long dailyMenuId; //每日菜单ID
Boolean isLocked; //是否锁定
String remark; //备注

@JoinToOne(leftProp="warehouseId",rightProp="id")
Warehouse warehouse;

@JoinToOne(leftProp="supplierId",rightProp="id")
Supplier supplier;

@JoinToOne(leftProp="dailyMenuId",rightProp="id")
DailyMenu dailyMenu;

@JoinToMany(leftProp="id",rightProp="operationId")
Set<StockOperationDetail> details; //操作明细

@JoinToMany(leftProp="id",rightProp="operationId")
Set<SettlementDetail> settlementDetails; //结算关联

}

/** 库存操作的物资明细 */
class StockOperationDetail{
Long id; //ID
Long operationId; //操作ID
Long materialId; //物资ID
java.math.BigDecimal quantity; //数量
String unit; //单位
java.math.BigDecimal unitPrice; //单价
java.math.BigDecimal amount; //金额
String batchNo; //批次号
String remark; //备注

@JoinToOne(leftProp="operationId",rightProp="id")
StockOperation operation;

@JoinToOne(leftProp="materialId",rightProp="id")
Material material;

}

/** 物资供应商信息 */
class Supplier{
Long id; //ID
String name; //供应商名称
String code; //供应商编码
String contactPerson; //联系人
String contactPhone; //联系电话
String address; //地址
String status; //状态 Options: ENABLED[启用],DISABLED[禁用],
String qualificationFiles; //资质文件
String remark; //备注

@JoinToMany(leftProp="id",rightProp="supplierId")
Set<StockOperation> operations; //供应记录

@JoinToMany(leftProp="id",rightProp="supplierId")
Set<SupplierSettlement> settlements; //结算记录

}

/** 供应商物资结算信息 */
class SupplierSettlement{
Long id; //ID
String settlementNo; //结算单号
Long supplierId; //供应商ID
Long companyId; //服务公司ID
java.time.LocalDate startDate; //开始日期
java.time.LocalDate endDate; //结束日期
java.math.BigDecimal totalAmount; //总金额
java.math.BigDecimal paidAmount; //已付金额
String status; //状态 Options: PENDING[待结算],PARTIAL[部分结算],COMPLETED[已完成],
java.time.LocalDate settlementDate; //结算日期
String operator; //操作人
String remark; //备注

@JoinToOne(leftProp="supplierId",rightProp="id")
Supplier supplier;

@JoinToOne(leftProp="companyId",rightProp="id")
ServiceCompany company;

@JoinToMany(leftProp="id",rightProp="settlementId")
Set<SettlementDetail> details; //结算明细

@JoinToMany(leftProp="id",rightProp="settlementId")
Set<PaymentRecord> payments; //付款记录

}

/** 服务公司的物资库房信息 */
class Warehouse{
Long id; //ID
Long companyId; //服务公司ID
String name; //库房名称
String code; //库房编码
String location; //位置
String manager; //管理员
String contactPhone; //联系电话
String remark; //备注

@JoinToOne(leftProp="companyId",rightProp="id")
ServiceCompany company;

@JoinToMany(leftProp="id",rightProp="warehouseId")
Set<Inventory> inventories; //库存列表

@JoinToMany(leftProp="id",rightProp="warehouseId")
Set<StockOperation> operations; //操作记录

@JoinToMany(leftProp="id",rightProp="warehouseId")
Set<InventoryCheck> checks; //盘点记录

}

