1. xpl表示节点是一个xpl段，它的内容使用【XPL模板语法】， 可以调用多个xpl标签
2. 通过next和nextOnError表示成功和失败时如何迁移到下一步骤。如果不指定，则按照step的先后顺序执行
3. 仅在错误可忽略时使用nextOnError跳转到条件分支，否则直接向外抛出异常即可
4. 尽量用已有标签和函数实现，只有极端必要时才使用`&lt;task:import>`导入的服务对象。增删改查不应使用服务对象。服务对象上能够调用的方法必须通过method节点声明。
5. step的name必须全局唯一
6. 核心业务数据应该入库，而不是仅在内存中完成计算，除非是试算过程
7. expr表示表达式，不需要用`${}`包裹，比如`itemsExpr="dataList"`
8. xpl-predicate表示条件判断，可以使用如下几种方式
9. 不需要考虑role角色权限问题，它由单独的配置负责。

```xpl
<when> x > 2</when>

或者
<when>
<eq name="status" value="2" />
</when>
或者
<when>
<c:script>
  if(x > 2){
    return true;
  }else{
    return false;
  }
</c:script>
</when>
```

🔴 禁止：`<when>`与步骤内条件逻辑冲突（常见反模式）
