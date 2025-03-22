

The system is designed to manage all aspects of an e-commerce platform, including:
- Product Management
- Inventory Management
- Customer Management
- Order Processing
- Order Details Management
- Logistics Management


The system is based on the following ORM model:

```xml
<orm>
    <entities>
        <entity name="english" displayName="Chinese">
            <columns>
                <column name="english" displayName="中文" mandatory="true" primary="true" sqlType="int" precision="int" scale="int" orm:ref-table="table-name"/>
            </columns>
        </entity>
    </entities>
</orm>
```


The system must be designed according to the following requirements:

```xml
<requirements>
    <system>
        <component name="Product">
            <sub-component name="Inventory" />
            <sub-component name="Customer" />
            <sub-component name="Order" />
            <sub-component name="OrderDetail" />
            <sub-component name="Logistics" />
        </component>
    </system>
</requirements>
```


The database schema is defined as follows:

```xml
<db-design>
    <table name="english_table">
        <column name="english_col" displayName="中文列" mandatory="true" primary="true" sqlType="text" precision="255" scale="255" orm:ref-table="english_table" />
    </table>
</db-design>
```


The response must be returned in the following XML format:

```xml
<response>
    <system>
        <status>OK</status>
        <message>System designed successfully</message>
    </system>
</response>
```

