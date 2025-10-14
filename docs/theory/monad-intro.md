# **写给小白的 Monad 指北 **

最近公司来了个新同事，他姓白，年纪又很小，我们都叫他小白。小白最近在学习函数式编程，前几天他过来问我一个问题。
小白：我正规985学校毕业，为什么看了这么多Monad介绍，还是云里雾里的。是这些文章写得的有问题，还是我的理解力有问题？
我：你学什么专业的？
小白：高分子。毕业后我在家自学了半年编程。
我：好吧...
最后我决定写这篇文章，帮小白搞清楚这个问题。

什么是Monad？从实用主义的角度上说，**Monad就是函数式编程中特有的一种设计模式**。为什么是函数式特有的？因为它主要解决的就是**函数之间如何组合**的问题。

## **一、函数**

为了研究函数之间如何组合，我们首先需要定义什么是函数。在编程语言中，函数可以被理解为从类型A到类型B的映射，形式上可以写成 `f: A -> B`。一个函数 `f` 接收一个类型为 `A` 的输入，返回一个类型为 `B` 的输出。

特别的，函数本身也可以作为输入或输出，这就是所谓的高阶函数。有了高阶函数的概念，我们就可以定义一个变换 `curry`，它负责将多参数函数化归为单参数函数。这样在理论上我们就只用研究单参数函数了。

```javascript
function add(x, y) {
    return x + y;
}

// curry化后，变成接收一个参数，并返回一个新函数
function curry_add(x) {
    return function(y) {
        return x + y;
    }
}

add(3, 4) === curry_add(3)(4); // 结果都为 7

// 在数学上，这可以看作是类型的变换：
// (A, B) -> C  等价于  A -> (B -> C)
```
redux中间件那个看起来有点吓人的形式 `store => next => action => { ... }` 本质上就是一个柯里化后的多参数函数。

函数最重要的操作是**组合（composition）**。如果有一个函数 `g: A -> B` 和另一个函数 `f: B -> C`，我们可以将它们组合成一个新函数 `h: A -> C`。

```javascript
// 为了更符合代码从左到右的执行顺序，我们约定 compose(g, f) 代表数据先流经 g，再流经 f
function compose(g, f) {
    return function(x) {
        // 先执行 g(x)，然后将其结果作为输入传给 f
        return f(g(x));
    }
}
// 值得注意的是，这个约定与一些函数式编程库（如 Ramda）的 `compose` 定义顺序相反。
// 我们这里定义的从左到右的组合方式，在社区中通常被称为 `pipe` 函数，因为它更像数据流过一根管道。
```

函数组合最重要的性质是满足**结合律**。对于一个 `h -> g -> f` 的执行顺序，无论我们是先组合 `h` 和 `g`，还是先组合 `g` 和 `f`，最终结果都一样。

```javascript
// 结合律： (h -> g) -> f  等价于  h -> (g -> f)
compose(compose(h, g), f)  // 在效果上等价于
compose(h, compose(g, f))
```

满足结合律意味着我们可以随意组合，因为计算结果与局部结合顺序无关。这使得我们可以像管道一样把一长串函数链接起来，例如 `f(g(h(x)))` 可以被看作是 `compose(h, g, f)(x)`（假设 `compose` 支持多参数）。

Monad本质上说的就是函数满足结合律这件事情，但它不是简单地说普通函数满足结合律（这显而易见），而是说**某种特殊形式的函数**，也能通过一种新的组合方式，同样满足结合律。

## **二、一个问题：Promise 的组合**

假设我们有如下两个异步函数，我们希望把它们组合成一个新的异步函数：

```javascript
// String -> Promise<User>
async function getUserById(userId) { /* ... */ }

// User -> Promise<Dept>
async function getDeptByUser(user) { /* ... */ }
```
我们的目标是创建一个函数 `getDeptByUserId`，它的类型应该是 `String -> Promise<Dept>`。如果我们尝试使用上面定义的普通 `compose`：

