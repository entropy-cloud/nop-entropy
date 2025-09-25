根据需求描述，我将为【库存操作】实体创建详细信息页面，考虑权限控制、字段分组和业务逻辑。以下是符合要求的form-layout实现：

```xml
<form title="库存操作详情">
    <layout>
        ===#group_basic====
        operationNo operationType operationDate
        warehouseId operator isLocked

        ===>##group_related===
        supplierId dailyMenuId

        ===^##group_remark===
        !remark

        ===#group_details====
        details

        ===#group_settlement===
        settlementDetails
    </layout>

    <cells>
        <cell id="group_basic" displayName="基础信息"/>
        <cell id="group_related" displayName="关联信息"/>
        <cell id="group_remark" displayName="备注信息"/>
        <cell id="group_details" displayName="操作明细"/>
        <cell id="group_settlement" displayName="结算关联"/>
    </cells>

    <actions>
        <action name="lock" label="锁定单据" handler="lockOperation" xui:role="company-admin">
            <visibleOn>!isLocked</visibleOn>
        </action>
        <action name="unlock" label="解锁单据" handler="unlockOperation" xui:role="company-admin">
            <visibleOn>isLocked</visibleOn>
        </action>
        <action name="edit" label="编辑" handler="editOperation" xui:role="company-admin">
            <visibleOn>!isLocked</visibleOn>
        </action>
        <action name="delete" label="删除" handler="deleteOperation" xui:role="company-admin">
            <visibleOn>!isLocked</visibleOn>
        </action>
    </actions>
</form>
```

设计说明：
1. 字段分组：
  - 基础信息：包含必填的核心字段，每行3个字段
  - 关联信息：默认展开的非必填关联字段
  - 备注信息：默认折叠的备注字段，隐藏标签
  - 明细和结算：两个重要列表单独分组显示

2. 权限控制：
  - 所有编辑类操作仅限company-admin角色
  - 根据isLocked状态显示不同的操作按钮
  - school-admin角色默认不显示任何操作按钮（只读）

3. 业务规则实现：
  - 锁定/解锁操作符合单据锁定机制需求
  - 编辑/删除操作在锁定状态下不可见
  - 重要字段优先排列，符合操作习惯

4. 布局优化：
  - 必填字段全部显示在基础信息组
  - 关联信息根据业务相关性分组
  - 列表类数据单独展示保证可读性

这个设计满足了所有需求描述中的要点，包括权限控制、业务规则和数据显示要求。
