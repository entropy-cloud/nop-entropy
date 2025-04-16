下面有多个发送给大模型生成UI界面的提示词，对每个提示词进行比较并打分

# 提示词1
NopLayout是一种支持复杂嵌套结构的布局语法，它只描述布局，不涉及具体控件的属性设置。

```xml
<form>
  <layout>
    ===#tabA===
    !fieldX

    ===>##groupA==
    fieldA fieldB
    fieldC
  </layout>

  <cells>
    <cell id="tabA" control="tab" displayName="标签A">
      <visibleOn>fieldA > fieldB</visibleOn>
      <action-bar>
        <button id="saveProduct" label="保存" icon="save" variant="primary" action="store.saveProduct"/>
        <button id="resetProduct" label="重置" icon="reset" action="store.reset"/>
      </action-bar>
    </cell>
  </cells>
</form>
```

布局语法的详细描述：

1. `!fieldName`表示fieldName对应的字段不显示label，否则会左侧显示label，右侧显示字段对应的控件。注意：是`!fieldName`，而不是`fieldName!`
2. 字段名为程序中使用的属性名
3. 通过#字符来表示嵌套深度
4. 通过cellId前面可选的>或者^字符表示当前分组可以折叠起来，^cellId表示可折叠状态，而>cellId表示可折叠但是展开。
5. 每一行最多放3个字段
6. 布局时需要考虑语义相关的字段放在一起，重要的、必填的字段放在前面
7. visibleOn中用到的字段名以及布局中用到的字段名都必须在【字段列表】中定义。<visibleOn>true</visibleOn>这种情况是冗余配置，不需要返回这个配置
8. 并不是所有字段都需要在layout中用到，没有用到的字段可以被忽略
9. 对于tab或者group，可以配置可选的action-bar，增加按钮。

采用NopLayout语法来描述如下界面：

有两个tab页，第一个tab页内包含产品基本信息，第二个tab页内包含销售历史

产品的【字段列表】：

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

在返回结果之前，严格检查一下布局语法是否满足NopLayout语法规范要求，并确保所有用到的字段都在【字段列表】中。

# 提示词2
【任务目标】
使用NopLayout语法创建一个页面，需求如下：
生成一个包含两个tab页的产品管理页面
1. 产品基本信息页
2. 销售历史页

【NopLayout语法规范】
1. 基础规则：
- `!fieldName` 表示隐藏字段标签（注意符号在前）
- `#` 表示嵌套层级深度
- `>cellId` 表示默认展开的可折叠区域
- `^cellId` 表示默认折叠的可折叠区域

2. 布局要求：
- 每行最多放置3个字段
- 语义相关的字段应分组显示
- 重要/必填字段应优先排列
- 所有字段必须来自下方提供的字段列表

3. 控件配置：
- 可为tab或group配置action-bar添加操作按钮
- 使用<visibleOn>条件控制显示逻辑(仅当需要时)
- 避免冗余配置(如不必要的visibleOn)

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

【示例结构】
```xml
<form>
  <layout>
    ===#mainTab===
    field1 field2 field3
    ===>##group1==
    !field4 field5
  </layout>

  <cells>
    <cell id="mainTab" control="tab" displayName="主标签">
      <action-bar>
        <button id="save" label="保存" action="save"/>
      </action-bar>
    </cell>
  </cells>
</form>
```


# 提示词3
NopLayout是一种支持复杂嵌套结构的布局语法，它只描述布局，不涉及具体控件的属性设置。

【核心语法】
1. `!fieldName` 隐藏label（注意符号位置）
2. `#`表示嵌套深度
3. `>cellId`(展开)/`^cellId`(收起)表示可折叠区域
4. 每行≤3字段
5. 语义相关字段分组，重要/必填字段靠前
6. 所有字段必须来自给定列表
7. 可配置action-bar添加按钮

【示例】
```xml
<form>
  <layout>
    ===#tabA===
    !fieldX
    ===>##groupA==
    fieldA fieldB
    fieldC
  </layout>
  <cells>
    <cell id="tabA" control="tab" displayName="标签A">
      <action-bar>
        <button id="save" label="保存" icon="save" action="store.save"/>
      </action-bar>
    </cell>
  </cells>
</form>
```

【任务】
用NopLayout描述含两个tab页的界面：
1. 产品基本信息
2. 销售历史

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


如果要进一步优化提示词，应该如何修改，返回最优化的提示词
