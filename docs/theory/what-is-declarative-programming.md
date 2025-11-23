# 什么是声明式编程

## 一. 声明式 vs. 命令式

什么是声明式编程？一般来说我们对于声明式的理解都是相对于命令式（imperative）而言的。图灵教会了我们imperative的真谛，并赋予了它数学意义上的精确定义：一台有状态的机器，根据明确的指令（instruction）一步步的执行。而所谓的声明式，它可以看作是命令式的反面。曾有人言：一切非imperative，皆是declarative。从这个意义上说，越是偏离图灵机的图像越远的，就越是声明式的。

所以，函数式编程（Functional Programming）是声明式的，因为它不使用可变状态，也不需要指定任何的执行顺序关系（可以假定所有的函数都是同时执行的，因为存在引用透明性，所谓的参数和变量都只是一堆符号的别名而已）。逻辑式编程（Logical Programming）也是声明式的，因为我们只需要通过facts和rules描述我们所需要解决的问题，具体的求解路径由编译器和程序运行时自动决定。

如果说命令式对应于由具体的物理机器可执行的步骤，那么声明式就可以看作是对应于更高级别的抽象的表达。由此引申出一种令人浮想联翩的理解：命令式是关于"how to do"的，而声明式是关于"what to do"的。在这种理解下，SQL语言作为一种领域特定语言（DSL）是声明式的。SQL语言描述了我们希望得到的逻辑上的数据加工结果，由执行引擎将SQL语言翻译为物理执行计划。

参考Kowalski曾提出的公式：algorithm = logic + control，逻辑（解决问题时所需要用到的知识）可以独立于具体执行时的控制流（具体使用知识的解题策略）得到表达。在DSL中，重要的是表达所有需要表达的信息，表达时的先后顺序往往是不重要的，并且与具体执行时的处理顺序也没有什么必然的关系。比如SQL语句表连接的先后顺序，以及过滤条件的先后顺序原则上是不影响执行结果的。

```sql
select *
   from a,b,c
   where a.x = b.x and c.x = a.x
```

在逻辑上等价于

```sql
select *
   from c,b,a
   where c.x = a.x and a.x = b.x
```

注意到select写在from部分的前面并不意味着select部分先执行，实际上这里的顺序仅仅是习惯上的。在C#的LINQ语法中，我们会这样写

```csharp
from x in array
  where x % 2 == 1
  orderby x descending
  select x * x;
```

## 二. 声明式编程：表达与运行分离

如果将命令式编程看作是一种"忠实的"表达（表达了就要执行，而且所表达的正是要执行的内容），那么声明式编程就是相当不老实的表达。

### 表达了可以不执行，甚至没法执行

比如说

```javascript
list = range(0, Infinity); // 得到一个从0到无穷大的所有整数构成的数组
list.take(5)            // 取数组的前5条记录
```

声明式编程中延迟计算是一个常见的特性，它极大增加了逻辑组织结构的灵活性。比如在WebMVC架构中

```java
// action中
entity = dao.getEntity(id)

// view中
entity.mainTable
entity.subItems
```

基于ORM的延迟加载特性可以同时兼顾表达的便捷性和按需访问的高性能。在action层可以直接表达获取相关数据，但并不真正把所有数据都读取到内存中。当实际使用时，才通过延迟加载机制进行数据读取。

### 不仅表达当下，还表达未来

现代编程语言中标配的Promise对象，它表示了未来可以获得的一个值，当我们还未真正得到这个值的时候，就可以把它作为返回值返回，并在程序中作为参数传来传去。   

而在传统的命令式编程概念中，函数的返回就表示执行完毕，如果是异步执行，则只能通过回调函数获取通知，在概念层面上我们并无法直接定义和使用"未来的值"。

```javascript
async function asyncValue(){
    return new Promise((resolve, reject) => {
        setTimeout(() => resolve('a'), 1000)
     });
}

var result = asyncValue();
doSomething(result);

async function doSomething(input){
     console.log('begin');
     var result = await input;
     console.log('1 second later: result='+result);
}
```

### 不仅表达自己有的，还表达自己没有的

未来的值虽然现在未来，但毕竟未来可期。但如果根本不知道未来是否会来，那能否给它分配一个表达形式呢？

在groovy语言中，提供了类似Ruby的methodMissing机制

```groovy
class Foo {

    def methodMissing(String name, def args) {
        println "Missing method name is $name"
    }

    static def $static_methodMissing(String name, Object args) {
        println "Missing static method name is $name"
    }
    static def $static_propertyMissing(String name) {
        println "Missing static property name is $name"
    }

    def propertyMissing(String name) { println "Missing property name is $name" }
}

foo = new Foo();
fo.x;
foo.f();
```

## 三. 声明式 & 命令式

传统上主流编程语言都是偏向命令式的，因为我们的硬件运行环境都是图灵机的升级版本，软件的功能就是指导硬件执行预设的动作。甚至有一种说法，程序开发工作的本质就是命令式的，毕竟为老板的小目标（what）找到具体的技术实现方案（how）才是我们的本职工作。但是，如果仔细观察一下现代编程的日常，就会发现，绝大多数的编程工作是建立在声明式API的基础之上的。

比如说，要显示界面，我们通过字符串操作拼接出HTML文本，为了调用服务，我们拼接出URL和JSON文本，为了访问数据库，我们拼接出SQL请求。很少有人清楚，如何使用命令式的指令一步步的构造出完整的应用程序。即使是最简单的前台拖拽动作，我们所会的多半也只是调用一个Draggable组件，监听（声明）一下拖拽结束时的触发动作。在这个意义上说，我们所掌握的编程技能只是从一种声明式视图映射到另一种声明式视图之间的转换策略而已。

整个软件开发生态环境正在不断向着声明式和命令式水乳交融的方向发展。以前，为了突出声明式的部分，我们会选择模板语言，即在描述性内容中嵌入少量的命令式控制逻辑。而在今天，出现了JSX这种直接将描述性内容嵌入到命令式上下文中的技术。更进一步，类似SwiftUI这种基于通用程序语言直接实现声明式表达的技术正快步向我们走来

```swift
List(landmarks) { landmark in
       HStack {
          Image(landmark.thumbnail)
          Text(landmark.name)
          Spacer()

          if landmark.isFavorite {
         Image(systemName: "star.fill")
            .foregroundColor(.yellow)
          }
       }
    }
```

## 四. 可逆计算视角下的声明式编程

我们如何才能进一步推进声明式编程？如果套用What & How的比喻，方向就在于如何通过系统化的方案来定义What, 并自动推导得到How。可逆计算提供了实现DSL的一整套完整技术路线，同时也为实现声明式编程带来一些新的启发。

这里空白太小，我写不下了，且听下回分解。

基于可逆计算理论设计的低代码平台NopPlatform已开源：

- gitee: https://gitee.com/canonical-entropy/nop-entropy
- github: https://github.com/entropy-cloud/nop-entropy
