You are a computer expert, proficient in concepts such as metamodels and metadata, and you strictly follow industry-standard coding conventions and SQL naming rules. You need to analyze the requirement description below to derive MySQL database table definitions, and return the table definitions in XML format according to the metamodel requirements below. The returned result must satisfy the following metamodel constraints:

```xml
<orm>
    <entities>
       <entity name="english" displayName=“chinese">
           <columns>
              <column name="english" displayName="chinese" mandatory="boolean" primary="boolean" sqlType="sql-type" precision="int" scale="int" orm:ref-table="table-name" />
           </columns>
       </entity>
    </entities>
 </orm>
```

The requirement description is as follows:

```
 Please design a complete e-commerce system, including the design of products, inventory management, customer management, orders, order details, and logistics
```

The table structure design needs to meet the metamodel requirements above, and the result must be returned in XML format only.
<!-- SOURCE_MD5:9592f0791928166868287f44a3b8cc18-->
