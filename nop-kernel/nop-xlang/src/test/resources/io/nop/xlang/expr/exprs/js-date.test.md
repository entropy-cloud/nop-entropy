# 1. new Date() 无参 - 当前时间（JsDate fallback，无需 import）

````expr
let d = new Date();
$.checkEquals(true, d != null);
$.checkEquals(true, d instanceof java.util.Date);
let year = d.getFullYear();
$.checkEquals(true, year >= 2024);
let ms = d.getTime();
$.checkEquals(true, ms > 0);
let iso = d.toISOString();
$.checkEquals(true, iso != null && iso.length >= 24);
````

# 2. new Date(ms) 数字参数 - 毫秒时间戳（JsDate fallback）

````expr
let d = new Date(0L);
$.checkEquals(0L, d.getTime());
let d2 = new Date(1000L);
$.checkEquals(1000L, d2.getTime());
let month = d2.getMonth();
$.checkEquals(true, month >= 0 && month <= 11);
let day = d2.getDate();
$.checkEquals(true, day >= 1 && day <= 31);
````

# 3. new Date(str) 字符串参数 - 解析日期字符串（JsDate fallback）

````expr
let d = new Date("2024-01-15 10:30:00");
$.checkEquals(true, d != null);
$.checkEquals(2024, d.getFullYear());
$.checkEquals(0, d.getMonth());    // JS 月份 0-11
$.checkEquals(15, d.getDate());    // 日期 1-31
$.checkEquals(10, d.getHours());
$.checkEquals(30, d.getMinutes());
````

# 4. new Date(y, m, d) 多参数构造

````expr
let d = new Date(2024, 5, 20);   // 2024-06-20
$.checkEquals(2024, d.getFullYear());
$.checkEquals(5, d.getMonth());
$.checkEquals(20, d.getDate());
````

# 5. JsDate 静态方法 Date.now() / parse()

````expr
import io.nop.xlang.utils.JsDate;
let now = JsDate.now();
let t = new Date().getTime();
$.checkEquals(true, now > 0);
$.checkEquals(true, t >= now);

let parsed = JsDate.parse("2024-01-15 10:30:00");
$.checkEquals(true, parsed > 0);
````

# 6. JsDate 显式 import 后 getFullYear 等 JS 方法可用

````expr
import io.nop.xlang.utils.JsDate;
let d = new Date(1000L);
$.checkEquals(true, d instanceof JsDate);
$.checkEquals(1000L, d.getTime());
let iso = d.toISOString();
$.checkEquals(true, iso != null && iso.startsWith("1970-01-01T00:00:01"));
````

# 7. 显式 import java.util.Date 时优先用 JDK Date（无 JS 风格方法）

````expr
import java.util.Date;
let d = new Date();
$.checkEquals(true, d instanceof Date);
let year = d.getYear();
$.checkEquals(true, year >= 124);  // getYear() = 实际年 - 1900
````

# 8. getTimezoneOffset 时区偏移

````expr
let d = new Date(0L);
let offset = d.getTimezoneOffset();
$.checkEquals(true, offset != null);
````