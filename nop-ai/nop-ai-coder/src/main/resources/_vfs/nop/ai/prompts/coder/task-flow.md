1. xpl表示节点是一个xpl段，它的内容使用【XPL模板语法】， 可以调用多个xpl标签
2. 通过next和nextOnError表示成功和失败时如何迁移到下一步骤。如果不指定，则按照step的先后顺序执行
3. 仅在错误可忽略时使用nextOnError跳转到条件分支，否则直接向外抛出异常即可
4. 尽量用已有标签和函数实现，只有极端必要时才使用`&lt;task:import>`导入的服务对象。增删改查不应使用服务对象。服务对象上能够调用的方法必须通过method节点声明。
5. step的name必须全局唯一
6. 核心业务数据应该入库，而不是仅在内存中完成计算，除非是试算过程
7. expr表示表达式，不需要用`${}`包裹，比如`itemsExpr="dataList"`
8. xpl-predicate表示条件判断，可以使用如下几种方式
9. 不需要考虑role角色权限和数据库事务等问题，它们由底层框架负责处理。
10. `<when>`仅用于可选的业务步骤，如根据条件跳过某些处理逻辑. 核心业务逻辑（特别是验证）不应使用<when>条件。

示例：

```xml

<task>
  <input name="request"/>

  <steps>
    <step name="init">
      <source>
        <!-- 初始化fee为0 -->
        <task:output name="fee" value="${0}" />
      </source>
    </step>

    <step name="validate">
      <source>
        <c:check errorCode="app.demo.invalid-amount" description="金额必须在{min}和{max}范围之内"
                 params="${{min:0, max:user.credit}}">
          <between name="request.amount" min="${0}" max="${user.credit}" />
        </c:check>
      </source>
    </step>

    <step name="calcFee">
      <!-- 仅当amount>100时才需要收取fee -->
      <when>request.amount > 100</when>
      <source>
        <c:script>
          let fee = request.amount * 10;
        </c:script>
        <task:output name="fee" value="${fee}"/>
      </source>
    </step>
  </steps>
</task>
```

`<when>`可以使用多种形式；
1. 简单表达式，如 request.amount > 100
2. filter节点

```xml
<when>
  <gt name="request.amount" value="${100}" />
</when>
```

【步骤划分】
在保证逻辑清晰的前提下尽量减少步骤数量。
1. 步骤划分要求：
  - 常见步骤：输入验证、聚合根实体获取、核心业务处理、结果返回
  - 每个步骤应完成一个完整业务概念的操作

2. 合并规则：
  - 数据查询操作可合并（如获取主记录和关联记录）
  - 计算和验证逻辑可合并到相关业务步骤
  - 紧密关联的写操作可以合并

3. 保持独立性的情况：
  - 核心业务修改操作必须独立
  - 会产生副作用的操作必须独立
  - 可能失败的操作建议独立

4. 变量管理：
  - 关键业务数据必须通过`<task:output>`显式设置，便于在后续步骤使用
  - 中间计算结果可在步骤内部处理


