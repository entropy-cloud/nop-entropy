# 函数式编程为什么有利于解耦(Decouple)

最近面试中发现很多人对于函数式编程的理解就是Lambda表达式和flatMap/map/join这种集合处理函数，对于函数式编程为什么有用，在什么场景下有用只有很模糊的感觉。在本文中，我就简单介绍一下函数式编程的思想以及在我们日常编程中如何应用函数式编程来实现逻辑解耦，函数式编程在哪些方面提供了对于面向对象编程的一种有益的补充。

什么是函数式编程？以下是智谱清言AI的回答：

函数式编程（Functional Programming, FP）是一种编程范式，它强调将计算过程构建为一系列的函数调用。函数式编程的基础是数学函数的概念，它具有以下特点：

1. **纯函数（Pure Functions）**：
   
   - 函数的输出只依赖于输入的参数。
   - 函数执行过程中没有副作用，例如不会改变外部状态。

2. **不可变性（Immutability）**：
   
   - 数据一旦创建，就不能被修改。如果要改变数据，需要创建一个新的数据副本。

3. **高阶函数（Higher-Order Functions）**：
   
   - 函数可以作为参数传递给其他函数，也可以作为其他函数的返回值。

4. **函数组合（Function Composition）**：
   
   - 将多个函数组合成一个新的函数，以实现更复杂的操作。

5. **递归（Recursion）**：
   
   - 函数通过调用自身来解决问题，而不是使用循环控制结构。

6. **惰性求值（Lazy Evaluation）**：
   
   - 表达式的求值被推迟到真正需要其结果时进行，这可以提高效率。
   
   ==============智谱AI创作完毕===================

以上这一大段可以说是老生常谈，但为什么存在这些概念？ 不使用这些概念是不是就不行？是不是只有在专门为函数式编程设计的库或者框架中这些概念才有用？

## 一. 函数应该具有参数和返回值

函数式编程只是一种思想，在面向对象编程语言中一样可以应用。最简单的一种应用就是发掘函数概念的能力：**函数应该有输入参数和返回值**。在面向对象编程语言中，
因为很多时候我们都可以通过this指针访问参数信息，通过数据库保存修改结果（一种副作用），所以大量的函数并没有参数或者返回值。

```javascript
// 计算利息
void calcInterest(){
  String accountId = this.requestMessage.getAccountId();
  Account account = accountRepository.getAccount(accountId);
  ...
  interestRepository.saveInterest(interest);
}
```

比如上面这个计算利息的函数，它从成员变量中读取accountId，而不是从函数参数中获取这一信息，在处理过程中大量使用各种repository，并且计算结果也保存到数据库中，没有返回值。

这种函数典型的问题是它没有什么可复用性，基本上只能在某个特定的场景中使用。比如说，我们现在要提供一个利息试算的功能，虽然计算利息的逻辑已经在上面的函数中编写了，但是**计算逻辑和持久化逻辑**耦合在了一起，导致我们无法复用上面的函数来实现利息试算。

**一个函数应该具有返回值**，这样的话我们就可以**将如何完成计算和如何使用计算结果这两件事情分开**，实现这两者的解耦。

同样重要的是，**一个函数应该具有请求参数**，除了定时任务之外，一个业务函数应该是根据明确的外部驱动信息来触发，函数和外部世界的之间的依赖关系应该尽量通过参数来明确表达出来，**具有参数的函数才能实现与上下文环境的解耦**。
应该尽量避免从ThreadLocal等上下文变量中获取信息，特别是业务层面的信息（非通用架构层面）应该明确表达、明确传递。

理想的情况下，通过观察一个函数的签名（函数名+参数类型+返回值类型）就可以知道这个函数的功能，它需要哪些信息，产生哪些信息，而不需要深入到这个函数的具体代码细节才能获取这些知识。

**在使用DDD架构的情况下，在编写业务逻辑时，我们可以完全通过聚合根对象来表达业务对象数据，从而完全避免使用repository**。这样的话，具体的数据到底是存储在数据库中，还是通过远程REST API获取，这种变化可以被屏蔽在聚合根对象接口之下。

```javascript
IntersetCalcResult calcInterest(IAccountBo accountBo){
  // 这里所有需要获取的信息都可以从accountBo这个聚合根对象读取到，因此不再需要使用各种repository依赖
  // 利息计算的结果通过返回值返回，而不是调用repository保存到
}
```

## 二. 尽量减少对共享数据的修改操作

