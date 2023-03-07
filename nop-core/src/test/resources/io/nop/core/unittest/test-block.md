# 5. 闭包变量，变量引用

```
let x = 1;
function add(y){
    return ()=>{ return x + y }
}
 x = 4;
 $.checkEquals(7, add(3)())
  
```

# 6. 通过闭包访问可变变量

````
let x = 1; 
function add(y){ 
    return ()=>{ return x + y }
} 
let h = add(3); 
x=4; 
$.checkEquals(7,h())
````