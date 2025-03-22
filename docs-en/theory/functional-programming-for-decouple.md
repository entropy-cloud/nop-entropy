# Why Functional Programming Helps in Decoupling

During recent interviews, I noticed that many candidates confuse functional programming concepts like Lambda expressions and `flatMap/map/join` collection handling functions. The question of why functional programming is useful often remains vague. In this article, I will briefly introduce the concept of functional programming and its application in our daily programming scenarios. I will also explain how functional programming can help achieve logic decoupling and where it adds value compared to object-oriented programming.


## What is Functional Programming?

Functional programming (FP) is a paradigm that emphasizes building complex computation as a series of function calls. Functional programming is rooted in mathematical functions and has the following characteristics:

1. **Pure Functions (Pure Functions)**:
   - The output depends solely on the input parameters.
   - These functions do not have side effects, meaning they do not modify external state.

2. **Immutability (Immutability)**:
   - Once data is created, it cannot be modified.
   - If changes are needed, a new copy of the data must be created.

3. **Higher-Order Functions (Higher-Order Functions)**:
   - Functions can be passed as arguments to other functions or used as return values.

4. **Function Composition (Function Composition)**:
   - Multiple functions are combined into a single new function to perform more complex operations.

5. **Recursion (Recursion)**:
   - Problems are solved by calling the same function again with different parameters, rather than using loop control structures.

6. **Lazy Evaluation (Lazy Evaluation)**:
   - Expressions are evaluated only when their results are actually needed.
   - This can improve efficiency by delaying computations until they are necessary.

# Reduce Shared Data Modifications

## Imutability is Key to Reference Transparency

In functional programming, **immutability** plays a crucial role in achieving **reference transparency**, which is one of the fundamental principles. The relationship between immutability and reference transparency can be explained as follows:

1. **Immutability is the Foundation of Reference Transparency**:
   - For a reference to be transparent, the data it references must be immutable. If the data were mutable, the same reference could point to different values over time, violating the principle of reference transparency.

2. **Reference Transparency Promotes Imutability**:
   - When aiming for reference transparency, one naturally gravitates towards using immutable data. This ensures that a reference can always access the same value without risking changes in the program's behavior.

3. **Shared Goal**:
   - Both immutability and reference transparency aim to eliminate side effects. Immutability ensures that data remains unchanged after creation, while reference transparency ensures that the result of a reference is stable across all points in the program.

4. **Predictability**:
   - Both principles contribute to making code more predictable. Immutability ensures that once data is created, it cannot be altered, and reference transparency ensures that the outcome of a reference is consistent.

5. **Functionality of Functional Programming**:
   - In functional programming, immutability and reference transparency are key enablers of pure functions. Pure functions produce the same output for the same input and have no side effects. This principle is central to functional programming's value proposition.

### Conclusion
- **Immutability and Reference Transparency Are Interdependent**: They together form the foundation of many functional programming benefits, including readability, maintainability, testability, and reusability. By adhering to these principles in your code, you can build more robust and efficient software systems.

---

Although immutability is highly beneficial, it's not often heavily emphasized in mainstream languages like Java due to performance considerations. However, a practical approach is to minimize mutation operations, especially concurrency-sensitive ones. A common practice is to use immutable objects in shared data structures while allowing local modifications through copies. This avoids the need for synchronization mechanisms and volatile keywords.

### Example Code Snippet

```javascript
MyClass obj = gObjects.get(objName);
MyClass copy = obj.clone(); // or new MyClass()
copy.setXX(x);
gObjects.put(objName, copy);
```

This approach mirrors transactional behavior found in databases using MVCC (Multi-Version Concurrency Control), where each object can exist in multiple versions. Developers can safely access previous states during modifications, ensuring data consistency without impacting business logic.

---

# Translate Chinese Technical Documentation to English

The following is a translation of the provided Chinese technical documentation into English, retaining the original Markdown format including headers, lists, and code blocks. Technical terms and code snippets have been accurately translated while maintaining their structure and indentation.

---

## Traditional Approach to Building Immutable Data

```javascript
// Traditional approach to creating immutable data
const todoReducer = (state = initialState, action) => {
  switch(action.type) {
    case ADD_TODO:
      // Use spread operator to create a new array copy
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
      return state;
  }
};
```

## Using Immer Simplified Approach

```javascript
// Using Immer's simplify approach
const todoReducer = (state = initialState, action) => {
  switch(action.type) {
    case ADD_TODO:
      // Use Immer's produce function to update the state
      return produce(state, (draft) => {
        draft.todos.push({
          id: Date.now(),
          text: action.payload,
          completed: false
        });
      });
    default:
      return state;
  }
};
```

---

## The Role of Immer's `produce` Function

**The `produce` function** by Immer initiates a transaction. Inside the `produce` function, you can modify the temporary `draft` object. Immer uses proxy technology similar to AOP (Aspect-Oriented Programming) to record all modification actions. When exiting the `produce` function, it constructs a new state object based on all recorded modifications.

