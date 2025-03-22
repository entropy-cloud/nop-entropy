# Dynamic Column Expansion

## Report Requirements:
- Display based on predefined component lists
- Each row shows components if there is a corresponding match
- Refer to the image for visual representation
![](dynamic-col/dynamic-col.png)

## Backend Data:

```javascript
let ds1 = [
  { "Name": "A", "Specification": "x", chenfen: [ { name: "001", weight: 3 }, { name: "003", weight: 4 } ] },
  { "Name": "B", "Specification": "y", chenfen: [ { name: "001", weight: 5 }, { name: "005", weight: 6 } ] }
];
let chenfenList = [
  { value: "001", name: "Component 1" },
  { value: "002", name: "Component 2" },
  { value: "003", name: "Component 3" },
  { value: "004", name: "Component 4" },
  { value: "005", name: "Component 5" },
  { value: "006", name: "Component 6" }
];
```

[View Report Template](https://gitee.com/canonical-entropy/nop-entropy/raw/master/nop-report/nop-report-demo/src/main/resources/_vfs/nop/report/demo/base/08-%E5%8A%A8%E6%80%81%E5%B1%95%E5%BC%80%E5%88%97.xpt.xlsx)

## Basic Approach:

1. Use expandExpr to trigger expansion based on predefined lists
2. Access parent cells using cell.rowParent and cell.colParent
3. Utilize Underscore.js functions in valueExpr for advanced lookups

## Detailed Steps:

### 1. In A4 Cell:
- Configure row expansion
- Use ds1 for component lookup
- valueExpr = cell.ei + 1
![](dynamic-col/row-expand.png)

*expandType*: r  
*expandExpr*: ds1  
*valueExpr*: cell.ei + 1

### 2. In D3 Cell:
- Configure column expansion
- Use chenfenList for component lookup
- valueExpr = cell.ev.name
![](dynamic-col/expand-col.png)

*expandType*: c  
*expandExpr*: chenfenList  
*valueExpr*: cell.ev.name  
*colParent*: D5

### 3. In D2 Cell:
- Configure parent row expansion
- Use colParent for dynamic lookup
- valueExpr = cell.cp.ei + 1
![](dynamic-col/col-parent.png)

*colParent*: D3  
*valueExpr*: cell.cp.ei + 1

### 4. In D4 Cell:
- Use advanced expressions
- Combine multiple lookups using Underscore.js
*valueExpr*: (`_.findWhere(cell.rp.ev.chenfen, { name: "name" }, cell.cp.ev.value)?.weight`) || 0) + 'g'
![](dynamic-col/cell-value.png)
