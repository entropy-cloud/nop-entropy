# 树形结构相关

## findTreeEntityPage和findTreeEntityList提供了树形结构的查询功能

它们根据XMeta中的tree配置，自动生成recursive SQL查询语句，实现了树形结构的查询功能。

```sql
with recursive tree_page as (
  select b.deptId as id,b.deptName as displayName,b.parentId as parentId,null as level,b.deptId as joinId
  from NopAuthDept b
  where 其他查询条件 and
    b.parentId is null
 union all
  select o.deptId as id,o.deptName as displayName,o.parentId as parentId,null as level,o.deptId as joinId
  from NopAuthDept o
      inner join tree_page p on o.parentId = p.joinId
  where 其他查询条件
)
select t.id, t.displayName, t.parentId, t.level, t.joinId
from tree_page t
order by t.id
```

* tree模型上配置了parentProp、levelProp、sortProp等属性，根据它们来生成EQL查询
* 根节点缺省通过`o.parentProp is null`
  来判断。如果指定了rootParentValue或者rootLevelValue，则判断`o.parentProp = ${rootParentValue}`
  或者`o.levelProp = ${rootLevelValue}`
* 返回的对象为标准的StdTreeEntity的`PageBean<StdTreeEntity>`或者`List<StdTreeEntity>`
* 因为有些人在建立树形结构时并不直接使用主键作为树形结构的id，而是另外通过一个唯一键字段，所以引入一个概念joinId，用于和parentId关联，一般它就是id
