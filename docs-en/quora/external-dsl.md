That's a thought-provoking point, and I largely agree with the sentiment if we're talking about external DSLs that require writing bespoke lexers and parsers. The overhead and loss of host-language benefits (like type-checking and tooling) in that scenario are indeed significant drawbacks.
However, I'd like to propose a different perspective. The true anti-pattern may not be the use of external DSLs, but rather the failure to distinguish between a DSL's **syntax** and its essential value: **the high-density expression of a domain model**.
When you suggest using "plain data structures in the host language," you're advocating for **Embedded DSLs**. They are incredibly powerful for tasks tightly coupled to the application's implementation. But this approach has a fundamental limitation: it binds the model inextricably to a specific host language and its ecosystem.

I believe the real power of a DSL emerges when we treat the **model** as an independent, first-class asset. Here's why:

1.  **The Model's Independence is its Value:** An external DSL, when expressed in a standard, language-agnostic format (like JSON, YAML, or even XML), is simply a serialized representation of a model. This model now has intrinsic value, separate from any single application. It is the explicit, canonical source of truth for a piece of domain logic.

2.  **Language and Platform Agnosticism:** This is the killer feature. Let's take your example of a database schema. If I define my ORM model in a YAML file, it can be consumed by:
  *   A Java code generator to create JPA entities.
  *   A TypeScript code generator for front-end types.
  *   A documentation generator to update our API specs.
  *   A database migration tool.
      An embedded DSL defined in Haskell or Scala is locked within its ecosystem and cannot serve these diverse, multi-language consumers. Tying the model to a host language severely limits its reach and longevity.

3.  **True Separation of Concerns:** An external DSL represented as pure data (e.g., JSON) contains zero implementation noise. It's the pure "what" (the model), completely decoupled from the "how" (the implementation in a specific language, with its Monads, Futures, and library-specific details). This purity makes the model easier to understand, validate, and evolve.

4.  **Enabling Generic Meta-Programming:** This is where the approach truly shines. When all your external DSLs (for APIs, schemas, configurations, etc.) are expressed in a common structured format and ideally governed by a common meta-model (like a sophisticated JSON Schema or, as we've implemented in the Nop platform, a meta-model definition language), you can build powerful, **generic** tools. For example, you can create a universal delta-customization mechanism, much like `kustomize`, that can apply patches and overlays to *any* DSL. This powerful capability is simply unattainable with a collection of disparate embedded DSLs.

So, perhaps the guideline shouldn't be "avoid external DSLs." It should be: **"Use embedded DSLs for tightly-coupled, implementation-specific logic, but use language-agnostic external DSLs to define independent, architectural models."**

The focus should be on elevating the model itself, and representing it as text is the most robust way to do that. The format (JSON/YAML) is secondary; the independence is primary.
