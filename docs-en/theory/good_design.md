What Makes a Good Model?

Yesterday, while introducing model-driven development, a peer asked me how to design a good model. This is a difficult question to answer because defining what constitutes a "good" model is inherently challenging.

First, a good definition often depends on the context. What was once considered optimal may no longer be suitable as the scenario evolves. Even something like Einstein's general relativity versus Newton's classical mechanics: while general relativity may provide a more accurate description of spacetime, for many practical purposes, Newtonian mechanics is still more useful.

Second, if we can create a model with a broad scope and the ability to handle various changes, its intrinsic logic likely follows certain inherent patterns. Software design is not yet a precise or complex scientific field; however, by observing others' successes and failures, one can generally achieve an acceptable level of performance.

Third, creative solutions at the outset may seem unconventional and may not fit well within existing frameworks. Such approaches might even be perceived as unfavorable.

Although it's difficult to define precisely what constitutes a "good" model, we can still apply some heuristic criteria to filter out suboptimal options.

1. **Multi-objective Optimization**: Break the problem into multiple dimensions, evaluate each individually, and then assess them comprehensively.
2. **Multi-level Architecture**: Avoid trying to create a single, all-encompassing model. For example, separate storage and application layers; they don't need to be unified. Similarly, using DDD (Domain-Driven Design) doesn't exclude physical models for generating entity storage layer code. Different abstraction levels should remain distinct. Reverse engineering is unnecessary.
3. **Time-aware Evolution**: A good model should not lock in design decisions too early. Allow room for incremental changes and adjustments based on future needs. Using a differential approach ensures fine-grained flexibility and fusion of multiple models.

4. **Balanced Complexity**: The complexity of the solution should align with the problem's complexity. Simple problems shouldn't require overly complex solutions. Even if a model technically outperforms others in terms of complexity, it may not be worth the effort if execution becomes too cumbersome or error-prone.

5. **External Dependency Minimization**: Define the model's dependencies on external systems clearly and minimize them. A good model should be understandable and manageable independently.

6. **Internal Concept Completeness and Consistency**: Internal concept completeness and consistency are crucial. Using reversible computation ensures that each change can be reversed, maintaining the ability to return to previous states. Inconsistencies often lead to missing information and incomplete designs, making them difficult to handle when exceptions occur.

7. **Modular Mechanisms for Complex Models**: Any sufficiently complex model should have decomposition, composition, and double abstraction mechanisms. For example, encapsulating components with support for import of sub-models ensures modularity. Reversible computation provides a comprehensive solution without unnecessary customization for each model.

8. **Multi-stage Processing**: Handle information through multiple stages—generation, compilation, preprocessing—to ensure each part is processed appropriately. Avoid intermingling runtime logic with parts irrelevant to the execution phase.

9. **Formality vs. Expressiveness Balance**: While content should be primarily about substance, formal representations are necessary for documentation and communication. However, over-reliance on formal methods can hinder long-term maintainability and evolution.

10. **Meta-modeling**: A model should be defined by a meta-model. This allows maximum exploration of the relationships between different models, their semantics, and structures. It also facilitates plug-in development of parsers, IDEs, and serializers without deep customization.

11. **Automatic Model Generation**: Use meta-modeling to automatically generate parsers, IDEs, and serializers, enabling seamless communication across various models.
