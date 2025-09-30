
# Viewing DDD’s Essence Through Reversible Computation

If you ask Baidu Wenxin Yiyan (ERNIE Bot) what the essence of DDD is, its answer is:
> The essence of DDD (Domain-Driven Design) lies in placing the business domain at the core, and by deeply understanding and analyzing business logic, constructing a domain model that accurately reflects business needs, thereby driving software design and development.

The core of DDD is undoubtedly domain modeling. Early DDD’s focus at the technical level was actually on … [source text incomplete].

In the context of microservices, DDD has sparked a revival, but there is considerable debate over whether its best practices can be standardized into a technical framework. In the process of implementing DDD in practice, many design meetings are filled with fruitless debates over technical details, with no one able to persuade anyone else.

Is DDD’s advantage that it better adapts to the object ecosystem? Or that it better unifies stakeholders’ mental models, explicitly encoding business and managerial constraints into the technical world (per Conway’s Law), thereby achieving a better mapping between technology and business? Or is there some mathematically provable technical necessity? Drawing on the implementation approach of the open-source low-code Nop platform, this article examines the technical core of DDD.

Using domain coordinates enables dimensionality reduction. The domain coordinate space is not a Euclidean space; the shortest paths between domain concepts are geodesics. The structure determines the underlying space, and business processing amounts to feasible transformation operators on the underlying space.

In the domain spaces we work with day to day, do subspaces that can be separated still exist? The CRUD subspace can be separated out as a whole. There is a complementary subspace to CRUD operations. Different mathematical spaces have different structures and call for different solution methods.

<!-- SOURCE_MD5:bd6a6ec156eba6af1189eecab87492a3-->
