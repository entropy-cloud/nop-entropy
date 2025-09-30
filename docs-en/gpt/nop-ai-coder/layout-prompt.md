【NopLayout Syntax Specification】
1. Basic rules:
  - `#` indicates the depth of the nesting level
  - `>cellId` indicates a collapsible section expanded by default
  - `^cellId` indicates a collapsible section collapsed by default
  - `!fieldName` hides the field label (note the symbol goes in front)

2. layout configuration:
  - At most 3 fields per row
  - Semantically related fields should be displayed as a group
  - Important/required fields should be prioritized
  - All fields must come from the field list provided below
  - You do not need to use all fields; unrelated fields can be directly ignored (not hidden)

3. cell configuration:
  - You can add action buttons by configuring actions for a tab or group
  - Use a <visibleOn> condition to control display logic (only when needed). visibleOn uses JavaScript syntax and does not need to be wrapped with `${}`

【Example Structure】
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
    <cell id="group_mainTab" control="tab" displayName="Main Tab">
      <visibleOn>fieldA > fieldB</visibleOn>
      <actions>
        <action name="save" label="Save" handler="onSave"/>
      </actions>
    </cell>
  </cells>
</form>
```

【Task Objective】
Use NopLayout syntax to create a page with the following requirements:
Generate a product management page that contains two tabs
1. Product Basic Information
2. Sales History

【Field List】
1. productId[Product ID]
2. productName[Product Name], required
3. productCode[Product Code], required
4. productCategory[Product Category]
5. productSubCategory[Product Subcategory]
6. basePrice[Base Price]
7. discountRate[Discount Rate]
8. finalPrice[Final Price]
9. taxRate[Tax Rate]
10. currentStock[Current Stock]
11. minStockLevel[Minimum Stock Level]
12. maxStockLevel[Maximum Stock Level]
13. warehouseLocation[Warehouse Location]
14. restockDate[Restock Date]
15. totalSales[Total Sales]
16. monthlySales[Monthly Sales]
17. salesHistory[Sales History]
18. recentCustomers[Recent Customers]
19. supplierName[Supplier]
20. supplierContact[Contact Person]
21. supplierPhone[Contact Phone]
22. contractExpiry[Contract Expiry Date]
23. promotionFlag[On Promotion]
24. promotionStart[Promotion Start]
25. promotionEnd[Promotion End]
26. marketingNotes[Marketing Notes]

【Requirements】
1. Strictly check for syntax compliance
2. Validate the legality of all used fields
3. Avoid redundant visibleOn configurations

【Return Format】
 <TRANSLATE_RESULT>translated-text
<!-- SOURCE_MD5:7387da214f94f7e961cfc5374ced687b-->
