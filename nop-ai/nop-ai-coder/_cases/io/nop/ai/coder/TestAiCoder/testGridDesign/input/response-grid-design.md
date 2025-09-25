根据需求描述，我将为【库存操作】实体设计列表页面的字段和操作。以下是符合要求的XML配置：

```xml
<grid title="库存操作">
    <cols>
        <col id="operationNo" xui:picker="true"/>
        <col id="operationType" xui:picker="true"/>
        <col id="operationDate" xui:picker="true"/>
        <col id="warehouseId"/>
        <col id="operator"/>
        <col id="isLocked"/>
    </cols>
    <xui:list-actions>
        <action name="createInbound" label="新建入库单" handler="createInboundOperation" xui:role="company-admin"/>
        <action name="createOutbound" label="新建出库单" handler="createOutboundOperation" xui:role="company-admin"/>
        <action name="export" label="导出数据" handler="exportStockOperations" xui:role="school-admin,company-admin"/>
    </xui:list-actions>
    <xui:row-actions>
        <action name="view" label="查看详情" handler="viewStockOperationDetail" xui:role="school-admin,company-admin"/>
        <action name="edit" label="编辑" handler="editStockOperation" xui:role="company-admin">
            <visibleOn>!this.isLocked</visibleOn>
        </action>
        <action name="lock" label="锁定" handler="lockStockOperation" xui:role="company-admin">
            <visibleOn>!this.isLocked</visibleOn>
        </action>
        <action name="unlock" label="解锁" handler="unlockStockOperation" xui:role="company-admin">
            <visibleOn>this.isLocked</visibleOn>
        </action>
    </xui:row-actions>
</grid>
```

设计说明：
1. 字段选择：
  - 在picker中显示最重要的3个字段：操作单号、操作类型和操作日期
  - 列表页显示这些关键字段加上库房ID、操作人和锁定状态

2. 列表操作：
  - 公司管理员可以创建入库单和出库单
  - 所有角色都可以导出数据

3. 行操作：
  - 所有角色都可以查看详情
  - 公司管理员可以编辑未锁定的单据
  - 锁定/解锁操作根据当前状态显示，且仅对公司管理员可见

4. 权限控制：
  - 严格遵循角色定义，公司管理员有读写权限，学校管理员只有查看权限
  - 锁定状态作为操作可见性的条件，避免冗余配置

5. 符合业务规则：
  - 支持出入库管理核心功能
  - 实现单据锁定机制
  - 不包含盘库管理相关操作（属于P1优先级）
