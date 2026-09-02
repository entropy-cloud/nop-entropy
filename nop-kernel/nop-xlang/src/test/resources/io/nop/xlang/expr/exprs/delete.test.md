# 1. delete Map key - 命中

````expr
let m = {a: 1, b: 2};
let r = delete m.a;
$.checkEquals(true, r);
$.checkEquals(false, m.containsKey('a'));
$.checkEquals(true, m.containsKey('b'));
````

# 2. delete Map 不存在的key返回false

````expr
let m = {a: 1};
let r = delete m.missingKey;
$.checkEquals(false, r);
$.checkEquals(true, m.containsKey('a'));
````

# 3. delete Bean 普通属性 - set null

````expr
function getUser() { return {name: 'alice', age: 30}; }
let u = getUser();
let r = delete u.name;
$.checkEquals(true, r);
$.checkEquals(null, u.name);
````

# 4. delete List 按索引

````expr
let list = [10, 20, 30];
let r = delete list[1];
$.checkEquals(true, r);
$.checkEquals(2, list.size());
$.checkEquals(10, list[0]);
$.checkEquals(30, list[1]);
````

# 5. delete $scope 变量

````expr
function f(){
  $scope.tmp = 'value';
  let r = delete $scope.tmp;
  return r;
}
$.checkEquals(true, f());
````

# 6. delete 在 if 表达式上下文中

````expr
let m = {x: 1};
let result = '';
if (delete m.x) {
  result = 'deleted';
} else {
  result = 'not-found';
}
$.checkEquals('deleted', result);
$.checkEquals(false, m.containsKey('x'));
````

# 7. delete 返回 boolean 赋给变量

````expr
let m = {k: 'v'};
let wasPresent = delete m.k;
$.checkEquals(true, wasPresent);
````

# 8. delete 在三元表达式上下文中

````expr
let m = {a: 1};
let msg = (delete m.a) ? 'gone' : 'kept';
$.checkEquals('gone', msg);
````

# 9. delete Map dynamic key

````expr
let m = {userId: 1, orderId: 2};
let key = 'userId';
let r = delete m[key];
$.checkEquals(true, r);
$.checkEquals(false, m.containsKey('userId'));
````

# 10. delete null attr on Map 走 map.remove(null)

````expr
let m = new java.util.HashMap();
m.put(null, 'nullVal');
let r = delete m[null];
$.checkEquals(true, r);
````

# 11. delete 不存在的 key 在 Map 上返回 false

````expr
let m = {};
let r = delete m.absent;
$.checkEquals(false, r);
````

# 12. delete List 按对象值

````expr
let list = ['a', 'b', 'c'];
let r = delete list['b'];
$.checkEquals(true, r);
$.checkEquals(2, list.size());
````

# 13. delete 不存在的 List 元素返回 false

````expr
let list = ['a', 'b'];
let r = delete list['z'];
$.checkEquals(false, r);
````

# 14. delete DynamicObject 属性 - 真删除条目

````expr
function f(){
  let u = {foo: 'bar', keep: 1};
  let r = delete u.foo;
  return [r, u.keep, ('foo' in u)];
}
$.checkEquals(true, f()[0]);
$.checkEquals(1, f()[1]);
$.checkEquals(false, f()[2]);
````

# 14b. delete Bean 不存在的属性返回 false

````expr
let u = {name: 'alice'};
let r = delete u.age;
$.checkEquals(false, r);
````

# 15. delete $scope["key"] computed 形式

````expr
function f(){
  $scope.k1 = 'v1';
  $scope.k2 = 'v2';
  let r = delete $scope['k1'];
  return [r, $scope.k2];
}
$.checkEquals(true, f()[0]);
$.checkEquals('v2', f()[1]);
````

# 16. delete $scope 不存在的变量返回 false

````expr
function f(){
  return delete $scope.unboundVar;
}
$.checkEquals(false, f());
````

# 17. delete 裸标识符抛错

````expr
function f(){
  let x = 1;
  delete x;
}
f();
````

* errorCode: nop.err.xlang.delete.not-member-expr

# 19. delete 链式抛错

````expr
function f(){
  let o = {a: {b: 1}};
  delete o.a.b;
}
f();
````

* errorCode: nop.err.xlang.delete.not-single-level

# 20. delete null 对象抛错

````expr
let o = null;
delete o.x;
````

* errorCode: nop.err.xlang.exec.delete-on-null-obj

# 21. delete 在 if 中删除

````expr
let m = {x: 1};
let result = delete m.x ? 'gone' : 'kept';
$.checkEquals('gone', result);
````