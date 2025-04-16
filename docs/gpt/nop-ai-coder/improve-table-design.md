【任务目标】
根据需求，进一步完善指定表的设计，返回结果遵循指定的XML格式。如有必要，可以增加新的表。如果已经很完善，则返回[NO_CHANGE]。

【表定义】
```xml

<orm>
  <entities>
    <entity name="Material" displayName="物资">
      <comment>食堂所用的各类食材物资</comment>
      <columns>
        <column name="id" displayName="物资编码" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="物资名称" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="category" displayName="物资分类" mandatory="true" primary="false" sqlType="varchar" precision="50"/>
        <column name="unit" displayName="计量单位" mandatory="true" primary="false" sqlType="varchar" precision="20"/>
        <column name="specification" displayName="规格型号" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="image" displayName="物资图片" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="brand" displayName="品牌" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="remark" displayName="备注" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
      </columns>
    </entity>

    <entity name="Supplier" displayName="供应商">
      <comment>各类食材的供货单位</comment>
      <columns>
        <column name="id" displayName="供应商编码" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="供应商名称" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="credit_code" displayName="社会信用代码" mandatory="true" primary="false" sqlType="varchar" precision="50"/>
        <column name="safety_certification" displayName="食品安全认证" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="contact" displayName="联系人信息" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="introduction" displayName="企业简介" mandatory="false" primary="false" sqlType="text"/>
        <column name="remark" displayName="备注" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="status" displayName="状态" mandatory="true" primary="false" sqlType="varchar" precision="10"/>
      </columns>
    </entity>

    <entity name="ServiceCompany" displayName="服务公司">
      <comment>学校食堂的经营单位</comment>
      <columns>
        <column name="id" displayName="公司编码" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="公司名称" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="credit_code" displayName="社会信用代码" mandatory="true" primary="false" sqlType="varchar" precision="50"/>
        <column name="safety_certification" displayName="食品安全认证" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="contact" displayName="联系人信息" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="introduction" displayName="企业简介" mandatory="false" primary="false" sqlType="text"/>
        <column name="remark" displayName="备注" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="status" displayName="状态" mandatory="true" primary="false" sqlType="varchar" precision="10"/>
      </columns>
    </entity>

    <entity name="BaseMenu" displayName="基础菜单">
      <comment>学校食堂日常菜单</comment>
      <columns>
        <column name="id" displayName="菜单编号" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="菜单名称" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="type" displayName="菜单类型" mandatory="true" primary="false" sqlType="varchar" precision="10"/>
        <column name="image" displayName="菜单图片" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="remark" displayName="备注" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
      </columns>
    </entity>

    <entity name="MenuMaterial" displayName="菜单配料">
      <comment>菜单与物资的关联表，记录菜单所需的物资及数量</comment>
      <columns>
        <column name="menu_id" displayName="菜单编号" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="BaseMenu"/>
        <column name="material_id" displayName="物资编码" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="Material"/>
        <column name="quantity" displayName="所需数量" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="unit_price" displayName="成本单价" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>

    <entity name="Warehouse" displayName="库房">
      <comment>服务公司的库房信息</comment>
      <columns>
        <column name="id" displayName="库房编码" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="库房名称" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="company_id" displayName="所属公司" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="ServiceCompany"/>
        <column name="remark" displayName="备注" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
      </columns>
    </entity>

    <entity name="DailyMenu" displayName="每日菜单">
      <comment>服务公司每日的菜单</comment>
      <columns>
        <column name="id" displayName="每日菜单编号" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="date" displayName="菜单日期" mandatory="true" primary="false" sqlType="date"/>
        <column name="menu_id" displayName="基础菜单编号" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="BaseMenu"/>
        <column name="company_id" displayName="服务公司" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="ServiceCompany"/>
        <column name="is_settled" displayName="是否结算" mandatory="true" primary="false" sqlType="boolean"/>
      </columns>
    </entity>

    <entity name="Inventory" displayName="库存">
      <comment>物资库存信息</comment>
      <columns>
        <column name="material_id" displayName="物资编码" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="Material"/>
        <column name="warehouse_id" displayName="库房编码" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="Warehouse"/>
        <column name="quantity" displayName="库存数量" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>

    <entity name="InventoryTransaction" displayName="出入库记录">
      <comment>物资的出入库记录</comment>
      <columns>
        <column name="id" displayName="出入库编码" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="type" displayName="类型" mandatory="true" primary="false" sqlType="varchar" precision="10"/>
        <column name="date" displayName="出入库日期" mandatory="true" primary="false" sqlType="date"/>
        <column name="warehouse_id" displayName="库房" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="Warehouse"/>
        <column name="handler" displayName="经手人" mandatory="false" primary="false" sqlType="varchar" precision="50"/>
        <column name="supplier_id" displayName="供应商" mandatory="false" primary="false" sqlType="varchar" precision="50" orm:ref-table="Supplier"/>
        <column name="paid_amount" displayName="已付金额" mandatory="false" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="total_amount" displayName="合计金额" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="attachment" displayName="附件材料" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="is_locked" displayName="是否锁定" mandatory="true" primary="false" sqlType="boolean"/>
      </columns>
    </entity>

    <entity name="InventoryTransactionDetail" displayName="出入库明细">
      <comment>出入库记录的物资明细</comment>
      <columns>
        <column name="transaction_id" displayName="出入库编码" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="InventoryTransaction"/>
        <column name="material_id" displayName="物资编码" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="Material"/>
        <column name="quantity" displayName="数量" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="unit_price" displayName="单价" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="amount" displayName="金额" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>

    <entity name="SupplierSettlement" displayName="供应商结算">
      <comment>服务公司与供应商的结算记录</comment>
      <columns>
        <column name="id" displayName="结算编号" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="transaction_id" displayName="出入库编码" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="InventoryTransaction"/>
        <column name="settlement_date" displayName="结算日期" mandatory="true" primary="false" sqlType="date"/>
        <column name="settled_amount" displayName="已结金额" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="total_amount" displayName="总金额" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>

    <entity name="DailyOperation" displayName="经营数据">
      <comment>服务公司每日的经营数据</comment>
      <columns>
        <column name="id" displayName="记录编号" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="date" displayName="日期" mandatory="true" primary="false" sqlType="date"/>
        <column name="company_id" displayName="服务公司" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="ServiceCompany"/>
        <column name="order_count" displayName="订餐人数" mandatory="false" primary="false" sqlType="int"/>
        <column name="fixed_cost" displayName="固定成本" mandatory="false" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="deviation" displayName="日偏差数据" mandatory="false" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>
  </entities>
</orm>
```

【需求描述】
