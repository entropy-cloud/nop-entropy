
# Tree Structure Related

## findTreeEntityPage and findTreeEntityList provide query capabilities for tree structures

They automatically generate recursive SQL query statements based on the tree configuration in XMeta to implement tree-structured queries.

```sql
with recursive tree_page as (
  select b.deptId as id,b.deptName as displayName,b.parentId as parentId,null as level,b.deptId as joinId
  from NopAuthDept b
  where other query conditions and
    b.parentId is null
 union all
  select o.deptId as id,o.deptName as displayName,o.parentId as parentId,null as level,o.deptId as joinId
  from NopAuthDept o
      inner join tree_page p on o.parentId = p.joinId
  where other query conditions
)
select t.id, t.displayName, t.parentId, t.level, t.joinId
from tree_page t
order by t.id
```

* On the tree model, properties such as parentProp, levelProp, and sortProp are configured, and the EQL query is generated based on them.
* By default, root nodes are determined by `o.parentProp is null`
  to determine. If rootParentValue or rootLevelValue is specified, then determine by `o.parentProp = ${rootParentValue}`
  or `o.levelProp = ${rootLevelValue}`
* The returned object is a standard StdTreeEntity `PageBean<StdTreeEntity>` or `List<StdTreeEntity>`.
* Because some users do not directly use the primary key as the tree node id when building tree structures, but instead use another unique key field, a concept called joinId is introduced to associate with parentId; typically, it is the same as id.

<!-- SOURCE_MD5:85968c75ae60130ebfc5e9b9c2c89ba0-->
