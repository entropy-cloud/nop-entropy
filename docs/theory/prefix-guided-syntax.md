# 利用前缀引导语法实现DSL的灵活嵌入

Nop平台在做DSL语法设计的时候，一个很重要的观念是分层语法设计，即我们可以把多种风格的DSL混合在一起使用，但是它们之间具有良好的形式分隔边界，在按照高层的DSL语法进行解析的时候，无需考虑低层DSL语法的解析要求。典型的，JSX语法不符合这一要求。JSX语法可以看作是普通js语法和XML语法的混合，但是jsx语法解析器在Lexer中统一识别js和XML的token，并把语法元素解析到一个统一的AST语法树中，并不能先按照XML解析得到一个总体结构，然后再针对局部解析js或者整体先按照js解析，然后再针对局部按照XML解析。

## 一. 在XML中嵌入其他语法

在Nop平台中，XLang语言采用了XML为基础语法形式，可以通过XML标签来标记出特定语法部分。例如

```xml
<c:script>
  let x = 3;
  ...
</c:script>

<c:script lang="groovy">
   // 调用groovy脚本引擎
</c:script>
```

`<c:script>`标签内部可以执行XScript脚本（语法类似TypeScript），也可以通过lang属性来选择使用groovy等其他脚本引擎，从而支持不同的程序语法。这里的关键点在于，在XML层面，`<c:script>`只是一个普通的XML标签，它的内容只是一个普通的字符串文本，只有局部的`<c:script>`标签的处理器需要具有Groovy语法的知识，而作为整体的XPL模板语言并不需要有任何Groovy的知识。

类似于Lisp语言中的宏，XLang语言的`<c:script>`本质上也只是一种宏标签，它在编译期会自动运行，执行具体的解析逻辑，并返回解析得到的抽象语法树。这一做法可以被很简单的推广到其他DSL语法格式上，例如在前台页面中使用的布局语法

```xml
<ui:Form>
  <layout>
  fieldA fieldB
  fieldC
  </layout>
</ui:Form>
```

`<ui:Form>`表示根据当前对象的元数据生成一个表单，这个表单具有两行，第一行显示fieldA, fieldB，第二行显示fieldC，具体使用的控件根据meta文件中配置的字段类型来自动选择。在实现层面，ui:Form会在编译期自动解析Layout布局对应的DSL语法，并生成LayoutModel对象，然后结合meta信息自动生成组件代码等。

基于这里的机制，其实我们很容易实现JetBrains公司的MPS产品中所提出的Projectional Editor的概念。所谓的可视化编辑器，其产生的输出可以看作是使用某种DSL来定义的模型，而DSL语法解析后的结果都是AST抽象语法树，而在任意的AST节点，我们都可以重新选择一个DSL语法形式，它用于实现对本AST节点信息的一个定制的表达。

## 二. 在JavaScript中嵌入其他语法

在XScript脚本语言中，我们使用模板字符串语法来嵌入XML。与JavaScript中不同的是，XScript脚本语言不会自动识别嵌入的表达式，例如

```javascript
let x = xpl `<c:if test="${condition}">...</c:if>`
```

在标准的js语法中，`${condition}`会被自动按照表达式语法进行解析，而在XScript中，整个模板字符串会被作为字符串来解析，通过重复的````字符来表示转义。

在我看来，js模板字符串的设计中，自动识别`${expr}`是一个错误，它破坏了一种自然的分层语法设计，使得模板字符串内的DSL语法与外部的JavaScript语法混杂到了一起，造成了解析和处理层面一系列的不便，同时也影响了内部DSL语法的直观性。

在XScript的实现中，模板字符串语法被定义为编译期宏函数的调用，即xpl为编译期自动执行的宏函数，它的具体实现如下

```java
@Macro
public static Expression xpl(IXLangCompileScope scope, CallExpression expr) {
    String tpl = getTemplateLiteralArg(expr);
    if (StringHelper.isBlank(tpl))
        return Literal.nullValue(expr.getLocation());

    XNode node = XNodeParser.instance().forFragments(true).parseFromText(expr.getArgument(0).getLocation(), tpl);
    return scope.getCompiler().parseTagBody(node, scope);
}
```

在编译期会执行xpl宏函数，传入模板字符串表达式所对应的AST节点。这一功能类似于C#中LinQ表达式的处理，只是宏函数是一个更通用的机制。本质上，它的作用也类似于Lisp语言中的宏。

这一机制可以用于多种DSL的嵌入，比如

```javascript
let p = xpath `/a/a[@id=a]`
```

表示在编译期会解析xpath语法，并返回一个XPath对象赋值给变量p。

