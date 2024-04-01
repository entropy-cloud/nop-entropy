# 1. 如何取出当前节点的所有兄弟节点，以及所有父节点

```
  dao.batchLoadPropsForEntity(entity,"parent.parent.parent.parent","parent.children")
```

可以通过IEntityDao接口上的batchLoadPropsForEntity函数来获取到向上几层的父节点，以及父节点下的兄弟节点。

然后通过上的关联属性，如`entity.getParent()`和`entity.getChildren()`等来获取到所有实体对象
