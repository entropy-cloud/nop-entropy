# 1. 无花括号case体

````expr
function f(x) {
    let r = 0;
    switch (x) {
        case 1:
            r = 10;
        case 2:
            r = 20;
        default:
            r = 30;
    }
    return r;
}

$.checkEquals(10, f(1));
$.checkEquals(20, f(2));
$.checkEquals(30, f(3));
````

# 2. case内break终止switch

````expr
function f(x) {
    let r = 0;
    switch (x) {
        case 1:
            r = 10;
            break;
            r = 99;
        case 2:
            r = 20;
            break;
        default:
            r = 30;
    }
    return r;
}

$.checkEquals(10, f(1));
$.checkEquals(20, f(2));
$.checkEquals(30, f(3));
````

# 3. break后语句不执行

````expr
function f() {
    let r = 0;
    switch (1) {
        case 1:
            r = 1;
            break;
            r = 2;
    }
    return r;
}
$.checkEquals(1, f());
````

# 4. case内let块级作用域

````expr
function f(x) {
    let r = 0;
    switch (x) {
        case 1:
            let a = 10;
            r = a;
            break;
        case 2:
            let b = 20;
            r = b;
            break;
    }
    return r;
}

$.checkEquals(10, f(1));
$.checkEquals(20, f(2));
````

# 5. case体内循环的break仍终止循环

````expr
function f(x) {
    let sum = 0;
    switch (x) {
        case 1:
            for (let i = 0; i < 10; i++) {
                if (i >= 3)
                    break;
                sum = sum + i;
            }
            sum = sum + 100;
            break;
    }
    return sum;
}
$.checkEquals(103, f(1));
````

# 6. 循环内switch的break终止switch而非循环

````expr
function f() {
    let sum = 0;
    for (let i = 1; i <= 3; i++) {
        switch (i) {
            case 1:
                sum = sum + 10;
                break;
            case 2:
                sum = sum + 20;
                break;
            default:
                sum = sum + 30;
        }
        sum = sum + 1;
    }
    return sum;
}
$.checkEquals(10 + 1 + 20 + 1 + 30 + 1, f());
````

# 7. 花括号case体（既有写法）仍支持

````expr
let x = 2;
let r = 0;
switch (x) {
    case 1: { r = 10 }
    case 2: { r = 20 }
    default: { r = 30 }
}
$.checkEquals(20, r);
````

# 8. switch后代码继续执行

````expr
let r = 0;
switch (5) {
    case 1:
        r = 1;
        break;
}
r = r + 7;
$.checkEquals(7, r);
````

# 9. 空case体匹配后不执行任何语句

````expr
function f(x) {
    let r = 0;
    switch (x) {
        case 1:
        case 2:
            r = 20;
            break;
        default:
            r = 30;
    }
    return r;
}

// XLang的case不贯穿：x==1匹配空case体后直接结束switch
$.checkEquals(0, f(1));
$.checkEquals(20, f(2));
$.checkEquals(30, f(3));
````

# 10. continue在循环内switch中仍作用于循环

````expr
function f() {
    let sum = 0;
    for (let i = 0; i < 5; i++) {
        switch (i % 2) {
            case 0:
                continue;
            default:
                sum = sum + i;
        }
    }
    return sum;
}
$.checkEquals(1 + 3, f());
````