```javascript
// 错误的尝试
const getDeptByUserId_wrong = compose(getUserById, getDeptByUser);
```
这为什么是错误的？让我们看看执行过程：
1. `getUserById(userId)` 返回一个 `Promise<User>`。
2. 普通的`compose`会把这个 `Promise<User>` 对象直接传给 `getDeptByUser`。
3. 但 `getDeptByUser` 期望的输入是 `User` 类型，而不是 `Promise<User>`！

问题出在哪里？这两个函数的返回值被一个“容器”——`Promise`——包裹了。我们需要一种新的组合方式来处理这种带“容器”的函数。

```javascript
// g 是第一个函数，f 是第二个函数
function composeM(g, f) {
   return function(x) {
       // 1. 先执行 g(x)，得到一个 Promise
       // 2. 使用 .then 从 Promise 中取出值，再传给 f
       return g(x).then(f);
    }
}

// 正确的组合：先 getUserById，再 getDeptByUser
const getDeptByUserId = composeM(getUserById, getDeptByUser);
```
这个特殊的 `composeM` 完美地解决了问题。更重要的是，可以证明，这种新的组合方式**同样满足结合律**！这就是通往 Monad 的大门。

## **三、另一个例子：List 的组合**

让我们看看另一种常见的“容器”——数组（List）。考虑以下两个函数：

```javascript
// number -> number[] (List<number>)
function positive(x) {
    return x > 0 ? [x] : [];
}

// number -> number[] (List<number>)
function duplicate(x) {
    return [x, x];
}
```
同样，我们无法用普通 `compose` 来组合它们。`Array.prototype.flatMap` 恰好是我们的工具。

```javascript
// g 是第一个函数，f 是第二个函数
function composeM_List(g, f) {
    return function(x) {
        return g(x).flatMap(f);
    }
}

// 组合：先 positive，再 duplicate
const p = composeM_List(positive, duplicate);
console.log([1, -1, 2].flatMap(p)); // 输出 [1, 1, 2, 2]
```
我们再次发现，对于返回数组的函数，我们也能定义一个满足结合律的 `composeM`。

## **四、Monad**

现在，数学家要开始表演了。数学是一门只关注“形式”的科学，形式上一样的东西在数学上可以认为是完全等价的，这就是“抽象”的威力。

`Promise` 的例子中，函数类型是 `A -> Promise<B>`。
`List` 的例子中，函数类型是 `A -> List<B>`。

它们的共同模式是：`A -> M<B>`。

`M` 就是一个符号，一个上下文的抽象。
*   当 `M` 是 `Promise` 时，它表示异步计算。
*   当 `M` 是 `List` 时，它表示可能产生零个或多个结果的计算。
*   当 `M` 是 `Identity` (即 `Identity<T> = T`) 时，`A -> Identity<B>` 就退化成了 `A -> B`，我们回到了普通的函数。

**Monad 的核心，就是为 `A -> M<B>` 这种类型的函数（在范畴论中称为 Kleisli 箭头）定义一个满足结合律的组合操作 `composeM` 和一个单位元 `unit`。**

一个满足结合律的系统，在数学上称为**半群（Semigroup）**。如果这个系统还有一个**单位元（Identity Element）**，它就升级成了**幺半群（Monoid）**。

`unit` 是一个特殊的函数，它把一个普通值 `a` 放入 Monad 容器中，其类型是 `A -> M<A>`。

```javascript
// 对于 Promise 而言
function unit_Promise(a) {
    return Promise.resolve(a);
}

// 对于 List 而言
function unit_List(a) {
    return [a];
}
```
所以，**Monad 就是“自函子范畴上的一个幺半群”**² 这句天书的“人话”版本就是：它是一个关于 `A -> M<B>` 类型函数的、带单位元的、满足结合律的组合系统。

**从 `composeM` 到 `bind` (`flatMap`)**

如果我们换一个“面向对象”的视角，不从组合函数出发，而是从一个已有的 monadic 值 `ma` (`M<A>`类型的对象) 出发，我们可以定义一个更常见的方法，通常叫做 `bind` 或 `flatMap`。

