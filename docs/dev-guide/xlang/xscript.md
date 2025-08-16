# XScript

XScript是语法类似于TypeScript的脚本语言。在XPL中可以通过`<c:script>`标签来引入XScript脚本。XScript采用了TypeScript语法的一个子集。

语法定义文件参见 [XLangParser.g4](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/model/antlr/XLangParser.g4)

## 从JavaScript语法中去除的特性

1. 去除了类定义的部分以及与`prototype`相关的部分。为简化与Java的互操作，只允许使用Java中已经存在的类型，不能新建类型.
2. 只允许与Java兼容的类型声明，不支持TypeScript中更加复杂的类型声明。
3. 去除了`undefined`，只使用`null`。
4. 去除了`generator`和`async`语法。
5. 修改了`import`语法，仅支持导入类和标签库，目前不支持导入其他xscript文件。
6. 去除了`===`相关的语法，禁止`==`进行类型转换。
7. 不允许在表达式中赋值语句，例如`while((x=f() != 0))`这种语法是不允许的

## 相比于JavaScript，增加的语法特性

1. 编译期表达式。
   通过`#{expr}`的形式表示在编译期执行的宏表达式，执行的结果会成为抽象语法树的一部分。例如

```xlang
 // 执行编译期表达式
  let x = #{ a.f(3) }
```

2. 执行XPL标签

```
<c:script>
  // 执行xpl标签
  let y = xpl('my:MyTag',{a:1,b:x+3})
</c:script>
```

`xpl`是一个宏函数，它支持三种调用形式。

```
result = xpl `<my:MyTag a='${1}' b='${x+3}' />`
result = xpl('my:MyTag',{a:1,b:x+3})
result = xpl('my:MyTag',1, x+3)
```

第三种形式要求参数顺序和标签库中定义的参数顺序相同

3. 调用扩展方法。可以为Java中的对象注册扩展对象方法，
   可以将帮助类上的静态函数注册为扩展函数

```javascript
    ReflectionManager.instance().registerHelperMethods(List.class, ListFunctions.class, null);
    ReflectionManager.instance().registerHelperMethods(String.class, StringHelper.class, "$");
    ReflectionManager.instance().registerHelperMethods(LocalDate.class, DateHelper.class, "$");
```

平台内置为`List`增加了`ListFunctions`上定义的扩展方法，为`String`增加了`StringHelper`上定义的扩展方法。所以可以在XScript中使用如下语法

```javascript
   str.$capitalize()  // 相当于调用 StringHelper.capitalize(str);
```

为了避免与Java类上已经定义的方法名冲突，一般扩展方法注册时都增加`$`前缀。
`ListFunctions`为`List`增加了`push`/`pop`等JavaScript中`Array`对象的方法，为了尽量和JavaScript语法接近，这些扩展方法没有增加
`$`前缀。

4. 安全性限制。所有以`$`为前缀的变量名保留为系统变量名，无法在XScript脚本中声明或者设置以`$`为前缀的变量。禁止访问`System`,
   `Class`等敏感对象。

## 全局变量

在XScript中可以使用的全局变量和全局函数通过`EvalGlobalRegistry`类进行管理。目前主要是注册了`GlobalFunctions`类中定义的方法。

在调试模式下，通过前端REST请求可以获知当前系统中注册的所有全局变量和全局函数

1. `/r/DevDoc__globalFunctions`
2. `/r/DevDoc__globalVars`

其他调试信息参见[debug.md](../debug.md)

| 变量名           | 描述                                  |
|---------------|-------------------------------------|
| $context      | 对应于ContextProvider.currentContext() |
| $scope        | 当前运行时的IEvalScope                    |
| $out          | 当前运行时的IEvalOutput                   |
| $beanProvider | 当前运行时的IEvalScope所关联的IBeanProvider   |
| $evalRt       | 当前运行时 EvalRuntime                   |
| $             | 对应于 Guard类                          |
| $JSON         | 对应于JsonTool类                        |
| $Math         | 对应于MathHelper类                      |
| $String       | 对应于StringHelper类                    |
| $Date         | 对应于DateHelper类                      |
| _             | 对应于Underscore类                      |
| $config       | 对应于AppConfig类                       |

## 扩展属性
实现了IPropGetMissingHook和IPropSetMissingHook扩展接口，在脚本代码或者表达式语言中就可以访问动态实体属性时，形式与访问普通属性相同。

```typescript
entity.extField = 3;

// 等价于
entity.prop_set('extField',3);
```

## 特定上下文变量

* `codeGenerator`: `XCodeGenerator`类型，`precompile`目录下的代码生成模板中可用
* `__dsl_root`：`XNode`类型，在`x:gen-extends`和`x:post-extends`这样的元编程处理段中可用

## 隐式传递scope


在XLang表达式中，也提供了隐式传递IEvalScope的机制。

```
    @EvalMethod
    public static ExcelImage QRCODE(IEvalScope scope) {
        IXptRuntime xptRt = IXptRuntime.fromScope(scope);
        ExpandedCell cell = xptRt.getCell();

        QrcodeOptions options = new QrcodeOptions();
        cell.getModel().readExtProps("qr:", true, options);
        ...
        return image;
    }
```

如果函数上标记了`@EvalMethod`注解，则第一个参数必须是IEvalScope。在表达式中调用的时候会自动传入表达式的运行时scope。通过scope可以获取到上下文中的其他变量。

比如ReportFunctions中的上述静态函数，在XScript中调用时只需要`QRCODE()`，scope参数会自动传递，不需要明确传递。


## JS兼容性

XScript可以看作是使用JavaScript语法的Java，它使用的对象和库都是Java语言的，因此很多地方并不兼容JavaScript.

### 全局对象
XScript中没有JSON、Object等全局对象，所有的全局对象名都以`$`开头，例如`$JSON`、`$Math`、`$Date`等。

### 集合函数
* 通过ListFunctions上的扩展函数为Java的List对象增加了一些JavaScript中Array对象的方法，例如`push/pop/shift/unshift/includes/some/reduceRight/slice/splice`等。
* `forEach/map`等函数只支持一个参数，使用的是Java Collection上定义的方法。JavaScript的map和forEach都具有两个参数，可以获知记录的下标。XScript增加了`map2/forEach2`，它们的语义类似JavaScript。
