# 知乎专栏: [可逆计算](https://www.zhihu.com/column/reversible-computation)

集中收录了作者关于可逆计算理论和Nop平台实现原理的相关论述

# [可逆计算: 下一代软件构造理论](reversible-computation.md)

对可逆计算理论的概要介绍

# [写给程序员的可逆计算理论辨析](reversible-computation-for-programmers.md)

从程序员熟悉的概念出发详细解释了差量与差量合并在程序实践中的具体形式和做法，并分析一些常见的对于可逆计算的理解为什么是错误的。

# [写给程序员的可逆计算理论辨析补遗](reversible-computation-for-programmers2.md)

继续补充一些针对可逆计算理论的概念辨析，澄清对于可逆计算理论的一些常见误解

# [从可逆计算看Delta Oriented Programming](delta-oriented-programming.md)

本文对比了可逆计算理论与软件工程理论界的相关工作，如面向特性编程（FOP）和面向差量编程（DOP），并指出在可逆计算视角下，这些理论还存在哪些不足之处。

# [从张量积看低代码平台的设计](tensor-product-lowcode.md)

框架设计中的多维度扩展在数学层面上可以看作是张量积空间上的线性映射函数。本文从理论层面解释了可逆计算原理如何与Loader抽象相结合。

# [从可逆计算看DSL的设计要点](xdsl-design.md)
Nop平台基于可逆计算原理，提出了一整套系统化的构建机制来简化DSL的设计和实现，使得我们很容易增加针对自己业务领域的DSL，也很容易在已有DSL的基础上进行扩展。

# [GPT用于复杂代码生产所需要满足的必要条件](nop-for-gpt.md)

现在很多人都在尝试用GPT直接生成代码，试图通过自然语言指导GPT完成传统的编码工作。但是，几乎没有人去真正认真的考虑一下生成的代码如何长期维护的问题。本文从理论层面分析了GPT用于复杂代码生产所需要满足的必要条件，并提出了Nop平台与GPT结合的具体策略。

# [如何评价一种框架技术（比如ORM框架）的好坏](props-and-cons-of-orm-framework.md)

对于一种新的框架技术，"很方便、很好用"这样的评价表达的仅仅是一种主观感受，能否在客观层面定义一些不受人的主观偏好影响的客观标准？