`bind: (ma: M<A>, f: A -> M<B>) -> M<B>`

对于 `Promise`，它就是 `then`；对于 `List`，它就是 `flatMap`。

`bind` 和 `composeM` 是等价的：`composeM(g, f)` 等价于 `x => g(x).bind(f)`。

**Monad 定律 (Monad Laws)**

`composeM` 的结合律和单位元性质，可以等价地用 `bind` 和 `unit` 来表述，这就是著名的 **Monad 三定律**。为了表述清晰，我们用“效果等价”来描述定律，因为在实际编程中，这通常意味着产生相同的值或最终状态，而非严格的对象引用相等（`===`）。

1.  **左单位元 (Left Identity)**: `unit(x).bind(f)` **效果等价于** `f(x)`
    把一个值装进容器，然后 bind 一个函数，等价于直接把该值应用到函数上。
*   `Promise.resolve(x).then(f)` 效果等价于 `f(x)` ¹
*   `[x].flatMap(f)` 效果等价于 `f(x)`

2.  **右单位元 (Right Identity)**: `ma.bind(unit)` **效果等价于** `ma`
    用 `unit` 函数去 bind 一个容器，等于什么也没做。
*   `promise.then(Promise.resolve)` 效果等价于 `promise`
*   `list.flatMap(x => [x])` 效果等价于 `list`

3.  **结合律 (Associativity)**: `ma.bind(f).bind(g)` **效果等价于** `ma.bind(x => f(x).bind(g))`
    一连串的 bind 操作，可以任意组合。
*   `promise.then(f).then(g)` 效果等价于 `promise.then(x => f(x).then(g))`
*   `list.flatMap(f).flatMap(g)` 效果等价于 `list.flatMap(x => f(x).flatMap(g))`


> 严格来说，`.then(f)` 会将 `f` 的执行推迟到下一个微任务（microtask），而直接调用 `f(x)` 可能是同步的。但从数据流和最终结果的角度看，它们是等价的。

>  **给好奇宝宝的“天书”注解**：**自函子 (Endofunctor)** 就是那个类型构造器 `M`，它将范畴内的类型映射回同一个范畴（例如 `T -> M<T>`); **范畴 (Category)** 在此可简单理解为你所用编程语言的类型系统；而 **幺半群 (Monoid)** 正是我们讨论的那个拥有单位元（`unit`）和满足结合律的二元操作（`composeM`）的组合系统。

## **五、作为设计模式的 Monad**

Monad 是一种对函数计算过程的通用抽象机制，关键是统一形式，统一操作模式，复用代码。因为这种模式很常见，一些语言会提供语法糖来方便编写，例如 `async/await` 就是 `Promise` Monad 的语法糖，而 Scala/Haskell 等语言中的 `for-comprehension` / `do` 语法，则可以用于任何 Monad。

```scala
// for-comprehension 语法
for {
  x <- mx
  y <- my
} yield x + y

// 会被编译器脱糖为一系列嵌套的 flatMap/map 调用
mx.flatMap { x =>
  my.map { y =>
    x + y
  }
}
```
这个嵌套结构也解释了为什么我们自己手写代码时，更喜欢可读性更好的链式调用：`mx.flatMap(f).flatMap(g)`。

## **六、Monad 到底干了什么？**

Monad 相比于普通的函数组合，关键是引入了一个包装结构 `M`，相当于把 `value` 包装在一个 `context` 中（monadic value = a value in a context）。

这使得我们可以在 `bind` 的实现中，将一部分通用逻辑（如异步等待、空值检查、状态传递、日志记录等）隐藏到这个 `context` 的处理中，从而让我们的主逻辑代码可以像简单的函数链一样清晰地表达出来。这有点类似于面向切面编程（AOP）的作用。

## **七、State Monad：封装副作用的艺术**

Monad 对于函数式语言还有一个特别的意义：它提供了一种环境封装机制，可以把副作用隔离到某个环境对象中，保证核心函数的“纯洁”。**State Monad** 是最好的例子。

