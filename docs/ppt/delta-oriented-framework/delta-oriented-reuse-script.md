### Slide 1: Extensibility based on the Delta-Oriented Framework
(Time: 0:00 - 1:30)

(Script):

"Good morning. Today, let's talk about a common challenge in software engineering: how to handle the customization of complex software.

Our industry has many ways to deal with this, such as plugins and APIs. But the approach I'm sharing today is truly unique. It’s called the Delta-Oriented Framework, and it allows us to build systems that are powerful, predictable, scalable, and designed from the start to evolve.

So, what makes it truly unique? Its theoretical foundation.

It's based on a software construction theory I proposed back in 2007, called Reversible Computation. This theory gives us a new language to talk about and manage change itself. Ultimately, it redefines how we approach extensibility."

[Click to Next Slide]


### Slide 2: The Customization Trap

**(Script):**

"So, what does this problem look like in practice? I call it 'The Customization Trap,' and this slide shows it perfectly.

It all starts here, with our standard, stable core product, version 1.0.

Then, an important customer arrives, and they require significant customization. The fastest way to deliver is to simply fork the code and start making changes. This leads to heavy customization and tightly coupled code, creating a version specific to that one customer.

Meanwhile,your core team continues to evolve the core product according to the roadmap. They release version 2.0 with great new features and important bug fixes.

And this is where the real challenge begins. As the diagram shows, the friction goes in two directions.

First, trying to upgrade the custom branch to version 2.0 is a nightmare. The merge is complex, risky, and incredibly costly because the codebases have diverged so much.

At the same time, let's say your team on the custom branch developed a fantastic new feature. Trying to merge that innovation back into the main product is just as difficult.

The two paths are now separated by a wall of complexity.

What is the result? The customer's branch is now stuck on an old version. They miss out on all your new innovation, and you are stuck maintaining this separate branch at a very high cost.

This is the vicious cycle. It begins with a 'copy and modify' approach that seems easy at first. But soon, the core and custom code are on diverging paths. It all ends in technical debt, stagnation, and expensive, painful maintenance. This is the cycle we must break."

**[Click to Next Slide]**

### Slide 3: Software Product Lines: From Ad Hoc to Systematic Reuse

**(Script):**

"Now, the industry has long been aware of this problem. In fact, a formal theory was proposed back in the early 2000s by the Software Engineering Institute at Carnegie Mellon University (CMU/SEI), a leading authority in the field.

They called this discipline **Software Product Lines**, or SPL.

The goal of SPL is to replace the chaotic, 'ad hoc' copying we saw with a systematic, engineering-led approach. As the diagram shows, it splits the process into two main activities.

First, there is **Domain Engineering**. Here, the mindset shifts completely. We don't just build one-off products. We strategically build a platform of reusable **Core Assets**. Think of it as creating a factory designed to produce a family of related products.

The second activity is **Application Engineering**. This is how we create a solution for a specific customer. We go to our factory, **Select** from the available core components, **Extend** them with any necessary custom features, and then **Deploy** the final product.

The key difference here, compared to standard product development, is that the core assets are intentionally built not as a final product, but as a platform for reuse.

You can see the immediate benefit: both Customer A and Customer B are now built on the same, stable, versioned core. This brings structure and discipline to customization. In theory, it seems like the perfect solution to the Customization Trap."

**[Click to Next Slide]**

### Slide 4: Promise vs. Reality

**(Script):**

"The promise of the Software Product Line approach was incredible.

The CMU Software Engineering Institute published case studies showing amazing results: more than 10x productivity, 60% cost reduction, and 98% faster time-to-market.

So, with such a great promise, why isn't this the standard practice everywhere?

The answer lies in the reality. Many companies found this very difficult to implement successfully. They all faced the same core challenge: **Effective Variability Management.**

In simple terms, how do you actually manage all the possible differences between all the products? How do you design a core platform that is stable enough to be reliable, but also flexible enough to support all the variations you need today... and all the ones you can't even predict for tomorrow?

This single challenge is the core of the problem. Now, let's look at how the traditional SPL approach tried to solve this."

**[Click to Next Slide]**

Of course. Let's proceed to Slide 5.

This slide explains the *mechanics* of the traditional approach. The key is to describe it clearly and objectively, while subtly planting the seed that this approach is rigid because it relies on architects predicting the future.

Here is the script for Slide 5.

---

