# XScript 简明指南

## 1. 概述

XScript是语法类似于TypeScript的脚本语言，在XPL模板中通过`<c:script>`标签引入，用于编写业务逻辑。它采用了TypeScript语法的一个子集，简化了与Java的互操作。

## 2. 语法特点

### 2.1 与JavaScript的区别

XScript去除了JavaScript中一些复杂的特性：
- 去除了类定义和`prototype`相关部分
- 只允许使用Java中已存在的类型，不能新建类型
- 只使用`null`，不使用`undefined`
- 去除了`generator`和`async`语法
- 修改了`import`语法，仅支持导入类和标签库
- 去除了`===`相关语法，禁止`==`进行类型转换

### 2.2 新增特性

XScript增加了一些Java集成和XLang特定的特性：
- 编译期表达式：通过`#{expr}`形式表示编译期执行的宏表达式
- 支持调用XPL标签：通过`xpl()`函数在脚本中调用XPL标签
- 扩展方法：可以为Java对象注册扩展方法，例如`str.$capitalize()`
- 安全性限制：禁止访问敏感对象，系统变量以`$`开头

## 3. 核心功能

### 3.1 基本语法

```javascript
<c:script>
  // 变量声明
  let a = 1;
  let b = a + 2;
  let c = "hello" + b;
  
  // 条件语句
  if (a > 0) {
    // 条件为true时执行
  } else if (a === 0) {
    // 条件为false时执行
  } else {
    // 其他情况
  }
  
  // 循环语句
  for (let i = 0; i < 10; i++) {
    // 循环执行
  }
  
  // 函数调用
  let result = StringHelper.firstPart(c, ".");
  
  // 扩展方法调用
  let capitalized = c.$capitalize();
</c:script>
```

### 3.2 全局变量

XScript提供了一些常用的全局变量，所有变量名都以`$`开头：

| 变量名           | 描述                                  |
|---------------|-------------------------------------|
| $context      | 对应于ContextProvider.currentContext() |
| $scope        | 当前运行时的IEvalScope                    |
| $out          | 当前运行时的IEvalOutput                   |
| $beanProvider | 当前运行时的IEvalScope所关联的IBeanProvider   |
| $             | 对应于 Guard类                          |
| $JSON         | 对应于JsonTool类                        |
| $Math         | 对应于MathHelper类                      |
| $String       | 对应于StringHelper类                    |
| $Date         | 对应于DateHelper类                      |
| _             | 对应于Underscore类                      |
| $config       | 对应于AppConfig类                       |

### 3.3 编译期表达式

编译期表达式在编译时执行，结果会成为抽象语法树的一部分：

```javascript
<c:script>
  // 编译期执行的表达式
  let x = #{ a.f(3) };
  
  // 编译期调用XPL标签
  let y = xpl('my:MyTag', {a:1, b:x+3});
</c:script>
```

### 3.4 调用XPL标签

XScript可以通过`xpl()`函数调用XPL标签，支持三种调用形式：

```javascript
// 形式1：模板字符串形式
result = xpl `<my:MyTag a='${1}' b='${x+3}' />`;

// 形式2：对象参数形式
result = xpl('my:MyTag', {a:1, b:x+3});

// 形式3：位置参数形式
result = xpl('my:MyTag', 1, x+3);
```

## 4. 最佳实践

1. **保持简洁**：XScript主要用于编写业务逻辑，避免编写复杂的算法
2. **使用扩展方法**：优先使用扩展方法，如`str.$capitalize()`，提高代码可读性
3. **避免全局变量**：尽量减少全局变量的使用，使用局部变量
4. **安全编程**：避免访问敏感对象，如`System`、`Class`等
5. **类型安全**：虽然XScript是动态类型语言，但尽量保持类型一致性

## 5. 应用场景

- 在XPL模板中编写业务逻辑
- 处理数据转换和计算
- 调用Java类和方法
- 与XPL标签交互
- 编写编译期宏表达式

## 6. 总结

XScript是XLang语言家族中的重要成员，它结合了JavaScript的灵活性和Java的强大功能，特别适合在模板中编写业务逻辑。通过与XPL模板语言的紧密集成，XScript可以实现复杂的业务逻辑和模板生成。

XScript的设计简洁易用，去除了JavaScript中的复杂特性，同时增加了与XLang生态系统集成的特性，如编译期表达式和XPL标签调用。它是构建LowCode平台和领域特定语言的重要工具。