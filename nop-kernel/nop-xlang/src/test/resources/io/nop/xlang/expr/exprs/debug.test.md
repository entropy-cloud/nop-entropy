# 1. 打印调试变量

````expr
let x = {a:1}
let y = x.a;
let z = x.a.$("my");
$.checkEquals(y,z);
````

# 2. 打印消息

只有当对应logLevel为enabled状态时，才会真正执行，否则会被自动忽略。

````expr
logTrace("not-executed", 'sss');
logInfo("nop.err.my-error:data={}",{b:2,a:1});
````

# 3. 测试空指针对象的属性访问
````expr
let x = null;
x?.b.f().$('null-data')
````
* errorCode: nop.err.xlang.exec.value-not-allow-null