# 1. JS 风格 new Array() 别名为 ArrayList

````expr
let arr = new Array();
$.checkEquals(true, arr instanceof java.util.ArrayList);
arr.add(1);
arr.add(2);
$.checkEquals(2, arr.size());
arr.push(3);  // ListFunctions.push 扩展方法
$.checkEquals(3, arr.size());
$.checkEquals(3, arr.get(2));
````

# 2. JS 风格 new Map() 别名为 LinkedHashMap

````expr
let m = new Map();
$.checkEquals(true, m instanceof java.util.LinkedHashMap);
m.set("a", 1);   // MapFunctions.set 扩展方法
m.set("b", 2);
$.checkEquals(1, m.get("a"));
$.checkEquals(true, m.has("b"));  // MapFunctions.has 扩展方法
$.checkEquals(false, m.has("c"));
m.delete("a");   // MapFunctions.delete 扩展方法
$.checkEquals(false, m.has("a"));
````

# 3. JS 风格 new Set() 别名为 LinkedHashSet

````expr
let s = new Set();
$.checkEquals(true, s instanceof java.util.LinkedHashSet);
s.add(1);
s.add(2);
s.add(3);
$.checkEquals(true, s.contains(1));
$.checkEquals(true, s.includes(2));  // SetFunctions.includes 扩展方法
s.delete(2);  // SetFunctions.remove + @Name("delete") 扩展方法
$.checkEquals(false, s.contains(2));
$.checkEquals(2, s.size());
````