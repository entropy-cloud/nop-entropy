根据需求描述和表定义，我将设计Web系统的后台功能菜单结构如下：

```xml
<auth>
    <site>
        <!-- 基础数据管理 -->
        <resource id="base-data" displayName="基础数据管理" icon="fa-database" routePath="/base-data">
            <children>
                <resource id="material-category" displayName="物资分类管理" icon="fa-sitemap" app:entityName="material_category" app:pageName="main" role="school-admin"/>
                <resource id="material" displayName="物资管理" icon="fa-box-open" app:entityName="material" app:pageName="main" role="school-admin,company-admin"/>
                <resource id="supplier" displayName="供应商管理" icon="fa-truck" app:entityName="supplier" app:pageName="main" role="school-admin,company-admin"/>
                <resource id="service-company" displayName="服务公司管理" icon="fa-building" app:entityName="service_company" app:pageName="main" role="school-admin,company-admin"/>
                <resource id="base-menu" displayName="基础菜单管理" icon="fa-utensils" app:entityName="base_menu" app:pageName="main" role="school-admin,company-admin"/>
            </children>
        </resource>

        <!-- 经营管理 -->
        <resource id="operation" displayName="经营管理" icon="fa-chart-line" routePath="/operation">
            <children>
                <resource id="warehouse" displayName="库房管理" icon="fa-warehouse" app:entityName="warehouse" app:pageName="main" role="company-admin"/>
                <resource id="daily-menu" displayName="每日菜单" icon="fa-calendar-day" app:entityName="daily_menu" app:pageName="main" role="company-admin"/>
                <resource id="stock-operation" displayName="出入库管理" icon="fa-exchange-alt" app:entityName="stock_operation" app:pageName="main" role="company-admin"/>
                <resource id="inventory-check" displayName="盘库管理" icon="fa-clipboard-check" app:entityName="inventory_check" app:pageName="main" role="company-admin"/>
            </children>
        </resource>

        <!-- 供应商结算 -->
        <resource id="settlement" displayName="供应商结算" icon="fa-money-bill-wave" routePath="/settlement">
            <children>
                <resource id="supplier-settlement" displayName="结算单管理" icon="fa-file-invoice-dollar" app:entityName="supplier_settlement" app:pageName="main" role="company-admin"/>
                <resource id="payment-record" displayName="付款记录" icon="fa-receipt" app:entityName="payment_record" app:pageName="main" role="company-admin"/>
            </children>
        </resource>

        <!-- 经营数据 -->
        <resource id="operation-data" displayName="经营数据" icon="fa-chart-pie" routePath="/operation-data">
            <children>
                <resource id="daily-operation" displayName="日常经营数据" icon="fa-clipboard-list" app:entityName="operation_data" app:pageName="main" role="company-admin"/>
            </children>
        </resource>

        <!-- 监管中心 -->
        <resource id="monitor" displayName="监管中心" icon="fa-eye" routePath="/monitor">
            <children>
                <resource id="stock-query" displayName="库存查询" icon="fa-boxes" app:entityName="inventory" app:pageName="query" role="school-admin"/>
                <resource id="operation-query" displayName="出入库查询" icon="fa-search" app:entityName="stock_operation" app:pageName="query" role="school-admin"/>
                <resource id="data-query" displayName="经营数据查询" icon="fa-table" app:entityName="operation_data" app:pageName="query" role="school-admin"/>
            </children>
        </resource>

        <!-- 统计分析 -->
        <resource id="analysis" displayName="统计分析" icon="fa-chart-bar" routePath="/analysis">
            <children>
                <resource id="material-analysis" displayName="物资统计报表" icon="fa-chart-line" app:pageName="material-report" role="school-admin,company-admin"/>
                <resource id="cost-analysis" displayName="成本分析报表" icon="fa-money-bill-alt" app:pageName="cost-report" role="school-admin,company-admin"/>
                <resource id="performance-analysis" displayName="经营绩效分析" icon="fa-trophy" app:pageName="performance-report" role="school-admin"/>
            </children>
        </resource>
    </site>
    <roles>
        <role name="school-admin" displayName="学校管理员">
            <description>负责系统基础数据维护、日常监管和统计分析</description>
        </role>
        <role name="company-admin" displayName="服务公司管理员">
            <description>食堂经营单位的管理人员，负责日常经营数据录入</description>
        </role>
    </roles>
</auth>
```

设计说明：

1. 按照功能模块划分菜单结构，包括基础数据管理、经营管理、供应商结算、经营数据、监管中心和统计分析六大模块。

2. 每个实体表对应一个main页面，包含完整的增删改查操作。对于非增删改查的业务(如统计分析)单独定义resource。

3. 主表+明细子表的结构(如stock_operation和stock_operation_detail)只从主实体入口进入。

4. 权限分配符合需求描述：
  - 学校管理员有基础数据管理的读写权限和监管中心的查询权限
  - 服务公司管理员有经营管理和供应商结算的完整权限
  - 统计分析模块根据角色分配不同权限

5. 图标使用了Font Awesome的图标库，符合系统整体风格。

6. 角色定义与需求描述完全一致，包括学校管理员和服务公司管理员两种角色。
