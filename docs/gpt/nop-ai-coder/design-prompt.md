【任务目标】
作为软件专家和业务领域专家，分析需求完成表设计

【要求】

1. 不包含User, Role等公共表。
2. 字段名不和SQL关键字冲突。

【返回格式】
返回结果采用如下XML格式

```xml

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
```

sql-type允许的值：VARCHAR, DATE, DATETIME,INTEGER,BIGINT,DECIMAL,BOOLEAN,VARBINARY,BLOB,CLOB

【需求描述】
