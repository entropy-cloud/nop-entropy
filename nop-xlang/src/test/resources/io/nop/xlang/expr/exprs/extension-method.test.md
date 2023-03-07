# 1. 列表扩展方法

````expr
let x = [1,2,3];
let sum = 0;
x.forEach(o=>{
  sum += o;
});

$.checkEquals(6,sum);

x.push(4);
let z = x.shift();
$.checkEquals(1,z);

$.checkEquals(3,x.length);

````

# 2. EvalMethod省略scope参数

````expr
import io.nop.xlang.expr.MyObject;

let o = new MyObject();
$.checkEquals(4,o.testEval(3));
````

# 3. 类型转换函数

任何变量都可以通过SysConvertRegistry中注册的类型转换器来进行类型转换。

````expr
let x = null;
$.checkEquals(null, x.$toInt());
$.checkEquals(3, x.$toInt(3));

$.checkEquals(true, x.$toFalsy());
````

# 4. 类型转换失败

````expr
'a'.$toLong();
````

* errorCode: nop.err.api.convert-to-type-fail