# Decoupling Is Far More Than Dependency Injection

What is coupling? How do we decouple? In today’s world where object-oriented techniques prevail, interactions are expressed as associations between objects; the outward manifestation of coupling is holding pointers to related objects. Thus, the decoupling problem appears to be about minimizing the amount of information required for object assembly, with the resulting solution crystallizing into the concept of dependency injection. For a detailed introduction, see invalid s’s answer

[What Is Decoupling](https://www.zhihu.com/question/20821697/answer/2608624207)

> <b>Dependency Inversion</b> Principle (Dependency Inversion Principle, DIP) states that when designing code structure, high-level modules should not <b>depend</b> on low-level modules; both should <b>depend</b> on their abstractions.

However, the understanding that dependency injection is the key to decoupling is merely a past trend. On the one hand, it indeed provides a practical means of decoupling: **do not depend on the whole object; only depend on a minimized abstract interface. Different objects can implement the same interface. Use a declarative assembly container to defer assembly choices.** On the other hand, it is not the entirety of decoupling techniques in software, and we might even say it should not be the primary means of decoupling.

## I. What Does Dependency Injection Inject? Homomorphic Image

To depend on abstractions rather than concrete details essentially means we **do not depend on the whole object but only on its homomorphic image**.

In mathematics, a homomorphism is a transformation in the object space that maps objects a and b in the original space O to P(a) and P(b) in the image space, while preserving certain operations f in the original space so that functions defined in the original space can naturally translate to functions in the image space.

```java
 P(f(a,b)) = f(P(a), P(b))
```

Objects implement interfaces; an object is an instance of an interface—obj is an instance of interfaceA. In design, we need to maintain the conceptual “is a” relationship between objects and base classes, as well as between objects and interfaces, to ensure that code written against interfaces can operate on derived objects, i.e.,

```java
f(interfaceA,interfaceB) == f(objA, objB)
```

An object can have many functions, but typically only a small subset is strictly required to execute business functionality—the functional interface methods—while a large number of other functions serve as auxiliary methods for dynamic configuration, lifecycle support, etc. If we insist that objects only depend on the functional interface, we effectively map the problem homomorphically into a smaller semantic space, thereby simplifying it.

A declarative dependency injection container knows the dependency relationships among all objects, so it can impose a global ordering on their construction sequence; it can also implement deferred assembly via configuration files and lazy-loading semantics via caching, effectively **decoupling unnecessary dependencies on object creation order (only global knowledge enables global optimization)**. Of course, if the system’s structure is relatively simple and can be extracted into a few clear lifecycle phases, the role of a dependency injection container in this respect may be diminished.

Interfaces can be seen as a local representation of an object. To maximize the usefulness of such a representation, **an essential requirement is that operations in the representation space exhibit some degree of completeness**—that is, the functions defined over the representation space should be sufficient to accomplish the primary business functions. Functions defined on the interface should be business-level self-consistent; otherwise, you will face the problem of leaky abstractions.

## II. From Homomorphic Mapping to Representation Transformation

Tracing back, concepts like coupling and decoupling originate from mathematics and physics, where the means to achieve decoupling are far richer and more powerful.

Recall the fundamental theory of the Fourier transform in mathematical physics: multiple signals of different frequencies superimpose in the time domain, looking completely intertwined—at every instant, multiple signals exert influence. Yet via the Fourier transform, we obtain completely separated signals in the frequency domain! Therefore, **the most essential and powerful means to achieve decoupling should be representation transformation; interfaces are merely the simplest form of such transformations.**

A criterion for evaluating decoupling quality is “high cohesion, low coupling.” The extreme of high cohesion is indivisibility (atomicity), while the extreme of low coupling is independence. In linear systems, there are infinitely many optimal decouplings: **you only need to find any orthonormal set of basis vectors** (rotating an orthogonal coordinate system by any angle preserves orthogonality), and then all quantities in the entire space can be obtained via linear combinations of the action on the basis vectors.

Representation transformation, simply put, can be seen as a coordinate transformation. The essence of a thing does not change, but when examined under different coordinate systems, we might mistakenly perceive changes in complexity and intense shifts in interrelationships. Conversely, when designing a system, we can choose a linear model with orthogonal dimensions as our target and strive to maintain linearity across layers, thereby simplifying the system structure. See

[Designing Low-Code Platforms from the Perspective of Tensor Products](https://zhuanlan.zhihu.com/p/531474176)

If we regard a DSL (Domain Specific Language) as a global representation, then representation transformation can be expressed as `F(DSL)`, namely an interpreter or code generator for the DSL. However, DSLs generally summarize known domain requirements, and their capabilities are limited. Therefore, we must supplement with additional Delta information to produce the target system. Thus we obtain

```java
App = F(DSL) + Delta
```

That is, we need to merge externally provided Delta information with the results of the representation transformation. But merging implies mixing, and mixing generally leads to entropy increase (a rise in system disorder). Entropy increase is essentially due to information loss. **To avoid entropy increase, we must preserve information integrity and ensure no loss; in thermodynamics, this means the system evolution is reversible.**

**Reversible operations imply decoupling between the Delta and the original entity.** The Delta can exist independently of the entity; it has meaning of its own.

```java
Delta = App - F(DSL)
```

> In Reversible Computation theory, a Docker image can be viewed as a Delta structure: an application image can be stored, transmitted, and managed independently of the underlying operating system image.

With the introduction of Reversible Computation, interesting phenomena emerge. Conventionally, decoupling means reducing interactions, which should first reduce the number of interacting objects. However, if Reversible Computation exists, we can achieve decoupling by introducing a new object, Delta. For example, Object A and Object B have conflicting designs for the same functionality. They can be designed in ways best suited to their internal structures, without worrying about coupling introduced by trying to avoid conflicts. When used together, we can add a Delta to resolve their conceptual conflicts.

```java
 App = A + B + (-C - D + E) = A + B + Delta
```

For A and B, Delta is a completely external entity.

Object-oriented and component theories essentially attempt to draw on experience from production activities in the material world. But as software production deepens, we **must reexamine the abstract essence of software.** Software is an information product existing in an abstract logical world, and information is not matter. **The construction and production laws of the abstract world differ fundamentally from those of the material world.** The production of material goods always has costs, yet the marginal cost of copying software can be zero. To move a table out of a room in the material world, you must pass through a door or window; in the abstract information space, you simply change the table’s coordinate from x to -x. The operational relationships among abstract elements are not constrained by numerous physical limitations. Therefore, the most effective mode of production in the information space is not assembly but the mastery and formulation of operational rules.

For a complete theoretical exposition of Reversible Computation, see my article

[Reversible Computation: The Next-Generation Theory of Software Construction](https://zhuanlan.zhihu.com/p/64004026)

For concrete implementations, refer to

[Technical Implementation of Reversible Computation](https://zhuanlan.zhihu.com/p/163852896)

[What Kind of ORM Engine Does a Low-Code Platform Need? (1)](https://zhuanlan.zhihu.com/p/543252423)

[What Kind of ORM Engine Does a Low-Code Platform Need? (2)](https://zhuanlan.zhihu.com/p/545063021)
<!-- SOURCE_MD5:d0a3efb5640613028040f0ff495bb6f9-->
