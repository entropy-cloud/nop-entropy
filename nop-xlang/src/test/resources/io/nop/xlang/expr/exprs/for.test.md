# 1. for循环

````expr
let count = 0;
for(let i=0,n=3;i<n;i++){
   count ++;
}
$.checkEquals(3,count);
````

# 2. for(let var of items)语句

````expr
let count = 0;
for(let x of [1,2,3]){
  count ++;
}
$.checkEquals(3,count);
````

# 3. for(var of items)语句

````expr
let count = 0;
let x;
let items = {a:1,b:2};

for(x of items.entrySet()){
   count += x.value;
}
$.checkEquals(3,count);
````

# 4. for语句局部循环变量

````expr
let count = 0;
let items = {a:1,b:2};

for(let x of items.entrySet()){
   count += x.value;
}

for(let x of items.entrySet()){
   count += x.value;
}

$.checkEquals(6,count);
````