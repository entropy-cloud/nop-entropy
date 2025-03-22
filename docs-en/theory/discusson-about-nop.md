# Discussion on Nop Platform and Low-Code Platform Construction Experiences

In recent discussions within the Nop platform WeChat group, we explored how to leverage difference operations and metaprogramming concepts to address common challenges in low-code platform construction.

---


## Nop Platform Design Goals and Development Plan

The primary goal of the Nop platform is not to provide a feature-complete, end-user-oriented low-code product. Instead, it aims to offer an innovative design theory that reconstructs the entire tech stack. If you aim to compete with world-class platforms like Mendix or OutSystems, Nop provides some superior functionalities for reference and benchmarking.

Currently, the Nok platform has approximately 100,000 lines of effective code (excluding self-generated parts). We plan to extend this to around 200,000 lines by year-end. Key components include support for the XLang programming language, as well as ORM/IoC/RPC/GraphQL/Rule/Report basic engines. The Workflow engine is expected to be completed by the end of the year, followed by a distributed batch processing engine in early 2024.

The Nop platform primarily focuses on backend implementation. We currently use the Baidu AMIS framework but can switch to any other low-code frontend framework as needed. Since Nok specializes in backend development, we welcome contributions from frontend frameworks that align with our tech stack and interfaces.

---


## Key Challenges of Low-Code Platforms


## Challenge 1: Supporting Inline Edits
Is it possible for your system to support inline edits while maintaining a clean UI/UX? If performance is a concern, what solutions can you implement?

---


## Nok Platform's Technical Solutions for Common Issues


Can your system handle querying and sorting across vertical tables without significant performance degradation?

The Nok ORM offers robust support for transforming complex object relationships into efficient database queries. For instance, the following SQL snippet demonstrates how to retrieve a specific status:

```sql
SELECT * FROM entity.subEntities.my.status WHERE my.status = 'Active'
```

Using our patented property mapping algorithm, this query is translated into an optimized SQL statement that directly accesses the required data.

---



How can you manage conflicts between natural product evolution and custom enhancements?

Nok's delta layer provides a unique solution to mitigate such conflicts. Unlike traditional version control systems like Git, Nok's delta approach allows for non-invasive modifications without disrupting the core system. For example:

```bash
x:override="remove"
x:override="append"
```

This technique enables incremental updates that preserve semantic integrity while allowing flexibility for specific customizations.

---



1. **Horizontal vs. Vertical Tables**: Use Nok ORM's property mapping to handle complex relationships without sacrificing performance.
2. **Version Control and Custom Enhancements**: Implement Nok's delta layer for non-invasive modifications, ensuring both product evolution and customization can coexist harmoniously.

For more details on low-code platform requirements, refer to [Low-Code Platforms Need What Kind of ORM](https://zhuanlan.zhihu.com/p/543252423).

---

