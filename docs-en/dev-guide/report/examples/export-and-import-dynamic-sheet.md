
# NopReport Example: Dynamic Sheets and Dynamic Columns

Tutorial video: https://www.bilibili.com/video/BV1fKXkYWEQJ/

NopReport is a very powerful Chinese-style reporting engine that supports various complex report formats. Reports that commercial engines like FineReport can produce all have simple corresponding solutions in NopReport.
For a detailed introduction, see [NopReport: An open-source Chinese-style reporting engine using Excel as the designer](https://mp.weixin.qq.com/s/_nKUiryetF2O5zSrPfU8FQ)

Unlike typical commercial reporting engines, in addition to flat DataSet table structures, NopReport also supports directly using complex nested object structures as data sources, generating reports from domain objects.

The following example demonstrates how to dynamically generate multiple sheets from complex JSON data, with each sheet containing multiple dynamically generated data columns.

![](dynamic-sheet/dynamic-sheet-and-col.png)

The input data is a complex nested structure:

```json
[
  {
    "name": "苹果",
    "list": [
      {
        "单价": "3.2",
        "产地": "山东",
        "属性": {
          "克重": "100",
          "大小": "中",
          "颜色": "红"
        }
      }
    ]
  },
  {
    "name": "香蕉",
    "list": [
      {
        "单价": "2.1",
        "产地": "山东",
        "属性": {
          "长度": "150",
          "成熟度": "中等",
          "存储": "低温"
        }
      }
    ]
  }
]
```

## Export Template

### Sheet Configuration

In a NopReport template, you can assign a corresponding report model to each sheet. For example, the model configuration for sheet1 is placed on the `sheet1-XptSheetModel` page.

![](dynamic-sheet/xpt-sheet-model.png)

* [Build Loop Variable] is used to configure an Xpl template snippet that returns a data list. During report export, the engine iterates over this list to generate multiple sheets. If the list is empty, the sheet generation is skipped. In the example, data returns the data variable from the current context, which is a List.

* [Loop Variable Name] specifies the loop variable used during generation. In the example, data is a List, and each entry’s variable name is entity. If [Loop Variable Name] is not specified, the default is `sheetLoopVar`. During looping, the current index variable name is `sheetLoopIndex`.

* [Sheet Name Expression] specifies the name of the dynamically generated Excel sheet. In the example, entity.name takes the name property from the current loop variable as the sheet name.

### Cell Expansion Configuration

![](dynamic-sheet/dynamic-expand-col.png)

Row expansion configuration on cell A3:

* `expandType=r` means row expansion
* `expandExpr=entity.list` means to take the list property from the current entity in context as the loop expansion data

Column expansion configuration on cell D2:

* `expandType=c` means column expansion
* `expandExpr=entity.list[0].属性.keySet()`
  means to take the [属性] field of the first entry in the list of the current entity in context. This field is a Map, and its key set is the collection of dynamic attribute names. In practice, other ways may be used to obtain dynamic attribute names.
* `*=产地` is a shorthand of valueExpr, used to read a specified property from the current expandedValue as the cell value.

Cell D3 obtains the dynamic column value via valueExpr with `valueExpr=cell.rp.ev.属性[cell.cp.ev]`

* cell.rp denotes the row parent cell of the current cell, i.e., the leftmost expansion cell A3
* ev means the expanded value of the cell, i.e., a row within entity.list
* cell.cp denotes the column parent cell of the current cell, i.e., the expansion cell D2 above
* cell.cp.ev corresponds to the dynamic attribute name. `cell.rp.ev.属性[cell.cp.ev]` means fetching the 属性 field from the current row, then using the attribute name to read the corresponding value.

## Import Configuration

The Nop platform provides the ImportModel. By configuring the `imp.xml` model file, you can parse Excel files without writing code.

The TestImportExcelModel unit test provides a `test-dynamic-sheet-and-col.imp.xml` configuration, which can be used to parse the Excel data file exported in the previous section.

```xml
<imp x:schema="/nop/schema/excel/imp.xdef" xmlns:x="/nop/schema/xdsl.xdef"
>

    <sheets>

        <sheet name="数据" field="data" list="true" namePattern=".*"
               multipleAsMap="true" multiple="true"
               noSeqCol="true" headerRowCount="2">

            <fields>
                <field name="品牌" displayName="品牌">

                </field>

                <field name="单价" mandatory="true">
                    <schema stdDomain="double"/>
                </field>

                <field name="产地" mandatory="true">

                </field>

                <field name="dynamicProp" virtual="true">
                    <valueExpr>
                        let map = record.makeMap('属性')
                        map[fieldLabel] = value;
                    </valueExpr>
                </field>
            </fields>

            <!-- Map all dynamic columns to the dynamicProp field for processing.
             The dynamicProp field is a virtual field; it executes valueExpr to perform data structure transformation.
             -->
            <fieldDecider>
                'dynamicProp'
            </fieldDecider>
        </sheet>

    </sheets>

    <normalizeFieldsExpr>
      const list = [];
      rootRecord['data'].forEach((k,v) => {
      list.push({name:k,list:v})
      })

      rootRecord.data = list;
    </normalizeFieldsExpr>

</imp>
```

* `field=data` stores the parsed data in the data variable
* `namePattern=".*" multiple=true multipleAsMap=true` means matching multiple sheets and parsing them into a Map whose keys are the sheet names
* `list="true"` means the sheet contains a list of data
* `headerRowCount="2"` means the header occupies the first two rows; data starts from the third row
* `noSeqCol="true"` means there is no sequence column; parsing starts from the third row until an empty row is encountered. If noSeqCol is not configured or set to false, the first column must be a sequence column with numeric content. Parsing stops when the sequence column becomes empty.

Dynamic column parsing uses the fieldDecider configuration

* In fieldDecider, you can decide whether a field is a dynamic column based on conditions such as fieldLabel, and if so, which parsing configuration to use. In the example, it always returns `'dynamicProp'`, indicating that dynamic columns are parsed using the dynamicProp field configuration.
* The dynamicProp field is a virtual field that executes valueExpr to perform data structure transformation. In the example, `record.makeMap('属性')` creates a Map object, and the Map’s values are set based on fieldLabel and value.

The parsed data is a Map structure, but the desired return type is `List<Map>`, so normalizeFieldsExpr is used to convert the Map to a List.

By calling `ExcelHelper.loadXlsxObject(impModelPath,excelResource)`, you can parse the Excel file into a bean object. The actual return type is DynamicObject, which is similar to a Map.

```javascript
 IResource resource = attachmentResource("test-dynamic-sheet-and-col.xlsx");
 Object bean = ExcelHelper.loadXlsxObject(
       "/nop/test/imp/test-dynamic-sheet-and-col.imp.xml", resource);
 assertEquals(attachmentJsonText("test-dynamic-sheet-and-col.json"),
       JsonTool.serialize(bean, true));
```

<!-- SOURCE_MD5:c3a36171bd2385df8e73c1bdbbbd7c7f-->