### Slide 5: The Prescribed Approach: Adapt, Replace, Extend

**(Script):**

"So, how does the traditional Software Product Line approach actually manage variability?

The prescribed method is built on one golden rule: **Do not modify the core assets.** All customization must happen through pre-planned 'Variation Points' that the architects have already created.

There are three main ways to use these points, as shown in the diagram.

First, there is **Adaptation**. This is where you tweak the behavior of a core component. A simple example is changing a parameter, like setting a log level. You're adapting how the core works, without changing its code.

Second is **Replacement**. This is about making a choice. You might replace a standard component with a different one that follows the same interface. For example, selecting a specific payment gateway from a list of options.

Third, we have **Extension**. This is how you add completely new features, usually as plugins. You can stack on new capabilities that the core product doesn't have.

So, the entire system works like this: you derive new products by **activating**, **selecting**, or **providing** these pre-defined variations.

This approach is disciplined and structured. But its success depends entirely on one thing: the architects must have predicted all the possible variations you would ever need. But what happens when a customer asks for something new, something no one predicted?"

**[Click to Next Slide]**


### Slide 6: The Dilemmas of Component-Based Reuse

**(Script):**

"This approach of using pre-defined variation points seems disciplined and safe. But when we look deeper, we find that it leads to two fundamental paradoxes that limit its power.

First, there is **The Granularity Paradox**.
The goal of reuse is to have a large core that we can share across many products. But how do we create that reusable core? We do it by factoring out the common parts. By definition, the common part is always *smaller* than any complete product. So, our desire for a large reusable core is in direct conflict with the very method we use to create it. The more we try to make something common to everyone, the smaller it becomes.

The second dilemma is even more critical. I call it **The Prediction Paradox**.
This entire model depends on architects being able to predict the future. They have to define all the necessary extension points in advance.
If they create too *few* extension points, the architecture is rigid and brittle. It cannot handle unexpected customer requests.
But if they try to solve this by creating too *many* extension points, the architecture is destroyed. It becomes a complex mess of hooks and plugins, with no clear structure left. The architect is forced into an impossible choice.

And this brings us to a crucial insight. These are not just small issues you can fix with better engineering. These are **fundamental limits** of the entire component-based, or 'additive,' approach. To go further, we don't just need a better method. We need a completely new way of thinking."

**[Click to Next Slide]**

Of course. That's a much more direct and compelling way to introduce the analogy. It frames physics not just as an "inspiration" but as a parallel truth.

Here is the revised script for Slide 7 with that exact change.

---

### Slide 7: Inspiration from Physics: A New Duality for Software

**(Script):**

"So, if the component-based approach has fundamental limits, where do we go? **Actually, we can get our inspiration from physics.** Quantum Physics discovered long ago that the underlying laws of our world are governed by wave-particle duality. This means we can look at the same physical reality from two different perspectives.

First, there is the **Particle View**. This is the world of reductionism. We break complex systems down into their smallest atoms to understand them. This is *exactly* how our industry has approached software for the last 50 years. We decompose systems into their atomic parts—objects, functions, and components—and then we **assemble** these parts to build the whole. This is the world we live in now.

But physics teaches us there is another, equally valid perspective: the **Wave View**. In this world, reality is not about assembling parts. It is about **superposition**. It's about different waves that overlay and combine to create the final, complex pattern we observe.

This wave view inspires a radical new thought. What if we could apply this same duality to how we build software? This leads us to the central, provocative question on this slide:

**Can we build software via non-invasive superposition, not through invasive assembly?**

This simple question changes everything, and it's the key to the solution I'm about to show you."

**[Click to Next Slide]**


### Slide 8: An Algebraic View of Software Evolution

**(Script):**

"So, how do we formalize this idea of 'superposition' in software? Let's trace the evolution of reuse.

First, we had **Object-Oriented** programming, which is based on inheritance. A derived class `B` inherits from a base class `A`. This means `B` has more than `A`, but the difference—the delta—is **implicit**. It's not defined as a separate, manageable thing. The result? Reuse is trapped and constrained within the inheritance hierarchy.

Then, the industry moved to **Component-Oriented** design, following the principle of 'composition over inheritance.' The real power here is that the relationship changes. It's no longer an implicit change. It becomes an explicit addition: `A = B + C`. The delta, `C`, is now a first-class, reusable component.

