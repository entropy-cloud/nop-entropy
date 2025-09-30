# 1. How to retrieve all sibling nodes of the current node and all parent nodes

```
  dao.batchLoadPropsForEntity(entity,"parent.parent.parent.parent","parent.children")
```

You can use the batchLoadPropsForEntity function on the IEntityDao interface to fetch the parent nodes several levels up, as well as the sibling nodes under those parent nodes.

Then, through the associated properties on the entity, such as `entity.getParent()` and `entity.getChildren()`, you can obtain all entity objects.
<!-- SOURCE_MD5:f2c63883d6ff5eca96c0e189fbd81d75-->
