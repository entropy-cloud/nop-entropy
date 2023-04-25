# XScript

XScript是语法类似于TypeScript的脚本语言。在XPL中可以通过`<c:script>`标签来引入XScript脚本。XScript采用了TypeScript语法的一个子集。

语法定义文件参见 [XLangParser.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/antlr/XLangParser.g4)

## 从JavaScript语法中去除的特性

1. 去除了类定义的部分以及与prototype相关的部分。为简化与Java的互操作，只允许使用Java中已经存在的类型，不能新建类型.
2. 只允许与Java兼容的类型声明，不支持TypeScript中更加复杂的类型声明。
3. 去除了undefined，只使用null。
4. 去除了generator和async语法。
5. 修改了import语法，仅支持导入类和标签库，目前不支持导入其他xscript文件。
6. 去除了===相关的语法，禁止==进行类型转换。
7. 不允许在表达式中赋值语句，例如while((x=f() != 0))这种语法是不允许的


## 相比于JavaScript，增加的语法特性

1. 编译期表达式
通过#{expr}的形式表示在编译期执行的宏表达式，执行的结果会成为抽象语法树的一部分。例如
```xlang
 // 执行编译期表达式
  let x = #{ a.f(3) }
```

2. 执行XPL标签


3. 调用扩展方法。可以为Java中的对象注册扩展对象方法，从而在
4. 安全性限制。所有以$为前缀的变量名保留为系统变量名，无法在XScript脚本中声明或者设置以$为前缀的变量。禁止访问System, Class等敏感对象。

````
<c:script>
  // 执行编译期表达式
  let x = #{ a.f(3) }

  // 执行xpl标签
  let y = xpl('my:MyTag',{a:1,b:x+3))
</c:script>
````

xpl是一个宏函数，它支持三种调用形式。

````
result = xpl `<my:MyTag a='${1}' b='${x+3}' />`
result = xpl('my:MyTag',{a:1,b:x+3})
result = xpl('my:MyTag',1, x+3)
````
第三种形式要求参数顺序和标签库中定义的参数顺序相同

## 全局变量
在XScript中可以使用的全局变量和全局函数通过 EvalGlobalRegistry类进行管理。目前主要是注册了GlobalFunctions类中定义的方法。

在调试模式下，通过前端REST请求可以获知当前系统中注册的所有全局变量和全局函数
1. /r/DevDoc_globalFunctions
2. /r/DevDoc_globalVars

其他调试信息参见[debug.md](../debug.md)

## 特定上下文变量
* codeGenerator: XCodeGenerator类型，precompile目录下的代码生成模板中可用
* __dsl_root：XNode类型，在`x:gen-extends`和`x:post-extends`这样的元编程处理段中可用