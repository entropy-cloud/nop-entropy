# DQL for OLAP Cube Analysis

DQL (Dimensional Query Language) is a data query language developed by Ruan Qian, designed for multi-dimensional data analysis. It simplifies complex hierarchical relationships into flat tables for efficient querying.

For more details, please refer to the article [Farewell to Wide Tables: DQL Powers the Next Generation of BI](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0).

Additionally, please refer to the document [dql.md](dql.md).

In the Nop platform, NopORM also provides similar querying capabilities. Using `QueryBean`, you can query data from multiple tables without worrying about table relationships.

## QueryBean for Multi-Table Queries

For example, in the following table structure:

```
NopAuthGroup <--- NopAuthGroupDept ---> NopAuthDept
```

If you want to query the `NopAuthGroup` table and also retrieve the total count of departments associated with each group, similar to the following SQL statement:

```sql
select o.groupId, o.name,
    (select count(g.deptId) from NopAuthGroupDept g where g.groupId = o.groupId) as deptCount
from NopAuthGroup o
```

This can be implemented using `QueryBean`:

```javascript
QueryBean query = new QueryBean();
query.setSourceName(NopAuthGroup.class.getName());
query.fields(
    mainField("groupId"), 
    mainField("name"),
    subField("deptMappings", "deptId").count().alias("deptCount")
);
query.addOrderField("name", true);

List<Map<String, Object>> list = ormTemplate.findListByQuery(query);
```

In this code:
- `mainField` represents the main table fields.
- `subField` represents the child table fields.
- The first parameter of `subField` is the association field name.
- The second parameter is the field to query in the child table.
- `count()` indicates counting the records in the child table.
- `alias()` sets an alias for the count result.

The actual implementation does not generate a complex SQL statement. Instead, it performs multiple queries in memory and uses a HashJoin to merge the results into a flat structure.

```sql
select o.groupId, o.name from NopAuthGroup o;

select g.groupId, count(g.deptId) as deptCount from NopAuthGroupDept g group by g.groupId;
```

This approach significantly simplifies complex multi-table queries and improves development efficiency.

## Pagination and Composite Relationships

`QueryBean` supports setting `offset` and `limit` properties for page-based queries. Additionally, since the underlying query engine is NopORM (not JDBC), it automatically identifies composite relationships and expands them into join relationships.

For example:

```javascript
QueryBean query = new QueryBean();
query.fields(
    mainField("refField.name"),
    subField("deptMappings", "otherRefField.user.name").count().alias("count")
);
query.addFilter(new FilterBeans().eq("refField.status", 1));
query.setOffset(100);
query.setLimit(10);
```

Here, `refField` is a `to-one` association on the main entity, while `deptMappings` is a `to-many` association. `otherRefField` is another `to-one` association related to `deptMappings`.

## Adding Complex Subqueries
[EndOfData]
```markdown

## SQL Query for Course Selection
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
      t.follower_id = 1
  )
group by
  t1.course_name
```
``` 


In `Sql-Lib`, you can dynamically build complex filter conditions through the `QueryBean` class. Here's an example implementation:

```javascript
QueryBean query = new QueryBean();
query.addField(
    QueryFieldBean.forField("course.courseName")
);
query.addField(
    QueryFieldBean.forField("studentId")
        .aggFunc("count")
        .alias("cnt")
);
Sql sql = Sql.begin()
    .sql("o.studentId in (select t.studentId from StudentFollow t where followerId = 1)");
sql.end();
query.addFilter(FilterBeans.sql(sql));
query.setSourceName("CourseSelection");
return orm().findListByQuery(query);
```


If you're working with `Sql-Lib`, here's how the query would look:

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



```markdown

## SQL Library Configuration


## Query Definitions


- **SQL Method**: findList
- **Source Definition**: NopAuthUser
  - **Fields**:
    - groupId
    - name
    - deptId (Alias: deptCount)
      - Aggregation Function: count
  - **Filters**:
    - Condition: someCondition
      - Subcondition: status == "${status}"
- **Ordering**: name ascending




- The `Underscore.java` utility class provides methods for collection operations.
- Example usage:
  ```javascript
  Underscore.leftjoinMerge(listA, listB, "leftProp", "rightProp", Arrays.asList(fldB1, fldB2));
  ```
- This is equivalent to:
  ```sql
  SELECT listA.*, listB.fldB1, listB.fldB2 
  FROM listA, listB 
  WHERE listA.leftProp = listB.rightProp;
  ```




- The `nop-tablesaw` module integrates the `tablesaw` package.
- Functionality similar to Python's pandas library for list data analysis.
- Example usage:
  ```javascript
  Table table = DataSetHelper.dataSetToTable(dsName, dataSet);
  table.numberColumn("count").sum();
  ```
- NopORM-generated `IDataSet` can be directly converted to tablesaw's `Table` interface.
  - This allows calling methods like `select/pivot/summarize/count`.

> Note: NopORM internally wraps all data access in the `IDataSet` interface, preventing direct access to ResultSet objects.

