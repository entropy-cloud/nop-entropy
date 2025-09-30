# Q&A on the Underlying Theory of the Nop Platform

## 1. When the proxy object equals the proxied object, does it indicate a bidirectional reversible transformation?

No. A proxy object—or any solution based on interfaces or polymorphism—can be viewed as a mathematical homomorphic mapping, which in category theory is the so-called Functor mapping.

Since many readers have reported unfamiliarity with the mathematical notion of homomorphism, we will give a brief, informal explanation. Suppose there exist objects a, b, c, d... in a space F, and certain operations/relations f, g, h... among them.

```
f(a,b), g(b,c), h(b,d), ...
```

A homomorphic mapping $\phi: F \rightarrow G$ maps the objects and operation relations in space F to space G while preserving the structural relations of the original space F. In other words, a homomorphism is a structure-preserving mapping.

$$
\phi(f(a,b)) = \phi(f)(\phi(a),\phi(b)) = f'(a',b')
$$

Here, object a is mapped to a new object a', object b is mapped to a new object b', and the operation relation f between a and b (which can be regarded as a function taking a and b as parameters) is mapped to a new operation relation f'. Note that the requirement is that for any a and any b, the relation f has a corresponding f' after mapping via $\phi$; in this sense, we consider that the operational structure between a and b is preserved. That is, for any possible operation between a and b, there exists a similar operation f' in the new structural space G.

If we write f(a, b) as $a \rightarrow b$, we can understand the meaning of “structure-preserving” more intuitively.

![](nop/homomorphic.png)

A homomorphic mapping allows multiple objects in space F to map to the same object in space G. For instance, in the example above, c and d both map to c'. This naturally forms a simplification mechanism: when the complex relations in space F are mapped to space G, the number of elements and relations can be reduced, thus simplifying the implementation compared to the original structure, while preserving the core part of the original structure.

Returning to our initial question. Interfaces—or polymorphism—can be viewed as the following homomorphic mapping:

```
f(objA,objB)  == mapped to the interface space ==>  f'(interfaceA, interfaceB)
```

In the original object space, there may be many possible object relations, but if we consider a new space with only a few interface objects and project the structure from the original object space into the interface space, the number of objects we need to reason about is reduced, and the number of functions is also reduced. More notably, in code form, f' and f can be exactly the same! However, we must recognize that having f' (the interface function) identical to f (the object function) is not something guaranteed by nature. In some languages based on generics, for the same function source code, the compiler may generate different binaries for different objects.

Using the concept of homomorphic mapping, we can clearly see the technical value of interfaces and polymorphism. We can use polymorphism to project the logic we truly need to write into a subspace that retains the main structural relations of the original space, while unimportant details are automatically hidden by the mapping process.

