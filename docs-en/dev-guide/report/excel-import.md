# Excel Data Import and Export

In the Nop platform, you only need to add an IMP model in the `imp.xml` import configuration to achieve the automatic parsing of complex business objects stored in Excel. For detailed information on the IMP model definition, refer to [imp.xdef](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xdefs/src/main/resources/_vfs/nop/schema/excel/imp.xdef).

## Basic Principle

* **For regular fields**: Parsing is done based on the label (left side) and value (right side).
* **For list fields**: Parsing is performed by matching the label (above) and values (below).
* **The first column of a list must be a numeric column, not necessarily unique, and it does not need to be defined in the field list. It is solely used to determine the range of rows.**
* **Key point**: The overall structure should allow for clear identification of parent-child relationships. Parent fields must cover the range of child fields. This ensures automatic parsing.
* **The label can correspond to either `field.displayName` or `name`, whichever is available.**
* **The order of fields does not affect parsing.** In the `imp.xml`, all defined fields are considered. You can use a subset in the template if needed.
* **Mandatory fields**: These must be present in the template; otherwise, parsing will find missing values and throw an error.

## Configuration Explanation

## How to Parse Lists

* **Use `list="true"` or `sheet`/`field` attributes to indicate that a field is a list.**
* **The first column of a list must be a numeric column, not necessarily unique, and does not need to be defined in the field list. It is solely used to determine the range of rows.**

## How to Parse a Group of Sheets into a Single Value

```xml
<sheet name="ss" namePattern=".*" multiple="true" multipleAsMap="true" field="ss">
</sheet>
```

* **namePattern**: Specifies a regular expression for matching sheets.
* **multiple="true"`: Indicates that multiple sheets will be matched.
* **multipleAsMap="true"`: When multiple sheets are matched, their data is aggregated into a map. If this attribute is not specified or set to `false`, the results of multiple sheets are merged into a list.
* **field**: The name of the field to parse.

## How to Export Using Import Templates

1. First, create an empty import template by deleting all data rows while keeping the column header row. For example, refer to [template.orm.xlsx](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-orm-model/src/main/resources/_vfs/nop/orm/imp/template.orm.xlsx).
2. In the `imp.xml` file, specify the path to the import template using the `templatePath` attribute.

```xml
<imp x:schema="/nop/schema/excel/imp.xdef" xmlns:x="/nop/schema/xdsl.xdef" xmlns:c="c" xmlns:xpt="xpt"
     templatePath="template.orm.xlsx">
</imp>
```

The ExcelReportHelper class provides methods to generate HTML or XLSX files based on imported business data.

```javascript
Object bean = ExcelHelper.loadXlsxObject("/nop/test/imp/test5.imp.xml", resource);
String html = ExcelReportHelper.getHtmlForXlsxObject(impModelPath, bean, scope);
ExcelReportHelper.saveXlsxObject(impModelPath, resource, bean);
```

Through the IMP model's functionality in Java, Excel can be treated as a serialized form of Java objects, similar to JSON serialization. This allows for automatic, bidirectional conversion between Excel and Java objects.

For detailed examples, refer to [TestImportExcelModel.java](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-biz/src/test/java/io/nop/biz/impl/TestImportExcelModel.java).

## Dynamically Determining Required Columns

The following configuration example demonstrates how to dynamically determine the parsing configuration for data columns. For instance, dynamic columns such as "2002 Indicator" and "2003 Indicator" can be parsed and converted into a list attribute.

![Dynamic Column Parsing](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-biz/src/test/resources/_vfs/nop/test/imp/test3.imp.xml)

```xml
<field name="columns" displayName="Project Indicators" list="true">
    <fields>
        <field name="name" displayName="Indicator" mandatory="true"/>

        <field name="indexValue" displayName="X Year Indicators" virtual="true">
            <schema stdDomain="int"/>
            <valueExpr>
                // If it's the first access to the 'indexValues' attribute, automatically create a List
                let list = record.makeList('indexValues')
                let year = fieldLabel.$removeTail('Indicator').$toInt()
                list.add({ year, value })
            </valueExpr>

            <xpt:labelExpandExpr>
                <!-- Incoming year list data -->
                indexYears
            </xpt:labelExpandExpr>

            <xpt:labelValueExpr>
                // Dynamically construct field display name based on expanded value
                cell.ev + 'Indicator'
            </xpt:labelValueExpr>

            <xpt:valueExpr>
                _.findWhere(cell.rp.ev.indexValues, 'year', cell.cp.ev.$toInt()).value
            </xpt:valueExpr>
        </field>

    </fields>

    <!-- If the field label ends with 'Indicator', apply the 'indexValue' parsing rule -->
    <fieldDecider>
        fieldLabel.endsWith("Indicator") ? "indexValue" : null
    </fieldDecider>
</field>
```

* virtual=true indicates a virtual field. During import, only the valueExpr of the field is executed, but the resulting value is not set to the record's properties.

* When executing valueExpr, you can reference the field's label using fieldLabel and retrieve the value from the cell using value. The cell can be accessed via cell.ev (expanded value).

* xpt:labelExpandExpr tags are used to dynamically construct field labels based on expanded values. For example, indexYears is used to display year list data.

* xpt:valueExpr tags are used to dynamically find and return the corresponding cell value based on the expanded value. For instance, cell.rp.ev.indexValues refers to the parent row's expanded indexValues, and cell.cp.ev.$toInt() converts the value to an integer.

* The fieldDecider rule checks if the field label ends with "Indicator" and applies the indexValue parsing rule accordingly.

