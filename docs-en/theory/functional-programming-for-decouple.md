# Why Functional Programming Facilitates Decoupling (Decouple)

In recent interviews, I noticed many people equate functional programming with Lambda expressions and collection-processing functions like flatMap/map/join. They have only a vague sense of why functional programming is useful and in what scenarios it applies. In this article, I’ll give a brief introduction to the ideas of functional programming and how to apply them in everyday coding to achieve logical decoupling, and explain in what ways functional programming provides a beneficial complement to object-oriented programming.

What is functional programming? Here is Zhipu Qingyan AI’s answer:

Functional Programming (FP) is a programming paradigm that emphasizes constructing the computational process as a series of function calls. The foundation of functional programming is the mathematical concept of functions, characterized by the following:

1. Pure Functions:
   
   - The function’s output depends only on its input parameters.
   - The function has no side effects during execution, e.g., it does not change external state.

2. Immutability:
   
   - Once data is created, it cannot be modified. To change data, you create a new copy.

3. Higher-Order Functions:
   
   - Functions can be passed as parameters to other functions and can also be returned by functions.

4. Function Composition:
   
   - Compose multiple functions into a new function to achieve more complex operations.

5. Recursion:
   
   - A function solves problems by calling itself, rather than using loop control structures.

6. Lazy Evaluation:
   
   - The evaluation of expressions is deferred until their results are actually needed, which can improve efficiency.
   
   ==============Zhipu AI creation completed===================

This entire section is often repeated, but why do these concepts exist? Is it impossible to proceed without them? Are they only useful within libraries or frameworks specifically designed for functional programming?

## I. Functions Should Have Parameters and Return Values

Functional programming is just a way of thinking; it can equally be applied in object-oriented languages. The simplest application is to leverage the concept of functions: a function should have input parameters and a return value. In object-oriented languages, we can often access parameter information via the this pointer and persist changes to a database (a kind of side effect), so many functions have neither parameters nor return values.

```javascript
// Calculate interest
void calcInterest(){
  String accountId = this.requestMessage.getAccountId();
  Account account = accountRepository.getAccount(accountId);
  ...
  interestRepository.saveInterest(interest);
}
```

For example, the interest calculation function above reads accountId from a member variable rather than as a function parameter, makes extensive use of various repositories in processing, and persists the calculation result to the database with no return value.

A typical problem with such functions is that they have poor reusability and can basically only be used in a specific scenario. For instance, suppose we now want to provide an interest what-if calculation feature. Although the interest calculation logic is already written in the function above, the calculation logic and the persistence logic are coupled together, preventing us from reusing the function to implement a what-if interest computation.

A function should have a return value so that we can separate how to perform the calculation from how to use the result, thus achieving decoupling between the two.

Equally important, a function should have request parameters. Aside from scheduled jobs, a business function should be triggered by explicit external driver information, and the dependency between the function and the outside world should be expressed as clearly as possible through parameters. Only functions with parameters can decouple from their context. You should avoid fetching information from ThreadLocal and other contextual variables; in particular, business-level information (not general infrastructure-level) should be explicitly expressed and passed.

Ideally, by observing a function’s signature (function name + parameter types + return type), you should be able to know what the function does, what information it needs, and what information it produces, without diving into its internal code to acquire this knowledge.

When using a DDD architecture, as we write business logic we can express business data entirely through aggregate root objects, completely avoiding any repository usage. In this way, whether the data is stored in a database or fetched through a remote REST API can be hidden behind the aggregate root’s interface.

```javascript
IntersetCalcResult calcInterest(IAccountBo accountBo){
  // All required information can be read from the aggregate root accountBo, so we no longer need various repository dependencies
  // The interest calculation result is returned via the return value rather than being persisted with a repository call
}
```

## II. Minimize Mutations on Shared Data

Functional programming strongly advocates immutability because it is key to achieving referential transparency. Zhipu Qingyan AI explains the relationship between immutability and referential transparency as follows:

Referential transparency means an expression can be replaced with its value anywhere without affecting the program’s behavior. Simply put, if an expression is referentially transparent, then its result depends only on its parameters and it has no side effects.
Here is the relationship between immutability and referential transparency:

1. Immutability is a prerequisite for referential transparency:
   - To make an expression referentially transparent, the data it depends on must be immutable. If data is mutable, the same expression’s result may differ due to external state changes, violating referential transparency.

