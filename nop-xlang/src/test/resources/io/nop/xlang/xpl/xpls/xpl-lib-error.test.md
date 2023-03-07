# 1. 调用标签时非空参数校验

````xpl
<c:unit>
  <c:import from="/test/my.xlib" />
  <c:script>
     let x = '';
  </c:script>
  
  <my:Add a='${x}' b="4" xpl:return="ret" />
  
</c:unit>
````

* errorCode: nop.err.xlang.exec.value-not-allow-empty

# 2. 发现未定义的参数时抛出异常

````xpl
<c:unit>
  <c:import from="/test/my.xlib" />
  
  <my:Add invalidVar="3" xpl:return="ret" />
  
</c:unit>
````

* errorCode: nop.err.xlang.xpl.unknown-tag-attr

# 3.调用标签缺少参数时抛出异常

```xpl
<c:unit>
   <c:import from="/test/my.xlib" />
   <my:Nested />
</c:unit>
```

* errorCode: nop.err.xlang.xpl.tag-missing-attr