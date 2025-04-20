根据需求描述，我将设计食堂物资管理系统的核心数据库表结构，不包含用户、角色等通用表。以下是XML格式的设计方案：

```xml
<orm>
  <entities>
    <!-- 物资分类表 -->
    <entity name="MaterialCategory" displayName="物资分类">
      <comment>物资分类信息，支持多级分类</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="parent_id" displayName="父分类ID" mandatory="false" sqlType="BIGINT" orm:ref-table="MaterialCategory"/>
        <column name="category_code" displayName="分类编码" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="category_name" displayName="分类名称" mandatory="true" sqlType="VARCHAR" precision="100"/>
        <column name="level" displayName="分类层级" mandatory="true" sqlType="INT"/>
        <column name="sort_order" displayName="排序序号" mandatory="true" sqlType="INT"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="BOOLEAN"/>
      </columns>
    </entity>

    <!-- 物资表 -->
    <entity name="Material" displayName="物资">
      <comment>食堂物资基本信息</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="category_id" displayName="分类ID" mandatory="true" sqlType="BIGINT" orm:ref-table="MaterialCategory"/>
        <column name="material_code" displayName="物资编码" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="material_name" displayName="物资名称" mandatory="true" sqlType="VARCHAR" precision="100"/>
        <column name="specification" displayName="规格" mandatory="false" sqlType="VARCHAR" precision="100"/>
        <column name="unit" displayName="单位" mandatory="true" sqlType="VARCHAR" precision="20"/>
        <column name="image_url" displayName="图片" mandatory="false" sqlType="VARCHAR" precision="255" stdDomain="image"/>
        <column name="min_stock" displayName="最低库存" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="max_stock" displayName="最高库存" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="BOOLEAN"/>
      </columns>
    </entity>

    <!-- 供应商表 -->
    <entity name="Supplier" displayName="供应商">
      <comment>物资供应商信息</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="supplier_code" displayName="供应商编码" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="supplier_name" displayName="供应商名称" mandatory="true" sqlType="VARCHAR" precision="100"/>
        <column name="contact_person" displayName="联系人" mandatory="false" sqlType="VARCHAR" precision="50"/>
        <column name="contact_phone" displayName="联系电话" mandatory="false" sqlType="VARCHAR" precision="20"/>
        <column name="address" displayName="地址" mandatory="false" sqlType="VARCHAR" precision="200"/>
        <column name="qualification_files" displayName="资质文件" mandatory="false" sqlType="VARCHAR" precision="500" stdDomain="fileList"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="BOOLEAN"/>
      </columns>
    </entity>

    <!-- 服务公司表 -->
    <entity name="ServiceCompany" displayName="服务公司">
      <comment>食堂经营服务公司信息</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="company_code" displayName="公司编码" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="company_name" displayName="公司名称" mandatory="true" sqlType="VARCHAR" precision="100"/>
        <column name="contact_person" displayName="联系人" mandatory="false" sqlType="VARCHAR" precision="50"/>
        <column name="contact_phone" displayName="联系电话" mandatory="false" sqlType="VARCHAR" precision="20"/>
        <column name="address" displayName="地址" mandatory="false" sqlType="VARCHAR" precision="200"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="BOOLEAN"/>
      </columns>
    </entity>

    <!-- 库房表 -->
    <entity name="Warehouse" displayName="库房">
      <comment>食堂物资库房信息</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="service_company_id" displayName="服务公司ID" mandatory="true" sqlType="BIGINT" orm:ref-table="ServiceCompany"/>
        <column name="warehouse_code" displayName="库房编码" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="warehouse_name" displayName="库房名称" mandatory="true" sqlType="VARCHAR" precision="100"/>
        <column name="location" displayName="位置" mandatory="false" sqlType="VARCHAR" precision="200"/>
        <column name="manager" displayName="管理员" mandatory="false" sqlType="VARCHAR" precision="50"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="BOOLEAN"/>
      </columns>
    </entity>

    <!-- 基础菜单表 -->
    <entity name="BaseMenu" displayName="基础菜单">
      <comment>基础菜单信息</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="menu_code" displayName="菜单编码" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="menu_name" displayName="菜单名称" mandatory="true" sqlType="VARCHAR" precision="100"/>
        <column name="image_url" displayName="图片" mandatory="false" sqlType="VARCHAR" precision="255" stdDomain="image"/>
        <column name="description" displayName="描述" mandatory="false" sqlType="VARCHAR" precision="500"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="BOOLEAN"/>
      </columns>
    </entity>

    <!-- 菜单配料表 -->
    <entity name="MenuMaterial" displayName="菜单配料">
      <comment>基础菜单的配料信息</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="menu_id" displayName="菜单ID" mandatory="true" sqlType="BIGINT" orm:ref-table="BaseMenu" orm:ref-prop="materials" orm:ref-prop-display-name="配料列表"/>
        <column name="material_id" displayName="物资ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Material"/>
        <column name="quantity" displayName="数量" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="unit" displayName="单位" mandatory="true" sqlType="VARCHAR" precision="20"/>
      </columns>
    </entity>

    <!-- 每日菜单表 -->
    <entity name="DailyMenu" displayName="每日菜单">
      <comment>每日提供的菜单</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="service_company_id" displayName="服务公司ID" mandatory="true" sqlType="BIGINT" orm:ref-table="ServiceCompany"/>
        <column name="menu_date" displayName="菜单日期" mandatory="true" sqlType="DATE"/>
        <column name="base_menu_id" displayName="基础菜单ID" mandatory="true" sqlType="BIGINT" orm:ref-table="BaseMenu"/>
        <column name="is_locked" displayName="是否锁定" mandatory="true" sqlType="BOOLEAN"/>
      </columns>
    </entity>

    <!-- 库存表 -->
    <entity name="Inventory" displayName="库存">
      <comment>物资库存信息</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="warehouse_id" displayName="库房ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Warehouse"/>
        <column name="material_id" displayName="物资ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Material"/>
        <column name="quantity" displayName="数量" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="last_update_time" displayName="最后更新时间" mandatory="true" sqlType="TIMESTAMP"/>
      </columns>
    </entity>

    <!-- 入库单表 -->
    <entity name="StockIn" displayName="入库单">
      <comment>物资入库单据</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="warehouse_id" displayName="库房ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Warehouse"/>
        <column name="supplier_id" displayName="供应商ID" mandatory="false" sqlType="BIGINT" orm:ref-table="Supplier"/>
        <column name="in_order_no" displayName="入库单号" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="in_date" displayName="入库日期" mandatory="true" sqlType="DATE"/>
        <column name="total_amount" displayName="总金额" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="operator" displayName="操作人" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="VARCHAR" precision="20"/>
        <column name="is_locked" displayName="是否锁定" mandatory="true" sqlType="BOOLEAN"/>
        <column name="remark" displayName="备注" mandatory="false" sqlType="VARCHAR" precision="500"/>
      </columns>
    </entity>

    <!-- 入库单明细表 -->
    <entity name="StockInDetail" displayName="入库单明细">
      <comment>入库单明细项</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="stock_in_id" displayName="入库单ID" mandatory="true" sqlType="BIGINT" orm:ref-table="StockIn" orm:ref-prop="details" orm:ref-prop-display-name="入库明细"/>
        <column name="material_id" displayName="物资ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Material"/>
        <column name="quantity" displayName="数量" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="unit_price" displayName="单价" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="amount" displayName="金额" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="batch_no" displayName="批次号" mandatory="false" sqlType="VARCHAR" precision="50"/>
        <column name="expiry_date" displayName="有效期" mandatory="false" sqlType="DATE"/>
      </columns>
    </entity>

    <!-- 出库单表 -->
    <entity name="StockOut" displayName="出库单">
      <comment>物资出库单据</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="warehouse_id" displayName="库房ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Warehouse"/>
        <column name="out_order_no" displayName="出库单号" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="out_date" displayName="出库日期" mandatory="true" sqlType="DATE"/>
        <column name="daily_menu_id" displayName="关联菜单ID" mandatory="false" sqlType="BIGINT" orm:ref-table="DailyMenu"/>
        <column name="operator" displayName="操作人" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="VARCHAR" precision="20"/>
        <column name="is_locked" displayName="是否锁定" mandatory="true" sqlType="BOOLEAN"/>
        <column name="remark" displayName="备注" mandatory="false" sqlType="VARCHAR" precision="500"/>
      </columns>
    </entity>

    <!-- 出库单明细表 -->
    <entity name="StockOutDetail" displayName="出库单明细">
      <comment>出库单明细项</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="stock_out_id" displayName="出库单ID" mandatory="true" sqlType="BIGINT" orm:ref-table="StockOut" orm:ref-prop="details" orm:ref-prop-display-name="出库明细"/>
        <column name="material_id" displayName="物资ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Material"/>
        <column name="quantity" displayName="数量" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="unit_price" displayName="单价" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="amount" displayName="金额" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
      </columns>
    </entity>

    <!-- 盘点单表 -->
    <entity name="InventoryCheck" displayName="盘点单">
      <comment>库存盘点单据</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="warehouse_id" displayName="库房ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Warehouse"/>
        <column name="check_no" displayName="盘点单号" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="check_date" displayName="盘点日期" mandatory="true" sqlType="DATE"/>
        <column name="operator" displayName="操作人" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="VARCHAR" precision="20"/>
        <column name="remark" displayName="备注" mandatory="false" sqlType="VARCHAR" precision="500"/>
      </columns>
    </entity>

    <!-- 盘点单明细表 -->
    <entity name="InventoryCheckDetail" displayName="盘点单明细">
      <comment>盘点单明细项</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="check_id" displayName="盘点单ID" mandatory="true" sqlType="BIGINT" orm:ref-table="InventoryCheck" orm:ref-prop="details" orm:ref-prop-display-name="盘点明细"/>
        <column name="material_id" displayName="物资ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Material"/>
        <column name="book_quantity" displayName="账面数量" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="actual_quantity" displayName="实际数量" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="difference" displayName="差异数量" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="unit_price" displayName="单价" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="difference_amount" displayName="差异金额" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
      </columns>
    </entity>

    <!-- 供应商结算单表 -->
    <entity name="SupplierSettlement" displayName="供应商结算单">
      <comment>供应商结算信息</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="service_company_id" displayName="服务公司ID" mandatory="true" sqlType="BIGINT" orm:ref-table="ServiceCompany"/>
        <column name="supplier_id" displayName="供应商ID" mandatory="true" sqlType="BIGINT" orm:ref-table="Supplier"/>
        <column name="settlement_no" displayName="结算单号" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="start_date" displayName="开始日期" mandatory="true" sqlType="DATE"/>
        <column name="end_date" displayName="结束日期" mandatory="true" sqlType="DATE"/>
        <column name="total_amount" displayName="总金额" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="paid_amount" displayName="已付金额" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="status" displayName="状态" mandatory="true" sqlType="VARCHAR" precision="20"/>
        <column name="settlement_time" displayName="结算时间" mandatory="false" sqlType="TIMESTAMP"/>
        <column name="operator" displayName="操作人" mandatory="true" sqlType="VARCHAR" precision="50"/>
      </columns>
    </entity>

    <!-- 付款记录表 -->
    <entity name="PaymentRecord" displayName="付款记录">
      <comment>供应商付款记录</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="settlement_id" displayName="结算单ID" mandatory="true" sqlType="BIGINT" orm:ref-table="SupplierSettlement" orm:ref-prop="payments" orm:ref-prop-display-name="付款记录"/>
        <column name="payment_no" displayName="付款单号" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="payment_amount" displayName="付款金额" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="payment_time" displayName="付款时间" mandatory="true" sqlType="TIMESTAMP"/>
        <column name="payment_method" displayName="付款方式" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="operator" displayName="操作人" mandatory="true" sqlType="VARCHAR" precision="50"/>
        <column name="remark" displayName="备注" mandatory="false" sqlType="VARCHAR" precision="500"/>
      </columns>
    </entity>

    <!-- 经营数据表 -->
    <entity name="OperationData" displayName="经营数据">
      <comment>食堂日常经营数据</comment>
      <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="BIGINT"/>
        <column name="service_company_id" displayName="服务公司ID" mandatory="true" sqlType="BIGINT" orm:ref-table="ServiceCompany"/>
        <column name="data_date" displayName="数据日期" mandatory="true" sqlType="DATE"/>
        <column name="diner_count" displayName="就餐人数" mandatory="true" sqlType="INT"/>
        <column name="fixed_cost" displayName="固定成本" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="material_cost" displayName="食材成本" mandatory="true" sqlType="DECIMAL" precision="12" scale="2"/>
        <column name="deviation_rate" displayName="偏差率" mandatory="true" sqlType="DECIMAL" precision="5" scale="2"/>
        <column name="is_locked" displayName="是否锁定" mandatory="true" sqlType="BOOLEAN"/>
        <column name="operator" displayName="操作人" mandatory="true" sqlType="VARCHAR" precision="50"/>
      </columns>
    </entity>
  </entities>
</orm>
```

这个设计方案涵盖了食堂物资管理系统的核心业务实体，包括：
1. 基础数据管理（物资、供应商、服务公司、基础菜单）
2. 库房和库存管理
3. 出入库管理
4. 盘点管理
5. 供应商结算
6. 经营数据管理

所有表结构都遵循了需求中的命名规范，避免了SQL关键字冲突，并合理设置了关联关系和集合属性。对于图片和文件类型的字段，使用了标准的stdDomain定义。
