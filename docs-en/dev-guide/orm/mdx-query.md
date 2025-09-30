# DQL Queries for OLAP

DQL (Dimensional Query Language) is a multidimensional data query language for OLAP proposed by Raqsoft. It can easily integrate complex master-detail tables into a single wide table for querying. For a detailed introduction, refer to the article from Qian Academy:

[Say goodbye to wide tables: DQL enables next-generation BI - Qian Academy](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

See also [dql.md](dql.md)

In the Nop platform, NopORM also provides a similar query approach. You can query data across multiple tables via QueryBean without needing to worry about relationships between tables.

## Using QueryBean to express multi-table queries

For example, given the following table structure:

```
  NopAuthGroup <--- NopAuthGroupDept ---> NopAuthDept
```

If you want to query the NopAuthGroup table and also return the total number of departments associated with each group, similar to the following SQL:

```sql
 select o.groupId, o.name,
     (select count(g.deptId) from NopAuthGroupDept g where g.groupId= o.groupId) as deptCount
 from NopAuthGroup o
```

You can achieve this with QueryBean:

```javascript
QueryBean query = new QueryBean();
query.setSourceName(NopAuthGroup.class.getName());
query.fields(mainField("groupId"), mainField("name"),
    subField("deptMappings", "deptId").count().alias("deptCount"));
query.addOrderField("name", true);

List<Map<String, Object>> list = ormTemplate.findListByQuery(query);
```

In the code above, mainField denotes a field from the main table, and subField denotes a field from a child table. The first parameter to subField is the association field name, and the second parameter is the field name to query on the child table. count indicates counting the number of child rows, and alias specifies an alias.

The implementation does not generate one complex SQL. Instead, it performs multiple queries in memory and then uses an in-memory HashJoin to merge the data into a single wide table.

```sql
select o.groupId, o.name from NopAuthGroup o;

select g.groupId, count(g.deptId) as deptCount from NopAuthGroupDept g group by g.groupId;
```

This querying approach can greatly simplify complex multi-table queries and improve development efficiency.

## Pagination and composite association properties

QueryBean supports setting offset and limit to perform pagination based on the main table. In addition, because the underlying engine is NopORM rather than a conventional JDBC query engine, it automatically recognizes composite properties and expands them into join relationships across tables.

```javascript
QueryBean query = new QueryBean();
query.fields(mainField("refField.name"), subField("deptMappings","otherRefField.user.name").count().alias("count"));
query.addFilter(FilterBeans.eq("refField.status",1));
query.setOffset(100);
query.setLimit(10);
```

Here we assume the main entity has a to-one association named refField, while deptMappings is a to-many association, and otherRefField is another to-one association on deptMappings.

## Adding complex subqueries

```sql
select
  t1.course_name as c1 ,
  count(o.student_id) as c2
from
  course_selection as o
   join course as t1 on o.course_id = t1.course_id

where
  o.student_id in (
    select
      t.student_id as c3
    from
      student_follow as t

    where
      t.follower_id =  1

  )
group by
  t1.course_name
```

In QueryBean, you can append complex filter conditions via an SQL sub-node:
```javascript
      QueryBean query = new QueryBean();
      query.addField(QueryFieldBean.forField("course.courseName"));
      query.addField(QueryFieldBean.forField("studentId").aggFunc("count").alias("cnt"));
      SQL sql = SQL.begin().sql("o.studentId in (select t.studentId from StudentFollow t where followerId = 1)").end();
      query.addFilter(FilterBeans.sql(sql));
      query.setSourceName("CourseSelection");

      orm().findListByQuery(query);
```

If using the query node in sql-lib:

```xml
<query name="testQueryBean" sqlMethod="findAll">
    <source>
        <sourceName>CourseSelection</sourceName>
        <fields>
            <field name="course.courseName"/>
            <field name="studentId" aggFunc="count" alias="cnt"/>
        </fields>

        <filter>
            <filter:sql xpl:lib="/nop/core/xlib/filter.xlib">
                o.studentId in (select t.studentId from StudentFollow t where followerId = 1)
            </filter:sql>
        </filter>

        <groupBy>
            <field name="course.courseName"/>
        </groupBy>
    </source>
</query>
```

## Managing dynamically constructed QueryBeans in a MyBatis-like sql-lib

```xml

<sql-lib>
    <sqls>
        <query name="queryGroupWithDeptCount" sqlMethod="findList">
            <source>
                <sourceName>NopAuthUser</sourceName>
                <fields>
                    <field name="groupId"/>
                    <field name="name"/>
                    <field owner="deptMappings" name="deptId" aggFunc="count" alias="deptCount"/>
                </fields>
                <filter>
                    <c:if test="${someCondition}">
                        <eq name="status" value="${status}"/>
                    </c:if>
                </filter>
                <orderBy>
                    <field name="name" asc="true"/>
                </orderBy>
            </source>
        </query>
    </sqls>
</sql-lib>
```

The source section of <query> is an XPL template snippet that produces an XML representation of a QueryBean. During generation, you can use XPL tags for further abstraction.

Via sqlMethod you can choose methods such as findList, findFirst, or exists to return a list of data, the first row, or to check for existence, respectively.

## Post-processing data with the Underscore helper

The Underscore.java utility class provides helper functions for collections. For example, leftjoinMerge can perform a join-and-merge of two lists.

```javascript
 Underscore.leftjoinMerge(listA,listB, leftPropName, rightPropName, Arrays.asList(fldB1,fldB2));
```

The call above is equivalent to:

```sql
select listA.*, listB.fldB1,listB.fldB2
from listA, listB
where listA.leftProp =  listB.rightProp
```

## Integrating tablesaw

The nop-tablesaw module integrates the tablesaw computation package. Its functionality is similar to the pandas library in Python and can perform a series of statistical computations on list data.

```javascript
Table table = DataSetHelper.dataSetToTable(dsName, dataSet);
table.numberColumn("count").sum();
```

The IDataSet dataset returned by NopORM queries can be directly converted to a tablesaw Table, after which you can invoke operations such as select/pivot/summarize/count.

> Internally, NopORM unifies all data collection objects under the IDataSet interface and therefore does not expose ResultSet or other interfaces that leak implementation details. NopORM’s core does not have to run on top of JDBC.
<!-- SOURCE_MD5:989c711e21d24c21081e504324ba7f98-->
