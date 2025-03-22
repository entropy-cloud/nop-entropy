# 1. How to retrieve the current node's siblings and all parent nodes

```
dao.batchLoadPropsForEntity(entity,"parent.parent.parent.parent","parent.children")
```

The `batchLoadPropsForEntity` method of the `IEntityDao` interface can be used to retrieve several levels of parent nodes along with the sibling nodes of the current entity.

To obtain the associated properties, such as `entity.getParent()` and `entity.getChildren()`, you can use these methods to access all entities.
