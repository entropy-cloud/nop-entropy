# 调用普通标签
````xpl
<c:unit>
  <c:import from="/test/my.xlib" />
  
  <c:script>
     let b = 3;
  </c:script>
  
  <my:Add a="${1}" xpl:return="ret" />
  
  <c:script>
    $.checkEquals(4,ret);
  </c:script>
</c:unit>
````

## 在表达式中调用标签
````xpl
<c:script><![CDATA[
   import "/test/my.xlib"
   let b = 3;
   let x = 2;
   
   let ret = xpl `<my:Add a="${x}" />`
   $.checkEquals(5,ret) 
]]></c:script>
````