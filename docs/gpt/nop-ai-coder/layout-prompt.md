【NopLayout语法规范】
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

3. cell配置：
  - 可为tab或group配置actions添加操作按钮
  - 使用<visibleOn>条件控制显示逻辑(仅当需要时)。visibleOn采用Javascript语法，不需要用`${}`包裹

【示例结构】
```xml

<form>
  <layout>
    ===#group_mainTab====
      field1 field2 field3

      ===>##group_sub1==
      field4 field5

    ===#group_secondTab===
      !field6
  </layout>

  <cells>
    <cell id="group_mainTab" control="tab" displayName="主标签">
      <visibleOn>fieldA > fieldB</visibleOn>
      <actions>
        <action name="save" label="保存" handler="onSave"/>
      </actions>
    </cell>
  </cells>
</form>
```

【任务目标】
使用NopLayout语法创建一个页面，需求如下：
生成一个包含两个tab页的产品管理页面
1. 产品基本信息页
2. 销售历史页

【字段列表】
1. productId[产品ID]
2. productName[产品名称]，必填
3. productCode[产品编码]，必填
4. productCategory[产品分类]
5. productSubCategory[产品子类]
6. basePrice[基础价格]
7. discountRate[折扣率]
8. finalPrice[最终售价]
9. taxRate[税率]
10. currentStock[当前库存]
11. minStockLevel[最低库存]
12. maxStockLevel[最高库存]
13. warehouseLocation[仓库位置]
14. restockDate[补货日期]
15. totalSales[总销量]
16. monthlySales[月销量]
17. salesHistory[销售历史]
18. recentCustomers[最近购买客户]
19. supplierName[供应商]
20. supplierContact[联系人]
21. supplierPhone[联系电话]
22. contractExpiry[合同到期日]
23. promotionFlag[是否促销]
24. promotionStart[促销开始]
25. promotionEnd[促销结束]
26. marketingNotes[营销备注]

【要求】
1. 严格检查语法合规性
2. 验证所有使用字段的合法性
3. 避免冗余visibleOn配置
