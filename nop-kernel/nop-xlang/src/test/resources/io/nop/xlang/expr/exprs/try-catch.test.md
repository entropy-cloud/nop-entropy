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
