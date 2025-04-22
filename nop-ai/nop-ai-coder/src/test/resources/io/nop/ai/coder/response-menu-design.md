根据需求描述和表定义，我设计了以下Web系统的后台功能菜单结构：

```xml
<site>
    <!-- 基础数据管理 -->
    <resource displayName="基础数据管理" icon="database" id="basic-data" routePath="/basic-data">
        <children>
            <resource app:entityName="MaterialCategory" app:pageName="main" displayName="物资分类管理" icon="sitemap" id="material-category"/>
            <resource app:entityName="Material" app:pageName="main" displayName="物资管理" icon="box" id="material"/>
            <resource app:entityName="Supplier" app:pageName="main" displayName="供应商管理" icon="truck" id="supplier"/>
            <resource app:entityName="ServiceCompany" app:pageName="main" displayName="服务公司管理" icon="building" id="service-company"/>
            <resource app:entityName="BaseMenu" app:pageName="main" displayName="基础菜单管理" icon="utensils" id="base-menu"/>
        </children>
    </resource>

    <!-- 库房管理 -->
    <resource displayName="库房管理" icon="warehouse" id="warehouse" routePath="/warehouse">
        <children>
            <resource app:entityName="Warehouse" app:pageName="main" displayName="库房信息" icon="warehouse" id="warehouse-info"/>
            <resource app:entityName="Inventory" app:pageName="main" displayName="库存查询" icon="boxes" id="inventory-query"/>
            <resource app:entityName="Inventory" app:pageName="inventory-warning" displayName="库存预警" icon="exclamation-triangle" id="inventory-warning"/>
        </children>
    </resource>

    <!-- 菜单管理 -->
    <resource displayName="菜单管理" icon="clipboard-list" id="menu" routePath="/menu">
        <children>
            <resource app:entityName="DailyMenu" app:pageName="main" displayName="每日菜单" icon="calendar-day" id="daily-menu"/>
            <resource app:entityName="DailyMenu" app:pageName="menu-plan" displayName="菜单计划" icon="calendar-week" id="menu-plan"/>
        </children>
    </resource>

    <!-- 出入库管理 -->
    <resource displayName="出入库管理" icon="exchange-alt" id="stock" routePath="/stock">
        <children>
            <resource app:entityName="StockIn" app:pageName="main" displayName="入库管理" icon="sign-in-alt" id="stock-in"/>
            <resource app:entityName="StockOut" app:pageName="main" displayName="出库管理" icon="sign-out-alt" id="stock-out"/>
            <resource app:entityName="InventoryCheck" app:pageName="main" displayName="盘点管理" icon="clipboard-check" id="inventory-check"/>
        </children>
    </resource>

    <!-- 供应商结算 -->
    <resource displayName="供应商结算" icon="money-bill-wave" id="settlement" routePath="/settlement">
        <children>
            <resource app:entityName="SupplierSettlement" app:pageName="main" displayName="结算单管理" icon="file-invoice-dollar" id="supplier-settlement"/>
            <resource app:entityName="SupplierSettlement" app:pageName="settlement-report" displayName="结算报表" icon="chart-line" id="settlement-report"/>
        </children>
    </resource>

    <!-- 经营数据 -->
    <resource displayName="经营数据" icon="chart-pie" id="operation" routePath="/operation">
        <children>
            <resource app:entityName="OperationData" app:pageName="main" displayName="经营数据录入" icon="edit" id="operation-data-input"/>
            <resource app:entityName="OperationData" app:pageName="deviation-analysis" displayName="偏差分析" icon="balance-scale" id="deviation-analysis"/>
        </children>
    </resource>

    <!-- 统计分析 -->
    <resource displayName="统计分析" icon="chart-bar" id="analysis" routePath="/analysis">
        <children>
            <resource app:entityName="Material" app:pageName="material-statistics" displayName="物资统计" icon="box-open" id="material-statistics"/>
            <resource app:entityName="StockIn" app:pageName="cost-analysis" displayName="成本分析" icon="calculator" id="cost-analysis"/>
            <resource app:entityName="OperationData" app:pageName="performance-analysis" displayName="经营绩效" icon="award" id="performance-analysis"/>
        </children>
    </resource>
</site>
```

设计说明：

1. 按照角色权限将功能分为7个主要模块，每个模块下有相关子功能
2. 基础数据管理包含所有基础实体的增删改查功能
3. 库房管理模块除了库房信息外，还包含库存查询和预警功能
4. 菜单管理模块包含每日菜单和菜单计划两个功能
5. 出入库管理模块包含入库、出库和盘点三个主要功能
6. 供应商结算模块包含结算单管理和报表功能
7. 经营数据模块包含数据录入和偏差分析功能
8. 统计分析模块提供各类报表和分析功能
9. 主表+明细表的结构(如StockIn+StockInDetail)只从主表入口进入
10. 非增删改查的功能(如统计分析)使用单独的pageName

这个设计满足了需求中的所有要求，并按照功能相关性组织了菜单结构，避免了单个children下只有一个resource的情况。