如果要在XScript中嵌入类似LinQ的SQL语法，可以使用

```javascript
function myFunc(x,y){
    return x + y;
} 
let obj = ...
let {a,b} = linq `
  select sum(x + y) as a , sum(x * y) as b
  from obj
  where myFunc(x,y) > 2 and sin(x) > cos(y)
`
```

在特殊定制的linq宏函数中，我们可以非常精确的分析出myFunc为外部环境中调用的函数，而obj为外部环境中定义的变量，实现SQL语法和JavaScript语法的自然融合。

JavaScript内置的模板字符串功能在XScript中可以通过tpl宏函数调用来实现

```javascript
let x = tpl `sss ${myVar}`
```

## 三. 前缀引导语法

上一节中介绍的xpl宏函数机制可以看作是前缀标识 + 多行文本字符串这种形式，前缀标识被解释为处理函数，而多行文本字符串具有内外两种结构，外部结构就是普通的多行文本，仅需要对特殊字符````进行识别转义即可，其他的回车换行反斜杠等字符都不需要进行转义。而内部结构就是仅由前缀标识函数所识别的DSL语法。这一形式的优点在于它可以在完全不改变外部程序语法也不需要任何内部DSL知识的情况下，将DSL无缝嵌入到外部程序结构中。我将这一形式称之为前缀引导语法。它可以被看作是DSL设计的一个通用技巧，应用范围非常广泛。

在Nop平台的设计中，我们大量使用了前缀引导语法的设计形式

### 3.1 加密字段

在配置文件中存储密码的时候需要加密，我们约定以`@enc:`为前缀的配置值需要被自动解密。

在ORM模型中如果标注了某个字段需要加密存储，则保存到数据库中时会自动加密并增加`@enc:`前缀，这样在读取的时候可以自动识别是否需要解密，便于在系统运行过程中动态调整加解密设置。

### 3.2 动态配置

在配置文件中的值一般为固定值，但是在灰度发布场景下，我们希望将静态配置扩展为动态配置，对满足某些条件的调用使用配置A，而对其他调用使用配置B。在Nop平台中，我们通过`@switch`前缀来标识动态配置项。例如

```
nop.a.b = @switch: {json格式的业务规则}
```

在调用端使用的时候

```java
static final IReference<String> CFG_XXX = AppConfig.varRef("nop.a.b",String.class);

CFG_XXX.get()
```

会根据switch配置返回动态确定的值。

使用前缀引导语法这种形式来扩展配置项，完全保持了系统原有的key=value配置结构，可以复用此前的界面和存储，只需要在使用端增加一个局部的DynamicReferenc的支持即可。

### 3.3 Redis缓存编码

将Java对象通过JSON序列化保存到Redis缓存中时，在JSON字符串之前增加`@data:DATA_CLASS`前缀，将反序列化所需要的Java类名（可以包含包含泛型信息）和数据一起打包保存到缓存中，便于反序列化后直接得到强类型的Java对象。

### 3.4 IoC配置

在Spring的XML配置中，如果我们希望表达value对应的不是值，而是引用名称，则需要使用一个新的属性名

```xml
<bean>
  <property name="myProp" value-ref="xxx" />
</bean>
```

value-ref表示属性myProp的值不是xxx这个字符串，而是它所对应的bean，xxx为bean的引用名称。实际上，为了支持引用的概念，spring引入了ref, value-ref, key-ref等多个额外的结构。而在Nop平台中，我们可以使用`@ref:name`这种形式来统一表达对象引用

```xml
<bean>
  <property name="myProp" value="@ref:xxx" /> 
</bean>
```

在任何需要对象引用的地方都可以直接使用`@ref`来表达，极大简化了领域对象的结构。同时，因为对象属性通过唯一的名称来标识，而不是分裂为 value-ref/value等多个属性名，这也方便了Delta定制（按名称直接覆盖，而不需要考虑多种情况，而不需要考虑多个属性同时存在时的优先级问题）。

Spring号称是完全声明式的依赖注入，但是涉及到IoC容器内部的一些概念的时候，它还是要依赖于明确约定的内部接口，比如为了注入bean的名称，需要实现BeanNameAware接口

```java
interface BeanNameAware{
    void setBeanName(String beanName);
}
```

在Nop平台中，我们可以通过`@bean:id`来表示注入bean的名称

```xml
<bean>
   <property name="beanName" value="@bean:id" />
</bean>
```

类似的，可以用`@bean:container`来表示注入当前容器等。


基于可逆计算理论设计的低代码平台NopPlatform已开源：

* gitee: https://gitee.com/canonical-entropy/nop-entropy
* github: https://github.com/entropy-cloud/nop-entropy
