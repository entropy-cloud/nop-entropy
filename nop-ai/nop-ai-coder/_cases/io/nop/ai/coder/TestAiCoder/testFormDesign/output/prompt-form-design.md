【任务目标】
使用form-layout语法创建【库存操作】实体的详细信息页面，它能够实现【需求描述】中涉及到的实体页面

【返回格式】
```xml
<form title="string">
    <layout>form-layout</layout>
    <cells>
        <cell id="string" label="chinese"/>
    </cells>
    <actions>
        <action name="english" label="chinese" handler="service-method" xui:role="role-csv-set">
            <visibleOn>conditional-expr</visibleOn>
        </action>
    </actions>
</form>
```

1. handler表示触发的后台业务方法名，比如resetPassword等
2. role-csv-set表示角色集合，比如admin,manager等。用到的角色必须在【role列表】中定义

【form-layout格式规范】
1. 基础规则：
- `#` 表示嵌套层级深度
- `>cellId` 表示默认展开的可折叠区域
- `^cellId` 表示默认折叠的可折叠区域
- `!fieldName` 表示隐藏字段标签（注意符号在前）

2. layout配置：
- 每行最多放置3个字段
- 语义相关的字段应分组显示
- 重要/必填字段应优先排列
- 所有字段必须来自下方提供的字段列表
- 不需要用到所有字段，无关的字段可以直接忽略（不是隐藏）


【示例】
```xml
<form>
  <layout>
    ===#group_main====
    field1 field2 field3

    ===>##group_sub1==
    field4 field5

    ===#group_second===
    !field6
  </layout>

  <cells>
    <cell id="group_main" displayName="主标签">
      <visibleOn>fieldA > fieldB</visibleOn>
    </cell>
  </cells>
</form>
```
【字段列表】
1. id[ID],必填
2. operationNo[操作单号],必填
3. warehouseId[库房ID],必填
4. operationType[操作类型],必填
5. operationDate[操作日期],必填
6. operator[操作人]
7. supplierId[供应商ID]
8. dailyMenuId[每日菜单ID]
9. isLocked[是否锁定],必填
10. remark[备注]
11. details[操作明细],必填,列表
12. settlementDetails[结算关联],必填,列表

【role列表】
1.school-admin[学校管理员]:负责系统基础数据维护、日常监管和统计分析
2.company-admin[服务公司管理员]:食堂经营单位的管理人员，负责日常经营数据录入

【要求】
1. 严格检查语法合规性
2. 验证所有使用字段的合法性
3. 避免冗余visibleOn配置

【需求描述】
从需求描述中抽取与【stock_operation(库存操作)】相关的部分如下：

1. **角色权限相关**
- 服务公司管理员拥有"出入库管理"和"盘库管理"的读写权限（仅限自身数据）
- 学校管理员对出入库管理仅有只读权限

2. **功能模块相关**
- 出入库管理（P0优先级）：
  - 入库单管理
  - 出库单管理
  - 整入整出操作
  - 单据锁定机制
- 盘库管理（P1优先级）：
  - 库存盘点
  - 盘盈盘亏处理
- 库存变更规则：
  - 入库增加库存
  - 出库减少库存
  - 盘盈盘亏调整库存

3. **数据流相关**
- 实际出入库操作影响实际库存数据
- 出库可与菜单关联，自动计算理论消耗量
- 日偏差计算依赖实际出库量数据

4. **核心业务规则**
- 出入库单据包含明细项，直接影响实时库存
- 结算日后的出入库数据自动锁定不可修改
- 敏感库存操作需记录操作日志

5. **关联功能**
- 物资管理中的"物资库存查询"功能
- 统计分析中的"库存预警"功能
- 经营数据管理中的"日偏差计算"（依赖出库数据）

6. **菜单入口**
- 服务公司管理员菜单中包含：
  - 出入库管理
  - 盘库管理
  - 库存查询

这些部分共同构成了系统中与库存操作相关的完整业务流程和权限体系。

