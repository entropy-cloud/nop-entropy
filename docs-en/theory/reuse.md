# From Reversible Computing to AI Era Reuse

Recent discussions have brought up an interesting viewpoint: perhaps we should abandon the long-preached principle of reuse in software engineering. Some even argue, "Lowering software complexity can only be achieved from the ground up, and reuse is the root of all evil."

In my view, this is merely a critique of traditional rigid reuse practices. While it's true that conventional reuse based on component composition does have its issues—such as the difficulty of finding reusable contexts as projects grow—the necessity of reuse itself isn't negated.

Reuse is absolutely necessary. We need to solidify known logical structures and inference pathways to avoid repeatedly re-expressing the same concepts every time. Technical reuse, which involves using a programming language alongside certain frameworks to implement business logic in its **solidified form**, is a common practice. However, the most valuable form of reuse lies in reusing the business logic itself once it's clearly defined. This allows us to transform it into executable code, either through code generators or even AI tools. The key here is that the business logic should be expressed in an unambiguous and analyzable form for reuse.

The known logical structures and inference pathways can indeed be expressed in a neutral technical form. This means business logic itself can be reused as long as its structural patterns remain unchanged. In reversible computing theory, Domain Specific Languages (DSLs) are used to express business logic while also providing tools to simplify the definition, development, and debugging of the DSL.

Conventional reuse practices are indeed rigid and "sclerosis": they bind information into fixed technical expression forms. However, if the information can be expressed in a form that satisfies reversible computing principles—allowing it to be extracted and transformed into other forms—reuse becomes a natural and straightforward process: **we don't need to reuse old forms; we should reuse the information itself**.

For generative programming to endure over the long term, a clear definition of the delta concept is essential. Phodal, in his article ["AI时代的可编程"](https://zhuanlan.zhihu.com/p/614319672), writes:

> Generative AI might imply that newly generated code differs fundamentally from the original. For code to be regenerated, we must also record changes made to the original prompt. This raises new challenges in managing the original requirements as they evolve.

The solution to this problem hinges on the definition of delta space and delta merging rules, which are provided by reversible computing theory.

Objective patterns exist independently of human intentions. AI may need to address problems using its own understanding of objective patterns. If AI lacks an understanding of addition's principles, adding 1 to 1000 is straightforward, but adding 1 to 1 billion becomes problematic. Reversible computing theory essentially highlights a structural rule in software construction.

```
App = Delta x-extends Generator<DSL>
```

As AI becomes increasingly intelligent, we will likely see more frequent mentions of DSL, Delta, reversible, and Generator concepts. The delta nature of these concepts will undoubtedly play a significant role in development practices.

The Nop platform has been open-sourced:

- **Gitee**: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- **GitHub**: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- **Example**: [tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- **Introduction and Q&A Video**: [https://www.bilibili.com/video/BV1u84y1w7kX/](https://www.bilibili.com/video/BV1u84y1w7kX/)
