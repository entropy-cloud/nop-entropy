## 1. EL表达式中调用标签函数

````
<c:unit>
 <c:import from="/test/my.xlib" />
 
 <c:script>
    let x = xpl('my:Add',3,4);
    $.checkEquals(7,x);
    
    x = xpl('my:Add',{a:3,b:4});
    $.checkEquals(7,x);
 </c:script>

</c:unit>
````