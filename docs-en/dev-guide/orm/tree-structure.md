# Tree Structure Related

## findTreeEntityPage and findTreeEntityList Provided Tree Structure Query Functionality
They generated recursive SQL queries based on XMeta's tree configuration to implement tree structure query functionality.

```sql
WITH recursive tree_page AS (
    SELECT b.deptId AS id,
           b.deptName AS displayName,
           b.parentId AS parentId,
           NULL AS level,
           b.deptId AS joinId
    FROM NopAuthDept b
    WHERE OTHER_QUERY_CONDITIONS AND
           b.parentId IS NULL
    UNION ALL
    SELECT o.deptId AS id,
           o.deptName AS displayName,
           o.parentId AS parentId,
           NULL AS level,
           o.deptId AS joinId
    FROM NopAuthDept o
    INNER JOIN tree_page p ON o.parentId = p.joinId
    WHERE OTHER_QUERY_CONDITIONS
)
SELECT t.id, t.displayName, t.parentId, t.level, t.joinId
FROM tree_page t
ORDER BY t.id
```

* The tree model has configured properties such as parentProp, levelProp, and sortProp.
* The root node is determined by `o.parentProp IS NULL`.
* If the rootParentValue or rootLevelValue is specified, it will be used to determine `o.parentProp = ${rootParentValue}` or `o.levelProp = ${rootLevelValue}`.
* The result set returns a standard StdTreeEntity object of type `PageBean<StdTreeEntity>` or `List<StdTreeEntity>`.
* Since some users do not use the primary key directly for tree structure ID, an additional joinId is introduced to link parentId and joinId, which typically maps to id.