**Without using Immer**, immutable data means creating a complete copy of the state every time. **Immer's purpose** is to record modification actions as deltas (changes), which are then applied to the initial state to produce a new state. 

---

## The Fundamental Impact of Immutability

The most essential aspect of immutability is that we can distinguish between the evolution details of the state space, which is the essence of predictability. If modifications are allowed, the same object's state will be overwritten unless we record copies or deltas at each modification step. Without immutability, we lose historical state information.

**Immutable means** we do not overwrite existing information. By recording pointers to the state at a given time, we can access historical details without creating new copies. Each overwrite introduces ambiguity. For example, if an error occurs mid-process and we need to roll back to a consistent state, how can we recover the original consistency if extensive modifications have been made?

---

## State Management in Multithreaded Environments

In a **multithreaded environment**, sharing the same object without immutability necessitates locking. **Locking** ensures exclusive access and blocks other threads from accessing the object while one is modifying it. Without immutability, multiple threads can interfere with each other, leading to blocking.

If we consider a thread as a timeline, lack of immutability causes the timeline to become entangled, with threads blocking each other. In a **globally shared object** with immutability, each timeline (thread) evolves independently, achieving decoupling. However, this also means we might observe multiple historical snapshots of the same object in parallel universes.

---

## Using Higher-Order Functions Instead of Inheritance

In object-oriented programming, one common misuse is the overuse of inheritance to achieve reuse. For example, using the **Template Method Pattern (TMP)**, a base class defines an algorithmic skeleton with a method that calls several abstract methods. Derived classes implement these abstract methods with specific logic, allowing the base class's method to be reused across different implementations.

### Example Base Class
```javascript
// Abstract class
abstract class AbstractProcess {
  // Template method defining the algorithmic framework
  public final void process() {
    this.primitiveOperation1();
    this.primitiveOperation2();
  }

  // Abstract methods to be implemented by subclasses
  protected abstract void primitiveOperation1();
  protected abstract void primitiveOperation2();
}
```

### Limitations of TMP
**The essence of the Template Method Pattern** is that it uses an implicit `this` pointer to access methods. This means that each combination of abstract methods requires a new subclass, binding specific logic to the template method. As a result, the pattern cannot be flexibly applied to scenarios outside its intended use.

In most cases, using **callback functions** and **callback interfaces** is more flexible than TMP for achieving reuse. For example:
- The `map` function in JavaScript can dynamically apply transformations without requiring a new class for each transformation.
- Callback interfaces allow multiple independent components to collaborate by passing callback functions between them, enabling dynamic behavior.

---

# Template Method and Function Composition

## 1. Template Method
The **Template Method** is a design pattern that allows defining the skeleton of an algorithm in one class, with the details implemented by child classes. This technique provides a flexible way to extend functionality without altering the base class.

### Example:
```java
class ConcreteProcess {
    public void process(IProcessor processor) {
        processor.primitiveOperation1();
        processor.primitiveOperation2();
    }
}
```

The **Template Method** is akin to using a fixed `this` parameter to retrieve function pointers. In contrast, directly passing function parameters to callback interfaces offers greater flexibility, such as decomposing multiple callback interfaces into separate ones for cross-composition.

## 2. Function Composition
Function composition refers to the process of combining two or more functions into a single function. If we have two functions `f: A → B` and `g: B → C`, their composition `g ∘ f` is a function from `A` to `C`, defined as `(g ∘ f)(x) = g(f(x))` for all `x ∈ A`.

### Example:
- Let `f(x) = x + 2`
- Let `g(x) = 2x`

Then, their composition `g ∘ f` is:
```math
(g ∘ f)(x) = g(f(x)) = g(x + 2) = 2(x + 2) = 2x + 4
```

The utility of function composition stems from the **associative property** of functions. This means that if three functions `f`, `g`, and `h` can be composed, their composition satisfies:
```math
(h ∘ (g ∘ f)) = ((h ∘ g) ∘ f)
```
This implies that the order in which functions are composed does not affect the final result.

### Example of Associativity:
- Let `f(x) = x + 2`
- Let `g(x) = 2x`
- Let `h(x) = x * 10`

Then:
```math
(h ∘ (g ∘ f))(x) = h(g(f(x))) = h(2(x + 2)) = 10 * (2(x + 2)) = 20x + 40
```
and
```math
((h ∘ g) ∘ f)(x) = (h ∘ g)(f(x)) = h(g(x + 2)) = h(2(x + 2)) = 10 * (2(x + 2)) = 20x + 40
```

Both compositions yield the same result, demonstrating the associative property.

## 3. The Value of Function Composition
The significance of function composition lies in its **associativity**. This property allows us to combine functions locally based on their context, making them abstract and reusable across different contexts. Without associativity, we would need to know the exact application context and target type of each function, limiting their independent extraction.

### Example:
- Let `f(x) = x + 2`
- Let `g(x) = 2x`

