# 面向OLAP的DQL查询

DQL(Dimentinal Query Language)语言是润乾公司提出的一种面向OLAP的多维数据查询语言，可以将复杂的主子表很简单的整合为一个大宽表来查询。具体介绍可以参考乾学院的文章

[告别宽表，用 DQL 成就新一代 BI - 乾学院](http://c.raqsoft.com.cn/article/1653901344139?p=1&m=0)

另外可以参见[dql.md](dql.md)

在Nop平台中，NopORM也提供了类似的查询方式，可以通过`QueryBean`来查询多个表的数据，而不需要关心表之间的关联关系。

## QueryBean表达多表查询

比如对于下面的表结构

```
  NopAuthGroup <--- NopAuthGroupDept ---> NopAuthDept
```

如果要查询`NopAuthGroup`表并同时返回每个分组关联的部门的总数，类似于下面的SQL语句

```sql
 select o.groupId, o.name,
     (select count(g.deptId) from NopAuthGroupDept g where g.groupId= o.groupId) as deptCount
 from NopAuthGroup o
```

可以通过`QueryBean`来实现

```javascript
QueryBean query = new QueryBean();
query.setSourceName(NopAuthGroup.class.getName());
query.fields(mainField("groupId"), mainField("name"),
    subField("deptMappings", "deptId").count().alias("deptCount"));
query.addOrderField("name", true);

List<Map<String, Object>> list = ormTemplate.findListByQuery(query);
```

上面的代码中，`mainField`表示主表字段，`subField`
表示子表字段，subField的第一个参数是关联的字段名，第二个参数是要查询的子表上的字段名，`count`表示统计子表的总数，`alias`表示别名。

实际的实现原理不是生成一个复杂SQL，而是在内存中分成多个查询，然后在内存中通过HashJoin来把数据整合为一个大宽表。

```sql
select o.groupId, o.name from NopAuthGroup o;

select g.groupId, count(g.deptId) as deptCount from NopAuthGroupDept g group by g.groupId;
```

这样的查询方式可以大大简化复杂的多表查询，提高开发效率。

## 分页和复合关联属性

`QueryBean`支持设置offset和limit属性，从而基于主表进行分页查询。此外，因为底层的运行引擎是NopORM，而不是普通的JDBC查询引擎，因此它会自动识别复合属性，并自动展开为表关联关系。

```javascript
QueryBean query = new QueryBean();
query.fields(mainField("refField.name"), subField("deptMappings","otherRefField.user.name").count().alias("count"));
query.addFilter(FilterBeans.eq("refField.status",1));
query.setOffset(100);
query.setLimit(10);
```

这里假定主实体上存在名为`refField`的`to-one`关联，而`deptMappings`是一个`to-many`关联，`otherRefField`是`deptMappings`
关联的另一个`to-one`关联。

## 在类似MyBatis的sql-lib中管理动态构建的QueryBean

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

`<query>`的source段就是一段xpl模板语言，它生成QueryBean的一个XML表示。在生成过程中，可以使用Xpl标签来实现进一步的抽象。

通过`sqlMethod`我们可以选择使用`findList`、`findFirst`或者`exists`等方法，分别用于返回列表数据、第一条数据或者判断是否存在数据。

## 通过Underscore帮助类对数据进行加工

`Underscore.java`工具类提供了一些针对集合对象的帮助函数，比如`leftjoinMerge`可以实现两个列表的join合并。

```javascript
 Underscore.leftjoinMerge(listA,listB, leftPropName, rightPropName, Arrays.asList(fldB1,fldB2));
```

以上函数调用相当于实现

```sql
select listA.*, listB.fldB1,listB.fldB2
from listA, listB
where listA.leftProp =  listB.rightProp
```

## 集成tablesaw

`nop-tablesaw`模块集成了`tablesaw`计算包，它的功能类似于python中的pandas库，可以完成一系列针对列表数据的统计计算。

```javascript
Table table = DataSetHelper.dataSetToTable(dsName, dataSet);
table.numberColumn("count").sum();
```

NopORM查询得到的`IDataSet`数据集可以直接被转换为`tablesaw`的`Table`接口，然后就可以调用`select/pivot/summarize/count`等一系列操作函数。

> NopORM内部将所有的数据集合对象都统一封装为IDataSet接口，因此并不对外暴露ResultSet等泄露实现细节的接口。NopORM底层可以不运行在JDBC之上。