---


## Dynamic Cell Styling

During data export, dynamic styling can be applied to cells. For example, if a cell's value exceeds 300, you can set its background color to red.

![Dynamic Column Parsing](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-biz/src/test/resources/_vfs/nop/test/imp/import-dynamic-col.png)

```xml
<field name="columns" displayName="Project Indicators" list="true">
    <fields>
        <field name="name" displayName="Indicator" mandatory="true"/>

        <field name="indexValue" displayName="X Year Indicators" virtual="true">
            <schema stdDomain="int"/>
            <valueExpr>
                // If it's the first access to the 'indexValues' attribute, automatically create a List
                let list = record.makeList('indexValues')
                let year = fieldLabel.$removeTail('Indicator').$toInt()
                list.add({ year, value })
            </valueExpr>

            <xpt:labelExpandExpr>
                <!-- Incoming year list data -->
                indexYears
            </xpt:labelExpandExpr>

            <xpt:labelValueExpr>
                // Dynamically construct field display name based on expanded value
                cell.ev + 'Indicator'
            </xpt:labelValueExpr>

            <xpt:valueExpr>
                _.findWhere(cell.rp.ev.indexValues, 'year', cell.cp.ev.$toInt()).value
            </xpt:valueExpr>
        </field>

    </fields>

    <!-- If the field label ends with 'Indicator', apply the 'indexValue' parsing rule -->
    <fieldDecider>
        fieldLabel.endsWith("Indicator") ? "indexValue" : null
    </fieldDecider>
</field>
```

* virtual=true indicates a virtual field. During import, only the valueExpr of the field is executed, but the resulting value is not set to the record's properties.

* When executing valueExpr, you can reference the field's label using fieldLabel and retrieve the value from the cell using value. The cell can be accessed via cell.ev (expanded value).

* xpt:labelExpandExpr tags are used to dynamically construct field labels based on expanded values. For example, indexYears is used to display year list data.

* xpt:valueExpr tags are used to dynamically find and return the corresponding cell value based on the expanded value. For instance, cell.rp.ev.indexValues refers to the parent row's expanded indexValues, and cell.cp.ev.$toInt() converts the value to an integer.

* The fieldDecider rule checks if the field label ends with "Indicator" and applies the indexValue parsing rule accordingly.

---

```xml
<field>
    <xpt:labelStyleIdExpr>
        cell.ev == 2002 ? 'red' : null
    </xpt:labelStyleIdExpr>

    <xpt:styleIdExpr>
        cell.value > 300 ? 'red' : null
    </xpt:styleIdExpr>
</field>
```

In the data template, you need to add an XptWorkbookModel sheet. Define named styles in it.
![excel-import/named-styles.png](https://via.placeholder.com/800x600)

The actual export result is:
![excel-import/export-with-style.png](https://via.placeholder.com/800x600)

## Support for grouped headers

When importing data, you can support multi-level headers (currently only two levels are supported).
![excel-import/template-group-header.png](https://via.placeholder.com/800x600)

The export result is:
![excel-import/group-header-export.png](https://via.placeholder.com/800x600)

In the imp.xml import model configuration, only consider matching the lowest level fields. Group fields will not directly participate in matching.

Add a groupField configuration to the field settings. It corresponds to another field configuration. You can configure `xpt:labelExpandExpr` and other export configurations within it.
```xml
<fields>
    <field name="indexValue" displayName="X年" virtual="true" groupField="group">
        <schema stdDomain="int"/>
        <valueExpr>
            let list = record.makeList('indexValues')
            let year = fieldLabel.$removeTail('年').$toInt()
            let group = labelData.groupLabel
            list.add({ year, value, group })
        </valueExpr>

        <xpt:labelExpandExpr>
            indexYears
        </xpt:labelExpandExpr>

        <xpt:labelValueExpr>
            cell.ev + '年'
        </xpt:labelValueExpr>

        <xpt:valueExpr>
            // cell.cp.cp represents the parent of the current cell's parent
            // cp stands for colParent
            // This expression dynamically expands the column hierarchy
            _.findWhere(cell.rp.ev.indexValues, { 
                year: cell(cp).ev.$toInt(), 
                group: cell(cp).cp.value 
            })?.value
        </xpt:valueExpr>

        <xpt:labelStyleIdExpr>
            cell(ev) == 2002 ? 'red' : null
        </xpt:labelStyleIdExpr>

        <xpt:styleIdExpr>
            cell(value) > 300 ? 'red' : null
            <!--cell(value) == 'A2' ? 'red' : null-->
        </xpt:styleIdExpr>
    </field>

    <field name="group" displayName="group">
        <schema stdDomain="string"/>
        <xpt:labelExpandExpr>
            groups
        </xpt:labelExpandExpr>
    </field>
</fields>
```


## Integration with Spring Framework

To use the Excel import/export functionality of the Nop platform, you need to add the following modules to your project's pom.xml file:

```xml
<dependencies>
  <!-- Implement Nok platform integration with Spring framework -->
  <dependency>
    <groupId>io.github_entropy</groupId>
    <artifactId>nop-spring-core-starter</artifactId>
  </dependency>

  <!-- Excel parsing and report engine support -->
  <dependency>
    <groupId>io.github_entropy</groupId>
    <artifactId>nop-report-core</artifactId>
  </dependency>
</dependencies>

```

For specific example project, see [nop-spring-report-demo](https://gitee.com/canonical-entropy/nop-entropy/tree/master/nop-demo/nop-spring-report-demo)
