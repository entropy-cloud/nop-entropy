
# Discussion on the Nop Platform and Experiences in Building Low-Code Platforms

A few days ago, the Reversible Computation and Nop Platform WeChat group organized a discussion exploring how to use the concepts of Delta operations and metaprogramming to address common challenges in building low-code platforms.

## Design Goals and Development Plan of the Nop Platform

The design goal of the Nop platform is not to deliver a feature-complete, end-user-oriented low-code product, but to provide a technical foundation that reconstructs the entire technology stack based on innovative design theory, paving the way for coarse-grained, system-level software reuse. If you aim to compete with the world's best low-code products such as Mendix and OutSystems, and deliver features that surpass them, you can study and adopt specific technical solutions from the Nop platform.

The Nop platform currently has a little over 100,000 lines of effective code (excluding auto-generated code and brackets), with the final code size expected to be a bit over 200,000 lines. We have completed the programming language XLang that supports the principles of Reversible Computation, along with the core engines for ORM/IoC/RPC/GraphQL/Rule/Report. We expect to finish the Workflow engine by year-end and deliver a distributed batch processing engine in the first half of next year.

The Nop platform is primarily a backend implementation; the frontend currently uses Baidu's AMIS framework, and in principle it can be replaced by any other low-code frontend. Because the Nop platform focuses on the backend, we welcome contributors from frontend frameworks to co-build, align technical interfaces, and reduce duplication.

**The development of the Nop platform will continue; commercial use is free for small and medium-sized enterprises; all future features will be open source, and there will be no paid components.** Third parties may optimize and wrap on top of the Nop platform, offering commercial enhancements and support; Nop's licensing will not restrict such activities, nor require the secondary packaging work to be open-sourced. **Secondary development may modify package names, but may not remove the copyright notice and author links at the top of source files.**

## How does the Nop Platform overcome the common technical difficulties of low-code platforms?

## 1. When extension fields are stored in a vertical (narrow) table form, can we support querying and sorting? What if performance is low?

The NopORM engine provides a general wide–narrow transformation mechanism. It is not only used to persist extension fields; it applies to all master–detail structures and supports querying and sorting. Specifically:

1. Define a unique identifier property keyProp in the child table, for example keyProp="fieldName".
2. When the EQL object query syntax (similar to JPA's object query syntax) accesses elements in a child collection, you can use object property syntax, which will be automatically translated into table join conditions.

```
   entity.subEntities.my.status corresponds to  entity.getSubEntities().getByKey("my").getStatus()
```

3. Through the alias mechanism, complex property paths can be mapped to simple property names. In code, they are indistinguishable from native properties on the table.

```
  entity.myStatus ==>  entity.subEntities.my.status
```

When a large number of extension fields impacts performance, you can configure a dedicated extension-field table for each base table, or even specify a separate storage table for each extension field of a table. Uniform exposure as ordinary properties via alias and keyProp ensures the application layer programming is unaffected.

For details, see [What kind of ORM engine does a low-code platform need](https://zhuanlan.zhihu.com/p/543252423)

## 2. In a SaaS product, the same base feature often requires different custom adjustments for different customers, while the base product continues to iterate. How do we resolve conflicts between the two?

There are two directions of change: the natural evolution of the product and the direction of customized variants. If these two changes collide directly, severe conflicts are inevitable. The Nop platform offers the following solutions:

1. Delta merging: Compared with Git-based version management, it avoids many non-business-layer conflicts.
   Git essentially operates in a line-oriented textual Delta space. Certain semantically equivalent operations (e.g., reordering methods that does not affect semantics, or code formatting) can cause dramatic changes in the line-based space. Changes to different DSL attributes on the same line are generally non-conflicting, but Git will still recognize them as line edits.
   At the same time, Delta merging explicitly expresses the override direction, e.g., x:override="remove", x:override="append", avoiding conflicts caused by unclear semantics.
2. Between the base product layer and the business application layer, you can insert any number of Delta layers, such as a Product Bug Fix layer and a Product Improvement layer. In the Bug Fix layer, you can urgently fix bugs that cannot wait for the base product to be fixed and patched; in the Improvement layer, you can add features that can be synchronized back to the base product. This prevents mixing general features with custom changes for the current business.

<!-- SOURCE_MD5:1150a85b78fe2006405c4c2d150e3285-->