2. Referential transparency promotes immutability:
   - When pursuing referential transparency, you naturally tend to use immutable data, because only then can you guarantee that an expression can be replaced by its value anywhere without affecting other parts.

3. Shared objectives:
   - Both immutability and referential transparency aim to reduce side effects and improve code predictability. Immutability ensures data does not change after creation, while referential transparency ensures expression results depend solely on inputs.

4. Reasonability:
   - Immutability and referential transparency make code easier to reason about. With immutability, you don’t worry about accidental changes elsewhere; with referential transparency, you can safely assume an expression’s result depends only on its inputs.

5. Core value of functional programming:
   - In functional programming, immutability and referential transparency are key to implementing pure functions. Pure functions have no side effects and always return the same output for the same input, which is a core value of functional programming.
     In summary, immutability and referential transparency are complementary concepts that jointly underpin many desirable features in functional programming, including readability, maintainability, testability, and reusability. By adhering to these principles in code, you can build more robust and efficient software systems.

==========Zhipu AI creation completed=================

Although immutability is very useful, it is rarely emphasized in mainstream languages such as Java because mutating variables is often more convenient and more performant than constructing brand-new objects. In Java, it’s unrealistic to enforce immutability at every single detail. A beneficial direction is to minimize mutations, especially concurrent mutations of the same global object across threads. A typical approach is to manage immutable objects in a global Map; when editing in a local context, use a mutable copy and allow local changes, but after updating to the global Map, the object is no longer mutated. This can minimize lock scope; during local editing, no locks are needed and objects do not need volatile, synchronized, or other synchronization keywords.

```
MyObject obj = g_objects.get(objName);
MyObject copy = obj.cloneInstance(); // Or new MyObject() to create a new instance
copy.setXX(xx);
g_objects.put(objName,copy);
```

This approach can also be seen as similar to database MVCC transaction handling: modifications made after the transaction starts and before it commits are invisible to others. The same object may exist in multiple state versions simultaneously; during processing we can always read the pre-modification version. That version satisfies data consistency, while the version being modified may be full of temporary changes and does not meet business consistency.

A similar approach exists on the frontend. For example, the Redux framework originally emphasized immutable data, but in recent years an interesting change has occurred: introducing the Immer library to enable local mutable edits.

```javascript
// Traditional approach to constructing immutable data
const todoReducer = (state = initialState, action) => {
  switch (action.type) {
    case ADD_TODO:
      // Use the spread operator to create a new copy of the todos array
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
// Approach simplified with Immer
const todoReducer = (state = initialState, action) => {
  switch (action.type) {
    case ADD_TODO:
      // Use Immer’s produce function to update state
      return produce(state, (draft) => {
        draft.todos.push({ id: Date.now(), text: action.payload, completed: false });
      });
    default:
      return state;
  }
};
```

Immer’s produce function is akin to opening a transaction: inside produce, we can mutate the temporary object draft; Immer uses AOP-like Proxy techniques to record all mutations, and upon exiting produce, it automatically constructs a new state object based on the recorded operations.

Without Immer, immutability means always constructing new full objects, whereas Immer’s role is to record mutations as Delta and then apply Delta to the initial state to produce a new state.

The most essential impact of immutability is that it lets us distinguish all the details of evolution in the state space—this is the fundamental source of predictability. If mutation is allowed, then an object’s state is overwritten; unless we record a copy before each change or the Delta produced by the modification, we will lose historical state information. Immutability means we do not overwrite existing information; if we care about the state details at some point, we just record the object’s pointer at that instant. Every overwrite introduces ambiguity. For example, if processing fails mid-way and we need to roll back to a consistent initial state, how do we restore the original consistency if we have already performed substantial overwriting mutations?

When multiple threads share the same object and immutability is not present, locks are necessarily required for mutation. The role of a lock is to achieve exclusive access—it blocks multiple threads, choosing one to execute. If we view a thread as a timeline, the lack of immutability causes multiple timelines to couple and block each other. In contrast, with global shared objects that are immutable, each timeline can evolve independently, enabling decoupling. Of course, this also means we may observe multiple historical snapshots of the same object coexisting (parallel universes).

## III. Use Higher-Order Functions Instead of Inheritance

In object-oriented programming, a common misuse is to rely heavily on inheritance for reuse. For instance, we often use the Template Method pattern: implement a template method in a base class, which calls several virtual functions, and then provide different implementations in derived classes to reuse the template method logic.

