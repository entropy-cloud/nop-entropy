# From Reversible Computation to Declarative Programming

Reversible computation is a theory introduced by the author for the next-generation software construction. Its core idea can be represented with a general construction formula.

```
App = Δ x-extends Generator<DSL>
```

In this formula, Domain-Specific Language (DSL) holds the central position. The primary strategy of reversible computation in practice is to decompose business logic into multiple business aspects and design a specific DSL for each aspect. DSL serves as a typical example of declarative programming, making reversible computation considered as one of its implementation paths. Through the concept of reversible computation, we gain new insights into declarative programming.

[Reversible Computation](https://zhuanlan.zhihu.com/p/64004026)

## Section 1: Virtualization

DSL is declarative because its expressed content is not directly executable by a physical machine but must be interpreted through an interpreter/compiler. However, from another perspective, this interpreter can be seen as a virtual machine, although it **does not necessarily follow the von Neumann architecture**. The key point lies in the lower level: the interpreter only needs to support a limited number of fixed domain-specific primitives (original language elements) for specific domains. This allows a DSL-written program to execute the domain-specific logic effectively. In general programming structures, business logic is expressed once, but in DSL-based programs, it is split into two layers. The lower layer deals with domain structure and general logic, while the upper layer adapts to specific business scenarios.

In modern software architecture, efforts to build an internal virtual machine are evident. For instance, [Slate](https://github.com/ianstormtaylor/slate), a highly customizable framework, claims to have a "Schema-less core". This means its core does not know the specifics of what it is editing; instead, these details are defined by schemas. Schemas determine which nodes are allowed, what attributes they need, and their format.

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
};
```

The renderNode function acts like an interpreter:

```
function renderNode(props, editor, next) {
  const { node, attributes, children } = props;
  switch (node.type) {
    case 'paragraph':
      return <p {...attributes}>{children}</p>;
    case 'quote':
      return <blockquote {...attributes}>{children}</blockquote>;
    case 'image': {
      const src = node.data.get('src');
      return <img {...attributes} src={src} />;
    }
    default:
      return next();
  }
}
```

Traditional rich text editors require the core to understand concepts like bold/italic, but Slate's core doesn't need to. Instead, it manipulates technical elements without worrying about specific business meanings. Using the same core, we can configure it to support various editors like Markdown or HTML through similar configurations.

## Section 2: Syntax-Directed

To implement virtualization, the simplest approach is a one-to-one mapping mechanism. This involves attaching a set of actions directly to each syntax rule of the DSL. When processing a specific syntax node, the corresponding action is executed. This process is known as syntax-directed programming (syntax-directed).

Based on XML or similar XML syntax template technology, such as Ant scripts and FreeMarker templates, can be considered examples of syntax translation. For instance, in Vue's template syntax:

```
<template>
  <BaseButton @click="search">
    <BaseIcon name="search"/>
  </BaseButton>
</template>
```

The `template` is equivalent to directly displaying the Abstract Syntax Tree (AST) in XML format. When processing component nodes, it locates the corresponding component definition based on the tag name and recursively processes it. The mapping process is context-independent, meaning it does not depend on the surrounding context of the node. The same tag name will always map to the same component.

The same approach lies at the core of Facebook's GraphQL technology. It uses syntax translation to send data access requests to a delayed processing queue for execution. This allows for batch optimization by merging queries.

For example, handling the following GraphQL query:

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

The backend only needs to specify data type mappings for each data type, such as `dataLoader`.

Here's an example of how to define types and resolvers:

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
    async followingUsers(user, args, { dataloaders }) {
      return dataloaders.users.loadMany(user.followingUserIds)
    }
  }
};
```

To implement syntax translation in modern programming languages, the default solution is to use annotation-based meta-programming. For example, Python's function annotations:

```python
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


In traditional programming, a piece of code has only one fixed meaning. Once the programmer translates their ideas into code, the form and content are set. However, reversible computing suggests that logic should be bidirectional. By reversing the flow from code to its original idea, we enable declarative programming, separating expression from execution.


## Example: Filter Conditions

<and>
  <eq name="status" value="1" />
  <gt name="amount" value="3" />
</and>

In the front end, this corresponds to a query form. In the back end, it maps to a Predicate interface implementation that sends the conditions to the database as part of the query. This approach avoids manual coding and simply represents the same information in multiple ways.


## Example: Parallel Processing

```rust

pub fn hello_macro(input: TokenStream) -> TokenStream {
    let ast = syn::parse(input).unwrap();
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


struct Pancakes;
```

In traditional programming, adding parallelism requires manual code changes. However, using OpenMP in Intel's compiler allows us to add directives like `#pragma omp parallel` to convert sequential code into parallel code without rewriting it entirely.



<and>
  <eq name="status" value="1" />
  <gt name="amount" value="3" />
</and>

In the front end, this corresponds to a query form. In the back end, it maps to a Predicate interface implementation that sends the conditions to the database as part of the query. This approach avoids manual coding and simply represents the same information in multiple ways.



In deep learning, tools like PyTorch and TensorFlow allow us to compile Python functions into GPU-accelerated code. Using TVM (Tensor Virtual Machine), we can even compile certain operations directly to FPGA hardware. This demonstrates that multiple interpretations of the same code can coexist, enabling flexibility in implementation.



Reversible computing introduces a new perspective where the flow of information is reversible. In this model, not only does data flow from front-end forms to back-end queries, but it can also be reversed to inspect the reasoning behind decisions. This concept extends to syntax trees (ASTs), where we can derive both the abstract representation and its transformations.

In Antlr4, it is no longer recommended to embed actions directly within the grammar definition. Instead, use Listener or Visitor patterns to handle actions. This allows leveraging the object-oriented language's inheritance mechanism for incremental updates.


## 2. Simple.g4语法定义
```
// Simple.g4
grammar Simple;

expr  : left=expr op=('*'|'/') right=expr #opExpr
      | left=expr op=('+'|'-') right=expr #opExpr
      | '(' expr ')'                      #parenExpr
      | atom=INT                          #atomExpr
      ;

INT : [0-9]+ ;
```


## 3. 生成的Visitor类
```
// Generated Visitor
public class SimpleBaseVisitor<T> extends AbstractParseTreeVisitor<T> implements SimpleVisitor<T> {
    @Override
    public T visitOpExpr(SimpleParser.OpExprContext ctx) { return visitChildren(ctx); }
    @Override
    public T visitAtomExpr(SimpleParser.AtomExprContext ctx) { return visitChildren(ctx); }
    @Override
    public T visitParenExpr(SimpleParser.ParenExprContext ctx) { return visitChildren(ctx); }
}
```


```
class MyVisitor<Double> extends SimpleBaseVisitor<Double> {
    ...
}
```


If we say that the ideal of declarative programming is to have machines automatically find solutions based on problem descriptions, then from where should we look for a class of problems that is both sufficiently general and solvable by machines? Fortunate it is that Newton's descent brings order to chaos; innovative it is that we've accumulated several such inherited problems, one of which is automatic differentiation.

By specifying the differential expressions of basis functions, we can automatically compute the derivatives of numerous composite functions. This capability is essential for all modern deep learning frameworks. The reversible computation theory indicates that automatic computation of differences can be extended beyond mathematical or algorithmic domains, becoming a powerful software construction mechanism.

For example, with k8s, this container orchestration engine's core idea revolves around using declarative APIs to define the system's "ideal" state. By continuously monitoring the current state against the ideal state, discrepancies are detected and corresponding actions are automatically triggered to correct these differences. The engine's core logic can be summarized as follows:

$$
action = Translator( PhysicalState - Planner(LogicalSpec) )
$$

The design principle adopted by k8s can be referred to as state-driven (State Driven). It focuses on the system's state and the differences between states, rather than traditional action-based APIs or event listening. The transition from Action to State resembles a paradigmatic shift in physics, transitioning from the force's perspective to that of potential functions based on fields.

From state A to state B, regardless of the path taken, the final result is the same. This path independence inherent in forces (and thus in potential functions) simplifies our understanding of systems. In k8s, for any given state deviation, the engine can automatically derive the necessary actions through gradient computations based on the potential function φ.

$$
F = - \nabla \phi
$$

From state A to state B, multiple feasible paths exist. Among these, the optimal path is determined by cost or benefit considerations. This optimization reflects the transition from action to state, representing a shift in the overall cognitive framework to a more holistic, integrated approach.

The transformation from action to state represents a paradigm shift in thinking, requiring us to adopt a new worldview to address problems and adapt corresponding technologies. This evolution continues to drive advancements across various domains, fostering innovation through systematic, comprehensive understanding.

The concept of force implies a complete recognition of the state space, where each attainable state is defined by valid means. Sometimes, for specific applications, this comprehensiveness may be overly demanding. For instance, in certain contexts, it might suffice to identify a particular path from state A to state B without exploring the entire state space.

In such cases, traditional procedural approaches would suffice, as they focus on specific action sequences rather than comprehensive system understanding. However, for broader, more complex systems, declarative programming's ability to automate problem-solving through state transitions becomes invaluable.


In everyday programming, the creation of new logical constructs is rare. Most of what we do is essentially mapping logic relationships. For example, when dealing with log collection, we often use tools like `logstash` to parse logs into JSON format for analysis in Elasticsearch.

When printing logs, maintaining their structure is crucial. This means we can avoid using intermediate tools like `logstash`. If we need to filter or process attributes further, we can directly map them using a generic object mapping service (often via a visualization interface) rather than writing custom log handlers for each specific log processing tool.

Here’s an example of how to keep the log structure intact while printing:

```
// Keep the object structure when logging
LOG.info(logCode, { parameterName:ParameterValue });
```



The primary reason programmers write code is due to information loss that occurs when crossing boundaries. For instance, when logs are printed as plain text, valuable structural information about the object is lost. Recovering this information requires human intuition, making it difficult to automate and leading to ambiguities during parsing.

Programmers bring domain knowledge, including background understanding, conventions, and overall architectural concepts. This expertise allows them to translate seemingly equivalent logical operations into code that may not be feasible to automate due to structural nuances.



In mathematics, equivalence is based on same structure. For example:

```
A ≈ B
A + Person = B + Person
```

This illustrates how mathematical concepts can guide software development practices, such as object mapping and data transformation.



Modern software development often requires reverse engineering to understand existing systems. This process involves analyzing components, understanding their relationships, and ensuring they align with project requirements.

For example:

```
A ≈ B
A + A' ≡ B + B'
```

This equation shows how two systems can be compared for equivalence through a mapping between their components.



In data visualization, abstracting data into meaningful representations is crucial. Tools like `DSL` (Data Source Language) allow for direct generation of visualizations without manual coding.

For example:

```
Visualization Interface = DSL
DSL = Data Extraction (Visualization Interface)
DSL = Interface Generator^{-1} * Visualization Interface
```



A significant challenge in modern software development is achieving reversibility. While mathematics provides abstract models, translating these into executable code remains complex. This complexity increases as systems grow, making it difficult to automate processes that require human insight for nuanced decisions.



Metadata plays a vital role in data representation. It captures information about data, enabling effective management and utilization. For example:

```
Data = Body, Headers
Metadata = { Data: [Body, Headers] }
```

This structure ensures comprehensive data handling without losing context, which is essential for accurate analysis.



Context is crucial in determining the meaning of data. Without it, even highly structured information can become meaningless. For instance:

```
World as a whole
Data = Body, Headers
```

Without understanding the broader context, the significance of `Body` and `Headers` may be lost.



While modern tools and systems aim to simplify log handling and data processing, the human element remains irreplaceable in translating abstract concepts into functional code. This balance between automation and manual intervention is key to effective software development.

