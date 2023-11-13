# 说明

XPL是一种采用XML语法格式，支持元编程、支持多种输出模式，与EL表达式（XScript脚本语言）紧密结合的一种模板语言。通过自定义标签，XPL可以引入新的语法结构，
实现多种DSL的无缝嵌套。

## 内置标签

XPL内置的标签提供了判断、循环、导入、宏处理等基本功能。

### 控制逻辑

* `<c:if>`
  判断语句。

````
<c:if test="${cond}">
  当条件为真时执行。
</c:if>
````   

* `<c:for>`
  循环语句。
  index对应循环下标

````
<c:for items="${list}" var="item" index="index">
</c:for>

<c:for begin="0" end="2" var="index" index="index">
</c:for>
````

* `<c:while>`
  while循环语句。

````
<c:while test="${cond}">
</c:while>
````   

* `<c:break>`
  跳出循环语句

* `<c:continue>`
  类似于java中的continue语句，跳过本次循环。

* `<c:return>`
  从自定义标签返回，可以通过value属性指定返回值.

````
  <c:return value="${result}" />
````

* `<c:choose>`
  多重选择语句。

````
 <c:choose>
   <when test="${cond}">
      当条件为true时执行
   </when>
   <otherwise>
      当其他条件都不满足时执行
   </otherwise>
 </c:choose>
````   

* `<c:throw>`

````
   <c:throw errorCode="xxx.yyy.zz" params="${{a:1,b:2}}" />
````

* `<c:try>`

````
<c:try>
  <body>
  需要执行的代码
  </body>
  
  <catch>
     捕获异常，上下文中$excetion对应异常对象
  </catch>
  
  <finally>
     
  </finally>
</c:try>
````

### 输出

* `<c:collect>`

* `<c:print>`

* `<c:out>`

### 脚本语言

* `<c:script>`
  嵌入脚本语言，可以用lang属性来指定使用不同的脚本引擎，例如groovy等，缺省是xlang

````
<c:script lang="groovy">
  代码
</c:script>
````  

### 编译期

* `<c:import>`
  导入常量类或者标签库

````
<c:import from="a/b.xlib" />

<c:import class="a.b.c.MyConstants" />
````

引入标签库的时候缺省名字空间根据标签库文件名推定，例如 a.xlib => a, a!ext.xlib => a。
也可以直接指定名字空间，例如

````xml

<c:import from="xxx.xlib" as="yyy"/>
<yyy:MyTag/>
````

* `<c:include>`

* `<macro:script>`
  编译指定内容, 并直接运行

* `<macro:gen>`
  宏标签会在编译期被运行。

```
  <macro:gen xpl:dump="true" >
    这里的内容先被编译为Expression, 然后在编译期会立刻执行此xpl。
    xpl输出的XNode会再次被编译
  </macro:gen>
```

### 其他

* `<c:unit>`
  一个仅起分组作用的虚拟标签。它的直接编译结果为空。

````
   <c:unit>
      <div/>
      <input/>
   </c:unit>
   
   等价于
   <div/>
   <input/>
````

* `<c:log>`
  只有io.nop.xpl.logger的对应log级别打开时，才拼接日志消息并打印日志。

```xml
  <c:log info="xxx ${myvar} ${myObj.func(3)} sss" />
```

## 自定义标签

### 条件标签

当自定义标签的conditionTag设置为true时为条件标签

````
标签定义
   <WhenAdmin conditionTag="true">
     <source>
       <c:script>
         $userContext.isUserInRole('admin')
       </c:script>
     </source>
   </WhenAdmin>
   
作为条件容器调用   
  <biz:WhenAdmin>
    当用户具有admin角色时执行这里的内容
  </biz:WhenAdmin>
````

条件标签可以直接作为c:choose的分支

````
  <c:choose>
    <biz:WhenAdmin>
    </biz:WhenAdmin>
    
    <c:otherwise>
      
    </c:otherwise>
  </c:choose>
````

## 动态属性

* 如果属性值返回null, 则该属性在输出时会被自动忽略。

```
  <input class="${null}" />
  实际输出  <input />
```

* xpl:attrs可以指定一个Map, 批量输出一组动态属性。如果属性值为null, 一样不输出

```
  <input xpl:attrs="{name:'a',class:null}" />
   实际输出 <input name="a" />
```

另外，需要注意，如果节点上已经存在某属性，则xpl:attrs指定的属性将会被忽略

```
  <input name="x" xpl:attrs="{name:'b'}" />
实际输出 <input name="x" />
```

## 输出模式

xpl标签既有返回值，又有输出。输出具有多种模式

* none 不允许输出文本
* html 输出html文本，自动识别<script><div>等需要使用完整封闭形式的标签
* xml 输出xml文本，如果xpl:allowUnknownTag为true, 则未识别的所有带名字空间的标签也会被输出
* text 不能输出xml标签，但是可以输出文本内容，而且输出的文本不会进行xml转义
* node 所有输出的内容自动被转化为XNode。

