# Functional Programming Thinking from a Pragmatist Perspective

Explainer video: [https://www.bilibili.com/video/BV1hrADeZE6v/](https://www.bilibili.com/video/BV1hrADeZE6v/)

Although functional programming has entered the mainstream of software development, many people still understand it only as collection-processing functions such as map/flatMap with extensive use of lambda expressions, as if using lambdas equals functional programming.
Some others emphasize functional programming languages like Haskell and Scala that support sophisticated type systems, implying that it’s hard to truly implement so-called functional programming in common imperative languages like Java and reap its benefits.
But these views are partial. The scope of functional programming is rich; it’s not merely about using higher-order functions.
Functional programming is a general programming paradigm rather than a single language feature. You can fully apply functional programming ideas in Java.
This article mainly shares practical experience on applying functional programming ideas in conventional imperative languages from a pragmatist standpoint. The content draws on the following references:

* [Why Functional Programming Aids Decoupling (Decouple)](https://mp.weixin.qq.com/s/dWbnBmBLM9LW7hlw8x2jDw)
* [Monad Guide for Beginners](https://zhuanlan.zhihu.com/p/65449477)
* [A Heuristic Derivation of the Y Combinator](https://mp.weixin.qq.com/s/ARsrYJpApqB2_72tl-MSQQ)
* [How to Break Framework Shackles and Achieve True Framework Agnosticism](https://mp.weixin.qq.com/s/v2_x4gre4uMfz3yYNPe9qA)
* [What Is Declarative Programming](https://zhuanlan.zhihu.com/p/85491177)

## I. Core Ideas of Functional Programming

If you ask DeepSeek AI what the core ideas of functional programming are, it will respond as follows:

The core ideas of functional programming can be summarized as:

1. **Pure functions**
   No side effects (no mutation of external state); output is determined solely by input; the same input always yields the same result. This makes code predictable and easy to test.

2. **Immutable data**
   Data cannot be modified once created; any change produces new data. This avoids concurrency issues from shared state; typical implementations include persistent data structures.

3. **Functions as first-class citizens**
   Functions can be passed, composed, and returned like ordinary values, supporting higher-order functions (e.g., `map`/`filter`) and closures.

4. **Declarative programming**
   Focus on “what to do” rather than “how to do it,” describing logic via expressions (e.g., `list.map(f)`) instead of imperative loops and control flow.

5. **Recursion and lazy evaluation**
   Use recursion instead of iterative loops, relying on tail-call optimization; laziness (e.g., streams) defers computation to improve efficiency.

6. **Function composition and currying**
   Build complex logic by composing small functions (`f(g(x))`); currying (partial application) enhances reuse.

**Key goal**: Elevate modularity, maintainability, and concurrency safety through the rigor of mathematical functions. Typical languages include Haskell and Erlang; modern languages (e.g., JavaScript/Python) have adopted these features as well.

=======DeepSeek AI creation completed=======

## II. The Essence of Functional Programming

If you further ask about the essential reasons behind functional programming, DeepSeek AI responds as below:

The essential reasons for functional programming (FP) should be analyzed deeply from three levels: **mathematical logic, the philosophy of computer science**, and **practical engineering problems**:

### A. Mapping in Mathematical Logic: From λ-Calculus to Determinism

The roots of FP lie in mathematical **λ-calculus (Lambda Calculus)**, whose core ideas are:

1. **Treat computation as mappings of mathematical functions**
   - A function is a pure mapping from inputs to outputs, not a “process” with side effects (such as variable mutation in imperative programming).
   - For example: `f(x) = x + 1` is a mathematical function, while `void increment(int& x)` is an imperative process.
2. **Eliminate interference from “time” and “state”**
   - The result of a mathematical function depends only on its parameters, not the external environment or execution order (e.g., global variables, IO).
   - **Determinism (Referential Transparency)**: The same expression evaluates to the same result in any time or context.

**Essential purpose**: Use mathematical determinism to counter software engineering **uncertainty** (e.g., race conditions, unpredictable side effects).

### B. Philosophy of Computer Science: From “Machine Model” to “Mathematical Model”

Traditional imperative programming (e.g., C, Java) is based on the **von Neumann architecture**, viewing a program as “instruction sequences + memory state,” with a focus on **how to change state**. The philosophy of FP is:

1. **View programs as constructions of mathematical propositions**
   - Programs are **expressions** rather than statements, deriving results by composing functions rather than stepwise state mutation.
   - For example: `map(f, filter(g, list))` is declarative logic, not loops with counters and temporaries.
2. **Abstract machine complexity**
   - Through immutable data and higher-order functions, FP hides low-level details of memory management and execution order, focusing on logic itself.

**Essential contradiction**:
One root cause of software complexity is **state management** (e.g., multithreaded locks, global-variable pollution). FP simplifies problems to static “input → output” relations by **eliminating mutable state**, reducing cognitive load.

### C. Real-World Engineering Pressures

The resurgence of FP (e.g., Scala, React Hooks) is directly related to **modern software complexity**:

1. **Concurrency and distributed systems**
   - Multi-core CPUs and distributed computing call for shared-nothing designs. Pure functions and immutable data naturally avoid lock contention and data pollution.
   - Example: Erlang achieves telecom-grade reliability with the Actor model and immutable message passing.
2. **Maintainability**
   - Pure and declarative code is easier to test, reason about, and refactor (due to the lack of implicit dependencies).
   - Example: In React, UI = f(state); immutable state updates ensure view consistency.
3. **Composability in complex systems**
   - Function composition (e.g., `compose(f, g)`) and type systems (e.g., Haskell’s Monad) enable modular construction of complex logic.

**Essential driving force**:
FP leverages mathematical **rigor** and **composability** to counter the entropy increase (growing disorder) as software scales.

### D. Deeper Philosophy: An Alternative Worldview of Computation

FP represents an **alternative definition of computation**:

- **Imperative programming**: Computation is the stepwise transformation of state (Turing machine model).
- **Functional programming**: Computation is the reduction of expressions (λ-calculus model).

The two models are theoretically equivalent (Church–Turing Thesis), but choose different abstraction levels in practice:

- **Turing machine model**: Closer to physical machine implementation; requires manual state management.
- **λ-calculus model**: Closer to human logical reasoning; relies on mathematical rules to ensure correctness.

### Summary: A Pursuit of “Determinism” and “Composability”

FP reshapes the software paradigm through:

1. **Eliminating side effects with mathematical functions** → enhances determinism.
2. **Eliminating temporal coupling with immutable data** → simplifies concurrency.
3. **Building abstractions with higher-order functions and type systems** → boosts composability.

Its ultimate goal is to: **elevate software engineering from “mechanical operations” to “mathematical proof”**, trading formal logical constraints for system reliability and maintainability.

========== DeepSeek AI creation completed =======

DeepSeek’s understanding is indeed quite deep; in fact, I’ve observed that many so-called architects do not reach this depth with these concepts.

Simply put, we can understand functional programming from the following angles.

### 2.1. The universality of the function concept

Functional programming is a distinctive perspective for observing the world and building abstractions. Object-oriented programming claims that **everything is an object**; similarly, functional programming claims that **everything is a function**.
From the functional perspective, **a value a can be regarded as equivalent to the lambda expression `()=>a`**.

> Some languages like Scala support so-called lazy variables. They are used like values but are essentially lazily-evaluated functions; the first access triggers function execution.

In the functional world, the result of a computation is just another name for the computation process. If needed, we can recompute at any time, and each computation yields the same result.

Relying solely on the concept of functions, we can build the software world and realize all feasible computations. This uniformity at the foundational level carries enormous epistemic value. However, note that uniformity may be insufficient in practical applications. For example, all matter in the physical world is composed of a small number of atomic species, yet actual material properties are highly diverse.

#### Values are efficient representations after information compression

In Chinese and Arabic numerals, 1 through 9 are represented by single characters; Roman numerals, which originated from ancient Rome and spread widely in the West, differ. Roman numeral I is 1, V is 5, IV represents 4 (5 − 1), and VI represents 6 (5 + 1).
A similar phenomenon exists in French, e.g., 80 = quatre-vingts (4×20) and 90 = quatre-vingt-dix (4×20+10).

Clearly, we could adopt the following notation: 1+1 for 2, 1+1+1 for 3, and so on. There is no logical issue with this notation; it’s just wasteful on paper in practice.

In the lambda calculus (the theoretical foundation of functional programming), the idea that everything is a function is pushed to the extreme: we can totally discard the concept of integers as values and represent integers with functions—at the cost of very verbose representations.
For example, the integer 3 can be represented by the lambda expression `λf.λx.f (f (f x))`, which essentially expresses 3 as “apply f to x three times.” Clearly, the familiar integer 3 is an efficient representation after information compression. Mathematically, it has infinitely many possible representations, all of which implicitly encode some computation process for 3.

In this sense, “everything is a function” is clearly uneconomical at the computation level; we must vigorously develop efficient representations for intermediate results.

### 2.2. Functions have favorable mathematical properties

In mathematics, functions possess the universal determinism characteristic of mathematical truths and satisfy associativity, allowing divide-and-conquer reasoning with limited local knowledge. Fully leveraging the mathematical properties of functions yields scientific benefits.

> Associativity means a + (b + c) = (a + b) + c. In a set of computations, we can freely add parentheses to group computations without affecting the final result. Grouped computations can be carried out independently of the outside, implying that local parts have intrinsic value and can be understood independently.

Interestingly, mathematically-defined functions need not be represented by functions in a specific programming language. For example, when modeling a process in Java, each step of the process can correspond to a mathematical function; however, in Java, we often model that function as a step object and use StepModel to represent the function alongside related metadata.

Conversely, are functions in functional languages necessarily the best carriers of the mathematical concept of functions? Not necessarily. Mathematics derives its power from analyzability and logical derivation, whereas functions in a functional language are black boxes at the application level—the compiler can analyze them, but typical application code cannot.
In big data processing models, functionality appears to be built by composing functions, but the core is still constructing an analyzable DAG; the framework needs to analyze this graph, reorganize logic, and perform optimizations. Relying only on low-level information exposed at compile time may be insufficient for automated reasoning.

### 2.3. Functions have no concept of time

The world of mathematical objects is timeless and free from causal constraints—an entirely free and eternal world. In this world, you always have the right to “regret”; nothing is irreversible.
Note that it’s not just mathematical functions that have this property—this property holds for all mathematical objects (for example, the Delta concept with an inverse element allows us to keep stacking changes and eventually return to the origin).

> Deriving proposition B from proposition A does not mean A is more fundamental than B; you can also derive A in reverse from B.

In functional programming, you can treat all functions as executing in parallel; regardless of the order, the final result is the same. Since no values are mutated, there are no races. Thus, FP has unique advantages in distributed and parallel programming.

In imperative programming, we frequently modify state; distinguishing before-change and after-change inevitably introduces time (at time t the value is X; at time t+1 after assignment the value becomes Y—how do you define change without time? Or rather, precisely because we can recognize change, we discover and define time).
Therefore, imperative computation always unfolds along a timeline, and when multiple timelines intersect (through shared mutable state), unnecessary complexity is often introduced (complexity not inherent to the business itself but to the computation process).

In the pure-functions + immutable-data paradigm of FP, akin to the many-worlds interpretation in quantum mechanics, each computation step spawns a new universe. All these universes can proceed in parallel, with the caveat that they may consume substantial resources. Thus, given resource constraints, the benefits promised by mathematical abstraction may not always be realized in practice.

### III. Functional Programming in Object-Oriented Languages

For detailed discussion, see [Why Functional Programming Aids Decoupling (Decouple)](https://mp.weixin.qq.com/s/dWbnBmBLM9LW7hlw8x2jDw)

1. Functions should have parameters and return values
2. Minimize mutations to shared data
3. Use higher-order functions instead of inheritance
4. The value of function composition stems from associativity
5. Decouple traversal logic from computation logic
6. Lazy evaluation reduces unnecessary causal coupling

### IV. Minimal Information Expression for Framework Neutrality

For detailed discussion, see [How to Break Framework Shackles and Achieve True Framework Agnosticism](https://mp.weixin.qq.com/s/v2_x4gre4uMfz3yYNPe9qA)

> framework agnosticism allows create technology solutions that are independent of any **predefined** frameworks or platforms.

First, recognize that framework neutrality is an outcome, not necessarily a goal we explicitly pursue. Our primary goal should be: **how to achieve minimal information expression**.

**Minimal expression is necessarily domain-specific**. Essentially, business logic is technology-neutral; it can be expressed and implemented independently of any framework. Therefore, if we strip away all extraneous information, what remains must be concepts internal to the business domain. For example:

```
void activateCard(HttpServletRequest req){
    String cardNo = req.getParameter("cardNo");
    ...
}

void activateCard(CardActivateRequest req){
    ...
}
```

Comparing the two functions above, the first is not minimally expressed because it introduces extra HTTP context, whereas the second uses only the domain-specific CardActivateRequest tailored to the current business.

Minimizing current expression, viewed conversely, maximizes future possible expressions. If the expression is minimal, we will only describe the goal we want to achieve and omit execution details (e.g., which method to use, in what order to execute). In other words, we defer concrete technical decisions and any execution-related information as much as possible. Thus, minimal information expression is necessarily declarative; execution details should be specified at runtime or inferred automatically by the runtime engine according to some optimization strategy.

To achieve framework neutrality, we need to treat the following in a framework-agnostic way:

1. Data (data input/output and storage)
2. Control (commands, events, etc.)
3. Side effects
4. Context

#### 4.1. Data (data input/output and storage)

   In mathematical terms, minimal information expression pushes all externally-related information to the boundary layer, which can be written as:

```
  output = biz_process(input)
```

### 4.2. Control (commands, events, etc.)

Traditionally, events are handled by passing an event-handler function into a component, which is then invoked internally. This is essentially the same as asynchronous callbacks. In modern async frameworks, most have abandoned callbacks in favor of Promises and async/await. Similarly, for events we can abstract event emission as a Stream and return this stream in the output.

```
Callback<E> ==> Promise<E>

EventListener<E> ==> Stream<E>
```

### 4.3. Side effects

Modeling the entanglement between business logic and the external world purely as input and output is often overly simplistic. A more refined description is:

```
[output, side_effect] = biz_process(input, context)
```

The problem with side effects is that they typically carry execution semantics and easily introduce dependencies on a specific runtime environment. For example, to implement file download in a typical web framework:

```javascript
 void download(HttpServletResponse response) {
     OutputStream out = response.getOutputStream();
     InputStream in = ...
     IoHelper.copy(in, out);
     out.flush();
 }
```

Because we need the runtime-provided response object, our business code is bound to the Servlet API; thus the file-download business code cannot automatically work with both Spring and Quarkus runtimes.

> Quarkus typically uses RESTEasy for the web layer, which does not support the Servlet API.

To solve this, a standard solution from FP is: don’t execute the side effect immediately; instead, wrap the side-effect information into a descriptive object and return it. For example, on the Nop platform, file download is implemented as follows:

```javascript
    @BizQuery
    public WebContentBean download(@Name("fileId") String fileId,
                                   @Name("contentType") String contentType,                                            IServiceContext ctx) {
        IFileRecord record = loadFileRecord(fileId, ctx);
        if (StringHelper.isEmpty(contentType))
            contentType = MediaType.APPLICATION_OCTET_STREAM;

        return new WebContentBean(contentType, record.getResource(),
                  record.getFileName());
    }
```

The Nop platform does not actually perform the download action but returns a WebContentBean that wraps the file to download. The framework recognizes WebContentBean and uses the download mechanism of different runtimes to execute the download. **At the business level, we only need to express the intent “download a file”; there is no need to perform the download ourselves**.

### 4.4. Context

The Nop platform’s approach is to **weaken the behavioral semantics of the context object and degrade it into a general-purpose data container**. Specifically, Nop uses a unified IServiceContext as the service context object (the same context interface across different engines, decoupled from the runtime environment). It has no special execution semantics and is essentially a Map that can be created and destroyed at will.

In front-end development, React Hooks cleverly leverages an implicitly available general context to decouple lifecycle functions from components, expanding the applicability of the function form.

The UI domain has traditionally been the stronghold of OOP. Previously, no UI framework built on FP languages and ideas achieved success comparable to OOP. React Hooks changed that; now front-end components have largely degenerated into functions. Of course, in some sense, Hooks also abandons traditional purity by using implicitly passed context to enable reactive, data-driven behavior.
(Theoretically, Hooks are similar to so-called algebraic effects.)

In [Why Is SpringBatch a Bad Design?](https://mp.weixin.qq.com/s/1F2Mkz99ihiw3_juYXrTFw), I introduced a design that generalizes a Hooks-like approach to batch-processing frameworks, overcoming many flaws of SpringBatch.

#### Algebraic Effects

Below is DeepSeek’s explanation of algebraic effects:

**Algebraic Effects** are a programming language feature that allows a function to **declare the operations it needs (e.g., state, IO) without handling their concrete implementation**, decoupling side-effect logic from the main flow. Key characteristics include:

1. **Declarative side effects**: Use a `perform`-like keyword to mark operations (e.g., `perform FetchData`), with an external **effect handler** providing the implementation;
2. **Resumable execution**: The function suspends; the handler completes the operation (e.g., fetching data) and then resumes the original function automatically;
3. **Implicit context passing**: No need to manually thread dependencies (like monadic chaining); the runtime manages context association.

**Compared with traditional error handling (try/catch)**:

- `try/catch` only handles errors and is not resumable
- Algebraic effects can handle arbitrary operations (state, async, etc.) and resume execution with values

**Example** (pseudo-code):

```ocaml
function getUser() {
  const token = perform GetToken();  // Declare token is needed
  return fetchUser(token);          // When resumed, token is provided by the handler
}

// External handler provides the concrete implementation
handle GetToken {
  resume with "xxx_token";  // Inject token and resume the original function
}
```

**Value**: Improves **composability** (freely compose effect handlers) and **maintainability** (separate pure logic from side effects).
(For example, React Hooks, via the Fiber architecture, simulates a similar pattern to realize a declarative abstraction for state management.)

## V. The Y Combinator

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/fp/Y-combinator.png)

The Y combinator is a technique for implementing recursive functions. It is defined as:

```
Y = λf.(λx.f (x x)) (λx.f (x x))
```

The Y combinator turns a non-recursive anonymous function into a function that can call itself recursively. For example, in JavaScript:

```javascript
const Y = f => (x => f(v => x(x)(v)))(x => f(v => x(x)(v)));
const fact = Y(g => n => n === 0 ? 1 : n * g(n - 1));
console.log(fact(5)); // Output 120
```

The fact function above is equivalent to:

```javascript
function fact(n){
  if(n === 0)
    return 1;
  return n * fact(n - 1);
}
```

> The Y combinator is theoretically significant because it can implement recursion in languages that lack built-in recursion. However, since most languages directly support named function references and recursive calls, it has limited practical use.

Typical derivations of the Y combinator tend to be obscure, such as [The Y Combinator Explained](https://mp.weixin.qq.com/s/EfGq9pfWXsu3IoHzau0D_Q) and [The Y Fixed-Point Combinator](https://zhuanlan.zhihu.com/p/100533005) https://zhuanlan.zhihu.com/p/100533005

Most introductions essentially verify that the definition of the Y combinator is correct but don’t explain why it must take that form. For instance, what does `x x` really mean inside—can we change it to `x x x`?

Below is a heuristic derivation for the shape of the Y combinator that is very intuitive; you can follow the same logic to obtain the Turing combinator and more, and even systematically construct infinitely many combinators.

First, consider the basic form of a recursive function:

```javascript
let f = x => in the function body, use f to denote itself for recursive calls
// Example: factorial
let fact = n => n < 2 ? 1 : n * fact(n-1)
```

> The recursive function above is first-order recursion, i.e., in its definition it only references itself to cause recursion.

We see that the function body references itself via the name f. If we want to eliminate this self-reference, we **must turn it into a parameter**, yielding:

```javascript
let g = f => x => in the function body, use f to denote the original recursive function
```

Function g adds a layer atop f, making it a higher-order function. Since f is an arbitrary recursive function, the only thing we know about g is that it can act on f:

```javascript
g(f) = x => within the function body, f is referenced via a closure over the parameter f
```

Clearly, g(f) returns the target recursive function we need, giving the so-called fixed-point equation:

```javascript
g(f) = f
```

Applying g to parameter f returns f itself; in this case, **f is a fixed point of g**.

We can now set up a standard procedure to construct an anonymous recursive function:

1. **Define an auxiliary function g based on the named function f**
2. **Solve for a fixed point of g**

Assume there **exists a standard method Y to obtain a fixed point of g**:

```javascript
f = Y g  ==>  Y g = g (Y g)
```

If Y exists, what does it look like? To solve the fixed-point equation, a common approach is iteration: repeatedly apply the original equation and examine the evolution:

```javascript
f = g(f) = g(g(g...))
```

If fully unrolled, f corresponds to an infinite sequence. **Assume we can take the square root of this infinite sequence**:

```javascript
f = g(g(g...)) = G(G) = g(f) = g(G(G))
```

If such a function G exists, what is its definition? Fortunately, **G(G) = g(G(G)) can itself be viewed as the definition of G**:

```javascript
G(G) = g(G(G)) ==> G = λG. g(G(G)) = λx. g(x x)
```

The last equality uses parameter renaming, i.e., α-conversion in λ-calculus.

Given G, the definition of Y is straightforward:

```
Y g = f = G(G) = (λx.g (x x)) (λx.g (x x))   (1)
Y = λg. (λx.g (x x)) (λx.g (x x))            (2)
```

In (1) we directly substitute G’s definition. In (2) we treat Y g as the definition of Y:

```
 Y g = expr ==> Y = λg. expr
```

We can continue α-conversion (renaming parameters) to obtain the common form seen in the literature:

```
Y = λf. (λx.f (x x)) (λx.f (x x))
```

For more details, see [A Heuristic Derivation of the Y Combinator](https://mp.weixin.qq.com/s/ARsrYJpApqB2_72tl-MSQQ)

As for why the Y combinator looks so daunting—how was such a complex thing conceived? Why choose the form `f = G(G)`?
The truth is, there’s no deep “why.” The square-root choice is arbitrary (or the simplest choice). If not square root, you could take a cube root `f = G G G`, or make a sandwich `f = G g G`, or even a mille-feuille if you like. The same pattern can be applied repeatedly to produce infinitely many fixed-point combinators.
<!-- SOURCE_MD5:197d9f82086b918e8b54b3ce151947d8-->