```java
// Abstract class
abstract class AbstractProcess {
    // Template method defining the algorithm’s skeleton
    public final void process() {
        this.primitiveOperation1();
        this.primitiveOperation2();
    }

    // Abstract operations to be implemented by subclasses
    protected abstract void primitiveOperation1();
    protected abstract void primitiveOperation2();
}
```

The essence of a template method is to obtain customizable function pointers via an implicitly passed this pointer. Its cost is that every combination requires creating a new subclass, and the code written in the subclass is bound to the template method, making it impossible to freely apply in other contexts.

Generally, passing in callback functions and callback interfaces is preferable to the Template Method pattern.

```java
class ConcreteProcess{
    public void process(IProcessor processor){
       processor.primitiveOperation1();
       processor.primitiveOperation2();
    }
}
```

A template method effectively uses a fixed this parameter to obtain function pointers. Passing callback interfaces directly through function parameters is much more flexible; for example, separate multiple callback interface parameters to achieve cross-composition.

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

In the example above, the code in IProcessor1 and IProcessor2 can be used not only in the current ConcreteProcess class, but also in other scenarios such as OtherProcess. When calling ConcreteProcess, passing different Processor1 and Processor2 implementations naturally enables cross-composition of different logic. The Template Method pattern, however, hardwires a particular combination of Process and Processor1/Processor2, effectively coupling all three.

## IV. The Value of Function Composition Stems from Associativity

Function composition is the process of merging two or more functions into one. If there are two functions f: A → B and g: B → C, then their composition g ∘ f is a function from A to C defined as (g ∘ f)(x) = g(f(x)) for all x ∈ A.
For example:

- Let f(x) = x + 2
- Let g(x) = 2x
- Then their composition (g ∘ f)(x) = g(f(x)) = g(x + 2) = 2(x + 2) = 2x + 4

Function composition is useful essentially because composition satisfies the associative law. That is, if three functions f, g, and h can be composed, then they satisfy associativity: (h ∘ (g ∘ f)) = ((h ∘ g) ∘ f). This means composing f and g first, then composing with h, yields the same result as composing g and h first, then composing with f.

- (h ∘ (g ∘ f))(x) = ((h ∘ g) ∘ f)(x) for all x ∈ A
  Here "∘" denotes function composition.

The existence of associativity allows us to organize multiple functions based on local information. Without associativity, we would have to know the functions’ specific application objects and contexts and could not extract several functions independently into something reusable.

```javascript
 h(g(f(x)) == p(f(x)) // p = h ∘ g
 h(g(u(x)) == p(u(x)) // The combined result p = h ∘ g can be reused in different contexts
```

