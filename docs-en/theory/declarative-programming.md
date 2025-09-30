# Declarative Programming Through the Lens of Reversible Computation

Reversible Computation is a next-generation theory of software construction proposed by the author. Its core idea can be expressed by a general formula for software construction

```
App = Delta x-extends Generaor<DSL>
```

In this formula, the domain-specific language (DSL) occupies a central position, and the main strategy of Reversible Computation in practice is to decompose business logic into multiple business aspects and design a DSL for each aspect to describe it. A DSL is a typical example of declarative programming, so Reversible Computation can be regarded as an approach to implementing declarative programming. Through the concept of Reversible Computation, we can gain new insights into declarative programming.

[Reversible Computation](https://zhuanlan.zhihu.com/p/64004026)

## I. Virtualization

DSLs are declarative because what they express cannot be directly executed by a physical machine; they must be translated through some interpreter/compiler. However, from another perspective, this interpreter can also be seen as a virtual machine—just not necessarily based on the von Neumann architecture. The key point here is that the underlying interpreter only needs to support a small set of fixed primitives for a specific domain to execute programs written in the DSL, thereby realizing different business logic. In a typical program structure, business logic is expressed once; in DSL-based programs, logic is expressed in two stages. The bottom layer is relatively general logic tied to domain structure and independent of specific business, while the upper layer is variable logic bound to specific business scenarios.

In relatively modern large-scale software architecture design, there is often an effort to construct some kind of internal virtual machine. Take the [Slate rich-text editor framework](https://github.com/ianstormtaylor/slate) as an example: it bills itself as a “fully customizable” framework with a “schema-less core” at its heart. That is, Slate’s core does not directly know the concrete structure of the data it edits; these structures are communicated to the core via a schema. The schema defines which nodes are allowed, what properties nodes have, and what formats properties must satisfy.

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

The custom render function is akin to an interpreter

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

Traditional rich-text editors must explicitly know concepts like bold/italic in the core, whereas Slate’s core crucially does not need to know specific business semantics to manipulate the corresponding technical elements—similar to how hardware instructions do not need to know software-level business information. By using the same core, we can configure and implement a Markdown editor, HTML editor, and many other purpose-specific editors.

## II. Syntax-Directed

The simplest way to achieve virtualization is to adopt a one-to-one mapping mechanism, i.e., directly attaching a set of actions to each syntactic rule of the DSL and executing the corresponding action when a syntactic node of the DSL is processed; this is called Syntax Directed.

Template technologies based on XML or XML-like syntax, such as Ant scripts and FreeMarker templates, can be seen as examples of syntax-directed translation. Take Vue’s template syntax, for example,

```
  <template>
    <BaseButton @click="search">
      <BaseIcon name="search"/>
    </BaseButton>
  </template>
```

The template is essentially the abstract syntax tree (AST) presented in XML form. When a component node is processed, the engine locates the corresponding component definition based on the tag name and then recursively processes it. The entire mapping process is context-free: the mapping does not depend on the node’s surrounding context; the same tag name always maps to the same component.

The same pattern underpins Facebook’s GraphQL technology. It uses a syntax-directed approach to send pending data access requests to a deferred processing queue and merges requests to achieve batch-loading optimization.
For example, to handle the following gql request

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

The backend only needs to specify a corresponding dataLoader for the data types:

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
     // Each User object returned from allUsers has only the followingUserIds property,
     // which needs to be transformed into full User objects
    async followingUsers(user, args, { dataloaders }) {
      return dataloaders.users.loadMany(user.followingUserIds)
    }
  }
};
```

To facilitate the syntax-directed pattern, modern programming languages already provide default solutions—namely, annotation-based metaprogramming. For example, function annotations in Python:

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

Viewing an annotation as a function name is a very simple and intuitive idea, one that TypeScript also adopts. In contrast, Java’s APT (Annotation Processing Tool) seems circuitous and verbose, which leads to few people using APT to implement custom annotation processors. However, its role is at compile time, where it operates on the AST, enabling deeper transformations. Rust’s procedural macros present a more elegant compile-time approach.

```
    #[proc_macro_derive(Hello)]
    pub fn hello_macro_derive(input: TokenStream) -> TokenStream {
        // Build the AST from the token stream
        let ast = syn::parse(input).unwrap();

        // Construct the returned syntax tree using a template-like generation approach
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

    // Use the macro to add an implementation of the Hello trait for the Pancakes struct
    #[derive(Hello)]
    struct Pancakes;
```

## III. Multiple Interpretations

Traditionally, a piece of code has only one prescribed runtime semantics. Once information flows out of the human mind and is solidified as code by the programmer’s hands, its form and connotation become fixed. But Reversible Computation points out that logical expression should be bidirectionally reversible: we can reverse the direction of information flow, extracting information expressed in code back out. This makes “express once, interpret in multiple ways” a common means of implementing declarative programming by separating expression from execution. For example, the following filter condition:

```
<and>
  <eq name="status" value="1" />
  <gt name="amount" value="3" />
</and>
```

When presented on the frontend, it corresponds to a query form; applied to the backend, it corresponds to an implementation of the Predicate interface; sent to the database, it becomes part of the SQL WHERE clause. And all of this requires no manual coding—they are simply multiple interpretations of the same information.

With the widespread dissemination of compilation techniques, traditional imperative programming, when reinterpreted, has taken on a declarative flavor. For instance, Intel’s OpenMP (Open Multi-Processing) technology:

```
   int sum = 0;
   int i = 0;

   #pragma omp parallel for shared(sum, i)
   for(i = 0; i < COUNT;i++){
      sum = sum + i;
    }
```

Simply adding a few pragmas to traditional imperative statements can transform serially executed code into a parallel program.

In the field of deep learning, compilation and transformation techniques have been pushed to new depths. Frameworks like PyTorch and TensorFlow can compile Python functions in form to instructions that run on GPUs. Heavyweights like TVM can even compile directly to FPGA code.

The possibility of multiple interpretations keeps a piece of code’s semantics perpetually open—everything is virtualized.

## IV. Delta Revision

Reversible Computation treats Delta as a first-class concept and considers a full state as a special case of Delta. In the design of Reversible Computation, a DSL must define a delta representation to enable incremental improvement, and the processing logic unfolded from the DSL should support incremental extension as well. Antlr4, for example, introduces import syntax and the visitor mechanism, thereby for the first time enabling delta revision of models.

In Antlr4, the import syntax is similar to inheritance in object-oriented languages. It is a smart include: the current grammar inherits all the rules, token specifications, named actions, etc., from the imported grammar and can override rules to replace inherited ones.

![antlr\_combined](antlr/combined.png)

In the example above, MyElang inherits several rules from ELang, while also overriding the expr rule and adding the INT rule. At long last, we no longer need to copy-paste every time we extend a grammar.

In Antlr4, it is no longer recommended to embed processing actions directly in the grammar definition file; instead, you should use the Listener or Visitor pattern, which allows incremental revision of the processing pipeline through inheritance built into object-oriented languages.

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

## V. Automatic Differentiation

If the ideal of declarative programming is that people only need to describe the problem and the machine automatically finds a solution, where can we find a class of problems that is sufficiently general and can be automatically solved? Fortunately, since Newton, science has flourished and we have inherited a few such evergreen problems—one of which is automatic differentiation.

As long as we specify the differential expressions of a few basic functions, we can automatically compute the derivatives of many composite functions. This capability is a must-have for all current deep learning frameworks. Reversible Computation posits that the concept of automatically computing Delta can be extended beyond mathematics and algorithms to become an effective mechanism for software structure construction.

Take k8s as an example. The core idea of this container orchestration engine is to specify the system’s “desired” state via a declarative API, then continuously detect deviations between the current state and the desired state via monitoring and measurement, and automatically execute actions to “correct” these deviations. Its core logic can be summarized by the following formula:

$$
action = Translator( PhysicalState - Planner(LogicalSpec) )
$$

This design principle adopted by k8s can be called State Driven. It shifts the focus to system states and the differences between states, rather than traditional action-based API calls and event listeners. The shift from actions (Action) to states (State) is analogous to the transition in physics from the concept of force to a field-based view grounded in a potential function (Potential).

Transitioning from state A to state B yields the same result regardless of the path taken, so the concept of potential is path-independent. Freeing ourselves from path dependence greatly simplifies our understanding of the system. Force can always be obtained by differentiating the potential function, i.e., by taking the gradient of the potential. Similarly, in k8s, for any state deviation, the engine can automatically infer the corresponding actions that need to be executed.

$$
F = - \nabla \phi
$$

When moving from state A to state B, there are multiple feasible paths, and selecting one according to cost or benefit criteria is what we call optimization.

The shift from actions to states is a transformation in holistic thinking. It requires us to adopt a new worldview and continuously adjust the corresponding technical implementations to fit this worldview. This trend is steadily strengthening and is giving rise to new frameworks and technologies in more and more application domains.

The notion of potential demands comprehensive knowledge of the state space: every reachable state must have a legitimate definition. Sometimes, for specific applications, this requirement may be too harsh. For example, we may only need to find one feasible path from a specific state A to a specific state B, without studying the state space itself formed by all states. In such cases, traditional imperative approaches are sufficient.

## VI. Isomorphic Transformation

There’s nothing new under the sun. In everyday programming, genuinely new logic that requires human creativity is rare; most of the time, we are simply mapping one logical relationship to another. Consider log collection: to ingest log file contents for analysis, we typically need to use tools like logstash to parse log text into JSON and then deliver it to ElasticSearch. However, if we preserve the object format at logging time, the intermediate logstash parsing step may not be necessary. If we need property filtering or further processing, we can directly plug into a general object-mapping service (with a visual interface for configuring mapping rules), rather than writing a domain-specific implementation just for logs.

```
    // Output logs in object format
   LOG.info(logCode, { paramName: paramValue });
```

Many times, the reason we need programmers to write code is that information loss occurs when crossing boundaries. For example, when logging is printed as text lines, we lose object structure information; the work of reconstructing structure from text cannot be easily automated—it must leverage the programmer’s mind to eliminate the various ambiguities that may arise during parsing. The information in the programmer’s mind includes background knowledge of our world, various conventions, and overarching architectural design ideas. Therefore, many things that appear logically equivalent often cannot be accomplished automatically by code and must be balanced by adding the human variable.

$$
A \approx B \\
A + Human = B + Human
$$

Modern mathematics is built on the concept of isomorphism. When we say A is B in mathematics, the subtext is that A is equivalent to B. Equivalence classes greatly reduce the number of objects we need to study and deepen our understanding of the essential structure of systems.

> Why is 3/8 = 6/16? Because that’s the definition of fractions! (3/8, 6/16, 9/24...) This sequence of representations is defined as an equivalence class, whose representative element is 3/8 (see the preface of Roger Penrose’s “The Road to Reality: A Complete Guide to the Laws of the Universe”).

Reversible Computation emphasizes reversible transformation of logical structures, attempting to establish mathematical-like abstract expressiveness in software construction. This only maximizes utility when all upstream and downstream parts of the software satisfy the principle of reversibility. For example, when fine-grained components and processing pipelines are both reversible, a visual designer can be generated directly from its DSL without special coding:

$$
\text{Visual Interface} = \text{UI Generator}(DSL)\\
DSL = \text{Data Extraction}(\text{Visual Interface})\\
DSL = \text{UI Generator}^{-1}\cdot\text{UI Generator}(DSL)
$$

An obstacle to achieving reversibility in real-world development is that software development is currently highly goal-oriented, so information unrelated to the current scenario often has nowhere to go. To solve this problem, the system’s foundation must provide a metadata space that allows custom extensions.

$$
A \approx B \\
    A + A' \equiv = B + B' \\
$$

The information corresponding to A' may not be used in the current system A, but to adapt to system B’s application logic, we must find a place to store this information. This is a holistic, collaborative process. Have you noticed that all modern programming languages have undergone “hat-wearing” retrofits and support some form of custom annotation mechanism? Some extended descriptive information is carried around in the hat. In other words, the (data, metadata) pair constitutes a complete expression of information—just like message objects always contain (body, headers).

The world is so complex; why should our purpose be singular? In a declarative world, we ought to maintain a more open attitude. You don’t have to wear a hat to block wind or sun—can’t you wear it simply because it looks good? Metadata is declarative; we typically say it’s data that describes data, but even if it doesn’t describe anything right now, it can have its own reason to exist. Isn’t there a notion called the “usefulness of uselessness”?
<!-- SOURCE_MD5:6f0ed94c65acad77eddc106044bf5438-->