函数式编程非常推崇不可变性，因为它是实现所谓引用透明性的关键。关于不可变性和引用透明性的关系，智谱清言AI的解释如下：

引用透明性指的是一个表达式在任何地方都可以被它的值所替代，而不影响程序的行为。简单来说，如果一个表达式是引用透明的，那么它的结果只取决于它的参数，并且没有副作用。
以下是不可变性与引用透明性之间的关系：

1. **不可变性是引用透明性的前提**：
   - 为了让一个表达式是引用透明的，它所依赖的数据必须是不可变的。如果数据是可变的，那么同一个表达式的结果可能会因为外部状态的变化而不同，这就违反了引用透明性的原则。
2. **引用透明性促进不可变性**：
   - 当你追求引用透明性时，你自然会倾向于使用不可变数据，因为只有这样才能保证表达式在程序中的任何位置都可以被它的值所替代，而不影响其他部分。
3. **共同目标**：
   - 不可变性和引用透明性都旨在减少副作用，提高代码的可预测性。不可变性确保数据在创建后不会改变，而引用透明性确保表达式的结果只依赖于其输入。
4. **可推理性**：
   - 不可变性和引用透明性都使得代码更加容易推理。不可变性让你不用担心数据在程序的某个地方被意外修改，而引用透明性让你可以安全地假设表达式的结果只与它的输入有关。
5. **函数式编程的核心价值**：
   - 在函数式编程中，不可变性和引用透明性是实现纯函数的关键。纯函数没有副作用，并且对于相同的输入总是返回相同的输出，这是函数式编程的核心价值之一。
     总结来说，不可变性和引用透明性是相辅相成的概念，它们共同构成了函数式编程中许多优秀特性的基础，包括代码的可读性、可维护性、可测试性和可重用性。通过在代码中坚持这两个原则，可以构建更加健壮和高效的软件系统。

==========智谱AI创作完毕=================

虽然不可变性非常有用，但在主流的程序语言如Java中却很少有人去强调，因为修改变量很多时候比构造全新的对象要方便很多，而且性能更好。在Java中强调在每一个细节处都维护不可变性是不现实的。但是一个有益的努力方向是尽量减少修改操作，特别是减少多线程并发修改同一个全局对象。**一个典型的做法是在全局Map中管理不可变对象，在局部环境中编辑时使用一个可修改的副本，并允许局部修改**，但是更新到全局Map中后就不再修改。这种做法可以尽量减少锁的作用范围，在局部编辑时完全不需要使用锁，也不需要对象使用volatile、synchronized等同步关键字。

```
MyObject obj = g_objects.get(objName);
MyObject copy = obj.cloneInstance(); // 或者 new MyObject()新建一个实例
copy.setXX(xx);
g_objects.put(objName,copy);
```

这种做法也可以看作是一种类似数据库MVCC事务处理的做法：开始事务后提交事务前的修改对其他人是不可见的，同一个对象有可能同时处于多个状态版本，在处理过程我们始终可以读取到修改前的状态版本。这个版本满足数据一致性要求，而我们正在修改的版本有可能充满临时变更，并不满足业务上的一致性要求。

在前端同样存在着类似的做法。比如说，前端Redux框架原本非常强调不可变数据，但是最近几年的发展出现了一个有趣的变化：开始引入Immer这个库来实现局部的可变修改。

```javascript
// 传统的构建不可变数据的做法
const todoReducer = (state = initialState, action) => {
  switch (action.type) {
    case ADD_TODO:
      // 使用展开操作符来创建 todos 数组的新副本
      return {
        ...state,
        todos: [
          ...state.todos,
          {
            id: Date.now(),
            text: action.payload,
            completed: false
          }
        ]
      };
    default:
      return
 state;
  }
};
// 使用Immer简化后的做法
const todoReducer = (state = initialState, action) => {
  switch (action.type) {
    case ADD_TODO:
      // 使用 Immer 的 produce 函数来更新状态
      return produce(state, (draft) => {
        draft.todos.push({ id: Date.now(), text: action.payload, completed: false });
      });
    default:
      return state;
  }
};
```

 Immer的produce函数类似开启了一个事务，在produce函数内部，我们可以修改draft这个临时对象，Immer通过类似AOP的Proxy代理技术记录所有修改动作，最后在退出produce函数的时候根据所有修改动作自动构建一个新的state对象。

**不使用Immer的时候所谓的不可变性意味着总是构建新的全量对象，而Immer的作用是将修改动作记录下来成为Delta差量，然后再应用Delta到初始state上，产生一个新的state**。

