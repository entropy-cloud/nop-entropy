# 1. try/catch吞异常（无finally合法）

````expr
import java.lang.IllegalArgumentException;

let result = 0;
try {
    throw new IllegalArgumentException("boom");
    result = 1;
} catch (e) {
    result = 2;
}
$.checkEquals(2, result);
````

# 2. catch变量绑定异常对象（throw自动包装为NopException）

````expr
import java.lang.IllegalArgumentException;
import io.nop.api.core.exceptions.NopException;

let caught = null;
try {
    throw new IllegalArgumentException("bind-error");
} catch (e) {
    caught = e;
}
$.checkEquals(true, caught != null);
$.checkEquals(true, caught instanceof NopException);
````

# 3. catch体显式throw重抛

````expr
import java.lang.IllegalArgumentException;
import io.nop.api.core.exceptions.NopException;

function f() {
    try {
        throw new IllegalArgumentException("rethrow-me");
    } catch (e) {
        throw e;
    }
}

let err = null;
try {
    f();
} catch (ex) {
    err = ex;
}
$.checkEquals(true, err instanceof NopException);
````

# 4. try正常执行不进入catch

````expr
let result = 0;
try {
    result = 1;
} catch (e) {
    result = 2;
}
$.checkEquals(1, result);
````

# 5. finally必须执行（异常路径）

````expr
import java.lang.IllegalArgumentException;

let flag = 0;
try {
    throw new IllegalArgumentException("fin");
} catch (e) {
    flag = 1;
} finally {
    flag = flag + 10;
}
$.checkEquals(11, flag);
````

# 6. finally必须执行（正常路径）

````expr
let flag = 0;
try {
    flag = 1;
} catch (e) {
    flag = 2;
} finally {
    flag = flag + 10;
}
$.checkEquals(11, flag);
````

# 7. 无catch的try/finally（异常继续传播）

````expr
import java.lang.IllegalArgumentException;

function f() {
    let flag = 0;
    try {
        throw new IllegalArgumentException("prop");
    } finally {
        flag = 1;
    }
    return flag;
}

let err = null;
try {
    f();
} catch (ex) {
    err = ex;
}
$.checkEquals(true, err != null);
````

# 8. try内return

````expr
function f() {
    try {
        return 1;
    } catch (e) {
        return 2;
    }
}
$.checkEquals(1, f());
````

# 9. catch内return

````expr
import java.lang.IllegalArgumentException;

function f() {
    try {
        throw new IllegalArgumentException("ret-in-catch");
    } catch (e) {
        return 2;
    }
}
$.checkEquals(2, f());
````

# 10. catch/finally体内break穿透到循环

````expr
let sum = 0;
for (let i = 0; i < 5; i++) {
    try {
        if (i == 2)
            break;
        sum = sum + 1;
    } catch (e) {
        sum = 100;
    } finally {
        sum = sum + 0;
    }
}
$.checkEquals(2, sum);
````

# 11. catch变量作用域限定在catch块内

````expr
import java.lang.IllegalArgumentException;

let err = null;
try {
    throw new IllegalArgumentException("scope");
} catch (ex) {
    err = ex;
}
$.checkEquals(true, err != null);
````

# 12. 异常对象方法可调用

````expr
import java.lang.IllegalArgumentException;
import io.nop.api.core.exceptions.NopException;

let cause = null;
try {
    throw new IllegalArgumentException("msg-check");
} catch (e) {
    $.checkEquals(true, e instanceof NopException);
    cause = e.getCause();
}
$.checkEquals(true, cause instanceof IllegalArgumentException);
````

# 13. catch(e)访问e.name（JavaScript Error.name兼容）

````expr
import io.nop.api.core.exceptions.NopScriptError;

let n = null;
try {
    throw new NopScriptError("my.error.code").name("CustomError");
} catch (e) {
    n = e.name;
}
$.checkEquals("CustomError", n);
````

# 14. catch(e)访问e.name默认值（未设置时返回errorCode）

````expr
import io.nop.api.core.exceptions.NopScriptError;

let n = null;
try {
    throw new NopScriptError("auto.error.code");
} catch (e) {
    n = e.name;
}
$.checkEquals("auto.error.code", n);
````

# 15. catch(e)访问e.message（已通过getMessage()自动支持）

````expr
import java.lang.IllegalArgumentException;
import io.nop.api.core.exceptions.NopException;

let m = null;
try {
    throw new IllegalArgumentException("specific-msg");
} catch (e) {
    m = e.message;
}
$.checkEquals(true, m != null && m.contains("specific-msg"));
````

# 16. catch(e)访问e.code（JavaScript Error.code语义）

````expr
import io.nop.api.core.exceptions.NopScriptError;

let c = null;
try {
    throw new NopScriptError("err.code.x");
} catch (e) {
    c = e.code;
}
$.checkEquals("err.code.x", c);
````

# 17. catch(e)访问e.status（HTTP状态码）

````expr
import io.nop.api.core.exceptions.NopScriptError;

let s = null;
try {
    throw new NopScriptError("err.status").status(404);
} catch (e) {
    s = e.status;
}
$.checkEquals(404, s);
````

# 18. catch(e)访问e.params（param Map）

````expr
import io.nop.api.core.exceptions.NopScriptError;

let userId = null;
try {
    throw new NopScriptError("err.param").param("userId", 123);
} catch (e) {
    userId = e.params.userId;
}
$.checkEquals(123, userId);
````

# 19. catch(e)访问e.stack（自定义堆栈）

````expr
import io.nop.api.core.exceptions.NopScriptError;

let s = null;
try {
    throw new NopScriptError("err.stack").stack("frame1\nframe2\nframe3");
} catch (e) {
    s = e.stack;
}
$.checkEquals(true, s != null && s.contains("frame2"));
````

# 20. JS 风格 throw new Error() 别名为 NopScriptError（无需 import）

````expr
let kind = null;
try {
    throw new Error("js-style-error");
} catch (e) {
    kind = e.errorCode;
}
$.checkEquals("js-style-error", kind);
````

# 21. JS 风格 throw new Error(code) 别名为 NopScriptError(code)

````expr
let errName = null;
let errMsg = null;
try {
    throw new Error("typed.code");
} catch (e) {
    errName = e.name;
    errMsg = e.message;
}
$.checkEquals("typed.code", errName);
$.checkEquals(true, errMsg != null && errMsg.contains("typed.code"));
````