Then:
```math
(f ∘ g)(x) = f(g(x)) = f(2x) = 2x + 2
```
and
```math
(g ∘ f)(x) = g(f(x)) = g(x + 2) = 2(x + 2) = 2x + 4
```

Here, `f ∘ g` and `g ∘ f` produce different results, illustrating the importance of function order in composition.

## 4. The Associative Property
The **associative property** is a fundamental characteristic of functions that allows for their local combination without knowledge of their broader context. If three functions `f`, `g`, and `h` can be composed, their composition satisfies:
```math
(h ∘ (g ∘ f)) = ((h ∘ g) ∘ f)
```
This property is crucial as it enables the creation of domain-specific compositions while maintaining a consistent interface.

### Example:
```javascript
// Example: Function composition in JavaScript
const compose = (f, g) => x => f(g(x));
```

Using this approach, functions can be dynamically composed without altering their signatures, enabling flexible and reusable code.

## 5. Further Reading
For a deeper understanding of function composition and its applications, consider exploring the difference between `git` and `docker`, as well as how these tools leverage composition to provide value in specific contexts.

### Note:
- **Any** function that satisfies the associative property can be used independently across different contexts.
- The concept of reversibility is not dependent on function associativity but rather on their specific definitions and inverses.

Satisfies the associativity property, meaning we can freely add parentheses throughout the computation. The parts within the parentheses can be independently computed from those outside.

In the world of functional programming, there is a concept that may seem rather mysterious to beginners: Monad (Monads). Many functional programming enthusiasts consider Monad to be the core of functional programming, a noble and prestigious subject that imparts a certain abstract power. However, from a practical perspective, **Monad is simply a unique design pattern within functional programming, representing a set of functions of the form `a -> m b` that satisfy the associativity property**. For a detailed explanation of Monad, please refer to my article on Zhihu: [Writing Monad for Dummies](https://zhuanlan.zhihu.com/p/65449477).

## Five. Decoupling Traversal Logic and Calculation Logic

If you carefully examine the code we write day in and day out, you will notice that it often consists of two tightly coupled parts: traversal logic and calculation logic. On one side, we are searching and iterating over data that needs to be processed; on the other, we are performing specific calculations.

### Example Code (JavaScript)

```javascript
Map<String,Integer> wordCount(File file) {
    Map<String, Integer> wordCount = new HashMap<>();

    String text = readText(file);
    // Coupling between traversal logic and calculation logic in a loop
    String[] words = text.split("\\s+");
    for (String word : words) {
        // Traversal logic: Iterate over each word
        // Calculation logic: Update the count of each word
        wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
    }
    return wordCount;
}
```

In this example, both traversal logic (splitting text into words and iterating) and calculation logic (updating counts) are tightly coupled within a single loop.

Functional programming leverages higher-order functions to effectively decouple these two aspects.

### Improved Code (JavaScript)

```javascript
void forEachWord(File file, Consumer<String> consumer) {
    String text = readText(file);
    Arrays.stream(text.split("\\s+")).forEach(consumer);
}

Map<String, Integer> wordCount(File file) {
    Map<String, Integer> wordCount = new HashMap<>();
    Consumer<String> countConsumer = 
        word -> wordCount.merge(word, 1, Integer::sum);
    
    forEachWord(file, countConsumer);
    return wordCount;
}
```

Here, we have refactored the code into two separate functions:
1. `forEachWord`: Handles the traversal logic.
2. `wordCount`: Manages the calculation logic.

This separation allows us to reuse `forEachWord` with different consumers without modifying its implementation. For instance, if we want to count only uppercase words, we can simply pass a new consumer:

```javascript
Map<String, Integer> wordCount(File file) {
    Map<String, Integer> wordCount = new HashMap<>();
    Consumer<String> upperConsumer = 
        word -> word.startsWithIgnoreCase(word.charAt(0)) ? 
            wordCount.merge(word, 1, Integer::sum) : 0;
    
    forEachWord(file, upperConsumer);
    return wordCount;
}
```

The same consumer can be reused for other traversal functions. For example, to count words in a specific directory, we could create a `forEachWordInDir` function and pass it as needed.

### Y Combinator: Recursive Structure and Calculation Structure Decoupling

In functional programming, another subtle form of decoupling is achieved through the Y Combinator (Y). This allows for the recursive structure and calculation structure to be separated in a particularly elegant way. In general programming languages, recursion is straightforward but requires function names for self-reference.

### Example Code (JavaScript)

```javascript
function fact(n) {
    return n < 2 ? 1 : n * fact(n - 1);
}
```

The Y Combinator can transform an ordinary recursive function like the one above into a form where the recursion is fully encapsulated, making it more versatile and reusable.

The Y Combinator itself looks something like this:

```javascript
function y(f) {
    return function(g) {
        return function(h) {
            return h(f(g(h)));
        };
    };
}
```

This allows `f` to be any function that takes two arguments, effectively turning it into a form where the recursion is abstracted away. The Y Combinator provides a elegant way to separate the recursive structure from the actual computation.

