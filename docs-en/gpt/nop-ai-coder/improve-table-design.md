【Task Objective】
Based on the requirements, further refine the design of the specified tables and return the result following the specified XML format. If necessary, you may add new tables. If the design is already complete, return [NO_CHANGE].

【Table Definition】
```xml

<orm>
  <entities>
    <entity name="Material" displayName="Material">
      <comment>Various food ingredients and materials used by the cafeteria</comment>
      <columns>
        <column name="id" displayName="Material Code" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="Material Name" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="category" displayName="Material Category" mandatory="true" primary="false" sqlType="varchar" precision="50"/>
        <column name="unit" displayName="Unit of Measure" mandatory="true" primary="false" sqlType="varchar" precision="20"/>
        <column name="specification" displayName="Specification/Model" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="image" displayName="Material Image" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="brand" displayName="Brand" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="remark" displayName="Remarks" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
      </columns>
    </entity>

    <entity name="Supplier" displayName="Supplier">
      <comment>Suppliers of various food ingredients</comment>
      <columns>
        <column name="id" displayName="Supplier Code" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="Supplier Name" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="credit_code" displayName="Unified Social Credit Code" mandatory="true" primary="false" sqlType="varchar" precision="50"/>
        <column name="safety_certification" displayName="Food Safety Certification" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="contact" displayName="Contact Information" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="introduction" displayName="Company Profile" mandatory="false" primary="false" sqlType="text"/>
        <column name="remark" displayName="Remarks" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="status" displayName="Status" mandatory="true" primary="false" sqlType="varchar" precision="10"/>
      </columns>
    </entity>

    <entity name="ServiceCompany" displayName="Service Company">
      <comment>Operating entity of the school cafeteria</comment>
      <columns>
        <column name="id" displayName="Company Code" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="Company Name" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="credit_code" displayName="Unified Social Credit Code" mandatory="true" primary="false" sqlType="varchar" precision="50"/>
        <column name="safety_certification" displayName="Food Safety Certification" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="contact" displayName="Contact Information" mandatory="false" primary="false" sqlType="varchar" precision="100"/>
        <column name="introduction" displayName="Company Profile" mandatory="false" primary="false" sqlType="text"/>
        <column name="remark" displayName="Remarks" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="status" displayName="Status" mandatory="true" primary="false" sqlType="varchar" precision="10"/>
      </columns>
    </entity>

    <entity name="BaseMenu" displayName="Base Menu">
      <comment>Daily menu of the school cafeteria</comment>
      <columns>
        <column name="id" displayName="Menu ID" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="Menu Name" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="type" displayName="Menu Type" mandatory="true" primary="false" sqlType="varchar" precision="10"/>
        <column name="image" displayName="Menu Image" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="remark" displayName="Remarks" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
      </columns>
    </entity>

    <entity name="MenuMaterial" displayName="Menu Ingredients">
      <comment>Association table between menu and materials, recording the materials and quantities required by the menu</comment>
      <columns>
        <column name="menu_id" displayName="Menu ID" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="BaseMenu"/>
        <column name="material_id" displayName="Material Code" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="Material"/>
        <column name="quantity" displayName="Required Quantity" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="unit_price" displayName="Cost Unit Price" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>

    <entity name="Warehouse" displayName="Warehouse">
      <comment>Warehouse information of the service company</comment>
      <columns>
        <column name="id" displayName="Warehouse Code" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="name" displayName="Warehouse Name" mandatory="true" primary="false" sqlType="varchar" precision="100"/>
        <column name="company_id" displayName="Affiliated Company" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="ServiceCompany"/>
        <column name="remark" displayName="Remarks" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
      </columns>
    </entity>

    <entity name="DailyMenu" displayName="Daily Menu">
      <comment>Daily menu of the service company</comment>
      <columns>
        <column name="id" displayName="Daily Menu ID" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="date" displayName="Menu Date" mandatory="true" primary="false" sqlType="date"/>
        <column name="menu_id" displayName="Base Menu ID" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="BaseMenu"/>
        <column name="company_id" displayName="Service Company" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="ServiceCompany"/>
        <column name="is_settled" displayName="Is Settled" mandatory="true" primary="false" sqlType="boolean"/>
      </columns>
    </entity>

    <entity name="Inventory" displayName="Inventory">
      <comment>Material inventory information</comment>
      <columns>
        <column name="material_id" displayName="Material Code" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="Material"/>
        <column name="warehouse_id" displayName="Warehouse Code" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="Warehouse"/>
        <column name="quantity" displayName="Inventory Quantity" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>

    <entity name="InventoryTransaction" displayName="Inventory Transaction">
      <comment>Inbound/Outbound records of materials</comment>
      <columns>
        <column name="id" displayName="Transaction ID" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="type" displayName="Type" mandatory="true" primary="false" sqlType="varchar" precision="10"/>
        <column name="date" displayName="Transaction Date" mandatory="true" primary="false" sqlType="date"/>
        <column name="warehouse_id" displayName="Warehouse" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="Warehouse"/>
        <column name="handler" displayName="Handler" mandatory="false" primary="false" sqlType="varchar" precision="50"/>
        <column name="supplier_id" displayName="Supplier" mandatory="false" primary="false" sqlType="varchar" precision="50" orm:ref-table="Supplier"/>
        <column name="paid_amount" displayName="Paid Amount" mandatory="false" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="total_amount" displayName="Total Amount" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="attachment" displayName="Attachments" mandatory="false" primary="false" sqlType="varchar" precision="255"/>
        <column name="is_locked" displayName="Is Locked" mandatory="true" primary="false" sqlType="boolean"/>
      </columns>
    </entity>

    <entity name="InventoryTransactionDetail" displayName="Inventory Transaction Detail">
      <comment>Material details of inventory transactions</comment>
      <columns>
        <column name="transaction_id" displayName="Transaction ID" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="InventoryTransaction"/>
        <column name="material_id" displayName="Material Code" mandatory="true" primary="true" sqlType="varchar" precision="50" orm:ref-table="Material"/>
        <column name="quantity" displayName="Quantity" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="unit_price" displayName="Unit Price" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="amount" displayName="Amount" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>

    <entity name="SupplierSettlement" displayName="Supplier Settlement">
      <comment>Settlement records between the service company and suppliers</comment>
      <columns>
        <column name="id" displayName="Settlement ID" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="transaction_id" displayName="Transaction ID" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="InventoryTransaction"/>
        <column name="settlement_date" displayName="Settlement Date" mandatory="true" primary="false" sqlType="date"/>
        <column name="settled_amount" displayName="Settled Amount" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="total_amount" displayName="Total Amount" mandatory="true" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>

    <entity name="DailyOperation" displayName="Operational Data">
      <comment>Daily operational data of the service company</comment>
      <columns>
        <column name="id" displayName="Record ID" mandatory="true" primary="true" sqlType="varchar" precision="50"/>
        <column name="date" displayName="Date" mandatory="true" primary="false" sqlType="date"/>
        <column name="company_id" displayName="Service Company" mandatory="true" primary="false" sqlType="varchar" precision="50" orm:ref-table="ServiceCompany"/>
        <column name="order_count" displayName="Number of Meal Orders" mandatory="false" primary="false" sqlType="int"/>
        <column name="fixed_cost" displayName="Fixed Cost" mandatory="false" primary="false" sqlType="decimal" precision="10" scale="2"/>
        <column name="deviation" displayName="Daily Deviation Data" mandatory="false" primary="false" sqlType="decimal" precision="10" scale="2"/>
      </columns>
    </entity>
  </entities>
</orm>
```

【Requirement Description】


【Return Format】
 <TRANSLATE_RESULT>translated-text
<!-- SOURCE_MD5:6028a1a81a0d023125e2aaacb31131ad-->
