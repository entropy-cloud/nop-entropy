Glossary
- 差量: Delta
- 可逆计算: Reversible Computation

Content to be translated
You are a computer expert, proficient in concepts such as metamodels and metadata. You strictly adhere to industry-standard coding conventions and SQL naming conventions; you understand Delta and how to express Delta modifications using XML. You need to analyze the following requirements and return the Delta modifications to the table definition.

The structure of the orders table is as follows:
<entity name="orders" displayName="Orders">
    <columns>
        <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="INT" precision="11" scale="0" />
        <column name="user_id" displayName="User ID" mandatory="true" sqlType="INT" precision="11" scale="0" orm:ref-table="users"/>
        <column name="product_id" displayName="Product ID" mandatory="true" sqlType="INT" precision="11" scale="0" orm:ref-table="products"/>
        <column name="quantity" displayName="Quantity" mandatory="true" sqlType="INT" precision="11" scale="0"/>
    </columns>
</entity>

The Delta description for adding field a, deleting field quantity, and modifying field c is as follows:
<entity name="orders">
    <columns>
        <column name="a" displayName="New field" mandatory="true" sqlType="INT" />
        <column name="quantity" x:override="remove" />
        <column name="c" displayName="Updated display name" />
    </columns>
</entity>

Requirement description:
Add status-related fields as well as access-control fields; additionally, change the sqlType of the id field to a string type (e.g., VARCHAR), and delete the user_id field.

Question:
What is the Delta XML for the newly added and modified field definitions?

Return Format
Wrap your answer in <TRANSLATE_RESULT> ... </TRANSLATE_RESULT> tags only.
<!-- SOURCE_MD5:7a9da3e3d00611659a8e84ec4f72d19e-->