Now, with **Reversible Computation**, we take the next logical step. We introduce a formal **inverse element**, a 'negative C'. This is the critical move. The statement `A = B + C` is no longer just a composition; it becomes a true algebraic equation. We can manipulate it, just like in math. We can solve for `B` by saying `B = A + (-C)`.

And this **completely changes the fundamental principle of reuse.**

Previously, reuse meant finding and reusing *identical* parts. Now, with algebraic manipulation, any two *related* structures can be transformed into one another using deltas. The principle of reuse is no longer 'identical is reusable.' It is now **'related is reusable.'**"

**[Click to Next Slide]**

### Slide 9: Reversible Computation: A Next-Gen Construction Theory

**(Script):**

"While the reversible delta is the core concept of Reversible Computation, the full theory corresponds to a universal computation paradigm: **`Y = F(X) + Delta`**.

For software construction, this general paradigm has a specific technical route, which can be expressed by this formula:

**`App = Delta x-extends Generator<DSL>`**

Let's break this down.

The **Generator** is like the deduction of a mathematical theorem. It takes a small set of core knowledge—the 'kernel of truth'—and automatically derives and unfolds it into a complete, runnable application.

This 'kernel of truth' is not expected to cover every single requirement. A good model describes the core trend, the stable, underlying law. Specific customer needs are like random data points around that trend line.

And this is where the **Delta** comes in. It is a reversible, composable, and non-invasive unit of change, designed precisely to handle those specific, unpredictable requirements.

Finally, we have the operator in the middle: **x-extends**. And I want to be very clear about this: **x-extends is a mathematically well-defined operation.** Any delta can be merged with any other delta, and any delta can be merged with any base. Because the merge rules are deterministic and algebraic, there is no such thing as a 'merge conflict' like you see in Git. The result is always predictable and guaranteed.

If you follow this logic, the 'base' itself is just the very first Delta applied to a void. In this model, **everything is a transformation.**"

**[Click to Next Slide]**

### Slide 10: From Theory to Practice: Docker as Reversible Computation

**(Script):**

"Now, the theory I just presented might sound abstract. But what if I told you that most of you in this room are already using it every single day?

Let's look at Docker. The formula for building a Docker container is:

`App = DockerBuild<Dockerfile> overlay-fs BaseImage`

Now, let's map this directly to our theory.

The `Dockerfile` is the DSL. It's a simple, text-based language. The `DockerBuild` tool acts as our **Generator**. It reads the `Dockerfile` and executes the commands, unfolding that simple script into a complete filesystem layer, or image.

And here is the genius of Docker's approach. It didn't have to invent all the generators from scratch. It brilliantly leveraged decades of work from the Linux community. Every command-line tool—like `apt-get`, `yum`, `cp`, or `mkdir`—is itself a powerful, pre-existing generator. The Dockerfile simply orchestrates them. **By doing this, Docker automatically gained access to a massive library of reliable, battle-tested generators.**

And the `overlay-fs`? This is a perfect real-world implementation of our `x-extends` operator. It non-invasively superimposes these new layers on top of the base image, without modifying the layers underneath. Each layer is a delta.

This isn't some far-off academic concept. It is a proven, industrial-scale pattern, hiding in plain sight. It demonstrates that building systems via superposition of deltas is not just possible, but practical and incredibly powerful."

**[Click to Next Slide]**


### Slide 11: The Inevitability of Deltas: A Universal Pattern

**(Script):**

"The Docker example shows us this pattern works in practice. But I want to argue that this isn't just one specific implementation. The concept of deltas is a universal and, in some ways, inevitable pattern for describing change.

Let's look at it abstractly.

Any computation can be described as: `Result = Function(Data)`.
Now, if we introduce change, we can always express it as a delta. `New_Function` is `Base_Function` plus a `Δ_Function`. `New_Data` is `Base_Data` plus a `Δ_Data`.

The new result will naturally be the original result, combined with a `Δ_Total` that represents the sum of all these changes.

This shows us that deltas arise naturally from any process of change. The general form, **`Y = F(X) + Delta`**, is the inevitable structure of any evolving system.

And this leads to a very powerful conclusion: **any software practice that deals with evolution, change, or variation can be understood and guided by the principles of Reversible Computation theory.**

This understanding is the true **paradigm shift**. We stop treating change as a messy side effect. Instead, we treat change itself as a first-class citizen—a pure, structured piece of data that we can manage, store, and combine.

