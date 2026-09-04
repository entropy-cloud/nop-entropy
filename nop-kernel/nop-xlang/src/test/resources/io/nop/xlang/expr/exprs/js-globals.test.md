# 1. Math 全局对象（裸名）

````expr
$.checkEquals(3L, Math.abs(-3L));
$.checkEquals(3L, Math.max(1L, 3L));
$.checkEquals(1L, Math.min(1L, 3L));
$.checkEquals(4L, Math.floor(4.7));
$.checkEquals(5L, Math.ceil(4.1));
$.checkEquals(9L, Math.pow(3L, 2L));
$.checkEquals(2L, Math.sqrt(4L));
$.checkEquals(1, Math.sign(5L));
$.checkEquals(-1, Math.sign(-5L));
$.checkEquals(0, Math.sign(0L));
$.checkEquals(true, Math.PI > 3.14 && Math.PI < 3.15);
$.checkEquals(true, Math.random() >= 0 && Math.random() < 1);
````

# 2. JSON 全局对象（裸名）

````expr
let obj = JSON.parse('{"a":1,"b":"x"}');
$.checkEquals(true, obj instanceof java.util.Map);
$.checkEquals(1, obj.get("a"));
$.checkEquals("x", obj.get("b"));

let s = JSON.stringify(obj);
$.checkEquals(true, s.contains("\"a\":1"));
$.checkEquals(true, s.contains("\"b\":\"x\""));
````

# 3. Number 全局对象（裸名）

````expr
$.checkEquals(42, Number.parseInt("42"));
$.checkEquals(16, Number.parseInt("10", 16));
$.checkEquals(true, Number.isNaN(0.0 / 0.0));
$.checkEquals(false, Number.isNaN(1L));
$.checkEquals(true, Number.isInteger(5L));
$.checkEquals(false, Number.isInteger(5.5));
$.checkEquals(true, Number.MAX_SAFE_INTEGER == 9007199254740992L);
````

# 4. 局部变量优先于 JS 全局对象

````expr
let Math = {pi: 3};
$.checkEquals(3, Math.pi);
````

# 5. Object 全局对象（裸名）

````expr
let m = {a: 1, b: 2};
let ks = Object.keys(m);
$.checkEquals(true, ks instanceof java.util.List);
$.checkEquals(2, ks.size());
$.checkEquals(true, ks.contains("a"));
$.checkEquals(true, ks.contains("b"));

let vs = Object.values(m);
$.checkEquals(true, vs.contains(1));
$.checkEquals(true, vs.contains(2));

let es = Object.entries(m);
$.checkEquals(2, es.size());

$.checkEquals(true, Object.hasOwn(m, "a"));
$.checkEquals(false, Object.hasOwn(m, "zzz"));
$.checkEquals(false, Object.isEmpty(m));
$.checkEquals(true, Object.isEmpty({}));
````

# 6. Object.assign 浅合并

````expr
let target = {a: 1};
Object.assign(target, {b: 2}, {a: 3});
$.checkEquals(3, target.get("a"));
$.checkEquals(2, target.get("b"));
````

# 7. Date 静态调用 Date.now()（裸名）

````expr
let now = Date.now();
$.checkEquals(true, now > 0);
let t = new Date().getTime();
$.checkEquals(true, t >= now);

let parsed = Date.parse("2024-01-15 10:30:00");
$.checkEquals(true, parsed > 0);
````

# 8. RegExp 全局对象 new RegExp(pat) 和 test/exec

````expr
let r = new RegExp("[0-9]+");
$.checkEquals(true, r instanceof io.nop.xlang.utils.JsRegExp);
$.checkEquals(true, r.test("abc123"));
$.checkEquals(false, r.test("abc"));
$.checkEquals("123", r.exec("abc123"));
$.checkEquals(null, r.exec("abc"));

let m = new RegExp("^hello$");
$.checkEquals(true, m.test("hello"));
$.checkEquals(false, m.test("hello world"));
````

# 9. JS 全局函数（裸名）

````expr
$.checkEquals(42, parseInt("42"));
$.checkEquals(16, parseInt("10", 16));
$.checkEquals(3.14, parseFloat("3.14"));
$.checkEquals(true, isNaN(0.0 / 0.0));
$.checkEquals(false, isNaN(1L));
$.checkEquals(true, isFinite(1L));
$.checkEquals(false, isFinite(0.0 / 0.0));

let encoded = encodeURIComponent("hello world");
$.checkEquals(true, encoded.contains("hello"));
$.checkEquals(true, encoded.contains("world"));
let decoded = decodeURIComponent(encoded);
$.checkEquals("hello world", decoded);
```