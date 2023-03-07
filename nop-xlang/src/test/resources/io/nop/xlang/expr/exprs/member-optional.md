# 1. 可空属性

````expr
let a = "a";
$.checkEquals(1,a ?.length);
$.checkEquals('a', a?.charAt(0).toString());

let b = [1];
$.checkEquals(1, b?.[0])
$.checkEquals(null,b[1])
$.checkEquals(null,b?.[1])
````

# 2. 访问null对象的属性和方法

````expr
let a = null;
$.checkEquals(null,a ?.length);
$.checkEquals(null, a?.charAt(0));

let b = null;
$.checkEquals(null, b?.[0])
$.checkEquals(null,b?.[1])
````

# 3. 访问空指针的属性报错
````
let a = null;
a.length
````

* errorCode: nop.err.xlang.exec.get-prop-on-null-obj