Now that we understand this principle, let's see how we can implement it."

**[Click to Next Slide]**


### Slide 12: Delta Customization (1): File-Level Overlay

**(Script):**

"So, let's bring this powerful theory down to practice. How do we actually implement Delta Customization in our own software?

The first and most fundamental mechanism is a **file-level overlay**.

The foundation for this is a special kind of **Virtual File System**, or VFS. This VFS understands the concept of 'delta layers.'

Let's look at the example directory structure on the slide. We have our standard product files, like `/beans/job.xml` and `/config/auth.json`. These are the base files.

Now, to customize the system for a specific customer, we **do not touch the original files.** Instead, we create a parallel directory structure under a special `_delta` folder. For Customer A, we might create a new version of `job.xml` at the path `/_delta/customer-a/beans/job.xml`.

Then, we activate this customization with a simple parameter, like `deltaId=customer-a`.

When the application running on this VFS asks for the file `/beans/job.xml`, the VFS knows to first look in the active delta layer for `customer-a`. If it finds the file there, it serves the custom version. If not, it transparently falls back and serves the base version.

This mechanism is simple, powerful, and completely non-invasive. The base product remains pristine and untouched, while we can still provide deep customization by overlaying files."

**[Click to Next Slide]**

### Slide 13: Delta Customization (2): Intra-File Surgical Scalpel 

**(Script):**

"File-level overlays are powerful, but sometimes they are too much. What if you only need to change one line inside a large configuration file?

For this, we need a more precise tool. I call this the **intra-file surgical scalpel**. This allows us to perform fine-grained modifications inside structured files like XML, JSON, or YAML.

Let's look at this example. Here is our base configuration file, `core.xml`. It defines a standard `securityManager` and a `dataService`.

Now, let's look at the delta file for Customer A. Notice that it has the same structure as the base file. A full file is just a special case of a delta. This delta file declaratively **describes the differences** for Customer A.

First, the special attribute `x:extends="super"` tells our smart loader to merge this delta with the base file.

Then, look at the precision of the changes described in this delta:

1.  It can **modify an attribute**. For the bean with `id="securityManager"`, it specifies a different `class` attribute.
2.  It can **remove an element**. For the `dataService` bean, it uses the state `x:override="remove"`.
3.  It can **add new elements conditionally**. It specifies a new `auditLogger` bean that should only exist if the feature flag `auditing.enabled` is on.

This gives us incredible power and precision. We can surgically alter complex configuration files on a case-by-case basis, all while leaving the original base files completely clean and untouched."

**[Click to Next Slide]**


### Slide 14: Delta Oriented Framework: The Core Principle

**(Script):**

"So we've seen the mechanics of deltas. But the real power comes from applying them universally. This brings us to the core principle of our framework: **Unified, DSL-Agnostic Customization.**

The goal is to have **one customization mechanism for every engine in our stack**. We don't want a special way to customize Spring, another for MyBatis, and a third for our front-end framework. That would just create new kinds of complexity.

Instead, we use a single, elegant pattern: the **'Loader as Generator'**.

The process is simple. For any engine in our stack, we replace its native loader with our universal Delta-aware loader. This new loader becomes the Generator. It non-intrusively finds the base configuration and any active deltas, merges them, and generates the final model that the engine expects.

**The impact of this approach is profound. By adapting the loaders at each layer of our technology stack, we gain the ability to customize everything—from the database to the UI—using a single, consistent delta mechanism. This is what enables true, seamless full-stack customization.**

And this points to our strategic direction. We can systematically transform more and more imperative code into declarative model definitions. As more of our system becomes model-driven, more of it can be customized using this powerful, unified delta approach."

**[Click to Next Slide]**


### Slide 15: From Model to Code: Delta-Driven Generation

**(Script):**

"On the last slide, we saw how to use deltas to create a unified, effective model for configuring runtime engines. But this unified model is far more powerful than that. It can become the single source of truth for **code generation.**

As you can see here, our effective model—the result of merging the base with any active deltas—becomes this unified source. From this single description, we can generate a huge range of artifacts: backend code like DAOs and services, API definitions, SQL schemas, and even front-end components and internationalization files.

Now, whenever we talk about code generation, a very important question always comes up: 'What happens to my custom code? If I regenerate, will all my manual work be overwritten?'

We solve this with a simple but very effective pattern called the **Generation Gap**. It works like this:

