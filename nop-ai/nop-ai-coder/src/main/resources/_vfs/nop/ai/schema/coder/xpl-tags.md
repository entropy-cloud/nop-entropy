```xml

<xpl>
  <!--
  验证传入的obj对象（可以是一个Map)上的属性
  -->
  <biz:Validator obj="t-expr">
    <check id="string" errorCode="error-code" errorDescription="string">
      <eq name="prop-path" value="t-expr"/>
    </check>

  </biz:Validator>
  <!--
  @return id对应的实体
  -->
  <bo:Get bizObjName="entity-name" id="t-expr"/>

  <!--
  @return ids对应的实体列表
  -->
  <bo:BatchGet bizObjName="entity-name" ids="t-expr"/>

  <!--
  @return 满足条件的第一条实体
  -->
  <bo:DoFindFirst bizObjName="entity-name">
    <filter>
      <eq name="field" value="t-expr"/>
    </filter>
    <orderBy>
      <field name="field-name" desc="boolean"/>
    </orderBy>
  </bo:DoFindFirst>

  <!--
  @return 实体列表
  -->
  <bo:DoFindList bizObjName="entity-name" limit="int">
    <filter>
      <eq name="field" value="t-expr"/>
    </filter>
    <orderBy>
      <field name="field-name" desc="boolean"/>
    </orderBy>
  </bo:DoFindList>

  <!--
  @return 新建的实体
  -->
  <bo:DoSave bizObjName="entity-name" data="t-expr"/>

  <!--
  @return id对应的实体
  -->
  <bo:DoDelete bizObjName="entity-name" id="t-expr"/>

  <!--
  @return id对应的实体
  -->
  <bo:DoUpdate bizObjName="entity-name" id="t-expr" data="t-expr"/>

  <bo:DoBatchDelete bizObjName="entity-name" ids="t-expr"/>

  <c:if test="t-expr">
    body
  </c:if>

  <!--
  @var 循环变量
  @index 循环下标变量名，从0开始
  -->
  <c:for var="var-name" items="t-expr" index="var-name">
    body
  </c:for>

  <!--
  begin和end必须返回整数，循环区间为[begin,end]，包含两者
  -->
  <c:for var="var-name" begin="t-expr" end="t-expr" index="var-name" step="int">
    body
  </c:for>

  <c:while test="t-expr">
    body
  </c:while>

  <c:return value="t-expr"/>

  <c:script>xscript</c:script>

  <c:throw errorCode="error-code" errorDescription="string" params="t-expr"/>

  <!--
  将变量发布到Task上下文中
  -->
  <task:assign name="var-name" value="t-expr"/>
</xpl>
```

1. 所有标签都可以通过xpl:return="varName"来获得返回值，例如
   <bo:Get bizObjName="NopAuthUser" id="${id}" xpl:return="user" />
   注意：`xpl:return`是节点的属性而不是一个子节点
2. 所有标签都可以通过xpl:if来增加一个判断条件，当该条件为true时，才执行本标签，例如
   <bo:Get xpl:if="id != null" bizObjName="   NopAuthUser" id="${id}" />
3. xscript是类似于JavaScript的脚本语言
4. 保存或者修改时只要设置简单字段，关联对象会自动因为关联字段的变化而变化。例如 entity.categoryId = category.id
   会导致entity.category关联对象自动变化，不需要调用entity.category=category重新绑定
5. 可以使用entity.a.b.c这种方式来直接访问关联对象，在filter中也可以直接利用复合属性了表达关联查询。
6. 生成代码时自动为所有xpl标签函数增加x:id唯一标识属性
7. biz:Validator的check节点的body部分是filter格式，可以使用and/or/gt/between等多种比较算符

示例:

```
<c:throw errorCode="app.check.invalid-name" errorDescription="{name}不合法" params="${{name:entity.name}}" />

```