假设，我们需要在程序中使用随机数：
`function addRandom(a) { return a + Random.nextInt(); }`
这个函数依赖全局变量 `Random`，是有副作用的。为了变纯，我们必须把状态作为参数传递：
`function addRandom(a, random) { return [a + random.nextInt(), random]; }`
返回值是一个元组 `[新值, 新状态]`。这是一个通用模式。所有需要状态的纯函数，其类型签名都可以写成 `(Value, State) -> (NewValue, NewState)`。

利用柯里化，我们可以把它变成 `Value -> (State -> (NewValue, NewState))`。
这完美匹配了 Monad 的形式 `A -> M<B>`！
*   `A` 就是 `Value`
*   `M<B>` 就是 `State -> (NewValue, NewState)`
*   `M` 就是 `State -> (..., NewState)` 这个结构，我们称之为 `State s`
*   `B` 就是 `NewValue`

所以，我们的函数类型是 `A -> State s B`，其中 `State s B` 是 `s -> (B, s)` 的别名。

> `State s` 就对应于符号M。再次强调，数学是一种彻底的形式主义，只要能按照确定的规则替换为指定形式，那么它们在数学上本质上就是一回事。

一旦形式匹配，我们就可以定义 `unit` 和 `bind`：
```javascript
// unit :: a -> State s a
function unit(a) {
    // unit 的作用是把值 a 包装进 State Monad
    // 它返回一个函数，该函数接收一个状态 s，然后原封不动地返回 a 和 s
    return function(s) {
        return [a, s];
    }
}

// bind :: State s a -> (a -> State s b) -> State s b
function bind(ma, f) {
    // bind 的作用是组合两个有状态的计算。
    // 它也返回一个函数，这个函数代表了整个组合后的计算。
    return function(s) {
        // 1. 执行第一个计算 ma，传入初始状态 s，得到新值 a 和新状态 s1
        const [a, s1] = ma(s);
        // 2. 将新值 a 传给函数 f，得到下一个有状态计算 f(a)
        const mb = f(a);
        // 3. 执行下一个计算 mb，并传入新状态 s1，得到最终值 b 和最终状态 s2
        const [b, s2] = mb(s1);
        // 4. 返回最终结果和最终状态
        return [b, s2];
    }
}

// 我们可以用一个简单的流程图来形象地理解这个状态传递过程：
//
//  初始状态(s) ---> [ 执行 ma ] --产生-->  值(a) 和 新状态(s1)
//                     |
//                     | (值 a 被用于生成下一个计算)
//                     v
//  (s1 被传入) ---> [ 执行 f(a) ] --产生--> 最终值(b) 和 最终状态(s2)
//
// 最终返回：[b, s2]
```
`bind` 的实现巧妙地把“传递状态”这个繁琐的过程封装了起来。

**State Monad 为什么可以封装副作用？因为它实现了延迟计算。`bind` 仅仅负责把一堆函数组装成一个更大的函数，它本身并不会立即执行。只有当你最后给这个大函数提供一个初始状态时，整个计算链才会真正启动。**

## **八、其他的 Monad**

Monad 模式在编程中无处不在：

*   **Option/Maybe Monad**: 用于处理可能为空的值。Kotlin/Swift 中的 `?.` 链式调用就是它的体现。如果遇到 `null`，整个链条会“短路”并返回 `null`，避免了层层嵌套的 `if (a != null)`。
*   **Either/Result Monad**: 用于处理可能失败的操作，它能同时携带成功的值或失败的错误信息。
*   **IO Monad**: 用于封装与外部世界交互的副作用（如读写文件、打印到控制台），将不纯的操作包裹起来，使得程序的其他部分保持纯净。

希望这篇文章能帮你驱散 Monad 的迷雾。它不是什么魔法，而是一种强大、通用、用来组织和抽象代码的优雅模式。掌握了它，你就掌握了函数式编程的一个核心思想。
