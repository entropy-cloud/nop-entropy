XPL是XML格式的模板语言

## 核心类型约定

- `t-expr`: 动态表达式，格式 `${expr}` 或者字面量
- `prop-path`: 属性路径，如 `user.contact.phone`
- `error-code`: 错误码，如`app.demo.unknown-order`
- `filter`: 支持复杂bool逻辑表达式， 如`<and/>, <or/> <eq/>`等

## 内置标签

### 1. 数据验证

```xpl-define
 <!--
  验证传入的obj对象（可以是一个Map)上的属性
  -->
<biz:Validator obj="t-expr">
  <check id="string" errorCode="error-code" errorDescription="string">
    <eq name="prop-path" value="t-expr"/>
    <ne name="prop-path" value="t-expr" />
    <between name="prop-path" min="t-expr" max="t-expr" excludeMin="boolean" excludeMax="boolean"/>
    <notEmpty name="prop-path" />
    <notBlank name="prop-path" />
    <in name="prop-path" value="t-expr" />
    <startsWith name="prop-path" />
    <regex name="prop-path" value="reg-ex" />
    <and/> <or/> <not/>

  </check>

</biz:Validator>
```

### 2. CRUD操作

```xpl-syntax
  <!--
   @return id对应的实体
   @ignoreUnknown 设置为true时没找到会返回null，否则会抛出nop.dao.unknown-entity异常
   -->
  <bo:Get bizObjName="entity-name" id="t-expr" ignoreUnknown="boolean"/>

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
```

### 3. 流程控制

```xpl-syntax

  <c:if test="t-expr">
    body
  </c:if>

  <c:choose>
    <case when="t-expr">body</case>
    <otherwise>body</otherwise>
  </c:choose>

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

  <c:break/>
  <c:continue/>

  <c:return value="t-expr"/>
```

### 4. 设置全局变量

```xpl-syntax
<task:assign name="var-name" value="t-expr" />
```

### 5. 脚本与异常

`<c:script>`中throw函数以及xpl中的`<c:throw>`标签都可以抛出异常

```xpl

  <c:script><![CDATA[
    let items = order.items;
    if(!items) {
       throw new NopScriptError("app.demo.order-no-items").param('orderNo',order.orderNo);
    }
    const userService = useService('userService);
    try{
       let result = userService.isUserValid(order.approverId);
    }catch(e){
    }finally{
    }
  ]]></c:script>

  <c:if test="${!items}">
    <c:throw errorCode="app.demo.order-no-items"
             errorDescription="订单{no}没有商品"
             params="${{no:order.orderNo}}"/>
  </c:if>

```

`<c:script>`的内容必须用CDATA包裹，采用类似JavaScript的语法，不允许嵌套XML标签，也就是以下做法是错误的

```xpl
<c:script><![CDATA[
  if(orderStats){}
    <task:assign name="orderStats" value="${stats}"/>
  }
]]></c:script>
```

**再次强调**: `<c:script>`内部嵌套`<task:assign>`这种XML标签是错误的!!!

## 5. 值作用域和返回值

所有标签都支持`xpl:return`属性来表示标签的返回值。例如，

```xpl

  <bo:Get bizObjName="User" id="${id}" ignoreUnknown="${true}" xpl:return="user"/>

  <c:script><![CDATA[
     let userName = user?.userName;
  ]]></c:script>

  <task:assign name="userName" value="${userName}" />
```

- `c:script`中的let声明和`xpl:return`的变量作用域都是当前step，不能在其他step中访问
- `<task:assign>`对应的变量作用域是整个task
- 除了request输入对象之外，必须通过`xpl:return`，`<c:script>`中的let或者`<task:assign>`等语法先定义变量然后才能使用

## 内置函数

```javascript
// 日期
now()     // 当前时间，返回类型LocalDateTime
today()   // 今天日期, 返回类型LocalDate

// 帮助类 $String提供大量字符串相关函数，$Date提供LocalDate等日期相关函数，$Math提供数学函数
$String.camelCase("hello_world")
$Date.formatDate(value,fmt)
$Math.power(value,3)
```

## 最佳实践

1. 优先使用属性路径代替多次查询，比如通过`order.items`直接得到实体关联的属性，不用通过`<bo:FindList>`去查询子表
2. 验证逻辑集中到Validator
3. 错误描述使用`{placeholder}`格式
4. 批量操作使用Batch前缀方法