For a deeper analysis of associativity, see [A Programmer’s Clarification of the Delta Concept, with Examples from Git and Docker](https://mp.weixin.qq.com/s/D5bDNkMJ9gYrFb0uDj2EzQ).

It is important to emphasize that anything that satisfies associativity is automatically decoupled from context—it is context-free! Whether it is a function is not the key; the key is satisfying associativity. (In Reversible Computation theory, Delta also satisfies associativity!)

```
 (a*(b*c))*d  == a * BC * d, BC = b*c
 u*((b*c)*v)  == u * BC * v, BC = b*c
```

Satisfying associativity means we can add parentheses anywhere in the overall computation, and the part inside the parentheses can be computed locally, independently of the outside. For example, in the example above, regardless of the external environment, b*c can be combined to produce a reusable BC.

In functional programming, there is a concept that can feel mysterious to beginners: the Monad. Many FP enthusiasts believe the Monad is the essence of FP, a noble abstraction granted by category theory. But from a pragmatic perspective, a Monad as a design pattern unique to FP simply expresses that a family of functions of the form a -> m b satisfy associativity. For a detailed introduction to Monad, see my Zhihu article [A Beginner’s Guide to Monad](https://zhuanlan.zhihu.com/p/65449477).

## V. Decouple Traversal Logic from Computation Logic

If you examine the code we write daily, you’ll see they often couple two kinds of logic: we both search/traverse the data that needs to be processed and perform specific computations at the same time.

```javascript
 Map<String,Integer> wordCount(File file){
    Map<String, Integer> wordCount = new HashMap<>();

    String text = readText(file);
    // Traversal logic and computation logic coupled in one loop
    String[] words = text.split("\\s+");
    for (String word : words) {
        // Traversal logic: iterate over each word
        // Computation logic: compute word occurrence counts
        wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
    }
    return wordCount;
}
```

In the example above, the traversal logic (parsing the file and looping over words) and the computation logic (updating counts in the HashMap) are tightly coupled.

Using higher-order functions, functional programming can effectively separate the logic of traversing data from the logic of processing that data.

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

We can write a forEachWord function that traverses each word in a file and supplies the computation through a passed-in consumer function. After separating traversal logic from computation logic, the same forEachWord can accept different consumers to implement different features—for example, counting only words that start with an uppercase letter. Such changes do not require modifying forEachWord.

```javascript
Map<String, Integer> wordCount = new HashMap<>();
Consumer<String> consumer = word -> {
    if(Character.isUpperCase(word.charAt(0))) {
      wordCount.merge(word, 1, Integer::sum);
    }
};

forEachWord(file, consumer);
```

Similarly, the same consumer can be used with different traversal functions. For example, to count words across all files in a specified directory, you don’t need to modify the consumer—just provide a new forEachWordInDir function.

### Y Combinator: Decoupling Recursive Structure from Computational Structure

In functional programming, a subtler form of decoupling is between recursive traversal and computation structure. In general-purpose languages, recursion is straightforward, but the function must have a name, and inside the function we reference that name to call itself recursively.

```javascript
function fact(n){
    return n < 2 ? 1 : n * fact(n-1)
}
```

There is a magical Y Combinator function that takes in an ordinary function without recursive references and returns a function that performs self-recursion.

```javascript
const fact0 = f => n => n < 2 ? 1 : n * f(n-1)
const fact = Y(fact0)
```

fact0 is a plain anonymous function whose implementation does not reference its own name. The so-called Y Combinator looks like this, which might seem a bit intimidating:

```
function Y(f) {
    const g = x => {
       return f(y => x(x)(y));
    };
    return g(g);
}

// Can be verified
fact(n) == Y(fact0)(n)
```

The Y Combinator may appear unintuitive, but I presented a heuristic derivation that makes the construction of Y straightforward and can be generalized to obtain more combinators. See [A Heuristic Derivation of the Y Combinator](https://mp.weixin.qq.com/s/ARsrYJpApqB2_72tl-MSQQ).

## VI. Lazy Evaluation Reduces Unnecessary Causal Coupling

What is lazy evaluation? Here is Zhipu Qingyan AI’s answer:

Lazy evaluation is an evaluation strategy in programming languages that allows the computation of expressions to be deferred until their results are actually needed. This strategy enables more efficient resource usage during program execution, especially when dealing with large data structures or potentially infinite data sets. By leveraging lazy evaluation, we can avoid unnecessary computation, reduce memory usage, and write code in a more declarative manner, making program logic clearer. Lazy evaluation is typically implemented by encapsulating expressions into thunks or Promise-like objects, which contain sufficient information to perform the computation when needed. This on-demand characteristic is an important tool in functional programming languages, enabling developers to express complex algorithms and data flows in a more abstract and concise way.

=============Zhipu Qingyan AI creation completed=================

Many languages strongly influenced by FP provide syntax features that support lazy evaluation. For example, Kotlin’s lazy syntax:

```kotlin
// Sample code generated by Zhipu Qingyan AI
fun main() {
    // Use the lazy keyword to create a lazy property
    val lazyValue: String by lazy {
        println("Computing the value...")
        "Hello, Lazy World!"
    }

    // On first access, the lazy property is computed
    println(lazyValue)

    // Subsequent accesses return the cached result without recomputation
    println(lazyValue)
}
```

Scala’s built-in Stream is also lazily evaluated:

```scala
// Sample code generated by Wenxin Yiyan AI
object Fibonacci {  
  // Define a lazily evaluated Fibonacci sequence using Stream  
  def fibonacci: Stream[BigInt] = {  
    def loop(a: BigInt, b: BigInt): Stream[BigInt] = a #:: loop(b, a + b)  
    loop(BigInt(0), BigInt(1))  
  }  

  def main(args: Array[String]): Unit = {  
    // Print the first 10 numbers of the Fibonacci sequence  
    fibonacci.take(10).foreach(println)  

    // Note: No infinite loop or memory overflow occurs here because Stream is lazy  
    // and we limit the number of elements via the take operation  
  }  
}
```

In this example, the fibonacci method uses an internally defined loop function to recursively generate the Fibonacci sequence. The #:: operator prepends the current element (a) to the stream produced by loop(b, a + b), forming a new stream. Because Stream is lazy, the recursive call only occurs when the next Fibonacci number is requested.

In main, we call fibonacci.take(10) to obtain the first 10 elements and use foreach to iterate and print them. Due to Stream’s laziness, these elements are calculated only when actually needed.

Java does not have built-in syntax for lazy evaluation, but we can simulate it with functions:

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
        // Create a lazily evaluated object that computes its value on first get()
        LazyValue<Integer> lazyValue = new LazyValue<>(() -> {
            System.out.println("Computing value...");
            return 42; // Example computation: return a fixed value
        });

        // The first get() performs the computation
        System.out.println("Value: " + lazyValue.get());
        // Subsequent get() calls reuse the previously computed result
        System.out.println("Value: " + lazyValue.get());
    }
}

// Output:
// Computing value...
// Value: 42
// Value: 42
```

The essence of lazy evaluation is to replace a value with a computation function, not evaluate immediately, and only execute the calculation process when the value is actually needed—generally caching the result so subsequent retrievals don’t recompute.

The problem with Eager Evaluation is that when we reference a value by a variable name, the value must have already been computed. This causes many unnecessary causal dependencies.

```javascript
function f(a,b){
    const x = h(b);
    return g(a, x);
}
```

In the example above, before calling f, the value of parameter a must be computed; we can only pass f a computed result. Although a’s value is not actually used inside f (it is just forwarded to another function), f’s computational process is forced into a dependency with a’s computation: f cannot execute in parallel with a; it must execute after a completes. Only code with causal dependency needs to be executed in order—cause first, effect afterward. However, in mainstream imperative languages, the order of function calls (expressions) in code typically implies the actual execution order.

In practice, many frameworks and design patterns help us separate the logical expression order from the actual execution order. We can express logic in an easy-to-understand order, or even out of order. The key point is that earlier expressions do not necessarily execute first; execution order is determined only when actual causal dependencies arise. A concrete application is the aggregate root in DDD (Domain-Driven Design).

```java
IAccountBo accountBo = accountManager.getAccount(accountId);
ICustomerBo customerBo = accountBo.getCustomerBo();
```

In DDD development, it is common that upon entering a backend service function, we first obtain an aggregate root object based on business parameters—for example, the account object accountBo. This aggregate root provides many business data accessor methods; for example, to obtain the customer object associated with the account, we use accountBo.getCustomerBo(). You could say that the aggregate root contains all account-related business data. Once the aggregate root is obtained, we no longer need to access the database in subsequent business code; using only the aggregate root object is sufficient, because all information can be accessed via the aggregate root’s properties!

A typical implementation of getCustomerBo is lazy loading,

```javascript
ICustomerBo getCustomerBo(){
    if(customerBo == null)
       customerBo = accountManagerImpl.getCustomerBo(
           account.getCustomerId(), dataCache);
    return customerBo;
}
```

The JavaBean property expression corresponding to accountBo.getCustomerBo() is accountBo.customerBo. Clearly, at the property-expression level, lazy loading is a form of lazy evaluation.

The aggregate root expresses the so-called domain model (i.e., data entity relationships) in the business domain, but this expression is not directly related to the specific data-fetching execution process. Only when a specific aggregate-root property is actually needed should the loading occur.

Because most companies in China use relatively weak ORM engines like MyBatis, implementing DDD can face potential difficulties. Code commonly written to obtain aggregate roots often fetches lots of redundant data!

```
AccountDTO accountDTO = accountManager.getAccount(accountId);
CustomerDTO customerDTO = accountDTO.getCustomer();
```

In much of the code, the data objects we obtain are simple POJO/DTO objects without any dynamic behavior. When fetching AccountDTO by accountId, if applying the aggregate root concept, developers often directly load the associated customer information and set it onto the accountDTO’s property—even if the external caller does not actually use customer information. This approach essentially writes traditional imperative scripts in an OO language without fully exploiting OO encapsulation. If DDD is implemented this way, to avoid performance issues due to duplicate data fetching, we must constrain the order in which functions are expressed and perhaps introduce additional contextual environments to store temporary cache data—all of which introduces unnecessary complexity. For example, although logically steps A and B have no dependencies and can execute in any order, to reuse already loaded data we might force step A to execute before step B; otherwise step B would lack some parameter or contextual data.

For a further introduction to declarative vs. imperative, see [What Is Declarative Programming](https://zhuanlan.zhihu.com/p/85491177).
<!-- SOURCE_MD5:ea3c88870f36f79b95e9799c3618bd3b-->
