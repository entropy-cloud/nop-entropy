# 1. and语句

````expr
let x = 'a';
let y = 'b';
let z = null && x;
$.checkEquals(null,z);

$.checkEquals(null, x && z);
$.checkEquals(y, x && y);
$.checkEquals(0, x && 0);
````

# 2. or语句

````expr
let x = 'a';
let y = 'b';
let z = null || x;
$.checkEquals(x,z);

$.checkEquals(x, x || z);
$.checkEquals(x, x || y);
$.checkEquals(x, x || 0);
````

# 3. not语句

````expr
let x = null;
$.checkEquals(true, !x);
$.checkEquals(true,!0);
$.checkEquals(false, !!x);

$.checkEquals(true, !x && !!!x); 
````

## 4. null coalesce

````expr
let x = null;
$.checkEquals('a', x ?? 'a');
$.checkEquals(true, x ?? !x);
````