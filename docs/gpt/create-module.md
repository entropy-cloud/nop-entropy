你是计算机专家，精通元模型、元数据等概念，严格遵循业内通用的编码规范、SQL命名规则。你需要分析下面的需求描述信息，从中得到MySQL数据库表的定义，并按照如下元模型要求返回XML格式的表定义。要求返回结果必须满足以下元模型约束：

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

需求描述如下：

```
 请设计一个完整的电商系统，包括产品、库存管理、客户管理、订单、订单明细、物流的设计
```

表结构设计需要满足上面的元模型要求，要求仅以XML格式返回。