1. 定义标签库时，可以通过在根结点上标记defaultOutputMode来设置本库中标签的缺省输出模式。
2. 也可以在定义每个自定义标签时指定outputMode
3. 在调用标签时，可以通过xpl:outputMode来设置未识别的标签的输出模式。如果自定义标签已经有自己的输出模式，则外部调用时再设置也是无效的。

## thisLib
在自定义标签库中，可以使用thisLib来指向当前标签库。
例如在web.xlib中，  `<thisLib:LoadPage>`对应于 `<web:LoadPage xpl:lib="/nop/web/xlib/web.xlib">`

引入thisLib这个特殊的名字空间的原因在于，外部引用标签库的时候有可能通过as来修改最终使用时的名字空间。例如

````
<c:import from="/nop/web/xlib/web.xlib" as="myweb" />
<myweb:GenPage page="xx" />
````

## xpl专用属性

XPL内置了一些通用属性，所有标签都可以指定这些属性。xpl属性的处理顺序为
xpl:enableNs -->  xpl:disableNs --> xpl:attrs -> xpl:frame -> xpl:decorator
--> xpl:lib --> xpl:outputMode --> xpl:is --> xpl:invert --> xpl:return
--> xpl:if

1. xpl:disableNs/xpl:enableNs
   xpl:disableNs 忽略指定的名字空间，不把它们看作是xpl标签库中的标签
   xpl:enableNs 取消xpl:ignoreNs的作用，恢复识别指定名字空间对应的标签库

```
  <!-- c:if标签不会被解析，而是作为文本被直接输出 -->
  <c:if test="${x}" xpl:ignoreNs="*">
     <!-- 子节点也不会被解析-->
     <my:MyTag>
         <!--  通过xpl:ignoreTag=false恢复对标签的解析 -->
         <c:if test="${xx}" xpl:checkNs="*">
            ...
         </c:if>
     </my:MyTag>
  </c:if>
```

2. xpl:ignoreExpr
   是否识别表达式。如果设置为true，则非自定义标签中用到的EL表达式将被作为文本直接输出。

````
<div xpl:ignoreExpr="true">
  ${这个表达式不会被解析}
</div>
````

3. xpl:is
   可以改变识别的xpl标签名

```
<div xpl:is="my:MyTag">
</div>
等价于
<my:MyTag>
</my:MyTag>
```

4. xpl:if
   控制标签是否运行，相当于简化c:if调用

```
  <div xpl:if="selectors.contains('a')">
  </div>
  
  <!-- 如果使用编译期表达式，xpl引擎内部会实现自动优化 -->
  <div xpl:if="#{myVar}">
  </div>
```

5. xpl:skipIf
   如果为true, 则跳过本层标签，直接编译标签的body。相当于是控制跳过嵌套的层次

```
  <my:MyTag xpl:skipIf="true">
     <body/>
  </my:MyTag>
  
  等价于
   <body/>
```

6. xpl:allowUnknownTag
   是否允许未识别的带名字空间的标签。缺省为false, 当带名字空间的标签不能被识别为自定义标签时，会抛出异常。

7. xpl:outputMode
   设置xpl编译器的输出模式。注意，在c:macro和x:extends运行期间，缺省设置了xpl:outputMode=node

8. xpl:lib
   在局部范围内引入标签库。当超出标签范围后，引入的标签库不可见。

````
 <my:MyTag xpl:lib="my.xlib" />
 
 <!-- xpl:lib引入的标签库仅对当前节点有效，对这里的节点不可见，因此编译时会报错 -->
 <my:MyTag />
````

9. xpl:return
   执行完标签后将把返回值设置为指定变量

```
  <my:MyTag  xpl:return="x">
    此标签的执行结果被保存到变量x。相当于 let x = #[my:MyTag()]
  </my:MyTag>
```

10. xpl:invert
    对于返回boolean值的自定义标签，xpl:invert表示对返回值取反。

```
  <biz:WhenAdmin>
    当具有admin角色的时候执行这里的内容
  </biz:WhenAdmin>
  
  <biz:WhenAdmin xpl:invert="true">
     当【不具有】admin角色的时候执行这里的内容
  </biz:WhenAdmin>
```

## 装饰器decorator

任何标签都支持名为`<xpl:decorator>`的装饰子节点, 它可以将嵌套结构变换为线性结构。

```
<div>
 <xpl:decorator>
   <test:MyTag a="1" />
   <test:MyTag2>
      <xpl:decorated/>
      <child/>
   </test:MyTag2>
 </xpl:decorator>
 
 content
</div>

等价于
<test:MyTag a="1">
  <test:MyTag2>
     <div >
      content
     </div>
     <child/>
  </test:MyTag2>
</test:MyTag>
```