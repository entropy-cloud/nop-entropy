【Task Objective】
As a software and domain expert, analyze the requirements and design the database tables.

【Requirements】
1. Do not include common/public tables such as User and Role.
2. Field names must not conflict with SQL reserved keywords.

【Return Format】
Return the result in the following XML format

<orm>
  <entities>
    <entity name="english" displayName="chinese">
      <comment>string</comment>
      <columns>
        <column name="english" displayName="chinese" mandatory="boolean" primary="boolean"
                sqlType="sql-type" precision="int" scale="int" orm:ref-table="table-name"/>
      </columns>
    </entity>
  </entities>
</orm>

Allowed values for sql-type: VARCHAR, DATE, DATETIME, INTEGER, BIGINT, DECIMAL, BOOLEAN, VARBINARY, BLOB, CLOB

【Requirement Description】
<!-- SOURCE_MD5:5da1b57dd396c75c375df7b3f7ec00b3-->
