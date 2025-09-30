
What makes a good model?

Yesterday, when introducing model-driven approaches, a student asked me how to design a good model. That’s a difficult question to answer, because “good” is hard to define, and there is no fixed recipe for achieving what is considered “good.”

First, the definition of “good” is often context-dependent; a currently optimal choice may cease to be good as the context changes. Even where there is a universally “better” theory—say, general relativity vs. Newtonian mechanics—the simpler Newtonian mechanics can be more practical for solving specific problems.

Second, if we can design a model with broad applicability that accommodates many variations, the fundamental reason is that the domain logic has certain inherent regularities; the model is merely a natural reflection of those regularities. Software design is still far from being a precise, intricate science; even without formal design theory, by observing others’ successes and failures, one can typically reach a reasonably adequate level.

Third, creative problem-solving approaches may initially appear heretical and out of step with the current ecosystem, making them seem “not good.”

Although it is hard to define a “good” model in a definitive way, we can still filter out some suboptimal choices based on observable criteria.

1. Design involves multi-objective optimization. We can decompose the problem into multiple dimensions, evaluate each dimension independently, and then synthesize the assessment.

2. Design is multi-layered (spatial); do not attempt to design a single best, universal model. For example, the storage layer and the application layer can have different object structures—there is no need to enforce a unified object structure across the board. Using a DDD domain-model-driven approach does not require rejecting code generation for the entity persistence layer based on a physical model. Elements at different abstraction levels, serving different usage intents, should not be mixed within the same model. Based on Reversible Computation theory, incremental code generation can be used to share some foundational information among models with different structures.

3. A model should be evolution-oriented (temporal), and should not prematurely introduce design decisions that constrain future choices. Based on Reversible Computation theory, the Delta mechanism can always preserve the model’s finest-grained extensibility and its ability for multi-model integration; metaprogramming can compensate for the limitations of component and plugin mechanisms.

4. The model’s complexity should be moderate. The complexity of the solution should match the problem’s complexity, as well as the level of complexity the implementers can handle. Simple problems should not adopt evidently overcomplicated solutions. If the implementers’ capability is limited, even a technically superior solution may be more prone to misuse and harder to troubleshoot independently; in such cases, it may be preferable to use a solution that implementers can fully understand and control autonomously. Conversely, a complex problem may have no simple solution; blunt simplification will mean robbing Peter to pay Paul and constant firefighting.

5. A model needs to clearly define its dependencies on the external environment and minimize its external dependency points. A model’s value lies primarily in its ability to be interpreted and understood independently.

6. Internal conceptual completeness and consistency are crucial. Based on Reversible Computation theory, for the computations a model participates in, we need paired design: every state-changing action (Delta) should correspond to a reverse action (Delta), so that the system can, in some sense, be restored to a previous state. The inability to reverse often implies missing information records and an incomplete design space, leaving exceptional situations unhandled.

7. Any model with non-trivial complexity should provide mechanisms for decomposition, merging, and secondary abstraction. For example, support component encapsulation and importing submodels. Reversible Computation theory provides a comprehensive set of general solutions for this, without the need to redesign and reimplement separately for each kind of model.

8. Processing and utilization of model information can be multi-stage. Whenever possible, handle it during code generation, compile time, preprocessing, etc., and avoid entangling logic unrelated to runtime state with runtime logic.

9. The content of a model matters more than its form, but the form should allow reverse extraction. Heavy use of a particular language’s or framework’s built-in mechanisms to express the model may reduce one-off authoring costs, but is not conducive to long-term model evolution. To maximize a model’s value, the model—as descriptive information—should be multipurpose, not serving only a single intent, and should support further processing and transformation of form.

10. A model should be defined by a metamodel. Through the metamodel, we can maximally uncover semantic and structural commonalities across different models, facilitating their interconnection and interoperability. A metamodel can automatically generate model parsers, IDE plugins, designers, etc.

<!-- SOURCE_MD5:55ad53091b9c04c829db7b3398128ba8-->