In Reversible Computation theory, “reversible” has multiple meanings. One of them is reversible transformation, which is an isomorphism. A homomorphism can be regarded as a one-way mapping, while an isomorphism is a bidirectional mapping. For a detailed introduction, see [What exactly does “reversible” mean in Reversible Computation theory?](https://mp.weixin.qq.com/s/Fngl7vYWhULn0VKeAEKPkQ)

## 2. PDF and HTML can be viewed as multiple representations of an Open API. For example, after converting to HTML, you need CSS for styling, and that part cannot be converted back. Is this CSS the Delta? If we identify the Delta, can many things be extended and information made reversible?

In a certain sense, yes.

In practical applications, most transformations we encounter are approximately equivalent:

```
A ~ F(B), G(A) ~ B
```

By supplementing the Delta, the relationships above can be turned into equalities:

```
A + dA = F(B + dB), G(A + dA) = B + dB
```

Therefore, a key design in the Nop platform is that all details allow storage of extension information—i.e., we always adopt the (data, ext_data) paired design. When information overflows in some form, there is a place to store it.

In physics, “reversible” means entropy remains unchanged, i.e., conservation of information. You need to understand the underlying regularity here as you would the law of conservation of energy, which requires some physics background. Otherwise, one can get lost in details and keep asking why perpetual motion is impossible.

Many representation transformations that people encounter exhibit locality: an attribute corresponds to a local structure across multiple representations. However, in physics, the most useful transformation is the Fourier transform, which performs large-scale shattering and recombination.

Representation transformation also needs to be understood in conjunction with minimal information representation. Minimal information representation implies achieving some form of optimality, and things that achieve optimality are generally unique; otherwise, we could compare two different options and continue choosing the better one. In mathematics, so-called uniqueness is defined via equivalence relations. That is, when mathematics says A = B, it does not mean A and B are identical in every detail, but that there exists a bidirectional reversible transformation between A and B. Therefore, if we always require minimal information representation when implementing frameworks, and multiple different frameworks ultimately achieve minimal information representation, the uniqueness of such minimal representations guarantees that equivalent transformations (reversible transformations) must exist between different framework representations.

## 3. Since Nop uses Excel to define models, is it unsuitable for Git-based version control in engineering?

Not at all. In the Nop platform, Excel does not act directly; it is equivalent to app.orm.xml. Excel is merely a presentation form of orm.xml—a display cache, so to speak. The Nop platform actually uses app.orm.xml. It can run completely without Excel; generating app.orm.xml from Excel is simply convenient for maintaining consistency between code and requirements documentation. If you do not use Excel, there are two options:

1. Use a code-driven approach or define via yaml files. These approaches are not convenient for non-technical requirements personnel to participate in, nor are they conducive to communication with customers. If documentation is maintained separately, inconsistency between documentation and code is likely.
2. Use an independent model management tool, such as yapi for API definitions. However, this requires deploying a separate application and is not easily integrated with DevOps automation. In the Nop platform, mvn packaging tools can directly read Excel models to generate code.

## 4. What is Delta? Is each Git commit a delta? Does Nop actually change commit granularity from code lines to some meta-capability?

git is defined over a general line space, so it is unstable for domain problems; domain-equivalent adjustments lead to merge conflicts in the line space. A similar situation exists with Docker. Many people understand Docker as providing dependency resolution between modules. But if it were only those functions, couldn’t virtual machines do the same? Many years ago, Python tools could manage dependencies and dynamically generate virtual machines. The essential difference is that the delta for virtual machines is defined in an unstructured byte space, whereas Docker’s is defined in a structured file space.
If you doubt the usefulness of Delta, think about what Docker enables. Docker is a file Delta space; Nop is a more general and more systematic Delta space.

## 5. Can subtraction be implemented by defining a disabled attribute on nodes?

Some may want to integrate a DSL into a tree, add a disabled attribute to each node, and prune logic in code processing via disabled. Such pruning generally requires bespoke runtime handling for specific semantics. Nop performs unified compile-time processing of all DSLs in the domain-structural space, without special-case handling for each situation.

## 6. What capabilities does the Nop platform enable easily that are difficult with other technologies?

The Nop platform has a feature not present in current software engineering: coarse-grained software reuse. Given a fully developed system, how can one customize all business logic without modifying its source code or changing its jar packages?
Products developed on the Nop platform are inherently extensible. It provides a built-in Delta customization scheme—a systematic secondary extension mechanism that does not require predefined extension points.

Customization is feasible only if changes can be stably located at the conceptual level, which requires introducing a stable domain coordinate system—a core concept in the Nop platform. In fact, many practical approaches are theoretically fuzzy; most people rely on gut feeling. The Nop platform’s approach is guided by rigorous theory, enabling extensive automatic reasoning.

Nop is not a silver bullet, but it is a significant theoretical advance—equivalent to introducing new operational rules and expanding the solution space, allowing problems that previously lacked general solutions to be defined with general solutions.
<!-- SOURCE_MD5:d4e58cb617bbd583b115ecd216a4a937-->