The generator creates a base class, for example, `_Account.java`. The underscore is a convention that tells everyone: 'This file is machine-owned. It is always safe to overwrite it.'

Then, you, the developer, write your custom logic in a separate class, `Account.java`, which extends the generated one. This file, without the underscore, is **never touched by the generator.**

This simple separation provides the core advantage of our entire system: **Fearless Regeneration.**

You can evolve the base model, apply a customer delta, and hit the 'generate' button a hundred times a day with complete confidence. You know that the core logic will be updated, while your manual extensions will always remain safe and untouched."

**[Click to Next Slide]**


### Slide 16: Delta Oriented Architecture: A Cohesive Blueprint

**(Script):**

"So, we have seen all the individual pieces. Now let's put them together into a single, cohesive blueprint: the **Delta-Oriented Architecture.**

It all starts at the bottom with our **Foundation Layer**. This is the bedrock of the entire system. It contains the core enablers: our Delta Virtual File System, the unified model loaders that understand `x-extends`, and our powerful code generation engine. This layer makes everything else possible.

Building on that foundation, we have our **Core Engine Layer**. These are general-purpose, reusable frameworks and engines that provide complex capabilities for our applications, such as a Rule Engine, a Batch Engine, or a Task Flow Engine. They are stable, robust, and designed to be configured.

At the top, we have our **Business Application Layer**. This is where we assemble solutions for specific business domains, like Issuing or Acquiring, by composing and configuring the engines below.

Now for the final, critical piece: our **Delta Customization** pillar. Notice how it doesn't fit *inside* any single layer. It stands beside them as an **orthogonal concern**—a cross-cutting capability.

From this one, unified pillar, we can apply non-invasive delta transformations to any layer of the stack. We can use a delta to modify a foundational model, tweak a core engine rule, and change a business application's UI—all from one consistent, manageable place.

This architecture creates the perfect balance: a stable, reusable platform that can be deeply and safely customized to meet a vast range of specific requirements."

**[Click to Next Slide]**


### Slide 17: Reversible Computation × AI: Cohesive Models, Cleaner Signals, Better Co-pilots (Refined Script)

**(Script):**

"This framework provides a robust architecture for our future partnership with AI, by giving us much better control over the process.

First, it allows us to **more effectively manage the AI's context.** Instead of having the AI parse scattered source files, we provide it with our unified `effective model`. This is a condensed, precise, and simple representation of the system, which leads to higher-quality AI output.

Second, our interactions become far more efficient. We ask the AI to **generate a delta**, not raw code. This minimizes the information exchanged and allows for safe, conflict-free merging.

Third, we can **enforce quality through automation**. Every AI-generated delta must pass validation against a schema. This schema contains our domain-specific rules, ensuring that the AI's output is locally valid. If an error is found, we can either correct it automatically or provide precise feedback to the AI for revision.

This disciplined interaction—providing clean context, requesting structured deltas, and validating the output—transforms AI from an unpredictable assistant into a reliable and highly efficient partner.

AI is already very good at generating structured content like documents and models. In fact, this presentation and its graphics were co-created with an AI using this very approach."


**[Click to Next Slide]**

### Slide 18: Summary: Extensibility Through Reversible Computation

**(Script):**

"So, let's bring everything together and summarize what we've discussed today.

We started with **The Problem**: that traditional component-based reuse, while a good idea, ultimately hits fundamental limits—what we called the Granularity and Prediction Paradoxes. It forces architects into impossible choices.

Our **Insight** was to step outside of conventional computer science. We drew inspiration from physics and mathematics to treat features not as parts to be assembled, but as **superposable transformations** to be overlaid.

This led us to **The Solution**: The Delta-Oriented Framework, a practical and powerful architecture that is enabled by the theory of **Reversible Computation**.

And here is the **Key Understanding** I want you to leave with: Reversible Computation is not just another technique. It fundamentally **expands our solution space**. We move beyond simply *adding* things to a system. We now have the algebraic power to *transform* any related structure into another.

The **Result** of this is a complete paradigm shift:

*   We move from a world of 'Extension Points Everywhere' to a world of **'Overlays Above Everything.'**
*   We move from the chaos of '1 core and N forks' to the clean model of **'1 base and N deltas.'**
*   And our final system becomes an elegant, predictable composition: The `Effective System = Base ⊕ ΔIndustry ⊕ ΔRegion ⊕ ΔCustomer`.

Thank you."