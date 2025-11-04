# 1. map属性存取

````expr
let x = {a:1,b:2};
x.a = 2;
$.checkEquals(2,x.a);
let k = 'a';
$.checkEquals(2,x[k]);
````

# 2. 列表元素存取

````expr
let x = [1,2,3];
x[1] = 4;
$.checkEquals(4,x[1]);
let k = 2;
$.checkEquals(3,x[k]);
````

# 3. map属性spread

````expr
let x = {a:1,b:2,c:3};
let {a,...r} = x;
$.checkEquals(1,x.a);
$.checkEquals(2,r.size());

let y = {...r,...x,d:3,a:9};
$.checkEquals(9,y.a);
$.checkEquals(3,y.d);
$.checkEquals(3,y.c);
````

# 4. 列表属性spread

````expr
let x = [1,2,3];
let [a,...r] = x;
$.checkEquals(1,x[0]);
$.checkEquals(2,r.size());

let y = [1,...r,...x];
$.checkEquals(1,y[0]);
$.checkEquals([1,2,3,1,2,3],y);
````

# 5. 嵌套属性

````expr
let x = {a:1,b:[1,{c:2}]};
$.checkEquals(2,x.b[1].c);

x.b[1] = 4;
$.checkEquals(4,x.b[1]);
````

# 6. 设置属性为引用变量

````expr
let x = 0;
function f(){
   x ++;
   return x;
}

let z = {};
z.a = x;

$.checkEquals(0,z.a);

f();
$.checkEquals(0,z.a);
````

# 7. 更新对象引用

````expr
let x = {a:1};
function f(){
   x = {a:2};
   return g();
}

function g(){
  return x.a + 10;
}

f();

$.checkEquals(12,g());
````

# 8. 嵌套结构

````
const x = {a: {b:{}}}
let b = x.a.b;
x.a.b.c = 1;
b.d = 2;
$.checkEquals(1, x.a.b['c'])
$.checkEquals(2, b.d);
````