不可变性最本质的影响是我们可以区分出状态空间中所有演化的细节，这是可预测性的本质来源。如果是允许修改，则同一个对象的状态会被覆盖，除非我们记录下每次修改前的拷贝或者修改产生的Delta，否则我们就会丢失历史上的状态信息。而不可变意味着我们不会覆盖掉已有的信息，如果我们对某个时刻的状态细节感兴趣，只要记录下当时存在的对象指针即可。每一次覆盖都会引入某种含混性。比如说，当我们中途处理失败，需要回退到初始时的一致性状态时，如果已经做了大量覆盖性的修改，则如何才能找回原始的一致性呢？

多个线程共享同一个对象时，如果不具备不可变性，则修改时必然需要加锁，锁的作用是实现排他访问，它将会阻塞多个线程，从中选择一个执行。**如果我们把一个线程看作是一个时间线，则不具备不可变性会导致多个时间线发生耦合，并相互阻塞**。而在（全局共享对象）具备不可变性的情况下，每个时间线可以独立演化，从而实现解耦。当然，这也意味着我们可能会观测到同一个对象的多个历史快照并存（平行宇宙）。

## 三.使用高阶函数代替继承

使用面向对象编程时，一个非常容易出现滥用的情况就是大量使用继承来实现复用。比如说，我们经常使用所谓模板方法模式（Template Method Pattern)，在基类中实现一个模板方法，它调用几个虚拟函数，然后在不同的派生类中提供不同的实现，就可以复用基类中的模板方法逻辑。

```java
// 抽象类
abstract class AbstractProcess {
    // 模板方法，定义了算法骨架
    public final void process() {
        this.primitiveOperation1();
        this.primitiveOperation2();
    }

    // 抽象操作，由子类实现
    protected abstract void primitiveOperation1();
    protected abstract void primitiveOperation2();
}
```

**模板方法的本质是通过一个隐式传递的this指针获取到可定制的函数指针，它的代价是每一次组合都要求产生一个新的子类，而且子类中编写的代码还与模板方法绑定，不能自由的应用于其他场景**。

一般情况下，采用传入回调函数和回调接口的方式会优于模板方法模式。

```java
class ConcreteProcess{
    public void process(IProcessor processor){
       processor.primitiveOperation1();
       processor.primitiveOperation2();
    }
}
```

模板方法相当于是使用固定的this参数来获取函数指针。通过函数参数直接传入回调接口的做法要灵活得多，比如说，拆分出多个回调接口参数，实现交叉组合。

```
```java
class ConcreteProcess{
    public void process(IProcessor1 processor1, IProcessor2 processor2){
       processor1.primitiveOperation1();
       processor2.primitiveOperation2();
    }
}

