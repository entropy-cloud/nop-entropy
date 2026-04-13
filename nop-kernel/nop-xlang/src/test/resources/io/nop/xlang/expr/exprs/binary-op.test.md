# 1. 比较语句

````expr
$.checkEquals(true, 2 < 3);
$.checkEquals(true, 2 >= 2);
$.checkEquals(true, 2 <= 2);
$.checkEquals(true, 2 == 2);
$.checkEquals(false, 2 > 3);
$.checkEquals(true, 2 != 3);
$.checkEquals(true, 2 === 2);
$.checkEquals(false, 2 === 3);
$.checkEquals(false, 2 !== 2);
$.checkEquals(true, 2 !== 3);
````

## 2. 严格相等与变量

````expr
let a = 1;
let b = 1;
$.checkEquals(true, a === b);
$.checkEquals(false, a !== b);
````

## 3. 严格相等与null

````expr
let x = null;
$.checkEquals(true, x === null);
$.checkEquals(false, x !== null);
$.checkEquals(true, 1 !== null);
$.checkEquals(false, 1 === null);
````

## 4. 严格相等与字符串

````expr
$.checkEquals(true, 'hello' === 'hello');
$.checkEquals(false, 'hello' === 'world');
$.checkEquals(false, 'hello' !== 'hello');
$.checkEquals(true, 'hello' !== 'world');
````

## 5. 严格相等与if条件

````expr
let x = 10;
if (x === 10) {
  $.checkEquals(true, true);
} else {
  $.checkEquals(true, false);
}

if (x !== 10) {
  $.checkEquals(true, false);
} else {
  $.checkEquals(true, true);
}
````

## 6. 严格相等与三元表达式

````expr
$.checkEquals('yes', 1 === 1 ? 'yes' : 'no');
$.checkEquals('no', 1 === 2 ? 'yes' : 'no');
$.checkEquals('no', 1 !== 1 ? 'yes' : 'no');
$.checkEquals('yes', 1 !== 2 ? 'yes' : 'no');
````

## 7. 严格相等与逻辑组合

````expr
$.checkEquals(true, 1 === 1 && 2 === 2);
$.checkEquals(false, 1 === 1 && 2 === 3);
$.checkEquals(true, 1 !== 2 || 2 !== 2);
$.checkEquals(false, 1 !== 1 || 2 !== 2);
````
