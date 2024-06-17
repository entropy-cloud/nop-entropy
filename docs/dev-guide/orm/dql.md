润乾公司开源了一个[前端BI系统](http://www.raqsoft.com.cn/r/os-bi)，它在技术层面提出了一个别致的DQL(Dimentional Query Language)语言。具体介绍可以参考乾学院的文章

[告别宽表，用 DQL 成就新一代 BI - 乾学院](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

润乾的观点是终端用户难以理解复杂的SQL JOIN，为了便于多维分析，只能使用大宽表，这为数据准备带来一系列困难。而DQL则是简化了对终端用户而言JOIN操作的心智模型，并且在性能上相比于SQL更有优势。

以如何查找**中国经理的美国员工**为例

```sql
-- SQL
SELECT A.*
FROM  员工表 A
JOIN 部门表  ON A.部门 = 部门表.编号
JOIN  员工表 C ON  部门表.经理 = C.编号
WHERE A.国籍 = '美国'  AND C.国籍 = '中国'

-- DQL
SELECT *
FROM 员工表
WHERE 国籍='美国' AND 部门.经理.国籍='中国'
```

这里的关键点被称为：外键属性化，也就是说外键指向表的字段可直接用子属性的方式引用，也允许多层和递归引用。

另一个类似的例子是根据订单表 (orders)，区域表(area)，查询订单的发货城市名称、以及所在的省份名称、地区名称。

```sql
-- DQL
SELECT
    send_city.name city,
    send_city.pid.name province,
    send_city.pid.pid.name region
FROM
    orders
```

DQL的第二个关键思想是：**同维表等同化**，也就是一对一关联的表，不用明确写关联查询条件，可以认为它们的字段是共享的。例如，员工表和经理表是一对一的，我们需要查询**所有员工的收入**

```sql
-- SQL
SELECT 员工表.姓名, 员工表.工资 + 经理表.津贴
FROM 员工表
LEFT JOIN 经理表 ON 员工表.编码 = 经理表.编号

-- DQL
SELECT 姓名,工资+津贴
FROM 员工表
```

DQL的第三个关键思想是：**子表集合化**，例如订单明细表可以看作是订单表的一个集合字段。如果要计算每张订单的汇总金额，

```sql
-- SQL
SELECT T1.订单编号,T1.客户,SUM(T2.价格)
FROM 订单表T1
JOIN 订单明细表T2 ON T1.订单编号=T2.订单编号
GROUP BY T1.订单编号,T1.客户

-- DQL
SELECT 订单编号,客户,订单明细表.SUM(价格)
FROM 订单表
```

"如果有多个子表时，SQL 需要分别先做 GROUP, 然后在一起和主表 JOIN 才行，会写成子查询的形式，但是 DQL 则仍然很简单，SELECT 后直接再加字段就可以了"。

DQL的第四个关键思想是：**数据按维度自然对齐**。我们不用特意指定关联条件，最终数据之所以能够放在同一张表里展示，原因不是因为它们之间存在什么先验的关联关系，仅仅是因为它们共享了最左侧的维度坐标而已。例如：我们希望**按日期统计合同额、回款额和库存金额**。我们需要从三个表分别取数据，然后按照日期对齐，汇总到结果数据集中。

```sql
-- SQL
SELECT T1.日期,T1.金额,T2.金额, T3.金额
FROM (SELECT  日期, SUM(金额) 金额  FROM  合同表  GROUP  BY  日期）T1
LEFT JOIN (SELECT  日期, SUM(金额) 金额  FROM  回款表  GROUP  BY  日期）T2
ON T1.日期 = T2.日期
LEFT JOIN (SELECT  日期, SUM(金额) 金额  FROM  库存表  GROUP  BY  日期 ) T3
ON T2.日期 = T3.日期

-- DQL
SELECT 合同表.SUM(金额),回款表.SUM(金额),库存表.SUM(金额) ON 日期
FROM 合同表 BY 日期
LEFT JOIN 回款表 BY 日期
LEFT JOIN 库存表 BY 日期
```

在 DQL 中，维度对齐可以和外键属性化结合，例如

```sql
-- DQL
SELECT 销售员.count(1),合同表.sum(金额) ON 地区
FROM 销售员 BY 地区
JOIN 合同表 BY 客户表.地区
SELECT 销售员.count(1),合同表.sum(金额) ON 地区
FROM 销售员 BY 地区
JOIN 合同表 BY 客户表.地区
```

如果从NopOrm的角度去看DQL的设计，则显然DQL本质上也是一种ORM的设计。

1. DQL需要通过设计器定义主外键关联，并为每个字段指定界面上的显式名称，这一做法完全与ORM模型设计相同。

2. DQL的外键属性化、同维等同化和子表集合化本质上就是EQL语法中的对象属性关联语法，只是它直接用数据库的关联字段作为关联对象名。这种做法比较简单，但缺点是对于复合主键关联的情况不太好处理。

3. DQL的维度对齐是一个有趣的思想。它的具体实现应该是分多个SQL语句去加载数据，然后在内存中通过Hash Join来实现关联，速度很快。特别是在分页查询的情况下，我们可以只对主表进行分页查询，然后其他子表通过in条件只取本页数据涉及到的记录即可，在大表的情况下有可能加速很多。

Nop平台中的MdxQueryExecutor实现了类似DQL的维度对齐查询。因为EQL已经内置支持了对象属性关联，所以只要实现对QueryBean对象的拆分、分片执行、数据并置融合就可以了。

## MdxQueryExecutor的执行逻辑
1. 拆分出主表字段和关联子表字段
2. 如果主表字段包含汇总自动，则非汇总的字段自动被加入group by部分，并成为dimFields。查询子表的时候会取主表dimFields对应的结果，然后作为filter过滤子表数据
3. 求取主表的dimFields和关联子表的关联字段的交集，则成为子表的dimFields。取交集是因为主表中的关联字段可能被汇总掉了，从而不是最终的dimFields的一部分。dimFields必须在选择列表中，因为最后需要利用这些字段进行关联。
4. 如果是一对多关联，则dimFields全部加入group by
