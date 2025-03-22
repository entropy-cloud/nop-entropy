# Decoupling is More Than Just Dependency Injection

What is coupling? How to decouple? In today's world, where object-oriented technology dominates, interaction is expressed as the mutual connection between objects. The external manifestation of coupling is holding references to related objects. Therefore, the problem of coupling seems to be about minimizing the information needed for object composition and eventually forming a solution that becomes dependency injection. For specific details, please refer to the answers provided in the invalid s thread.

[What is decoupling](https://www.zhihu.com/question/20821697/answer/2608624207)

> **Inversion of Control** principle (Dependence Inversion Principle, DIP) refers to designing a code structure where higher-level modules should not **depend** on lower-level modules. Instead, both should **depend** on abstractions.

However, dependency injection is often misunderstood as the primary method for decoupling. While it indeed provides a useful mechanism: **no dependency on objects as a whole, only dependency on minimal abstract interfaces. Different objects can implement the same interface. Using a container for deferred composition (e.g., configuration files for delayed loading) is a common approach. However, it is not the sole means for achieving decoupling, nor should it be considered the primary method in all cases.

## 1. What is dependency injection? Phenomenal transformation

Dependency injection means relying on abstracts rather than specifics. At its core, it states: **No dependency on objects as a whole, only dependency on their interface.** This ensures that any object implementing the interface can be used without knowing its specific type.

Mathematically, this is akin to a morphism in the object space. It maps objects from one space (O) to another space (P), transforming O's elements (a, b) into P's corresponding elements (Pa, Pb). This transformation preserves certain operations defined in O, allowing natural translation of functions from O to P.

```java
P(f(a, b)) = f(P(a), P(b))
```

Objects can implement multiple interfaces. For example, an object may be an instance of interface A and another of interface B. The relationship "is a" is crucial at the conceptual level to ensure that methods written for interface A also apply to derived objects.

```java
f(interfaceA, interfaceB) = f(objA, objB)
```

Most objects have numerous functions, but only a few are essential for business operations. These are typically the interface-related functions. Other functions are auxiliary, supporting dynamic configuration, lifecycle management, and other operational aspects. By adhering to this principle, we minimize unnecessary object creation order dependencies (only those with global knowledge can achieve global optimization). If the system is relatively simple, it can be modularized into a few well-defined lifecycle stages. In such cases, dependency injection containers can weaken their influence.

## 2. From morphism to transformation

In mathematics and physics, particularly in Fourier analysis, coupling and decoupling are fundamental concepts. Signals with different frequencies are combined in the time domain, appearing as a single composite signal. Using Fourier transform techniques, these signals can be separated in the frequency domain. Thus, **the essential method for achieving decoupling is transformation (phenomenal transformation)**.

The goal of coupling is to create a system where components are highly interdependent. The limit of tight coupling is reached when components cannot be altered without affecting others—reaching an "atom" state. Conversely, the aim of decoupling is to achieve maximum independence, minimizing side effects. The extreme form of decoupling would be having completely unrelated components.

In linear algebra terms, this can be visualized using orthogonal bases. By rotating the coordinate system (through transformation), complex relationships in one basis become simple and manageable in another. Thus, **transformation is the most powerful tool for achieving decoupling**.

The criterion for evaluating coupling vs. decoupling lies in the balance between tight coupling and loose coupling. The ideal is a system where components are neither overly dependent nor completely independent. In linear systems, this translates to having a basis that can express any vector using a minimal number of basis vectors (i.e., being orthogonal).

Finally, remember that **transformation is not just about code; it's about rethinking your entire approach**.

[From coupling to transformation](https://zhuanlan.zhihu.com/p/531474176)  



A **Domain-Specific Language (DSL)** can be viewed as a global representation of a domain if we consider it in the context of **reversible computation**. This global representation allows us to express transformations using an interpreter or code generator that operates on the DSL, denoted as `F(DSL)`.


However, a DSL is typically designed for known domain requirements and has inherent limitations in its capabilities. To construct a target system, we must augment the DSL with additional information, denoted as `Delta`, to address these limitations.


The combination of `F(DSL)` and `Delta` allows us to merge external delta information with domain-specific transformations. This merging process can lead to **entropy increase** due to information loss. To minimize entropy increase, we must ensure that the information remains intact and does not suffer loss.


In reversible computation theory, operations are designed to be invertible. This means that changes (denoted as `Delta`) can be decoupled from the system's intrinsic properties. Changes can exist independently of the system, carrying their own significance.

```java
Delta = App - F(DSL)
```




In reversible computation theory, a **Docker image** can be seen as a difference structure. It isolates the operating system layer from the application layer, enabling independent management, transmission, and storage.


The introduction of reversible computation leads to interesting phenomena. In general, decoupling (or reducing interactions) is achieved by minimizing mutual dependencies. This can be done by reducing the number of objects involved in interactions. However, with reversible computation, we can achieve this by introducing new objects, such as `Delta`.

```java
App = A + B + (-C - D + E) = A + B + Delta
```


The **object-oriented** and **component-oriented** paradigms aim to extract knowledge from the physical world of material production. However, as software development progresses, we must recognize the **abstract nature** of software. Software exists in an abstract logical space, not as a physical entity.


In the physical world, objects and their interactions are constrained by physical laws. In the abstract world of information, objects like `Delta` can exist independently without such constraints. Thus, constructing software becomes more efficient in an abstract space.




Software is an abstract product created from logical operations. Unlike physical products, which have production costs that decrease with mass production (e.g., the marginal cost of producing additional copies approaches zero), software's marginal cost remains high.


In reversible computation theory, `Delta` represents changes that can exist independently of the system. It allows us to isolate and manage modifications without affecting the intrinsic properties of the system.



- [Reversible Computation: The Next Generation Software Construction Theory](https://zhuanlan.zhihu.com/p/64004026)
- [Technological Implementation of Reversible Computation](https://zhuanlan.zhihu.com/p/163852896)

- [What ORM Should a Low-Code Platform Use? (1)](https://zhuanlan.zhihu.com/p/543252423)
- [What ORM Should a Low-Code Platform Use? (2)](https://zhuanlan.zhihu.com/p/545063021)

