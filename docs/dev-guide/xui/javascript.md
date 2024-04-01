# XScript与JavaScript的兼容性

## 所有对象都是Java对象

XScript语法类似于JavaScript，但是并不支持JavaScript内置的对象如Array, Set, Map等。

如果严格符合ECMAScript的对象标准，就会导致与Java的不兼容。现在语法设计是java兼容优先，避免对象转换，所以
数组语法`[1,2,3]`返回的是ArrayList，而对象语法 `{a:1,b:2}`返回的是LinkedHashMap(这里选择了LinkedHashMap而不是HashMap是为了保持key的顺序与加入顺序一致)。

所以XScript中所使用的对象都是Java对象，其中的方法都是java的方法，其中对于List特殊处理了一下，针对List扩展了一些函数（参见ListFunctions.java），
可以使用push,pop, slice等函数，但是并没有取消原先List上的add等函数，本质上函数都是使用Java对象上的函数。

在XScript中可以调用`new Set()`，但是它也只是新建LinkedHashSet对象。

## 不区分undefined和null

undefined设计与Java不兼容，所以取消了`===`语法，也取消了undefined语义，只定义了null语义。