class OtherProcess{
    public void process(IProcessor1 processor1, IProcessor3 processor3){
       processor1.primitiveOperation1();
       processor3.primitiveOperation2();
    }
}
```

在上面的例子中，IProcessor1和IProcessor2接口中的代码不仅仅可以用于当前的ConcreteProcess类，而是可以用于其他应用场景，比如OtherProcess。在调用ConcreteProcess的时候，传入不同的Processor1和Processor2，就可以很自然的通过交叉组合实现不同的逻辑。而模板方法模式则是固化了Process和Processor1， Processor2的一种组合关系，相当于是将三者耦合在了一起。

## 四. 函数组合的价值源于函数的结合律

函数的组合是指将两个或多个函数合并为一个函数的过程。如果有两个函数 f: A → B 和 g: B → C，则它们的组合 g ∘ f 是一个从 A 到 C 的函数，定义为 (g ∘ f)(x) = g(f(x)) 对于所有 x ∈ A。
例如：

- 设 f(x) = x + 2
- 设 g(x) = 2x
- 则它们的组合 (g ∘ f)(x) = g(f(x)) = g(x + 2) = 2(x + 2) = 2x + 4

函数的组合之所以有用，本质上是因为函数的组合满足结合律。也就是说，如果三个函数 f, g, 和 h 可以组合，则它们的组合满足结合律，即 (h ∘ (g ∘ f)) = ((h ∘ g) ∘ f)。这意味着先将 f 和 g 组合，然后再与 h 组合，与先将 g 和 h 组合，再与 f 组合的结果是一样的。

- (h ∘ (g ∘ f))(x) = ((h ∘ g) ∘ f)(x) 对于所有 x ∈ A
  这里的 "∘" 表示函数的组合。

结合律的存在使得我们可以基于局部信息将多个函数组织在一起。而如果没有结合律，我们就必须要知道函数的具体应用对象和具体应用的上下文，而无法将几个函数独立的抽取出来成为一个可以到处复用的函数。

```javascript
 h(g(f(x)) == p(f(x)) // p = h ∘ g
 h(g(u(x)) == p(u(x)) // h∘g结合的结果p可以被复用到不同的上下文中
```

对于结合律的进一步分析，参见[写给程序员的差量概念辨析,以Git和Docker为例 ](https://mp.weixin.qq.com/s/D5bDNkMJ9gYrFb0uDj2EzQ)。

需要强调的是，**任何满足结合律的东西都必然是自动实现了与上下文的解耦，它们都是上下文无关的**！是否是函数并不关键，关键的是满足结合律。（可逆计算理论中的Delta差量也满足结合律！）

```
 (a*(b*c))*d  == a * BC * d, BC = b*c
 u*((b*c)*v)  == u * BC * v, BC = b*c
```

满足结合律意味着我们可以在整个计算过程中随意添加括号，括号内的部分可以独立于括号外的部分进行局部计算。例如上面的例子中，无论外部的环境是什么，`b*c`都可以结合在一起产生一个可以被复用的BC。

在函数式编程领域，有一个对于初学者而言有些神秘晦涩的概念：Monad（单子）。很多函数式的拥趸认为Monad是函数式编程的精华所在，是高贵的范畴论赋予它神秘的抽象力量。但是从实用主义的角度上说，**Monad作为函数式编程中特有的一种设计模式，它表达的不过是一组形如`a-> m b`这种形式的函数满足结合律而已**。关于Monad的详细介绍，可以参见我在知乎的文章 [写给小白的Monad指北](https://zhuanlan.zhihu.com/p/65449477)。

## 五. 解耦遍历逻辑和计算逻辑

如果仔细观察我们日常编写的代码，会发现它们经常是两种逻辑的耦合：我们一边在查找/遍历需要参与计算的数据，一边在完成具体的计算。

```javascript
 Map<String,Integer> wordCount(File file){
    Map<String, Integer> wordCount = new HashMap<>();

    String text = readText(file);
    // 遍历逻辑和计算逻辑耦合在一个循环中
    String[] words = text.split("\\s+");
    for (String word : words) {
        // 遍历逻辑：遍历每个单词
        // 计算逻辑：计算单词出现次数
        wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
    }
    return wordCount;
}
```

在上面的例子中，遍历逻辑（解析文件并循环word）和计算逻辑（更新`HashMap`中的计数）被紧密地耦合在一起。

函数式编程借助于高阶函数，可以有效地将遍历数据的逻辑与对数据进行处理的逻辑分离开来。

```javascript
void forEachWord(File file, Consumer<String> consumer){
    String text = readText(file);
    Arrays.stream(text.split("\\s+")).forEach(consumer);
}

Map<String, Integer> wordCount = new HashMap<>();
Consumer<String> consumer = 
    word -> wordCount.merge(word, 1, Integer::sum);

forEachWord(file, consumer);
```

我们可以编写一个forEachWord函数，它负责实现遍历文件中每个word的功能，通过传入的consumer函数来提供具体的计算功能。分离遍历逻辑和计算逻辑之后，同样的forEachWord函数可以传入不同的consumer来实现不同的功能，比如说，我们可以统计所有的大写字母开头的word的个数。这种需求的变动不需要修改forEachWord的实现。

```javascript
Map<String, Integer> wordCount = new HashMap<>();
Consumer<String> consumer = word -> {
    if(Character.isUpperCase(word.charAt(0))) {
      wordCount.merge(word, 1, Integer::sum);
    }
};

forEachWord(file, consumer);
```

类似的，同样的consumer也可以用于不同的遍历函数。比如要求统计指定目录下所有文件中的word计数，此时不需要修改consumer，只需要提供一个新的forEachWordInDir函数即可。

### Y组合子：递归结构和计算结构的解耦

在函数式编程中，一个更为微妙的解耦方式是递归遍历与计算结构的解耦。在一般的程序语言中，实现递归非常简单，但是它要求函数必须有一个名字，我们在函数内部通过函数的名字来递归的引用自己。

```javascript
function fact(n){
    return n < 2 ? 1 : n * fact(n-1)
}
```

有一个神奇的Y组合子（Y Combinator）函数，它可以吃进去一个普通的、不包含递归引用的函数，然后吐出一个实现了自递归调用的函数。

```javascript
const fact0 = f => n => n < 2 ? 1 : n * f(n-1)
const fact = Y(fact0)
```

**fact0是一个普通的匿名函数，它的实现体中并没有用到函数名fact0**。这个所谓的Y组合子长下面这个样子，看上去有点吓人

```
function Y(f) {
    const g = x => {
       return f(y => x(x)(y));
    };
    return g(g);
}

//可以验证
fact(n) == Y(fact0)(n)
```

Y组合子看上去似乎非常不直观，但是我提出了一个启发式的推导，可以让Y组合子的构造过程变得简单直观，并可以推广得到更多得组合子。具体参见[Y组合子的一个启发式推导 ](https://mp.weixin.qq.com/s/ARsrYJpApqB2_72tl-MSQQ)。

## 六. 惰性求值减少不必要的因果耦合

什么是惰性求值？以下是智谱清言AI的回答：

所谓惰性求值（Lazy Evaluation），就是一种编程语言中的求值策略，它允许表达式的计算被延迟到真正需要其结果的时候。这种策略使得程序在执行时可以更加高效地利用资源，尤其是当涉及到大型数据结构或潜在无限的数据集时。通过惰性求值，我们可以避免不必要的计算，减少内存占用，并且能够以声明式的方式编写代码，让程序逻辑更加清晰。惰性求值的实现通常涉及到将表达式封装成某种 thunk 或 Promise 对象，这些对象包含了足够的信息，以便在需要时能够完成计算。这种按需计算的特性，是函数式编程语言中的一个重要工具，它使得开发者能够以更加抽象和简洁的方式来表达复杂的算法和数据流。

=============智谱清言AI创作完毕=================

很多受到函数式深刻影响的语言会提供支持惰性求值的语法特性，比如Kotlin语言中的lazy语法。

```kotlin
// 示例代码由智谱清言AI生成
fun main() {
    // 使用 lazy 关键字创建一个惰性属性
    val lazyValue: String by lazy {
        println("Computing the value...")
        "Hello, Lazy World!"
    }

    // 第一次访问惰性属性时，会计算其值
    println(lazyValue)

    // 后续访问惰性属性时，不会重新计算，而是直接返回之前计算的结果
    println(lazyValue)
}
```

Scala语言中内置的Stream也是惰性求值的

```scala
// 示例代码由文心一言AI生成
object Fibonacci {  
  // 使用Stream定义一个惰性求值的斐波那契数列  
  def fibonacci: Stream[BigInt] = {  
    def loop(a: BigInt, b: BigInt): Stream[BigInt] = a #:: loop(b, a + b)  
    loop(BigInt(0), BigInt(1))  
  }  

  def main(args: Array[String]): Unit = {  
    // 打印斐波那契数列的前10个数  
    fibonacci.take(10).foreach(println)  

    // 注意：这里不会造成无限循环或内存溢出，因为Stream是惰性的  
    // 并且我们通过take操作限制了需要计算的元素数量  
  }  
}
```

在这个例子中，`fibonacci`方法使用了一个内部定义的`loop`函数来递归地生成斐波那契数列。`#::`操作符用于将当前元素（`a`）添加到由`loop(b, a + b)`生成的流的前面，从而形成一个新的流。由于`Stream`是惰性的，所以这个递归调用只有在需要下一个斐波那契数时才会进行。

在`main`方法中，我们通过调用`fibonacci.take(10)`来获取斐波那契数列的前10个元素，并通过`foreach`来遍历并打印这些元素。由于`Stream`的惰性特性，只有当我们实际需要这些元素时，它们才会被计算出来。

在Java语言中，并没有直接内置支持惰性求值的语法，但是我们可以用函数来模拟实现。

```java
public class LazyValue<T> {
    private Supplier<T> supplier;
    private T value;
    private boolean computed = false;

    public LazyValue(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public T get() {
        if (!computed) {
            value = supplier.get();
            computed = true;
        }
        return value;
    }
}

public class LazyEvaluationExample {
    public static void main(String[] args) {
        // 创建一个惰性求值的对象，它在第一次调用 get() 时计算其值
        LazyValue<Integer> lazyValue = new LazyValue<>(() -> {
            System.out.println("Computing value...");
            return 42; // 示例计算：返回一个固定的值
        });

        // 第一次调用 get() 将执行计算
        System.out.println("Value: " + lazyValue.get());
        // 后续调用 get() 将复用之前的计算结果
        System.out.println("Value: " + lazyValue.get());
    }
}

// 输出：
// Computing value...
// Value: 42
// Value: 42
```

**惰性求值的本质就是将值替换为一个求值的函数，并不立刻求值，只有当实际用到这个值的时候才执行对应的计算过程**，并且一般会缓存计算结果，下次再获取时不会再重复计算。

Eager Evaluation的问题在于，当我们通过一个变量名来引用某个值的时候，要求这个值的计算已经完成，这导致了大量不必要的因果依赖关系。

```javascript
function f(a,b){
    const x = h(b);
    return g(a, x);
}
```

在上面的示例中，调用函数f之前，参数a的值就必须要完成计算，我们传入f的只能是一个计算后的结果。虽然函数f中并没有实际用到参数a的值（只是转手传给了别的函数），但是f的计算过程却被迫和a的计算过程产生了依赖关系：f的计算不能和a的计算并行执行，而只能是在a的计算完成之后执行。**只有存在因果依赖的代码才需要按照先后顺序执行：因在前，果在后**。但是目前我们所使用的主流程序语言都是命令式语言，代码中的函数调用（表达）顺序一般就预示了代码执行时的实际顺序。

在实际编程中，**很多框架和设计模式的作用，就是帮助我们分离逻辑的表达顺序和实际执行顺序**。我们可以按照一个容易理解的顺序进行表达，甚至是乱序表达，**关键点在于先表达的并不一定要先执行，而是实际发生因果依赖的时候才确定执行顺序**。一个具体的应用实例是DDD中的聚合根对象（Aggregate Root)。

```java
IAccountBo accountBo = accountManager.getAccount(accountId);
ICustomerBo customerBo = accountBo.getCustomerBo();
```

在DDD的开发中，一般性的做法都是进入后台服务函数时，先根据业务参数获取到一个聚合根对象，比如账户对象accountBo，这个聚合根对象提供了大量的业务数据获取方法，比如获取账户关联的客户对象信息可以使用`accountBo.getCustomerBo()`函数。可以说，聚合根对象包含了所有账户相关的业务数据信息，**获取到聚合根相当于是持有了所有这些信息，在后续的业务代码中，我们就再也不需要访问数据库了，仅仅使用聚合根对象就足够了，因为所有的信息都可以通过聚合根对象的属性访问到**！  

getCustomerBo的典型实现方法是使用延迟加载，

```javascript
ICustomerBo getCustomerBo(){
    if(customerBo == null)
       customerBo = accountManagerImpl.getCustomerBo(
           account.getCustomerId(), dataCache);
    return customerBo;
}
```

`accountBo.getCustomerBo()`对应的JavaBean属性表达式是 `accountBo.customerBo` 。显然，在属性表达式层面，延迟加载就是一种惰性求值。

聚合根对象是对于业务领域中所谓领域模型（也就是数据实体关系）的一种表达，但是这种表达与具体获取数据的执行过程并没有直接关系。实际用到聚合根中某个属性的时候才应该去执行加载过程。

因为目前国内大部分公司使用的是MyBatis这种功能薄弱的ORM引擎，导致在实施DDD的时候存在潜在的困难。一般人所编写的获取聚合根的代码总是会获取大量多余的数据！

```
AccountDTO accountDTO = accountManager.getAccount(accountId);
CustomerDTO customerDTO = accountDTO.getCustomer();
```

很多人编写的代码中我们获取到的数据对象都是完全没有任何动态特性的POJO对象，或者说DTO对象。**根据accountId获取到AccountDTO时，如果使用了聚合根的概念，一般人会将关联的customer信息直接加载，设置到accountDTO的属性上**。即使外部调用者没有实际用到customer的信息，也避免不了这一加载过程。这种做法本质上是在用面向对象语言编写传统的命令式脚本，没有充分发挥面向对象的封装能力。如果是使用这种方式去实施DDD，那么为了避免出现重复获取数据导致的性能问题，我们就必须要限制函数的表达顺序，而且可能还需要提供一些额外的上下文环境来保存临时缓存数据，这些都会引入不必要的复杂性。例如虽然在逻辑上步骤A和步骤B之间没有任何依赖关系，它们的执行顺序可以是任意的，但是为了复用已经加载过的数据，我们可能会强制要求步骤A在步骤B之前执行，否则步骤B就缺少某个参数或者上下文中的某个数据。

关于声明式和命令式的进一步介绍，参见[什么是声明式编程](https://zhuanlan.zhihu.com/p/85491177)。
