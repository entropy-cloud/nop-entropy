# 1. 宏标签在编译期动态生成代码

`<macro:gen>`的body部分编译后会被自动执行，执行过程中输出的节点为最终编译的代码

````xpl
  <macro:gen xpl:outputMode="xml">
    <c:script>
       function f(a){
         return a +1;
       }
       
       let x = 4;
    </c:script>
    
    <div>${f(3)},${x}</div>
  </macro:gen>

````

* outputMode: xml
* output:

````

<div>4,4</div>
````

# 2. 宏变量定义

`<macro:gen>`标签中定义的变量都是局部变量，在兄弟节点中无法访问。如果不同的标签需要共享变量，可以通过`<macro:var>`标签来定义

````xpl
<c:unit xpl:outputMode="xml">
 <macro:var name="x" />
 <macro:gen>
    <c:script>
      x = 3;
    </c:script>
 </macro:gen>
 
 <c:if test="#{x == 3}">
   <div>#{x+1}</div>
 </c:if>
</c:unit>
````

* outputMode: xml
* output

````

<div>4</div>
````

# 3. 宏变量定义的范围

````xpl
<c:unit>
 <c:unit>
     <macro:var name="x" />
     <macro:gen>
        <c:script>
          x = 3;
        </c:script>
     </macro:gen>
 </c:unit>
 
 <c:if test="#{x == 3}">
   <div>#{x+1}</div>
 </c:if>
</c:unit>
````

* errorCode: nop.err.xlang.unresolved-identifier

# 4. `<macro:script>`中定义的变量外部可见

````xpl
<c:unit xpl:outputMode="node">
  <macro:script>
    let y = 3;
    let z = 4;
    let x = 1;
    function f(y){
      return x + y;
    }
  </macro:script>
 
  <macro:if test="#{x == 1}">
    <div>#{f(3)}</div>
  </macro:if>

</c:unit>
````

* outputMode: node
* output

````

<_>
<div>4</div>
</_>
````