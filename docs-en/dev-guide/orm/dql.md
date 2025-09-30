Raqsoft has open-sourced a [front-end BI system](http://www.raqsoft.com.cn/r/os-bi) that, at the technical level, proposes a distinctive DQL (Dimensional Query Language). For details, see the Raqsoft Academy article:

[Say Goodbye to Wide Tables, Use DQL to Achieve the New Generation of BI - Raqsoft Academy](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

Raqsoft’s view is that end users find complex SQL JOINs hard to understand. To facilitate multidimensional analysis, they end up using big wide tables, which introduces a series of challenges for data preparation. DQL simplifies the mental model of JOIN operations for end users and offers performance advantages compared to SQL.

Take the example of how to find American employees managed by Chinese managers:

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

The key point here is called foreign key attributization: fields in the table referenced by a foreign key can be directly accessed via sub-attributes, and multi-level and recursive references are allowed.

Another similar example is to query, based on the orders table and the area table, the shipment city name, the corresponding province name, and the region name:

```sql
-- DQL
SELECT
    send_city.name city,
    send_city.pid.name province,
    send_city.pid.pid.name region
FROM
    orders
```

The second key idea of DQL is same-dimension table equivalence. That is, for one-to-one associated tables, you do not need to explicitly write join conditions; their fields can be considered shared. For example, the employee table and the manager table are one-to-one, and we want to query all employees’ income:

```sql
-- SQL
SELECT 员工表.姓名, 员工表.工资 + 经理表.津贴
FROM 员工表
LEFT JOIN 经理表 ON 员工表.编码 = 经理表.编号

-- DQL
SELECT 姓名,工资+津贴
FROM 员工表
```

The third key idea of DQL is treating child tables as collections. For example, the order details table can be regarded as a collection field of the orders table. If we want to calculate the total amount for each order:

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

"If there are multiple child tables, SQL needs to GROUP each separately and then JOIN them back to the main table, often using subqueries; but DQL remains very simple—you can directly add the fields after SELECT."

The fourth key idea of DQL is natural alignment of data by dimension. We don’t need to explicitly specify join conditions. The reason the final data can be displayed in the same table is not because they have some a priori relationship, but simply because they share the leftmost dimension coordinate. For example, we want to aggregate contract amounts, payment amounts, and inventory amounts by date. We need to fetch data from three tables and then align by date, consolidating into the result set.

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

In DQL, dimension alignment can be combined with foreign key attributization, for example:

```sql
-- DQL
SELECT 销售员.count(1),合同表.sum(金额) ON 地区
FROM 销售员 BY 地区
JOIN 合同表 BY 客户表.地区
SELECT 销售员.count(1),合同表.sum(金额) ON 地区
FROM 销售员 BY 地区
JOIN 合同表 BY 客户表.地区
```

From the perspective of NopOrm, the design of DQL is evidently also an ORM design in essence.

1. DQL requires defining primary/foreign key associations via a designer and specifying an explicit display name for each field, which is entirely consistent with ORM model design.

2. DQL’s foreign key attributization, same-dimension equivalence, and child-table-as-collection are essentially the object property association syntax in EQL; it simply uses database association fields as associated object names. This approach is relatively simple, but the drawback is that it is not easy to handle cases involving composite primary key associations.

3. DQL’s dimension alignment is an interesting idea. Its implementation is likely to load data using multiple SQL statements and then perform associations in memory via hash joins, which is fast. Especially for paginated queries, we can paginate only the main table, and then sub-tables fetch only the records involved in the current page via an IN condition. This can significantly speed up queries for large tables.

In the Nop platform, MdxQueryExecutor implements dimension-alignment queries similar to DQL. Since EQL already has built-in support for object property associations, we only need to implement splitting the QueryBean object, sharded execution, and data juxtaposition/merging.

## Execution Logic of MdxQueryExecutor
1. Split out main table fields and associated child-table fields.
2. If the main table fields contain aggregations, then non-aggregated fields are automatically added to the GROUP BY clause and become dimFields. When querying child tables, fetch the results corresponding to the main table’s dimFields and use them as filters to constrain child-table data.
3. Compute the intersection between the main table’s dimFields and the associated child table’s join fields; this becomes the child table’s dimFields. The intersection is taken because associated fields in the main table may have been aggregated away and thus are not part of the final dimFields. dimFields must be included in the select list because they are needed later for joining.
4. For one-to-many associations, include all dimFields in the GROUP BY.
<!-- SOURCE_MD5:4a4c0772808ae9c9e9eb2cc4842ee3d8-->
