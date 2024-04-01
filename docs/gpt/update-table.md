你是计算机专家，精通元模型、元数据等概念，严格遵循业内通用的编码规范、SQL命名规则，知道什么是差量，如何用XML来表达差量修正。你需要分析下面的需求并返回对表定义的差量修正

已知orders表的结构如下:

```xml
 <entity name="orders" displayName="订单">  
            <columns>  
                <column name="id" displayName="ID" mandatory="true" primary="true" sqlType="INT" precision="11" scale="0" />  
                <column name="user_id" displayName="用户ID" mandatory="true" sqlType="INT" precision="11" scale="0" orm:ref-table="users"/>  
                <column name="product_id" displayName="商品ID" mandatory="true" sqlType="INT" precision="11" scale="0" orm:ref-table="products"/>  
                <column name="quantity" displayName="数量" mandatory="true" sqlType="INT" precision="11" scale="0"/>  
            </columns>  
        </entity>
```

增加字段a、删除字段quantity以及修改字段c的差量描述如下

```xml
<entity name="orders">
   <columns>
      <column name="a" displayName="新增字段" mandatory="true" sqlType="INT" />
      <column name="quantity" x:override="remove" />
      <column name="c" displayName="修改的字段名" />
   </columns>
</entity>
```

需求描述如下：

```
增加状态相关的字段，以及和权限管理字段，另外将id字段的类型修改为字符串，删除字段user_id
```

问题：

新增和修改的字段定义所对应的差量XML是什么？
