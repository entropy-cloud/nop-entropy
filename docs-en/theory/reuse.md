# Reuse in the AI Era from the Perspective of Reversible Computation

Recently, alongside the explosive popularity of ChatGPT, a view has been gaining traction: we should abandon the concept of reuse that has been repeatedly emphasized in software engineering. Some even claim that "the only way to reduce software complexity is to tackle it at the source; reuse is the root of all evil."

In my view, this is merely a critique of rigid, traditional reuse practices. Traditional reuse based on the concept of composition does have issues: only identical things can be reused, but the larger the granularity, the harder it is to find identical reusable scenarios. To make reuse possible, we have to pre-embed various adaptation conditions and selective switches in prefabricated components, which introduces unnecessary complexity, and there is all sorts of glue work during integration. But none of this is a reason to reject reuse itself.

First, reuse is absolutely necessary. We need to solidify known logical structures and logical derivation paths to avoid re-expressing them every time (what is solidified is essentially a prefab). For many people, reuse means technical reuse—i.e., the **codified form** of some business logic implemented using a certain programming language plus certain frameworks. But the most valuable reuse is the reuse of the business logic itself: once the business logic is clearly expressed, how it is finally converted into executable code can be done via code generators, even assisted by AI tools. However, these deal with the implementation layer of the logic, whereas the business logic itself should be reused in an unambiguous, analyzable form. When the underlying technology is upgraded, it can be regenerated entirely based on the description at the business-logic layer.

Known logical structures and logical derivation paths can be expressed in a technology-neutral form; that is, the expression of business logic itself can be reused. What we reuse are the structural regularities themselves, not a fixed, hardened product. As long as the structural regularities do not change, the underlying generation/derivation process and tools can remain unchanged. In Reversible Computation theory, we use DSLs to express business logic and a series of supporting tools to simplify the definition, development, and debugging of DSLs.

Traditional reuse is rigid and hardened—information is bound to a specific technological form of expression. But if information expression adheres to the principles of Reversible Computation, allowing reverse extraction and reversible conversion to other forms, then reuse becomes natural and intuitive: **we do not need to reuse prior forms; what we need to reuse is the information itself**.

For generative programming to evolve in the long run, it must clearly define the concept of Delta. Phodal writes in "AI Programming with a Promising Future" (https://zhuanlan.zhihu.com/p/614319672):

> Generative AI may also mean that the newly generated code is completely different from the original, including line counts and positions, etc. And to be able to regenerate, we need to record the new changes back into the original prompt. So at this point, how to better manage the original requirements becomes a new challenge.

The solution to this problem essentially depends on defining a Delta space and Delta merge rules—areas where Reversible Computation theory already provides a systematic solution.

"Objective laws do not bend to human will"—this effectively says that regardless of whether people are aware of them, objective laws exist. If AI is to solve problems, it must recognize and master the corresponding objective laws. If AI has not mastered the rules of addition, it may easily handle addition from 1 to 1,000, but when it comes to 1 to 100 billion, it will be riddled with errors. Reversible Computation theory essentially points out a structural regularity in software construction:

```
  App = Delta x-extends Generator<DSL>
```

As intelligence advances further, we will inevitably see DSL, Delta, Reversible, and Generator invoked more frequently. And the delta-ization of DSLs and Generators themselves will inevitably enter developers’ field of view.

The reference implementation of Reversible Computation, the Nop platform, has been open sourced:

- gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- Development examples: [tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [Introductory and Q&A video](https://www.bilibili.com/video/BV1u84y1w7kX/)
<!-- SOURCE_MD5:0bc8f8e9c27d3fd4db5d74927d149843-->
