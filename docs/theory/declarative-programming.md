# 从可逆计算看声明式编程

可逆计算是笔者提出的下一代软件构造理论，它的核心思想可以表示为一个通用的软件构造公式

```
App = Delta x-extends Generaor<DSL>
```

在这一公式中，所谓的领域特定语言（DSL）占有核心位置，而可逆计算在实践中的主要策略就是将业务逻辑分解为多个业务切面，针对每个业务切面设计一种DSL来描述。DSL是声明式编程的一种典型范例，因此可逆计算可以被看作是声明式编程的一种实现途径。透过可逆计算的概念，我们可以获得对声明式编程的一些新的理解。

[可逆计算](https://zhuanlan.zhihu.com/p/64004026)

## 一. 虚拟化

DSL是声明式的，因为它所表达的内容不像是可以直接交由某个物理机器执行的，而必须通过某种interpreter/compiler进行翻译。不过如果换一个角度去考虑，这个interpreter一样可以被看作是某种虚拟机，只不过它**不一定是冯.诺伊曼体系结构**的。这里核心的要点在于，底层的interpreter只要支持少数固定的针对某个特定领域的原语，即可执行DSL所编写的程序，从而实现不同的业务逻辑。在一般的程序结构中，业务逻辑是一次性表达的，而在基于DSL概念的程序中，逻辑是分两阶段表达的。底层是与具体业务无关的，只与领域结构有关的相对通用的逻辑，而上层才是多变的，与特定业务场景绑定的逻辑。

在比较现代的大型软件结构设计中，多多少少都会体现出构造某种内部虚拟机的努力。以[Slate富文本编辑器框架](https://github.com/ianstormtaylor/slate)为例，它号称是一个“完全可定制的”框架，核心是一个所谓的“Schema-less core"。也就是说，Slate的内核并不直接知道它所编辑的数据的具体结构，这些结构是通过schema告诉内核的。schema定义了允许哪些节点，节点有哪些属性，属性需要满足什么样的格式。

```
const schema = {
  document: {
    nodes: [
      {
        match: [{ type: 'paragraph' }, { type: 'image' }],
      },
    ],
  },
  blocks: {
    paragraph: {
      nodes: [
        {
          match: { object: 'text' },
        },
      ],
    },
    image: {
      isVoid: true,
      data: {
        src: v => v && isUrl(v),
      },
    },
  },
}
​
<Editor
  schema={schema}
  value={this.state.value}
  ...
/>
```

自定义的render函数类似于解释器

```
function renderNode(props, editor, next) {
  const { node, attributes, children } = props
​
  switch (node.type) {
    case 'paragraph':
      return <p {...attributes}>{children}</p>
    case 'quote':
      return <blockquote {...attributes}>{children}</blockquote>
    case 'image': {
      const src = node.data.get('src')
      return <img {...attributes} src={src} />
    }
    default:
      return next()
  }
}
```

传统的富文本编辑器，在内核中需要明确知道bold/italic这样的概念，而在Slate的内核中，关键的关键就在于不需要知道具体的业务含义就可以操纵对应的技术元素，这就类似于硬件指令不需要知道软件层面的业务信息。通过使用同一个内核，我们可以通过类似配置的方式实现Markdown编辑器，Html编辑器等多种不同用途的设计器。

## 二. 语法制导

实现虚拟化，最简单的方式是采用一一对应的映射机制，即将一组动作直接附加到DSL的每条语法规则上，处理到DSL的某个语法节点时，就执行对应的动作，这叫作语法制导（Syntax Directed）。

基于XML或者类XML语法的模板技术，例如Ant脚本，FreeMarker模板都可以看作是语法制导翻译的范例。以Vue的模板语法为例，

```
  <template>
    <BaseButton @click="search">
      <BaseIcon name="search"/>
    </BaseButton>
  </template>
```

template相当于是将抽象语法树（AST）直接以XML格式展现，处理到组件节点时，将会直接根据标签名称定位到对应组件的定义，然后递归进行处理。整个映射过程是上下文无关的，即映射过程并不依赖于节点所处的上下文环境，同样的标签名总是映射到同样的组件。

同样的套路构成了Facebook的GraphQL技术的核心，它通过语法制导将待执行的数据访问请求发送到一个延迟处理队列，通过合并请求实现批量加载优化。
例如，为处理如下gql请求

```
    query {
      allUsers {
        id
        name
        followingUsers {
          id
          name
        }
      }
    }
```

后台只需要针对数据类型指定对应dataLoader

```
const typeDefs = gql`
  type Query {
    testString: String
    user(name: String!): User
    allUsers: [User]
  }

  type User {
    id: Int
    name: String
    bestFriend: User
    followingUsers: [User]
  }
`;

const resolvers = {
  Query: {
    allUsers(root, args, context) {
      return ...
    }
  },
  User: {
     // allUsers调用返回的每个User对象，其中只有followingUserIds属性，它需要被转换为完整的User对象
    async followingUsers(user, args, { dataloaders }) {
      return dataloaders.users.loadMany(user.followingUserIds)
    }
  }
};
```

为了方便实现语法制导这一模式，现代程序语言已经有了默认的解决方案，那就是基于注解（Annotation）的元编程技术。例如，python中的函数注解

```
 def logged(level, name=None, message=None):
    """
    Add logging to a function. level is the logging
    level, name is the logger name, and message is the
    log message. If name and message aren't specified,
    they default to the function's module and name.
    """
    def decorate(func):
        logname = name if name else func.__module__
        log = logging.getLogger(logname)
        logmsg = message if message else func.__name__

        @wraps(func)
        def wrapper(*args, **kwargs):
            log.log(level, logmsg)
            return func(*args, **kwargs)
        return wrapper
    return decorate

 # Example use
 @logged(logging.DEBUG)
 def add(x, y):
    return x + y
```

将注解看作是函数名，这一观念非常简单直观，TypeScript也采纳了同样的观点。相比之下，Java的APT（Annotation Processing Tool）技术显得迂回冗长，这也导致很少有人使用APT去实现自定义的注解处理器，不过它的作用是在编译期，拿到的是AST抽象语法树，因此可以做一些更加深刻的转化。Rust语言中的过程宏（procedural macros）则展现了一种更加优雅的编译期实现方案。

```
    #[proc_macro_derive(Hello)]
    pub fn hello_macro_derive(input: TokenStream) -> TokenStream {
        // 从token流构建AST语法树
        let ast = syn::parse(input).unwrap();

        // 采用类似模板生成的方式构造返回的语法树
        let name = &ast.ident;
        let gen = quote! {
        impl Hello for #name {
            fn hello_macro() {
                println!("Hello, Macro! My name is {}", stringify!(#name));
            }
        }
        };
        gen.into()
    }

    pub trait Hello {
        fn hello_macro();
    }

    // 使用宏为Pancakes结构体增加Hello这个trait的实现
    #[derive(Hello)]
    struct Pancakes;
```

## 三. 多重诠释

传统上一段代码只有一种设定的运行语义，一旦信息从人的头脑中流出经由程序员的手固化为代码，它的形式和内涵就固定了。但是可逆计算指出，逻辑表达应该是双向可逆的，我们可以逆转信息的流向，将以代码形式表达的信息反向提取出来，这使得“一次表达，多重诠释”成为实现声明式编程，分离表达与运行的常规手段。例如，下面一段过滤条件

```
<and>
  <eq name="status" value="1" />
  <gt name="amount" value="3" />
</and>
```

展现在前台，对应于一个查询表单，应用到后台，对应于Predicate接口的实现，发送到数据库中，转化为SQL过滤条件的一部分。而这一切，并不需要人工编码，它们只是同一信息的多重诠释而已。

随着编译技术的广泛传播，传统上的命令式编程经过再诠释，现在也具有了声明式的意味。比如，Intel的OpenMP（Open Multi-Processing）技术

```
   int sum = 0;
   int i = 0;

   #pragma omp parallel for shared(sum, i)
   for(i = 0; i < COUNT;i++){
      sum = sum + i;
    }
```

只要在传统的命令式语句中增加一些标记，即可把串行执行的代码转化为并行程序。

而在深度学习领域，编译转换技术更是被推进到了新的深度。pytorch和tensorflow这样的框架均可将形式上的python函数编译转换为GPU上运行的指令。而TVM这样的大杀器，甚至可以直接编译得到FPGA代码。

多重诠释的可能性，使得一段代码的语义永远处于开放状态，一切都是虚拟化的。

## 四. 差量修订

可逆计算将差量作为第一性的概念，将全量看作是差量的特例。按照可逆计算的设计，DSL必须要定义差量表示，允许增量改进，同时，DSL展开后的处理逻辑也应该支持增量扩展。以Antlr4为例，它引入了import语法和visitor机制，从而第一次实现了模型的差量修订。

在Antlr4中，import语法类似面向对象编程语言中的继承概念。它是一种智能的include，当前的grammar会继承导入的grammar的所有规则，tokens specifications，names actions等，并可以重写规则来覆盖继承的规则。

![antlr\_combined](antlr/combined.png)

在上面的例子中，MyElang通过继承ELang得到若干规则，同时也重写了expr规则并增加了INT规则。终于，我们不再需要每次扩展语法都要拷贝粘贴了。

在Antlr4，不再推荐将处理动作直接嵌入在语法定义文件中，而是使用Listener或者Visitor模式，这样就可以通过面向对象语言内置的继承机制来实现对处理过程的增量修订。

```
// Simple.g4
grammar Simple;

expr  : left=expr op=('*'|'/') right=expr #opExpr
      | left=expr op=('+'|'-') right=expr #opExpr
      | '(' expr ')'                      #parenExpr
      | atom=INT                          #atomExpr
      ;

INT : [0-9]+ ;

// Generated Visitor
public class SimpleBaseVisitor<T> extends AbstractParseTreeVisitor<T> implements SimpleVisitor<T> {
    @Override public T visitOpExpr(SimpleParser.OpExprContext ctx) { return visitChildren(ctx); }
    @Override public T visitAtomExpr(SimpleParser.AtomExprContext ctx) { return visitChildren(ctx); }
    @Override public T visitParenExpr(SimpleParser.ParenExprContext ctx) { return visitChildren(ctx); }
}

class MyVisitor<Double> extends SimpleBaseVisitor<Double>{
  ...
}
```

## 五. 自动微分

如果说声明式编程的理想是人们只需要描述问题，由机器自动找出解决方案，那么我们从哪里去找一类足够通用，而且又能够自动求解的问题呢？幸而自牛顿以降，科学昌明，我们还是积攒了几个这样的祖传问题的，其中一个就是自动微分。

只要指定几个基础函数的微分表达式，我们就可以自动计算大量复合函数的微分，这一能力是目前所有深度学习框架的必备技能。可逆计算理论指出，自动计算差量这一概念可以被扩展到数学或者算法领域之外，成为一种有效的软件结构构造机制。

以k8s为例，这一容器编排引擎的核心思想是通过声明式的API来指定系统的“理想”状态，然后通过监控测量不断发现当前状态与理想状态的偏差，自动执行相应的动作来“纠正”这些偏差。它的核心逻辑可以总结为如下公式：

$$
action = Translator( PhysicalState - Planner(LogicalSpec) )
$$

k8s所采用的这种设计原理可以称为是状态驱动（State Driven），它关注的重点是系统的状态以及状态之间的差异，而不再是传统的基于动作概念的API调用和事件监听。从动作（Action）到状态（State）的这种思维转换其实类似于物理学中从力的观点过渡到以势能函数（Potential）为基础的场（Field）的观点。

从状态A迁移到状态B，无论经过什么路径，最终得到的结果都是一样的，因此势的概念是路径无关的。摆脱了路径依赖极大简化了我们对系统的认知。而所谓的力，随时可以通过对势函数求导，从势函数的梯度得到。同样，在k8s中，对于任意的状态偏差，引擎都可以自动推导得到相应需要执行的动作。

$$
F = - \nabla \phi
$$

从状态A迁移到状态B有多条可行的路径，在这些路径中按照成本或者收益原则选择其一，这就是所谓的优化。

从动作到状态的转换是整体思维模式的一种变革，它要求我们用新的世界观去思考问题，并不断调整相应的技术实现去适应这种世界观。这一变革趋势正在逐渐加强，也在越来越多的应用领域促生着新的框架和技术。

势的观念要求我们对状态空间有着全面的认知，每一个可达的状态都有着合法的定义。有的时候，对于特定应用而言，这种要求可能过于严苛，例如，我们可能只需要找到从特定状态A到特定状态B的某一条可行的道路即可，没必要去研究所有状态构成的状态空间自身，此时传统的命令式的做法就足够了。

## 六. 同构转化

太阳底下没有新鲜事。在日常编程中，真正需要人们去创造的新的逻辑是很少的，绝大多数情况下我们所做的只是某种逻辑关系的映射而已。比如说，日志收集这件事情，为了采集日志文件内容进行分析，一般需要使用类似logstash这样的工具解析日志文本到json格式，然后投递到ElasticSearch服务。但是，如果在打印日志的时候，我们就保留对象格式，那么实际上可以不需要中间logstash的解析过程。如果需要对属性过滤或者进行再加工，也可以直接对接一个通用的对象映射服务（可以通过可视化界面进行映射规则配置），而不需要为日志处理领域单独编写一套实现。

```
    // 保持对象格式输出日志
   LOG.info(日志码，{参数名：参数值});
```

很多时候，我们之所以需要程序员去编写代码，原因在于跨越边界时出现了信息丢失。例如，以文本行形式打印日志时，我们丢失了对象结构信息，从文本反向恢复出结构的工作很难自动完成，它必须借助程序员的头脑，才能消除解析过程中可能出现的各种歧义情况。程序员头脑中的信息包括我们所处的这个世界的背景知识，各种习惯约定，以及整体架构设计思想等。因此，很多看似逻辑上等价的事情往往无法通过代码自动完成，而必须通过增加人这个变量来实现配平。

$$
A \approx B \\
A + 人 = B + 人
$$

现代数学是建立在同构概念基础之上的，在数学上我们说A就是B，潜台词说的是A等价于B。等价归并大幅削减了我们所需要研究的对象，加深了我们对系统本质结构的认识。

> 为什么 3/8 = 6/16, 因为这就是分数的定义！（3/8，6/16，9/24...）这一系列表示被定义为一个等价类，它的代表元素就是3/8（参见 彭罗斯《通向实在之路--宇宙法则的完全指南》一书的前言）。

可逆计算强调逻辑结构的可逆转化，从而试图在软件构造领域建立起类似数学的抽象表达能力，而这只有当上下游软件各个部分都满足可逆原则时才能够实现效用的最大化。例如，当细粒度组件和处理过程均可逆时，可视化设计器可以根据DSL直接生成，而不需要进行特殊编码

$$
可视化界面 = 界面生成器(DSL)\\
DSL = 数据提取(可视化界面)\\
DSL = 界面生成器^{-1}\cdot界面生成器(DSL)
$$

在现实开发过程中实现可逆性的一个障碍在于，目前软件开发的目的性都是很强的，因此与当前场景无关的信息往往无处安放。为了解决这个问题，必须在系统底层增加允许自定义扩展的元数据空间。

$$
A \approx B \\
    A + A' \equiv = B + B' \\
$$

对应于A'部分的信息在当前的系统A中不一定会使用，但是为了适应系统B的应用逻辑，我们必须找到一个地方把这些信息存储下来。这是一种整体性的协同处理过程。你注意到没有，所有能称得上现代的程序语言都经历了戴帽子工程改造，都支持某种形式的自定义注解（Annotation）机制，一些扩展的描述信息会存在帽子里随身携带。换句话说，(data, metadata)配对才是信息的完整表达，这和消息对象总是包含(body, headers)是一个道理。

世界如此复杂，目的为何唯一？在声明式的世界中，我们有必要持有一种更加开放的态度。戴帽子不为了挡风挡雨，也不为了遮阳防晒，我就为了好看不行吗？metadata是声明式的，一般我们说它是描述数据的数据，但实际上它就算当前不描述任何东西可以有自己存在的理由，不是说有一种用叫“无用之用”吗。
