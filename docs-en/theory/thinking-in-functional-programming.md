# Functional Programming from a Pragmatic Perspective

## Video Explanation: [https://www.bilibili.com/video/BV1hrADeZE6v/](https://www.bilibili.com/video/BV1hrADeZE6v/)

Functional programming ideas have entered the mainstream development field, but many people's understanding of it is still limited to `map`, `flatMap`, and a large number of Lambda expressions. It seems that using Lambda expressions itself equates to functional programming.

Some people also emphasize languages like Haskell and Scala, which support complex type definitions for functional programming, while in languages like Java, which are generally imperative, it's difficult to truly implement so-called functional programming and gain its benefits.

However, these views are one-sided. Functional programming encompasses much more than just using higher-order functions. It is a general programming paradigm and not a feature of specific programming languages. In Java, for example, functional programming ideas can still be fully realized.

This article discusses some practical experiences of using conventional imperative languages to practice functional programming ideas under the pragmatic perspective.

The following articles are references:

- [Why is functional programming beneficial for decoupling?](https://mp.weixin.qq.com/s/dWbnBmBLM9LW7hlw8jDw)
- [Monad Orientation: For Rookies](https://zhuanlan.zhihu.com/p/65449477)
- [An Inspirational Derivation of Y Combinator](https://mp.weixin.qq.com/s/ARsrYJpApqB2_72tl-MSQQ)
- [Breaking Free from Frameworks, Achieving True Framework Neutrality](https://mp.weixin.qq.com/s/v2_x4gre4uMfz3yYNPe9qA)
- [What is declarative programming?](https://zhuanlan.zhihu.com/p/85491177)

## 1. Core Ideas of Functional Programming

If you ask DeepSeek AI about the core idea of functional programming, it will respond as follows:

Functional programming's core idea can be summarized as follows:

### 1.1 Pure Functions
- A pure function has no side effects (does not modify external state).
- Its output is solely determined by its input.
- The same input always produces the same output. This makes code predictable and testable.

### 1.2 Immutable Data
- Once data is created, it cannot be modified.
- Sharing state leads to concurrency issues, typical implementations include persistent data structures.

### 1.3 Functions as First-Class Citizens
- Functions can be treated like any other variable.
- They can be passed as arguments and returned as values.
- Support for higher-order functions (like `map`, `flatMap`) and closures.

### 1.4 Declarative Programming
- Focus on "what to do" rather than "how to do it."
- Describes logic using expressions (e.g., `list.map(f)`), not through imperative control structures.

### 1.5 Recursion and Lazy Evaluation
- Replace iterative loops with recursion.
- Tail recursion optimization; lazy evaluation (e.g., stream processing).

### 1.6 Function Composition and Currying
- Build complex logic by composing small functions (e.g., `f(g(x))`).
- Currying: Applying multiple functions step-by-step.

**Key Goal**: Through mathematical strictness, improve code modularity, maintainability, and concurrency safety. Languages like Haskell and Erlang are typical examples. Modern languages such as JavaScript and Python also incorporate some of their characteristics.

**Final Note from DeepSeek AI**: Completion of functional programming exploration.

---

## 2. The Essence of Functional Programming

If you continue to question the fundamental reasons behind functional programming, DeepSeek AI provides the following response:

Functional programming (FP) exists due to three layers of depth: mathematical logic, computer science philosophy, and real engineering challenges.

### A. Mapping from Logic to Determinism
Functional programming originates from mathematical **lambda calculus** (Lambda Calculus), whose core idea is:
1. Functions are pure mappings from inputs to outputs.
2. No side effects; results depend solely on inputs.
3. Example: `f(x) = x + 1` is a mathematical function, while `void increment(int& x)` is an imperative operation.

Deterministic nature (Referential Transparency): The result of any expression remains consistent across all contexts and times.

**Core Purpose**: Use mathematical determinism to counter software engineering's uncertainty (e.g., race conditions, unpredictable side effects).

### B. From Machine Models to Mathematical Models
Traditional imperative programming (like C or Java) follows the **von Neumann architecture**, viewing programs as sequences of operations on memory states. Functional programming, however, treats programs as mathematical constructs.

1. **Mathematical Construction**: Programs are built using functions that manipulate data.
2. **Elimination of State Manipulation**: Focus on function definitions rather than state changes.

Functional programming's philosophical stance is rooted in this shift from machine-centric to math-centric models.

---

## 2.1 Mathematical Logic: From Lambda to Determinism
Functional programming's foundation is mathematical lambda calculus, which formalizes functions as pure mappings. This leads to:

- **Purity of Functions**: No state changes; outputs depend solely on inputs.
- **Deterministic Evaluation**: Results are consistent across all contexts.

### 2.2 Computer Science Philosophy: From Mechanism to Mathematics
Functional programming challenges the von Neumann worldview by treating programs as mathematical objects rather than mere sequences of operations:

1. **Programs as Mathematical Objects**: Functions define program behavior, not just data transformations.
2. **Elimination of Side Effects**: State is an artifact of imperative programming.

### 2.3 Engineering Reality: From Complexity to Simplicity
Functional programming's practical benefits:
- Easier testing and debugging due to predictability.
- Reduced error-prone complexity in concurrent systems.
- Higher code reuse through function composition.

---

# The Nature of Imperative Programming: A Technical Document

## The Fundamental Duality

In programming, **Expression** (Expression) is not the same as **Statement** (Statement). By combining functions to derive results rather than modifying states, **Functionality** (Functional Programming) simplifies the problem into a static "Input→Output" relationship.

- Example: `map(f, filter(g, list))` represents declarative logic, not a loop with temporary variables.

## The Complexity of Abstract Machines

The complexity of abstract machines arises from their reliance on mutable data and control flow. By using **Immutable Data** (Immutable Data) and higher-order functions, we hide low-level memory management and execution order details, focusing solely on the logic itself.

## The Fundamental Contradiction

The root of software complexity lies in **State Management** (State Management), such as multi-threaded locks and global variable pollution. Functional programming resolves this by eliminating mutable state, reducing the problem to a static "Input→Output" relationship, thereby lowering cognitive load.

---

# The Nature of Functional Programming: A Technical Document

## The Fundamental Drive

Functional programming's resurgence (e.g., Scala, React Hooks) is directly related to **Modern Software Complexity** (Modern Software Complexity). This is due to:

1. **Concurrency and Distributed Systems' Challenges**
   - Multi-core CPUs and distributed computations require no shared state (Shared-Nothing), which pure functions and immutable data naturally avoid.
   - Example: Erlang uses the Actor model and immutable message passing to build reliable communication systems.

2. **Software Maintainability Needs**
   - Pure functions and declarative code are more easily testable, reason about, and refactor due to reduced implicit dependencies.
   - Example: React's UI = f(state) ensures consistent visual updates via immutable state updates.

3. **Complex Systems' Composability**
   - Function composition (e.g., `compose(f, g)`) and type systems (e.g., Haskell's Monad) enable modular construction of complex logic.

---

## The Fundamental Force

Functional programming's essence is to redefine computation as **Mathematical Rigor** (Mathematical Rigor) and **Composability** (Composability). This opposes the exponential growth in software complexity due to entropy (disorder).

---

# The Fundamental Philosophy: A Different Worldview

Functional programming's core is a redefinition of **"Computation"**:

- **Imperative Programming**: Computation is state modification (Turing Machine model).
- **Functional Programming**: Computation is expression simplification (Lambda Calculus model).

Both models are theoretically equivalent under the Church-Turing Thesis, but their practical abstraction levels differ:

- **Turing Machine Model**: Closer to physical machine implementation but requires manual state management.
- **Lambda Calculus Model**: Aligns more closely with human logical reasoning, ensuring correctness through mathematical rules.

---

# Summary: The Essence is "Determinism" and "Composability"

Functional programming redefines software paradigms through:

1. **Mathematical Functions Eliminate Side Effects** → Enhances determinism.
2. **Immutable Data Eradicates Time Coupling** → Simplifies concurrency.
3. **Higher-Order Functions and Type Systems Build Abstractions** → Boosts composability.

Its ultimate goal is to elevate software engineering from "Mechanical Operations" to "Mathematical Proof," ensuring reliability and maintainability through formalization.

---

# DeepSeek's Understanding: A Reflection

DeepSeek's understanding runs far deeper than most architects' grasp of these concepts. Essentially, functional programming can be understood from the following angles:

## 2.1. Function Concept's Universality

Functional programming offers a unique perspective on observing and abstracting the world. While object-oriented programming declares **"Everything is an Object"**, functional programming asserts **"Everything is a Function"**.

In this paradigm, **"Value a"** can be seen as equivalent to the Lambda expression `()=>a`. Languages like Scala support lazy evaluation, where variables are initially functions that execute upon first use.

Functionality transforms computation into a static process. Results are consistent across repeated computations, allowing for reliable and reproducible outcomes.

The foundation of this world is **Uniformity** (Uniformity), yet its practical application may fall short in real-world complexity. For instance, physical materials exhibit vast structural diversity despite being composed of a limited number of atomic types.

---

## Data as Information Compression

In Chinese and Arabic numerals, digits 1-9 are single characters, reflecting their historical evolution. Similarly, Roman numerals use combinations like IV (4) and IX (9), which represent subtractive notation. In French, numbers like **80** (quatre-vingts) and **90** (neuf) employ similar conventions.

Arabic numerals exemplify efficient information storage through compact representation. This efficiency is mirrored in programming languages that use single characters for digits, ensuring concise data transmission.

However, the logical layer in Lambda calculus theory views all constructs as functions, leading to verbose representations like `map(f, filter(g, list))`. While this may seem cumbersome, it ensures mathematical rigor and correctness.

---

### 2.2 Functions Have Good Mathematical Properties

Mathematical functions possess a unique certainty inherent in mathematical truths. This certainty allows us to use limited local knowledge for reasoning through division and association. By leveraging the mathematical properties of functions, we can achieve significant benefits in the scientific domain.

> Associativity is the property that allows us to add parentheses without affecting the final result:  
  \(a + (b + c) = (a + b) + c\).  
  In a set of calculations, we can freely add parentheses, and the result will remain unchanged. This independence within allows for local value while maintaining overall integrity.

However, this certainty does not necessarily translate to specific programming languages. For example, in Java, modeling a process involves creating a StepModel to represent each step mathematically, whereas in reality, it is modeled as an object. Similarly, the flow of data and operations can be represented using StepModel while preserving the integrity of mathematical functions.

### 2.3 Functions Have No Temporal Concept

In the world of mathematical objects, there exists a realm without time or causality—a completely free and eternal domain where every action carries no consequences. This freedom extends to all mathematical objects, including those with inverses (like differences), allowing us to accumulate changes indefinitely, ultimately returning to the origin.

> From proposition A, we can derive proposition B, but this does not imply that A is more fundamental than B. Instead, B can be derived from A in reverse, demonstrating the interconnected nature of mathematical concepts.

In programming terms, functional programming excels in parallel execution because all functions are inherently concurrent. There is no state modification, so there's no competition or race condition. This advantage holds particularly in distributed and parallel computing environments.

### 2.4 Function Programming in Object-Oriented Languages

Detailed discussion can be found in the article [Why Functional Programming Helps Decouple](https://mp.weixin.qq.com/s/dWbnBmBLM9LW7hlw8x2jDw).

1. Functions should have parameters and return values.
2. Minimize shared data modifications.
3. Use higher-order functions instead of inheritance.
4. The value of function composition stems from the associativity property.
5. Decouple traversal and computation logic.
6. Lazy evaluation reduces unnecessary causal coupling.

### 4. Implementing Framework-Neutral Minimal Information Representation

Detailed discussion can be found in the article [Breaking Free from Frameworks: Achieving True Framework Neutrality](https://mp.weixin.qq.com/s/v2_x4gre4uMfz3yYNPe9qA).

> Framework-agnostic minimal information representation allows for independence from any predefined frameworks or platforms. This is achieved by stripping away all additional information, leaving only the core concepts relevant to your domain.

For example:
```lambda
<lambda>
  <lambda>
    <apply>
      <function>
        <variable>f</variable>
        <lambda>
          <variable>x
          <apply>
            <function>
              <variable>f
              <apply>
                <function>
                  <variable>f
                  <variable>x
                </function>
                <variable>x
              </apply>
            </function>
            <variable>x
          </apply>
        </lambda>
      </function>
      <variable>x</variable>
    </apply>
  </lambda>
</lambda>
```
This lambda expression represents applying the function `f` three times to `x`, effectively computing \(f(f(f(x)))\). While our familiar integer `3` is a concise representation, there are infinitely many ways to represent `3` mathematically, each encapsulating some computation process.

From this perspective, everything is a function. On the computational level, adding parentheses seems redundant, but it allows for local computations without affecting the overall result. This independence means functions have intrinsic value and can be reasoned about locally while maintaining global integrity.

However, mathematical functions don't always need to be represented by specific programming language functions. For instance, modeling a process in Java involves creating a `StepModel` object that mathematically represents each step, whereas in reality, it's modeled as an object. Similarly, data flow and operations can be managed using `StepModel`, preserving the integrity of mathematical functions.

void activateCard(HttpServletRequest req) {
    String cardNo = req.getParameter("cardNo");
    ...
}

void activateCard(CardActivateRequest req) {
    ...
}

Compare the above two functions, the first function's expression is not minimal because it introduces additional Http context information, while the second function uses only custom business-specific CardActivateRequest object information.

Minimizing current information representation can be understood from the opposite direction, which maximizes future possible information representation. If the expression is minimized, we will inevitably describe what we aim to achieve and omit various execution details such as how to reach, in what order, etc. This means we try to defer making specific technical decisions as much as possible, deferring expressing and executing related information.

To achieve framework neutrality, we need to handle the following in a framework-neutral way:

1. Data (Data Input Output & Storage)
2. Control (Commands, Events, etc.)
3. Side Effect
4. Context


#### 4.1. Data (Data Input Output & Storage)

From a mathematical perspective, minimizing information representation leads to all externally related information being concentrated in the boundary layer, which can be expressed as:

output = biz_process(input)

```
output = biz_process(input)
```


### 4.2. Control (Commands, Events, etc.)

The traditional approach to event handling is to pass an event response function to a component and then call this function inside the component. This process is essentially consistent with how asynchronous callback functions are handled. In modern asynchronous processing frameworks, callbacks have been replaced by Promises and async/await syntax, similar to event handling.

For example, both Callback and EventListener can be abstracted into Stream objects:

```
Callback<E> ==> Promise<E>

EventListener<E> ==> Stream<E>
```


### 4.3. Side Effect

Interpreting business logic as a tug-of-war between input/output needs and external world interactions leads to over-simplification in many cases. A more detailed explanation can be expressed using the following formula:

[output, side_effect] = biz_process(input, context)

```
[output, side_effect] = biz_process(input, context)
```

Side effects are often tied to specific runtime environments and dependencies. For instance, file downloads in standard Web frameworks require HttpServletResponse which tightly couples business code with Servlet interfaces, making it incompatible with Spring and Quarkus.

> Quarkus typically uses RESTEasy for the Web layer, which does not support Servlet interfaces

To address this, a functional programming approach offers a standardized solution: avoid executing side effects directly and encapsulate corresponding information into a descriptive object that is returned as part of the output. For example, in Nop's platform:

```markdown
@BizQuery
public WebContentBean download(@Name("fileId") String fileId,
                              @Name("contentType") String contentType,
                              @IServiceContext ctx) {
    IFileRecord record = loadFileRecord(fileId, ctx);
    if (StringHelper.isEmpty(contentType)) {
        contentType = MediaType.APPLICATION_OCTET_STREAM;
    }

    return new WebContentBean(
        contentType,
        record.getResource(),
        record.getFileName()
    );
}
```

### 4.4 Context

Nop platform's approach is to **deemphasize the semantic meaning of context** by reducing it to a generic data container. Specifically, Nop uses `IServiceContext` as the uniform context interface (different engines adopt this same interface), making context independent from the specific runtime environment. However, it doesn't have special semantic meaning; it's essentially just a map that can be created and destroyed on demand.

In frontend development, React Hooks elegantly leverage an implicit generic context to decouple lifecycle functions from components, greatly expanding the scope of functional programming.

The UI field has traditionally been the domain of object-oriented programming's strengths. Previous UI frameworks built on functional programming ideas struggled to match object-oriented programming's success. However, React Hooks have introduced a new landscape where most frontend components have been reduced to function-like forms. In a sense, Hooks have abandoned traditional purism by implicitly passing context, enabling data-driven response handling.

[Why is SpringBatch a bad design?](https://mp.weixin.qq.com/s/1F2Mkz99ihiw3_juYXrTFw) discusses how similar approaches to Hooks can be applied to batch processing frameworks to overcome the limitations of SpringBatch.

## 5. Y Combinator
# The Y Combinator

The **Y-combinator** is a technique used to implement recursive functions. Its definition is as follows:

```
Y = λf.(λx.f (x x)) (λx.f (x x))
```

The purpose of the Y-combinator is to transform a non-recursive anonymous function into a recursively callable function. For example, in JavaScript:

```javascript
const Y = f => (x => f(v => v(x)(v))) (x => f(v => v(x)(v)));
const fact = Y(g => g(n) => n === 0 ? 1 : n * g(n - 1));
console.log(fact(5)); // Outputs 120
```

The defined `fact` function is equivalent to:

```javascript
function fact(n){
  if(n === 0)
    return 1;
  return n * fact(n - 1);
}
```

> The Y-combinator holds significant theoretical importance because it enables recursion in languages that do not inherently support recursive mechanisms. However, in practical terms, its utility is limited since most programming languages directly support named functions and recursive calls.

The derivations involving the Y-combinator often appear confusing, such as in articles like "[The Y Combinator](https://mp.weixin.qq.com/s/EfGq9pfWXsu3IoHzau0D_Q)" and "[Y Fixed-point Combinator](https://zhuanlan.zhihu.com/p/100533005)", but they essentially verify the correctness of the Y-combinator's definition. While these articles may not fully explain why the Y-combinator takes its specific form, they confirm that its definition is accurate.

The Y-combinator's form can be intuitively derived by following these steps:

1. **Define the Recursive Function**: Start with a basic recursive function, such as the factorial function:
   ```javascript
   let f = n => n < 2 ? 1 : n * f(n - 1);
   ```

2. **Identify the Fixed Point**: Recognize that applying the function to itself (i.e., `f(f)`) yields the desired fixed point.

3. **Apply the Y-combinator**: Use the Y-combinator to transform the non-recursive form into a recursive one:
   ```javascript
   const g = f => x => f(x)(x);
   const Y = g => f => g(f)(g(f));
   ```

4. **Derive the Fixed Point Equation**: Through repeated application, derive that `f(f) = f`.

By following these steps, we can construct a recursive function from its non-recursive counterpart using the Y-combinator.

# Recursive Function Basics

A basic recursive function might look like this:

```javascript
let f = n => {
  if(n === 0)
    return 1;
  return n * f(n - 1);
};
```

This is a first-order recursive function, where each call to `f(n)` depends on `f(n-1)`.

# Constructing the Y-combinator

To create the Y-combinator, follow these steps:

1. **Define the Auxiliary Function**: Create an auxiliary function `g` that takes a function `f` and returns another function:
   ```javascript
   let g = f => x => f(x)(x);
   ```

2. **Define the Y-combinator**: Use `g` to define `Y`, which takes a function `f` and applies `g(f)` twice:
   ```javascript
   const Y = g => f => g(f)(g(f));
   ```

3. **Use the Y-combinator**: Apply `Y` to your recursive function to turn it into a fully recursive one.

For example, applying `Y` to the factorial function:

```javascript
const fact = Y(g => g(n) => n === 0 ? 1 : n * g(n - 1));
```

# Deriving the Fixed Point

The Y-combinator effectively solves the fixed point equation:

```javascript
f(f) = f
```

Through repeated application, we see that `f` becomes a fixed point of itself. This property allows us to construct recursive functions from their non-recursive forms.

```
Y g = f = G(G) = (λx.g (x x)) (λx.g (x x))   (1)
Y = λg. (λx.g (x x)) (λx.g (x x))            (2)
```

The above (1) directly substitutes the definition of G. While (2) treats Y g as the definition of Y.

```
Y g = expr ==> Y = λg. expr
```

We can continue executing alpha-transformations, changing parameter names to make the Y combination resemble a common form in literature.

```
Y = λf. (λx.f (x x)) (λx.f (x x))
```

For further details, see [an inspirational derivation of the Y combination](https://mp.weixin.qq.com/s/ARsrYJpApqB2_72tl-MSQQ).

Specifically, why does Y combination look so intimidating? How was this complex thing invented? Why choose `f = G(G)` as its decomposition form?
In reality, there's no deep reason. Choosing to square is just a simple, arbitrary choice (or perhaps the simplest one). If you don't choose to square, you can also cube it: `f=G G G`, or even make a sandwich: `f=G g G`. If you're feeling adventurous, you might even go for something layered like `f=G G G`. The same pattern can be repeated indefinitely, generating an infinite number of fixed points.

