# 1. xpl:slot嵌套调用

```xpl
<c:unit xpl:outputMode="xml">
<c:import from="/test/my-ext.xlib" />

<my-ext:MyTagExt >
  <ext xpl:slotScope="x">
     <x>${x}</x>
  </ext>
</my-ext:MyTagExt>
</c:unit>
```

* outputMode: xml
* output

````

<x>3</x>
<a>1</a>
````