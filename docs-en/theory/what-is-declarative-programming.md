# What is Declarative Programming

## I. Declarative vs. Imperative

What is declarative programming? Generally, our understanding of declarative is relative to imperative. Turing taught us the essence of imperative and endowed it with a mathematically precise definition: a stateful machine that executes step by step according to explicit instructions. Declarative, on the other hand, can be seen as the opposite of imperative. Some have said: everything that is not imperative is declarative. In this sense, the further one deviates from the image of a Turing machine, the more declarative it is.

Therefore, Functional Programming is declarative because it does not use mutable state and does not require specifying any execution order (it can be assumed that all functions execute simultaneously due to referential transparency; parameters and variables are merely aliases for a set of symbols). Logical Programming is also declarative because we only need to describe the problem we need to solve through facts and rules, and the specific solution path is automatically determined by the compiler and program runtime.

If imperative corresponds to steps executable by a specific physical machine, then declarative can be seen as corresponding to expressions at a higher level of abstraction. This leads to an intriguing understanding: imperative is about "how to do," while declarative is about "what to do." Under this understanding, the SQL language, as a domain-specific language (DSL), is declarative. The SQL language describes the logical data processing results we wish to obtain, and the execution engine translates the SQL language into a physical execution plan.

Referencing the formula proposed by Kowalski: algorithm = logic + control, logic (the knowledge needed to solve a problem) can be expressed independently of the control flow during specific execution (the specific problem-solving strategy using knowledge). In DSLs, what is important is expressing all the necessary information; the order of expression is often unimportant and has no necessary relationship with the processing order during specific execution. For example, the order of table joins in SQL statements and the order of filter conditions do not affect the execution results in principle.

```sql
select *
   from a,b,c
   where a.x = b.x and c.x = a.x
```

is logically equivalent to

```sql
select *
   from c,b,a
   where c.x = a.x and a.x = b.x
```

Note that writing select before the from part does not mean the select part executes first; in fact, the order here is merely conventional. In C#'s LINQ syntax, we write it like this:

```csharp
from x in array
  where x % 2 == 1
  orderby x descending
  select x * x;
```

## II. Declarative Programming: Separation of Expression and Execution

If imperative programming is seen as a "faithful" expression (what is expressed is executed, and what is expressed is exactly what is to be executed), then declarative programming is a rather "unfaithful" expression.

### Expression Without Execution, or Even Inability to Execute

For example:

```javascript
list = range(0, Infinity); // Get an array composed of all integers from 0 to Infinity
list.take(5)            // Take the first 5 records of the array
```

Lazy evaluation is a common feature in declarative programming, which greatly enhances the flexibility of logical organization structures. For example, in the WebMVC architecture:

```java
// In action
entity = dao.getEntity(id)

// In view
entity.mainTable
entity.subItems
```

The lazy loading feature based on ORM can balance both the convenience of expression and the high performance of on-demand access. In the action layer, one can directly express the retrieval of relevant data without actually reading all the data into memory. Data is read only when actually used, through the lazy loading mechanism.

### Expressing Not Only the Present but Also the Future

The Promise object, standard in modern programming languages, represents a value that can be obtained in the future. When we haven't actually obtained this value yet, we can return it as a return value and pass it around as a parameter in the program.

In traditional imperative programming concepts, a function's return indicates completion of execution. If it is asynchronous execution, notification can only be obtained through callback functions. At the conceptual level, we cannot directly define and use "future values."

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

### Expressing Not Only What One Has but Also What One Does Not Have

Future values, although not yet present, are at least expected. But what if it is fundamentally unknown whether the future will come? Can we assign a form of expression to it?

In the Groovy language, a mechanism similar to Ruby's methodMissing is provided:

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
foo.x;
foo.f();
```

## III. Declarative & Imperative

Traditionally, mainstream programming languages have been偏向 imperative because our hardware operating environments are enhanced versions of the Turing machine, and the function of software is to guide the hardware to perform pre-defined actions. There is even a saying that the essence of program development work is imperative, after all, finding specific technical implementation solutions (how) for the boss's small goals (what) is our primary job. However, if we carefully observe the daily routine of modern programming, we will find that the vast majority of programming work is based on declarative APIs.

For example, to display an interface, we concatenate HTML text through string operations; to call a service, we concatenate URLs and JSON text; to access a database, we concatenate SQL requests. Few people clearly understand how to use imperative instructions to construct a complete application step by step. Even for the simplest front-end drag-and-drop actions, what most of us know is probably just calling a Draggable component and declaring a trigger action when the drag ends. In this sense, the programming skills we master are merely conversion strategies from one declarative view to another declarative view.

The entire software development ecosystem is continuously developing towards a direction where declarative and imperative are seamlessly integrated. In the past, to highlight the declarative part, we would choose template languages, that is, embedding a small amount of imperative control logic within descriptive content. Today, technologies like JSX have emerged, which directly embed descriptive content into imperative contexts. Going further, technologies like SwiftUI, which directly implement declarative expressions based on general-purpose programming languages, are rapidly approaching us:

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

## IV. Declarative Programming from the Perspective of Reversible Computation

How can we further advance declarative programming? If we use the What & How metaphor, the direction lies in how to define What through systematic solutions and automatically derive How. Reversible computation provides a complete set of technical routes for implementing DSLs and also brings some new inspiration for realizing declarative programming.

The space here is too small to write it all. Stay tuned for the next part.

The low-code platform NopPlatform, designed based on reversible computation theory, is open source:

- gitee: https://gitee.com/canonical-entropy/nop-entropy
- github: https://github.com/entropy-cloud/nop-entropy